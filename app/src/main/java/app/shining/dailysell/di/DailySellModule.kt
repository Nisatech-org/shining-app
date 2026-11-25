package app.shining.dailysell.di

import app.shining.dailysell.api.DailySellApi
import app.shining.dailysell.domain.repository.DailySellRepository
import app.shining.dailysell.domain.repository.DailySellRepositoryImplementation
import app.shining.dailysell.domain.usecase.GetDailySellProgramUseCase
import app.shining.dailysell.domain.usecase.GetUserDailySalesUseCase
import app.shining.dailysell.domain.usecase.OrderDailySellFoodUseCase
import app.shining.dailysell.ui.viewmodel.DailySellViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

val dailySellModule = module {
    single { get<Retrofit.Builder>().build().create(DailySellApi::class.java) }

    single<DailySellRepository> { DailySellRepositoryImplementation(get(), get()) }

    factory { OrderDailySellFoodUseCase(get()) }

    factory { GetDailySellProgramUseCase(get()) }

    factory { GetUserDailySalesUseCase(get()) }

    viewModel { DailySellViewModel(get(), get(), get(), get(), get(), get()) }
}