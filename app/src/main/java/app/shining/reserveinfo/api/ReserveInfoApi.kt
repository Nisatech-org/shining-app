package app.shining.reserveinfo.api

import app.shining.domain.network.SamadResponse
import app.shining.reserveinfo.api.dto.ForgetCodeDto
import app.shining.reserveinfo.api.dto.ReservationInformationDto
import retrofit2.http.GET
import retrofit2.http.Query

interface ReserveInfoApi {

    @GET("rest/reserves")
    suspend fun getReserveInformation(
        @Query("weekStartDate") weekStartDate: String,
    ): SamadResponse<ReservationInformationDto>

    @GET("rest/forget-card-codes/print")
    suspend fun getForgetCode(
        @Query("reserveId") reserveId: Int,
        @Query("count") count: Int = 1,
        @Query("dailySale") dailySale: Boolean = false
    ): SamadResponse<ForgetCodeDto>

}