package app.shining.order.domain.repository

import app.shining.order.api.dto.reserve.ReserveProgramDto
import app.shining.order.api.dto.self.SelfDto
import app.shining.order.domain.model.ReserveRequestBodyData

interface OrderFoodRepository {

    suspend fun reserveFood(
        reserveRequestBodyData: ReserveRequestBodyData,
        programId: Int,
    ): Result<String>

    suspend fun getReserveProgram(
        selfId: Int,
        weekStartDate: String,
    ): Result<ReserveProgramDto>

    suspend fun getAvailableSelfs(
    ): Result<List<SelfDto>>
}