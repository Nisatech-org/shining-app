package app.shining.login.ui.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import app.shining.account.domain.repository.AccountManager
import app.shining.account.domain.repository.AccountStore
import app.shining.domain.model.LoadableData
import app.shining.domain.repository.Captcha
import app.shining.domain.repository.CaptchaAnswer
import app.shining.domain.repository.TokenProvider
import app.shining.domain.model.NavigationEvent
import app.shining.domain.ui.model.ShiningInformationBoxState
import app.shining.domain.ui.model.ShiningInformationBoxUIModel
import app.shining.domain.ui.viewmodel.ShiningViewModel
import app.shining.domain.util.toEnglishNumber
import app.shining.domain.util.toLocalNumber
import app.shining.login.domain.model.LoginMode
import app.shining.login.domain.model.STUDENT_NUMBER_LENGTHS
import app.shining.login.domain.model.isValidStudentNumber
import app.shining.login.domain.model.studentNumberLength
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val STUDENT_NUMBER_ERROR = "شماره دانشجویی باید ۸ یا ۹ رقم باشه."
internal const val CAPTCHA_EMPTY = "کد امنیتی توی تصویر رو وارد کن."

class LoginScreenViewModel(
    private val accountManager: AccountManager,
    private val accountStore: AccountStore,
    val mode: LoginMode,
    private val reauthUsername: String?,
    private val tokenProvider: TokenProvider,
) : ShiningViewModel() {

    /** Samad's captcha, needed for every login. A new one is fetched after each attempt. */
    val captcha: MutableState<LoadableData<Captcha>> = mutableStateOf(LoadableData.NotLoaded)
    val captchaText = mutableStateOf("")

    val username = mutableStateOf(reauthUsername?.toLocalNumber().orEmpty())
    val password = mutableStateOf("")
    val loginToken: MutableState<LoadableData<Unit>> = mutableStateOf(LoadableData.NotLoaded)
    val informationBoxData: MutableState<ShiningInformationBoxUIModel?> = mutableStateOf(null)
    private var hideInformationBoxJob: Job? = null

    /** Set once the user tries to log in, so a half-typed number isn't flagged while typing. */
    private val loginAttempted = mutableStateOf(false)

    /**
     * Shown under the student number field. A number that is already too long is flagged right
     * away; a short one only after a login attempt. A saved account's number is never flagged.
     */
    val usernameError: String?
        get() {
            if (!isUsernameEditable) return null
            val length = studentNumberLength(username.value)
            val tooLong = length > STUDENT_NUMBER_LENGTHS.last
            val tooShort = length < STUDENT_NUMBER_LENGTHS.first
            return if (tooLong || (tooShort && loginAttempted.value)) STUDENT_NUMBER_ERROR else null
        }

    /** The username can't be changed when logging in again to a saved account. */
    val isUsernameEditable: Boolean
        get() = mode != LoginMode.Reauth

    val navHome: NavigationEvent = NavigationEvent()

    fun onLaunch() {
        loadCaptcha()
        if (mode == LoginMode.Reauth && reauthUsername != null) {
            accountStore.find(reauthUsername)?.let {
                informationBoxData.value = ShiningInformationBoxUIModel(
                    ShiningInformationBoxState.FAILED,
                    "${it.label}: باید دوباره وارد شی.",
                )
            }
        }
    }

    fun loadCaptcha() {
        if (captcha.value is LoadableData.Loading) return
        captcha.value = LoadableData.Loading
        captchaText.value = ""
        viewModelScope.launch {
            captcha.value = tokenProvider.fetchCaptcha().fold(
                onSuccess = { LoadableData.Loaded(it) },
                onFailure = { LoadableData.Failed(it.message.orEmpty()) },
            )
        }
    }

    fun onCaptchaChange(text: String) {
        captchaText.value = text.toEnglishNumber().trim()
    }

    fun onUserNameChange(newUserName: String) {
        if (isUsernameEditable) username.value = newUserName.toLocalNumber()
    }

    fun onPasswordChange(newPassword: String) {
        password.value = newPassword.toLocalNumber()
    }

    fun onLoginClick() {
        if (loginToken.value is LoadableData.Loading) return
        loginAttempted.value = true
        if (isUsernameEditable && !isValidStudentNumber(username.value)) return
        val shown = (captcha.value as? LoadableData.Loaded)?.data
        if (shown == null) {
            loadCaptcha()
            return
        }
        if (captchaText.value.isBlank()) {
            showInformationBox(CAPTCHA_EMPTY, ShiningInformationBoxState.FAILED)
            return
        }
        val answer = CaptchaAnswer(shown.nonce, shown.validation, captchaText.value)
        loginToken.value = LoadableData.Loading
        viewModelScope.launch {
            accountManager.addAccount(username.value, password.value, answer).fold(
                onSuccess = {
                    loginToken.value = LoadableData.Loaded(Unit)
                    navHome.navigate()
                },
                onFailure = {
                    // A captcha works for one attempt only.
                    loadCaptcha()
                    loginToken.value = LoadableData.Failed(it.message ?: "خطای ناشناخته")
                    showInformationBox(
                        message = it.message ?: "خطای ناشناخته",
                        state = ShiningInformationBoxState.FAILED
                    )
                }
            )
        }
    }

    private fun showInformationBox(message: String, state: ShiningInformationBoxState) {
        hideInformationBoxJob?.cancel()
        hideInformationBoxJob = viewModelScope.launch {
            informationBoxData.value = ShiningInformationBoxUIModel(state, message)
            delay(5000)
            informationBoxData.value = null
        }
    }
}
