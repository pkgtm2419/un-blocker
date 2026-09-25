package com.unblocker.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocked_domains",
    indices = [
        Index(value = ["domain"], unique = true),
        Index(value = ["category"])
    ]
)
data class BlockedDomain(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "domain")
    val domain: String,
    @ColumnInfo(name = "category")
    val category: String = "CUSTOM", // AD, ADULT, CUSTOM
    @ColumnInfo(name = "reason")
    val reason: String = "User Blocklist",
    @ColumnInfo(name = "source")
    val source: String = "User",
    @ColumnInfo(name = "priority")
    val priority: Int = 10,
    @ColumnInfo(name = "added_date")
    val addedDate: Long = System.currentTimeMillis()
)
