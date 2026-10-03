"""Executable release contract: no tag mismatch or random signing publication."""
import importlib.util
import pathlib
import unittest


class ReleasePolicyTest(unittest.TestCase):
    def policy(self):
        path = pathlib.Path(__file__).resolve().parents[2] / 'scripts/release-policy.py'
        self.assertTrue(path.is_file(), 'Release preflight is missing')
        spec = importlib.util.spec_from_file_location('release_policy', path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_matching_tags_and_apk_names(self):
        policy = self.policy()
        secrets = {name: 'present' for name in policy.SIGNING_FIELDS}
        for tag, prerelease in [('test-v1.3.0', True), ('v1.3.0', False)]:
            release = policy.validate_release('1.3.0', tag, secrets)
            self.assertEqual('ub-blocker-1.3.0.apk', release['apk_name'])
            self.assertEqual(prerelease, release['prerelease'])

    def test_mismatched_or_injected_dispatch_tags_are_rejected(self):
        policy = self.policy()
        secrets = {name: 'present' for name in policy.SIGNING_FIELDS}
        for tag in ['test-v1.2.2', 'main', 'v1.3.0\nINJECT=true', '$(touch unwanted)', 'v1.3.0-extra']:
            with self.assertRaises(ValueError):
                policy.validate_release('1.3.0', tag, secrets)

    def test_every_signing_secret_is_required(self):
        policy = self.policy()
        for missing in policy.SIGNING_FIELDS:
            secrets = {name: 'present' for name in policy.SIGNING_FIELDS if name != missing}
            with self.assertRaises(ValueError):
                policy.validate_release('1.3.0', 'test-v1.3.0', secrets)


if __name__ == '__main__':
    unittest.main()
