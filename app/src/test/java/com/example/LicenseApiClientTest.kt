package com.example

import com.example.monetization.ApiResult
import com.example.monetization.FailureKind
import com.example.monetization.LicenseApiClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LicenseApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: LicenseApiClient

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        client = LicenseApiClient(server.url("/").toString())
    }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun productsParsedWithServerPrices() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":"full_access","title":"Полный доступ","kind":"durable","price":299}]"""))
        val r = client.products() as ApiResult.Success
        assertEquals("299 ₽", r.value.single().priceLabel)
        assertEquals("/api/v1/products", server.takeRequest().path)
    }

    @Test
    fun createOrderSendsProductAndRejectsNonHttpsConfirmation() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"order_id":"o1","confirmation_url":"https://yookassa.ru/pay/1","amount":{"value":"299.00","currency":"RUB"}}"""))
        val ok = client.createOrder("full_access") as ApiResult.Success
        assertEquals("o1", ok.value.orderId)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.body.readUtf8().contains("\"product_id\":\"full_access\""))

        server.enqueue(MockResponse().setBody("""{"order_id":"o2","confirmation_url":"http://evil.example/pay","amount":{"value":"1","currency":"RUB"}}"""))
        val bad = client.createOrder("full_access") as ApiResult.Failure
        assertEquals(FailureKind.PARSE, bad.kind)
    }

    @Test
    fun activateMapsBackendErrorCode() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"not_paid"}"""))
        val r = client.activate("DPLA-AAAA", "inst", "1.0") as ApiResult.Failure
        assertEquals(FailureKind.HTTP, r.kind)
        assertEquals("not_paid", r.error)
        assertEquals(403, r.httpCode)
    }

    @Test
    fun garbageBodyIsParseFailure() = runBlocking {
        server.enqueue(MockResponse().setBody("<html>oops</html>"))
        assertEquals(FailureKind.PARSE, (client.order("o1") as ApiResult.Failure).kind)
    }

    @Test
    fun networkErrorAndNotConfigured() = runBlocking {
        server.shutdown()
        assertEquals(FailureKind.NETWORK, (client.products() as ApiResult.Failure).kind)
        assertEquals(FailureKind.NOT_CONFIGURED, (LicenseApiClient("").products() as ApiResult.Failure).kind)
        assertEquals(FailureKind.NOT_CONFIGURED, (LicenseApiClient("http://example.com").products() as ApiResult.Failure).kind)
    }
}
