package app.shining

import android.app.Application
import android.os.Build
import app.shining.debug.DebugLog
import app.shining.account.di.accountModule
import app.shining.autoreserve.di.autoReserveModule
import app.shining.credit.di.creditModule
import app.shining.dailysell.di.dailySellModule
import app.shining.domain.di.appModule
import app.shining.home.di.homeModule
import app.shining.login.di.loginModule
import app.shining.account.domain.repository.AccountStore
import app.shining.market.di.marketModule
import app.shining.market.domain.MarketSessions
import app.shining.order.di.orderModule
import app.shining.profile.di.profileModule
import app.shining.push.MenuCheckWorker
import app.shining.push.PushSettings
import app.shining.push.PushTopics
import app.shining.push.pushModule
import app.shining.reserveinfo.di.reserveInfoModule
import app.shining.settings.di.settingsModule
import app.shining.splash.di.splashModule
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class ShiningApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        DebugLog.init(filesDir)
        DebugLog.i(
            "app",
            "started: version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}, " +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}, " +
                "server ${BuildConfig.SHINING_SERVER_URL}",
        )
        startKoin {
            androidLogger()
            androidContext(this@ShiningApplication)
            modules(
                appModule,
                splashModule,
                loginModule,
                homeModule,
                creditModule,
                reserveInfoModule,
                profileModule,
                orderModule,
                dailySellModule,
                settingsModule,
                autoReserveModule,
                accountModule,
                pushModule,
                marketModule,
            )
        }
        PushTopics.subscribeAll(get<PushSettings>())
        get<MarketSessions>().subscribeAll(get<AccountStore>().state.value.accounts.map { it.username })
        MenuCheckWorker.schedule(this)
    }
}
