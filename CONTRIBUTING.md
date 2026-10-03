# Contributing

Use JDK 17, Python 3.11 and Android SDK 36. Follow the build commands in README.md.

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

Please help build a fully privacy-focused open-source project: contribute labeled
false-positive cases, accessible UI, packet fixtures, on-device performance tests,
license-reviewed rule data or reproducible OEM reboot reports. Use invented or
public test domains in reports, not your personal DNS history.

Use a feature branch and pull request to `main`. Required quality checks are
`build` (compiler/policy/JVM/lint/APK) and `instrumentation` (emulator privacy/VPN).
Do not lower the FP=0 checked-in corpus baseline to make a failing rule pass.
Explain dataset provenance and scope; corpus metrics are not global effectiveness.
Compiler and release contract tests run with:

```bash
python -m unittest discover -s tools/filter-compiler -p 'test_*.py' -v
```

Only maintainers publish matching version tags using the existing private signing
key. Keep keystores and APK binaries out of source control. Published download
links must point to actual release assets, not a CI run page or an unpublished APK.
