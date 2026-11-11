package com.example.shared

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Свои голоса: родитель записывает фразы («Молодец!», названия зверей…), и они звучат вместо
 * встроенных заглушек. Файлы лежат во внутренней папке приложения (`filesDir/voices/<имя>.m4a`),
 * наружу и в сеть не уходят; Room не затронут.
 *
 * Имя записи совпадает с именем файла в `res/raw` (`praise_1`, `voice_name_cow`…), поэтому
 * подмена делается один раз в [SoundManager] на уровне загрузки звука, а экраны игр не меняются.
 * Одна запись заменяет и русский файл, и его `_en`-копию, если отдельной английской записи нет.
 */
object VoiceRecordings {

    class Phrase(val file: String, val text: String, val group: String)

    /** Фразы, которые действительно звучат в играх (список и тексты — как в VOICES.md). */
    val PHRASES: List<Phrase> = listOf(
        Phrase("praise_1", "Молодец!", "Похвала"),
        Phrase("praise_2", "Умничка!", "Похвала"),
        Phrase("praise_3", "Ура!", "Похвала"),
        Phrase("peekaboo_1", "Ку-ку!", "Похвала"),
        Phrase("peekaboo_2", "Ку-ку! А вот и я!", "Похвала"),
        Phrase("yum_voice", "Ням-ням!", "Похвала"),
        Phrase("voice_hint_tap", "Нажми!", "Подсказки"),
        Phrase("voice_hint_drag", "Потяни сюда!", "Подсказки"),
        Phrase("voice_hint_look", "Смотри!", "Подсказки"),
        Phrase("voice_arrow_normal", "Стрела!", "Лук"),
        Phrase("voice_arrow_carrot", "Морковка!", "Лук"),
        Phrase("voice_arrow_star", "Звёздочка!", "Лук"),
        Phrase("voice_shape_circle", "Круг", "Фигурки"),
        Phrase("voice_shape_square", "Квадрат", "Фигурки"),
        Phrase("voice_shape_triangle", "Треугольник", "Фигурки"),
        Phrase("voice_shape_star", "Звезда", "Фигурки"),
        Phrase("voice_shape_heart", "Сердце", "Фигурки"),
        Phrase("voice_shape_oval", "Овал", "Фигурки"),
        Phrase("voice_name_cow", "Коровка", "Зверята"),
        Phrase("voice_name_dog", "Собачка", "Зверята"),
        Phrase("voice_name_cat", "Кошечка", "Зверята"),
        Phrase("voice_name_pig", "Свинка", "Зверята"),
        Phrase("voice_name_sheep", "Барашек", "Зверята"),
        Phrase("voice_name_chick", "Цыплёнок", "Зверята"),
        Phrase("phone_hi_dog", "Алё-алё! Гав-гав! Давай играть!", "Телефон"),
        Phrase("phone_hi_cat", "Алё-алё! Мяу-мяу! Привет, малыш!", "Телефон"),
        Phrase("phone_hi_duck", "Алё-алё! Кря-кря! Пойдём на пруд!", "Телефон"),
        Phrase("phone_hi_cow", "Алё-алё! Му-у-у! Вкусное молочко!", "Телефон"),
        Phrase("phone_hi_frog", "Алё-алё! Ква-ква! Прыг-скок!", "Телефон"),
        Phrase("phone_hi_pig", "Алё-алё! Хрю-хрю! Я люблю лужи!", "Телефон"),
        Phrase("phone_hi_horse", "Алё-алё! Иго-го! Давай скакать!", "Телефон"),
        Phrase("phone_hi_sheep", "Алё-алё! Бе-е-е! Я пушистый!", "Телефон"),
        Phrase("phone_hi_chick", "Алё-алё! Пи-пи-пи! Я маленький!", "Телефон"),
        Phrase("phone_hi_rooster", "Алё-алё! Ку-ка-ре-ку! Пора вставать!", "Телефон"),
        Phrase("phone_hi_bunny", "Алё-алё! Прыг-скок! Хочу морковку!", "Телефон")
    )

    /** Записи короче этого — случайное касание, не сохраняем. */
    const val MIN_MS = 400L

    /** Дольше этого запись останавливается сама. */
    const val MAX_MS = 6000L

    private fun dir(context: Context): File = File(context.filesDir, "voices")

    fun file(context: Context, name: String): File = File(dir(context), "$name.m4a")

    fun has(context: Context, name: String): Boolean =
        file(context, name).let { it.isFile && it.length() > 0 }

    /**
     * Запись для файла звука [fileName] или null. Для `X_en` берётся запись `X`, если английской нет.
     */
    fun resolve(context: Context, fileName: String): File? {
        val own = file(context, fileName)
        if (own.isFile && own.length() > 0) return own
        if (fileName.endsWith("_en")) {
            val base = file(context, fileName.removeSuffix("_en"))
            if (base.isFile && base.length() > 0) return base
        }
        return null
    }

    fun delete(context: Context, name: String): Boolean = file(context, name).delete()

    fun count(context: Context): Int = PHRASES.count { has(context, it.file) }
}

/** Запись одной фразы с микрофона. Всё best-effort: сбой не должен ронять приложение. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var tmp: File? = null
    private var target: File? = null
    private var startMs = 0L

    val isRecording: Boolean get() = recorder != null

    fun start(name: String): Boolean {
        cancel()
        return try {
            val t = File(context.cacheDir, "voice_rec_tmp.m4a")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(96_000)
            r.setOutputFile(t.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            tmp = t
            target = VoiceRecordings.file(context, name)
            startMs = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.w("VoiceRecorder", "start failed", e)
            cancel()
            false
        }
    }

    /** Останавливает запись; true — запись сохранена (она не короче [VoiceRecordings.MIN_MS]). */
    fun stop(): Boolean {
        val r = recorder ?: return false
        recorder = null
        val long = System.currentTimeMillis() - startMs >= VoiceRecordings.MIN_MS
        val stopped = try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            Log.w("VoiceRecorder", "stop failed", e)
            false
        } finally {
            try { r.release() } catch (e: Exception) { Log.w("VoiceRecorder", "release failed", e) }
        }
        var saved = false
        val t = tmp
        val dst = target
        if (stopped && long && t != null && dst != null) {
            try {
                dst.parentFile?.mkdirs()
                t.copyTo(dst, overwrite = true)
                saved = true
            } catch (e: Exception) {
                Log.w("VoiceRecorder", "save failed", e)
            }
        }
        t?.delete()
        tmp = null
        target = null
        return saved
    }

    /** Отмена без сохранения (выход из диалога, новая запись). */
    fun cancel() {
        val r = recorder
        recorder = null
        if (r != null) {
            try { r.stop() } catch (e: RuntimeException) { Log.d("VoiceRecorder", "cancel: stop failed") }
            try { r.release() } catch (e: Exception) { Log.d("VoiceRecorder", "cancel: release failed") }
        }
        tmp?.delete()
        tmp = null
        target = null
    }
}
