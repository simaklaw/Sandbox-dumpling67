package com.example

import com.example.monetization.GameAccess
import com.example.monetization.ProductIds
import com.example.screens.FOLDER_ITEMS
import com.example.screens.FolderType
import com.example.screens.visibleSections
import com.example.screens.visibleWorlds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAccessTest {
    private val allCatalog = FOLDER_ITEMS.values.flatten().map { it.pageIndex }
    // Все игры теперь лежат в мирах; список оставлен пустым ради совместимости проверок ниже.
    private val homeLevel = emptyList<Int>()
    private fun visibleCount(canPlay: (Int) -> Boolean) = FolderType.values().sumOf { visibleSections(it, canPlay).flatMap { s -> s.items }.size }

    @Test
    fun allGamesAreFreeByDefault() {
        assertFalse(GameAccess.PREMIUM_GATING_ENABLED)
        // Даже при включённой монетизации и без покупок открыто всё, включая «Кухню» и боевые игры.
        (allCatalog + homeLevel + listOf(5, 11, 28, 31, 4)).forEach {
            assertTrue("игра $it закрыта", GameAccess.canPlay(it, monetizationEnabled = true, unlockedProducts = emptySet()))
        }
        val open: (Int) -> Boolean = { GameAccess.canPlay(it, true, emptySet()) }
        assertEquals(allCatalog.size, visibleCount(open))
        assertEquals(FolderType.values().toList(), visibleWorlds(open))
    }

    @Test
    fun monetizationOffOpensEverything() {
        (allCatalog + homeLevel + listOf(5, 11, 28)).forEach {
            assertTrue(GameAccess.canPlay(it, monetizationEnabled = false, unlockedProducts = emptySet()))
        }
    }

    @Test
    fun withoutPurchaseOnlyTwelveFreeGamesPlay() {
        assertEquals(12, GameAccess.FREE_GAMES.size)
        val playable = (allCatalog + homeLevel).toSet().filter { GameAccess.canPlay(it, true, emptySet(), gatingEnabled = true) }.toSet()
        assertEquals(GameAccess.FREE_GAMES, playable)
        // боевые игры тоже платные, но остаются в каталоге (владелец: игры для всех возрастов)
        listOf(28, 31, 4).forEach { assertFalse(GameAccess.canPlay(it, true, emptySet(), gatingEnabled = true)) }
    }

    @Test
    fun everyFreeGameExistsInCatalogOrOnHome() {
        val known = (allCatalog + homeLevel).toSet()
        GameAccess.FREE_GAMES.forEach { assertTrue("Бесплатной игры $it нет в каталоге", it in known) }
    }

    @Test
    fun fullAccessOpensEverything() {
        (allCatalog + homeLevel + listOf(11)).forEach {
            assertTrue(GameAccess.canPlay(it, true, setOf(ProductIds.FULL_ACCESS), gatingEnabled = true))
        }
    }

    @Test
    fun unknownProductDoesNotOpenPremium() {
        assertFalse(GameAccess.canPlay(28, true, setOf("tip_50"), gatingEnabled = true))
        assertFalse(GameAccess.canPlay(28, true, setOf(ProductIds.WINTER_PACK), gatingEnabled = true)) // состав набора пока пуст
    }

    @Test
    fun lockedCatalogHidesEmptySectionsAndWorlds() {
        val free: (Int) -> Boolean = { GameAccess.canPlay(it, true, emptySet(), gatingEnabled = true) }
        val shown = FolderType.values().flatMap { visibleSections(it, free) }
        assertTrue(shown.all { it.items.isNotEmpty() })
        assertTrue(shown.flatMap { it.items }.all { it.pageIndex in GameAccess.FREE_GAMES })
        // миры без бесплатных игр на главном экране не показываются
        visibleWorlds(free).forEach { assertTrue(visibleSections(it, free).isNotEmpty()) }
        // с полным доступом каталог целиком
        val all: (Int) -> Boolean = { GameAccess.canPlay(it, true, setOf(ProductIds.FULL_ACCESS), gatingEnabled = true) }
        assertEquals(allCatalog.size, visibleCount(all))
    }

    @Test
    fun mainActivityGuardsPremiumGames() {
        val src = listOf("src/main/java/com/example/MainActivity.kt", "app/src/main/java/com/example/MainActivity.kt")
            .map { java.io.File(it) }.first { it.exists() }.readText()
        assertTrue("MainActivity должна проверять canPlay перед отрисовкой игры", src.contains("!canPlay(screenState.pageIndex)"))
        assertTrue(src.contains("canPlay = canPlay"))
    }
}
