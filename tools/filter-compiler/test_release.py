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
        test_secrets = {name: 'present' for name in policy.TEST_SIGNING_FIELDS}
        release_secrets = {name: 'present' for name in policy.RELEASE_SIGNING_FIELDS}
        test_release = policy.validate_release('1.4.0', 'test-v1.4.0', test_secrets)
        self.assertEqual('ub-blocker-1.4.0.apk', test_release['apk_name'])
        self.assertEqual(True, test_release['prerelease'])
        self.assertEqual('debug', test_release['build_type'])

        prod_release = policy.validate_release('1.4.0', 'v1.4.0', release_secrets)
        self.assertEqual('ub-blocker-1.4.0.apk', prod_release['apk_name'])
        self.assertEqual(False, prod_release['prerelease'])
        self.assertEqual('release', prod_release['build_type'])

    def test_mismatched_or_injected_dispatch_tags_are_rejected(self):
        policy = self.policy()
        secrets = {name: 'present' for name in policy.RELEASE_SIGNING_FIELDS}
        for tag in ['test-v1.2.2', 'main', 'v1.4.0\nINJECT=true', '$(touch unwanted)', 'v1.4.0-extra']:
            with self.assertRaises(ValueError):
                policy.validate_release('1.4.0', tag, secrets)

    def test_every_signing_secret_is_required(self):
        policy = self.policy()
        # Missing for test release
        for missing in policy.TEST_SIGNING_FIELDS:
            secrets = {name: 'present' for name in policy.TEST_SIGNING_FIELDS if name != missing}
            with self.assertRaises(ValueError):
                policy.validate_release('1.4.0', 'test-v1.4.0', secrets)
        # Missing for production release
        for missing in policy.RELEASE_SIGNING_FIELDS:
            secrets = {name: 'present' for name in policy.RELEASE_SIGNING_FIELDS if name != missing}
            with self.assertRaises(ValueError):
                policy.validate_release('1.4.0', 'v1.4.0', secrets)

    def test_production_release_fails_if_only_test_keys_provided(self):
        policy = self.policy()
        test_only = {name: 'present' for name in policy.TEST_SIGNING_FIELDS}
        with self.assertRaises(ValueError):
            policy.validate_release('1.4.0', 'v1.4.0', test_only)


if __name__ == '__main__':
    unittest.main()
