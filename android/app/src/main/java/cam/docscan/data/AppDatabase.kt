package cam.docscan.data

/**
 * Local page model. Room wiring lands in a later library slice.
 */
data class PageEntity(
    val id: String,
    val batchId: String?,
    val name: String,
    val source: String,
    val imagePath: String,
    val createdAt: Long,
    val ocrText: String? = null,
    val ocrLang: String? = null,
    val ocrLangConfidence: Float? = null,
    val docType: String? = null,
    val docTypeConfidence: Float? = null,
    val docTypesAlt: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val summary: String? = null,
    val summarySource: String? = null,
    val nlpVersion: String? = null,
    val metadataWrittenAt: Long? = null,
    val sortIndex: Int = 0,
)

data class BatchEntity(
    val id: String,
    val name: String,
    val createdAt: Long,
    val tags: List<String> = emptyList(),
    val summary: String? = null,
    val summarySource: String? = null,
)

interface PageRepository {
    suspend fun all(): List<PageEntity>
    suspend fun insert(page: PageEntity)
    suspend fun upsertBatch(batch: BatchEntity)
    suspend fun search(query: String): List<PageEntity>
    suspend fun deletePage(id: String)
    suspend fun clearAll()
}

class InMemoryPageRepository : PageRepository {
    private val pages = mutableListOf<PageEntity>()
    private val batches = mutableMapOf<String, BatchEntity>()

    override suspend fun all(): List<PageEntity> = pages.sortedByDescending { it.createdAt }

    override suspend fun insert(page: PageEntity) {
        pages.removeAll { it.id == page.id }
        pages += page
    }

    override suspend fun upsertBatch(batch: BatchEntity) {
        batches[batch.id] = batch
    }

    override suspend fun search(query: String): List<PageEntity> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all()
        return pages.filter { page ->
            page.name.lowercase().contains(q) ||
                page.ocrText?.lowercase()?.contains(q) == true ||
                page.tags.any { it.lowercase().contains(q) } ||
                page.docType?.lowercase()?.contains(q) == true ||
                page.summary?.lowercase()?.contains(q) == true
        }.sortedByDescending { it.createdAt }
    }

    override suspend fun deletePage(id: String) {
        pages.removeAll { it.id == id }
    }

    override suspend fun clearAll() {
        pages.clear()
        batches.clear()
    }
}
