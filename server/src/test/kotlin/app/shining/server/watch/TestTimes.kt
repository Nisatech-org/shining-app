package app.shining.server.watch

import app.shining.server.discovery.TEHRAN
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** A Tehran wall-clock time in November 2026. */
fun tehran(day: Int, hour: Int, minute: Int = 0): Instant =
    LocalDateTime.of(2026, 11, day, hour, minute).atZone(TEHRAN).toInstant()

fun nov(day: Int): LocalDate = LocalDate.of(2026, 11, day)

/** Sunday 8 November 2026 starts the checks for the week of Saturday 14 November. */
val WATCHED: LocalDate = nov(14)

/** Saturday to Wednesday of the watched week. */
val FULL_WEEK: Set<LocalDate> = (14..18).map(::nov).toSet()
