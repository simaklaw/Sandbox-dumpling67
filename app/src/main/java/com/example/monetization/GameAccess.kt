package com.example.monetization

/**
 * Какие игры бесплатны и когда игра доступна. Чистая логика без Android.
 * Модель «все игры бесплатны»: платный гейтинг выключен константой [PREMIUM_GATING_ENABLED], доступно всё независимо от
 * `MONETIZATION_ENABLED` и покупок. Код гейтинга сохранён для будущих платных функций; включать только решением владельца.
 */
object GameAccess {
    /** Платный слой игр выключен: все игры бесплатны. */
    const val PREMIUM_GATING_ENABLED = false

    /** Бесплатный слой (индексы `pageIndex`): Зверята, Кормление, Шарики, Рисование, Поп-ит, Пузырчатая плёнка,
     * Смешинки, Кинетический песок, Телефончик, Дождик, Фигурки-окошки, Кнопочная радость. Всё остальное — премиум. */
    val FREE_GAMES: Set<Int> = setOf(0, 1, 7, 9, 16, 20, 21, 22, 23, 24, 26, 34)

    /** Игры, которые открывает отдельный набор (кроме полного доступа). Состав `winter_pack` решает владелец: пока пусто. */
    val PACK_GAMES: Map<String, Set<Int>> = mapOf(ProductIds.WINTER_PACK to emptySet())

    fun isFree(pageIndex: Int): Boolean = pageIndex in FREE_GAMES

    fun canPlay(
        pageIndex: Int,
        monetizationEnabled: Boolean,
        unlockedProducts: Set<String>,
        gatingEnabled: Boolean = PREMIUM_GATING_ENABLED
    ): Boolean {
        if (!gatingEnabled || !monetizationEnabled) return true
        if (isFree(pageIndex)) return true
        if (ProductIds.FULL_ACCESS in unlockedProducts) return true
        return unlockedProducts.any { pageIndex in (PACK_GAMES[it] ?: emptySet()) }
    }
}
