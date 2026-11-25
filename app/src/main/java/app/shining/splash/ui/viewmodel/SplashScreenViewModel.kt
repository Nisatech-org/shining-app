package app.shining.splash.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import app.shining.account.domain.model.AuthException
import app.shining.account.domain.model.ResumeResult
import app.shining.account.domain.repository.AccountManager
import app.shining.domain.model.NavigationEvent
import app.shining.domain.network.ConnectionState
import app.shining.domain.network.currentConnectivityState
import app.shining.domain.ui.viewmodel.ShiningViewModel
import kotlinx.coroutines.launch

class SplashScreenViewModel(
    private val accountManager: AccountManager,
) : ShiningViewModel() {

    val showNetworkError = mutableStateOf(false)
    val navLogin: NavigationEvent = NavigationEvent()
    val navReauth: NavigationEvent = NavigationEvent()
    val navReserveInfo: NavigationEvent = NavigationEvent()

    /** Account whose saved password was rejected, for [navReauth]. */
    var reauthUsername: String? = null
        private set

    fun onLaunch(context: Context) {
        start(isOnline = context.currentConnectivityState == ConnectionState.Available)
    }

    /**
     * Opens the app for the saved account. Without internet, or when Samad can't be reached, an
     * account that logged in before still goes to the home screen, which shows its saved
     * reservations; only a first launch with no account stops at the network error.
     */
    fun start(isOnline: Boolean) {
        if (!isOnline) {
            openOffline()
            return
        }
        viewModelScope.launch {
            when (val result = accountManager.resumeActive()) {
                ResumeResult.Resumed -> navReserveInfo.navigate()
                ResumeResult.NoAccount -> navLogin.navigate()
                is ResumeResult.NeedsLogin -> when (result.error) {
                    is AuthException.Network -> openOffline()
                    is AuthException.InvalidCredentials -> {
                        reauthUsername = result.username
                        navReauth.navigate()
                    }
                }
            }
        }
    }

    private fun openOffline() {
        if (accountManager.hasActiveAccount) {
            navReserveInfo.navigate()
        } else {
            showNetworkError.value = true
        }
    }

    fun onReload(context: Context) {
        showNetworkError.value = false
        onLaunch(context = context)
    }
}
