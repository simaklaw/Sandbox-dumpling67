package com.example.shared

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * «Свои голоса»: родитель записывает фразы сам, и они играют вместо заглушек из res/raw.
 *
 * Правила (чтобы потом ничего не переделывать):
 *  - ключ записи = имя файла озвучки без `_en` (`praise_1`, `voice_name_cow`, `phone_hi_dog`);
 *  - запись лежит в `filesDir/voices/<ключ>.m4a` (AAC, mono) и перекрывает res/raw для обоих языков;
 *  - нет файла — играет встроенный звук; удалить запись = вернуть встроенный;
 *  - запись идёт во временный файл и заменяет прежнюю только при успехе;
 *  - разрешения INTERNET нет; нужен только RECORD_AUDIO, и он запрашивается в момент записи.
 */
object CustomVoices {
    private const val TAG = "CustomVoices"
    const val MAX_MS = 4000
    private const val MIN_BYTES = 1500L

    class Phrase(val key: String, val text: String)
    class Group(val title: String, val phrases: List<Phrase>)

    /** Тексты — как в VOICES.md. Порядок: сначала то, что слышно чаще всего. */
    val groups: List<Group> = listOf(
        Group("Похвала и игры", listOf(
            Phrase("praise_1", "Молодец!"), Phrase("praise_2", "Умничка!"), Phrase("praise_3", "Ура!"),
            Phrase("peekaboo_1", "Ку-ку!"), Phrase("peekaboo_2", "Ку-ку! А вот и я!"), Phrase("yum_voice", "Ням-ням!"),
            Phrase("voice_hint_tap", "Нажми!"), Phrase("voice_hint_drag", "Потяни сюда!"), Phrase("voice_hint_look", "Смотри!"),
            Phrase("voice_arrow_normal", "Стрела!"), Phrase("voice_arrow_carrot", "Морковка!"), Phrase("voice_arrow_star", "Звёздочка!")
        )),
        Group("Названия животных", listOf(
            Phrase("voice_name_cow", "Коровка"), Phrase("voice_name_dog", "Собачка"), Phrase("voice_name_cat", "Кошечка"),
            Phrase("voice_name_pig", "Свинка"), Phrase("voice_name_sheep", "Барашек"), Phrase("voice_name_chick", "Цыплёнок"),
            Phrase("voice_name_duck", "Утка"), Phrase("voice_name_frog", "Лягушка"), Phrase("voice_name_horse", "Лошадка"),
            Phrase("voice_name_piglet", "Поросёнок"), Phrase("voice_name_bear", "Мишка"), Phrase("voice_name_elephant", "Слон"),
            Phrase("voice_name_dino", "Динозавр"), Phrase("voice_name_robot", "Робот")
        )),
        Group("Фигурки", listOf(
            Phrase("voice_shape_circle", "Круг"), Phrase("voice_shape_square", "Квадрат"), Phrase("voice_shape_triangle", "Треугольник"),
            Phrase("voice_shape_star", "Звезда"), Phrase("voice_shape_heart", "Сердце"), Phrase("voice_shape_oval", "Овал")
        )),
        Group("Друзья по телефону", listOf(
            Phrase("phone_hi_dog", "Алё-алё! Гав-гав! Давай играть!"), Phrase("phone_hi_cat", "Алё-алё! Мяу-мяу! Привет, малыш!"),
            Phrase("phone_hi_duck", "Алё-алё! Кря-кря! Пойдём на пруд!"), Phrase("phone_hi_cow", "Алё-алё! Му-у-у! Вкусное молочко!"),
            Phrase("phone_hi_frog", "Алё-алё! Ква-ква! Прыг-скок!"), Phrase("phone_hi_pig", "Алё-алё! Хрю-хрю! Я люблю лужи!"),
            Phrase("phone_hi_horse", "Алё-алё! Иго-го! Давай скакать!"), Phrase("phone_hi_sheep", "Алё-алё! Бе-е-е! Я пушистый!"),
            Phrase("phone_hi_chick", "Алё-алё! Пи-пи-пи! Я маленький!"), Phrase("phone_hi_rooster", "Алё-алё! Ку-ка-ре-ку! Пора вставать!"),
            Phrase("phone_hi_bunny", "Алё-алё! Прыг-скок! Хочу морковку!")
        ))
    )

    /**
     * «Главные» фразы: то, что малыш слышит чаще всего (похвала, ку-ку, ням, подсказки). Родителю
     * достаточно записать их (8 штук, около двух минут), остальное можно не трогать.
     */
    val essentialKeys: List<String> = listOf(
        "praise_1", "praise_2", "praise_3", "peekaboo_1", "peekaboo_2", "yum_voice", "voice_hint_tap", "voice_hint_drag"
    )

    val allPhrases: List<Phrase> get() = groups.flatMap { it.phrases }

    fun textOf(key: String): String = allPhrases.firstOrNull { it.key == key }?.text ?: key

    fun dir(context: Context): File = File(context.filesDir, "voices")

    fun file(context: Context, key: String): File = File(dir(context), "$key.m4a")

    fun has(context: Context, key: String): Boolean = file(context, key).isFile

    fun delete(context: Context, key: String) {
        try {
            file(context, key).delete()
        } catch (e: Exception) {
            Log.w(TAG, "delete $key failed", e)
        }
    }

    /** Один рекордер на экран; пишет в `<ключ>.tmp`, а в `<ключ>.m4a` переносит только годную запись. */
    class Recorder(private val context: Context) {
        private var rec: MediaRecorder? = null
        private var key: String? = null
        private var onAutoStop: (() -> Unit)? = null

        val isRecording: Boolean get() = rec != null

        private fun tmp(k: String) = File(dir(context), "$k.tmp")

        /** @return false, если записать не удалось (нет микрофона, занят и т. п.). */
        fun start(k: String, autoStop: () -> Unit): Boolean {
            stopQuietly()
            return try {
                dir(context).mkdirs()
                @Suppress("DEPRECATION")
                val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
                r.setAudioSource(MediaRecorder.AudioSource.MIC)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioChannels(1)
                r.setAudioSamplingRate(44100)
                r.setAudioEncodingBitRate(96000)
                r.setMaxDuration(MAX_MS)
                r.setOutputFile(tmp(k).absolutePath)
                r.setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onAutoStop?.invoke()
                }
                r.prepare()
                r.start()
                rec = r
                key = k
                onAutoStop = autoStop
                true
            } catch (e: Exception) {
                Log.w(TAG, "start $k failed", e)
                stopQuietly()
                false
            }
        }

        /** Останавливает запись. @return ключ сохранённой фразы или null, если запись слишком короткая/сбойная. */
        fun finish(): String? {
            val r = rec ?: return null
            val k = key ?: return null
            rec = null
            key = null
            onAutoStop = null
            val ok = try {
                r.stop()
                true
            } catch (e: Exception) { // stop() бросает, если записали меньше ~100 мс
                Log.w(TAG, "stop $k failed", e)
                false
            } finally {
                try { r.release() } catch (_: Exception) { Log.w(TAG, "release failed") }
            }
            val t = tmp(k)
            if (!ok || !t.isFile || t.length() < MIN_BYTES) {
                t.delete()
                return null
            }
            val dst = file(context, k)
            dst.delete()
            return if (t.renameTo(dst)) k else { t.delete(); null }
        }

        /** Отмена без сохранения (выход с экрана во время записи). */
        fun stopQuietly() {
            val r = rec
            val k = key
            rec = null
            key = null
            onAutoStop = null
            if (r != null) {
                try { r.stop() } catch (_: Exception) { Log.d(TAG, "stop on cancel") }
                try { r.release() } catch (_: Exception) { Log.d(TAG, "release on cancel") }
            }
            if (k != null) tmp(k).delete()
        }
    }
}
