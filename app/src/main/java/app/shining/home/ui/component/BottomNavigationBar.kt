package app.shining.home.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import app.shining.domain.ui.component.Emoji
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.ShiningRoutes
import app.shining.home.ui.model.BottomNavItems

private val BOTTOM_NAV_ITEMS = listOf(
    BottomNavItems.ReserveInfo,
    BottomNavItems.Reserve,
    BottomNavItems.Market,
    BottomNavItems.DailySell,
    BottomNavItems.Profile,
)

/** Routes that show the bar. Login and splash don't. */
private val ROUTES_WITH_BAR = setOf(
    ShiningRoutes.ReservationInfoScreen.route,
    ShiningRoutes.DailySaleScreen.route,
    ShiningRoutes.MarketScreen.route,
    ShiningRoutes.ProfileScreen.route,
    ShiningRoutes.ReservableScreen.route,
    ShiningRoutes.SettingsScreen.route,
)

// Five tabs fit a 360 dp phone with room for the current tab's name.
private val ITEM_SIZE = 46.dp

/**
 * A floating bar of round buttons. The current tab grows into a black pill with its name.
 * Long-pressing Profile opens the account switcher.
 */
@Composable
fun BottomNavigationBar(
    navController: NavController,
    onProfileLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    /** A guest has only the market, so there's nothing to switch to. */
    hidden: Boolean = false,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    AnimatedVisibility(
        visible = !hidden && currentRoute in ROUTES_WITH_BAR,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        BottomNavigationBarContent(
            currentRoute = currentRoute,
            onItemClick = { item ->
                if (currentRoute != item.route) {
                    navController.navigate(item.route) {
                        popUpTo(ShiningRoutes.ReservationInfoScreen.route)
                        launchSingleTop = true
                    }
                }
            },
            onProfileLongPress = onProfileLongPress,
        )
    }
}

@Composable
fun BottomNavigationBarContent(
    currentRoute: String?,
    onItemClick: (BottomNavItems) -> Unit,
    onProfileLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = ShiningTheme.colors.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to background.copy(alpha = 0.92f),
                    1f to background,
                )
            )
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BOTTOM_NAV_ITEMS.forEach { item ->
                // Sub-screens light up the tab they belong to.
                val isSelected = when (currentRoute) {
                    ShiningRoutes.SettingsScreen.route -> item == BottomNavItems.Profile
                    else -> currentRoute == item.route
                }
                NavItem(
                    emoji = item.emoji,
                    label = item.name,
                    isSelected = isSelected,
                    onClick = { onItemClick(item) },
                    onLongClick = if (item == BottomNavItems.Profile) onProfileLongPress else null,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.NavItem(
    @DrawableRes emoji: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val colors = ShiningTheme.colors
    val haptic = LocalHapticFeedback.current
    val container by animateColorAsState(
        if (isSelected) colors.ink else colors.surface,
        label = "navContainer",
    )
    val content by animateColorAsState(
        if (isSelected) colors.onInk else colors.ink,
        label = "navContent",
    )
    val shape = ShiningTheme.shapes.pill

    Row(
        modifier = Modifier
            .then(if (isSelected) Modifier.weight(1f) else Modifier.width(ITEM_SIZE))
            .height(ITEM_SIZE)
            .shadow(if (isSelected) 10.dp else 4.dp, shape, spotColor = colors.ink.copy(alpha = 0.3f))
            .clip(shape)
            .background(container)
            .border(1.dp, if (isSelected) Color.Transparent else colors.border, shape)
            .semantics {
                selected = isSelected
                contentDescription = label
            }
            .combinedClickable(
                role = Role.Tab,
                onClick = onClick,
                onLongClick = onLongClick?.let {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                },
            )
            .padding(horizontal = if (isSelected) 6.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isSelected) Arrangement.Start else Arrangement.Center,
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(shape)
                    .background(colors.brand),
                contentAlignment = Alignment.Center,
            ) {
                Emoji(emoji = emoji, size = 22.dp)
            }
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = label,
                style = ShiningTheme.typography.label,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.size(12.dp))
        } else {
            Emoji(emoji = emoji, size = 24.dp)
        }
    }
}
