# Bundled domain seed data

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
