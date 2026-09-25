package com.unblocker.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.unblocker.app.data.dao.BlockedDomainDao
import com.unblocker.app.data.dao.ConnectionDao
import com.unblocker.app.data.dao.StatsDao
import com.unblocker.app.data.dao.WhitelistDao
import com.unblocker.app.data.model.BlockedDomain
import com.unblocker.app.data.model.ConnectionLog
import com.unblocker.app.data.model.ContentFilterStats
import com.unblocker.app.data.model.WhitelistDomain

@Database(
    entities = [
        ConnectionLog::class,
        BlockedDomain::class,
        WhitelistDomain::class,
        ContentFilterStats::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun connectionDao(): ConnectionDao
    abstract fun blockedDomainDao(): BlockedDomainDao
    abstract fun whitelistDao(): WhitelistDao
    abstract fun statsDao(): StatsDao

    fun checkIntegrity(): Boolean {
        return try {
            val cursor = openHelper.readableDatabase.query("PRAGMA integrity_check")
            var isOk = false
            if (cursor.moveToFirst()) {
                val result = cursor.getString(0)
                isOk = result.equals("ok", ignoreCase = true)
            }
            cursor.close()
            isOk
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "unblocker_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
