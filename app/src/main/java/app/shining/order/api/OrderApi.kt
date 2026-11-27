package app.shining.order.api

import app.shining.domain.network.SamadResponse
import app.shining.order.api.dto.reserve.ReserveProgramDto
import app.shining.order.api.dto.self.SelfDto
import app.shining.order.domain.model.ReserveRequestBodyData
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface OrderApi {

    @GET("rest/reservations/programs/v2")
    suspend fun getReserveProgram(
        @Query("selfId") selfId: Int,
        @Query("weekStartDate") date: String,
    ): SamadResponse<ReserveProgramDto>

    @PUT("rest/reserves/{programId}/reserve")
    suspend fun reserveFood(
        @Path("programId") programId: Int,
        @Body reservableFoodDetail: ReserveRequestBodyData,
    ): SamadResponse<Unit>

    @GET("rest/reservations/selfs")
    suspend fun getAvailableSelfs(
    ): SamadResponse<List<SelfDto>>
}