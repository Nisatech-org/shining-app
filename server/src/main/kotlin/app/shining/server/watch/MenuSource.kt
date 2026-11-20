package app.shining.server.watch

import app.shining.server.discovery.programEntries
import app.shining.server.discovery.samadWeekParam
import app.shining.server.discovery.string
import app.shining.server.log.Log
import app.shining.server.samad.CaptchaAnswer
import app.shining.server.samad.SamadAuthException
import app.shining.server.samad.SamadCaptcha
import app.shining.server.samad.SamadClient
import app.shining.server.samad.SamadHttpException
import app.shining.server.samad.SignInNeededException
import app.shining.server.samad.samadPayload
import app.shining.server.toEnglishDigits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.util.Base64
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

data class SelfInfo(val id: Int, val name: String)

/** One cafeteria's menu for a week, as one check saw it. */
data class SelfProgram(
    /** Days with at least one food. */
    val foodDays: Set<LocalDate>,
    /**
     * Days with at least one food that can be reserved now (Samad's `reserveRuleViolated` is false,
     * or missing). A menu can be visible before reservations open; alerts follow these days.
     */
    val openDays: Set<LocalDate> = foodDays,
    /** Foods on the menu, all days together. */
    val foods: Int = 0,
    /** The menu as Samad sent it, without the account's own reservations. Kept in the archive. */
    val raw: JsonElement? = null,
    /** What identifies this menu (days, foods, prices, open or not), to archive it only when it changes. */
    val signature: String = "",
)

/** Where menus come from: Samad in production, a fake in tests. */
interface MenuSource {
    suspend fun selfs(): List<SelfInfo>

    /** Days of the week starting [weekStart] that have at least one food at [selfId]. */
    suspend fun daysWithFood(selfId: Int, weekStart: LocalDate): Set<LocalDate>

    /** The whole menu of [selfId] for the week starting [weekStart]. */
    suspend fun program(selfId: Int, weekStart: LocalDate): SelfProgram = SelfProgram(daysWithFood(selfId, weekStart))
}

/** Where the admin signs the server in to Samad: a page on the server itself (see http/SignIn.kt). */
const val SIGN_IN_PAGE = "https://shining-api.nisatech.org/v1/debug?signin"

/** How often a password login without a captcha is tried, in case Samad stops asking for one. */
val PASSWORD_RETRY: Duration = Duration.ofHours(6)

/** How long a captcha on the sign-in page can be answered. */
val CAPTCHA_LIFETIME: Duration = Duration.ofMinutes(10)

/** What a sign-in from the page came to, in words for the page. */
@Serializable
data class SignInResult(val ok: Boolean, val message: String)

/** What the sign-in page needs: [SamadMenuSource] in production, a fake in tests. */
interface SamadSignIn {
    /** True while the server has no working session and waits for someone to sign in. */
    val signInNeeded: Boolean

    suspend fun newCaptcha(): SamadCaptcha

    suspend fun signIn(nonce: String, text: String): SignInResult
}

/**
 * Reads menus from Samad with one account. Signs in on first use; when Samad says the token is
 * no longer valid, renews it with the refresh token, or signs in again, and retries once.
 *
 * Since November 2026 Samad asks for a captcha on every password login, which only a person can
 * solve. So the server keeps a [session]: the access token (Samad's last about 180 days) and the
 * refresh token, saved so restarts and deploys keep them. Samad refused the server's renewals in
 * November 2026, so the access token is what keeps it signed in. When nothing works, [signInNeeded] turns true and
 * the admin signs in on the sign-in page: the server fetches the captcha itself ([newCaptcha]),
 * the admin types what it says, and the server logs in from its own address ([signIn]). A session started
 * on another computer was refused the first time the server used it (2026-11-08).
 *
 * Without a working session, a password login without a captcha is only tried every
 * [PASSWORD_RETRY], and a refused session isn't retried: each would be one more failed login on
 * Samad.
 */
class SamadMenuSource(
    private val client: SamadClient,
    private val username: String,
    private val password: CharArray,
    private val session: SamadSession? = null,
    private val clock: () -> Instant = Instant::now,
) : MenuSource, SamadSignIn {

    /** Menu reads, sign-ins and captchas one at a time: they share one client and its tokens. */
    private val lock = Mutex()

    /** A saved session Samad refused; not tried again until the file holds another one. */
    @Volatile
    private var refusedToken: String? = null

    @Volatile
    private var lastPasswordTry: Instant = Instant.EPOCH

    /** The captcha on the sign-in page, waiting for its answer. Only the newest one counts. */
    @Volatile
    private var shown: Pair<SamadCaptcha, Instant>? = null

    @Volatile
    override var signInNeeded: Boolean = false
        private set

    /** A saved access token Samad refused; not used again. */
    @Volatile
    private var deadAuthorization: String? = null

    /**
     * When the session runs out: the saved access token's end (Samad's last about 180 days, and
     * renewals are refused), or else the refresh token's own end. Null if neither is known.
     */
    val sessionExpiresAt: Instant?
        get() = session?.loadAccess()?.takeIf { it.authorization != deadAuthorization }?.expiresAt
            ?: session?.load()?.let(::jwtExpiry)

    init {
        if (session != null) {
            client.onRefreshToken = session::save
            client.onAuthorization = session::saveAccess
        }
    }

    /** A new captcha for the sign-in page. The server fetches it itself, so the login happens from here. */
    override suspend fun newCaptcha(): SamadCaptcha = lock.withLock {
        withContext(Dispatchers.IO) { client.captcha() }.also {
            shown = it to clock()
            Log.info("source", "captcha fetched for the sign-in page")
        }
    }

    /**
     * Signs in with [text], what the captcha [newCaptcha] gave out (by its [nonce]) says. The new
     * session is saved and renewed like any other. Each captcha is good for one try.
     */
    override suspend fun signIn(nonce: String, text: String): SignInResult = lock.withLock {
        val answer = toEnglishDigits(text).trim()
        val captcha = shown?.takeIf { (captcha, at) ->
            captcha.nonce == nonce && Duration.between(at, clock()) <= CAPTCHA_LIFETIME
        }?.first
        when {
            answer.isEmpty() -> SignInResult(false, "کد توی تصویر رو بنویس.")
            captcha == null -> SignInResult(false, "این تصویر دیگه اعتبار نداره. یه تصویر تازه بگیر.")
            else -> {
                shown = null
                try {
                    withContext(Dispatchers.IO) {
                        client.login(username, password, CaptchaAnswer(nonce, captcha.validation, answer))
                    }
                    signInNeeded = false
                    refusedToken = null
                    deadAuthorization = null
                    Log.info("source", "signed in to Samad from the sign-in page", "sessionUntil" to sessionExpiresAt)
                    SignInResult(true, "سرور وارد سماد شد.")
                } catch (e: SamadAuthException) {
                    Log.warn("source", "sign-in from the page refused", "reason" to e.message)
                    val wrongCode = "captcha" in e.message.orEmpty().lowercase() || "کپچا" in e.message.orEmpty()
                    SignInResult(false, if (wrongCode) "کد درست نبود. یه بار دیگه." else "سماد قبول نکرد: ${e.message}")
                }
            }
        }
    }

    override suspend fun selfs(): List<SelfInfo> = authorized {
        val list = client.get("rest/reservations/selfs").samadPayload() as? JsonArray
        list.orEmpty().filterIsInstance<JsonObject>().mapNotNull { self ->
            val id = self.string("id")?.toIntOrNull() ?: return@mapNotNull null
            SelfInfo(id, self.string("name").orEmpty())
        }
    }

    override suspend fun daysWithFood(selfId: Int, weekStart: LocalDate): Set<LocalDate> =
        program(selfId, weekStart).foodDays

    override suspend fun program(selfId: Int, weekStart: LocalDate): SelfProgram = authorized {
        val payload = try {
            client.get(
                "rest/reservations/programs/v2",
                mapOf("selfId" to selfId.toString(), "weekStartDate" to samadWeekParam(weekStart)),
            ).samadPayload()
        } catch (e: SamadHttpException) {
            // Before a menu is published Samad answers HTTP 400 "self has no program" instead of
            // an empty week. That's the normal state before a release, not a failed check.
            if (!isNoProgram(e)) throw e
            null
        }
        programFrom(payload)
    }

    private suspend fun <T> authorized(block: () -> T): T = lock.withLock {
        withContext(Dispatchers.IO) {
            if (!client.isLoggedIn) resumeSession()
            try {
                block()
            } catch (e: SignInNeededException) {
                throw e
            } catch (e: SamadAuthException) {
                Log.info("source", "Samad says the session expired; renewing and retrying once", "reason" to e.message)
                deadAuthorization = client.currentAuthorization
                if (!client.refresh()) resumeSession()
                block()
            }
        }
    }

    /**
     * Signs in without a person: with the saved access token while it lasts, else by renewing the
     * saved session, or (rarely) with the password alone.
     */
    private fun resumeSession() {
        val access = session?.loadAccess()
        if (access != null && access.authorization != deadAuthorization && access.expiresAt?.isAfter(clock()) != false) {
            client.useSaved(access.authorization, session?.load())
            Log.info("source", "using the saved Samad access token", "until" to access.expiresAt)
            signInNeeded = false
            return
        }
        val saved = session?.load()
        if (saved != null && saved != refusedToken) {
            if (client.resume(saved)) {
                Log.info("source", "signed in from the saved Samad session", "sessionUntil" to sessionExpiresAt)
                signInNeeded = false
                return
            }
            refusedToken = saved
            Log.warn("source", "the saved Samad session was refused; someone has to sign in", "page" to SIGN_IN_PAGE)
        }
        if (session == null) return client.login(username, password)

        val now = clock()
        var reason = "no working session"
        if (Duration.between(lastPasswordTry, now) >= PASSWORD_RETRY) {
            lastPasswordTry = now
            try {
                client.login(username, password)
                signInNeeded = false
                return
            } catch (e: SamadAuthException) {
                reason = e.message ?: reason
            }
        }
        if (!signInNeeded) Log.warn("source", "waiting for someone to sign in to Samad", "page" to SIGN_IN_PAGE, "reason" to reason)
        signInNeeded = true
        throw SignInNeededException("Samad needs a person to sign in ($reason)")
    }
}

/**
 * The server's Samad session: one refresh token in one file (`data/samad-session`, readable only by
 * the service and the deploy user). Written after a sign-in on the sign-in page, and replaced with
 * each new token Samad hands out. The token is never logged.
 */
class SamadSession(private val file: File) {
    /** The access token and when it runs out, next to the refresh token (`samad-session-access`). */
    private val accessFile = File(file.absoluteFile.parentFile, "${file.name}-access")

    fun load(): String? = runCatching { file.takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null } }.getOrNull()

    fun save(token: String) {
        if (token == load()) return
        if (write(file, token)) Log.info("source", "Samad session saved")
    }

    /** The saved access token (the Authorization header value) and when it runs out, if any. */
    fun loadAccess(): SavedAccess? = runCatching {
        val lines = accessFile.takeIf { it.isFile }?.readLines()?.map { it.trim() } ?: return null
        val authorization = lines.getOrNull(0)?.ifEmpty { null } ?: return null
        SavedAccess(authorization, lines.getOrNull(1)?.let { runCatching { Instant.parse(it) }.getOrNull() })
    }.getOrNull()

    /**
     * Saves the access token with its end. Samad's last about 180 days while it refuses the
     * server's renewals, so this is what keeps the server signed in through restarts and deploys.
     */
    fun saveAccess(authorization: String, expiresAt: Instant?) {
        if (write(accessFile, listOfNotNull(authorization, expiresAt?.toString()).joinToString("\n"))) {
            Log.info("source", "Samad access token saved", "until" to expiresAt)
        }
    }

    private fun write(target: File, text: String): Boolean = try {
        target.absoluteFile.parentFile.mkdirs()
        val temp = File(target.absoluteFile.parentFile, "${target.name}.tmp")
        temp.writeText(text)
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        true
    } catch (e: Exception) {
        // Losing it only costs a new sign-in after the next restart.
        Log.warn("source", "couldn't save the Samad session", "file" to target.name, error = e)
        false
    }
}

/** An access token saved by [SamadSession.saveAccess]. */
data class SavedAccess(val authorization: String, val expiresAt: Instant?)

/**
 * When a JWT says it runs out (its `exp`), or null if [token] isn't one. Samad's refresh tokens
 * are JWTs; the one saved on 2026-11-08 was good for about a year. Only read to warn ahead of
 * time: the signature isn't checked, and nothing from the token is logged but this date.
 */
fun jwtExpiry(token: String): Instant? = runCatching {
    val payload = token.trim().split('.').takeIf { it.size == 3 }?.get(1) ?: return null
    val claims = Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)).jsonObject
    claims["exp"]?.jsonPrimitive?.longOrNull?.let(Instant::ofEpochSecond)
}.getOrNull()

/** Samad's way of saying a cafeteria has no menu for that week yet (code 10108005). */
internal fun isNoProgram(e: SamadHttpException): Boolean =
    e.message.orEmpty().let { "SELF_HAS_NO_PROGRAM" in it || "self has no program" in it || "10108005" in it }

/** Reads a `programs/v2` payload. The account's own reservations and user id are left out of [SelfProgram.raw]. */
fun programFrom(payload: JsonElement?): SelfProgram {
    val entries = programEntries(payload)
    fun day(entry: JsonObject) = entry.string("date")?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun closed(entry: JsonObject) = (entry["reserveRuleViolated"] as? JsonPrimitive)?.booleanOrNull == true
    val signature = entries
        .map { e ->
            listOf("date", "programId", "foodId", "foodName", "foodTypeId", "mealTypeId", "price", "reserveRuleViolated")
                .joinToString("|") { e.string(it).orEmpty() }
        }
        .sorted()
        .joinToString("\n")
    return SelfProgram(
        foodDays = entries.mapNotNull(::day).toSet(),
        openDays = entries.filterNot(::closed).mapNotNull(::day).toSet(),
        foods = entries.size,
        raw = (payload as? JsonObject)?.let { JsonObject(it - setOf("userWeekReserves", "userId")) } ?: payload,
        signature = signature,
    )
}
