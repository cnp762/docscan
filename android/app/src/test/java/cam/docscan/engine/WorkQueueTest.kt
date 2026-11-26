package cam.docscan.engine

import cam.docscan.engine.runtime.DeviceCapability
import cam.docscan.engine.runtime.WorkJob
import cam.docscan.engine.runtime.WorkPriority
import cam.docscan.engine.runtime.WorkQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WorkQueueTest {

    @Test
    fun newerFreshScanJumpsAheadOfOlderAndSuggest() = runBlocking {
        val order = Collections.synchronizedList(mutableListOf<String>())
        val latch = CountDownLatch(4)
        val started = CountDownLatch(1)
        val gate = CountDownLatch(1)

        val queue = WorkQueue { job ->
            val label = when (job) {
                is WorkJob.OcrPage -> "ocr:${job.pageId}"
                is WorkJob.SuggestScoreItem -> "suggest:${job.mediaId}"
                is WorkJob.LangPage -> "lang:${job.pageId}"
                else -> job.id
            }
            if (label == "ocr:A") {
                started.countDown()
                gate.await(2, TimeUnit.SECONDS)
            }
            order += label
            latch.countDown()
        }

        // Start A OCR in-flight
        queue.enqueue(
            WorkJob.OcrPage(priority = WorkPriority.FreshScan, pageId = "A", groupId = "gA"),
            groupEnqueuedAt = 1L,
            stepIndex = 0,
        )
        assertTrue(started.await(2, TimeUnit.SECONDS))

        // While A running: queue remaining A lang, suggest, then newer page B ocr
        queue.enqueue(
            WorkJob.LangPage(priority = WorkPriority.FreshScan, pageId = "A", groupId = "gA"),
            groupEnqueuedAt = 1L,
            stepIndex = 1,
        )
        queue.enqueue(
            WorkJob.SuggestScoreItem(mediaId = "m1", takenAtMs = 0L),
            groupEnqueuedAt = 2L,
            stepIndex = 0,
        )
        queue.enqueue(
            WorkJob.OcrPage(priority = WorkPriority.FreshScan, pageId = "B", groupId = "gB"),
            groupEnqueuedAt = 3L,
            stepIndex = 0,
        )

        gate.countDown()
        assertTrue(latch.await(5, TimeUnit.SECONDS))

        // In-flight A ocr finishes first; then B (newer FreshScan) before remaining A and Suggest
        assertEquals("ocr:A", order[0])
        assertEquals("ocr:B", order[1])
        assertTrue(order.indexOf("lang:A") > order.indexOf("ocr:B"))
        assertTrue(order.indexOf("suggest:m1") > order.indexOf("ocr:B"))
        queue.close()
    }

    @Test
    fun chainPreservesStepOrderWithinPage() = runBlocking {
        val order = Collections.synchronizedList(mutableListOf<String>())
        val latch = CountDownLatch(3)
        val queue = WorkQueue { job ->
            when (job) {
                is WorkJob.OcrPage -> order += "ocr"
                is WorkJob.LangPage -> order += "lang"
                is WorkJob.ClassifyPage -> order += "classify"
                else -> {}
            }
            latch.countDown()
        }
        queue.enqueueChain(
            listOf(
                WorkJob.OcrPage(priority = WorkPriority.FreshScan, pageId = "p", groupId = "g"),
                WorkJob.LangPage(priority = WorkPriority.FreshScan, pageId = "p", groupId = "g"),
                WorkJob.ClassifyPage(priority = WorkPriority.FreshScan, pageId = "p", groupId = "g"),
            ),
            groupEnqueuedAt = 10L,
        )
        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertEquals(listOf("ocr", "lang", "classify"), order.toList())
        queue.close()
    }

    @Test
    fun suggestDoesNotRunWhileFreshScanPending() = runBlocking {
        val order = Collections.synchronizedList(mutableListOf<String>())
        val latch = CountDownLatch(2)
        val queue = WorkQueue { job ->
            when (job) {
                is WorkJob.OcrPage -> order += "ocr"
                is WorkJob.SuggestScoreItem -> order += "suggest"
                else -> {}
            }
            latch.countDown()
            delay(10)
        }
        queue.enqueue(WorkJob.SuggestScoreItem(mediaId = "m", takenAtMs = 1), groupEnqueuedAt = 1)
        queue.enqueue(
            WorkJob.OcrPage(priority = WorkPriority.FreshScan, pageId = "p", groupId = "g"),
            groupEnqueuedAt = 2,
        )
        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertEquals(listOf("ocr", "suggest"), order.toList())
        queue.close()
    }

    @Test
    fun stepTimeoutIncrementsFailed() = runBlocking {
        val latch = CountDownLatch(1)
        val failed = AtomicInteger(0)
        val queue = WorkQueue { _ ->
            delay(200)
        }
        queue.enqueue(
            WorkJob.LangPage(
                priority = WorkPriority.FreshScan,
                pageId = "p",
                groupId = "g",
                timeoutMs = 50,
            ),
        )
        // Wait for worker
        var spins = 0
        while (queue.progress.value.failed == 0L && spins++ < 100) {
            delay(20)
        }
        failed.set(queue.progress.value.failed.toInt())
        latch.countDown()
        assertTrue(failed.get() >= 1)
        queue.close()
    }

    @Test
    fun systemOkDefaultsTrueWithoutContext() {
        val cap = DeviceCapability(null)
        assertTrue(cap.systemOk())
        assertTrue(cap.allowGenerative())
    }
}
