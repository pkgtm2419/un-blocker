# Exact installation tree and purpose

Extract the ZIP **at the top of the `un-blocker` repository** (same directory as `settings.gradle.kts`). This ZIP is an additive overlay, not a full application clone. Dotfolders are intentional.

```text
un-blocker/
├── AGENTS.md                   # All-agent mandatory project contract
├── PRODUCT.md                  # Product goals, features and non-goals
├── DESIGN.md                   # Compose design / accessibility rules
├── SECURITY.md                 # Vulnerability and security policy
├── CHANGELOG.md                # Changes tracked without fabricated history
├── INSTALL_DOC_PACK.md         # Setup, bootstrap and automation
├── FOLDER_STRUCTURE.md         # This directory map
├── .editorconfig               # Basic formatting conventions
├── .agents/
│   ├── rules/
│   │   ├── coding-conventions.md
│   │   ├── dns-safety.md
│   │   ├── privacy-security.md
│   │   ├── ui-compose.md
│   │   ├── gradle-dependencies.md
│   │   ├── docs-lifecycle.md
│   │   ├── testing-standards.md
│   │   └── release-safety.md
│   └── skills/
│       ├── docs-maintainer/SKILL.md
│       ├── feature-implementation/SKILL.md
│       ├── dns-protocol-change/SKILL.md
│       ├── privacy-security-review/SKILL.md
│       ├── filter-list-update/SKILL.md
│       ├── android-testing/SKILL.md
│       ├── compose-ui-change/SKILL.md
│       ├── bug-investigation/SKILL.md
│       ├── code-review/SKILL.md
│       └── release-verifier/SKILL.md
├── docs/
│   ├── README.md               # Documentation home and links
│   ├── ARCHITECTURE.md         # Runtime flow / module graph
│   ├── PROJECT_MODULES.md     # Gradle dependency and ownership
│   ├── FEATURE_MATRIX.md      # Feature ID / source evidence / status
│   ├── BEHAVIOR_CONTRACTS.md  # Exact block/allow precedence
│   ├── DNS_PIPELINE.md        # DNS packet and resolver contract
│   ├── FILTERING_RULES.md     # Datasets, parsing, rule limits
│   ├── PRIVACY_DATA_FLOW.md   # HMAC learning vs local logs
│   ├── SECURITY_REVIEW.md     # Threat model and review checklist
│   ├── CODE_STANDARDS.md      # Kotlin, Gradle, Python conventions
│   ├── BUILD_AND_TEST.md      # Commands and known environment facts
│   ├── TESTING_STRATEGY.md    # Unit, device, migration, fuzz
│   ├── TEST_MATRIX.md         # Changed files → required tests
│   ├── RELEASE_PROCESS.md     # Signing, verification and publishing
│   ├── UI_UX.md               # Navigation, status, accessibility
│   ├── CONFIGURATION.md      # Preferences, flags, CI env vars
│   ├── DEPENDENCIES.md       # Package and version catalog review
│   ├── DATASETS_AND_PROVENANCE.md
│   ├── PERFORMANCE.md        # Measurement methodology
│   ├── QUALITY_GATES.md      # PR completion criteria
│   ├── AGENT_WORKFLOW.md      # Antigravity development process
│   ├── IMPLEMENTATION_PLAYBOOK.md
│   ├── DOCUMENTATION_MAINTENANCE.md
│   ├── REPO_RECONCILIATION.md # Existing source/doc mismatches
│   ├── API_CONTRACTS.md       # Kotlin/Android internal contracts
│   ├── INTEGRATION_GUIDE.md
│   ├── FEATURE_CHANGE_TEMPLATE.md
│   ├── DECISIONS.md
│   ├── GLOSSARY.md
│   ├── TROUBLESHOOTING.md
│   ├── modules/               # 5 detailed Gradle module guides
│   ├── features/              # 9 detailed feature area guides
│   ├── audits/                # Static audit and reference comparison
│   ├── generated/             # Machine-generated inventories + README
│   ├── qa/                    # Honest historical baseline placeholder
│   ├── references/            # 8 historical documents + provenance index + QA JSON
│   └── superpowers/
│       ├── specs/             # Architecture design spec
│       └── plans/             # Phased implementation plan
├── scripts/
│   ├── docs_sync.py            # --write / --check inventory maintenance
│   ├── docs_contract.py       # Frontmatter, required docs, local links
│   └── tests/test_docs_tooling.py
└── .github/
    ├── PULL_REQUEST_TEMPLATE.md
    └── workflows/
        ├── documentation-check.yml
        └── documentation-sync.yml
```

Existing un-blocker source folders (`app`, `common`, `data-store`, `vpn-engine`, `ml-engine`), `README.md`, `PRIVACY.md`, `.gsd`, and the existing `.github/workflows` stay in place. **Do not extract into `docs/`, `app/` or `.agents/` directly.**

After extraction, run from repository root:

```bash
python3 scripts/docs_sync.py --write
python3 scripts/docs_contract.py
python3 scripts/docs_sync.py --check
python3 -m unittest discover -s scripts/tests -p 'test_docs_*.py' -v
```

Only `docs/generated/` content is safely rebuilt from source automatically. Semantic product/security documentation is updated by the code-changing developer or agent and reviewed in the associated PR; CI cannot guarantee accurate product prose from diffs alone.

The installed tree additionally contains [integration verification](docs/audits/INTEGRATION_REVIEW_2026-11-14.md) and [integration plan](docs/superpowers/plans/2026-11-14-documentation-integration.md). All five generated inventories are populated. Original inputs remain under `unb/` and in the root ZIP; [reference index](docs/references/README.md) explains which material was organized and which historical plans were not supplied.
