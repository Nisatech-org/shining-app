package app.shining.domain.util

import android.net.Uri
import androidx.navigation.NavController
import app.shining.login.domain.model.LoginMode

enum class ShiningRoutes(
    val route: String,
) {
    LoginScreen("login?$LOGIN_ARG_MODE={$LOGIN_ARG_MODE}&$LOGIN_ARG_USERNAME={$LOGIN_ARG_USERNAME}"),
    SplashScreen("splash_screen"),
    ReservationInfoScreen("reservation_info"),
    ReservableScreen("reservable"),
    ProfileScreen("profile"),
    DailySaleScreen("daily_sale"),
    SettingsScreen("settings"),

    /** Hidden: Settings › tap the version 5 times. */
    DebugScreen("debug"),
    ;

    companion object {
        fun login(mode: LoginMode, username: String? = null): String =
            "login?$LOGIN_ARG_MODE=${mode.name}" +
                username?.let { "&$LOGIN_ARG_USERNAME=${Uri.encode(it)}" }.orEmpty()
    }
}

const val LOGIN_ARG_MODE = "mode"
const val LOGIN_ARG_USERNAME = "username"

/**
 * Navigates to [route] and drops everything before it, so screens (and their view models) from
 * a previous account or login state can't be reached with back.
 */
fun NavController.navigateClearingStack(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
