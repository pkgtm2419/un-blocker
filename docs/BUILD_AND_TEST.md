# Local build, validation and CI

## Toolchain snapshot

`app/build.gradle.kts`: `minSdk=26`, `compileSdk=35`, `targetSdk=35`, `versionName=2.7.2`, `versionCode=38`, JVM target 17. Version catalog `gradle/libs.versions.toml` has Android Gradle Plugin 8.10.1 and Kotlin 2.0.21. **Gradle is authoritative for builds**. Use Python 3.11 for tooling when possible and inspect `gradle/wrapper/gradle-wrapper.properties` for wrapper version.

## Quick commands

```bash
# From repository root (Linux/macOS)
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
# Requires an isolated emulator/device with VPN permission:
./gradlew connectedDebugAndroidTest
# Rule compiler and release policy unit tests:
python3 -m unittest discover -s tools/filter-compiler -p 'test_*.py' -v
python3 -m unittest discover -s scripts -p 'test_*.py' -v
bash scripts/tests/run-all.sh
# Documentation pack:
python3 scripts/docs_sync.py --write
python3 scripts/docs_contract.py
python3 scripts/docs_sync.py --check
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
```

Windows PowerShell: substitute `.\gradlew.bat` for `./gradlew` and `python` for `python3` as needed. Set `JAVA_HOME` to JDK 17; install Android SDK platform/build tools required by Gradle; configure `ANDROID_HOME` or local untracked `local.properties`.

Documentation tools require Python **3.11 or newer** with no third-party packages. If the Windows Store alias intercepts `python`, use an installed interpreter path or `py -3`. This checkout's [local integration record](audits/INTEGRATION_REVIEW_2026-11-14.md) records documentation checks separately from Android build/device checks. `python validate_plans.py` validates installed plan structure without assuming six unavailable historical plans.

## CI coverage as configured

- `.github/workflows/android.yml`: hygiene, script/compile tests, Gradle unit tests, lint and APK smoke assembly; currently some Android lint failures may **not** fail a build because `app/build.gradle.kts` sets `abortOnError=false`, `ignoreWarnings=true` and disables named detectors. Treat this as an audit finding, not a green-lint guarantee.
- `.github/workflows/android-device-matrix.yml`: nightly and manual instrumentation on API 26/30/35; schedule does not equal device coverage actually observed.
- `.github/workflows/codeql.yml`: static analysis schedule/push/PR.
- `.github/workflows/release.yml`: signed release build with preflight and secret handling; do not repurpose for ordinary feature PRs.
- `.github/workflows/documentation-check.yml` and `documentation-sync.yml` are added by this pack.

## Verification record template

| Command | Environment | Result | Evidence / limitation |
| --- | --- | --- | --- |
| `./gradlew testDebugUnitTest` | JDK/SDK/device details | PASS/FAIL/NOT RUN | attach output/CI URL |
| `./gradlew lintDebug` | JDK/SDK | PASS/FAIL/NOT RUN | note current lint policy |
| `./gradlew assembleDebug` | JDK/SDK | PASS/FAIL/NOT RUN | APK path/hash |
| `./gradlew connectedDebugAndroidTest` | emulator API/vendor | PASS/FAIL/NOT RUN | consent, real network, logs |
| Python/doc checks | Python version | PASS/FAIL/NOT RUN | command stdout |

This documentation pack was created from GitHub source inspection, not an Android SDK/device build. **No runtime pass result is implied.**
