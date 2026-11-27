package cam.docscan.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import androidx.core.content.FileProvider
import cam.docscan.data.PageEntity
import cam.docscan.data.local.FileStorageManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportQualityLevel(val jpegQuality: Int, val maxDimension: Int) {
    COMPACT(65, 1400),
    STANDARD(82, 2000),
    MAXIMUM(95, 3200),
}

object ExportServices {

    fun exportSearchablePdf(
        storage: FileStorageManager,
        title: String,
        pages: List<PageEntity>,
        quality: ExportQualityLevel = ExportQualityLevel.STANDARD,
    ): File {
        val safeName = sanitizeFileName(title.ifBlank { "DocScan" })
        val outFile = storage.createExportFile("$safeName.pdf")
        val pdf = PdfDocument()

        val textPaint = Paint().apply {
            color = Color.TRANSPARENT
            textSize = 10f
            isAntiAlias = true
        }

        pages.forEachIndexed { index, page ->
            val bitmap = loadScaledBitmap(page.imagePath, quality.maxDimension)
            val width = bitmap?.width ?: 1240
            val height = bitmap?.height ?: 1754
            val pageInfo = PdfDocument.PageInfo.Builder(width, height, index + 1).create()
            val pdfPage = pdf.startPage(pageInfo)
            val canvas = pdfPage.canvas

            if (bitmap != null) {
                canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), null)
                bitmap.recycle()
            } else {
                canvas.drawColor(Color.WHITE)
            }

            // Invisible OCR text layer so PDF viewers can search and copy text
            val ocr = page.ocrText?.trim().orEmpty()
            if (ocr.isNotEmpty()) {
                var y = 24f
                for (line in ocr.lines().take(80)) {
                    canvas.drawText(line, 16f, y, textPaint)
                    y += 14f
                    if (y > height - 16f) break
                }
            }

            pdf.finishPage(pdfPage)
        }

        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        return outFile
    }

    fun exportDocx(
        storage: FileStorageManager,
        title: String,
        pages: List<PageEntity>,
        quality: ExportQualityLevel = ExportQualityLevel.STANDARD,
    ): File {
        val safeName = sanitizeFileName(title.ifBlank { "DocScan" })
        val outFile = storage.createExportFile("$safeName.docx")
        writeDocxToFile(outFile, title, pages, quality)
        return outFile
    }

    /**
     * Pure JVM-testable OOXML DOCX writer (ZIP with [Content_Types].xml, _rels/.rels, word/document.xml, and embedded JPEGs).
     */
    fun writeDocxToFile(
        outFile: File,
        title: String,
        pages: List<PageEntity>,
        quality: ExportQualityLevel = ExportQualityLevel.STANDARD,
    ) {
        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            val contentTypes = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Default Extension="jpg" ContentType="image/jpeg"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
            """.trimIndent()
            putZipText(zip, "[Content_Types].xml", contentTypes)

            val rootRels = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
            """.trimIndent()
            putZipText(zip, "_rels/.rels", rootRels)

            val bodyBuilder = StringBuilder()
            bodyBuilder.append("<w:p><w:r><w:t>${escapeXml(title)}</w:t></w:r></w:p>")
            pages.forEachIndexed { idx, page ->
                val imgBytes = loadJpegBytes(page.imagePath, quality)
                if (imgBytes != null) {
                    putZipBytes(zip, "word/media/page_${idx + 1}.jpg", imgBytes)
                }
                val text = page.ocrText?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    for (line in text.lines()) {
                        bodyBuilder.append("<w:p><w:r><w:t xml:space=\"preserve\">${escapeXml(line)}</w:t></w:r></w:p>")
                    }
                }
            }

            val documentXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    $bodyBuilder
                    <w:sectPr/>
                  </w:body>
                </w:document>
            """.trimIndent()
            putZipText(zip, "word/document.xml", documentXml)
        }
    }

    fun exportImagesZip(
        storage: FileStorageManager,
        title: String,
        pages: List<PageEntity>,
        quality: ExportQualityLevel = ExportQualityLevel.STANDARD,
    ): File {
        val safeName = sanitizeFileName(title.ifBlank { "DocScan" })
        val outFile = storage.createExportFile("$safeName-images.zip")
        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            pages.forEachIndexed { idx, page ->
                val bytes = loadJpegBytes(page.imagePath, quality) ?: return@forEachIndexed
                putZipBytes(zip, "${safeName}_page_${idx + 1}.jpg", bytes)
            }
        }
        return outFile
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
    }

    fun printPdfFile(context: Context, pdfFile: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: android.os.Bundle?,
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(pdfFile.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?,
            ) {
                if (destination == null) {
                    callback?.onWriteFailed("Missing destination")
                    return
                }
                runCatching {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                }.onSuccess {
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }.onFailure {
                    callback?.onWriteFailed(it.message)
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }

    private fun loadScaledBitmap(path: String, maxDim: Int): Bitmap? {
        if (path.isBlank() || !File(path).exists()) return null
        val raw = BitmapFactory.decodeFile(path) ?: return null
        val maxSide = maxOf(raw.width, raw.height)
        if (maxSide <= maxDim) return raw
        val scale = maxDim.toFloat() / maxSide.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            raw,
            (raw.width * scale).toInt().coerceAtLeast(1),
            (raw.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== raw) raw.recycle()
        return scaled
    }

    private fun loadJpegBytes(path: String, quality: ExportQualityLevel): ByteArray? {
        val bmp = loadScaledBitmap(path, quality.maxDimension) ?: return null
        return ByteArrayOutputStream().use { baos ->
            bmp.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, baos)
            bmp.recycle()
            baos.toByteArray()
        }
    }

    private fun putZipText(zip: ZipOutputStream, entryName: String, content: String) {
        putZipBytes(zip, entryName, content.toByteArray(Charsets.UTF_8))
    }

    private fun putZipBytes(zip: ZipOutputStream, entryName: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun sanitizeFileName(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(64)

    private fun escapeXml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
