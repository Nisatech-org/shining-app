package app.shining.credit.di

import app.shining.credit.api.CreditApi
import app.shining.credit.domain.repository.CreditRepository
import app.shining.credit.domain.repository.CreditRepositoryImplementation
import app.shining.credit.domain.usecase.GetRedirectLoginAsTokenUseCase
import org.koin.dsl.module
import retrofit2.Retrofit

val creditModule = module {

    single { get<Retrofit.Builder>().build().create(CreditApi::class.java) }

    single<CreditRepository> { CreditRepositoryImplementation(get()) }

    factory { GetRedirectLoginAsTokenUseCase(get()) }
}