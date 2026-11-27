package app.shining.login.ui.component

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.domain.ui.component.ShiningTextField
import app.shining.domain.ui.theme.ShiningTheme

/** Samad's login captcha as the login screen shows it. */
data class CaptchaUIModel(
    val imageBase64: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val text: String = "",
)

/**
 * Samad's captcha picture on its own row, big enough to read, with a button for a new one, and
 * the code field under it. The picture is dark on transparent, so it always sits on white, also
 * in dark mode. Tapping it also fetches a new one.
 */
@Composable
fun CaptchaField(
    captcha: CaptchaUIModel,
    onTextChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onDone: () -> Unit,
) {
    val bitmap = remember(captcha.imageBase64) {
        captcha.imageBase64?.let {
            runCatching {
                val bytes = Base64.decode(it, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp)
                    .clip(ShiningTheme.shapes.field)
                    .background(Color.White)
                    .clickable(onClick = onRefresh),
            ) {
                when {
                    captcha.loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    bitmap != null -> Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(R.string.login_captcha),
                        contentScale = ContentScale.Fit,
                        // A small picture blown up: keep the letters' edges sharp.
                        filterQuality = FilterQuality.None,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    else -> Text(
                        text = stringResource(R.string.login_captcha_retry),
                        style = ShiningTheme.typography.caption,
                        color = Color.DarkGray,
                    )
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onRefresh),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = stringResource(R.string.login_captcha_new),
                    tint = ShiningTheme.colors.inkSecondary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        ShiningTextField(
            value = captcha.text,
            onValueChange = onTextChange,
            label = stringResource(R.string.login_captcha),
            placeholder = stringResource(R.string.login_captcha_placeholder),
            ltrInput = true,
            error = captcha.error,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )
    }
}
