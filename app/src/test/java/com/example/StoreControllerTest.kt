package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.monetization.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StoreControllerTest {
    private val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val pub = Base64.getEncoder().encodeToString(kp.public.encoded.takeLast(32).toByteArray())
    private lateinit var db: AppDatabase

    private class Api : LicenseApi {
        var products: ApiResult<List<Product>> = ApiResult.Success(
            listOf(Product("full_access", "Полный доступ", "durable", 299.0), Product("tip_50", "Поддержать", "consumable", 50.0))
        )
        var order: ApiResult<OrderResponse> = ApiResult.Success(OrderResponse("o1", "https://pay.example/1", Amount("299.00", "RUB")))
        var status: ApiResult<OrderStatus> = ApiResult.Success(OrderStatus("pending"))
        var activate: ApiResult<ActivateResponse> = ApiResult.Failure(FailureKind.NETWORK)
        override suspend fun products() = products
        override suspend fun createOrder(productId: String) = order
        override suspend fun order(orderId: String) = status
        override suspend fun activate(licenseKey: String, installId: String, appVersion: String) = activate
        override suspend fun status(licenseId: String, installId: String): ApiResult<LicenseStatus> = ApiResult.Failure(FailureKind.NETWORK)
    }

    private class Mem : PendingOrderStore {
        var id: String? = null
        override fun get() = id
        override fun set(orderId: String?) { id = orderId }
    }

    private val api = Api()
    private val mem = Mem()
    private lateinit var gateway: DirectLicenseGateway
    private lateinit var controller: StoreController

    private fun token(): String {
        val bytes = """{"product_id":"full_access","license_id":"L1","issued_at":1731500000,"expires_at":null,"v":1}""".toByteArray()
        val sig = Signature.getInstance("Ed25519").apply { initSign(kp.private); update(bytes) }.sign()
        val enc = Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString(bytes) + "." + enc.encodeToString(sig)
    }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        gateway = DirectLicenseGateway(api, EntitlementVerifier(pub), db.entitlementDao(), { "i" }, "1.0")
        controller = StoreController(gateway, mem)
    }
    @After fun tearDown() { db.close() }

    @Test
    fun loadShowsOnlyDurableProductsFromServer() = runBlocking {
        controller.load()
        val p = controller.state.value.products as ProductsState.Loaded
        assertEquals(listOf("full_access"), p.products.map { it.id })
    }

    @Test
    fun loadOfflineShowsOfflineMessage() = runBlocking {
        api.products = ApiResult.Failure(FailureKind.NETWORK)
        controller.load()
        assertEquals(MSG_OFFLINE, (controller.state.value.products as ProductsState.Failed).message)
    }

    @Test
    fun buyOpensPaymentPageAndRemembersOrder() = runBlocking {
        var opened: String? = null
        controller.buy("full_access") { opened = it; true }
        assertEquals("https://pay.example/1", opened)
        assertEquals("o1", mem.id)
        assertEquals("o1", controller.state.value.pendingOrderId)
        assertTrue(gateway.unlockedProducts.first().isEmpty()) // покупка сама ничего не открывает
    }

    @Test
    fun buyOfflineKeepsNoOrder() = runBlocking {
        api.order = ApiResult.Failure(FailureKind.NETWORK)
        controller.buy("full_access") { true }
        assertNull(mem.id)
        assertEquals(MSG_OFFLINE, controller.state.value.message)
        assertTrue(!controller.state.value.busy)
    }

    @Test
    fun pendingOrderIsRestoredFromStore() {
        mem.id = "o9"
        assertEquals("o9", StoreController(gateway, mem).state.value.pendingOrderId)
    }

    @Test
    fun claimingPaidWithoutServerConfirmationOpensNothing() = runBlocking {
        controller.buy("full_access") { true }
        controller.checkPending() // сервер говорит pending
        assertNull(controller.state.value.readyKey)
        assertTrue(gateway.unlockedProducts.first().isEmpty())
        assertEquals("o1", mem.id)
    }

    @Test
    fun paidOrderGivesKey_thenActivationUnlocksAndClearsPending() = runBlocking {
        controller.buy("full_access") { true }
        api.status = ApiResult.Success(OrderStatus("paid", "DPLA-AAAA-BBBB-CCCC"))
        controller.checkPending()
        assertEquals("DPLA-AAAA-BBBB-CCCC", controller.state.value.readyKey)

        api.activate = ApiResult.Success(ActivateResponse(token(), "full_access", 2))
        controller.activate("DPLA-AAAA-BBBB-CCCC")
        assertEquals("Полный доступ открыт. Спасибо!", controller.state.value.message)
        assertEquals("full_access", controller.state.value.activatedProductId)
        assertNull(mem.id)
        assertEquals(setOf("full_access"), gateway.unlockedProducts.first())
    }

    @Test
    fun failedPaymentClearsPending() = runBlocking {
        controller.buy("full_access") { true }
        api.status = ApiResult.Success(OrderStatus("failed"))
        controller.checkPending()
        assertNull(mem.id)
        assertNull(controller.state.value.pendingOrderId)
    }

    @Test
    fun activationErrorsGiveReadableMessages() = runBlocking {
        api.activate = ApiResult.Failure(FailureKind.HTTP, 400, "invalid_key")
        controller.activate("X")
        assertTrue(controller.state.value.message!!.contains("Такого ключа нет"))
        api.activate = ApiResult.Failure(FailureKind.NETWORK)
        controller.activate("X")
        assertEquals(MSG_OFFLINE, controller.state.value.message)
        assertTrue(gateway.unlockedProducts.first().isEmpty())
    }
}
