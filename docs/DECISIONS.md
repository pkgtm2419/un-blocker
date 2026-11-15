# Architecture and product decision register

| ID | State | Decision / rationale | Change trigger |
| --- | --- | --- | --- |
| ADR-001 | Existing product contract | DNS-only, on-device filtering rather than full traffic VPN/TLS interception | explicit product/privacy review |
| ADR-002 | Existing implementation | Device-keyed HMAC storage for learned/user rules; preserve migration compatibility | data-format/security proposal |
| ADR-003 | Existing implementation | Local query aggregates can store readable domains; default raw event mode is off | opt-in, retention or privacy change |
| ADR-004 | Existing implementation | Active app service discovers underlying Android resolvers; protect sockets; no automatic public fallback claimed without code verification | resolver design RFC |
| ADR-005 | Agent-pack convention | Human-owned semantics in hand-reviewed docs; deterministic generated inventories in `docs/generated` | generator/schema change |
| ADR-006 | Agent-pack convention | Automation opens reviewable PR, never directly pushes sensitive docs to protected main | repository governance approval |
| ADR-007 | Existing implementation caveat | `APPROVED_ONLY` in current policy allows unmatched domains; name is not a default-deny contract | explicit semantic requirement + tests |

When adding a decision: record date, owner/PR, considered alternatives, consequences, source paths, tests and migration/release impact. Never edit an old status to imply an unverified completion.
