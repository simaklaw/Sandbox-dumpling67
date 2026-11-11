package com.example.screens.Busyboard

import android.os.SystemClock
import android.util.Log
import com.example.shared.SoundManager

/**
 * Звуки «Бизиборда». Все — короткие одноразовые OGG из generate_busyboard_sounds.py (res/raw/bb2_*.ogg).
 *
 * Почему теперь звук не «тянется» после остановки:
 *  • ни одной петли и ни одного синтеза «на лету» — каждый звук короче 2,2 с, а «непрерывные» детали
 *    (молния, шестерёнки, колесо) звучат серией крошечных щелчков по одному на шаг движения;
 *  • [BbGate] не пускает частые звуки чаще, чем позволяет пауза, и ограничивает всплеск звуков за 120 мс;
 *  • каждый запущенный поток запоминается, а [silence] обрывает их все — при уходе из комнаты,
 *    сворачивании приложения и закрытии экрана ничего не доигрывает.
 */
internal enum class BbSound(val file: String, val volume: Float, val gapMs: Long) {
    CLICK("bb2_click", 0.9f, 45L),
    CLACK("bb2_clack", 0.95f, 90L),
    TICK("bb2_tick", 0.8f, 26L),
    ZIP("bb2_zip", 0.8f, 20L),
    BEAD("bb2_bead", 0.9f, 40L),
    KEYTURN("bb2_keyturn", 0.95f, 180L),
    THUNK("bb2_thunk", 0.95f, 140L),
    DING("bb2_ding", 0.85f, 250L),
    FANFARE("bb2_fanfare", 0.9f, 1500L),
    WHOOSH("bb2_whoosh", 0.7f, 200L),
    POP("bb2_pop", 0.85f, 60L),
    SPARK("bb2_spark", 0.6f, 140L),
    ZAP("bb2_zap", 0.8f, 600L),
    RELIEF("bb2_relief", 0.9f, 600L),
    LAMP("bb2_lamp", 0.85f, 70L)
}

internal class BbAudio(private val sm: SoundManager) {
    private val soundCount = BbSound.values().size
    private val xyloBars = 8

    private val gate = BbGate(
        LongArray(soundCount + xyloBars) { i -> if (i < soundCount) BbSound.values()[i].gapMs else 30L }
    )

    // кольцо последних потоков SoundPool: по нему silence() обрывает всё, что ещё звучит
    private val streams = IntArray(16)
    private var head = 0

    fun preload() {
        try {
            sm.preloadRaw(BbSound.values().map { it.file } + (1..xyloBars).map { "bb2_xylo_$it" })
        } catch (e: Exception) {
            Log.w(TAG, "preload failed", e)
        }
    }

    fun play(s: BbSound, rate: Float = 1f, volume: Float = 1f): Boolean =
        playFile(s.ordinal, s.file, s.volume * volume, rate)

    /** Пластинка ксилофона 0..7 (до-мажор C5…C6), высота не меняется. */
    fun xylo(bar: Int, volume: Float = 1f): Boolean {
        val k = bar.coerceIn(0, xyloBars - 1)
        return playFile(soundCount + k, "bb2_xylo_${k + 1}", 0.95f * volume, 1f)
    }

    /** Голос зверя (категория «звери» — выключается родителем вместе со всеми зверями). */
    fun animal(name: String) {
        try {
            sm.play("animal_$name")
        } catch (e: Exception) {
            Log.w(TAG, "animal sound failed", e)
        }
    }

    fun praise() {
        try {
            sm.play("praise")
        } catch (e: Exception) {
            Log.w(TAG, "praise failed", e)
        }
    }

    /** Обрывает все ещё звучащие звуки доски. Безопасно вызывать сколько угодно раз. */
    fun silence() {
        for (i in streams.indices) {
            val id = streams[i]
            if (id > 0) sm.stopStream(id)
            streams[i] = 0
        }
    }

    private fun playFile(slot: Int, file: String, volume: Float, rate: Float): Boolean {
        if (!gate.allow(slot, SystemClock.uptimeMillis())) return false
        val id = try {
            sm.playFxRawStream(file, volume, rate)
        } catch (e: Exception) {
            Log.w(TAG, "sound $file failed", e)
            0
        }
        if (id <= 0) return false
        streams[head] = id
        head = (head + 1) % streams.size
        return true
    }

    private companion object {
        const val TAG = "BusyboardAudio"
    }
}
