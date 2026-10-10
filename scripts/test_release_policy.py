import importlib.util
import os
import pathlib
import subprocess
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('release_policy', pathlib.Path(__file__).with_name('release-policy.py'))
policy = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(policy)


def git(cwd, *args):
    subprocess.run(['git', *args], cwd=cwd, check=True, capture_output=True,
                   env={**os.environ, 'GIT_AUTHOR_NAME': 't', 'GIT_AUTHOR_EMAIL': 't@t',
                        'GIT_COMMITTER_NAME': 't', 'GIT_COMMITTER_EMAIL': 't@t'})


class TagParsing(unittest.TestCase):
    def test_valid_tags(self):
        self.assertEqual(policy.parse_tag('v2.0.2'), ('2.0.2', False))
        self.assertEqual(policy.parse_tag('test-v2.0.2'), ('2.0.2', True))

    def test_invalid_tags(self):
        for tag in ('', 'v2.0', 'v2.0.2-rc1', 'release-2.0.2', 'test-2.0.2', 'v2.0.2\n', 'V2.0.2', 'v2.0.2;rm -rf'):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                policy.parse_tag(tag)


class Validation(unittest.TestCase):
    def test_matching_version(self):
        release = policy.validate_release('2.0.2', 'test-v2.0.2')
        self.assertTrue(release['prerelease'])
        self.assertEqual(release['build_type'], 'debug')
        self.assertEqual(release['environment'], 'test-release')
        self.assertEqual(release['apk_path'], 'app/build/outputs/apk/debug/ub-blocker-2.0.2.apk')

    def test_production(self):
        release = policy.validate_release('2.0.2', 'v2.0.2')
        self.assertFalse(release['prerelease'])
        self.assertEqual((release['build_type'], release['environment']), ('release', 'production'))

    def test_tag_version_mismatch_is_rejected(self):
        with self.assertRaises(ValueError):
            policy.validate_release('1.4.1', 'v1.4.2')

    def test_bad_version_name_is_rejected(self):
        with self.assertRaises(ValueError):
            policy.validate_release('2.0', 'v2.0.0')


class Secrets(unittest.TestCase):
    def test_missing_names_are_reported(self):
        self.assertEqual(policy.missing_secrets({}, True), list(policy.TEST_FIELDS))
        env = {name: 'x' for name in policy.RELEASE_FIELDS}
        self.assertEqual(policy.missing_secrets(env, False), [])
        env['UB_RELEASE_KEY_ALIAS'] = '   '
        self.assertEqual(policy.missing_secrets(env, False), ['UB_RELEASE_KEY_ALIAS'])

    def test_secret_values_are_never_printed(self):
        env = {**os.environ, **{name: 'TOPSECRETVALUE' for name in policy.TEST_FIELDS[1:]}}
        result = subprocess.run(['python3', str(pathlib.Path(__file__).with_name('release-policy.py')), 'secrets', '--build-type', 'debug'],
                                capture_output=True, text=True, env=env)
        self.assertEqual(result.returncode, 1)
        self.assertNotIn('TOPSECRETVALUE', result.stdout + result.stderr)
        self.assertIn('UB_BLOCKER_KEYSTORE_BASE64', result.stderr)


class BranchPolicy(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.repo = self.tmp.name
        git(self.repo, 'init', '-q', '-b', 'main')
        pathlib.Path(self.repo, 'app').mkdir()
        pathlib.Path(self.repo, 'app', 'build.gradle.kts').write_text('android {\n    versionName = "2.0.2"\n}\n')
        git(self.repo, 'add', '.')
        git(self.repo, 'commit', '-q', '-m', 'main commit')
        git(self.repo, 'update-ref', 'refs/remotes/origin/main', 'HEAD')
        git(self.repo, 'tag', 'v2.0.2')
        git(self.repo, 'checkout', '-q', '-b', 'feature')
        pathlib.Path(self.repo, 'x.txt').write_text('x')
        git(self.repo, 'add', '.')
        git(self.repo, 'commit', '-q', '-m', 'feature commit')
        git(self.repo, 'tag', 'v2.0.3')
        self.cwd = os.getcwd()
        os.chdir(self.repo)

    def tearDown(self):
        os.chdir(self.cwd)
        self.tmp.cleanup()

    def test_tag_on_main(self):
        self.assertTrue(policy.tag_is_on_main('v2.0.2'))

    def test_tag_not_on_main(self):
        self.assertFalse(policy.tag_is_on_main('v2.0.3'))

    def test_preflight_writes_outputs(self):
        out = pathlib.Path(self.repo, 'out.txt')
        code = policy.main(['preflight', '--tag', 'v2.0.2', '--github-output', str(out)])
        self.assertEqual(code, 0)
        text = out.read_text()
        for line in ('tag=v2.0.2', 'version=2.0.2', 'build_type=release', 'prerelease=false', 'environment=production'):
            self.assertIn(line, text)

    def test_production_tag_off_main_is_rejected(self):
        self.assertEqual(policy.main(['preflight', '--tag', 'v2.0.3', '--github-output', os.devnull]), 1)

    def test_sibling_tag_on_same_commit_is_ok(self):
        git(self.repo, 'tag', 'test-v2.0.2', 'refs/remotes/origin/main')
        self.assertIsNone(policy.sibling_tag_conflict('v2.0.2'))
        self.assertEqual(policy.main(['preflight', '--tag', 'v2.0.2', '--github-output', os.devnull]), 0)

    def test_sibling_tag_on_other_commit_is_rejected(self):
        git(self.repo, 'tag', 'test-v2.0.2', 'feature')
        self.assertIn('different commit', policy.sibling_tag_conflict('v2.0.2'))
        self.assertEqual(policy.main(['preflight', '--tag', 'v2.0.2', '--github-output', os.devnull]), 1)
        self.assertIn('different commit', policy.sibling_tag_conflict('test-v2.0.2'))

    def test_no_sibling_is_ok(self):
        self.assertIsNone(policy.sibling_tag_conflict('v2.0.2'))

    def test_test_tag_is_allowed_off_main_but_version_must_match(self):
        pathlib.Path(self.repo, 'app', 'build.gradle.kts').write_text('android {\n    versionName = "2.0.3"\n}\n')
        git(self.repo, 'tag', 'test-v2.0.3')
        self.assertEqual(policy.main(['preflight', '--tag', 'test-v2.0.3', '--github-output', os.devnull]), 0)
        self.assertEqual(policy.main(['preflight', '--tag', 'test-v2.0.2', '--github-output', os.devnull]), 1)


if __name__ == '__main__':
    unittest.main()
