package app.shining.order.domain.model

import app.shining.domain.network.SamadResponse
import app.shining.order.api.OrderApi
import app.shining.order.api.dto.reserve.ReserveProgramDto
import app.shining.order.api.dto.self.SelfDto
import app.shining.order.domain.repository.OrderFoodRepositoryImplementation
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReserveErrorTest {

    private val full = "ظرفیت رزرو این غذا تکمیل شده است"

    @Test
    fun `a full سلف مرکزی food is Samad's error, and the user is told to try again`() {
        val message = reserveErrorMessage(1, full)
        assertTrue(full in message)
        assertTrue("سلف مرکزی پر نمی‌شه" in message)
        assertTrue("دوباره رزرو کن" in message)
        assertTrue("the girls' central self too", "سلف مرکزی پر نمی‌شه" in reserveErrorMessage(2, full))
    }

    @Test
    fun `a full food anywhere else really is full`() {
        assertEquals(full, reserveErrorMessage(20, full)) // کاله
        assertEquals(full, reserveErrorMessage(24, full)) // کلانا
    }

    @Test
    fun `other refusals at سلف مرکزی are shown as they are`() {
        assertEquals("اعتبار کافی نیست", reserveErrorMessage(1, "اعتبار کافی نیست"))
        assertEquals("مهلت رزرو این غذا تمام شده است", reserveErrorMessage(1, "مهلت رزرو این غذا تمام شده است"))
    }

    @Test
    fun `capacity and limit wordings count as full, a credit ceiling doesn't`() {
        assertTrue(isCapacityMessage("ظرفیت سلف تکمیل است"))
        assertTrue(isCapacityMessage("تعداد رزرو این غذا به حداکثر رسیده است"))
        assertTrue(isCapacityMessage("سقف رزرو این برنامه پر شده است"))
        assertTrue(isCapacityMessage("Reserve capacity exceeded"))
        assertFalse(isCapacityMessage("اعتبار شما از سقف مجاز کمتر است"))
        assertFalse(isCapacityMessage("مهلت رزرو تمام شده"))
        assertFalse(isCapacityMessage("خطای ناشناخته"))
    }

    private val repository = OrderFoodRepositoryImplementation(NoApi, Json { ignoreUnknownKeys = true })

    @Test
    fun `Samad's reason is read from any HTTP error, not only 400`() {
        val body = """{"code":409,"type":"ERROR","messageFa":"$full","message":"capacity"}"""
        assertEquals(full, repository.reserveRejection(409, body))
        assertEquals(full, repository.reserveRejection(400, body))
        assertEquals("capacity", repository.reserveRejection(500, """{"code":500,"message":"capacity"}"""))
    }

    @Test
    fun `an error page instead of Samad's reply gets a plain message, not a crash`() {
        val message = repository.reserveRejection(502, "<html>Bad Gateway</html>")
        assertTrue("502" in message)
        assertTrue("502" in repository.reserveRejection(502, null))
    }

    private object NoApi : OrderApi {
        override suspend fun getReserveProgram(selfId: Int, date: String): SamadResponse<ReserveProgramDto> = error("unused")
        override suspend fun reserveFood(programId: Int, reservableFoodDetail: ReserveRequestBodyData): SamadResponse<Unit> = error("unused")
        override suspend fun getAvailableSelfs(): SamadResponse<List<SelfDto>> = error("unused")
    }
}
