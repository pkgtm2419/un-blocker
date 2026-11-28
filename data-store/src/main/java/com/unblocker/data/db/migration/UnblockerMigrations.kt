package com.unblocker.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit non-destructive Room migrations.
 */
object UnblockerMigrations {
    /**
     * Adds domain aggregates, events, and indices without removing version 1 tables.
     */
    val MIGRATION_1_2 =
        object : Migration(1, 2) {
            /**
             * Creates the version 2 log tables if absent.
             *
             * @param db Connection supplied by Room for the migration transaction.
             */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `domain_stats` (
                        `domain` TEXT NOT NULL,
                        `call_count` INTEGER NOT NULL,
                        `blocked_count` INTEGER NOT NULL,
                        `allowed_count` INTEGER NOT NULL,
                        `first_seen` INTEGER NOT NULL,
                        `last_seen` INTEGER NOT NULL,
                        `last_decision` INTEGER NOT NULL,
                        `last_reason` INTEGER NOT NULL,
                        PRIMARY KEY(`domain`)
                    )
                    """.trimIndent(),
                )

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_domain_stats_last_seen` ON `domain_stats` (`last_seen`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_domain_stats_call_count` ON `domain_stats` (`call_count`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `domain_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `domain` TEXT NOT NULL,
                        `ts` INTEGER NOT NULL,
                        `decision` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_domain_events_domain_ts` ON `domain_events` (`domain`, `ts`)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_domain_events_ts` ON `domain_events` (`ts`)")
            }
        }

    /**
     * Adds readable `user_rule` table without modifying HMAC stores or existing tables (WP-20 / D2).
     */
    val MIGRATION_2_3 =
        object : Migration(2, 3) {
            /**
             * Creates the version 3 `user_rule` table and its lookup index if absent.
             *
             * @param db Connection supplied by Room for the migration transaction.
             */
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_rule` (
                        `domain` TEXT NOT NULL,
                        `action` INTEGER NOT NULL,
                        `is_whole_site` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`domain`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_user_rule_action_updated_at` ON `user_rule` (`action`, `updated_at`)",
                )
            }
        }

    /**
     * Adds `last_outcome`, `last_rule`, `last_source`, `fail_count`, and `last_network_type` to `domain_stats` (WP-21).
     */
    val MIGRATION_3_4 =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `domain_stats` ADD COLUMN `last_outcome` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `domain_stats` ADD COLUMN `last_rule` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `domain_stats` ADD COLUMN `last_source` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `domain_stats` ADD COLUMN `fail_count` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `domain_stats` ADD COLUMN `last_network_type` TEXT NOT NULL DEFAULT ''")
            }
        }

    /**
     * Explicit migration paths registered by the database provider.
     */
    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
