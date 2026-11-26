package cam.docscan.engine.metadata

import androidx.exifinterface.media.ExifInterface
import cam.docscan.engine.api.PageNlpMetadata
import java.io.File

/**
 * Persists NLP onto image: sidecar JSON always attempted; EXIF best-effort.
 * Failures must not fail Save.
 *
 * Sidecar JSON is hand-built (no org.json) so JVM unit tests work without Robolectric.
 */
class ImageMetadataWriter {

    sealed class Result {
        data object Ok : Result()
        data object Skipped : Result()
    }

    fun write(imagePath: String, meta: PageNlpMetadata): Result {
        val image = File(imagePath)
        if (!image.exists() || !image.isFile) return Result.Skipped

        var sidecarOk = false
        try {
            writeSidecar(image, meta)
            sidecarOk = true
        } catch (_: Throwable) {
            // ignore
        }

        try {
            writeExif(image, meta)
        } catch (_: Throwable) {
            // ignore — sidecar may still have succeeded
        }

        return if (sidecarOk) Result.Ok else Result.Skipped
    }

    fun readSidecar(imagePath: String): PageNlpMetadata? {
        val sidecar = sidecarFile(File(imagePath))
        if (!sidecar.exists()) return null
        return try {
            parseSidecar(sidecar.readText())
        } catch (_: Throwable) {
            null
        }
    }

    private fun writeSidecar(image: File, meta: PageNlpMetadata) {
        val tagsJson = meta.tags.joinToString(prefix = "[", postfix = "]") { "\"${escape(it)}\"" }
        val body = buildString {
            append('{')
            append("\"docscan\":1,")
            append("\"page_id\":\"").append(escape(meta.pageId)).append("\",")
            append("\"lang\":").append(meta.lang?.let { "\"${escape(it)}\"" } ?: "null").append(',')
            append("\"lang_confidence\":")
            append(meta.langConfidence?.toString() ?: "null").append(',')
            append("\"doc_type\":").append(meta.docType?.let { "\"${escape(it)}\"" } ?: "null").append(',')
            append("\"tags\":").append(tagsJson).append(',')
            append("\"summary\":").append(meta.summary?.let { "\"${escape(it)}\"" } ?: "null").append(',')
            append("\"summary_source\":")
            append(meta.summarySource?.let { "\"${escape(it)}\"" } ?: "null").append(',')
            append("\"nlp_version\":\"").append(escape(meta.nlpVersion)).append('"')
            append('}')
        }
        sidecarFile(image).writeText(body)
    }

    private fun writeExif(image: File, meta: PageNlpMetadata) {
        val desc = buildString {
            meta.docType?.let { append("doc_type=").append(it).append("; ") }
            meta.lang?.let { append("lang=").append(it).append("; ") }
            meta.summary?.take(200)?.let { append(it) }
        }.trim().ifBlank { return }

        val exif = ExifInterface(image.absolutePath)
        exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, desc.take(500))
        exif.saveAttributes()
    }

    companion object {
        fun sidecarFile(image: File): File {
            val stem = image.nameWithoutExtension
            return File(image.parentFile, "$stem.docscan.json")
        }

        fun parseSidecar(json: String): PageNlpMetadata {
            fun str(key: String): String? {
                val re = Regex("\"$key\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\"|null)")
                val m = re.find(json) ?: return null
                val raw = m.groupValues[1]
                if (raw == "null") return null
                return unescape(raw.removeSurrounding("\""))
            }

            fun num(key: String): Float? {
                val re = Regex("\"$key\"\\s*:\\s*(null|[0-9]+(?:\\.[0-9]+)?)")
                val m = re.find(json) ?: return null
                val raw = m.groupValues[1]
                if (raw == "null") return null
                return raw.toFloatOrNull()
            }

            fun strList(key: String): List<String> {
                val re = Regex("\"$key\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
                val inner = re.find(json)?.groupValues?.get(1) ?: return emptyList()
                return Regex("\"((?:\\\\.|[^\"\\\\])*)\"").findAll(inner)
                    .map { unescape(it.groupValues[1]) }
                    .toList()
            }

            return PageNlpMetadata(
                pageId = str("page_id").orEmpty(),
                lang = str("lang"),
                langConfidence = num("lang_confidence"),
                docType = str("doc_type"),
                tags = strList("tags"),
                summary = str("summary"),
                summarySource = str("summary_source"),
                nlpVersion = str("nlp_version") ?: PageNlpMetadata.NLP_VERSION,
            )
        }

        private fun escape(s: String): String = buildString(s.length + 8) {
            for (c in s) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
        }

        private fun unescape(s: String): String = buildString(s.length) {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) {
                        'n' -> append('\n')
                        'r' -> append('\r')
                        't' -> append('\t')
                        '"', '\\' -> append(s[i + 1])
                        else -> append(s[i + 1])
                    }
                    i += 2
                } else {
                    append(c)
                    i++
                }
            }
        }
    }
}
