package com.example.monetization

import com.google.crypto.tink.subtle.Ed25519Verify
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Проверенное право: данные из подписанного токена. */
data class VerifiedEntitlement(
    val productId: String,
    val licenseId: String,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long?
)

sealed interface VerifyResult {
    data class Valid(val entitlement: VerifiedEntitlement) : VerifyResult
    data class Invalid(val reason: String) : VerifyResult
}

@Serializable
private data class Payload(
    @SerialName("product_id") val productId: String,
    @SerialName("license_id") val licenseId: String,
    @SerialName("issued_at") val issuedAt: Long,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val v: Int
)

/**
 * Проверка подписанного токена права без сети. Формат: `base64url(payloadJson).base64url(ed25519Signature)`;
 * подпись считается по байтам payload ровно так, как они переданы (до base64). Публичный ключ — raw 32 байта в base64.
 * Пустой или битый ключ => всё отклоняется (права без подписи недопустимы).
 */
@OptIn(ExperimentalEncodingApi::class)
class EntitlementVerifier(
    publicKeyBase64: String,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val verifier: Ed25519Verify? = try {
        val key = Base64.Default.decode(publicKeyBase64.trim())
        if (key.size == 32) Ed25519Verify(key) else null
    } catch (e: IllegalArgumentException) {
        null
    }
    private val json = Json { ignoreUnknownKeys = true }

    fun verify(token: String): VerifyResult {
        val v = verifier ?: return VerifyResult.Invalid("no_public_key")
        val parts = token.trim().split('.')
        if (parts.size != 2) return VerifyResult.Invalid("format")
        val url = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
        val payloadBytes: ByteArray
        val signature: ByteArray
        try {
            payloadBytes = url.decode(parts[0])
            signature = url.decode(parts[1])
        } catch (e: IllegalArgumentException) {
            return VerifyResult.Invalid("base64")
        }
        try {
            v.verify(signature, payloadBytes)
        } catch (e: GeneralSecurityException) {
            return VerifyResult.Invalid("signature")
        }
        val p = try {
            json.decodeFromString(Payload.serializer(), String(payloadBytes, Charsets.UTF_8))
        } catch (e: kotlinx.serialization.SerializationException) {
            return VerifyResult.Invalid("payload")
        } catch (e: IllegalArgumentException) {
            return VerifyResult.Invalid("payload")
        }
        if (p.v != 1) return VerifyResult.Invalid("version")
        val exp = p.expiresAt?.let { it * 1000 }
        if (exp != null && exp <= nowMillis()) return VerifyResult.Invalid("expired")
        return VerifyResult.Valid(VerifiedEntitlement(p.productId, p.licenseId, p.issuedAt * 1000, exp))
    }
}
