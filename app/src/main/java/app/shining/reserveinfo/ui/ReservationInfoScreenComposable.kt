package app.shining.reserveinfo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import app.shining.market.ui.MarketWidgetViewModel
import app.shining.push.MenuStatusViewModel
import app.shining.push.ReserveDeepLink
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import app.shining.account.ui.viewmodel.AccountsViewModel
import app.shining.domain.model.LoadableData
import app.shining.domain.ui.component.MessageBanner
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.ShiningRoutes
import app.shining.profile.ui.viewmodel.ProfileViewModel
import app.shining.reserveinfo.ui.component.ReservationInfoScreen
import app.shining.reserveinfo.ui.viewmodel.ReservationInfoViewModel

@Composable
fun ReservationInfoScreenComposable(
    viewModel: ReservationInfoViewModel,
    profileViewModel: ProfileViewModel,
    accountsViewModel: AccountsViewModel,
    menuStatusViewModel: MenuStatusViewModel,
    marketWidgetViewModel: MarketWidgetViewModel,
    navController: NavController,
    finish: () -> Unit,
) {
    val reservationInfoUIModel = viewModel.reservationInfoUIModel.value
    val profile = profileViewModel.profileScreenUIModel.value
    val activeAccount = accountsViewModel.accounts.value.firstOrNull { it.isActive }

    val name = (profile as? LoadableData.Loaded)?.data?.firstName?.takeIf { it.isNotBlank() }
        ?: activeAccount?.title?.substringBefore(' ')

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ShiningTheme.colors.background)
    ) {
        ReservationInfoScreen(
            name = name,
            accountInitial = activeAccount?.initial ?: "؟",
            reservations = reservationInfoUIModel,
            savedNotice = viewModel.savedNotice.value,
            sessionEnded = viewModel.sessionEnded.value,
            // The login screen in Reauth mode: saved password filled in, only the captcha to type.
            onLoginAgain = { activeAccount?.username?.let { accountsViewModel.onLoginAgainClick(it) } },
            onSettingsClick = { viewModel.onSettingsClick() },
            onAvatarClick = {
                navController.navigate(ShiningRoutes.ProfileScreen.route) { launchSingleTop = true }
            },
            onAvatarLongClick = { accountsViewModel.onProfileLongPress() },
            onRefresh = {
                viewModel.onRefresh()
                profileViewModel.onRefresh()
                menuStatusViewModel.onRefresh()
                marketWidgetViewModel.onLaunch()
            },
            onNavigate = { route ->
                navController.navigate(route) {
                    popUpTo(ShiningRoutes.ReservationInfoScreen.route)
                    launchSingleTop = true
                }
            },
            onGetForgetCodeClick = { id, onFinish -> viewModel.onGetForgetCode(id, onFinish) },
            menuStatus = menuStatusViewModel.rows.value,
            sellCard = marketWidgetViewModel.card.value,
            onSellClick = { key -> navController.navigate(ShiningRoutes.marketMeal(key)) },
        )

        MessageBanner(
            visible = viewModel.showMessage.value || profileViewModel.showMessage.value,
            data = if (profileViewModel.showMessage.value) {
                profileViewModel.informationBoxUIModel.value
            } else {
                viewModel.informationBoxUIModel.value
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    BackHandler {
        viewModel.onBackClick()
    }

    // The sell card follows the reservations: once they load (or reload), it picks the next meal again.
    LaunchedEffect(reservationInfoUIModel) {
        marketWidgetViewModel.onLaunch()
    }

    LaunchedEffect(true) {
        viewModel.onLaunch()
        if (profile !is LoadableData.Loaded) profileViewModel.onLaunch()
        menuStatusViewModel.onLaunch()
    }

    // A tapped menu notification opens Reserve once the user is in; Reserve takes the target.
    val reserveTarget by ReserveDeepLink.target.collectAsState()
    LaunchedEffect(reserveTarget) {
        if (reserveTarget != null) {
            navController.navigate(ShiningRoutes.ReservableScreen.route) {
                popUpTo(ShiningRoutes.ReservationInfoScreen.route)
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(reservationInfoUIModel) {
        viewModel.navBack.setNavigateAction {
            finish()
        }

        viewModel.navSettings.setNavigateAction {
            navController.navigate(ShiningRoutes.SettingsScreen.route)
        }
    }
}
