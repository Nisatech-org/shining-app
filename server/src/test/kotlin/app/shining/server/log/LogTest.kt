package app.shining.server.log

import java.io.File
import java.io.IOException
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogTest {

    private val lines = mutableListOf<String>()

    @BeforeTest
    fun capture() {
        Log.reset()
        Log.clock = { Instant.parse("2026-11-10T10:33:12.345Z") }
        Log.sinks = listOf { lines += it }
    }

    @AfterTest
    fun restore() = Log.reset()

    @Test
    fun `a line has Tehran time, level, tag, message and details`() {
        Log.info("check", "check done", "week" to "2026-11-14", "ms" to 2310, "why" to "has spaces")
        assertEquals(
            "2026-11-10T14:03:12.345+03:30 INFO  check   check done | week=2026-11-14 ms=2310 why=\"has spaces\"",
            lines.single(),
        )
    }

    @Test
    fun `lines below the level are dropped`() {
        Log.debug("samad", "GET rest/x")
        Log.minLevel = Log.Level.DEBUG
        Log.debug("samad", "GET rest/y")
        assertEquals(1, lines.size)
        assertTrue("GET rest/y" in lines.single())
    }

    @Test
    fun `the level comes from SHINING_LOG_LEVEL`() {
        Log.configure(mapOf("SHINING_LOG_LEVEL" to "debug"))
        assertEquals(Log.Level.DEBUG, Log.minLevel)
        Log.configure(mapOf("SHINING_LOG_LEVEL" to "nonsense"))
        assertEquals(Log.Level.INFO, Log.minLevel)
    }

    @Test
    fun `an error is printed with its stack trace`() {
        Log.warn("check", "check failed", error = IOException("connect timed out"))
        val text = lines.single()
        assertTrue("java.io.IOException: connect timed out" in text)
        assertTrue("\n    \tat " in text, "stack frames are indented under the line")
    }

    @Test
    fun `secrets never reach the log`() {
        Log.info("x", "Authorization: Bearer abcdefghij1234567890")
        Log.info("x", "basic", "header" to "Basic c2FtYWQtbW9iaWxlOnNhbWFkLW1vYmlsZS1zZWNyZXQ=")
        Log.info("x", "url", "url" to "oauth/token?username=98123456&password=hunter2secret&grant_type=password")
        Log.info("x", "reply", "body" to """{"access_token":"tok123456789","refresh_token":"ref123456789","token_type":"bearer"}""")
        Log.info("x", "key", "pem" to "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBg\n-----END PRIVATE KEY-----")
        Log.info("x", "jwt", "assertion" to "eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJzZW5kZXIifQ.c2lnbmF0dXJlc2lnbmF0dXJl")

        val all = lines.joinToString("\n")
        listOf("abcdefghij1234567890", "c2FtYWQtbW9iaWxl", "hunter2secret", "tok123456789", "ref123456789", "MIIEvQIBADANBg", "eyJpc3MiOiJzZW5kZXIifQ")
            .forEach { secret -> assertFalse(secret in all, "leaked: $secret") }
        assertTrue("username=98123456" in all, "non-secret parts stay readable")
    }

    @Test
    fun `log files are per day and old ones are deleted`() {
        val dir = File(createTempDirectory("shining-logs").toFile(), "logs")
        dir.mkdirs()
        File(dir, "shining-2026-10-01.log").writeText("old\n")
        File(dir, "shining-2026-11-01.log").writeText("recent\n")
        File(dir, "other.txt").writeText("not a log\n")

        Log.enableFiles(dir, keepDays = 14)
        Log.info("server", "starting")

        val today = File(dir, "shining-2026-11-10.log")
        assertTrue(today.readText().contains("starting"))
        assertFalse(File(dir, "shining-2026-10-01.log").exists(), "older than 14 days")
        assertTrue(File(dir, "shining-2026-11-01.log").exists())
        assertTrue(File(dir, "other.txt").exists())
    }
}
