package app.shining.splash.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import app.shining.domain.util.ShiningRoutes
import app.shining.domain.util.navigateClearingStack
import app.shining.login.domain.model.LoginMode
import app.shining.splash.ui.component.SplashScreen
import app.shining.splash.ui.viewmodel.SplashScreenViewModel

@Composable
fun SplashScreenComposable(
    viewModel: SplashScreenViewModel,
    navController: NavController,
    finish: () -> Unit,
) {
    val context = LocalContext.current
    val networkErrorState = remember(
        viewModel.showNetworkError.value
    ) {
        viewModel.showNetworkError.value
    }
    SplashScreen(
        shouldShowNetworkError = networkErrorState,
        onReload = { viewModel.onReload(context) },
    )

    BackHandler {
        finish()
    }

    LaunchedEffect(true) {
        viewModel.navLogin.setNavigateAction {
            navController.navigateClearingStack(ShiningRoutes.login(LoginMode.Initial))
        }
        viewModel.navReauth.setNavigateAction {
            navController.navigateClearingStack(
                ShiningRoutes.login(LoginMode.Reauth, viewModel.reauthUsername)
            )
        }
        viewModel.navMarket.setNavigateAction {
            navController.navigateClearingStack(ShiningRoutes.MarketScreen.route)
        }
        viewModel.navReserveInfo.setNavigateAction {
            navController.navigateClearingStack(ShiningRoutes.ReservationInfoScreen.route)
        }

        viewModel.onLaunch(context)
    }


}