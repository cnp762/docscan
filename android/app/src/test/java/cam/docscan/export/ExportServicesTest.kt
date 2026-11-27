package cam.docscan.export

import cam.docscan.data.PageEntity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipFile

class ExportServicesTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun writeDocxToFile_producesValidOoxmlZipWithOcrText() {
        val outFile = tempFolder.newFile("invoice.docx")
        val page = PageEntity(
            id = "p1",
            batchId = null,
            name = "Invoice Mar 12",
            source = "camera",
            imagePath = "",
            createdAt = 1L,
            ocrText = "INVOICE #1042\nTotal: $1,240.00",
        )

        ExportServices.writeDocxToFile(outFile, "Invoice Mar 12", listOf(page))

        ZipFile(outFile).use { zip ->
            assertTrue(zip.getEntry("[Content_Types].xml") != null)
            assertTrue(zip.getEntry("_rels/.rels") != null)
            val docEntry = zip.getEntry("word/document.xml")
            assertTrue(docEntry != null)
            val xml = zip.getInputStream(docEntry).bufferedReader().readText()
            assertTrue(xml.contains("INVOICE #1042"))
        }
    }
}
