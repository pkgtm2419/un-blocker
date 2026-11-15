# Un-Blocker — engineering documentation hub

**Snapshot:** target repository `main` at `8934519` (2026-11-10). The source tree, tests and current configuration remain authoritative over this documentation. See [known disagreements](REPO_RECONCILIATION.md). For application setup start at [INSTALL_DOC_PACK.md](../INSTALL_DOC_PACK.md) (agent pack) or existing root [README.md](../README.md) (app installation).

## Core references

| Need | Read |
| --- | --- |
| Agent development protocol | [AGENTS.md](../AGENTS.md), [Agent workflow](AGENT_WORKFLOW.md) |
| Product intent and constraints | [PRODUCT.md](../PRODUCT.md), [Feature matrix](FEATURE_MATRIX.md) |
| Modules and runtime dependency graph | [Architecture](ARCHITECTURE.md), [Modules](PROJECT_MODULES.md), [Module directory](modules/README.md) |
| Exact blocking outcomes | [Behavior contracts](BEHAVIOR_CONTRACTS.md), [Filtering rules](FILTERING_RULES.md) |
| Network and packets | [DNS pipeline](DNS_PIPELINE.md), [Coverage limits](features/NETWORK_COVERAGE.md) |
| User-facing flows and screens | [Feature guides](features/README.md), [UI/UX](UI_UX.md), [DESIGN.md](../DESIGN.md) |
| Local data and disclosure | [Privacy data flow](PRIVACY_DATA_FLOW.md), [Privacy policy](../PRIVACY.md) |
| Vulnerability and security review | [SECURITY.md](../SECURITY.md), [Security review](SECURITY_REVIEW.md), [Initial audit](audits/INITIAL_REVIEW_2026-11-13.md) |
| Build, tests, CI and release | [Build and test](BUILD_AND_TEST.md), [Testing strategy](TESTING_STRATEGY.md), [Test matrix](TEST_MATRIX.md), [Release process](RELEASE_PROCESS.md) |
| Style and performance | [Code standards](CODE_STANDARDS.md), [Performance](PERFORMANCE.md) |
| Datasets and dependencies | [Datasets](DATASETS_AND_PROVENANCE.md), [Dependencies](DEPENDENCIES.md), [Configuration](CONFIGURATION.md) |
| How docs stay current | [Doc maintenance](DOCUMENTATION_MAINTENANCE.md), [Generated inventories](generated/README.md), [Docs quality gates](QUALITY_GATES.md) |
| New feature / bug workflow | [Implementation playbook](IMPLEMENTATION_PLAYBOOK.md), [Feature change template](FEATURE_CHANGE_TEMPLATE.md), [Troubleshooting](TROUBLESHOOTING.md) |
| Source reconciliation / decisions | [Reconciliation](REPO_RECONCILIATION.md), [Decision register](DECISIONS.md), [Initial review](audits/INITIAL_REVIEW_2026-11-13.md) |
| Agent and API integration | [Integration guidance](INTEGRATION_GUIDE.md), [Public API contracts](API_CONTRACTS.md) |
| Glossary and reference pattern | [Glossary](GLOSSARY.md), [Reference repo comparison](audits/REFERENCE_COMPARISON.md) |
| Saved research, older QA and contribution proposals | [Reference index](references/README.md) |
| Local installation and verification | [Integration review, 2026-11-14](audits/INTEGRATION_REVIEW_2026-11-14.md) |

## Existing README links restored as honest status pages

[Improvement progress](improvement-progress.md), [coverage baseline](coverage-baseline.md), [DNS bypass matrix](dns-bypass-matrix.md), [local privacy update](local-privacy-update.md), [historical performance baseline placeholder](qa/performance-baseline-v2.2.0.md).

## File ownership policy

- **Human/agent-reviewed docs:** all above Markdown contracts, root product and security files; review when code changes.
- **Machine-generated docs:** `docs/generated/{MODULE_INDEX,SOURCE_SYMBOLS,EXTENSION_CATALOG,BUILD_FACTS,TEST_CATALOG}.md`; change via `scripts/docs_sync.py --write`. Never manually edit their generated regions.
- **Project-history records:** existing `.gsd` milestone files; do not erase or pretend they represent current state.
- **No documentation is permission to override real code semantics**; tests and code are the execution authority.
