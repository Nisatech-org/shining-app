package app.shining.order.ui

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
import app.shining.order.ui.component.OrderScreen
import app.shining.order.ui.viewmodel.OrderScreenViewModel

@Composable
fun OrderScreenComposable(
    viewModel: OrderScreenViewModel,
    navController: NavController,
) {
    val orderScreenUIModel = viewModel.orderScreenUIModel.value
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ShiningTheme.colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = stringResource(R.string.order_screen_title_bar),
                subtitle = stringResource(R.string.order_screen_subtitle),
            ) {
                ShiningButton(
                    text = stringResource(R.string.increase_credit_button_label),
                    icon = R.drawable.ic_add,
                    style = ShiningButtonStyle.Secondary,
                    size = ShiningButtonSize.Small,
                    onClick = { viewModel.onIncreaseCreditClick { url -> context.openUrl(url) } },
                )
            }

            when (orderScreenUIModel) {
                is LoadableData.Failed -> ErrorState(
                    message = orderScreenUIModel.message,
                    onRetry = { viewModel.onRefresh() },
                    modifier = Modifier.fillMaxSize(),
                )

                is LoadableData.Loaded -> OrderScreen(
                    data = orderScreenUIModel.data,
                    pendingFoods = viewModel.pendingFoods.value,
                    onReserveFoodClick = { viewModel.onOrderFoodClick(it) },
                    initialDay = viewModel.reserveTarget.value?.day,
                    newSelfIds = viewModel.reserveTarget.value?.highlightSelfIds.orEmpty(),
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

    LaunchedEffect(orderScreenUIModel) {
        viewModel.navBack.setNavigateAction {
            navController.popBackStack()
        }
    }
}
