---
trigger: glob
globs: "**/*.md,docs/**,.agents/**,scripts/docs_*.py"
---

# Docs Lifecycle

Project docs must be source-grounded and specific. Human-owned feature/security/privacy statements require review; machine inventory belongs to `docs/generated/` and is regenerated only with `python3 scripts/docs_sync.py --write`. Always run `scripts/docs_contract.py` and `scripts/docs_sync.py --check`; do not fabricate statuses or benchmark data. New code means corresponding docs in same PR. See `/docs-maintainer`.
