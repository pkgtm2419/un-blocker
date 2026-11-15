# Troubleshooting and reproducible diagnostics

## Protection appears on but domains are not blocked

Check actual `UnblockerVpnService.session` state, Android VPN consent, current network resolver availability and Private DNS/DoH settings. VPN `RUNNING` does not guarantee all DNS crosses the tunnel. Compare `ProtectionUiStateMapper` warning state. Inspect synthetic DNS tests and `DnsResolverPolicy` before changing filter rules.

## Internet stops working after filtering starts

Reproduce on disposable emulator and capture *synthetic* domain tests. Test VPN tunnel routes, protect() on upstream sockets, network transitions, TCP fallback, resolver epoch and blocked response framing. Verify never-block and mobile unbreak lists. Avoid universal public DNS fallback as an unreviewed workaround.

## User allow rule seems ignored

Read `DecideBlockingUseCase` precedence, `DeviceLearning.setUserRule`, exact-vs-subtree rules, `PrivateDomainSet`, HMAC v1/v3 migration. Test rule acceptance, child-domain semantics and stored key state. Do not expose real stored hostnames in logs.

## Logs come back after Clear

Trace `QueryLogSink.clearHistoryAndBarrier`, queue generations, Room DAO and ViewModel flow. Use `QueryLogSinkTest` concurrency fixture; don't weaken durability/retention to hide timing errors.

## Extension update fails

Check HTTPS source and redirects, DNS resolution, private-address checks, network timeouts, 15 MiB and 300k rule limits, parse skip counters, atomic temp file/rollback, catalog enabled state. Do not disable safety limits without threat review.

## Gradle/Android failures

Check JDK 17, SDK 35 installed to match actual Gradle, AGP plugin and `gradle/verification-metadata.xml`, Python rule compiler availability, `gradlew` wrapper executable, network/proxy, Android emulator API and known lint flags. Read exact first failing stack trace, not only summary. Windows use `gradlew.bat`. Don't modify tool versions or security gates speculatively.

## Documentation/Antigravity not updating

Run `python3 scripts/docs_sync.py --write`, `python3 scripts/docs_contract.py`, `python3 scripts/docs_sync.py --check`. Make sure ZIP was extracted to repository root including hidden `.agents`, and generated inventory committed. If `documentation-sync.yml` cannot open PR, check GitHub Actions token permissions and branch protections; manual PR is acceptable. For new human-readable feature behavior, invoke `/docs-maintainer`; generated inventory intentionally cannot write it.
