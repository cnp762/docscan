package cam.docscan.backup

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * Google Drive (`drive.file`) & Google Photos (`photoslibrary.appendonly`) backup helper.
 */
class CloudBackupManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val driveScope = Scope("https://www.googleapis.com/auth/drive.file")
    private val photosScope = Scope("https://www.googleapis.com/auth/photoslibrary.appendonly")

    fun buildSignInIntent(includePhotos: Boolean): Intent {
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(driveScope)
        if (includePhotos) {
            gsoBuilder.requestScopes(photosScope)
        }
        return GoogleSignIn.getClient(context, gsoBuilder.build()).signInIntent
    }

    fun getSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    suspend fun revokeAll() = withContext(Dispatchers.IO) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        runCatching { GoogleSignIn.getClient(context, gso).revokeAccess().await() }
    }

    suspend fun uploadImageToDrive(file: File, displayName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val account = getSignedInAccount()?.account
                ?: throw IllegalStateException("Google account not connected")
            val token = GoogleAuthUtil.getToken(
                context,
                account,
                "oauth2:https://www.googleapis.com/auth/drive.file",
            )
            val metadataJson = """{"name":"$displayName","mimeType":"image/jpeg"}"""
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(
                    metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType()),
                )
                .addPart(
                    file.asRequestBody("image/jpeg".toMediaType()),
                )
                .build()

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $token")
                .post(multipart)
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Drive upload failed (${resp.code})")
                }
            }
        }
    }

    suspend fun uploadImageToPhotos(file: File, displayName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val account = getSignedInAccount()?.account
                ?: throw IllegalStateException("Google account not connected")
            val token = GoogleAuthUtil.getToken(
                context,
                account,
                "oauth2:https://www.googleapis.com/auth/photoslibrary.appendonly",
            )
            val uploadReq = Request.Builder()
                .url("https://photoslibrary.googleapis.com/v1/uploads")
                .header("Authorization", "Bearer $token")
                .header("Content-type", "application/octet-stream")
                .header("X-Goog-Upload-Content-Type", "image/jpeg")
                .header("X-Goog-Upload-Protocol", "raw")
                .post(file.asRequestBody("application/octet-stream".toMediaType()))
                .build()

            val uploadToken = client.newCall(uploadReq).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Photos upload failed (${resp.code})")
                resp.body?.string().orEmpty()
            }

            val createPayload = """
                {"newMediaItems":[{"description":"$displayName","simpleMediaItem":{"fileName":"$displayName","uploadToken":"$uploadToken"}}]}
            """.trimIndent()
            val createReq = Request.Builder()
                .url("https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate")
                .header("Authorization", "Bearer $token")
                .post(createPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(createReq).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Photos batchCreate failed (${resp.code})")
            }
        }
    }
}
