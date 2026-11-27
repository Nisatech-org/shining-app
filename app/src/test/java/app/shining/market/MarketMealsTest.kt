package app.shining.market

import app.shining.market.api.MealInfo
import app.shining.market.api.Reputation
import app.shining.market.api.TradeView
import app.shining.market.domain.MarketHistory
import app.shining.market.domain.MarketMeals
import app.shining.market.domain.MarketSession
import app.shining.market.domain.MarketSessions
import app.shining.market.domain.label
import app.shining.market.domain.parsePrice
import app.shining.market.domain.toman
import app.shining.market.ui.countdown
import app.shining.reserveinfo.domain.model.ReservationDayInfo
import app.shining.reserveinfo.domain.model.ReservationMealInfo
import app.shining.testing.FakeKeyValueStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MarketMealsTest {

    /** A Tehran wall-clock time on 22 November 2026, in milliseconds. */
    private fun tehran(day: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance(MarketMeals.TEHRAN).apply {
        clear()
        set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
    }.timeInMillis

    private val lunch = ReservationMealInfo(
        selfText = "سلف مرکزی", foodText = "قیمه", mealText = "ناهار", price = 350_000, id = 77,
        selfId = 1, foodId = 345, mealTypeId = 1,
    )
    private val dinner = lunch.copy(foodText = "کوکو", mealText = "شام", id = 78, foodId = 346, mealTypeId = 5)
    private val today = ReservationDayInfo(name = "یکشنبه", iranianDate = "1405/09/01", date = "2026-11-22T00:00:00", mealInfo = listOf(lunch, dinner))
    private val tomorrow = today.copy(name = "دوشنبه", date = "2026-11-23T00:00:00", mealInfo = listOf(lunch.copy(id = 79)))

    @Test
    fun `a reservation becomes a meal with the server's board key`() {
        val meal = MarketMeals.fromReservation(today, lunch)!!
        assertEquals("2026-11-22_1_1_345", meal.key)
        assertEquals("قیمه", meal.foodName)
        assertEquals(350_000, meal.reservedPrice)
        // Copies saved before 1.13.0 have no Samad ids: they can't be sold until reloaded.
        assertNull(MarketMeals.fromReservation(today, lunch.copy(selfId = 0, foodId = 0, mealTypeId = 0)))
    }

    @Test
    fun `meals end like the server says`() {
        val meal = MarketMeals.fromReservation(today, lunch)!!
        assertEquals(tehran(22, 14), MarketMeals.endsAt(meal))
        assertEquals(tehran(22, 21), MarketMeals.endsAt(meal.copy(mealTypeId = 5)))
        assertEquals(tehran(22, 10), MarketMeals.endsAt(meal.copy(mealTypeId = 3)))
    }

    @Test
    fun `only meals not eaten and still served can be sold, soonest first`() {
        val days = listOf(tomorrow, today)
        assertEquals(listOf(77, 78, 79), MarketMeals.sellable(days, tehran(22, 11)).map { it.reserveId })
        // After lunch ends, today's dinner comes first.
        assertEquals(listOf(78, 79), MarketMeals.sellable(days, tehran(22, 14)).map { it.reserveId })
        val eaten = today.copy(mealInfo = listOf(lunch.copy(consumed = true), dinner))
        assertEquals(listOf(78), MarketMeals.sellable(listOf(eaten), tehran(22, 11)).map { it.reserveId })
    }

    @Test
    fun `prices are read the way students write them`() {
        assertEquals(120_000L, parsePrice("120000"))
        assertEquals(120_000L, parsePrice("۱۲۰٬۰۰۰"))
        assertEquals(120_000L, parsePrice("۱۲۰"))
        assertEquals(95_000L, parsePrice("95"))
        assertNull(parsePrice(""))
        assertNull(parsePrice("0"))
        assertEquals("۱۲۰٬۰۰۰ تومن", 120_000L.toman())
    }

    @Test
    fun `day labels and the server's times`() {
        assertEquals("امروز", MarketMeals.dayLabel("2026-11-22", tehran(22, 9)))
        assertEquals("فردا", MarketMeals.dayLabel("2026-11-23", tehran(22, 9)))
        assertEquals("چهارشنبه", MarketMeals.dayLabel("2026-11-25", tehran(22, 9)))
        assertEquals(tehran(22, 11, 5), MarketMeals.parseInstant("2026-11-22T07:35:00Z"))
        assertEquals(tehran(22, 11, 5) + 123, MarketMeals.parseInstant("2026-11-22T07:35:00.123Z"))
        assertNull(MarketMeals.parseInstant("not a time"))
        assertEquals("۴:۰۵", countdown(tehran(22, 11, 5), tehran(22, 11, 5) - 245_000))
        assertEquals("۰:۰۰", countdown(tehran(22, 11, 5), tehran(22, 11, 6)))
    }

    @Test
    fun `reputation reads like the listing shows it`() {
        assertEquals("تازه‌وارد", Reputation().label(asSeller = true))
        assertEquals("۱۲ فروش", Reputation(sales = 12).label(asSeller = true))
        assertEquals("۳ خرید · ۱ مشکل", Reputation(purchases = 3, problems = 1).label(asSeller = false))
    }

    @Test
    fun `a bought code stays on the phone after the server forgets it`() {
        val history = MarketHistory(FakeKeyValueStore(), Json { ignoreUnknownKeys = true })
        val meal = MealInfo("2026-11-22", 1, "ناهار", 1, "سلف", 345, "قیمه")
        val trade = TradeView(
            id = 5, key = meal.key, meal = meal, role = "buyer", origin = "buy_now", price = 120_000,
            status = TradeView.COMPLETED, createdAt = "2026-11-22T07:35:00Z", payBy = "2026-11-22T07:40:00Z", code = "987654",
        )
        history.save("401100001", listOf(trade))
        history.save("401100001", listOf(trade.copy(code = null, buyerOk = true)))
        val saved = history.get("401100001", 5)!!
        assertEquals("987654", saved.code)
        assertEquals(true, saved.buyerOk)
        assertTrue(history.all("402200002").isEmpty())
    }

    @Test
    fun `joining listens on the account's topic and removing the account stops it`() = runTest {
        val topics = mutableListOf<Pair<String, Boolean>>()
        val sessions = MarketSessions(FakeKeyValueStore(), Json, setTopic = { topic, on -> topics += topic to on })
        sessions.save("401100001", MarketSession("token-abc", "market-123"))
        assertEquals("token-abc", sessions.get("401100001")?.token)
        sessions.subscribeAll(listOf("401100001", "402200002"))
        sessions.deleteAccountData("401100001")
        assertNull(sessions.get("401100001"))
        assertEquals(
            listOf("market-123" to true, "market-123" to true, "market-123" to false),
            topics,
        )
    }
}
