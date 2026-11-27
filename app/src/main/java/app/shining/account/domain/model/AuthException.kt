package app.shining.account.domain.model

/**
 * Why logging in to Samad failed. Only [InvalidCredentials] means the stored password is wrong;
 * a [Network] failure (no connection, a timeout, a Samad server error or an unreadable reply)
 * says nothing about the account. Its message tells the user which one it was.
 */
sealed class AuthException(message: String) : Exception(message) {
    class InvalidCredentials(message: String) : AuthException(message)

    class Network(message: String) : AuthException(message)
}
