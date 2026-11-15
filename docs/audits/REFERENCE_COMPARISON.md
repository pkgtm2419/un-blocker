# Documentation architecture compared with Mister Download Manager

**Reference inspected:** `anefzaoui/misterdownloadmanager` at main SHA `4328526e8cda0d042b55bd62c41665727b556e72` (2026-11-13). Patterns are used as **documentation inspiration**, not as copied implementation, features or policies.

| Reference repo practice | Adaptation for Un-Blocker |
| --- | --- |
| `CLAUDE.md`: focused agent instructions and command list | `AGENTS.md`: cross-agent conventions and short runtime navigation |
| `PRODUCT.md`: product boundaries and user intent | `PRODUCT.md`: DNS-only, local-first and verified behavioral exceptions |
| `DESIGN.md`: UI design constraints | `DESIGN.md`: current Compose theme, adaptive and reduced-motion patterns |
| `SECURITY.md`: security disclosure and critical surfaces | `SECURITY.md`: Android packet parsing, rule URLs, Keystore, CI, correct reporting |
| `docs/ARCHITECTURE.md`: clear component map | `docs/ARCHITECTURE.md` + 5 module guides with real app entrypoint |
| `docs/README.md`: navigable feature guide index | `docs/README.md` and `docs/features/*.md` |
| Scoped reusable agent skill and references | Antigravity-native `.agents/skills/*/SKILL.md`, `.agents/rules/*` |
| Source-linked assertions and proof | static review findings, explicit evidence status, test matrix and machine inventories |

Do **not** import reference repo Rust/egui, downloader functionality, shell scripts, assets, specific product metrics, brand voice, security SLAs or platform details. The repositories are unrelated implementations.
