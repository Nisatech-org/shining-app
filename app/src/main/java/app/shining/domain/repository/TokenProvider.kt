package app.shining.domain.repository

import app.shining.debug.DebugLog
import app.shining.account.domain.model.AuthException
import app.shining.account.domain.repository.AccountStore
import app.shining.domain.api.CaptchaRequest
import app.shining.domain.api.TokenApi
import app.shining.domain.api.dto.SamadTokenDto
import app.shining.domain.api.dto.toSamadToken
import app.shining.domain.model.SamadToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.UUID

/** A captcha to show: a base64 PNG, and what goes back to Samad with the user's answer. */
data class Captcha(val nonce: String, val validation: String, val imageBase64: String)

/** The user's answer to a [Captcha]. */
data class CaptchaAnswer(val nonce: String, val validation: String, val text: String)

internal const val NO_CONNECTION = "به سرور وصل نمی‌تونیم بشیم. اینترنتت اوکیه؟"
internal const val TIMEOUT = "سماد جواب نداد. چند لحظه دیگه دوباره امتحان کن."
internal const val SERVER_ERROR = "سرور سماد الان مشکل داره. چند دقیقه دیگه دوباره امتحان کن."
internal const val UNREADABLE_RESPONSE = "جواب سماد رو نتونستیم بخونیم. بعداً دوباره امتحان کن."
internal const val INVALID_CREDENTIALS = "نام‌ کاربری / رمزعبور نادرست است"
internal const val WRONG_CAPTCHA = "کد امنیتی درست نبود. کد تازه رو وارد کن."
internal const val CAPTCHA_NEEDED = "سماد برای ورود کد امنیتی می‌خواد. دوباره وارد شو."
internal const val CAPTCHA_FAILED = "کد امنیتی از سماد نیومد. دکمه‌ی تازه کردن رو بزن."
private const val BROKEN_TOKEN = "توکن زایید!"
private const val TAG = "TokenProvider"

/**
 * Talks to Samad's OAuth endpoint and keeps each account's tokens in [AccountStore].
 */
class TokenProvider(
    private val tokenApi: TokenApi,
    private val accountStore: AccountStore,
) {
    private val refreshLock = Mutex()

    fun activeUsername(): String? = accountStore.active?.username

    fun getSamadToken(username: String? = activeUsername()): SamadToken? =
        username?.let { accountStore.find(it)?.token }

    /**
     * Refreshes [username]'s access token and saves it to that account, even if another account
     * became active while the request was running.
     *
     * Samad gives a new refresh token with each renewal and refuses the old one, so renewals run
     * one at a time, for the app start and for requests that got a 401 alike. A caller whose
     * [rejectedAccessToken] was already replaced by another renewal gets the saved token without
     * spending the refresh token again.
     */
    suspend fun refreshAccessToken(
        username: String,
        rejectedAccessToken: String? = null,
    ): Result<SamadToken> = refreshLock.withLock {
        val account = accountStore.find(username)
        val saved = account?.token
        if (saved != null && rejectedAccessToken != null && saved.accessToken != rejectedAccessToken) {
            return@withLock Result.success(saved)
        }
        val refreshToken = account?.refreshToken?.takeIf { it.isNotEmpty() }
            ?: return@withLock Result.failure(AuthException.InvalidCredentials(BROKEN_TOKEN))
        requestRefresh(refreshToken).onSuccess { token ->
            accountStore.update(username) {
                it.copy(accessToken = token.accessToken, refreshToken = token.refreshToken)
            }
        }
    }

    /**
     * Asks Samad whether a saved access token still works, without renewing it. Its access tokens
     * last about 180 days, and since November 2026 it refuses renewals and wants a captcha for
     * every password login, so a working one must be kept. Fails with
     * [AuthException.InvalidCredentials] only when Samad answers 401; no reply or any other answer
     * is [AuthException.Network], since it says nothing about the token.
     */
    suspend fun checkAccessToken(accessToken: String): Result<Unit> {
        val response = send("token check") { tokenApi.checkAccessToken(accessToken) }
            .getOrElse { return Result.failure(it) }
        response.errorBody()?.close()
        return when {
            response.isSuccessful -> {
                DebugLog.i(TAG, "saved access token works, not renewing it")
                Result.success(Unit)
            }
            response.code() == 401 -> {
                DebugLog.i(TAG, "saved access token was rejected: HTTP 401")
                Result.failure(AuthException.InvalidCredentials(BROKEN_TOKEN))
            }
            else -> {
                DebugLog.e(TAG, "token check failed: HTTP ${response.code()}")
                Result.failure(AuthException.Network(SERVER_ERROR))
            }
        }
    }

    /** Exchanges a refresh token for new tokens without saving them. */
    suspend fun requestRefresh(refreshToken: String): Result<SamadToken> = request {
        tokenApi.refreshAccessToken(refreshToken = refreshToken)
    }

    /**
     * Logs in with a username and password without saving anything. Samad also wants a solved
     * [captcha]; without one (a silent re-login with the saved password) it's refused, and the
     * account is asked to log in again.
     */
    suspend fun requestLogin(username: String, password: String, captcha: CaptchaAnswer? = null): Result<SamadToken> = request {
        tokenApi.login(
            username = username,
            password = password,
            captchaText = captcha?.text,
            validation = captcha?.validation,
            nonce = captcha?.nonce,
        )
    }

    /** A new captcha for the login screen. Each one can be used for one login attempt. */
    suspend fun fetchCaptcha(): Result<Captcha> {
        val nonce = UUID.randomUUID().toString()
        return try {
            val payload = tokenApi.captcha(CaptchaRequest(nonce)).payload
            if (payload == null || payload.image.isBlank() || payload.validation.isBlank()) {
                DebugLog.e(TAG, "captcha reply was empty")
                Result.failure(AuthException.Network(CAPTCHA_FAILED))
            } else {
                Result.success(Captcha(nonce, payload.validation, payload.image))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.e(TAG, "captcha request failed: ${e.javaClass.simpleName}: ${e.message}")
            Result.failure(AuthException.Network(CAPTCHA_FAILED))
        }
    }

    /**
     * Only a 400 or 401 from Samad means the password or refresh token was rejected. Everything
     * else (no connection, a timeout, a server error, a reply we can't read) says nothing about
     * the account, and each gets its own message so the user isn't told to check their internet
     * when the problem is on Samad's side.
     */
    private suspend fun request(
        call: suspend () -> Response<SamadTokenDto>,
    ): Result<SamadToken> {
        val response = send("token request", call).getOrElse { return Result.failure(it) }
        if (!response.isSuccessful) {
            val code = response.code()
            val body = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
            DebugLog.e(TAG, "token request rejected: HTTP $code ${body.take(300)}")
            return Result.failure(
                when (code) {
                    400, 401 -> AuthException.InvalidCredentials(rejectionMessage(body))
                    else -> AuthException.Network(SERVER_ERROR)
                }
            )
        }
        val token = response.body()
            ?.takeIf { it.accessToken.isNotBlank() }
            ?.toSamadToken()
        if (token == null) {
            DebugLog.e(TAG, "token reply had no access token: HTTP ${response.code()}")
            return Result.failure(AuthException.Network(UNREADABLE_RESPONSE))
        }
        return Result.success(token)
    }

    /** Runs [call]. Getting no reply at all is an [AuthException.Network] with its own message. */
    private suspend fun <T> send(what: String, call: suspend () -> Response<T>): Result<Response<T>> =
        try {
            Result.success(call())
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedIOException) {
            DebugLog.e(TAG, "$what timed out: ${e.message}")
            Result.failure(AuthException.Network(TIMEOUT))
        } catch (e: IOException) {
            DebugLog.e(TAG, "$what failed: ${e.javaClass.simpleName}: ${e.message}")
            Result.failure(AuthException.Network(NO_CONNECTION))
        } catch (e: SerializationException) {
            // The message can quote the reply, which holds tokens, so only the type is logged.
            DebugLog.e(TAG, "$what reply could not be parsed: ${e.javaClass.simpleName}")
            Result.failure(AuthException.Network(UNREADABLE_RESPONSE))
        } catch (e: Exception) {
            DebugLog.e(TAG, "$what failed: ${e.javaClass.simpleName}")
            Result.failure(AuthException.Network(UNREADABLE_RESPONSE))
        }
}

/**
 * What to tell the user when Samad refuses a login. Its replies say "Captcha params is invalid"
 * when no captcha was sent and "Captcha is invalid" for a wrong answer; anything else is the
 * username or password.
 */
internal fun rejectionMessage(body: String): String = when {
    "captcha params" in body.lowercase() -> CAPTCHA_NEEDED
    "captcha" in body.lowercase() -> WRONG_CAPTCHA
    else -> INVALID_CREDENTIALS
}
