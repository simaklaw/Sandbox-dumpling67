package com.example.monetization

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

enum class FailureKind { NOT_CONFIGURED, NETWORK, HTTP, PARSE }

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    /** [error] — код ошибки backend (`invalid_key`, `not_paid`, ...), если он прислан. */
    data class Failure(val kind: FailureKind, val httpCode: Int? = null, val error: String? = null) : ApiResult<Nothing>
}

/** Контракт backend; интерфейс нужен, чтобы подменять сеть в тестах. */
interface LicenseApi {
    suspend fun products(): ApiResult<List<Product>>
    suspend fun createOrder(productId: String): ApiResult<OrderResponse>
    suspend fun order(orderId: String): ApiResult<OrderStatus>
    suspend fun activate(licenseKey: String, installId: String, appVersion: String): ApiResult<ActivateResponse>
    suspend fun status(licenseId: String, installId: String): ApiResult<LicenseStatus>
}

@Serializable private data class CreateOrderBody(@SerialName("product_id") val productId: String)
@Serializable private data class ActivateBody(
    @SerialName("license_key") val licenseKey: String,
    @SerialName("install_id") val installId: String,
    @SerialName("app_version") val appVersion: String
)
@Serializable private data class StatusBody(
    @SerialName("license_id") val licenseId: String,
    @SerialName("install_id") val installId: String
)

/**
 * HTTP-клиент к НАШЕМУ backend (не к ЮKassa). Только HTTPS (http допускается для localhost в тестах),
 * таймауты, никаких секретов: платёжные ключи живут на сервере. Вызывать только из родительской зоны.
 */
class LicenseApiClient(
    baseUrl: String,
    private val http: OkHttpClient = defaultHttp()
) : LicenseApi {
    private val base = baseUrl.trimEnd('/').toHttpUrlOrNull()?.takeIf { isAllowed(it.scheme, it.host) }
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override suspend fun products() = get("api/v1/products", ListSerializer(Product.serializer()))

    override suspend fun createOrder(productId: String): ApiResult<OrderResponse> {
        val r = post("api/v1/orders", CreateOrderBody.serializer(), CreateOrderBody(productId), OrderResponse.serializer())
        // Платёжную страницу открываем только по https: подмена на http:// или чужую схему недопустима.
        if (r is ApiResult.Success && !r.value.confirmationUrl.startsWith("https://")) {
            return ApiResult.Failure(FailureKind.PARSE, error = "bad_confirmation_url")
        }
        return r
    }

    override suspend fun order(orderId: String) =
        get("api/v1/orders/" + java.net.URLEncoder.encode(orderId, "UTF-8"), OrderStatus.serializer())

    override suspend fun activate(licenseKey: String, installId: String, appVersion: String) =
        post("api/v1/licenses/activate", ActivateBody.serializer(), ActivateBody(licenseKey, installId, appVersion), ActivateResponse.serializer())

    override suspend fun status(licenseId: String, installId: String) =
        post("api/v1/licenses/status", StatusBody.serializer(), StatusBody(licenseId, installId), LicenseStatus.serializer())

    private suspend fun <T> get(path: String, ser: KSerializer<T>): ApiResult<T> {
        val url = base?.newBuilder()?.addPathSegments(path)?.build()
            ?: return ApiResult.Failure(FailureKind.NOT_CONFIGURED)
        return execute(Request.Builder().url(url).get().build(), ser)
    }

    private suspend fun <B, T> post(path: String, bodySer: KSerializer<B>, body: B, ser: KSerializer<T>): ApiResult<T> {
        val url = base?.newBuilder()?.addPathSegments(path)?.build()
            ?: return ApiResult.Failure(FailureKind.NOT_CONFIGURED)
        val rb = json.encodeToString(bodySer, body).toRequestBody(jsonType)
        return execute(Request.Builder().url(url).post(rb).build(), ser)
    }

    private suspend fun <T> execute(request: Request, ser: KSerializer<T>): ApiResult<T> {
        val response = try {
            http.newCall(request).await()
        } catch (e: IOException) {
            return ApiResult.Failure(FailureKind.NETWORK, error = e.javaClass.simpleName)
        }
        response.use { r ->
            val text = try { r.body?.string().orEmpty() } catch (e: IOException) {
                return ApiResult.Failure(FailureKind.NETWORK, error = e.javaClass.simpleName)
            }
            if (!r.isSuccessful) {
                val code = runCatching { json.decodeFromString(ErrorBody.serializer(), text).error }.getOrNull()
                return ApiResult.Failure(FailureKind.HTTP, r.code, code)
            }
            return try {
                ApiResult.Success(json.decodeFromString(ser, text))
            } catch (e: kotlinx.serialization.SerializationException) {
                ApiResult.Failure(FailureKind.PARSE, r.code)
            } catch (e: IllegalArgumentException) {
                ApiResult.Failure(FailureKind.PARSE, r.code)
            }
        }
    }

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        internal fun isAllowed(scheme: String, host: String): Boolean =
            scheme == "https" || (scheme == "http" && (host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"))
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWith(Result.failure(e))
        }
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
