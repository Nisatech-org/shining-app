package app.shining.server.http

import app.shining.server.discovery.TEHRAN
import app.shining.server.watch.ArrivalSample
import app.shining.server.watch.FULL_WEEK
import app.shining.server.watch.VendorWeek
import app.shining.server.watch.WATCHED
import app.shining.server.watch.WatchState
import app.shining.server.watch.nov
import app.shining.server.watch.tehran
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatusTest {

    private val state = WatchState(
        initialized = true,
        weekStart = WATCHED,
        vendors = mapOf(
            "self" to VendorWeek(FULL_WEEK, tehran(9, 0)),
            "kalana" to VendorWeek(setOf(nov(14), nov(15)), tehran(10, 14)),
        ),
        history = listOf(nov(7), nov(7).minusWeeks(1), nov(7).minusWeeks(2)).map { week ->
            ArrivalSample("kalana", week, week.minusDays(4).atTime(14, 0).atZone(TEHRAN).toInstant())
        },
        coveredSelfIds = setOf(24, 1),
        lastSuccessAt = tehran(10, 14),
    )

    @Test
    fun `each vendor gets its state, days and usual time`() {
        val status = buildStatus(state, tehran(10, 15))

        assertEquals("2026-11-14", status.week)
        assertEquals(listOf(1, 24), status.coveredSelfIds)
        assertTrue(status.lastCheckOk)
        val byKey = status.vendors.associateBy { it.key }
        assertEquals("complete", byKey.getValue("self").state)
        assertEquals("partial", byKey.getValue("kalana").state)
        assertEquals("none", byKey.getValue("unifood").state)
        assertEquals(listOf("2026-11-14", "2026-11-15"), byKey.getValue("kalana").days)
        assertEquals(UsualArrival("TUESDAY", "14:00"), byKey.getValue("kalana").usualArrival)
        assertNull(byKey.getValue("self").usualArrival)
    }

    @Test
    fun `last week's state doesn't leak into a new week`() {
        val status = buildStatus(state, tehran(16, 1))
        assertEquals("2026-11-21", status.week)
        assertTrue(status.vendors.all { it.state == "none" })
    }

    @Test
    fun `the endpoint serves the status as JSON`() = testApplication {
        application { statusRoutes({ buildStatus(state, tehran(10, 15)) }) }

        val response = client.get("/v1/status")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val decoded = Json.decodeFromString(StatusResponse.serializer(), response.bodyAsText())
        assertEquals("2026-11-14", decoded.week)

        assertEquals("ok", client.get("/health").bodyAsText())
    }
}
