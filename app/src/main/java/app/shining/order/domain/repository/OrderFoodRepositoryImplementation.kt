package app.shining.order.domain.repository

import android.util.Log
import app.shining.domain.network.SamadResponse
import app.shining.order.api.OrderApi
import app.shining.order.api.dto.reserve.ReserveProgramDto
import app.shining.order.api.dto.self.SelfDto
import app.shining.order.domain.model.ReserveRequestBodyData
import kotlinx.serialization.json.Json
import retrofit2.HttpException

class OrderFoodRepositoryImplementation(
    private val api: OrderApi,
    private val json: Json,
) : OrderFoodRepository {

    override suspend fun reserveFood(
        reserveRequestBodyData: ReserveRequestBodyData,
        programId: Int
    ): Result<String> {
        return try {
            val response = api.reserveFood(
                programId = programId,
                reservableFoodDetail = reserveRequestBodyData
            )
            if (response.isSuccessful) {
                Result.success(response.messageFa ?: "عملیات رزرو با موفقیت انجام شد")
            } else {
                Result.failure(Throwable(response.getErrorMessage()))
            }
        } catch (e: HttpException) {
            Log.e("OrderFoodRepository", "reserveFood: ${e.message}")
            Result.failure(Throwable(reserveRejection(e.code(), runCatching { e.response()?.errorBody()?.string() }.getOrNull())))
        } catch (e: Exception) {
            Log.e("OrderFoodRepository", "reserveFood: ${e.message}")
            Result.failure(Throwable("در ارتباط با سرور به مشکل خوردیم."))
        }
    }

    /**
     * Samad's own words for a refused reservation, whatever the HTTP status: its body is a
     * [SamadResponse] with the reason (credit, deadline, a full food...). A body that isn't one
     * (an HTML error page) gets a plain message instead of crashing the screen.
     */
    internal fun reserveRejection(code: Int, errorBody: String?): String {
        val samad = errorBody?.let { body ->
            runCatching { json.decodeFromString<SamadResponse<Unit>>(body) }.getOrNull()
        }
        return samad?.let { it.messageFa ?: it.message }
            ?: "سماد رزرو رو قبول نکرد (خطای $code). چند لحظه دیگه دوباره امتحان کن."
    }

    override suspend fun getReserveProgram(
        selfId: Int,
        weekStartDate: String,
    ): Result<ReserveProgramDto> {
        return try {
            val response = api.getReserveProgram(
                selfId = selfId,
                date = weekStartDate
            )
            if (response.isSuccessful) {
                Result.success(response.payload!!)
            } else {
                Result.failure(Throwable(response.getErrorMessage()))
            }
        } catch (e: Exception) {
            Log.e("OrderFoodRepository:getReservableProgram", "Error ${e.message}")
            Result.failure(Throwable("در گرفتن برنامه غذایی خطایی پیش آمده."))
        }

    }

    override suspend fun getAvailableSelfs(
    ): Result<List<SelfDto>> {
        return try {
            val response = api.getAvailableSelfs()

            return if (response.isSuccessful) {
                Result.success(response.payload!!)
            } else {
                Result.failure(Throwable(response.getErrorMessage()))
            }
        } catch (e: Exception) {
            Log.e("OrderFoodRepository:getAvailableSelfs", "Error ${e.message}")
            Result.failure(Throwable("هیچ سلفی برای شما تعریف نشده."))
        }
    }

}