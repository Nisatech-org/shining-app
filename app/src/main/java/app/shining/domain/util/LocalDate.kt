package app.shining.domain.util

import android.os.Build
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class DaysOfWeek(
    val id: Int,
    val dayName: String
) {
    SATURDAY(0, "Saturday"),
    SUNDAY(1, "Sunday"),
    MONDAY(2, "Monday"),
    TUESDAY(3, "Tuesday"),
    WEDNESDAY(4, "Wednesday"),
    THURSDAY(5, "Thursday"),
    FRIDAY(6, "Friday");

    companion object {
        fun fromName(name: String): DaysOfWeek {
            return when (name.lowercase(Locale.ROOT)) {
                "saturday" -> SATURDAY
                "sunday" -> SUNDAY
                "monday" -> MONDAY
                "tuesday" -> TUESDAY
                "wednesday" -> WEDNESDAY
                "thursday" -> THURSDAY
                "friday" -> FRIDAY
                else -> SATURDAY
            }
        }
    }
}

fun String.toLocalDayName(): String {
    return when (this.lowercase(Locale.ROOT)) {
        "saturday" -> "شنبه"
        "sunday" -> "یکشنبه"
        "monday" -> "دوشنبه"
        "tuesday" -> "سه شنبه"
        "wednesday" -> "چهارشنبه"
        "thursday" -> "پنجشنبه"
        "friday" -> "جمعه"
        else -> this
    }
}

fun getToday(format: String = "yyyy-MM-dd"): String {
    val now = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        LocalDateTime.now(ZoneId.of("Asia/Tehran"))
    } else {
        return ""
    }

    val formatter = DateTimeFormatter.ofPattern(format)
    val date = now.format(formatter)

    return date
}

fun getPreviousSaturday(): String {
    var now = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        LocalDateTime.now(ZoneId.of("Asia/Tehran"))
    } else {
        return ""
    }

    now = if (now.dayOfWeek == DayOfWeek.SATURDAY) {
        now.withHour(0).withMinute(0).withSecond(0).withNano(0)
    } else {
        now.with(TemporalAdjusters.previous(DayOfWeek.SATURDAY))
            .withHour(0).withMinute(0).withSecond(0).withNano(0)
    }

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val date = now.format(formatter)

    return date
}

fun getNextSaturday(): String {
    // Get current date and time
    var now = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        LocalDateTime.now(ZoneId.of("Asia/Tehran"))
    } else {
        return ""
    }

    // If today is Saturday, find the next Saturday
    now = if (now.dayOfWeek == DayOfWeek.SATURDAY) {
        now.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
    } else {
        // If today is not Saturday, find the upcoming Saturday
        now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
    }

    // Set the time to 00:00:00
    now = now.withHour(0).withMinute(0).withSecond(0).withNano(0)

    // Format the date and time to the required pattern
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val date = now.format(formatter)
    return date
}
/**
 * The Jalali day of the month of a Gregorian date ("2026-11-28", a time after it is ignored),
 * or null if it can't be read. Used where only the day number is shown, like Reserve's day chips.
 */
fun String.toJalaliDayOfMonth(): Int? {
    val parts = take(10).split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return null
    val (gy, gm, gd) = parts
    // The standard arithmetic conversion (as in jalaali-js), valid for this and nearby centuries.
    val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 +
        gd + gDaysInMonth[gm - 1]
    days %= 12053
    days %= 1461
    if (days > 365) days = (days - 1) % 365
    return if (days < 186) 1 + days % 31 else 1 + (days - 186) % 30
}
