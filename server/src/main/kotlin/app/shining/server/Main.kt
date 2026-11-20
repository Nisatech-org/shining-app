package app.shining.server

import app.shining.server.discovery.Discovery
import app.shining.server.samad.SamadAuthException
import app.shining.server.samad.SamadClient
import app.shining.server.samad.SamadHttpException
import java.io.IOException
import kotlin.system.exitProcess

private const val USAGE = """Usage:
  shining-server ping                       check that Samad can be reached (no login)
  shining-server discover [student-number]  save the cafeteria list and menus to discovery-output/
  shining-server run                        watch Samad for next week's menus and serve /v1/status
  shining-server push-test [message]        send one test alert to the 'admin' topic through Firebase
  shining-server push-test menus            send a sample menu alert to the 'admin' topic

run reads SAMAD_USERNAME and SAMAD_PASSWORD (required), and SHINING_DATA_DIR (default: data),
SHINING_HOST (default: 127.0.0.1) and SHINING_PORT (default: 8050). run and push-test send through
Firebase when SHINING_FCM_KEY_FILE points to the service-account key file.

discover asks for the password without showing it. Where there is no terminal to type into,
it reads the SAMAD_PASSWORD environment variable instead."""

fun main(args: Array<String>) {
    try {
        when (args.firstOrNull()) {
            "ping" -> {
                val status = SamadClient().ping()
                // 401 is the expected answer: the connection and certificate work, we're just not logged in.
                println("Samad answered HTTP $status" + if (status == 401) " (reachable)" else "")
            }

            "run" -> runServer(ServerConfig.fromEnv())

            "push-test" -> if (args.getOrNull(1) == "menus") {
                pushTestMenus()
            } else {
                pushTest(args.drop(1).joinToString(" ").ifBlank { "تست اعلان سرور شاینینگ" })
            }

            "discover" -> {
                val username = toEnglishDigits(args.getOrNull(1) ?: prompt("Student number: ")).trim()
                Discovery.run(username, readPassword())
            }

            else -> {
                println(USAGE)
                exitProcess(1)
            }
        }
    } catch (e: SamadAuthException) {
        System.err.println(e.message)
        exitProcess(2)
    } catch (e: SamadHttpException) {
        System.err.println(e.message)
        exitProcess(3)
    } catch (e: IllegalArgumentException) {
        System.err.println(e.message)
        exitProcess(1)
    } catch (e: IOException) {
        System.err.println("Can't reach Samad: ${e.javaClass.simpleName}: ${e.message}")
        exitProcess(4)
    }
}

private fun prompt(text: String): String =
    System.console()?.readLine(text) ?: run {
        print(text)
        readln()
    }

private fun readPassword(): CharArray {
    System.console()?.readPassword("Samad password (hidden): ")?.let {
        return toEnglishDigits(String(it)).toCharArray().also { _ -> it.fill(' ') }
    }
    System.getenv("SAMAD_PASSWORD")?.takeIf { it.isNotEmpty() }?.let {
        return toEnglishDigits(it).toCharArray()
    }
    System.err.println("No terminal to read the password from. Run it in PowerShell, or set SAMAD_PASSWORD.")
    exitProcess(1)
}

/** Persian and Arabic digits typed on a Persian keyboard become 0-9, as the app does. */
fun toEnglishDigits(text: String): String = text.map { char ->
    when (char) {
        in '۰'..'۹' -> '0' + (char - '۰')
        in '٠'..'٩' -> '0' + (char - '٠')
        else -> char
    }
}.joinToString("")
