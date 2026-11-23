package app.shining.push

import app.shining.BuildConfig
import app.shining.debug.DebugViewModel
import app.shining.debug.RequestLogInterceptor
import app.shining.domain.model.PreferencesManager
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

val pushModule = module {
    single<ServerStatusApi> {
        // Its own client, without the Samad AuthInterceptor: Samad tokens never go to our server.
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(RequestLogInterceptor())
            .build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.SHINING_SERVER_URL)
            .client(client)
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ServerStatusApi::class.java)
    }
    single { ServerStatusRepository(get(), get<PreferencesManager>()) }
    single { PushSettings(get<PreferencesManager>()) }
    single { AnnouncedAlerts(get<PreferencesManager>()) }
    single { AlertWindow(get<PreferencesManager>()) }
    single { MenuNotifications(androidContext(), get(), get()) }
    viewModel { MenuStatusViewModel(get(), get()) }
    viewModel { DebugViewModel(androidContext(), get(), get(), get(), get()) }
}
