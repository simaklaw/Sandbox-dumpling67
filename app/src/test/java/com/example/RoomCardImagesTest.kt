package com.example

import com.example.screens.FOLDER_SECTIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** У каждой карточки комнаты есть картинка (если новой комнате её ещё нет, добавить номер в [withoutImage]). */
class RoomCardImagesTest {

    private val withoutImage = emptySet<Int>()

    @Test
    fun everyRoomHasImageExceptKnownGaps() {
        val items = FOLDER_SECTIONS.values.flatten().flatMap { it.items }
        assertTrue(items.isNotEmpty())
        val missing = items.filter { it.iconRes == null }.map { it.pageIndex }.toSet()
        assertEquals(withoutImage, missing)
    }
}
