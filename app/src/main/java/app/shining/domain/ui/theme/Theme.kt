package app.shining.domain.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.accompanist.systemuicontroller.rememberSystemUiController

@Immutable
data class ShiningShapes(
    val card: Shape = RoundedCornerShape(22.dp),
    val tile: Shape = RoundedCornerShape(12.dp),
    val field: Shape = RoundedCornerShape(16.dp),
    val small: Shape = RoundedCornerShape(8.dp),
    val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val pill: Shape = CircleShape,
)

private val LocalShiningColors = staticCompositionLocalOf { LightShiningColors }
private val LocalShiningTypography = staticCompositionLocalOf { ShiningTypography() }
private val LocalShiningShapes = staticCompositionLocalOf { ShiningShapes() }

/**
 * The app theme: light or dark following the system, right-to-left, Vazirmatn everywhere.
 * Material 3 components (switches, dialogs, sheets, text selection) get a matching scheme.
 */
@Composable
fun ShiningTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkShiningColors else LightShiningColors
    val typography = ShiningTypography()

    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setSystemBarsColor(
            color = colors.background,
            darkIcons = !darkTheme,
        )
    }

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.ink,
            onPrimary = colors.onInk,
            secondary = colors.brand,
            onSecondary = colors.onBrand,
            background = colors.background,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.surfaceMuted,
            onSurfaceVariant = colors.inkSecondary,
            outline = colors.border,
            error = colors.negative,
        )
    } else {
        lightColorScheme(
            primary = colors.ink,
            onPrimary = colors.onInk,
            secondary = colors.brand,
            onSecondary = colors.onBrand,
            background = colors.background,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.surfaceMuted,
            onSurfaceVariant = colors.inkSecondary,
            outline = colors.border,
            error = colors.negative,
        )
    }

    val materialTypography = Typography(
        headlineSmall = typography.headline,
        titleLarge = typography.title,
        titleMedium = typography.subtitle,
        bodyLarge = typography.body,
        bodyMedium = typography.body,
        bodySmall = typography.caption,
        labelLarge = typography.button,
        labelMedium = typography.label,
        labelSmall = typography.caption,
    )

    CompositionLocalProvider(
        LocalShiningColors provides colors,
        LocalShiningTypography provides typography,
        LocalShiningShapes provides ShiningShapes(),
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(colorScheme = scheme, typography = materialTypography) {
            CompositionLocalProvider(
                LocalTextStyle provides typography.body,
                LocalContentColor provides colors.ink,
                content = content,
            )
        }
    }
}

object ShiningTheme {
    val colors: ShiningColors
        @Composable
        @ReadOnlyComposable
        get() = LocalShiningColors.current

    val typography: ShiningTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalShiningTypography.current

    val shapes: ShiningShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalShiningShapes.current
}
