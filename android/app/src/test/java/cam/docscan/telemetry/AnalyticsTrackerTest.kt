package cam.docscan.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AnalyticsTrackerTest {
    @Test
    fun sanitizeParams_stripsDisallowedPiiAndDocFields() {
        val raw = mapOf(
            "source" to "camera",
            "n_pages" to 3,
            "is_pro" to false,
            "ocr_text" to "CONFIDENTIAL INVOICE 1234",
            "summary" to "Invoice from Acme Corp",
            "image_path" to "/data/user/0/cam.docscan/files/p1.jpg",
            "filename" to "passport_john_doe.pdf",
        )
        val sanitized = AnalyticsTracker.sanitizeParams(raw)
        assertEquals(3, sanitized.size)
        assertEquals("camera", sanitized["source"])
        assertFalse(sanitized.containsKey("ocr_text"))
        assertFalse(sanitized.containsKey("summary"))
        assertFalse(sanitized.containsKey("image_path"))
        assertFalse(sanitized.containsKey("filename"))
    }
}
