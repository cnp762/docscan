package cam.docscan.engine.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared serial background queue for Save NLP + 7-day suggest.
 *
 * Sort: priority band desc → groupEnqueuedAt desc (newer page first) → stepIndex asc
 * (preserve step order within a page). In-flight job is never preempted.
 */
class WorkQueue(
    private val handler: suspend (WorkJob) -> Unit = {},
) {
    data class Progress(
        val pending: Int = 0,
        val running: WorkJob? = null,
        val completed: Long = 0,
        val failed: Long = 0,
        val lastError: String? = null,
    )

    data class QueuedEntry(
        val job: WorkJob,
        val groupEnqueuedAt: Long,
        val stepIndex: Int,
    )

    private val closed = AtomicBoolean(false)
    private val cancelTags = mutableSetOf<String>()
    private val cancelPageIds = mutableSetOf<String>()
    private val cancelJobIds = mutableSetOf<String>()

    private val comparator = Comparator<QueuedEntry> { a, b ->
        val byBand = b.job.priority.band.compareTo(a.job.priority.band)
        if (byBand != 0) return@Comparator byBand
        val byGroup = b.groupEnqueuedAt.compareTo(a.groupEnqueuedAt)
        if (byGroup != 0) return@Comparator byGroup
        a.stepIndex.compareTo(b.stepIndex)
    }

    private val queue = PriorityBlockingQueue(64, comparator)
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "docscan-work").apply { isDaemon = true }
    }
    private val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    @Volatile
    private var workerJob: Job? = null

    fun enqueue(job: WorkJob, groupEnqueuedAt: Long = job.enqueuedAt, stepIndex: Int = 0): String {
        check(!closed.get()) { "WorkQueue closed" }
        queue.offer(QueuedEntry(job, groupEnqueuedAt, stepIndex))
        publishPending()
        ensureWorker()
        return job.id
    }

    /** Enqueue an ordered chain (same group time; stepIndex = list order). */
    fun enqueueChain(jobs: List<WorkJob>, groupEnqueuedAt: Long = System.nanoTime()): List<String> {
        return jobs.mapIndexed { index, job -> enqueue(job, groupEnqueuedAt, index) }
    }

    fun enqueueAll(jobs: List<WorkJob>): List<String> =
        jobs.map { enqueue(it) }

    fun cancel(jobId: String) {
        cancelJobIds += jobId
        queue.removeIf { it.job.id == jobId }
        publishPending()
    }

    fun cancelPage(pageId: String) {
        cancelPageIds += pageId
        queue.removeIf { it.job.pageId == pageId }
        publishPending()
    }

    fun cancelGroup(groupId: String) {
        cancelTags += groupId
        queue.removeIf { it.job.groupId == groupId }
        publishPending()
    }

    fun cancelSuggest() {
        queue.removeIf { it.job.priority == WorkPriority.Suggest }
        publishPending()
    }

    fun pendingSnapshot(): List<WorkJob> =
        queue.toList().sortedWith(comparator).map { it.job }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        queue.clear()
        scope.cancel()
        executor.shutdownNow()
        _progress.value = Progress()
    }

    private fun ensureWorker() {
        val existing = workerJob
        if (existing != null && existing.isActive) return
        workerJob = scope.launch {
            while (!closed.get()) {
                val entry = queue.poll() ?: break
                val job = entry.job
                if (isCancelled(job)) {
                    publishPending()
                    continue
                }
                _progress.value = _progress.value.copy(
                    pending = queue.size,
                    running = job,
                )
                try {
                    val ok = withTimeoutOrNull(job.timeoutMs) {
                        handler(job)
                        true
                    }
                    if (ok == true) {
                        _progress.value = _progress.value.copy(
                            completed = _progress.value.completed + 1,
                            running = null,
                            pending = queue.size,
                            lastError = null,
                        )
                    } else {
                        _progress.value = _progress.value.copy(
                            failed = _progress.value.failed + 1,
                            running = null,
                            pending = queue.size,
                            lastError = "timeout:${job::class.simpleName}",
                        )
                    }
                } catch (t: Throwable) {
                    _progress.value = _progress.value.copy(
                        failed = _progress.value.failed + 1,
                        running = null,
                        pending = queue.size,
                        lastError = t.message,
                    )
                }
            }
            _progress.value = _progress.value.copy(running = null, pending = queue.size)
            if (!closed.get() && queue.isNotEmpty()) {
                workerJob = null
                ensureWorker()
            }
        }
    }

    private fun isCancelled(job: WorkJob): Boolean {
        if (job.id in cancelJobIds) return true
        if (job.pageId != null && job.pageId in cancelPageIds) return true
        if (job.groupId != null && job.groupId in cancelTags) return true
        return false
    }

    private fun publishPending() {
        _progress.value = _progress.value.copy(pending = queue.size)
    }
}
