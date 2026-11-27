package cam.docscan.engine

import android.graphics.Bitmap
import cam.docscan.data.PageEntity
import cam.docscan.data.PageRepository
import cam.docscan.engine.api.EnhancePreset
import cam.docscan.engine.api.Quad
import cam.docscan.engine.impl.DefaultDocScanEngine
import cam.docscan.engine.runtime.PageWorkStore
import cam.docscan.engine.runtime.WorkJob
import cam.docscan.engine.runtime.WorkPriority
import cam.docscan.engine.runtime.WorkQueue
import java.io.File
import java.util.UUID

data class EnqueuedPageWork(
    val pageId: String,
    val groupId: String,
    val jobIds: List<String>,
)

/**
 * Save-path orchestration via shared [WorkQueue] stepped jobs.
 * New capture/import → [WorkPriority.FreshScan]; re-edit → [WorkPriority.UserSave].
 */
class DocumentPipeline(
    private val engine: DefaultDocScanEngine,
    private val repo: PageRepository,
    private val store: PageWorkStore,
    val queue: WorkQueue,
    private val modelsDir: File? = null,
) {
    suspend fun ensureInitialized() {
        if (!engine.isReady()) engine.initialize(modelsDir)
    }

    /**
     * Enqueue stepped Save NLP. Returns immediately with pageId + groupId.
     */
    suspend fun enqueuePage(
        image: Bitmap,
        batchId: String? = null,
        quad: Quad? = null,
        preset: EnhancePreset = EnhancePreset.DOCUMENT,
        imagePath: String,
        source: String = "camera",
        sortIndex: Int = 0,
        pageId: String = UUID.randomUUID().toString(),
        freshScan: Boolean = true,
        needDetect: Boolean = quad == null,
        needEnhance: Boolean = preset != EnhancePreset.ORIGINAL,
    ): EnqueuedPageWork {
        ensureInitialized()
        val priority = if (freshScan) WorkPriority.FreshScan else WorkPriority.UserSave
        val groupId = UUID.randomUUID().toString()
        val groupAt = System.nanoTime()
        val now = System.currentTimeMillis()
        val entity = PageEntity(
            id = pageId,
            batchId = batchId,
            name = "Scan",
            source = source,
            imagePath = imagePath,
            createdAt = now,
            sortIndex = sortIndex,
        )
        store.put(
            PageWorkStore.State(
                entity = entity,
                sourceBitmap = image,
                workingBitmap = image,
                quad = quad,
                preset = preset,
            ),
        )
        repo.insert(entity)

        val jobs = buildList {
            if (needEnhance) {
                add(WorkJob.EnhancePage(priority = priority, pageId = pageId, groupId = groupId))
            }
            if (needDetect) {
                add(WorkJob.DetectPage(priority = priority, pageId = pageId, groupId = groupId))
            }
            add(WorkJob.OcrPage(priority = priority, pageId = pageId, groupId = groupId))
            add(WorkJob.LangPage(priority = priority, pageId = pageId, groupId = groupId))
            add(WorkJob.ClassifyPage(priority = priority, pageId = pageId, groupId = groupId))
            add(WorkJob.SummarizePage(priority = priority, pageId = pageId, groupId = groupId))
            add(WorkJob.WriteMetadata(priority = priority, pageId = pageId, groupId = groupId))
        }
        val ids = queue.enqueueChain(jobs, groupEnqueuedAt = groupAt)
        return EnqueuedPageWork(pageId, groupId, ids)
    }

    fun enqueueBatchSummary(batchId: String, pageIds: List<String>): String =
        queue.enqueue(
            WorkJob.SummarizeBatch(
                priority = WorkPriority.UserSave,
                groupId = batchId,
                batchId = batchId,
                pageIds = pageIds,
            ),
        )

    fun pageState(pageId: String): PageWorkStore.State? = store.get(pageId)

    fun cancelPage(pageId: String) = queue.cancelPage(pageId)
}
