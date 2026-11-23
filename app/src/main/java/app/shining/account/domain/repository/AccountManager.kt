package app.shining.account.domain.repository

import app.shining.domain.repository.CaptchaAnswer
import android.util.Log
import app.shining.account.domain.model.Account
import app.shining.account.domain.model.AccountLimitException
import app.shining.account.domain.model.AuthException
import app.shining.account.domain.model.RemoveResult
import app.shining.account.domain.model.ResumeResult
import app.shining.domain.model.SamadToken
import app.shining.domain.repository.TokenProvider
import app.shining.domain.util.toEnglishNumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Adds, switches, removes and resumes accounts. Only the active account talks to Samad; the
 * others keep their saved credentials and log in again when switched to.
 *
 * Every change waits for running reserve/cancel requests to finish first, so their results
 * always belong to the account they were made for.
 */
class AccountManager(
    private val store: AccountStore,
    private val tokenProvider: TokenProvider,
    private val inFlight: ReserveInFlightTracker,
    private val accountData: List<AccountScopedData>,
    /** Loads the active account's full name from Samad. */
    private val loadDisplayName: suspend () -> Result<String>,
    private val appScope: CoroutineScope,
) {
    private val mutex = Mutex()

    /** True when an account was logged in before, even if it can't reach Samad right now. */
    val hasActiveAccount: Boolean
        get() = store.active != null

    /** Called on app start: makes sure the active account has working tokens. */
    suspend fun resumeActive(): ResumeResult = mutex.withLock {
        val account = store.active ?: return ResumeResult.NoAccount
        authenticate(account).fold(
            onSuccess = {
                activate(account.username, it)
                ResumeResult.Resumed
            },
            onFailure = {
                ResumeResult.NeedsLogin(account.username, it.asAuthException())
            },
        )
    }

    /**
     * Logs in and makes the account active. An already saved username gets its password and
     * tokens updated instead of being added twice. A new account starts with a copy of the
     * currently active account's local data.
     */
    suspend fun addAccount(username: String, password: String, captcha: CaptchaAnswer? = null): Result<Account> =
        mutex.withLock {
            inFlight.awaitIdle()
            val normalizedUsername = username.toEnglishNumber().trim()
            val normalizedPassword = password.toEnglishNumber()
            val existing = store.find(normalizedUsername)
            if (existing == null && store.state.value.accounts.size >= MAX_ACCOUNTS) {
                return Result.failure(
                    AccountLimitException("حداکثر $MAX_ACCOUNTS_LOCAL حساب می‌تونی اضافه کنی.")
                )
            }
            val token = tokenProvider.requestLogin(normalizedUsername, normalizedPassword, captcha)
                .getOrElse { return Result.failure(it) }

            if (existing == null) {
                val copyFrom = store.active?.username
                store.upsert(Account(username = normalizedUsername, password = normalizedPassword))
                accountData.forEach {
                    runCatchingNonCancellation { it.copyAccountData(copyFrom, normalizedUsername) }
                }
            } else {
                store.update(normalizedUsername) { it.copy(password = normalizedPassword) }
            }
            activate(normalizedUsername, token)
            Result.success(store.find(normalizedUsername)!!)
        }

    /**
     * Makes [username] the active account. If Samad rejects its saved password the current
     * account stays active and [username] is marked as needing a new login.
     */
    suspend fun switchTo(username: String): Result<Account> = mutex.withLock {
        inFlight.awaitIdle()
        if (store.active?.username == username) return Result.success(store.active!!)
        val account = store.find(username)
            ?: return Result.failure(IllegalArgumentException("Unknown account"))
        val token = authenticate(account).getOrElse { return Result.failure(it) }
        activate(username, token)
        Result.success(store.find(username)!!)
    }

    /**
     * Removes the account and its local data. If it was active, the most recently used account
     * that can still log in takes over.
     */
    suspend fun remove(username: String): RemoveResult = mutex.withLock {
        inFlight.awaitIdle()
        val wasActive = store.active?.username == username
        store.remove(username)
        accountData.forEach { runCatchingNonCancellation { it.deleteAccountData(username) } }
        if (!wasActive) return RemoveResult.Removed

        val candidates = store.state.value.accounts.sortedByDescending { it.lastUsedAt }
        for (candidate in candidates) {
            val token = authenticate(candidate).getOrNull() ?: continue
            activate(candidate.username, token)
            return RemoveResult.SwitchedTo(candidate.username)
        }
        val fallback = candidates.firstOrNull()?.username
        store.setActive(fallback)
        RemoveResult.NeedsLogin(fallback)
    }

    fun move(fromIndex: Int, toIndex: Int) = store.move(fromIndex, toIndex)

    fun updateActiveDisplayName(name: String) {
        val active = store.active ?: return
        if (name.isNotBlank() && name != active.displayName) {
            store.update(active.username) { it.copy(displayName = name) }
        }
    }

    /**
     * Gets working tokens. A saved access token that Samad still accepts is kept as it is: they
     * last about 180 days, while since November 2026 Samad refuses renewals and wants a captcha
     * for every password login, so renewing at every start signed everyone out. Only when Samad
     * rejects it (401) come the refresh token, then the saved password. When Samad can't be
     * reached nothing else is tried. Only a rejected password marks the account as needing a new
     * login; network errors don't.
     */
    private suspend fun authenticate(account: Account): Result<SamadToken> {
        if (account.accessToken.isNotEmpty()) {
            tokenProvider.checkAccessToken(account.accessToken).fold(
                onSuccess = {
                    return Result.success(
                        SamadToken(accessToken = account.accessToken, refreshToken = account.refreshToken)
                    )
                },
                onFailure = { if (it !is AuthException.InvalidCredentials) return Result.failure(it) },
            )
        }
        if (account.refreshToken.isNotEmpty()) {
            // Through TokenProvider, so a request that got a 401 meanwhile and renewed the token
            // doesn't make this spend the refresh token a second time.
            tokenProvider.refreshAccessToken(account.username, rejectedAccessToken = account.accessToken)
                .onSuccess { return Result.success(it) }
        }
        return tokenProvider.requestLogin(account.username, account.password).onFailure {
            if (it is AuthException.InvalidCredentials) {
                store.update(account.username) { saved -> saved.copy(needsReauth = true) }
            }
        }
    }

    private fun activate(username: String, token: SamadToken) {
        store.update(username) {
            it.copy(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                needsReauth = false,
            )
        }
        store.setActive(username)
        appScope.launch {
            loadDisplayName().onSuccess { name ->
                // Samad answered for whichever account was active when the request was sent.
                if (store.active?.username == username) updateActiveDisplayName(name)
            }
        }
    }

    private suspend fun runCatchingNonCancellation(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("AccountManager", "account data update failed", e)
        }
    }

    private fun Throwable.asAuthException(): AuthException =
        this as? AuthException ?: AuthException.Network(message.orEmpty())

    companion object {
        const val MAX_ACCOUNTS = 5
        private const val MAX_ACCOUNTS_LOCAL = "۵"
    }
}
