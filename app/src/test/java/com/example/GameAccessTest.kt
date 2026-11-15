package com.example

import com.example.monetization.GameAccess
import com.example.monetization.ProductIds
import com.example.screens.FOLDER_ITEMS
import com.example.screens.FolderType
import com.example.screens.visibleSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAccessTest {
    private val allCatalog = FOLDER_ITEMS.values.flatten().map { it.pageIndex }
    // Игры с главного экрана вне папок: Кормление 1, Шарики 7, Рисование 9, Смешинки 21, Телефончик 23.
    private val homeLevel = listOf(1, 7, 9, 21, 23)

    @Test
    fun monetizationOffOpensEverything() {
        (allCatalog + homeLevel + listOf(5, 11, 28)).forEach {
            assertTrue(GameAccess.canPlay(it, monetizationEnabled = false, unlockedProducts = emptySet()))
        }
    }

    @Test
    fun withoutPurchaseOnlyTwelveFreeGamesPlay() {
        assertEquals(12, GameAccess.FREE_GAMES.size)
        val playable = (allCatalog + homeLevel).toSet().filter { GameAccess.canPlay(it, true, emptySet()) }.toSet()
        assertEquals(GameAccess.FREE_GAMES, playable)
        // боевые игры тоже платные, но остаются в каталоге (владелец: игры для всех возрастов)
        listOf(28, 31, 4).forEach { assertFalse(GameAccess.canPlay(it, true, emptySet())) }
    }

    @Test
    fun everyFreeGameExistsInCatalogOrOnHome() {
        val known = (allCatalog + homeLevel).toSet()
        GameAccess.FREE_GAMES.forEach { assertTrue("Бесплатной игры $it нет в каталоге", it in known) }
    }

    @Test
    fun fullAccessOpensEverything() {
        (allCatalog + homeLevel + listOf(11)).forEach {
            assertTrue(GameAccess.canPlay(it, true, setOf(ProductIds.FULL_ACCESS)))
        }
    }

    @Test
    fun unknownProductDoesNotOpenPremium() {
        assertFalse(GameAccess.canPlay(28, true, setOf("tip_50")))
        assertFalse(GameAccess.canPlay(28, true, setOf(ProductIds.WINTER_PACK))) // состав набора пока пуст
    }

    @Test
    fun lockedCatalogHidesEmptySectionsAndKitchen() {
        val free: (Int) -> Boolean = { GameAccess.canPlay(it, true, emptySet()) }
        assertTrue(visibleSections(FolderType.KITCHEN, free).isEmpty())
        val games = visibleSections(FolderType.GAMES, free)
        assertTrue(games.isNotEmpty())
        assertTrue(games.all { it.items.isNotEmpty() })
        assertTrue(games.flatMap { it.items }.all { it.pageIndex in GameAccess.FREE_GAMES })
        // с полным доступом каталог целиком
        val all: (Int) -> Boolean = { GameAccess.canPlay(it, true, setOf(ProductIds.FULL_ACCESS)) }
        assertEquals(allCatalog.size, visibleSections(FolderType.GAMES, all).flatMap { it.items }.size + visibleSections(FolderType.KITCHEN, all).flatMap { it.items }.size)
    }

    @Test
    fun mainActivityGuardsPremiumGames() {
        val src = listOf("src/main/java/com/example/MainActivity.kt", "app/src/main/java/com/example/MainActivity.kt")
            .map { java.io.File(it) }.first { it.exists() }.readText()
        assertTrue("MainActivity должна проверять canPlay перед отрисовкой игры", src.contains("!canPlay(screenState.pageIndex)"))
        assertTrue(src.contains("canPlay = canPlay") && src.contains("kitchenVisible = kitchenVisible"))
    }
}
