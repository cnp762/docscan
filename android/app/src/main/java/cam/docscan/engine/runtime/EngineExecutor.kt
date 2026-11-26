package cam.docscan.engine.runtime

/**
 * @deprecated Use [WorkQueue] for all heavy background work (Save NLP + suggest).
 * Kept as a thin timed submit helper for isolated unit tests only.
 */
@Deprecated("Use WorkQueue", ReplaceWith("WorkQueue"))
class EngineExecutor(
    private val queue: WorkQueue? = null,
) {
    suspend fun <T> submit(block: suspend () -> T): T = block()

    suspend fun <T> submitTimed(timeoutMs: Long, block: suspend () -> T): T? {
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { block() }
    }

    fun cancelPending() {
        queue?.cancelSuggest()
    }

    fun close() {
        // no-op when unbound
    }
}
