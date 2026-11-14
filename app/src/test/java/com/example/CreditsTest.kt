package com.example

import com.example.shared.CREDITS
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditsTest {
    @Test
    fun attributionLicensesAreListed() {
        val all = CREDITS.joinToString("\n") { "${it.what} ${it.author} ${it.license}" }
        assertTrue("CC BY Block Puzzle Kit", all.contains("heyheythere") && all.contains("CC BY 4.0"))
        assertTrue("CC BY-SA утка", all.contains("Secretlondon") && all.contains("CC BY-SA"))
        assertTrue("three.js", all.contains("three.js"))
    }

    @Test
    fun everyEntryIsFilled() {
        CREDITS.forEach {
            assertTrue(it.what.isNotBlank() && it.author.isNotBlank() && it.license.isNotBlank())
        }
    }
}
