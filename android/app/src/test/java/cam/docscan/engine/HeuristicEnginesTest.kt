package cam.docscan.engine

import cam.docscan.engine.api.OcrPage
import cam.docscan.engine.api.PageNlpMetadata
import cam.docscan.engine.api.SummaryMode
import cam.docscan.engine.classify.DocClassifyEngine
import cam.docscan.engine.lang.LangDetectEngine
import cam.docscan.engine.metadata.ImageMetadataWriter
import cam.docscan.engine.summary.SummaryEngine
import cam.docscan.engine.tag.HeuristicTagEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HeuristicEnginesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun tagsInvoiceFromOcr() {
        val ocr = OcrPage(
            text = "INVOICE\nAmount due: 100 USD\nTax invoice for services",
            title = "INVOICE",
        )
        val tags = HeuristicTagEngine().tag(ocr).tags
        assertTrue(tags.contains("invoice"))
    }

    @Test
    fun classifyInvoicePrimary() {
        val ocr = OcrPage(
            text = "INVOICE\nBill to: Acme\nAmount due: 100 USD\nTax invoice",
            title = "INVOICE",
        )
        val result = DocClassifyEngine().classify(ocr)
        assertEquals("invoice", result.docType)
        assertTrue(result.confidence >= 0.45f)
        assertTrue(result.tags.contains("invoice"))
    }

    @Test
    fun langDetectEnglish() {
        val r = LangDetectEngine().detect(
            "This is a plain English invoice for consulting services rendered today.",
        )
        assertEquals("en", r.lang)
        assertTrue(r.confidence > 0.4f)
    }

    @Test
    fun langDetectVietnamese() {
        val r = LangDetectEngine().detect(
            "Hóa đơn này được xuất cho khách hàng và không được chuyển nhượng.",
        )
        assertEquals("vi", r.lang)
    }

    @Test
    fun langDetectChinese() {
        val r = LangDetectEngine().detect("这是一份发票文件，金额合计如下所示。")
        assertEquals("zh", r.lang)
    }

    @Test
    fun extractiveSummaryNonEmpty() = runBlocking {
        val text = "This is a sufficiently long first sentence about the agreement. " +
            "A second sentence mentions the total amount due today. " +
            "Third sentence closes the document politely."
        val summary = SummaryEngine().summarize(text, SummaryMode.EXTRACTIVE)
        assertTrue(summary.text.isNotBlank())
        assertEquals("extractive", summary.source)
        assertTrue(summary.text.length <= 500)
    }

    @Test
    fun sidecarRoundTrip() {
        val image = tmp.newFile("page1.jpg")
        image.writeBytes(ByteArray(16) { it.toByte() })
        val writer = ImageMetadataWriter()
        val meta = PageNlpMetadata(
            pageId = "p-1",
            lang = "en",
            langConfidence = 0.82f,
            docType = "invoice",
            tags = listOf("invoice"),
            summary = "Amount due today.",
            summarySource = "extractive",
        )
        val result = writer.write(image.absolutePath, meta)
        assertTrue(result is ImageMetadataWriter.Result.Ok)
        val sidecar = ImageMetadataWriter.sidecarFile(image)
        assertTrue(sidecar.exists())
        val read = writer.readSidecar(image.absolutePath)
        assertNotNull(read)
        assertEquals("p-1", read!!.pageId)
        assertEquals("en", read.lang)
        assertEquals("invoice", read.docType)
        assertEquals(listOf("invoice"), read.tags)
        assertEquals("Amount due today.", read.summary)
    }
}
