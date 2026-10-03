# Bundled domain seed data

Build-time generation uses `tools/filter-compiler/sources.json`: pinned revisions,
SHA-256 canonical text bytes (CRLF normalized to LF) and explicit licenses are required. `preBuild` fails on changed
bytes or unsupported syntax. Python 3.9+ is a build prerequisite, not an app
runtime dependency. `dns-rules.tsv` and its deterministic manifest are bundled.

The full Public Suffix List (including PRIVATE section) is bundled at revision
`a179a48c465e818cfd8d626691cb317985da87fb` under MPL-2.0; its license is included
as `MPL-2.0.txt`. Source: https://github.com/publicsuffix/list . It supplies
registrable-domain boundaries only, never permission to block or allow a host.

`app/src/main/assets/ad_domains.txt` and `adult_domains.txt` are small,
project-maintained seed sets distributed under the repository's Apache License
2.0. They contain domain identifiers only and are not copied from a third-party
bulk blocklist.

The seeds provide deterministic starting coverage for widely identified
advertising/tracking services and optional adult-content filtering. They are not
an effectiveness benchmark, an exhaustive list, or a claim about every service
hosted by a domain. Contributions must include a concrete blocking rationale,
false-positive review, canonical domain syntax, and regression tests where the
change affects expected behavior.

The installed app never downloads list updates. Runtime additions come only from
the private on-device heuristic learner or explicit local exceptions. Raw learned
domains and exceptions are not added to these files.
