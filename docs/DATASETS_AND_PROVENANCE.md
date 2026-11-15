# Dataset, blocklist and license review

Canonical existing policy: [DATASETS.md](../DATASETS.md), [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md), [LICENSE](../LICENSE), `tools/filter-compiler/sources.json` and `app/src/main/assets/extensions/extensions_manifest.json`.

## Source classes

1. **Vendored pinned compiler sources:** e.g., StevenBlack snapshot under `tools/filter-compiler/vendor/stevenblack/…`, explicitly check revision, normalized SHA256 and license. Manifest-based deterministic build should fail on modified input.
2. **PSL:** `app/src/main/assets/public_suffix_list.dat`, with MPL-2.0 notice. PSL marks registrable boundaries; PSL inclusion **does not** justify blocking a suffix.
3. **Bundled app domain lists / extension rules:** `ad_domains.txt`, `adult_domains.txt`, `extensions/bundled_rules.txt`; require legal provenance and scope checks.
4. **Remote update list catalog:** 67 entries in `extensions_manifest.json` (v8); URLs and enabled status are data, not automatically vetted endorsements.

## Review checklist

Document list publisher, exact retrieval URL, date/revision, license and attribution, intended type (DNS/host/browser-only), SHA256 for vendored assets, normalized domain count, protected-domain/admission rejections, changes to false-positive corpus, rollback behavior, and compatibility with device memory constraints. Review batch changes on a branch. Do not add third-party adblocking assets by copying the reference download-manager repository's unrelated content.
