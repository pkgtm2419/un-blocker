package com.unblocker.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "whitelist_domains",
    indices = [
        Index(value = ["domain"], unique = true)
    ]
)
data class WhitelistDomain(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "domain")
    val domain: String,
    @ColumnInfo(name = "notes")
    val notes: String = "",
    @ColumnInfo(name = "added_date")
    val addedDate: Long = System.currentTimeMillis()
)
