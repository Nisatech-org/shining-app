package app.shining.domain.network

import retrofit2.HttpException

/**
 * Samad refused the saved session: a request got a 401 even after AuthInterceptor tried to renew
 * it. Samad ends sessions on its own (for example when the same account logs in somewhere else)
 * and wants a captcha for every login, so only logging in again helps. Not a network problem, and
 * the user shouldn't be told it is.
 */
class SessionEndedException : Exception("سماد نشستت رو بست. برای دیدن رزروهای تازه دوباره وارد شو.")

/** True if [error] is Samad's 401 for the saved session. */
fun isSessionRejected(error: Throwable): Boolean = error is HttpException && error.code() == 401
