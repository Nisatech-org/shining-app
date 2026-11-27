package app.shining.home.ui.model

import app.shining.R
import app.shining.domain.util.ShiningRoutes

sealed class BottomNavItems(
    val name: String,
    /** Emoji drawable for the tab, where a picture fits better than a line icon. */
    val emoji: Int,
    val route: String,
    /** Line icon shown in the navigation bar. */
    val icon: Int,
    val badgeCount: Int = 0
) {
    data object Reserve :
        BottomNavItems("رزرو", R.drawable.emoji_plate, ShiningRoutes.ReservableScreen.route, R.drawable.ic_restaurant)

    data object ReserveInfo : BottomNavItems(
        "خانه",
        R.drawable.emoji_home,
        ShiningRoutes.ReservationInfoScreen.route,
        R.drawable.ic_home,
    )

    data object Market :
        BottomNavItems("بازار", R.drawable.emoji_money_bag, ShiningRoutes.MarketScreen.route, R.drawable.ic_money_bag)

    data object Profile :
        BottomNavItems("پروفایل", R.drawable.emoji_profile, ShiningRoutes.ProfileScreen.route, R.drawable.ic_profile)
}
