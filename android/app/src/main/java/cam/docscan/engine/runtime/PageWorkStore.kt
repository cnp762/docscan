package cam.docscan.engine.runtime

import android.graphics.Bitmap
import cam.docscan.data.PageEntity
import cam.docscan.engine.api.DocTypeResult
import cam.docscan.engine.api.EnhancePreset
import cam.docscan.engine.api.OcrPage
import cam.docscan.engine.api.Quad
import cam.docscan.engine.api.SummaryResult
import java.util.concurrent.ConcurrentHashMap

/** In-memory working set for stepped Save jobs (bitmap + partial NLP). */
class PageWorkStore {
    data class State(
        var entity: PageEntity,
        var sourceBitmap: Bitmap? = null,
        var workingBitmap: Bitmap? = null,
        var quad: Quad? = null,
        var preset: EnhancePreset = EnhancePreset.DOCUMENT,
        var ocr: OcrPage? = null,
        var docType: DocTypeResult? = null,
        var summary: SummaryResult? = null,
        var metadataWritten: Boolean = false,
    )

    private val pages = ConcurrentHashMap<String, State>()

    fun put(state: State) {
        pages[state.entity.id] = state
    }

    fun get(pageId: String): State? = pages[pageId]

    fun update(pageId: String, block: State.() -> Unit): State? {
        val s = pages[pageId] ?: return null
        synchronized(s) { s.block() }
        return s
    }

    fun remove(pageId: String) {
        pages.remove(pageId)?.let { st ->
            // Do not recycle bitmaps owned by caller; just drop refs.
            st.sourceBitmap = null
            st.workingBitmap = null
        }
    }

    fun clear() = pages.clear()
}
