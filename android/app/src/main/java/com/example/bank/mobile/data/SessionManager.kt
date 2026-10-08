package com.example.bank.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/** Where the app should start. */
enum class StartPoint { SIGN_IN, CREATE_PIN, UNLOCK }

/**
 * Owns the signed-in state.
 *
 * Sign-in with email + password happens once; the app then keeps a refresh token
 * (encrypted) and asks for a 6-digit PIN on every launch, like Cambodian bank apps.
 * The PIN never leaves the phone: it only unlocks the stored refresh token, which
 * is exchanged for a fresh access token.
 */
class SessionManager(
    private val store: SessionStore,
    private val server: ServerAddress,
    private val publicApi: () -> BankApi,
    private val api: () -> BankApi,
) {
    @Volatile
    var accessToken: String? = null
        private set

    var fullName: String = ""
        private set
    var email: String = ""
        private set

    /** From the last sign-in; screens show a reminder until it is true. */
    var emailVerified: Boolean = true
        private set

    private val refreshLock = Mutex()
    private val _signedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the session ends unexpectedly (refresh token expired or revoked). */
    val signedOut: SharedFlow<Unit> = _signedOut

    suspend fun startPoint(): StartPoint {
        val s = store.load()
        s.serverUrl?.let { runCatching { server.set(it) } }
        fullName = s.fullName.orEmpty()
        email = s.email.orEmpty()
        return when {
            s.refreshToken == null -> StartPoint.SIGN_IN
            !s.hasPin -> StartPoint.CREATE_PIN
            else -> StartPoint.UNLOCK
        }
    }

    suspend fun currentServer(): String = store.load().serverUrl ?: server.url.toString()

    /**
     * Throws ApiException with code TOTP_REQUIRED when two-step login is on and
     * [totpCode] is missing: the screen then asks for the code and calls again.
     */
    suspend fun signIn(serverUrl: String, email: String, password: String, totpCode: String? = null) {
        useServer(serverUrl)
        val auth = call { publicApi().login(LoginRequest(email.trim(), password, totpCode)) }
        accessToken = auth.accessToken
        val me = call { api().me() }
        store.saveLogin(auth.refreshToken, me.fullName, me.email)
        fullName = me.fullName
        this.email = me.email
        emailVerified = me.emailVerified
    }

    suspend fun verifyEmail(code: String) {
        call { publicApi().verifyEmail(VerifyEmailRequest(email, code)).orThrow() }
        emailVerified = true
    }

    suspend fun forgotPassword(serverUrl: String, email: String) {
        useServer(serverUrl)
        call { publicApi().forgotPassword(ForgotPasswordRequest(email.trim())).orThrow() }
    }

    suspend fun resetPassword(email: String, code: String, newPassword: String) =
        call { publicApi().resetPassword(ResetPasswordRequest(email.trim(), code, newPassword)).orThrow() }

    /** Refreshes the cached profile flags (e.g. after unlocking with the PIN). */
    suspend fun refreshProfile() {
        runCatching { call { api().me() } }.onSuccess { me ->
            fullName = me.fullName
            emailVerified = me.emailVerified
        }
    }

    suspend fun register(serverUrl: String, fullName: String, email: String, password: String) {
        useServer(serverUrl)
        call { publicApi().register(RegisterRequest(fullName.trim(), email.trim(), password)) }
        signIn(serverUrl, email, password)
    }

    suspend fun createPin(pin: String) = store.savePin(pin)

    sealed interface UnlockResult {
        data object Ok : UnlockResult
        data class WrongPin(val triesLeft: Int) : UnlockResult
        data object LockedOut : UnlockResult
    }

    /** After [MAX_PIN_FAILURES] wrong PINs the session is wiped and the password is needed again. */
    suspend fun unlock(pin: String): UnlockResult {
        if (!store.checkPin(pin)) {
            val failures = store.load().pinFailures
            if (failures >= MAX_PIN_FAILURES) {
                endSession(notify = false)
                return UnlockResult.LockedOut
            }
            return UnlockResult.WrongPin(MAX_PIN_FAILURES - failures)
        }
        refreshAccessToken(staleToken = accessToken)
            ?: throw IllegalStateException("Session expired")
        return UnlockResult.Ok
    }

    /**
     * Gets a new access token. Several requests can fail with 401 at the same moment;
     * the lock makes only the first one refresh, the others reuse its result.
     * Returns null when the session can't be renewed (the user must sign in again).
     */
    suspend fun refreshAccessToken(staleToken: String?): String? = refreshLock.withLock {
        val current = accessToken
        if (current != null && current != staleToken) return current
        val refresh = store.load().refreshToken ?: return null
        return try {
            val auth = withContext(Dispatchers.IO) {
                publicApi().refreshBlocking(RefreshRequest(refresh)).execute()
            }.let { response ->
                if (!response.isSuccessful) throw HttpException(response)
                response.body() ?: throw IllegalStateException("Empty refresh response")
            }
            store.saveRefreshToken(auth.refreshToken) // rotated: the old one no longer works
            accessToken = auth.accessToken
            auth.accessToken
        } catch (e: HttpException) {
            if (e.code() == 401) endSession(notify = true)
            null
        }
    }

    suspend fun signOut() {
        store.load().refreshToken?.let { token ->
            runCatching { publicApi().logout(RefreshRequest(token)) } // best effort; we forget it anyway
        }
        endSession(notify = false)
    }

    private suspend fun endSession(notify: Boolean) {
        accessToken = null
        store.clearSession()
        if (notify) _signedOut.tryEmit(Unit)
    }

    private suspend fun useServer(serverUrl: String) {
        server.set(serverUrl)
        store.saveServer(server.url.toString())
    }

    companion object {
        const val MAX_PIN_FAILURES = 5
    }
}
