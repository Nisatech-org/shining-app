package app.shining.dailysell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import app.shining.R
import app.shining.dailysell.ui.component.DailySaleScreen
import app.shining.dailysell.ui.viewmodel.DailySellViewModel
import app.shining.domain.model.LoadableData
import app.shining.domain.ui.component.ErrorState
import app.shining.domain.ui.component.LoadingState
import app.shining.domain.ui.component.MessageBanner
import app.shining.domain.ui.component.ScreenHeader
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningButtonSize
import app.shining.domain.ui.component.ShiningButtonStyle
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.openUrl

@Composable
fun DailySellComposable(
    viewModel: DailySellViewModel,
    navController: NavController,
) {
    val dailySellScreenUIModel = viewModel.dailySaleScreenUIModel.value
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ShiningTheme.colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = stringResource(R.string.daily_sell_screen_title),
                subtitle = stringResource(R.string.daily_sell_screen_subtitle),
            ) {
                ShiningButton(
                    text = stringResource(R.string.increase_credit_button_label),
                    icon = R.drawable.ic_add,
                    style = ShiningButtonStyle.Secondary,
                    size = ShiningButtonSize.Small,
                    onClick = { viewModel.onIncreaseCreditClick { url -> context.openUrl(url) } },
                )
            }
            when (dailySellScreenUIModel) {
                is LoadableData.Failed -> ErrorState(
                    message = dailySellScreenUIModel.message,
                    onRetry = { viewModel.onRefresh() },
                    modifier = Modifier.fillMaxSize(),
                )

                is LoadableData.Loaded -> DailySaleScreen(
                    uiModel = dailySellScreenUIModel.data,
                    onGetForgetCodeClick = { id, onFinish -> viewModel.onGetForgetCode(id, onFinish) },
                    onOrderDailySaleClick = { id, onFinish -> viewModel.onOrderDailySale(id, onFinish) },
                )

                is LoadableData.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
                is LoadableData.NotLoaded -> Unit
            }
        }

        MessageBanner(
            visible = viewModel.showMessage.value,
            data = viewModel.informationBoxUIModel.value,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    LaunchedEffect(true) {
        viewModel.onLaunch()
    }

    LaunchedEffect(dailySellScreenUIModel) {
        viewModel.navBack.setNavigateAction {
            navController.popBackStack()
        }
    }
}
