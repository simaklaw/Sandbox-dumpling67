package com.example.monetization

/**
 * Какие игры бесплатны и когда игра доступна. Чистая логика без Android.
 * Пока монетизация выключена (`BuildConfig.MONETIZATION_ENABLED = false`), доступно всё: поведение приложения не меняется.
 */
object GameAccess {
    /** Бесплатный слой (индексы `pageIndex`): Зверята, Кормление, Шарики, Рисование, Поп-ит, Пузырчатая плёнка,
     * Смешинки, Кинетический песок, Телефончик, Дождик, Фигурки-окошки, Кнопочная радость. Всё остальное — премиум. */
    val FREE_GAMES: Set<Int> = setOf(0, 1, 7, 9, 16, 20, 21, 22, 23, 24, 26, 34)

    /** Игры, которые открывает отдельный набор (кроме полного доступа). Состав `winter_pack` решает владелец: пока пусто. */
    val PACK_GAMES: Map<String, Set<Int>> = mapOf(ProductIds.WINTER_PACK to emptySet())

    fun isFree(pageIndex: Int): Boolean = pageIndex in FREE_GAMES

    fun canPlay(pageIndex: Int, monetizationEnabled: Boolean, unlockedProducts: Set<String>): Boolean {
        if (!monetizationEnabled) return true
        if (isFree(pageIndex)) return true
        if (ProductIds.FULL_ACCESS in unlockedProducts) return true
        return unlockedProducts.any { pageIndex in (PACK_GAMES[it] ?: emptySet()) }
    }
}
