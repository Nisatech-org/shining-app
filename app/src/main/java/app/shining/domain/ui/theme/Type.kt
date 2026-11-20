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

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_light, FontWeight.Light),
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_extrabold, FontWeight.ExtraBold),
    Font(R.font.vazirmatn_black, FontWeight.Black),
)

private fun style(size: Int, weight: FontWeight, lineHeight: Int) = TextStyle(
    fontFamily = Vazirmatn,
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
    val display: TextStyle = style(30, FontWeight.ExtraBold, 40),
    /** Screen titles. */
    val headline: TextStyle = style(22, FontWeight.Bold, 32),
    /** Card titles. */
    val title: TextStyle = style(17, FontWeight.Bold, 26),
    val subtitle: TextStyle = style(15, FontWeight.SemiBold, 24),
    val body: TextStyle = style(14, FontWeight.Normal, 23),
    val bodyStrong: TextStyle = style(14, FontWeight.Medium, 23),
    val label: TextStyle = style(13, FontWeight.Medium, 20),
    val caption: TextStyle = style(12, FontWeight.Normal, 18),
    val button: TextStyle = style(14, FontWeight.SemiBold, 20),
)
