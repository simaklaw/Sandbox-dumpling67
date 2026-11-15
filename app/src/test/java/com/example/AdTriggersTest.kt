package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Структурные проверки: реклама только на безопасных точках и только через AdManager. */
class AdTriggersTest {
    private fun src(rel: String) = listOf("src/main/java/com/example/$rel", "app/src/main/java/com/example/$rel")
        .map(::File).first { it.exists() }.readText()

    @Test
    fun everyGameExitGoesThroughExitGame() {
        val main = src("MainActivity.kt")
        // parentStateForGame присваивается состоянию ровно в одном месте: внутри exitGame
        assertEquals(1, Regex("""activeScreenState = parentStateForGame\(""").findAll(main).count())
        assertTrue(main.contains("exitGame((activeScreenState as AppScreenState.Game).pageIndex)"))
        assertTrue(main.contains("exitGame(screenState.pageIndex)"))
    }

    @Test
    fun adsAreOnlyDecidedByAdManagerAtTwoTriggers() {
        val main = src("MainActivity.kt")
        assertEquals(2, Regex("""adManager\.decide\(""").findAll(main).count())
        assertTrue(main.contains("AdTrigger.EXIT") && main.contains("AdTrigger.LAUNCH"))
    }

    @Test
    fun noAdCodeInsideGameScreens() {
        val roots = listOf("src/main/java/com/example", "app/src/main/java/com/example").map(::File).first { it.exists() }
        val offenders = roots.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { it.path.contains("/screens/") || it.path.contains("/ui/screens/") }
            .filter { f -> f.readText().let { it.contains("com.example.ads") || it.contains("AdManager") } }
            .map { it.name }.toList()
        assertTrue("Реклама внутри игровых экранов запрещена: $offenders", offenders.isEmpty())
    }

    @Test
    fun adSdkIsNotInitializedAtStartup() {
        val main = src("MainActivity.kt")
        // в onCreate только счётчик запусков; провайдер/SDK не создаются
        assertFalse(main.substringAfter("override fun onCreate").substringBefore("setContent").contains("provider()"))
    }
}
