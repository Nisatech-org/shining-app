package app.shining.account

import app.shining.account.domain.model.AccountLimitException
import app.shining.account.domain.model.AuthException
import app.shining.account.domain.model.RemoveResult
import app.shining.account.domain.model.ResumeResult
import app.shining.account.domain.repository.AccountManager
import app.shining.account.domain.repository.AccountScopedData
import app.shining.account.domain.repository.AccountStore
import app.shining.account.domain.repository.ReserveInFlightTracker
import app.shining.domain.repository.TokenProvider
import app.shining.testing.FakeKeyValueStore
import app.shining.testing.FakeTokenApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountManagerTest {

    private var now = 0L
    private val store = AccountStore(FakeKeyValueStore(), Json) { ++now }
    private val api = FakeTokenApi(
        passwords = mutableMapOf("me" to "pw-me", "ali" to "pw-ali", "sara" to "pw-sara")
    )
    private val tokenProvider = TokenProvider(api, store)
    private val inFlight = ReserveInFlightTracker()
    private val accountData = FakeAccountData()
    private val names = mutableMapOf("me" to "Me Myself", "ali" to "Ali Rezaei")

    private fun TestScope.manager() = AccountManager(
        store = store,
        tokenProvider = tokenProvider,
        inFlight = inFlight,
        accountData = listOf(accountData),
        loadDisplayName = {
            names[store.active?.username]?.let { Result.success(it) }
                ?: Result.failure(Exception("no profile"))
        },
        appScope = this,
    )

    private suspend fun AccountManager.addAll(vararg usernames: String) {
        usernames.forEach { addAccount(it, "pw-$it").getOrThrow() }
    }

    @Test
    fun `adding an account logs in, activates it and loads its name`() = runTest {
        val manager = manager()

        val account = manager.addAccount("me", "pw-me").getOrThrow()
        advanceUntilIdle()

        assertEquals("me", store.active?.username)
        assertTrue(account.accessToken.startsWith("bearer me:access"))
        assertEquals("Me Myself", store.active?.displayName)
    }

    @Test
    fun `usernames typed with Persian digits are stored with English digits`() = runTest {
        api.passwords["400100100"] = "1234"

        manager().addAccount("۴۰۰۱۰۰۱۰۰", "۱۲۳۴").getOrThrow()

        assertEquals("400100100", store.active?.username)
    }

    @Test
    fun `a new account copies the active account's data`() = runTest {
        val manager = manager()
        manager.addAll("me")

        manager.addAccount("ali", "pw-ali").getOrThrow()

        assertEquals(listOf(null to "me", "me" to "ali"), accountData.copies)
    }

    @Test
    fun `wrong password adds nothing`() = runTest {
        val result = manager().addAccount("me", "wrong")

        assertTrue(result.exceptionOrNull() is AuthException.InvalidCredentials)
        assertTrue(store.state.value.accounts.isEmpty())
    }

    @Test
    fun `adding a saved username updates it instead of adding a duplicate`() = runTest {
        val manager = manager()
        manager.addAll("me", "ali")
        store.update("me") { it.copy(needsReauth = true) }
        api.passwords["me"] = "changed"

        manager.addAccount("me", "changed").getOrThrow()

        assertEquals(listOf("me", "ali"), store.state.value.accounts.map { it.username })
        assertEquals("changed", store.find("me")?.password)
        assertFalse(store.find("me")!!.needsReauth)
        assertEquals("me", store.active?.username)
        assertEquals(2, accountData.copies.size)
    }

    @Test
    fun `no more than five accounts`() = runTest {
        val manager = manager()
        (1..5).forEach { api.passwords["u$it"] = "pw-u$it" }
        manager.addAll("u1", "u2", "u3", "u4", "u5")
        api.loginCalls.clear()

        val result = manager.addAccount("me", "pw-me")

        assertTrue(result.exceptionOrNull() is AccountLimitException)
        assertTrue(api.loginCalls.isEmpty())
        // Re-adding a saved account is still allowed at the limit.
        assertTrue(manager.addAccount("u1", "pw-u1").isSuccess)
    }

    @Test
    fun `switching keeps a saved access token that still works`() = runTest {
        val manager = manager()
        manager.addAll("me", "ali")
        val saved = store.find("me")!!.accessToken
        api.loginCalls.clear()

        manager.switchTo("me").getOrThrow()

        assertEquals("me", store.active?.username)
        assertEquals(listOf(saved), api.checkCalls)
        assertTrue(api.loginCalls.isEmpty())
        assertTrue(api.refreshCalls.isEmpty())
        assertEquals(saved, store.find("me")!!.accessToken)
    }

    @Test
    fun `switching renews an expired access token and skips the password`() = runTest {
        val manager = manager()
        manager.addAll("me", "ali")
        api.validAccessTokens.clear()
        api.loginCalls.clear()

        manager.switchTo("me").getOrThrow()

        assertEquals("me", store.active?.username)
        assertTrue(api.loginCalls.isEmpty())
        assertEquals(1, api.refreshCalls.size)
    }

    @Test
    fun `switching falls back to the saved password when the refresh token is dead`() =
        runTest {
            val manager = manager()
            manager.addAll("me", "ali")
            api.validAccessTokens.clear()
            api.validRefreshTokens.clear()

            manager.switchTo("me").getOrThrow()

            assertEquals("me", store.active?.username)
            assertEquals(listOf("me"), api.loginCalls.takeLast(1))
        }

    @Test
    fun `a rejected password keeps the current account and marks the other one`() = runTest {
        val manager = manager()
        manager.addAll("me", "ali")
        api.validAccessTokens.clear()
        api.validRefreshTokens.clear()
        api.passwords["me"] = "changed elsewhere"

        val result = manager.switchTo("me")

        assertTrue(result.exceptionOrNull() is AuthException.InvalidCredentials)
        assertEquals("ali", store.active?.username)
        assertTrue(store.find("me")!!.needsReauth)
    }

    @Test
    fun `a network error keeps the current account without marking the other one`() =
        runTest {
            val manager = manager()
            manager.addAll("me", "ali")
            api.networkDown = true

            val result = manager.switchTo("me")

            assertTrue(result.exceptionOrNull() is AuthException.Network)
            assertEquals("ali", store.active?.username)
            assertFalse(store.find("me")!!.needsReauth)
        }

    @Test
    fun `switching waits for running reservations`() = runTest {
        val manager = manager()
        manager.addAll("me", "ali")
        val reservation = CompletableDeferred<Unit>()
        launch { inFlight.track { reservation.await() } }
        runCurrent()

        val switch = async { manager.switchTo("me") }
        runCurrent()
        assertEquals("ali", store.active?.username)

        reservation.complete(Unit)
        switch.await().getOrThrow()
        assertEquals("me", store.active?.username)
    }

    @Test
    fun `a name that arrives after another switch is not applied`() = runTest {
        val nameGate = CompletableDeferred<Unit>()
        val manager = AccountManager(
            store = store,
            tokenProvider = tokenProvider,
            inFlight = inFlight,
            accountData = listOf(accountData),
            loadDisplayName = { nameGate.await(); Result.success("Stale Name") },
            appScope = this,
        )
        manager.addAll("me", "ali")
        manager.switchTo("me").getOrThrow()

        nameGate.complete(Unit)
        advanceUntilIdle()

        assertEquals("Stale Name", store.find("me")?.displayName)
        assertNull(store.find("ali")?.displayName)
    }

    @Test
    fun `removing another account keeps the active one`() = runTest {
        val manager = manager()
        manager.addAll("me", "ali")

        val result = manager.remove("me")

        assertEquals(RemoveResult.Removed, result)
        assertEquals("ali", store.active?.username)
        assertEquals(listOf("me"), accountData.deleted)
        assertNull(store.find("me"))
    }

    @Test
    fun `removing the active account switches to the most recently used one`() = runTest {
        val manager = manager()
        manager.addAll("me", "ali", "sara")
        manager.switchTo("ali").getOrThrow()
        manager.switchTo("sara").getOrThrow()

        val result = manager.remove("sara")

        assertEquals(RemoveResult.SwitchedTo("ali"), result)
        assertEquals("ali", store.active?.username)
    }

    @Test
    fun `removing the active account skips accounts that can't log in`() = runTest {
        val manager = manager()
        manager.addAll("me", "ali", "sara")
        manager.switchTo("ali").getOrThrow()
        manager.switchTo("sara").getOrThrow()
        api.validAccessTokens.removeAll { it.startsWith("bearer ali:") }
        api.validRefreshTokens.removeAll { it.startsWith("ali") }
        api.passwords["ali"] = "changed"

        val result = manager.remove("sara")

        assertEquals(RemoveResult.SwitchedTo("me"), result)
        assertTrue(store.find("ali")!!.needsReauth)
    }

    @Test
    fun `removing the last account asks for a new login`() = runTest {
        val manager = manager()
        manager.addAll("me")

        val result = manager.remove("me")

        assertEquals(RemoveResult.NeedsLogin(null), result)
        assertNull(store.active)
    }

    @Test
    fun `resume reports each outcome`() = runTest {
        val manager = manager()
        assertEquals(ResumeResult.NoAccount, manager.resumeActive())

        manager.addAll("me")
        assertEquals(ResumeResult.Resumed, manager.resumeActive())

        api.validAccessTokens.clear()
        api.validRefreshTokens.clear()
        api.passwords["me"] = "changed"
        val result = manager.resumeActive()
        assertTrue(result is ResumeResult.NeedsLogin)
        assertTrue((result as ResumeResult.NeedsLogin).error is AuthException.InvalidCredentials)
    }

    /** Samad since November 2026: renewals refused, and a captcha for every password login. */
    private fun samadRefusesRenewalsAndWantsCaptcha() {
        api.validRefreshTokens.clear()
        api.requireCaptcha = true
    }

    private fun clearCalls() {
        api.checkCalls.clear()
        api.refreshCalls.clear()
        api.loginCalls.clear()
    }

    @Test
    fun `resume keeps a saved access token that still works without renewing it`() = runTest {
        val manager = manager()
        manager.addAll("me")
        val saved = store.find("me")!!
        samadRefusesRenewalsAndWantsCaptcha()
        clearCalls()

        assertEquals(ResumeResult.Resumed, manager.resumeActive())

        assertEquals(listOf(saved.accessToken), api.checkCalls)
        assertTrue(api.refreshCalls.isEmpty())
        assertTrue(api.loginCalls.isEmpty())
        val me = store.find("me")!!
        assertEquals(saved.accessToken, me.accessToken)
        assertEquals(saved.refreshToken, me.refreshToken)
        assertFalse(me.needsReauth)
        assertEquals("me", store.active?.username)
    }

    @Test
    fun `resume renews an expired access token with the refresh token`() = runTest {
        val manager = manager()
        manager.addAll("me")
        val expired = store.find("me")!!.accessToken
        api.validAccessTokens.clear()
        clearCalls()

        assertEquals(ResumeResult.Resumed, manager.resumeActive())

        assertEquals(listOf(expired), api.checkCalls)
        assertEquals(1, api.refreshCalls.size)
        assertTrue(api.loginCalls.isEmpty())
        assertTrue(store.find("me")!!.accessToken != expired)
    }

    @Test
    fun `resume falls back to the saved password when the token expired and renewal is refused`() =
        runTest {
            val manager = manager()
            manager.addAll("me")
            api.validAccessTokens.clear()
            api.validRefreshTokens.clear()
            clearCalls()

            assertEquals(ResumeResult.Resumed, manager.resumeActive())

            assertEquals(1, api.refreshCalls.size)
            assertEquals(listOf("me"), api.loginCalls)
        }

    @Test
    fun `an expired token with a refused renewal and a wanted captcha asks to log in again`() =
        runTest {
            val manager = manager()
            manager.addAll("me")
            api.validAccessTokens.clear()
            samadRefusesRenewalsAndWantsCaptcha()

            val result = manager.resumeActive()

            assertTrue((result as ResumeResult.NeedsLogin).error is AuthException.InvalidCredentials)
            assertTrue(store.find("me")!!.needsReauth)
        }

    @Test
    fun `resume offline spends nothing and marks nothing`() = runTest {
        val manager = manager()
        manager.addAll("me")
        val saved = store.find("me")!!
        api.networkDown = true
        clearCalls()

        val result = manager.resumeActive()

        assertTrue((result as ResumeResult.NeedsLogin).error is AuthException.Network)
        assertTrue(api.refreshCalls.isEmpty())
        assertTrue(api.loginCalls.isEmpty())
        val me = store.find("me")!!
        assertEquals(saved.refreshToken, me.refreshToken)
        assertFalse(me.needsReauth)
    }

    @Test
    fun `a server error on the token check spends nothing`() = runTest {
        val manager = manager()
        manager.addAll("me")
        api.validAccessTokens.clear()
        api.rejectedTokenStatus = 502
        clearCalls()

        val result = manager.resumeActive()

        assertTrue((result as ResumeResult.NeedsLogin).error is AuthException.Network)
        assertTrue(api.refreshCalls.isEmpty())
        assertTrue(api.loginCalls.isEmpty())
        assertFalse(store.find("me")!!.needsReauth)
    }

    @Test
    fun `resume doesn't renew again when a request already renewed the expired token`() =
        runTest {
            val manager = manager()
            manager.addAll("me")
            val expired = store.find("me")!!.accessToken
            api.validAccessTokens.clear()
            // A request that got a 401 renews the token while the start's check is on its way.
            api.duringCheck = {
                api.duringCheck = null
                tokenProvider.refreshAccessToken("me", rejectedAccessToken = expired).getOrThrow()
            }
            clearCalls()

            assertEquals(ResumeResult.Resumed, manager.resumeActive())

            assertEquals(1, api.refreshCalls.size)
            assertTrue(api.loginCalls.isEmpty())
            val renewed = store.find("me")!!.accessToken
            assertTrue(renewed != expired)
            assertTrue(renewed in api.validAccessTokens)
        }

    @Test
    fun `a request's 401 after the start renewed the token uses it without renewing again`() =
        runTest {
            val manager = manager()
            manager.addAll("me")
            val expired = store.find("me")!!.accessToken
            api.validAccessTokens.clear()
            manager.resumeActive()
            val renewed = store.find("me")!!.accessToken
            api.refreshCalls.clear()

            // A request sent with the expired token before the start renewed it gets a 401.
            val token = tokenProvider.refreshAccessToken("me", rejectedAccessToken = expired).getOrThrow()

            assertEquals(renewed, token.accessToken)
            assertTrue(api.refreshCalls.isEmpty())
        }

    @Test
    fun `a 401 refresh saves tokens to the request's account, not the active one`() = runTest {
        val manager = manager()
        manager.addAll("me", "ali")
        val meBefore = store.find("me")!!.accessToken

        // A request started as "me" gets a 401 after "ali" became active.
        tokenProvider.refreshAccessToken("me").getOrThrow()

        val me = store.find("me")!!
        assertTrue(me.accessToken != meBefore)
        assertEquals("ali", store.active?.username)
    }

    private class FakeAccountData : AccountScopedData {
        val copies = mutableListOf<Pair<String?, String>>()
        val deleted = mutableListOf<String>()

        override suspend fun copyAccountData(fromUsername: String?, toUsername: String) {
            copies += fromUsername to toUsername
        }

        override suspend fun deleteAccountData(username: String) {
            deleted += username
        }
    }
}
