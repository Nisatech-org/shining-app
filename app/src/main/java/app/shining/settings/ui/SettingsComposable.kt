package app.shining.settings.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import app.shining.R
import app.shining.domain.ui.component.ScreenHeader
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.ShiningRoutes
import app.shining.domain.util.openUrl
import app.shining.settings.ui.component.SettingsScreen
import app.shining.settings.ui.component.body.SettingsBottomSheet
import app.shining.settings.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsComposable(
    viewModel: SettingsViewModel,
    navController: NavController,
) {
    val showBottomSheet = remember { mutableStateOf(false) }
    val versionTaps = remember { mutableStateOf(0) }
    val lastVersionTap = remember { mutableStateOf(0L) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ShiningTheme.colors.background)
    ) {
        ScreenHeader(
            title = stringResource(R.string.settings_screen_title),
            onBackClick = { viewModel.onBackClick() },
        )
        SettingsScreen(
            notificationsEnabled = viewModel.notificationsEnabled.value,
            onNotificationToggleChange = { viewModel.onNotificationToggleChange(it) },
            menuVendors = viewModel.menuVendors.value,
            onMenuVendorToggle = { key, enabled -> viewModel.onMenuVendorToggle(key, enabled) },
            onAboutClick = { viewModel.onAboutUsClick() },
            onSourceClick = { viewModel.onRedirectToGithub { context.openUrl(it) } },
            onReportIssueClick = { viewModel.onReportIssueClick { context.openUrl(it) } },
            onVersionTap = {
                val now = System.currentTimeMillis()
                versionTaps.value = if (now - lastVersionTap.value < 1500) versionTaps.value + 1 else 1
                lastVersionTap.value = now
                if (versionTaps.value >= 5) {
                    versionTaps.value = 0
                    navController.navigate(ShiningRoutes.DebugScreen.route)
                }
            },
            onVersionLongPress = {
                val on = viewModel.onToggleAdminAlerts()
                Toast.makeText(
                    context,
                    if (on) R.string.settings_admin_alerts_on else R.string.settings_admin_alerts_off,
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }

    if (showBottomSheet.value) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { showBottomSheet.value = false },
            containerColor = ShiningTheme.colors.surface,
            shape = ShiningTheme.shapes.sheet,
        ) {
            // The sheet is its own window and loses the theme's right-to-left layout.
            ShiningTheme {
                SettingsBottomSheet(
                    onReportIssueClick = { viewModel.onReportIssueClick { context.openUrl(it) } },
                    onGithubRedirectClick = { viewModel.onRedirectToGithub { context.openUrl(it) } },
                )
            }
        }
    }

    LaunchedEffect(true) {
        viewModel.onLaunch(ownSelfsName = context.getString(R.string.settings_menu_alerts_own))
    }

    LaunchedEffect(viewModel) {
        viewModel.navBack.setNavigateAction {
            navController.popBackStack()
        }

        viewModel.navAboutUs.setNavigateAction {
            showBottomSheet.value = true
        }
    }
}
