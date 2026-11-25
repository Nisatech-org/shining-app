package app.shining.autoreserve.di

import androidx.room.Room
import app.shining.autoreserve.api.AutoReserveApi
import app.shining.account.domain.repository.AccountStore
import app.shining.autoreserve.db.AutoReserveDataBase
import app.shining.autoreserve.db.AutoReserveMigration1To2
import app.shining.autoreserve.db.PrepopulateFoodsDatabase
import app.shining.autoreserve.domain.repository.AutoReserveRepository
import app.shining.autoreserve.domain.repository.AutoReserveRepositoryImplementation
import app.shining.autoreserve.domain.usecase.GetAllFoodsUseCase
import app.shining.autoreserve.domain.usecase.GetAllSelectedDaysUseCase
import app.shining.autoreserve.domain.usecase.InsertFoodUseCase
import app.shining.autoreserve.domain.usecase.UpdateAutoReserveDaysUseCase
import app.shining.autoreserve.domain.usecase.UpdateFoodPriorityUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import retrofit2.Retrofit

// Auto-reserve has no screen for now; its data layer stays so it can come back later.
val autoReserveModule = module {

    single {
        val accountStore = get<AccountStore>()
        Room
            .databaseBuilder(
                get(),
                AutoReserveDataBase::class.java,
                AutoReserveDataBase.DATABASE_NAME
            )
            .addCallback(PrepopulateFoodsDatabase(androidContext()))
            .addMigrations(AutoReserveMigration1To2 { accountStore.legacyUsername })
            .build()
    }

    single { get<Retrofit.Builder>().build().create(AutoReserveApi::class.java) }

    single<AutoReserveRepository> {
        val accountStore = get<AccountStore>()
        AutoReserveRepositoryImplementation(get()) { accountStore.active?.username }
    }

    factory { GetAllSelectedDaysUseCase(get()) }

    factory { GetAllFoodsUseCase(get()) }

    factory { InsertFoodUseCase(get()) }

    factory { UpdateAutoReserveDaysUseCase(get()) }

    factory { UpdateFoodPriorityUseCase(get()) }
}