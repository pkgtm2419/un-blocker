> Imported reference, 2026-11-14. Original: `unb/deep-research-report (1).md`. Historical analysis or proposal; claims and prior test results have not been independently rerun for this checkout. Original chat citation markers are not usable source links. See [reference index](README.md) for status.

# Un-Blocker Remediation, Hardening, and Effectiveness Program

## Executive assessment and technical boundaries

This plan is based on the repository state identified for review on **November 11, 2026**, with `main` at commit `8934519b021d56f12e90d824c20b4f60be1bda4b` and release `v2.6.1` at commit `d0e621a`. The commit immediately after the release removed 106 files and 11,716 lines, including prior QA, performance, security, and planning documents, so reproducible work should begin from a pinned commit rather than relying on mutable `main`. The current release is `v2.6.1`, published November 10, 2026. citeturn681398view0turn681398view1

The application is fundamentally a **DNS filtering VPN**, not a general HTTPS content blocker. Its README describes a local Android VPN that evaluates DNS requests on-device while leaving ordinary web traffic outside the tunnel. This is a strong architecture for privacy, low overhead, and broad cross-app domain blocking, but it creates a hard ceiling: DNS filtering sees domain names, not individual HTTP requests, HTML elements, video segments, resource types, or application semantics. citeturn892484view0

That means the correct engineering target is **maximum safe DNS-level coverage**, not “100% ads from every app.” Ads from dedicated ad-serving domains can often be blocked; ads delivered from the same host as primary content, first-party feeds, many YouTube-style in-stream ads, and traffic from apps that perform their own encrypted DNS cannot be reliably separated by a DNS-only engine. The project’s own bypass document explicitly labels strict Android Private DNS as bypassing its port-53 interception, although that document also marks its device matrix as not yet fully verified. citeturn188541view4

Android itself imposes important VPN constraints. `VpnService` creates a virtual interface and Android provides always-on and lockdown modes; if non-VPN traffic is blocked incorrectly, applications can lose connectivity. The Android documentation specifically warns VPN implementers about this failure mode. citeturn569672search0turn569672search1

Android 16 is API 36, while Android 17 is API 37. The reviewed app still builds and targets API 35, and the current emulator CI matrix contains only API 26, 30, and 35. Consequently, Android 16/17 compatibility remains a test obligation rather than an established property. Android recommends compatibility testing against the latest platform behavior changes, and Android 17 introduces additional target-SDK-gated behavior changes. citeturn892484view1turn771081view3turn569672search3turn569672search4

### Evidence grading used in this plan

**Confirmed defect** means the behavior follows directly from reviewed code or build configuration. **Confirmed process defect** means the CI/release path demonstrably permits an undesirable state. **Suspected risk** means there is enough design evidence to justify testing, but not enough to claim exploitation or breakage. **Architectural limitation** means the behavior follows from DNS/VPN constraints rather than an implementation bug. **Enhancement** means functionality that would improve coverage, usability, observability, or maintainability but is not correcting a defect.

The highest-confidence findings are the parser semantics, wildcard allow behavior, plaintext custom-domain storage, weak lint enforcement, test-signed production fallback, missing Android 16/17 CI coverage, and inadequacy of the historical blocking percentage as a real-world metric. citeturn892484view2turn771081view0turn892484view3turn892484view1turn771081view2turn771081view3turn188541view3

## Prioritized issue register and remediation design

| Priority | Finding | Why it happens / impact | Remediation and acceptance criteria |
|---|---|---|---|
| **P0** | Conditional Adblock rules are converted into unconditional DNS rules | `RuleLineParser.parse()` accepts `$third-party`, `$script`, `$image`, `$media`, `xhr`, etc. when the host is below its registrable domain, then returns a normal DNS `SUFFIX` rule. DNS has no resource-type or first/third-party context, so a conditional network rule can become much broader than intended. This can break legitimate app traffic. citeturn892484view2 | In `RuleLineParser.kt`, reject all options whose semantics cannot be represented by DNS. Preserve only options that are genuinely domain-wide and DNS-equivalent. Add a distinct `SkipReason.CONDITIONAL_UNREPRESENTABLE`. Acceptance: every supported rule has identical DNS semantics; every unsupported conditional rule is skipped and reported. |
| **P0** | Wildcard allow semantics collapse into suffix allow semantics | `ExtensionRuleStore.Builder.addRule()` stores both `RuleKind.SUFFIX` and `RuleKind.WILDCARD` allow rules in `allowSuffix`; `matchRule()` then allows the base hostname and descendants. A wildcard normally means descendants and should not necessarily imply the apex. citeturn771081view0 | Add `allowWildcard` to `RuleSnapshot`. Match it only during parent traversal, not direct apex lookup. Add rule-specificity tests. Acceptance: `*.example.com` allows `a.example.com` but not `example.com` unless an explicit suffix/exact allow exists. |
| **P0** | Rule precedence is too coarse | `matchRule()` checks all allow rules before all block rules, irrespective of specificity or source. An extremely broad allow suffix can therefore override a more precise block. citeturn771081view0 | Define an explicit precedence model: protected system rule > explicit user exact allow/block > exact > longest matching suffix/wildcard > source priority > default. Represent matches as candidates with specificity rather than early-return sets. Acceptance: deterministic precedence property tests for all exact/suffix/wildcard allow/block combinations. |
| **P0** | Production release can silently fall back to a test signer | The release workflow explicitly prints that missing production secrets cause fallback to stable test signing; a nominal production release can therefore be published using the test certificate, while release notes still say “Production release.” citeturn771081view2 | Fail closed for `vX.Y.Z`: production signing secrets and expected production signer fingerprint must exist. Restrict test signing to `test-v*` prereleases. Add artifact attestation, SHA-256 manifest, signer fingerprint, commit SHA, and reproducible release metadata. Acceptance: production job fails before build when release key is unavailable; no production release can be test-signed. GitHub artifact attestations provide verifiable provenance and can include SBOM information. citeturn190127search7 |
| **P0** | Android lint is deliberately non-blocking | `abortOnError=false`, `checkReleaseBuilds=false`, and `ignoreWarnings=true`; several checks are explicitly disabled. This makes release quality dependent on developers manually noticing reports. citeturn892484view1 | Turn release lint into a required CI gate. Re-enable disabled rules individually and document narrow suppressions with rationale. Acceptance: `lintRelease` must pass with no unsuppressed errors; warnings tracked through a baseline rather than globally ignored. |
| **P1** | Custom domains are stored as readable strings | `MyDomainsStore` reads and writes `Set<String>` through ordinary `SharedPreferences`. This conflicts with the repository README’s broader claim that learned domains and custom rules are stored as device-keyed HMAC identifiers. citeturn892484view3turn892484view0 | Separate two requirements: matching and display. For user-entered rules that must be editable/exportable, use application-level authenticated encryption with a key protected by Android Keystore; for opaque learned decisions, HMAC storage remains appropriate. Do not adopt deprecated `EncryptedSharedPreferences` as the long-term design. Android marks that API deprecated. citeturn190127search3 |
| **P1** | Backup behavior for rule history/custom domains is unclear | Android Auto Backup includes SharedPreferences by default. If custom domains are privacy-sensitive, they may be included unless explicit backup rules exclude or constrain them. citeturn190127search2 | Add explicit `dataExtractionRules` / backup configuration. Exclude encrypted secrets, raw DNS logs, key-dependent ciphertext, and ephemeral learned state unless a documented migration mechanism exists. Acceptance: backup/restore instrumentation verifies excluded data remains absent. |
| **P1** | Android 16/17 are outside the automated device matrix | Current CI tests API 26, 30, 35 only. citeturn771081view3 | Expand emulator CI to API 26, 30, 35, 36, and 37 where images are stable; add physical Android 16 and 17 runs for networking, battery, private DNS, Doze, reboot, always-on VPN, Wi-Fi/mobile transitions, IPv6 and OEM behavior. Acceptance: all release-critical VPN scenarios pass on supported current platforms. |
| **P1** | Current device tests are insufficient to prove VPN behavior | The workflow runs connected tests, but emulator success alone cannot establish power, OEM behavior, cellular routing, real Private DNS, long-duration service survival, or Android network transitions. Android’s own power tooling recommends physical hardware for power metrics. citeturn771081view3turn190127search5 | Create a separate device-certification suite with physical devices, controlled router/network, ADB traces and 24–72h soak tests. Release gate requires at least one physical Android 16 and Android 17 device when available. |
| **P1** | Historical “94.7% ad blocking” is not a real-world blocking percentage | The published baseline measures 18/19 labeled advertising hosts and 13/22 tracker hosts on an old 1.4.1 corpus, with 0/58 necessary hosts blocked. It explicitly describes deterministic host recall rather than real-world ad impressions. citeturn188541view3 | Preserve this metric as **host-list recall only**. Add real-app request/impression studies described below. Acceptance: UI and release documentation never present host recall as device-wide ad-block percentage. |
| **P1** | Downloader SSRF/TOCTOU behavior needs a fresh code audit before enabling arbitrary URLs broadly | User-controlled HTTPS filter subscriptions create a class of SSRF, redirect, DNS-rebinding, oversized-response, compression-bomb and content-type risks. The named `BoundedFilterDownloader.kt` could not be independently located in the pinned post-release tree during this research pass, so its precise implementation must be rediscovered before claiming a defect. OWASP notes that DNS validation followed by a second lookup does not prevent DNS rebinding; destination validation must hold at connect time. citeturn190127search9 | In Phase 0 locate downloader implementation and map every redirect/resolution path. Require HTTPS, reject credentials/fragments, cap redirects, response bytes, decompressed bytes, line length and rule count, reject loopback/link-local/private/multicast/metadata destinations after every DNS resolution, and bind connection to validated addresses while retaining hostname verification. Acceptance: malicious redirect/rebinding harness cannot reach disallowed addresses. |
| **P2** | Protected-domain list is static and evaluation coverage is small | Historical testing includes 58 “NEEDED” hosts including Android connectivity, sign-in, payment, CDN and developer infrastructure. That is useful but not sufficient for the modern Android ecosystem. citeturn188541view3 | Introduce a versioned protected-domain policy with provenance, reason codes and tests. Include Android connectivity checks, push messaging, Play services, common identity flows, payment flows, captive portals and DNS bootstrap hosts. Do not blanket-allow large parent domains where narrower endpoints suffice. |
| **P2** | BYO rule system needs richer validation and rollback | The project supports dynamic filter lists and custom rules, but safe user ownership requires preview, diagnostics, source isolation and rollback. The rule store already exposes skipped-rule statistics that can support this UX. citeturn771081view0 | Add paste/import/file/HTTPS subscriptions, parsing preview, source name/version, valid/skipped/unsafe counters, conflicts, protected-domain warnings, enable/disable per source, export, snapshot rollback and last-known-good activation. |
| **P2** | Supply-chain controls can be stronger | The release workflow validates the Gradle wrapper and requires dependency verification metadata, but this is not the same as PR dependency-policy enforcement or a release SBOM. citeturn771081view2turn190127search8 | Require GitHub dependency review on PRs, Dependabot where appropriate, locked action SHAs, Gradle verification, CycloneDX/SPDX SBOM, CodeQL, secret scanning and release attestations. |
| **P2** | DNS-only bypass remains possible | The repository’s own matrix identifies strict system Private DNS as a bypass hypothesis/expected behavior; app-embedded DoH/DoT/DoQ can similarly evade a port-53-only DNS design. citeturn188541view4 | First detect and clearly report bypass conditions. Do **not** silently break encrypted DNS. Evaluate an advanced full-tunnel mode only after DNS-only reliability is proven. |
| **P3** | Performance evidence is not current enough for release claims | No current independent battery/latency number for v2.6.1 is established in the reviewed evidence. Android provides Macrobenchmark and physical-device PowerMetric tooling specifically for repeatable performance measurements. citeturn190127search4turn190127search5 | Add benchmark module, deterministic DNS load generator, VPN throughput/latency test, power study, memory/GC traces and long-duration soak reports. |
| **P3** | Main-branch evidence hygiene is poor | Commit `8934519` removed many QA, security and performance reports immediately after v2.6.1. That does not imply malicious intent, but it makes historical evidence harder to audit. citeturn681398view0 | Keep reproducible benchmark protocols and release evidence in versioned, immutable release artifacts or an audit directory. Never rely on unversioned prose alone. |

### Test-first implementation rule for every defect

For every confirmed defect, start with a failing test that encodes the desired behavior, confirm it fails on the pinned baseline, implement the smallest change, confirm the new test passes, run the whole module suite, then run the full project suite. No parser, matcher, VPN, downloader or persistence refactor should be merged without a regression test reproducing the original defect.

For parser correctness, use table-driven and property-based tests covering exact, suffix, wildcard, exceptions, comments, hosts syntax, public suffixes, IDNs, trailing dots, capitalization, malformed labels, cosmetic rules, option combinations, negated options, regex/path filters, and extremely long lines. The current parser explicitly distinguishes cosmetic, path/regex, unsupported options, public suffix and protected-host rejection, so those categories should become observable contract tests rather than incidental behavior. citeturn892484view2

## Target architecture and user-controlled blocking

The recommended architecture is to **preserve DNS-only mode as the default**. It is the simplest model, keeps general app traffic outside the tunnel, avoids TLS interception, and corresponds to the project’s stated local-first design. citeturn892484view0

The rule engine should become a compiled immutable snapshot rather than a collection of loosely ordered sets. A proposed model is:

`RuleSource -> Parse -> Normalize -> Validate -> Classify -> Conflict analysis -> Compile snapshot -> Atomic activate`

Each compiled entry should retain `action`, `kind`, normalized domain, category, source ID, source priority, user/system provenance, and reason metadata. Matching should return both the decision and the winning rule so the UI can explain why a request was blocked or allowed.

The precedence model should be deliberately conservative:

1. Hard protected system/OS safety rules.
2. Explicit user exact override.
3. Exact list rules.
4. Longest matching suffix/wildcard.
5. Source priority.
6. Category policy.
7. Default allow.

This is safer than the current unconditional “allow rules first” algorithm because specificity is preserved instead of discarded. The current implementation visibly checks allow sets before block sets. citeturn771081view0

### User-provided rule workflow

The custom-rule subsystem should support **paste**, **local file**, **hosts file**, **plain domains**, **supported DNS-equivalent Adblock rules**, and **HTTPS subscriptions**. Unsupported syntax must never be silently broadened. The preview screen should show accepted rules, duplicates, unsupported conditions, unsafe/public-suffix entries, protected-domain conflicts, parse failures, source size, estimated memory impact and final rule count before activation.

Every imported source should have an immutable last-known-good snapshot. Update flow should be:

`Download -> validate transport -> validate address -> cap size -> parse in staging -> enforce safety -> compile -> smoke-test protected domains -> atomic swap`

If any stage fails, the active rules remain unchanged.

Remote subscriptions should also use cache validators such as ETag/Last-Modified when available, randomized update windows, bounded retries and exponential backoff. Network failures must not disable the currently active rules.

For privacy, editable custom rules should be encrypted at rest with an application key protected by Android Keystore. Learned domain decisions that need no human-readable export can continue using keyed HMAC identifiers. Sensitive files should be excluded from backup or restored using an intentionally designed key migration strategy; Android notes that SharedPreferences are backed up by default and warns that encrypted preference files tied to missing keys can become unusable after restore. citeturn190127search2turn190127search3

### Advanced full-tunnel mode

A full-tunnel local VPN should be treated as an **optional research track**, not Phase 1. It could observe destination IPs, associate flows with app UIDs where Android APIs permit, and potentially enforce stronger controls on plaintext DNS or known encrypted-DNS endpoints. However, it substantially increases packet-processing complexity, DNS/TCP/UDP state management, IPv6 requirements, MTU issues, battery use and risk of breaking apps.

It still would **not** make universal ad filtering possible without decrypting TLS, because HTTPS payloads remain encrypted. Therefore the project should explicitly reject blanket TLS MITM and accessibility-driven “click/remove ad UI” approaches as default architecture. Those mechanisms materially increase security, privacy, certificate-management and app-compatibility risk while still failing on many first-party/in-stream formats.

## Phased execution and pull-request strategy

### Phase zero — access, inventory, and reproducible baseline

The first branch should contain no behavioral change. Pin the audit base to `v2.6.1`/`d0e621a` and separately examine the one-commit delta to `8934519`. The release tag is one commit behind current `main`, while current `main` removes significant evidence files. citeturn681398view0turn681398view1

Work items: clone/fork, record SHA, inventory modules, capture Gradle/JDK/Android SDK versions, locate every rule parser/store/downloader/VPN/persistence/logging component, generate a dependency graph, run all current unit/instrumentation/tool tests, capture lint/CodeQL/dependency results, build `debug`, `releaseTest` and unsigned `release`, and archive raw reports.

The previously observed GitHub integration error, `403 Resource not accessible by integration`, is an explicit workflow blocker for direct branch creation in `pkgtm2419/un-blocker`. The safe contribution route is a fork under the authorized account, with `upstream` pointing to the original repository, followed by pull requests from fork branches. Direct upstream branches should only be used if repository/integration permissions are corrected.

**Go gate:** baseline builds are reproducible and every existing failure is classified.
**Effort:** M.

### Phase one — correctness and breakage prevention

Create a branch such as `fix/rule-semantics-and-precedence`.

Start with failing tests for conditional rules, wildcard allow, exact-vs-suffix specificity, conflict ordering, public suffix rejection and protected domains. Then modify `RuleLineParser.kt`, `ExtensionRuleStore.kt`, and associated rule models. citeturn892484view2turn771081view0

Add a protected-domain regression corpus for Android connectivity, Google sign-in, push, payment, DNS bootstrap, CDN, captive portal, Play Store, messaging and developer essentials. Preserve the historical 58-host “NEEDED” corpus but expand it substantially. citeturn188541view3

**Go gate:** zero protected-host regression failures and complete parser/matcher property-suite success.
**Effort:** M–L.

### Phase two — signing, CI, security and privacy

Use separate focused PRs rather than one giant security PR.

One PR makes release signing fail closed and prevents test-signed artifacts from being labeled production. One turns lint into a required gate. One introduces dependency review, CodeQL/dependency/SBOM checks and artifact attestations. GitHub documents that artifact attestations bind build provenance to repository, commit, workflow and event, while dependency review can reject newly introduced vulnerable dependencies. citeturn190127search7turn190127search8

A storage PR replaces readable custom-domain persistence with authenticated encryption, defines backup exclusions and migration from current `my_domains_store`, and confirms migration is atomic and reversible. `MyDomainsStore` currently uses ordinary `SharedPreferences`. citeturn892484view3

A downloader-security PR follows only after locating and reviewing the actual current downloader code. Test redirect chains, IPv4/IPv6 private destinations, loopback, link-local, DNS rebinding, multiple A/AAAA records, redirect-to-private, oversized responses, gzip/decompression bombs, slow reads and certificate failures. OWASP’s SSRF guidance specifically warns that resolving a hostname, checking it, and later reconnecting using a fresh lookup is vulnerable to rebinding. citeturn190127search9

**Go gate:** production releases cannot use test keys, release lint is mandatory, storage migration passes backup/restore tests, and downloader adversarial suite is green.
**Effort:** L.

### Phase three — bring-your-own lists and explainability

Add source-oriented list management rather than extending a single text field.

Every source gets: ID, name, type, URL/file reference, enabled state, last successful update, content hash, parser statistics, active rule count, failure reason and rollback snapshot.

UI work includes preview, diff before update, invalid-rule explanation, protected-domain conflict warnings, per-source enable/disable, update-now, export, import and rollback.

**Go gate:** malformed or unsafe data cannot modify the active snapshot; rollback restores the exact prior rule hash.
**Effort:** L.

### Phase four — coverage improvements

Only after correctness is stable should rule coverage expand. More lists should not be equated with better blocking.

Evaluate carefully curated sources at distinct aggressiveness levels: **Balanced**, **Enhanced**, and **Aggressive**. Each level must have a false-positive budget. Never merge a new list solely because it increases host recall.

Use source-specific category metadata for advertising, analytics/tracking, telemetry, malware and optional adult content. Keep security-oriented domains logically separate from ad blocking so critical security rules are not disabled when a user switches ad protection modes.

**Go gate:** improved eligible-ad/request recall without exceeding the breakage threshold defined later in the scorecard.
**Effort:** L.

### Phase five — Android 16/17 and performance validation

Expand CI beyond the current 26/30/35 matrix. citeturn771081view3

Test Android 16/API 36 and Android 17/API 37; run compatibility-framework toggles where relevant. Android exposes compatibility changes through ADB precisely to help test behavior changes before raising target SDK. citeturn569672search5

Physical-device scenarios should include reboot, foreground/background, Doze, battery saver, screen-off, Wi-Fi→cellular, cellular→Wi-Fi, airplane-mode transitions, captive portal, IPv4-only, IPv6-only where available, dual-stack, DNS failure, resolver change, Private DNS Off/Automatic/Strict, another VPN already installed, always-on VPN, lockdown attempt, service revocation, app update and process death.

Performance measurements should use repeatable Macrobenchmark/system tracing and, where suitable, physical Pixel `PowerMetric`; Android documents both and notes that PowerMetric requires supported physical Pixel hardware. citeturn190127search4turn190127search5

**Go gate:** no release-critical compatibility failures and performance stays inside target budgets.
**Effort:** L.

### Phase six — comparative effectiveness study

Freeze two artifacts:

`BASELINE = v2.6.1 / d0e621a`
`CANDIDATE = final remediation commit`

Run them using identical devices, OS versions, networks, app versions, accounts, test scripts and observation windows.

No code or rule list may change during the matched comparison.

**Go gate:** statistically defensible improvement with acceptable false positives and performance cost.
**Effort:** L.

### Phase seven — release and upstream PR

Do not submit a “fix everything” PR. Recommended PR sequence:

`PR-1 tests + rule correctness`
`PR-2 protected domains + precedence`
`PR-3 CI/lint/security gates`
`PR-4 release signing/provenance`
`PR-5 secure storage/migration`
`PR-6 downloader hardening`
`PR-7 custom-rule/list UX`
`PR-8 coverage/list improvements`
`PR-9 Android 16/17 + benchmark infrastructure`
`PR-10 evidence/reporting and release documentation`

Each PR should be independently reviewable, revertible, tested and benchmarked where applicable.

The final release PR is allowed only after all required gates pass; it should include SHA-256, signer certificate fingerprint, artifact attestation, SBOM, exact Git SHA and links to immutable test artifacts. GitHub artifact attestations are designed to let consumers verify where and how a released binary was produced. citeturn190127search7

## Rigorous verification program

### Unit and property testing

The rule parser and matcher require the highest test density because a single semantic mistake can convert a narrow community filter into a domain-wide outage.

Required tests include:

- all accepted syntaxes;
- all unsupported ABP options;
- negated options;
- wildcard apex behavior;
- suffix inheritance;
- exact precedence;
- user overrides;
- protected domains;
- public suffix boundaries;
- IDN/punycode;
- Unicode rejection/normalization;
- trailing dot;
- mixed case;
- empty labels;
- single-label hosts;
- IPv4 and IPv6 literals;
- malformed filter expressions;
- 10k+ character lines;
- duplicate rules;
- contradictory sources;
- randomized domain trees.

Property tests should enforce invariants such as “adding an unrelated rule cannot change the decision for another unrelated domain” and “a wildcard rule cannot match its apex.”

### DNS packet and VPN adversarial testing

Build synthetic DNS corpus tests for A, AAAA, CNAME, MX, TXT, HTTPS/SVCB, EDNS0, truncation, malformed compression pointers, duplicate question sections, invalid lengths, unexpected transaction IDs, very large packets and response timeouts.

Fuzz parsers and packet decoders. Any malformed packet must produce a bounded failure, never an uncaught exception, unbounded allocation or VPN service death.

Network tests should deliberately induce resolver timeouts, DNS SERVFAIL, NXDOMAIN, TCP fallback, packet loss, network switch, resolver switch and upstream unreachable states.

### Downloader and input-security tests

Remote list testing must include SSRF and rebinding cases because user-controlled URLs are a trust boundary. OWASP recommends revalidating resolved destinations and preventing connection-time address changes from bypassing validation. citeturn190127search9

Test:

`127.0.0.1`, `0.0.0.0/8`, RFC1918, link-local, CGNAT, IPv6 loopback, ULA, IPv6 link-local, metadata endpoints, mixed public/private A records, DNS answer changes between requests, public URL redirecting private, HTTPS→HTTP downgrade, excessive redirects, embedded credentials, unusual ports, huge content-length, chunked unbounded responses, gzip bombs and slow streams.

### Static and supply-chain tests

Required CI checks should include Gradle dependency verification, Android lint, Detekt/Ktlint if adopted, CodeQL, dependency review, secret scanning, unit tests, instrumentation tests, SBOM generation and release provenance.

GitHub dependency review can fail a PR when a vulnerable dependency is introduced, providing a useful merge gate rather than discovering the problem only after release. citeturn190127search8

### Compatibility tests

Automated scripted workflows should exercise:

Google/Android account login, OAuth, Play Store, FCM notifications, common payment flows, Stripe/PayPal sandbox, banking test apps where legally available, streaming playback, messaging attachments, video conferencing, games with ad SDKs, browser downloads, captive portal login and app updates.

The project’s historical baseline already identifies connectivity checks, accounts, Play Store, GitHub, package registries, payments, CDNs and major consumer services as important non-block targets; that is a good seed but not a sufficient production compatibility corpus. citeturn188541view3

### Soak, memory and power testing

Run 24-hour, 48-hour and 72-hour sessions under controlled workloads. Capture service restarts, ANRs, crashes, RSS/PSS, Java/Kotlin heap, native heap, GC frequency, DNS latency distribution, packet loss and resolver errors.

For battery comparison, use identical physical devices where possible, fixed display settings, fixed network, same app workload and randomized A/B order. Android recommends modern tracing/Power Profiler/Macrobenchmark approaches over relying exclusively on the older Battery Historian workflow. citeturn190127search6turn190127search5

## Before-and-after effectiveness methodology

The existing project figure of **94.7% advertising-host recall** must be retained only as a historical self-test. It came from 18 blocked advertising hosts out of 19 and 13 blocked tracker hosts out of 22, evaluated on version 1.4.1, with 0/58 “NEEDED” hosts blocked. citeturn188541view3

It does **not** answer the question “what percentage of ads disappear from my Android phone?”

### Metrics

Define distinct denominators.

**Eligible ad-request blocking rate**

\[
B_r = \frac{\text{eligible ad network requests prevented}}{\text{eligible ad network requests attempted}}
\]

Eligible means the request is independently labeled as advertising and is technically distinguishable by the architecture.

**Visible ad-impression removal rate**

\[
B_i = \frac{\text{baseline visible ad impressions absent with blocker}}{\text{baseline visible ad impressions}}
\]

This is the user-facing metric.

**Tracker-request blocking rate**

\[
T_r = \frac{\text{known tracker requests prevented}}{\text{known tracker requests attempted}}
\]

**Breakage rate**

\[
F = \frac{\text{required user workflows failing with blocker}}{\text{required workflows tested}}
\]

**False-positive request rate**

\[
FP = \frac{\text{legitimate requests incorrectly blocked}}{\text{legitimate requests observed}}
\]

Report Wilson 95% confidence intervals for proportions rather than a naked percentage whenever sample sizes permit.

### Corpus

Use a stratified corpus rather than cherry-picked apps:

| Category | Examples of behavior being measured |
|---|---|
| Mobile games | AdMob/AppLovin/Unity/ironSource-style SDK advertising |
| Utility/free apps | banner/interstitial/rewarded SDK ads |
| News/content apps | mixed first- and third-party advertising |
| Browsers | ordinary web display advertising |
| Social feeds | first-party sponsored posts |
| Streaming/video | preroll, midroll and same-CDN content |
| YouTube-like in-stream | known DNS-hard cases |
| Apps with embedded DoH | bypass resistance |
| Strict Android Private DNS | system bypass behavior |
| Essential workflows | login, payment, notifications, media and updates |

Each category needs enough repeated observations to produce meaningful confidence intervals.

### Matched experiment

For each test:

1. Reset app state.
2. Fix device/OS/network/app version.
3. Run blocker OFF baseline.
4. Record DNS, network endpoints, screen/video evidence and workflow outcome.
5. Reset.
6. Run v2.6.1.
7. Reset.
8. Run candidate.
9. Repeat in randomized order.
10. Label requests independently of whether Un-Blocker blocked them.

Do not label traffic by asking the blocklist itself whether the hostname is an ad; that creates circular evaluation.

### Historical versus new measurement table

| Metric | v2.6.1 baseline | Candidate | Status today |
|---|---:|---:|---|
| Historical ad-host corpus recall | Historical comparison required | TBD | Older project result: 18/19 = 94.7% on v1.4.1, not v2.6.1 real-world rate. citeturn188541view3 |
| Historical tracker-host recall | Historical comparison required | TBD | Older result: 13/22 = 59.1%. citeturn188541view3 |
| Real app eligible ad-request block rate | **UNKNOWN** | **UNKNOWN** | Must measure |
| Visible ad-impression removal | **UNKNOWN** | **UNKNOWN** | Must measure |
| Tracker-request blocking | **UNKNOWN** | **UNKNOWN** | Must measure |
| First-party/in-stream blocking | **UNKNOWN** | **UNKNOWN** | DNS architecture expected to be limited |
| Strict Private DNS bypass rate | **UNKNOWN** | **UNKNOWN** | Project documentation currently marks device results unverified. citeturn188541view4 |
| Workflow false positives | **UNKNOWN** | **UNKNOWN** | Must measure |
| Median added DNS latency | **UNKNOWN** | **UNKNOWN** | Must measure |
| p95 added DNS latency | **UNKNOWN** | **UNKNOWN** | Must measure |
| Memory steady-state | **UNKNOWN** | **UNKNOWN** | Must measure |
| 24h energy overhead | **UNKNOWN** | **UNKNOWN** | Must measure |
| Crash/ANR rate in soak | **UNKNOWN** | **UNKNOWN** | Must measure |

### Proposed acceptance scorecard

These are **engineering targets, not achieved results**.

| Area | Proposed release target |
|---|---|
| Parser semantic correctness | 100% of supported syntax equivalence tests pass |
| Protected-domain regression | 0 failures in critical system/login/payment/notification flows |
| Real-world eligible ad-request improvement | statistically significant improvement vs v2.6.1 |
| Tracker-request improvement | statistically significant improvement vs v2.6.1 |
| Visible ad-impression improvement | improvement in at least the categories technically addressable by DNS |
| Critical workflow breakage | 0% |
| Overall workflow breakage | <0.5% test cases, with no P0/P1 flow |
| p95 DNS overhead | target <10 ms local processing contribution, measured separately from upstream resolver time |
| DNS decision engine | no unbounded allocations; deterministic bounded latency |
| VPN soak | no crash, ANR or unrecovered tunnel failure over 72h |
| Rule update failure | never destroys last-known-good active rules |
| Production signing | 100% production-key signed |
| Android compatibility | release suite green on API 26, 30, 35, 36, 37 plus physical current-version devices |
| Security | no open P0/P1 findings |
| Supply chain | SBOM + SHA-256 + signer fingerprint + provenance attestation |

The p95 threshold should be adjusted only after the baseline is measured; it is a proposed quality budget, not a claim about current performance.

## Final audit and release-report template

The completed post-implementation report should use this structure.

### Build identity

`Version:`
`Git commit:`
`Base comparison commit:`
`Build timestamp:`
`Gradle version:`
`Android Gradle Plugin:`
`Kotlin version:`
`compileSdk:`
`targetSdk:`
`minSdk:`
`APK SHA-256:`
`Signer certificate SHA-256:`
`Artifact attestation:`
`SBOM SHA-256:`
`Filter snapshot SHA-256:`
`Filter source revisions:`

The baseline configuration currently shows minSdk 26, compileSdk 35, targetSdk 35, and version 2.6.1. citeturn892484view1

### Security findings

For every issue:

`ID | severity | original evidence | exploitability | fix commit | regression test | independent verification | residual risk`

Include parser safety, rule precedence, downloader SSRF/rebinding, storage, backup, VPN lifecycle, exported Android components, certificate/network behavior, dependencies, signing and supply-chain provenance.

### Functional effectiveness

Report raw numerators and denominators, not only percentages:

`Eligible ads blocked: X / N`
`Visible ads removed: X / N`
`Trackers blocked: X / N`
`Legitimate requests blocked: X / N`
`Critical workflows passed: X / N`

Then break down by category and device.

### Performance

Report:

`DNS local decision p50/p95/p99`
`End-to-end DNS p50/p95/p99`
`Cold/warm UI startup`
`steady RSS/PSS`
`peak heap`
`GC count`
`network throughput delta`
`battery/energy delta`
`24h/48h/72h stability`

Android Macrobenchmark provides repeatable app-level timing infrastructure, while supported physical Pixel devices can expose power/energy metrics. citeturn190127search4turn190127search5

### Android compatibility

For every tested device:

`manufacturer | model | Android/API | security patch | targetSdk | IPv4/IPv6 | Private DNS mode | VPN mode | result`

Record Wi-Fi/mobile transitions, reboot, Doze, always-on, lockdown, revocation, another VPN conflict and strict Private DNS behavior.

### Reproduction bundle

Archive:

`source SHA`
`APK`
`SHA256SUMS`
`apksigner output`
`SBOM`
`artifact attestation`
`rule-source manifest`
`raw benchmark CSV/JSON`
`JUnit XML`
`lint reports`
`CodeQL/SAST output`
`device model/build fingerprints`
`ADB scripts`
`PCAP/DNS traces where privacy-safe`
`test corpus versions`
`screenshots/video evidence`
`statistical analysis notebook/script`

GitHub's artifact-attestation mechanism can cryptographically link release artifacts to repository, workflow, commit and build event, making this evidence materially stronger than a release note alone. citeturn190127search7

### Release decision

Final status should be exactly one of:

**GO** — no open P0/P1 finding, production signer verified, Android compatibility gates pass, safety targets pass, and effectiveness improves without unacceptable breakage.

**CONDITIONAL GO** — no security-critical issue, but a documented P2 limitation remains with an explicit user-facing warning.

**NO-GO** — signing provenance is invalid, any P0/P1 security/reliability issue remains, protected workflows break, VPN stability fails, or measured effectiveness does not justify the introduced complexity.

The most defensible path is therefore **not** to redesign Un-Blocker immediately. First make its current DNS engine semantically correct, deterministic, securely released and measurably safe. Then add robust user-owned filter sources. Then improve coverage. Only after those steps should an advanced full-tunnel mode be investigated. That sequence gives each improvement an independently testable purpose and prevents “more blocking” from quietly becoming “more broken apps.”
