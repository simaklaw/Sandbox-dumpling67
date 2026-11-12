package com.example

import com.example.screens.FOLDER_ITEMS
import com.example.screens.FolderType
import com.example.screens.Boxes.BoxSurpriseCatalog
import com.example.screens.Shapes.SorterShapeKind
import com.example.screens.folderTypeForPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Проверяет единый список игр по папкам и логику «Назад/Домой» (Шаг 14). */
class GameNavigationTest {

    @Test
    fun everyGameLivesInExactlyOneFolder() {
        val all = FOLDER_ITEMS.values.flatten().map { it.pageIndex }
        assertEquals("Игра не должна дублироваться в папках", all.size, all.toSet().size)
    }

    @Test
    fun newStep14GamesAreInGamesFolder() {
        listOf(24, 26, 27, 28, 30, 31, 32, 33, 34, 35).forEach { assertEquals(FolderType.GAMES, folderTypeForPage(it)) }
    }

    @Test
    fun kitchenGamesReturnToKitchen() {
        listOf(6).forEach { assertEquals(FolderType.KITCHEN, folderTypeForPage(it)) }
        assertEquals(AppScreenState.Folder("Кухня", FolderType.KITCHEN), parentStateForGame(6))
    }

    @Test
    fun homeLevelGamesReturnHome() {
        listOf(1, 7, 9).forEach {
            assertNull(folderTypeForPage(it))
            assertEquals(AppScreenState.Home, parentStateForGame(it))
        }
    }

    @Test
    fun retiredPageIndexesAreNotInFolders() {
        // 25 — «Кто в коробке?», 45 — «Гардероб героев»: комнаты убраны, индексы не переиспользуем.
        val inFolders = FOLDER_ITEMS.values.flatten().map { it.pageIndex }.toSet()
        listOf(25, 45).forEach { assertTrue("Индекс $it выведен из использования", it !in inFolders) }
    }

    @Test
    fun formerHomeSectionGamesMovedToGames() {
        // Раздел «Кухня → Дом» упразднён: все его игры живут в «Играх», в кухне остаются только кухонные.
        listOf(38, 39, 40, 41, 42, 43, 44, 46, 47, 48, 49, 50).forEach {
            assertEquals("Игра $it должна быть в «Играх»", FolderType.GAMES, folderTypeForPage(it))
        }
        assertEquals(listOf(37, 36, 6, 51), FOLDER_ITEMS[FolderType.KITCHEN]!!.map { it.pageIndex })
        assertEquals(AppScreenState.Folder("Игры", FolderType.GAMES), parentStateForGame(38))
    }

    @Test
    fun step14ContentIsConsistent() {
        val ids = BoxSurpriseCatalog.ALL_SURPRISES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue("Для 4 коробок нужно минимум 4 разных сюрприза", ids.size >= 4)
        assertEquals(6, SorterShapeKind.values().size)
    }
}
