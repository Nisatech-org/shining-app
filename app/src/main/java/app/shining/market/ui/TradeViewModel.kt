package app.shining.market.ui

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.shining.domain.ui.model.ShiningInformationBoxState
import app.shining.domain.ui.model.ShiningInformationBoxUIModel
import app.shining.market.api.TradeView
import app.shining.market.domain.MarketRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TradeState(
    val trade: TradeView? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val busy: String? = null,
    val message: ShiningInformationBoxUIModel? = null,
)

/**
 * One trade, step by step. Refreshes every few seconds while the
 * trade is still moving, so both sides see the other's step without waiting for the push.
 */
class TradeViewModel(
    private val id: Long,
    private val repository: MarketRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val state = mutableStateOf(TradeState())
    val note = mutableStateOf("")
    val problem = mutableStateOf("")
    val showProblem = mutableStateOf(false)

    /** Ticks every second for the timers. */
    val now = mutableLongStateOf(clock())

    private var started = false

    fun onLaunch() {
        // Survives rotation; the loops below must start only once.
        if (started) return
        started = true
        viewModelScope.launch {
            while (true) {
                now.longValue = clock()
                delay(1_000)
            }
        }
        viewModelScope.launch {
            while (true) {
                refresh()
                val active = state.value.trade?.isActive ?: true
                delay(if (active) 5_000 else 30_000)
            }
        }
    }

    suspend fun refresh() {
        repository.trade(id).fold(
            onSuccess = { state.value = state.value.copy(trade = it, loading = false, error = null) },
            onFailure = { state.value = state.value.copy(loading = false, error = it.message) },
        )
    }

    fun onPaid() = act("paid") { repository.paid(id, note.value) }

    fun onCancel() = act("cancel") { repository.cancelTrade(id) }

    /** The seller got the money: their app reads the code from Samad and sends it. */
    fun onConfirm() {
        val trade = state.value.trade ?: return
        act("confirm") { repository.confirm(trade) }
    }

    fun onRate(ok: Boolean) = act(if (ok) "ok" else "bad") { repository.rate(id, ok) }

    fun onProblem() = act("problem") {
        repository.problem(id, problem.value).onSuccess {
            showProblem.value = false
            problem.value = ""
            show(true, "گزارشت رسید. بررسیش می‌کنیم.")
        }
    }

    private fun act(name: String, block: suspend () -> Result<TradeView>) {
        if (state.value.busy != null) return
        viewModelScope.launch {
            state.value = state.value.copy(busy = name)
            block().fold(
                onSuccess = { state.value = state.value.copy(trade = it) },
                onFailure = {
                    show(false, it.message ?: "یه مشکلی پیش اومد.")
                    refresh()
                },
            )
            state.value = state.value.copy(busy = null)
        }
    }

    private fun show(success: Boolean, message: String) {
        val box = ShiningInformationBoxUIModel(
            if (success) ShiningInformationBoxState.SUCCESS else ShiningInformationBoxState.FAILED,
            message,
        )
        state.value = state.value.copy(message = box)
        viewModelScope.launch {
            delay(4_000)
            if (state.value.message == box) state.value = state.value.copy(message = null)
        }
    }
}
