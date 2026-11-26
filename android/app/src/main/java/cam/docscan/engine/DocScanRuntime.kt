package cam.docscan.engine

import android.content.Context
import cam.docscan.data.InMemoryPageRepository
import cam.docscan.data.PageRepository
import cam.docscan.engine.impl.DefaultDocScanEngine
import cam.docscan.engine.runtime.PageWorkStore
import cam.docscan.engine.runtime.WorkJobHandler
import cam.docscan.engine.runtime.WorkQueue
import cam.docscan.engine.suggest.EmptySuggestMediaProvider
import cam.docscan.engine.suggest.SuggestCache
import cam.docscan.engine.suggest.SuggestMediaProvider
import cam.docscan.engine.suggest.SuggestScanner

/**
 * App-scoped shared WorkQueue + pipeline + suggest scanner.
 */
class DocScanRuntime(
    context: Context,
    val repo: PageRepository = InMemoryPageRepository(),
    suggestProvider: SuggestMediaProvider = EmptySuggestMediaProvider(),
    val suggestCache: SuggestCache = SuggestCache(),
) {
    val engine = DefaultDocScanEngine(context.applicationContext)
    val store = PageWorkStore()

    private val queueRef = arrayOfNulls<WorkQueue>(1)

    private val handler = WorkJobHandler(
        engine = engine,
        store = store,
        repo = repo,
        capability = engine.capability(),
        suggestProvider = suggestProvider,
        suggestCache = suggestCache,
        onSuggestChunk = { jobs -> queueRef[0]?.enqueueAll(jobs) },
    )

    val queue: WorkQueue = WorkQueue { job -> handler.handle(job) }.also { queueRef[0] = it }

    val pipeline = DocumentPipeline(engine, repo, store, queue)

    val suggestScanner = SuggestScanner(queue, suggestCache)

    fun close() {
        queue.close()
        store.clear()
        engine.close()
    }
}
