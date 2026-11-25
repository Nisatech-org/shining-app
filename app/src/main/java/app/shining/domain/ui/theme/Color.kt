package app.shining.domain.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The main color of the app: tabs, highlights, forget codes. */
val ShiningBlue = Color(0xFF2862FF)

@Immutable
data class ShiningColors(
    /** Screen background behind cards. */
    val background: Color,
    /** Cards, sheets, fields. */
    val surface: Color,
    /** Fills inside a card: tracks, inactive chips, inner rows. */
    val surfaceMuted: Color,
    val border: Color,
    /** Main text, icons and the primary button. */
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    /** Text and icons on [ink]. */
    val onInk: Color,
    val brand: Color,
    val brandSoft: Color,
    /** Text and icons on [brand]. */
    val onBrand: Color,
    val positive: Color,
    val positiveSoft: Color,
    val negative: Color,
    val negativeSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val isDark: Boolean,
)

val LightShiningColors = ShiningColors(
    background = Color(0xFFF3F3F5),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF5F5F7),
    border = Color(0xFFE8E8EC),
    ink = Color(0xFF121214),
    inkSecondary = Color(0xFF6B6B74),
    inkTertiary = Color(0xFFA1A1AA),
    onInk = Color(0xFFFFFFFF),
    brand = ShiningBlue,
    brandSoft = Color(0xFFE3EAFF),
    onBrand = Color(0xFFFFFFFF),
    positive = Color(0xFF16A05A),
    positiveSoft = Color(0xFFE5F6EC),
    negative = Color(0xFFE5484D),
    negativeSoft = Color(0xFFFDECEC),
    warning = Color(0xFFC77C00),
    warningSoft = Color(0xFFFFF2D9),
    isDark = false,
)

val DarkShiningColors = ShiningColors(
    background = Color(0xFF0D0D0F),
    surface = Color(0xFF18181B),
    surfaceMuted = Color(0xFF222226),
    border = Color(0xFF2B2B30),
    ink = Color(0xFFF4F4F5),
    inkSecondary = Color(0xFFA1A1AA),
    inkTertiary = Color(0xFF71717A),
    onInk = Color(0xFF0D0D0F),
    brand = ShiningBlue,
    brandSoft = Color(0xFF16224A),
    onBrand = Color(0xFFFFFFFF),
    positive = Color(0xFF3DD68C),
    positiveSoft = Color(0xFF12301F),
    negative = Color(0xFFFF6369),
    negativeSoft = Color(0xFF3A1517),
    warning = Color(0xFFF5B544),
    warningSoft = Color(0xFF3A2A0D),
    isDark = true,
)
