package cam.docscan.engine.suggest

import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap

data class SuggestMediaItem(
    val mediaId: String,
    val takenAtMs: Long,
    val width: Int = 0,
    val height: Int = 0,
)

interface SuggestMediaProvider {
    /** Load next chunk of media from the last 7 days. */
    suspend fun loadChunk(cursor: String?, limit: Int): List<SuggestMediaItem>

    /** Lightweight thumb/bitmap for scoring (caller should recycle if needed). */
    suspend fun loadThumb(mediaId: String): Bitmap?
}

class SuggestCache {
    data class Entry(
        val mediaId: String,
        val takenAtMs: Long,
        val score: Float,
        val dismissedAt: Long? = null,
        val imported: Boolean = false,
    )

    private val map = ConcurrentHashMap<String, Entry>()

    fun upsert(mediaId: String, takenAtMs: Long, score: Float) {
        val prev = map[mediaId]
        map[mediaId] = Entry(
            mediaId = mediaId,
            takenAtMs = takenAtMs,
            score = score,
            dismissedAt = prev?.dismissedAt,
            imported = prev?.imported == true,
        )
    }

    fun markImported(mediaId: String) {
        map.compute(mediaId) { _, v ->
            (v ?: Entry(mediaId, 0, 0f)).copy(imported = true)
        }
    }

    fun dismiss(mediaId: String) {
        map.compute(mediaId) { _, v ->
            (v ?: Entry(mediaId, 0, 0f)).copy(dismissedAt = System.currentTimeMillis())
        }
    }

    fun isDismissed(mediaId: String): Boolean = map[mediaId]?.dismissedAt != null

    fun alreadyImported(mediaId: String): Boolean = map[mediaId]?.imported == true

    fun topCandidates(minScore: Float = 0.45f): List<Entry> =
        map.values
            .filter { it.dismissedAt == null && !it.imported && it.score >= minScore }
            .sortedByDescending { it.score }
}

/**
 * Heuristic document-likeness: aspect + resolution (+ optional edge density later).
 */
class SuggestScorer {
    fun score(bitmap: Bitmap): Float {
        val w = bitmap.width.coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(1)
        val aspect = w.toFloat() / h
        val portraitDoc = aspect in 0.55f..0.85f
        val landscapeDoc = aspect in 1.2f..1.8f
        val aspectScore = when {
            portraitDoc || landscapeDoc -> 0.55f
            aspect in 0.9f..1.1f -> 0.25f
            else -> 0.35f
        }
        val megapixels = (w * h) / 1_000_000f
        val resScore = when {
            megapixels >= 2f -> 0.35f
            megapixels >= 0.5f -> 0.25f
            else -> 0.1f
        }
        return (aspectScore + resScore).coerceIn(0f, 1f)
    }
}

/** Stub provider for tests / until MediaStore wired. */
class EmptySuggestMediaProvider : SuggestMediaProvider {
    override suspend fun loadChunk(cursor: String?, limit: Int): List<SuggestMediaItem> = emptyList()
    override suspend fun loadThumb(mediaId: String): Bitmap? = null
}
