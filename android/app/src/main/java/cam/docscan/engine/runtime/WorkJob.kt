package cam.docscan.engine.runtime

import java.util.UUID

/**
 * Typed background jobs for the shared WorkQueue.
 * Payload fields stay light; heavy bitmaps live in [PageWorkStore] keyed by pageId.
 */
sealed class WorkJob {
    abstract val id: String
    abstract val priority: WorkPriority
    abstract val enqueuedAt: Long
    abstract val pageId: String?
    abstract val groupId: String?
    abstract val timeoutMs: Long

    data class EnhancePage(
        override val id: String = UUID.randomUUID().toString(),
        override val priority: WorkPriority = WorkPriority.FreshScan,
        override val enqueuedAt: Long = System.nanoTime(),
        override val pageId: String,
        override val groupId: String?,
        override val timeoutMs: Long = WorkTimeouts.ENHANCE_DETECT_MS,
    ) : WorkJob()

    data class DetectPage(
        override val id: String = UUID.randomUUID().toString(),
        override val priority: WorkPriority = WorkPriority.FreshScan,
        override val enqueuedAt: Long = System.nanoTime(),
        override val pageId: String,
        override val groupId: String?,
        override val timeoutMs: Long = WorkTimeouts.ENHANCE_DETECT_MS,
    ) : WorkJob()

    data class OcrPage(
        override val id: String = UUID.randomUUID().toString(),
        override val priority: WorkPriority = WorkPriority.FreshScan,
        override val enqueuedAt: Long = System.nanoTime(),
        override val pageId: String,
        override val groupId: String?,
        override val timeoutMs: Long = WorkTimeouts.OCR_MS,
    ) : WorkJob()

    data class LangPage(
        override val id: String = UUID.randomUUID().toString(),
        override val priority: WorkPriority = WorkPriority.FreshScan,
        override val enqueuedAt: Long = System.nanoTime(),
        override val pageId: String,
        override val groupId: String?,
        override val timeoutMs: Long = WorkTimeouts.LANG_MS,
    ) : WorkJob()

    data class ClassifyPage(
        override val id: String = UUID.randomUUID().toString(),
        override val priority: WorkPriority = WorkPriority.FreshScan,
        override val enqueuedAt: Long = System.nanoTime(),
        override val pageId: String,
        override val groupId: String?,
        override val timeoutMs: Long = WorkTimeouts.CLASSIFY_MS,
    ) : WorkJob()

    data class SummarizePage(
        override val id: String = UUID.randomUUID().toString(),
        override val priority: WorkPriority = WorkPriority.FreshScan,
        override val enqueuedAt: Long = System.nanoTime(),
        override val pageId: String,
        override val groupId: String?,
        override val timeoutMs: Long = WorkTimeouts.SUMMARY_GENERATIVE_MS,
    ) : WorkJob()

    data class WriteMetadata(
        override val id: String = UUID.randomUUID().toString(),
        override val priority: WorkPriority = WorkPriority.FreshScan,
        override val enqueuedAt: Long = System.nanoTime(),
        override val pageId: String,
        override val groupId: String?,
        override val timeoutMs: Long = WorkTimeouts.METADATA_MS,
    ) : WorkJob()

    data class SummarizeBatch(
        override val id: String = UUID.randomUUID().toString(),
        override val priority: WorkPriority = WorkPriority.UserSave,
        override val enqueuedAt: Long = System.nanoTime(),
        override val pageId: String? = null,
        override val groupId: String?,
        val batchId: String,
        val pageIds: List<String>,
        override val timeoutMs: Long = WorkTimeouts.SUMMARY_GENERATIVE_MS,
    ) : WorkJob()

    data class SuggestScanChunk(
        override val id: String = UUID.randomUUID().toString(),
        override val priority: WorkPriority = WorkPriority.Suggest,
        override val enqueuedAt: Long = System.nanoTime(),
        override val pageId: String? = null,
        override val groupId: String? = null,
        val cursor: String? = null,
        val chunkSize: Int = 20,
        override val timeoutMs: Long = WorkTimeouts.SUGGEST_MS,
    ) : WorkJob()

    data class SuggestScoreItem(
        override val id: String = UUID.randomUUID().toString(),
        override val priority: WorkPriority = WorkPriority.Suggest,
        override val enqueuedAt: Long = System.nanoTime(),
        override val pageId: String? = null,
        override val groupId: String? = null,
        val mediaId: String,
        val takenAtMs: Long,
        override val timeoutMs: Long = WorkTimeouts.SUGGEST_MS,
    ) : WorkJob()
}

object WorkTimeouts {
    const val SUGGEST_MS = 5_000L
    const val ENHANCE_DETECT_MS = 8_000L
    const val OCR_MS = 15_000L
    const val LANG_MS = 2_000L
    const val CLASSIFY_MS = 2_000L
    const val SUMMARY_EXTRACTIVE_MS = 2_000L
    const val SUMMARY_GENERATIVE_MS = 8_000L
    const val METADATA_MS = 3_000L
}
