package app.shining.debug

import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The app's own log, kept in a file on the phone so a bug can be looked at after it happened:
 * Settings › tap the version 5 times › debug screen › share the log.
 *
 * Every line also goes to Logcat. Secrets are blanked first ([Redact]), the file is capped at
 * about [LogFile.maxBytes] (the older half is dropped), and a crash is written down before the
 * app dies.
 */
object DebugLog {
    @Volatile private var file: LogFile? = null

    /** Call once from Application.onCreate. */
    fun init(dir: File) {
        file = LogFile(File(dir, "logs"))
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            e("crash", "app crashed on thread ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun d(tag: String, message: String) = write("D", tag, message, null)
    fun i(tag: String, message: String) = write("I", tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = write("W", tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)

    /** The whole log, oldest first, for the debug screen and sharing. */
    fun read(): String = file?.read().orEmpty()

    fun clear() = file?.clear()

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        val text = Redact.text(if (error == null) message else "$message\n${stackTrace(error)}")
        when (level) {
            "D" -> Log.d("Shining/$tag", text)
            "I" -> Log.i("Shining/$tag", text)
            "W" -> Log.w("Shining/$tag", text)
            else -> Log.e("Shining/$tag", text)
        }
        file?.append(level, tag, text)
    }

    private fun stackTrace(error: Throwable): String =
        StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString().trimEnd().prependIndent("    ")
}

/** The log file itself, separate from Android so it can be tested on the JVM. */
class LogFile(
    private val dir: File,
    val maxBytes: Long = 512 * 1024,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val file get() = File(dir, "shining.log")
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Tehran")
    }

    @Synchronized
    fun append(level: String, tag: String, text: String) {
        runCatching {
            dir.mkdirs()
            file.appendText("${timeFormat.format(Date(clock()))} $level ${tag.padEnd(8)} $text\n")
            if (file.length() > maxBytes) trim()
        }
    }

    @Synchronized
    fun read(): String = runCatching { file.readText() }.getOrDefault("")

    @Synchronized
    fun clear() {
        file.delete()
    }

    /** Keeps the newest half, starting at a line boundary. */
    private fun trim() {
        val text = file.readText()
        val keepFrom = text.length / 2
        val cut = text.indexOf('\n', keepFrom).takeIf { it >= 0 }?.plus(1) ?: keepFrom
        file.writeText("--- older lines removed to keep the log small ---\n" + text.substring(cut))
    }
}

/** Blanks out secrets: bearer and basic tokens, token and password fields, JWTs. */
object Redact {
    private val rules = listOf(
        Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]{8,}") to "$1***",
        Regex("(?i)(basic\\s+)[A-Za-z0-9+/=]{8,}") to "$1***",
        Regex("(?i)\\b(access_token|refresh_token|password|accessToken|refreshToken)=([^&\\s\",)]+)") to "$1=***",
        Regex("(?i)\"(access_token|refresh_token|password|accessToken|refreshToken)\"\\s*:\\s*\"(?:\\\\.|[^\"\\\\])*\"") to "\"$1\":\"***\"",
        Regex("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}") to "***JWT***",
    )

    fun text(input: String): String = rules.fold(input) { text, (pattern, replacement) -> pattern.replace(text, replacement) }
}
