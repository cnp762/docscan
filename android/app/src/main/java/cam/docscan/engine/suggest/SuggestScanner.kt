package cam.docscan.engine.suggest

import cam.docscan.engine.runtime.WorkJob
import cam.docscan.engine.runtime.WorkPriority
import cam.docscan.engine.runtime.WorkQueue

/**
 * Enqueues 7-day library suggest work onto the shared [WorkQueue] in chunks.
 */
class SuggestScanner(
    private val queue: WorkQueue,
    private val cache: SuggestCache = SuggestCache(),
    private val chunkSize: Int = 20,
) {
    /** Start (or continue) a suggest scan from [cursor]. */
    fun enqueueScan(cursor: String? = null): String =
        queue.enqueue(
            WorkJob.SuggestScanChunk(
                priority = WorkPriority.Suggest,
                cursor = cursor,
                chunkSize = chunkSize,
            ),
        )

    fun enqueueScore(mediaId: String, takenAtMs: Long): String =
        queue.enqueue(
            WorkJob.SuggestScoreItem(
                priority = WorkPriority.Suggest,
                mediaId = mediaId,
                takenAtMs = takenAtMs,
            ),
        )

    fun cancel() = queue.cancelSuggest()

    fun topCandidates(minScore: Float = 0.45f) = cache.topCandidates(minScore)
}
