package com.example.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.SoundManager
import com.example.shared.rememberHaptics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class SmeshinkiSkin(val title: String, val emoji: String, val bodyColor: Color, val secondaryColor: Color, val blushColor: Color) {
    PELMEN("Пельмешек", "🥟", Color(0xFFFFFDF5), Color(0xFFF3EED9), Color(0xFFFF8595)),
    KOLOBOK("Колобок", "🟡", Color(0xFFFFD54F), Color(0xFFFFA000), Color(0xFFFF6E40)),
    ZEFIRKA("Зефирка", "🍥", Color(0xFFFFE4EC), Color(0xFFFFB2C9), Color(0xFFFF4081)),
    KITTEN("Котик", "🐱", Color(0xFFFFC98B), Color(0xFFE08A3C), Color(0xFFFF7F9A))
}

/**
 * «Смешинки» — живой смешной персонаж.
 *
 * Тап по частям тела запускает разные смешные реакции (чих, надувание живота, язык, вертушка,
 * падение на попу, икота, прыжок, глаза на стебельках, поцелуй, смех до слёз, наряды,
 * испуг, пузыри, радуга). Водишь пальцем по телу — щекотка. Тап по фону — вылетают стикеры.
 * Серия быстрых тыков — праздник с танцами и конфетти. Если не трогать — персонаж засыпает.
 * Звуки короткие, предзагруженные; одновременно их звучит ограниченное число.
 */

private class Sp(var x: Float, val k: Float, val c: Float) {
    var target = x
    var v = 0f
    fun step(dt: Float) {
        v += ((target - x) * k - v * c) * dt
        x += v * dt
    }
}

private class Todo(val at: Float, val f: () -> Unit)

private class SmPart(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float, val kind: Int, val color: Color, val size: Float,
    val text: String = "", val grav: Float = 900f
)

private val SM_STICKERS = listOf("🎈", "⭐", "🦋", "🍭", "🌈", "🐥", "🎵", "🍓", "🦄", "🌸", "🚀", "🍦", "🐞", "💎")
private val SM_COLORS = listOf(Color(0xFFFFD54F), Color(0xFFFF80AB), Color(0xFF80DEEA), Color(0xFFB9F6CA), Color(0xFFFFAB91), Color.White)

private const val ACC_COUNT = 6

private class SmWorld(val dp: Float) {
    var w = 0f
    var h = 0f
    var time = 0f
    var fxT = 0f
    val parts = ArrayList<SmPart>()
    val todos = ArrayList<Todo>()

    val sq = Sp(0f, 260f, 12f)
    val puff = Sp(0f, 140f, 8f)
    val rot = Sp(0f, 120f, 8f)
    val fall = Sp(0f, 200f, 11f)
    val tongue = Sp(0f, 160f, 9f)
    val eyePop = Sp(0f, 170f, 7f)
    val blush = Sp(0f, 60f, 10f)
    val accS = Array(ACC_COUNT) { Sp(0f, 220f, 9f) }
    val acc = BooleanArray(ACC_COUNT)

    var oy = 0f
    var vy = 0f
    var airborne = false
    var spinT = -1f
    var eyes = 0       // 0 обычные, 1 зажмурены >_<, 2 спирали, 3 косые, 4 радостные ^^, 5 круглые, 6 спит
    var mouth = 0      // 0 улыбка, 1 смех, 3 «О», 4 поцелуй, 5 зевок, 6 сон
    var blinkT = 2f
    var blinkOn = 0f
    var lookX = 0f
    var lookY = 0f
    var lookTX = 0f
    var lookTY = 0f
    var lookHold = 0f
    var idleT = 0f
    var sleeping = false
    var snoreT = 1f
    var partyT = 0f
    var partyHop = 0f
    var partyNote = 0f
    var tintT = 0f
    var lastId = -1
    var combo = 0
    var comboGap = 0f
    var tickleT = 0f
    var tickleSfx = 0f
    var tickleHeart = 0f
    var skinIdx = 0
    var taps = 0

    var sfx: (String, Float) -> Unit = { _, _ -> }
    var haptic: (Int) -> Unit = {}
    var onTapCount: (Int) -> Unit = {}

    val R get() = min(w, h) * 0.27f
    val cx get() = w / 2f
    val cy get() = h * 0.58f

    fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

    fun later(t: Float, f: () -> Unit) { todos.add(Todo(fxT + t, f)) }

    fun calm() {
        eyes = 0; mouth = 0
        tongue.target = 0f; eyePop.target = 0f; puff.target = 0f
        sq.target = 0f; rot.target = 0f; fall.target = 0f; blush.target = 0f
    }

    fun resetFx() {
        todos.clear()
        calm()
        spinT = -1f
        fxT = 0f
    }

    fun burst(x: Float, y: Float, n: Int, speed: Float = 320f, colors: List<Color> = SM_COLORS) {
        repeat(n) { i ->
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val s = (Random.nextFloat() * 0.7f + 0.4f) * speed * dp
            parts.add(
                SmPart(x, y, cos(a) * s, sin(a) * s - 80f * dp, 1f, 1f, if (i % 2 == 0) 0 else 1,
                    colors.random(), rnd(5f, 11f) * dp)
            )
        }
    }

    fun emoji(x: Float, y: Float, text: String, vx: Float, vy: Float, life: Float, size: Float, grav: Float = 0f) {
        parts.add(SmPart(x, y, vx * dp, vy * dp, life, life, 3, Color.White, size * dp, text, grav * dp))
    }

    fun hop(strength: Float) {
        airborne = true
        vy = -strength
        sq.x = -0.22f; sq.v = 0f
    }

    private fun landed() {
        sq.x = 0.3f; sq.v = 0f
        val by = cy + R * 1.0f
        repeat(6) {
            val dir = if (it % 2 == 0) 1f else -1f
            parts.add(SmPart(cx + dir * R * 0.5f, by, dir * rnd(80f, 220f) * dp, rnd(-120f, -30f) * dp, 0.5f, 0.5f, 1,
                Color.White.copy(alpha = 0.8f), rnd(5f, 9f) * dp, grav = 200f * dp))
        }
    }

    // ---------- реакции ----------
    fun react(id: Int) {
        resetFx()
        lastId = id
        idleT = 0f
        sleeping = false
        val headY = cy + oy - R * 0.9f
        val mouthY = cy + oy + R * 0.2f
        when (id) {
            0 -> { // чих
                eyes = 1; mouth = 3; rot.target = -9f; puff.target = 0.14f
                sfx("sm_sneeze", 1f)
                later(0.42f) {
                    rot.target = 16f; rot.v = 220f; puff.target = 0f; sq.v = -5f; mouth = 1
                    burst(cx, cy + oy - R * 0.05f, 16)
                    repeat(10) {
                        parts.add(SmPart(cx + R * 0.1f, cy + oy, rnd(380f, 700f) * dp, rnd(-90f, 90f) * dp, 0.55f, 0.55f, 1,
                            Color.White, rnd(5f, 10f) * dp, grav = 0f))
                    }
                    haptic(1)
                }
                later(1.1f) { calm() }
            }
            1 -> { // надувание живота
                eyes = 5; mouth = 3; puff.target = 0.5f
                sfx("sm_inflate", 1f)
                later(0.55f) {
                    sfx("sm_pop", 1f)
                    puff.x = -0.14f; puff.target = 0f; puff.v = 0f
                    eyes = 4; mouth = 1
                    burst(cx, cy + oy, 22, 380f)
                    haptic(1)
                }
                later(0.75f) { sfx("sm_giggle", 1f) }
                later(1.5f) { calm() }
            }
            2 -> { // язык
                eyes = 3; tongue.target = 1f; rot.target = 8f
                sfx("sm_blah", 1f)
                later(0.45f) { rot.target = -8f }
                later(1.0f) { calm() }
            }
            3 -> { // вертушка
                eyes = 2; mouth = 3; spinT = 0f
                sfx("sm_whirl", 1f)
                for (i in 0..5) later(i * 0.13f) {
                    val a = i * 1.05f
                    parts.add(SmPart(cx + cos(a) * R * 0.6f, headY + sin(a) * R * 0.2f, cos(a) * 120f * dp, -140f * dp, 0.8f, 0.8f, 0,
                        SM_COLORS.random(), 11f * dp, grav = 100f * dp))
                }
                later(0.9f) { rot.v = 320f; mouth = 1 }
                later(1.6f) { calm() }
            }
            4 -> { // падение на попу
                rot.target = -14f; fall.target = R * 0.3f; sq.target = 0.28f; eyes = 5; mouth = 3
                later(0.2f) {
                    sfx("sm_thud", 1f)
                    haptic(1)
                    landed()
                    eyes = 2; mouth = 1
                    for (i in 0..4) parts.add(SmPart(cx + rnd(-0.5f, 0.5f) * R, headY, rnd(-60f, 60f) * dp, rnd(-200f, -80f) * dp, 0.9f, 0.9f, 0,
                        SM_COLORS.random(), 10f * dp, grav = 150f * dp))
                }
                later(0.55f) { sfx("sm_giggle", 1f); eyes = 4 }
                later(1.1f) { fall.target = 0f; sq.target = 0f; rot.target = 0f; sq.v = -4f; hop(R * 3.2f); eyes = 0; mouth = 0 }
                later(1.7f) { calm() }
            }
            5 -> { // икота
                for (i in 0..3) later(i * 0.27f) {
                    sfx("sm_hic", 1f + i * 0.05f)
                    hop(R * 2.4f)
                    mouth = 3
                    repeat(3) {
                        emoji(cx + rnd(-10f, 10f) * dp, mouthY, "🫧", rnd(-40f, 40f), rnd(-220f, -120f), 1.0f, rnd(18f, 28f), -40f)
                    }
                    haptic(1)
                }
                later(1.2f) { sfx("sm_giggle", 1.1f); mouth = 1; eyes = 4 }
                later(1.9f) { calm() }
            }
            6 -> { // прыжок
                mouth = 1; eyes = 5
                hop(R * 5.6f)
                sfx("sm_wee", 1f)
                later(0.36f) {
                    burst(cx, cy + oy - R * 0.3f, 18, 340f)
                    eyes = 4
                }
                later(0.85f) { sfx("sm_thud", 0.8f); }
                later(1.3f) { calm() }
            }
            7 -> { // глаза на стебельках
                sfx("sm_boing", 1f)
                eyePop.target = 1f; eyes = 3; mouth = 3
                later(1.0f) { sfx("sm_pop", 1f); eyePop.target = 0f; eyes = 5 }
                later(1.5f) { calm() }
            }
            8 -> { // поцелуй
                mouth = 4; eyes = 4; blush.target = 1f
                later(0.12f) {
                    sfx("sm_smooch", 1f)
                    repeat(12) {
                        emoji(cx + rnd(-0.4f, 0.4f) * R, mouthY, if (it % 3 == 0) "💖" else "💗", rnd(-120f, 120f), rnd(-420f, -220f), 1.6f, rnd(26f, 42f), 60f)
                    }
                    haptic(1)
                }
                later(1.6f) { calm() }
            }
            9 -> { // смех до слёз
                eyes = 4; mouth = 1
                sfx("sm_giggle", 1f)
                for (i in 0..6) later(i * 0.12f) {
                    rot.target = if (i % 2 == 0) 9f else -9f
                    sq.v = -3f
                }
                for (i in 0..2) later(0.1f + i * 0.22f) {
                    repeat(6) {
                        val dir = if (it % 2 == 0) 1f else -1f
                        parts.add(SmPart(cx + dir * R * 0.32f, cy + oy - R * 0.1f, dir * rnd(150f, 360f) * dp, rnd(-260f, -120f) * dp,
                            0.9f, 0.9f, 4, Color(0xFF4FC3F7), rnd(5f, 8f) * dp, grav = 800f * dp))
                    }
                }
                later(1.2f) { sfx("sm_giggle", 1.2f); }
                later(1.7f) { calm() }
            }
            10 -> { // наряд
                var idx = -1
                for (k in 0 until ACC_COUNT) if (!acc[k]) { idx = k; break }
                if (idx == -1) {
                    for (k in 0 until ACC_COUNT) { acc[k] = false; accS[k].x = 0f; accS[k].target = 0f }
                    sfx("sm_pop", 0.8f)
                    burst(cx, headY, 20, 360f, listOf(Color.White, Color(0xFFCFD8DC)))
                    eyes = 5
                } else {
                    acc[idx] = true
                    accS[idx].x = 0f; accS[idx].v = 0f; accS[idx].target = 1f
                    sfx("sm_tada", 1f)
                    burst(cx, headY, 16, 300f)
                    hop(R * 2.2f)
                    eyes = 4; mouth = 1
                    haptic(1)
                }
                later(1.2f) { calm() }
            }
            11 -> { // танцы
                partyT = 3.0f; partyHop = 0f; partyNote = 0f
                sfx("sm_party", 1f)
                eyes = 4; mouth = 1
                burst(cx, headY, 30, 420f)
                haptic(1)
            }
            12 -> { // испуг
                sfx("sm_gasp", 1f)
                hop(R * 2.8f)
                eyes = 5; mouth = 3; rot.target = -6f
                emoji(cx, headY - R * 0.1f, "❗", 0f, -60f, 1.2f, 54f)
                repeat(5) {
                    parts.add(SmPart(cx + rnd(-0.6f, 0.6f) * R, headY + R * 0.2f, rnd(-120f, 120f) * dp, rnd(-200f, -60f) * dp, 0.8f, 0.8f, 4,
                        Color(0xFF81D4FA), rnd(5f, 8f) * dp, grav = 700f * dp))
                }
                later(0.3f) { sfx("sm_oops", 1f) }
                later(1.1f) { calm() }
            }
            13 -> { // пузыри
                mouth = 3; eyes = 4
                for (i in 0..5) later(i * 0.16f) {
                    sfx("sm_bubble", 0.9f + i * 0.08f)
                    repeat(2) {
                        parts.add(SmPart(cx + rnd(-8f, 8f) * dp, mouthY, rnd(-60f, 60f) * dp, rnd(-260f, -120f) * dp, rnd(1.8f, 2.8f), 2.8f, 5,
                            Color(0xFF80DEEA), rnd(14f, 30f) * dp, grav = -20f * dp))
                    }
                }
                later(1.4f) { calm() }
            }
            14 -> { // радуга
                tintT = 1.4f
                eyes = 4; mouth = 1
                sfx("sm_note1", 1f)
                later(0.14f) { sfx("sm_note2", 1f) }
                later(0.28f) { sfx("sm_note3", 1f) }
                burst(cx, cy + oy, 26, 360f, listOf(Color.Red, Color(0xFFFF9100), Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta))
                later(1.5f) { calm() }
            }
        }
    }

    fun pickFrom(list: List<Int>): Int {
        val opts = list.filter { it != lastId }
        return (if (opts.isEmpty()) list else opts).random()
    }

    fun tap(x: Float, y: Float) {
        idleT = 0f
        comboGap = 0f
        combo++
        taps++
        onTapCount(taps)
        lookTX = ((x - cx) / R).coerceIn(-1f, 1f); lookTY = ((y - cy) / R).coerceIn(-1f, 1f); lookHold = 1.5f
        val dx = (x - cx) / R
        val dy = (y - (cy + oy)) / R
        val over = dx * dx + (dy / 1.05f) * (dy / 1.05f) <= 1.2f
        if (!over) {
            emoji(x, y, SM_STICKERS.random(), rnd(-60f, 60f), rnd(-420f, -260f), 1.8f, rnd(40f, 64f), 40f)
            burst(x, y, 8, 240f)
            sfx("sm_pop", rnd(0.85f, 1.25f))
            haptic(0)
            sq.v = 2f
            return
        }
        if (sleeping) { react(12); return }
        if (combo >= 8) {
            combo = 0
            react(11)
            return
        }
        val ax = abs(dx)
        val id = when {
            dy < -0.55f -> pickFrom(listOf(3, 10, 6, 14))
            dy in -0.5f..-0.08f && ax in 0.15f..0.65f -> pickFrom(listOf(7, 9, 12))
            ax < 0.17f && dy in -0.3f..0.1f -> pickFrom(listOf(0, 13, 12))
            ax > 0.4f && dy in -0.1f..0.4f -> pickFrom(listOf(2, 8, 9))
            dy > 0.7f -> pickFrom(listOf(4, 6, 5))
            else -> pickFrom(listOf(1, 5, 1, 6, 14))
        }
        react(id)
    }

    fun pointerDown(x: Float, y: Float) {
        idleT = 0f
        lookTX = ((x - cx) / R).coerceIn(-1f, 1f); lookTY = ((y - cy) / R).coerceIn(-1f, 1f); lookHold = 1.2f
    }

    fun drag(x: Float, y: Float) {
        idleT = 0f
        lookTX = ((x - cx) / R).coerceIn(-1f, 1f); lookTY = ((y - cy) / R).coerceIn(-1f, 1f); lookHold = 1.2f
        val dx = (x - cx) / R
        val dy = (y - (cy + oy)) / R
        val over = dx * dx + (dy / 1.05f) * (dy / 1.05f) <= 1.3f
        if (over && !sleeping && fxT > 0.8f && partyT <= 0f) {
            if (tickleT <= 0f) { eyes = 4; mouth = 1; blush.target = 0.7f }
            tickleT = 0.35f
            if (time - tickleSfx > 0.17f) {
                tickleSfx = time
                sfx("sm_tickle", rnd(0.9f, 1.35f))
                sq.v += rnd(-3f, 3f)
                rot.target = if (rot.target > 0f) -7f else 7f
            }
            if (time - tickleHeart > 0.35f) {
                tickleHeart = time
                emoji(x, y, listOf("😆", "✨", "💕").random(), rnd(-60f, 60f), -200f, 1.0f, 36f, 0f)
            }
        } else if (!over) {
            parts.add(SmPart(x, y, rnd(-40f, 40f) * dp, rnd(-40f, 40f) * dp, 0.7f, 0.7f, 0,
                Color.hsv((time * 220f) % 360f, 0.55f, 1f), rnd(7f, 13f) * dp, grav = 150f * dp))
        }
    }

    fun pointerUp() {
        if (sleeping) return
    }

    fun skinChanged() {
        sq.v = 4f
        burst(cx, cy + oy, 14, 300f)
    }

    fun step(dtRaw: Float) {
        if (w <= 0f) return
        val dt = dtRaw.coerceIn(0.001f, 0.025f)
        time += dt
        fxT += dt

        if (todos.isNotEmpty()) {
            val due = todos.filter { it.at <= fxT }
            if (due.isNotEmpty()) {
                todos.removeAll(due.toSet())
                for (t in due) t.f()
            }
        }

        sq.step(dt); puff.step(dt); rot.step(dt); fall.step(dt)
        tongue.step(dt); eyePop.step(dt); blush.step(dt)
        for (a in accS) a.step(dt)

        if (airborne) {
            vy += R * 15f * dt
            oy += vy * dt
            if (oy >= 0f) {
                val hard = vy > R * 2f
                oy = 0f; vy = 0f; airborne = false
                if (hard) landed()
            }
        }
        if (spinT >= 0f) {
            spinT += dt / 0.85f
            if (spinT >= 1f) spinT = -1f
        }
        if (tintT > 0f) tintT -= dt

        // взгляд
        lookHold -= dt
        if (lookHold <= 0f) { lookTX = 0f; lookTY = 0f }
        val kk = min(1f, dt * 9f)
        lookX += (lookTX - lookX) * kk
        lookY += (lookTY - lookY) * kk

        // моргание
        blinkT -= dt
        if (blinkT <= 0f) { blinkOn = 0.13f; blinkT = rnd(2f, 4.5f) }
        if (blinkOn > 0f) blinkOn -= dt

        // щекотка
        if (tickleT > 0f) {
            tickleT -= dt
            if (tickleT <= 0f) { calm() }
        }

        // комбо
        comboGap += dt
        if (comboGap > 1.3f) combo = 0

        // праздник
        if (partyT > 0f) {
            partyT -= dt
            rot.target = sin(time * 9f) * 9f
            if (!airborne) {
                partyHop -= dt
                if (partyHop <= 0f) { hop(R * 2.3f); partyHop = 0.34f }
            }
            partyNote -= dt
            if (partyNote <= 0f) {
                partyNote = 0.22f
                emoji(cx + rnd(-1.1f, 1.1f) * R, cy - R * 0.6f, listOf("🎵", "🎶", "🎉", "✨").random(), rnd(-70f, 70f), rnd(-260f, -140f), 1.4f, rnd(30f, 48f), 40f)
            }
            repeat(2) {
                parts.add(SmPart(rnd(0f, w), -10f * dp, rnd(-40f, 40f) * dp, rnd(60f, 160f) * dp, 3f, 3f, 1,
                    Color.hsv(Random.nextFloat() * 360f, 0.7f, 1f), rnd(5f, 9f) * dp, grav = 90f * dp))
            }
            if (partyT <= 0f) { calm(); eyes = 0 }
        }

        // сон
        idleT += dt
        if (!sleeping && idleT > 9f && fxT > 1.5f && partyT <= 0f) {
            sleeping = true
            sfx("sm_yawn", 1f)
            eyes = 6; mouth = 5
            snoreT = 1.4f
        }
        if (sleeping) {
            if (mouth == 5 && idleT > 10.2f) mouth = 6
            snoreT -= dt
            if (snoreT <= 0f) {
                snoreT = 3.2f
                sfx("sm_snore", 0.5f)
                emoji(cx + R * 0.5f, cy - R * 0.9f, "💤", 30f, -90f, 2.0f, 40f, 0f)
            }
        }

        // частицы
        val it = parts.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            p.vy += p.grav * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.kind == 5) p.x += sin(time * 5f + p.size) * 22f * dp * dt
            if (p.life <= 0f || p.y > h + 80f) it.remove()
        }
        if (parts.size > 260) repeat(parts.size - 260) { parts.removeAt(0) }
    }
}

private fun ease(p: Float): Float = if (p < 0.5f) 4f * p * p * p else 1f - Math.pow((-2.0 * p + 2.0), 3.0).toFloat() / 2f

@Composable
fun SmeshinkiScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val dp = LocalDensity.current.density
    val world = remember { SmWorld(dp) }
    var tick by remember { mutableIntStateOf(0) }
    var taps by remember { mutableIntStateOf(0) }
    var skin by remember { mutableStateOf(SmeshinkiSkin.PELMEN) }
    val recent = remember { ArrayList<Long>() }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }

    LaunchedEffect(Unit) {
        soundManager.preloadRaw(
            listOf("sm_sneeze", "sm_inflate", "sm_pop", "sm_blah", "sm_whirl", "sm_thud", "sm_hic", "sm_wee", "sm_boing",
                "sm_smooch", "sm_giggle", "sm_gasp", "sm_tada", "sm_note1", "sm_note2", "sm_note3", "sm_bubble",
                "sm_tickle", "sm_yawn", "sm_snore", "sm_party", "sm_honk", "sm_oops")
        )
    }

    world.sfx = { name, rate ->
        // не больше 4 звуков за 0,6 секунды — быстрые тапы не копят «хвост» из звуков
        val now = System.currentTimeMillis()
        recent.removeAll { now - it > 600L }
        if (recent.size < 4) {
            recent.add(now)
            soundManager.play(name, volume = 0.9f, rate = rate)
        }
    }
    world.haptic = { k -> if (k == 0) haptics.vibrateShort() else haptics.vibratePop() }
    world.onTapCount = { taps = it }

    DisposableEffect(Unit) {
        onDispose { world.todos.clear() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(skin.secondaryColor.copy(alpha = 0.35f), skin.bodyColor.copy(alpha = 0.9f), Color(0xFFFFF3E0))))
            .testTag("smeshinki_root")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("smeshinki_character_canvas")
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slop = viewConfiguration.touchSlop * 1.5f
                        world.pointerDown(down.position.x, down.position.y)
                        var last = down.position
                        var travel = 0f
                        var moved = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: break
                            if (!ch.pressed) break
                            travel += (ch.position - last).getDistance()
                            last = ch.position
                            if (travel > slop) {
                                moved = true
                                world.drag(ch.position.x, ch.position.y)
                            }
                            ch.consume()
                        }
                        if (!moved) world.tap(down.position.x, down.position.y) else world.pointerUp()
                    }
                }
        ) {
            if (world.w != size.width || world.h != size.height) { world.w = size.width; world.h = size.height }
            @Suppress("UNUSED_VARIABLE") val redraw = tick
            drawSmeshinki(world, skin, paint)
        }

        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(88.dp))
            for (s in SmeshinkiSkin.values()) {
                val selected = s == skin
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(s.bodyColor)
                        .border(if (selected) 4.dp else 2.dp, if (selected) Color(0xFFFF7043) else Color.White, CircleShape)
                        .clickable {
                            skin = s
                            world.skinChanged()
                            soundManager.play("sm_pop", volume = 0.9f, rate = 1.2f)
                            haptics.vibratePop()
                        }
                        .testTag("skin_switcher_btn"),
                    contentAlignment = Alignment.Center
                ) { Text(s.emoji, fontSize = 22.sp) }
            }
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.85f),
                shadowElevation = 4.dp
            ) {
                Text(
                    "😜 $taps",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5D4037)
                )
            }
        }
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

private fun DrawScope.drawSmeshinki(world: SmWorld, skin: SmeshinkiSkin, paint: Paint) {
    val dp = world.dp
    val cx = world.cx
    val R = world.R
    val baseCy = world.cy

    // фон: пузырьки-«боке»
    for (i in 0 until 9) {
        val bx = ((i * 0.13f + 0.05f) % 1f) * size.width
        val by = size.height * (0.15f + ((i * 0.37f) % 0.7f)) + sin(world.time * 0.6f + i) * 14f * dp
        drawCircle(Color.White.copy(alpha = 0.22f), (18f + (i % 4) * 12f) * dp, Offset(bx, by))
    }
    // праздничная вспышка фона
    if (world.partyT > 0f) {
        drawRect(Color.hsv((world.time * 160f) % 360f, 0.5f, 1f).copy(alpha = 0.22f))
    }

    // пол и тень
    val floorY = baseCy + R * 1.02f
    drawRect(Color.Black.copy(alpha = 0.05f), Offset(0f, floorY), Size(size.width, size.height - floorY))
    val lift = (-world.oy / (R * 1.5f)).coerceIn(0f, 1f)
    val shW = R * 1.5f * (1f - lift * 0.4f)
    drawOval(Color.Black.copy(alpha = 0.16f * (1f - lift * 0.5f)), Offset(cx - shW / 2f, floorY - 10f * dp), Size(shW, 24f * dp))

    val sx0 = 1f + world.sq.x + world.puff.x
    val sy0 = (1f - world.sq.x * 0.9f + world.puff.x * 0.8f).coerceIn(0.5f, 1.8f)
    val breathe = 1f + sin(world.time * 2.2f) * 0.015f
    val sx = sx0.coerceIn(0.6f, 2.0f) * breathe
    val sy = sy0 * (2f - breathe)
    val spin = if (world.spinT >= 0f) 720f * ease(world.spinT.coerceIn(0f, 1f)) else 0f
    val angle = world.rot.x + spin
    val ccy = baseCy + world.oy + world.fall.x
    val pivot = Offset(cx, baseCy + R * 1.02f + world.fall.x + world.oy)

    withTransform({
        scale(sx, sy, pivot)
        rotate(angle, Offset(cx, ccy))
    }) {
        drawCharacter(world, skin, cx, ccy, R, dp)
    }

    // частицы
    for (p in world.parts) {
        val a = (p.life / p.maxLife * 1.6f).coerceIn(0f, 1f)
        when (p.kind) {
            0 -> drawSmStar(Offset(p.x, p.y), p.size, p.color.copy(alpha = a))
            1, 4 -> drawCircle(p.color.copy(alpha = a), p.size / 2f, Offset(p.x, p.y))
            5 -> {
                drawCircle(p.color.copy(alpha = 0.25f * a), p.size, Offset(p.x, p.y))
                drawCircle(p.color.copy(alpha = a), p.size, Offset(p.x, p.y), style = Stroke(2.5f * dp))
                drawCircle(Color.White.copy(alpha = 0.8f * a), p.size * 0.22f, Offset(p.x - p.size * 0.4f, p.y - p.size * 0.4f))
            }
            3 -> {
                paint.textSize = p.size
                paint.alpha = (a * 255).toInt()
                drawContext.canvas.nativeCanvas.drawText(p.text, p.x, p.y, paint)
            }
        }
    }

    // подсказка «нажми» — пульсирующее колечко, пока ещё не тыкали
    if (world.taps == 0) {
        val pr = R * (0.18f + 0.06f * sin(world.time * 4f).let { abs(it) })
        drawCircle(Color(0xFFFF7043).copy(alpha = 0.55f), pr, Offset(cx, baseCy + R * 0.3f), style = Stroke(5f * dp))
    }
}

private fun DrawScope.drawCharacter(world: SmWorld, skin: SmeshinkiSkin, cx: Float, cy: Float, R: Float, dp: Float) {
    val rX = R
    val rY = R * 1.02f

    // уши котика
    if (skin == SmeshinkiSkin.KITTEN) {
        for (side in listOf(-1f, 1f)) {
            val ear = Path().apply {
                moveTo(cx + side * rX * 0.25f, cy - rY * 0.88f)
                lineTo(cx + side * rX * 0.78f, cy - rY * 1.28f)
                lineTo(cx + side * rX * 0.92f, cy - rY * 0.45f)
                close()
            }
            drawPath(ear, skin.secondaryColor)
            val inner = Path().apply {
                moveTo(cx + side * rX * 0.38f, cy - rY * 0.88f)
                lineTo(cx + side * rX * 0.74f, cy - rY * 1.1f)
                lineTo(cx + side * rX * 0.82f, cy - rY * 0.58f)
                close()
            }
            drawPath(inner, skin.blushColor.copy(alpha = 0.7f))
        }
    }

    // тело
    drawOval(
        Brush.radialGradient(
            listOf(Color.White, skin.bodyColor, skin.secondaryColor),
            center = Offset(cx - rX * 0.25f, cy - rY * 0.25f), radius = rX * 1.3f
        ),
        Offset(cx - rX, cy - rY), Size(rX * 2f, rY * 2f)
    )
    if (skin == SmeshinkiSkin.KITTEN) {
        for (i in -1..1) {
            drawLine(skin.secondaryColor.copy(alpha = 0.8f), Offset(cx + i * rX * 0.18f, cy - rY * 0.98f),
                Offset(cx + i * rX * 0.14f, cy - rY * 0.7f), 5f * dp, StrokeCap.Round)
        }
        for (side in listOf(-1f, 1f)) for (k in -1..1) {
            drawLine(Color(0xFF5D4037).copy(alpha = 0.6f), Offset(cx + side * rX * 0.42f, cy + rY * 0.12f + k * 8f * dp),
                Offset(cx + side * rX * 0.95f, cy + rY * 0.05f + k * 18f * dp), 2f * dp, StrokeCap.Round)
        }
    }
    if (skin == SmeshinkiSkin.PELMEN) {
        for (i in -3..3) {
            val fa = (i * 18f) * PI.toFloat() / 180f
            drawCircle(skin.secondaryColor.copy(alpha = 0.75f), 14f * dp,
                Offset(cx + sin(fa) * rX * 0.88f, cy - cos(fa) * rY * 0.88f), style = Stroke(3f * dp))
        }
    }
    drawOval(Color.Black.copy(alpha = 0.15f), Offset(cx - rX, cy - rY), Size(rX * 2f, rY * 2f), style = Stroke(3.5f * dp))

    // радужная вспышка
    if (world.tintT > 0f) {
        val a = (world.tintT / 1.4f).coerceIn(0f, 1f) * 0.55f
        drawOval(Color.hsv((world.time * 500f) % 360f, 0.8f, 1f).copy(alpha = a), Offset(cx - rX, cy - rY), Size(rX * 2f, rY * 2f))
    }

    // румяна
    val blushR = (22f + world.blush.x * 12f) * dp
    val ba = (0.5f + world.blush.x * 0.4f).coerceIn(0f, 1f)
    drawCircle(skin.blushColor.copy(alpha = ba), blushR, Offset(cx - rX * 0.55f, cy + rY * 0.12f))
    drawCircle(skin.blushColor.copy(alpha = ba), blushR, Offset(cx + rX * 0.55f, cy + rY * 0.12f))

    // глаза
    val ex = rX * 0.32f
    val ey = cy - rY * 0.18f
    val er = 22f * dp
    val pop = world.eyePop.x
    val eyeY = ey - pop * rY * 0.42f
    val eyeR = er * (1f + pop * 0.55f)
    if (pop > 0.05f) {
        for (side in listOf(-1f, 1f)) {
            drawLine(skin.secondaryColor, Offset(cx + side * ex, ey), Offset(cx + side * ex, eyeY), er * 0.5f, StrokeCap.Round)
        }
    }
    val closedBlink = world.blinkOn > 0f && world.eyes == 0
    val lk = Offset(world.lookX, world.lookY)
    for (side in listOf(-1f, 1f)) {
        val c = Offset(cx + side * ex, eyeY)
        when {
            closedBlink -> drawLine(Color(0xFF212121), Offset(c.x - eyeR * 0.7f, c.y), Offset(c.x + eyeR * 0.7f, c.y), 4f * dp, StrokeCap.Round)
            world.eyes == 1 -> {
                val a = eyeR * 0.65f
                drawLine(Color.Black, Offset(c.x - side * a, c.y - a), Offset(c.x + side * a, c.y), 5f * dp, StrokeCap.Round)
                drawLine(Color.Black, Offset(c.x - side * a, c.y + a), Offset(c.x + side * a, c.y), 5f * dp, StrokeCap.Round)
            }
            world.eyes == 2 -> drawSmSpiral(c, eyeR * 0.9f, world.time)
            world.eyes == 4 -> {
                val arc = Path().apply {
                    moveTo(c.x - eyeR * 0.8f, c.y + eyeR * 0.3f)
                    quadraticBezierTo(c.x, c.y - eyeR * 0.9f, c.x + eyeR * 0.8f, c.y + eyeR * 0.3f)
                }
                drawPath(arc, Color(0xFF212121), style = Stroke(5f * dp, cap = StrokeCap.Round))
            }
            world.eyes == 6 -> {
                val arc = Path().apply {
                    moveTo(c.x - eyeR * 0.8f, c.y - eyeR * 0.1f)
                    quadraticBezierTo(c.x, c.y + eyeR * 0.6f, c.x + eyeR * 0.8f, c.y - eyeR * 0.1f)
                }
                drawPath(arc, Color(0xFF212121), style = Stroke(4f * dp, cap = StrokeCap.Round))
            }
            world.eyes == 3 -> {
                val off = if (side < 0f) Offset(-6f * dp, -5f * dp) else Offset(-8f * dp, 7f * dp)
                drawCircle(Color.White, eyeR, c)
                drawCircle(Color.Black.copy(alpha = 0.2f), eyeR, c, style = Stroke(2.5f * dp))
                drawCircle(Color(0xFF212121), eyeR * 0.6f, c + off)
                drawCircle(Color.White, eyeR * 0.2f, c + off - Offset(3f * dp, 3f * dp))
            }
            else -> {
                val rr = if (world.eyes == 5) eyeR * 1.2f else eyeR
                val pr = if (world.eyes == 5) rr * 0.38f else rr * 0.62f
                drawCircle(Color.White, rr, c)
                drawCircle(Color.Black.copy(alpha = 0.2f), rr, c, style = Stroke(2.5f * dp))
                val pc = c + Offset(lk.x * rr * 0.32f, lk.y * rr * 0.32f)
                drawCircle(Color(0xFF212121), pr, pc)
                drawCircle(Color.White, pr * 0.38f, pc - Offset(pr * 0.3f, pr * 0.35f))
                drawCircle(Color.White, pr * 0.2f, pc + Offset(pr * 0.35f, pr * 0.3f))
            }
        }
    }

    // нос
    drawCircle(skin.blushColor, 11f * dp, Offset(cx, cy - rY * 0.02f))

    // усы-наряд будет рисоваться позже; рот
    val my = cy + rY * 0.2f
    val tg = world.tongue.x
    when {
        tg > 0.05f -> {
            drawArc(Color(0xFF6B1D2F), 0f, 180f, true, Offset(cx - 32f * dp, my - 8f * dp), Size(64f * dp, 44f * dp))
            val tl = 56f * dp * tg
            drawRoundRect(Color(0xFFFF5252), Offset(cx - 14f * dp + sin(world.time * 22f) * 5f * dp * tg, my + 10f * dp), Size(36f * dp, tl), CornerRadius(18f * dp))
        }
        world.mouth == 1 -> {
            drawArc(Color(0xFF8B263E), 0f, 180f, true, Offset(cx - 36f * dp, my - 12f * dp), Size(72f * dp, 50f * dp))
            drawCircle(Color(0xFFFF5252), 15f * dp, Offset(cx, my + 28f * dp))
        }
        world.mouth == 3 -> {
            drawOval(Color(0xFF6B1D2F), Offset(cx - 14f * dp, my - 4f * dp), Size(28f * dp, 34f * dp))
        }
        world.mouth == 4 -> {
            drawCircle(Color(0xFFE53935), 12f * dp, Offset(cx, my + 14f * dp))
            drawCircle(Color(0xFFB71C1C), 5f * dp, Offset(cx, my + 14f * dp))
        }
        world.mouth == 5 -> {
            drawOval(Color(0xFF6B1D2F), Offset(cx - 22f * dp, my - 6f * dp), Size(44f * dp, 56f * dp))
            drawCircle(Color(0xFFFF5252), 12f * dp, Offset(cx, my + 36f * dp))
        }
        world.mouth == 6 -> {
            drawLine(Color.Black.copy(alpha = 0.8f), Offset(cx - 12f * dp, my + 14f * dp), Offset(cx + 12f * dp, my + 14f * dp), 4f * dp, StrokeCap.Round)
        }
        else -> {
            val smile = Path().apply {
                moveTo(cx - 28f * dp, my + 8f * dp)
                quadraticBezierTo(cx, my + 30f * dp, cx + 28f * dp, my + 8f * dp)
            }
            drawPath(smile, Color.Black.copy(alpha = 0.85f), style = Stroke(4f * dp, cap = StrokeCap.Round))
        }
    }

    // пупок
    drawCircle(skin.secondaryColor, 7f * dp, Offset(cx, cy + rY * 0.45f))

    // наряды
    // 0 праздничный колпак
    run {
        val s = world.accS[0].x
        if (s > 0.01f) {
            val base = cy - rY * 0.92f
            val hat = Path().apply {
                moveTo(cx - rX * 0.32f, base + 6f * dp)
                lineTo(cx, base - rY * 0.6f * s)
                lineTo(cx + rX * 0.32f, base + 6f * dp)
                close()
            }
            drawPath(hat, Color(0xFFAB47BC))
            drawLine(Color(0xFFFFEB3B), Offset(cx - rX * 0.2f, base - rY * 0.15f * s), Offset(cx + rX * 0.2f, base - rY * 0.22f * s), 6f * dp, StrokeCap.Round)
            drawCircle(Color(0xFFFF4081), 9f * dp * s, Offset(cx, base - rY * 0.6f * s))
        }
    }
    // 1 очки
    run {
        val s = world.accS[1].x
        if (s > 0.01f) {
            val st = Stroke(5f * dp)
            drawCircle(Color(0xFF263238), er * 1.3f * s, Offset(cx - ex, ey), style = st)
            drawCircle(Color(0xFF263238), er * 1.3f * s, Offset(cx + ex, ey), style = st)
            drawLine(Color(0xFF263238), Offset(cx - ex + er * 1.3f * s, ey), Offset(cx + ex - er * 1.3f * s, ey), 5f * dp)
            drawCircle(Color(0xFF81D4FA).copy(alpha = 0.3f), er * 1.3f * s, Offset(cx - ex, ey))
            drawCircle(Color(0xFF81D4FA).copy(alpha = 0.3f), er * 1.3f * s, Offset(cx + ex, ey))
        }
    }
    // 2 усы
    run {
        val s = world.accS[2].x
        if (s > 0.01f) {
            val my2 = cy + rY * 0.1f
            for (side in listOf(-1f, 1f)) {
                drawOval(Color(0xFF4E342E), Offset(cx + (if (side < 0) -46f * dp * s else 0f), my2 - 8f * dp * s), Size(46f * dp * s, 18f * dp * s))
            }
        }
    }
    // 3 корона
    run {
        val s = world.accS[3].x
        if (s > 0.01f) {
            val base = cy - rY * 0.93f
            val hh = rY * 0.36f * s
            val cr = Path().apply {
                moveTo(cx - rX * 0.34f, base)
                lineTo(cx - rX * 0.34f, base - hh)
                lineTo(cx - rX * 0.17f, base - hh * 0.55f)
                lineTo(cx, base - hh * 1.15f)
                lineTo(cx + rX * 0.17f, base - hh * 0.55f)
                lineTo(cx + rX * 0.34f, base - hh)
                lineTo(cx + rX * 0.34f, base)
                close()
            }
            drawPath(cr, Color(0xFFFFC107))
            drawPath(cr, Color(0xFFFF8F00), style = Stroke(3f * dp))
            drawCircle(Color(0xFFE53935), 6f * dp * s, Offset(cx, base - hh * 0.35f))
        }
    }
    // 4 бабочка
    run {
        val s = world.accS[4].x
        if (s > 0.01f) {
            val by = cy + rY * 0.82f
            for (side in listOf(-1f, 1f)) {
                val tri = Path().apply {
                    moveTo(cx, by)
                    lineTo(cx + side * 34f * dp * s, by - 18f * dp * s)
                    lineTo(cx + side * 34f * dp * s, by + 18f * dp * s)
                    close()
                }
                drawPath(tri, Color(0xFFE53935))
            }
            drawCircle(Color(0xFFB71C1C), 8f * dp * s, Offset(cx, by))
        }
    }
    // 5 цветок
    run {
        val s = world.accS[5].x
        if (s > 0.01f) {
            val fx = cx - rX * 0.55f
            val fy = cy - rY * 0.78f
            for (k in 0 until 6) {
                val a = k * 1.047f
                drawCircle(Color(0xFFF48FB1), 9f * dp * s, Offset(fx + cos(a) * 12f * dp * s, fy + sin(a) * 12f * dp * s))
            }
            drawCircle(Color(0xFFFFEB3B), 8f * dp * s, Offset(fx, fy))
        }
    }
}

private fun DrawScope.drawSmSpiral(center: Offset, radius: Float, time: Float) {
    drawCircle(Color.White, radius, center)
    val path = Path()
    val steps = 40
    for (i in 0 until steps) {
        val ang = i * 0.35f + time * 10f
        val r = (i.toFloat() / steps) * radius * 0.85f
        val x = center.x + cos(ang) * r
        val y = center.y + sin(ang) * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, Color(0xFF212121), style = Stroke(3.5f, cap = StrokeCap.Round))
}

private fun DrawScope.drawSmStar(center: Offset, size: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) size else size * 0.45f
        val angle = (i * PI / 5 - PI / 2).toFloat()
        val x = center.x + cos(angle) * r
        val y = center.y + sin(angle) * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}
