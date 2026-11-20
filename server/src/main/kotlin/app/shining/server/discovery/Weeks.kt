package app.shining.server.discovery

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

val TEHRAN: ZoneId = ZoneId.of("Asia/Tehran")

/** Samad weeks start on Saturday. Returns this week's and next week's Saturdays. */
fun weekStarts(today: LocalDate): List<LocalDate> {
    val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
    return listOf(thisWeek, thisWeek.plusWeeks(1))
}

/** The `weekStartDate` value Samad expects, e.g. `2026-11-07 00:00:00`. */
fun samadWeekParam(saturday: LocalDate): String = "$saturday 00:00:00"
