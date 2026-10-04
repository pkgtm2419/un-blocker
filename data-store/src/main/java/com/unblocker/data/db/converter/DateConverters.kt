package com.unblocker.data.db.converter

import androidx.room.TypeConverter
import java.util.Date

/**
 * Room TypeConverters for types that SQLite cannot store natively.
 *
 * Only [Long] ↔ [Date] is needed for now; extend this file as new entity field
 * types are added (e.g., [com.unblocker.common.model.RuleSource] if we switch from
 * the String-name approach to a typed enum column).
 */
class DateConverters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time
}
