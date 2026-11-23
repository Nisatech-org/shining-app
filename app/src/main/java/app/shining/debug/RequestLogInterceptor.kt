package app.shining.debug

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * One debug-log line per Samad request: method, path, status and time, e.g.
 * `GET rest/reservations/programs/v2 → 200 in 312ms`. Never headers or bodies (they hold tokens).
 * Queries are kept but pass through [Redact] like every log line.
 */
class RequestLogInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val what = "${request.method} ${request.url.encodedPath.trimStart('/')}" +
            (request.url.encodedQuery?.let { "?$it" } ?: "")
        val started = System.currentTimeMillis()
        return try {
            chain.proceed(request).also { response ->
                val line = "$what → ${response.code} in ${System.currentTimeMillis() - started}ms"
                if (response.isSuccessful) DebugLog.d(TAG, line) else DebugLog.w(TAG, line)
            }
        } catch (e: IOException) {
            DebugLog.w(TAG, "$what → ${e.javaClass.simpleName}: ${e.message} after ${System.currentTimeMillis() - started}ms")
            throw e
        }
    }

    private companion object {
        const val TAG = "http"
    }
}
