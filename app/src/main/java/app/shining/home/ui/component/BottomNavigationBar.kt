package app.shining.home.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
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
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.domain.util.ShiningRoutes
import app.shining.home.ui.model.BottomNavItems

private val BOTTOM_NAV_ITEMS = listOf(
    BottomNavItems.ReserveInfo,
    BottomNavItems.Reserve,
    BottomNavItems.Market,
    BottomNavItems.Profile,
)

/** Routes that show the bar. Login and splash don't. */
private val ROUTES_WITH_BAR = setOf(
    ShiningRoutes.ReservationInfoScreen.route,
    ShiningRoutes.MarketScreen.route,
    ShiningRoutes.ProfileScreen.route,
    ShiningRoutes.ReservableScreen.route,
    ShiningRoutes.SettingsScreen.route,
)

private val ITEM_SIZE = 48.dp
private val BAR_PADDING = 6.dp

/** The bar is black in both themes, with white on it. */
private val BarColor = Color(0xFF0B0B0B)
private val BarItemColor = Color(0xFF2E2E2E)
private val BarContent = Color(0xFFFFFFFF)
private val BarActiveContent = Color(0xFF0B0B0B)

/**
 * A floating black pill of round tabs, centered. The current tab grows into a white pill with
 * its icon and name; the others are dark circles. Long-pressing Profile opens the account switcher.
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
    val shape = ShiningTheme.shapes.pill
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to background.copy(alpha = 0.85f),
                    1f to background,
                )
            )
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .shadow(18.dp, shape, spotColor = Color.Black.copy(alpha = 0.45f))
                .clip(shape)
                .background(BarColor)
                .padding(BAR_PADDING),
            horizontalArrangement = Arrangement.spacedBy(BAR_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BOTTOM_NAV_ITEMS.forEach { item ->
                // Sub-screens light up the tab they belong to.
                val isSelected = when (currentRoute) {
                    ShiningRoutes.SettingsScreen.route -> item == BottomNavItems.Profile
                    else -> currentRoute == item.route
                }
                NavItem(
                    icon = item.icon,
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
private fun NavItem(
    @DrawableRes icon: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val haptic = LocalHapticFeedback.current
    val container by animateColorAsState(
        if (isSelected) BarContent else BarItemColor,
        label = "navContainer",
    )
    val content by animateColorAsState(
        if (isSelected) BarActiveContent else BarContent,
        label = "navContent",
    )
    val shape = ShiningTheme.shapes.pill

    Row(
        modifier = Modifier
            .animateContentSize()
            .then(if (isSelected) Modifier.widthIn(min = ITEM_SIZE) else Modifier.width(ITEM_SIZE))
            .height(ITEM_SIZE)
            .clip(shape)
            .background(container)
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
            .padding(horizontal = if (isSelected) 18.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(22.dp),
        )
        if (isSelected) {
            Text(
                text = label,
                style = ShiningTheme.typography.button,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
