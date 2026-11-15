# Initial static review — 2026-11-13

**Inspected:** GitHub `pkgtm2419/un-blocker`, default `main` SHA `8934519b021d56f12e90d824c20b4f60be1bda4b`, current repo tree and selected Kotlin/Gradle/manifest/README/PRIVACY/CI source. **Scope limitation:** source inspection only; no full Android compile, device/emulator execution, network test or independent vulnerability assessment. Items below are findings/hypotheses, not a vulnerability disclosure.

## Priority investigation items

| Priority | Observation | Action and verification |
| --- | --- | --- |
| High documentation/privacy | README's broad no-history wording conflicts with `QueryLogSink` local `domain_stats` plaintext-domain aggregate writes and default unpaused flag | Compare exact user UI opt-in and disclosure to `PRIVACY.md`; runtime retention test; align claim before release |
| High privacy consistency | Existing `PRIVACY.md` retention (7 days/50 recent events) differs from `QueryLogSink` current comment (24h/1000 raw history, 30d aggregates) | Inspect actual DAO/worker retention and update policy, preserving user controls |
| Medium security review | URL target checked via DNS resolution then `HttpURLConnection` opens connection; possible rebinding window | Test resolver-to-connect consistency, including HTTPS redirects and private mapped addresses; do not mark exploited without proof |
| Medium architecture review | `vpn-engine/UpstreamDnsServer.DEFAULTS` contains public DNS; app active service appears to use platform-resolver `DnsUpstreamClient` | Inspect runtime dependency wiring; ensure alternate module cannot override promise |
| Medium engineering gate | `app/build.gradle.kts` has `lint.abortOnError=false`, `ignoreWarnings=true` and explicit disabled checks | Evaluate whether existing CI truly gates security lint; recommend incremental enforcement with tests |
| Medium documentation | README, `.gsd`, Gradle versions differ: README badges 2.6.1, section says 2.3.0 and SDK 36; Gradle says 2.6.1 and SDK 35; `.gsd` v2.5.0 | Align source of truth and clearly label historical milestone docs |
| Medium feature documentation | `.gsd` claims 7 essential filters; runtime catalog file contains 19 entries | Validate how many are active and how compatibility lists are interpreted; document count/scope |
| Low documentation | README links 5 missing `docs/*` files | This pack adds linked pages with explicit verified/unverified status rather than false metrics |
| Medium ML terminology | `ml-engine` worker comments mention TFLite; heuristic classifier source exists | Verify bindings/scheduler; clarify model vocabulary; avoid cloud or accuracy claims |

## Source anchors

- [App configuration](https://github.com/pkgtm2419/un-blocker/blob/8934519b021d56f12e90d824c20b4f60be1bda4b/app/build.gradle.kts)
- [Runtime VPN](https://github.com/pkgtm2419/un-blocker/blob/8934519b021d56f12e90d824c20b4f60be1bda4b/app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt)
- [Decision usecase](https://github.com/pkgtm2419/un-blocker/blob/8934519b021d56f12e90d824c20b4f60be1bda4b/app/src/main/java/com/unblocker/app/domain/usecase/DecideBlockingUseCase.kt)
- [Local log sink](https://github.com/pkgtm2419/un-blocker/blob/8934519b021d56f12e90d824c20b4f60be1bda4b/app/src/main/java/com/unblocker/app/data/logs/QueryLogSink.kt)
- [Manifest catalog](https://github.com/pkgtm2419/un-blocker/blob/8934519b021d56f12e90d824c20b4f60be1bda4b/app/src/main/assets/extensions/extensions_manifest.json)

For tracked remediation use [reconciliation queue](../REPO_RECONCILIATION.md). Record test evidence before closing an item.
