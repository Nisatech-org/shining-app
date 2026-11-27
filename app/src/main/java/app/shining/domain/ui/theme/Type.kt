package app.shining.domain.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import app.shining.R

/**
 * The app's font, built from whichever folder app/build.gradle.kts picks: IRANSansX in the private
 * repo (licensed, never published), Vazirmatn in the public one.
 */
val AppFont = FontFamily(
    Font(R.font.app_font_light, FontWeight.Light),
    Font(R.font.app_font_regular, FontWeight.Normal),
    Font(R.font.app_font_medium, FontWeight.Medium),
    Font(R.font.app_font_demibold, FontWeight.SemiBold),
    Font(R.font.app_font_bold, FontWeight.Bold),
    Font(R.font.app_font_extrabold, FontWeight.ExtraBold),
    Font(R.font.app_font_black, FontWeight.Black),
)

private fun style(size: Int, weight: FontWeight, lineHeight: Int) = TextStyle(
    fontFamily = AppFont,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    textDirection = TextDirection.ContentOrRtl,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

@Immutable
data class ShiningTypography(
    /** Big numbers, like the credit balance. */
    val display: TextStyle = style(40, FontWeight.Medium, 52),
    /** Screen titles: large and light, like a magazine heading. */
    val headline: TextStyle = style(30, FontWeight.Medium, 42),
    /** Card titles. */
    val title: TextStyle = style(18, FontWeight.SemiBold, 28),
    val subtitle: TextStyle = style(15, FontWeight.SemiBold, 24),
    val body: TextStyle = style(14, FontWeight.Normal, 23),
    val bodyStrong: TextStyle = style(14, FontWeight.Medium, 23),
    val label: TextStyle = style(13, FontWeight.Medium, 20),
    val caption: TextStyle = style(12, FontWeight.Normal, 18),
    val button: TextStyle = style(14, FontWeight.SemiBold, 20),
    /** Big headings inside a hero card ("سلام، علی!"). */
    val hero: TextStyle = style(28, FontWeight.Medium, 40),
)
