---
name: android-testing
description: Use when a change needs Android emulator or device verification, particularly VPN consent, Keystore, Room, boot and permission behavior.
---

# Android Testing

## Steps
1. Identify Android API levels and device/emulator needed from `docs/TEST_MATRIX.md` (nightly matrix currently 26, 30, 35). Use disposable emulator; do not run VPN capture on a user device without consent.
2. Run pure JVM/Gradle tests and Python scripts first to isolate non-device failures.
3. Run `./gradlew connectedDebugAndroidTest` with explicit emulator and capture only synthetic, nonpersonal fixtures. Test consent denied/revoked, network change, background/foreground state, strict Private DNS bypass, IPv4/IPv6 and genuine boot where applicable.
4. Inspect Room v1-v2 migration, Keystore keys, backup exclusion, logging pause/clear and user rule persistence on upgrade.
5. Record date, device model/API, command, build SHA, PASS/FAIL and logs; never imply test passed if no emulator ran.
6. Update affected feature guides, `docs/BUILD_AND_TEST.md` evidence and user-facing limitations.

## Example
A boot restoration fix needs actual cold reboot on emulator/device, not just a synthetic broadcast.

## Mistake
Equating green JVM tests with successful VPN packet routing on physical Android.

## References

Read `AGENTS.md`, `docs/README.md`, `docs/QUALITY_GATES.md` and relevant source/test files before declaring completion.
