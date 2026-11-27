package app.shining.domain.api

import app.shining.domain.api.dto.SamadTokenDto
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface TokenApi {

    @POST("oauth/token")
    suspend fun refreshAccessToken(
        @Query("refresh_token") refreshToken: String,
        @Header("Authorization") token: String = "Basic c2FtYWQtbW9iaWxlOnNhbWFkLW1vYmlsZS1zZWNyZXQ=",
        @Query("grant_type") grantType: String = "refresh_token",
    ): Response<SamadTokenDto>

    @POST("oauth/token")
    suspend fun login(
        @Query("username") username: String,
        @Query("password") password: String,
        @Header("Authorization") token: String = "Basic c2FtYWQtbW9iaWxlOnNhbWFkLW1vYmlsZS1zZWNyZXQ=",
        @Query("grant_type") grantType: String = "password",
        @Query("scope") scope: String = "read+write",
        // Samad wants a captcha for every password login since November 2026. Left out when null.
        @Query("captchaText") captchaText: String? = null,
        @Query("validation") validation: String? = null,
        @Query("nonce") nonce: String? = null,
    ): Response<SamadTokenDto>

    /** A new captcha for [CaptchaRequest.nonce]: a PNG (base64) and the token that goes back with the answer. */
    @POST("rest/captcha")
    suspend fun captcha(@Body request: CaptchaRequest): CaptchaReply

    /**
     * The user's profile, asked for only to see whether [token] (a saved access token, with its
     * type) still works: 200 if it does, 401 if not. This client has no `AuthInterceptor`, so a 401
     * here doesn't renew anything.
     *
     * (Not `DomainApi.pingServer`: that client renews on a 401, and can only check the active account.)
     */
    @GET("rest/users/me")
    suspend fun checkAccessToken(@Header("Authorization") token: String): Response<Unit>

}

@Serializable
data class CaptchaRequest(val nonce: String)

@Serializable
data class CaptchaReply(val payload: CaptchaPayload? = null)

@Serializable
data class CaptchaPayload(val image: String = "", val validation: String = "")