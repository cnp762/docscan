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
import java.io.File

/**
 * App-scoped shared WorkQueue + pipeline + suggest scanner.
 */
class DocScanRuntime(
    context: Context,
    val repo: PageRepository = InMemoryPageRepository(),
    suggestProvider: SuggestMediaProvider = EmptySuggestMediaProvider(),
    val suggestCache: SuggestCache = SuggestCache(),
) {
    val modelsDir: File? = syncBundledModels(context.applicationContext)
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

    val pipeline = DocumentPipeline(engine, repo, store, queue, modelsDir)

    val suggestScanner = SuggestScanner(queue, suggestCache)

    fun close() {
        queue.close()
        store.clear()
        engine.close()
    }

    companion object {
        private fun syncBundledModels(context: Context): File? = runCatching {
            val outDir = File(context.filesDir, "models")
            copyAssetDir(context, "models", outDir)
            outDir
        }.getOrNull()

        private fun copyAssetDir(context: Context, assetPath: String, destDir: File) {
            val entries = context.assets.list(assetPath) ?: return
            if (entries.isEmpty()) return
            destDir.mkdirs()
            for (name in entries) {
                val childAsset = "$assetPath/$name"
                val childDest = File(destDir, name)
                val sub = context.assets.list(childAsset)
                if (!sub.isNullOrEmpty()) {
                    copyAssetDir(context, childAsset, childDest)
                } else if (!childDest.exists() || childDest.length() == 0L) {
                    context.assets.open(childAsset).use { input ->
                        childDest.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }
}
