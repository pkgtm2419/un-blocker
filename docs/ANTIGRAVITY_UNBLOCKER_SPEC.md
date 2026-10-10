# ANTIGRAVITY TASK SPEC — Un-Blocker: Ad-Block Coverage, Security Hardening, Production Readiness

> **Addendum 2026-10-10 (CI / GitHub Actions):** the status of S-1…S-4 in this plan is superseded by **Phase 3 §7A** (22 CI/repo findings) and the validated `ci-fixed/` bundle. Notably S-4 "Gradle dependency verification" has **regressed** (metadata deleted, verification switched off), S-1 "production release flow" has never succeeded in practice, and the hygiene guard from S-3 does not catch build outputs.

> **Addendum 2026-10-09 (filter lists):** the source table in §4 A.1 is **superseded by Phase 3 §6A (WP-7)**. Measured: the app build uses only StevenBlack (MIT) + project seeds; **OISD is GPL-3.0**, uBlock/AdGuard lists are GPL-3.0, EasyList/EasyPrivacy are GPL-3.0-or-later OR CC BY-SA 3.0-or-later; 1Hosts Lite is MPL-2.0 (73 k → 262 k domains with 1Hosts). Only permissive lists are bundled; copyleft lists are handled by the optional on-device list catalogue (owner decision). Browser-only lists (cosmetic, cookie, anti-adblock) are not applicable to DNS.

| | |
|---|---|
| Repo | `pkgtm2419/un-blocker`, branch `main` |
| Audited commit | `fbd2422` — "feat: implement DNS correctness, evidence-based learning, and rule compiler" |
| App at audit | `1.3.0` (versionCode 6), compileSdk/targetSdk 36, minSdk 26, package `com.unblocker.app` |
| Audit date | 2026-10-04 |
| Method | Static read of all `app/src/main` Kotlin, manifest, Gradle, CI workflows and the Python rule compiler. Ran the Python compiler tests (7 pass). Fetched and measured candidate blocklists. |
| **Not done** | The APK was **not built** and **not run on a device**. Every runtime/behavioural claim that depends on Android is tagged **[verify]**: write a failing test or run a probe first, and record the result in the PR description. |

---

## 0. Read this first

### 0.1 TL;DR — what to do, in order

1. **PR-1** CI/release/repo hygiene: a `v*` tag would publish a *debug* APK as a normal release; APKs are tracked in git; workflow injection/supply-chain gaps (S-1…S-4).
2. **PR-2** Safety net before coverage: a compile-time *never-block* gate + shipped allow rules; stop whole-domain blocking of vendor sites; fix adult-regex false positives; stop lexical matching on the registrable label (A.4, A.5, A.6, C-5).
3. **PR-3** Close the *persistent poisoning* path: CNAME data from plaintext upstream DNS can create 30-day persistent blocks (S-5).
4. **PR-4** Stop the DNS hot path from holding a global lock and rewriting+fsync'ing a file per update (R-2).
5. **PR-5** The main coverage fix: the app ships **937** ad domains; mainstream DNS blockers ship **tens/hundreds of thousands**. Vendor pinned, license-gated lists; compact binary rule format; shared matcher (A.1–A.3).
6. **PR-6** Serve DNS-over-TCP on the tunnel (R-1) **[verify first]**.
7. **PR-7** Private-DNS / DoH bypass detection + UI warning (A.7).
8. **PR-8** Cleanup/deps (Q-*).

### 0.2 Rules of engagement

- One PR per workstream above. Each PR must pass, from repo root:
  - `bash gradlew testDebugUnitTest lintDebug assembleDebug`
  - `cd tools/filter-compiler && python3 -m unittest test_compiler test_release`
  - plus `connectedDebugAndroidTest` when touching VPN, preferences, Keystore, or manifest.
- **No fix is done without a test that fails before and passes after.** Where the spec says *[verify]*, the first commit is the failing test/probe.
- Keep diffs minimal. Many source files use **CRLF** line endings — do not reformat or re-encode whole files.
- If this spec conflicts with the repository's own `README.md`, `PRIVACY.md`, `DATASETS.md`, or `UNBLOCKER_IMPROVEMENT_PLAN.md`, **stop and ask the owner** — do not silently pick one.
- Anything marked **OWNER DECISION** (§9) must be raised as an issue/question, not decided by you.

### 0.3 Invariants — never violate (from README/PRIVACY/plan §4)

- No telemetry, remote models, uploaded domains, traffic logs, automatic accounts.
- **No hidden resolver fallbacks.** Upstream = Android-configured resolvers only (`DnsResolverPolicy`, `ResolverRegistry`). Any new upstream option must be explicit and user-chosen.
- Installed app **never downloads list updates**; lists are bundled at build time (`DATASETS.md`).
- Persisted learning stays HMAC-keyed (Keystore), in `noBackupFilesDir`, no raw domains on disk, no plaintext fallback.
- User exceptions (`ALLOW`) beat every shipped/learned block.
- Every new dataset/dependency documents license + provenance (pinned revision + SHA-256).
- No unmeasured effectiveness percentages anywhere (README:98, plan §4.4).

### 0.4 Explicit non-goals (do NOT implement)

TLS interception / local CA certificate; cloud sync or analytics; federated learning; remote or TFLite models; "certificate fingerprint" signals (the app is a **DNS-only** VPN — it never sees TLS handshakes, so this is impossible); any "98%" style claims.

### 0.5 Stale documents — do not follow

Earlier AI-generated files that may be lying around (`UN-BLOCKER-LEVEL1-INDEX.md`, `un-blocker-status-summary.md`, `un-blocker-implementation-audit-and-checklist.md`, `un-blocker-level1-implementation-guide.md`, and the older "Level 1–4 / 98.5% / LearnedDomainStore.kt / CertificateChecker.kt / TFLite / federated learning" plans) describe an architecture **this repo does not have** (wrong paths, wrong package, non-existent classes, fabricated certificate hashes, unmeasured metrics). **The repository wins. Ignore those files.**

---

## 1. What the app actually is (verified by reading the code)

```
TUN  10.10.0.2/32 + fd00:1::2/128; DNS = 10.10.0.1 / fd00:1::1; routes ONLY those two /32,/128
  │  UnblockerVpnService.runVpnLoop (services/UnblockerVpnService.kt)
  ▼
DnsPacketUtil.parseIpPacket  (logic/dns/DnsPacket.kt)  — UDP/53 queries only; strict bounds; EDNS validated
  ▼
ContentFilterEngine.analyzeAndFilter → DecideBlockingUseCase
   order: user ALLOW → user BLOCK → shipped static rules (AdDetector/DnsRuleEngine)
          → adaptive learner (AdaptiveBlockingEngine → LocalNetworkLearner → ReputationPolicy)
          → adult filter (AdultContentDetector)
  ▼
blocked:  synthetic 0.0.0.0 / :: (A/AAAA, TTL 300); NODATA for other types; NXDOMAIN for use-application-dns.net
allowed:  DnsCache → DnsUpstreamClient (UDP, TCP on TC; sockets protect()ed; random ephemeral ports)
          → DnsResponseValidator (txid, qname, qtype, qclass, compression-loop bounds, CNAME chain ≤8)
          → CNAME re-check (CnamePolicy) → deliver + cache
```

### 1.1 Preserve (high quality — do not regress)

Strict, bounded DNS parsing and response validation; per-run resource ownership (`TunnelRun`, session owner checks); bounded forwarding pool (4 threads / queue 64); resolver epoch snapshots (`ResolverRegistry`); IPv6 UDP checksum; atomic file writes; HMAC-only persistence with distinct keystore aliases; no logging anywhere in `app/src/main` (grep for `Log.`/`println`/`printStackTrace` returns nothing); `allowBackup=false` + data-extraction rules; non-exported service/receiver; `PendingIntent.FLAG_IMMUTABLE`; license/sha256-gated offline compiler; deterministic rule output; 40+ unit test files.

**Static review found no memory-safety, injection, or exported-component vulnerability in the app code.** The real security issues are in the release pipeline, in *persistence of attacker-influenced data*, and in availability/performance — listed below.

---

## 2. The "Google DNS blocks ads better" observation

**Fact:** Google Public DNS (`8.8.8.8` / `dns.google`) does **not** block or filter ads — Google states it never blocks, filters or redirects. So the better result you saw was caused by something else. Ranked hypotheses to test (do **T0** before changing code):

| # | Hypothesis | How to test |
|---|---|---|
| H1 | **List size gap** (937 seeds vs 72k–203k in common lists) | §2.1 numbers; run the same hostnames through both (§6) |
| H2 | **Bypass of the tunnel** (Private DNS strict hostname, browser "secure DNS" with a custom provider, apps with hard-coded resolvers) so queries never reach Un-Blocker **[verify]** | Test matrix in A.7.1 |
| H3 | The "Google DNS" run actually used an **ad-blocking resolver** (AdGuard/NextDNS/Quad9 etc.) or a browser's built-in blocking | Ask owner which exact setting was used |
| H4 | **First-party ads** (YouTube, in-feed ads on the same hostname as content) — *no* DNS blocker can stop these | Document as a known limit in README |

### 2.1 Measured coverage gap (measured 2026-10-04)

| Source | Count | Notes |
|---|---|---|
| `app/src/main/assets/ad_domains.txt` | **937** domains (940 lines incl. 3 comments) | compiles to a 31,925-byte `dns-rules.tsv` |
| `adult_domains.txt` | 17 domains | |
| StevenBlack/hosts `hosts` | **72,233** unique domains (file header dated 2026-10-02) | MIT |
| badmojr/1Hosts `Lite/domains.txt` | **202,954** rules (header dated 2026-09-03) | MPL-2.0 |
| Overlap | only **426 / 937** seeds appear verbatim in StevenBlack | seeds contain unique streaming/popunder families → **keep seeds, add bulk lists on top** |

### 2.2 T0 — baseline before any change

Create `docs/coverage-baseline.md` with: commit hash, list revisions, the labeled corpus used, and recall / false-positive counts **for that corpus only** (see §6). Do not publish a bare percentage.

---

## 3. Findings register

Severity: **High** = ship-blocker or misleading release · **Medium** = real defect, bounded impact · **Low** = hygiene.

| ID | Sev | Title | Evidence |
|---|---|---|---|
| S-1 | **High (latent)** | A `v*` tag publishes a **debug** APK as a normal (non-prerelease) release; missing secrets silently fall back to debug signing | `.github/workflows/release.yml:69` (`apk/debug/…`), `:94` (`assembleDebug`), `:83` (fallback), `:50` ("Release build for…") |
| S-2 | Medium | Release build not minified/shrunk; proguard rules reference Room (unused) | `app/build.gradle:62` `minifyEnabled false`; `app/proguard-rules.pro:4-5` |
| S-3 | Medium | APKs tracked in git, incl. a **debug** build: `release/ub-blocker-1.2.2.apk`, `release/unblocker-debug.apk` (both 18,821,289 bytes — identical size), `release/unblocker-v1.0.0.apk` (19,061,456). `.gitignore` already lists `*.apk`, so they were force-added; history also had `release/ub-blocker-1.2.1.apk` | `git ls-files release/` |
| S-4 | Medium | Workflow injection + supply-chain gaps: `${{ inputs.tag_name }}` interpolated into shell; actions pinned to tags not SHAs; no wrapper validation, no Dependabot, no Gradle dependency verification | `release.yml:37,38,58` |
| S-5 | Medium | **Persistent poisoning**: CNAME data from a *plaintext* upstream response creates a CONFIRMED learned block (30-day retention) for the *original* hostname | `ContentFilterEngine.kt:39` → `LocalNetworkLearner.kt:42` → `ReputationPolicy.kt:29` (`trustedAlias`) → `PrivateEvidenceStore.kt:47,68` |
| S-6 | Low | Upstream DNS is plaintext only (no integrity/confidentiality); no optional encrypted upstream | `DnsUpstreamClient.kt` |
| R-1 | Medium **[verify]** | Client DNS-over-TCP is not served: TCP packets to the tunnel DNS IP are dropped, yet oversized answers are truncated with TC, which makes clients retry over TCP | `DnsPacket.kt:100` (`protocol != 17 → null`), `:245-253` (synthetic TC) |
| R-2 | Medium | TUN loop holds global `lifecycleLock` around classification; classification may rewrite + `fsync` the whole evidence file; `stopVpn` takes the same lock and is reachable from the main thread → stall/ANR risk | `UnblockerVpnService.kt:192-195`, `:98-101`, `:351-352`; `PrivateEvidenceStore.kt:68,78-92` (`fd.sync()` at 88) |
| R-3 | Low | Unparseable/odd queries are dropped silently → client waits for timeout | `UnblockerVpnService.kt:189` |
| R-4 | Low | `check(prefs…commit())` can throw on the STOP/revoke path | `FilteringPreferences.kt:35-36`, called at `UnblockerVpnService.kt:99,391` |
| R-5 | Low | Health check re-creates a full `ContentFilterEngine` (re-reads `dns-rules.tsv`) every run; its report is never surfaced | `HealthCheckService.kt:20`, `AdDetector.kt:15`, `HealthCheckWorker.kt:20` |
| C-1 | **Medium (main ask)** | Only 937 ad domains bundled | §2.1 |
| C-2 | Medium | Whole-domain blocking of vendor sites/dashboards; hard-coded duplicate tracker set in the learner | `LocalNetworkLearner.kt:18`; apex entries in `ad_domains.txt` (`amplitude.com`, `appsflyer.com`, `branch.io`, …) |
| C-3 | Medium **[verify]** | No handling/detection of Private DNS, browser secure-DNS, hard-coded resolvers | grep for `isPrivateDnsActive`/DoH hosts: none |
| C-4 | Low–Med | Lexical token test runs on **every** label including the registrable label / public suffix | `DomainFeatureExtractor.kt:10` |
| C-5 | Medium | Adult regex tier blocks legitimate sites (default adult filter is ON: `default_config.json`) | `AdultContentDetector.kt:28-36`; probe in Appendix B |
| Q-1 | Low | Manifest: unused `CHANGE_NETWORK_STATE`; no explicit `usesCleartextTraffic=false`; no predictive-back opt-in for targetSdk 36 | `AndroidManifest.xml:7` |
| Q-2 | Low | Dead/legacy code: KSP plugin, `AdDetector.addDomain`, vestigial learning-day API, legacy `PrivateReputationStore` | §5 Q-2 |
| Q-3 | Info | Dependency versions likely behind (AGP 8.10.1, Kotlin 2.0.21, Compose BOM 2024.10.00, core-ktx 1.13.1, …) | `build.gradle`, `app/build.gradle` |

---

## 4. Workstream A — Coverage (the main ask)

### A.0 Principles

Offline at runtime; deterministic builds; every list vendored at a **pinned commit** with SHA-256 + license; **allow beats block**; breakage is caught at *compile time*; broad regexes/heuristics never hard-block on their own (plan §6.2).

### A.1 Candidate sources (licenses read from each repo's LICENSE file on 2026-10-04)

| Source | License | Size | Compiler gate today (`compiler.py: LICENSES = {'Apache-2.0','MPL-2.0'}`) | Recommendation |
|---|---|---|---|---|
| StevenBlack/hosts (`hosts`) | **MIT** | 72,233 domains | `MIT` not allowed → add | **Primary** (conservative, widely used) |
| badmojr/1Hosts `Lite/domains.txt` | **MPL-2.0** | 202,954 rules | already allowed (PSL is MPL-2.0 too) | Optional second tier; measure breakage + memory first |
| hagezi/dns-blocklists | **GPL-3.0** | — | not allowed | **Do not bundle** without OWNER/legal decision (GPL-3.0 vs the app's Apache-2.0) |
| AdguardTeam/AdGuardSDNSFilter | **GPL-3.0** | — | not allowed | **Do not bundle** (same) |

Caveats you must handle: I verified **top-level** license files only. StevenBlack's unified list is itself a merge of upstream lists, and 1Hosts credits upstream sources — **read each upstream's license at the pinned revision and record it in `THIRD_PARTY_NOTICES.md`**; if any upstream is incompatible, use a subset or another list. This is not legal advice — OWNER confirms.

### A.2 Vendoring + compiler changes

1. **Maintainer-run vendoring script** (never run at build/runtime): `tools/filter-compiler/vendor.py` downloads a list **at a 40-hex commit SHA** (never `master`), normalizes CRLF→LF, writes `tools/filter-compiler/vendor/<name>/<sha>/list.txt` plus the upstream `LICENSE`, and prints the SHA-256 to paste into `sources.json`. Do **not** use Git LFS (breaks offline reproducibility).
2. `sources.json` entry shape (existing fields; `syntax` is new):
   ```json
   {"id": 4, "path": "tools/filter-compiler/vendor/stevenblack/<commit>/list.txt",
    "license": "MIT", "revision": "<40-hex commit>", "url": "https://github.com/StevenBlack/hosts",
    "sha256": "<canonical LF bytes sha256>", "syntax": "hosts", "category": "AD"}
   ```
3. `tools/filter-compiler/compiler.py`:
   - `LICENSES` += `'MIT'` (keep GPL variants rejected).
   - New syntaxes: `hosts` (`0.0.0.0 host` / `127.0.0.1 host`; ignore inline `#` comments) and `domains` (one host per line).
   - Drop loopback/meta names (`localhost`, `localhost.localdomain`, `local`, `broadcasthost`, `ip6-*`, `0.0.0.0`).
   - **Invalid-line policy for bulk lists:** count and skip invalid lines, but **fail the build if invalid lines exceed 0.1 %** (proposed threshold) or if any invalid line looks like a rule (so format drift is noticed). Keep strict raising for the project's own seed files.
   - Apply allow rules + the never-block gate (A.4) **before** emitting output.
   - Deterministic output; manifest gains per-source `ruleCount`, `skippedInvalid`, `sha256` of each emitted artifact.
4. `test_compiler.py` additions: hosts parsing; loopback exclusion; invalid-ratio failure; GPL-3.0 rejected; checksum mismatch rejected; deterministic bytes across two runs; never-block gate triggers with a readable report (`rule → source id → matched host`).

### A.3 Scalable runtime matcher + format

**Problem (verified by reading):** `AdDetector.loadStaticList()` (`AdDetector.kt:15`) parses the whole TSV into `DnsRule` objects (`DnsRule.kt:19 fromTsv`, plus `distinct()`), copies into a `LinkedHashSet`, then `DnsRuleEngine` builds `groupBy` maps of more objects. At 100k–300k rules this is several object graphs per rule (my estimate: well over 100 bytes/rule/copy — **[measure]**). It also runs on **every** `ContentFilterEngine` construction: VPN start *and* every 6-hour health check (`HealthCheckService.kt:20`).

**Spec:**
- New immutable `RuleSet` loaded from a compact `dns-rules.bin` (format v2) produced by the compiler; keep `dns-rules.tsv` for tests/debugging.
- Load via `AssetManager.openFd` (requires `androidResources { noCompress += "bin" }`) and map/read into one `ByteBuffer`/`ByteArray`. **Never on the main thread** (the VPN worker already loads off-main — keep that).
- Suggested layout (little-endian): header `magic "UBR2"`, `version u16`, `ruleCount u32`, `blobLen u32`; then `u32 offsets[ruleCount]` sorted by key; then blob entries `keyLen u8 | key bytes (reversed-label, e.g. "net.doubleclick.ad") | flags u8 (action, kind, category) | sourceId u16`. Reversed labels turn suffix matching into prefix matching on a sorted table.
- **Semantics must be identical to the current `DnsRuleEngine.match`:** ALLOW checked before BLOCK; `EXACT` only the full name; `SUFFIX` at the name or any parent; `WILDCARD` only strict parents. Keep `DnsRuleEngine` as the **oracle** in a differential test (random + corpus domains; 100 % agreement) and delete it only after that passes.
- One process-wide `RuleSetHolder` (volatile, lazy, thread-safe) used by the VPN service **and** `HealthCheckService`; `AdDetector` receives a `RuleSet` instead of reloading assets. Remove `AdDetector.addDomain` (no production callers) or reduce it to a small overlay.
- `PackedRuleMatcher` is currently **test-only** (`MatcherBenchmarkTest`, `PackedMatcherTest`): replace with the new `RuleSet`, then delete.
- **Proposed acceptance targets (tune after measuring; do not publish as claims):** retained heap for 300k rules ≤ 16 MB; cold load ≤ 400 ms on a background thread; JVM-benchmark p95 lookup ≤ 25 µs; `match()` allocates at most one reversed-host buffer.

### A.4 Breakage guard — must land BEFORE large lists

- `tools/filter-compiler/never_block.txt`: hosts that must never be matched by any BLOCK rule (the host itself and its listed subdomains). Compiler **fails** with a report if any BLOCK rule from any source matches one. The same file feeds `app/src/test/resources/never-block.tsv`, and a Kotlin unit test asserts the **compiled asset** also allows them (guards the Kotlin matcher too).
- Starter set (examples of widely needed infrastructure; **verify each and extend**; the maintainer adds the payment/banking/government services they personally depend on):
  `connectivitycheck.gstatic.com`, `connectivitycheck.android.com`, `clients3.google.com`, `captive.apple.com`, `www.msftconnecttest.com`, `detectportal.firefox.com`, `time.android.com`, `time.google.com`, `google.com`, `www.google.com`, `accounts.google.com`, `gstatic.com`, `www.gstatic.com`, `googleapis.com`, `play.google.com`, `mtalk.google.com`, `apple.com`, `icloud.com`, `microsoft.com`, `login.microsoftonline.com`, `github.com`, `api.github.com`, `raw.githubusercontent.com`, `registry.npmjs.org`, `pypi.org`, `cdnjs.cloudflare.com`, `cdn.jsdelivr.net`, `fonts.googleapis.com`, `cloudflare.com`, `wikipedia.org`, `whatsapp.com`, `whatsapp.net`, `telegram.org`, `signal.org`, `paypal.com`, `amazon.com`.
- `tools/filter-compiler/allow.txt` as an extra source (`@@||host^` already parses): every entry carries a `#` rationale comment.

### A.5 Over-blocking of vendor sites (C-2)

**Evidence:** `ad_domains.txt` contains apex entries such as `amplitude.com`, `appsflyer.com`, `applovin.com`, `branch.io`; `LocalNetworkLearner.kt:18` hard-codes a second set (`google-analytics.com, adjust.com, appsflyer.com, branch.io, kochava.com, flurry.com, singular.net, braze.com, mixpanel.com, segment.com, segment.io, amplitude.com, scorecardresearch.com, quantserve.com, moatads.com, clarity.ms, hotjar.com, newrelic.com, app-measurement.com`). Matching is suffix-based, so marketing sites, docs and logged-in dashboards on the same registrable domain are blocked too.

**Spec:**
1. Delete `knownTrackers` from the learner; `known(domain)` becomes `ruleSet.match(domain)?.action == BLOCK` (single source of truth; allow rules then win automatically).
2. For each apex that is both a vendor site and a tracker host: convert to **endpoint-level** BLOCK rules plus ALLOW `EXACT` for the apex and `www.` (and documented dashboard/docs hosts). **Take endpoint hostnames from the vendor's public SDK docs — do not guess.** Put a table in the PR: vendor → blocked endpoints → allowed hosts → doc URL.
3. After A.2 lands, run a `tools/filter-compiler/diff_seed_vs_bulk.py` to list seeds **not** covered by the bulk lists; keep only those (seeds ≠ redundant: 511 of 937 are not verbatim in StevenBlack).
4. Tests: dashboard/doc hosts allowed; SDK endpoints blocked; user ALLOW/BLOCK still override.

### A.6 Heuristic ("pattern") tier — safer, not broader

- **C-4 fix:** `DomainFeatureExtractor.kt:10` tests tokens against every label of the full name. Restrict lexical matching to labels **left of the registrable domain** (use `PublicSuffixRules.subdomain`), so a site whose registrable label happens to be a token (e.g. `ad.<ccTLD>`) cannot score lexical. [verify with unit tests using such names]
- Tokens remain *evidence bits*, never a hard block; keep the existing rule that lexical alone never confirms (`HeuristicScorer`: lexical + at least one other signal; `ReputationPolicy`: ≥2 spaced windows).
- Add tokens conservatively and only with corpus evidence: `adserv(er|ice)`, `adsystem`, `adtech`, `pagead`, `rtb`, `ssp`, `dsp`, `sdk-api`. Each addition needs a must-allow corpus run showing **zero** new false positives.
- Extend `app/src/test/resources/classifier-corpus.tsv` with labeled rows from §6; CI gate = **0 false positives** on the must-allow corpus; recall is reported, not gated.

### A.7 Encrypted-DNS / bypass handling (C-3) — default-safe, explicit UI

All behaviours below need a device test matrix recorded in `docs/dns-bypass-matrix.md` (Android version, device, setting, result).

- **A.7.1 Detection + warning (do this one).** The service already registers a `NetworkCallback`. On API 28+ read `LinkProperties.isPrivateDnsActive` / `privateDnsServerName` for the active underlying network and expose a `StateFlow` to the UI. Show a banner such as "Private DNS is on — some DNS may bypass Un-Blocker" with a button to open network settings (`Settings.ACTION_WIRELESS_SETTINGS`; there is no guaranteed deep link to the Private DNS page). **[verify]** my expectation: *Automatic* mode falls back to cleartext to `10.10.0.1` (captured); *strict hostname* mode bypasses the tunnel DNS. Do not ship UI wording that asserts this until the matrix confirms it.
- **A.7.2 Optional toggle "Block encrypted-DNS bootstrap hosts" (default OFF, P3).** NXDOMAIN for a short curated list of public DoH/DoT provider hostnames (**verify each from the provider's documentation — do not trust any list from memory**). UI must warn that a browser explicitly configured with a custom secure-DNS provider can stop resolving. **[verify]** Chrome's automatic secure-DNS upgrade appears to key off the system resolver; with `10.10.0.1` it may not upgrade at all — if confirmed, this toggle has little value; skip it. Keep the existing `use-application-dns.net` NXDOMAIN as is.
- **A.7.3 Capturing hard-coded public resolvers (P3, research, default OFF, do not start before A.1–A.4 and S-*):** add `/32` routes for a few public resolver IPs, answer their UDP/53 locally, drop everything else to those IPs. Plan §5.6 warns this can blackhole unrelated traffic (e.g. DoH-by-IP, DoT). Explicit opt-in with warning only. OWNER DECISION.

---

## 5. Workstream S/R/C/Q — Security, reliability, quality fixes

### S-1 Release pipeline publishes debug builds (do first)

- Add a `release` `signingConfig` fed by **separate** secrets `UB_RELEASE_KEYSTORE_FILE|PASSWORD`, `UB_RELEASE_KEY_ALIAS|PASSWORD` (not the existing *test* keystore). Remove the silent fallback.
- For `v*` tags: `assembleRelease` (R8), then **fail** if secrets are missing; verify the signer SHA-256 against a repo variable (`scripts/verify-apk-signature.sh` already exists); publish `ub-blocker-<ver>.apk`, `SHA256SUMS`, and `mapping.txt`. Add a hard assertion that the APK is **not** debuggable, e.g. `"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging app.apk | grep -q application-debuggable && exit 1`.
- `test-v*` tags keep today's behaviour but must **always** be `--prerelease` and labelled test builds.
- Optional: build-provenance attestation (`actions/attest-build-provenance`).
- Fix README's download badge (currently points at `test-v1.2.2`) once a signed release exists.
- **Acceptance:** a dry-run on a fork shows (1) missing secrets ⇒ job fails, (2) APK non-debuggable, (3) signer equals the pinned digest.
- Not code (owner): Play Console VPN-service declaration and the `specialUse` foreground-service justification text.

### S-2 Minify/shrink

- `minifyEnabled true`, `shrinkResources true`; replace `proguard-rules.pro` (Room rules are dead) with only keeps you can justify.
- Add a `releaseTest` build type (initWith release, debug-signed) and run `connectedAndroidTest` against it so tests exercise R8'd code. Verify `HealthCheckWorker` (instantiated reflectively by WorkManager) and the Keystore HMAC paths survive R8.
- Consider replacing `material-icons-extended` with the few icons actually used. **Record APK size before/after in the PR** (tracked 1.2.2 APK is 18,821,289 bytes).

### S-3 Tracked binaries

- `git rm --cached release/*.apk`; add `scripts/check-no-binaries.sh` (`git ls-files | grep -E '\.(apk|aab|jks|keystore|p12)$' && exit 1`) as a CI step.
- **OWNER DECISION — history purge:** `git filter-repo --path release/ --invert-paths` rewrites history and diverges every fork/clone. Do **not** run it; open an issue with the exact commands. GitHub Release assets are unaffected.

### S-4 CI supply chain

- Move `${{ inputs.tag_name }}` into `env: INPUT_TAG: ${{ inputs.tag_name }}`, reference `"$INPUT_TAG"`, and validate with `^(v|test-v)[0-9]+\.[0-9]+\.[0-9]+$` before use.
- Pin all third-party actions to commit SHAs (resolve from each action's official repo at the time; keep a `# vX.Y.Z` comment) and add `.github/dependabot.yml` for `gradle` and `github-actions`.
- Add `gradle/actions/wrapper-validation`; generate `gradle/verification-metadata.xml` (`./gradlew --write-verification-metadata sha256 help`) and commit it.
- Least-privilege `permissions:` (publish step/job only gets `contents: write`); add a `concurrency` group for releases; drop `--clobber` re-uploads or restrict to `workflow_dispatch`.

### S-5 Persistent poisoning from forged CNAMEs (**Medium**)

**Chain (verified by reading):** upstream response (plaintext UDP/TCP) → `DnsResponseValidator` extracts the CNAME chain → `ContentFilterEngine.blockedAlias` (`:39`) sees a blocked alias → calls `networkLearner.observeTrustedAlias(original)` → `ReputationPolicy.trustedAlias` writes `score .98, mask 16, CONFIRMED` → `PrivateEvidenceStore.put` persists it (CONFIRMED retention = 30 days, `:47`). Later queries for `original` are blocked by the learner path even when the upstream is honest.

**Attack:** a hostile Wi-Fi/ISP/carrier resolver (on-path; an off-path attacker would need the txid + random source port) answers a query for a real hostname with `CNAME → <known tracker>`. Result: that hostname is blocked on every network for up to 30 days until the user adds an exception.

**Fix:**
1. `blockedAlias` must **not** persist. Keep alias blocking per-response (already done in `resolveAndForward` and on cache hits). If a learned record is wanted for efficiency, keep it **volatile** (bounded LRU, in-memory, TTL ≤ the DNS TTL).
2. If persistence is kept at all, require ≥3 alias observations on ≥2 distinct `dayBucket`s, never for hosts covered by the never-block list (A.4) or critical registrable domains, and cap new persistent CONFIRMED records per day.
3. **Tests:** (a) feed a forged `CNAME→doubleclick.net` for `accounts.google.com` 100× → assert **no** CONFIRMED record exists and the next *clean* response is allowed; (b) genuine first-party CNAME cloaking is still blocked per-response; (c) existing persistence/decay tests still pass.

### S-6 Optional encrypted upstream (P2, OWNER DECISION first)

`UpstreamMode { SYSTEM (default) | DOT }`: explicit user choice, provider presets with documented bootstrap IPs, TLS via `SSLSocketFactory` with hostname verification (`SSLParameters.endpointIdentificationAlgorithm = "HTTPS"`), `protect()` before connect, ≤4 in-flight, strict timeouts, responses validated by the existing `DnsResponseValidator`, **no silent downgrade to plaintext** unless the user ticks an explicit "allow fallback". Update `PRIVACY.md`. Tests with a local TLS server fixture.

### R-1 Serve DNS-over-TCP on the tunnel **[verify first]**

**Why:** `parseIpPacket` returns null for any protocol other than UDP (`DnsPacket.kt:100`) and the loop `continue`s. `wrapClientResponse` replaces oversized answers with a TC (truncated) reply (`:245-253`), and Android's stub resolver follows TC with a TCP query to the same DNS IP — which is dropped. **Verify** with a probe (e.g. Termux `dig +tcp @10.10.0.1 example.com`, and a name whose answer exceeds the client's UDP limit without EDNS).

**Spec:** minimal user-space TCP DNS responder for `10.10.0.1:53` and `[fd00:1::1]:53`: SYN→SYN-ACK (random ISN), accept one length-prefixed query per connection, run the same `analyzeAndFilter` + upstream path, reply PSH/ACK with a 2-byte length, handle FIN/RST. Hard bounds: ≤32 concurrent connections, ≤64 KB buffer each, 5 s idle timeout, drop out-of-order segments (clients retransmit), RST on anything unexpected. Needs new TCP checksum helpers (v4 + v6) next to `InternetChecksum`.
**Tests:** synthetic packet exchanges (handshake, query, response, FIN, RST, garbage, SYN-flood bound), plus an instrumented probe.

### R-2 Hot path: lock scope and persistence cost

- Do **not** hold `lifecycleLock` while classifying (`UnblockerVpnService.kt:192-195`); use the run-owned `running` flag. Keep the lock for establish/teardown only.
- `PrivateEvidenceStore.put` currently rewrites the whole file and `fd.sync()`s on each change (`:68`, `:78-92`). Make it **write-behind**: mark dirty; one single-thread writer flushes at most every ~15–30 s and on stop; snapshot under the store lock, write **outside** it; `fsync` only on flush. Keep atomic temp-file+move.
- **Tests:** burst of 10 000 unique positive-evidence domains must not block the packet loop (assert p99 classification latency below a threshold you measure and then fix, e.g. 5 ms); `stop` during a flush completes promptly; crash-consistency (kill mid-flush leaves the old file valid).

### R-3 / R-4 / R-5 small fixes

- R-3: for queries with an intact 12-byte header that fail validation, answer **FORMERR** (echo txid; opcode≠QUERY → NOTIMP) instead of silently dropping; keep dropping <12-byte garbage; rate-limit.
- R-4: wrap `setProtectionEnabled` calls on the STOP/revoke path in `runCatching` and fall back to `apply()`; never let a prefs failure crash the service.
- R-5: `HealthCheckService` must use the shared `RuleSetHolder`; make the report real (service status, resolver count, last successful upstream response time) or delete the unused fields (`memoryUsageMb`).

### C-5 Adult regex false positives

**Evidence (Appendix B reproduces it; the 7 regex strings from `AdultContentDetector.kt:28-36` run with Python `re.fullmatch`, same semantics as Kotlin `Regex.matches`):** blocked → `cam.ac.uk`, `www.cam.ac.uk`, `adult.education.gov.au`, `adult-learning.org`, `comic-strip.com`, `strip-mall.example.com`, `sex-ed.example.org`, `fap.rs`; not blocked → `stripe.com`, `adultswim.com`, `sextant.com`, `sexualhealth.org`, `essex.ac.uk`. Default adult filtering is **on**.
**Fix:** evaluate patterns only on the registrable label + subdomain labels (PSL), never on public-suffix labels; skip entirely for institutional suffixes (`edu`, `ac.*`, `edu.*`, `gov`, `gov.*`, `mil`); demote weak standalone tokens (`cam`, `cams`, `strip`, `adult`, `fap`, `sex`) from hard block to learner evidence (or require a strong co-token); keep strong tokens. Prefer a vetted adult list through the same compiler (category `ADULT_CONTENT` is already supported) over regex coverage.
**Tests:** must-allow corpus (≥50 domains: universities, health education, retail words) → zero blocks; must-block corpus (strong tokens + listed domains) → blocks.

### Q-1 Manifest (Android 16 / least privilege)

Remove `CHANGE_NETWORK_STATE` (`AndroidManifest.xml:7`; no `requestNetwork`/`bindProcessToNetwork` callers — grep verified). Add `android:usesCleartextTraffic="false"` (the app makes no HTTP calls) and `android:enableOnBackInvokedCallback="true"`; check Compose back handling.

### Q-2 Dead / legacy code (confirm with grep before deleting)

KSP plugin declared in root `build.gradle:10,18` but unused; `AdDetector.addDomain` (no production callers); `FilteringPreferences.getDaysSinceInstall/learningStartTime/resetLearning/resetLearningStartDateForTesting` and the `AdaptiveBlockingEngine` compatibility facade (installation day no longer authorizes blocking; UI still calls `resetLearning()` at `UnblockerScreen.kt:530`); legacy `PrivateReputationStore` (`DeviceLearning.store`, `migrate`) once migration is no longer needed; `HealthReport.memoryUsageMb`.

### Q-3 Dependencies

Let Dependabot propose bumps; do patch/minor only per PR, run the full suite, and check AGP/Kotlin/Compose compatibility. Do not assert "latest" versions from memory.

### Q-4 Test gaps to fill (by workstream)

TCP DNS; forged-CNAME persistence; store write-behind + crash consistency; matcher differential vs `DnsRuleEngine`; never-block gate (Python + Kotlin); adult false-positive corpus; lexical registrable-label case; R8 `releaseTest` smoke; compiler hosts/invalid-ratio/license cases.

---

## 6. Evaluation harness & the numbers policy

- `tools/eval/har_hosts.py`: reads one or more HAR files → unique hostnames with request counts → CSV `host,count,label` (`AD | TRACKER | NEEDED | OTHER`) for **manual labeling**.
- Corpus: HARs from ~20 ad-heavy pages (desktop Chrome DevTools is fine). For apps, capture on a test network with a resolver query log (maintainer test infra — **not** shipped, **no** logging added to the app).
- `CoverageReportTest` (JVM): for each labeled host report matched/not, by which source → recall on `AD+TRACKER`, false positives on `NEEDED`, **for that corpus and commit only**. Print, don't gate recall; **gate false positives at 0** on `NEEDED`.
- Reference run: feed the same hostnames to the vendored lists directly (no network) to show what a list-based resolver would block.
- Smoke only: the public d3ward "adblock test" page is widely used for DNS blockers but has been criticized as unreliable (Privacy Guides forum) — use it as a rough sanity check, never as a benchmark.
- README/PRIVACY keep the "no unsupported effectiveness percentage" stance; when publishing a number, publish corpus name, commit and list revisions with it.

---

## 7. Production release checklist

- [ ] Release keystore created (owner), secrets set, signer digest pinned; `v*` flow produces a signed, R8-minified, **non-debuggable** APK + `SHA256SUMS` + `mapping.txt`
- [ ] No tracked `*.apk|*.aab|*.jks|*.keystore`; CI guard active
- [ ] Manifest least-privilege (Q-1); backup/data-extraction rules unchanged
- [ ] `DATASETS.md` updated — it currently says seeds are "not copied from a third-party bulk blocklist"; **that becomes false after A.2**; add bulk-list provenance + licenses; update `THIRD_PARTY_NOTICES.md` and bundled license texts (`copyLegalAssets` copies them into the app)
- [ ] `PRIVACY.md` still accurate (lists are bundled, never downloaded at runtime)
- [ ] README: known limits (first-party ads, Private DNS/secure-DNS bypass), download badge → signed release
- [ ] `versionCode` bumped; `docs/coverage-baseline.md` + `docs/dns-bypass-matrix.md` committed
- [ ] Full suite green: JVM tests, lint, `connectedAndroidTest` (incl. `releaseTest`), Python tests

---

## 8. Definition of Done (global)

1. Every finding in §3 is either fixed with a failing-then-passing test, or has an issue linked and an OWNER DECISION recorded.
2. Never-block gate green with all bulk lists enabled; **0** false positives on the `NEEDED` corpus.
3. Differential test: new matcher ≡ `DnsRuleEngine` oracle; heap/latency targets measured and recorded (not claimed).
4. S-5 forged-CNAME test proves no persistent block is created.
5. R-2 burst test shows the packet loop is not stalled by persistence.
6. `docs/coverage-baseline.md` shows before/after on the same corpus, with commit and list revisions.
7. No new telemetry, logging, runtime downloads, hidden resolvers, or unlicensed data.

---

## 9. OWNER DECISIONS — raise, don't decide

1. Which bulk list(s) and tier ship by default (StevenBlack only vs + 1Hosts Lite), after seeing breakage + memory numbers.
2. Legal sign-off on MIT/MPL lists and their upstreams; whether GPL-3.0 lists are ever acceptable.
3. Git history purge of `release/*.apk` (rewrites history).
4. Creating the release keystore / Play Console declarations.
5. Optional encrypted upstream (S-6) vs the current "Allowed DNS queries go to your resolver" promise.
6. Opt-in capture of hard-coded public resolvers (A.7.3) and the encrypted-DNS bootstrap toggle (A.7.2).
7. Whether to remove or keep legacy learning-day UI/API (Q-2).

## 10. PR plan

| PR | Scope | Size | Depends on |
|---|---|---|---|
| 1 | S-1, S-3, S-4, Q-1 (CI/release/repo/manifest) | S | — |
| 2 | A.4 never-block gate + allow rules, A.5, A.6 (C-4), C-5 | M | — |
| 3 | S-5, R-3, R-4 | M | PR-2 (uses never-block list) |
| 4 | R-2 write-behind + lock scope | M | — |
| 5 | A.1–A.3 vendored lists, compiler, `dns-rules.bin`, `RuleSetHolder`, R-5 | L | PR-2 |
| 6 | R-1 TCP DNS | L | — |
| 7 | A.7.1 (+ optional A.7.2) | M | — |
| 8 | Q-2, Q-3, S-2 (if not already in PR-1) | S–M | PR-5 |
| Opt | S-6, A.7.3 | M each | owner decisions |

Order rationale: build the safety net (never-block, no-poisoning, no stalls) **before** adding 70k–200k rules that raise the breakage risk.

---

## Appendix A — Evidence index

| Claim | Location |
|---|---|
| Debug APK published as release | `release.yml:69,94,83,50` |
| Inputs injected into shell | `release.yml:37,38,58` |
| Not minified | `app/build.gradle:62` |
| Tracked APKs | `release/` (3 files), history: `release/ub-blocker-1.2.1.apk` |
| UDP-only parse | `DnsPacket.kt:100` |
| Synthetic TC | `DnsPacket.kt:245-253` |
| Lock around classification | `UnblockerVpnService.kt:192-195` |
| Stop path takes same lock / main thread | `UnblockerVpnService.kt:98-101, 351-352` |
| fsync per save | `PrivateEvidenceStore.kt:78-92 (88)` |
| Alias → persisted CONFIRMED | `ContentFilterEngine.kt:39`, `LocalNetworkLearner.kt:42`, `ReputationPolicy.kt:29`, `PrivateEvidenceStore.kt:47,68` |
| Hard-coded tracker set | `LocalNetworkLearner.kt:18` |
| Lexical on all labels | `DomainFeatureExtractor.kt:10` |
| Adult regexes / exceptions | `AdultContentDetector.kt:15,28-36` |
| TSV reload per engine | `AdDetector.kt:15`, `HealthCheckService.kt:20` |
| Unused permission | `AndroidManifest.xml:7` |
| `check(commit())` | `FilteringPreferences.kt:35-36` |
| Silent drop of bad queries | `UnblockerVpnService.kt:189` |

## Appendix B — Reproduce the audit probes

```bash
git clone https://github.com/pkgtm2419/un-blocker && cd un-blocker
git log --oneline | head -3                 # expect fbd2422 at/near top
cd tools/filter-compiler && python3 -m unittest test_compiler test_release   # 7 tests OK
cd ../.. && python3 tools/filter-compiler/compiler.py --root . --output /tmp/out   # dns-rules.tsv ≈ 31,925 bytes
grep -v '^\s*#' app/src/main/assets/ad_domains.txt | tr -d '\r' | grep -c .        # 937
ls -la release/                              # three tracked APKs
```

Adult-regex probe (same pattern strings as `AdultContentDetector.kt`; Kotlin `Regex.matches` ≡ Python `fullmatch`):

```python
import re
pats=[r".*(?:^|[\.-])(?:porn|sex|xxx|nsfw|erotic|cams?|strip|hentai|milf|fap|brazzers|xvideos|pornhub|xnxx)(?:[\.-]|$).*",
r".*-porn-.*",r".*-sex-.*",r".*-xxx-.*",r".*-cam-.*",
r".*(?:red|x|porno|dirty|free|wet|spank|erotic|sex|cam)tube\d*\.(?:com|net|org|xxx)$",
r".*(?:^|[\.-])(?:adult|erotica|chaturbate|stripchat|livejasmin)(?:[\.-]|$).*"]
rx=[re.compile(p) for p in pats]
for d in ["cam.ac.uk","www.cam.ac.uk","adult.education.gov.au","adult-learning.org","comic-strip.com",
          "sex-ed.example.org","fap.rs","stripe.com","adultswim.com","sextant.com","sexualhealth.org","essex.ac.uk"]:
    print(f"{d:28s}", "BLOCK" if any(r.fullmatch(d) for r in rx) else "ok")
```

List-size probe (public raw URLs; network required, **not** part of the build):

```bash
curl -sL https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts | head -8            # header shows unique-domain count
curl -sL https://raw.githubusercontent.com/badmojr/1Hosts/master/Lite/domains.txt | head -6     # header shows rule count + MPLv2
curl -sL https://raw.githubusercontent.com/StevenBlack/hosts/master/license.txt | head -2       # MIT
curl -sL https://raw.githubusercontent.com/hagezi/dns-blocklists/main/LICENSE | head -2         # GPL-3.0
```

**Not verified (needs a device or build):** all runtime behaviour of Private DNS, browser secure-DNS, TCP DNS (R-1), APK size after R8, memory/latency numbers for large rule sets, and whether `releaseTest`/R8 breaks anything.
