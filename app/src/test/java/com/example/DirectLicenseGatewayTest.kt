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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DirectLicenseGatewayTest {
    private val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val pub = Base64.getEncoder().encodeToString(kp.public.encoded.takeLast(32).toByteArray())
    private lateinit var db: AppDatabase

    private class FakeApi : LicenseApi {
        var activate: ApiResult<ActivateResponse> = ApiResult.Failure(FailureKind.NETWORK)
        var status: ApiResult<LicenseStatus> = ApiResult.Failure(FailureKind.NETWORK)
        override suspend fun products() = ApiResult.Success(emptyList<Product>())
        override suspend fun createOrder(productId: String) = ApiResult.Failure(FailureKind.NETWORK)
        override suspend fun order(orderId: String) = ApiResult.Failure(FailureKind.NETWORK)
        override suspend fun activate(licenseKey: String, installId: String, appVersion: String) = activate
        override suspend fun status(licenseId: String, installId: String) = status
    }

    private fun token(product: String, license: String = "L1", key: PrivateKey = kp.private): String {
        val json = """{"product_id":"$product","license_id":"$license","issued_at":1731500000,"expires_at":null,"v":1}"""
        val bytes = json.toByteArray()
        val sig = Signature.getInstance("Ed25519").apply { initSign(key); update(bytes) }.sign()
        val enc = Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString(bytes) + "." + enc.encodeToString(sig)
    }

    private val api = FakeApi()
    private fun gateway() = DirectLicenseGateway(api, EntitlementVerifier(pub), db.entitlementDao(), { "install-1" }, "1.0")

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun tearDown() { db.close() }

    @Test
    fun activationUnlocksProduct_andRepeatDoesNotDuplicate() = runBlocking {
        val g = gateway()
        api.activate = ApiResult.Success(ActivateResponse(token("full_access"), "full_access", 2))
        assertEquals(ActivationOutcome.Activated("full_access", 2), g.activate(" dpla-aaaa-bbbb-cccc "))
        assertEquals(ActivationOutcome.Activated("full_access", 2), g.activate("DPLA-AAAA-BBBB-CCCC"))
        assertEquals(setOf("full_access"), g.unlockedProducts.first())
        assertEquals(1, db.entitlementDao().activeDurable().size)
    }

    @Test
    fun forgedTokenGrantsNothing() = runBlocking {
        val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        api.activate = ApiResult.Success(ActivateResponse(token("full_access", key = other.private), "full_access", 2))
        assertEquals(ActivationOutcome.BadSignature, gateway().activate("DPLA-X"))
        assertTrue(gateway().unlockedProducts.first().isEmpty())
    }

    @Test
    fun tokenForDifferentProductThanClaimedIsRejected() = runBlocking {
        api.activate = ApiResult.Success(ActivateResponse(token("winter_pack"), "full_access", 1))
        assertEquals(ActivationOutcome.BadSignature, gateway().activate("DPLA-X"))
    }

    @Test
    fun rowWithBrokenTokenIsNotUnlocked_evenIfActiveInDb() = runBlocking {
        // «premium» одним флагом в БД без валидного токена не открывает ничего.
        db.entitlementDao().upsert(
            com.example.data.local.EntitlementEntity("L9", "full_access", "durable", 1, true, "direct", "garbage", 1)
        )
        assertTrue(gateway().unlockedProducts.first().isEmpty())
    }

    @Test
    fun backendErrorsMapToOutcomes() = runBlocking {
        val g = gateway()
        fun fail(code: String) { api.activate = ApiResult.Failure(FailureKind.HTTP, 400, code) }
        fail("invalid_key"); assertEquals(ActivationOutcome.InvalidKey, g.activate("K"))
        fail("not_paid"); assertEquals(ActivationOutcome.NotPaid, g.activate("K"))
        fail("too_many_activations"); assertEquals(ActivationOutcome.TooManyActivations, g.activate("K"))
        fail("revoked"); assertEquals(ActivationOutcome.Revoked, g.activate("K"))
        api.activate = ApiResult.Failure(FailureKind.NETWORK); assertEquals(ActivationOutcome.Offline, g.activate("K"))
        assertEquals(ActivationOutcome.InvalidKey, g.activate("   "))
    }

    @Test
    fun revalidateRevokesOnlyOnExplicitAnswer_networkErrorKeepsAccess() = runBlocking {
        val g = gateway()
        api.activate = ApiResult.Success(ActivateResponse(token("full_access"), "full_access", 1))
        g.activate("K")

        api.status = ApiResult.Failure(FailureKind.NETWORK)
        assertEquals(0, g.revalidate())
        assertEquals(setOf("full_access"), g.unlockedProducts.first())

        api.status = ApiResult.Success(LicenseStatus(active = true))
        assertEquals(0, g.revalidate())
        assertEquals(setOf("full_access"), g.unlockedProducts.first())

        api.status = ApiResult.Success(LicenseStatus(active = false)) // возврат
        assertEquals(1, g.revalidate())
        assertTrue(g.unlockedProducts.first().isEmpty())
    }
}
