package com.example

import com.example.screens.introLetterTime
import com.example.shared.CustomVoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CustomVoicesTest {
    private val rawDir: File = listOf(File("src/main/res/raw"), File("app/src/main/res/raw"))
        .firstOrNull { it.isDirectory } ?: error("res/raw не найден")

    @Test
    fun essentialKeysAreRealPhrasesWithBuiltInSound() {
        val all = CustomVoices.allPhrases.map { it.key }.toSet()
        assertEquals("главных фраз должно быть 8", 8, CustomVoices.essentialKeys.size)
        for (k in CustomVoices.essentialKeys) {
            assertTrue("«$k» нет в списке фраз", k in all)
            assertTrue("нет встроенного звука $k.ogg", File(rawDir, "$k.ogg").isFile)
        }
    }

    @Test
    fun phraseKeysAreUnique() {
        val keys = CustomVoices.allPhrases.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun introLettersAppearInOrder() {
        for (i in 1 until 15) assertTrue(introLetterTime(i) > introLetterTime(i - 1))
        assertTrue("все буквы должны появиться до конца заставки (4,6 с)", introLetterTime(14) + 0.4f < 4.6f)
    }

    @Test
    fun replacedSoundsStayShort() {
        // Частые звуки (клик, пинбол, чпоки) не длиннее 0,6 с — правило CLAUDE.md
        for (n in listOf("tap", "wall", "flip", "bumper", "spawn", "pickup", "snap_in", "thud", "poke")) {
            val f = File(rawDir, "$n.ogg")
            assertTrue("$n.ogg пропал", f.isFile)
            assertTrue("$n.ogg слишком большой для короткого звука: ${f.length()}", f.length() < 20_000)
        }
    }
}
