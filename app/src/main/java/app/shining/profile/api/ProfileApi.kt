package app.shining.profile.api

import app.shining.domain.network.SamadResponse
import app.shining.profile.api.dto.me.SamadUserDto
import app.shining.profile.api.dto.nurture.NurtureProfileDto
import retrofit2.http.GET

interface ProfileApi {

    @GET("rest/users/nurture-profiles")
    suspend fun getNurtureProfile(
    ): SamadResponse<NurtureProfileDto>

    @GET("rest/users/me")
    suspend fun getUserProfile(
    ): SamadResponse<SamadUserDto>
}