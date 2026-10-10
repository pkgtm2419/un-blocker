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
        test_release = policy.validate_release('2.0.2', 'test-v2.0.2')
        self.assertTrue(test_release['apk_path'].endswith('ub-blocker-2.0.2.apk'))
        self.assertEqual(True, test_release['prerelease'])
        self.assertEqual('debug', test_release['build_type'])

        prod_release = policy.validate_release('2.0.2', 'v2.0.2')
        self.assertTrue(prod_release['apk_path'].endswith('ub-blocker-2.0.2.apk'))
        self.assertEqual(False, prod_release['prerelease'])
        self.assertEqual('release', prod_release['build_type'])

    def test_mismatched_or_injected_dispatch_tags_are_rejected(self):
        policy = self.policy()
        for tag in ['test-v1.2.2', 'main', 'v1.4.1\nINJECT=true', '$(touch unwanted)', 'v1.4.1-extra']:
            with self.assertRaises(ValueError):
                policy.validate_release('2.0.2', tag)

    def test_every_signing_secret_is_required(self):
        policy = self.policy()
        # Test missing secrets
        for missing in policy.TEST_FIELDS:
            env = {name: 'present' for name in policy.TEST_FIELDS if name != missing}
            self.assertIn(missing, policy.missing_secrets(env, True))
        # Production missing secrets
        for missing in policy.RELEASE_FIELDS:
            env = {name: 'present' for name in policy.RELEASE_FIELDS if name != missing}
            self.assertIn(missing, policy.missing_secrets(env, False))

    def test_production_release_fails_if_only_test_keys_provided(self):
        policy = self.policy()
        test_only = {name: 'present' for name in policy.TEST_FIELDS}
        missing = policy.missing_secrets(test_only, False)
        self.assertEqual(list(policy.RELEASE_FIELDS), missing)


if __name__ == '__main__':
    unittest.main()
