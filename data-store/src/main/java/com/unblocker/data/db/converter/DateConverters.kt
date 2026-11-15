package com.unblocker.data.db.converter

import androidx.room.TypeConverter
import java.util.Date

/**
 * Room conversions between nullable dates and epoch-millisecond SQLite values.
 */
class DateConverters {
    /**
     * Converts a stored timestamp to a Date.
     *
     * @param value Milliseconds since the Unix epoch, or null.
     * @return A new Date for [value], or null for a null value.
     */
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    /**
     * Converts a Date to its SQLite representation.
     *
     * @param date Date to encode, or null.
     * @return Milliseconds since the Unix epoch, or null for a null date.
     */
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time
}
