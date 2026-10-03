# Bundled domain seed and rule data

Build-time generation uses `tools/filter-compiler/sources.json`: pinned revisions,
SHA-256 canonical text bytes (CRLF normalized to LF) and explicit licenses are required. `preBuild` fails on changed
bytes or unsupported syntax. Python 3.9+ is a build prerequisite, not an app
runtime dependency. `dns-rules.tsv`, `dns-rules.bin` (UBR2 format) and their deterministic manifest are bundled.

The full Public Suffix List (including PRIVATE section) is bundled at revision
`a179a48c465e818cfd8d626691cb317985da87fb` under MPL-2.0; its license is included
as `MPL-2.0.txt`. Source: https://github.com/publicsuffix/list . It supplies
registrable-domain boundaries only, never permission to block or allow a host.

`tools/filter-compiler/vendor/stevenblack/` contains the StevenBlack hosts list
pinned at commit `21605ccaecf26941005d4a7a3c1267af234599cf` under the MIT License.
It provides broad base coverage for known ad and telemetry domains.

`app/src/main/assets/ad_domains.txt` and `adult_domains.txt` are small,
project-maintained seed sets distributed under the repository's Apache License
2.0.

The installed app never downloads list updates at runtime. Lists are bundled offline at build time. Runtime additions come only from
the private on-device heuristic learner or explicit local exceptions. Raw learned
domains and exceptions are never uploaded or shared.
