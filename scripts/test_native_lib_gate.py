#!/usr/bin/env python3
import importlib.util
import pathlib
import tempfile
import unittest
import zipfile

SPEC = importlib.util.spec_from_file_location(
    'check_native_libs',
    pathlib.Path(__file__).with_name('check-release-native-libs.py')
)
check_native = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(check_native)


class TestNativeLibGate(unittest.TestCase):

    def test_nonexistent_apk_fails(self):
        self.assertFalse(check_native.check_apk('/tmp/nonexistent.apk'))

    def test_apk_without_tflite_passes(self):
        with tempfile.NamedTemporaryFile(suffix='.apk', delete=False) as f:
            apk_path = f.name
        try:
            with zipfile.ZipFile(apk_path, 'w') as zf:
                zf.writestr('AndroidManifest.xml', b'<manifest/>')
                zf.writestr('classes.dex', b'dex')
            self.assertTrue(check_native.check_apk(apk_path))
        finally:
            pathlib.Path(apk_path).unlink(missing_ok=True)

    def test_apk_with_unverified_tflite_so_fails(self):
        with tempfile.NamedTemporaryFile(suffix='.apk', delete=False) as f:
            apk_path = f.name
        try:
            with zipfile.ZipFile(apk_path, 'w') as zf:
                zf.writestr('AndroidManifest.xml', b'<manifest/>')
                zf.writestr('lib/arm64-v8a/libtensorflowlite_jni.so', b'\x7fELF')
            self.assertFalse(check_native.check_apk(apk_path))
        finally:
            pathlib.Path(apk_path).unlink(missing_ok=True)


if __name__ == '__main__':
    unittest.main()
