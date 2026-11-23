package app.shining.reserveinfo.ui.model

import app.shining.domain.util.toLocalNumber

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/** Tells the user they're looking at reservations saved [savedAt], because Samad didn't answer. */
fun savedNoticeText(savedAt: Long, now: Long): String =
    "به سماد وصل نشدیم. این برنامه‌ی ذخیره‌شده‌ی ${timeAgo(savedAt, now)} هست."

/** "همین الان", "۵ دقیقه پیش", "۳ ساعت پیش" or "۲ روز پیش". */
fun timeAgo(then: Long, now: Long): String {
    val elapsed = (now - then).coerceAtLeast(0)
    return when {
        elapsed < MINUTE -> "همین الان"
        elapsed < HOUR -> "${elapsed / MINUTE} دقیقه پیش".toLocalNumber()
        elapsed < DAY -> "${elapsed / HOUR} ساعت پیش".toLocalNumber()
        else -> "${elapsed / DAY} روز پیش".toLocalNumber()
    }
}
