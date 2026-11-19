package com.example

import com.example.screens.FOLDER_SECTIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** У каждой карточки комнаты есть картинка, кроме тех, чьих картинок ещё нет (там остаётся эмодзи). */
class RoomCardImagesTest {

    /** Гипертрасса (52), Фигурки-окошки (26), Бизиборд (10): картинки появятся позже. */
    private val withoutImage = setOf(52, 26, 10)

    @Test
    fun everyRoomHasImageExceptKnownGaps() {
        val items = FOLDER_SECTIONS.values.flatten().flatMap { it.items }
        assertTrue(items.isNotEmpty())
        val missing = items.filter { it.iconRes == null }.map { it.pageIndex }.toSet()
        assertEquals(withoutImage, missing)
    }
}
