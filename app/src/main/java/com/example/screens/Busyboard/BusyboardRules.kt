package com.example.screens.Busyboard

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sign

/*
 * Чистая логика «Бизиборда» (комната 10): без Compose, без Android — её проверяют юнит-тесты.
 * Рисование и жесты живут в BusyboardElements*.kt, звук — в BusyboardAudio.kt.
 */

internal const val BB_ROOMS = 3

/** Девять деталей доски: за каждую — одна звёздочка. Порядок объявления = номер бита в маске звёзд. */
internal enum class BbElement(val room: Int) {
    LATCH(0), SWITCH(0), ZIPPER(0), KEYLOCK(0),
    GEARS(1), ABACUS(1), XYLOPHONE(1), WHEEL(1),
    PANEL(2)
}

internal val BB_STARS: Int = BbElement.values().size

private val BB_ALL_MASK: Int = (1 shl BbElement.values().size) - 1

// ─────────────────────── звёздочки (битовая маска — хорошо переживает поворот экрана) ───────────────────────

internal fun bbHas(mask: Int, e: BbElement): Boolean = mask and (1 shl e.ordinal) != 0

internal fun bbAward(mask: Int, e: BbElement): Int = mask or (1 shl e.ordinal)

internal fun bbCount(mask: Int): Int = Integer.bitCount(mask and BB_ALL_MASK)

internal fun bbComplete(mask: Int): Boolean = bbCount(mask) == BB_STARS

internal fun bbRoomSize(room: Int): Int = BbElement.values().count { it.room == room }

internal fun bbRoomCount(mask: Int, room: Int): Int = BbElement.values().count { it.room == room && bbHas(mask, it) }

/** «Настроение комнаты»: 0 — все звёзды впереди (приглушённый свет), 1 — всё собрано (тёплое сияние). */
internal fun bbMood(mask: Int): Float = bbCount(mask).toFloat() / BB_STARS

/** Какую деталь подсветить подсказкой: первая ещё не заработавшая в этой комнате. */
internal fun bbNextHint(mask: Int, room: Int): BbElement? =
    BbElement.values().firstOrNull { it.room == room && !bbHas(mask, it) }

/** Колесо: сколько вращений нужно для звёздочки (малышу достаточно двух). */
internal fun bbSpinsNeeded(toddler: Boolean): Int = if (toddler) 2 else 3

/** Ксилофон: звёздочка за пять РАЗНЫХ пластинок (маска сыгранных). */
internal fun bbXyloDone(playedMask: Int): Boolean = Integer.bitCount(playedMask and 0xFF) >= 5

// ─────────────────────── ограничитель звука ───────────────────────

/**
 * Не даёт звукам «строчить»: у каждого звука своя минимальная пауза, а всего за короткое окно
 * играется не больше [globalLimit] звуков. Именно очередь частых звуков была причиной «звуков после остановки».
 * Время передаётся снаружи (мс), поэтому класс проверяется без Android.
 */
internal class BbGate(
    private val gaps: LongArray,
    private val globalLimit: Int = 6,
    private val globalWindowMs: Long = 120L
) {
    private val never = Long.MIN_VALUE / 2
    private val last = LongArray(gaps.size) { never }
    private val recent = LongArray(globalLimit) { never }
    private var head = 0

    fun allow(slot: Int, now: Long): Boolean {
        if (slot !in gaps.indices) return false
        if (now - last[slot] < gaps[slot]) return false
        if (now - recent[head] < globalWindowMs) return false
        last[slot] = now
        recent[head] = now
        head = (head + 1) % globalLimit
        return true
    }
}

// ─────────────────────── движение ───────────────────────

/** Пружинка: деталь «досаживается» к цели с лёгким перелётом. */
internal class BbSpring(
    var x: Float = 0f,
    var target: Float = 0f,
    private val stiffness: Float = 240f,
    private val damping: Float = 20f
) {
    var v = 0f

    fun step(dt: Float) {
        var left = dt.coerceIn(0f, 0.05f)
        while (left > 0f) {
            val h = min(left, 0.008f)
            v += ((target - x) * stiffness - v * damping) * h
            x += v * h
            left -= h
        }
    }

    fun snap(to: Float) {
        x = to
        target = to
        v = 0f
    }

    val settled: Boolean get() = abs(target - x) < 0.002f && abs(v) < 0.02f
}

/** Сколько отметок шага [step] пересекла величина при движении a → b. Нужна для щелчков «по одному на шаг». */
internal fun bbCrossings(a: Float, b: Float, step: Float): Int {
    if (step <= 0f) return 0
    return abs(floor(b / step).toInt() - floor(a / step).toInt())
}

// ─────────────────────── шестерёнки ───────────────────────

/**
 * Зубчатая передача: ведущая шестерня 0 крутится пальцем, остальные в зацеплении и вращаются в обратную
 * сторону с передаточным числом teeth[0] / teeth[i]. «Храповик»: каждый зубец ведущей даёт один щелчок.
 */
internal class BbGearTrain(private val teeth: IntArray) {
    var angle = 0f
        private set
    var omega = 0f
    var turned = 0f
        private set

    fun teethOf(i: Int): Int = teeth[i]

    /** Угол i-й шестерни, градусы. Ведомые сдвинуты на ползубца — зубья входят во впадины. */
    fun angleOf(i: Int): Float = if (i == 0) angle else -angle * teeth[0] / teeth[i] + 180f / teeth[i]

    /** Поворот ведущей на [deltaDeg]; возвращает число щелчков (зубцов, прошедших мимо «собачки»). */
    fun advance(deltaDeg: Float): Int {
        val pitch = 360f / teeth[0]
        val before = angle
        angle += deltaDeg
        turned += abs(deltaDeg)
        return bbCrossings(before, angle, pitch)
    }

    /** Инерция после отпускания; возвращает число щелчков за кадр. */
    fun coast(dt: Float): Int {
        if (omega == 0f) return 0
        val a = abs(omega)
        val na = a - (a * 1.6f + 30f) * dt
        val clicks = advance(omega * dt)
        omega = if (na <= 8f) 0f else sign(omega) * na
        return clicks
    }

    fun progress(turnsGoal: Float): Float = (turned / (360f * turnsGoal)).coerceIn(0f, 1f)
}

// ─────────────────────── колесо ───────────────────────

/** Затухание колеса: вязкое трение + «сухое», чтобы оно честно остановилось, а не ползло вечно. */
internal fun bbWheelDecay(omega: Float, dt: Float): Float {
    val a = abs(omega)
    val na = a - (a * 0.9f + 40f) * dt
    return if (na <= 6f) 0f else sign(omega) * na
}

/** Сектор под верхней стрелкой при повороте колеса на [rotDeg] градусов по часовой. */
internal fun bbWheelIndex(rotDeg: Float, sectors: Int): Int {
    val step = 360f / sectors
    val a = (((-rotDeg) % 360f) + 360f) % 360f
    return (a / step).toInt().coerceIn(0, sectors - 1)
}

// ─────────────────────── счёты ───────────────────────

/**
 * Двигает косточку [i] к [target] (0..1 слева направо), расталкивая соседей; [gap] — минимальный шаг между ними.
 * Возвращает число сдвинутых соседей — по нему играется «клак».
 */
internal fun bbAbacusDrag(pos: FloatArray, i: Int, target: Float, gap: Float): Int {
    val n = pos.size
    val lo = i * gap
    val hi = 1f - (n - 1 - i) * gap
    pos[i] = target.coerceIn(lo, hi)
    var pushed = 0
    for (j in i + 1 until n) {
        val need = pos[j - 1] + gap
        if (pos[j] < need) {
            pos[j] = need
            pushed++
        } else break
    }
    for (j in i - 1 downTo 0) {
        val need = pos[j + 1] - gap
        if (pos[j] > need) {
            pos[j] = need
            pushed++
        } else break
    }
    return pushed
}

/** Ряд собран справа: все косточки прижаты к правому краю. */
internal fun bbAbacusRowRight(pos: FloatArray, gap: Float, eps: Float = 0.02f): Boolean {
    val n = pos.size
    for (j in 0 until n) if (pos[j] < 1f - (n - 1 - j) * gap - eps) return false
    return true
}

// ─────────────────────── электрощиток ───────────────────────

internal enum class BbPanelEvent { LAMP_ON, LAMP_OFF, OVERLOAD, AUTO_TRIP, RESTORED }

internal data class BbPanelNote(val event: BbPanelEvent, val lamp: Int = -1)

/**
 * Электрощиток: рубильник + три автомата, у каждого своя лампа.
 *  • рубильник вниз — ток пошёл, лампы включённых автоматов загораются;
 *  • когда горят ВСЕ три лампы разом — перегрузка («короткое замыкание»): лампы сходят с ума, щиток искрит;
 *  • навести порядок можно рубильником вверх или любым автоматом — тогда «Фух!» и звёздочка электрика;
 *  • если ребёнок не вмешался, автомат сам «выбивает» через [autoTripSec] — хаос никогда не длится вечно.
 */
internal class BbPanel(val lamps: Int = 3, private val autoTripSec: Float = 7f) {
    var power = false
        private set
    val breakers = BooleanArray(lamps)
    var overloadSec = 0f
        private set

    val lit: Int get() = if (power) breakers.count { it } else 0
    val overloaded: Boolean get() = power && lit >= lamps

    fun lampOn(i: Int): Boolean = power && breakers[i]

    fun setPower(on: Boolean): List<BbPanelNote> {
        if (on == power) return emptyList()
        val was = overloaded
        power = on
        val out = ArrayList<BbPanelNote>()
        for (i in 0 until lamps) if (breakers[i]) out += BbPanelNote(if (on) BbPanelEvent.LAMP_ON else BbPanelEvent.LAMP_OFF, i)
        overloadSec = 0f
        if (on && overloaded) out += BbPanelNote(BbPanelEvent.OVERLOAD)
        if (!on && was) out += BbPanelNote(BbPanelEvent.RESTORED)
        return out
    }

    fun toggleBreaker(i: Int): List<BbPanelNote> {
        if (i !in 0 until lamps) return emptyList()
        val was = overloaded
        breakers[i] = !breakers[i]
        val out = ArrayList<BbPanelNote>()
        if (power) out += BbPanelNote(if (breakers[i]) BbPanelEvent.LAMP_ON else BbPanelEvent.LAMP_OFF, i)
        if (!was && overloaded) {
            overloadSec = 0f
            out += BbPanelNote(BbPanelEvent.OVERLOAD)
        } else if (was && !overloaded) {
            overloadSec = 0f
            out += BbPanelNote(BbPanelEvent.RESTORED)
        }
        return out
    }

    /** Время идёт: при перегрузке автомат сам выбивает рубильник. */
    fun tick(dt: Float): List<BbPanelNote> {
        if (!overloaded) return emptyList()
        overloadSec += dt
        if (overloadSec < autoTripSec) return emptyList()
        power = false
        overloadSec = 0f
        return listOf(BbPanelNote(BbPanelEvent.AUTO_TRIP), BbPanelNote(BbPanelEvent.RESTORED))
    }
}
