# Product contract — Un-Blocker

**Status:** code-grounded product context; snapshot `main` `8934519` (2026-11-10), not a certified product audit.

## Purpose and audience

Provide Android users a controllable, local-first DNS blocker for known ads, trackers and optional adult-content domains, with transparency about why a domain was allowed or blocked. Users include people who value privacy, those protecting app reliability, and contributors validating DNS behavior across Android versions.

## Product capabilities

- Android VPN consent and persistent foreground DNS interception when system policy permits.
- Domain policy: shipped/static, downloaded extension, private exact/whole-site user rules, mobile compatibility protection, evidence-based adaptive heuristics, adult-domain policy.
- Two blocking modes: `SMART` and `APPROVED_ONLY` (consult implementation for exact meaning); filtering tiers `BASIC`, `OPTIMAL`, `COMPLETE`; 5-minute notification pause.
- Extension catalog with installable/enableable HTTPS lists, manual updates, optional auto-update toggle, and custom list URLs.
- Private learning using Keystore-backed HMAC identifiers; local query statistics/optional raw event history managed separately.
- Separate My domains protection list stored as readable names in private preferences; its storage differs from HMAC allow/block rules.
- Logs UI with search/filter/sort, allow/block actions, pause, clear, and safe CSV export through Android SAF.
- Learn-an-App per-UID recording session with human review; health checks, Private DNS coverage warning, reboot-restart intent.
- Adaptive Compose layout, floating bottom navigation, settings and accessible state reporting.

See [docs/FEATURE_MATRIX.md](docs/FEATURE_MATRIX.md) and [docs/features/README.md](docs/features/README.md) for ownership, limitations, evidence and test anchors.

## Non-goals and hard limits

- No anonymity, traffic concealment, complete VPN tunneling, TLS MITM, content-aware web ad removal, blocking of app-defined DoH/DoT, direct-IP filtering, remote privacy/analytics platform, cloud model or account system.
- No guarantee of zero false positives, zero battery cost, every OEM boot policy or measured global effectiveness. Do not invent percentages.
- DNS is resolvable only through real upstream resolver(s). The network/resolver operator can observe ordinary DNS; don't advertise complete invisibility.
- `APPROVED_ONLY` is not a default-deny firewall in current `DecideBlockingUseCase`: no-match is allowed. Avoid describing it as a strict block-all allowlist until code and tests actually implement that.

## Product quality bar

1. Never break essential device connectivity to improve block counts.
2. User intent (rules, pause, consent, privacy settings) must be honored deterministically.
3. New filters must be security-admitted and regression-tested for false positives.
4. State must accurately represent protection *and coverage*, not just service-start intent.
5. Data remains on-device by design; optional plaintext local logs must be honestly disclosed.
6. Marketing, README, release notes and docs must reflect **current source** and measured evidence.

## Key implementation proof

`app/src/main/java/com/unblocker/app/domain/usecase/DecideBlockingUseCase.kt`; `app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt`; `app/src/main/java/com/unblocker/app/data/logs/QueryLogSink.kt`; `app/src/main/java/com/unblocker/app/ui/MainScreen.kt`; `app/src/main/assets/extensions/extensions_manifest.json`; `app/build.gradle.kts`.
