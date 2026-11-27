package app.shining.market.domain

import app.shining.domain.util.toGroupedLocalNumber
import app.shining.market.api.MealInfo
import app.shining.order.api.dto.reserve.SelfWeekProgramDto
import app.shining.order.domain.model.MealType
import app.shining.reserveinfo.domain.model.ReservationDayInfo
import app.shining.reserveinfo.domain.model.ReservationMealInfo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Turns Samad's reservations and menus into market meals, and holds the small rules the screens
 * share. No java.time here: the app still runs on Android 7.
 */
object MarketMeals {

    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    val TEHRAN: TimeZone = TimeZone.getTimeZone("Asia/Tehran")

    /** A reservation as a market meal, or null when it lacks Samad's ids (a copy saved before 1.13.0). */
    fun fromReservation(day: ReservationDayInfo, meal: ReservationMealInfo): MealInfo? {
        val date = day.date.take(10)
        if (!DATE.matches(date) || meal.selfId <= 0 || meal.foodId <= 0 || meal.mealTypeId <= 0) return null
        return MealInfo(
            date = date,
            mealTypeId = meal.mealTypeId,
            mealName = meal.mealText.ifBlank { MealType.getMealTypeById(meal.mealTypeId).getLocalName() },
            selfId = meal.selfId,
            selfName = meal.selfText,
            foodId = meal.foodId,
            foodName = meal.foodText,
            reservedPrice = meal.price,
        )
    }

    /** A food on a self's menu as a market meal. */
    fun fromMenu(item: SelfWeekProgramDto, selfName: String): MealInfo? {
        val date = item.date.take(10)
        if (!DATE.matches(date) || item.foodId <= 0) return null
        return MealInfo(
            date = date,
            mealTypeId = item.mealTypeId,
            mealName = MealType.getMealTypeById(item.mealTypeId).getLocalName(),
            selfId = item.selfId,
            selfName = selfName,
            foodId = item.foodId,
            foodName = item.foodName,
            reservedPrice = item.price.toLong(),
        )
    }

    /** A reserved meal the user can still sell, with its reservation id. */
    data class Sellable(val meal: MealInfo, val reserveId: Int)

    /** The user's upcoming meals that are reserved, not eaten and still being served. */
    fun sellable(days: List<ReservationDayInfo>, now: Long = System.currentTimeMillis()): List<Sellable> =
        days.flatMap { day ->
            day.mealInfo.filterNotNull()
                .filter { !it.consumed && !it.hasPassed }
                .mapNotNull { meal -> fromReservation(day, meal)?.let { Sellable(it, meal.id) } }
        }.filter { now < endsAt(it.meal) }
            .sortedWith(compareBy({ it.meal.date }, { endsAt(it.meal) }))

    /**
     * When a meal stops being served, in milliseconds: breakfast 10:00, lunch 14:00, dinner 21:00
     * (Tehran), the same rule as the server's.
     */
    fun endsAt(meal: MealInfo): Long {
        val (hour, minute) = when (meal.mealTypeId) {
            1 -> 14 to 0
            2, 3 -> 10 to 0
            4, 5 -> 21 to 0
            else -> 23 to 59
        }
        val parts = meal.date.split('-').mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return Long.MAX_VALUE
        return Calendar.getInstance(TEHRAN).apply {
            clear()
            set(parts[0], parts[1] - 1, parts[2], hour, minute, 0)
        }.timeInMillis
    }

    /** Today in Tehran as yyyy-MM-dd. */
    fun today(now: Long = System.currentTimeMillis()): String = format("yyyy-MM-dd", now)

    private fun format(pattern: String, millis: Long): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = TEHRAN }.format(Date(millis))

    /** "2026-11-22T07:35:12.345Z" (the server's times) → milliseconds, or null. */
    fun parseInstant(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm'Z'")
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(text)
            }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return null
    }

    /** "۱۲:۳۵" in Tehran, for a trade's times. */
    fun clock(millis: Long): String = format("HH:mm", millis).toGroupedLocalNumber().replace("٬", "")

    /** Whole Persian day names by weekday, as Samad writes them. */
    private val WEEKDAYS = mapOf(
        Calendar.SATURDAY to "شنبه", Calendar.SUNDAY to "یکشنبه", Calendar.MONDAY to "دوشنبه",
        Calendar.TUESDAY to "سه‌شنبه", Calendar.WEDNESDAY to "چهارشنبه", Calendar.THURSDAY to "پنجشنبه",
        Calendar.FRIDAY to "جمعه",
    )

    /** "امروز", "فردا", or the weekday's name, for a yyyy-MM-dd date. */
    fun dayLabel(date: String, now: Long = System.currentTimeMillis()): String {
        val today = today(now)
        if (date == today) return "امروز"
        val tomorrow = today(now + 24L * 60 * 60 * 1000)
        if (date == tomorrow) return "فردا"
        val parts = date.split('-').mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return date
        val calendar = Calendar.getInstance(TEHRAN).apply {
            clear()
            set(parts[0], parts[1] - 1, parts[2], 12, 0, 0)
        }
        return WEEKDAYS[calendar.get(Calendar.DAY_OF_WEEK)] ?: date
    }
}

/** 120000 → "۱۲۰٬۰۰۰ تومن". Market prices are in toman, as the Telegram groups write them. */
fun Long.toman(): String = "${toGroupedLocalNumber()} تومن"

/**
 * What a student typed as a price → toman, or null. "۱۲۰" and "120" mean 120,000 (people write
 * thousands), anything from 1,000 up is taken as is.
 */
fun parsePrice(text: String): Long? {
    val digits = text.map { char ->
        when (char) {
            in '۰'..'۹' -> '0' + (char - '۰')
            in '٠'..'٩' -> '0' + (char - '٠')
            else -> char
        }
    }.filter { it.isDigit() }.joinToString("")
    val value = digits.take(9).toLongOrNull() ?: return null
    if (value <= 0) return null
    return if (value < 1_000) value * 1_000 else value
}

/** "۱۲ فروش · ۱ مشکل", or "تازه‌وارد" for someone with no trades yet. */
fun app.shining.market.api.Reputation.label(asSeller: Boolean): String {
    val count = if (asSeller) sales else purchases
    if (count == 0 && problems == 0) return "تازه‌وارد"
    val what = if (asSeller) "فروش" else "خرید"
    val problemText = if (problems > 0) " · ${problems.toLong().toGroupedLocalNumber()} مشکل" else ""
    return "${count.toLong().toGroupedLocalNumber()} $what$problemText"
}
