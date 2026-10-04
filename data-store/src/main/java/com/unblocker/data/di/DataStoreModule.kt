package com.unblocker.data.di

import android.content.Context
import androidx.room.Room
import com.unblocker.data.db.UnblockerDatabase
import com.unblocker.data.db.dao.BlockRuleDao
import com.unblocker.data.db.dao.DnsLogDao
import com.unblocker.data.db.dao.WhitelistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the `:data-store` module.
 *
 * Provides:
 * - [UnblockerDatabase] as a singleton Room database with WAL mode enabled.
 * - DAO instances extracted from the database.
 * - [BloomFilterManager] and [RadixTreeFilter] are self-providing via [@Inject] constructors
 *   and do not need explicit [Provides] methods here.
 *
 * ## WAL Mode
 * [setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)] ensures that:
 * - Concurrent reads from the UI Flow collectors are never blocked by the ML worker's bulk writes.
 * - The VPN foreground service's high-frequency async log inserts do not stall reads.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): UnblockerDatabase =
        Room.databaseBuilder(
            context,
            UnblockerDatabase::class.java,
            UnblockerDatabase.DATABASE_NAME
        )
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .fallbackToDestructiveMigration() // Replace with proper migrations before production
            .build()

    @Provides
    @Singleton
    fun provideDnsLogDao(db: UnblockerDatabase): DnsLogDao = db.dnsLogDao()

    @Provides
    @Singleton
    fun provideBlockRuleDao(db: UnblockerDatabase): BlockRuleDao = db.blockRuleDao()

    @Provides
    @Singleton
    fun provideWhitelistDao(db: UnblockerDatabase): WhitelistDao = db.whitelistDao()
}
