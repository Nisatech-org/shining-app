package app.shining.domain.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.domain.ui.theme.ShiningTheme

/**
 * A labelled text field. [ltrInput] keeps digits and Latin passwords left-to-right while the
 * rest of the field stays right-to-left. A non-null [error] turns the border red and shows the
 * message under the field.
 */
@Composable
fun ShiningTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    ltrInput: Boolean = false,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = ShiningTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    val shape = ShiningTheme.shapes.field

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = ShiningTheme.typography.label,
            color = colors.inkSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .alpha(if (enabled) 1f else 0.6f)
                .clip(shape)
                .background(colors.surface)
                .border(
                    width = if (focused || error != null) 1.5.dp else 1.dp,
                    color = when {
                        error != null -> colors.negative
                        focused -> colors.ink
                        else -> colors.border
                    },
                    shape = shape,
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leadingIcon?.let {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    tint = if (focused) colors.ink else colors.inkTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        style = ShiningTheme.typography.body,
                        color = colors.inkTertiary,
                    )
                }
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (ltrInput) LayoutDirection.Ltr else LayoutDirection.Rtl
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        singleLine = true,
                        textStyle = ShiningTheme.typography.subtitle.copy(color = colors.ink),
                        cursorBrush = SolidColor(colors.ink),
                        interactionSource = interaction,
                        visualTransformation = if (isPassword && !passwordVisible) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (isPassword) {
                Icon(
                    painter = painterResource(if (passwordVisible) R.drawable.ic_eye_off else R.drawable.ic_eye),
                    contentDescription = stringResource(
                        if (passwordVisible) R.string.hide_password else R.string.show_password
                    ),
                    tint = colors.inkSecondary,
                    modifier = Modifier
                        .size(22.dp)
                        .clickableNoRipple { passwordVisible = !passwordVisible },
                )
            }
        }
        error?.let {
            Text(
                text = it,
                style = ShiningTheme.typography.caption,
                color = colors.negative,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
    }
}
