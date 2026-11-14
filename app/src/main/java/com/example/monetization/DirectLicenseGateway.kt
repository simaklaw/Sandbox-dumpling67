package com.example.monetization

import com.example.data.local.EntitlementDao
import com.example.data.local.EntitlementEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface ActivationOutcome {
    data class Activated(val productId: String, val activationsLeft: Int) : ActivationOutcome
    object InvalidKey : ActivationOutcome
    object NotPaid : ActivationOutcome
    object TooManyActivations : ActivationOutcome
    object Revoked : ActivationOutcome
    /** Сервер вернул токен, подпись которого не прошла проверку: права не выдаются. */
    object BadSignature : ActivationOutcome
    /** Нет сети или сервер недоступен; игры работают как обычно. */
    object Offline : ActivationOutcome
    object NotConfigured : ActivationOutcome
    data class Error(val code: String?) : ActivationOutcome
}

/** Нормализация ключа, введённого родителем: регистр, пробелы, длинные тире. */
fun normalizeLicenseKey(raw: String): String =
    raw.trim().uppercase().replace('–', '-').replace('—', '-').replace(" ", "")

/**
 * Фасад покупок и прав: продукты, заказ, активация ключа, проверка отзыва, список разблокированного.
 * Права = подписанный токен; в Room лежит его кэш. Локального флага `premium` без токена нет.
 */
class DirectLicenseGateway(
    private val api: LicenseApi,
    private val verifier: EntitlementVerifier,
    private val dao: EntitlementDao,
    private val installId: () -> String,
    private val appVersion: String,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    /** Разблокированные продукты: только строки с активным правом и токеном, прошедшим проверку подписи прямо сейчас. */
    val unlockedProducts: Flow<Set<String>> = dao.activeDurableFlow().map { rows ->
        rows.filter { verifier.verify(it.token) is VerifyResult.Valid }.map { it.productId }.toSet()
    }

    suspend fun products() = api.products()

    suspend fun startPurchase(productId: String) = api.createOrder(productId)

    suspend fun checkOrder(orderId: String) = api.order(orderId)

    suspend fun activate(rawKey: String): ActivationOutcome {
        val key = normalizeLicenseKey(rawKey)
        if (key.isEmpty()) return ActivationOutcome.InvalidKey
        return when (val r = api.activate(key, installId(), appVersion)) {
            is ApiResult.Success -> store(r.value)
            is ApiResult.Failure -> when (r.kind) {
                FailureKind.NOT_CONFIGURED -> ActivationOutcome.NotConfigured
                FailureKind.NETWORK -> ActivationOutcome.Offline
                FailureKind.PARSE -> ActivationOutcome.Error("parse")
                FailureKind.HTTP -> when (r.error) {
                    "invalid_key" -> ActivationOutcome.InvalidKey
                    "not_paid" -> ActivationOutcome.NotPaid
                    "too_many_activations" -> ActivationOutcome.TooManyActivations
                    "revoked" -> ActivationOutcome.Revoked
                    else -> ActivationOutcome.Error(r.error ?: "http_${r.httpCode}")
                }
            }
        }
    }

    private suspend fun store(resp: ActivateResponse): ActivationOutcome {
        val verified = verifier.verify(resp.entitlement)
        if (verified !is VerifyResult.Valid) return ActivationOutcome.BadSignature
        val e = verified.entitlement
        if (e.productId != resp.productId) return ActivationOutcome.BadSignature
        val now = nowMillis()
        // Идемпотентно по licenseId: повторная активация обновляет ту же строку, а не создаёт вторую.
        dao.upsert(
            EntitlementEntity(
                licenseId = e.licenseId, productId = e.productId, kind = "durable",
                issuedAtMillis = e.issuedAtMillis, active = true, source = "direct",
                token = resp.entitlement, updatedAtMillis = now
            )
        )
        return ActivationOutcome.Activated(e.productId, resp.activationsLeft)
    }

    /**
     * Проверка отзыва (возврата) при наличии сети. Отзываем только по прямому ответу сервера «active=false»;
     * сетевая ошибка права не трогает (офлайн-грация).
     * @return сколько прав отозвано
     */
    suspend fun revalidate(): Int {
        var revoked = 0
        for (row in dao.activeDurable()) {
            val r = api.status(row.licenseId, installId())
            if (r is ApiResult.Success && !r.value.active) {
                dao.deactivate(row.licenseId, nowMillis())
                revoked++
            }
        }
        return revoked
    }
}
