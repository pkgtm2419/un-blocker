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
 * Hilt providers for the versioned Room database and its DAOs, using write-ahead logging.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    /**
     * Builds the singleton database with explicit non-destructive migrations.
     *
     * @param context Application context owning the database files.
     * @return Room database configured for write-ahead logging.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): UnblockerDatabase =
        Room
            .databaseBuilder(
                context,
                UnblockerDatabase::class.java,
                UnblockerDatabase.DATABASE_NAME,
            ).setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(*com.unblocker.data.db.migration.UnblockerMigrations.ALL)
            .build()

    /**
     * Provides the database-owned DnsLog DAO.
     *
     * @param db Shared Room database.
     * @return DAO attached to [db].
     */
    @Provides
    @Singleton
    fun provideDnsLogDao(db: UnblockerDatabase): DnsLogDao = db.dnsLogDao()

    /**
     * Provides the database-owned DomainLog DAO.
     *
     * @param db Shared Room database.
     * @return DAO attached to [db].
     */
    @Provides
    @Singleton
    fun provideDomainLogDao(db: UnblockerDatabase): com.unblocker.data.db.dao.DomainLogDao = db.domainLogDao()

    /**
     * Provides the database-owned BlockRule DAO.
     *
     * @param db Shared Room database.
     * @return DAO attached to [db].
     */
    @Provides
    @Singleton
    fun provideBlockRuleDao(db: UnblockerDatabase): BlockRuleDao = db.blockRuleDao()

    /**
     * Provides the database-owned Whitelist DAO.
     *
     * @param db Shared Room database.
     * @return DAO attached to [db].
     */
    @Provides
    @Singleton
    fun provideWhitelistDao(db: UnblockerDatabase): WhitelistDao = db.whitelistDao()
}
