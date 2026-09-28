# Contributing

Use JDK 17 and Android SDK 36. Follow the build commands in README.md.

Keep filtering and learning on-device. Do not add telemetry, uploaded domain
lists, remote models or automatic account/sync features. Treat domain names as
sensitive input: no logs or exception messages containing names, no plaintext
learning persistence, and no private-data backups.

Submit focused changes with tests demonstrating the bug or behavior. Run the
JVM suite, Android lint and an APK build. Run instrumentation tests on a disposable
emulator for VPN, preferences or Keystore changes. Record which Android versions
were actually tested. Test real reboot separately from a synthetic boot broadcast.

Preserve third-party attribution and document the license/source of added lists
or dependencies. Contributions to application source are under Apache-2.0.
Never commit signing keys, credentials, local.properties or personal traffic logs.
