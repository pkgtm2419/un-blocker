# Feature change / requirements template

> Copy this into a new dated feature specification; do not replace this template with work-in-progress content.

**Feature ID:** `Fxx`
**Status:** Proposed / Verified / Released (evidence URL)
**Business need and user story:** …
**Affected modules/classes:** exact repository paths
**Current behavior:** observed by code and tests
**Desired behavior:** concrete input → output examples
**Out of scope:** …

## Functional acceptance

- [ ] Happy path with specific inputs and outputs.
- [ ] Errors, offline, permission revoked, invalid input, cancelled tasks, concurrent operations.
- [ ] Preferences, process death, migrations and device restart if relevant.
- [ ] Android min SDK and UI/a11y states.

## Threat model and privacy

Which data enters/exits device? Which persistent fields are plaintext vs hashed? Which permissions and trust boundaries change? Is there a resolver, DNS, list ingestion, Keystore or backup impact?

## Implementation and verification

| File | Responsibility | Test |
| --- | --- | --- |
| `path` | concrete interface | `path/to/Test.kt` |

## Documentation ownership

Update feature matrix, behavior contracts, relevant `docs/features/`, module docs, product/privacy/security, changelog/README. Regenerate `docs/generated` by script. Link PR and test evidence. Do not mark shipped before a release is verified.
