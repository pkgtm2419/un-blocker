package com.unblocker.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit non-destructive Room migrations.
 */
object UnblockerMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
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
            """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_domain_stats_last_seen` ON `domain_stats` (`last_seen`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_domain_stats_call_count` ON `domain_stats` (`call_count`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `domain_events` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `domain` TEXT NOT NULL,
                    `ts` INTEGER NOT NULL,
                    `decision` INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_domain_events_domain_ts` ON `domain_events` (`domain`, `ts`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_domain_events_ts` ON `domain_events` (`ts`)")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
