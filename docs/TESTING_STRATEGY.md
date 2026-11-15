# Testing strategy: correctness before speed

## Test pyramid

1. **Unit/JVM:** pure DNS parser, response validator, IP/checksum, rule and precedence, hardened list parser, domain normalization, learning evidence, concurrency, cache, privacy, UI mapping.
2. **Property/fuzz:** malformed packet bytes, compression pointer cycles, random short/truncated headers, boundary sizes, HTTP rule-line mutations.
3. **Repository/Room:** schema v1→v2 migration, DAOs, aggregate retention/clear barrier, HMAC migration, rollback on extension download failures.
4. **Android instrumentation:** actual VPN consent and TUN routing, Keystore keys and backup exclusion, UID tracking, boot behavior, foreground notice, Private DNS warning, IPv4/IPv6 and network change.
5. **End-to-end release:** signed APK install/upgrade, cold reboot on real device where available, basic internet safety, protected domains, false-positive corpus, no unwanted DNS fallback.

## Existing high-value test anchors

`app/src/test/java/com/unblocker/app/domain/DecideBlockingUseCaseTest.kt`; `dns/DnsPacketTest.kt`, `dns/DnsResponseValidatorTest.kt`, `dns/TcpDnsResponderTest.kt`, `dns/DnsFuzzMutationTest.kt`; `logic/NeverBlockPolicyTest.kt`; `services/DnsConcurrencyStressTest.kt`, `services/DnsResolverPolicyTest.kt`; `data/repository/BoundedFilterDownloaderTest.kt`; `privacy/ReviewPrivacyRegressionTest.kt`; `quality/BlockingRegressionCorpusTest.kt`; `data-store/src/test/java/com/unblocker/data/db/migration/`; `app/src/androidTest/java/com/unblocker/app/LocalPrivacyAndVpnTest.kt`.

## Test requirements per change class

- **Policy:** table-driven tests on all precedence gates and conflicting user rules, plus no-match behavior in both modes.
- **DNS/network:** valid query, malformed query, bad/forged upstream reply, TCP truncation, IPv6 checksums and network transition.
- **Logging:** aggregate vs raw opt-in, pause, clear while queued, retention, database failure, CSV formula injection.
- **Storage/migration:** v1→v2 upgrade, old keyed user rules, partial failure atomicity and private-backup rules.
- **UI:** Android permission denial, loading/error/recovery, accessibility, screen-size/reduced-motion.
- **Extension:** HTTPS/redirect/oversize/unsupported format, rollback, HTTP failure and offline behavior.

Don't alter known-good corpus expectations or loosen tests to hide false positives. Mark machine/runtime unavailable tests **NOT RUN** in PR evidence. [Test matrix](TEST_MATRIX.md).
