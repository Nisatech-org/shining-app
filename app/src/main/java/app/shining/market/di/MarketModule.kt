package app.shining.market.di

import app.shining.BuildConfig
import app.shining.account.domain.repository.AccountStore
import app.shining.debug.RequestLogInterceptor
import app.shining.domain.model.PreferencesManager
import app.shining.domain.repository.TokenProvider
import app.shining.market.api.MarketApi
import app.shining.market.domain.GuestMode
import app.shining.market.domain.ListedReservations
import app.shining.market.domain.MarketHistory
import app.shining.market.domain.MarketRepository
import app.shining.market.domain.MarketSessions
import app.shining.market.push.MarketNotifications
import app.shining.market.ui.MarketViewModel
import app.shining.market.ui.MarketWidgetViewModel
import app.shining.market.ui.MealBoardViewModel
import app.shining.market.ui.TradeViewModel
import app.shining.profile.api.ProfileApi
import app.shining.push.PushTopics
import app.shining.reserveinfo.domain.usecase.GetForgetCodeUseCase
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

val marketModule = module {
    single<MarketApi> {
        // Its own client, without the Samad AuthInterceptor: Samad tokens never go to our server,
        // except the one the user sends on purpose when joining.
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(RequestLogInterceptor())
            .build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.SHINING_SERVER_URL)
            .client(client)
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MarketApi::class.java)
    }
    single { MarketSessions(get<PreferencesManager>(), get(), setTopic = PushTopics::set) }
    single { MarketHistory(get<PreferencesManager>(), get()) }
    single { ListedReservations(get<PreferencesManager>()) }
    single { GuestMode(get<PreferencesManager>()) }
    single {
        val accountStore = get<AccountStore>()
        val tokenProvider = get<TokenProvider>()
        val profileApi = get<ProfileApi>()
        val getForgetCode = get<GetForgetCodeUseCase>()
        val guestMode = get<GuestMode>()
        val isGuest = { accountStore.active == null && guestMode.chosen.value }
        MarketRepository(
            api = get(),
            sessions = get(),
            history = get(),
            listed = get(),
            activeUsername = { accountStore.active?.username ?: GuestMode.USERNAME.takeIf { isGuest() } },
            isGuestMode = isGuest,
            samadAuthorization = {
                // Any Samad call renews an expired token first (AuthInterceptor), so the one sent is fresh.
                profileApi.getUserProfile()
                tokenProvider.getSamadToken()?.accessToken
            },
            forgetCode = { reserveId -> getForgetCode(reserveId).map { it.code } },
        )
    }
    single { MarketNotifications(androidContext()) }
    viewModel { MarketViewModel(get(), get()) }
    viewModel { MarketWidgetViewModel(get(), get()) }
    viewModel { params -> MealBoardViewModel(params.get(), get(), get()) }
    viewModel { params -> TradeViewModel(params.get(), get()) }
}
