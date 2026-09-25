package com.unblocker.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ContentType {
    AD,
    ADULT_CONTENT,
    NORMAL
}

enum class DetectionMethod {
    STATIC_LIST,
    PATTERN_MATCH,
    TLD_RULE,
    CUSTOM_BLACKLIST,
    WHITELIST,
    NONE
}

@Entity(
    tableName = "connections",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["category"]),
        Index(value = ["domain"]),
        Index(value = ["is_blocked"])
    ]
)
data class ConnectionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "source_app")
    val sourceApp: String = "System",
    @ColumnInfo(name = "domain")
    val domain: String,
    @ColumnInfo(name = "ip")
    val ip: String = "",
    @ColumnInfo(name = "port")
    val port: Int = 53,
    @ColumnInfo(name = "protocol")
    val protocol: String = "DNS",
    @ColumnInfo(name = "data_size")
    val dataSize: Long = 64,
    @ColumnInfo(name = "is_blocked")
    val isBlocked: Boolean = false,
    @ColumnInfo(name = "detection_method")
    val detectionMethod: String = DetectionMethod.NONE.name,
    @ColumnInfo(name = "category")
    val category: String = ContentType.NORMAL.name,
    @ColumnInfo(name = "confidence")
    val confidence: Float = 1.0f,
    @ColumnInfo(name = "reason")
    val reason: String = "Allowed"
)
