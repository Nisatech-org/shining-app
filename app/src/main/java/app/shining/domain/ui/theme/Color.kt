package app.shining.domain.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The main color of the app: the current tab, highlights, forget codes. Always with ink on it. */
val ShiningLime = Color(0xFFC8E223)

@Immutable
data class ShiningColors(
    /** Screen background behind cards: a soft sage. */
    val background: Color,
    /** Cards, sheets, fields. */
    val surface: Color,
    /** Fills inside a card: tracks, inactive chips, inner rows, icon circles. */
    val surfaceMuted: Color,
    val border: Color,
    /** Main text, icons and the primary button. */
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    /** Text and icons on [ink]. */
    val onInk: Color,
    /** The black hero cards (a trade, the sell card). A raised dark card in dark mode. */
    val inverse: Color,
    val onInverse: Color,
    val onInverseSecondary: Color,
    /** Grey-green tiles for quieter blocks, like "talk to a human". */
    val sage: Color,
    val onSage: Color,
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
    background = Color(0xFFD3D9CF),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFEFF1EC),
    border = Color(0xFFE4E7E0),
    ink = Color(0xFF0F110E),
    inkSecondary = Color(0xFF656B62),
    inkTertiary = Color(0xFFA0A69C),
    onInk = Color(0xFFFFFFFF),
    inverse = Color(0xFF0F110E),
    onInverse = Color(0xFFFFFFFF),
    onInverseSecondary = Color(0xFF9EA59A),
    sage = Color(0xFF959E91),
    onSage = Color(0xFFFFFFFF),
    brand = ShiningLime,
    brandSoft = Color(0xFFEEF5C6),
    onBrand = Color(0xFF0F110E),
    positive = Color(0xFF586B00),
    positiveSoft = Color(0xFFEEF5C6),
    negative = Color(0xFFD9443F),
    negativeSoft = Color(0xFFFBE5E2),
    warning = Color(0xFFA86A00),
    warningSoft = Color(0xFFFBEFD2),
    isDark = false,
)

val DarkShiningColors = ShiningColors(
    background = Color(0xFF0C0F0D),
    surface = Color(0xFF181C19),
    surfaceMuted = Color(0xFF232823),
    border = Color(0xFF2A302B),
    ink = Color(0xFFF1F4EE),
    inkSecondary = Color(0xFFA2A99F),
    inkTertiary = Color(0xFF6C7369),
    onInk = Color(0xFF0C0F0D),
    inverse = Color(0xFF232823),
    onInverse = Color(0xFFF1F4EE),
    onInverseSecondary = Color(0xFF8E968B),
    sage = Color(0xFF3A423B),
    onSage = Color(0xFFE6EBE2),
    brand = ShiningLime,
    brandSoft = Color(0xFF2A310D),
    onBrand = Color(0xFF0C0F0D),
    positive = Color(0xFFC8E223),
    positiveSoft = Color(0xFF242B0B),
    negative = Color(0xFFFF6B63),
    negativeSoft = Color(0xFF3A1715),
    warning = Color(0xFFF2B54A),
    warningSoft = Color(0xFF382A0E),
    isDark = true,
)
