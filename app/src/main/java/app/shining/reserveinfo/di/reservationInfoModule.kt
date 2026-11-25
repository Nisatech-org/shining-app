package app.shining.reserveinfo.di

import androidx.room.Room
import app.shining.account.domain.repository.AccountStore
import app.shining.domain.model.PreferencesManager
import app.shining.reserveinfo.api.ReserveInfoApi
import app.shining.reserveinfo.db.ForgetCodeDatabase
import app.shining.reserveinfo.db.ForgetCodeMigration1To2
import app.shining.reserveinfo.domain.repository.ReservationInfoRepository
import app.shining.reserveinfo.domain.repository.ReservationInfoRepositoryImplementation
import app.shining.reserveinfo.domain.usecase.GetForgetCodeMapCacheUseCase
import app.shining.reserveinfo.domain.usecase.GetForgetCodeUseCase
import app.shining.reserveinfo.domain.usecase.LoadReservationsUseCase
import app.shining.reserveinfo.ui.viewmodel.ReservationInfoViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

val reserveInfoModule = module {
    single<ReserveInfoApi> {
        get<Retrofit.Builder>().build().create(ReserveInfoApi::class.java)
    }

    single {
        val accountStore = get<AccountStore>()
        Room
            .databaseBuilder(
                get(),
                ForgetCodeDatabase::class.java,
                ForgetCodeDatabase.DATABASE_NAME
            )
            .addMigrations(ForgetCodeMigration1To2 { accountStore.legacyUsername })
            .build()
    }

    single { get<ForgetCodeDatabase>().forgetCodeDao() }

    single<ReservationInfoRepository> {
        val accountStore = get<AccountStore>()
        ReservationInfoRepositoryImplementation(
            api = get(),
            forgetCodeDao = get(),
            storage = get<PreferencesManager>(),
            json = get(),
            activeAccountId = { accountStore.active?.username },
        )
    }

    factory {
        LoadReservationsUseCase(get())
    }

    viewModel {
        ReservationInfoViewModel(get(), get(), get())
    }

    factory {
        GetForgetCodeUseCase(get())
    }

    factory {
        GetForgetCodeMapCacheUseCase(get())
    }

}