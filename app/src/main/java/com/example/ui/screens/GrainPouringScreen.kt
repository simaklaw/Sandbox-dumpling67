package com.example.ui.screens

/*
 * «Сыпучести» — сенсорная игра для малышей: пересыпаем крупу и песок
 * между банками и играемся с ней, как в настоящей песочнице.
 *
 * Что нового в v5 (идеи из книг Шелла «Геймдизайн», «Хочу в геймдев!»,
 * Тидвелл «Разработка интерфейсов» и «UE5 Shaders Cookbook»):
 *  • «Сочность» (juice): банка упруго пружинит при захвате (squash-stretch),
 *    кучка на столе вздрагивает под струёй, при старте пересыпания —
 *    микро-встряска. Ни одно действие не остаётся без мгновенного отклика.
 *  • Пасхалки любопытства: редкое ЗОЛОТОЕ зёрнышко в струе (звенит искрами
 *    при приземлении), иногда в струе живёт РАДУГА, бабочку (днём) и
 *    светлячка (ночью) можно «поймать» пальцем — они разлетаются искрами.
 *  • Разнообразие наград: конфетти трёх форм (кружки, звёзды, сердечки),
 *    каждая восьмая награда — редкая «золотая»: больше, дольше, звонче.
 *  • «Всё красивое отвечает» (аффорданс): солнышко пульсирует, месяц
 *    подпрыгивает, облако покачивается, кучку крупы можно «взбодрить»
 *    тапом — зёрнышки подпрыгивают. Ничего нельзя сломать (Safe Exploration).
 *  • «Свет» по рецептам UE5: звёзды и светлячки с мягкими ореолами,
 *    тонкие края кучки светятся изнутри (subsurface-подсветка).
 *
 * Что нового в v4 (по мотивам «Мобилизации» В. Файнштейна):
 *  • ТРИ банки: слева полная, в центре и справа — пустые. Поднятую банку
 *    можно тянуть куда угодно и сыпать в любую — выбор маршрута остаётся
 *    за ребёнком. Средняя банка сама разворачивается по направлению
 *    переноса (потянул влево — льёт влево). Если банка уже полная,
 *    зёрна красиво скатываются с её горки наружу — на стол.
 *  • Режим «День / Ночь» (пример из книги: разный контент в разное
 *    время суток). После 19:00 игра сама включается в «успокаивающий»
 *    вечерний режим: тёмное небо со звёздами и месяцем, светлячки вместо
 *    бабочек, сонные полуприкрытые глазки у банок, крупа льётся
 *    медленнее. Кнопка ☀️/🌙 внизу слева переключает вручную.
 *  • Аналитика: каждое действие ребёнка проходит через ОДИН callback
 *    world.onEvent — готовая точка для подключения repository.logEvent()
 *    (см. комментарий в GrainPouringScreen).
 *  • Исправлен баг v3: правая банка не пересыпала — её угол наклона был
 *    отрицательным и поток считался нулевым. Теперь используется |угол|.
 *  • Убран жест «разметать кучку»: он слабо считывается детьми и
 *    конкурировал с главным действием («не бойся убивать фичи»).
 *
 * Что умеет (унаследовано из v3):
 *  • Банки — живые персонажи: глаза следят за струёй и пальцем, моргают,
 *    радуются на вехах наполнения, «едят» крупу открытым ртом,
 *    пустая банка спит с летающими «Z-z-z».
 *  • Свободное переливание: поднятую банку можно ТЯНУТЬ пальцем —
 *    горизонтально летает над столом, вертикально меняет наклон.
 *    Направил струю мимо банки — крупа сыплется на стол.
 *  • Кучки на столе: рассыпанная крупа образует настоящие горки
 *    с углом естественного откоса.
 *  • Вехи наполнения 25/50/75% — банка радуется мини-салютом;
 *    финиш — прыжок, конфетти, большая звезда.
 *  • Живой мир: солнышко с лучами, облака, бабочки, холмы под цвет крупы.
 *  • Физика: height-field рельеф (26 столбиков в банке, 64 на столе),
 *    угол откоса для каждой крупы, поток от наклона, пружинный наклон
 *    с overshoot, отскок крупной крупы.
 *
 * Сохранены внешний API GrainPouringScreen(repository, soundPlayer),
 * testTag("grain_pouring_root") и остановка лупа при выходе.
 *
 * Звуковые события: lift / lower / empty / pourStart / done
 *                    + milestone (веха) + nightOn / nightOff (переключение)
 *                    + treasure (золотое зёрнышко) / rainbow (радуга в струе)
 *                    + butterfly / firefly (поимка) + goldReward (золотая награда)
 *                    + sun / moon / cloud / heapPoke (мир отвечает на тап).
 */

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.LoopHandle
import com.example.shared.SoundManager
import com.example.shared.i18n.Text
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

// ============================================================================
// Крупы
// ============================================================================

/**
 * Описание крупы. Физика подобрана под реальные свойства сыпучих:
 * у песка крутой угол откоса и он льётся только при сильном наклоне,
 * у гороха откос пологий, зато зёрна крупные и прыгучие.
 * [scene] — цвет холмов на фоне (настроение «времени года» крупы).
 */
private class GrainType(
    val name: String,
    val emoji: String,
    val base: Color,
    val dark: Color,
    val light: Color,
    val scene: Color,
    val smallJar: Boolean,   // песок — банки поменьше
    val grainR: Float,       // радиус «зёрнышка», dp
    val reposeDeg: Float,    // угол естественного откоса, градусы
    val pourAtDeg: Float,    // наклон банки, при котором крупа начинает сыпаться
    val flowPerSec: Float,   // скорость пересыпания (доля банки в секунду при полном наклоне)
    val particlesPerJar: Int // сколько зёрен визуально «в одной банке»
)

private val GRAIN_TYPES = listOf(
    GrainType("Песок", "🏖️", Color(0xFFEACB86), Color(0xFFB98F3A), Color(0xFFF7E7B8), Color(0xFFE6C27A), true, 1.4f, 34f, 40f, 0.32f, 800),
    GrainType("Рис", "🍚", Color(0xFFFDF6E3), Color(0xFFCFC6A0), Color(0xFFFFFFFF), Color(0xFFAED581), false, 2.4f, 32f, 38f, 0.28f, 380),
    GrainType("Гречка", "🌰", Color(0xFF96603A), Color(0xFF59331B), Color(0xFFB07B4F), Color(0xFF9E7B52), false, 2.2f, 31f, 38f, 0.28f, 480),
    GrainType("Горох", "🟢", Color(0xFF93C960), Color(0xFF4E7F26), Color(0xFFC4E39A), Color(0xFFA5D6A7), false, 4.2f, 24f, 34f, 0.20f, 200),
    GrainType("Чечевица", "🔴", Color(0xFFE06A48), Color(0xFF9E3520), Color(0xFFF0937A), Color(0xFFEF9A73), false, 3.2f, 28f, 36f, 0.24f, 280)
)

// ============================================================================
// Вспомогательное
// ============================================================================

private const val COLS = 26           // столбиков рельефа крупы в банке
private const val TCOLS = 64          // столбиков рельефа крупы на столе
private const val JARS = 3            // сколько банк на столе
private const val MAX_TILT_DEG = 68f // наклон банки по умолчанию (одиночный тап)
private val DEG2RAD = (PI / 180.0).toFloat()
private val RAD2DEG = 57.29578f

/** Детерминированный «шум» для зёрнышек: одинаков каждый кадр, без мерцания. */
private fun hash1(n: Int): Float {
    val x = sin(n * 12.9898f) * 43758.5453f
    return x - floor(x)
}

/** Пятиконечная звезда (награда за пересыпание). */
private fun starPath(cx: Float, cy: Float, rOuter: Float): Path {
    val p = Path()
    for (i in 0 until 10) {
        val ang = -PI / 2.0 + i * (PI / 5.0)
        val r = if (i % 2 == 0) rOuter else rOuter * 0.45f
        val x = cx + r * cos(ang)
        val y = cy + r * sin(ang)
        if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
    }
    p.close()
    return p
}

/** Сердечко (форма конфетти и искр). */
private fun heartPath(cx: Float, cy: Float, s: Float): Path {
    val p = Path()
    p.moveTo(cx, cy + s * 0.9f)
    p.cubicTo(cx - s * 1.5f, cy - s * 0.3f, cx - s * 0.6f, cy - s * 1.4f, cx, cy - s * 0.35f)
    p.cubicTo(cx + s * 0.6f, cy - s * 1.4f, cx + s * 1.5f, cy - s * 0.3f, cx, cy + s * 0.9f)
    p.close()
    return p
}

/**
 * «Вечер» для автопереключения режима: с 19:00 до 7:00 игра сама
 * включают успокаивающий ночной режим (пример из «Мобилизации»:
 * разный контент в разное время суток — вечер перед сном).
 */
private fun isNightNow(): Boolean {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return hour >= 19 || hour < 7
}

/** Падающее зерно в полёте. */
private class FallingGrain(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    val r: Float,        // радиус, px
    val shade: Float,    // 0..1 — оттенок зерна
    var bounces: Int,    // сколько раз подпрыгнуло
    val golden: Boolean = false // пасхалка: редкое золотое зёрнышко
)

/** Конфетти (салют при вехах и завершении).
 *  [shape]: 0 — кружок, 1 — звезда, 2 — сердечко (награды не повторяются). */
private class Confetti(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val color: Color, val r: Float, val shape: Int = 0
)

/** Облачко-пуф: тап-волна, пыль при падении крупы. */
private class Puff(val x: Float, val y: Float, var life: Float)

/**
 * Состояние одной банки.
 * На столе крупа описывается рельефом [heights], при подъёме — уровнем [level]
 * (поверхность крупы в наклонённой открытой банке остаётся горизонтальной).
 * [dir] — плавное направление наклона: +1 льёт вправо, −1 влево
 * (средняя банка разворачивается по направлению переноса).
 */
private class JarState {
    var tilt = 0f        // 0..1(+) — пружинная анимация наклона
    var tiltVel = 0f
    var level = 0f       // уровень крупы в поднятой банке, 0..1
    var hoverX = 0f       // x зависания (управляется свайпом); при tilt→0 банка съезжает к базе
    var dir = 1f         // −1..+1, пружинно анимируется
    var dirVel = 0f
    val heights = FloatArray(COLS)
    var lastMilestone = 0  // сколько четвертей наполнения уже отпраздновано
    var happy = 0f         // время радости (глаза-дужки, подскоки), сек
    var boing = -1f        // «сочность»: время с захвата (пружина squash-stretch); < 0 — нет

    fun volume(): Float {
        var s = 0f
        for (v in heights) s += v
        return s / COLS
    }

    fun setFlat(v: Float, seed: Int) {
        level = v
        lastMilestone = (v * 4f).toInt()
        for (i in heights.indices) {
            heights[i] = (v + (hash1(i * 131 + seed) - 0.5f) * 0.025f).coerceIn(0f, 1f)
        }
    }
}

// ============================================================================
// Мир игры
// ============================================================================

private class PourWorld(val dp: Float) {

    var w = 0f
    var h = 0f
    var jw = 0f
    var jh = 0f
    var floorY = 0f
    var typeIdx = 0

    val jars = Array(JARS) { JarState() }
    val tableHeights = FloatArray(TCOLS) // кучки крупы на столе
    var lifted = -1          // индекс поднятой банки, -1 — все на столе
    var pouring = false
    var dragging = false     // пользователь тянет поднятую банку
    var tiltBoost = 0f       // добавка наклона от вертикального свайпа

    // Режим «день/ночь»: nightTarget — что выбрали, nightMix — плавный переход 0..1.
    var nightTarget = false
    var nightMix = 0f

    val grains = ArrayList<FallingGrain>()
    val confetti = ArrayList<Confetti>()
    val puffs = ArrayList<Puff>()

    var emitAcc = 0f
    var time = 0f
    var shake = 0f
    var shakeJar = -1
    var starPhase = -1f      // < 0 — звезды нет; иначе время с момента награды
    var starAt = -1          // над какой банкой рисовать звезду

    // ── «Сочность» и пасхалки v5 ──────────────────────────────────────────
    var heapPulse = 0f        // вздрагивание кучки под струёй, 0..1, затухает
    var rainbowT = -1f        // время старта радуги в струе; < 0 — нет радуги
    var scareT = -10f         // когда поймали бабочку — они разлетаются
    var sunPoke = -10f        // тап по солнышку (пульс)
    var moonPoke = -10f       // тап по месяцу (подпрыгивание)
    var cloudPoke = -10f      // тап по облаку (покачивание)
    val butterflyPhase = FloatArray(2) // накопленные фазы полёта бабочек
    val fireflyPhase = FloatArray(4)   // и светлячков (для точного попадания)

    // куда смотрят глаза
    var lastLandX = 0f
    var lastLandY = 0f
    var lastLandJar = -1     // в какую банку сейчас сыплется (-1 — на стол)
    var lastTapX = 0f
    var lastTapY = 0f
    var lastTapT = -10f

    var everLifted = 0
    var onEvent: (String) -> Unit = {}

    private var initialized = false
    private val maxTiltRad = MAX_TILT_DEG * DEG2RAD

    val type: GrainType get() = GRAIN_TYPES[typeIdx]

    // ---------------------------------------------------------------- геометрия

    fun layout(width: Float, height: Float) {
        w = width; h = height
        val small = GRAIN_TYPES[typeIdx].smallJar
        jw = min(min(w * 0.21f, h * 0.24f), 150f * dp) * (if (small) 0.80f else 1f)
        jh = jw * 1.45f
        floorY = h * 0.80f
        if (!initialized) {
            initialized = true
            jars[0].setFlat(1f, 1)   // слева — полная
            jars[1].setFlat(0f, 2)   // в центре — пустая
            jars[2].setFlat(0f, 3)   // справа — пустая
            jars[0].dir = 1f
            jars[1].dir = 1f
            jars[2].dir = -1f
        }
    }

    fun reset(type: Int) {
        typeIdx = type
        jars[0].setFlat(1f, 1)
        jars[1].setFlat(0f, 2)
        jars[2].setFlat(0f, 3)
        jars[0].dir = 1f; jars[1].dir = 1f; jars[2].dir = -1f
        for (j in jars) { j.tilt = 0f; j.tiltVel = 0f; j.dirVel = 0f; j.happy = 0f; j.boing = -1f }
        for (i in tableHeights.indices) tableHeights[i] = 0f
        lifted = -1; pouring = false; dragging = false; tiltBoost = 0f
        grains.clear(); confetti.clear(); puffs.clear()
        emitAcc = 0f; starPhase = -1f; starAt = -1
        heapPulse = 0f; rainbowT = -1f
        layout(w, h)
    }

    /** Слева, в центре и справа. */
    fun baseCx(i: Int) = when (i) {
        0 -> w * 0.20f
        1 -> w * 0.50f
        else -> w * 0.80f
    }

    fun baseCy() = floorY - jh / 2f

    /** Ближайшая ДРУГАЯ банка к точке x (куда смотрит/куда сыплем). */
    fun nearestOther(i: Int, x: Float): Int {
        var best = -1
        var bd = Float.MAX_VALUE
        for (j in jars.indices) if (j != i) {
            val d = abs(baseCx(j) - x)
            if (d < bd) { bd = d; best = j }
        }
        return best
    }

    /** Высота, на которой левитирует поднятая банка (губа чуть выше горлышка соседки). */
    private fun liftY() = floorY - jh - 30f * dp

    /** Позиция центра банки: на столе — база; поднятая — зависание.
     *  Интерполяция идёт по tilt, поэтому при опускании банка плавно
     *  съезжает с неба обратно на своё место, без телепортации. */
    fun center(i: Int): Offset {
        val e = jars[i].tilt.coerceIn(0f, 1f)
        val fromX = baseCx(i); val fromY = baseCy()
        return Offset(fromX + (jars[i].hoverX - fromX) * e, fromY + (liftY() - fromY) * e)
    }

    fun offset(i: Int): Offset {
        val c = center(i)
        return Offset(c.x - baseCx(i), c.y - baseCy())
    }

    /** Знак угла — направление наклона (для средней банки — плавное). */
    fun angleRad(i: Int): Float {
        val a = jars[i].dir * maxTiltRad * jars[i].tilt
        val lim = 80f * DEG2RAD
        return a.coerceIn(-lim, lim)
    }

    fun angleDeg(i: Int) = angleRad(i) * RAD2DEG

    /** Точка «губы» наклонённой банки — откуда сыплется струя. */
    fun lipPos(i: Int): Offset {
        val s = jars[i].dir
        val th = angleRad(i)
        val lx = s * jw * 0.30f; val ly = -jh / 2f
        val c = center(i)
        return Offset(c.x + lx * cos(th) - ly * sin(th), c.y + lx * sin(th) + ly * cos(th))
    }

    // ------------------------------------------------- позиции «живого мира»

    fun sunPos() = Offset(w * 0.11f, floorY * 0.14f)
    fun moonPos() = Offset(w * 0.84f, floorY * 0.16f)

    fun cloudPos(b: Int): Offset {
        val period = w + 160f * dp
        val x = (time * (10f + b * 6f) * dp + b * period * 0.5f) % period - 80f * dp
        return Offset(x, floorY * (0.10f + b * 0.09f))
    }

    /** Бабочка: летит по накопленной фазе (после «поимки» — ускоряется). */
    fun butterflyPos(b: Int): Offset {
        val x = ((butterflyPhase[b] + hash1(b + 90)) % 1.15f) * w
        val y = floorY * (0.28f + 0.12f * sin(time * 0.9f + b * 2.4f)) + sin(time * 3.1f + b) * 8f * dp
        return Offset(x, y)
    }

    fun fireflyPos(f: Int): Offset {
        val x = ((fireflyPhase[f] + hash1(f + 40)) % 1.2f) * w * 1.05f - w * 0.03f
        val y = floorY * (0.35f + 0.25f * hash1(f * 13 + 5)) + sin(time * 0.8f + f * 2.2f) * 14f * dp
        return Offset(x, y)
    }

    // ---------------------------------------------------------------- ввод

    private fun hitBase(i: Int, p: Offset): Boolean {
        val cx = baseCx(i)
        return p.x in (cx - jw * 0.85f)..(cx + jw * 0.85f) &&
                p.y in (floorY - jh * 1.35f)..(floorY + 40f * dp)
    }

    private fun hitLifted(i: Int, p: Offset): Boolean {
        val c = center(i)
        return abs(p.x - c.x) < jw * 1.1f && abs(p.y - c.y) < jh * 0.95f
    }

    fun tap(p: Offset) {
        lastTapX = p.x; lastTapY = p.y; lastTapT = time
        puffs.add(Puff(p.x, p.y, 0.5f))
        if (tapScenery(p)) return
        var handled = false
        if (lifted == -1) {
            // несколько банок — берём ту, чья база ближе к пальцу
            var best = -1
            var bestD = Float.MAX_VALUE
            for (i in jars.indices) if (hitBase(i, p)) {
                val d = abs(baseCx(i) - p.x)
                if (d < bestD) { bestD = d; best = i }
            }
            if (best >= 0) {
                handled = true
                val vol = jars[best].volume()
                if (vol > 0.03f) {
                    lifted = best
                    jars[best].level = vol
                    jars[best].hoverX = baseCx(best)
                    jars[best].boing = 0f // «сочность»: упругий squash-stretch
                    onEvent("lift")
                } else {
                    shake = 0.5f; shakeJar = best; onEvent("empty")
                }
            }
        } else {
            val i = lifted
            if (hitLifted(i, p)) {
                handled = true
                lowerLifted(); onEvent("lower")
            } else {
                // тап по любой другой банке тоже запускает пересыпание
                for (j in jars.indices) {
                    if (j != i && hitBase(j, p) && !pouring) {
                        handled = true
                        pouring = true
                        emitAcc = 0f
                        shake = 0.16f; shakeJar = i // микро-встряска при старте
                        if (Random.nextFloat() < 0.10f) { rainbowT = time; onEvent("rainbow") }
                        lastLandJar = j
                        lastLandX = baseCx(j)
                        lastLandY = floorY - jh * 0.3f
                        onEvent("pourStart")
                        break
                    }
                }
            }
        }
        if (!handled) tapHeap(p)
    }

    /**
     * Тап по «живому миру»: бабочку (днём) или светлячка (ночью) можно
     * «поймать», солнышко пульсирует, месяц подпрыгивает, облако
     * покачивается. Всё — без последствий, чистая радость (Safe Exploration).
     */
    private fun tapScenery(p: Offset): Boolean {
        if (nightMix < 0.5f) {
            for (b in 0 until 2) {
                val bp = butterflyPos(b)
                if (abs(p.x - bp.x) < 24f * dp && abs(p.y - bp.y) < 24f * dp) {
                    scareT = time
                    sparkleAt(bp.x, bp.y, 9, pink = true)
                    puffs.add(Puff(bp.x, bp.y, 0.5f))
                    onEvent("butterfly")
                    return true
                }
            }
            val sp = sunPos()
            if (abs(p.x - sp.x) < 40f * dp && abs(p.y - sp.y) < 40f * dp) {
                sunPoke = time
                sparkleAt(sp.x, sp.y + 26f * dp, 8, pink = false)
                onEvent("sun")
                return true
            }
        } else {
            for (f in 0 until 4) {
                val fp = fireflyPos(f)
                if (abs(p.x - fp.x) < 22f * dp && abs(p.y - fp.y) < 22f * dp) {
                    sparkleAt(fp.x, fp.y, 7, pink = false)
                    puffs.add(Puff(fp.x, fp.y, 0.5f))
                    onEvent("firefly")
                    return true
                }
            }
            val mp = moonPos()
            if (abs(p.x - mp.x) < 32f * dp && abs(p.y - mp.y) < 32f * dp) {
                moonPoke = time
                sparkleAt(mp.x, mp.y, 8, pink = false)
                onEvent("moon")
                return true
            }
        }
        if (nightMix < 0.85f) {
            for (b in 0 until 2) {
                val cp = cloudPos(b)
                val r = (16f + b * 6f) * dp
                if (abs(p.x - cp.x) < r * 1.6f && abs(p.y - cp.y) < r * 1.2f) {
                    cloudPoke = time
                    puffs.add(Puff(cp.x, cp.y + r * 0.6f, 0.6f))
                    puffs.add(Puff(cp.x - r * 0.8f, cp.y + r * 0.7f, 0.5f))
                    puffs.add(Puff(cp.x + r * 0.8f, cp.y + r * 0.7f, 0.5f))
                    onEvent("cloud")
                    return true
                }
            }
        }
        return false
    }

    /** Тап по кучке на столе: зёрнышки подпрыгивают и падают обратно. */
    private fun tapHeap(p: Offset): Boolean {
        if (tableVolume() < 0.05f) return false
        val surf = tableSurfaceY(p.x)
        if (p.y < surf - 24f * dp || p.y > floorY + 40f * dp) return false
        val n = 7
        for (k in 0 until n) {
            val a = (-PI / 2.0 + (k - (n - 1) / 2.0) * 0.30).toFloat()
            val sp = (170f + Random.nextFloat() * 150f) * dp
            grains.add(
                FallingGrain(
                    x = p.x + (Random.nextFloat() - 0.5f) * 14f * dp,
                    y = surf - 2f * dp,
                    vx = cos(a) * sp * 0.55f,
                    vy = sin(a) * sp,
                    r = type.grainR * dp * (0.8f + Random.nextFloat() * 0.5f),
                    shade = Random.nextFloat(),
                    bounces = 0
                )
            )
        }
        addMassTable(p.x, -n * 0.9f / type.particlesPerJar) // зёрна вернутся, когда упадут
        heapPulse = 1f
        onEvent("heapPoke")
        return true
    }

    /**
     * Драг: по поднятой банке — летим горизонтально и меняем наклон вертикально.
     * Средняя банка по направлению переноса сама разворачивается.
     */
    fun dragStart(p: Offset) {
        lastTapX = p.x; lastTapY = p.y; lastTapT = time
        if (lifted >= 0 && hitLifted(lifted, p)) {
            dragging = true
        }
    }

    fun dragBy(dx: Float, dy: Float) {
        if (dragging && lifted >= 0 && w > 0f) {
            jars[lifted].hoverX = (jars[lifted].hoverX + dx).coerceIn(w * 0.12f, w * 0.88f)
            tiltBoost = (tiltBoost - dy / (h * 0.35f)).coerceIn(-0.45f, 0.20f)
        }
    }

    fun dragEnd() {
        dragging = false
    }

    private fun lowerLifted() {
        pouring = false
        tiltBoost = 0f
        dragging = false
        rainbowT = -1f
        reseat(lifted)
        lifted = -1
    }

    /** Банка вернулась на стол — пересобрать рельеф из уровня. */
    private fun reseat(i: Int) {
        jars[i].setFlat(jars[i].level, i * 991 + (time * 7f).toInt())
    }

    // ---------------------------------------------------------------- кучки на столе

    fun tableVolume(): Float {
        var s = 0f
        for (v in tableHeights) s += v
        return s
    }

    private fun tableCol(x: Float): Int = (x / (w / TCOLS)).toInt().coerceIn(0, TCOLS - 1)

    private fun tableSurfaceY(x: Float): Float {
        return floorY - 2f * dp - tableHeights[tableCol(x)] * jh * 0.55f
    }

    private fun addMassTable(x: Float, m: Float) {
        val add = m * 16f
        val c = tableCol(x)
        // coerceIn(0..1): масса может и убывать (кучку «взбодрили» тапом)
        tableHeights[c] = (tableHeights[c] + add * 0.6f).coerceIn(0f, 1f)
        if (c > 0) tableHeights[c - 1] = (tableHeights[c - 1] + add * 0.2f).coerceIn(0f, 1f)
        if (c < TCOLS - 1) tableHeights[c + 1] = (tableHeights[c + 1] + add * 0.2f).coerceIn(0f, 1f)
        if (Random.nextFloat() < 0.08f) {
            puffs.add(Puff(x, tableSurfaceY(x), 0.4f))
        }
    }

    // ---------------------------------------------------------------- физика

    /** Скорость потока (доля банки в секунду) при данном угле и остатке крупы. */
    private fun flowAt(angleDeg: Float, srcLevel: Float): Float {
        val t = type
        if (angleDeg < t.pourAtDeg) return 0f
        val k = ((angleDeg - t.pourAtDeg) / (80f - t.pourAtDeg)).coerceIn(0f, 1f)
        val head = 0.35f + 0.65f * srcLevel.coerceIn(0f, 1f) // чем меньше крупы, тем слабее струя
        val sputter = 0.85f + 0.15f * sin(time * 11f) * sin(time * 5.3f) // живое «шуршание»
        val calm = 1f - 0.35f * nightMix // ночью крупа льётся медленнее
        return t.flowPerSec * (0.25f + 0.75f * k * k) * head * sputter * calm
    }

    /** Максимальный перепад между соседними столбиками (угол откоса). */
    private fun jarMaxDiff(): Float {
        val colW = (jw - 8f * dp) / COLS
        return (colW / (jh * 0.96f)) * tan(type.reposeDeg * DEG2RAD)
    }

    private fun tableMaxDiff(): Float {
        val colW = w / TCOLS
        return (colW / (jh * 0.55f)) * tan(type.reposeDeg * DEG2RAD)
    }

    /** Осыпание: слишком крутые склоны «стекают» в соседние столбики. */
    private fun relaxSlope(hh: FloatArray, maxDiff: Float, cap: Float) {
        val n = hh.size
        for (pass in 0 until 2) {
            var i = 0
            while (i < n - 1) {
                val d = hh[i] - hh[i + 1]
                if (d > maxDiff) {
                    val m = (d - maxDiff) * 0.25f
                    hh[i] -= m; hh[i + 1] += m
                } else if (-d > maxDiff) {
                    val m = (-d - maxDiff) * 0.25f
                    hh[i + 1] -= m; hh[i] += m
                }
                i++
            }
        }
        for (i in hh.indices) hh[i] = hh[i].coerceIn(0f, cap)
    }

    private fun surfaceY(idx: Int, x: Float): Float {
        val cx = baseCx(idx)
        val innerW = jw - 8f * dp
        if (abs(x - cx) > innerW / 2f + 2f * dp) return -1f
        val innerH = jh * 0.96f
        val c = (((x - (cx - innerW / 2f)) / innerW) * COLS).toInt().coerceIn(0, COLS - 1)
        val hh = jars[idx].heights[c].coerceIn(0f, 1.02f)
        return (floorY - 2f * dp) - innerH * hh
    }

    private fun addMass(idx: Int, x: Float, m: Float) {
        val cx = baseCx(idx)
        val innerW = jw - 8f * dp
        val c = (((x - (cx - innerW / 2f)) / innerW) * COLS).toInt().coerceIn(0, COLS - 1)
        val add = m * COLS
        val hh = jars[idx].heights
        hh[c] = min(1.04f, hh[c] + add * 0.6f)
        if (c > 0) hh[c - 1] = min(1.04f, hh[c - 1] + add * 0.2f)
        if (c < COLS - 1) hh[c + 1] = min(1.04f, hh[c + 1] + add * 0.2f)
    }

    /** Естественный конец пересыпания: банка наелась или опустела — праздник! */
    private fun finishPour(celebrate: Int) {
        val srcIdx = lifted
        if (jars[srcIdx].level <= 0.004f) jars[srcIdx].level = 0f
        pouring = false
        tiltBoost = 0f
        dragging = false
        rainbowT = -1f
        reseat(srcIdx)
        lifted = -1 // банка сама плавно опускается
        // Разнообразие наград: каждая восьмая — редкая «золотая».
        val golden = Random.nextInt(8) == 0
        starPhase = 0f
        starAt = celebrate
        jars[celebrate].happy = if (golden) 2.0f else 1.2f
        spawnConfetti(celebrate, if (golden) 56 else 40, golden)
        onEvent(if (golden) "goldReward" else "done")
    }

    private fun spawnConfetti(i: Int, n: Int, golden: Boolean = false) {
        val cx = baseCx(i)
        val topY = floorY - jh - 8f * dp
        val palette = if (golden) {
            listOf(Color(0xFFFFD54F), Color(0xFFFFE082), Color(0xFFFFC107), Color(0xFFF57F17))
        } else {
            listOf(Color(0xFFFFC107), Color(0xFFFFE082), type.light, type.base, Color(0xFFFF7043))
        }
        repeat(n) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val sp = (140f + Random.nextFloat() * 260f) * dp
            confetti.add(
                Confetti(
                    x = cx + (Random.nextFloat() - 0.5f) * jw * 0.6f,
                    y = topY,
                    vx = cos(a) * sp,
                    vy = -abs(sin(a)) * sp - 120f * dp,
                    life = (0.9f + Random.nextFloat() * 0.6f) * (if (golden) 1.3f else 1f),
                    color = palette[Random.nextInt(palette.size)],
                    r = (if (golden) 2.4f + Random.nextFloat() * 1.6f else 1.6f + Random.nextFloat() * 2.4f) * dp,
                    shape = if (golden) 1 else Random.nextInt(3)
                )
            )
        }
    }

    /** Россыпь искр в точке: пасхалки, поимка бабочки, тап по солнышку. */
    fun sparkleAt(x: Float, y: Float, n: Int, pink: Boolean) {
        val palette = if (pink) {
            listOf(Color(0xFFF48FB1), Color(0xFFCE93D8), Color(0xFFFFF176), Color.White)
        } else {
            listOf(Color(0xFFFFD54F), Color(0xFFFFECB3), Color(0xFFFF8A65), Color.White)
        }
        repeat(n) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val sp = (60f + Random.nextFloat() * 160f) * dp
            confetti.add(
                Confetti(
                    x = x, y = y,
                    vx = cos(a) * sp,
                    vy = -abs(sin(a)) * sp - 60f * dp,
                    life = 0.5f + Random.nextFloat() * 0.5f,
                    color = palette[Random.nextInt(palette.size)],
                    r = (1.2f + Random.nextFloat() * 1.8f) * dp,
                    shape = if (pink) 2 else 1
                )
            )
        }
    }

    fun step(dtRaw: Float) {
        if (w <= 0f) return
        val dt = dtRaw.coerceIn(0.001f, 0.033f)
        time += dt

        // Плавный переход день ↔ ночь.
        val nt = if (nightTarget) 1f else 0f
        val dm = nt - nightMix
        nightMix += if (abs(dm) < 0.004f) dm else dm * min(1f, 2.5f * dt)

        if (shake > 0f) shake = max(0f, shake - dt)
        if (starPhase >= 0f) starPhase += dt
        for (j in jars) {
            if (j.happy > 0f) j.happy = max(0f, j.happy - dt)
            if (j.boing >= 0f) { j.boing += dt; if (j.boing > 1.2f) j.boing = -1f }
        }
        if (heapPulse > 0f) heapPulse = max(0f, heapPulse - dt * 2.2f)
        if (rainbowT >= 0f && time - rainbowT > 6f) rainbowT = -1f

        // Бабочки и светлячки: после «поимки» бабочки полторы секунды
        // разлетаются быстрее (фазы копятся — координаты совпадают с рендером).
        val scare = (time - scareT).coerceAtMost(1.6f)
        val boost = if (scare < 1.6f) 1f + 4f * (1f - scare / 1.6f) else 1f
        for (b in 0 until 2) butterflyPhase[b] += dt * (0.05f + b * 0.02f) * boost
        for (f in 0 until 4) fireflyPhase[f] += dt * (0.10f + 0.035f * f)

        // Пружинный наклон: плавный подъём с лёгким overshoot.
        for (i in jars.indices) {
            val j = jars[i]
            val target = if (lifted == i) (1f + tiltBoost).coerceIn(0.50f, 1.18f) else 0f
            j.tiltVel += (target - j.tilt) * 120f * dt
            j.tiltVel *= max(0f, 1f - 14f * dt)
            j.tilt += j.tiltVel * dt
            if (j.tilt < 0f) { j.tilt = 0f; j.tiltVel = 0f }
            j.tilt = min(j.tilt, 1.18f)
            if (abs(target - j.tilt) < 0.004f && abs(j.tiltVel) < 0.03f) { j.tilt = target; j.tiltVel = 0f }

            // Направление наклона: крайние банки льют к центру, средняя —
            // разворачивается туда, куда её тянут (с гистерезисом от дрожи).
            val dirT = when (i) {
                0 -> 1f
                2 -> -1f
                else -> when {
                    j.hoverX < w * 0.5f - jw * 0.25f -> -1f
                    j.hoverX > w * 0.5f + jw * 0.25f -> 1f
                    else -> if (j.dir < 0f) -1f else 1f
                }
            }
            j.dirVel += (dirT - j.dir) * 60f * dt
            j.dirVel *= max(0f, 1f - 8f * dt)
            j.dir += j.dirVel * dt
        }

        // Наклонился достаточно — крупа потекла сама (прямая причинность).
        // ВАЖНО: |угол| — иначе правая банка (наклон влево) не льётся.
        if (lifted >= 0 && !pouring && abs(angleDeg(lifted)) >= type.pourAtDeg) {
            pouring = true
            emitAcc = 0f
            shake = 0.16f; shakeJar = lifted // микро-встряска при старте
            if (Random.nextFloat() < 0.10f) { // иногда — радуга в струе
                rainbowT = time
                onEvent("rainbow")
            }
            lastLandJar = nearestOther(lifted, lipPos(lifted).x)
            lastLandX = baseCx(lastLandJar)
            lastLandY = floorY - jh * 0.3f
            onEvent("pourStart")
        }

        // Поток.
        if (pouring && lifted >= 0) {
            val src = jars[lifted]
            val flow = flowAt(abs(angleDeg(lifted)), src.level)
            if (flow > 0f) {
                val amt = min(flow * dt, src.level)
                if (amt > 0f) {
                    src.level = max(0f, src.level - amt)
                    emitAcc += amt * type.particlesPerJar
                    val lip = lipPos(lifted)
                    val s = jars[lifted].dir
                    while (emitAcc >= 1f) {
                        emitAcc -= 1f
                        grains.add(
                            FallingGrain(
                                x = lip.x + (Random.nextFloat() - 0.5f) * 7f * dp,
                                y = lip.y + Random.nextFloat() * 4f * dp,
                                vx = s * (35f + Random.nextFloat() * 65f) * dp * (0.35f + jars[lifted].tilt * 0.65f),
                                vy = (Random.nextFloat() * 30f - 8f) * dp,
                                r = type.grainR * dp * (0.8f + Random.nextFloat() * 0.5f),
                                shade = Random.nextFloat(),
                                bounces = 0,
                                golden = Random.nextFloat() < 0.007f // пасхалка!
                            )
                        )
                    }
                }
            }
        }

        // Рельеф крупы оседает по углу откоса: в банках и на столе.
        for (j in jars.indices) if (lifted != j) relaxSlope(jars[j].heights, jarMaxDiff(), 1.02f)
        if (tableVolume() > 0.001f) relaxSlope(tableHeights, tableMaxDiff(), 1f)

        // Падающие зёрна: гравитация, приземление в любую банку под струёй
        // или на стол, отскок крупной крупы, переливание через край.
        val g = 1700f * dp
        val it = grains.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.vy += g * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            var landed = false
            for (j in jars.indices) {
                if (j == lifted) continue // поднятая банка в воздухе — в неё не сыпем
                val sy = surfaceY(j, p.x)
                if (sy > 0f && p.y >= sy) {
                    if (jars[j].volume() >= 0.995f) {
                        // Банка полна: зерно скатывается с горки наружу — на стол.
                        val away = if (p.x < baseCx(j)) -1f else 1f
                        p.vx = away * (60f + Random.nextFloat() * 90f) * dp
                        if (p.vy > 250f * dp) {
                            p.y = sy - p.r
                            p.vy = -p.vy * 0.25f
                            p.bounces++
                        }
                    } else if (type.grainR >= 2.8f && p.vy > 360f * dp && p.bounces < 1) {
                        // горошина подпрыгивает
                        p.y = sy - p.r
                        p.vy = -p.vy * 0.32f
                        p.vx *= 0.6f
                        p.bounces++
                    } else {
                        addMass(j, p.x, 1f / type.particlesPerJar)
                        if (p.golden) { onEvent("treasure"); sparkleAt(p.x, sy, 10, pink = false) }
                        lastLandJar = j; lastLandX = p.x; lastLandY = sy
                        landed = true
                    }
                    break
                }
            }
            if (!landed && p.y >= tableSurfaceY(p.x)) {
                if (type.grainR >= 2.8f && p.vy > 360f * dp && p.bounces < 1) {
                    p.y = tableSurfaceY(p.x) - p.r
                    p.vy = -p.vy * 0.32f
                    p.vx *= 0.6f
                    p.bounces++
                } else {
                    addMassTable(p.x, 1f / type.particlesPerJar)
                    heapPulse = min(1f, heapPulse + 0.10f) // кучка вздрагивает от струи
                    if (p.golden) { onEvent("treasure"); sparkleAt(p.x, tableSurfaceY(p.x), 10, pink = false) }
                    lastLandJar = -1; lastLandX = p.x; lastLandY = floorY - 2f * dp
                    landed = true
                }
            }
            if (p.y > h + 30f) landed = true
            if (landed) it.remove()
        }

        // Вехи наполнения 25/50/75% и финиш — для ЛЮБОЙ банки под струёй.
        if (pouring && lifted >= 0) {
            var fullJar = -1
            for (j in jars.indices) {
                if (j == lifted) continue
                val v = jars[j].volume()
                val m = (v * 4f).toInt()
                if (m > jars[j].lastMilestone && v < 0.995f) {
                    jars[j].lastMilestone = m
                    jars[j].happy = 0.9f
                    spawnConfetti(j, 12)
                    onEvent("milestone")
                }
                if (v >= 0.995f) fullJar = j
            }
            val srcEmpty = jars[lifted].level <= 0.004f
            if (srcEmpty || fullJar >= 0) {
                val target = when {
                    fullJar >= 0 -> fullJar
                    lastLandJar >= 0 && lastLandJar != lifted -> lastLandJar
                    else -> nearestOther(lifted, jars[lifted].hoverX)
                }
                finishPour(target)
            }
        }

        val cit = confetti.iterator()
        while (cit.hasNext()) {
            val c = cit.next()
            c.life -= dt
            c.vy += 900f * dp * dt
            c.x += c.vx * dt
            c.y += c.vy * dt
            if (c.life <= 0f || c.y > h + 20f) cit.remove()
        }

        val pit = puffs.iterator()
        while (pit.hasNext()) {
            val u = pit.next()
            u.life -= dt
            if (u.life <= 0f) pit.remove()
        }
    }

    // ---------------------------------------------------------------- взгляд и подсказки

    /** Куда смотрит банка-персонаж: струя → сосед → палец → ленивое блуждание. */
    fun lookTarget(i: Int): Offset {
        return when {
            pouring && lifted >= 0 -> Offset(lastLandX, lastLandY)
            lifted >= 0 -> if (i == lifted) center(nearestOther(i, jars[i].hoverX)) else center(lifted)
            time - lastTapT < 2.5f -> Offset(lastTapX, lastTapY)
            else -> Offset(
                w * (0.5f + 0.35f * sin(time * 0.21f + i * 2.1f)),
                floorY - jh * 0.4f
            )
        }
    }

    /** Индекс банки-подсказки (−1 — не показывать): куда тапнуть/потянуть. */
    fun hint(): Int {
        return when {
            lifted >= 0 && !pouring -> lifted
            lifted == -1 -> {
                var full = -1
                var fv = 0.03f
                for (j in jars.indices) if (jars[j].volume() > fv) { fv = jars[j].volume(); full = j }
                if (full >= 0 && (everLifted < 3 || time % 14f > 11.5f)) full else -1
            }
            else -> -1
        }
    }
}

// ============================================================================
// Рендер (Compose Canvas)
// ============================================================================

private fun DrawScope.drawScene(world: PourWorld, tick: Int) {
    if (world.w <= 0f) return
    val type = world.type
    val dp = world.dp
    val w = size.width
    val h = size.height
    val floorY = world.floorY
    val mix = world.nightMix

    // Небо: плавный переход «день ↔ ночь».
    val bgTop = lerp(Color(0xFFFFF8E1), Color(0xFF0D1440), mix)
    val bgBot = lerp(Color(0xFFFFE0B2), Color(0xFF24356B), mix)
    drawRect(
        Brush.verticalGradient(listOf(bgTop, bgBot), startY = 0f, endY = floorY),
        Offset(0f, 0f), Size(w, floorY + 2f)
    )

    drawStars(world, w, floorY, mix)
    drawSun(world, w, floorY, 1f - mix)
    drawMoon(world, w, floorY, mix, lerp(bgTop, bgBot, 0.16f))
    drawClouds(world, w, floorY, 1f - mix * 0.75f)
    drawHills(w, floorY, type.scene, dp, mix)
    drawButterflies(world, 1f - mix)
    drawFireflies(world, mix)
    drawTable(w, h, floorY, dp)
    // Ночью стол притемняется — лунный свет.
    if (mix > 0.01f) {
        drawRect(Color(0xFF26324F).copy(alpha = 0.40f * mix), Offset(0f, floorY), Size(w, h - floorY))
    }
    drawHeap(world, type)

    val hint = world.hint()
    if (hint >= 0) drawHintRing(world, hint, dp)

    for (i in world.jars.indices) drawJarShadow(world, i, dp)

    // Поднятая банка рисуется поверх остальных.
    for (i in world.jars.indices) if (i != world.lifted) drawJar(world, i, type)
    if (world.lifted >= 0) drawJar(world, world.lifted, type)

    drawGrains(world, type)
    drawPuffs(world, dp)
    drawConfetti(world)
    drawStarBurst(world, dp)
    if (hint >= 0 && !world.pouring) drawHintArrow(world, hint, dp)
}

/** Солнышко с медленно вращающимися лучами (днём; ночью плавно тает).
 *  Отвечает на тап упругим «пульсом» (сочность, juice). */
private fun DrawScope.drawSun(world: PourWorld, w: Float, floorY: Float, a: Float) {
    if (a < 0.02f) return
    val dp = world.dp
    val c = world.sunPos()
    val poke = if (world.sunPoke >= 0f) (1f - (world.time - world.sunPoke)).coerceIn(0f, 1f) else 0f
    val pulse = 1f + 0.18f * poke * abs(sin(world.time * 18f))
    drawCircle(Color(0xFFFFE082).copy(alpha = 0.45f * a), 46f * dp * pulse, c)
    withTransform({ rotate(world.time * (8f + 30f * poke), pivot = c) }) {
        for (k in 0 until 8) {
            val ang = k * 45f * DEG2RAD
            val long = if (k % 2 == 0) 34f else 27f
            drawLine(
                Color(0xFFFFC107).copy(alpha = 0.7f * a),
                Offset(c.x + 22f * dp * cos(ang), c.y + 22f * dp * sin(ang)),
                Offset(c.x + long * dp * pulse * cos(ang), c.y + long * dp * pulse * sin(ang)),
                strokeWidth = 4f * dp
            )
        }
    }
    drawCircle(Color(0xFFFFD54F).copy(alpha = a), 20f * dp * pulse, c)
}

/** Ночное небо: мерцающие звёздочки с мягкими ореолами (bloom по мотивам UE5). */
private fun DrawScope.drawStars(world: PourWorld, w: Float, floorY: Float, mix: Float) {
    if (mix < 0.02f) return
    val dp = world.dp
    for (s in 0 until 28) {
        val hx = hash1(s * 17 + 3)
        val hy = hash1(s * 29 + 11)
        val x = hx * w
        val y = hy * floorY * 0.60f + 4f * dp
        val tw = 0.45f + 0.55f * abs(sin(world.time * (0.6f + hx) + s * 2.1f))
        val r = (0.8f + hash1(s * 7 + 1) * 1.4f) * dp
        // ореол: радиальный градиент от тёплого света к прозрачному
        val halo = r * 4.5f
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFFF3B0).copy(alpha = 0.4f * mix * tw), Color.Transparent),
                center = Offset(x, y), radius = halo
            ),
            halo, Offset(x, y)
        )
        drawCircle(Color(0xFFFFF3B0).copy(alpha = mix * tw), r, Offset(x, y))
    }
}

/** Месяц с ореолом (появляется ночью). [skyAt] — цвет неба на высоте месяца,
 *  им «выкусывается» серп. */
private fun DrawScope.drawMoon(world: PourWorld, w: Float, floorY: Float, mix: Float, skyAt: Color) {
    if (mix < 0.02f) return
    val dp = world.dp
    // тап — месяц упруго подпрыгивает (мир отвечает на касание)
    val poke = if (world.moonPoke >= 0f) (1f - (world.time - world.moonPoke)).coerceIn(0f, 1f) else 0f
    val mp = world.moonPos()
    val c = Offset(mp.x, mp.y - poke * 6f * dp * abs(sin(world.time * 16f)))
    val r = 17f * dp * (1f + 0.12f * poke)
    drawCircle(Color(0xFFF4E8B9).copy(alpha = 0.35f * mix), r * 1.9f, c) // ореол
    drawCircle(Color(0xFFF4E8B9).copy(alpha = mix), r, c)
    drawCircle(skyAt.copy(alpha = mix), r * 0.86f, Offset(c.x + r * 0.42f, c.y - r * 0.12f))
}

/** Плывущие облака (ночью — тусклые, подсвеченные луной).
 *  Тап — облако мягко покачивается (мир отвечает на касание). */
private fun DrawScope.drawClouds(world: PourWorld, w: Float, floorY: Float, a: Float) {
    if (a < 0.02f) return
    val dp = world.dp
    val poke = if (world.cloudPoke >= 0f) (1f - (world.time - world.cloudPoke)).coerceIn(0f, 1f) else 0f
    for (b in 0 until 2) {
        val pos = world.cloudPos(b)
        val x = pos.x
        val y = pos.y + sin(world.time * 26f + b * 3f) * 5f * dp * poke
        val r = (16f + b * 6f) * dp
        drawCircle(Color.White.copy(alpha = 0.55f * a), r, Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.55f * a), r * 0.8f, Offset(x + r * 1.1f, y + r * 0.25f))
        drawCircle(Color.White.copy(alpha = 0.55f * a), r * 0.75f, Offset(x - r * 1.05f, y + r * 0.3f))
    }
}

/** Далёкие холмы, окрашенные под настроение крупы (ночью синеют). */
private fun DrawScope.drawHills(w: Float, floorY: Float, tint: Color, dp: Float, mix: Float) {
    val t = lerp(tint, Color(0xFF2E3A5C), 0.65f * mix)
    drawOval(t.copy(alpha = 0.30f), Offset(-w * 0.15f, floorY - 70f * dp), Size(w * 0.7f, 150f * dp))
    drawOval(t.copy(alpha = 0.22f), Offset(w * 0.55f, floorY - 55f * dp), Size(w * 0.75f, 130f * dp))
}

/** Бабочки, порхающие над столом (днём). Можно «поймать» пальцем! */
private fun DrawScope.drawButterflies(world: PourWorld, a: Float) {
    if (a < 0.02f) return
    val dp = world.dp
    for (b in 0 until 2) {
        val pos = world.butterflyPos(b)
        val x = pos.x
        val y = pos.y
        val flap = sin(world.time * (11f + b * 3f))
        val wing = (4.5f + 3.5f * abs(flap)) * dp
        val col = if (b == 0) Color(0xFFFF7043) else Color(0xFFBA68C8)
        drawOval(col.copy(alpha = 0.9f * a), Offset(x - wing, y - 5f * dp), Size(wing, 4.5f * dp))
        drawOval(col.copy(alpha = 0.9f * a), Offset(x, y - 5f * dp), Size(wing, 4.5f * dp))
        drawOval(col.copy(alpha = 0.7f * a), Offset(x - wing * 0.8f, y + 0.5f * dp), Size(wing * 0.8f, 3.5f * dp))
        drawOval(col.copy(alpha = 0.7f * a), Offset(x, y + 0.5f * dp), Size(wing * 0.8f, 3.5f * dp))
        drawCircle(Color(0xFF5D4037).copy(alpha = a), 1.4f * dp, Offset(x, y))
    }
}

/** Светлячки — ночная замена бабочкам: плывут и размеренно вспыхивают.
 *  Каждый — с мягким свечением-ореолом (bloom). Тоже «ловятся» пальцем. */
private fun DrawScope.drawFireflies(world: PourWorld, mix: Float) {
    if (mix < 0.02f) return
    val dp = world.dp
    for (f in 0 until 4) {
        val pos = world.fireflyPos(f)
        val glow = 0.35f + 0.65f * abs(sin(world.time * (1.3f + f * 0.4f) + f))
        val halo = 8f * dp
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFD9F26B).copy(alpha = 0.45f * glow * mix), Color.Transparent),
                center = pos, radius = halo
            ),
            halo, pos
        )
        drawCircle(Color(0xFFFFF9C4).copy(alpha = 0.9f * glow * mix), 2f * dp, pos)
    }
}

/** Деревянный стол: доски, кромка. */
private fun DrawScope.drawTable(w: Float, h: Float, floorY: Float, dp: Float) {
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFFBC8D5F), Color(0xFF8D5E3C)), startY = floorY, endY = h),
        Offset(0f, floorY), Size(w, h - floorY)
    )
    drawRect(Color(0xFFD7B089), Offset(0f, floorY), Size(w, 4f * dp))
    drawRect(Color(0xFF6D4A2F).copy(alpha = 0.6f), Offset(0f, floorY + 4f * dp), Size(w, 3f * dp))
    val step = w / 5f
    var x = step
    while (x < w) {
        drawRect(Color.Black.copy(alpha = 0.08f), Offset(x, floorY + 4f * dp), Size(2f * dp, h - floorY))
        x += step
    }
}

/** Кучки рассыпанной на стол крупы — настоящие горки с углом откоса.
 *  «Сочность»: под струёй кучка чуть вздрагивает; тонкие края светятся
 *  изнутри (subsurface, по мотивам рецептов UE5 Cookbook). */
private fun DrawScope.drawHeap(world: PourWorld, type: GrainType) {
    if (world.tableVolume() < 0.05f) return
    val dp = world.dp
    val w = size.width
    val colW = w / TCOLS
    val maxH = world.jh * 0.55f
    val floorY = world.floorY
    val shiver = 1f + 0.02f * sin(world.time * 40f) * world.heapPulse
    fun hy(c: Int) = floorY - 2f * dp - maxH * world.tableHeights[c] * shiver
    val p = Path().apply {
        moveTo(0f, floorY + 6f * dp)
        lineTo(0f, hy(0))
        for (c in 1 until TCOLS) lineTo(colW * c, hy(c))
        lineTo(w, floorY + 6f * dp)
        close()
    }
    drawPath(p, Brush.verticalGradient(listOf(type.light, type.base, type.dark), startY = floorY - maxH, endY = floorY))
    // зёрнышки по рельефу
    for (c in 0 until TCOLS) {
        val hh = world.tableHeights[c]
        if (hh < 0.02f) continue
        val n = (hh * 8f).toInt().coerceAtLeast(1)
        for (r in 0 until n) {
            val jx = hash1(c * 137 + r * 19)
            val jy = hash1(c * 61 + r * 83)
            val px = colW * (c + jx)
            val py = floorY - 2f * dp - maxH * hh * shiver * ((r + 0.3f + jy) / (n + 0.5f))
            drawCircle(lerp(type.dark, type.base, jy), type.grainR * dp * (0.6f + 0.4f * jx), Offset(px, py), alpha = 0.55f)
        }
    }
    // гребень с «блюмом»: широкий тёплый штрих под тонким белым
    val ridge = Path().apply {
        moveTo(0f, hy(0))
        for (c in 1 until TCOLS) lineTo(colW * c, hy(c))
    }
    drawPath(ridge, type.light.copy(alpha = 0.30f), style = Stroke(width = 5f * dp))
    drawPath(ridge, Color.White.copy(alpha = 0.30f), style = Stroke(width = 2f * dp))
    // тонкие края кучки «просвечивают» изнутри (subsurface-подсветка)
    for (c in 0 until TCOLS) {
        val hh = world.tableHeights[c]
        if (hh <= 0.02f || hh > 0.30f) continue
        drawCircle(type.light.copy(alpha = 0.35f), 2.6f * dp, Offset(colW * (c + 0.5f), hy(c)))
    }
}

/** Тень под банкой: остаётся на столе под банкой и тает при подъёме. */
private fun DrawScope.drawJarShadow(world: PourWorld, i: Int, dp: Float) {
    val cx = if (world.jars[i].tilt > 0.02f) world.jars[i].hoverX else world.baseCx(i)
    val e = world.jars[i].tilt.coerceIn(0f, 1f)
    val a = 0.18f * (1f - 0.55f * e)
    val r = world.jw * (0.75f - 0.25f * e)
    val c = Offset(cx, world.floorY - 2f * dp)
    drawCircle(
        brush = Brush.radialGradient(listOf(Color.Black.copy(alpha = a), Color.Transparent), center = c, radius = r),
        radius = r, center = c
    )
}

/** Силуэт банки с горлышком. */
private fun jarPath(cx: Float, cy: Float, jw: Float, jh: Float, dp: Float): Path {
    val halfB = jw / 2f
    val halfN = jw * 0.30f
    val top = cy - jh / 2f
    val bottom = cy + jh / 2f
    val shoulder = top + jh * 0.28f
    val lipH = 6f * dp
    val rC = min(12f * dp, jw * 0.14f)
    val p = Path()
    p.moveTo(cx - halfB, shoulder)
    p.lineTo(cx - halfN - 2f * dp, top + lipH)
    p.quadraticBezierTo(cx - halfN, top, cx - halfN + 3f * dp, top)
    p.lineTo(cx + halfN - 3f * dp, top)
    p.quadraticBezierTo(cx + halfN, top, cx + halfN + 2f * dp, top + lipH)
    p.lineTo(cx + halfB, shoulder)
    p.lineTo(cx + halfB, bottom - rC)
    p.quadraticBezierTo(cx + halfB, bottom, cx + halfB - rC, bottom)
    p.lineTo(cx - halfB + rC, bottom)
    p.quadraticBezierTo(cx - halfB, bottom, cx - halfB, bottom - rC)
    p.close()
    return p
}

private fun DrawScope.drawJar(world: PourWorld, i: Int, type: GrainType) {
    val dp = world.dp
    val jw = world.jw
    val jh = world.jh
    val bc = Offset(world.baseCx(i), world.baseCy())
    val off = world.offset(i)
    val deg = world.angleRad(i) * RAD2DEG
    val jar = world.jars[i]
    var shx = 0f
    if (world.shake > 0f && world.shakeJar == i) {
        shx = sin(world.time * 50f) * 6f * dp * world.shake * 2f
    }
    // радостный подскок банки-«объедалки»
    var hopY = 0f
    if (jar.happy > 0f && world.lifted != i) {
        hopY = -abs(sin((1.2f - jar.happy) * 16f)) * 8f * dp * min(1f, jar.happy * 3f)
    }

    // «сочность»: упругий squash-stretch при захвате (затухающая пружина)
    var sqX = 1f
    var sqY = 1f
    if (jar.boing in 0f..1.2f) {
        val b = exp(-jar.boing * 5.5f) * cos(jar.boing * 24f)
        sqX = 1f + 0.10f * b
        sqY = 1f - 0.14f * b
    }

    withTransform({
        translate(off.x + shx, off.y + hopY)
        rotate(deg, pivot = bc)
        scale(sqX, sqY, pivot = bc)
    }) {
        val glass = jarPath(bc.x, bc.y, jw, jh, dp)
        val halfB = jw / 2f
        val top = bc.y - jh / 2f
        val bottom = bc.y + jh / 2f

        // ---- 1. Содержимое (крупа) ----
        clipPath(glass) {
            if (world.lifted == i) {
                // Наклонённая открытая банка: поверхность крупы горизонтальна.
                rotate(-deg, pivot = bc) {
                    val th = deg * DEG2RAD
                    val cs = cos(th); val sn = sin(th)
                    fun ry(lx: Float, ly: Float) = lx * sn + ly * cs
                    val lowest = max(ry(-halfB, jh / 2f), ry(halfB, jh / 2f))
                    val lipLow = max(ry(-halfB, -jh / 2f), ry(halfB, -jh / 2f))
                    val surface = lowest - jar.level * (lowest - lipLow)
                    if (surface < lowest - 0.5f) {
                        val sy = bc.y + surface
                        drawRect(
                            Brush.verticalGradient(
                                listOf(type.light, type.base, type.dark),
                                startY = sy, endY = bc.y + lowest + 30f * dp
                            ),
                            Offset(bc.x - jw, sy), Size(jw * 2f, lowest - surface + 34f * dp)
                        )
                        val n = (jar.level * 110f).toInt() + 4
                        for (k in 0 until n) {
                            val jx = hash1(k * 127 + i * 7)
                            val jy = hash1(k * 311 + i * 13)
                            drawCircle(
                                color = lerp(type.dark, type.base, jy),
                                radius = type.grainR * dp * (0.55f + 0.55f * jx),
                                center = Offset(bc.x - halfB + jx * jw, sy + jy * (lowest - surface)),
                                alpha = 0.55f
                            )
                        }
                        // блик по поверхности
                        drawRect(Color.White.copy(alpha = 0.30f), Offset(bc.x - jw, sy - 1.5f * dp), Size(jw * 2f, 3f * dp))
                    }
                }
            } else {
                // Банка на столе: рисуем рельеф (настоящая горка!).
                var hasGrain = false
                for (hh in jar.heights) if (hh > 0.008f) { hasGrain = true; break }
                if (hasGrain) {
                    val innerW = jw - 8f * dp
                    val innerH = jh * 0.96f
                    val left = bc.x - innerW / 2f
                    val fill = Path().apply {
                        moveTo(left, bottom - 2f * dp)
                        lineTo(left, bottom - 2f * dp - innerH * jar.heights[0])
                        for (c in 1 until COLS) {
                            lineTo(left + innerW * c / COLS, bottom - 2f * dp - innerH * jar.heights[c])
                        }
                        lineTo(left + innerW, bottom - 2f * dp)
                        close()
                    }
                    drawPath(
                        fill,
                        Brush.verticalGradient(
                            listOf(type.light, type.base, type.dark),
                            startY = bottom - 2f * dp - innerH, endY = bottom
                        )
                    )
                    // зёрнышки по рельефу
                    for (c in 0 until COLS) {
                        val hc = jar.heights[c]
                        if (hc < 0.012f) continue
                        val n = (hc * 13f).toInt().coerceAtLeast(1)
                        for (r in 0 until n) {
                            val jx = hash1(c * 131 + r * 17 + i * 977)
                            val jy = hash1(c * 57 + r * 91 + i * 313)
                            val px = left + innerW * (c + jx) / COLS
                            val py = bottom - 2f * dp - innerH * hc * ((r + 0.2f + jy * 0.8f) / (n + 0.4f))
                            drawCircle(
                                color = lerp(type.dark, type.base, jx),
                                radius = type.grainR * dp * (0.6f + 0.5f * jy),
                                center = Offset(px, py),
                                alpha = 0.5f
                            )
                        }
                    }
                    // светящийся гребень
                    val ridge = Path().apply {
                        moveTo(left, bottom - 2f * dp - innerH * jar.heights[0])
                        for (c in 1 until COLS) {
                            lineTo(left + innerW * c / COLS, bottom - 2f * dp - innerH * jar.heights[c])
                        }
                    }
                    drawPath(ridge, Color.White.copy(alpha = 0.35f), style = Stroke(width = 2f * dp))
                }
            }
        }

        // ---- 2. Стекло ----
        drawPath(
            glass,
            Brush.horizontalGradient(
                listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.30f)),
                startX = bc.x - halfB, endX = bc.x + halfB
            )
        )
        drawPath(glass, Color(0xFF64B5F6).copy(alpha = 0.90f), style = Stroke(width = 3f * dp))
        // горлышко
        val halfN = jw * 0.30f
        drawRoundRect(
            Color(0xFF90CAF9),
            Offset(bc.x - halfN - 2f * dp, top - 2f * dp),
            Size(halfN * 2f + 4f * dp, 9f * dp),
            CornerRadius(4f * dp)
        )
        // вертикальный блик на стекле
        drawRoundRect(
            Color.White.copy(alpha = 0.35f),
            Offset(bc.x - halfB + 6f * dp, top + jh * 0.16f),
            Size(5f * dp, jh * 0.52f),
            CornerRadius(2.5f * dp)
        )
        drawCircle(Color.White.copy(alpha = 0.28f), 3f * dp, Offset(bc.x - halfB + 9f * dp, top + jh * 0.12f))

        // ---- 3. Личико-персонаж ----
        drawFace(world, i, bc, jw, jh, dp)
    }
}

/**
 * Живое личико банки: глаза следят за происходящим, моргают, радуются дужками,
 * «едят» крупу открытым ртом, пустая банка спит с летающими «Z-z-z».
 * Ночью непросыпающиеся глазки полуприкрыты сонными веками.
 */
private fun DrawScope.drawFace(world: PourWorld, i: Int, bc: Offset, jw: Float, jh: Float, dp: Float) {
    val jar = world.jars[i]
    val vol = if (world.lifted == i) jar.level else jar.volume()
    val sleeping = world.lifted != i && vol < 0.03f
    val happy = jar.happy > 0f
    val eating = world.pouring && world.lastLandJar == i
    val sleepy = world.nightMix > 0.5f && !sleeping && !happy && !eating
    val look = world.lookTarget(i)
    val eyeY = bc.y - jh * 0.10f
    val eyeDx = jw * 0.17f
    val r = jw * 0.105f
    val blink = ((world.time + i * 2.3f) % 4.6f) < 0.16f
    val lineC = Color(0xFF5D4037)

    for (side in intArrayOf(-1, 1)) {
        val ex = bc.x + side * eyeDx
        when {
            sleeping -> drawLine(lineC, Offset(ex - r, eyeY + r * 0.4f), Offset(ex + r, eyeY + r * 0.4f), strokeWidth = 2.5f * dp)
            happy -> {
                val p = Path().apply {
                    moveTo(ex - r, eyeY)
                    quadraticBezierTo(ex, eyeY - r * 1.5f, ex + r, eyeY)
                }
                drawPath(p, lineC, style = Stroke(width = 2.5f * dp))
            }
            blink -> drawLine(lineC, Offset(ex - r * 0.8f, eyeY), Offset(ex + r * 0.8f, eyeY), strokeWidth = 2.5f * dp)
            else -> {
                drawCircle(Color.White, r, Offset(ex, eyeY))
                var dx = look.x - ex
                var dy = look.y - eyeY
                var len = sqrt(dx * dx + dy * dy)
                if (len < 0.5f) { dx = 0f; dy = 1f; len = 1f } // по умолчанию — чуть вниз
                val px = ex + dx / len * r * 0.42f
                val py = eyeY + dy / len * r * 0.42f
                drawCircle(Color(0xFF4E342E), r * 0.42f, Offset(px, py))
                drawCircle(Color.White, r * 0.14f, Offset(px - r * 0.12f, py - r * 0.15f))
                if (sleepy) {
                    // сонное полуприкрытое веко
                    drawLine(lineC, Offset(ex - r, eyeY - r * 0.5f), Offset(ex + r, eyeY - r * 0.5f), strokeWidth = r * 0.8f)
                }
            }
        }
    }

    // ротик
    val my = eyeY + jh * 0.16f
    when {
        eating -> {
            drawCircle(Color(0xFF6D4C41), jw * 0.055f, Offset(bc.x, my))
            drawCircle(Color(0xFFE57373).copy(alpha = 0.8f), jw * 0.028f, Offset(bc.x, my + jw * 0.02f))
        }
        sleeping -> drawCircle(Color(0xFF6D4C41), jw * 0.03f, Offset(bc.x, my), alpha = 0.7f)
        else -> {
            val mw = if (happy) jw * 0.11f else jw * 0.07f
            val p = Path().apply {
                moveTo(bc.x - mw, my)
                quadraticBezierTo(bc.x, my + mw * 0.9f, bc.x + mw, my)
            }
            drawPath(p, lineC, style = Stroke(width = 2.2f * dp))
        }
    }

    // румянец
    drawCircle(Color(0xFFFF8A80).copy(alpha = 0.35f), jw * 0.05f, Offset(bc.x - eyeDx - r * 0.9f, eyeY + r * 1.5f))
    drawCircle(Color(0xFFFF8A80).copy(alpha = 0.35f), jw * 0.05f, Offset(bc.x + eyeDx + r * 0.9f, eyeY + r * 1.5f))

    // «Z-z-z» у спящей банки
    if (sleeping && !world.pouring) {
        val zc = world.time % 2.6f
        if (zc < 2.0f) {
            val zz = zc / 2.0f
            val zx = bc.x + jw * 0.45f
            val zy = bc.y - jh * 0.42f - zz * 26f * dp
            val s = (5f + zz * 5f) * dp
            val al = (1f - zz) * 0.7f
            val p = Path().apply {
                moveTo(zx, zy); lineTo(zx + s, zy); lineTo(zx, zy + s); lineTo(zx + s, zy + s)
            }
            drawPath(p, Color(0xFF8D6E63).copy(alpha = al), style = Stroke(width = 2f * dp))
            if (zz > 0.35f) {
                val s2 = s * 0.6f
                val p2 = Path().apply {
                    moveTo(zx + s * 1.4f, zy + s * 1.2f); lineTo(zx + s * 1.4f + s2, zy + s * 1.2f)
                    lineTo(zx + s * 1.4f, zy + s * 1.2f + s2); lineTo(zx + s * 1.4f + s2, zy + s * 1.2f + s2)
                }
                drawPath(p2, Color(0xFF8D6E63).copy(alpha = al * 0.7f), style = Stroke(width = 1.6f * dp))
            }
        }
    }
}

/** Падающие зёрна с глянцевым бликом. Пасхалки: редкое золотое зёрнышко
 *  сверкает искрой-крестиком; иногда вся струя становится радугой. */
private fun DrawScope.drawGrains(world: PourWorld, type: GrainType) {
    val dp = world.dp
    val rainbow = world.rainbowT >= 0f && world.pouring
    for (g in world.grains) {
        val col = when {
            g.golden -> Color(0xFFFFD54F)
            rainbow -> Color.hsv(360f * g.shade, 0.75f, 1f)
            else -> lerp(type.base, type.dark, g.shade)
        }
        val r = if (g.golden) g.r * 1.5f else g.r
        drawCircle(col, r, Offset(g.x, g.y))
        drawCircle(Color.White.copy(alpha = if (g.golden) 0.8f else 0.35f), r * 0.38f, Offset(g.x - r * 0.28f, g.y - r * 0.34f))
        if (g.golden) {
            val s = r * 1.8f
            drawLine(Color.White.copy(alpha = 0.9f), Offset(g.x - s, g.y), Offset(g.x + s, g.y), strokeWidth = 1.5f * dp)
            drawLine(Color.White.copy(alpha = 0.9f), Offset(g.x, g.y - s), Offset(g.x, g.y + s), strokeWidth = 1.5f * dp)
        }
    }
}

/** Облачка-пуфы: волна от тапа, пыль от падения крупы. */
private fun DrawScope.drawPuffs(world: PourWorld, dp: Float) {
    for (u in world.puffs) {
        val t = (u.life / 0.5f).coerceIn(0f, 1f)
        drawCircle(
            color = Color.White.copy(alpha = 0.5f * t),
            radius = (1f - t) * 26f * dp + 4f * dp,
            center = Offset(u.x, u.y),
            style = Stroke(width = 2.5f * dp)
        )
    }
}

/** Конфетти трёх форм: кружки, звёзды и сердечки — награды не повторяются. */
private fun DrawScope.drawConfetti(world: PourWorld) {
    for (c in world.confetti) {
        val a = (c.life / 1.2f).coerceIn(0f, 1f)
        when (c.shape) {
            1 -> drawPath(starPath(c.x, c.y, c.r * 2.2f), c.color.copy(alpha = 0.9f * a))
            2 -> drawPath(heartPath(c.x, c.y, c.r * 1.4f), c.color.copy(alpha = 0.9f * a))
            else -> drawCircle(c.color.copy(alpha = 0.9f * a), c.r, Offset(c.x, c.y))
        }
    }
}

/** Большая звезда, выпрыгивающая над целевой банкой. */
private fun DrawScope.drawStarBurst(world: PourWorld, dp: Float) {
    if (world.starPhase < 0f || world.starPhase > 1.1f || world.starAt < 0) return
    val t = (world.starPhase / 1.1f).coerceIn(0f, 1f)
    val cx = world.baseCx(world.starAt)
    val cy = world.floorY - world.jh - 46f * dp
    val scale = 0.55f + 0.9f * sin(t * PI.toFloat())
    val alpha = (1f - t).coerceIn(0f, 1f)
    val p = starPath(cx, cy, 26f * dp * scale)
    drawPath(p, Color(0xFFFFC107).copy(alpha = alpha))
    drawPath(p, Color(0xFFBF8F00).copy(alpha = 0.8f * alpha), style = Stroke(width = 2.5f * dp))
}

/** Пульсирующее кольцо вокруг банки, по которой нужно тапнуть. */
private fun DrawScope.drawHintRing(world: PourWorld, i: Int, dp: Float) {
    val pulse = sin(world.time * 4f)
    val c = if (world.lifted == i) world.center(i) else Offset(world.baseCx(i), world.baseCy())
    drawCircle(
        color = Color(0xFFFF7043).copy(alpha = 0.20f + 0.10f * pulse),
        radius = world.jw * (0.92f + 0.06f * pulse),
        center = c,
        style = Stroke(width = 3f * dp)
    )
}

/** Прыгающая стрелка-подсказка (без слов). */
private fun DrawScope.drawHintArrow(world: PourWorld, i: Int, dp: Float) {
    val bounce = sin(world.time * 5.5f) * 9f * dp
    val ax: Float
    val ay: Float
    if (world.lifted == i) {
        val c = world.center(i)
        ax = c.x; ay = c.y - world.jh * 0.62f - 14f * dp
    } else {
        ax = world.baseCx(i); ay = world.floorY - world.jh - 26f * dp
    }
    val tipY = ay + bounce + 22f * dp
    val baseY = ay + bounce - 6f * dp
    val p = Path().apply {
        moveTo(ax, tipY)
        lineTo(ax - 15f * dp, baseY)
        lineTo(ax + 15f * dp, baseY)
        close()
    }
    drawPath(p, Color(0xFFFF7043))
    drawPath(p, Color(0xFFBF360C), style = Stroke(width = 2.5f * dp))
}

// ============================================================================
// Экран
// ============================================================================

@Composable
fun GrainPouringScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val dp = LocalDensity.current.density
    val world = remember { PourWorld(dp) }
    var stars by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var typeIdx by remember { mutableIntStateOf(0) }
    // Вечер/ночь выбираются автоматически по часам (19:00–7:00),
    // кнопка внизу слева переключает вручную.
    var night by remember { mutableStateOf(isNightNow()) }
    var loopHandle by remember { mutableStateOf<LoopHandle?>(null) }

    fun stopPour() {
        loopHandle?.let { soundManager.stopLoop(it) }
        loopHandle = null
    }

    LaunchedEffect(night) { world.nightTarget = night }

    world.onEvent = { ev ->
        // ── Единая точка аналитики ─────────────────────────────────────────
        // КАЖДОЕ действие ребёнка проходит через этот callback. Подключите
        // ваш сбор метрик одной строкой, например:
        //     repository.logEvent("grain_action", mapOf(
        //         "action" to ev,          // lift/lower/empty/pourStart/done/milestone/nightOn/nightOff
        //                                 // + treasure/rainbow/butterfly/firefly/goldReward/sun/moon/cloud/heapPoke
        //         "grain" to typeIdx,      // какая крупа выбрана
        //         "stars" to stars,         // прогресс сессии
        //     ))
        // Время на экране уже накоплено в world.time (секунды сессии).
        // ──────────────────────────────────────────────────────────────────
        when (ev) {
            "lift" -> { world.everLifted++; soundPlayer.playPop(1.1f); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            "lower" -> { stopPour(); soundPlayer.playPop(0.8f) }
            "empty" -> soundManager.play("boing", volume = 0.5f)
            "pourStart" -> { stopPour(); loopHandle = soundManager.startLoop("grain_pour") }
            "done" -> { stopPour(); stars++; soundPlayer.playChime(1.2f); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            "milestone" -> { soundPlayer.playPop(1.5f); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            "nightOn" -> { night = true; soundPlayer.playPop(0.9f) }
            "nightOff" -> { night = false; soundPlayer.playPop(1.3f) }
            // ── v5: пасхалки, «сочность» и мир-который-отвечает ──────────
            "treasure" -> { soundPlayer.playChime(1.7f); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            "rainbow" -> soundPlayer.playChime(1.3f)
            "butterfly" -> { soundPlayer.playPop(1.6f); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            "firefly" -> soundPlayer.playPop(1.1f)
            "goldReward" -> { stopPour(); stars++; soundPlayer.playChime(1.9f); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            "sun" -> soundPlayer.playPop(1.35f)
            "moon" -> soundPlayer.playPop(0.85f)
            "cloud" -> soundManager.play("boing", volume = 0.35f)
            "heapPoke" -> soundManager.play("boing", volume = 0.4f)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            world.pouring = false
            loopHandle?.let { soundManager.stopLoop(it, 0) }
            loopHandle = null
        }
    }

    // Счётчик звёзд «пружинит» при начислении.
    val starPop by animateFloatAsState(
        targetValue = stars.toFloat(),
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 260f),
        label = "starPop"
    )
    val starScale = 1f + 0.45f * (stars.toFloat() - starPop).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF8E1)) // подложка до первого кадра; дальше рисует Canvas
            .testTag("grain_pouring_root")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { world.tap(it) } }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { world.dragStart(it) },
                        onDragEnd = { world.dragEnd() },
                        onDragCancel = { world.dragEnd() },
                        onDrag = { _, d -> world.dragBy(d.x, d.y) }
                    )
                }
        ) {
            if (world.w != size.width || world.h != size.height) world.layout(size.width, size.height)
            drawScene(world, tick)
        }

        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(88.dp))
            for ((idx, t) in GRAIN_TYPES.withIndex()) {
                val selected = idx == typeIdx
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(t.base)
                        .border(2.dp, Color.White, CircleShape)
                        .border(
                            if (selected) 4.dp else 2.dp,
                            if (selected) Color(0xFFFF7043) else Color(0xFFD7CCC8),
                            CircleShape
                        )
                        .clickable {
                            typeIdx = idx
                            stopPour()
                            world.reset(idx)
                            soundPlayer.playPop(1.2f)
                        },
                    contentAlignment = Alignment.Center
                ) { Text(t.emoji, fontSize = 22.sp) }
            }
            Spacer(Modifier.weight(1f))
            Surface(
                modifier = Modifier.scale(starScale),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.9f),
                shadowElevation = 6.dp
            ) {
                Text(
                    "⭐ $stars",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5D4037)
                )
            }
        }

        // Переключатель «день/ночь» — уголок внизу слева, вне зоны банок.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 14.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.75f))
                .border(
                    2.dp,
                    if (night) Color(0xFF7986CB) else Color(0xFFFFE082),
                    CircleShape
                )
                .clickable { world.onEvent(if (night) "nightOff" else "nightOn") },
            contentAlignment = Alignment.Center
        ) { Text(if (night) "☀️" else "🌙", fontSize = 20.sp) }
    }

    LaunchedEffect(world) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) world.step((now - last) / 1_000_000_000f)
                last = now
                tick++
            }
        }
    }
}
