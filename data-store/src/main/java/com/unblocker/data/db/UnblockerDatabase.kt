package com.unblocker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.unblocker.data.db.converter.DateConverters
import com.unblocker.data.db.dao.BlockRuleDao
import com.unblocker.data.db.dao.DnsLogDao
import com.unblocker.data.db.dao.WhitelistDao
import com.unblocker.data.db.dao.DomainLogDao
import com.unblocker.data.db.entity.BlockRuleEntity
import com.unblocker.data.db.entity.DnsLogEntity
import com.unblocker.data.db.entity.DomainEventEntity
import com.unblocker.data.db.entity.DomainStatEntity
import com.unblocker.data.db.entity.WhitelistEntity

/**
 * Room database for un-blocker 2.0.x.
 */
@Database(
    entities = [
        DnsLogEntity::class,
        BlockRuleEntity::class,
        WhitelistEntity::class,
        DomainStatEntity::class,
        DomainEventEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(DateConverters::class)
abstract class UnblockerDatabase : RoomDatabase() {

    abstract fun dnsLogDao(): DnsLogDao
    abstract fun domainLogDao(): DomainLogDao
    abstract fun blockRuleDao(): BlockRuleDao
    abstract fun whitelistDao(): WhitelistDao

    companion object {
        const val DATABASE_NAME = "unblocker_v2.db"
    }
}
