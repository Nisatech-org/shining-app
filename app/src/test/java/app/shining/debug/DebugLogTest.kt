package app.shining.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DebugLogTest {

    private val dir = Files.createTempDirectory("shining-log").toFile()

    @Test
    fun `lines are written with Tehran time, level and tag`() {
        // 10:33:12 UTC on 2026-11-10 is 14:03:12 in Tehran.
        val log = LogFile(dir, clock = { 1_794_306_792_000L })
        log.append("I", "push", "push received")
        assertEquals("2026-11-10 14:03:12.000 I push     push received\n", log.read())
    }

    @Test
    fun `a full log drops its older half, at a line boundary`() {
        val log = LogFile(dir, maxBytes = 2_000)
        repeat(100) { log.append("I", "t", "line number $it") }

        val text = log.read()
        assertTrue(File(dir, "shining.log").length() <= 2_000 + 100)
        assertTrue(text.startsWith("--- older lines removed"))
        assertTrue("line number 99" in text)
        assertFalse("line number 0\n" in text)
        assertTrue(text.lines().drop(1).filter { it.isNotEmpty() }.all { it.matches(Regex("\\d{4}-.* I t +line number \\d+")) })
    }

    @Test
    fun `clearing removes everything`() {
        val log = LogFile(dir)
        log.append("W", "x", "something")
        log.clear()
        assertEquals("", log.read())
    }

    @Test
    fun `secrets are blanked before they're written`() {
        val text = Redact.text(
            "Authorization: Bearer abcdefghij1234567890 | GET oauth/token?username=98123456&password=hunter2secret " +
                """| {"access_token":"tok123456789","refresh_token":"ref123456789"} | Account(accessToken=acc123456789, refreshToken=ref987654321)""",
        )
        listOf("abcdefghij1234567890", "hunter2secret", "tok123456789", "ref123456789", "acc123456789", "ref987654321")
            .forEach { assertFalse("leaked $it in $text", it in text) }
        assertTrue("username=98123456" in text)
    }
}
