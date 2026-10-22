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
        listOf(24, 25, 26, 27, 28, 29).forEach { assertEquals(FolderType.GAMES, folderTypeForPage(it)) }
    }

    @Test
    fun kitchenGamesReturnToKitchen() {
        listOf(13, 8, 6).forEach { assertEquals(FolderType.KITCHEN, folderTypeForPage(it)) }
        assertEquals(AppScreenState.Folder("Кухня", FolderType.KITCHEN), parentStateForGame(13, isToddler2 = false))
    }

    @Test
    fun homeLevelGamesReturnHome() {
        listOf(1, 2, 7, 9).forEach {
            assertNull(folderTypeForPage(it))
            assertEquals(AppScreenState.Home, parentStateForGame(it, isToddler2 = false))
        }
    }

    @Test
    fun toddlerModeHasNoFolders() {
        listOf(0, 13, 24, 26).forEach {
            assertEquals(AppScreenState.Home, parentStateForGame(it, isToddler2 = true))
        }
    }

    @Test
    fun step14ContentIsConsistent() {
        val ids = BoxSurpriseCatalog.ALL_SURPRISES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue("Для 4 коробок нужно минимум 4 разных сюрприза", ids.size >= 4)
        assertEquals(6, SorterShapeKind.values().size)
    }
}
