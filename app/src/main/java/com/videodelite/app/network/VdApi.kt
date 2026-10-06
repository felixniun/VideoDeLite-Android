package com.videodelite.app.network

import com.videodelite.app.BuildConfig
import com.videodelite.app.data.TokenStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit

interface VdApi {

    // public
    @POST("api/v1/accounts/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @POST("api/v1/accounts/verify-email")
    suspend fun verifyEmail(@Body body: VerifyEmailRequest): VerifyEmailResponse

    @POST("api/v1/accounts/resend-verification")
    suspend fun resendVerification(@Body body: ResendVerificationRequest): MessageResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenResponse

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body body: LogoutRequest): OkResponse

    @GET("api/v1/version")
    suspend fun version(): VersionResponse

    // authenticated (Authorization header attached by the interceptor)
    @POST("api/v1/devices/activate")
    suspend fun activate(@Body body: ActivateRequest): ActivationResponse

    @GET("api/v1/devices")
    suspend fun listDevices(): DevicesResponse

    @DELETE("api/v1/devices/{id}")
    suspend fun revokeDevice(@Path("id") id: Long): OkResponse

    @GET("api/v1/license/status")
    suspend fun licenseStatus(@Query("installationId") installationId: String): LicenseStatusResponse

    @POST("api/v1/accounts/me/delete")
    suspend fun deleteAccount(@Body body: DeleteAccountRequest): OkResponse
}

/** Typed API error carrying the server's {"error": "..."} message. */
class ApiException(val code: Int, message: String) : Exception(message)

fun HttpException.errorMessage(): String {
    val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
    if (body != null) {
        val parsed = runCatching { ApiJson.decodeFromString<ErrorResponse>(body) }.getOrNull()
        parsed?.error?.let { return it }
    }
    return "HTTP ${code()}"
}

/** Maps transport errors to user-facing messages. */
suspend fun <T> apiCall(block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    throw ApiException(e.code(), e.errorMessage())
} catch (e: IOException) {
    throw ApiException(0, "network")
}

val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    coerceInputValues = true
}

private fun buildRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
    .baseUrl("${BuildConfig.API_BASE}/")
    .client(client)
    .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
    .build()

/**
 * OkHttp stack with bearer injection and single-flight refresh on 401
 * (the server rotates refresh tokens, so the new pair is persisted before
 * any request retries — same flow as internal/account on the desktop).
 */
class AuthStack(private val tokens: TokenStore, private val rawApi: VdApi) {

    private val refreshMutex = Mutex()

    /** Returns true when a fresh access token is available in the store. */
    suspend fun refreshNow(): Boolean = refreshMutex.withLock {
        val session = tokens.load() ?: return@withLock false
        try {
            val resp = rawApi.refresh(RefreshRequest(session.refreshToken))
            tokens.updateTokens(
                resp.accessToken, resp.refreshToken,
                System.currentTimeMillis() + resp.expiresIn * 1000L,
            )
            true
        } catch (e: HttpException) {
            if (e.code() == 401) tokens.clear() // refresh rejected: force logout
            false
        } catch (e: IOException) {
            false
        }
    }

    /** Pre-flight: refresh shortly before expiry so most requests never 401. */
    suspend fun ensureFreshToken(): Boolean {
        val session = tokens.load() ?: return false
        if (System.currentTimeMillis() < session.expiresAtMs - 60_000L) return true
        return refreshNow()
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val token = tokens.load()?.accessToken
            val request = if (token != null) {
                chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        .authenticator { _, response ->
            val original = response.request
            if (original.header("Authorization") == null) return@authenticator null
            if (original.header(RETRY_HEADER) != null) return@authenticator null
            val ok = runBlocking { refreshNow() }
            if (!ok) return@authenticator null
            val token = tokens.load()?.accessToken ?: return@authenticator null
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .header(RETRY_HEADER, "1")
                .build()
        }
        .build()

    private companion object {
        const val RETRY_HEADER = "X-VD-Auth-Retry"
    }
}

/** Retrofit factories: [rawApi] has no auth stack (login/refresh/logout). */
object ApiFactory {

    fun rawClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun api(client: OkHttpClient): VdApi = buildRetrofit(client).create(VdApi::class.java)
}
