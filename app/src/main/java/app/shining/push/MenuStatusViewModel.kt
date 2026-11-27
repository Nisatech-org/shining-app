package app.shining.push

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import app.shining.domain.ui.viewmodel.ShiningViewModel
import kotlinx.coroutines.launch

/** The Home card about next week's menus: the saved status first, then the server's latest. */
class MenuStatusViewModel(
    private val repository: ServerStatusRepository,
    private val settings: PushSettings,
) : ShiningViewModel() {

    /** Null while nothing is known (the server was never reached): the card is hidden then. */
    val rows = mutableStateOf<List<MenuStatusRow>?>(null)

    fun onLaunch() {
        repository.cached()?.let(::show)
        onRefresh()
    }

    fun onRefresh() {
        viewModelScope.launch { repository.fetch().onSuccess(::show) }
    }

    private fun show(status: ServerStatus) {
        rows.value = menuStatusRows(status, settings::isVendorEnabled).takeIf { it.isNotEmpty() }
    }
}
