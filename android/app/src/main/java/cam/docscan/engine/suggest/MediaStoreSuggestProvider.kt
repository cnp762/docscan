package cam.docscan.engine.suggest

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real MediaStore-backed 7-day photo suggest provider.
 */
class MediaStoreSuggestProvider(
    private val context: Context,
) : SuggestMediaProvider {

    override suspend fun loadChunk(cursor: String?, limit: Int): List<SuggestMediaItem> =
        withContext(Dispatchers.IO) {
            val sevenDaysAgoSec = (System.currentTimeMillis() / 1000L) - SEVEN_DAYS_SEC
            val offset = cursor?.toIntOrNull() ?: 0
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.DISPLAY_NAME,
            )
            val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
            val selectionArgs = arrayOf(sevenDaysAgoSec.toString())
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            val items = mutableListOf<SuggestMediaItem>()
            runCatching {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder,
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val wCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                    val hCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                    var skipped = 0
                    while (c.moveToNext() && items.size < limit) {
                        if (skipped < offset) {
                            skipped++
                            continue
                        }
                        val id = c.getLong(idCol)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id,
                        )
                        items += SuggestMediaItem(
                            mediaId = contentUri.toString(),
                            takenAtMs = c.getLong(dateCol) * 1000L,
                            width = c.getInt(wCol),
                            height = c.getInt(hCol),
                        )
                    }
                }
            }
            items
        }

    override suspend fun loadThumb(mediaId: String): Bitmap? =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(mediaId)
            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                var sample = 1
                while (opts.outWidth / sample > THUMB_MAX_PX || opts.outHeight / sample > THUMB_MAX_PX) {
                    sample *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                }
            }.getOrNull()
        }

    companion object {
        private const val SEVEN_DAYS_SEC = 7L * 86_400L
        private const val THUMB_MAX_PX = 384
    }
}
