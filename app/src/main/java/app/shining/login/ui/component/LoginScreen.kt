package app.shining.login.ui.component

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.shining.R
import app.shining.domain.ui.component.InlineMessage
import app.shining.domain.ui.component.ShiningButton
import app.shining.domain.ui.component.ShiningButtonSize
import app.shining.domain.ui.component.ShiningButtonStyle
import app.shining.domain.ui.component.ShiningCard
import app.shining.domain.ui.component.ShiningTextField
import app.shining.domain.ui.model.ShiningInformationBoxUIModel
import app.shining.domain.ui.theme.ShiningTheme
import app.shining.login.ui.model.LoginScreenUIModel

@Composable
fun LoginScreen(
    data: LoginScreenUIModel,
    loginStatus: ShiningInformationBoxUIModel?,
    isLoading: Boolean,
    onUserNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    @StringRes titleRes: Int = R.string.login_title,
    isUsernameEditable: Boolean = true,
    usernameError: String? = null,
    captcha: CaptchaUIModel = CaptchaUIModel(),
    onCaptchaChange: (String) -> Unit = {},
    onCaptchaRefresh: () -> Unit = {},
    /** "ورود مهمان": the market without a Samad login. Null hides it (adding an account, logging in again). */
    onGuestClick: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ShiningTheme.colors.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.size(16.dp))
        ShiningCard(
            modifier = Modifier.fillMaxWidth(),
            background = ShiningTheme.colors.brand,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(ShiningTheme.shapes.pill)
                    .background(ShiningTheme.colors.onBrand),
                contentAlignment = Alignment.Center,
            ) {
                ShiningLogo(size = 38)
            }
            Spacer(modifier = Modifier.size(28.dp))
            Text(
                text = stringResource(R.string.login_welcome),
                style = ShiningTheme.typography.hero,
                color = ShiningTheme.colors.onBrand,
            )
            Text(
                text = stringResource(titleRes),
                style = ShiningTheme.typography.body,
                color = ShiningTheme.colors.onBrand.copy(alpha = 0.65f),
            )
        }
        Spacer(modifier = Modifier.size(12.dp))

        ShiningCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        ) {
            ShiningTextField(
                value = data.username,
                onValueChange = onUserNameChange,
                label = stringResource(R.string.student_number),
                placeholder = stringResource(R.string.login_username_placeholder),
                leadingIcon = R.drawable.ic_profile,
                enabled = isUsernameEditable,
                ltrInput = true,
                error = usernameError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )
            Spacer(modifier = Modifier.size(16.dp))
            ShiningTextField(
                value = data.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.password),
                placeholder = stringResource(R.string.login_password_placeholder),
                leadingIcon = R.drawable.ic_code,
                isPassword = true,
                ltrInput = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                    autoCorrect = false,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )
            Spacer(modifier = Modifier.size(16.dp))
            CaptchaField(
                captcha = captcha,
                onTextChange = onCaptchaChange,
                onRefresh = onCaptchaRefresh,
                onDone = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    onLoginClick()
                },
            )

            AnimatedVisibility(visible = loginStatus != null) {
                // Keeps the last message while the box animates out.
                val status = remember { mutableStateOf(loginStatus) }
                if (loginStatus != null) status.value = loginStatus
                status.value?.let {
                    InlineMessage(data = it, modifier = Modifier.padding(top = 16.dp))
                }
            }

            Spacer(modifier = Modifier.size(20.dp))
            ShiningButton(
                text = stringResource(R.string.login_login_button_label),
                icon = R.drawable.ic_login,
                onClick = {
                    focusManager.clearFocus()
                    onLoginClick()
                },
                loading = isLoading,
                size = ShiningButtonSize.Large,
                modifier = Modifier.fillMaxWidth(),
            )
            if (onGuestClick != null) {
                Spacer(modifier = Modifier.size(8.dp))
                ShiningButton(
                    text = stringResource(R.string.login_guest_button_label),
                    onClick = {
                        focusManager.clearFocus()
                        onGuestClick()
                    },
                    style = ShiningButtonStyle.Secondary,
                    size = ShiningButtonSize.Large,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.login_guest_note),
                    style = ShiningTheme.typography.caption,
                    color = ShiningTheme.colors.inkTertiary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.size(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check_rounded),
                contentDescription = null,
                tint = ShiningTheme.colors.positive,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = stringResource(R.string.login_privacy_note),
                style = ShiningTheme.typography.caption,
                color = ShiningTheme.colors.inkSecondary,
            )
        }
    }
}

/** The app's logo: 🍟 fries with no face, on no background. */
@Composable
fun ShiningLogo(size: Int, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.logo_fries),
        contentDescription = null,
        modifier = modifier.size(size.dp),
    )
}

@Preview
@Composable
private fun LoginScreenPreview() {
    ShiningTheme {
        LoginScreen(
            data = LoginScreenUIModel("", ""),
            loginStatus = null,
            isLoading = false,
            onUserNameChange = {},
            onPasswordChange = {},
            onLoginClick = {},
        )
    }
}
