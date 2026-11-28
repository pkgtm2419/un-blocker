package com.unblocker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.unblocker.data.db.converter.DateConverters
import com.unblocker.data.db.dao.BlockRuleDao
import com.unblocker.data.db.dao.DnsLogDao
import com.unblocker.data.db.dao.DomainLogDao
import com.unblocker.data.db.dao.UserRuleDao
import com.unblocker.data.db.dao.WhitelistDao
import com.unblocker.data.db.entity.BlockRuleEntity
import com.unblocker.data.db.entity.DnsLogEntity
import com.unblocker.data.db.entity.DomainEventEntity
import com.unblocker.data.db.entity.DomainStatEntity
import com.unblocker.data.db.entity.UserRuleEntity
import com.unblocker.data.db.entity.WhitelistEntity

/**
 * Room schema version 3 for library rules, legacy DNS logs, readable domain statistics, and user exceptions.
 */
@Database(
    entities = [
        DnsLogEntity::class,
        BlockRuleEntity::class,
        WhitelistEntity::class,
        DomainStatEntity::class,
        DomainEventEntity::class,
        UserRuleEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(DateConverters::class)
abstract class UnblockerDatabase : RoomDatabase() {
    /**
     * Accesses legacy DNS logs.
     *
     * @return DAO owned by this database instance.
     */
    abstract fun dnsLogDao(): DnsLogDao

    /**
     * Accesses domain aggregates and events.
     *
     * @return DAO owned by this database instance.
     */
    abstract fun domainLogDao(): DomainLogDao

    /**
     * Accesses block rules.
     *
     * @return DAO owned by this database instance.
     */
    abstract fun blockRuleDao(): BlockRuleDao

    /**
     * Accesses readable allow entries.
     *
     * @return DAO owned by this database instance.
     */
    abstract fun whitelistDao(): WhitelistDao

    /**
     * Accesses readable user exceptions (WP-20 / D2).
     *
     * @return DAO owned by this database instance.
     */
    abstract fun userRuleDao(): UserRuleDao

    /**
     * Stable database file identifier.
     */
    companion object {
        /**
         * Default database filename used by the library Hilt provider.
         */
        const val DATABASE_NAME = "unblocker_v2.db"
    }
}
