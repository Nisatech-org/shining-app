package app.shining.domain.network

import app.shining.domain.repository.TokenProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

private const val DEFAULT_ACCESS_TOKEN = "Basic c2FtYWQtbW9iaWxlOnNhbWFkLW1vYmlsZS1zZWNyZXQ="

class AuthInterceptor(
    private val tokenProvider: TokenProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        // The account this request belongs to, even if the active account changes meanwhile.
        val username = tokenProvider.activeUsername()
        val token = tokenProvider.getSamadToken(username)?.accessToken ?: DEFAULT_ACCESS_TOKEN

        val modifiedRequest = originalRequest.newBuilder()
            .addHeader("Authorization", token)
            .build()

        var response = chain.proceed(modifiedRequest)

        if (response.code == 401 && username != null) {
            // TokenProvider renews one at a time, and when another request or the app start
            // already replaced this token it hands that one back instead of spending the refresh
            // token again.
            val newToken = runBlocking {
                tokenProvider.refreshAccessToken(username, rejectedAccessToken = token)
                    .getOrNull()?.accessToken
            }
            newToken?.let {
                response.close()

                val retryRequest = originalRequest.newBuilder()
                    .addHeader("Authorization", it)
                    .build()

                response = chain.proceed(retryRequest)
            }
        }
        return response
    }
}
