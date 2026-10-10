import hashlib
import importlib.util
import json
import pathlib
import struct
import tempfile
import unittest


class CompilerTest(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location('compiler', pathlib.Path(__file__).with_name('compiler.py'))
        self.compiler = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.compiler)

    def fixture(self, root):
        source = root / 'seed.txt'
        source.write_bytes(b'||ads.test^\n@@||good.ads.test^\n*.wild.test\nexact.test\n')
        return {'sources': [{'id': 1, 'path': 'seed.txt', 'license': 'Apache-2.0',
            'revision': 'test-pinned', 'sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
            'syntax': 'rules', 'category': 'AD', 'url': 'https://example.test/source'}]}

    def test_deterministic_and_typed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            manifest = self.fixture(root)
            for folder in ('a', 'b'):
                self.compiler.compile_sources(root, manifest, root / folder)
            self.assertEqual((root / 'a/dns-rules.tsv').read_bytes(), (root / 'b/dns-rules.tsv').read_bytes())
            self.assertEqual((root / 'a/dns-rules.bin').read_bytes(), (root / 'b/dns-rules.bin').read_bytes())
            self.assertEqual((root / 'a/dns-rules-manifest.json').read_bytes(), (root / 'b/dns-rules-manifest.json').read_bytes())
            self.assertIn(b'ALLOW\tSUFFIX\tgood.ads.test', (root / 'a/dns-rules.tsv').read_bytes())

    def test_missing_provenance_unknown_license_and_checksum_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            for field, value in [('license', 'unknown'), ('license', 'GPL-3.0'), ('revision', ''), ('sha256', '0' * 64), ('url', '')]:
                manifest = self.fixture(root)
                manifest['sources'][0][field] = value
                with self.assertRaises(ValueError):
                    self.compiler.compile_sources(root, manifest, root / 'out')

    def test_windows_line_endings_have_identical_canonical_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            manifest = self.fixture(root)
            self.compiler.compile_sources(root, manifest, root / 'unix')
            source = root / 'seed.txt'
            source.write_bytes(source.read_bytes().replace(b'\n', b'\r\n'))
            self.compiler.compile_sources(root, manifest, root / 'windows')
            self.assertEqual((root / 'unix/dns-rules.tsv').read_bytes(), (root / 'windows/dns-rules.tsv').read_bytes())
            self.assertEqual((root / 'unix/dns-rules.bin').read_bytes(), (root / 'windows/dns-rules.bin').read_bytes())

    def test_unsupported_syntax_fails_instead_of_broadening(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            manifest = self.fixture(root)
            (root / 'seed.txt').write_bytes(b'||ads.test^$important\n')
            manifest['sources'][0]['sha256'] = hashlib.sha256((root / 'seed.txt').read_bytes()).hexdigest()
            with self.assertRaises(ValueError):
                self.compiler.compile_sources(root, manifest, root / 'out')

    def test_never_block_gate_triggers_with_readable_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            manifest = self.fixture(root)
            (root / 'never_block.txt').write_text("critical.ads.test\n")
            with self.assertRaises(ValueError) as ctx:
                self.compiler.compile_sources(root, manifest, root / 'out')
            self.assertIn("Never-block violations detected", str(ctx.exception))
            self.assertIn("BLOCK SUFFIX ads.test from source 1 matches never-block domain critical.ads.test", str(ctx.exception))

    def test_allow_exact_and_suffix_rules_parsed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            source = root / 'seed.txt'
            source.write_bytes(b'@@dashboard.example.com\n@@||portal.example.com^\n')
            manifest = {'sources': [{'id': 1, 'path': 'seed.txt', 'license': 'MIT',
                'revision': 'test-pinned', 'sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                'syntax': 'rules', 'category': 'AD', 'url': 'https://example.test/source'}]}
            self.compiler.compile_sources(root, manifest, root / 'out')
            tsv = (root / 'out/dns-rules.tsv').read_bytes()
            self.assertIn(b'ALLOW\tEXACT\tdashboard.example.com', tsv)
            self.assertIn(b'ALLOW\tSUFFIX\tportal.example.com', tsv)

    def test_hosts_parsing_and_loopback_exclusion(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            hosts = root / 'hosts.txt'
            hosts.write_bytes(
                b'127.0.0.1 localhost\n'
                b'127.0.0.1 localhost.localdomain\n'
                b'255.255.255.255 broadcasthost\n'
                b'::1 localhost\n'
                b'::1 ip6-localhost\n'
                b'0.0.0.0 0.0.0.0\n'
                b'0.0.0.0 ad-tracker.example.com # inline comment\n'
                b'127.0.0.1 metrics.telemetry.net\n'
            )
            manifest = {'sources': [{'id': 4, 'path': 'hosts.txt', 'license': 'MIT',
                'revision': 'pinned-commit', 'sha256': hashlib.sha256(hosts.read_bytes()).hexdigest(),
                'syntax': 'hosts', 'category': 'AD', 'url': 'https://example.test/hosts'}]}
            self.compiler.compile_sources(root, manifest, root / 'out')
            tsv = (root / 'out/dns-rules.tsv').read_text()
            self.assertIn("BLOCK\tEXACT\tad-tracker.example.com\tAD\t4", tsv)
            self.assertIn("BLOCK\tEXACT\tmetrics.telemetry.net\tAD\t4", tsv)
            self.assertNotIn("localhost", tsv)
            self.assertNotIn("broadcasthost", tsv)
            self.assertNotIn("0.0.0.0\t", tsv)

            bin_bytes = (root / 'out/dns-rules.bin').read_bytes()
            magic, version, count, blob_len = struct.unpack('<4sHII', bin_bytes[:14])
            self.assertEqual(magic, b'UBR2')
            self.assertEqual(version, 2)
            self.assertEqual(count, 2)

    def test_bulk_invalid_ratio_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            hosts = root / 'hosts.txt'
            # 1000 lines, 3 invalid lines (0.3% > 0.1%)
            valid_lines = [f"0.0.0.0 tracker{i}.com" for i in range(997)]
            invalid_lines = ["0.0.0.0 bad..domain1", "0.0.0.0 bad..domain2", "0.0.0.0 bad..domain3"]
            content = "\n".join(valid_lines + invalid_lines) + "\n"
            hosts.write_bytes(content.encode('utf-8'))
            manifest = {'sources': [{'id': 4, 'path': 'hosts.txt', 'license': 'MIT',
                'revision': 'pinned-commit', 'sha256': hashlib.sha256(hosts.read_bytes()).hexdigest(),
                'syntax': 'hosts', 'category': 'AD', 'url': 'https://example.test/hosts'}]}
            with self.assertRaises(ValueError) as ctx:
                self.compiler.compile_sources(root, manifest, root / 'out')
            self.assertIn("exceeded 0.1%", str(ctx.exception))

    def test_bulk_rule_syntax_drift_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            hosts = root / 'hosts.txt'
            hosts.write_bytes(b'0.0.0.0 ||bad-syntax.test^\n')
            manifest = {'sources': [{'id': 4, 'path': 'hosts.txt', 'license': 'MIT',
                'revision': 'pinned-commit', 'sha256': hashlib.sha256(hosts.read_bytes()).hexdigest(),
                'syntax': 'hosts', 'category': 'AD', 'url': 'https://example.test/hosts'}]}
            with self.assertRaises(ValueError) as ctx:
                self.compiler.compile_sources(root, manifest, root / 'out')
            self.assertIn("rule-like syntax", str(ctx.exception))


if __name__ == '__main__':
    unittest.main()
