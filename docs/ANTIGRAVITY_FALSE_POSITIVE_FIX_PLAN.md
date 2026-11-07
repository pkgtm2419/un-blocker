# ANTIGRAVITY_FALSE_POSITIVE_FIX_PLAN.md

**Repo:** `pkgtm2419/un-blocker` · **Base:** `main` @ `90f3af0` (v2.3.0, versionCode 28) · **Written:** 2026-11-07
**Goal:** stop the app from blocking primary/legitimate domains, add a "learn one app at a time, block only what I approve" mode, and add an in-app CSV/log export so the domain list can be validated outside the phone and fed back as fixes.

> Everything in section 1 was found by reading the code at `90f3af0` and replaying the parser logic (ported to Python) against live uBlock Origin lists downloaded on 2026-11-07. The Kotlin was **not** compiled and the app was **not** run on a device. Treat section 1 as strong leads, and confirm each one with the failing tests in WP1-WP3 before fixing it.

---

## 0. How to run this in Antigravity

Put this file in the repo root and give the agent this prompt:

```text
You are working in pkgtm2419/un-blocker (Android, Kotlin, Gradle, v2.3.0).
Read ANTIGRAVITY_FALSE_POSITIVE_FIX_PLAN.md completely, then execute WP1..WP10 in order.
For each WP: (1) write the failing tests first, (2) implement, (3) run the WP's test command,
(4) commit with the given message, (5) append one line to docs/improvement-progress.md.
Ship in two releases: v2.3.1 = WP1-WP4 + WP9 (hotfix), v2.4.0 = everything else.
If a step needs something you cannot do (physical device, signing key, network), write it to
BLOCKERS.md and continue with the next WP. Never weaken CI gates, signing policy, or the
never-block gate. Finish by writing docs/qa/2026-11-fp-fix-report.md.
```

**Agent rules (apply to every WP):**
1. No server, no analytics, no new network calls. The only network use stays the existing filter-list downloads.
2. Keep the single START button flow. New screens are reached from Logs (overflow menu) or Settings, and reuse `FloatingPillNavBar`, `SkeuomorphicCard` and the existing theme.
3. No new dangerous permissions. Do **not** add `QUERY_ALL_PACKAGES`; use a `<queries>` launcher intent instead (WP7). File export uses the Storage Access Framework only.
4. Fail open: if any new code throws, the DNS query is forwarded, never answered with a block.
5. Read `CONTRIBUTING.md`, `docs/release-signing-and-upgrade-policy.md` and `.github/workflows/*.yml` before touching release files.
6. Keep the dev machine clean: do not commit `build/`, downloaded lists, or tool installs. List any tool you installed only for this work in the final report and remove it.

---

## 1. Verified findings (root causes of primary domains being blocked)

| # | Sev | Finding | Where | Evidence |
|---|-----|---------|-------|----------|
| F1 | **Critical** | `parseLine` cuts every line at the first `$` and treats what is left as a **whole-domain** rule. Browser-only rules like `\|\|facebook.com^$removeparam=mibextid` become `BLOCK SUFFIX facebook.com`. | `logic/rules/ExtensionRuleStore.kt` → `parseLine()` | Replay on 5 uBlock lists: **3,923** option rules became DNS blocks. Primary hosts hit: `facebook.com`, `instagram.com`, `youtube.com`, `reddit.com`, `open.spotify.com`, `twitch.tv`, `indiatimes.com` |
| F2 | **Critical** | `parseLine` strips everything after the first `#` as a comment. Cosmetic/scriptlet rules like `www.youtube.com##+js(...)` become `BLOCK EXACT www.youtube.com`. Even **exception** lines (`mail.google.com#@#...`) become blocks. | same function | Replay: **3,800** cosmetic rules became blocks. Hit: `www.youtube.com`, `m.youtube.com`, `docs.google.com`, `mail.google.com`, `x.com`, `www.reddit.com`, `old.reddit.com`, `linkedin.com`, `paypal.com`, `bing.com`, `microsoft.com`, `ndtv.com`, `timesofindia.indiatimes.com`, `economictimes.indiatimes.com`, `hindustantimes.com`, `quora.com`, `www.twitch.tv` |
| F3 | **Critical** | A single-label rule is accepted: uBlock badware has `\|\|com^$doc,ipaddress=...` → `BLOCK SUFFIX com`. `matchRule()` walks every parent suffix **including the TLD**, so every `*.com` host that is not on the 62-host never-block list or the user allowlist can match. | `ExtensionRuleStore.parseLine()` + `matchRule()` parent loop | Replay shows the rule is emitted. Matching behaviour is from code reading, so confirm with the WP1 test |
| F4 | High | Downloaded lists are never checked against never-block. `compiler.py:check_never_block` only checks the vendored StevenBlack file + seeds at build time. The ~40 lists downloaded at runtime bypass it. | `tools/filter-compiler/compiler.py`; `data/repository/ExtensionRepository.kt` | No runtime gate exists |
| F5 | High | `BoundedFilterDownloader` counts a download as "valid" using the same buggy `parseLine`, so a list full of bogus rules passes validation. | `data/repository/BoundedFilterDownloader.kt` lines ~111, ~131 | |
| F6 | High | `CnamePolicy.blockedAlias()` never consults `NeverBlockPolicy`. A protected host whose CNAME target matches any block rule is still blocked (4 call sites in the VPN service). | `logic/rules/CnamePolicy.kt`; `services/UnblockerVpnService.kt` ~246, ~272, ~337, ~475 | |
| F7 | High | Protection is thin and exact-match only: 62 hosts (`never_block.txt`), no registrable-domain protection, no user-defined "my domains". `PrivateDomainSet` (user allow/block) is **exact-host only**, HMAC-hashed (not listable, not exportable) and capped at 1,000. Allowing `example.com` leaves `api.example.com` blocked. | `logic/analysis/NeverBlockPolicy.kt`, `PrivateDomainSet.kt` | |
| F8 | Medium | Learner can persist `CONFIRMED` blocks from tokens (`ad`, `ads`, `analytics`, `metrics`, `pixel`, `telemetry`, `beacon`, `sponsor`...) in the subdomain plus entropy/cadence. Developer-owned hosts like `analytics.<yourdomain>` or `metrics.<yourapp>` fit that shape. `trustedAlias()` then marks the **original** hostname CONFIRMED permanently. Hard-coded patches (`FIRST_PARTY_TOOLING_HOSTS`) include the test placeholder `track.example.org`. | `logic/analysis/DomainFeatureExtractor.kt`, `ReputationPolicy.kt`, `LocalNetworkLearner.kt` | |
| F9 | Medium | Existing test locks in the bug: `parseLine("\|\|adserver.net^$third-party,image")` is asserted to produce a rule. | `app/src/test/.../logic/rules/ExtensionRuleStoreTest.kt` ~line 52 | |
| F10 | Medium | Seed list contains breakage-prone Google measurement domains as SUFFIX rules: `app-measurement.com` (line 412), `google-analytics.com` (559), `googletagmanager.com` (563). Legit blocks for many users, but they break consent/checkout flows on some sites. | `app/src/main/assets/ad_domains.txt` | Needs a "breakage-prone" tier, not removal |
| F11 | Medium | 40+ lists enabled by default, including **browser-only** annoyance/cookie/anti-adblock lists (`fanboy_annoyance`, `adguard_annoyances`, `ublock_annoyances`, `easylist_cookie`, `adblock_warning_removal`, `ffadblock`) and 5 non-Indian regional lists. These are almost entirely cosmetic rules, so at DNS level their only effect is F2. `malicious_url_blocklist` and `urlhaus_agh` are the same URL. | `app/src/main/assets/extensions/extensions_manifest.json` | |
| F12 | Info | No per-app attribution exists (only `addDisallowedApplication(ownPackage)`), no export exists, and `DnsLogEntity`/`DomainStatEntity` store no matched rule or source list. Default history mode is `AGGREGATE_ONLY` (privacy-first). | `services/UnblockerVpnService.kt`, `data-store/.../entity/*` | |

**Already good, keep:** decision order (user allow → user block → never-block → rules → learner → adult), `compiler.py` never-block gate, `adultPatterns` use label boundaries, fail-closed release signing, bounded downloads.

---

## 2. Target behaviour

### 2.1 Blocking modes (Settings → "Blocking mode"; START button unchanged)
- **SMART (default):** curated lists + safety rails. Learner may *suggest* but only auto-blocks hosts that pass every protection check.
- **APPROVED_ONLY:** the DNS filter blocks **only** domains the user approved (via Learn-an-app review or Logs → Block). Lists and learner never block; they only feed suggestions.

### 2.2 Decision pipeline (replace the body of `DecideBlockingUseCase.invoke`)
```text
1  normalize(host)                       -> invalid  => ALLOW(INVALID)
2  paused (notification "Pause 5 min")   -> ALLOW(PAUSED)
3  user ALLOW  (exact host OR ancestor entry marked "whole site")      -> ALLOW(USER_ALLOW)
4  user BLOCK  (exact host OR ancestor entry; per-app scope if set)    -> BLOCK(USER_BLOCK)
5  mode == APPROVED_ONLY                                               -> ALLOW(NO_MATCH) [record suggestion]
6  protected (built-in exact | user "My domains" subtree)              -> ALLOW(PROTECTED)
7  rules (shipped + downloaded): ALLOW beats BLOCK; BLOCK only if admitted -> BLOCK(LIST_RULE)
8  learner: BLOCK only if registrable domain is not protected and not user-allowed -> BLOCK(LEARNED)
9  adult filter (if enabled): list / TLD / label-token patterns        -> BLOCK(ADULT_*)
10 else ALLOW(NO_MATCH)
any exception anywhere -> ALLOW(ENGINE_ERROR)   (fail open)
```
`BlockingDecision` gains: `ruleValue: String?`, `ruleKind: RuleKind?`, `sourceId: Int?`, and new `DecisionReason` values `PROTECTED, PAUSED, NO_MATCH, ENGINE_ERROR, SUGGESTED`. Keep existing enum ordinals stable (they are stored as ints in `domain_stats`); append new values at the end.

---

## 3. Work packages

### WP1. Rule-line parser rewrite (fixes F1, F2, F3, F5, F9)
**Files:** new `logic/rules/RuleLineParser.kt`; edit `ExtensionRuleStore.kt` (`Builder.addLine`, `parseLine` delegates), `BoundedFilterDownloader.kt`, `RuleParser.kt`, `logic/rules/DnsRule.kt`.

**Spec**
- Skip (never produce a rule): blank, `!` comments, `[Adblock...]` headers, lines starting with a single `#`.
- Skip as **COSMETIC** if the line contains any of `##  #@#  #?#  #$#  #%#  #@$#  #@?#  #@%#  $$`.
- `#` starts a trailing comment **only** in hosts/plain-domain lines and only when preceded by whitespace.
- Hosts syntax: `0.0.0.0|127.0.0.1|::|::1 <host>` → `BLOCK EXACT host`.
- Plain domain line (no `/ $ * ? | ^ = :` and no spaces) → `BLOCK EXACT`.
- ABP `[@@]||host^[$opts]`: host part must contain only hostname characters; otherwise skip `PATH_OR_REGEX`.
- Options policy (this is the heart of the fix):
  - none, or only `important` → accept.
  - only party/resource modifiers (`third-party 3p script image stylesheet css font media xmlhttprequest xhr subdocument frame ping popup object other websocket`) → accept **only if the host is a subdomain** of its registrable domain (e.g. `ads.example.com`). If the host is the bare registrable domain (`facebook.com`, `youtube.com`), skip as `OPTIONS_ON_REGISTRABLE`.
  - only `doc`/`document`/`all` → accept **only** when the source list category is `SECURITY` and action is BLOCK (malware/phishing whole-site blocks such as `\|\|vlc.de^$doc`).
  - anything else (`removeparam, redirect, redirect-rule, csp, domain=, denyallow, dnstype, dnsrewrite, badfilter, ipaddress, replace, ...`) → skip `UNSUPPORTED_OPTIONS`.
  - `@@` exception lines with any option other than `important` → skip (never create broad allows from option rules).
- Parser returns `ParsedLine.Rule | ParsedLine.Skipped(reason)` so counters can be shown in diagnostics.

**Reference skeleton**
```kotlin
package com.unblocker.app.logic.rules

enum class SkipReason { BLANK_OR_COMMENT, COSMETIC, PATH_OR_REGEX, UNSUPPORTED_OPTIONS,
    OPTIONS_ON_REGISTRABLE, INVALID_HOST, SINGLE_LABEL, PUBLIC_SUFFIX, IP_LITERAL, PROTECTED_HOST, QUARANTINED }

sealed interface ParsedLine {
    data class Rule(val rule: DnsRule) : ParsedLine
    data class Skipped(val why: SkipReason) : ParsedLine
}

data class ParseContext(val sourceId: Int, val security: Boolean, val category: BlockingCategory)

class RuleLineParser(private val admission: RuleAdmission, private val psl: PublicSuffixRules) {
    private val cosmetic = arrayOf("##", "#@#", "#?#", "#$#", "#%#", "#@$#", "#@?#", "#@%#", "$$")
    private val partyResource = setOf("third-party","3p","script","image","stylesheet","css","font","media",
        "xmlhttprequest","xhr","subdocument","frame","ping","popup","object","other","websocket")
    private val docLevel = setOf("doc","document","all")
    private val hostsIps = setOf("0.0.0.0","127.0.0.1","::","::1")
    private val hostChars = Regex("[a-z0-9._-]+")

    fun parse(raw: String, ctx: ParseContext): ParsedLine {
        val s = raw.trim()
        if (s.isEmpty() || s[0] == '!' || s[0] == '[' || (s[0] == '#' && !s.startsWith("##"))) return skip(SkipReason.BLANK_OR_COMMENT)
        if (cosmetic.any { s.contains(it) }) return skip(SkipReason.COSMETIC)

        // hosts / plain-domain syntax (only place where " #" begins a comment)
        val noComment = s.substringBefore(" #").substringBefore("\t#").trim()
        val parts = noComment.split(' ', '\t').filter { it.isNotEmpty() }
        if (parts.size >= 2 && parts[0] in hostsIps) return admit(RuleAction.BLOCK, RuleKind.EXACT, parts[1], ctx)
        if (parts.size == 1 && !s.startsWith("||") && !s.startsWith("@@") && !s.startsWith("*.") &&
            s.none { it in "/$*?|^=:" }) return admit(RuleAction.BLOCK, RuleKind.EXACT, s, ctx)

        var rest = s
        var action = RuleAction.BLOCK
        if (rest.startsWith("@@")) { action = RuleAction.ALLOW; rest = rest.substring(2) }
        if (rest.startsWith("*.") && rest.none { it in "$/|^=:" }) return admit(action, RuleKind.WILDCARD, rest.substring(2), ctx)
        if (!rest.startsWith("||")) return skip(SkipReason.PATH_OR_REGEX)

        val dollar = rest.indexOf('$')
        val pattern = (if (dollar >= 0) rest.substring(0, dollar) else rest).removePrefix("||").removeSuffix("^")
        val options = if (dollar >= 0) rest.substring(dollar + 1).split(',').map { it.trim().lowercase() } else emptyList()
        val host = pattern.lowercase()
        if (!hostChars.matches(host)) return skip(SkipReason.PATH_OR_REGEX)

        val opts = options.filter { it != "important" }
        if (opts.isNotEmpty()) {
            if (action == RuleAction.ALLOW) return skip(SkipReason.UNSUPPORTED_OPTIONS)
            when {
                opts.all { it in partyResource } -> {
                    val reg = psl.registrableDomain(host)
                    if (reg == null || reg == host) return skip(SkipReason.OPTIONS_ON_REGISTRABLE)
                }
                opts.all { it in docLevel } && ctx.security -> Unit
                else -> return skip(SkipReason.UNSUPPORTED_OPTIONS)
            }
        }
        return admit(action, RuleKind.SUFFIX, host, ctx)
    }

    private fun admit(a: RuleAction, k: RuleKind, host: String, ctx: ParseContext): ParsedLine {
        val n = DomainName.normalizeUserEntry(host) ?: return skip(SkipReason.INVALID_HOST)
        admission.check(a, k, n)?.let { return skip(it) }
        return ParsedLine.Rule(DnsRule(a, k, n, ctx.category, ctx.sourceId))
    }
    private fun skip(r: SkipReason) = ParsedLine.Skipped(r)
}
```
- `ExtensionRuleStore.parseLine(raw, category)` must now call `RuleLineParser` (keep the signature for callers/tests; add an overload taking `ParseContext`).
- `ExtensionRuleStore.Builder` gets per-reason skip counters (`AtomicIntegerArray`) exposed via `RuleStats` for diagnostics (WP9).
- `BoundedFilterDownloader` must count only `ParsedLine.Rule` as valid, and reject a list when more than 2% of its non-comment lines are skipped for reasons other than `COSMETIC`/`OPTIONS_*`/`UNSUPPORTED_OPTIONS` (those are expected in ABP lists).
- Update `ExtensionRuleStoreTest` ~line 52: `||adserver.net^$third-party,image` must now be **skipped** (bare registrable + options). Add `||ads.adserver.net^$third-party,image` → accepted.

**Tests (write first):** `RuleLineParserTest` driven by `app/src/test/resources/rule-parser-cases.tsv` (Appendix A). The Python audit script in WP9 reads the same file so the two never drift.
**Run:** `./gradlew :app:testDebugUnitTest --tests "*RuleLineParserTest" --tests "*ExtensionRuleStoreTest" --tests "*BoundedFilterDownloaderTest"`
**Commit:** `fix(rules): parse only DNS-representable filter lines; never turn options/cosmetic rules into domain blocks`

---

### WP2. Rule admission guard (fixes F3, F4)
**Files:** new `logic/rules/RuleAdmission.kt`; wire into `ExtensionRuleStore.Builder.addRule`, `AdDetector.addDomain`, `DeviceUserRuleGateway` (custom/manual lists), `CompiledRuleSet`; extend `PublicSuffixRules` (`isPublicSuffix(host)` = host is a suffix itself; if missing, `registrableDomain(host) == null`).

```kotlin
class RuleAdmission(private val psl: PublicSuffixRules, private val protected: ProtectedDomains) {
    fun check(action: RuleAction, kind: RuleKind, host: String): SkipReason? {
        if (host.all { it.isDigit() || it == '.' } || host.contains(':')) return SkipReason.IP_LITERAL
        if (!host.contains('.')) return SkipReason.SINGLE_LABEL                 // 'com', 'localhost'
        if (psl.isPublicSuffix(host)) return SkipReason.PUBLIC_SUFFIX           // 'co.uk', 'github.io', 'blogspot.com'
        if (action == RuleAction.BLOCK && protected.ruleWouldBlockProtected(kind, host)) return SkipReason.PROTECTED_HOST
        if (QuarantineList.contains(kind, host)) return SkipReason.QUARANTINED  // asset rule-quarantine.txt (WP10)
        return null
    }
}
```
- `ruleWouldBlockProtected`: SUFFIX/WILDCARD rule is equal to or an ancestor of any built-in protected host (mirror `compiler.py:check_never_block` semantics); EXACT rule equals a protected host.
- Also harden `ExtensionRuleStore.matchRule`: **never test a single-label parent** (stop the parent loop when the remaining string has no `.`), as defence in depth even if a bad rule is ever loaded.
- Surface per-list counters in the Extensions screen: "N rules skipped (cosmetic / options / unsafe)". Never auto-disable a list.

**Tests:** `RuleAdmissionTest` (single label, public suffixes incl. private-section `github.io`, IPs, protected ancestors: `||google.com^` rejected but `||ads.google.com^` accepted), `ExtensionRuleStoreTest` (`com` rule in a list never blocks `example.com`).
**Run:** `./gradlew :app:testDebugUnitTest --tests "*RuleAdmissionTest" --tests "*ExtensionRuleStoreTest" --tests "*PublicSuffixRulesTest"`
**Commit:** `fix(rules): admission guard rejects TLD, public-suffix, IP and protected-ancestor rules`

---

### WP3. Protection model incl. "My domains" and CNAME path (fixes F6, F7)
**Files:** new `logic/analysis/ProtectedDomains.kt`; edit `NeverBlockPolicy.kt`, `CnamePolicy.kt`, `ContentFilterEngine.kt`, `DecideBlockingUseCase.kt`, `LocalNetworkLearner.kt`; new `data/preferences/MyDomainsStore.kt`; UI in Settings + Logs row actions; `tools/filter-compiler/never_block.txt`.

1. `ProtectedDomains` = built-in exact hosts (from `never-block-hosts.txt`, keep the existing `www.` handling) **+** user "My domains" (registrable domain **and all subdomains**).
   ```kotlin
   fun isProtected(host: String): Boolean {
       if (builtIn.contains(host) || (host.startsWith("www.") && builtIn.contains(host.removePrefix("www.")))) return true
       var h = host
       while (h.contains('.')) {                       // walk host, parent, ... down to registrable domain
           if (myDomains.contains(h)) return true
           if (h == psl.registrableDomain(host)) break
           h = h.substringAfter('.')
       }
       return false
   }
   ```
2. **My domains** is stored **readable** (app-private DataStore, max 500 entries) because the user must see, edit, import and export it. Update `PRIVACY.md` and `docs/local-privacy-update.md`: this is a user-curated list, not browsing history, and never leaves the device.
3. Logs row actions become: **Allow host**, **Allow whole site** (adds the registrable domain to the user-allow set with subtree semantics), **Protect this domain** (adds to My domains), **Block**, **Reset**. Extend `PrivateDomainSet` or add a second set so user allow/block entries can carry a `subtree` flag; the lookup walks ancestors exactly like `isProtected`.
4. `CnamePolicy.blockedAlias`: return `null` when `protected.isProtected(original)` or the user allowed the original or any ancestor. Add `CnamePolicyTest` cases.
5. `NeverBlockPolicy`: keep the static fallback, but fix the doc comment (it says "and their subdomains" but the implementation is exact-match). Expand `never_block.txt` with the gaps found: `docs.google.com, mail.google.com, drive.google.com, maps.google.com, www.youtube.com, m.youtube.com, youtube.com, i.ytimg.com, play.googleapis.com, firebaseinstallations.googleapis.com, www.facebook.com, facebook.com, www.instagram.com, instagram.com, x.com, twitter.com, www.reddit.com, reddit.com, linkedin.com, www.linkedin.com, bing.com, paypal.com, www.paypal.com, login.live.com, outlook.live.com, www.amazon.in, amazon.in, amazon.com, www.flipkart.com, flipkart.com, paytm.com, phonepe.com, groww.in, zerodha.com, kite.zerodha.com, web.whatsapp.com, claude.ai, anthropic.com, openai.com, chatgpt.com, stackoverflow.com, npmjs.com` (the compiler gate will confirm no shipped block rule covers them; fix any gate failure by quarantining the rule, not by removing the host).
6. `LocalNetworkLearner.analyzeQuery` and `ReputationPolicy.observe`: return `SUPPRESSED` for protected hosts and for any host whose registrable domain the user ever allowed. Remove `track.example.org` from `FIRST_PARTY_TOOLING_HOSTS`.

**Tests:** `ProtectedDomainsTest`, `CnamePolicyTest`, `DecideBlockingUseCaseTest` (My-domain subtree beats every list rule and the learner), `LocalNetworkLearnerTest`.
**Run:** `./gradlew :app:testDebugUnitTest --tests "*ProtectedDomainsTest" --tests "*CnamePolicyTest" --tests "*DecideBlockingUseCaseTest" --tests "*NeverBlockPolicyTest" --tests "*LocalNetworkLearnerTest" && python3 tools/filter-compiler/test_compiler.py`
**Commit:** `feat(protection): user "My domains", subtree allow, CNAME-path protection, wider never-block set`

---

### WP4. Default list set and learner gating (fixes F8, F10, F11)
**Files:** `extensions_manifest.json` (bump `"version"` so `migrateExtensions` runs; verify it preserves user toggles), `ExtensionFilter.kt` (add optional `browserOnly: Boolean`), `ExtensionsScreen.kt`, `ad_domains.txt` (tiering), `ReputationPolicy.kt`.
- Default **disabled** (keep available): `fanboy_annoyance, adguard_annoyances, ublock_annoyances, easylist_cookie, adblock_warning_removal, ffadblock, adguard_turkish, abpindo_indonesian, adguard_russian, adguard_spanish_portuguese, easylist_german`. Show a "Browser-only list, little effect on DNS" badge. Remove the duplicate `malicious_url_blocklist` (same URL as `urlhaus_agh`).
- Add `tier` to seed lines (`# tier=aggressive`) or a sibling asset `ad_domains_aggressive.txt` for `googletagmanager.com, google-analytics.com, app-measurement.com` and similar measurement domains; enabled by a Settings toggle **"Block analytics endpoints that may break some sites"** (default ON in SMART, ignored in APPROVED_ONLY).
- Learner: in SMART mode keep auto-block but require `ReputationState.CONFIRMED` **and** that the host is not protected; in APPROVED_ONLY the learner only records `SUGGESTED` entries for the review queue (WP7).

**Tests:** manifest migration test (user toggles preserved, new defaults applied once), `ReputationPolicyTest`, `AdaptiveBlockingEngineTest`.
**Run:** `./gradlew :app:testDebugUnitTest --tests "*Extension*" --tests "*Reputation*" --tests "*AdaptiveBlocking*"`
**Commit:** `fix(defaults): disable browser-only lists by default; tier measurement domains; learner never blocks protected hosts`

**Ship v2.3.1 after WP1-WP4 and WP9 pass** (see section 7).

---

### WP5. Blocking modes
**Files:** `FilteringPreferences.kt` (`blockingMode: StateFlow<BlockingMode>`), `DecideBlockingUseCase.kt`, Settings UI, `default_config.json` (`"blockingMode": "SMART"`).
- Implement section 2.2 exactly. APPROVED_ONLY short-circuits at step 5.
- Add notification action **Pause 5 min** (sets `pausedUntil`, auto-resumes; shown in the existing foreground notification).
- `DnsCache` must hold **upstream answers only**; the block/allow decision is evaluated per request (already true for CNAME re-checks; add a test so per-app scopes in WP7 cannot be bypassed or poisoned by cached results).

**Tests:** `DecideBlockingUseCaseTest` for every pipeline step and for `ENGINE_ERROR` fail-open; `DnsCacheTest`.
**Commit:** `feat(modes): SMART and APPROVED_ONLY blocking modes, pause action, fail-open decision pipeline`

---

### WP6. Decision explainability
**Files:** `BlockingDecision.kt`, `DecideBlockingUseCase.kt`, `ExtensionRuleStore.matchRule` (return `sourceId` where known), `QueryLogSink.kt`, `LogViewerScreen.kt`.
- Do **not** store rule/source per query on the hot path (memory/battery plans in `docs/superpowers/plans/2026-11-02-apk-memory-cpu-and-battery.md` forbid it). Instead add `RuleExplainer.explain(domain): Explanation` that re-runs the pipeline and, off the main thread, streams the enabled `*.rules` files once to find which list ids contain a rule covering the domain.
- Logs row → "Why?" sheet: decision, reason code, matched rule, matching list names, whether protected, user override.

**Tests:** `RuleExplainerTest` (fixtures: a domain in two lists, a protected domain, a user-blocked domain).
**Commit:** `feat(logs): explain why a domain was blocked or allowed`

---

### WP7. Learn-an-app mode (open apps one by one, block only what you approve)
**Files:** new `services/UidResolver.kt`, `services/RecordingSessionManager.kt`, `ui/learn/LearnAppScreen.kt`, `ui/learn/ReviewDomainsScreen.kt`, `ui/learn/LearnViewModel.kt`; edit `UnblockerVpnService.kt` (query handling ~237-240 and ~322-324), `logic/dns/DnsPacket.kt` / `DnsPacketUtil.parseWireQuery` (expose protocol, src/dst IP and ports), `data-store` (new tables + migration 2→3 + `3.json` schema), `AndroidManifest.xml`.

**Flow**
1. Logs → overflow → **Learn an app**. List launchable apps (`<queries><intent><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent></queries>`; no `QUERY_ALL_PACKAGES`).
2. Tap an app → **Start recording** → `RecordingSessionManager.start(pkg)` → launch it with `getLaunchIntentForPackage`. The foreground notification shows **Stop & review**.
3. While recording, every DNS query attributed to that app's UID is stored in `session_domains(session_id, domain, registrable, call_count, first_seen, last_seen, was_blocked, attribution)`. Bounded: 5,000 domains per session, sessions kept 30 days, deleted on request.
4. **Review screen:** domains grouped by registrable domain. Pre-selection: tick only domains that match an admitted list rule or learner `SUGGESTED`. Never pre-tick protected or My-domains hosts (shown locked with a shield). Actions: *Block selected* (scope: **this app only** / **all apps**), *Protect*, *Skip*.
5. Saved choices go to the user block set with an optional `scopePackage`. Per-app scope is applied only when the resolved package equals `scopePackage`; if attribution is unknown, a per-app rule is **not** applied (fail open).

**UID attribution (best effort, API 29+)**
```kotlin
class UidResolver(private val cm: ConnectivityManager, private val pm: PackageManager) {
    private val cache = object : LinkedHashMap<Long, Int>(256, .75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<Long, Int>) = size > 256 }
    fun resolve(proto: Int, src: InetSocketAddress, dst: InetSocketAddress): Attribution {
        if (Build.VERSION.SDK_INT < 29) return Attribution.UNKNOWN
        return try {
            val uid = cm.getConnectionOwnerUid(proto, src, dst)
            if (uid == Process.INVALID_UID) Attribution.UNKNOWN
            else Attribution(uid, pm.getPackagesForUid(uid)?.toList().orEmpty(), exact = true)
        } catch (_: SecurityException) { Attribution.UNKNOWN } catch (_: IllegalArgumentException) { Attribution.UNKNOWN }
    }
}
```
- `minSdk` is 26, so guard with `SDK_INT`. Label every row `attribution = exact | best_effort | unknown`. On some Android versions DNS is sent by a system process, which resolves to a system UID; show those as "system / unknown" rather than guessing.
- Apps that use DoH/DoT directly never reach the tunnel DNS; the review screen must say so ("This app may be using encrypted DNS; see `docs/dns-bypass-matrix.md`").
- Per-app tables are written **only** during a recording session or if the user turns on **Per-app insights** (default OFF), keeping the default `AGGREGATE_ONLY` privacy stance.

**Tests:** `RecordingSessionManagerTest`, `UidResolverTest` (fake `ConnectivityManager`), `LearnViewModelTest` (pre-selection never ticks protected hosts), Room migration test 2→3, `UserRuleActionTest` for scoped rules.
**Run:** `./gradlew :app:testDebugUnitTest --tests "*Learn*" --tests "*Recording*" --tests "*UidResolver*" :data-store:testDebugUnitTest`
**Commit:** `feat(learn): per-app recording sessions with review-and-approve blocking`

---

### WP8. Export all domains to CSV or log
**Files:** new `data/export/DomainExporter.kt`, `data/export/CsvWriter.kt`, `ui/logs/ExportDialog.kt`; edit `LogViewerScreen.kt` (overflow → **Export domains**), `LogViewModel.kt`.
- Uses `ActivityResultContracts.CreateDocument("text/csv")` (and `text/plain` for the `.log` variant). No storage permission, no network. Suggested name `unblocker-domains-YYYYMMDD-HHmm.csv`.
- Export dialog: **Scope** (all seen / blocked only / last 24 h), **Include 18+ category** (default OFF), **Include per-app columns** (only if present), **Include explanations** (default ON, runs `RuleExplainer` in the background with a progress bar, capped at 50,000 rows).
- Source data: `domain_stats` (aggregate). For the `.log` variant use `domain_events` only if `HistoryMode.RAW_HISTORY_OPT_IN` is on; otherwise show "Raw log is off for privacy. Turn it on for 24 h?" with a one-tap temporary enable.
- `user_override` is computed by testing each exported domain against the user sets (they are hashed and cannot be listed).
- RFC 4180 quoting, UTF-8, ISO-8601 UTC timestamps, and **formula-injection guard**: prefix any cell starting with `= + - @` with `'`.
- Warn before saving: "This file lists sites visited on this phone. Share it only with someone you trust."
- Columns: see Appendix B. Add a header row only (no comment lines) so parsers and spreadsheets read it cleanly.

**Tests:** `CsvWriterTest` (quoting, commas, quotes, newlines, formula guard, 18+ exclusion), `DomainExporterTest` (scope filters, row cap, stable column order), `ExportPrivacyTest` (no export when the user cancels the picker; nothing written to shared storage).
**Commit:** `feat(export): export domain list to CSV or log via SAF with explanations`

---

### WP9. Self-test, regression corpus, audit script (hotfix gate)
**Files:** new `app/src/test/resources/primary-hosts.tsv` (Appendix C), `rule-parser-cases.tsv` (Appendix A), `tools/eval/list_audit.py`, `PrimaryHostsRegressionTest.kt`, Diagnostics screen row.
- `PrimaryHostsRegressionTest`: load default settings plus fixture excerpts of the problem lines and assert every host in `primary-hosts.tsv` decides `ALLOW` in SMART mode.
- `tools/eval/list_audit.py`: downloads every **enabled** list in `extensions_manifest.json` (needs network; run it on the agent machine), applies the **same cases file semantics** as the Kotlin parser, prints per-list skip counters, and fails if any `primary-hosts.tsv` host would be blocked or any single-label/public-suffix rule is admitted. Report-only diagnostic; the Kotlin tests are the source of truth. Add `tools/eval/test_list_audit.py`.
- Diagnostics screen: **Run self-test** button resolves 10 primary hosts and 3 known ad hosts through the live engine and shows pass/fail (a quick check on the phone, no export needed).
**Run:** `./gradlew :app:testDebugUnitTest --tests "*PrimaryHostsRegressionTest" && python3 tools/eval/test_list_audit.py && python3 tools/eval/list_audit.py`
**Commit:** `test(regression): primary-host corpus, shared parser cases, list audit tool`

---

### WP10. Validation import, quarantine, docs, release hygiene
- **Import validation file** (Settings → Advanced): `OpenDocument` for the files described in section 4 so fixes apply on the phone without rebuilding. Validate every line with `DomainName.normalizeUserEntry`, cap 5,000 lines, show a preview before applying.
- `tools/filter-compiler/quarantine.txt` + compiler support: the compiler emits `rule-quarantine.txt` into `generated/dns-assets`; `QuarantineList` loads it at runtime (used by `RuleAdmission`).
- `scripts/apply_validation.py`: merges the validation files (section 4) into `never_block.txt`, `allow.txt` (as `@@||host^`) and `quarantine.txt`, de-duplicates, keeps comments, then runs `tools/filter-compiler/test_compiler.py`. Add `scripts/tests/test_apply_validation.py`.
- Docs: update `PRIVACY.md`, `docs/local-privacy-update.md`, `docs/dns-bypass-matrix.md`, `DATASETS.md`, `README.md` (modes, Learn-an-app, export, My domains). Remove the stale `plans/` duplicates only if `validate_plans.py` still passes.
- Run every existing verifier the repo already has (`validate_plans.py`, `verify_artifacts.py`, `scripts/tests/run-all.sh`) and regenerate `FILE_MANIFEST_SHA256.json` / `artifact-evidence.json` using the repo's existing method (check `CONTRIBUTING.md` first; do not hand-edit hashes).
**Commit:** `feat(validation): import validation files, quarantine list, docs`

---

## 4. Validation loop with Claude (the "upload the CSV" workflow)

1. On the phone: Logs → overflow → **Export domains** → CSV (tick *Include explanations*; leave *18+* unticked unless you suspect a false positive there).
2. Upload the CSV to the Claude chat. Claude classifies each **registrable domain** as `PRIMARY/FIRST_PARTY`, `SHARED_INFRA/CDN`, `AD/TRACKER`, `ADULT`, `MALWARE`, or `UNKNOWN`, flags every primary domain that was blocked, and says which rule/list caused it.
3. Claude returns these files:
   - `validation_report.md` (what was wrong and why)
   - `protected_domains_add.txt` (one registrable domain per line; goes to **My domains** / `never_block.txt`)
   - `allow_add.txt` (exact hosts)
   - `rule_quarantine.txt` (tab-separated: `kind<TAB>rule<TAB>list_id<TAB>reason`)
   - `block_add.txt` (domains confirmed as ads/trackers)
4. Apply on the phone with **Import validation file**, or in the repo with `python3 scripts/apply_validation.py <folder>` followed by a new build.
5. Repeat after a day of normal use. The goal is an empty `primary blocked` section in the next report.

---

## 5. Testing on your phone (no cable needed)
1. Install the new build, press **START**, keep the default SMART mode.
2. Settings → Diagnostics → **Run self-test** (all green expected).
3. Logs → **Learn an app** → pick one app → **Start recording** → use it for 1-2 minutes → **Stop & review** → tick only what you want blocked → save. Repeat per app.
4. Open the apps and sites that were broken. If one still fails: Logs → that domain → **Why?** → **Allow whole site** or **Protect this domain**.
5. Export the CSV and upload it for validation (section 4).

---

## 6. Acceptance criteria
- [ ] Every line in `rule-parser-cases.tsv` produces the expected result (Appendix A), in Kotlin and in `list_audit.py`.
- [ ] With the default lists loaded, **0** hosts in `primary-hosts.tsv` are blocked in SMART mode, and **0** are blocked in APPROVED_ONLY mode.
- [ ] No rule in any default list is admitted if it is single-label, a public suffix, an IP, or covers a protected host. `com` can never match `example.com`.
- [ ] A protected host whose CNAME target is on a block list is **not** blocked.
- [ ] In APPROVED_ONLY mode only user-approved domains are blocked; lists and learner never block.
- [ ] A per-app block applies only to that app; unknown attribution never blocks.
- [ ] Export produces a valid RFC 4180 CSV with the Appendix B columns; 18+ rows are excluded by default; the formula guard works; cancelling the picker writes nothing.
- [ ] No new permission other than the `<queries>` element; no new network call; START flow unchanged; DNS answers still return upstream when the engine throws.
- [ ] `./gradlew :app:testDebugUnitTest :data-store:testDebugUnitTest :vpn-engine:testDebugUnitTest` is green; `python3 tools/filter-compiler/test_compiler.py`, `tools/eval/test_list_audit.py`, `scripts/tests/run-all.sh` are green; CodeQL and device-matrix workflows pass.
- [ ] Performance budget from `docs/qa/performance-baseline-v2.2.0.md` is not regressed (matcher benchmark, allocation benchmark, APK size).

---

## 7. Release and cleanup (test → push → build/release → verify)
1. Work on branch `fix/false-positives`. After WP1-WP4 + WP9 are green: bump to **2.3.1 (versionCode 29)**, update release download links the same way commit `84394c1` did, push, open a PR, wait for `android.yml`, `codeql.yml`, `android-device-matrix.yml` to pass.
2. Tag `v2.3.1`. The existing `release.yml` is fail-closed on signing: if the signing secret is missing, stop and write `BLOCKERS.md`; do **not** add a signing fallback.
3. Verify after push: release assets present, `scripts/verify-release.sh` and `scripts/verify-apk-signature.sh` pass, `mapping.txt` included, APK installs over the previous version (upgrade policy in `docs/release-signing-and-upgrade-policy.md`).
4. Repeat for WP5-WP8, WP10 as **v2.4.0 (versionCode 30)**.
5. Cleanup: no downloaded lists, `build/`, or temp files committed; uninstall anything installed only for this work and list it in `docs/qa/2026-11-fp-fix-report.md`.

---

## Appendix A. `rule-parser-cases.tsv` (seed cases; columns: `line<TAB>listCategory<TAB>expected`)
Expected values: `SKIP:<REASON>` or `RULE:<ACTION>:<KIND>:<value>`.
```text
||facebook.com^$removeparam=mibextid	PRIVACY	SKIP:UNSUPPORTED_OPTIONS
||youtube.com^$removeparam=pp	PRIVACY	SKIP:UNSUPPORTED_OPTIONS
||instagram.com^$removeparam=ig_rid	PRIVACY	SKIP:UNSUPPORTED_OPTIONS
||reddit.com^$doc,removeparam=/web_only=/	PRIVACY	SKIP:UNSUPPORTED_OPTIONS
||open.spotify.com^$removeparam=referral	PRIVACY	SKIP:UNSUPPORTED_OPTIONS
||twitch.tv^$xhr,3p,domain=controlc.com	AD	SKIP:UNSUPPORTED_OPTIONS
||indiatimes.com^$image,redirect-rule=1x1.gif	AD	SKIP:UNSUPPORTED_OPTIONS
||com^$doc,ipaddress=206.82.7.123	SECURITY	SKIP:UNSUPPORTED_OPTIONS
||com^	AD	SKIP:SINGLE_LABEL
||co.uk^	AD	SKIP:PUBLIC_SUFFIX
||github.io^	AD	SKIP:PUBLIC_SUFFIX
www.youtube.com##+js(trusted-replace-xhr-response, /"adPlacements.*?/)	AD	SKIP:COSMETIC
mail.google.com#@#div[aria-label="Ads"]	AD	SKIP:COSMETIC
docs.google.com##+js(no-xhr-if, method:POST url:/logImpressions)	AD	SKIP:COSMETIC
linkedin.com##.ad-banner-container	AD	SKIP:COSMETIC
x.com##+js(prevent-xhr, /api/1.1/flow/viewer.json)	AD	SKIP:COSMETIC
paypal.com##+js(href-sanitizer, a[href^="https://app.adjust.com/"])	AD	SKIP:COSMETIC
windowsreport.com##.code-block	AD	SKIP:COSMETIC
||vlc.de^$doc	SECURITY	RULE:BLOCK:SUFFIX:vlc.de
||vlc.de^$doc	AD	SKIP:UNSUPPORTED_OPTIONS
||adserver.net^$third-party,image	AD	SKIP:OPTIONS_ON_REGISTRABLE
||ads.adserver.net^$third-party,image	AD	RULE:BLOCK:SUFFIX:ads.adserver.net
||adserver.net^	AD	RULE:BLOCK:SUFFIX:adserver.net
||adserver.net^$important	AD	RULE:BLOCK:SUFFIX:adserver.net
@@||goodservice.com^	AD	RULE:ALLOW:SUFFIX:goodservice.com
@@||goodservice.com^$script,domain=a.com	AD	SKIP:UNSUPPORTED_OPTIONS
||example.com/ads/banner.js	AD	SKIP:PATH_OR_REGEX
/banner\d+\.gif/	AD	SKIP:PATH_OR_REGEX
0.0.0.0 tracker.example.net	AD	RULE:BLOCK:EXACT:tracker.example.net
0.0.0.0 tracker.example.net # comment	AD	RULE:BLOCK:EXACT:tracker.example.net
127.0.0.1 localhost	AD	SKIP:INVALID_HOST
tracker.example.net	AD	RULE:BLOCK:EXACT:tracker.example.net
*.tracker.example.net	AD	RULE:BLOCK:WILDCARD:tracker.example.net
||google.com^	AD	SKIP:PROTECTED_HOST
||ads.google.com^	AD	RULE:BLOCK:SUFFIX:ads.google.com
||www.youtube.com^	AD	SKIP:PROTECTED_HOST
```

## Appendix B. CSV columns (`schema_version=1`)
| Column | Meaning |
|---|---|
| `schema_version` | `1` |
| `domain` | host as seen by DNS (lowercase, punycode) |
| `registrable_domain` | eTLD+1 via the bundled public suffix list |
| `call_count`, `blocked_count`, `allowed_count` | from `domain_stats` |
| `first_seen_utc`, `last_seen_utc` | ISO-8601 UTC |
| `last_action` | `BLOCK` / `ALLOW` |
| `last_reason_code` | `DecisionReason` name |
| `category` | `AD` / `TRACKER` / `ADULT_CONTENT` / `CUSTOM` / `NORMAL` |
| `matched_rule`, `matched_rule_kind` | from `RuleExplainer` (as of export time) |
| `matched_list_ids` | `;`-separated manifest ids |
| `is_protected` | `true` if built-in or My-domains |
| `user_override` | `ALLOW` / `BLOCK` / `NONE` |
| `cname_target` | first CNAME alias if cached |
| `app_packages` | `;`-separated, only when per-app data exists |
| `app_attribution` | `exact` / `best_effort` / `unknown` |
| `blocking_mode` | `SMART` / `APPROVED_ONLY` |
| `app_version`, `rules_snapshot` | app version and a short hash of enabled list ids + their last-updated times |

## Appendix C. `primary-hosts.tsv` seed (extend with your own domains via My domains)
```text
google.com  www.google.com  mail.google.com  docs.google.com  drive.google.com  maps.google.com  accounts.google.com  play.google.com
youtube.com  www.youtube.com  m.youtube.com  music.youtube.com  i.ytimg.com
facebook.com  www.facebook.com  m.facebook.com  instagram.com  www.instagram.com  x.com  twitter.com
reddit.com  www.reddit.com  old.reddit.com  linkedin.com  www.linkedin.com  quora.com  twitch.tv  www.twitch.tv  open.spotify.com
github.com  gist.github.com  api.github.com  raw.githubusercontent.com  stackoverflow.com  npmjs.com  registry.npmjs.org  pypi.org
bing.com  www.bing.com  microsoft.com  outlook.live.com  login.microsoftonline.com  paypal.com  www.paypal.com
amazon.in  www.amazon.in  amazon.com  flipkart.com  www.flipkart.com  paytm.com  phonepe.com  groww.in  zerodha.com  kite.zerodha.com
timesofindia.indiatimes.com  economictimes.indiatimes.com  ndtv.com  hindustantimes.com
whatsapp.com  web.whatsapp.com  telegram.org  claude.ai  anthropic.com  openai.com  chatgpt.com  wikipedia.org
connectivitycheck.gstatic.com  mtalk.google.com  firebaseinstallations.googleapis.com  play.googleapis.com
```
