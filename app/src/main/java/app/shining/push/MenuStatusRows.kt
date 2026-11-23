package app.shining.push

import app.shining.domain.util.toLocalNumber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class MenuState { Published, Partial, Waiting }

/** One line of the Home card: a vendor and how far its next-week menu is. */
data class MenuStatusRow(val name: String, val state: MenuState, val detail: String)

private val TEHRAN: TimeZone = TimeZone.getTimeZone("Asia/Tehran")

private val WEEKDAY_NAMES = mapOf(
    Calendar.SATURDAY to "شنبه",
    Calendar.SUNDAY to "یکشنبه",
    Calendar.MONDAY to "دوشنبه",
    Calendar.TUESDAY to "سه‌شنبه",
    Calendar.WEDNESDAY to "چهارشنبه",
    Calendar.THURSDAY to "پنجشنبه",
    Calendar.FRIDAY to "جمعه",
)

private val DAY_OF_WEEK_NAMES = mapOf(
    "SATURDAY" to "شنبه",
    "SUNDAY" to "یکشنبه",
    "MONDAY" to "دوشنبه",
    "TUESDAY" to "سه‌شنبه",
    "WEDNESDAY" to "چهارشنبه",
    "THURSDAY" to "پنجشنبه",
    "FRIDAY" to "جمعه",
)

/** Saturday to Wednesday. */
private const val WEEK_DAYS = 5

/**
 * The Home card's lines for the vendors the user wants alerts for:
 * «از سه‌شنبه ۱۴:۰۳» once out, «۲ روز از ۵» while partial, «معمولاً سه‌شنبه حدود ۱۴:۰۰» or
 * «هنوز نیومده» before.
 */
fun menuStatusRows(status: ServerStatus, isVendorEnabled: (String) -> Boolean): List<MenuStatusRow> =
    status.vendors.filter { isVendorEnabled(it.key) }.map { vendor ->
        val since = vendor.firstSeenAt?.let(::tehranWeekdayTime)
        when {
            vendor.state == "complete" -> MenuStatusRow(
                vendor.name, MenuState.Published, since?.let { "از $it" } ?: "اومده",
            )

            vendor.days.isNotEmpty() -> MenuStatusRow(
                vendor.name,
                MenuState.Partial,
                "${vendor.days.size.toString().toLocalNumber()} روز از ${WEEK_DAYS.toString().toLocalNumber()}" +
                    (since?.let { "، از $it" } ?: ""),
            )

            else -> MenuStatusRow(
                vendor.name,
                MenuState.Waiting,
                vendor.usualArrival?.let { usual ->
                    DAY_OF_WEEK_NAMES[usual.dayOfWeek]?.let { "معمولاً $it حدود ${usual.time.toLocalNumber()}" }
                } ?: "هنوز نیومده",
            )
        }
    }

/** "2026-11-10T10:33:00Z" (fractions allowed) → «سه‌شنبه ۱۴:۰۳» in Tehran time. */
internal fun tehranWeekdayTime(instant: String): String? {
    val date = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(instant.take(19))
    }.getOrNull() ?: return null
    val calendar = Calendar.getInstance(TEHRAN).apply { time = date }
    val time = String.format(Locale.US, "%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
    return "${WEEKDAY_NAMES.getValue(calendar.get(Calendar.DAY_OF_WEEK))} ${time.toLocalNumber()}"
}
