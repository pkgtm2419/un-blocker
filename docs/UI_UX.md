# UI/UX screen and state guide

## Navigation map

| Screen | Source | Job |
| --- | --- | --- |
| Protection | `ui/screens/UnblockerScreen.kt` | VPN consent, start/stop, real coverage status, filtering tier, safe recovery |
| Local logs | `ui/logs/LogViewerScreen.kt`, `LogViewModel.kt` | local stats, search/filter, allow/block feedback, clear/export |
| Learn an App | `ui/learn/LearnAppScreen.kt`, `LearnViewModel.kt` | choose app, bounded recording, review host decisions, approve |
| Extensions | `ui/extensions/ExtensionsScreen.kt`, `ExtensionsViewModel.kt` | filter catalogs, enabled sources, custom URLs, update errors |
| Settings | `ui/settings/SettingsScreen.kt` | adult/ad toggles, mode, privacy, restart and other preferences |

`MainScreen.kt` owns tab state and VPN consent launcher. Theme and widgets live in `ui/theme`, `ui/components`, `ui/adaptive`, `ui/motion`; see root [DESIGN.md](../DESIGN.md).

## State matrix

For each screen verify: first use, empty, loading, populated, no network, stale data, permission denied, service starting/stopping, runtime failure, high font scale, light/dark, rotation/landscape, screen reader, reduced motion, user cancellation. A green shield **must not** mean "all traffic protected" when strict Private DNS or external DoH bypasses DNS. Use `ProtectionUiStateMapper` for coverage distinctions.

## Copy and data privacy

- Be precise: DNS-domain filtering, not HTTPS ad removal or anonymity.
- Avoid exposing private DNS names in device logs and screenshots; use synthetic examples.
- Explain the difference between local aggregate logs and HMAC private learning.
- Blocking decisions and live state should explain a *reason* and give clear recovery paths.
- Use existing theme tokens, `SkeuomorphicCard` and `FloatingPillNavBar`, and test touch/contrast rather than redesigning unrequested screens.
