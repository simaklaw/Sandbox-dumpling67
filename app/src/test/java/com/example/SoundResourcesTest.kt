package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Сторожа для звуковых ресурсов (res/raw). Это обычные JVM-тесты: Vorbis они не декодируют,
 * длительность берётся из контейнера OGG (частота — из заголовка Vorbis, число отсчётов — из
 * последней страницы).
 *
 * Зачем: «вжухи» на переходах когда-то были белым шумом по 0,35 с, жевание мишки — четырьмя
 * острыми щелчками, а шесть звуков лопания шариков — шестью разными по характеру файлами.
 * Спектр здесь не проверить, но длина и состав набора ловят самые частые откаты
 * (подмена файла «длинной» записью, потеря ноты цвета, потеря англоязычной копии озвучки).
 */
class SoundResourcesTest {

    private val rawDir: File = listOf(File("src/main/res/raw"), File("app/src/main/res/raw"))
        .firstOrNull { it.isDirectory }
        ?: error("res/raw не найден (рабочая папка теста: ${File(".").absolutePath})")

    private fun ogg(name: String): File {
        val f = File(rawDir, "$name.ogg")
        if (!f.isFile) fail("Нет файла res/raw/$name.ogg")
        return f
    }

    private fun le32(b: ByteArray, at: Int): Long =
        (0 until 4).fold(0L) { acc, i -> acc or ((b[at + i].toLong() and 0xff) shl (8 * i)) }

    private fun le64(b: ByteArray, at: Int): Long =
        (0 until 8).fold(0L) { acc, i -> acc or ((b[at + i].toLong() and 0xff) shl (8 * i)) }

    /** Длительность OGG Vorbis в секундах. */
    private fun durationSec(name: String): Double {
        val b = ogg(name).readBytes()
        // Заголовок идентификации: 0x01 "vorbis", версия(4), каналы(1), частота(4, little-endian).
        val magic = byteArrayOf(1, 'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 'b'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte())
        var idx = -1
        search@ for (i in 0..b.size - magic.size) {
            for (k in magic.indices) if (b[i + k] != magic[k]) continue@search
            idx = i
            break
        }
        require(idx >= 0) { "$name: нет заголовка Vorbis" }
        val sampleRate = le32(b, idx + 12)
        require(sampleRate in 8_000..96_000) { "$name: странная частота $sampleRate" }

        // Идём по страницам: в granule position последней страницы — число отсчётов всего файла.
        var pos = 0
        var granule = 0L
        while (pos + 27 <= b.size && b[pos] == 'O'.code.toByte() && b[pos + 1] == 'g'.code.toByte()) {
            granule = le64(b, pos + 6)
            val segments = b[pos + 26].toInt() and 0xff
            var payload = 0
            for (s in 0 until segments) payload += b[pos + 27 + s].toInt() and 0xff
            pos += 27 + segments + payload
        }
        return granule.toDouble() / sampleRate
    }

    @Test
    fun transitionSoundsAreShortAndExist() {
        // Эти файлы играют на каждом переходе меню ↔ игра и на каждом листании.
        // Длинный «вжух» быстро надоедает и маскирует озвучку.
        val limits = mapOf("whoosh_soft" to 0.5, "whoosh" to 0.6, "fx_zone_transition" to 0.9)
        for ((name, max) in limits) {
            val d = durationSec(name)
            assertTrue("$name: ${"%.2f".format(d)} с, ожидалось от 0.1 до $max", d in 0.1..max)
        }
    }

    @Test
    fun balloonPopsAreOneFamilyOfSevenNotes() {
        // По ноте на каждый из 7 цветов шариков (BALLOON_COLORS в BalloonPopScreen).
        val durations = (1..7).map { durationSec("bp_pop$it") }
        durations.forEachIndexed { i, d ->
            assertTrue("bp_pop${i + 1}: ${"%.2f".format(d)} с, «поп» не должен тянуться дольше 0.5 с", d in 0.1..0.5)
        }
        val spread = durations.max() - durations.min()
        assertTrue("Звуки лопания должны быть одной длины (разброс ${"%.2f".format(spread)} с)", spread <= 0.1)
        assertTrue("Лишний bp_pop8: нота без цвета", !File(rawDir, "bp_pop8.ogg").exists())
    }

    @Test
    fun munchIsShortAndSoft() {
        for (name in listOf("kc_munch1", "kc_munch2")) {
            val d = durationSec(name)
            assertTrue("$name: ${"%.2f".format(d)} с, «ням» должно быть коротким", d in 0.1..0.45)
        }
    }

    @Test
    fun everySpokenFileHasEnglishTwin() {
        // Озвучка и приветствия друзей: язык переключается суффиксом _en (SoundManager.play).
        val spoken = rawDir.listFiles { f ->
            f.extension == "ogg" && !f.nameWithoutExtension.endsWith("_en") &&
                Regex("""voice_(hint|arrow|name|shape)_.+|praise_\d|peekaboo_\d|yum_voice|phone_hi_.+""")
                    .matches(f.nameWithoutExtension)
        }.orEmpty().map { it.nameWithoutExtension }.sorted()

        assertTrue("Не нашлось ни одного говорящего файла: изменилось имя шаблона?", spoken.size >= 40)
        val missing = spoken.filter { !File(rawDir, "${it}_en.ogg").isFile }
        assertEquals("Нет _en-копии у: $missing", emptyList<String>(), missing)
    }

    @Test
    fun customVoiceListMatchesBuiltInSounds() {
        // Каждая фраза экрана «Свои голоса» должна иметь встроенный звук под тем же именем:
        // удаление записи возвращает его, а перекрывает запись именно его ключ.
        val keys = com.example.shared.CustomVoices.groups.flatMap { g -> g.phrases.map { it.key } }
        assertEquals("Ключи фраз должны быть уникальны", keys.size, keys.toSet().size)
        val missing = keys.filter { !File(rawDir, "$it.ogg").isFile }
        assertEquals("Нет встроенного звука для: $missing", emptyList<String>(), missing)
    }
}
