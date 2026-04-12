package com.cprint.app.data.local

import androidx.room.TypeConverter
import com.cprint.app.domain.model.PrintJobStatus
import com.cprint.app.domain.model.PrinterStatus
import java.util.Date

/**
 * Room type converters for complex data types
 */
class Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromPrintJobStatus(status: PrintJobStatus): String {
        return status.name
    }

    @TypeConverter
    fun toPrintJobStatus(value: String): PrintJobStatus {
        return PrintJobStatus.valueOf(value)
    }

    @TypeConverter
    fun fromPrinterStatus(status: PrinterStatus): String {
        return status.name
    }

    @TypeConverter
    fun toPrinterStatus(value: String): PrinterStatus {
        return PrinterStatus.valueOf(value)
    }

    @TypeConverter
    fun fromStringList(value: String): List<String> {
        return if (value.isEmpty()) emptyList() else value.split(",")
    }

    @TypeConverter
    fun toStringList(list: List<String>): String {
        return list.joinToString(",")
    }
}
