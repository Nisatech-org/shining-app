package app.shining.reserveinfo.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.domain.model.LoadableData
import app.shining.domain.ui.component.BottomBarSpace
import app.shining.domain.ui.component.EmptyState
import app.shining.domain.ui.component.ErrorState
import app.shining.domain.ui.component.LoadingState
import app.shining.domain.ui.component.SectionHeader
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningButtonSize
import app.shining.domain.ui.component.ShiningButtonStyle
import app.shining.domain.ui.component.ShiningCard
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.ShiningRoutes
import app.shining.market.ui.SellCardState
import app.shining.market.ui.SellMealCard
import app.shining.push.MenuStatusRow
import app.shining.reserveinfo.ui.fixture.reserveInfoScreenUIModelFixture
import app.shining.reserveinfo.ui.model.ReservationInfoScreenUIModel

/** The home screen: greeting and this week's reservations. */
@Composable
fun ReservationInfoScreen(
    name: String?,
    accountInitial: String,
    reservations: LoadableData<ReservationInfoScreenUIModel?>,
    /** Shown above the list when it is the copy saved on the device. */
    savedNotice: String?,
    onSettingsClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onAvatarLongClick: () -> Unit,
    onRefresh: () -> Unit,
    onNavigate: (String) -> Unit,
    onGetForgetCodeClick: (Int, () -> Unit) -> Unit,
    /** Next week's menus per vendor; null hides the card (the server was never reached). */
    menuStatus: List<MenuStatusRow>? = null,
    /** "Want to sell this meal?": the next meal the user could sell in the market, if any. */
    sellCard: SellCardState? = null,
    onSellClick: (String) -> Unit = {},
) {
    val loaded = (reservations as? LoadableData.Loaded)?.data
    val days = loaded?.reservationInfoCardUIModelList.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = BottomBarSpace),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            HomeHeader(
                name = name,
                initial = accountInitial,
                onSettingsClick = onSettingsClick,
                onAvatarClick = onAvatarClick,
                onAvatarLongClick = onAvatarLongClick,
            )
        }

        if (sellCard != null) {
            item(key = "sellCard") {
                SellMealCard(
                    state = sellCard,
                    onSellClick = { onSellClick(sellCard.meal.key) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (menuStatus != null) {
            item(key = "menuStatus") {
                MenuStatusCard(
                    rows = menuStatus,
                    onClick = { onNavigate(ShiningRoutes.ReservableScreen.route) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        item(key = "reservationsTitle") {
            SectionHeader(
                title = stringResource(R.string.reservation_info_screen_title),
                modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp),
                action = {
                    ShiningButton(
                        text = stringResource(R.string.refresh),
                        icon = R.drawable.ic_refresh,
                        style = ShiningButtonStyle.Ghost,
                        size = ShiningButtonSize.Small,
                        onClick = onRefresh,
                    )
                },
            )
        }

        if (savedNotice != null && reservations is LoadableData.Loaded) {
            item(key = "savedNotice") {
                SavedCopyNotice(
                    message = savedNotice,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        when (reservations) {
            is LoadableData.Loading,
            is LoadableData.NotLoaded -> item(key = "loading") {
                LoadingState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            }

            is LoadableData.Failed -> item(key = "failed") {
                ErrorState(
                    message = reservations.message,
                    onRetry = onRefresh,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is LoadableData.Loaded -> if (days.isEmpty()) {
                item(key = "empty") {
                    ShiningCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        EmptyState(
                            emoji = R.drawable.emoji_plate,
                            title = stringResource(R.string.reservation_info_screen_no_data),
                            subtitle = stringResource(R.string.reservation_info_screen_no_data_hint),
                            modifier = Modifier.fillMaxWidth(),
                            action = {
                                ShiningButton(
                                    text = stringResource(R.string.order_order_food_detail_reserve),
                                    icon = R.drawable.ic_add,
                                    onClick = { onNavigate(ShiningRoutes.ReservableScreen.route) },
                                )
                            },
                        )
                    }
                }
            } else {
                items(days, key = { it.farsiDate }) { day ->
                    ReservationInfoCard(
                        data = day,
                        onGetForgetCodeClick = onGetForgetCodeClick,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun ReservationInfoScreenPreview() {
    ShiningTheme {
        ReservationInfoScreen(
            name = "علی",
            accountInitial = "ع",
            reservations = LoadableData.Loaded(reserveInfoScreenUIModelFixture),
            savedNotice = "به سماد وصل نشدیم. این برنامه‌ی ذخیره‌شده‌ی ۳ ساعت پیش هست.",
            onSettingsClick = {},
            onAvatarClick = {},
            onAvatarLongClick = {},
            onRefresh = {},
            onNavigate = {},
            onGetForgetCodeClick = { _, _ -> },
        )
    }
}
