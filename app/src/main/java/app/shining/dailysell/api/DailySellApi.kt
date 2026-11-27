package app.shining.dailysell.api

import app.shining.dailysell.api.dto.DailySellProgramDto
import app.shining.dailysell.api.dto.IdHrefDto
import app.shining.dailysell.api.dto.UserDailySalesInfoDto
import app.shining.domain.network.SamadResponse
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

interface DailySellApi {

    @GET("/rest/daily-sell-programs")
    suspend fun getDailySalesProgram(): SamadResponse<DailySellProgramDto>

    @GET("/rest/users/me/reserve-daily-sales")
    suspend fun getUserDailySalesInfo(): SamadResponse<List<UserDailySalesInfoDto>>

    @PUT("/rest/daily-sell-programs/{reserveId}/reserve-daily-sell")
    suspend fun orderDailySale(
        @Path("reserveId") reserveId: Int
    ): SamadResponse<IdHrefDto>

}