package com.example.ads

/** Все лимиты рекламы в одном месте. Менять только здесь; тесты берут значения отсюда. */
object AdConfig {
    const val GLOBAL_COOLDOWN_MS = 5 * 60 * 1000L   // не чаще одного показа в 5 минут
    const val DAILY_LIMIT = 4                        // не больше 4 показов в сутки
    const val MIN_LAUNCH_BEFORE_FIRST_AD = 3         // первые 3 запуска приложения без рекламы (первая реклама на 4-м)
    const val CLICKABILITY_DELAY_MS = 1500L          // тапы по креативу включаются через 1,5 с после показа
    const val EXIT_PROBABILITY = 0.4f                // шанс показа при выходе из игры
    const val HOME_DAILY_LIMIT = 1                   // на главном экране не более 1 раза в сутки
    const val CLOSE_BUTTON_MIN_DP = 48               // кнопка закрытия не меньше 48dp и видна сразу
}
