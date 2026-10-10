# ANTIGRAVITY TASK SPEC — PHASE 3: Fix 2.0.x (network, button, logs, One UI 9) and ship a verified release

| | |
|---|---|
| Repo | `pkgtm2419/un-blocker`, branch `main` |
| Audited | tag `v2.0.0` (as the owner tested it) **and** HEAD `cfcb29e` = `2.0.1` (versionCode 9) |
| Audit date | 2026-10-06 |
| Revision | 2026-10-09: **WP-7 filter-list catalogue** (§6A); 2026-10-10: **WP-8 complete CI / GitHub Actions / repo-config audit with validated fixes** (§7A, `ci-fixed/`) and test rows C-1…C-4 |
| Supersedes | Phase 1/2 specs where they conflict. Their invariants still apply (see §1.3). |
| **Not done** | The app was **not built** and **not run on a device**. Runtime claims that need Android are tagged **[verify]**. |

## Verification legend

| Tag | Meaning |
|---|---|
| **[READ]** | static reading of the source at the stated tag/commit |
| **[RUN]** | I compiled the real Kotlin source with kotlinc 2.0.21 / JDK 21 and executed it in a harness (outputs in Appendix B) |
| **[PY]** | independent Python re-implementation / checksum verification |
| **[CI]** | public GitHub badge/release pages (REST API was rate-limited, logs are not public) |

---

## 0. Owner-reported problems (verbatim requirements — each must be fixed AND tested)

> Version 2.0.0 does not work properly:
> 1. it is **blocking the internet** for the main application network;
> 2. the **internet connection is very slow** after enabling the blocking server;
> 3. the **application is not working properly** — the **main enable/disable button is not working**;
> 4. the **bottom menu UI is not proper** and the **whole application layout is not proper**;
> 5. in the **Logs tab show every domain only once, with total call count and history based on timestamp**;
> 6. the **layout and UI should be like Samsung One UI 9.0**, and the **bottom menu bar should float like a pill**.

### 0.1 Root cause of each (evidence)

| # | Symptom | Root cause | Evidence | Fixed in 2.0.1? |
|---|---|---|---|---|
| 1 | Internet blocked | **Full-tunnel VPN.** `VpnConfiguration` added `addRoute("0.0.0.0",0)` and `addRoute("::",0)`, so *every* packet from every app entered the tunnel; the engine then handled non-DNS packets with `output.write(buf,0,length)` — writing the app's **outbound** packet **back into the TUN** (a reflection, not forwarding). All TCP/QUIC/IPv6 traffic was black-holed. A userspace DNS filter has no TCP/IP stack and must never carry general traffic. | [READ] `vpn-engine/.../builder/VpnConfiguration.kt`, `.../service/UnblockerVpnService.kt` (`// Non-DNS traffic — pass through directly`) | **Routes: yes** (now `10.10.0.1/32`, `fd00:1::1/128`). The reflect-back code **still exists** |
| 2 | Very slow | (a) the packet loop calls `forwarder.forwardToUpstream()` **synchronously** (UDP timeout 700 ms, then TCP 1 s + 1 s) on the single reader thread → one slow query stalls all DNS; (b) no DNS cache; (c) a coroutine + **Room insert per DNS query**; (d) **IPv6 DNS is broken** (see 3); (e) upstream hard-coded to `1.1.1.1`/`8.8.8.8`; (f) read-failure path spins: `if (length <= 0) continue` | [READ]; (d) **[RUN]** | **No** |
| 3 | App not working | **IPv6 path:** `extractDnsPayload()` assumes IPv4 (`ihl=(buf[0]&0x0F)*4`). For an IPv6 query it returned **69 bytes of garbage instead of the 29-byte query**; and `buildForwardedResponse()` writes a **zero UDP checksum for IPv6, which receivers must drop**. 2.0.1 now advertises `fd00:1::1` as a DNS server, so Android will send IPv6 queries. | **[RUN]** Appendix B.1 | **No** (and newly exposed) |
| 3b | **Enable/disable button does nothing** | `QuickStartManager`, `BootReceiver`, `UnblockerScreen` all call `com.unblocker.app.services.UnblockerVpnService` (the **old** class in `:app`). The manifest declares **only** `com.unblocker.vpn.service.UnblockerVpnService` (the **new** class). Nothing in `:app` ever starts the declared service (`grep com.unblocker.vpn` in `app/src/main` → only the DI import). `startForegroundService` for an undeclared service silently starts nothing, and the UI status is bound to the *old* service's state, which never changes. | [READ] `AndroidManifest.xml`, `QuickStartManager.kt:~71`, `BootReceiver.kt` | **No** |
| 3c | Toggle state wrong even if it ran | `VpnStateHolder.isActive` is a getter that builds a **new constant `MutableStateFlow` on every access** → a collector never sees updates | [READ] `VpnStateHolder.kt` | **No** |
| 3d | Even a working tunnel blocks ~nothing | `FilterRepository.seedStaticRules()` has **0 callers**, so `block_rules` starts empty → the Bloom filter is empty → the 73k-rule list from 1.x is **not used**. `domain_classifier.tflite` is **not in the repo**, so the nightly ML job cannot produce rules either. | [READ] grep callers; `find *.tflite` → none | **No** |
| 4/6 | Layout / bottom bar | Plain Material `NavigationBar` wrapped in a full-width rounded `Box` — not a compact pill; theme is a Tailwind slate/teal palette, not One UI | [READ] `MainScreen.kt`, `Theme.kt`, `Color.kt` | Partial (rounded full-width bar) |
| 5 | Logs | `DnsLogDao.getRecentLogsFlow()` = last **100 rows**; grouping is done in the UI over those 100 rows → counts are capped at 100 total and per-domain history isn't available | [READ] `DnsLogDao.kt`, `LogViewerScreen.kt` | Partial (grouped in UI, wrong counts, no history) |

### 0.2 Additional defects found in 2.0.x

| ID | Defect | Evidence |
|---|---|---|
| D-1 | **Two engines in the repo.** `:app` still contains the entire 1.4.2 engine (service, rule engine, TCP responder, learner, 47 test files); `:vpn-engine/:data-store/:ml-engine` contain a second, less capable one. New modules have **0 tests**. | [READ] |
| D-2 | **Bloom filter as authority**: any false positive (0.1 % per lookup, checked at every parent label including the TLD) permanently blocks a legitimate name with no exact verification; 1.x `ALLOW`/exact/suffix semantics (vendor-site unblock rules) are gone; whitelisting a subdomain of a blocked parent does not work | [READ] `BloomFilterManager.isBlocked` |
| D-3 | **Privacy regression**: every query (domain + timestamp) is written to a plaintext Room table; `PRIVACY.md` still says no per-query timestamps/complete query history | [READ] `DnsLogEntity`, `PRIVACY.md` |
| D-4 | **Hard-coded public resolvers** (`UpstreamDnsServer.DEFAULTS = 1.1.1.1, 8.8.8.8`) used by `DnsForwarder` → all DNS leaks to Cloudflare/Google, captive-portal / enterprise / carrier split-DNS breaks; violates the "no hidden resolver fallbacks" invariant | [READ] |
| D-5 | Release pipeline weakened: `gradle/verification-metadata.xml` **deleted** and `--dependency-verification=off` added to release builds; the release job now **commits README edits to `main` from CI** via ~12 `sed` replacements; release workflow is **failing** for `v2.0.0`/`v2.0.1` (no assets) | [READ][CI] |
| D-6 | **1,255 build outputs tracked in git** (`data-store/build` 471, `vpn-engine/build` 299, `ml-engine/build` 285, `common/build` 200 — 384 `.class` files); `.gitignore` only ignores `/build` and `app/build/`. Stray files: `patch.py`, `.planning/`, `tools/filter-compiler/compiler.patch`, spec files in `docs/archive/` | [READ] |
| D-7 | Old-engine defects from the Phase 2 audit are **still present**: `NeverBlockPolicy.load()` has **0 callers** (the learner guard is a no-op in production), FORMERR/NOTIMP code after `continue` is **unreachable**, TCP SYN to port ≠ 53 (e.g. DoT 853) is silently dropped | [READ] |
| D-8 | `docs/version-2.0.0-fixes.md` and `docs/improvement-progress.md` claim fixes; I did not verify them and the reading above contradicts "button works" / "internet restored" | not reviewed |
| D-9 | **CI / GitHub Actions / repo configuration** (22 findings: dependency verification off, production releases never succeeded, verify script is a stub, secrets exposed to the whole build, 1,255 tracked build files, …) | see **§7A** and `ci-fixed/` |

### 0.3 Honest scoreboard (my judgment; evidence in Appendix A) — use as the baseline to beat

| Category | 1.4.2 | 2.0.0 | 2.0.1 |
|---|---|---|---|
| Security | 8 | 4.5 | 5 |
| On-device process (VPN lifecycle, reliability) | 8 | 1 | 3 |
| Blocking ads from network/device | 8.5 | 0.5 | 1 |
| Finding/learning new ad domains | 6.5 | 1 | 1 |
| Privacy | 9 | 3.5 | 3.5 |
| Performance | 8 | 1.5 | 2.5 |
| UI/UX | 6 | 3 | 4 |
| Release/CI/code hygiene | 6 | 3 | 2.5 |
| **Weighted overall (/10)** | **7.7** | **2.1** | **2.8** |

---

## 1. Decision, rules, invariants

### 1.1 DECISION (follow unless the owner says otherwise): **Plan A — re-base on the proven 1.4.2 engine**
The 1.4.2 engine was measured to work (Appendix A: 131/134 ad/tracker/popup/adult probes blocked, 100/103 legitimate probes allowed, matcher p50 2.2 µs). 2.0.x replaced it with an engine that is not wired, not seeded and not IPv6-correct. Rebuilding that engine is more work and risk than porting the new **UI + logs** onto the proven engine.

**Plan A in one paragraph:** keep **one** VPN service — `com.unblocker.app.services.UnblockerVpnService` (declared in the manifest) with the 1.4.2 pipeline (`parseIpPacket` → `ContentFilterEngine` → `DnsUpstreamClient` using device resolvers, `TcpDnsResponder`, `RuleSet`, learner). Delete `:vpn-engine`'s service/parser/forwarder/builder, `:ml-engine` (no model exists), and the Bloom-filter/`block_rules` path. Keep `:common` only if still used; keep `:data-store` **only** for the new logs tables (§4). Port the new UI (§5).

*Plan B (only if the owner insists on the new modules):* every item in §0.1 must still be fixed in the new modules, plus: add IPv6-correct parsing/response building with valid checksums, async forwarding with a bounded pool and cache, system-resolver upstream, seed rules, replace Bloom-as-authority with an exact structure (or verify Bloom hits), wire the service, and port the 47 tests. Estimated cost is much higher; do not start Plan B without explicit approval.

### 1.2 Rules of engagement
- One PR per work package (§2–§7). Each PR must pass: `bash gradlew testDebugUnitTest lintDebug assembleDebug assembleReleaseTest`; `python3 -m unittest` in `tools/filter-compiler` and `tools/eval`; `bash scripts/check-no-binaries.sh`.
- Every fix needs a test that fails before and passes after. Where this spec says **[verify]**, record the device result in the PR.
- Do not claim anything the code or a recorded measurement does not support (this was violated in earlier docs: fabricated device matrix, "non-blocking", "zero-allocation", "204 tests passing" while CI was red).
- Keep diffs minimal; no whole-file reformatting; no mass line-ending changes.

### 1.3 Invariants (unchanged) and one **owner-approved exception**
Unchanged: no telemetry/upload; no hidden resolver fallback; no runtime list downloads; HMAC-only learning store; user ALLOW beats everything; every dataset documents license + provenance; no unmeasured effectiveness percentages.
**Exception (owner requested the Logs tab):** the app may store a **local, bounded** query history (§4). That requires updating `PRIVACY.md` (it currently says no per-query history) and the protections in §4.5.
**Second exception (only if the owner approves WP-7 §6A.10):** an optional, user-initiated download of filter lists from their official publishers, with the safeguards in §6A.7. Without that approval the app stays offline and only Class P (permissive, bundled) lists are used.

---

## 2. WP-1 — One VPN service, working enable/disable button (fixes 3, 3b, 3c)

**Required changes**
1. Exactly one `VpnService` subclass exists in the codebase and in the manifest. Delete the other. Add a unit test that parses the merged manifest (or `AndroidManifest.xml` sources) and asserts: every class referenced by `startService/startForegroundService/Intent(…, X::class.java)` is a declared `<service>`.
2. **Single source of truth** for state: `VpnSession.status: StateFlow<ServiceStatus>` (`STOPPED | STARTING | RUNNING | STOPPING | ERROR(reason)`), written only by the service, read by UI/QuickStartManager/BootReceiver/HealthCheck. Delete `VpnStateHolder.isActive` (the getter that creates a new flow per read). UI collects with `collectAsStateWithLifecycle()`.
3. Toggle behaviour (Home screen switch/button):
   - Off → tap: request notification permission (API 33+, non-blocking), then `VpnService.prepare()`; on consent start the service; show **STARTING** immediately; **RUNNING** only after the tunnel is established *and* the first packet loop iteration ran.
   - On → tap: stop; **STOPPING** → **STOPPED**.
   - Timeout: STARTING > 8 s → ERROR("start timed out") with Retry.
   - Debounce double taps; disable the control only while STARTING/STOPPING.
   - Permission denied / user revokes VPN / another VPN takes over (`onRevoke`) → **STOPPED**, `protectionEnabled=false`, snackbar with the reason.
   - Process death/restart: persisted `protectionEnabled` + service restart must converge to the same status the UI shows.
   - `BootReceiver` starts the same service; if consent is missing it must not crash and must show a notification action to re-enable.
4. Remove `catch (_: Exception) {}` silent swallows around start/stop; map failures to `ERROR(reason)` shown in the UI.

**Tests**
- Unit: state machine (all transitions, illegal transitions rejected, restart convergence).
- Instrumented (Compose + fake `VpnController`): tap on/off updates UI within 1 frame of state change; error and timeout paths; revoke path.
- **[verify] on device:** toggle on → `adb shell dumpsys connectivity | grep -i vpn` shows the VPN; toggle off → gone; UI always matches.

---

## 3. WP-2 — Network correctness and speed (fixes 1, 2, 3)

### 3.1 Routing / addressing (DNS-only, never a full tunnel)
- Routes **only** `10.10.0.1/32` and `fd00:1::1/128`; addresses `10.10.0.2/32`, `fd00:1::2/128`; DNS servers = those two tunnel IPs; MTU 1500. **Never** add `0.0.0.0/0` or `::/0`.
- **Regression test:** a unit test over `VpnConfiguration` (fake Builder) asserting the exact route set and that no prefix length < 32 (v4) / < 128 (v6) is ever added.
- Any non-DNS packet that reaches the TUN must be **dropped, never written back**. Delete the `output.write(buf,0,length)` pass-through. Test: feed a TCP SYN to a non-tunnel address → zero bytes written.
- TCP to `10.10.0.1:53` is handled by `TcpDnsResponder`; TCP to any other port on the tunnel IPs gets `RST|ACK` (fast fail for DoT/853).

### 3.2 Upstream resolvers
- Use Android's configured resolvers (`ResolverRegistry`/`DnsResolverPolicy` from 1.4.2). **Delete `UpstreamDnsServer.DEFAULTS`** (1.1.1.1/8.8.8.8). Any encrypted/custom upstream is opt-in and user-chosen (Phase 2 S-6).

### 3.3 Threading and latency (the "very slow" fix)
- The TUN **reader thread must never block on the network**. Resolve on a bounded pool (e.g. 4 threads, queue 64; drop with SERVFAIL-or-silence policy on overflow). Add the 1.4.2 `DnsCache` back in front of upstream.
- **No per-query DB write on the hot path** (see §4: in-memory aggregation, batched flush).
- Read loop: on `read()` error distinguish "closed" (exit loop) from transient (`sleep(5 ms)` backoff); **never** `continue` in a tight loop.
- Start order: load rules/learner **before** `establish()`; if loading is slow, answer from a minimal built-in list rather than leaving queries unanswered (an unanswered DNS query = "no internet").

### 3.4 IPv6 correctness (from the [RUN] findings)
- Parse DNS payload offsets from the **IP version** (IPv4 `IHL*4`; IPv6 40 bytes + extension headers) — use `DnsPacketUtil` from 1.4.2, which handles both.
- Every IPv6 UDP response must carry a **valid UDP checksum** (pseudo-header sum; never 0). IPv4 may use 0.
- **Regression tests (copy my harness cases, Appendix B.1):** an IPv6 DNS query must yield the exact 29-byte query upstream and a response whose UDP checksum verifies; same for IPv4; property test over random names/lengths.

### 3.5 Acceptance (measure on a device; record numbers, don't claim in advance)
| Metric | Method | Proposed target |
|---|---|---|
| Added DNS latency | 200 uncached lookups (Termux `dig`), VPN off vs on | median ≤ 10 ms, p95 ≤ 50 ms added |
| Packet loss / failures | same run | 0 failures |
| Page load | 10 loads of an ad-heavy page, VPN off vs on | on ≤ 115 % of off |
| Non-DNS traffic | browse, YouTube, speed test, WhatsApp call during protection | unaffected (app is not in the data path) |
| Idle CPU / battery | 8 h idle with protection on | recorded, no regression vs 1.4.2 |

---

## 4. WP-3 — Logs: every domain once, total call count, timestamp history (fixes 5)

### 4.1 Data model (two small tables; no per-query rows on the hot path)
```
domain_stats(domain TEXT PRIMARY KEY, call_count INTEGER, blocked_count INTEGER,
             allowed_count INTEGER, first_seen INTEGER, last_seen INTEGER,
             last_decision INTEGER /*0 allowed,1 blocked*/, last_reason INTEGER)
   INDEX(last_seen DESC), INDEX(call_count DESC)
domain_events(id INTEGER PK AUTOINCREMENT, domain TEXT, ts INTEGER, decision INTEGER)
   INDEX(domain, ts DESC), INDEX(ts)
```
- `domain_stats` is the **one row per domain** the Logs tab lists. `domain_events` is the **bounded history** shown on expand: keep the newest **50 per domain** and a global cap (e.g. 100 000 rows); default retention **7 days** (user-configurable 1/7/30 days).

### 4.2 Write path (off the packet loop)
- Engine → `QueryLogSink.record(domain, decision, reason, tsMillis)` which only does a lock-free enqueue into a bounded ring (drop-oldest when full; never blocks).
- A single writer coroutine aggregates by domain for ≤ 1 s (or 200 events) then, **in one transaction**, upserts `domain_stats` (`INSERT … ON CONFLICT(domain) DO UPDATE SET call_count=call_count+:n, …`) and inserts the aggregated timestamps. Prune hourly (retention, per-domain cap, global cap; evict by oldest `last_seen`).
- Domain strings normalized with `DomainName.normalize` (lowercase, no trailing dot). Cap length 253.

### 4.3 Read path / UI behaviour
- List query: `SELECT … FROM domain_stats WHERE (:filter) AND (:q IS NULL OR domain LIKE :q||'%' ESCAPE '\') ORDER BY <last_seen|call_count> DESC` with **keyset paging** (Paging 3 or `LIMIT`+last key); never load the whole table into memory.
- Row (One UI list item, §5): domain (middle-ellipsis), status chip (**Blocked / Allowed / Mixed**), "**N calls**" (+ "B blocked"), relative last-seen ("2 min ago"). Tap → bottom sheet / detail pane with **history grouped by day**, each line `HH:mm:ss — Blocked|Allowed`, newest first, up to 50 entries, plus first-seen/last-seen.
- Header counters (Domains · Calls · Blocked) from `SELECT SUM(…)`; filter chips **All / Blocked / Allowed**; sort **Recent / Most calls**; search; **Clear history** (confirm dialog); optional **Pause logging**.
- Row actions: **Allow this domain** / **Block this domain** (feeds the existing user rules + learner feedback).
- Empty/loading/error states; TalkBack: row announces "domain, N calls, blocked, last seen …".

### 4.4 Tests
- DAO tests (in-memory Room): upsert accumulates counts exactly; 1 000 queries for one domain → 1 row, `call_count=1000`, ≤ 50 events; per-domain and global caps; retention prune; keyset paging stable under inserts.
- Writer tests: burst of 50 000 records → engine enqueue never blocks (assert p99 enqueue < 100 µs), DB writes ≤ 1 transaction/s, no loss under cap (counts equal) and graceful drop-oldest above cap.
- Compose UI test: one row per domain, count shown, expand shows timestamps in descending order.

### 4.5 Privacy requirements for this feature
Update `PRIVACY.md` (it currently denies per-query history). Exclude the DB from backup/data-extraction rules; no sync/export; default retention 7 days; **Clear history** and **Pause logging** in the UI; never log from private DNS bypass etc.; never write domains to Logcat; keep `allowBackup=false`.

---

## 5. WP-4 — One UI 9-style UI with a floating pill bottom bar (fixes 4, 6)

> Basis and limits: Samsung introduced **pill-shaped floating bottom bars with pill-shaped tabs in One UI 8.5**, and reports on early One UI 9 builds describe a **more compact, rounder** floating bar with larger gaps at the sides and bottom (Galaxy Store was updated to this style). One UI 9 details are partly based on leaks; **treat the dimensions below as starting values and calibrate against real One UI 9 screenshots on a Samsung device.**

### 5.1 Components
1. **`FloatingPillNavBar(items, selected, onSelect)`** (replace the Material `NavigationBar`; do **not** use `Scaffold.bottomBar`):
   - `Surface(shape = CircleShape)` (fully rounded), `tonalElevation 3.dp`, `shadowElevation 8.dp`, 1 dp outline at ~12 % alpha.
   - **Wrap-content width** (not full width), `widthIn(max = 360.dp)`, height **64 dp**; centered; outer margin: sides ≥ 24 dp, bottom `max(16.dp, navigationBarsInset + 12.dp)`.
   - Each tab: min 48 dp touch target; selected tab shows a **capsule indicator** (animated container colour) with icon 24 dp + label 12 sp; unselected shows icon (+ label).
   - Content scrolls **behind** the bar; every screen gets `contentPadding(bottom = barHeight + margins + insets)` so the last item is never covered.
   - Gesture-nav and 3-button-nav insets handled (`WindowInsets.navigationBars`); IME hides the bar.
2. **Large collapsing title** (`LargeTopAppBar` + `exitUntilCollapsedScrollBehavior`): "Un-Blocker" with a one-line status subtitle ("Protection on · 1,284 blocked today").
3. **Card groups**: surfaces as large rounded cards (corner **28 dp**, padding 20 dp, 12 dp gaps), list rows separated by dividers inside a card, switches right-aligned.
4. **Theme:** dynamic colour on API 31+ (`dynamicLightColorScheme/dynamicDarkColorScheme`), neutral surface hierarchy, brand fallback; dark mode with true-dark background option; typography with a clear large-title/body scale. Replace the Tailwind slate/teal tokens.

### 5.2 Screens
- **Home:** (1) *Protection* card — large switch row with status line and error/permission hints (WP-1 states); (2) *Today* card — Blocked / Allowed / Domains; (3) *Filters* card — Block ads, Block adult content; (4) *Exceptions* card (existing allow/block rules UI, tidied); (5) conditional notices (strict Private DNS, VPN permission, notification permission).
- **Logs:** §4.3 inside a card list with large title, search, chips.
- **Settings/About** (if present): grouped cards.

### 5.3 Layout robustness
Compact phones 360×640 → large phones → foldable/tablet (`WindowSizeClass`: Logs becomes list + detail pane ≥ 600 dp; the pill bar stays centered and compact); landscape; display/font scale 0.85–2.0 with no clipping or truncated controls; edge-to-edge with correct insets; RTL.

### 5.4 Acceptance
- Screenshot tests (Paparazzi/Roborazzi or Compose `captureToImage`) at 360×800 and 411×914, light/dark, font scale 1.0/1.3/2.0, plus a fold width; **manual checklist on a Samsung device (One UI 8.5/9)**: the bar floats, never overlaps the last list item or the gesture area, tab indicator animates, TalkBack reads "Home, tab, selected, 1 of 2".
- No hard-coded colours outside the theme; no `androidx.compose.…` fully-qualified calls inline (the 2.0.1 `MainScreen` mixes them — tidy).

---

## 6. WP-5 — Rules, learning and the old-engine fixes (carry-over; each with a test)

| ID | Fix | Test |
|---|---|---|
| R-1 | Ship the compiled `RuleSet` (`dns-rules.bin`, `never-block-hosts.txt`) as in 1.4.2 (`compileDnsRules` already exists in `app/build.gradle.kts`); **delete the Bloom-filter/`block_rules` path**. | rule-consistency test: every BLOCK row blocks unless host is never-block |
| R-2 | **Call `NeverBlockPolicy.load(context)`** at process start (0 production callers today → the learner guard is a no-op). | unit test: after init, `isNeverBlock("connectivitycheck.gstatic.com")` is true |
| R-3 | **FORMERR/NOTIMP unreachable** (`continue` before `buildErrorResponseIfApplicable`): restructure so TCP handling returns early *only when the packet was consumed*. | malformed UDP/53 query ⇒ FORMERR written |
| R-4 | TCP SYN to tunnel IP, port ≠ 53 → `RST|ACK`. | DoT/853 SYN ⇒ RST |
| R-5 | **Learner eviction**: SUSPECT entries must not evict CONFIRMED ones (30k flood erased a confirmed record in my run). Evict non-confirmed first. | flood test: confirmed record survives |
| R-6 | **Keyword false positives** (7/10 legit "keyword" hostnames — `tracking.ups.com`, `analytics.google.com`, `analytics.twitter.com`, `ads.twitter.com`, `metrics.cloudflare.com`, `pixel.google.com`, `track.example.org` — were learned as blockers after 2 bursty windows). Require corroboration beyond lexical+cadence for hosts under known-legit registrable domains (ship a small curated "first-party tooling" allow list), surface "Why blocked?" with one-tap Allow, keep the existing user-ALLOW override. | the 10 probes: legit ones never CONFIRMED without user BLOCK |
| R-7 | Learner/doc honesty: keep `isHighConfidenceAd` only if it changes behaviour; otherwise remove. | — |
| R-8 | Remove ML/TFLite until a trained model **and** an evaluation exist. | build has no `ml-engine` or it is feature-flagged off |

---

## 6A. WP-7 — Filter-list catalogue (the lists from the 1DM "Hosts & filters" screen)

### 6A.1 Direct answers
1. **Does the app use these lists today? No.** The build knows only: project seeds (`ad_domains.txt`, 926 domains), adult seeds, the Public Suffix List, **StevenBlack/hosts (MIT, 72,233 domains)** and `allow.txt` (`tools/filter-compiler/sources.json`, ids 1–4 and 10). None of the 27 lists in the screenshots is a direct source. (StevenBlack's file is itself a merge of upstream lists, so some of their domains can arrive indirectly — not verified list by list.) In 2.0.x **nothing is loaded at runtime at all** (WP-5 R-1).
2. **Can we use them? Partly.** They are *browser* filter lists (EasyList/uBlock/AdGuard syntax). A DNS blocker can only enforce **domain-level** rules (`||domain^`); cosmetic rules (`##…`), scriptlets, URL-path rules, regex rules and `$domain=`/resource-type conditions cannot be applied at DNS. 1DM can hide elements and block by URL inside its own browser; Un-Blocker cannot (no TLS interception — a non-goal). Adopting the lists therefore gives the **domain-level subset** only and will **not** reproduce 1DM's in-browser results.
3. **The screenshots:** 28 entries (27 lists + a "Manual filters" slot), all showing "Filters: 0 / Total active filters: 0". Their timestamps (Oct 09, 12:01–12:02 am) show 1DM **downloads the lists on the device** — that is the model for Class C below.

### 6A.2 Measured usability (2026-10-09; lists fetched from GitHub; extraction rules in 6A.5)
"Usable" = unique valid domains from `||domain^` rules (tier A: no options; tier B: only `$third-party`/`$all`/`$important`). "NEW" = not already in the app build (StevenBlack ∪ seeds = **72,739** domains).

| List (as fetched) | Rules | Cosmetic | Usable domains | NEW vs app | Usable share |
|---|---:|---:|---:|---:|---:|
| EasyList: adservers | 46,819 | 0 | 46,077 | 45,154 | 98.4 % |
| EasyList: thirdparty | 1,863 | 0 | 1,432 | 1,387 | 76.9 % |
| EasyList: general+specific block | 1,866 | 0 | 110 | 102 | 5.9 % |
| EasyPrivacy: trackingservers | 35 | 0 | 30 | 21 | 85.7 % |
| EasyPrivacy: general+thirdparty | 6,537 | 0 | 1,746 | 1,410 | 26.7 % |
| uBlock filters | 6,111 | 4,333 | 103 | 95 | 1.7 % |
| uBlock – Privacy | 1,769 | 325 | 96 | 87 | 5.4 % |
| uBlock – Badware risks | 4,409 | 139 | 1,153 | 1,136 | 26.2 % |
| uBlock – Resource abuse | 78 | 41 | 1 | 1 | 1.3 % |
| uBlock – Unbreak | 2,561 | 618 | 50 | 11 | 2.0 % |
| AdGuard Base | 143,129 | 58,204 | 59,842 | 58,631 | 41.8 % |
| AdGuard Mobile Ads | 8,747 | 6,443 | 946 | 567 | 10.8 % |
| AdGuard Tracking/"Spyware" (not in the screenshots) | 328,114 | 836 | 106,695 | 104,064 | 32.5 % |
| AdGuard Annoyances | 61,741 | 54,498 | 877 | 826 | 1.4 % |
| **OISD big (ABP)** (1DM's "OISD Abp Full" may be another edition) | 240,062 | 0 | 240,038 | 220,243 | 100 % |
| **1Hosts Lite** (hosts) | 202,868 | – | 202,868 | 189,569 | – |
| Online Malicious URL (urlhaus-filter) | 6,817 | 0 | 0 | 0 | 0 % (stale 2022 mirror, URL-path rules) |

Union views (exact-domain sets): app today **72,739** → + 1Hosts Lite **262,308** → + OISD **292,982** → + all usable EasyList/EasyPrivacy/uBlock/AdGuard **238,158** (+165,419) → everything **553,183**. GPL-family domains not already covered by app + 1Hosts + OISD: **107,034**. Conflicts with the project's `never_block`/`allow` hosts (36): **0**.

**Probe check (120 well-known ad/tracker hosts; my hand-picked set, not a benchmark):** app baseline 118 · 1Hosts 104 · OISD 78 · AdGuard Base 52 · EasyList adservers 46 · AdGuard Tracking 43 · AdGuard Mobile 29. The two baseline misses (`connect.facebook.net`, `settings-win.data.microsoft.com`) are added by **none** of the lists. **So the value of these lists is the long tail** (e.g. EasyList's adservers file is mostly obscure hash-named ad hosts such as `||000491b06a.com^`), which only a real HAR corpus can measure (Phase 1 §6.2).
**Raw false positives on 99 legitimate probes** (before the project's `allow.txt`): 1Hosts 6, OISD 3, AdGuard Tracking 7, EasyPrivacy 1, baseline 5 raw (the project's `allow.txt` neutralises most vendor dashboards, but in my earlier engine run `sentry.io` and `analytics.google.com` were still blocked); AdGuard Base, AdGuard Mobile, EasyList adservers 0. The offenders are analytics **dashboards** (`analytics.google.com`, `mixpanel.com`, `app.segment.com`, `app.amplitude.com`, `insights.hotjar.com`, `adjust.com`) — exactly what `allow.txt` is for, so every list addition must extend it and re-run the gate.
*Caveats:* AdGuard figures come from the FiltersRegistry files (much larger than the 1.0–1.2 MB 1DM shows), so they may overstate what 1DM has; regional lists, Fanboy's lists and Peter Lowe's list were **not** fetched/measured.

### 6A.3 Classification of the 28 entries
| Entry(ies) | Nature | DNS-usable? | License (source) | Class |
|---|---|---|---|---|
| Manual filters | user's own rules | yes | – | **Import** `||domain^` / `@@||domain^` lines into the existing user allow/block rules |
| 1Hosts (Lite) | hosts list | yes (202,868) | **MPL-2.0** (repo LICENSE) | **P** — bundle at build time |
| StevenBlack (already used) | hosts list | yes (72,233) | **MIT** (repo license) | **P** — keep |
| EasyList, EasyPrivacy, EasyList German | ABP | subset (see 6A.2) | **GPL-3.0-or-later OR CC BY-SA 3.0-or-later** (easylist.to licence page) | **C** |
| uBlock filters, Privacy, Badware risks, Resource abuse | ABP (uAssets) | small subset; **Badware** is valuable (1,153 malware/scam hosts) | **GPL-3.0** (uAssets LICENSE) | **C** |
| AdGuard Base, Mobile Ads, (Tracking), Turkish, Russian, Spanish/Portuguese | ABP | subset (Base 59,842; Mobile 946) | **GPL-3.0** (list header → AdguardFilters LICENSE) | **C** |
| OISD (Abp Full) | domain-only ABP | yes (240,038) | **GPL-3.0** (sjhgvr/oisd LICENSE) | **C** |
| Peter Lowe's list | hosts | yes | custom "McRae GPL" per uBlock Origin's licence table — **not verified, not fetched** | **Skip** (StevenBlack already merges it; confirm provenance) |
| Online Malicious URL Blocklist | URL-path rules | no (0 in my copy) | not verified | **Later:** use a *domain/hosts* export of URLhaus after verifying its license |
| Fanboy's Indian, ABPindo, other regional ABP | ABP | unknown (not measured) | varies (several regional EasyList lists are CC BY-NC-SA — non-commercial) | **C, optional "Regional" group; measure first** |
| uBlock Unbreak, uBlock/AdGuard/Fanboy's Annoyances, EasyList Cookie | browser exceptions / cosmetic / cookie banners | **no** | – | **N** — show as "Browser-only (not applicable to DNS)" or hide |
| AakList, FFAdblock, Adblock Warning Removal | anti-adblock-killer scripts | **no** | – | **N** |
| 1DM filters | app-specific, 1.3 KB | unknown | unknown | **Skip** |

### 6A.4 Decision (follow unless the owner decides otherwise)
- **Class P (permissive: MIT / MPL-2.0 / CC0) → bundled at build time**, offline, pinned, as in Phase 1 §4 A.2: keep StevenBlack, **add 1Hosts Lite**. No runtime network. Needs `MPL-2.0` text already shipped (`MPL-2.0.txt`), attribution in `THIRD_PARTY_NOTICES.md`.
- **Class C (copyleft: GPL-3.0 / CC BY-SA) → NOT bundled.** Offer them as an **optional, user-controlled "Hosts & filters" screen** (like 1DM): the app downloads the list **from the official publisher URL on the user's request** and converts it on-device (6A.6–6A.7). The app does not redistribute the list text, which also avoids the GPL-vs-Apache bundling question. *This is the one place the invariant "the installed app never downloads list updates" changes — OWNER DECISION (6A.10); default OFF; `PRIVACY.md` must be updated.*
- **Class N → shown as "Browser-only" or hidden.** Never imply they work.
- Never bundle, and never download by default, anything whose license I could not verify.

### 6A.5 `AbpDomainExtractor` (on-device and in `tools/filter-compiler`; one spec, two implementations with shared test vectors)
Accept only network rules of the form `||<domain>^` with options **absent (tier A)** or ⊆ {`third-party`,`3p`,`all`,`important`} (tier B). Reject everything else and count it by reason.
- Map to `BLOCK SUFFIX` (an ABP `||d^` covers subdomains). Hosts/domain-list formats map to `BLOCK EXACT` (as today).
- **Ignore exceptions (`@@…`) from third-party lists** — project `allow.txt`, never-block and user rules are the only allow authority.
- Reject: cosmetic/scriptlet markers (`##`, `#@#`, `#?#`, `#$#`, `#%#`, `$$`), `badfilter`, `||domain` without `^`, any `/` path, `*` wildcards, regex (`/…/`), IP literals, single-label names, public suffixes (PSL), non-ASCII (IDN must be punycode), labels > 63, names > 253, invalid characters.
- **Linear-time, bounded parsing** (no backtracking regexes on untrusted input), UTF-8 with BOM/CRLF tolerance, hard caps (≤ 20 MB per list, ≤ 600 000 accepted domains per list), invalid-line budget (> 0.5 % ⇒ reject the whole list).
- **Tests:** golden vectors for each reject reason; the measured table in 6A.2 reproduced from pinned copies of 3 lists; fuzz test (random bytes, huge lines, 1 M-label names) must neither crash nor allocate beyond the cap.

### 6A.6 Catalogue and UI ("Hosts & filters", One UI style per §5)
- A fixed in-app **catalogue** (JSON asset): `id, name, url (official), license, licenseUrl, class (P|C|N), syntax (hosts|abp|domains), expectedSizeKb, region, recommended`. No arbitrary URL in v1; a "custom list" field may come later with an explicit warning.
- Screen (card list like 1DM): per list — switch, **domains accepted**, size, **last updated**, license chip, ⋮ (details/refresh/remove); toolbar **Refresh all**, **Search**; header "Total active domains". Class N entries are shown disabled with "Browser-only". A **profile** selector sits on top: **Light** (≈ 72.7 k, baseline) · **Balanced** (≈ 262 k, + 1Hosts Lite; default) · **Strict** (≈ 553 k, + OISD + AdGuard + EasyList/EasyPrivacy; needs downloads). Show the false-positive warning from 6A.2 next to AdGuard Tracking and 1Hosts.
- Every blocked entry in Logs and "Why blocked?" shows its **source list name** (the rule binary already stores a `sourceId`).
- A **licence & attribution** screen lists each list with its license and source URL.

### 6A.7 Download → compile → activate pipeline (Class C only; **after owner approval**)
1. **Trigger:** manual *Refresh*, plus optional weekly `WorkManager` job (unmetered network + charging only; default off). Never on every start.
2. **Fetch:** HTTPS only (`usesCleartextTraffic=false` already), ≤ 3 redirects, same-scheme, 15 s connect / 60 s read timeout, size cap, conditional GET (`ETag`/`Last-Modified`), generic `User-Agent`, **no device identifiers, no cookies**; each request reveals the user's IP and the chosen lists to the publisher — say so in the UI and `PRIVACY.md`. The hosts used (`raw.githubusercontent.com`, `easylist.to`, `adguardteam.github.io`, `oisd.nl`, …) must be in `never_block` so the blocker never blocks its own updates.
3. **Parse** with 6A.5, streaming, off the main thread, low priority.
4. **Gates before activation** (all must pass, otherwise keep last-known-good and show why): format sniff (reject HTML/JSON error pages); invalid ratio ≤ 0.5 %; accepted count within ±50 % of the previous version unless first download; **no accepted rule may match any `never_block`/project-allow host**; the legitimate-probe corpus (Phase 1 §6.2 + Appendix A) must show **0 new false positives**; build ≤ memory budget.
5. **Compile** all enabled lists + bundled Class P into one `RuleSet` binary in app-private storage (`noBackupFilesDir`), **atomic rename**, hot-swap via `RuleSetHolder`; keep the previous binary for rollback. The Kotlin writer must be **byte-compatible with the Python compiler** (golden-file test).
6. **Rollback:** if the health check or the first-minute self-test fails (legit probe blocked, or DNS failure rate spikes), revert automatically and disable the offending list.
7. **Size/time budget (to be measured, not claimed):** estimated binary ≈ 27.7 B/rule (measured on 73,213 rules) ⇒ Light ≈ 2.0 MB, Balanced ≈ 7.3 MB, Strict ≈ 15.3 MB; the on-device sort must use primitive arrays/external merge — no per-rule objects; target "Strict build completes on a mid-range phone in a bounded time and heap you record in `docs/performance.md`".

### 6A.8 Class P build changes (`tools/filter-compiler`)
Add `1Hosts Lite` to `sources.json` (`license: MPL-2.0`, pinned 40-hex revision, `syntax: domains`, SHA-256); keep the never-block gate, `allow.txt`, invalid-ratio and determinism tests; extend `allow.txt` for every raw FP in 6A.2 that the new list introduces (e.g. `www.hotjar.com`, `insights.hotjar.com`, `app.segment.com`, `app.amplitude.com`) with a rationale comment; re-run the probe corpus and record before/after counts. Do **not** use the Python compiler for Class C data.

### 6A.9 Acceptance tests
| ID | Test | Expected |
|---|---|---|
| F-T1 | Rule-consistency (existing) with 1Hosts added | every BLOCK row blocks unless host is never-block |
| F-T2 | Probe corpus before/after 1Hosts | no regression in legit set; blocked count ≥ baseline |
| F-T3 | `AbpDomainExtractor` vectors + fuzz | all reject reasons covered; no crash; caps enforced |
| F-T4 | Corrupt / HTML / oversized / truncated download | rejected, last-known-good kept, user-visible reason |
| F-T5 | Update that would block a never-block host | rejected |
| F-T6 | Toggle a list off | its exclusive domains stop blocking, counts update, no restart |
| F-T7 | Kotlin vs Python binary writer | byte-identical output on the same input |
| F-T8 | Offline refresh | clear error, no state change |
| F-T9 | Licence screen | every shown list has license + URL; Class N entries never enable |
| F-T10 | Manual filters import | `||d^` → user block, `@@||d^` → user allow; invalid lines reported |

### 6A.10 OWNER DECISIONS (do not decide in code)
1. Approve the **optional list downloader** (changes the "no list downloads" invariant; requires `PRIVACY.md` update) — or restrict the app to Class P only.
2. Legal view on **bundling** GPL/CC BY-SA lists in an Apache-2.0 app (this spec **does not bundle** them; I am not a lawyer).
3. Default profile (Light / **Balanced**) and whether Strict is offered.
4. Verify licenses/provenance of Peter Lowe's list, URLhaus exports and any regional lists before listing them; also the upstream licenses behind StevenBlack's merged data (already shipped).

---

## 7. WP-6 — Repository, supply chain, release (fixes D-5, D-6) and the release runbook

### 7.1 Repo hygiene
```bash
git rm -r --cached --ignore-unmatch common/build data-store/build vpn-engine/build ml-engine/build
git rm --cached --ignore-unmatch patch.py tools/filter-compiler/compiler.patch
git rm -r --ignore-unmatch .planning docs/archive
# .gitignore must contain: **/build/  .gradle/  .kotlin/  *.hprof  __pycache__/  local.properties
bash scripts/check-no-binaries.sh && git ls-files | grep -E '(^|/)build/|\.class$' && echo "STILL TRACKED" || echo clean
```
Add a CI step that **fails** if any tracked path matches `(^|/)build/|\.class$|\.dex$`. (History purge of old APKs/build outputs is an **owner decision**.)

### 7.2 Supply chain
- **Restore `gradle/verification-metadata.xml`** (`./gradlew --write-verification-metadata sha256 help`) and **remove `--dependency-verification=off`** from every workflow.
- Remove the **README `sed` + `git push` step** from `release.yml` (CI must not commit to `main`). Use dynamic badges (`shields.io/github/v/release/pkgtm2419/un-blocker`) so no rewrite is needed.
- Keep: SHA-pinned actions, wrapper validation, Dependabot, binary guard, tag-vs-`versionName` guard, mandatory signer pin for `v*`.

### 7.3 Find why `release.yml` fails (don't guess)
> **Update 2026-10-10:** answered in §7A.2 — every production-tag run fails and only test-tag runs succeed; the most likely cause is missing `UB_RELEASE_*` secrets (fail-closed), but confirm with the run log as described there.
`gh run list --workflow release.yml -L 5` → `gh run view <id> --log-failed`. Fix the root cause (likely the new README/git-push step, `lintRelease`, or an R8 error) **before** creating any new tag. Add `assembleReleaseTest` to `android.yml` so R8 problems fail on PRs. I could not read these logs (not public).

### 7.4 Version and tags
- Next version **`2.0.2` / versionCode 10**. Do **not** push tags until `versionName` matches (the release guard correctly refused `v1.4.2` earlier for exactly this reason).
- The empty `v2.0.0`/`v2.0.1` GitHub releases: owner decision to delete the releases/tags (never force-move published tags silently; document in the changelog).

### 7.5 Runbook — test → push → build → release → verify
> **Superseded by §7A.4-H** (dry run → `test-v` tag → `v` tag on the same commit; never move tags). The `verify-release.sh` described below is replaced by the tested script in `ci-fixed/scripts/`.
```bash
# 1. local gates (must all pass)
bash gradlew --stop && bash gradlew clean testDebugUnitTest lintDebug assembleDebug assembleReleaseTest
( cd tools/filter-compiler && python3 -m unittest test_compiler test_release ) && ( cd tools/eval && python3 -m unittest test_har_hosts )
# 2. commit per work package, open PR, wait for "Android checks" = success, merge
# 3. bump versionName 2.0.2 / versionCode 10 in app/build.gradle.kts, merge, then:
git tag test-v2.0.2 && git push origin test-v2.0.2 && gh run watch
# 4. verify the published test build (script below); only then, if release keystore + UB_RELEASE_SIGNER_SHA256 exist:
git tag v2.0.2 && git push origin v2.0.2 && gh run watch && bash scripts/verify-release.sh v2.0.2
```
`scripts/verify-release.sh <tag>` (also run as the last workflow step): `gh release download` → `sha256sum -c SHA256SUMS` → `aapt2 dump badging` shows `versionName == tag` → assets contain `dns-rules.bin` and **no** `dns-rules.tsv` → for `v*`: **not debuggable**, `apksigner verify --print-certs` equals the pinned signer, `mapping.txt` attached; for `test-v*`: release is a prerelease. Then on a device: install, toggle ON, load `http://pagead2.googlesyndication.com/` (must fail fast), load a normal site (must work), reboot (auto-start), toggle OFF (everything normal).

---

## 7A. WP-8 — GitHub Actions, scripts and repository configuration (complete audit, fixes, owner checklist)

> **Validated deliverable:** `ci-fixed/` (also `ci-fixed.zip`) contains drop-in replacements for both workflows, Dependabot and all release scripts. They pass `actionlint` 1.7.7 (with shellcheck on every `run:` block), `zizmor` 1.30.1 in **auditor** mode (strictest; **0 findings**, versus 26 findings / 22 suppressed on the current files), `shellcheck`, **22 shell tests** and **16 Python tests**. They have **not** been run on GitHub.

### 7A.1 Scope and method
**Read in full:** `.github/workflows/android.yml`, `release.yml`, `.github/dependabot.yml`, `scripts/{check-no-binaries.sh,verify-apk-signature.sh,verify-release.sh,release-policy.py}`, `gradle/wrapper/gradle-wrapper.properties`, `gradle.properties`, `settings.gradle.kts`, root and `app/build.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`, `.gitattributes`, the tracked-file inventory, all tags and their commits, public release pages/badges, and the CI-related claims in `docs/`.
**Tools run:** actionlint, zizmor (default and auditor), shellcheck, the Python test suites, a tag-checkout simulation, and the new gates against the real repo.
**Could not do:** read failed run logs (not public) or see repository settings (branch/tag protection, environments, secrets).
**Correction of my own hypothesis:** I suspected the README step's `git checkout main` fails after a shallow tag checkout. I reproduced the checkout and it **works**, so that step is *not* the cause of the failures (it is still a bad design — CI-5).

### 7A.2 What actually happened to the releases (public evidence)
| Tag | Commit | `release.yml` result | Published assets | Note |
|---|---|---|---|---|
| `test-v1.4.1` | `fb384c8` | success | APK + `SHA256SUMS` | last fully published build before 2.0 |
| `v1.4.2`, `test-v1.4.2` | `a8eeb2e` | failing | none | tag ≠ `versionName 1.4.1` — the guard worked as designed |
| `test-v2.0.0` | `d63549e` | **success** | `ub-blocker-2.0.0.apk` + `SHA256SUMS` | **the APK the owner tested** |
| `v2.0.0` | `085d54b` | failing | none | **different commit than `test-v2.0.0`** for the same version |
| `v2.0.1` | `772ce8b` | failing | none | tagged **before** 4 later workflow-fix commits; **no `test-v2.0.1` tag exists → no 2.0.1 APK has ever been published** |

- The GitHub releases titled just `v1.4.2`, `v2.0.0`, `v2.0.1` (no assets) are empty shells created by hand; the workflow titles its releases "Un-Blocker X test build".
- History shows trial-and-error on tags: an empty commit named "trigger: rebuild after deleting github release", and 6 commits iterating the README-rewrite step. There is no way to test a tag-triggered workflow without publishing, which is why a dry-run mode exists in the new workflow.
- **Every production (`v*`) run has failed; only test-tag runs succeed.** Most likely cause (not confirmed — logs are not public): the production path is **fail-closed on missing release-signing secrets/variable** (`UB_RELEASE_*`, `UB_RELEASE_SIGNER_SHA256`) that were never configured. **Confirm with:** `gh run list --workflow release.yml --limit 10` then `gh run view <run-id> --log-failed`; look for the failing step name ("Validate tag and release policy" / "Prepare signing keys" ⇒ missing secrets; "Test and build APK" ⇒ a build problem).

### 7A.3 Findings register
| ID | Sev | Finding | Evidence | Fix |
|---|---|---|---|---|
| CI-1 | **High** | **Dependency verification disabled**: `--dependency-verification=off` in `android.yml` and `release.yml`; `gradle/verification-metadata.xml` was deleted. The cause was metadata that did not cover new plugin/dependency versions; it was hand-patched (`patch.py`) and then switched off (`docs/version-2.0.0-fixes.md` §5). | workflows; `git ls-files gradle/` | 7A.4-C |
| CI-2 | **High** | Production release path has **never succeeded** (3 failing `v*` runs); no 2.0.1 artifact exists | 7A.2 | 7A.4-G, 7A.4-H |
| CI-3 | **High** | **Signing secrets are exposed to the whole Gradle build** (tests, lint, third-party plugins) while verification is off; zizmor: 16× `secrets-outside-env` | `release.yml` single job | 7A.4-A (split jobs + Environments) |
| CI-4 | **High** | **"Post-publish Verification" is a stub**: `scripts/verify-release.sh` only runs `gh release view`; it checks no checksum, version, debuggable flag, signer or assets | script | 7A.4-B |
| CI-5 | Medium | CI **commits to `main`** from a tag run via ~11 brittle `sed` rewrites of README (and `[skip ci]`, so those commits get no CI) | `release.yml` README step | remove; dynamic badges |
| CI-6 | Medium | `workflow_dispatch` checks out the **dispatch branch, not the tag**, then publishes under the input tag (wrong source under a tag name) | `actions/checkout` has no `ref:` | `ref:` fix in new workflow |
| CI-7 | Medium | **Tag hygiene**: `v2.0.0` and `test-v2.0.0` on different commits; `v2.0.1` tagged before its fixes; empty "trigger" commits; no tag protection | 7A.2 | policy rule + tag ruleset |
| CI-8 | Medium | `gh release upload --clobber` overwrites published assets; empty hand-made releases not handled | `release.yml` | immutable assets; upload into empty release |
| CI-9 | Medium | **Python tests are not run in CI and are broken**: 9 of 13 compiler tests error (CLI/API changed); `docs/improvement-progress.md` still claims "13 tests passing" | I ran them | 7A.4-F |
| CI-10 | Medium | Reports are uploaded only on success (no `if: always()`) and only for `app/` → a failing build leaves no diagnostics; library-module reports are never collected | `android.yml` | new workflow |
| CI-11 | Medium | **R8/release build is exercised only on a tag** (PR CI builds debug only); `lintRelease` runs only on tags | workflows | `assembleReleaseTest lintRelease` in PR CI |
| CI-12 | Medium | `check-no-binaries.sh` ignores `build/`, `.class`, `.dex` and `.gitignore` lacks `**/build/` → **1,255 build outputs tracked** (common 200, data-store 471, ml-engine 285, vpn-engine 299) | gate run on HEAD | 7A.4-D |
| CI-13 | Medium | zizmor: no top-level `permissions` in `android.yml`; job-wide `contents: write` in `release.yml`; `persist-credentials` left on (artipacked ×2); no `timeout-minutes`/`concurrency` in `android.yml`; Dependabot lacks `cooldown` (×2); anonymous jobs | zizmor auditor | new workflow |
| CI-14 | Medium | **SDK mismatch**: all modules use `compileSdk/targetSdk 35`, both workflows install only `platforms;android-36` → Gradle downloads platform 35 on every run (non-hermetic) | `build.gradle.kts` vs workflows | install 35; pin `buildToolsVersion` |
| CI-15 | Medium | `gradle-wrapper.properties` has **no `distributionSha256Sum`** (wrapper validation checks only the jar, not the downloaded distribution) | file | 7A.4-E |
| CI-16 | Low | `compileDnsRules` declares an incomplete, hard-coded input list (StevenBlack vendor commit path; no other vendored files or helper modules). `sources.json` is also an input, so most changes still invalidate it, but the declaration is a maintenance trap once WP-7 adds lists | `app/build.gradle.kts` | 7A.4-E |
| CI-17 | Low | `.gitattributes` has no `* text=auto`: CRLF/LF churn (two thirds of the 2.0 diff was line endings); a one-time renormalization touches **94 files** | measured | 7A.4-D |
| CI-18 | Low | Leftovers: `patch.py`, `.planning/`, `tools/filter-compiler/compiler.patch`, spec files in `docs/archive/`; JDK tarball/dirs referenced in `.gitignore` | tracked files | 7A.4-D |
| CI-19 | Low | Hard-coded test signer digest in the workflow; redundant `JAVA_HOME` env and `-Dorg.gradle.java.home`; unquoted `${PRERELEASE_FLAG}` (shellcheck SC2086) | actionlint | new workflow |
| CI-20 | Low | Toolchain/dependency versions are old (e.g. Compose BOM `2024.10.00`, Kotlin 2.0.21, Hilt 2.51.1); targetSdk dropped from 36 (1.4.x) to 35 in the rewrite — confirm it is intentional and check Google Play's current target-API requirement (I could not verify it) | `libs.versions.toml` | Dependabot + owner decision |
| CI-21 | Low | `docs/` claims contradict reality ("13 tests passing"; disabling verification described as a fix) | docs | correct |
| CI-22 | Owner | **Repository settings** unknown/likely missing: branch ruleset, tag ruleset, Environments with required reviewers, secret scanning + push protection, default read-only token | cannot see | 7A.4-G |

### 7A.4 Solutions in detail

**A. Workflows (files in `ci-fixed/.github/workflows/`)**
```
android.yml   hygiene (tracked-file gate, script tests, Python tests, compiler determinism)   ║ parallel
              build   (wrapper validation → verification-metadata must exist → tests + lintDebug + lintRelease
                       + assembleDebug + assembleReleaseTest; reports/APKs uploaded even on failure)
release.yml   preflight (tag format, tag == versionName, production tag reachable from main,
                         v1.2.3 and test-v1.2.3 must be the SAME commit, hygiene gate)       no secrets
              → verify  (tests + lintRelease + R8 smoke build, no Gradle cache)               no secrets
              → publish (environment: test-release | production; check secrets by NAME → build signed APK
                         → verify (non-debuggable + pinned signer) → checksums → publish immutable assets
                         → post-publish verification that downloads and re-checks the release)  only job with write access
```
Design decisions and the findings they close: top-level `permissions: {}` + per-job least privilege (CI-13); signing secrets only in the `publish` job behind a GitHub **Environment** (CI-3); no Gradle cache in the publishing workflow — zizmor rated it a cache-poisoning risk (CI-3); `ref:` from the tag, never the dispatch branch (CI-6); `dry_run` input defaults to **true** for manual runs so workflow bugs are found without publishing (CI-2/7); no README mutation — use `https://img.shields.io/github/v/release/pkgtm2419/un-blocker` badges (CI-5); no `--clobber` (CI-8); `if: always()` report uploads from `**/build/...` (CI-10); `timeout-minutes` and `concurrency` everywhere (CI-13); `platforms;android-35` + `build-tools;35.0.0` and a dynamic build-tools lookup instead of a hard-coded path (CI-14); `persist-credentials: false` (CI-13); `UB_TEST_SIGNER_SHA256` repository variable with the current test digest as fallback (CI-19). **Names kept identical to the repo:** secrets `UB_BLOCKER_TEST_KEYSTORE_BASE64/…PASSWORD/KEY_ALIAS/KEY_PASSWORD`, `UB_RELEASE_KEYSTORE_BASE64/…PASSWORD/KEY_ALIAS/KEY_PASSWORD`, variable `UB_RELEASE_SIGNER_SHA256`; Gradle env vars `UB_BLOCKER_KEYSTORE_FILE…`, `UB_RELEASE_KEYSTORE_FILE…` (verified against `app/build.gradle.kts`; empty values are treated as unset by `isNullOrBlank`).

**B. Scripts (files in `ci-fixed/scripts/`, all tested)**
- `check-no-binaries.sh` — rejects tracked `*.apk|aab|jks|keystore|p12|pfx|class|dex|hprof|so|pyc`, any `build/`, `.gradle/`, `.kotlin/`, `.idea/`, `__pycache__/`, `captures/`, `local.properties`, `.env`. On today's HEAD it reports **1,255** files. (9 tests.)
- `release-policy.py preflight|secrets` — fail-closed policy: strict tag regex, tag == `versionName`, production tag must be an ancestor of `origin/main`, **one version ↔ one commit** across `vX` / `test-vX`; writes outputs to `$GITHUB_OUTPUT`; the `secrets` mode prints **names only**, never values. (16 tests.)
- `verify-release.sh <tag> [--dir DIR]` — downloads (or reads) the assets and checks: prerelease flag matches the tag type; `SHA256SUMS`; `versionName == tag`; required assets present (`assets/dns-rules.bin` by default) and forbidden ones absent (`assets/dns-rules.tsv`); production: **not debuggable**, signer equals the pinned digest, `mapping.txt` attached. Override `REQUIRED_ASSETS`/`FORBIDDEN_ASSETS` per architecture. (13 tests with stubbed `aapt2`/`apksigner`: tampered APK, wrong version, debuggable production APK, wrong/missing signer pin, missing mapping, forbidden/missing asset, bad tag.)

**C. Restore dependency verification (CI-1)**
```bash
# remove every --dependency-verification=off (the new workflows have none), then regenerate for EVERY task CI runs:
bash gradlew --write-verification-metadata sha256 \
  testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease assembleReleaseTest
git diff --stat gradle/verification-metadata.xml     # review: only expected groups (androidx, kotlin, google, hilt, room…)
bash gradlew testDebugUnitTest                       # must pass with verification ON
```
Never hand-edit hashes (`patch.py` did): a hash taken from a failing build is trust-on-first-use, so review each new group, and delete `patch.py`. CI now fails if the file is missing (`test -s gradle/verification-metadata.xml`). **Negative test:** on a throwaway branch change one hash — the build must fail.

**D. Repository hygiene (CI-12, 17, 18) — three separate commits**
```bash
# 1. stop tracking build outputs and leftovers (no history rewrite)
git rm -r --cached --ignore-unmatch common/build data-store/build vpn-engine/build ml-engine/build
git rm -r --ignore-unmatch .planning patch.py tools/filter-compiler/compiler.patch docs/archive
# 2. .gitignore additions (measured: covers all 1,255 tracked build files)
printf '%s\n' '**/build/' '.gradle/' '.kotlin/' '*.hprof' '__pycache__/' '*.pyc' 'local.properties' 'jdk*/' '*.tar.gz' >> .gitignore
# 3. .gitattributes (then ONE normalization commit, nothing else in it; measured: 94 files)
cat >> .gitattributes <<'EOF'
* text=auto eol=lf
*.bat text eol=crlf
*.png binary
*.jpg binary
*.webp binary
*.jar binary
*.bin binary
*.tflite binary
*.keystore binary
EOF
git add --renormalize . && git commit -m "chore: normalize line endings"
```
History purge of the old build outputs/APKs is an **owner decision** (rewrites history).

**E. Gradle-side changes (CI-14, 15, 16)**
```kotlin
// every module: pin the build tools that CI installs (match the workflow packages) and keep SDK levels consistent
android { compileSdk = 35; buildToolsVersion = "35.0.0" }
// app/build.gradle.kts — replace the hard-coded input list of compileDnsRules
inputs.dir(rootProject.file("tools/filter-compiler")).withPropertyName("filterCompiler")   // includes vendor/ and helper modules
inputs.files(fileTree("src/main/assets") { include("*.txt", "*.dat") })
// then exclude caches:  tools/filter-compiler/**/__pycache__
```
```properties
# gradle/wrapper/gradle-wrapper.properties — take the checksum from https://gradle.org/release-checksums/ for the EXACT version in distributionUrl
distributionSha256Sum=<sha256 of the gradle-<version>-bin.zip>      # do not guess; then run: bash gradlew wrapper --gradle-version <version>
```
Also: decide whether `targetSdk 35` (down from 36 in 1.4.x) is intended, and let Dependabot propose the stale dependency bumps.

**F. Python tooling tests and docs (CI-9, 21)** — update the 9 failing compiler tests to the current `compile_sources()`/CLI (`--output-assets`, `--output-test`), keep them in CI (`android.yml` `hygiene` job), delete the stale `tools/filter-compiler/test_release.py` if it tests the old `release-policy.py`, and correct `docs/improvement-progress.md` / `docs/version-2.0.0-fixes.md`: state only test counts that CI logs show, and do not describe disabling a security control as a fix.

**G. Repository settings and secrets (owner checklist — nothing here can be done from code)**
| Setting | Value |
|---|---|
| Settings → Actions → General | Workflow permissions: **read repository contents**; require approval for workflows from outside collaborators |
| Ruleset for `main` | require PR; required checks **"Repository hygiene and tooling tests"** and **"Build, unit tests, lint and R8 smoke build"**; block force-push and deletion |
| Ruleset for tags `v*` and `test-v*` | restrict creation to maintainers; **block update and deletion** (tags immutable) |
| Environment `test-release` | secrets `UB_BLOCKER_TEST_KEYSTORE_BASE64`, `…_KEYSTORE_PASSWORD`, `…_KEY_ALIAS`, `…_KEY_PASSWORD`; variable `UB_TEST_SIGNER_SHA256` |
| Environment `production` | secrets `UB_RELEASE_KEYSTORE_BASE64`, `…_KEYSTORE_PASSWORD`, `…_KEY_ALIAS`, `…_KEY_PASSWORD`; variable `UB_RELEASE_SIGNER_SHA256`; **required reviewer = owner**; deployment tags `v*` only |
| Code security | secret scanning + **push protection**, Dependabot alerts and security updates **on** |
| `CODEOWNERS` | `/.github/` and `/scripts/` owned by the maintainer |
Existing repository-level secrets keep working (an environment job can read them); moving them into environments adds the approval gate. **Creating the production keystore (owner):** `keytool -genkeypair -v -keystore ub-release.jks -alias ub -keyalg RSA -keysize 4096 -validity 10000`; base64-encode it for the secret (Linux `base64 -w0 ub-release.jks`, PowerShell `[Convert]::ToBase64String([IO.File]::ReadAllBytes("ub-release.jks"))`); read the digest with `keytool -list -v -keystore ub-release.jks` (SHA-256) into `UB_RELEASE_SIGNER_SHA256`; **back the keystore up offline — losing it means installed users cannot receive updates.** Google Play signing is a separate decision.

**H. Release procedure (replaces Phase 2 §5.2 and §7.5 where they conflict)**
1. Merge to `main` only with both required checks green. Bump `versionName`/`versionCode` in a normal PR.
2. **Dry run:** Actions → *Publish release APK* → Run workflow → tag of an existing test tag, `dry_run = true` (builds and verifies, publishes nothing).
3. Push **`test-vX.Y.Z`** → test prerelease (debuggable, test-signed) → install and run the device matrix (§8).
4. Push **`vX.Y.Z` on the same commit** (the policy rejects a different commit) → approve the `production` environment → signed, non-debuggable release.
5. **Never move, delete or re-push a tag, and never overwrite assets.** If anything fails, fix forward and bump the patch version. No empty "trigger" commits.
6. After publish, `verify-release.sh` runs automatically; run it again by hand against the final release.

### 7A.5 Acceptance tests
| ID | Test | Expected |
|---|---|---|
| CI-T1 | Hygiene gate on a clean repo / with a tracked `build/` file | passes / fails (9 tests exist) |
| CI-T2 | `actionlint` and `zizmor --persona auditor` on `.github` before each workflow change | 0 problems / 0 findings |
| CI-T3 | `workflow_dispatch` with `dry_run=true` | builds and verifies, **no release, no asset** |
| CI-T4 | `v*` tag with no `UB_RELEASE_*` secrets | fails at "Check signing secrets" listing **names only**, before any build; nothing published |
| CI-T5 | `v*` tag on a commit not on `main` | preflight fails |
| CI-T6 | `vX` and `test-vX` on different commits | preflight fails (tested) |
| CI-T7 | publish into an empty hand-made release / into a release that already has assets | succeeds / fails with "assets are immutable" |
| CI-T8 | tampered asset, wrong version, debuggable production APK, wrong signer, missing mapping | `verify-release.sh` fails each (13 tests) |
| CI-T9 | a failing unit test | reports for **all modules** are still uploaded |
| CI-T10 | change one hash in `verification-metadata.xml` on a throwaway branch | build fails |
| CI-T11 | Python suites (`tools/*`, `scripts/`) in PR CI | run and pass |
| CI-T12 | PR CI | builds `assembleReleaseTest` and runs `lintRelease` |
| CI-T13 | tamper `distributionSha256Sum` | Gradle wrapper download is rejected |

### 7A.6 Order of work
PR-A hygiene (7A.4-D steps 1–2) → PR-B restore dependency verification (7A.4-C) → PR-C workflows + scripts (`ci-fixed/`) with the Gradle changes (7A.4-E) and Python test repair (7A.4-F) → separate commit for line-ending normalization → **owner** completes 7A.4-G → dry run → `test-v` tag → device matrix → `v` tag. The new CI fails on today's HEAD by design until PR-A and PR-B land.

---

## 8. Test matrix — run all on at least two devices (one Samsung) and record results in `docs/device-test-results.md`

| # | Scenario | Expected |
|---|---|---|
| N-1 | Wi-Fi IPv4-only, protection ON | web, YouTube, speed test, WhatsApp call all work; DNS latency within §3.5 |
| N-2 | Wi-Fi dual-stack | same; IPv6 DNS answered with valid checksums |
| N-3 | Mobile IPv4 | same |
| N-4 | **IPv6-only mobile (464XLAT, e.g. Jio)** | same; no stalls |
| N-5 | Switch Wi-Fi ↔ mobile while ON | resolution recovers < 3 s, no manual restart |
| N-6 | Captive-portal Wi-Fi | portal login works (system resolver used, no hard-coded DNS) |
| N-7 | Private DNS Off / Automatic / Strict hostname | record interception per mode; banner only for strict **[verify]** |
| N-8 | Browser "secure DNS" (custom provider) | documented bypass, no breakage of other apps |
| N-9 | Another VPN active / Always-on VPN | clear message, no crash, state STOPPED |
| N-10 | Large DNS answer (TC → TCP) | resolves (TCP responder) |
| N-11 | Doze / sleep 1 h, then wake | still protected, no leak of state mismatch |
| N-12 | Reboot | auto-start works (or notification action if consent missing) |
| N-13 | App force-stop / swipe away | UI reflects reality on next launch |
| N-14 | Banking/UPI apps that refuse to run under any VPN | documented limitation; add **excluded apps** (split tunnel via `addDisallowedApplication`) as P2 |
| B-1 | Probe corpus (Appendix A) | ≥ baseline: 131/134 blocked, 100/103 legit allowed — no regression |
| B-2 | Vendor dashboards (`mixpanel.com`, `app.amplitude.com`, `one.newrelic.com`) | allowed; SDK endpoints blocked |
| B-3 | First-party ads (YouTube/Instagram feed) | **not** blockable by DNS — documented, not a failure |
| F-1 | Profile Light → Balanced (1Hosts Lite) | rule count = compiler manifest; probe corpus: legit set unchanged, blocked ≥ baseline |
| F-2 | Toggle a downloaded list off/on | exclusive domains unblock/block without restart; counts update |
| F-3 | Corrupt / HTML / oversized list download | rejected, last-known-good kept, reason shown |
| F-4 | List update that would block a never-block host | rejected |
| F-5 | Class N entry (e.g. anti-adblock killer, cookie list) | shown as "Browser-only", cannot be enabled |
| C-1 | Push a PR | `hygiene` and `build` checks run; R8 smoke build and `lintRelease` included; reports uploaded even on failure |
| C-2 | `test-v` tag with secrets in the `test-release` environment | prerelease with APK + `SHA256SUMS`; post-publish verification passes |
| C-3 | `v` tag without release secrets | fails by name before building; nothing published |
| C-4 | `v` tag after the owner configured `production` | waits for approval, then a signed, non-debuggable release that passes `verify-release.sh` |
| L-1 | 1 000 queries to one domain | **one** Logs row, "1000 calls", ≤ 50 timestamps in history |
| L-2 | Block then allow a domain from Logs | takes effect immediately; history keeps both decisions |
| U-1 | Pill bar: gesture nav, 3-button nav, landscape, fold, font 2.0× | bar floats, never clips content or controls |
| U-2 | TalkBack | all controls named; tab roles/selected state announced |

---

## 9. Cleanup (repo and machine) — dry-run first
Repo: §7.1, plus delete this file after the work (or keep under `docs/archive/` excluded from release assets). Machine: `git clean -ndx` (review) before any `-fdx`; keep keystores/`local.properties`; `bash gradlew --stop`; remove emulators/SDK images/caches you no longer need (`avdmanager list avd`, `sdkmanager --list_installed`, `du -sh ~/.gradle/caches`). Remove packages installed only for this project (`pip list --user`, `npm ls -g --depth=0`) after checking other projects don't use them.

---

## Appendix A — Measured baseline (1.4.2 engine; JVM harness, not a device)

**Probe corpus** (hosts I chose; existence of each not individually verified — a smoke test, not a real-world benchmark):

| Category | Result |
|---|---|
| Web ad networks | 49/49 blocked |
| Mobile ad SDKs | 16/16 |
| Trackers/analytics | 30/31 (missed `connect.facebook.net`) |
| OS telemetry | 10/11 (missed `settings-win.data.microsoft.com`) |
| Popup/redirect | 13/13 |
| Adult | 13/14 |
| Legit: OS essentials / popular / payments+gov / SaaS dashboards / keyword look-alikes | 12/12, 31/31, 12/12, 11/11, 12/12 allowed |
| Legit: dev tools | 22/25 (wrongly blocked: `sentry.io`, `app.datadoghq.com`, `analytics.google.com`) |

**Learner scenarios:** keyword + bursty queries → blocked after **2 windows (~65 s)** for 10/10 unlisted hosts; steady 10 s beacon → blocked after 100 s; **never**: single query, 45 s slow beacon, random-looking hosts with no keyword, keyword only in the registrable label (`bestads.com`); user ALLOW feedback works; 30 k-host flood evicted a confirmed record; throughput ≈ 88.8 k queries/s/thread (11.3 µs).
**Rule matcher on the shipped binary:** 73,213 rules, load 8 ms, p50 2.2 µs / p95 3.8 µs / p99 5.3 µs lookup; `adservice.google.com`, `ads.google.com`, `telemetry.microsoft.com` BLOCK; `mixpanel.com`, `one.newrelic.com` ALLOW EXACT; `api.mixpanel.com` BLOCK.

## Appendix B — Evidence from running the 2.0.x code

**B.1 New engine, IPv4 vs IPv6 DNS query (real `DnsPacketParser` + verbatim `extractDnsPayload`):**
```
IPv4 | parsed=example.com | payloadMatchesRealDnsQuery=true  | 29 bytes (real 29)
IPv6 | parsed=example.com | payloadMatchesRealDnsQuery=false | 69 bytes (real 29)   <- garbage forwarded upstream
IPv4 response: UDP checksum 0x0000 -> valid (legal for IPv4) ; IP header checksum ok
IPv6 response: UDP checksum 0x0000 -> INVALID (IPv6 receivers must drop it)
```
**B.2** Earlier [RUN] results for the 1.4.2 `TcpDnsResponder` (async, segmentation, duplicate-SYN handling) are in Phase 2; they are the reason Plan A keeps that engine.

## Appendix C — Not verified (needs Gradle or a device)
Gradle build and unit tests at 2.0.x; why the release workflow fails; behaviour on real networks (§8); R8 results; APK size; Samsung One UI 9 exact dimensions; contents/accuracy of `docs/version-2.0.0-fixes.md`.

## Appendix D — Filter-list measurements (2026-10-09)
Method: lists downloaded from GitHub raw (`easylist/easylist` pieces, `uBlockOrigin/uAssets`, `AdguardTeam/FiltersRegistry`, `sjhgvr/oisd` `abp_big.txt`, `badmojr/1Hosts` Lite, StevenBlack from the repo's vendored copy); a Python extractor implementing §6A.5 counted tier A/B domains and compared exact-domain sets with the app baseline (72,739). Probe sets are hand-picked (120 ad/tracker hosts, 99 legitimate hosts), suffix-matched for ABP lists and exact for hosts lists. Licenses read from repository LICENSE files (uAssets GPL-3.0, AdguardFilters GPL-3.0, FiltersRegistry LGPL-3.0, OISD GPL-3.0, 1Hosts MPL-2.0, StevenBlack MIT) and from easylist.to's licence page (GPL-3.0-or-later OR CC BY-SA 3.0-or-later). **Not measured:** regional lists, Fanboy's lists, Peter Lowe's list, a current URLhaus export, real-world long-tail effectiveness, on-device build time/memory.
