package app.shining.account.di

import app.shining.account.domain.repository.AccountManager
import app.shining.account.domain.repository.AccountStore
import app.shining.account.domain.repository.ReserveInFlightTracker
import app.shining.account.ui.viewmodel.AccountsViewModel
import app.shining.autoreserve.domain.repository.AutoReserveRepository
import app.shining.domain.model.PreferencesManager
import app.shining.profile.domain.usecase.GetNurtureProfile
import app.shining.reserveinfo.domain.repository.ReservationInfoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val accountModule = module {

    single { AccountStore(get<PreferencesManager>(), get()) }

    single { ReserveInFlightTracker() }

    single {
        val getNurtureProfile = get<GetNurtureProfile>()
        AccountManager(
            store = get(),
            tokenProvider = get(),
            inFlight = get(),
            accountData = listOf(get<AutoReserveRepository>(), get<ReservationInfoRepository>()),
            loadDisplayName = {
                getNurtureProfile().map { "${it.firstName} ${it.lastName}".trim() }
            },
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
    }

    viewModel { AccountsViewModel(get(), get(), get()) }
}
