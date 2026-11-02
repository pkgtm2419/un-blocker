package com.unblocker.data.db.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class UnblockerMigrationsTest {

    @Test
    fun testMigration1To2ExecutesExpectedSchemaDdl() {
        assertEquals(1, UnblockerMigrations.MIGRATION_1_2.startVersion)
        assertEquals(2, UnblockerMigrations.MIGRATION_1_2.endVersion)

        val executedSql = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        UnblockerMigrations.MIGRATION_1_2.migrate(db)

        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `domain_stats`") })
        assertTrue(executedSql.any { it.contains("CREATE INDEX IF NOT EXISTS `index_domain_stats_last_seen`") })
        assertTrue(executedSql.any { it.contains("CREATE INDEX IF NOT EXISTS `index_domain_stats_call_count`") })
        assertTrue(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `domain_events`") })
        assertTrue(executedSql.any { it.contains("CREATE INDEX IF NOT EXISTS `index_domain_events_domain_ts`") })
        assertTrue(executedSql.any { it.contains("CREATE INDEX IF NOT EXISTS `index_domain_events_ts`") })
    }
}
