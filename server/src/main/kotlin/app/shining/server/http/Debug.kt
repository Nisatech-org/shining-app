package app.shining.server.http

import app.shining.server.SERVER_VERSION
import app.shining.server.log.Log
import app.shining.server.watch.CheckPlan
import app.shining.server.watch.InstantAsString
import app.shining.server.watch.SamadSignIn
import app.shining.server.watch.WatchState
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.Instant

/**
 * Everything the server knows, for debugging: its whole saved state (snapshot, unsent events,
 * release history, the last checks and pushes) plus when and why the next check runs. The state
 * holds no personal data or secrets, but it's still only shown with the debug token.
 */
@Serializable
data class DebugInfo(
    val serverVersion: String,
    @Serializable(with = InstantAsString::class) val startedAt: Instant,
    @Serializable(with = InstantAsString::class) val now: Instant,
    @Serializable(with = InstantAsString::class) val nextCheckAt: Instant,
    val nextCheckReason: String,
    /** True while the server waits for someone to sign in to Samad on the sign-in page. */
    val samadSignInNeeded: Boolean = false,
    /** When the saved Samad session runs out, as its token says (an ISO instant). */
    val samadSessionUntil: String? = null,
    val state: WatchState,
) {
    companion object {
        fun of(
            state: WatchState,
            plan: CheckPlan,
            startedAt: Instant,
            now: Instant,
            signInNeeded: Boolean = false,
            sessionUntil: Instant? = null,
        ) = DebugInfo(SERVER_VERSION, startedAt, now, plan.at, plan.reason, signInNeeded, sessionUntil?.toString(), state)
    }
}

/**
 * `GET /v1/debug` with the token in an `X-Debug-Token` header (or `?token=`, handy from a phone's
 * browser). Without a configured token, or with a wrong one, it answers 404 as if it didn't exist.
 *
 * With [signIn], `GET /v1/debug?signin` is the Samad sign-in page and `POST /v1/debug` (token in
 * the header only) its actions; see SignIn.kt. [onSignedIn] runs after a sign-in works.
 */
fun Application.debugRoutes(
    token: String?,
    json: Json = Json { prettyPrint = true },
    signIn: SamadSignIn? = null,
    onSignedIn: () -> Unit = {},
    info: () -> DebugInfo,
) {
    routing {
        get("/v1/debug") {
            if (token != null && signIn != null && "signin" in call.request.queryParameters) {
                return@get call.respondSignInPage()
            }
            val given = call.request.headers["X-Debug-Token"] ?: call.request.queryParameters["token"]
            if (token == null || given == null || !sameSecret(token, given)) {
                if (given != null) Log.warn("http", "debug page asked with a wrong token")
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            Log.info("http", "debug page served")
            call.response.header("Cache-Control", "no-store")
            call.respondText(json.encodeToString(DebugInfo.serializer(), info()), ContentType.Application.Json)
        }
        post("/v1/debug") {
            val given = call.request.headers["X-Debug-Token"]
            if (token == null || signIn == null || given == null || !sameSecret(token, given)) {
                if (given != null) Log.warn("http", "sign-in action with a wrong token")
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            val body = call.receiveText()
            if (body.length > 1024) return@post call.respond(HttpStatusCode.PayloadTooLarge)
            call.handleSignIn(signIn, body, Json, onSignedIn)
        }
    }
}

/** Compares in constant time, so response timing doesn't leak how much of the token was right. */
private fun sameSecret(expected: String, given: String): Boolean =
    MessageDigest.isEqual(expected.toByteArray(), given.toByteArray())
