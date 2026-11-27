package app.shining.order.di

import app.shining.order.api.OrderApi
import app.shining.order.domain.repository.OrderFoodRepository
import app.shining.order.domain.repository.OrderFoodRepositoryImplementation
import app.shining.order.domain.usecase.GetAvailableSelfsUseCase
import app.shining.order.domain.usecase.GetReservableProgramUseCase
import app.shining.order.domain.usecase.ReserveFoodUseCase
import app.shining.order.ui.viewmodel.OrderScreenViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

val orderModule = module {
    single {
        get<Retrofit.Builder>().build().create(OrderApi::class.java)
    }

    single<OrderFoodRepository> {
        OrderFoodRepositoryImplementation(get(), get())
    }

    factory {
        GetReservableProgramUseCase(get())
    }

    factory {
        ReserveFoodUseCase(get(), get())
    }

    factory {
        GetAvailableSelfsUseCase(get())
    }

    viewModel {
        OrderScreenViewModel(get(), get(), get(), get())
    }
}