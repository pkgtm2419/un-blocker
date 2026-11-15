"""Regression checks for style input selection and verified formatter artifacts."""
import importlib.util
from pathlib import Path
import tempfile
import unittest


class KotlinStyleTests(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location(
            'kotlin_style', Path(__file__).resolve().parents[1] / 'kotlin_style.py'
        )
        self.style = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.style)
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_selects_all_source_scopes_and_gradle_without_generated_outputs(self):
        included = ['app/src/main/Main.kt', 'app/src/test/MainTest.kt',
                    'app/src/androidTest/Device.kt', 'common/src/main/Model.kt',
                    'build.gradle.kts', 'settings.gradle.kts', 'app/build.gradle.kts']
        excluded = ['app/build/generated/Generated.kt', '.tooling/example.kt',
                    'unb/example.kt', 'app/src/main/res/values/strings.xml']
        for rel in included + excluded:
            path = self.root / rel
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('', encoding='utf-8')
        actual = [p.relative_to(self.root).as_posix() for p in self.style.kotlin_files(self.root)]
        self.assertEqual(sorted(included), actual)

    def test_existing_unverified_jar_is_rejected_without_execution(self):
        jar = self.root / 'ktlint.jar'
        jar.write_bytes(b'untrusted artifact')
        with self.assertRaisesRegex(ValueError, 'SHA-256'):
            self.style.verify_jar(jar)

    def test_missing_tool_requires_explicit_download(self):
        with self.assertRaisesRegex(FileNotFoundError, 'download-tool'):
            self.style.obtain_jar(self.root / 'absent.jar', download=False)


if __name__ == '__main__':
    unittest.main()
