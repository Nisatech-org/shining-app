package app.shining.server

import app.shining.server.http.buildStatus
import app.shining.server.http.statusRoutes
import app.shining.server.push.ADMIN_TOPIC
import app.shining.server.push.FcmNotifier
import app.shining.server.push.ServiceAccount
import app.shining.server.samad.SamadClient
import app.shining.server.watch.LogNotifier
import app.shining.server.watch.MenuArchive
import app.shining.server.watch.Notifier
import app.shining.server.watch.SamadMenuSource
import app.shining.server.watch.SamadSession
import app.shining.server.watch.StateStore
import app.shining.server.watch.Watcher
import app.shining.server.discovery.TEHRAN
import app.shining.server.watch.BatchVendor
import app.shining.server.watch.MenuBatch
import app.shining.server.watch.bundleReadyAt
import app.shining.server.watch.watchedWeek
import java.time.LocalDate
import app.shining.server.http.DebugInfo
import app.shining.server.http.debugRoutes
import app.shining.server.log.Log
import app.shining.server.watch.CheckPlan
import app.shining.server.watch.nextCheckPlan
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/** Server settings, read from environment variables so no secret is ever in a file in the repo. */
data class ServerConfig(
    val username: String,
    val password: CharArray,
    val dataDir: File,
    val host: String,
    val port: Int,
    /** Samad's address; only changed to test against something that isn't the real Samad. */
    val samadBaseUrl: String,
    /** The Firebase service-account key file. Without it, alerts only go to the log. */
    val fcmKeyFile: File?,
    /** Secret for `/v1/debug` and the sign-in page. Without it, neither exists. */
    val debugToken: String? = null,
) {
    // Keeps the password and the debug token out of logs.
    override fun toString() =
        "ServerConfig(username=$username, dataDir=$dataDir, host=$host, port=$port, samadBaseUrl=$samadBaseUrl, " +
            "fcmKeyFile=$fcmKeyFile, debugToken=${if (debugToken == null) "none" else "set"})"

    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig {
            fun required(name: String) = env[name]?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Set $name.")
            return ServerConfig(
                username = toEnglishDigits(required("SAMAD_USERNAME")).trim(),
                password = toEnglishDigits(required("SAMAD_PASSWORD")).toCharArray(),
                dataDir = File(env["SHINING_DATA_DIR"]?.takeIf { it.isNotBlank() } ?: "data"),
                host = env["SHINING_HOST"]?.takeIf { it.isNotBlank() } ?: "127.0.0.1",
                port = env["SHINING_PORT"]?.toIntOrNull() ?: 8050,
                samadBaseUrl = env["SAMAD_BASE_URL"]?.takeIf { it.isNotBlank() }?.let { if (it.endsWith("/")) it else "$it/" }
                    ?: "https://setad.dining.sharif.edu/",
                fcmKeyFile = fcmKeyFileFromEnv(env),
                debugToken = env["SHINING_DEBUG_TOKEN"]?.takeIf { it.length >= 16 }
                    ?: env["SHINING_DEBUG_TOKEN"]?.takeIf { it.isNotBlank() }?.let {
                        throw IllegalArgumentException("SHINING_DEBUG_TOKEN must be at least 16 characters.")
                    },
            )
        }

        fun fcmKeyFileFromEnv(env: Map<String, String> = System.getenv()): File? =
            env["SHINING_FCM_KEY_FILE"]?.takeIf { it.isNotBlank() }?.let(::File)?.also {
                require(it.isFile) { "SHINING_FCM_KEY_FILE points to $it, which isn't a file." }
            }
    }
}

/** Firebase when a key file is set, otherwise the log. */
fun notifierFor(keyFile: File?): Notifier =
    if (keyFile == null) {
        Log.warn("push", "SHINING_FCM_KEY_FILE isn't set: alerts only go to the log")
        LogNotifier()
    } else {
        FcmNotifier(ServiceAccount.load(keyFile)).also {
            Log.info("push", "sending through Firebase", "keyFile" to keyFile)
        }
    }

/** Sends one test alert to the admin topic, to check Firebase end to end. */
fun pushTest(message: String) = runBlocking {
    val keyFile = ServerConfig.fcmKeyFileFromEnv()
        ?: throw IllegalArgumentException("Set SHINING_FCM_KEY_FILE to the Firebase key file.")
    notifierFor(keyFile).alertAdmin(message)
    println("Sent to the '$ADMIN_TOPIC' topic. Phones with admin alerts on should show it now.")
}

/**
 * Sends a sample menu alert, for a made-up vendor «تست شاینینگ» on next Saturday and Sunday with
 * the main self's foods tagged, to the admin topic only. Tapping it should open Reserve on that
 * Saturday. The made-up vendor key keeps it from hiding a real alert later.
 */
fun pushTestMenus() = runBlocking {
    val keyFile = ServerConfig.fcmKeyFileFromEnv()
        ?: throw IllegalArgumentException("Set SHINING_FCM_KEY_FILE to the Firebase key file.")
    val week = watchedWeek(LocalDate.now(TEHRAN))
    val batch = MenuBatch(
        weekStart = week,
        vendors = listOf(BatchVendor("test", "تست شاینینگ", listOf(week, week.plusDays(1)), true, selfIds = listOf(1))),
    )
    FcmNotifier(ServiceAccount.load(keyFile)).sendMenusTo(ADMIN_TOPIC, batch)
    println("Sent a sample menu alert for the week of $week to the '$ADMIN_TOPIC' topic.")
}

/**
 * Runs the watcher forever: checks Samad when [nextCheckPlan] says so, sends bundles when they're
 * ready, and serves `/v1/status` (and `/v1/debug` with the token). Wakes at least once a minute.
 * An unexpected error in one round is logged with its stack trace and the loop goes on.
 */
fun runServer(config: ServerConfig) = runBlocking {
    Log.configure(dataDir = config.dataDir)
    val startedAt = Instant.now()
    Log.info(
        "server", "starting",
        "version" to SERVER_VERSION, "config" to config, "logLevel" to Log.minLevel,
        "debugPage" to (config.debugToken != null),
    )
    val samad = SamadMenuSource(
        SamadClient(baseUrl = config.samadBaseUrl), config.username, config.password,
        session = SamadSession(File(config.dataDir, "samad-session")),
    )
    val watcher = Watcher(
        source = samad,
        store = StateStore(File(config.dataDir, "state.json")),
        notifier = notifierFor(config.fcmKeyFile),
        archive = MenuArchive(File(config.dataDir, "archive")),
        announceStart = true,
        sessionExpiry = { samad.sessionExpiresAt },
    )
    Log.info(
        "server", "state loaded",
        "week" to watcher.state.weekStart, "initialized" to watcher.state.initialized,
        "pending" to watcher.state.pending.size, "historySamples" to watcher.state.history.size,
        "failures" to watcher.state.failures,
    )

    // Set from the sign-in page too, so a sign-in is followed by a check right away.
    val plan = AtomicReference(CheckPlan(Instant.now(), "first check after start"))
    embeddedServer(CIO, port = config.port, host = config.host) {
        statusRoutes(status = { buildStatus(watcher.state, Instant.now()) })
        debugRoutes(
            config.debugToken,
            signIn = samad,
            onSignedIn = { plan.set(CheckPlan(Instant.now(), "signed in to Samad on the sign-in page")) },
        ) { DebugInfo.of(watcher.state, plan.get(), startedAt, Instant.now(), samad.signInNeeded, samad.sessionExpiresAt) }
    }.start(wait = false)
    Log.info("server", "listening", "status" to "http://${config.host}:${config.port}/v1/status", "data" to config.dataDir.absolutePath)

    while (true) {
        try {
            if (!Instant.now().isBefore(plan.get().at)) {
                watcher.check()
                val next = nextCheckPlan(Instant.now(), watcher.state, watcher.watchedVendorKeys)
                plan.set(next)
                Log.info("check", "next check planned", "at" to next.at.atZone(TEHRAN).toLocalDateTime(), "why" to next.reason)
            }
            watcher.flush()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.error("server", "unexpected error in the main loop; carrying on", error = e)
            plan.set(CheckPlan(Instant.now().plusSeconds(300), "retry after an unexpected error"))
        }
        val state = watcher.state
        val wake = listOfNotNull(plan.get().at, bundleReadyAt(state.pending, state.vendors, state.weekStart)).min()
        delay(Duration.between(Instant.now(), wake).toMillis().coerceIn(1_000, 60_000))
    }
}

/** Shown in the startup log and on the debug page. */
const val SERVER_VERSION = "1.6.0"
