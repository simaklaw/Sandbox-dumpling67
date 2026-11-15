package com.example.ads

import android.app.Activity

enum class AdTrigger { LAUNCH, EXIT }

/** APP_OPEN — при входе в приложение (главный экран), INTERSTITIAL — после выхода из игры. */
enum class AdFormat { APP_OPEN, INTERSTITIAL }

/**
 * Источник рекламы (Яндекс или заглушка). Решение «показывать ли» принимает [AdManager]; провайдер лишь сообщает,
 * загружена ли реклама, подгружает её и показывает полноэкранно. SDK инициализируется лениво, при первом [preload],
 * а не в `Application`. Пометку «Реклама» и кнопку закрытия рисует сам SDK.
 */
interface AdProvider {
    /** true, если реклама этого формата уже загружена и готова к показу (без ожидания). */
    fun isAvailable(format: AdFormat): Boolean

    /** Подгрузить рекламу заранее. Вызывать только когда [AdManager] разрешил (нет `remove_ads`, онлайн, не первые запуски). */
    fun preload(activity: Activity)

    /**
     * Показать загруженную рекламу. [onShown] — реклама реально появилась на экране (тогда считаются лимиты),
     * [onFinished] — закрыта или не показалась.
     */
    fun show(activity: Activity, format: AdFormat, onShown: () -> Unit, onFinished: () -> Unit)
}

/** Рекламы нет: если SDK выключен (`ADS_ENABLED=false`) и во всех тестах. */
object NoopProvider : AdProvider {
    override fun isAvailable(format: AdFormat) = false
    override fun preload(activity: Activity) = Unit
    override fun show(activity: Activity, format: AdFormat, onShown: () -> Unit, onFinished: () -> Unit) = onFinished()
}
