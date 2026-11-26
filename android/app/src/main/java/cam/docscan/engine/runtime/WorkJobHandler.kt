package cam.docscan.engine.runtime

import cam.docscan.data.BatchEntity
import cam.docscan.data.PageRepository
import cam.docscan.engine.api.PageNlpMetadata
import cam.docscan.engine.api.SummaryMode
import cam.docscan.engine.api.SummaryResult
import cam.docscan.engine.impl.DefaultDocScanEngine
import cam.docscan.engine.suggest.SuggestCache
import cam.docscan.engine.suggest.SuggestMediaProvider
import cam.docscan.engine.suggest.SuggestScorer

/**
 * Executes one [WorkJob] on the WorkQueue worker thread.
 */
class WorkJobHandler(
    private val engine: DefaultDocScanEngine,
    private val store: PageWorkStore,
    private val repo: PageRepository,
    private val capability: DeviceCapability,
    private val suggestProvider: SuggestMediaProvider? = null,
    private val suggestCache: SuggestCache = SuggestCache(),
    private val suggestScorer: SuggestScorer = SuggestScorer(),
    private val onSuggestChunk: (List<WorkJob.SuggestScoreItem>) -> Unit = {},
) {
    suspend fun handle(job: WorkJob) {
        when (job) {
            is WorkJob.EnhancePage -> enhance(job)
            is WorkJob.DetectPage -> detect(job)
            is WorkJob.OcrPage -> ocr(job)
            is WorkJob.LangPage -> lang(job)
            is WorkJob.ClassifyPage -> classify(job)
            is WorkJob.SummarizePage -> summarizePage(job)
            is WorkJob.WriteMetadata -> metadata(job)
            is WorkJob.SummarizeBatch -> summarizeBatch(job)
            is WorkJob.SuggestScanChunk -> suggestChunk(job)
            is WorkJob.SuggestScoreItem -> suggestScore(job)
        }
    }

    private suspend fun enhance(job: WorkJob.EnhancePage) {
        val st = store.get(job.pageId) ?: return
        val src = st.workingBitmap ?: st.sourceBitmap ?: return
        val out = engine.enhance(src, st.preset)
        store.update(job.pageId) { workingBitmap = out }
    }

    private suspend fun detect(job: WorkJob.DetectPage) {
        val st = store.get(job.pageId) ?: return
        if (st.quad != null) return
        val bmp = st.workingBitmap ?: st.sourceBitmap ?: return
        val q = engine.detectDocument(bmp)
        store.update(job.pageId) { quad = q }
    }

    private suspend fun ocr(job: WorkJob.OcrPage) {
        val st = store.get(job.pageId) ?: return
        val bmp = st.workingBitmap ?: st.sourceBitmap ?: return
        val page = engine.recognize(bmp, st.quad)
        val name = engine.autoname(page, st.entity.tags)
        store.update(job.pageId) {
            ocr = page
            entity = entity.copy(
                ocrText = page.text,
                name = name.ifBlank { entity.name },
            )
        }
        repo.insert(store.get(job.pageId)!!.entity)
    }

    private suspend fun lang(job: WorkJob.LangPage) {
        val st = store.get(job.pageId) ?: return
        val text = st.ocr?.text ?: st.entity.ocrText ?: return
        val result = engine.detectLanguage(text, st.ocr?.lang)
        store.update(job.pageId) {
            ocr = (ocr ?: cam.docscan.engine.api.OcrPage(text = text)).copy(
                lang = result.lang,
                langConfidence = result.confidence,
            )
            entity = entity.copy(
                ocrLang = result.lang,
                ocrLangConfidence = result.confidence,
            )
        }
        repo.insert(store.get(job.pageId)!!.entity)
    }

    private suspend fun classify(job: WorkJob.ClassifyPage) {
        val st = store.get(job.pageId) ?: return
        val ocrPage = st.ocr ?: return
        val result = engine.classifyDocument(ocrPage, st.workingBitmap)
        val tags = result.toTagResult()
        val name = engine.autoname(ocrPage, tags.tags)
        store.update(job.pageId) {
            docType = result
            entity = entity.copy(
                docType = result.docType,
                docTypeConfidence = result.confidence,
                docTypesAlt = result.alternatives,
                tags = tags.tags,
                name = name,
            )
        }
        repo.insert(store.get(job.pageId)!!.entity)
    }

    private suspend fun summarizePage(job: WorkJob.SummarizePage) {
        val st = store.get(job.pageId) ?: return
        val text = st.ocr?.text ?: st.entity.ocrText.orEmpty()
        val mode = if (capability.allowGenerative()) SummaryMode.AUTO else SummaryMode.EXTRACTIVE
        val result = engine.summarize(text, mode)
        store.update(job.pageId) {
            summary = result
            entity = entity.copy(
                summary = result.text.ifBlank { null },
                summarySource = result.source,
                nlpVersion = PageNlpMetadata.NLP_VERSION,
            )
        }
        repo.insert(store.get(job.pageId)!!.entity)
    }

    private suspend fun metadata(job: WorkJob.WriteMetadata) {
        val st = store.get(job.pageId) ?: return
        val e = st.entity
        val meta = PageNlpMetadata(
            pageId = e.id,
            lang = e.ocrLang,
            langConfidence = e.ocrLangConfidence,
            docType = e.docType,
            tags = e.tags,
            summary = e.summary,
            summarySource = e.summarySource,
        )
        val ok = engine.writeImageMetadata(e.imagePath, meta)
        val now = System.currentTimeMillis()
        store.update(job.pageId) {
            metadataWritten = ok
            entity = entity.copy(metadataWrittenAt = if (ok) now else null)
        }
        repo.insert(store.get(job.pageId)!!.entity)
    }

    private suspend fun summarizeBatch(job: WorkJob.SummarizeBatch) {
        val texts = job.pageIds.mapNotNull { id ->
            store.get(id)?.ocr?.text ?: store.get(id)?.entity?.ocrText
        }
        val concat = texts.joinToString("\n\n").take(DeviceCapability.BATCH_SUMMARY_MAX_CHARS)
        val mode = if (capability.allowGenerative()) SummaryMode.AUTO else SummaryMode.EXTRACTIVE
        val result = engine.summarize(concat, mode)
        val tags = job.pageIds.flatMap { store.get(it)?.entity?.tags.orEmpty() }.distinct()
        val name = store.get(job.pageIds.firstOrNull().orEmpty())?.entity?.name ?: "Batch"
        repo.upsertBatch(
            BatchEntity(
                id = job.batchId,
                name = name,
                createdAt = System.currentTimeMillis(),
                tags = tags,
                summary = result.text.ifBlank { null },
                summarySource = result.source,
            ),
        )
    }

    private suspend fun suggestChunk(job: WorkJob.SuggestScanChunk) {
        val provider = suggestProvider ?: return
        if (!capability.systemOk()) return
        val items = provider.loadChunk(cursor = job.cursor, limit = job.chunkSize)
        val scoreJobs = items
            .filter { !suggestCache.isDismissed(it.mediaId) && !suggestCache.alreadyImported(it.mediaId) }
            .map {
                WorkJob.SuggestScoreItem(
                    mediaId = it.mediaId,
                    takenAtMs = it.takenAtMs,
                )
            }
        onSuggestChunk(scoreJobs)
    }

    private suspend fun suggestScore(job: WorkJob.SuggestScoreItem) {
        val provider = suggestProvider ?: return
        if (!capability.systemOk()) return
        val sample = provider.loadThumb(job.mediaId) ?: return
        val score = suggestScorer.score(sample)
        suggestCache.upsert(
            mediaId = job.mediaId,
            takenAtMs = job.takenAtMs,
            score = score,
        )
    }
}
