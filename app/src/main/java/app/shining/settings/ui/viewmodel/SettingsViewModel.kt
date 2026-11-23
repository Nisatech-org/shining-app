package app.shining.settings.ui.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import app.shining.domain.model.NavigationEvent
import app.shining.domain.model.PreferencesManager
import app.shining.domain.ui.viewmodel.ShiningViewModel
import app.shining.domain.util.SharedPrefKeys
import app.shining.push.MenuVendorToggle
import app.shining.push.PushSettings
import app.shining.push.PushTopics
import app.shining.push.ServerStatusRepository
import app.shining.push.menuVendorToggles
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val pushSettings: PushSettings,
    private val statusRepository: ServerStatusRepository,
) : ShiningViewModel() {

    val navBack: NavigationEvent = NavigationEvent()
    val navAboutUs: NavigationEvent = NavigationEvent()

    val notificationsEnabled = mutableStateOf(
        preferencesManager.getBoolean(SharedPrefKeys.NotificationsEnabled.key, true)
    )

    /** Next-week menu alerts: one switch per vendor. */
    val menuVendors = mutableStateOf<List<MenuVendorToggle>>(emptyList())

    /** [ownSelfsName] is the label for the user's own cafeterias the server doesn't watch. */
    fun onLaunch(ownSelfsName: String) {
        menuVendors.value = menuVendorToggles(statusRepository.cached(), pushSettings, ownSelfsName)
        // The server's list may have new vendors; the saved one is shown meanwhile.
        viewModelScope.launch {
            statusRepository.fetch().onSuccess {
                menuVendors.value = menuVendorToggles(it, pushSettings, ownSelfsName)
            }
        }
    }

    fun onBackClick() {
        navBack.navigate()
    }

    fun onNotificationToggleChange(
        newState: Boolean,
    ) {
        notificationsEnabled.value = newState
        preferencesManager.putBoolean(
            SharedPrefKeys.NotificationsEnabled.key,
            newState
        )
    }

    fun onMenuVendorToggle(key: String, enabled: Boolean) {
        pushSettings.setVendorEnabled(key, enabled)
        menuVendors.value = menuVendors.value.map { if (it.key == key) it.copy(enabled = enabled) else it }
    }

    /** Turns the server's admin alerts on or off on this phone; returns whether they're on now. */
    fun onToggleAdminAlerts(): Boolean {
        pushSettings.adminAlerts = !pushSettings.adminAlerts
        PushTopics.set(PushTopics.ADMIN, pushSettings.adminAlerts)
        return pushSettings.adminAlerts
    }

    fun onAboutUsClick() {
        navAboutUs.navigate()
    }

    fun onSendFeedBackClick(onFinish: () -> Unit) {
        onFinish()
    }

    fun onRedirectToGithub(invokeIntent: (String) -> Unit) {
        invokeIntent(SOURCE_URL)
    }

    fun onReportIssueClick(invokeIntent: (String) -> Unit) {
        invokeIntent("$SOURCE_URL/issues")
    }

    companion object {
        const val SOURCE_URL = "https://github.com/Nisatech-org/shining-app"
    }
}
