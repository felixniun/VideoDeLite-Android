package com.videodelite.app.network

import kotlinx.serialization.Serializable

/**
 * Wire models mirroring the Go server's JSON (server/handlers.go).
 * Field names must match exactly; unknown fields are ignored in the Json
 * config so server additions never break old clients.
 */
@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val inviteCode: String? = null,
)

@Serializable
data class RegisterResponse(
    val accountId: Long = 0,
    val message: String? = null,
    val warning: String? = null,
    val sendError: String? = null,
    val devVerificationCode: String? = null,
)

@Serializable
data class VerifyEmailRequest(val email: String, val code: String)

@Serializable
data class VerifyEmailResponse(val verified: Boolean = false)

@Serializable
data class ResendVerificationRequest(val email: String)

@Serializable
data class MessageResponse(val message: String? = null)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String)

@Serializable
data class AccountDto(val id: Long, val username: String, val email: String)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int = 900,
    val account: AccountDto? = null,
)

@Serializable
data class ActivateRequest(val installationId: String, val appVersion: String)

@Serializable
data class ActivationResponse(
    val status: String,
    val installationId: String? = null,
    val licenseType: String? = null,
    val authorizedAt: String? = null,
    val expiresAt: String? = null,
)

@Serializable
data class DeviceDto(
    val id: Long,
    val installationId: String = "",
    val appVersion: String? = null,
    val status: String = "",
    val createdAt: String? = null,
    val lastSeen: String? = null,
)

@Serializable
data class DevicesResponse(val devices: List<DeviceDto> = emptyList())

@Serializable
data class LicenseStatusResponse(val status: String, val expiresAt: String? = null)

@Serializable
data class DeleteAccountRequest(val password: String)

@Serializable
data class OkResponse(val ok: Boolean = false)

@Serializable
data class VersionResponse(val latest: String = "", val channel: String = "")

@Serializable
data class ErrorResponse(val error: String? = null)
