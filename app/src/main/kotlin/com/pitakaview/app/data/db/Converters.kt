package com.pitakaview.app.data.db

import androidx.room3.ColumnTypeConverter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/** Dates are stored as numbers: LocalDate as epoch day, Instant as epoch millis, DayOfWeek as ISO 1–7. */
class Converters {
    @ColumnTypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @ColumnTypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @ColumnTypeConverter
    fun instantToEpochMilli(value: Instant?): Long? = value?.toEpochMilli()

    @ColumnTypeConverter
    fun epochMilliToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @ColumnTypeConverter
    fun dayOfWeekToIso(value: DayOfWeek?): Int? = value?.value

    @ColumnTypeConverter
    fun isoToDayOfWeek(value: Int?): DayOfWeek? = value?.let(DayOfWeek::of)
}
