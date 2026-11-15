# Local privacy and reliability review record

The original README linked here; the source tree snapshot did not contain the page. This is a **review index**, not a fabricated test result.

## Verified by reading source

- Keystore HMAC identifiers for learned and user rules in `DeviceLearning`, `PrivateEvidenceStore`, `PrivateDomainSet`.
- Non-exported VPN service/boot receiver, backup disabled in Android manifest.
- `QueryLogSink` uses local Room domain aggregates (plaintext domain names possible), with a raw-event mode that defaults off and a separate pause control.
- Android-network resolver selection and response-validation code is present; running device behavior still requires testing.

## Still to verify on device

Cold boot/force-stop/OEM startup; Android 26/30/35 VPN consent and network change; actual private-backup/device transfer; retention and clear-barrier behavior; every installed APK's exact artifact/signature; DoT/DoH coverage limits. See [audit](audits/INITIAL_REVIEW_2026-11-13.md) and [privacy details](PRIVACY_DATA_FLOW.md).
