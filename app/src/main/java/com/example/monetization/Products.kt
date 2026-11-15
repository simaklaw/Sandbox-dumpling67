package com.example.monetization

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Идентификаторы продуктов; цены и названия приходят с backend (`GET /products`), в APK их нет. */
object ProductIds {
    const val FULL_ACCESS = "full_access"
    const val WINTER_PACK = "winter_pack"
}

@Serializable
data class Product(
    val id: String,
    val title: String,
    val kind: String,
    val price: Double
) {
    /** «299 ₽»; цена берётся только с сервера. */
    val priceLabel: String
        get() = (if (price % 1.0 == 0.0) price.toLong().toString() else "%.2f".format(price)) + " ₽"
}

@Serializable
data class Amount(val value: String, val currency: String)

@Serializable
data class OrderResponse(
    @SerialName("order_id") val orderId: String,
    @SerialName("confirmation_url") val confirmationUrl: String,
    val amount: Amount,
    val description: String = ""
)

@Serializable
data class OrderStatus(
    val status: String,
    @SerialName("license_key") val licenseKey: String? = null
)

@Serializable
data class ActivateResponse(
    val entitlement: String,
    @SerialName("product_id") val productId: String,
    @SerialName("activations_left") val activationsLeft: Int = 0
)

@Serializable
data class LicenseStatus(val active: Boolean)

@Serializable
internal data class ErrorBody(val error: String? = null)
