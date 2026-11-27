package app.shining.domain.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import app.shining.debug.RequestLogInterceptor
import app.shining.domain.api.DomainApi
import app.shining.domain.api.TokenApi
import app.shining.domain.model.PreferencesManager
import app.shining.domain.network.AuthInterceptor
import app.shining.domain.repository.DomainRepository
import app.shining.domain.repository.DomainRepositoryImplementation
import app.shining.domain.repository.TokenProvider
import app.shining.domain.usecase.CheckTokenValidationUseCase
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import retrofit2.Retrofit

val appModule = module {
    val networkJson = Json { ignoreUnknownKeys = true }

    single { networkJson }

    single {
        Retrofit.Builder()
            .baseUrl("https://setad.dining.sharif.edu/")
            .addConverterFactory(networkJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TokenApi::class.java)
    }

    single { TokenProvider(get<TokenApi>(), get()) }

    single {
        PreferencesManager(get())
    }

    single {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(get()))
            .addInterceptor(HttpLoggingInterceptor())
            .addInterceptor(RequestLogInterceptor())
            .build()
    }

    single<Retrofit.Builder> {
        Retrofit
            .Builder()
            .baseUrl("https://setad.dining.sharif.edu/")
            .client(get())
            .addConverterFactory(networkJson.asConverterFactory("application/json".toMediaType()))
    }

    single { get<Retrofit.Builder>().build().create(DomainApi::class.java) }

    single<DomainRepository> { DomainRepositoryImplementation(get()) }

    factory { CheckTokenValidationUseCase(get()) }
}