# `:data-store` — storage, indexes and repositories

**Source snapshot:** `main` `8934519` (2026-11-10); validate against checkout.

`db/UnblockerDatabase.kt` exports **schema version 2**. Entities include `DnsLogEntity`, `BlockRuleEntity`, `WhitelistEntity`, `DomainStatEntity`, `DomainEventEntity`; DAOs include `DnsLogDao`, `DomainLogDao`, `BlockRuleDao`, `WhitelistDao`. `migration/UnblockerMigrations.kt` declares non-destructive `MIGRATION_1_2`; exported schema files in `data-store/schemas/` must be versioned.

`cache/{BloomFilterManager,OffHeapRuleEngine,RadixTreeFilter,RuleCompiler}.kt` and `repository/{FilterRepository,LogRepository}.kt` provide local filtering storage/index/batch APIs.

**Privacy:** `domain_stats`/`domain_events` have readable domain fields. The app's HMAC user rule/learning stores are elsewhere (`app/src/main/java/com/unblocker/app/logic/analysis/`); do not describe this module as entirely hashed. Test migration, retention, batch ingest, clear and data exposure.

## Module rules

Keep dependencies consistent with `settings.gradle.kts`, use checked module build files, test existing contracts before changes, update `docs/PROJECT_MODULES.md`, source-controlled tests, feature matrix and generated symbol/module inventory. Record risk and verification in PR. [Module index](README.md) | [Architecture](../ARCHITECTURE.md).
