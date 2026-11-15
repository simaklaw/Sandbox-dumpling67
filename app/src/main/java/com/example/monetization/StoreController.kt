package com.example.monetization

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface ProductsState {
    object Loading : ProductsState
    data class Loaded(val products: List<Product>) : ProductsState
    data class Failed(val message: String) : ProductsState
}

data class StoreState(
    val products: ProductsState = ProductsState.Loading,
    val busy: Boolean = false,
    /** Заказ, отправленный на оплату: помним между запусками, чтобы после оплаты можно было проверить статус. */
    val pendingOrderId: String? = null,
    /** Ключ оплаченного заказа, готовый к активации. */
    val readyKey: String? = null,
    val message: String? = null,
    val activatedProductId: String? = null
)

/** Где хранится id заказа в ожидании оплаты (SharedPreferences в приложении, память в тестах). */
interface PendingOrderStore {
    fun get(): String?
    fun set(orderId: String?)
}

const val MSG_OFFLINE = "Покупки требуют интернет. Купленные игры работают и без него."

/**
 * Логика родительского магазина без UI: продукты, покупка, проверка оплаты, активация ключа.
 * Статус оплаты определяет только backend (его ответ на `order`); слова «я оплатил» ничего не открывают.
 * Любая ошибка превращается в понятное сообщение, игры не блокируются.
 */
class StoreController(
    private val gateway: DirectLicenseGateway,
    private val pending: PendingOrderStore
) {
    private val _state = MutableStateFlow(StoreState(pendingOrderId = pending.get()))
    val state: StateFlow<StoreState> = _state.asStateFlow()

    private fun failureText(f: ApiResult.Failure, whenHttp: String): String = when (f.kind) {
        FailureKind.NETWORK -> MSG_OFFLINE
        FailureKind.NOT_CONFIGURED -> "Магазин пока недоступен."
        FailureKind.PARSE -> "Сервер ответил неожиданно. Попробуйте позже."
        FailureKind.HTTP -> whenHttp
    }

    suspend fun load() {
        _state.update { it.copy(products = ProductsState.Loading) }
        val r = gateway.products()
        _state.update {
            it.copy(
                products = when (r) {
                    is ApiResult.Success -> ProductsState.Loaded(r.value.filter { p -> p.kind == "durable" })
                    is ApiResult.Failure -> ProductsState.Failed(failureText(r, "Не удалось загрузить список. Попробуйте позже."))
                }
            )
        }
        // Проверка возврата при наличии сети; ошибка сети права не трогает.
        if (r is ApiResult.Success) gateway.revalidate()
    }

    /** Создаёт заказ и отдаёт адрес платёжной страницы в [open]. */
    suspend fun buy(productId: String, open: (String) -> Boolean) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null, activatedProductId = null) }
        when (val r = gateway.startPurchase(productId)) {
            is ApiResult.Success -> {
                pending.set(r.value.orderId)
                val opened = open(r.value.confirmationUrl)
                _state.update {
                    it.copy(
                        busy = false, pendingOrderId = r.value.orderId, readyKey = null,
                        message = if (opened) "После оплаты вернитесь сюда и нажмите «Я оплатил(а) — проверить»."
                        else "Не удалось открыть страницу оплаты: нет браузера."
                    )
                }
            }
            is ApiResult.Failure -> _state.update {
                it.copy(busy = false, message = failureText(r, "Не удалось начать покупку. Попробуйте позже."))
            }
        }
    }

    suspend fun checkPending() {
        val id = _state.value.pendingOrderId ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        when (val r = gateway.checkOrder(id)) {
            is ApiResult.Success -> when (r.value.status) {
                "paid" -> {
                    val key = r.value.licenseKey
                    _state.update {
                        it.copy(
                            busy = false, readyKey = key,
                            message = if (key != null) "Оплата прошла. Запишите ключ и нажмите «Активировать»." else "Оплата прошла, ключ ещё готовится. Проверьте через минуту."
                        )
                    }
                }
                "failed" -> {
                    pending.set(null)
                    _state.update { it.copy(busy = false, pendingOrderId = null, readyKey = null, message = "Оплата не прошла. Можно попробовать ещё раз.") }
                }
                else -> _state.update { it.copy(busy = false, message = "Оплата ещё не подтверждена. Подождите минуту и проверьте снова.") }
            }
            is ApiResult.Failure -> _state.update {
                it.copy(busy = false, message = failureText(r, "Не удалось проверить оплату. Попробуйте позже."))
            }
        }
    }

    suspend fun activate(rawKey: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null, activatedProductId = null) }
        val outcome = gateway.activate(rawKey)
        if (outcome is ActivationOutcome.Activated) {
            pending.set(null)
            _state.update {
                it.copy(busy = false, pendingOrderId = null, readyKey = null, activatedProductId = outcome.productId,
                    message = activatedMessage(outcome.productId))
            }
        } else {
            _state.update { it.copy(busy = false, message = outcomeMessage(outcome)) }
        }
    }

    companion object {
        fun activatedMessage(productId: String): String = when (productId) {
            ProductIds.REMOVE_ADS -> "Реклама отключена навсегда. Спасибо!"
            ProductIds.FULL_ACCESS -> "Полный доступ открыт. Спасибо!"
            else -> "Покупка активирована. Спасибо!"
        }

        fun outcomeMessage(o: ActivationOutcome): String = when (o) {
            is ActivationOutcome.Activated -> "Готово."
            ActivationOutcome.InvalidKey -> "Такого ключа нет. Проверьте, что ввели его без ошибок."
            ActivationOutcome.NotPaid -> "Этот ключ ещё не оплачен."
            ActivationOutcome.TooManyActivations -> "Ключ уже активирован на трёх устройствах."
            ActivationOutcome.Revoked -> "Платёж по этому ключу возвращён, ключ больше не действует."
            ActivationOutcome.BadSignature -> "Не удалось проверить подлинность лицензии. Обновите приложение или напишите в поддержку."
            ActivationOutcome.Offline -> MSG_OFFLINE
            ActivationOutcome.NotConfigured -> "Магазин пока недоступен."
            is ActivationOutcome.Error -> "Не удалось активировать. Попробуйте позже."
        }
    }
}
