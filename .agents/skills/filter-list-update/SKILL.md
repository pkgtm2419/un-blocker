---
name: filter-list-update
description: Use when adding or replacing bundled domain datasets, extension source URLs, filter parser rules or blocklist admission logic.
---

# Filter List Update

## Steps
1. Review `DATASETS.md`, `docs/FILTERING_RULES.md`, `sources.json`, current extension manifest (19 entries at snapshot) and licenses.
2. Verify source publisher, pinned revision, SHA256, redistribution terms, parser compatibility, actual DNS-rule semantics and protected infrastructure.
3. Create a safe synthetic list fixture covering adblock allow/block, cosmetic rules, public suffix, IP literal, prohibited registrable domains, unsupported syntax and duplicates.
4. Protect downloader: HTTPS, redirects, private-network/loopback targets, DNS rebinding, 15 MiB, 300k-rule and per-line limits, partial download rollback.
5. Re-run filter-compiler Python tests, `ExtensionRepositoryTest`, `BoundedFilterDownloaderTest`, rule admission + false-positive corpus.
6. Update `DATASETS.md`, third-party notices if needed, features/EXTENSIONS, feature matrix, changelog and regenerate `EXTENSION_CATALOG.md`.

## Example
A new URL tracker list is not valid until its license and unsafe cross-domain blocking cases are reviewed.

## Mistake
Treating browser cosmetic CSS rules as network DNS rules.

## References

Read `AGENTS.md`, `docs/README.md`, `docs/QUALITY_GATES.md` and relevant source/test files before declaring completion.
