package cam.docscan.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DocScanApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var identity: FakeIdentityProvider
    private lateinit var api: DocScanApiClient

    private class FakeIdentityProvider : DeviceIdentityProvider {
        var id: String = "test-device-uuid-1234"
        var jwt: String? = "test-jwt-token"
        var cleared = false

        override fun getOrCreateDeviceId(): String = id
        override fun getToken(): String? = jwt
        override fun saveToken(token: String) {
            jwt = token
        }
        override fun clearAll() {
            cleared = true
            jwt = null
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        identity = FakeIdentityProvider()
        api = DocScanApiClient(
            rawBaseUrl = server.url("/v1").toString(),
            identity = identity,
            appVersion = "1.0.0-test",
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun normalizeV1BaseUrl_handlesWithAndWithoutSuffix() {
        assertEquals(
            "https://api.docscan.cam/v1",
            DocScanApiClient.normalizeV1BaseUrl("https://api.docscan.cam/v1/"),
        )
        assertEquals(
            "https://desktop-d5brjmr.tail79d3ce.ts.net:3005/v1",
            DocScanApiClient.normalizeV1BaseUrl("https://desktop-d5brjmr.tail79d3ce.ts.net:3005"),
        )
    }

    @Test
    fun consumeQuota_returnsAllowedOn200() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"utc_day":"2026-11-26","used":2,"limit":5,"remaining":3,"is_pro":false}"""),
        )

        val result = api.consumeQuota(2)
        assertTrue(result is QuotaConsumeResult.Allowed)
        val allowed = result as QuotaConsumeResult.Allowed
        assertEquals(3, allowed.status.remaining)

        val req = server.takeRequest()
        assertEquals("/v1/quota/consume", req.path)
        assertEquals("test-device-uuid-1234", req.getHeader("X-Device-Id"))
        assertEquals("Bearer test-jwt-token", req.getHeader("Authorization"))
    }

    @Test
    fun consumeQuota_returnsQuotaExceededOn402() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(402)
                .setBody("""{"detail":"quota_exceeded"}"""),
        )

        val result = api.consumeQuota(1)
        assertTrue(result is QuotaConsumeResult.QuotaExceeded)
    }

    @Test
    fun deleteDeviceMe_clearsLocalIdentity() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val res = api.deleteDeviceMe()
        assertTrue(res.isSuccess)
        assertTrue(identity.cleared)
        assertEquals("/v1/devices/me", server.takeRequest().path)
    }
}
