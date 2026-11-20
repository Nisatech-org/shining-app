package app.shining.testing

import app.shining.domain.api.CaptchaPayload
import app.shining.domain.api.CaptchaReply
import app.shining.domain.api.CaptchaRequest
import app.shining.domain.api.TokenApi
import app.shining.domain.api.dto.SamadTokenDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.IOException

/**
 * Samad's OAuth endpoint: [passwords] are the accounts that exist, [validRefreshTokens] the
 * refresh tokens it still accepts. Each successful call issues fresh tokens.
 */
class FakeTokenApi(
    val passwords: MutableMap<String, String> = mutableMapOf(),
    val validRefreshTokens: MutableSet<String> = mutableSetOf(),
) : TokenApi {
    var networkDown = false

    /** Like Samad since November 2026: a password login needs the answer to the last captcha. */
    var requireCaptcha = false
    val captchaAnswer = "Xjben5"
    val captchaNonces = mutableListOf<String>()
    val loginCalls = mutableListOf<String>()
    val refreshCalls = mutableListOf<String>()
    private var issued = 0

    override suspend fun login(
        username: String,
        password: String,
        token: String,
        grantType: String,
        scope: String,
        captchaText: String?,
        validation: String?,
        nonce: String?,
    ): Response<SamadTokenDto> {
        loginCalls += username
        if (networkDown) throw IOException("offline")
        if (requireCaptcha) {
            if (captchaText == null || validation == null || nonce == null) return rejected("""{"message":"Captcha params is invalid"}""")
            if (captchaText != captchaAnswer || validation != "valid-$nonce" || nonce != captchaNonces.lastOrNull()) {
                return rejected("""{"message":"Captcha is invalid"}""")
            }
        }
        return if (passwords[username] == password) issue(username) else unauthorized()
    }

    override suspend fun captcha(request: CaptchaRequest): CaptchaReply {
        if (networkDown) throw IOException("offline")
        captchaNonces += request.nonce
        return CaptchaReply(CaptchaPayload(image = "aW1hZ2U=", validation = "valid-${request.nonce}"))
    }

    override suspend fun refreshAccessToken(
        refreshToken: String,
        token: String,
        grantType: String,
    ): Response<SamadTokenDto> {
        refreshCalls += refreshToken
        if (networkDown) throw IOException("offline")
        if (!validRefreshTokens.remove(refreshToken)) return unauthorized()
        return issue(refreshToken.substringBefore(":"))
    }

    private fun issue(username: String): Response<SamadTokenDto> {
        issued++
        val refresh = "$username:refresh$issued"
        validRefreshTokens += refresh
        return Response.success(
            SamadTokenDto(
                accessToken = "$username:access$issued",
                refreshToken = refresh,
                tokenType = "bearer",
            )
        )
    }

    private fun unauthorized(): Response<SamadTokenDto> = rejected("{}")

    private fun rejected(body: String): Response<SamadTokenDto> =
        Response.error(400, body.toResponseBody("application/json".toMediaType()))
}
