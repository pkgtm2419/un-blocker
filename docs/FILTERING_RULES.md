# Domain rules, admission and precedence

## Sources and formats

- Build-time compiler: `tools/filter-compiler/compiler.py`, `tools/filter-compiler/sources.json` and pinned vendored inputs. Compiler creates deterministic rule assets (UBR2 binary) consumed through `app/src/main/java/com/unblocker/app/logic/rules/RuleSet.kt` and optionally off-heap indexes from `data-store/src/main/java/com/unblocker/data/cache/OffHeapRuleEngine.kt`.
- Bundled local lists: `app/src/main/assets/ad_domains.txt`, `adult_domains.txt`, `extensions/bundled_rules.txt`, `extensions/extensions_manifest.json`, `public_suffix_list.dat`.
- Runtime extension sources: `ExtensionRepository` and `BoundedFilterDownloader`; extension `RuleLineParser`/`ExtensionRuleStore` exclude cosmetic and incompatible syntaxes. **The inspected extension manifest contains 19 items**, of which one is `manual_filters` and one (`ublock_annoyances`) is disabled. This is not the 7 claimed by the older `.gsd` roadmap.
- User rules: `DeviceLearning` private HMAC membership and `UserRuleGateway`, exact/whole-site support. User allow should beat user block when conflicts arise.

## Rule admission and non-negotiable safety

### Current implementation gaps

`RuleLineParser.parse()` accepts resource/party options on subdomains and document options for security sources, then returns a plain `SUFFIX` rule. Such DNS rules cannot preserve the original request conditions. `ExtensionRuleStore.Builder.addRule()` also puts wildcard allow entries into `allowSuffix`, allowing the apex as well as descendants. These are source-observed gaps, not guarantees that the desired admission contract below is already enforced. The supplied contribution patch proposes corrections but has **not** been applied by this documentation installation. See [saved references](references/README.md) and [reconciliation](REPO_RECONCILIATION.md).

Reject bare public suffixes/TLDs, IP literals, unsupported path/regex/cosmetic options, protected-ancestor rules, invalid IDNA/hostname syntax and potential quarantine conflicts. Never globally block a whole CDN or content domain without documented impact and synthetic false-positive tests.

`DecideBlockingUseCase` determines **when** a ruleset match is considered; `RuleSet` and `ExtensionRuleStore` determine **which** allowed/block rule matches; do not confuse those scopes. Use [behavior contracts](BEHAVIOR_CONTRACTS.md) for full precedence including pause, mode and protected exceptions.

## Remote download contract

`BoundedFilterDownloader` permits HTTPS sources only; resolves targets and rejects loopback/site-local/link-local and some private ranges; redirects limited to three. Default caps: 15 MiB, 300,000 rules, 4096 bytes/line, connection timeout 10s, read timeout 15s. Inspect DNS rebinding and redirect-to-private tests. An IP validation result is not necessarily a connection pin: avoid overclaiming perfect SSRF defense.

## When changing a list

1. Record URL, publisher, exact revision/hash, license, terms and list purpose in `DATASETS.md`/`docs/DATASETS_AND_PROVENANCE.md`.
2. Re-run compiler unit tests, source integrity checks, rule admission, NeverBlock and false-positive regression corpus.
3. Verify how runtime extension cache/update behaves on malformed downloads, truncated lists, redirects, removal, rollback and Android offline state.
4. Update `docs/generated/EXTENSION_CATALOG.md` through `docs_sync.py --write`, feature docs and changelog. Never claim corpus percentages apply to the wider internet.
