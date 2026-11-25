package app.shining.splash

import app.shining.account.domain.model.Account
import app.shining.account.domain.repository.AccountManager
import app.shining.account.domain.repository.AccountStore
import app.shining.account.domain.repository.ReserveInFlightTracker
import app.shining.domain.repository.TokenProvider
import app.shining.splash.ui.viewmodel.SplashScreenViewModel
import app.shining.testing.FakeKeyValueStore
import app.shining.testing.FakeTokenApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashScreenViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val store = AccountStore(FakeKeyValueStore(), Json) { 0L }
    private val api = FakeTokenApi(passwords = mutableMapOf("401106789" to "pw"))
    private val visited = mutableListOf<String>()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): SplashScreenViewModel {
        val manager = AccountManager(
            store = store,
            tokenProvider = TokenProvider(api, store),
            inFlight = ReserveInFlightTracker(),
            accountData = emptyList(),
            loadDisplayName = { Result.failure(Exception("no profile")) },
            appScope = backgroundScope,
        )
        return SplashScreenViewModel(manager).apply {
            navLogin.setNavigateAction { visited += "login" }
            navReauth.setNavigateAction { visited += "reauth" }
            navReserveInfo.setNavigateAction { visited += "home" }
        }
    }

    private fun savedAccount() {
        store.upsert(Account(username = "401106789", password = "pw"))
        store.setActive("401106789")
    }

    @Test
    fun `without internet a saved account still opens the home screen`() = runTest(dispatcher) {
        savedAccount()
        val vm = viewModel()

        vm.start(isOnline = false)
        advanceUntilIdle()

        assertEquals(listOf("home"), visited)
        assertFalse(vm.showNetworkError.value)
    }

    @Test
    fun `when Samad can't be reached a saved account still opens the home screen`() =
        runTest(dispatcher) {
            savedAccount()
            api.networkDown = true
            val vm = viewModel()

            vm.start(isOnline = true)
            advanceUntilIdle()

            assertEquals(listOf("home"), visited)
        }

    @Test
    fun `without internet and no account the network error shows`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.start(isOnline = false)
        advanceUntilIdle()

        assertTrue(visited.isEmpty())
        assertTrue(vm.showNetworkError.value)
    }

    /** An account that logged in before, with tokens; Samad refuses renewals and wants a captcha. */
    private fun accountWithTokens() {
        store.upsert(
            Account(
                username = "401106789",
                password = "pw",
                accessToken = "bearer 401106789:access-saved",
                refreshToken = "401106789:refresh-saved",
            )
        )
        store.setActive("401106789")
        api.requireCaptcha = true
    }

    @Test
    fun `a saved access token that still works opens the home screen without renewing`() =
        runTest(dispatcher) {
            accountWithTokens()
            api.validAccessTokens += "bearer 401106789:access-saved"
            val vm = viewModel()

            vm.start(isOnline = true)
            advanceUntilIdle()

            assertEquals(listOf("home"), visited)
            assertTrue(api.refreshCalls.isEmpty())
            assertTrue(api.loginCalls.isEmpty())
            assertFalse(store.find("401106789")!!.needsReauth)
        }

    @Test
    fun `an expired token that Samad won't renew asks to log in again`() = runTest(dispatcher) {
        accountWithTokens()
        val vm = viewModel()

        vm.start(isOnline = true)
        advanceUntilIdle()

        assertEquals(listOf("reauth"), visited)
        assertEquals(listOf("401106789:refresh-saved"), api.refreshCalls)
    }

    @Test
    fun `when Samad can't be reached a saved token isn't renewed`() = runTest(dispatcher) {
        accountWithTokens()
        api.networkDown = true
        val vm = viewModel()

        vm.start(isOnline = true)
        advanceUntilIdle()

        assertEquals(listOf("home"), visited)
        assertTrue(api.refreshCalls.isEmpty())
        assertTrue(api.loginCalls.isEmpty())
    }

    @Test
    fun `a rejected password still asks to log in again`() = runTest(dispatcher) {
        store.upsert(Account(username = "401106789", password = "old"))
        store.setActive("401106789")
        val vm = viewModel()

        vm.start(isOnline = true)
        advanceUntilIdle()

        assertEquals(listOf("reauth"), visited)
    }
}
