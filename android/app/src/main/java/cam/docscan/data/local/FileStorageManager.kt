package cam.docscan.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Manages app-private document image files under filesDir/docscan/pages.
 */
class FileStorageManager(private val context: Context) {
    private val pagesDir: File
        get() = File(context.filesDir, "docscan/pages").apply { mkdirs() }

    private val exportsDir: File
        get() = File(context.cacheDir, "docscan/exports").apply { mkdirs() }

    fun saveBitmap(pageId: String, bitmap: Bitmap, quality: Int = 92): File {
        val out = File(pagesDir, "$pageId.jpg")
        FileOutputStream(out).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        }
        return out
    }

    fun copyUriToPageFile(pageId: String, uri: Uri): File? = runCatching {
        val out = File(pagesDir, "$pageId.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val decoded = BitmapFactory.decodeStream(input) ?: return null
            FileOutputStream(out).use { output ->
                decoded.compress(Bitmap.CompressFormat.JPEG, 92, output)
            }
            decoded.recycle()
        } ?: return null
        out
    }.getOrNull()

    fun createExportFile(fileName: String): File = File(exportsDir, fileName)

    fun deletePageFile(imagePath: String) {
        if (imagePath.isNotBlank()) {
            runCatching { File(imagePath).delete() }
        }
    }

    fun clearAllFiles() {
        runCatching { pagesDir.deleteRecursively() }
        runCatching { exportsDir.deleteRecursively() }
    }
}
