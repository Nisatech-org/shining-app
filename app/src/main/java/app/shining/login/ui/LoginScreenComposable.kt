package app.shining.login.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import app.shining.R
import app.shining.domain.model.LoadableData
import app.shining.domain.util.ShiningRoutes
import app.shining.domain.util.navigateClearingStack
import app.shining.login.domain.model.LoginMode
import app.shining.login.ui.component.CaptchaUIModel
import app.shining.login.ui.component.LoginScreen
import app.shining.login.ui.model.LoginScreenUIModel
import app.shining.login.ui.viewmodel.LoginScreenViewModel

@Composable
fun LoginScreenComposable(
    navController: NavController,
    viewModel: LoginScreenViewModel,
    finish: () -> Unit,
) {
    val isLoading = viewModel.loginToken.value is LoadableData.Loading

    LoginScreen(
        data = LoginScreenUIModel(
            username = viewModel.username.value,
            password = viewModel.password.value,
        ),
        titleRes = when (viewModel.mode) {
            LoginMode.Initial -> R.string.login_title
            LoginMode.Add -> R.string.login_title_add_account
            LoginMode.Reauth -> R.string.login_title_reauth
        },
        isUsernameEditable = viewModel.isUsernameEditable,
        usernameError = viewModel.usernameError,
        onUserNameChange = { username -> viewModel.onUserNameChange(username) },
        onPasswordChange = { password -> viewModel.onPasswordChange(password) },
        loginStatus = viewModel.informationBoxData.value,
        onLoginClick = { viewModel.onLoginClick() },
        isLoading = isLoading,
        captcha = CaptchaUIModel(
            imageBase64 = (viewModel.captcha.value as? LoadableData.Loaded)?.data?.imageBase64,
            loading = viewModel.captcha.value is LoadableData.Loading,
            error = (viewModel.captcha.value as? LoadableData.Failed)?.message,
            text = viewModel.captchaText.value,
        ),
        onCaptchaChange = { viewModel.onCaptchaChange(it) },
        onCaptchaRefresh = { viewModel.loadCaptcha() },
    )

    LaunchedEffect(true) {
        viewModel.onLaunch()
    }

    LaunchedEffect(viewModel) {
        viewModel.navHome.setNavigateAction {
            navController.navigateClearingStack(ShiningRoutes.ReservationInfoScreen.route)
        }
    }

    BackHandler {
        // Adding an account or logging in again from Profile returns there; a login that
        // started the app has nothing behind it.
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            finish()
        }
    }
}
