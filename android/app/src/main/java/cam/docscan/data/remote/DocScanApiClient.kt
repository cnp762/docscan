package cam.docscan.data.remote

import cam.docscan.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class DeviceRegisterRequest(
    @SerialName("device_id") val deviceId: String,
    val platform: String = "android",
    @SerialName("app_version") val appVersion: String? = null,
)

@Serializable
data class DeviceRegisterResponse(
    @SerialName("device_id") val deviceId: String,
    val platform: String,
    val token: String,
    @SerialName("registered_at") val registeredAt: String,
)

@Serializable
data class QuotaStatusResponse(
    @SerialName("utc_day") val utcDay: String,
    val used: Int,
    val limit: Int? = null,
    val remaining: Int? = null,
    @SerialName("is_pro") val isPro: Boolean,
)

@Serializable
data class QuotaConsumeRequest(val n: Int)

@Serializable
data class BillingVerifyRequest(
    val store: String = "google",
    @SerialName("product_id") val productId: String,
    @SerialName("receipt_or_token") val receiptOrToken: String,
)

@Serializable
data class EntitlementResponse(
    @SerialName("is_pro") val isPro: Boolean,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

sealed interface QuotaConsumeResult {
    data class Allowed(val status: QuotaStatusResponse) : QuotaConsumeResult
    data class QuotaExceeded(val detail: String) : QuotaConsumeResult
    data class NetworkError(val cause: Throwable) : QuotaConsumeResult
}

class DocScanApiException(val code: Int, override val message: String) : IOException(message)

class DocScanApiClient(
    rawBaseUrl: String = BuildConfig.API_BASE_URL,
    private val identity: DeviceIdentityProvider,
    private val appVersion: String = BuildConfig.VERSION_NAME,
    private val client: OkHttpClient = defaultOkHttpClient(),
) {
    private val baseV1Url: String = normalizeV1BaseUrl(rawBaseUrl)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ensureRegistered(): Result<DeviceRegisterResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val deviceId = identity.getOrCreateDeviceId()
            val body = json.encodeToString(
                DeviceRegisterRequest(
                    deviceId = deviceId,
                    platform = "android",
                    appVersion = appVersion,
                ),
            ).toRequestBody(JSON_MEDIA)

            val request = Request.Builder()
                .url("$baseV1Url/devices/register")
                .post(body)
                .build()

            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw DocScanApiException(resp.code, "register failed (${resp.code}): $text")
                }
                val parsed = json.decodeFromString<DeviceRegisterResponse>(text)
                identity.saveToken(parsed.token)
                parsed
            }
        }
    }

    suspend fun getQuotaToday(): Result<QuotaStatusResponse> = withContext(Dispatchers.IO) {
        runCatching {
            ensureTokenIfMissing()
            executeAuthedGet("$baseV1Url/quota/today") { text ->
                json.decodeFromString<QuotaStatusResponse>(text)
            }
        }
    }

    suspend fun consumeQuota(n: Int): QuotaConsumeResult = withContext(Dispatchers.IO) {
        try {
            ensureTokenIfMissing()
            val payload = json.encodeToString(QuotaConsumeRequest(n = n)).toRequestBody(JSON_MEDIA)
            val request = authedBuilder("$baseV1Url/quota/consume").post(payload).build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.isSuccessful -> {
                        QuotaConsumeResult.Allowed(json.decodeFromString<QuotaStatusResponse>(text))
                    }
                    resp.code == 402 -> {
                        QuotaConsumeResult.QuotaExceeded(text.ifBlank { "quota_exceeded" })
                    }
                    resp.code == 401 -> {
                        ensureRegistered().getOrThrow()
                        val retry = authedBuilder("$baseV1Url/quota/consume").post(payload).build()
                        client.newCall(retry).execute().use { r2 ->
                            val t2 = r2.body?.string().orEmpty()
                            if (r2.isSuccessful) {
                                QuotaConsumeResult.Allowed(json.decodeFromString<QuotaStatusResponse>(t2))
                            } else if (r2.code == 402) {
                                QuotaConsumeResult.QuotaExceeded(t2.ifBlank { "quota_exceeded" })
                            } else {
                                QuotaConsumeResult.NetworkError(DocScanApiException(r2.code, t2))
                            }
                        }
                    }
                    else -> QuotaConsumeResult.NetworkError(DocScanApiException(resp.code, text))
                }
            }
        } catch (t: Throwable) {
            QuotaConsumeResult.NetworkError(t)
        }
    }

    suspend fun verifyBilling(productId: String, purchaseToken: String): Result<EntitlementResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureTokenIfMissing()
                val payload = json.encodeToString(
                    BillingVerifyRequest(
                        store = "google",
                        productId = productId,
                        receiptOrToken = purchaseToken,
                    ),
                ).toRequestBody(JSON_MEDIA)
                val request = authedBuilder("$baseV1Url/billing/verify").post(payload).build()
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw DocScanApiException(resp.code, "billing verify failed (${resp.code}): $text")
                    }
                    json.decodeFromString<EntitlementResponse>(text)
                }
            }
        }

    suspend fun getEntitlement(): Result<EntitlementResponse> = withContext(Dispatchers.IO) {
        runCatching {
            ensureTokenIfMissing()
            executeAuthedGet("$baseV1Url/billing/entitlement") { text ->
                json.decodeFromString<EntitlementResponse>(text)
            }
        }
    }

    suspend fun deleteDeviceMe(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureTokenIfMissing()
            val request = authedBuilder("$baseV1Url/devices/me").delete().build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 404) {
                    throw DocScanApiException(resp.code, "delete device failed (${resp.code})")
                }
            }
            identity.clearAll()
        }
    }

    private suspend fun ensureTokenIfMissing() {
        if (identity.getToken().isNullOrBlank()) {
            ensureRegistered().getOrThrow()
        }
    }

    private suspend fun <T> executeAuthedGet(url: String, decode: (String) -> T): T {
        val req = authedBuilder(url).get().build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 401) {
                ensureRegistered().getOrThrow()
                val retry = authedBuilder(url).get().build()
                client.newCall(retry).execute().use { r2 ->
                    val t2 = r2.body?.string().orEmpty()
                    if (!r2.isSuccessful) throw DocScanApiException(r2.code, t2)
                    return decode(t2)
                }
            }
            if (!resp.isSuccessful) throw DocScanApiException(resp.code, text)
            return decode(text)
        }
    }

    private fun authedBuilder(url: String): Request.Builder {
        val deviceId = identity.getOrCreateDeviceId()
        val token = identity.getToken()
        return Request.Builder()
            .url(url)
            .header("X-Device-Id", deviceId)
            .apply {
                if (!token.isNullOrBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun normalizeV1BaseUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            return if (trimmed.endsWith("/v1")) trimmed else "$trimmed/v1"
        }

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
