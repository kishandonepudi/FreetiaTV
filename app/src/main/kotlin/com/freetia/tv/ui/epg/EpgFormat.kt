package com.freetia.tv.ui.epg

import com.freetia.tv.data.epg.DAY_MS
import com.freetia.tv.data.repository.RegionFilter
import com.freetia.tv.data.repository.RegionRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal object EpgFormat {
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    fun time(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        timeFormat.format(Instant.ofEpochMilli(ms).atZone(zone))

    fun day(ms: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> dayFormat.format(date)
        }
    }

    fun range(start: Long, stop: Long, now: Long): String = "${day(start, now)}, ${time(start)} to ${time(stop)}"

    fun regionLabel(stored: String): String {
        val f = RegionFilter.decode(stored)
        return when {
            f.isAll -> "All regions"
            f.country.isNotEmpty() && f.language.isNotEmpty() -> "${RegionRules.countryName(f.country)}, ${RegionRules.languageName(f.language)}"
            f.country.isNotEmpty() -> RegionRules.countryName(f.country)
            else -> RegionRules.languageName(f.language)
        }
    }

    /** Rounds down to the half hour so the time header always ticks on :00 and :30. */
    fun floorHalfHour(ms: Long): Long = Math.floorDiv(ms, HALF_HOUR) * HALF_HOUR

    const val HALF_HOUR = 30L * 60 * 1000
    const val HOUR = 60L * 60 * 1000
    val DAY = DAY_MS
}
