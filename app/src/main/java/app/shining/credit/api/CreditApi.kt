package app.shining.credit.api

import app.shining.credit.api.dto.RedirectLoginAsTokenDto
import app.shining.domain.network.SamadResponse
import retrofit2.http.GET

interface CreditApi {

    @GET("rest/users/me/login-token")
    suspend fun getRedirectLoginAsToken(): SamadResponse<RedirectLoginAsTokenDto>


}