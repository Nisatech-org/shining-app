package app.shining.splash.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.domain.ui.component.EmptyState
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningProgressIndicator
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.login.ui.component.ShiningLogo

@Composable
fun SplashScreen(
    shouldShowNetworkError: Boolean,
    onReload: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ShiningTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        if (shouldShowNetworkError) {
            EmptyState(
                emoji = R.drawable.emoji_antenna,
                title = stringResource(R.string.network_connection_error),
                subtitle = stringResource(R.string.network_connection_error_hint),
                modifier = Modifier.fillMaxWidth(),
                action = {
                    ShiningButton(
                        text = stringResource(R.string.retry),
                        icon = R.drawable.ic_refresh,
                        onClick = onReload,
                    )
                },
            )
        } else {
            val pulse by rememberInfiniteTransition(label = "logo").animateFloat(
                initialValue = 0.94f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "logoScale",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .scale(pulse)
                        .size(148.dp)
                        .clip(ShiningTheme.shapes.pill)
                        .background(ShiningTheme.colors.brand),
                    contentAlignment = Alignment.Center,
                ) {
                    ShiningLogo(size = 84)
                }
                Spacer(modifier = Modifier.size(24.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = ShiningTheme.typography.display,
                    color = ShiningTheme.colors.ink,
                )
                Text(
                    text = stringResource(R.string.splash_screen_caption),
                    style = ShiningTheme.typography.body,
                    color = ShiningTheme.colors.inkSecondary,
                )
            }
            ShiningProgressIndicator(
                size = 22.dp,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
            )
        }
    }
}

@Preview
@Composable
private fun SplashScreenPreview() {
    ShiningTheme { SplashScreen(shouldShowNetworkError = false, onReload = {}) }
}
