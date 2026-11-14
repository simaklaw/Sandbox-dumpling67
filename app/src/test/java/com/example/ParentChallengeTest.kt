package com.example

import com.example.shared.ParentChallenges
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentChallengeTest {
    @Test
    fun optionsAreFourDistinctAndContainExactlyOneAnswer() {
        val random = Random(42)
        repeat(2000) {
            val c = ParentChallenges.create(random)
            assertEquals("варианты: ${c.options}", 4, c.options.size)
            assertEquals("повторы: ${c.options}", 4, c.options.toSet().size)
            assertEquals(1, c.options.count { it == c.answer })
            assertTrue(c.options.all { it > 0 })
        }
    }

    @Test
    fun sumPromptMatchesAnswer() {
        val random = Random(7)
        var seen = 0
        repeat(500) {
            val c = ParentChallenges.create(random)
            val m = Regex("""(\d+) \+ (\d+) = \?""").matchEntire(c.prompt) ?: return@repeat
            seen++
            assertEquals(m.groupValues[1].toInt() + m.groupValues[2].toInt(), c.answer)
        }
        assertTrue("примеров на сложение не выпало", seen > 50)
    }

    @Test
    fun spelledPromptNamesTheAnswer() {
        val random = Random(9)
        var seen = 0
        repeat(500) {
            val c = ParentChallenges.create(random)
            if (!c.prompt.startsWith("Нажмите число: ")) return@repeat
            seen++
            assertEquals("Нажмите число: " + ParentChallenges.spell(c.answer), c.prompt)
        }
        assertTrue("задач словами не выпало", seen > 50)
    }

    @Test
    fun spellsRussianNumbers() {
        assertEquals("двадцать", ParentChallenges.spell(20))
        assertEquals("двадцать один", ParentChallenges.spell(21))
        assertEquals("тридцать четыре", ParentChallenges.spell(34))
        assertEquals("сорок", ParentChallenges.spell(40))
        assertEquals("шестьдесят семь", ParentChallenges.spell(67))
        assertEquals("девяносто девять", ParentChallenges.spell(99))
    }

    @Test
    fun challengesVaryBetweenCalls() {
        val random = Random(1)
        val prompts = (1..50).map { ParentChallenges.create(random).prompt }.toSet()
        assertTrue(prompts.size > 20)
    }
}
