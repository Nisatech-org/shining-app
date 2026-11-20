package app.shining.domain.repository

import app.shining.account.domain.model.AuthException
import app.shining.account.domain.repository.AccountStore
import app.shining.domain.api.TokenApi
import app.shining.domain.api.dto.SamadTokenDto
import app.shining.testing.FakeKeyValueStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class TokenProviderTest {

    /** Answers every login with whatever [reply] returns or throws. */
    private class ScriptedTokenApi(val reply: () -> Response<SamadTokenDto>) : TokenApi {
        override suspend fun login(
            username: String,
            password: String,
            token: String,
            grantType: String,
            scope: String,
            captchaText: String?,
            validation: String?,
            nonce: String?,
        ) = reply()

        override suspend fun refreshAccessToken(refreshToken: String, token: String, grantType: String) =
            reply()

        override suspend fun captcha(request: app.shining.domain.api.CaptchaRequest) =
            throw IOException("not used")
    }

    @Test
    fun `Samad's captcha refusals get their own messages`() {
        assertEquals(CAPTCHA_NEEDED, rejectionMessage("""{"message":"Captcha params is invalid"}"""))
        assertEquals(WRONG_CAPTCHA, rejectionMessage("""{"message":"Captcha is invalid"}"""))
        assertEquals(INVALID_CREDENTIALS, rejectionMessage("""{"error":"invalid_grant","error_description":"Bad credentials"}"""))
    }

    private fun provider(reply: () -> Response<SamadTokenDto>) =
        TokenProvider(ScriptedTokenApi(reply), AccountStore(FakeKeyValueStore(), Json) { 0L })

    private fun error(code: Int): Response<SamadTokenDto> =
        Response.error(code, "{\"error\":\"x\"}".toResponseBody("application/json".toMediaType()))

    private suspend fun loginError(reply: () -> Response<SamadTokenDto>): Throwable =
        provider(reply).requestLogin("401105786", "pw").exceptionOrNull()!!

    @Test
    fun `a good reply gives a bearer token`() = runTest {
        val result = provider {
            Response.success(SamadTokenDto(accessToken = "abc", refreshToken = "r", tokenType = "bearer"))
        }.requestLogin("401105786", "pw")

        assertEquals("bearer abc", result.getOrThrow().accessToken)
        assertEquals("r", result.getOrThrow().refreshToken)
    }

    @Test
    fun `400 and 401 mean the password is wrong`() = runTest {
        for (code in listOf(400, 401)) {
            val error = loginError { error(code) }
            assertTrue("HTTP $code", error is AuthException.InvalidCredentials)
            assertEquals(INVALID_CREDENTIALS, error.message)
        }
    }

    @Test
    fun `other HTTP errors are Samad's problem, not the password's`() = runTest {
        for (code in listOf(403, 429, 500, 502, 503)) {
            val error = loginError { error(code) }
            assertTrue("HTTP $code", error is AuthException.Network)
            assertEquals("HTTP $code", SERVER_ERROR, error.message)
        }
    }

    @Test
    fun `no connection asks about the internet`() = runTest {
        val error = loginError { throw UnknownHostException("setad.dining.sharif.edu") }
        assertTrue(error is AuthException.Network)
        assertEquals(NO_CONNECTION, error.message)

        assertEquals(NO_CONNECTION, loginError { throw IOException("reset") }.message)
    }

    @Test
    fun `a timeout says Samad didn't answer`() = runTest {
        val error = loginError { throw SocketTimeoutException("timeout") }
        assertTrue(error is AuthException.Network)
        assertEquals(TIMEOUT, error.message)
    }

    @Test
    fun `a reply that can't be parsed is reported as unreadable`() = runTest {
        val error = loginError { throw SerializationException("Expected string literal") }
        assertTrue(error is AuthException.Network)
        assertEquals(UNREADABLE_RESPONSE, error.message)
    }

    @Test
    fun `a success without an access token is reported as unreadable`() = runTest {
        val error = loginError { Response.success(SamadTokenDto(tokenType = "bearer")) }
        assertTrue(error is AuthException.Network)
        assertEquals(UNREADABLE_RESPONSE, error.message)
    }
}
