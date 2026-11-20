package app.shining.server

import app.shining.server.log.Log
import app.shining.server.push.FcmNotifier
import app.shining.server.push.HttpPost
import app.shining.server.push.ServiceAccount
import app.shining.server.samad.SamadClient
import app.shining.server.testing.FakeSamad
import app.shining.server.watch.MenuBatch
import app.shining.server.watch.SamadMenuSource
import app.shining.server.watch.StateStore
import app.shining.server.watch.Watcher
import app.shining.server.watch.nov
import app.shining.server.watch.tehran
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.KeyPairGenerator
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The whole server path with only the outside world faked: a fake Samad over real HTTP, the real
 * client, menu source, watcher and state file, and the real Firebase sender talking to a fake
 * Google. One simulated week, checking what goes out and what gets logged.
 */
class EndToEndTest {

    private val samad = FakeSamad()
    private val logLines = mutableListOf<String>()
    private var now: Instant = tehran(9, 0)
    private val dataDir = createTempDirectory("shining-e2e").toFile()

    /** Records what the server sends to Firebase, and answers like Google. */
    private class FakeGoogle : HttpPost {
        val sent = mutableListOf<Pair<String, Map<String, String>>>()
        override fun post(url: String, headers: Map<String, String>, body: String): Pair<Int, String> {
            if ("oauth2" in url) return 200 to """{"access_token":"google-token-xyz123456","expires_in":3600}"""
            val message = Json.parseToJsonElement(body).jsonObject.getValue("message").jsonObject
            sent += message.getValue("topic").jsonPrimitive.content to
                message.getValue("data").jsonObject.mapValues { it.value.jsonPrimitive.content }
            return 200 to """{"name":"projects/test/messages/${sent.size}"}"""
        }
    }

    private val google = FakeGoogle()

    private fun watcher(): Watcher {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private
        val pem = "-----BEGIN PRIVATE KEY-----\n${Base64.getMimeEncoder().encodeToString(key.encoded)}\n-----END PRIVATE KEY-----\n"
        return Watcher(
            source = SamadMenuSource(SamadClient(baseUrl = samad.baseUrl), "98123456", "test-password-1".toCharArray()),
            store = StateStore(File(dataDir, "state.json")),
            notifier = FcmNotifier(ServiceAccount("test", "sender@test.iam.gserviceaccount.com", pem), google, clock = { now }),
            clock = { now },
            requestGap = Duration.ZERO,
        )
    }

    @BeforeTest
    fun captureLogs() {
        Log.reset()
        Log.minLevel = Log.Level.DEBUG
        Log.clock = { now }
        Log.sinks = listOf { logLines += it }
    }

    @AfterTest
    fun tearDown() {
        Log.reset()
        samad.close()
    }

    @Test
    fun `a week with a baseline, a release, one push, then a Samad outage and recovery`() = runTest {
        val fullWeek = (14..18).map(::nov).toSet()
        samad.days[1] = fullWeek
        val watcher = watcher()

        // Monday 09:00: the first check only records the main self's week.
        watcher.check()
        assertTrue(watcher.state.pending.isEmpty())

        // Tuesday: Kalana publishes its whole week between two checks; nothing more to wait for, so it goes out at the next flush.
        now = tehran(10, 13, 55)
        watcher.check()
        samad.days[24] = fullWeek
        now = tehran(10, 14, 0)
        watcher.check()
        now = tehran(10, 14, 10)
        watcher.flush()

        val (topic, data) = google.sent.single()
        assertEquals("menus", topic)
        val batch = Json.decodeFromString(MenuBatch.serializer(), data.getValue("batch"))
        assertEquals(nov(14), batch.weekStart)
        assertEquals(listOf("kalana"), batch.vendors.map { it.key })
        assertEquals(listOf(24), batch.vendors.single().selfIds)
        assertEquals(fullWeek.sorted(), batch.vendors.single().newDays)
        assertEquals(1, watcher.state.history.size, "a release seen 5 minutes after the last check is learned from")

        // Samad goes down: three failures alert the admin topic once.
        samad.failWith = 502
        repeat(3) {
            now += Duration.ofMinutes(5)
            watcher.check()
        }
        assertEquals(listOf("menus", "admin"), google.sent.map { it.first })

        // Back up: one more admin message says so.
        samad.failWith = null
        now += Duration.ofMinutes(5)
        watcher.check()
        assertEquals(listOf("menus", "admin", "admin"), google.sent.map { it.first })

        // The debug trail: every check and the push are recorded.
        assertEquals(7, watcher.state.recentChecks.size)
        assertEquals(3, watcher.state.recentChecks.count { !it.ok })
        assertTrue(watcher.state.recentChecks.first { it.events.isNotEmpty() }.events.single().startsWith("kalana appeared"))
        assertTrue(watcher.state.recentPushes.single().ok)

        // The log tells the story, and never holds a secret.
        val log = logLines.joinToString("\n")
        listOf("first check", "vendor appeared", "check done", "sending menu bundle", "check failed", "alerting admin", "work again")
            .forEach { assertTrue(it in log, "log should mention: $it") }
        listOf("test-password-1", samad.lastAccessToken, "fake-refresh", "google-token-xyz123456")
            .forEach { assertFalse(it in log, "log leaked: $it") }
    }

    @Test
    fun `after a restart the server neither forgets nor repeats`() = runTest {
        samad.days[1] = (14..18).map(::nov).toSet()
        watcher().check()
        now = tehran(10, 14)
        samad.days[24] = (14..18).map(::nov).toSet()
        watcher().check()

        // A fresh watcher (a restart) reads the saved state: the event is still pending, not lost.
        now = tehran(10, 14, 10)
        val restarted = watcher()
        assertEquals(1, restarted.state.pending.size)
        restarted.flush()
        restarted.check()
        restarted.flush()
        assertEquals(1, google.sent.size, "sent exactly once")
    }
}
