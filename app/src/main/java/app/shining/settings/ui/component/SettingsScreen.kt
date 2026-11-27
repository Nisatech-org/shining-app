package app.shining.settings.ui.component

import android.Manifest
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.domain.notification.setAlarmsBasedOnPreference
import app.shining.domain.ui.component.BottomBarSpace
import app.shining.domain.ui.component.EmojiTile
import app.shining.domain.ui.component.IconTile
import app.shining.domain.ui.component.ShiningCard
import app.shining.domain.ui.component.ShiningDivider
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.toLocalNumber
import app.shining.login.ui.component.ShiningLogo
import app.shining.push.MenuVendorToggle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    notificationsEnabled: Boolean,
    onNotificationToggleChange: (Boolean) -> Unit,
    menuVendors: List<MenuVendorToggle> = emptyList(),
    onMenuVendorToggle: (key: String, enabled: Boolean) -> Unit = { _, _ -> },
    onAboutClick: () -> Unit,
    onSourceClick: () -> Unit,
    onReportIssueClick: () -> Unit,
    /** Hidden: a long press on the version turns the server's admin alerts on or off. */
    onVersionLongPress: () -> Unit = {},
    /** Hidden: tapping the version (5 times) opens the debug screen. */
    onVersionTap: () -> Unit = {},
) {
    // Previews have no activity to ask for the permission from.
    val notificationPermission = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !LocalInspectionMode.current
    ) {
        rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = BottomBarSpace),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsGroup(title = stringResource(R.string.settings_group_notifications)) {
            SettingsRow(
                emoji = R.drawable.emoji_clock,
                title = stringResource(R.string.settings_notification_toggle_title),
                subtitle = stringResource(R.string.settings_notification_toggle_subtitle),
                trailing = {
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = {
                            if (it && notificationPermission?.status?.isGranted == false) {
                                notificationPermission.launchPermissionRequest()
                            }
                            onNotificationToggleChange(it)
                            setAlarmsBasedOnPreference(context)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ShiningTheme.colors.brand,
                            checkedTrackColor = ShiningTheme.colors.ink,
                            checkedBorderColor = ShiningTheme.colors.ink,
                            uncheckedThumbColor = ShiningTheme.colors.inkTertiary,
                            uncheckedTrackColor = ShiningTheme.colors.surfaceMuted,
                            uncheckedBorderColor = ShiningTheme.colors.border,
                        ),
                    )
                },
            )
        }

        if (menuVendors.isNotEmpty()) {
            SettingsGroup(title = stringResource(R.string.settings_group_menu_alerts)) {
                Text(
                    text = stringResource(R.string.settings_menu_alerts_hint),
                    style = ShiningTheme.typography.caption,
                    color = ShiningTheme.colors.inkSecondary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                )
                menuVendors.forEach { vendor ->
                    ShiningDivider()
                    SettingsRow(
                        emoji = R.drawable.emoji_plate,
                        title = vendor.name,
                        trailing = {
                            Switch(
                                checked = vendor.enabled,
                                onCheckedChange = {
                                    if (it && notificationPermission?.status?.isGranted == false) {
                                        notificationPermission.launchPermissionRequest()
                                    }
                                    onMenuVendorToggle(vendor.key, it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ShiningTheme.colors.brand,
                                    checkedTrackColor = ShiningTheme.colors.ink,
                                    checkedBorderColor = ShiningTheme.colors.ink,
                                    uncheckedThumbColor = ShiningTheme.colors.inkTertiary,
                                    uncheckedTrackColor = ShiningTheme.colors.surfaceMuted,
                                    uncheckedBorderColor = ShiningTheme.colors.border,
                                ),
                            )
                        },
                    )
                }
            }
        }

        SettingsGroup(title = stringResource(R.string.settings_group_about)) {
            SettingsRow(
                emoji = R.drawable.emoji_info,
                title = stringResource(R.string.about_us_title),
                onClick = onAboutClick,
            )
            ShiningDivider()
            SettingsRow(
                icon = R.drawable.ic_github,
                title = stringResource(R.string.settings_source_title),
                onClick = onSourceClick,
            )
            ShiningDivider()
            SettingsRow(
                emoji = R.drawable.emoji_bug,
                title = stringResource(R.string.about_us_send_feedback),
                onClick = onReportIssueClick,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShiningLogo(size = 36)
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.settings_version, version.toLocalNumber()),
                style = ShiningTheme.typography.caption,
                color = ShiningTheme.colors.inkTertiary,
                modifier = Modifier.combinedClickable(onClick = onVersionTap, onLongClick = onVersionLongPress),
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            style = ShiningTheme.typography.label,
            color = ShiningTheme.colors.inkSecondary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        ShiningCard(contentPadding = PaddingValues(0.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    @DrawableRes emoji: Int? = null,
    /** A line icon instead of an emoji, for a brand mark like GitHub's. */
    @DrawableRes icon: Int? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            icon != null -> IconTile(icon = icon, size = 40.dp, iconSize = 22.dp)
            emoji != null -> EmojiTile(emoji = emoji, size = 40.dp)
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = ShiningTheme.typography.subtitle, color = ShiningTheme.colors.ink)
            subtitle?.let {
                Text(
                    text = it,
                    style = ShiningTheme.typography.caption,
                    color = ShiningTheme.colors.inkSecondary,
                )
            }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = null,
                tint = ShiningTheme.colors.inkTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    ShiningTheme {
        SettingsScreen(
            notificationsEnabled = true,
            onNotificationToggleChange = {},
            onAboutClick = {},
            onSourceClick = {},
            onReportIssueClick = {},
        )
    }
}
