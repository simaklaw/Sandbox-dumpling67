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
    fun everyFolderGameHasAScreenBranchInMainActivity() {
        // Реестр папок и `when (pageIndex)` в MainActivity — два списка; забытая ветка дала бы тихий пустой экран.
        val file = listOf("src/main/java/com/example/MainActivity.kt", "app/src/main/java/com/example/MainActivity.kt")
            .map { java.io.File(it) }.first { it.exists() }
        val branches = Regex("""(?m)^\s*(\d+)\s*->""").findAll(file.readText()).map { it.groupValues[1].toInt() }.toSet()
        val missing = FOLDER_ITEMS.values.flatten().map { it.pageIndex }.filter { it !in branches }
        assertTrue("Нет ветки в MainActivity для игр: $missing", missing.isEmpty())
    }

    @Test
    fun everyGameLivesInExactlyOneFolder() {
        val all = FOLDER_ITEMS.values.flatten().map { it.pageIndex }
        assertEquals("Игра не должна дублироваться в папках", all.size, all.toSet().size)
    }

    @Test
    fun allWorldsAreFilledAndEveryGameIsInAWorld() {
        // Шесть миров, в каждом хотя бы одна игра; в каталоге ровно 40 игр.
        assertEquals(6, FolderType.values().size)
        FolderType.values().forEach { assertTrue("Мир $it пуст", FOLDER_ITEMS.getValue(it).isNotEmpty()) }
        assertEquals(40, FOLDER_ITEMS.values.flatten().size)
        // игры, которые раньше висели на главном экране, теперь лежат в мирах
        assertEquals(FolderType.HOME, folderTypeForPage(1))      // Кормление
        assertEquals(FolderType.SHOOTING, folderTypeForPage(7))  // Шарики
        assertEquals(FolderType.CREATIVE, folderTypeForPage(9))  // Рисование
        assertEquals(FolderType.CALM, folderTypeForPage(21))     // Смешинки
        assertEquals(FolderType.CALM, folderTypeForPage(23))     // Телефончик
    }

    @Test
    fun backFromGameReturnsToItsWorld() {
        assertEquals(AppScreenState.Folder("Дом и кухня", FolderType.HOME), parentStateForGame(6))
        assertEquals(AppScreenState.Folder("Приключения", FolderType.ADVENTURE), parentStateForGame(52))
        assertEquals(AppScreenState.Folder("Антистресс", FolderType.CALM), parentStateForGame(10))
    }

    @Test
    fun gamesOutsideTheCatalogReturnHome() {
        // 11 («Сюрпризы») открывается только из родительских настроек и в каталоге не лежит.
        listOf(11, 3, 33).forEach {
            assertNull(folderTypeForPage(it))
            assertEquals(AppScreenState.Home, parentStateForGame(it))
        }
    }

    @Test
    fun retiredPageIndexesAreNotInFolders() {
        // 25 — «Кто в коробке?», 45 — «Гардероб героев», 33 — «Забег монстрика» (влит в «Монстрики и забег», 32): индексы не переиспользуем.
        val inFolders = FOLDER_ITEMS.values.flatten().map { it.pageIndex }.toSet()
        listOf(25, 33, 45).forEach { assertTrue("Индекс $it выведен из использования", it !in inFolders) }
    }

    @Test
    fun mineScreenStateSurvivesSaveAndRestore() {
        val raw = AppScreenStateSaver.run { androidx.compose.runtime.saveable.SaverScope { true }.save(AppScreenState.Mine) }
        assertEquals("M", raw)
        assertEquals(AppScreenState.Mine, AppScreenStateSaver.restore("M"))
        // состояние старой версии («F|GAMES|Игры») не роняет приложение
        assertEquals(AppScreenState.Home, AppScreenStateSaver.restore("F|GAMES|Игры"))
    }

    @Test
    fun step14ContentIsConsistent() {
        val ids = BoxSurpriseCatalog.ALL_SURPRISES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue("Для 4 коробок нужно минимум 4 разных сюрприза", ids.size >= 4)
        assertEquals(6, SorterShapeKind.values().size)
    }
}
