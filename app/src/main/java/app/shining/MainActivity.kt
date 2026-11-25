package app.shining

import android.content.Intent
import android.os.Bundle
import app.shining.debug.DebugScreen
import app.shining.debug.DebugViewModel
import app.shining.push.MenuStatusViewModel
import app.shining.push.ReserveDeepLink
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.shining.account.ui.component.AccountSwitcherSheet
import app.shining.account.ui.component.RemoveAccountDialog
import app.shining.account.ui.model.AccountNavEvent
import app.shining.account.ui.viewmodel.AccountsViewModel
import app.shining.dailysell.ui.DailySellComposable
import app.shining.dailysell.ui.viewmodel.DailySellViewModel
import app.shining.domain.notification.createNotificationChannel
import app.shining.domain.notification.setAlarmsBasedOnPreference
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.LOGIN_ARG_MODE
import app.shining.domain.util.LOGIN_ARG_USERNAME
import app.shining.domain.util.MARKET_ARG_KEY
import app.shining.domain.util.MARKET_ARG_TRADE
import app.shining.market.push.MarketDeepLink
import app.shining.market.push.MarketTarget
import app.shining.market.ui.MarketComposable
import app.shining.market.ui.MarketViewModel
import app.shining.market.ui.MarketWidgetViewModel
import app.shining.market.ui.MealBoardComposable
import app.shining.market.ui.MealBoardViewModel
import app.shining.market.ui.TradeComposable
import app.shining.market.ui.TradeViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import app.shining.domain.util.ShiningRoutes
import app.shining.domain.util.navigateClearingStack
import app.shining.home.ui.component.BottomNavigationBar
import app.shining.login.domain.model.LoginMode
import app.shining.login.ui.LoginScreenComposable
import app.shining.login.ui.viewmodel.LoginScreenViewModel
import app.shining.order.ui.OrderScreenComposable
import app.shining.order.ui.viewmodel.OrderScreenViewModel
import app.shining.profile.ui.ProfileComposable
import app.shining.profile.ui.viewmodel.ProfileViewModel
import app.shining.reserveinfo.ui.ReservationInfoScreenComposable
import app.shining.reserveinfo.ui.viewmodel.ReservationInfoViewModel
import app.shining.settings.ui.SettingsComposable
import app.shining.settings.ui.viewmodel.SettingsViewModel
import app.shining.splash.ui.SplashScreenComposable
import app.shining.splash.ui.viewmodel.SplashScreenViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.core.parameter.parametersOf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A tapped menu notification says which day to open in Reserve; a market one, which trade.
        if (savedInstanceState == null) {
            ReserveDeepLink.receive(intent)
            MarketDeepLink.receive(intent)
        }

        setContent {
            ShiningTheme {
                val navController = rememberNavController()
                // Activity-scoped so Profile, Home and the long-press switcher share it.
                val accountsViewModel = getViewModel<AccountsViewModel>()
                val screenStoreOwner = accountsViewModel.screenViewModelStoreOwner.value
                AccountNavigation(accountsViewModel, navController)
                MarketNavigation(navController)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ShiningTheme.colors.background)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ShiningRoutes.SplashScreen.route,
                        enterTransition = { fadeIn(animationSpec = tween(250)) },
                        exitTransition = { fadeOut(animationSpec = tween(200)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(250)) },
                        popExitTransition = { fadeOut(animationSpec = tween(200)) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        composable(route = ShiningRoutes.SplashScreen.route) {
                            SplashScreenComposable(
                                viewModel = koinViewModel<SplashScreenViewModel>(),
                                navController = navController,
                                finish = { finish() },
                            )
                        }
                        composable(
                            route = ShiningRoutes.LoginScreen.route,
                            arguments = listOf(
                                navArgument(LOGIN_ARG_MODE) {
                                    type = NavType.StringType
                                    defaultValue = LoginMode.Initial.name
                                },
                                navArgument(LOGIN_ARG_USERNAME) {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                            ),
                        ) { entry ->
                            val mode = entry.arguments?.getString(LOGIN_ARG_MODE)
                                ?.let { runCatching { LoginMode.valueOf(it) }.getOrNull() }
                                ?: LoginMode.Initial
                            val username = entry.arguments?.getString(LOGIN_ARG_USERNAME)
                            val loginViewModel = koinViewModel<LoginScreenViewModel> {
                                parametersOf(mode, username)
                            }
                            LoginScreenComposable(
                                viewModel = loginViewModel,
                                navController = navController,
                                finish = { finish() },
                            )
                        }
                        composable(route = ShiningRoutes.ReservationInfoScreen.route) {
                            ReservationInfoScreenComposable(
                                viewModel = koinViewModel<ReservationInfoViewModel>(
                                    viewModelStoreOwner = screenStoreOwner
                                ),
                                profileViewModel = koinViewModel<ProfileViewModel>(
                                    viewModelStoreOwner = screenStoreOwner
                                ),
                                accountsViewModel = accountsViewModel,
                                menuStatusViewModel = koinViewModel<MenuStatusViewModel>(
                                    viewModelStoreOwner = screenStoreOwner
                                ),
                                marketWidgetViewModel = koinViewModel<MarketWidgetViewModel>(
                                    viewModelStoreOwner = screenStoreOwner
                                ),
                                navController = navController,
                                finish = { finish() },
                            )
                        }
                        composable(route = ShiningRoutes.ReservableScreen.route) {
                            OrderScreenComposable(
                                viewModel = koinViewModel<OrderScreenViewModel>(
                                    viewModelStoreOwner = screenStoreOwner
                                ),
                                navController = navController,
                            )
                        }
                        composable(route = ShiningRoutes.ProfileScreen.route) {
                            ProfileComposable(
                                viewModel = koinViewModel<ProfileViewModel>(
                                    viewModelStoreOwner = screenStoreOwner
                                ),
                                accountsViewModel = accountsViewModel,
                                navController = navController,
                            )
                        }
                        composable(route = ShiningRoutes.DailySaleScreen.route) {
                            DailySellComposable(
                                viewModel = koinViewModel<DailySellViewModel>(
                                    viewModelStoreOwner = screenStoreOwner
                                ),
                                navController = navController,
                            )
                        }
                        composable(route = ShiningRoutes.MarketScreen.route) {
                            MarketComposable(
                                viewModel = koinViewModel<MarketViewModel>(viewModelStoreOwner = screenStoreOwner),
                                navController = navController,
                            )
                        }
                        composable(
                            route = ShiningRoutes.MarketMealScreen.route,
                            arguments = listOf(navArgument(MARKET_ARG_KEY) { type = NavType.StringType }),
                        ) { entry ->
                            val key = entry.arguments?.getString(MARKET_ARG_KEY).orEmpty()
                            MealBoardComposable(
                                viewModel = koinViewModel<MealBoardViewModel>(key = "board-$key") { parametersOf(key) },
                                navController = navController,
                            )
                        }
                        composable(
                            route = ShiningRoutes.MarketTradeScreen.route,
                            arguments = listOf(navArgument(MARKET_ARG_TRADE) { type = NavType.LongType }),
                        ) { entry ->
                            val id = entry.arguments?.getLong(MARKET_ARG_TRADE) ?: 0L
                            TradeComposable(
                                viewModel = koinViewModel<TradeViewModel>(key = "trade-$id") { parametersOf(id) },
                                navController = navController,
                            )
                        }
                        composable(route = ShiningRoutes.DebugScreen.route) {
                            DebugScreen(viewModel = koinViewModel<DebugViewModel>(), navController = navController)
                        }
                        composable(route = ShiningRoutes.SettingsScreen.route) {
                            SettingsComposable(
                                viewModel = koinViewModel<SettingsViewModel>(
                                    viewModelStoreOwner = screenStoreOwner
                                ),
                                navController = navController,
                            )
                        }
                    }

                    BottomNavigationBar(
                        navController = navController,
                        onProfileLongPress = { accountsViewModel.onProfileLongPress() },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                AccountDialogs(accountsViewModel)
            }
        }

        createNotificationChannel(this)
        setAlarmsBasedOnPreference(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ReserveDeepLink.receive(intent)
        MarketDeepLink.receive(intent)
    }
}

/**
 * Opens the trade or board a tapped market notification points to, once the user is past the
 * splash and login screens (wherever they are in the app).
 */
@Composable
private fun MarketNavigation(navController: NavController) {
    val target by MarketDeepLink.target.collectAsState()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    LaunchedEffect(target, route) {
        if (target == null || route == null) return@LaunchedEffect
        if (route == ShiningRoutes.SplashScreen.route || route.startsWith("login")) return@LaunchedEffect
        when (val next = MarketDeepLink.take()) {
            is MarketTarget.Trade -> navController.navigate(ShiningRoutes.marketTrade(next.id)) { launchSingleTop = true }
            is MarketTarget.Meal -> navController.navigate(ShiningRoutes.marketMeal(next.key)) { launchSingleTop = true }
            null -> Unit
        }
    }
}

/** Follows the account view model's navigation requests (after a switch, add or removal). */
@Composable
private fun AccountNavigation(
    accountsViewModel: AccountsViewModel,
    navController: NavController,
) {
    LaunchedEffect(accountsViewModel) {
        accountsViewModel.navEvents.collect { event ->
            when (event) {
                AccountNavEvent.Home ->
                    navController.navigateClearingStack(ShiningRoutes.ReservationInfoScreen.route)

                is AccountNavEvent.Login -> {
                    val route = ShiningRoutes.login(event.mode, event.username)
                    if (event.clearStack) {
                        navController.navigateClearingStack(route)
                    } else {
                        navController.navigate(route)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDialogs(accountsViewModel: AccountsViewModel) {
    val reservesInFlight by accountsViewModel.reservesInFlight.collectAsState()

    if (accountsViewModel.showSwitcher.value) {
        ModalBottomSheet(
            onDismissRequest = { accountsViewModel.onSwitcherDismiss() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = ShiningTheme.colors.surface,
            shape = ShiningTheme.shapes.sheet,
        ) {
            // The sheet is its own window and loses the theme's right-to-left layout.
            ShiningTheme {
                AccountSwitcherSheet(
                    accounts = accountsViewModel.accounts.value,
                    canAddAccount = accountsViewModel.canAddAccount.value,
                    switchingTo = accountsViewModel.switchingTo.value,
                    waitingForReserve = reservesInFlight > 0,
                    status = accountsViewModel.status.value,
                    onAccountClick = { accountsViewModel.onAccountClick(it) },
                    onLoginAgainClick = { accountsViewModel.onLoginAgainClick(it) },
                    onAddAccountClick = { accountsViewModel.onAddAccountClick() },
                )
            }
        }
    }

    accountsViewModel.pendingRemoval.value?.let {
        RemoveAccountDialog(
            account = it,
            onConfirm = { accountsViewModel.onRemoveConfirm() },
            onDismiss = { accountsViewModel.onRemoveDismiss() },
        )
    }
}
