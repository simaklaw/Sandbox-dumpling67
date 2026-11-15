package com.example.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class AdTrigger { LAUNCH, EXIT }

enum class AdFormat { NATIVE_CARD, INTERSTITIAL }

/**
 * Источник рекламы (Яндекс/ВК или заглушка). Решение «показывать ли» принимает [AdManager]; провайдер только
 * сообщает, готова ли реклама, и рисует креатив. Реальный SDK инициализируется лениво, при первом [isAvailable]/[Content],
 * а не в `Application`. Пометку «Реклама» и кнопку закрытия рисует обёртка, провайдер их не контролирует.
 */
interface AdProvider {
    fun isAvailable(format: AdFormat): Boolean

    /** Креатив. [interactive] = false первые `CLICKABILITY_DELAY_MS`: провайдер не должен реагировать на тапы. */
    @Composable
    fun Content(format: AdFormat, interactive: Boolean, modifier: Modifier)
}

/** Рекламы нет: используется, пока не подключён SDK и во всех тестах. */
object NoopProvider : AdProvider {
    override fun isAvailable(format: AdFormat) = false

    @Composable
    override fun Content(format: AdFormat, interactive: Boolean, modifier: Modifier) = Unit
}
