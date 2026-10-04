package com.unblocker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.unblocker.data.db.converter.DateConverters
import com.unblocker.data.db.dao.BlockRuleDao
import com.unblocker.data.db.dao.DnsLogDao
import com.unblocker.data.db.dao.WhitelistDao
import com.unblocker.data.db.entity.BlockRuleEntity
import com.unblocker.data.db.entity.DnsLogEntity
import com.unblocker.data.db.entity.WhitelistEntity

/**
 * Room database for un-blocker 2.0.0.
 *
 * ## Schema Version
 * Version 1 is the initial schema for the 2.0.0 modular rewrite.
 * When adding new columns or tables, increment [version] and provide a [Migration].
 *
 * ## WAL Mode
 * Write-Ahead Logging is enabled at construction time (via [DataStoreModule]) so that
 * the nightly ML worker's bulk writes do not block the VPN foreground service's async
 * log inserts.
 *
 * ## Tables
 * | Entity              | Table         | Purpose                                           |
 * |---------------------|---------------|---------------------------------------------------|
 * | [DnsLogEntity]      | `dns_logs`    | Every intercepted DNS query (VPN → DB async)     |
 * | [BlockRuleEntity]   | `block_rules` | Blocking rules from STATIC / ML / USER sources   |
 * | [WhitelistEntity]   | `whitelist`   | User-managed allow-list (overrides all blocks)   |
 */
@Database(
    entities = [
        DnsLogEntity::class,
        BlockRuleEntity::class,
        WhitelistEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DateConverters::class)
abstract class UnblockerDatabase : RoomDatabase() {

    abstract fun dnsLogDao(): DnsLogDao
    abstract fun blockRuleDao(): BlockRuleDao
    abstract fun whitelistDao(): WhitelistDao

    companion object {
        const val DATABASE_NAME = "unblocker_v2.db"
    }
}
