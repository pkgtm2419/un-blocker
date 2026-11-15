# Un-Blocker: Ad-Blocking and Self-Learning Improvement Plan

**Repository:** `pkgtm2419/un-blocker`  
**Reviewed branch:** `main`  
**Reviewed commit:** `b16fde3ab52f5812a41129ad5afec601c1612bdf`  
**Review date:** 2026-09-28  
**Primary goals:** improve ad/tracker coverage, strengthen the local self-learning algorithm, reduce false positives, preserve privacy, and improve DNS/VPN reliability.

---

## 1. Executive Summary

The project already has a solid privacy-first foundation: DNS-only interception through Android `VpnService`, deterministic local seed data, explicit local allow/block rules, a local adaptive classifier, HMAC-protected persisted reputation, bounded caches and queues, and a meaningful JVM/instrumentation test base.

However, the next improvement should **not** be "add more broad regexes and lower the blocking threshold." That would increase apparent coverage while making false positives materially worse. The more scalable path is to first fix DNS correctness and privacy inconsistencies, then improve the rule engine and response-side visibility, and only after that redesign the learning system around repeated evidence rather than elapsed installation time.

The highest-priority findings are:

1. **Code/documentation privacy mismatch:** `UnblockerVpnService.kt` falls back to hardcoded Google, Cloudflare, and Quad9 resolvers when no Android-configured resolver is discovered, while `README.md`, `PRIVACY.md`, and `docs/local-privacy-update.md` explicitly say the app has no hardcoded public DNS fallback.
2. **IPv6 UDP responses are not standards-correct:** both synthetic blocked DNS responses and wrapped upstream IPv6 DNS responses use a zero UDP checksum. In normal IPv6 UDP, that checksum is mandatory. This can make IPv6 DNS behavior unreliable.
3. **The current "adaptive" algorithm is mainly an age-based threshold schedule:** the `BlockingStrategy` values are descriptive, but production behavior changes almost entirely by lowering one score threshold from 0.90 to 0.76 as days pass. It does not mature based on observation count or independent confirmations.
4. **A single qualifying heuristic event can become persistent reputation:** `LocalNetworkLearner` writes a learned score as soon as the composite score crosses the active threshold. The reputation record does not retain confirmation count, independent observation windows, evidence types, or user corrections.
5. **Filtering is query-name-only:** the app does not inspect returned CNAME chains. This misses CNAME-cloaked tracking, where a seemingly first-party hostname resolves through an alias to a known tracking domain.
6. **Encrypted-DNS coverage is overstated:** blocking `use-application-dns.net` can influence Firefox's automatic DoH behavior, but it is not a general Chrome/Firefox DoH bypass solution and does not defeat manually enabled encrypted DNS.
7. **The current seed matcher will not scale efficiently to very large datasets:** a `HashSet<String>` is perfectly reasonable for the current 937 bundled ad domains, but memory overhead will grow significantly if the project moves to 100k+ rules.
8. **Quality tests need a labeled corpus and measurable classifier gates:** current tests are useful regressions, but they do not produce precision/recall, false-positive rate, CNAME coverage, TCP fallback coverage, or IPv6 checksum validation.

### Recommended order

| Phase | Priority | Outcome |
|---|---|---|
| Phase 0 | P0 | DNS correctness, privacy-promise alignment, protocol reliability |
| Phase 1 | P1 | Stronger deterministic rule engine and CNAME-based blocking |
| Phase 2 | P1 | Evidence-based self-learning v3 with safer persistence and user feedback |
| Phase 3 | P2 | Large-list scalability and performance engineering |
| Phase 4 | P2/P3 | Advanced DNS protocol coverage and encrypted-DNS bypass research |
| Phase 5 | P3 | CI, release, repository hygiene, documentation synchronization |

The first three phases provide the largest product benefit. Phase 0 should be completed before making the classifier more aggressive.

---

## 2. Scope of Review

The review covered the files materially involved in DNS transport, blocking decisions, local learning, persistence, configuration, tests, CI, release behavior, and user controls.

### Core filtering and learning

- `app/src/main/java/com/unblocker/app/logic/AdDetector.kt`
- `app/src/main/java/com/unblocker/app/logic/AdultContentDetector.kt`
- `app/src/main/java/com/unblocker/app/logic/ContentFilterEngine.kt`
- `app/src/main/java/com/unblocker/app/logic/DomainName.kt`
- `app/src/main/java/com/unblocker/app/logic/analysis/AdaptiveBlockingEngine.kt`
- `app/src/main/java/com/unblocker/app/logic/analysis/DeviceLearning.kt`
- `app/src/main/java/com/unblocker/app/logic/analysis/DomainSignature.kt`
- `app/src/main/java/com/unblocker/app/logic/analysis/LocalNetworkLearner.kt`
- `app/src/main/java/com/unblocker/app/logic/analysis/PrivateDomainSet.kt`
- `app/src/main/java/com/unblocker/app/logic/analysis/PrivateReputationStore.kt`
- `app/src/main/java/com/unblocker/app/domain/model/BlockingDecision.kt`
- `app/src/main/java/com/unblocker/app/domain/usecase/DecideBlockingUseCase.kt`

### DNS/VPN path

- `app/src/main/java/com/unblocker/app/services/UnblockerVpnService.kt`
- `app/src/main/java/com/unblocker/app/services/TunnelRun.kt`
- `app/src/main/java/com/unblocker/app/services/DnsResolverPolicy.kt`
- `app/src/main/java/com/unblocker/app/logic/dns/DnsPacket.kt`
- `app/src/main/java/com/unblocker/app/logic/dns/DnsCache.kt`
- `app/src/main/java/com/unblocker/app/logic/dns/ByteArrayPool.kt`

### Settings, UI and lifecycle

- `app/src/main/java/com/unblocker/app/data/preferences/FilteringPreferences.kt`
- `app/src/main/java/com/unblocker/app/services/QuickStartManager.kt`
- `app/src/main/java/com/unblocker/app/services/HealthCheckService.kt`
- `app/src/main/java/com/unblocker/app/services/HealthCheckWorker.kt`
- `app/src/main/java/com/unblocker/app/services/BlockingRegressionCheck.kt`
- `app/src/main/java/com/unblocker/app/ui/screens/UnblockerScreen.kt`
- `app/src/main/java/com/unblocker/app/receivers/BootReceiver.kt`

### Datasets, tests, CI and docs

- `app/src/main/assets/ad_domains.txt`
- `app/src/main/assets/adult_domains.txt`
- `app/src/main/assets/default_config.json`
- JVM tests under `app/src/test/`
- instrumentation tests under `app/src/androidTest/`
- `.github/workflows/android.yml`
- `.github/workflows/release.yml`
- `README.md`
- `PRIVACY.md`
- `DATASETS.md`
- `CONTRIBUTING.md`
- `docs/local-privacy-update.md`
- Gradle configuration and release metadata

### Current dataset snapshot

At the reviewed commit, `ad_domains.txt` contains **937 canonical domain entries** with no duplicates in the file. This is still small enough that the current in-memory string set is acceptable, but the architecture should change before moving to very large lists.

---

## 3. Current Architecture

The effective runtime path is approximately:

```text
Android app
    |
    v
UnblockerVpnService
    |
    +-- TUN DNS packet parser
    |       |
    |       v
    |   ContentFilterEngine
    |       |
    |       v
    |   DecideBlockingUseCase
    |       |
    |       +-- exact local allow rule  ---> ALLOW
    |       +-- exact local block rule  ---> BLOCK
    |       +-- AdDetector
    |       |     +-- bundled seed set
    |       |     +-- suffix matching
    |       |     +-- hard regex patterns
    |       |
    |       +-- AdaptiveBlockingEngine
    |       |     +-- LocalNetworkLearner
    |       |           +-- known tracker signatures
    |       |           +-- lexical features
    |       |           +-- cadence features
    |       |           +-- entropy features
    |       |           +-- DDNS/depth boosts
    |       |           +-- PrivateReputationStore
    |       |
    |       +-- AdultContentDetector
    |
    +-- blocked -> synthetic DNS response
    |
    +-- allowed -> DNS cache -> Android resolver forwarding
                            -> response wrapped back into TUN
```

The architecture is already separated better than many small blockers. The main limitation is that classification is performed only on the **query name**, before the upstream response is parsed for aliases or other DNS metadata.

---

## 4. What Should Be Preserved

The following design choices are valuable and should remain project invariants unless the product direction deliberately changes.

### 4.1 Local-first privacy

Preserve:

- no account requirement;
- no uploaded DNS history;
- no remote machine-learning model calls;
- no analytics/advertising SDKs;
- no plaintext learned-domain persistence;
- no browsing-history database;
- private learning stored as keyed identifiers;
- local user control over reset/allow/block behavior.

### 4.2 Explicit user rules remain authoritative

Current precedence makes the exact local allow rule win over other blockers, including when the same domain also exists in the local block set. This is predictable and useful.

Recommended precedence for the future rule engine:

```text
1. User explicit allow
2. User explicit block
3. Safety/compatibility system rules
4. High-confidence deterministic block rules
5. Learned confirmed reputation
6. Heuristic/risk score
7. Allow
```

The only reason to change this order would be a deliberate security-mode feature where the user chooses different semantics.

### 4.3 Bounded memory and queues

The current project already uses bounded structures in several places:

- reputation capacity;
- recent-domain cadence map;
- DNS cache;
- forwarding thread pool queue;
- local rule capacity.

Any new feature should preserve explicit limits and predictable memory usage.

### 4.4 No invented effectiveness claims

The repository correctly avoids unsupported claims such as "99% blocking." Continue this approach. After a labeled corpus is introduced, publish measured values only for that corpus and version.

---

## 5. High-Priority Findings

## 5.1 P0 - Hardcoded resolver fallback contradicts the privacy contract

### Current behavior

`UnblockerVpnService.configuredResolvers()` first gathers Android-discovered resolvers, but when the result is empty it returns a hardcoded list containing public resolvers such as Google, Cloudflare, and Quad9.

At the same time, documentation currently states that:

- allowed queries go only to Android-configured resolvers;
- there is no hardcoded public fallback;
- if no resolver is available, DNS retries/fails rather than selecting a third party.

This is a product/privacy mismatch, not just a documentation typo.

### Recommended change

Given the current privacy promise, **remove the public fallback**.

Change the resolver selection contract to return an empty result when there is no usable underlying resolver. The forwarding path should fail closed for DNS resolution in that moment and let the client retry while the network callback refreshes available resolvers.

Recommended implementation shape:

```kotlin
private fun configuredResolvers(): List<InetAddress> =
    DnsResolverPolicy.sanitize(networkResolvers.values.flatten())
```

Then explicitly handle an empty list without creating an external resolver dependency.

### Add tests

Add a service-level policy test ensuring that the final resolver-selection path, not only `DnsResolverPolicy.sanitize`, returns no invented fallback.

### Files

- `services/UnblockerVpnService.kt`
- `services/DnsResolverPolicy.kt`
- `services/DnsResolverPolicyTest.kt`
- instrumentation test(s)
- `README.md`
- `PRIVACY.md`

---

## 5.2 P0 - IPv6 UDP checksum must be calculated

### Current behavior

For IPv6:

- `DnsPacketUtil.buildBlockedDnsResponsePacket()` writes UDP checksum `0x0000`.
- `UnblockerVpnService.wrapDnsResponseInIpUdp()` also writes UDP checksum `0x0000`.

For normal IPv6 UDP, the checksum is mandatory. A compliant receiver normally discards a UDP packet with a zero checksum unless a very specific tunnel exception is in use.

### Impact

Potential symptoms include:

- IPv6 DNS blocks silently not reaching the requesting application;
- valid upstream answers being discarded after wrapping;
- inconsistent dual-stack behavior between devices;
- tests passing on IPv4 while field behavior remains unreliable on IPv6.

### Recommended change

Create a reusable UDP checksum function supporting both IPv4 pseudo-header and IPv6 pseudo-header calculation.

Suggested structure:

```text
logic/dns/
    InternetChecksum.kt
    DnsPacket.kt
```

For IPv6, always compute the UDP checksum for both blocked and forwarded responses.

For IPv4, the project may continue using zero UDP checksum if desired, although computing it everywhere would make packet construction more uniform and testable.

### Tests

Add deterministic packet fixtures that independently recompute the checksum and assert correctness for:

- IPv6 A query blocked response;
- IPv6 AAAA query blocked response;
- IPv6 allowed response wrapping;
- odd/even payload lengths;
- non-zero checksum normalization edge case.

### Files

- `logic/dns/DnsPacket.kt`
- `services/UnblockerVpnService.kt`
- new checksum utility
- `DnsPacketTest.kt`
- `DnsPacketEdgeCaseTest.kt`
- instrumentation test for a real IPv6-routed DNS query where available

---

## 5.3 P0 - Validate more than DNS transaction ID on upstream responses

### Current behavior

The forwarding path accepts an upstream UDP response when:

- its payload length is at least 12 bytes; and
- its transaction ID equals the request transaction ID.

That is weaker than necessary.

### Recommended validation

At minimum verify:

- QR flag indicates response;
- transaction ID matches;
- QDCOUNT is valid for expected query;
- canonical question name matches request;
- QTYPE matches;
- QCLASS matches;
- source socket is connected to the chosen resolver, as it already is;
- response structure is parseable before cache insertion.

This improves robustness and helps prevent accidental response confusion or poisoning-like behavior.

### Design change

Introduce a parsed response model:

```kotlin
data class DnsResponseMetadata(
    val transactionId: Int,
    val questionName: String,
    val questionType: Int,
    val questionClass: Int,
    val truncated: Boolean,
    val rcode: Int,
    val aliases: List<String>,
    val minPositiveTtlSeconds: Long?
)
```

Do not persist this information. Parse it transiently in memory.

---

## 5.4 P0 - Implement DNS-over-TCP fallback for truncated UDP responses

### Current behavior

Allowed requests are forwarded only over UDP. The service does not detect the DNS `TC` flag and retry the query over TCP.

### Why this matters

A resolver may intentionally truncate a UDP answer and require TCP retry. Returning a truncated packet as the final result can create resolution failures for larger responses and DNSSEC-heavy responses.

### Recommended behavior

1. Send normal UDP request.
2. Parse reply.
3. If `TC=1`, open a protected `Socket` to the same resolver on port 53.
4. Send the DNS message with the two-byte TCP length prefix.
5. Read exact length-prefixed response with strict upper bounds.
6. Validate question and response metadata.
7. Return/cache the TCP answer.

Keep strict timeouts and cancellation tied to `TunnelRun`.

### Resource changes

`TunnelRun` currently tracks `DatagramSocket`s. Extend run-scoped resource ownership to include TCP sockets or a generic closable set so shutdown interrupts both transport types.

---

## 5.5 P0 - Correct the DoH canary semantics

### Current behavior

`AdDetector` treats `use-application-dns.net` as a DoH canary and comments/tests describe it as preventing Chrome and Firefox DoH bypass.

### Correct interpretation

The canary is specifically used by Firefox's automatic DoH behavior. A negative response can signal Firefox not to automatically enable application DNS in relevant modes. It is not a general encrypted-DNS control and does not override a user who deliberately selected DoH.

### Recommended changes

- Rename reason to something explicit such as `Firefox DoH canary compatibility rule`.
- Remove claims that it "forces Chrome & Firefox to use local DNS."
- Keep the rule if the desired behavior is to discourage automatic Firefox DoH while the blocker is active.
- Add documentation saying manually configured application DoH remains a bypass.

---

## 5.6 P0 - Public-DNS IP routes can blackhole unrelated traffic

The VPN adds /32 routes for several public DNS resolver IPv4 addresses in order to catch hardcoded UDP/53 DNS.

Because routing happens by destination IP rather than destination port, **all traffic** to those IPs enters the TUN interface. The parser only processes UDP destination port 53. Other traffic to the same IP can therefore be dropped.

This is especially relevant because some public resolver IPs also host HTTPS services, including encrypted DNS endpoints.

### Options

**Option A - Conservative:** remove these extra resolver-IP routes and clearly document that hardcoded DNS may bypass the DNS-only route model.

**Option B - Broader firewall architecture:** route broader traffic and implement pass-through handling for non-DNS packets. This is a much larger project and changes the app from a DNS-only split tunnel into a more general packet-forwarding firewall.

For the current privacy/product scope, Option A is safer unless strict hardcoded-DNS interception is a deliberate roadmap objective.

---

## 6. Ad/Tracker Coverage Findings

## 6.1 Current deterministic coverage is strong for a small seed set

`AdDetector` already provides:

- exact matches;
- parent/suffix matching;
- popular ad/tracker domains;
- known pop-under networks;
- redirect/shortener patterns;
- common analytics/telemetry prefixes.

The recent expansion to 937 domain entries is a useful improvement.

The issue is not that deterministic matching exists; it is that **hard regex and heuristic responsibilities are mixed together**.

---

## 6.2 Broad regexes should not automatically equal hard block

Patterns such as tokens for analytics, metrics, sponsor, affiliate, tracking, or telemetry can identify many trackers, but can also appear in legitimate infrastructure.

Current `AdDetector` converts a regex match directly into a high-confidence block.

### Recommended refactor

Split deterministic rules from heuristic evidence:

```text
DeterministicBlockRule
    - exact domain
    - suffix domain
    - explicit wildcard
    - explicit exception

HeuristicSignal
    - ad lexical token
    - analytics token
    - tracker prefix
    - high entropy
    - cadence burst
    - regular beacon cadence
    - dynamic DNS provider
    - deep subdomain
```

A rule should hard-block only when its provenance is deterministic enough to justify it. Generic lexical signals should contribute to the learner score instead.

---

## 6.3 Add a typed DNS rule engine

Introduce a dedicated `DnsRuleEngine` instead of embedding all rule forms in `AdDetector`.

### Initial syntax to support

Keep the first version intentionally smaller than a complete browser adblock syntax parser:

- exact domain: `example.com`
- suffix rule: `||example.com^`
- explicit allow/exception: `@@||example.com^`
- optional wildcard labels: `*.ads.example.com`
- comments and source metadata

Avoid URL/path rules because DNS filtering cannot evaluate URL paths.

### Rule precedence

Recommended:

```text
User allow
    > user block
    > shipped exception
    > shipped block
    > learned confirmed block
    > heuristic suspicion
```

For conflicting shipped rules, explicit exception should win.

### Data model

```kotlin
enum class RuleAction { ALLOW, BLOCK }
enum class RuleKind { EXACT, SUFFIX, WILDCARD }

data class CompiledDnsRule(
    val action: RuleAction,
    val kind: RuleKind,
    val value: String,
    val category: BlockingCategory,
    val sourceId: Int
)
```

The runtime matcher should consume a precompiled representation rather than parse text for every query.

---

## 6.4 Add response-side CNAME filtering

This is one of the highest-value coverage improvements available to a DNS blocker.

### Problem

A page or app may request:

```text
metrics.example-site.com
```

The hostname looks first-party and may not match any blocklist. The DNS response can reveal:

```text
metrics.example-site.com CNAME tracker.vendor.net
```

The current app forwards the response because it evaluates only the original query domain.

### Proposed flow

```text
Query: metrics.example-site.com
        |
        v
Query-name classifier says ALLOW
        |
        v
Upstream DNS response
        |
        +-- parse CNAME chain
        |
        +-- evaluate every alias target through deterministic rule engine
        |
        +-- known tracker target? -> replace with local blocked response
        |
        +-- otherwise forward response
```

### Learning integration

If a first-party-looking alias repeatedly resolves to a deterministic tracker endpoint, that is high-quality evidence. The queried alias can receive a strong reputation confirmation without storing the raw alias persistently.

Use a distinct evidence type such as:

```text
EVIDENCE_CNAME_TO_KNOWN_TRACKER
```

Do **not** permanently convert it to a shipped block rule; keep it as private local reputation unless it is independently curated into the public dataset.

### Required parser hardening

CNAME parsing must safely handle:

- DNS compression pointers;
- pointer loops;
- maximum recursion/depth;
- multiple CNAMEs;
- mixed answers;
- malformed RDATA;
- NXDOMAIN and NODATA;
- bounded alias count.

A limit such as 8 alias hops is sufficient for defensive parsing; exact value should be tested.

---

## 6.5 Consider HTTPS/SVCB alias handling after CNAME

Modern DNS can use HTTPS/SVCB records with alias behavior. This is not the first feature to implement, but the response parser should be designed so new RR-type handlers can be added later without rewriting the packet parser.

This belongs in Phase 4, after CNAME parsing is stable and fuzz tested.

---

## 6.6 Use Public Suffix List-aware domain decomposition

Current heuristic code treats domain labels with simple string operations. That is not sufficient for registrable-domain logic across suffixes such as `co.uk`, `com.au`, or private suffixes.

### Recommended use

Add an offline Public Suffix List snapshot at build time or use a well-audited compact library if licensing and size are acceptable.

Use it for:

- registrable-domain extraction;
- deciding which labels are subdomains;
- entropy feature calculation;
- preventing incorrect parent-domain assumptions in heuristic analysis.

Do **not** use PSL as a blocking list. It is structural metadata only.

---

## 7. Self-Learning Algorithm Review

## 7.1 Current strengths

The learner has several good properties:

- no cloud dependency;
- reputation stored using HMAC identifiers;
- bounded persistence;
- weekly score decay;
- recent cadence exists only in memory;
- entropy alone cannot block normal sites;
- punycode is excluded from entropy scoring;
- known safe services are protected from common heuristic false positives;
- malformed domains are rejected before learning.

These should be retained.

---

## 7.2 Current "two-week learning" is not evidence maturity

`AdaptiveBlockingEngine` defines strategies for Day 1 through Day 14, but actual production behavior uses the same analysis and mainly changes a numeric threshold.

A device that is installed and unused for two weeks can immediately enter the lowest threshold when it is finally used. Conversely, a device with thousands of observations on Day 1 remains artificially conservative.

### Recommended replacement

Treat elapsed time only as a secondary safety factor. Make the primary maturity signal **evidence volume and diversity**.

Suggested states:

```kotlin
enum class ReputationState {
    UNKNOWN,
    OBSERVING,
    SUSPECT,
    CONFIRMED,
    SUPPRESSED
}
```

A domain should move between states based on independent evidence, not calendar age alone.

---

## 7.3 Self-Learning v3: evidence model

### Proposed persisted record

The store should still never persist raw domain names.

Conceptual record:

```kotlin
data class ReputationRecordV3(
    val score: Float,
    val state: ReputationState,
    val evidenceMask: Int,
    val positiveWindows: Int,
    val userNegativeFeedback: Int,
    val lastConfirmedBucket: Long
)
```

Where:

- `score`: bounded aggregate confidence;
- `state`: learning lifecycle;
- `evidenceMask`: which signal families have ever confirmed the domain;
- `positiveWindows`: count of independent observation windows, saturating at a small maximum;
- `userNegativeFeedback`: count/saturation marker for local correction;
- `lastConfirmedBucket`: coarse time bucket rather than per-query timestamp.

The persisted key remains a keyed identifier.

### Candidate evidence bits

```text
1 << 0  DETERMINISTIC_LIST
1 << 1  KNOWN_TRACKER_FAMILY
1 << 2  STRONG_LEXICAL
1 << 3  CADENCE_BURST
1 << 4  PERIODIC_BEACON
1 << 5  HIGH_ENTROPY_SUBDOMAIN
1 << 6  DDNS_PLUS_TRACKER_SIGNAL
1 << 7  DEEP_SUBDOMAIN_PLUS_SIGNAL
1 << 8  CNAME_TO_KNOWN_TRACKER
1 << 9  USER_BLOCK
1 << 10 USER_ALLOW_NEGATIVE
```

Use typed evidence rather than parsing human-readable reason strings.

---

## 7.4 Require independent confirmation before learning an unknown hard block

The current learner can persist a reputation immediately after one qualifying composite event.

For an unknown domain, a safer initial policy is:

- one strong event -> `SUSPECT`, do not persist as confirmed block;
- repeated strong evidence in another observation window -> eligible for `CONFIRMED`;
- at least two different signal families should normally be present unless one signal is deterministic/high-authority, such as CNAME to a known tracker;
- user block can confirm immediately;
- user allow must suppress immediately.

The exact numeric thresholds should be calibrated with a labeled corpus rather than hardcoded from intuition.

### Observation window definition

An independent window could be separated by either:

- a minimum elapsed monotonic interval; or
- a process/VPN session boundary.

No unique app/user identity is needed.

---

## 7.5 Use monotonic time for cadence

`LocalNetworkLearner` currently uses `System.currentTimeMillis()` for burst and periodicity logic.

Wall-clock time can jump due to:

- automatic time correction;
- user changes;
- timezone changes;
- clock synchronization.

Cadence should use an injected monotonic clock based on `SystemClock.elapsedRealtime()` on Android.

Suggested interface:

```kotlin
fun interface MonotonicClock {
    fun nowMillis(): Long
}
```

Unit tests can supply deterministic time values instead of relying on rapid loops and real system time.

---

## 7.6 Make feature extraction pure and single-pass

`extractDomainSignature()` currently calls cadence evaluation and then invokes `analyzeQuery()`, which evaluates cadence again. Therefore a diagnostic signature extraction can mutate the cadence window twice for one logical event.

### Refactor

Separate:

```text
observe(domain, timestamp) -> ObservationContext
extractFeatures(domain, observationContext) -> DomainFeatures
score(features, reputation) -> Classification
updateReputation(classification) -> persistence side effect
```

This makes scoring deterministic and prevents tests/UI diagnostics from changing the training signal accidentally.

Suggested classes:

```text
analysis/
    DomainFeatureExtractor.kt
    CadenceTracker.kt
    HeuristicScorer.kt
    ReputationPolicy.kt
    ReputationStore.kt
```

---

## 7.7 Replace free-text reasons with typed evidence and reasons

Current classification category sometimes depends on checking whether a reason string contains the word `tracker`.

That is fragile.

Introduce:

```kotlin
enum class DecisionReason {
    USER_ALLOW,
    USER_BLOCK,
    STATIC_EXACT,
    STATIC_SUFFIX,
    STATIC_WILDCARD,
    KNOWN_TRACKER,
    CNAME_TRACKER,
    LEARNED_REPUTATION,
    HEURISTIC_MULTI_FACTOR,
    ADULT_RULE,
    BELOW_THRESHOLD
}
```

`BlockingDecision` can carry both a typed reason and a UI-safe description.

This also makes analytics-free local diagnostics and testing much more reliable.

---

## 7.8 Treat safe-service domains as negative priors, not universal suffix exemptions

`safeExceptions` includes broad infrastructure domains such as CDNs and large service providers. This reduces false positives but can create false negatives because tracking endpoints can exist below shared infrastructure domains.

### Recommended policy

Keep a small **never heuristically block** set only for truly essential first-party roots where the project has high confidence.

For broad infrastructure/CDN domains:

- reduce heuristic score;
- require stronger/different evidence;
- do not completely bypass deterministic CNAME or explicit rules;
- preserve explicit user allow as absolute.

For example:

```text
safe prior = -0.25 score
CNAME to deterministic tracker = +0.95 evidence
user allow = unconditional allow
```

Do not literally use these numeric values until corpus calibration; they illustrate policy separation.

---

## 7.9 Integrate user corrections into learning

Today the user allowlist overrides a learned block at decision time, but the reputation record is not corrected. The model therefore never benefits from the user's feedback.

### Recommended behavior

When the user explicitly allows a domain:

- keep the exact allow rule authoritative;
- set local learned state to `SUPPRESSED` or strongly reduce its reputation;
- record a negative-feedback bit/count in the HMAC reputation record;
- do not allow heuristic events alone to re-promote it while the explicit allow rule remains present.

When the user explicitly blocks a domain:

- exact local block is authoritative;
- optionally mark the learned state confirmed with `USER_BLOCK` evidence;
- if the user later removes that rule, return to normal evidence policy instead of keeping an immortal hard block.

This makes self-learning responsive without uploading any feedback.

---

## 7.10 Improve reputation decay and pruning

Current reputation values decay when read, but weak/stale entries can continue occupying capacity until insertion pressure evicts them.

Add periodic lightweight maintenance:

- prune records below a minimum effective score after sufficient age;
- prune `OBSERVING` records that never receive a second confirmation;
- retain explicit user-derived states only while the corresponding explicit rule exists;
- cap counters and avoid unbounded metadata.

Maintenance can run on store load, occasional writes, or bounded health work; it does not need a background telemetry job.

---

## 7.11 Separate cryptographic namespaces for different stores

`DeviceLearning` currently uses the same HMAC key for:

- learned reputation;
- allowlist;
- blocklist.

That means the same domain produces the same identifier in all three files. If multiple private files are exposed together, an observer can correlate that an identifier appears in more than one category.

### Improvement

Use separate Android Keystore aliases:

```text
unblocker.reputation.hmac.v1
unblocker.allowlist.hmac.v1
unblocker.blocklist.hmac.v1
```

or derive purpose-separated keys if a safe derivation design is introduced.

Because Android Keystore HMAC keys may not be exportable for a normal HKDF implementation, separate aliases are the simpler design.

### Migration

A migration from the old shared-key file format cannot transform existing HMAC IDs into new-key IDs without the plaintext domain. Therefore:

- existing reputation can remain in legacy v2 until naturally replaced/decayed; or
- reset only the learned reputation during a major version upgrade with clear release notes;
- existing allow/block sets cannot be transparently re-keyed without asking the user to re-enter domains.

Do not silently pretend the identifiers are migratable.

A pragmatic approach is to introduce namespace-separated aliases only for new store generations and retain old stores read-only until the user clears/recreates rules.

---

## 8. Dataset Expansion Strategy

## 8.1 Keep runtime filtering offline by default

The repository explicitly promises that the installed app does not automatically download blocklists. Preserving this is compatible with significantly better coverage.

Use a **build-time list compiler** rather than runtime auto-downloads.

### Proposed pipeline

```text
Curated upstream/list inputs
        |
        +-- license allowlist
        +-- pinned source URL/version/commit
        +-- checksum
        +-- domain syntax normalization
        +-- exception processing
        +-- duplicate removal
        +-- false-positive denylist
        |
        v
Compiled deterministic DNS rules
        |
        +-- provenance manifest
        +-- generated stats
        +-- regression corpus samples
        |
        v
Packaged Android asset
```

The runtime app remains offline except for normal DNS resolution.

---

## 8.2 Add a provenance manifest

Example:

```json
{
  "schema": 1,
  "generatedAt": "2026-09-28",
  "sources": [
    {
      "id": "project-curated",
      "license": "Apache-2.0",
      "revision": "...",
      "sha256": "..."
    }
  ],
  "ruleCount": 12345
}
```

Do not bundle third-party lists unless their license permits redistribution under the intended terms and attribution is included.

The compiler should fail CI when provenance is missing.

---

## 8.3 Support categories internally

A larger list should distinguish at least:

- ad network;
- tracker/analytics;
- malware/security if later added;
- affiliate/redirect;
- adult content.

This enables future UI profiles without rebuilding the core matcher.

For the current product, ads and trackers can remain enabled under one user-facing switch while preserving typed category data internally.

---

## 8.4 Optional manual local list import

A future privacy-preserving feature can allow the user to import a local hosts/domain/rule file through Android Storage Access Framework.

Properties:

- import happens only after explicit user action;
- no background URL fetching;
- normalize and compile on device;
- enforce size limits;
- expose parse errors/counts without logging domains externally;
- imported rules have lower precedence than explicit local allow rules.

This can substantially improve power-user coverage without changing the no-auto-download promise.

---

## 9. Large-List Data Structures

At 937 ad entries, `HashSet<String>` is adequate. Do not prematurely optimize the current list.

Before moving to roughly 100k+ entries, benchmark these approaches.

### 9.1 Reversed-label suffix trie

For DNS rules, a suffix trie maps naturally:

```text
com
  -> example
       -> ads
```

Benefits:

- exact/suffix matching in one structure;
- avoids repeatedly allocating `substringAfter()` parents;
- supports exception nodes;
- compact implementations can share label strings.

### 9.2 Sorted packed domain table

A generated sorted byte/string table plus binary search can have excellent memory characteristics and deterministic build output.

For suffix checks, store normalized reversed labels or suffix hashes.

### 9.3 Bloom filter only as a prefilter

A Bloom filter can quickly say "definitely not in set" but has false positives.

Therefore:

```text
Bloom says no -> allow deterministic-list path to skip exact lookup
Bloom says yes -> MUST verify using exact authoritative structure
```

Never let Bloom membership directly block traffic.

### 9.4 Benchmark before selection

Measure at:

- current 1k rules;
- 25k;
- 100k;
- 250k;

Metrics:

- cold startup asset load time;
- retained heap;
- average/p50/p95/p99 match time;
- allocation rate per DNS query;
- APK size.

---

## 10. Proposed Target Architecture

```text
                          +----------------------+
                          | User Rule Controller |
                          | allow / block / reset|
                          +----------+-----------+
                                     |
                                     v
+-------------+             +-----------------------+
| DNS query   |-----------> | BlockingOrchestrator  |
+-------------+             +-----------+-----------+
                                        |
           +----------------------------+----------------------------+
           |                            |                            |
           v                            v                            v
+---------------------+     +----------------------+      +----------------------+
| Compiled Rule Engine|     | Reputation Policy    |      | Adult Rule Engine    |
| exact/suffix/except |     | HMAC aggregate state |      | deterministic        |
+----------+----------+     +----------+-----------+      +----------+-----------+
           |                           ^                             |
           |                           |                             |
           v                           |                             |
+---------------------+                |                             |
| Query Feature       |----------------+                             |
| Extractor           |                                              |
| lexical/PSL/etc.    |                                              |
+----------+----------+                                              |
           |                                                         |
           v                                                         |
+---------------------+                                              |
| Cadence Tracker     |                                              |
| monotonic/in-memory |                                              |
+---------------------+                                              |
                                                                    |
                 ALLOW QUERY                                        |
                     |                                              |
                     v                                              |
              +---------------+                                     |
              | Upstream DNS  |                                     |
              +-------+-------+                                     |
                      |                                             |
                      v                                             |
              +--------------------+                                |
              | Response Validator |                                |
              | TC/CNAME/TTL/RRs   |                                |
              +---------+----------+                                |
                        |                                           |
                        +-- CNAME evidence --------------------------+
                        |
                        +-- BLOCK -> local synthetic reply
                        |
                        +-- ALLOW -> cache + forward reply
```

The key architectural improvement is that **query classification and response classification become two stages**, both feeding a typed evidence system.

---

# 11. Multi-Phase Implementation Roadmap

## Phase 0 - P0 - DNS Correctness and Privacy-Promise Alignment

**Goal:** make the transport path trustworthy before increasing blocking aggressiveness.

### 0.1 Remove hardcoded public resolver fallback

Tasks:

- remove `defaultUpstreamResolvers` or stop using it;
- make empty Android resolver set explicit;
- define behavior while resolver discovery is temporarily empty;
- add tests for no-resolver state;
- align privacy docs.

Files:

- `services/UnblockerVpnService.kt`
- `services/DnsResolverPolicy.kt`
- `services/DnsResolverPolicyTest.kt`
- instrumentation tests
- `README.md`
- `PRIVACY.md`

Acceptance:

- no code path selects an unconfigured public DNS server;
- a test fails if a future hardcoded fallback is reintroduced;
- docs and code state the same resolver policy.

### 0.2 Implement IPv6 UDP checksum

Tasks:

- add checksum utility;
- update blocked response builder;
- update forwarded-response wrapper;
- verify on deterministic fixtures.

Acceptance:

- every constructed IPv6 UDP packet has a valid non-zero checksum;
- independent test recomputation passes.

### 0.3 Add strict DNS response validation

Tasks:

- parse response header/question safely;
- match qname/qtype/qclass;
- reject malformed/mismatched responses;
- cache only validated responses.

Acceptance:

- wrong-name same-TXID fixture rejected;
- wrong-QTYPE fixture rejected;
- malformed compression fixture rejected without crash.

### 0.4 Add UDP truncation -> TCP fallback

Tasks:

- parse `TC` flag;
- protect TCP socket from VPN recursion;
- length-prefix request/response;
- bounded reads/timeouts;
- run-scoped cleanup.

Acceptance:

- synthetic truncated UDP test causes TCP retry;
- TCP answer is validated and returned;
- stopping VPN cancels pending TCP I/O.

### 0.5 Fix Firefox canary language

Tasks:

- rename reason/test descriptions;
- document exact scope;
- remove Chrome/general-DoH claim.

### 0.6 Decide public resolver-IP routing policy

Recommended default:

- remove broad /32 public resolver routes unless non-DNS passthrough is implemented.

Acceptance:

- normal HTTPS traffic to a routed resolver IP is not accidentally blackholed, or the route is no longer installed.

### 0.7 Synchronize stale metadata

Tasks:

- update `default_config.json` app version or remove duplicated version field;
- mark `docs/local-privacy-update.md` as historical verification if retained;
- remove stale source comments referencing missing `un-blocker-improvement-plan.md` unless this report is added under that name.

**Suggested PRs:**

1. `fix/dns-resolver-privacy-contract`
2. `fix/ipv6-dns-checksum-validation`
3. `feat/dns-tcp-fallback`
4. `docs/dns-scope-sync`

---

## Phase 1 - P1 - Deterministic Rule Engine and CNAME Coverage

**Goal:** improve real ad/tracker coverage without making heuristics dangerously broad.

### 1.1 Introduce `DnsRuleEngine`

Add:

```text
logic/rules/
    DnsRule.kt
    DnsRuleEngine.kt
    CompiledRuleSet.kt
    RuleParser.kt
```

Move exact/suffix matching out of `AdDetector`.

Keep `AdDetector` as a compatibility facade initially if that reduces migration risk.

Acceptance:

- current seed-list behavior remains equivalent;
- exceptions have explicit precedence;
- no new per-query regex compilation;
- parser has malformed-rule tests.

### 1.2 Reclassify generic regexes as heuristic signals

Review every `adPatterns` entry.

Categories:

- deterministic vendor/network pattern -> keep hard block;
- broad lexical concept -> move to feature score;
- obsolete/redundant -> remove.

Examples likely better as signals rather than unconditional blocks:

- generic `metrics.*`;
- generic `analytics.*`;
- generic `affiliate.*`;
- generic `sponsor.*`.

Acceptance:

- existing known ad-network fixtures still block;
- add legitimate endpoints with similar words to negative corpus.

### 1.3 Add DNS response parser with CNAME extraction

Create a reusable parser rather than extending only `minCacheTtlSeconds`.

Suggested package:

```text
logic/dns/
    DnsMessageReader.kt
    DnsResponseMetadata.kt
    DnsRecord.kt
```

Acceptance:

- compressed CNAME chain parses correctly;
- malicious pointer loop terminates safely;
- malformed RDATA returns failure instead of exception;
- alias count/depth bounded.

### 1.4 Block CNAME-cloaked trackers

After an allowed query receives a valid response:

- evaluate CNAME targets using deterministic rules;
- if a target is a deterministic tracker/ad domain, generate blocked response for original query;
- add `CNAME_TO_KNOWN_TRACKER` evidence to private learning.

Acceptance:

- first-party alias -> known tracker is blocked;
- benign CNAME/CDN chains remain allowed;
- user explicit allow for original queried domain remains authoritative according to documented precedence.

### 1.5 Add PSL-aware domain structure

Use PSL only for feature extraction and registrable-domain boundaries.

Acceptance:

- `example.co.uk` treated as registrable domain correctly;
- entropy scoring analyzes actual subdomain labels rather than assuming two-label public suffixes.

### 1.6 Build-time dataset compiler

Add a script/tool under something like:

```text
tools/filter-compiler/
```

or Gradle task plus scripts.

Responsibilities:

- normalize domains;
- reject invalid IDN/domain syntax;
- deduplicate;
- compile exact/suffix/exception rules;
- verify licenses/provenance;
- emit manifest and stats;
- generate deterministic output.

Acceptance:

- same inputs produce byte-identical output;
- source without approved license metadata fails build;
- generated rule count and checksum are visible in CI.

**Suggested PRs:**

5. `refactor/dns-rule-engine`
6. `feat/cname-response-filtering`
7. `build/filter-list-compiler`
8. `feat/public-suffix-domain-features`

---

## Phase 2 - P1 - Self-Learning v3

**Goal:** replace elapsed-time threshold relaxation with evidence-based local learning.

### 2.1 Split observation, feature extraction, scoring, and persistence

Refactor `LocalNetworkLearner` into independently testable components.

Suggested files:

```text
logic/analysis/
    DomainFeatureExtractor.kt
    CadenceTracker.kt
    DomainFeatures.kt
    HeuristicScorer.kt
    ReputationPolicy.kt
    ReputationEvidence.kt
    LocalNetworkLearner.kt
```

Acceptance:

- extracting a signature does not mutate cadence twice;
- scoring a fixed feature object is pure/deterministic;
- persistence happens only through `ReputationPolicy`.

### 2.2 Replace calendar phase with evidence phase

`AdaptiveBlockingEngine` should become either:

- a thin policy facade; or
- be renamed to `ReputationPolicy` / `AdaptiveReputationEngine`.

Replace decorative strategies with actual state transitions.

Example state transitions:

```text
UNKNOWN
  |
  | strong but non-deterministic evidence
  v
OBSERVING
  |
  | repeated independent evidence
  v
SUSPECT
  |
  | multiple signal families / trusted CNAME / user block
  v
CONFIRMED

Any state -- user allow --> SUPPRESSED
```

Elapsed installation day may remain only as a weak policy input, not the primary maturity metric.

### 2.3 Add independent confirmation windows

Persist a small saturating counter rather than timestamps per query.

Candidate initial policy for experimentation:

- unknown domains require two independent positive windows before `CONFIRMED`;
- two distinct signal families required unless deterministic evidence exists;
- CNAME-to-known-tracker can count as high-authority confirmation;
- user block confirms immediately;
- user allow suppresses immediately.

These are starting hypotheses, not final constants. Calibrate them against the corpus introduced in the testing phase.

### 2.4 Use monotonic cadence clock

- Android production: `SystemClock.elapsedRealtime()`;
- tests: fake clock.

Rewrite burst/heartbeat tests using explicit timestamps.

### 2.5 Add typed evidence and decision reason

Update:

- `BlockingDecision.kt`;
- `FilterResult.kt` if needed;
- UI mapping;
- tests.

Do not infer category from human-readable strings.

### 2.6 Learn from user corrections

Integrate rule actions with reputation policy:

```text
User Allow:
  add allow rule
  suppress reputation

User Block:
  add block rule
  add USER_BLOCK evidence

Remove rule:
  remove explicit override
  preserve only normal evidence-based state
```

### 2.7 Add v3 private persistence format

Suggested on-disk line shape remains opaque-domain-first and compact, for example:

```text
v3:<key-check>
<id>:<score>:<state>:<evidence-mask>:<positive-windows>:<negative-feedback>:<time-bucket>
```

Validate every field and cap all values.

Alternative: a binary format can reduce size, but text is currently simple and auditable. Optimize only if profiling shows need.

### 2.8 Add pruning

Prune:

- expired `OBSERVING` entries;
- low-score stale records;
- invalid/corrupt records;
- excess capacity by useful LRU-like policy rather than pure insertion order if practical.

### 2.9 Cryptographic namespace separation

Plan migration carefully. Do not assume old keyed IDs can be re-keyed without raw domains.

A safe staged approach:

1. introduce new reputation alias for v3;
2. keep old v2 reputation read-only for a bounded transition period if desired;
3. start new observations in v3;
4. allow old scores to decay away;
5. address user rule key separation only in a release that clearly communicates that old private rules may need recreation, unless a compatibility path is retained.

Acceptance for Phase 2:

- one burst on an unknown domain does not immediately become a persistent hard block;
- repeated independent multi-factor evidence can confirm a tracker;
- deterministic known trackers still block immediately;
- user allow immediately overrides/suppresses learned state;
- no raw domain is added to persisted learning;
- v2/v3 malformed store input cannot crash filtering;
- learning remains bounded under load.

**Suggested PRs:**

9. `refactor/learning-feature-pipeline`
10. `feat/reputation-v3-state-machine`
11. `feat/learning-user-feedback`
12. `privacy/reputation-key-namespace`

---

## Phase 3 - P2 - Scale and Performance

**Goal:** prepare the blocker for much larger deterministic rule sets while protecting battery, startup, and query latency.

### 3.1 Benchmark current baseline before changing structures

Create repeatable benchmark data for:

- 1k rules;
- 25k rules;
- 100k rules;
- 250k rules.

Measure:

- parser/compile time;
- app startup impact;
- matcher lookup latency;
- heap usage;
- per-query allocation;
- APK asset size.

### 3.2 Implement compact matcher selected by benchmark

Likely candidates:

- reversed-label trie;
- packed sorted suffix table;
- generated minimal representation.

Do not select solely by theoretical complexity.

### 3.3 Add negative-result acceleration

If useful after profiling, introduce:

- Bloom prefilter;
- small hot-domain cache;
- immutable generated structures.

Every probabilistic result must be verified before BLOCK.

### 3.4 Optimize CNAME parsing/cache

Do not repeatedly parse the same immutable upstream payload if it is already validated and cached.

Cache classification metadata only in memory and respect DNS TTL.

### 3.5 Battery and allocation acceptance

Set targets from measured baseline, not arbitrary marketing values. For example, a change can be required not to regress p95 classification latency or retained heap by more than an agreed percentage unless it provides measured coverage benefit.

**Suggested PRs:**

13. `bench/domain-matcher-baseline`
14. `perf/compiled-domain-matcher`
15. `perf/dns-response-classification-cache`

---

## Phase 4 - P2/P3 - Advanced DNS Coverage and Bypass Research

**Goal:** close protocol gaps after the core engine is trustworthy.

### 4.1 IPv6 extension headers

Current IPv6 parser expects the immediate next header to be UDP. It does not walk extension headers.

Add a bounded extension-header walker for the types relevant to legitimate DNS traffic. Define an explicit policy for fragments.

Never implement an unbounded linked-header loop.

### 4.2 EDNS0 and larger responses

Review:

- advertised UDP payload size;
- 4096-byte receive-buffer assumption;
- malformed OPT records;
- truncation fallback;
- DNSSEC-related larger payloads.

TCP fallback reduces the need for oversized UDP handling, but the parser should still be correct.

### 4.3 SVCB/HTTPS alias-mode inspection

Extend the response parser only after CNAME is stable.

Add RR-type-specific tests and conservative semantics.

### 4.4 Encrypted DNS research

The present DNS-only split tunnel cannot guarantee blocking when an application uses its own DoH/DoT path.

Possible directions:

#### Direction A - Keep DNS-only scope

- document DoH/DoT bypass honestly;
- retain Firefox canary compatibility;
- avoid architecture expansion.

This best matches current privacy/complexity goals.

#### Direction B - Optional strict mode

A true strict mode may need to route and pass through broader TCP/UDP traffic so that known encrypted-DNS endpoints can be recognized without breaking unrelated traffic.

This is a substantial firewall/forwarder project involving:

- generic packet forwarding;
- TCP state handling or transparent proxying;
- IPv4/IPv6 routing;
- endpoint maintenance;
- QUIC/HTTP3 considerations;
- increased battery and reliability risk.

Do not market the existing canary rule as equivalent to this capability.

### 4.5 Hardcoded IP traffic

DNS filtering cannot block an app that never performs DNS for its ad endpoint. Solving this also requires a broader firewall architecture and is out of scope for the current DNS-only design.

**Suggested PRs:**

16. `feat/ipv6-extension-header-dns`
17. `feat/dns-svcb-https-analysis`
18. `research/encrypted-dns-strict-mode` (design document before code)

---

## Phase 5 - P3 - Quality, CI, Release and Repository Hygiene

### 5.1 Add labeled classifier corpus

Create a deterministic offline corpus with separate positive and negative examples.

Possible layout:

```text
app/src/test/resources/filter-corpus/
    ads.tsv
    trackers.tsv
    benign.tsv
    cname-fixtures/
    provenance.json
```

Each record should include category and source/provenance metadata where licensing permits.

### 5.2 Compute confusion-matrix metrics

For the corpus, calculate:

- true positives;
- false positives;
- true negatives;
- false negatives;
- precision;
- recall;
- false-positive rate;
- false-negative rate.

Do not use these numbers as universal internet effectiveness claims. Label them explicitly as corpus/version metrics.

### 5.3 Add regression gates

Recommended CI policy:

- no increase in corpus false positives without an explicit reviewed exception;
- recall changes must be reported;
- DNS parser fuzz/property tests must pass;
- deterministic generated list checksum must match;
- lint and unit tests required;
- instrumentation job for relevant PR labels or nightly schedule if emulator cost is high.

### 5.4 Expand packet-level tests

Required fixtures:

- valid IPv4/IPv6 UDP checksums;
- compressed CNAME;
- multi-hop CNAME;
- compression pointer loop;
- TC flag and TCP retry;
- mismatched TXID;
- same TXID but mismatched qname;
- mismatched QTYPE/QCLASS;
- NXDOMAIN;
- NODATA;
- A and AAAA;
- EDNS0 OPT;
- malformed record length;
- oversized declared message;
- IPv6 extension header cases after Phase 4.

### 5.5 Add network transition instrumentation

Test:

- Wi-Fi -> mobile handoff;
- resolver replacement;
- temporary no-resolver state;
- IPv6-only or IPv6-preferred network where test infrastructure supports it;
- service restart with active learning;
- boot restart.

### 5.6 Protect `main`

At the reviewed snapshot, `main` is not protected and has no required status checks configured.

Recommended GitHub settings:

- pull request required for main;
- Android checks required;
- stale review dismissal optional;
- force pushes disabled;
- signed commits optional depending on contributor workflow.

### 5.7 Stop storing APK binaries in normal source history

The repository currently contains large APK files under `release/`.

Prefer:

- GitHub Releases for distributable APKs;
- Actions artifacts for CI outputs;
- source repository only for code and small deterministic fixtures.

Update `.gitignore` accordingly for future binaries. Historical blobs remain in Git history unless a deliberate history rewrite is performed; do not rewrite history casually.

### 5.8 Single source of version truth

`app/build.gradle` is at 1.2.2 while `default_config.json` still reports 1.2.0.

Preferred design:

- remove duplicated app version from JSON if unused; or
- generate JSON/build metadata from Gradle version automatically.

### 5.9 Dependency updates after functional stabilization

Dependency modernization is useful but lower priority than filtering correctness. Update AGP/Kotlin/AndroidX incrementally with CI and device verification after Phases 0-2.

---

## 12. Testing Strategy in Detail

## 12.1 Unit tests for rule engine

Test matrices should include:

| Scenario | Expected |
|---|---|
| exact block | block |
| subdomain of suffix block | block |
| similarly named unrelated domain | allow |
| exact exception over suffix block | allow |
| user allow over shipped block | allow |
| user block with filters disabled | block |
| malformed rule | compiler reject |
| IDN normalized form | deterministic documented behavior |

## 12.2 Learning tests with fake monotonic time

Replace timing-by-fast-loop tests with explicit timestamps.

Example sequence:

```text
t=0       observe suspicious domain
 t=100    observe
 t=200    observe
 t=300    observe -> burst evidence

Advance clock by independent-window interval

 t=...    new multi-factor observation -> second confirmation
```

Then assert state transitions exactly.

### Negative cases

- high entropy only -> not confirmed;
- cadence only -> not confirmed;
- one lexical burst -> suspect but not persistent hard block;
- broad CDN domain with weak signal -> allowed;
- user allow -> suppressed immediately;
- clock wall-time changes do not matter because monotonic source is used.

## 12.3 CNAME fixtures

Include:

```text
firstparty.example -> tracker.vendor.test
```

and benign:

```text
www.example -> cdn.example-cdn.test
```

Test:

- deterministic target blocked;
- learned evidence generated only for tracker alias;
- malformed chain does not train anything;
- explicit user allow semantics are respected.

## 12.4 Reputation persistence tests

For v3:

- different keys produce different identifiers;
- separate purpose aliases do not correlate identifiers;
- record validation rejects negative/overflow counters;
- corrupt line ignored safely;
- capacity bound enforced;
- stale weak entries pruned;
- confirmation counters saturate safely;
- concurrent reads/writes remain consistent;
- atomic replacement failure does not take down filtering.

## 12.5 Benchmark tests

Keep benchmarks separate from correctness tests.

Track trends in CI or release verification, but avoid flaky hard timing assertions on generic GitHub-hosted runners. Strong performance gates are better on a controlled emulator/device profile.

---

## 13. Suggested Metrics

Metrics must be local test/release metrics, not user telemetry.

### Classification quality

- corpus precision;
- corpus recall;
- corpus false-positive count;
- corpus false-negative count;
- CNAME-cloaking fixture coverage.

### Protocol correctness

- IPv4/IPv6 packet fixture pass rate;
- malformed-packet fuzz crash count;
- TCP fallback tests;
- resolver-policy tests.

### Performance

- cold compiled-list load time;
- heap after list load;
- p50/p95/p99 deterministic match time;
- p50/p95 full classification time;
- allocation/query;
- VPN forwarding saturation behavior.

### Learning safety

- percentage of unknown positive corpus examples that reach confirmed state after expected evidence;
- number of benign corpus entries that ever reach `CONFIRMED`;
- number of one-shot burst cases incorrectly persisted as confirmed;
- decay/pruning behavior under simulated weeks.

No user browsing data is necessary for any of these measurements.

---

## 14. Migration Plan

## 14.1 Keep Phase 0 schema-free where possible

Protocol and resolver fixes should not touch learning files. This reduces release risk.

## 14.2 Rule-engine migration

For bundled rules:

- keep `ad_domains.txt` readable during transition;
- generate compiled asset at build time;
- compare old/new classification in unit tests;
- remove legacy runtime parser only when equivalence is established.

## 14.3 Reputation v3 migration

Because keyed IDs cannot be mapped to a new key without the raw domain, migration must respect cryptographic reality.

Recommended choices:

### Choice A - Same reputation key for v2 -> v3, new record schema

Pros:

- existing learned IDs can migrate;
- user sees continuity.

Cons:

- does not immediately provide namespace separation from legacy rule files.

This is the least disruptive first migration.

### Choice B - New reputation key and reset learning

Pros:

- cleaner privacy namespace;
- simpler v3 state assumptions.

Cons:

- local learned reputation resets after update.

This is acceptable if clearly communicated because learning is local optimization, not user-authored critical data.

### User allow/block rules

Do not reset them casually. Exact local rules are user intent. Preserve the legacy store/key if necessary, and introduce purpose-separated stores only for new installations until a user-safe migration design exists.

---

## 15. Proposed File-Level Changes

### New files likely needed

```text
app/src/main/java/com/unblocker/app/logic/rules/DnsRule.kt
app/src/main/java/com/unblocker/app/logic/rules/DnsRuleEngine.kt
app/src/main/java/com/unblocker/app/logic/rules/CompiledRuleSet.kt
app/src/main/java/com/unblocker/app/logic/rules/RuleParser.kt

app/src/main/java/com/unblocker/app/logic/dns/InternetChecksum.kt
app/src/main/java/com/unblocker/app/logic/dns/DnsMessageReader.kt
app/src/main/java/com/unblocker/app/logic/dns/DnsResponseMetadata.kt

app/src/main/java/com/unblocker/app/logic/analysis/CadenceTracker.kt
app/src/main/java/com/unblocker/app/logic/analysis/DomainFeatureExtractor.kt
app/src/main/java/com/unblocker/app/logic/analysis/DomainFeatures.kt
app/src/main/java/com/unblocker/app/logic/analysis/ReputationEvidence.kt
app/src/main/java/com/unblocker/app/logic/analysis/ReputationPolicy.kt

app/src/test/resources/filter-corpus/...
tools/filter-compiler/...
```

### Existing files with major changes

```text
AdDetector.kt
ContentFilterEngine.kt
AdaptiveBlockingEngine.kt
LocalNetworkLearner.kt
PrivateReputationStore.kt
DeviceLearning.kt
BlockingDecision.kt
DecideBlockingUseCase.kt
DnsPacket.kt
UnblockerVpnService.kt
TunnelRun.kt
FilteringPreferences.kt
UnblockerScreen.kt
```

### Docs/build

```text
README.md
PRIVACY.md
DATASETS.md
CONTRIBUTING.md
docs/local-privacy-update.md
app/src/main/assets/default_config.json
.github/workflows/android.yml
.github/workflows/release.yml
.gitignore
```

---

## 16. Suggested PR Sequence

Keeping PRs focused will make regression review much easier.

| PR | Branch idea | Scope | Priority |
|---|---|---|---|
| 1 | `fix/dns-resolver-privacy-contract` | remove fallback, resolver-state tests, docs | P0 |
| 2 | `fix/ipv6-udp-checksum` | checksum utility + packet tests | P0 |
| 3 | `fix/dns-response-validation` | question validation/cache safety | P0 |
| 4 | `feat/dns-tcp-fallback` | TC handling + protected TCP transport | P0 |
| 5 | `docs/doh-canary-scope` | correct Firefox canary claims | P0 |
| 6 | `refactor/dns-rule-engine` | exact/suffix/exception engine | P1 |
| 7 | `feat/cname-response-filtering` | response parser + alias blocking | P1 |
| 8 | `build/filter-list-compiler` | provenance/licensing/compiled assets | P1 |
| 9 | `feat/public-suffix-features` | PSL-aware decomposition | P1 |
| 10 | `refactor/learning-feature-pipeline` | pure feature extraction + monotonic cadence | P1 |
| 11 | `feat/reputation-v3` | evidence state machine + new store | P1 |
| 12 | `feat/learning-user-feedback` | allow/block feedback integration | P1 |
| 13 | `test/labeled-filter-corpus` | precision/recall gates | P1/P2 |
| 14 | `bench/domain-matcher` | scale measurements | P2 |
| 15 | `perf/compiled-matcher` | selected compact structure | P2 |
| 16 | `feat/advanced-dns-records` | extension headers/SVCB after core | P2/P3 |
| 17 | `chore/repo-release-hygiene` | version sync, APK handling, CI policy | P3 |

Dependencies:

```text
PR 1-5
  |
  +--> PR 6
         |
         +--> PR 7
         |
         +--> PR 8
         |
         +--> PR 9
                |
                +--> PR 10
                       |
                       +--> PR 11
                              |
                              +--> PR 12

Corpus work can start in parallel after PR 6 interface stabilizes.
Performance work starts after rule representation and corpus gates stabilize.
```

---

## 17. Priority Matrix

### P0 - Must fix before aggressive coverage work

- hardcoded DNS fallback vs privacy documentation;
- IPv6 UDP checksum;
- response question validation;
- TCP fallback for truncated DNS;
- DoH canary documentation/semantics;
- public-resolver route side effects;
- duplicated/stale release metadata.

### P1 - Highest product impact

- typed deterministic rule engine;
- CNAME response-side filtering;
- build-time curated list compiler;
- PSL-aware features;
- evidence-based reputation state machine;
- monotonic cadence tracker;
- pure feature extraction;
- user correction feedback;
- labeled corpus and classifier gates.

### P2 - Scale and advanced correctness

- compact 100k+ matcher;
- benchmarks;
- pruning improvements;
- EDNS0/large response hardening;
- IPv6 extension headers;
- SVCB/HTTPS alias handling.

### P3 - Nice to have / operational

- strict encrypted-DNS research;
- repo binary cleanup;
- dependency modernization;
- branch protection/release process refinements;
- power-user local list import if desired.

---

## 18. What Not To Do

### 18.1 Do not lower the adaptive threshold globally

Lowering 0.76 to something like 0.60 will mostly increase false positives because the feature model was intentionally designed so several weak signals never block by themselves.

Improve evidence quality before changing thresholds.

### 18.2 Do not add hundreds of generic hard-block regex tokens

A hard pattern for every word related to advertising will block legitimate infrastructure.

Use generic words as weighted evidence, not deterministic truth.

### 18.3 Do not store raw DNS history to make learning easier

The current privacy architecture is a differentiator. Aggregate evidence is enough for a much better learner.

### 18.4 Do not add cloud/federated learning without a new product/privacy decision

It would fundamentally change the current promise and threat model.

### 18.5 Do not claim the Firefox canary blocks all DoH

It does not.

### 18.6 Do not use a Bloom filter as the final blocking authority

False positives are inherent. Always verify a positive match.

### 18.7 Do not automatically import huge third-party lists without license review

A technical parser does not solve redistribution and attribution obligations.

### 18.8 Do not solve same-domain ads by pretending DNS can see URLs

DNS cannot distinguish `example.com/content` from `example.com/ad`. Same-host ads require a different layer, such as browser content filtering or TLS/HTTP visibility, which is intentionally outside the current architecture.

---

## 19. Expected Benefits by Phase

### After Phase 0

- reliable IPv6 DNS replies;
- privacy behavior matches documentation;
- fewer mysterious resolver failures;
- correct handling of truncated DNS responses;
- safer upstream response acceptance;
- truthful encrypted-DNS messaging.

### After Phase 1

- significantly better deterministic coverage;
- ability to block CNAME-cloaked trackers;
- cleaner rule semantics;
- safer use of broad lexical patterns;
- reproducible/licensed list expansion.

### After Phase 2

- self-learning based on actual evidence instead of app age;
- fewer one-off false learned blocks;
- model reacts to user corrections;
- typed explainable decisions;
- better testability and maintainability;
- privacy preserved.

### After Phase 3

- ability to ship much larger lists without excessive memory or latency;
- measurable performance envelope;
- more predictable battery/startup behavior.

### After Phase 4/5

- stronger DNS standards compliance;
- clearer limitations around encrypted DNS;
- production-grade quality gates and repository discipline.

---

## 20. Definition of Done for the Overall Program

The improvement program should be considered complete when all of the following are true:

### DNS and transport

- [ ] no undocumented public DNS fallback exists;
- [ ] IPv6 UDP checksums are correct;
- [ ] upstream responses are fully matched to requests;
- [ ] truncated UDP replies use TCP fallback;
- [ ] CNAME chains are safely parsed;
- [ ] malformed DNS cannot crash or train the learner;
- [ ] resolver/network transitions have integration coverage.

### Blocking quality

- [ ] deterministic rules use explicit typed semantics;
- [ ] generic heuristics are not automatic hard blocks;
- [ ] CNAME-cloaked tracker fixtures are blocked;
- [ ] benign CNAME/CDN fixtures are allowed;
- [ ] an offline labeled corpus reports repeatable quality metrics;
- [ ] false-positive regressions are CI-visible.

### Learning

- [ ] learning maturity depends on evidence, not only days installed;
- [ ] unknown domains require independent confirmation before learned hard block;
- [ ] cadence uses monotonic time;
- [ ] feature extraction is pure/single-pass;
- [ ] user allow/block feedback updates learning state;
- [ ] persisted records remain domain-pseudonymous and bounded;
- [ ] stale weak reputation is pruned;
- [ ] reason/category are typed rather than inferred from strings.

### Scale

- [ ] larger rule corpus has measured startup/memory/latency results;
- [ ] final matcher choice is benchmark-driven;
- [ ] probabilistic prefilters never directly block.

### Privacy and docs

- [ ] README, privacy policy, code, and UI describe the same network behavior;
- [ ] no raw query history is persisted;
- [ ] no automatic cloud learning is introduced;
- [ ] dataset sources/licenses are machine-checked;
- [ ] encrypted-DNS limitations are stated accurately.

---

## 21. External Technical References Used for This Review

These references are for protocol/design verification, not for copying datasets into the repository.

1. **Mozilla - Canary domain for `use-application-dns.net`**  
   https://support.mozilla.org/en-US/kb/canary-domain-use-application-dnsnet

2. **RFC 8200 - Internet Protocol, Version 6 (IPv6) Specification**  
   https://www.rfc-editor.org/rfc/rfc8200.html

3. **RFC 7766 - DNS Transport over TCP - Implementation Requirements**  
   https://www.rfc-editor.org/rfc/rfc7766.html

4. **AdGuard CNAME trackers research/list project**  
   https://github.com/AdguardTeam/cname-trackers

5. **AdGuard DNS filtering rule syntax documentation**  
   https://adguard-dns.io/kb/general/dns-filtering-syntax/

6. **Android `VpnService.Builder` documentation**  
   https://developer.android.com/reference/android/net/VpnService.Builder

7. **Public Suffix List**  
   https://publicsuffix.org/

---

## 22. Final Recommendation

The project can materially improve ad and tracker blocking while remaining fully local and privacy-first. The largest gains will not come from making the current regex/threshold system more aggressive. They come from improving the **quality of the evidence available to the blocker**:

1. make DNS transport correct and privacy-consistent;
2. give the engine a proper typed rule system;
3. inspect DNS responses for CNAME tracker aliases;
4. build larger licensed datasets offline at build time;
5. replace age-based "learning" with evidence-confirmed reputation;
6. incorporate explicit user corrections;
7. measure every change against a labeled negative/positive corpus;
8. optimize data structures only after coverage and rule semantics stabilize.

This sequence increases coverage while protecting the project's strongest differentiators: local operation, explainability, user control, and no browsing-history collection.

