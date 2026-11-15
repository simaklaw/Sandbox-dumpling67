package com.example

import com.example.monetization.EntitlementVerifier
import com.example.monetization.VerifyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class EntitlementVerifierTest {
    private val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    // X.509-кодировка Ed25519: последние 32 байта — сам ключ.
    private val pubB64 = Base64.getEncoder().encodeToString(kp.public.encoded.takeLast(32).toByteArray())

    private fun token(json: String, key: java.security.PrivateKey = kp.private): String {
        val bytes = json.toByteArray()
        val sig = Signature.getInstance("Ed25519").apply { initSign(key); update(bytes) }.sign()
        val enc = Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString(bytes) + "." + enc.encodeToString(sig)
    }

    private fun payload(exp: String = "null", v: Int = 1, product: String = "full_access") =
        """{"product_id":"$product","license_id":"L-1","issued_at":1731500000,"expires_at":$exp,"v":$v}"""

    @Test
    fun validTokenIsAccepted() {
        val r = EntitlementVerifier(pubB64).verify(token(payload()))
        assertTrue(r is VerifyResult.Valid)
        r as VerifyResult.Valid
        assertEquals("full_access", r.entitlement.productId)
        assertEquals("L-1", r.entitlement.licenseId)
    }

    @Test
    fun tamperedPayloadIsRejected() {
        val t = token(payload())
        val enc = Base64.getUrlEncoder().withoutPadding()
        val forged = enc.encodeToString(payload(product = "winter_pack").toByteArray()) + "." + t.substringAfter('.')
        assertEquals("signature", (EntitlementVerifier(pubB64).verify(forged) as VerifyResult.Invalid).reason)
    }

    @Test
    fun signatureFromAnotherKeyIsRejected() {
        val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val r = EntitlementVerifier(pubB64).verify(token(payload(), other.private))
        assertEquals("signature", (r as VerifyResult.Invalid).reason)
    }

    @Test
    fun expiredAndUnknownVersionAreRejected() {
        val now = 2_000_000_000_000L
        val v = EntitlementVerifier(pubB64) { now }
        assertEquals("expired", (v.verify(token(payload(exp = "1000"))) as VerifyResult.Invalid).reason)
        assertEquals("version", (v.verify(token(payload(v = 2))) as VerifyResult.Invalid).reason)
        assertTrue(v.verify(token(payload(exp = "4000000000"))) is VerifyResult.Valid)
    }

    @Test
    fun garbageAndMissingKeyAreRejected() {
        val v = EntitlementVerifier(pubB64)
        assertTrue(v.verify("") is VerifyResult.Invalid)
        assertTrue(v.verify("abc") is VerifyResult.Invalid)
        assertTrue(v.verify("a.b.c") is VerifyResult.Invalid)
        assertTrue(v.verify("!!!.???") is VerifyResult.Invalid)
        assertEquals("no_public_key", (EntitlementVerifier("").verify(token(payload())) as VerifyResult.Invalid).reason)
        assertEquals("no_public_key", (EntitlementVerifier("не-base64").verify(token(payload())) as VerifyResult.Invalid).reason)
    }

    @Test
    fun rfc8032Vector1Verifies() {
        // RFC 8032, тест 1: пустое сообщение. Проверяем саму реализацию Ed25519 на эталоне.
        val pk = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val sig = ("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b")
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        com.google.crypto.tink.subtle.Ed25519Verify(pk).verify(sig, ByteArray(0))
    }
}
