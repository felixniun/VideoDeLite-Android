package com.videodelite.app.network

import android.content.Context
import com.videodelite.app.BuildConfig
import com.videodelite.app.data.DeviceId
import com.videodelite.app.data.Session
import com.videodelite.app.data.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** License state as reported by GET /license/status. */
enum class LicenseState { LOADING, ACTIVE, NONE, REVOKED, EXPIRED }

data class AccountState(
    val loggedIn: Boolean = false,
    val username: String = "",
    val email: String = "",
    val license: LicenseState = LicenseState.NONE,
    val activating: Boolean = false,
) {
    companion object {
        val LOGGED_OUT = AccountState()
    }
}

data class AccountDevice(
    val id: Long,
    val installationId: String,
    val appVersion: String?,
    val status: String,
    val lastSeen: String?,
)

/**
 * Account + device + license flows against the production account service,
 * mirroring the desktop's internal/account client: login stores the token
 * pair, every session activates its device (idempotent) and refresh uses
 * single-use rotation through the OkHttp authenticator.
 */
class AccountClient(
    private val api: VdApi,
    private val rawApi: VdApi,
    private val tokens: TokenStore,
    private val authStack: AuthStack,
    context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(AccountState.LOGGED_OUT)
    val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        // Restore a persisted session (if any) and re-register this device in
        // the background. Sign-in already unlocks Professional mode (V1: the
        // server auto-issues a license to every verified account), so device
        // activation never blocks the UI — it only refreshes the device list.
        scope.launch {
            val session = tokens.load() ?: return@launch
            _state.value = AccountState(
                loggedIn = true,
                username = session.username,
                email = session.email,
                license = LicenseState.ACTIVE,
                activating = false,
            )
            runCatching { activateDevice() }
        }
    }

    fun session(): Session? = tokens.load()

    suspend fun login(email: String, password: String) {
        val resp = apiCall { rawApi.login(LoginRequest(email.trim().lowercase(), password)) }
        val account = resp.account ?: throw ApiException(-1, "login response missing account")
        tokens.save(
            Session(
                accessToken = resp.accessToken,
                refreshToken = resp.refreshToken,
                expiresAtMs = System.currentTimeMillis() + resp.expiresIn * 1000L,
                accountId = account.id,
                username = account.username,
                email = account.email,
            )
        )
        // Sign-in unlocks Professional mode immediately; device activation
        // runs in the background and only refreshes the device list (its
        // failure downgrades the *displayed* license but not the unlock).
        _state.value = AccountState(
            loggedIn = true,
            username = account.username,
            email = account.email,
            license = LicenseState.ACTIVE,
            activating = false,
        )
        scope.launch { runCatching { activateDevice() } }
    }

    suspend fun register(username: String, email: String, password: String, inviteCode: String?): RegisterResponse =
        apiCall {
            rawApi.register(RegisterRequest(username.trim(), email.trim().lowercase(), password, inviteCode?.trim()?.takeIf { it.isNotEmpty() }))
        }

    suspend fun verifyAndLogin(email: String, code: String, password: String) {
        apiCall { rawApi.verifyEmail(VerifyEmailRequest(email.trim().lowercase(), code.trim())) }
        login(email, password)
    }

    suspend fun resendVerification(email: String): String? {
        val resp = apiCall { rawApi.resendVerification(ResendVerificationRequest(email.trim().lowercase())) }
        return resp.message
    }

    /**
     * Background device registration (idempotent; keeps lastSeen fresh).
     *
     * Professional mode is already unlocked by sign-in (V1: the server
     * auto-issues a license to every verified account), so a transient failure
     * here — network trouble, or the server's device/license tables hanging —
     * must NOT flip the account back to "not activated". Only an explicit 403
     * (revoked device / banned account) locks the feature.
     */
    suspend fun activateDevice(): LicenseState {
        val fallback = _state.value.license
        if (tokens.load() == null) {
            _state.value = _state.value.copy(license = LicenseState.NONE, activating = false)
            return LicenseState.NONE
        }
        // Every branch clears `activating`, otherwise the UI spins forever on
        // "device activating" (a transport/parse failure used to leave it set).
        return try {
            val resp = apiCall {
                api.activate(ActivateRequest(DeviceId.get(appContext), BuildConfig.VERSION_NAME))
            }
            val license = if (resp.status == "authorized") LicenseState.ACTIVE else fallback
            _state.value = _state.value.copy(license = license, activating = false)
            license
        } catch (e: ApiException) {
            val license = if (e.code == 403) LicenseState.REVOKED else fallback
            android.util.Log.w("VdAccount", "activate failed: HTTP ${e.code} ${e.message}")
            _state.value = _state.value.copy(license = license, activating = false)
            license
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Transient (network / timeout / parse): keep the sign-in state.
            android.util.Log.w("VdAccount", "activate failed: ${e.javaClass.simpleName} ${e.message}")
            _state.value = _state.value.copy(license = fallback, activating = false)
            fallback
        }
    }

    suspend fun refreshLicense(): LicenseState {
        val session = tokens.load() ?: return LicenseState.NONE
        return try {
            authStack.ensureFreshToken()
            val resp = apiCall { api.licenseStatus(DeviceId.get(appContext)) }
            val license = when (resp.status) {
                "active" -> LicenseState.ACTIVE
                "revoked" -> LicenseState.REVOKED
                "expired" -> LicenseState.EXPIRED
                else -> LicenseState.NONE
            }
            _state.value = _state.value.copy(license = license)
            license
        } catch (e: ApiException) {
            _state.value = _state.value.copy(license = LicenseState.NONE)
            LicenseState.NONE
        }
    }

    suspend fun listDevices(): List<AccountDevice> {
        authStack.ensureFreshToken()
        val resp = apiCall { api.listDevices() }
        return resp.devices.map {
            AccountDevice(it.id, it.installationId, it.appVersion, it.status, it.lastSeen)
        }
    }

    suspend fun revokeDevice(id: Long) {
        authStack.ensureFreshToken()
        apiCall { api.revokeDevice(id) }
    }

    suspend fun checkUpdate(): VersionResponse = apiCall { rawApi.version() }

    suspend fun logout() {
        val session = tokens.load()
        if (session != null) {
            runCatching { rawApi.logout(LogoutRequest(session.refreshToken)) }
        }
        tokens.clear()
        _state.value = AccountState.LOGGED_OUT
    }

    suspend fun deleteAccount(password: String) {
        authStack.ensureFreshToken()
        apiCall { api.deleteAccount(DeleteAccountRequest(password)) }
        tokens.clear()
        _state.value = AccountState.LOGGED_OUT
    }
}
