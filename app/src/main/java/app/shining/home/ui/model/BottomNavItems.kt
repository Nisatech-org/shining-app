package app.shining.home.ui.model

import app.shining.R
import app.shining.domain.util.ShiningRoutes

sealed class BottomNavItems(
    val name: String,
    /** Emoji drawable shown on the tab. */
    val emoji: Int,
    val route: String,
    val badgeCount: Int = 0
) {
    data object Reserve :
        BottomNavItems("رزرو", R.drawable.emoji_plate, ShiningRoutes.ReservableScreen.route)

    data object ReserveInfo : BottomNavItems(
        "خانه",
        R.drawable.emoji_home,
        ShiningRoutes.ReservationInfoScreen.route
    )

    data object DailySell :
        BottomNavItems("روزفروش", R.drawable.emoji_cart, ShiningRoutes.DailySaleScreen.route)

    data object Profile :
        BottomNavItems("پروفایل", R.drawable.emoji_profile, ShiningRoutes.ProfileScreen.route)
}
