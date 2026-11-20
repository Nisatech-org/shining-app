package app.shining.server.log

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The server's one logger. Every line looks like
 *
 *     2026-11-10T14:03:12.345+03:30 INFO  check   done | week=2026-11-14 ms=2310 events=1
 *
 * - time in Tehran, level, a short component tag, a message, then `key=value` details;
 * - everything passes through [Redact], so a token or password can't reach a log by mistake;
 * - a throwable is printed with its whole stack trace, indented under the line.
 *
 * Lines go to every [sinks] entry: standard output (systemd's journal on the server) and,
 * with [enableFiles], a daily file under `data/logs/`.
 */
object Log {
    enum class Level { DEBUG, INFO, WARN, ERROR }

    @Volatile var minLevel: Level = Level.INFO
    @Volatile var clock: () -> Instant = Instant::now
    @Volatile var zone: ZoneId = ZoneId.of("Asia/Tehran")

    private val defaultSinks: List<(String) -> Unit> = listOf({ line -> println(line) })
    @Volatile var sinks: List<(String) -> Unit> = defaultSinks

    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

    fun debug(tag: String, message: String, vararg fields: Pair<String, Any?>) = write(Level.DEBUG, tag, message, fields, null)
    fun info(tag: String, message: String, vararg fields: Pair<String, Any?>) = write(Level.INFO, tag, message, fields, null)
    fun warn(tag: String, message: String, vararg fields: Pair<String, Any?>, error: Throwable? = null) =
        write(Level.WARN, tag, message, fields, error)
    fun error(tag: String, message: String, vararg fields: Pair<String, Any?>, error: Throwable? = null) =
        write(Level.ERROR, tag, message, fields, error)

    /** Reads `SHINING_LOG_LEVEL` (DEBUG, INFO, WARN, ERROR); anything else keeps INFO. */
    fun configure(env: Map<String, String> = System.getenv(), dataDir: File? = null) {
        minLevel = env["SHINING_LOG_LEVEL"]?.uppercase()?.let { runCatching { Level.valueOf(it) }.getOrNull() } ?: Level.INFO
        if (dataDir != null) enableFiles(File(dataDir, "logs"))
    }

    /** Also writes to `dir/shining-yyyy-MM-dd.log`, keeping [keepDays] days of files. */
    fun enableFiles(dir: File, keepDays: Int = 14) {
        val sink = DailyFileSink(dir, keepDays, { clock() }, zone)
        sinks = defaultSinks + sink::write
    }

    /** Back to standard output only. Used by tests. */
    fun reset() {
        sinks = defaultSinks
        minLevel = Level.INFO
        clock = Instant::now
    }

    internal fun format(level: Level, tag: String, message: String, fields: Array<out Pair<String, Any?>>, error: Throwable?): String {
        val time = timeFormat.format(clock().atZone(zone))
        // Each value is cleaned before quoting: quoting escapes `"`, which would hide JSON secrets from the patterns.
        val details = fields.joinToString(" ") { (key, value) -> "$key=${quote(value?.let { Redact.text(it.toString()) })}" }
        val line = buildString {
            append(time).append(' ').append(level.name.padEnd(5)).append(' ').append(tag.padEnd(7)).append(' ').append(Redact.text(message))
            if (details.isNotEmpty()) append(" | ").append(details)
            if (error != null) append('\n').append(stackTrace(error).prependIndent("    "))
        }
        return Redact.text(line)
    }

    private fun write(level: Level, tag: String, message: String, fields: Array<out Pair<String, Any?>>, error: Throwable?) {
        if (level < minLevel) return
        val line = format(level, tag, message, fields, error)
        sinks.forEach { sink -> runCatching { sink(line) } }
    }

    private fun quote(value: Any?): String {
        val text = value?.toString() ?: "null"
        return if (text.isEmpty() || text.any { it.isWhitespace() || it == '"' }) "\"${text.replace("\"", "\\\"")}\"" else text
    }

    private fun stackTrace(error: Throwable): String = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString().trimEnd()
}

/** Blanks out secrets in log text: bearer tokens, token and password fields, private keys. */
object Redact {
    private val rules = listOf(
        Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]{8,}") to "$1***",
        Regex("(?i)(basic\\s+)[A-Za-z0-9+/=]{8,}") to "$1***",
        Regex("(?i)\\b(access_token|refresh_token|id_token|password|assertion|client_secret)=([^&\\s\"]+)") to "$1=***",
        Regex("(?i)\"(access_token|refresh_token|id_token|password|private_key|assertion)\"\\s*:\\s*\"(?:\\\\.|[^\"\\\\])*\"") to "\"$1\":\"***\"",
        Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----") to "***PRIVATE KEY***",
        Regex("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}") to "***JWT***",
    )

    fun text(input: String): String = rules.fold(input) { text, (pattern, replacement) -> pattern.replace(text, replacement) }
}

/** One log file per Tehran day; files older than [keepDays] are deleted when a new day starts. */
internal class DailyFileSink(
    private val dir: File,
    private val keepDays: Int,
    private val clock: () -> Instant,
    private val zone: ZoneId,
) {
    private var openDay: LocalDate? = null

    @Synchronized
    fun write(line: String) {
        val today = clock().atZone(zone).toLocalDate()
        if (today != openDay) {
            dir.mkdirs()
            cleanUp(today)
            openDay = today
        }
        File(dir, "shining-$today.log").appendText(line + "\n")
    }

    private fun cleanUp(today: LocalDate) {
        val oldest = today.minusDays(keepDays.toLong() - 1)
        dir.listFiles { file -> file.name.startsWith("shining-") && file.name.endsWith(".log") }?.forEach { file ->
            val day = runCatching { LocalDate.parse(file.name.removePrefix("shining-").removeSuffix(".log")) }.getOrNull()
            if (day != null && day.isBefore(oldest)) file.delete()
        }
    }
}
