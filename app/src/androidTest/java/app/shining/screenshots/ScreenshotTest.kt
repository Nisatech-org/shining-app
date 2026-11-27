package app.shining.screenshots

import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.shining.R
import app.shining.account.ui.component.AccountSwitcherSheet
import app.shining.account.ui.model.AccountRowUIModel
import app.shining.dailysell.domain.model.DailySellItem
import app.shining.dailysell.domain.model.DailySellProgram
import app.shining.dailysell.domain.model.SamadTime
import app.shining.dailysell.domain.model.UserDailySaleInfo
import app.shining.dailysell.domain.model.UserDailySales
import app.shining.dailysell.ui.DailySellHeader
import app.shining.dailysell.ui.component.DailySaleScreen
import app.shining.dailysell.ui.model.toDailySellScreenUIModel
import app.shining.domain.model.LoadableData
import app.shining.domain.ui.component.ScreenHeader
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.ShiningRoutes
import app.shining.home.ui.component.BottomNavigationBarContent
import app.shining.login.ui.component.CaptchaUIModel
import app.shining.login.ui.component.LoginScreen
import app.shining.login.ui.model.LoginScreenUIModel
import app.shining.market.api.Board
import app.shining.market.api.ListingView
import app.shining.market.api.MealInfo
import app.shining.market.api.OrderView
import app.shining.market.api.Reputation
import app.shining.market.api.TradeView
import app.shining.market.ui.MarketInfoSheet
import app.shining.market.ui.MarketScreen
import app.shining.market.ui.MarketScreenState
import app.shining.market.ui.MealBoardScreen
import app.shining.market.ui.MealBoardState
import app.shining.market.ui.SellCardState
import app.shining.market.ui.TradeScreen
import app.shining.market.ui.TradeState
import app.shining.order.domain.model.MealType
import app.shining.order.ui.ReserveScreenHeader
import app.shining.order.ui.component.OrderScreen
import app.shining.order.ui.model.OrderCardUIModel
import app.shining.order.ui.model.OrderFoodDetailUIModel
import app.shining.order.ui.model.OrderScreenUIModel
import app.shining.profile.ui.ProfileContent
import app.shining.profile.ui.model.CreditTransferUIModel
import app.shining.profile.ui.model.ProfileScreenUIModel
import app.shining.push.MenuState
import app.shining.push.MenuStatusRow
import app.shining.push.MenuVendorToggle
import app.shining.reserveinfo.ui.component.ReservationInfoScreen
import app.shining.reserveinfo.ui.model.MealRowUIModel
import app.shining.reserveinfo.ui.model.ReservationInfoCardUIModel
import app.shining.reserveinfo.ui.model.ReservationInfoScreenUIModel
import app.shining.settings.ui.component.SettingsScreen
import app.shining.splash.ui.component.SplashScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Renders every screen with sample data and saves a full-screen PNG of each (sheets included,
 * since they open in their own window) to the app's files/screenshots folder. It uses the
 * emulator's own light or dark setting, since sheets follow it: run it once in each.
 * Not a regression test: it only makes the pictures in docs/screenshots. See docs/screenshots/README.md.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)

    @Test
    fun captureEveryScreen() {
        val dark = rule.activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val out = File(rule.activity.filesDir, "screenshots").apply { mkdirs() }
        rule.setContent {
            ShiningTheme {
                screen?.invoke()
            }
        }
        for ((name, content) in screens()) {
            screen = content
            rule.waitForIdle()
            // Let sheets slide in and the system bars take the theme's colors.
            Thread.sleep(1_200)
            rule.waitForIdle()
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(out, if (dark) "$name-dark.png" else "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            screen = null
            rule.waitForIdle()
        }
    }

    private fun screens(): List<Pair<String, @Composable () -> Unit>> = listOf(
        "splash" to { SplashScreen(shouldShowNetworkError = false, onReload = {}) },
        "login" to {
            LoginScreen(
                data = LoginScreenUIModel(username = "", password = ""),
                loginStatus = null,
                isLoading = false,
                onUserNameChange = {},
                onPasswordChange = {},
                onLoginClick = {},
                captcha = CaptchaUIModel(imageBase64 = SAMPLE_CAPTCHA),
                onGuestClick = {},
            )
        },
        "home" to { WithBar(ShiningRoutes.ReservationInfoScreen.route) { Home() } },
        "account-switcher" to {
            WithBar(ShiningRoutes.ReservationInfoScreen.route) { Home() }
            Sheet {
                AccountSwitcherSheet(
                    accounts = ACCOUNTS,
                    canAddAccount = true,
                    switchingTo = null,
                    waitingForReserve = false,
                    status = null,
                    onAccountClick = {},
                    onLoginAgainClick = {},
                    onAddAccountClick = {},
                )
            }
        },
        "reserve" to {
            WithBar(ShiningRoutes.ReservableScreen.route) {
                Column(modifier = Modifier.fillMaxSize()) {
                    ReserveScreenHeader(onDailySellClick = {})
                    OrderScreen(data = ORDER, pendingFoods = persistentSetOf(), onReserveFoodClick = {})
                }
            }
        },
        "daily-sale" to {
            Background {
                Column(modifier = Modifier.fillMaxSize()) {
                    DailySellHeader(onBackClick = {}, onIncreaseCreditClick = {})
                    DailySaleScreen(
                        uiModel = DAILY_SALE,
                        onOrderDailySaleClick = { _, _ -> },
                        onGetForgetCodeClick = { _, _ -> },
                    )
                }
            }
        },
        "market" to { WithBar(ShiningRoutes.MarketScreen.route) { Market(MARKET) } },
        "market-join" to {
            WithBar(ShiningRoutes.MarketScreen.route) {
                Market(MARKET.copy(joined = false, activeTrades = emptyList(), pastTrades = emptyList()))
            }
        },
        "market-info" to {
            WithBar(ShiningRoutes.MarketScreen.route) { Market(MARKET) }
            MarketInfoSheet(joined = true, onDismiss = {})
        },
        "market-meal" to {
            MealBoardScreen(
                state = MEAL_BOARD,
                price = "",
                card = "",
                onPriceChange = {},
                onCardChange = {},
                onBack = {},
                onRetry = {},
                onJoin = {},
                onBuyNow = {},
                onSellNow = {},
                onPlaceAsk = {},
                onPlaceBid = {},
                onCancelOrder = {},
                onOpenTrade = {},
            )
        },
        "trade" to {
            TradeScreen(
                state = TradeState(trade = TRADE, loading = false),
                now = NOW,
                note = "",
                problem = "",
                showProblem = false,
                onNoteChange = {},
                onProblemChange = {},
                onShowProblem = {},
                onBack = {},
                onRetry = {},
                onPaid = {},
                onCancel = {},
                onConfirm = {},
                onRate = {},
                onSendProblem = {},
                onOpenBoard = {},
            )
        },
        "profile" to {
            WithBar(ShiningRoutes.ProfileScreen.route) {
                ProfileContent(
                    profile = LoadableData.Loaded(PROFILE),
                    accounts = ACCOUNTS,
                    canAddAccount = true,
                    switchingTo = null,
                    waitingForReserve = false,
                    accountStatus = null,
                    onSettingsClick = {},
                    onIncreaseCreditClick = {},
                    onRefresh = {},
                    onLogoutClick = {},
                    onAccountClick = {},
                    onLoginAgainClick = {},
                    onAddAccountClick = {},
                    onRemoveClick = {},
                    onMove = { _, _ -> },
                )
            }
        },
        "settings" to {
            WithBar(ShiningRoutes.SettingsScreen.route) {
                Column(modifier = Modifier.fillMaxSize()) {
                    ScreenHeader(title = stringResource(R.string.settings_screen_title), onBackClick = {})
                    SettingsScreen(
                        notificationsEnabled = true,
                        onNotificationToggleChange = {},
                        menuVendors = VENDORS,
                        onAboutClick = {},
                        onSourceClick = {},
                        onReportIssueClick = {},
                    )
                }
            }
        },
    )

    @Composable
    private fun Background(content: @Composable BoxScope.() -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ShiningTheme.colors.background)
        ) { content() }
    }

    /** A tab's screen with the floating bar over it, as in MainActivity. */
    @Composable
    private fun WithBar(route: String, content: @Composable () -> Unit) {
        Background {
            content()
            BottomNavigationBarContent(
                currentRoute = route,
                onItemClick = {},
                onProfileLongPress = {},
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Sheet(content: @Composable () -> Unit) {
        ModalBottomSheet(
            onDismissRequest = {},
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = ShiningTheme.colors.surface,
            shape = ShiningTheme.shapes.sheet,
        ) {
            ShiningTheme { content() }
        }
    }

    @Composable
    private fun Home() {
        ReservationInfoScreen(
            name = "علی",
            accountInitial = "ع",
            reservations = LoadableData.Loaded(RESERVATIONS),
            savedNotice = null,
            onSettingsClick = {},
            onAvatarClick = {},
            onAvatarLongClick = {},
            onRefresh = {},
            onNavigate = {},
            onGetForgetCodeClick = { _, _ -> },
            menuStatus = MENU_STATUS,
            sellCard = SellCardState(meal = LUNCH_TOMORROW, reserveId = 11, priceNow = 55_000, offers = 2),
        )
    }

    @Composable
    private fun Market(state: MarketScreenState) {
        MarketScreen(
            state = state,
            onRefresh = {},
            onJoin = {},
            onLeave = {},
            onLogin = {},
            onDaySelected = {},
            onMealTypeSelected = {},
            onSelfSelected = {},
            onSell = {},
            onBuy = {},
            onCancelListing = {},
            onTradeClick = {},
        )
    }

    private companion object {
        val NOW = System.currentTimeMillis()

        /** A made-up captcha (androidTest assets), so the login screen looks as it does with Samad's. */
        val SAMPLE_CAPTCHA: String = InstrumentationRegistry.getInstrumentation().context.assets
            .open("sample-captcha.png").use { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
        private const val DAY = 24 * 60 * 60 * 1000L

        fun date(daysFromNow: Int): String = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("Asia/Tehran") }
            .format(Date(NOW + daysFromNow * DAY))

        fun instant(millis: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

        val ACCOUNTS = persistentListOf(
            AccountRowUIModel("401105678", "علی رضایی", "401105678", "ع", isActive = true, needsReauth = false),
            AccountRowUIModel("400209876", "مریم احمدی", "400209876", "م", isActive = false, needsReauth = false),
        )

        val MENU_STATUS = listOf(
            MenuStatusRow("سلف", MenuState.Waiting, "معمولاً سه‌شنبه"),
            MenuStatusRow("کاله", MenuState.Published, "از دوشنبه ۱۵:۴۶"),
            MenuStatusRow("فست‌فود شریف", MenuState.Published, "از دوشنبه ۱۸:۱۴"),
            MenuStatusRow("کلین‌فود", MenuState.Partial, "۳ روز از ۵ روز"),
        )

        val RESERVATIONS = ReservationInfoScreenUIModel(
            persistentListOf(
                ReservationInfoCardUIModel(
                    farsiDayName = "شنبه",
                    farsiDate = "۱۴۰۵/۰۹/۰۸",
                    mealRowUIModelList = persistentListOf(
                        MealRowUIModel("ناهار", "مرکزی - سلف دانشجویان آقا", "چلو جوجه کباب", 1, consumed = true),
                        MealRowUIModel("شام", "مرکزی - سلف دانشجویان آقا", "عدس پلو", 2, forgetCode = "48213"),
                    ),
                ),
                ReservationInfoCardUIModel(
                    farsiDayName = "یکشنبه",
                    farsiDate = "۱۴۰۵/۰۹/۰۹",
                    mealRowUIModelList = persistentListOf(
                        MealRowUIModel("ناهار", "کاله", "قرمه سبزی", 3, forgetCode = "19377"),
                    ),
                ),
                ReservationInfoCardUIModel(
                    farsiDayName = "دوشنبه",
                    farsiDate = "۱۴۰۵/۰۹/۱۰",
                    mealRowUIModelList = persistentListOf(
                        MealRowUIModel("ناهار", "مرکزی - سلف دانشجویان آقا", "خوراک لوبیا", 4),
                    ),
                ),
            )
        )

        fun food(
            name: String,
            price: Long,
            self: String,
            foodTypeId: Int,
            mealTypeId: Int = 1,
            selected: Boolean = false,
        ) = OrderFoodDetailUIModel(
            programId = 10 + mealTypeId,
            selfId = self.hashCode(),
            mealTypeId = mealTypeId,
            foodTypeId = foodTypeId,
            foodName = name,
            price = price,
            selfName = self,
            isSelected = selected,
        )

        val ORDER = OrderScreenUIModel(
            persistentListOf(
                OrderCardUIModel(
                    farsiDayName = "شنبه",
                    date = date(2),
                    dayName = "Saturday",
                    reserveInfoList = mapOf(
                        MealType.LUNCH to persistentListOf(
                            food("زرشک پلو با مرغ", 45_000, "سلف", 1, selected = true),
                            food("کباب کوبیده", 98_000, "کاله", 2),
                            food("پاستا آلفردو", 120_000, "کلانا", 3),
                        ),
                        MealType.DINNER to persistentListOf(
                            food("عدس پلو", 35_000, "سلف", 1, mealTypeId = 5),
                            food("سالاد سزار", 110_000, "کلین‌فود", 2, mealTypeId = 5),
                        ),
                    ),
                ),
                OrderCardUIModel("یکشنبه", date(3), "Sunday", emptyMap()),
                OrderCardUIModel("دوشنبه", date(4), "Monday", emptyMap()),
                OrderCardUIModel("سه‌شنبه", date(5), "Tuesday", emptyMap()),
                OrderCardUIModel("چهارشنبه", date(6), "Wednesday", emptyMap()),
            )
        )

        fun sellItem(id: Int, name: String, price: Int, sold: Int, count: Int, meal: String = "ناهار") = DailySellItem(
            count = count,
            finishTime = SamadTime(13, 30, "13:30"),
            foodNames = name,
            foodTypeTitle = "",
            id = id,
            mealTypeName = meal,
            price = price,
            programDate = date(0),
            selfName = "مرکزی - سلف دانشجویان آقا",
            soldCount = sold,
            startTime = SamadTime(11, 0, "11:00"),
            totalCount = count,
        )

        val DAILY_SALE = DailySellProgram(
            listOf(
                sellItem(1, "قیمه", 45_000, sold = 12, count = 40),
                sellItem(2, "کباب کوبیده", 80_000, sold = 38, count = 40),
                sellItem(3, "ماکارونی", 40_000, sold = 30, count = 30),
            )
        ).toDailySellScreenUIModel(
            forgetCodeMap = mapOf(7 to "52741"),
            userDailySales = UserDailySales(
                listOf(
                    UserDailySaleInfo(
                        consumed = false,
                        dailySellProgramId = 1,
                        finishTime = null,
                        foodNames = "قیمه",
                        foodTypeTitle = "",
                        id = 7,
                        price = 45_000,
                        programDate = date(0),
                        remainCount = 0,
                        selfName = "مرکزی - سلف دانشجویان آقا",
                        startTime = null,
                    ),
                )
            ),
        )

        fun meal(days: Int, mealTypeId: Int, mealName: String, selfId: Int, self: String, food: String, price: Long) =
            MealInfo(date(days), mealTypeId, mealName, selfId, self, foodId = food.hashCode(), foodName = food, reservedPrice = price)

        val LUNCH_TOMORROW = meal(1, 1, "ناهار", 1, "مرکزی - سلف دانشجویان آقا", "چلو مرغ", 45_000)
        private val KALE_LUNCH = meal(1, 1, "ناهار", 2, "کاله", "کباب کوبیده", 98_000)
        private val DINNER_TOMORROW = meal(1, 5, "شام", 1, "مرکزی - سلف دانشجویان آقا", "عدس پلو", 35_000)
        private val LUNCH_LATER = meal(2, 1, "ناهار", 3, "کلانا", "پاستا آلفردو", 120_000)

        val LISTINGS = listOf(
            ListingView(1, LUNCH_TOMORROW.key, LUNCH_TOMORROW, 50_000, verified = true, reputation = Reputation(sales = 6)),
            ListingView(2, KALE_LUNCH.key, KALE_LUNCH, 85_000, verified = true, reputation = Reputation(sales = 2)),
            ListingView(3, DINNER_TOMORROW.key, DINNER_TOMORROW, 30_000),
            ListingView(4, LUNCH_LATER.key, LUNCH_LATER, 100_000, mine = true, verified = true),
        )

        val TRADE = TradeView(
            id = 41,
            key = KALE_LUNCH.key,
            meal = KALE_LUNCH,
            role = "buyer",
            origin = "buy_now",
            price = 85_000,
            status = TradeView.AWAITING_PAYMENT,
            createdAt = instant(NOW - 60_000),
            payBy = instant(NOW + 4 * 60_000),
            card = "6037 99** **** 4821",
            counterpart = Reputation(sales = 2),
            counterpartVerified = true,
        )

        private val PAST_TRADE = TradeView(
            id = 37,
            key = DINNER_TOMORROW.key,
            meal = DINNER_TOMORROW.copy(date = date(-2)),
            role = "seller",
            origin = "buy_now",
            price = 30_000,
            status = TradeView.COMPLETED,
            createdAt = instant(NOW - 2 * DAY),
            payBy = instant(NOW - 2 * DAY + 5 * 60_000),
            completedAt = instant(NOW - 2 * DAY + 3 * 60_000),
            buyerOk = true,
        )

        val MARKET = MarketScreenState(
            joined = true,
            all = LISTINGS,
            listings = LISTINGS,
            days = listOf(date(1) to "فردا", date(2) to "پس‌فردا"),
            mealTypes = listOf(1 to "ناهار", 5 to "شام"),
            selves = listOf(1 to "سلف", 2 to "کاله", 3 to "کلانا"),
            activeTrades = listOf(TRADE),
            pastTrades = listOf(PAST_TRADE),
            loading = false,
        )

        val MEAL_BOARD = MealBoardState(
            key = KALE_LUNCH.key,
            meal = KALE_LUNCH,
            board = Board(
                key = KALE_LUNCH.key,
                meal = KALE_LUNCH,
                asks = listOf(
                    OrderView(1, "ask", 85_000, instant(NOW - 3_600_000), verified = true, reputation = Reputation(sales = 2)),
                    OrderView(2, "ask", 90_000, instant(NOW - 7_200_000)),
                ),
                bids = listOf(
                    OrderView(3, "bid", 70_000, instant(NOW - 1_800_000), verified = true),
                ),
                lastPrice = 80_000,
                priceNow = 80_000,
            ),
            joined = true,
            card = "6037 99** **** 1234",
            loading = false,
        )

        val PROFILE = ProfileScreenUIModel(
            firstName = "علی",
            lastName = "رضایی",
            credit = "1250000",
            userName = "401105678",
            creditTransfers = persistentListOf(
                CreditTransferUIModel("۵۰۰۰۰۰", "۱۴۰۵/۰۹/۰۲", isPositive = true, amountValue = 500_000),
                CreditTransferUIModel("-۴۵۰۰۰", "۱۴۰۵/۰۹/۰۱", isPositive = false, amountValue = -45_000),
                CreditTransferUIModel("-۹۸۰۰۰", "۱۴۰۵/۰۸/۲۸", isPositive = false, amountValue = -98_000),
                CreditTransferUIModel("۲۰۰۰۰۰", "۱۴۰۵/۰۸/۲۰", isPositive = true, amountValue = 200_000),
            ),
        )

        val VENDORS = listOf(
            MenuVendorToggle("self", "سلف", true),
            MenuVendorToggle("kaleh", "کاله", true),
            MenuVendorToggle("kalana", "کلانا", false),
            MenuVendorToggle("cleanfood", "کلین‌فود", true),
            MenuVendorToggle("unifood", "یونی‌فود", false),
            MenuVendorToggle("fastfood", "فست‌فود شریف", true),
        )
    }
}
