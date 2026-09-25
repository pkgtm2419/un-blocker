package com.unblocker.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "content_filter_stats")
data class ContentFilterStats(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "date")
    val date: String, // YYYY-MM-DD
    @ColumnInfo(name = "total_connections")
    val totalConnections: Long = 0,
    @ColumnInfo(name = "ad_connections")
    val adConnections: Long = 0,
    @ColumnInfo(name = "adult_connections")
    val adultConnections: Long = 0,
    @ColumnInfo(name = "blocked_ads")
    val blockedAds: Long = 0,
    @ColumnInfo(name = "blocked_adult")
    val blockedAdult: Long = 0,
    @ColumnInfo(name = "allowed_connections")
    val allowedConnections: Long = 0,
    @ColumnInfo(name = "data_saved_bytes")
    val dataSavedBytes: Long = 0
)
