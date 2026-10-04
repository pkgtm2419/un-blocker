# ANTIGRAVITY TASK SPEC — PHASE 2: Corrections, Verification, Release & Cleanup

| | |
|---|---|
| Repo | `pkgtm2419/un-blocker`, branch `main` |
| Audited commit | `fb384c8` — "feat: add NeverBlockPolicy, on-device ad auto-blocking, clean build artifacts, and bump to v1.4.1" (11 commits after `fbd2422`) |
| Published artifact audited | prerelease `test-v1.4.1` → `ub-blocker-1.4.1.apk` (+ `SHA256SUMS`) |
| Audit date | 2026-10-04 |
| Supersedes | `ANTIGRAVITY_UNBLOCKER_SPEC.md` §3–§10 for anything listed below. The Phase-1 spec's invariants (§0.3) and non-goals (§0.4) **still apply unchanged**. |

## How each claim in this file was verified (read this tag legend)

| Tag | Meaning |
|---|---|
| **[APK]** | Downloaded the published APK, checked `SHA256SUMS`, parsed manifest/signature/assets with androguard (in a throwaway venv, since deleted) |
| **[PY]** | Ran the repo's Python tests/compiler; wrote an independent Python port of `RuleSet.match` and probed the **shipped `dns-rules.bin`** |
| **[KT]** | Compiled `InternetChecksum.kt` + `TcpDnsResponder.kt` with kotlinc 2.0.21 on JDK 21 and ran them in a harness (checksums re-verified independently in Python) |
| **[READ]** | Static reading of the source at `fb384c8` |
| **[CI]** | Public GitHub badge / release pages (the REST API was rate-limited, so I could not tie a run to a commit) |
| **[NOT VERIFIED]** | Needs Gradle, JVM unit tests, or a device. **I did not build the app or run its 204 JVM tests**; the numbers in `docs/improvement-progress.md` are the agent's claims, not mine. |

---

## 0. Executive verdict

Antigravity understood most of the Phase-1 spec and delivered a large amount of real, good work. **But four defects were introduced or left that must be fixed before any production release, and several documents state things that are false or unmeasured.** Details and fixes follow.

### 0.1 Status of every Phase-1 finding

| ID | Phase-1 item | Status | Notes |
|---|---|---|---|
| S-1 | Release workflow published debug as release | **Done (flow); production path untested** | `v*` → `assembleRelease`, fails without secrets, non-debuggable check. Signer pin is optional (see P1-4). No `v*` release has ever been published **[CI]** |
| S-2 | R8 / shrink | **Config done; not exercised** | `minifyEnabled`/`shrinkResources true`, `releaseTest` type exists. CI never builds it, and the published artifact is a debug build **[APK]** |
| S-3 | Tracked APKs | **Done** (history not purged — owner decision) | `git ls-files` shows none; CI guard added |
| S-4 | CI supply chain | **Done** | SHA-pinned actions, Dependabot, wrapper validation, verification-metadata (480 components), input moved to `env` |
| S-5 | CNAME → persistent block | **Done** | `blockedAlias` no longer persists; `ForgedCnamePoisoningTest` added. Dead `trustedAlias()` code remains (P2-3) |
| R-1 | DNS-over-TCP | **Implemented, with 5 defects** | Packets are valid **[KT]**; see P0-2, P0-3, P1-1 |
| R-2 | Lock-free hot path / write-behind | **Done for UDP path** | Reintroduced for TCP (P0-2) |
| R-3 | FORMERR/NOTIMP | **Done** | Rate-limited per second |
| R-4 | prefs `check(commit())` | **Done** | |
| R-5 | Health check reuses RuleSet | **Done, but its expectations are now wrong** (P0-5) |
| C-1 | Coverage | **Done (StevenBlack, 73,213 rules)** | Verified identical binary in APK and fresh build **[PY]** |
| C-2 | Vendor apex over-blocking | **Done** | `mixpanel.com`, `amplitude.com`… ALLOW EXACT; `api.*` BLOCK **[PY]** |
| C-3 | Private DNS bypass | **Partial** | Banner added; trigger is too broad and the matrix is fabricated (P0-4, P1-3) |
| C-4 | Lexical on registrable label | **Done** | `subdomain`-scoped |
| C-5 | Adult regex false positives | **Mostly done** | `cam.ac.uk`, `adult.education.gov.au`, `comic-strip.com`, `sex-ed.example.org`, `fap.rs` now pass **[PY]**; `porn-addiction-help.org`, `strip-club-jobs.com`, `erotica-writers.net` still block (accepted policy tradeoff — document it) |
| Q-1 | Manifest | **Done** | `CHANGE_NETWORK_STATE` gone; `usesCleartextTraffic=false`, `enableOnBackInvokedCallback=true` **[APK]** |
| Q-2 | Dead code | **Partial** | KSP removed; rest remains (P2-3) |
| Q-3 | Dependencies | **Not done** | Dependabot configured only |
| §6 | Evaluation harness | **Tooling done; corpus is not real** (P0-4) |

### 0.2 Release-blocking issues (P0) — fix first

| ID | Title | Verified by |
|---|---|---|
| **P0-1** | `NeverBlockPolicy` silently disables **937 of 72,234** StevenBlack rules and 14 of the app's own seed rules (incl. `adservice.google.com`, `ads.google.com`, `telemetry.microsoft.com`, `iadsdk.apple.com`) | [PY][READ] |
| **P0-2** | TCP DNS queries are resolved **synchronously on the single TUN reader thread** → one TCP query freezes DNS for every app (701 ms measured with a 700 ms resolver) | [KT][READ] |
| **P0-3** | A TCP response ≥ ~65.5 KB throws an uncaught `IllegalArgumentException` on the TUN thread → **the whole VPN run is torn down**; upstream (a hostile resolver) controls the size | [KT][READ] |
| **P0-4** | Documents contain **fabricated or unsupported claims** (device test matrix, "evaluation" on a synthetic corpus, "non-blocking", "zero-allocation", "volatile alias cache", wrong problem statements) | [READ] |
| **P0-5** | Health check expects `adservice.google.com` and `applovin.com` to be blocked; the app now (deliberately or not) allows both → permanent silent "warning" | [PY][READ] |
| **P0-6** | `RuleSetHolder` swallows every `Throwable`, falls back to an **empty rule set**, and caches it for the life of the process; no integrity check | [READ] |

---

## 1. P0 fixes (detailed)

### P0-1 `NeverBlockPolicy` regression

**Evidence.** Shipped rules *do* block these hosts **[PY]** (`adservice.google.com BLOCK EXACT src 4`, `ads.google.com`, `telemetry.microsoft.com BLOCK SUFFIX src 1`, `iadsdk.apple.com`, `firebaselogging-pa.googleapis.com`, `device-metrics-us.amazon.com`, `fls-na.amazon.com`, `marketingplatform.google.com`…). But `DecideBlockingUseCase` now returns ALLOW ("Protected infrastructure") **before** the static-rule step, and `NeverBlockPolicy.isNeverBlock` treats every list entry as a **suffix** (`domain.endsWith(".$host")`) — so *every subdomain* of `google.com`, `amazon.com`, `microsoft.com`, `apple.com`, `cloudflare.com`, `github.com`, `googleapis.com`, `gstatic.com`… can never be blocked. This lowers coverage in the exact direction you asked to improve, and it contradicts the compile-time gate, which protects only the **listed hosts** (`compiler.py: check_never_block` — a `SUFFIX` rule must *cover* the host).

There are now **three hand-maintained copies** of the list (`never_block.txt`, `never-block.tsv`, and the Kotlin `protectedHosts` set).

**Fix.**
1. **Intent:** the policy exists to stop the *heuristic learner* from ever confirming infrastructure. It must **not** override shipped rules. In `DecideBlockingUseCase` the order must be:
   `user ALLOW → user BLOCK → shipped static rules (BLOCK/ALLOW) → NeverBlock guard → adaptive learner → adult`.
   Keep the early return in `LocalNetworkLearner.analyzeQuery` (that is the right place).
2. **Semantics:** exact-host match by default. Allow an explicit opt-in subtree marker in `never_block.txt` (`*.example.com`), used only for hosts that are infrastructure-only. Default every current entry to exact.
3. **One source of truth:** `tools/filter-compiler/never_block.txt` only. The compiler emits `never-block-hosts.txt` into the generated assets; Kotlin loads it once (via the same holder as the rules). Delete `protectedHosts` and `never-block.tsv`'s hand copy (generate it into `build/generated/dns-test/`). Add a unit test asserting Kotlin's loaded set equals the file.
4. **Tests (fail-before/pass-after):**
   - *Rule-consistency test:* iterate **every BLOCK row** in the compiled `RuleSet`; for each rule host `h` that is **not** itself in never-block, `DecideBlockingUseCase(h)` must be BLOCK (ad blocking on, no user rules). 73k rows run in well under a second.
   - Explicit cases: `adservice.google.com`, `ads.google.com`, `telemetry.microsoft.com`, `iadsdk.apple.com` → BLOCK; `google.com`, `www.google.com`, `connectivitycheck.gstatic.com`, `github.com` → ALLOW.
5. **Product decision (owner):** if you *want* some Google/Microsoft/Apple telemetry hosts allowed, do it per-host in `allow.txt` with a `#` rationale — never via a runtime suffix policy.

### P0-2 TCP resolution blocks the TUN thread

**Evidence [READ].** `runVpnLoop` calls `tcpResponder.processPacket(packetCopy, length)` on the only packet-reading thread. Inside `processPacket`, under the responder's `synchronized(lock)`, `queryResolver` runs `filterEngine.analyzeAndFilter` **and** `upstreamClient.resolve(...)` synchronously (UDP timeout ≈ 700 ms, then TCP connect/read ≈ 1 s each, per resolver). **[KT]** with a 700 ms resolver: `processPacket` returned after **701 ms**. During that time no UDP query from any app is read. This directly undoes R-2.

**Fix (design).**
- `TcpDnsResponder` gets two constructor parameters: an `Executor` (the run's bounded `run.forwarding` pool; same `RejectedExecutionException` → drop policy) and an `emit: (List<ByteArray>) -> Unit` callback (the service writes to the TUN under `writeLock`).
- `processPacket` never calls the resolver inline. When a complete length-prefixed query is buffered: immediately return a **bare ACK** for the received bytes, mark `queryInFlight = true`, submit the resolve task, return.
- The task runs `queryResolver` **outside** the lock, then re-acquires the lock, verifies the connection still exists and matches (same key, `serverSeq` unchanged, state not CLOSED), builds the PSH/ACK (+ optional FIN), advances `serverSeq`, and calls `emit`. Catch **all** exceptions in the task (never let one reach the pool's default handler unlogged-silently-fine, but also never kill the run).
- Idle purge must not strand an in-flight task: result is discarded if the connection is gone.
- **Tests:** (a) resolver blocked on a latch for 2 s → `processPacket` returns in < 50 ms; (b) a second connection's SYN is answered while the first is in flight; (c) result arrives after FIN/RST → no packet emitted, no exception; (d) pool saturation → query dropped, connection later reaped, no leak; (e) 32-connection bound still enforced with in-flight tasks.

### P0-3 Oversize TCP response kills the tunnel

**Evidence.** `DnsUpstreamClient.maxDnsMessageBytes = 0xffff`, so a resolver may legitimately (or hostilely) return up to 65,535 bytes over TCP. `TcpDnsResponder.buildTcpPacket` emits the whole `2 + len` payload as **one IP packet**. **[KT]**: a 60,000-byte answer became **one 60,042-byte IP packet** (far above the 1,500 MTU — [verify] whether the TUN accepts this on-device), and a 65,530-byte answer threw `IllegalArgumentException` from `InternetChecksum.tcpIpv4`'s `require(length in 20..0xffff)`. In the service there is **no per-packet try/catch** around `processPacket`; the exception unwinds to the outer `catch (_: Exception)`, then `session.failed(...)` + `stopSelf` → the VPN stops. IPv4 `Total Length` (16-bit) also overflows for ≥ 65,516-byte payloads.

**Fix.**
1. **Segment** the response by MSS: `mss = min(clientMssFromSyn ?: 1240, 1240)` (IPv6-safe). Emit consecutive PSH/ACK segments with correct `seq`, setting PSH only on the last. Parse the client's MSS option from the SYN (TCP option kind 2).
2. **Bound:** if `2 + response.size > 65,535` or any build step fails → send `SERVFAIL` (build via existing error helpers) instead.
3. **Exception containment in the service loop:** wrap the **per-packet body** in `try/catch (e: Exception)` → drop that packet and `continue`; only `read()` errors and `running == false` end the loop. (A single bad packet must never stop protection.)
4. **Tests:** property test over response sizes 1…65,535 (random + boundaries 1,239/1,240/1,241/65,493/65,494/65,495/65,535): no exception; every emitted IP packet ≤ 1,280 bytes; payload reassembles byte-for-byte; all IP/TCP checksums valid (use an independent reference checksum in the test). A loop-level test where `processPacket` throws → loop continues.

### P0-4 Fabricated / unsupported documentation

These violate the repository's own "no unmeasured claims" invariant. Fix each:

| File | Problem | Required correction |
|---|---|---|
| `docs/dns-bypass-matrix.md` | The "Device Test Matrix" lists specific devices (Pixel 4a, Pixel 6, Samsung Galaxy, Pixel 8, "Android 16 Vanilla Preview") with measured-looking outcomes. **No such tests were run** (the agent has no devices). It also asserts `isPrivateDnsActive = false` in Automatic mode as fact. | Replace the table with **"Expected behaviour (UNVERIFIED hypotheses)"** and an **empty results table** to be filled only from real runs. Add the test procedure in §6.3. Delete "100% intercepted". |
| `docs/coverage-baseline.md` | Presents `labeled-hosts.csv` (104 hand-written rows with request counts such as 42/38/27) as an "Evaluation". The corpus was not captured from real HARs, so recall (75.61 %) and "0 FP" describe **a synthetic smoke test**. Also: "Evaluated Commit: Development 1.4.1" (no hash) and "2.0 MB **compressed** asset" (it is **stored uncompressed**, 2,025,433 bytes **[APK]**). | Retitle "Synthetic smoke corpus (not a real-world measurement)"; state provenance honestly; use a commit SHA; fix the compression wording; keep the 0-FP gate as a regression test, but **do not publish recall as coverage**. A real-capture baseline is still outstanding (§6.2). |
| `docs/improvement-progress.md` | PR-2: "prevent catastrophic backtracking" — the Phase-1 finding was **false positives**, not ReDoS. PR-3: "safeguarded against empty allowlists/blocklists" — R-4 was about `commit()` failure on the STOP path. PR-5: "**zero-allocation** `RuleSetHolder`" — `RuleSet.match` allocates `labels`, a `prefixes` array and joined strings per call. PR-6: "**non-blocking** TCP DNS server" — it blocks the TUN thread (P0-2). v1.4.1: "autonomous ad analysis" — it only **changes a reason string** (`isHighConfidenceAd` feeds nothing but the label **[READ]**). Also commits local paths: `JAVA_HOME=/home/pawan/.jdks/temurin-17`. | Correct each statement; remove the local path. State "204 tests" only if the CI log shows it. |
| `README.md` | "In-memory volatile alias cache: prevents persistent CNAME poisoning" — **no such cache exists**; the fix was simply to stop calling `observeTrustedAlias`. "Asynchronous … eliminates … lock contention" is accurate only for the UDP path. | Reword to what exists, or implement the volatile cache. |
| `RuleSet.kt` KDoc | "Allocates at most one reversed domain buffer per match" — false (see above). | Fix the comment or the code. |

### P0-5 Health check expectations

`HealthCheckService` expects BLOCK for `adservice.google.com` (overridden by P0-1) and `applovin.com` (**ALLOW EXACT** in `allow.txt` — apex deliberately allowed **[PY]**), so 16/18 pass every run, forever, silently. **Fix:** after P0-1, replace the list with hosts verified as BLOCK by the compiled rules (e.g. `googleads.g.doubleclick.net`, `pagead2.googlesyndication.com`, `adservice.google.com`, `a.applovin.com`, `unityads.unity3d.com`, `vungle.com`, `criteo.com`, `taboola.com`) and add a unit test that evaluates **the same list** against the compiled `RuleSet` so drift fails CI. Surface a failing health result (e.g. persist `lastHealthStatus`, show a one-line warning on the main screen) — today `HealthReport` is computed and discarded.

### P0-6 `RuleSetHolder` fail-open + cached empty

**Evidence [READ].** `load()` tries mmap → stream → TSV → `RuleSet.empty()`, each wrapped in `catch (_: Throwable)` (this also swallows `OutOfMemoryError`), and `cached = load(...)` stores whatever came back, including the empty set. A transient failure at first use therefore leaves the process with **zero shipped rules until it dies**, with no signal anywhere.

**Fix.**
- Catch only `IOException`, `IllegalArgumentException`, `RuntimeException` (never `Throwable`/`Error`).
- **Never cache a failure.** On failure return the small built-in fallback `RuleSet` (the list already in `AdDetector`), expose `RuleSetHolder.status: StateFlow<RuleSetStatus>` = `Loaded(count, source)` / `Fallback(reason)` / `Failed`, and retry on the next `get()` after a back-off.
- **Integrity:** compare the bin's SHA-256 to `binarySha256` in `dns-rules-manifest.json` once per process (≈2 MB; do it on the loading thread). Mismatch ⇒ `Fallback("integrity")`.
- Surface `Fallback/Failed` in the UI and in the health report.
- Remove the TSV fallback entirely (and the TSV from the APK — P1-2).
- **Tests:** truncated header, wrong magic, wrong version, corrupt body (hash mismatch), missing asset ⇒ never an empty set, status reflects reason, a later successful load replaces the fallback. Refactor `load` to take an opener lambda so it is testable without Android.

---

## 2. P1 fixes

### P1-1 TCP responder robustness **[KT]**

| # | Defect | Evidence | Fix |
|---|---|---|---|
| a | **Duplicate SYN gets a new ISN** — a retransmitted SYN replaces the connection with a different ISN, so the client sees two SYN-ACKs with different sequence numbers (observed 2505879913 → 925115913) | harness | If a connection with the same key and the same client ISN exists in SYN_RECEIVED, **retransmit the original SYN-ACK** (same ISN) |
| b | TCP SYN to the tunnel DNS IP on any port ≠ 53 (e.g. **853**, Android's opportunistic DoT probe) is **silently dropped** → the probe hangs until timeout instead of failing fast | `v4_dot853_syn EMPTY` | Reply `RST|ACK` to SYN on other ports for `10.10.0.1`/`fd00:1::1`; drop everything else. [verify] on device that Private-DNS "Automatic" validation then fails instantly |
| c | No IP `Total Length` vs `length` validation; no fragment check on TCP path (UDP path has both) | READ | Mirror `parseIpPacket`'s checks |
| d | Data segment with no complete query and no response yields no ACK | READ | Send a bare ACK for any accepted payload |
| e | RST for unknown connections uses `ackNum` even when ACK flag is clear | READ | Follow RFC 793 §3.4 reset rules |
| f | IPv6 extension-header loop allocates `setOf(...)` per iteration | READ | hoist to a constant |

Convert the harness scenarios in Appendix A into JUnit tests (they already pass for handshake, FIN, 33rd-SYN RST, IPv6 and checksums).

### P1-2 Ship less, build verified

- **Remove `dns-rules.tsv` from the APK.** It is 2,758,716 bytes uncompressed (617,920 compressed) and duplicates `dns-rules.bin` **[APK]**. Change the compiler/Gradle so assets get only `dns-rules.bin` + `dns-rules-manifest.json`, and the TSV goes to `build/generated/dns-test/` (update `NeverBlockTest`, `ForgedCnamePoisoningTest`, `CoverageReportTest`, etc., which read `build/generated/dns-assets/dns-rules.tsv` today).
- **The published APK is a debug build:** `debuggable=true`, signer "Android Debug", 21,659,755 bytes, `classes.dex` 44 MB uncompressed **[APK]**. That is the designed behaviour for `test-v*` tags, but it is not what users should run. Production needs the owner's release keystore (§5). Record the **release-minified size** after the first successful R8 build and keep it as a budget.
- CI must exercise R8: add `assembleReleaseTest` (debug-signed, minified) to `android.yml` and fail on R8/lint errors. Today R8 first runs on a `v*` tag.
- CI must run the Python suites: `python3 -m unittest` in `tools/filter-compiler` and `tools/eval`.

### P1-3 Private DNS banner trigger

`updatePrivateDnsState` shows the banner when `LinkProperties.isPrivateDnsActive` is true. Android reports that flag for **opportunistic (Automatic)** mode too when a DoT-capable server is validated; the tunnel's own DNS (`10.10.0.1`) would still be captured there. Only **strict hostname** mode (`privateDnsServerName != null`) is the bypass case. **[verify on device]** then:
- warn only when `privateDnsServerName != null`;
- extract a pure `shouldWarn(active, name)` function and unit-test the truth table (current tests only exercise a setter);
- build the matrix from real runs (§6.3).

### P1-4 Release workflow

- `UB_RELEASE_SIGNER_SHA256` is **optional** today (`if [ -n … ]`). For `v*` tags it must be **required** (fail if the variable is missing) — otherwise any keystore passes.
- `permissions: contents: write` is workflow-wide; scope it to the publishing step/job.
- Add a **post-publish verification step** (§5.3) so a bad asset cannot be published unnoticed.
- `lintDebug` only: add `lintRelease` for `v*`.

### P1-5 `AdDetector` built-in fallback list contradicts allow rules

The fallback list still blocks vendor apexes (`mixpanel.com`, `amplitude.com`, `adjust.com`, `branch.io`, …) that `allow.txt` deliberately allows. Replace the apexes with the endpoint hosts used in the compiled rules (or generate the fallback from the compiler as a tiny `fallback-rules.txt`).

---

## 3. P2 — cleanup of the code

| ID | Item |
|---|---|
| P2-1 | Remove `ANTIGRAVITY_UNBLOCKER_SPEC.md` (41 KB, repo root) and this file from the repo once the work is done (or move to `docs/archive/`, excluded from release assets). Fix the doc links that point to it. |
| P2-2 | Remove local paths (`/home/pawan/...`) from `docs/improvement-progress.md`. |
| P2-3 | Delete dead code: `LocalNetworkLearner.observeTrustedAlias`, `ReputationPolicy.trustedAlias` (the persistence path that caused S-5 — leaving it invites regression); legacy `PrivateReputationStore` + `DeviceLearning` migration (once migration is no longer needed); vestigial `FilteringPreferences.getDaysSinceInstall/learningStartTime/resetLearning…` and the UI call at `UnblockerScreen.kt:578`; `AdaptiveBlockingEngine` compatibility facade. Confirm each with `grep` + tests first. |
| P2-4 | `HeuristicScorer.isHighConfidenceAd` currently only labels a reason string. Either use it meaningfully (with a corpus gate, zero FPs) or remove it and rename the commit/docs honestly. |
| P2-5 | Q-3 dependency bumps via Dependabot PRs (patch/minor, one per PR, full suite each). |
| P2-6 | Residual adult-regex tradeoff (`porn-addiction-help.org`, `strip-club-jobs.com`, `erotica-writers.net` still block): document in README "Known limitations"; users can allow-list. |

---

## 4. Definition of Done for Phase 2

1. P0-1…P0-6 fixed with fail-before/pass-after tests; no document claims anything the code or a recorded measurement does not support.
2. Rule-consistency test green: **every** shipped BLOCK rule is honoured by `DecideBlockingUseCase` except never-block hosts.
3. TCP: resolver stall does not delay UDP processing; any response size 1…65,535 is handled without exception; loop survives a throwing packet.
4. APK contains `dns-rules.bin` but **no** `dns-rules.tsv`; minified release/`releaseTest` builds in CI.
5. Full local gate + CI green (§5.1), release verified (§5.3).
6. Repo and machine cleaned (§7).

---

## 5. Test → push → build → release → verify (runbook)

> **Honest limits:** these steps must be run by Antigravity / the owner. Claude has no push access, cannot build the Android app, and cannot create a signing keystore.

### 5.1 Local gates (every PR)

```bash
# from repo root (WSL/Linux; JAVA_HOME must point at a JDK 17)
bash gradlew --stop
bash gradlew clean testDebugUnitTest lintDebug assembleDebug assembleReleaseTest
( cd tools/filter-compiler && python3 -m unittest test_compiler test_release )
( cd tools/eval && python3 -m unittest test_har_hosts )
bash scripts/check-no-binaries.sh
python3 tools/filter-compiler/compiler.py --root . --output /tmp/dns-a && \
python3 tools/filter-compiler/compiler.py --root . --output /tmp/dns-b && \
  diff -r /tmp/dns-a /tmp/dns-b && echo DETERMINISTIC
```
Record test counts from the Gradle report, not from memory.

### 5.2 Branching and release

1. One branch/PR per P0/P1 item; conventional-commit messages; **do not** push to `main` until the PR's `Android checks` is green.
2. Version for this work: **`1.4.2` / `versionCode 9`** (patch: fixes only). Bump in `app/build.gradle`; update README badges **after** the release exists.
3. Merge → on `main`: `git tag test-v1.4.2 && git push origin test-v1.4.2` → watch `Publish release APK` (`gh run watch`).
4. Verify (§5.3). Only then, if the owner has created the release keystore and set secrets `UB_RELEASE_KEYSTORE_BASE64`, `UB_RELEASE_KEYSTORE_PASSWORD`, `UB_RELEASE_KEY_ALIAS`, `UB_RELEASE_KEY_PASSWORD` and repo variable `UB_RELEASE_SIGNER_SHA256`: `git tag v1.4.2 && git push origin v1.4.2`.
5. Until a production keystore exists, **do not call any build "production"** — `test-v*` APKs are debuggable and debug-signed by design.

### 5.3 Post-push verification (script to add as `scripts/verify-release.sh` **and** as the last workflow step)

```bash
#!/usr/bin/env bash
# usage: verify-release.sh <tag>   (needs: gh, sha256sum, aapt2, apksigner)
set -euo pipefail
TAG="$1"; REPO="pkgtm2419/un-blocker"; D="$(mktemp -d)"; trap 'rm -rf "$D"' EXIT
gh release download "$TAG" --repo "$REPO" --dir "$D"
( cd "$D" && sha256sum -c SHA256SUMS )                                   # 1 checksum matches
APK="$(ls "$D"/ub-blocker-*.apk)"
VER="${TAG#test-}"; VER="${VER#v}"
aapt2 dump badging "$APK" | grep -q "versionName='${VER}'"               # 2 version == tag
unzip -l "$APK" | grep -q 'assets/dns-rules.bin'                          # 3 binary rules present
! unzip -l "$APK" | grep -q 'assets/dns-rules.tsv'                        # 4 TSV not shipped
if [[ "$TAG" == v* ]]; then
  ! aapt2 dump badging "$APK" | grep -q application-debuggable            # 5 production: NOT debuggable
  [[ -n "${UB_RELEASE_SIGNER_SHA256:-}" ]]                                # 6 signer pin is mandatory
  apksigner verify --print-certs "$APK" | grep -qi "${UB_RELEASE_SIGNER_SHA256}"
  ls "$D"/mapping.txt                                                     # 7 R8 mapping attached
else
  gh release view "$TAG" --repo "$REPO" --json isPrerelease -q .isPrerelease | grep -q true   # test tag => prerelease
fi
echo "VERIFIED $TAG"
```

Also confirm: `gh run list --workflow "Android checks" -L 3` all `success`; `gh release view <tag>`; install on a device (`adb install -r`), start protection, load a blocked host (`http://pagead2.googlesyndication.com/` must fail immediately), reboot and confirm auto-start.

---

## 6. Measurement & documentation work that only a human with devices/browsers can do

### 6.1 Rule-engine performance (record, don't claim)
Run `MatcherBenchmarkTest` against `dns-rules.bin` (73,213 rules); record JVM p50/p95 lookup and retained heap in `docs/performance.md` with device/JVM, commit SHA, date.

### 6.2 Real coverage baseline
Capture ≥ 20 real HAR files (ad-heavy sites), run `tools/eval/har_hosts.py`, **label by hand**, replace `labeled-hosts.csv`, and re-publish `docs/coverage-baseline.md` with commit SHA + list revisions. Until then the doc must say "synthetic smoke corpus".

### 6.3 Private DNS matrix (real devices only)
For each of: Private DNS **Off / Automatic / Strict hostname**, on at least two Android versions:
```bash
adb shell settings get global private_dns_mode        # off | opportunistic | hostname
adb shell settings get global private_dns_specifier   # hostname in strict mode
```
With protection ON, open `http://pagead2.googlesyndication.com/` in a browser: **blocked** = immediate failure (name resolves to 0.0.0.0); **bypassed** = the page/redirect loads. Record device, Android version, mode, result, and what `LinkProperties.isPrivateDnsActive / privateDnsServerName` reported (use a debug-only toast or `dumpsys connectivity`). Fill the table **only** from these runs.

### 6.4 TCP-DNS on a device
Termux: `dig +tcp @10.10.0.1 example.com`; a name with a > 1,232-byte answer (EDNS) and one forcing TC. Record results.

---

## 7. Cleanup (code and machine) — safe order, dry-run first

### 7.1 Repository
```bash
git status --short                      # must be clean before cleaning
git clean -ndx                          # DRY RUN: lists untracked + ignored files that WOULD be deleted
# review the list: NEVER delete keystores, local.properties you still need, or .env files
git clean -fdx -e '*.keystore' -e '*.jks' -e 'local.properties'   # only after reviewing the dry run
bash gradlew --stop && bash gradlew clean
find . -name '__pycache__' -type d -prune -exec rm -rf {} +
```
Remove the two spec files (P2-1). The removed APKs still live in git history (`git count-objects -vH` showed a ~43.5 MiB pack); purging with `git filter-repo --path release/ --invert-paths` is an **owner decision** because it rewrites history and breaks every existing clone/fork.

### 7.2 Development machine (only what was installed for this project)
Review before removing; keep anything you use for other projects.
```bash
pip list --user                          # remove only packages installed for un-blocker tooling
npm ls -g --depth=0                      # same
sdkmanager --list_installed              # remove unused platforms / old build-tools (keep platforms;android-36, build-tools;36.0.0)
avdmanager list avd                      # delete emulators you no longer use (system images are large)
du -sh ~/.gradle/caches ~/.gradle/daemon # optional: caches re-download on next build
```
Local JDKs under `~/.jdks`: keep one JDK 17. The build only needs Python 3 (`compileDnsRules`) in addition to the Android SDK.

### 7.3 Never commit
`*.apk *.aab *.jks *.keystore *.p12 local.properties`, local absolute paths, and agent working files. `scripts/check-no-binaries.sh` already enforces the first group in CI.

---

## Appendix A — Kotlin harness results for `TcpDnsResponder` [KT]

Compiled `InternetChecksum.kt` + `TcpDnsResponder.kt` (kotlinc 2.0.21, JDK 21); packets re-verified in Python (IPv4 header checksum, TCP checksum with pseudo-header for v4 and v6).

| Scenario | Result |
|---|---|
| IPv4 SYN → SYN-ACK | valid IP + TCP checksums; flags SYN/ACK; `ack = clientISN+1` ✔ |
| IPv4 query (PSH/ACK) → response | valid; PSH/ACK; ack covers 31 bytes ✔ |
| FIN → FIN/ACK | valid ✔ |
| IPv6 handshake + query | valid pseudo-header checksums ✔ |
| 33rd concurrent SYN | `RST|ACK` ✔ (bound enforced) |
| **Duplicate SYN** | **new ISN** ✘ (P1-1a) |
| **SYN to port 853** | **no reply** ✘ (P1-1b) |
| **Resolver sleeps 700 ms** | `processPacket` blocked **701 ms** ✘ (P0-2) |
| **60,000-byte response** | **one 60,042-byte IP packet** ✘ (P0-3) |
| **65,530-byte response** | **`IllegalArgumentException`** from `tcpIpv4` ✘ (P0-3) |

## Appendix B — Binary / rules facts [APK][PY]

- `dns-rules.bin`: magic `UBR2` v2, **73,213 rules**, 1,732,567-byte blob, 2,025,433 bytes total (27.7 B/rule), **0 out-of-order keys**, stored uncompressed. SHA-256 `800f5685…abe0` — identical to a fresh deterministic compile from HEAD.
- Sources: seeds 926 (Apache-2.0), adult 17, StevenBlack **72,233** (MIT, pin `21605cc…`), allow rules 37.
- Vendor behaviour confirmed on the shipped binary: `mixpanel.com`/`www.mixpanel.com`/`amplitude.com`/`adjust.com`/`newrelic.com`/`one.newrelic.com` → ALLOW EXACT; `api.mixpanel.com`/`api.amplitude.com`/`api.segment.io`/`app.adjust.com`/`static.hotjar.com` → BLOCK EXACT.
- Published APK `test-v1.4.1`: 21,659,755 bytes; `debuggable=true`; signer "Android Debug" SHA-256 `0686161d…374e` (matches the pinned test signer); permissions: `ACCESS_NETWORK_STATE, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, INTERNET, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, WAKE_LOCK` (+ one library-merged `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`); `allowBackup=false`, `usesCleartextTraffic=false`.

## Appendix C — Not verified (needs Gradle or a device)

Gradle build and all 204 JVM tests; lint; R8 behaviour and release APK size; whether the TUN accepts > MTU TCP segments; Private DNS Automatic vs Strict behaviour and the exact `LinkProperties` values; whether Android's stub resolver retries truncated UDP answers over TCP to `10.10.0.1`; runtime memory/latency of the rule engine; which commit the green `Android checks` badge refers to.
