#!/usr/bin/env python3
"""Inspects APK ZIP and validates absence of unverified native ML libraries."""
import sys
import zipfile
from pathlib import Path

def check_apk(apk_path: str) -> bool:
    path = Path(apk_path)
    if not path.is_file():
        print(f"Error: APK not found at {apk_path}")
        return False

    with zipfile.ZipFile(path) as apk:
        names = apk.namelist()
        native_libs = [n for n in names if n.endswith('.so')]
        models = [n for n in names if n.endswith(('.tflite', '.onnx'))]
        has_tflite_so = any('libtensorflowlite' in lib for lib in native_libs)

        print(f"APK: {path.name}")
        print(f"Total entries: {len(names)}")
        print(f"Native .so count: {len(native_libs)}")
        print(f"Model files count: {len(models)}")

        if has_tflite_so:
            print("FAILED: APK contains libtensorflowlite_jni.so without verified runtime usage!")
            return False

        print("PASSED: No unverified native TFLite binaries found in APK.")
        return True

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python check-release-native-libs.py <path-to-apk>")
        sys.exit(1)
    success = check_apk(sys.argv[1])
    sys.exit(0 if success else 1)
