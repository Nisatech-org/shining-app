package app.shining.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.shining.R
import app.shining.account.ui.model.AccountRowUIModel
import app.shining.domain.ui.model.ShiningInformationBoxUIModel
import app.shining.profile.ui.model.ProfileScreenUIModel
import kotlinx.collections.immutable.ImmutableList
import app.shining.account.ui.component.AccountList
import app.shining.account.ui.component.AccountSwitchStatus
import app.shining.account.ui.viewmodel.AccountsViewModel
import app.shining.domain.model.LoadableData
import app.shining.domain.ui.component.BottomBarSpace
import app.shining.domain.ui.component.CircleIconButton
import app.shining.domain.ui.component.ErrorState
import app.shining.domain.ui.component.LoadingState
import app.shining.domain.ui.component.MessageBanner
import app.shining.domain.ui.component.PillTone
import app.shining.domain.ui.component.ScreenHeader
import app.shining.domain.ui.component.SectionHeader
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningButtonSize
import app.shining.domain.ui.component.ShiningButtonStyle
import app.shining.domain.ui.component.ShiningCard
import app.shining.domain.ui.component.ShiningDivider
import app.shining.domain.ui.component.StatusPill
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.ShiningRoutes
import app.shining.domain.util.openUrl
import app.shining.domain.util.toLocalNumber
import app.shining.profile.ui.component.CreditHeroCard
import app.shining.profile.ui.component.CreditTransferRow
import app.shining.profile.ui.viewmodel.ProfileViewModel

private const val COLLAPSED_TRANSACTIONS = 6

@Composable
fun ProfileComposable(
    viewModel: ProfileViewModel,
    accountsViewModel: AccountsViewModel,
    navController: NavController,
) {
    val reservesInFlight by accountsViewModel.reservesInFlight.collectAsState()
    val profileUIModel = viewModel.profileScreenUIModel.value
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ShiningTheme.colors.background)
    ) {
        ProfileContent(
            profile = profileUIModel,
            accounts = accountsViewModel.accounts.value,
            canAddAccount = accountsViewModel.canAddAccount.value,
            switchingTo = accountsViewModel.switchingTo.value,
            waitingForReserve = reservesInFlight > 0,
            accountStatus = accountsViewModel.status.value,
            onSettingsClick = { navController.navigate(ShiningRoutes.SettingsScreen.route) },
            onIncreaseCreditClick = { viewModel.onIncreaseCreditClick { url -> context.openUrl(url) } },
            onRefresh = { viewModel.onRefresh() },
            onLogoutClick = { accountsViewModel.onRemoveActiveClick() },
            onAccountClick = { accountsViewModel.onAccountClick(it) },
            onLoginAgainClick = { accountsViewModel.onLoginAgainClick(it) },
            onAddAccountClick = { accountsViewModel.onAddAccountClick() },
            onRemoveClick = { accountsViewModel.onRemoveClick(it) },
            onMove = { from, to -> accountsViewModel.onMove(from, to) },
        )

        MessageBanner(
            visible = viewModel.showMessage.value,
            data = viewModel.informationBoxUIModel.value,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    LaunchedEffect(profileUIModel) {
        viewModel.navBack.setNavigateAction {
            navController.popBackStack()
        }
    }

    LaunchedEffect(true) {
        viewModel.onLaunch()
    }
}

@Composable
fun ProfileContent(
    profile: LoadableData<ProfileScreenUIModel>,
    accounts: ImmutableList<AccountRowUIModel>,
    canAddAccount: Boolean,
    switchingTo: AccountRowUIModel?,
    waitingForReserve: Boolean,
    accountStatus: ShiningInformationBoxUIModel?,
    onSettingsClick: () -> Unit,
    onIncreaseCreditClick: () -> Unit,
    onRefresh: () -> Unit,
    onLogoutClick: () -> Unit,
    onAccountClick: (String) -> Unit,
    onLoginAgainClick: (String) -> Unit,
    onAddAccountClick: () -> Unit,
    onRemoveClick: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var showAllTransactions by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = BottomBarSpace),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            ScreenHeader(
                title = stringResource(R.string.nurture_profile_titlebar_name),
                subtitle = stringResource(R.string.profile_subtitle),
            ) {
                CircleIconButton(
                    icon = R.drawable.ic_settings,
                    onClick = { onSettingsClick() },
                    contentDescription = stringResource(R.string.settings_screen_title),
                )
            }
        }

        item(key = "card") {
            when (profile) {
                is LoadableData.Failed -> ShiningCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    ErrorState(
                        message = profile.message,
                        onRetry = { onRefresh() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                else -> CreditHeroCard(
                    data = (profile as? LoadableData.Loaded)?.data,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        item(key = "actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShiningButton(
                    text = stringResource(R.string.increase_credit_button_label),
                    icon = R.drawable.ic_add,
                    onClick = { onIncreaseCreditClick() },
                    modifier = Modifier.weight(1f),
                )
                ShiningButton(
                    text = stringResource(R.string.refresh),
                    icon = R.drawable.ic_refresh,
                    style = ShiningButtonStyle.Secondary,
                    loading = profile is LoadableData.Loading,
                    onClick = { onRefresh() },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item(key = "accounts") {
            ShiningCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(
                    title = stringResource(R.string.accounts_title),
                    action = {
                        ShiningButton(
                            text = stringResource(R.string.settings_logout_button_label),
                            icon = R.drawable.ic_logout,
                            style = ShiningButtonStyle.Danger,
                            size = ShiningButtonSize.Small,
                            onClick = { onLogoutClick() },
                        )
                    },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                AccountSwitchStatus(
                    switchingTo = switchingTo,
                    waitingForReserve = waitingForReserve,
                    status = accountStatus,
                    contentColor = ShiningTheme.colors.inkSecondary,
                )
                AccountList(
                    accounts = accounts,
                    canAddAccount = canAddAccount,
                    contentColor = ShiningTheme.colors.ink,
                    rowBackground = ShiningTheme.colors.surfaceMuted,
                    onAccountClick = onAccountClick,
                    onLoginAgainClick = onLoginAgainClick,
                    onAddAccountClick = onAddAccountClick,
                    enabled = switchingTo == null,
                    onRemoveClick = onRemoveClick,
                    onMove = onMove,
                )
            }
        }

        val transfers = (profile as? LoadableData.Loaded)?.data?.creditTransfers.orEmpty()
        when {
            profile is LoadableData.Loading -> item(key = "loading") {
                LoadingState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                )
            }

            transfers.isNotEmpty() -> item(key = "transfers") {
                val shown = if (showAllTransactions) transfers else transfers.take(COLLAPSED_TRANSACTIONS)
                ShiningCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    SectionHeader(
                        title = stringResource(R.string.nurture_profile_row_item_transactions),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                        action = {
                            StatusPill(
                                text = transfers.size.toString().toLocalNumber(),
                                tone = PillTone.Neutral,
                            )
                        },
                    )
                    shown.forEachIndexed { index, transfer ->
                        if (index > 0) ShiningDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        CreditTransferRow(data = transfer)
                    }
                    if (transfers.size > COLLAPSED_TRANSACTIONS) {
                        ShiningDivider()
                        ShiningButton(
                            text = stringResource(
                                if (showAllTransactions) R.string.show_less else R.string.show_all
                            ),
                            icon = R.drawable.ic_chevron_down,
                            style = ShiningButtonStyle.Ghost,
                            size = ShiningButtonSize.Small,
                            onClick = { showAllTransactions = !showAllTransactions },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
