package app.shining.login

import app.shining.account.domain.repository.AccountManager
import app.shining.account.domain.repository.AccountStore
import app.shining.account.domain.repository.ReserveInFlightTracker
import app.shining.domain.repository.TokenProvider
import app.shining.login.domain.model.LoginMode
import app.shining.login.domain.model.isValidStudentNumber
import app.shining.login.ui.viewmodel.LoginScreenViewModel
import app.shining.login.ui.viewmodel.CAPTCHA_EMPTY
import app.shining.login.ui.viewmodel.STUDENT_NUMBER_ERROR
import app.shining.domain.model.LoadableData
import app.shining.domain.repository.WRONG_CAPTCHA
import app.shining.testing.FakeKeyValueStore
import app.shining.testing.FakeTokenApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginScreenViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val store = AccountStore(FakeKeyValueStore(), Json) { 0L }
    private val api = FakeTokenApi(passwords = mutableMapOf("401105786" to "pw"))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        mode: LoginMode = LoginMode.Initial,
        reauthUsername: String? = null,
    ): LoginScreenViewModel {
        val manager = AccountManager(
            store = store,
            tokenProvider = TokenProvider(api, store),
            inFlight = ReserveInFlightTracker(),
            accountData = emptyList(),
            loadDisplayName = { Result.failure(Exception("no profile")) },
            appScope = backgroundScope,
        )
        return LoginScreenViewModel(manager, store, mode, reauthUsername, TokenProvider(api, store)).also {
            it.onLaunch()
            advanceUntilIdle()
            it.onCaptchaChange(api.captchaAnswer)
        }
    }

    @Test
    fun `only 8 or 9 digits are a student number`() {
        assertFalse(isValidStudentNumber(""))
        assertFalse(isValidStudentNumber("4011057"))
        assertTrue(isValidStudentNumber("40110578"))
        assertTrue(isValidStudentNumber("401105786"))
        assertTrue(isValidStudentNumber("۴۰۱۱۰۵۷۸۶"))
        assertTrue(isValidStudentNumber(" 401105786 "))
        assertFalse(isValidStudentNumber("4011057861"))
        assertFalse(isValidStudentNumber("40110574886"))
    }

    @Test
    fun `a short number isn't flagged while typing, only after tapping login`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onUserNameChange("4011")
            assertNull(vm.usernameError)

            vm.onLoginClick()
            advanceUntilIdle()

            assertEquals(STUDENT_NUMBER_ERROR, vm.usernameError)
            assertTrue(api.loginCalls.isEmpty())
        }

    @Test
    fun `a number that is too long is flagged right away and never sent`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onUserNameChange("40110574886")
        vm.onPasswordChange("pw")
        assertEquals(STUDENT_NUMBER_ERROR, vm.usernameError)

        vm.onLoginClick()
        advanceUntilIdle()

        assertTrue(api.loginCalls.isEmpty())
    }

    @Test
    fun `fixing the number clears the error and logs in`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onUserNameChange("4011057")
        vm.onPasswordChange("pw")
        vm.onLoginClick()
        assertEquals(STUDENT_NUMBER_ERROR, vm.usernameError)

        vm.onUserNameChange("401105786")
        assertNull(vm.usernameError)
        vm.onLoginClick()
        advanceUntilIdle()

        assertEquals(listOf("401105786"), api.loginCalls)
        assertEquals("401105786", store.active?.username)
    }

    @Test
    fun `the login sends the captcha answer, as Samad now requires`() = runTest(dispatcher) {
        api.requireCaptcha = true
        val vm = viewModel()
        assertTrue(vm.captcha.value is LoadableData.Loaded)
        vm.onUserNameChange("401105786")
        vm.onPasswordChange("pw")

        vm.onLoginClick()
        advanceUntilIdle()

        assertEquals("401105786", store.active?.username)
    }

    @Test
    fun `a wrong captcha says so and brings a new one`() = runTest(dispatcher) {
        api.requireCaptcha = true
        val vm = viewModel()
        vm.onUserNameChange("401105786")
        vm.onPasswordChange("pw")
        vm.onCaptchaChange("wrong")

        vm.onLoginClick()
        advanceUntilIdle()

        assertNull(store.active)
        assertEquals(WRONG_CAPTCHA, (vm.loginToken.value as LoadableData.Failed).message)
        assertEquals(2, api.captchaNonces.size)
        assertEquals("", vm.captchaText.value)
        vm.onCaptchaChange(api.captchaAnswer)
        vm.onLoginClick()
        advanceUntilIdle()
        assertEquals("401105786", store.active?.username)
    }

    @Test
    fun `an empty captcha isn't sent`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onUserNameChange("401105786")
        vm.onPasswordChange("pw")
        vm.onCaptchaChange("")

        vm.onLoginClick()
        // The message box hides itself after 5 seconds; look before that.
        runCurrent()

        assertTrue(api.loginCalls.isEmpty())
        assertEquals(CAPTCHA_EMPTY, vm.informationBoxData.value?.message)
    }

    @Test
    fun `logging in again to a saved account skips the check`() = runTest(dispatcher) {
        api.passwords["1234567890"] = "pw"
        val vm = viewModel(mode = LoginMode.Reauth, reauthUsername = "1234567890")
        vm.onPasswordChange("pw")

        vm.onLoginClick()
        advanceUntilIdle()

        assertNull(vm.usernameError)
        assertEquals(listOf("1234567890"), api.loginCalls)
    }

    @Test
    fun `logging in again starts with the saved password, so only the captcha is left`() = runTest(dispatcher) {
        api.passwords["401105786"] = "1234"
        api.requireCaptcha = true
        store.upsert(app.shining.account.domain.model.Account(username = "401105786", password = "1234"))
        val vm = viewModel(mode = LoginMode.Reauth, reauthUsername = "401105786")

        assertEquals("۱۲۳۴", vm.password.value)
        vm.onLoginClick()
        advanceUntilIdle()

        assertEquals(listOf("401105786"), api.loginCalls)
        assertTrue(vm.loginToken.value is LoadableData.Loaded)
    }

    @Test
    fun `a new login starts with an empty password`() = runTest(dispatcher) {
        store.upsert(app.shining.account.domain.model.Account(username = "401105786", password = "1234"))
        val vm = viewModel(mode = LoginMode.Add)

        assertEquals("", vm.password.value)
    }
}
