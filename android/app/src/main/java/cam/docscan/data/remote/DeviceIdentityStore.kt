package cam.docscan.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

interface DeviceIdentityProvider {
    fun getOrCreateDeviceId(): String
    fun getToken(): String?
    fun saveToken(token: String)
    fun clearAll()
}

/**
 * Stores the device UUID and server JWT in EncryptedSharedPreferences.
 * Falls back to standard private SharedPreferences only if KeyStore fails on broken OEM devices.
 */
class DeviceIdentityStore(context: Context) : DeviceIdentityProvider {
    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    override fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    override fun getToken(): String? = prefs.getString(KEY_JWT, null)

    override fun saveToken(token: String) {
        prefs.edit().putString(KEY_JWT, token).apply()
    }

    override fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREF_NAME = "docscan_device_identity"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_JWT = "jwt_token"

        private fun createPrefs(context: Context): SharedPreferences =
            runCatching {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrElse {
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            }
    }
}
