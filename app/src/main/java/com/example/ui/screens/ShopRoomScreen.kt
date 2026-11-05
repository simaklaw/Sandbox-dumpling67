package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.LocalAgeProfile
import com.example.shared.SoundManager
import com.example.shared.i18n.Text
import com.example.shared.rememberHaptics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Комната «Магазин пельменей».
// К прилавку приходит зверушка (мишка, зайка, котик) и заказывает пельмешки в облачке.
// Ребёнок перетаскивает их с витрины на тарелку (лишнее можно вернуть), жмёт на кассу, собирает монетки,
// покупатель «съедает» заказ и машет лапкой. Награды: наклейки альбома «Лучший продавец» и золотой пельмень-корона.

private const val SH_PREFS = "shop_prefs"

private const val SH_CLASSIC = 0
private const val SH_STAR = 1
private const val SH_TRI = 2
private const val SH_HEART = 3
private const val SH_CROWN = 4

private const val PH_ARRIVE = 0
private const val PH_ORDER = 1
private const val PH_COINS = 2
private const val PH_EAT = 3
private const val PH_LEAVE = 4

private class ShSticker(val id: Int, val name: String, val need: Int, val note: String?)

private val SH_STICKERS = listOf(
    ShSticker(0, "Шеф-Повар", 1, null),
    ShSticker(1, "Быстрый Заказ", 3, null),
    ShSticker(2, "Золотая Касса", 5, "Пельмень с золотой короной"),
    ShSticker(3, "Любимец Заек", 8, null),
    ShSticker(4, "Мастер Форм", 12, null),
    ShSticker(5, "Король Магазина", 16, null)
)

private val SH_FILL_A = listOf(Color(0xFFFFF3D2), Color(0xFFFFEE70), Color(0xFFFFA3CC), Color(0xFFFF8A8A), Color(0xFFFFE066))
private val SH_FILL_B = listOf(Color(0xFFF0C987), Color(0xFFF2B705), Color(0xFFEC66A0), Color(0xFFE04F4F), Color(0xFFF0A000))
private val SH_EDGE = listOf(Color(0xFFC98F3D), Color(0xFFC58A00), Color(0xFFC23D7C), Color(0xFFB92E2E), Color(0xFFB87400))
private val SH_EX = floatArrayOf(0.30f, 0.24f, 0.26f, 0.30f, 0.30f)
private val SH_EY = floatArrayOf(-0.02f, 0.05f, 0.28f, -0.12f, 0.12f)

private val SH_COIN_A = listOf(Color(0xFFFFE06A), Color(0xFFE9F0F8), Color(0xFFFFB3D1), Color(0xFF9BF0CF))
private val SH_COIN_B = listOf(Color(0xFFE0A100), Color(0xFF9FB0C4), Color(0xFFE0568F), Color(0xFF2FAA80))

private val SH_FUR = listOf(Color(0xFFB9803F), Color(0xFFF6F1FA), Color(0xFFF59A4A))
private val SH_FUR_D = listOf(Color(0xFF8E5C28), Color(0xFFCFC3E0), Color(0xFFD97A2B))
private val SH_INNER = listOf(Color(0xFFE3B98A), Color(0xFFFFB3D1), Color(0xFFFFC9A0))
private val SH_SNOUT = listOf(Color(0xFFF6DDB4), Color(0xFFFFFFFF), Color(0xFFFFF1DF))
private val SH_SHIRT = listOf(Color(0xFF4FA3E0), Color(0xFFFF7DB0), Color(0xFF5CC98B))

private val SH_PATHS: List<Path> by lazy {
    val classic = Path().apply {
        moveTo(-1f, 0.35f)
        cubicTo(-1f, -0.6f, -0.5f, -0.95f, 0f, -0.95f)
        cubicTo(0.5f, -0.95f, 1f, -0.6f, 1f, 0.35f)
        cubicTo(1f, 0.7f, 0.6f, 0.82f, 0f, 0.82f)
        cubicTo(-0.6f, 0.82f, -1f, 0.7f, -1f, 0.35f)
        close()
    }
    val star = Path().apply {
        for (i in 0 until 10) {
            val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
            val rr = if (i % 2 == 0) 1.0f else 0.56f
            val x = cos(a) * rr
            val y = sin(a) * rr + 0.06f
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    val tri = Path().apply {
        moveTo(0f, -0.86f); lineTo(0.92f, 0.72f); lineTo(-0.92f, 0.72f); close()
    }
    val heart = Path().apply {
        moveTo(0f, 0.92f)
        cubicTo(-0.35f, 0.65f, -1.05f, 0.2f, -1.0f, -0.35f)
        cubicTo(-0.95f, -0.85f, -0.2f, -0.95f, 0f, -0.4f)
        cubicTo(0.2f, -0.95f, 0.95f, -0.85f, 1.0f, -0.35f)
        cubicTo(1.05f, 0.2f, 0.35f, 0.65f, 0f, 0.92f)
        close()
    }
    val crown = Path().apply {
        moveTo(-1f, 0.72f); lineTo(-1f, -0.4f); lineTo(-0.5f, 0.1f); lineTo(0f, -0.9f)
        lineTo(0.5f, 0.1f); lineTo(1f, -0.4f); lineTo(1f, 0.72f); close()
    }
    listOf(classic, star, tri, heart, crown)
}

private class ShPart(
    val kind: Int, var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float, val size: Float, val color: Color, var rot: Float, val vr: Float
)

private class ShItem(val type: Int) {
    var dx = 0f
    var dy = 0f
    var squash = 0f
    val ph = Random.nextFloat() * 6f
    var retT = -1f
}

private class ShCoin(var x: Float, var y: Float, val col: Int) {
    var sx = 0f
    var sy = 0f
    var tx = 0f
    var ty = 0f
    var big = false
    var state = 0       // 0 вылетает, 1 лежит, 2 летит в кошелёк
    var t = 0f
    var delay = 0f
    val ph = Random.nextFloat() * 6f
}

private class ShFly(val type: Int, var x: Float, var y: Float, val tx: Float, val ty: Float) {
    var t = 0f
}

private class ShModel {
    var clock = 0f
    var started = false
    var lastTouch = 0f
    var demo = false
    var hx = 0f
    var hy = 0f
    var hDown = false
    var dStep = 0
    var dWait = 0f

    var phase = PH_ARRIVE
    var phaseT = 0f
    var kind = 0
    var lastKind = -1
    var offX = 0f
    var greeted = false
    val order = ArrayList<Int>()
    val plate = ArrayList<ShItem>()
    val coins = ArrayList<ShCoin>()
    val flies = ArrayList<ShFly>()
    val parts = ArrayList<ShPart>()

    var carry = -1
    var cx = 0f
    var cy = 0f
    var carryPlate = false
    var real = false

    var blink = 0f
    var nextBlink = 2f
    var thinkT = 0f
    var mouth = 0f
    var wave = 0f
    var hop = 0f
    var regShake = 0f
    var walletBump = 0f
    var eaten = 0
    var eatTotal = 0
    var endT = -1f
    var byeDone = false
    var yumDone = false
    var rewardChecked = false
    var wasReady = false
    var hint = ""
    var hintTimer = 0f
    var lookX = 0f
    var lookY = 0f
    var downX = 0f
    var downY = 0f
    var downT = 0f
    var orderT = 0f
}

private fun shEase(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
private fun shLerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun Color.shA(a: Float) = copy(alpha = a.coerceIn(0f, 1f))

// ───────────────────────── рисование ─────────────────────────

private fun DrawScope.shStar(c: Offset, r: Float, color: Color, rot: Float) {
    val p = Path()
    for (i in 0 until 10) {
        val a = rot + i * PI.toFloat() / 5f
        val rr = if (i % 2 == 0) r else r * 0.42f
        val x = c.x + cos(a) * rr
        val y = c.y + sin(a) * rr
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}

/** Пельмешек с живыми глазами. type: форма; mood 0 спокойный, 1 радостный; squash — «прыжок»; blinkPh — фаза моргания */
private fun DrawScope.shPelmen(type: Int, cx: Float, cy: Float, r: Float, mood: Int, t: Float, ph: Float, squash: Float = 0f, rot: Float = 0f) {
    val path = SH_PATHS[type]
    withTransform({
        translate(cx, cy)
        rotate(rot, Offset.Zero)
        scale(r * (1f + squash * 0.16f), r * (1f - squash * 0.16f), Offset.Zero)
    }) {
        drawOval(Color(0x2A000000), Offset(-0.85f, 0.66f), Size(1.7f, 0.3f))
        drawPath(path, Brush.linearGradient(listOf(SH_FILL_A[type], SH_FILL_B[type]), Offset(-0.6f, -0.9f), Offset(0.8f, 0.95f)))
        drawPath(path, SH_EDGE[type], style = Stroke(0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawOval(Color.White.shA(0.4f), Offset(-0.62f, -0.72f), Size(0.5f, 0.2f))
        when (type) {
            SH_CLASSIC -> for (i in 0..6) {
                val a = (22f + 136f * i / 6f) * PI.toFloat() / 180f
                val px = cos(a) * 0.9f
                val py = 0.08f - sin(a) * 0.88f
                drawLine(SH_EDGE[type].shA(0.8f), Offset(px, py), Offset(px * 0.82f, 0.08f + (py - 0.08f) * 0.82f), 0.05f, StrokeCap.Round)
            }
            SH_CROWN -> {
                drawCircle(Color(0xFFFF5C7A), 0.09f, Offset(-1f, -0.4f))
                drawCircle(Color(0xFF4FC3FF), 0.09f, Offset(0f, -0.9f))
                drawCircle(Color(0xFF6BE08C), 0.09f, Offset(1f, -0.4f))
                drawRect(Color(0xFFB87400), Offset(-1f, 0.6f), Size(2f, 0.08f))
            }
        }
        val ex = SH_EX[type]
        val ey = SH_EY[type]
        val blinking = ((t * 0.55f + ph) % 3.1f) > 3.0f
        val dark = Color(0xFF3A1F0A)
        for (sgn in listOf(-1f, 1f)) {
            drawCircle(Color(0xFFFF7FA8).shA(0.45f), 0.12f, Offset(sgn * (ex + 0.2f), ey + 0.2f))
            if (blinking) {
                drawLine(dark, Offset(sgn * ex - 0.09f, ey), Offset(sgn * ex + 0.09f, ey), 0.05f, StrokeCap.Round)
            } else {
                drawCircle(dark, 0.11f, Offset(sgn * ex, ey))
                drawCircle(Color.White, 0.04f, Offset(sgn * ex - 0.04f, ey - 0.04f))
            }
        }
        if (mood == 1) {
            drawArc(dark, 0f, 180f, true, Offset(-0.2f, ey + 0.1f), Size(0.4f, 0.3f))
            drawArc(Color(0xFFFF7F8F), 20f, 140f, true, Offset(-0.1f, ey + 0.22f), Size(0.2f, 0.12f))
        } else {
            drawArc(dark, 20f, 140f, false, Offset(-0.16f, ey + 0.08f), Size(0.32f, 0.24f), style = Stroke(0.05f, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.shCoin(x: Float, y: Float, r: Float, col: Int, t: Float, flip: Float = 1f) {
    withTransform({ translate(x, y); scale(max(0.15f, abs(flip)), 1f, Offset.Zero) }) {
        drawCircle(Color(0x33000000), r, Offset(r * 0.08f, r * 0.14f))
        drawCircle(SH_COIN_B[col], r, Offset.Zero)
        drawCircle(Brush.radialGradient(listOf(Color.White, SH_COIN_A[col]), Offset(-r * 0.3f, -r * 0.3f), r * 1.3f), r * 0.86f, Offset.Zero)
        drawCircle(SH_COIN_B[col].shA(0.6f), r * 0.62f, Offset.Zero, style = Stroke(r * 0.07f))
        shStar(Offset.Zero, r * 0.42f, SH_COIN_B[col], -PI.toFloat() / 2f)
        val gl = (sin(t * 5f + x * 0.01f) * 0.5f + 0.5f)
        drawCircle(Color.White.shA(0.5f * gl), r * 0.12f, Offset(-r * 0.45f, -r * 0.5f))
    }
}

private fun DrawScope.shCustomer(
    kind: Int, cx: Float, cy: Float, cs: Float, mood: Int, lookX: Float, lookY: Float,
    blink: Float, mouth: Float, wave: Float, hop: Float, t: Float
) {
    val fur = SH_FUR[kind]
    val furD = SH_FUR_D[kind]
    val inner = SH_INNER[kind]
    val snout = SH_SNOUT[kind]
    val dark = Color(0xFF3A1F0A)
    withTransform({ translate(cx, cy - abs(sin(hop * PI.toFloat())) * cs * 0.3f); scale(cs, cs, Offset.Zero) }) {
        // туловище и лапки
        drawRoundRect(SH_SHIRT[kind], Offset(-0.95f, 0.7f), Size(1.9f, 1.9f), CornerRadius(0.7f))
        drawCircle(Color.White.shA(0.85f), 0.2f, Offset(0f, 0.95f))
        drawCircle(fur, 0.26f, Offset(-0.85f, 1.1f))
        if (wave > 0.01f) {
            val py = 0.15f + sin(t * 11f) * 0.12f
            drawLine(fur, Offset(0.8f, 1.0f), Offset(1.15f, py), 0.34f, StrokeCap.Round)
            drawCircle(fur, 0.26f, Offset(1.15f, py))
        } else drawCircle(fur, 0.26f, Offset(0.85f, 1.1f))

        // уши
        when (kind) {
            0 -> for (sgn in listOf(-1f, 1f)) {
                drawCircle(furD, 0.34f, Offset(sgn * 0.74f, -0.74f))
                drawCircle(inner, 0.19f, Offset(sgn * 0.74f, -0.74f))
            }
            1 -> for (sgn in listOf(-1f, 1f)) {
                withTransform({ rotate(sgn * 9f + sin(t * 1.7f + sgn) * 3f, Offset(sgn * 0.45f, -0.7f)) }) {
                    drawOval(fur, Offset(sgn * 0.45f - 0.2f, -2.15f), Size(0.4f, 1.55f))
                    drawOval(inner, Offset(sgn * 0.45f - 0.11f, -2.0f), Size(0.22f, 1.25f))
                }
            }
            else -> for (sgn in listOf(-1f, 1f)) {
                val p = Path().apply { moveTo(sgn * 0.98f, -0.3f); lineTo(sgn * 0.78f, -1.2f); lineTo(sgn * 0.12f, -0.85f); close() }
                val p2 = Path().apply { moveTo(sgn * 0.84f, -0.4f); lineTo(sgn * 0.74f, -0.98f); lineTo(sgn * 0.3f, -0.78f); close() }
                drawPath(p, fur); drawPath(p2, inner)
            }
        }
        // голова
        drawCircle(Brush.radialGradient(listOf(lerp(fur, Color.White, 0.28f), fur), Offset(-0.3f, -0.4f), 1.4f), 1f, Offset.Zero)
        if (kind == 2) for (i in -1..1) drawLine(furD, Offset(i * 0.2f, -0.98f), Offset(i * 0.16f, -0.66f), 0.07f, StrokeCap.Round)
        drawCircle(Color(0xFFFF7FA8).shA(0.45f), 0.17f, Offset(-0.66f, 0.2f))
        drawCircle(Color(0xFFFF7FA8).shA(0.45f), 0.17f, Offset(0.66f, 0.2f))
        drawOval(snout, Offset(-0.34f, 0.1f), Size(0.68f, 0.52f))
        // нос
        when (kind) {
            0 -> drawOval(dark, Offset(-0.1f, 0.14f), Size(0.2f, 0.13f))
            else -> drawOval(Color(0xFFFF6F9C), Offset(-0.08f, 0.14f), Size(0.16f, 0.1f))
        }
        // рот
        if (mouth > 0.05f) {
            drawOval(Color(0xFF7A2230), Offset(-0.14f, 0.34f), Size(0.28f, 0.1f + 0.26f * mouth))
        } else if (mood == 2) {
            drawArc(Color(0xFF7A2230), 0f, 180f, true, Offset(-0.2f, 0.3f), Size(0.4f, 0.3f))
            drawArc(Color(0xFFFF8FA0), 20f, 140f, true, Offset(-0.1f, 0.44f), Size(0.2f, 0.12f))
        } else {
            drawArc(dark, 20f, 140f, false, Offset(-0.17f, 0.26f), Size(0.34f, 0.24f), style = Stroke(0.05f, cap = StrokeCap.Round))
            if (kind == 1) drawRoundRect(Color.White, Offset(-0.07f, 0.36f), Size(0.14f, 0.13f), CornerRadius(0.03f))
        }
        if (kind == 2) for (sgn in listOf(-1f, 1f)) for (k in 0..2) {
            drawLine(dark.shA(0.55f), Offset(sgn * 0.5f, 0.3f), Offset(sgn * 1.08f, 0.2f + k * 0.12f), 0.025f, StrokeCap.Round)
        }
        // глаза
        for (sgn in listOf(-1f, 1f)) {
            val ex = sgn * 0.38f
            val ey = -0.14f
            if (mood == 2) {
                drawArc(dark, 200f, 140f, false, Offset(ex - 0.14f, ey - 0.06f), Size(0.28f, 0.28f), style = Stroke(0.06f, cap = StrokeCap.Round))
            } else if (blink > 0f) {
                drawLine(dark, Offset(ex - 0.13f, ey), Offset(ex + 0.13f, ey), 0.06f, StrokeCap.Round)
            } else {
                drawCircle(Color.White, 0.21f, Offset(ex, ey))
                drawCircle(Color(0x22000000), 0.21f, Offset(ex, ey), style = Stroke(0.02f))
                val px = ex + lookX * 0.08f
                val py = ey + lookY * 0.08f
                drawCircle(dark, 0.125f, Offset(px, py))
                drawCircle(Color.White, 0.045f, Offset(px - 0.045f, py - 0.045f))
            }
            if (mood == 1) drawLine(dark, Offset(ex - 0.15f, ey - 0.3f + (if (sgn < 0) 0.03f else -0.03f)), Offset(ex + 0.15f, ey - 0.3f + (if (sgn < 0) -0.03f else 0.03f)), 0.05f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.shHand(c: Offset, size: Float, down: Boolean, t: Float) {
    val k = if (down) 0.82f else 1f
    drawCircle(Color.White.shA(0.35f), size * (1.1f + 0.25f * sin(t * 8f)), c)
    drawCircle(Color(0xFFFFD9B5), size * 0.6f * k, c)
    drawCircle(Color(0xFFE0A980), size * 0.6f * k, c, style = Stroke(size * 0.08f))
}

private fun DrawScope.shStickerArt(id: Int, c: Offset, r: Float) {
    val bg = listOf(Color(0xFFFFC93C), Color(0xFF7FD0FF), Color(0xFFFFB347), Color(0xFFFF9CC2), Color(0xFFB28DFF), Color(0xFFFFD43B))
    drawCircle(Color(0x33000000), r, Offset(c.x + r * 0.05f, c.y + r * 0.09f))
    drawCircle(Brush.radialGradient(listOf(Color.White, bg[id % 6]), Offset(c.x - r * 0.3f, c.y - r * 0.35f), r * 1.5f), r, c)
    drawCircle(Color.White, r, c, style = Stroke(r * 0.1f))
    when (id) {
        0 -> shPelmen(SH_CLASSIC, c.x, c.y, r * 0.58f, 1, 0f, 0f)
        1 -> shPelmen(SH_STAR, c.x, c.y, r * 0.6f, 1, 0f, 0f)
        2 -> shCoin(c.x, c.y, r * 0.58f, 0, 0f)
        3 -> shPelmen(SH_HEART, c.x, c.y, r * 0.58f, 1, 0f, 0f)
        4 -> {
            shPelmen(SH_STAR, c.x - r * 0.42f, c.y + r * 0.1f, r * 0.3f, 1, 0f, 0f)
            shPelmen(SH_TRI, c.x + r * 0.42f, c.y + r * 0.1f, r * 0.3f, 1, 0f, 0f)
            shPelmen(SH_HEART, c.x, c.y - r * 0.3f, r * 0.3f, 1, 0f, 0f)
        }
        else -> shPelmen(SH_CROWN, c.x, c.y, r * 0.62f, 1, 0f, 0f)
    }
}

// ───────────────────────── экран ─────────────────────────

@Composable
fun ShopRoomScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val sm = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val toddler = LocalAgeProfile.current.isToddler2
    val prefs = remember { context.getSharedPreferences(SH_PREFS, Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) {
        sm.preloadRaw(
            listOf(
                "fx_sh_hello_bear", "fx_sh_hello_bunny", "fx_sh_hello_cat", "fx_sh_bell", "fx_sh_plate", "fx_sh_pick",
                "fx_sh_wrong", "fx_sh_ready", "fx_sh_cash", "fx_sh_coin1", "fx_sh_coin2", "fx_sh_coin3",
                "fx_sh_yum", "fx_sh_bye", "fx_sh_gold", "fx_sh_reward",
                "fx_sh_p_hello", "fx_sh_p_plate", "fx_sh_p_cash", "fx_sh_p_coin", "fx_sh_p_yum"
            )
        )
    }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(0) }
    val m = remember { ShModel() }
    var served by remember { mutableIntStateOf(prefs.getInt("served", 0)) }
    var hintText by remember { mutableStateOf("") }
    var progressPct by remember { mutableIntStateOf(0) }
    var showAlbum by remember { mutableStateOf(false) }
    var rewardSticker by remember { mutableStateOf<ShSticker?>(null) }
    var demoOn by remember { mutableStateOf(false) }

    fun W() = boxSize.width.toFloat()
    fun H() = boxSize.height.toFloat()
    fun snd(n: String, v: Float = 1f, r: Float = 1f) { sm.play(n, volume = v, rate = r) }
    fun coinName(): String { val k = Random.nextInt(4); return if (k == 3) "fx_sh_p_coin" else "fx_sh_coin${k + 1}" }
    fun either(a: String, b: String): String = if (Random.nextBoolean()) a else b

    // ── геометрия ──
    fun r() = min(W() * 0.075f, H() * 0.06f)
    fun cs() = min(W() * 0.16f, H() * 0.13f)
    fun counterY() = H() * 0.54f
    fun custX() = W() * 0.36f + m.offX
    fun custY() = counterY() - cs() * 0.95f
    fun shelfY() = H() * 0.65f
    fun plateX() = W() * 0.28f
    fun plateY() = H() * 0.85f
    fun regX() = W() * 0.83f
    fun regY() = H() * 0.83f
    fun walletX() = W() * 0.585f
    fun walletY() = H() * 0.86f
    fun goodsUnlocked() = served >= 5
    fun shelfTypes(): List<Int> {
        val l = ArrayList<Int>()
        l.add(SH_CLASSIC); l.add(SH_STAR); l.add(SH_TRI)
        if (!toddler) l.add(SH_HEART)
        if (goodsUnlocked()) l.add(SH_CROWN)
        return l
    }
    fun slotX(i: Int, n: Int): Float {
        val sp = min(W() * 0.17f, r() * 3.3f)
        return W() * 0.5f + (i - (n - 1) / 2f) * sp
    }
    fun slotOf(type: Int): Offset {
        val l = shelfTypes()
        val i = l.indexOf(type).coerceAtLeast(0)
        return Offset(slotX(i, l.size), shelfY())
    }
    fun plateItemPos(i: Int, n: Int): Offset {
        val sp = min(r() * 1.5f, r() * 4.6f / max(1, n))
        val it = m.plate[i]
        return Offset(plateX() + (i - (n - 1) / 2f) * sp + it.dx, plateY() - r() * 0.3f + it.dy)
    }
    fun onPlate(x: Float, y: Float) = abs(x - plateX()) < r() * 3.2f && abs(y - plateY()) < r() * 2.7f

    fun burst(kind: Int, x: Float, y: Float, n: Int, color: Color, speed: Float, size: Float, up: Float = 0f, life: Float = 0.9f) {
        repeat(n) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val sp = speed * (0.4f + Random.nextFloat() * 0.6f)
            m.parts.add(ShPart(kind, x, y, cos(a) * sp, sin(a) * sp - up, life, life, size * (0.6f + Random.nextFloat() * 0.6f), color, Random.nextFloat() * 6f, (Random.nextFloat() - 0.5f) * 6f))
        }
    }

    // ── состояние заказа ──
    fun countOrder(t: Int): Int = m.order.count { it == t }
    fun countPlate(t: Int): Int = m.plate.count { it.type == t }
    fun isReady(): Boolean {
        if (m.plate.size != m.order.size || m.order.isEmpty()) return false
        for (t in 0..4) if (countOrder(t) != countPlate(t)) return false
        return true
    }
    fun extraIndex(): Int {
        val have = IntArray(5)
        for (i in m.plate.indices) {
            val t = m.plate[i].type
            have[t]++
            if (have[t] > countOrder(t)) return i
        }
        return -1
    }
    fun missingType(): Int {
        for (t in 0..4) if (countOrder(t) > countPlate(t)) return t
        return -1
    }

    fun genOrder(): ArrayList<Int> {
        val types = ArrayList(shelfTypes().filter { it != SH_CROWN })
        val n = if (toddler) 1 else (1 + (if (served >= 2) 1 else 0) + (if (served >= 6 && Random.nextFloat() < 0.6f) 1 else 0))
        val res = ArrayList<Int>()
        if (goodsUnlocked() && Random.nextFloat() < 0.25f) res.add(SH_CROWN)
        while (res.size < n) res.add(types[Random.nextInt(types.size)])
        res.sort()
        return res
    }

    fun startCustomer() {
        var k = Random.nextInt(3)
        if (k == m.lastKind) k = (k + 1 + Random.nextInt(2)) % 3
        m.kind = k
        m.lastKind = k
        m.order.clear(); m.order.addAll(genOrder())
        m.plate.clear(); m.coins.clear(); m.flies.clear()
        m.real = false
        m.phase = PH_ARRIVE
        m.phaseT = 0f
        m.offX = -W() * 0.9f
        m.greeted = false
        m.eaten = 0; m.eatTotal = 0; m.endT = -1f
        m.byeDone = false; m.yumDone = false; m.rewardChecked = false
        m.thinkT = 0f; m.mouth = 0f; m.wave = 0f; m.hop = 0f
        m.carry = -1
        m.wasReady = false
        m.orderT = 0f
        m.dStep = 0; m.dWait = 0f
    }

    fun spawnCoins() {
        val tip = m.real && m.orderT < 16f
        val n = max(2, m.order.size + 1) + (if (tip) 1 else 0)
        for (k in 0 until n) {
            val c = ShCoin(regX(), regY() - r() * 0.8f, (k + Random.nextInt(2)) % 4)
            c.sx = c.x; c.sy = c.y
            if (tip && k == n - 1) { c.big = true }
            val u = (k + 0.5f) / n
            c.tx = W() * 0.40f + W() * 0.5f * u
            c.ty = H() * (0.715f + 0.035f * (k % 2))
            c.delay = k * 0.11f
            m.coins.add(c)
        }
    }

    fun tapRegister(real: Boolean) {
        if (m.phase != PH_ORDER) return
        if (isReady()) {
            m.phase = PH_COINS
            m.phaseT = 0f
            snd(either("fx_sh_cash", "fx_sh_p_cash"), 1f)
            if (real) haptics.vibratePop()
            burst(1, regX(), regY() - r(), 8, Color(0xFFFFF3A0), r() * 2.4f, r() * 0.3f, up = r() * 1.5f, life = 0.7f)
            spawnCoins()
        } else {
            m.regShake = 1f
            snd("fx_sh_wrong", 0.5f, 1.2f)
            if (real) haptics.vibrateShort()
        }
    }

    fun collectCoin(c: ShCoin, real: Boolean) {
        if (c.state != 1) return
        c.state = 2; c.t = 0f; c.sx = c.x; c.sy = c.y
        snd(coinName(), 0.8f, 0.95f + Random.nextFloat() * 0.2f)
        if (real) haptics.vibrateShort()
        burst(1, c.x, c.y, 4, Color(0xFFFFF3A0), r() * 1.6f, r() * 0.2f, life = 0.5f)
    }

    fun addToPlate(type: Int, x: Float, y: Float, real: Boolean) {
        val it = ShItem(type)
        m.plate.add(it)
        val pos = plateItemPos(m.plate.size - 1, m.plate.size)
        it.dx += x - pos.x
        it.dy += y - pos.y
        it.squash = 1f
        snd(either("fx_sh_plate", "fx_sh_p_plate"), 0.9f, 0.95f + Random.nextFloat() * 0.1f)
        if (real) haptics.vibrateSquish()
        burst(1, pos.x, pos.y - r() * 0.6f, 4, Color(0xFFFFF3A0), r() * 1.4f, r() * 0.2f, life = 0.5f)
        if (countPlate(type) > countOrder(type)) {
            m.thinkT = 1.3f
            snd("fx_sh_wrong", 0.45f)
            if (toddler) it.retT = 1.0f
        } else if (isReady() && !m.wasReady) {
            m.wasReady = true
            snd("fx_sh_ready", 0.9f)
            m.hop = 1f
            burst(2, custX(), custY() - cs() * 0.4f, 8, Color(0xFFFFF3A0), cs() * 1.2f, cs() * 0.16f, life = 0.8f)
        }
    }

    fun dropCarry(x: Float, y: Float, real: Boolean) {
        val type = m.carry
        if (type < 0) return
        val fromPlate = m.carryPlate
        m.carry = -1; m.carryPlate = false
        if (onPlate(x, y) && m.phase == PH_ORDER) {
            addToPlate(type, x, y, real)
        } else {
            val s = slotOf(type)
            m.flies.add(ShFly(type, x, y, s.x, s.y))
            if (fromPlate) snd("fx_sh_pick", 0.6f, 0.8f)
        }
    }

    fun pickFromPlate(i: Int, x: Float, y: Float) {
        val it = m.plate.removeAt(i)
        m.carry = it.type; m.carryPlate = true
        m.cx = x; m.cy = y
        m.wasReady = isReady()
        snd("fx_sh_pick", 0.7f)
    }

    fun hitRegister(x: Float, y: Float) = abs(x - regX()) < r() * 1.7f && abs(y - regY()) < r() * 1.6f

    fun stopDemo() {
        if (m.demo) {
            if (m.carry >= 0) { m.carry = -1; m.carryPlate = false }
            m.demo = false; m.hDown = false; demoOn = false
        }
    }

    fun onDown(x: Float, y: Float, real: Boolean) {
        if (real) m.real = true
        m.downX = x; m.downY = y; m.downT = m.clock
        when (m.phase) {
            PH_COINS -> {
                for (c in m.coins) if (c.state == 1 && hypot(x - c.x, y - c.y) < r() * 1.5f) collectCoin(c, real)
            }
            PH_ORDER -> {
                if (hitRegister(x, y)) { tapRegister(real); return }
                for (i in m.plate.indices.reversed()) {
                    val p = plateItemPos(i, m.plate.size)
                    if (hypot(x - p.x, y - p.y) < r() * 1.15f) { pickFromPlate(i, x, y); return }
                }
                val l = shelfTypes()
                for (i in l.indices) {
                    if (hypot(x - slotX(i, l.size), y - shelfY()) < r() * 1.3f) {
                        m.carry = l[i]; m.carryPlate = false; m.cx = x; m.cy = y
                        snd("fx_sh_pick", 0.7f)
                        if (real) haptics.vibrateShort()
                        return
                    }
                }
                if (hypot(x - custX(), y - custY()) < cs() * 1.1f) {
                    m.hop = 1f
                    snd("fx_sh_hello_${listOf("bear", "bunny", "cat")[m.kind]}", 0.8f, 1.1f)
                }
            }
            else -> if (hypot(x - custX(), y - custY()) < cs() * 1.1f && m.phase != PH_LEAVE) m.hop = 1f
        }
    }

    fun onMove(x: Float, y: Float) {
        if (m.carry >= 0) { m.cx = x; m.cy = y }
        if (m.phase == PH_COINS) {
            for (c in m.coins) if (c.state == 1 && hypot(x - c.x, y - c.y) < r() * 1.2f) collectCoin(c, true)
        }
    }

    fun onUp() {
        if (m.carry < 0) return
        val tap = hypot(m.cx - m.downX, m.cy - m.downY) < r() * 0.35f && m.clock - m.downT < 0.45f
        if (tap && m.phase == PH_ORDER) {
            // короткий тап: с витрины — сразу на тарелку, с тарелки — обратно на витрину
            val type = m.carry
            val fromPlate = m.carryPlate
            m.carry = -1; m.carryPlate = false
            val s = slotOf(type)
            if (fromPlate) {
                m.flies.add(ShFly(type, m.cx, m.cy, s.x, s.y))
                m.wasReady = isReady()
                snd("fx_sh_pick", 0.6f, 0.8f)
            } else addToPlate(type, s.x, s.y, true)
        } else dropCarry(m.cx, m.cy, true)
    }

    fun startDemo() {
        m.demo = true; demoOn = true
        m.hx = W() * 0.5f; m.hy = H() * 0.8f
        m.dStep = 0; m.dWait = 0.4f
    }

    fun moveHand(tx: Float, ty: Float, dt: Float): Boolean {
        val dx = tx - m.hx
        val dy = ty - m.hy
        val d = hypot(dx, dy)
        val sp = W() * 1.0f * dt
        if (d <= sp + 2f) { m.hx = tx; m.hy = ty; return true }
        m.hx += dx / d * sp; m.hy += dy / d * sp
        return false
    }

    fun computeHint(): String = when (m.phase) {
        PH_ARRIVE -> "Здравствуйте! Заходите!"
        PH_ORDER -> when {
            extraIndex() >= 0 -> "Лишний пельмешек — верни его на витрину"
            isReady() -> "Заказ готов! Нажми на кассу"
            m.plate.isEmpty() -> if (toddler) "Положи на тарелку то, что в облачке" else "Собери заказ: положи на тарелку пельмешки из облачка"
            else -> "Отлично! Добавь ещё пельмешки из облачка"
        }
        PH_COINS -> if (m.coins.any { it.big }) "Быстрый заказ! Забери чаевые!" else "Собери монетки!"
        PH_EAT -> "Приятного аппетита!"
        else -> "До свидания! Приходите ещё!"
    }

    fun updateProgress() {
        val next = SH_STICKERS.firstOrNull { it.need > served }
        val prev = SH_STICKERS.lastOrNull { it.need <= served }?.need ?: 0
        val p = if (next == null) 100 else ((served - prev) * 100 / (next.need - prev))
        if (p != progressPct) progressPct = p
    }

    // ── кадр ──
    fun update(dt: Float) {
        if (W() <= 0f) { frame++; return }
        m.clock += dt
        val clock = m.clock
        if (!m.started) { m.started = true; startCustomer(); updateProgress() }
        val overlay = showAlbum || rewardSticker != null

        m.nextBlink -= dt
        if (m.nextBlink <= 0f) { m.blink = 0.14f; m.nextBlink = 2f + Random.nextFloat() * 2.5f }
        m.blink = max(0f, m.blink - dt)
        m.thinkT = max(0f, m.thinkT - dt)
        m.hop = max(0f, m.hop - dt * 1.8f)
        m.regShake = max(0f, m.regShake - dt * 2.2f)
        m.walletBump = max(0f, m.walletBump - dt * 3f)
        m.phaseT += dt
        if (m.phase == PH_ORDER && !m.demo) m.orderT += dt

        // взгляд зверушки
        val hxc = custX(); val hyc = custY()
        val (fx, fy) = if (m.carry >= 0) Pair(m.cx, m.cy) else Pair(plateX(), plateY())
        val lx = fx - hxc; val ly = fy - hyc
        val ld = max(1f, hypot(lx, ly))
        m.lookX += (lx / ld - m.lookX) * min(1f, dt * 8f)
        m.lookY += (ly / ld - m.lookY) * min(1f, dt * 8f)

        // предметы на тарелке
        val dec = exp(-14f * dt)
        var ri = m.plate.size - 1
        while (ri >= 0) {
            val it = m.plate[ri]
            it.dx *= dec; it.dy *= dec
            it.squash = max(0f, it.squash - dt * 3.2f)
            if (it.retT >= 0f) {
                it.retT -= dt
                if (it.retT < 0f) {
                    val p = plateItemPos(ri, m.plate.size)
                    val s = slotOf(it.type)
                    m.flies.add(ShFly(it.type, p.x, p.y, s.x, s.y))
                    m.plate.removeAt(ri)
                    m.wasReady = isReady()
                }
            }
            ri--
        }
        // летящие обратно на витрину
        for (f in m.flies) {
            f.t += dt / 0.28f
            val k = shEase(f.t)
            f.x = shLerp(f.x, f.tx, min(1f, k * 0.35f + 0.0f) )
            f.y = shLerp(f.y, f.ty, min(1f, k * 0.35f))
        }
        m.flies.removeAll { it.t >= 1f }

        when (m.phase) {
            PH_ARRIVE -> {
                val d = 1.3f
                m.offX = shLerp(-W() * 0.9f, 0f, shEase(m.phaseT / d))
                if (m.phaseT < dt * 1.5f) snd("fx_sh_bell", 0.7f)
                if (!m.greeted && m.phaseT > 0.9f) {
                    m.greeted = true
                    snd(if (Random.nextFloat() < 0.3f) "fx_sh_p_hello" else "fx_sh_hello_${listOf("bear", "bunny", "cat")[m.kind]}", 0.9f)
                    m.hop = 1f
                }
                if (m.phaseT >= d) { m.phase = PH_ORDER; m.phaseT = 0f; m.offX = 0f }
            }
            PH_COINS -> {
                val auto = m.phaseT > (if (toddler) 6f else 9f)
                var allDone = true
                for (c in m.coins) {
                    c.t += dt
                    when (c.state) {
                        0 -> {
                            allDone = false
                            val u = ((c.t - c.delay) / 0.55f)
                            if (u >= 0f) {
                                val k = shEase(u)
                                c.x = shLerp(c.sx, c.tx, k)
                                c.y = shLerp(c.sy, c.ty, k) - sin(min(1f, u) * PI.toFloat()) * H() * 0.1f
                                if (u >= 1f) {
                                    c.state = 1; c.t = 0f
                                    snd(coinName(), 0.5f, 1f)
                                }
                            }
                        }
                        1 -> {
                            allDone = false
                            if (auto && c.t > 0.3f) collectCoin(c, false)
                        }
                        else -> {
                            val u = c.t / 0.45f
                            val k = shEase(u)
                            c.x = shLerp(c.sx, walletX(), k)
                            c.y = shLerp(c.sy, walletY(), k) - sin(min(1f, u) * PI.toFloat()) * H() * 0.05f
                            if (u >= 1f) {
                                c.state = 3
                                m.walletBump = 1f
                                burst(2, walletX(), walletY(), 3, Color(0xFFFFF3A0), r() * 1.2f, r() * 0.18f, life = 0.5f)
                            } else allDone = false
                        }
                    }
                }
                m.coins.removeAll { it.state == 3 }
                if (allDone && m.coins.isEmpty() && m.phaseT > 0.7f) {
                    m.phase = PH_EAT; m.phaseT = 0f
                    m.eatTotal = m.plate.size
                    m.hop = 1f
                }
            }
            PH_EAT -> {
                if (!m.yumDone && m.phaseT > 0.35f) { m.yumDone = true; snd(either("fx_sh_yum", "fx_sh_p_yum"), 0.9f) }
                if (m.plate.isNotEmpty()) {
                    m.mouth = if (m.phaseT > 0.3f) 0.5f + 0.5f * abs(sin(m.phaseT * 9f)) else 0f
                    if (m.phaseT >= 0.5f + 0.6f * m.eaten) {
                        val p = plateItemPos(0, m.plate.size)
                        burst(0, p.x, p.y - r() * 0.5f, 5, Color(0xFFFF6B8A), r() * 1.6f, r() * 0.45f, up = r() * 1.8f, life = 1.0f)
                        m.plate.removeAt(0)
                        m.eaten++
                        haptics.vibratePop()
                    }
                } else {
                    m.mouth = 0f
                    if (m.endT < 0f) { m.endT = m.phaseT; m.wave = 1f; m.hop = 1f }
                    if (!m.byeDone && m.phaseT > m.endT + 0.15f) {
                        m.byeDone = true
                        snd("fx_sh_bye", 0.9f)
                        burst(0, custX(), custY() - cs() * 0.5f, 10, Color(0xFFFF6B8A), cs() * 1.6f, cs() * 0.2f, up = cs() * 1.2f, life = 1.2f)
                    }
                    if (m.phaseT >= m.endT + 1.4f) {
                        if (!m.rewardChecked) {
                            m.rewardChecked = true
                            if (m.real) {
                                served += 1
                                prefs.edit().putInt("served", served).apply()
                                updateProgress()
                                val st = SH_STICKERS.firstOrNull { it.need == served }
                                if (st != null) {
                                    rewardSticker = st
                                    snd("fx_sh_reward", 0.9f)
                                    if (st.note != null) snd("fx_sh_gold", 0.9f)
                                }
                            }
                        }
                        if (rewardSticker == null && !showAlbum) { m.phase = PH_LEAVE; m.phaseT = 0f }
                    }
                }
            }
            PH_LEAVE -> {
                m.offX = shLerp(0f, W() * 0.95f, shEase(m.phaseT / 1.2f))
                if (m.phaseT >= 1.2f) { m.wave = 0f; startCustomer() }
            }
        }

        // частицы
        for (p in m.parts) {
            p.life -= dt
            when (p.kind) {
                0 -> { p.y += p.vy * dt; p.x += p.vx * dt * 0.3f + sin(clock * 4f + p.rot) * 0.4f; p.vy *= 0.985f }
                else -> { p.x += p.vx * dt; p.y += p.vy * dt; p.vy += H() * 0.9f * dt * (if (p.kind == 3) 1f else 0.35f); p.rot += p.vr * dt }
            }
        }
        m.parts.removeAll { it.life <= 0f }
        if (m.parts.size > 260) repeat(50) { m.parts.removeAt(0) }

        // демо-палец
        if (m.demo) {
            if (overlay || (m.phase != PH_ORDER && m.phase != PH_COINS)) { stopDemo(); if (!overlay) m.lastTouch = clock - 4f } else {
                if (m.carry >= 0 && m.dStep != 0) { m.cx = m.hx; m.cy = m.hy }
                if (m.dWait > 0f) { m.dWait -= dt } else if (m.phase == PH_COINS) {
                    val c = m.coins.firstOrNull { it.state == 1 }
                    if (c != null && moveHand(c.x, c.y, dt)) { collectCoin(c, false); m.dWait = 0.25f }
                    m.hDown = false
                } else when (m.dStep) {
                    0 -> {
                        val ex = extraIndex()
                        if (ex >= 0) {
                            val p = plateItemPos(ex, m.plate.size)
                            if (moveHand(p.x, p.y, dt)) { pickFromPlate(ex, p.x, p.y); m.dStep = 2; m.dWait = 0.2f; m.hDown = true }
                        } else {
                            val mt = missingType()
                            if (mt < 0) {
                                if (moveHand(regX(), regY(), dt)) { tapRegister(false); m.dWait = 0.3f }
                            } else {
                                val s = slotOf(mt)
                                if (moveHand(s.x, s.y, dt)) {
                                    m.carry = mt; m.carryPlate = false; m.cx = s.x; m.cy = s.y
                                    snd("fx_sh_pick", 0.5f)
                                    m.dStep = 1; m.dWait = 0.2f; m.hDown = true
                                }
                            }
                        }
                    }
                    1 -> if (moveHand(plateX(), plateY() - r() * 0.3f, dt)) { dropCarry(m.hx, m.hy, false); m.dStep = 0; m.dWait = 0.3f; m.hDown = false }
                    else -> if (moveHand(W() * 0.5f, shelfY(), dt)) { dropCarry(m.hx, m.hy, false); m.dStep = 0; m.dWait = 0.3f; m.hDown = false }
                }
            }
        } else if (!overlay && m.carry == -1 && (m.phase == PH_ORDER || m.phase == PH_COINS) && clock - m.lastTouch > 5f) {
            startDemo()
        }

        m.hintTimer -= dt
        if (m.hintTimer <= 0f) {
            m.hintTimer = 0.2f
            val h = computeHint()
            if (h != m.hint) { m.hint = h; hintText = h }
        }
        frame++
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
                last = now
                update(dt)
            }
        }
    }

    // ───────────────────────── интерфейс ─────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (showAlbum || rewardSticker != null) return@awaitEachGesture
                        stopDemo()
                        m.lastTouch = m.clock
                        onDown(down.position.x, down.position.y, true)
                        var ended = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            m.lastTouch = m.clock
                            if (ch.changedToUp()) { onUp(); ended = true; break }
                            if (ch.positionChanged()) { onMove(ch.position.x, ch.position.y); ch.consume() }
                        }
                        if (!ended) onUp()
                    }
                }
        ) {
            frame.hashCode()
            val w = size.width
            val h = size.height
            if (w <= 0f) return@Canvas
            val t = m.clock
            val rr = r()
            val cY = counterY()

            // стена
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFE7BF), Color(0xFFFFD2A0)), 0f, cY), Offset.Zero, Size(w, cY))
            for (i in 0 until 10) if (i % 2 == 0) drawRect(Color.White.shA(0.18f), Offset(w * i / 10f, 0f), Size(w / 10f, cY))
            // гирлянда флажков
            val gcol = listOf(Color(0xFFFF6B8A), Color(0xFFFFD43B), Color(0xFF4FC3FF), Color(0xFF6BE08C))
            val gp = Path().apply { moveTo(0f, h * 0.02f); quadraticTo(w * 0.5f, h * 0.085f, w, h * 0.02f) }
            drawPath(gp, Color(0xFF8E5628), style = Stroke(w * 0.004f))
            for (i in 0 until 14) {
                val u = (i + 0.5f) / 14f
                val gx = w * u
                val gy = h * 0.02f + (1f - (2f * u - 1f) * (2f * u - 1f)) * h * 0.04f
                val sw = sin(t * 1.5f + i) * 3f
                val fp = Path().apply { moveTo(gx - w * 0.02f, gy); lineTo(gx + w * 0.02f, gy); lineTo(gx + sw, gy + h * 0.035f); close() }
                drawPath(fp, gcol[i % 4])
            }
            // вывеска и окно
            val sx = w * 0.60f
            drawRoundRect(Color(0xFFB5733C), Offset(sx, h * 0.12f), Size(w * 0.34f, h * 0.075f), CornerRadius(w * 0.03f))
            drawRoundRect(Color(0xFFFFE08A), Offset(sx + w * 0.01f, h * 0.126f), Size(w * 0.32f, h * 0.063f), CornerRadius(w * 0.025f))
            shPelmen(SH_CLASSIC, sx + w * 0.07f, h * 0.158f, h * 0.026f, 1, t, 0f)
            shPelmen(SH_STAR, sx + w * 0.17f, h * 0.158f, h * 0.026f, 1, t, 1f)
            shPelmen(SH_HEART, sx + w * 0.27f, h * 0.158f, h * 0.026f, 1, t, 2f)
            drawRoundRect(Color(0xFFB5733C), Offset(w * 0.05f, h * 0.15f), Size(w * 0.24f, h * 0.17f), CornerRadius(w * 0.02f))
            drawRoundRect(Color(0xFF9ADBFF), Offset(w * 0.06f, h * 0.16f), Size(w * 0.22f, h * 0.15f), CornerRadius(w * 0.015f))
            drawLine(Color(0xFFB5733C), Offset(w * 0.17f, h * 0.16f), Offset(w * 0.17f, h * 0.31f), w * 0.008f)
            drawCircle(Color.White.shA(0.7f), w * 0.02f, Offset(w * 0.11f, h * 0.2f))
            drawCircle(Color.White.shA(0.7f), w * 0.026f, Offset(w * 0.135f, h * 0.205f))

            // покупатель
            val cx0 = custX()
            val cy0 = custY()
            val mood = when {
                m.phase == PH_COINS || m.phase == PH_LEAVE || (m.phase == PH_EAT && m.plate.isEmpty()) -> 2
                m.thinkT > 0f -> 1
                else -> 0
            }
            shCustomer(m.kind, cx0, cy0, cs(), mood, m.lookX, m.lookY, m.blink, m.mouth, m.wave, m.hop, t)

            // облачко с заказом
            if (m.phase == PH_ORDER || m.phase == PH_ARRIVE && m.phaseT > 0.9f) {
                val n = m.order.size
                val bw = cs() * (0.35f + 0.72f * n)
                val bh = cs() * 1.0f
                val bx = cx0 + cs() * 0.95f
                val by = cy0 - cs() * 1.6f
                val pop = if (m.phase == PH_ARRIVE) shEase((m.phaseT - 0.9f) / 0.4f) else 1f
                withTransform({ scale(pop, pop, Offset(bx, by + bh)) }) {
                    drawCircle(Color.White, cs() * 0.1f, Offset(bx - cs() * 0.08f, by + bh + cs() * 0.1f))
                    drawCircle(Color.White, cs() * 0.17f, Offset(bx + cs() * 0.02f, by + bh - cs() * 0.05f))
                    drawRoundRect(Color(0x22000000), Offset(bx + 3f, by + 5f), Size(bw, bh), CornerRadius(cs() * 0.45f))
                    drawRoundRect(Color.White, Offset(bx, by), Size(bw, bh), CornerRadius(cs() * 0.45f))
                    drawRoundRect(Color(0xFFFFB3CC), Offset(bx, by), Size(bw, bh), CornerRadius(cs() * 0.45f), style = Stroke(cs() * 0.04f))
                    val have = IntArray(5)
                    for (it in m.plate) have[it.type]++
                    val used = IntArray(5)
                    for (i in 0 until n) {
                        val ty = m.order[i]
                        val ix = bx + cs() * 0.35f + cs() * 0.72f * i
                        val iy = by + bh * 0.5f
                        used[ty]++
                        val done = used[ty] <= have[ty]
                        shPelmen(ty, ix, iy + sin(t * 3f + i) * cs() * 0.02f, cs() * 0.29f, if (done) 1 else 0, t, i.toFloat())
                        if (done) {
                            val cc = Offset(ix + cs() * 0.2f, iy + cs() * 0.2f)
                            drawCircle(Color(0xFF3FD07A), cs() * 0.15f, cc)
                            drawLine(Color.White, Offset(cc.x - cs() * 0.07f, cc.y), Offset(cc.x - cs() * 0.015f, cc.y + cs() * 0.06f), cs() * 0.04f, StrokeCap.Round)
                            drawLine(Color.White, Offset(cc.x - cs() * 0.015f, cc.y + cs() * 0.06f), Offset(cc.x + cs() * 0.08f, cc.y - cs() * 0.06f), cs() * 0.04f, StrokeCap.Round)
                        }
                    }
                }
            }

            // прилавок (столешница)
            drawRect(Brush.verticalGradient(listOf(Color(0xFFD9A066), Color(0xFFB8793F)), cY, h), Offset(0f, cY), Size(w, h - cY))
            for (i in 1 until 9) drawLine(Color(0x1A5A2D0C), Offset(0f, cY + (h - cY) * i / 9f), Offset(w, cY + (h - cY) * i / 9f), 2f)
            drawRect(Color(0xFF8E5628), Offset(0f, cY), Size(w, h * 0.014f))
            drawRect(Color(0xFFF0C48A), Offset(0f, cY + h * 0.014f), Size(w, h * 0.006f))

            // витрина
            val types = shelfTypes()
            val n = types.size
            val gl = slotX(0, n) - rr * 1.7f
            val gr = slotX(n - 1, n) + rr * 1.7f
            val gt = shelfY() - rr * 1.55f
            val gh = rr * 2.9f
            drawRoundRect(Color(0x22000000), Offset(gl + 6f, gt + 10f), Size(gr - gl, gh), CornerRadius(rr * 0.4f))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFEAF8FF), Color(0xFFCDEBFA)), gt, gt + gh), Offset(gl, gt), Size(gr - gl, gh), CornerRadius(rr * 0.4f))
            drawRoundRect(Color(0xFFB5733C), Offset(gl, gt), Size(gr - gl, gh), CornerRadius(rr * 0.4f), style = Stroke(rr * 0.12f))
            drawRect(Color(0xFFB5733C), Offset(gl, gt + gh - rr * 0.35f), Size(gr - gl, rr * 0.35f))
            for (i in 0 until n) {
                val px = slotX(i, n)
                val ty = types[i]
                drawOval(Color(0xFF8E5628), Offset(px - rr * 1.25f, shelfY() + rr * 0.55f), Size(rr * 2.5f, rr * 0.55f))
                val need = m.phase == PH_ORDER && toddler && missingType() == ty
                val pul = if (need) 1f + 0.07f * sin(t * 6f) else 1f
                if (need) drawCircle(Color(0x66FFF3A0), rr * 1.45f * pul, Offset(px, shelfY()))
                shPelmen(ty, px, shelfY() + sin(t * 2f + i) * rr * 0.03f, rr * pul, 0, t, i * 1.3f)
            }
            drawRoundRect(Color.White.shA(0.35f), Offset(gl + rr * 0.2f, gt + rr * 0.15f), Size(rr * 1.2f, gh * 0.8f), CornerRadius(rr * 0.2f))

            // тарелка
            val px = plateX(); val py = plateY()
            if (m.carry >= 0 && m.phase == PH_ORDER) {
                val near = onPlate(m.cx, m.cy)
                drawOval(Color(0xFFFFF3A0).shA(if (near) 0.55f else 0.2f + 0.08f * sin(t * 6f)), Offset(px - rr * 3.4f, py - rr * 1.25f), Size(rr * 6.8f, rr * 2.5f))
            }
            drawOval(Color(0x33000000), Offset(px - rr * 3.0f + 6f, py - rr * 0.8f + 12f), Size(rr * 6f, rr * 1.8f))
            drawOval(Color(0xFFFFFFFF), Offset(px - rr * 3f, py - rr * 0.95f), Size(rr * 6f, rr * 1.8f))
            drawOval(Color(0xFF6CC3F5), Offset(px - rr * 3f, py - rr * 0.95f), Size(rr * 6f, rr * 1.8f), style = Stroke(rr * 0.12f))
            drawOval(Color(0xFFD7F0FF), Offset(px - rr * 2.4f, py - rr * 0.68f), Size(rr * 4.8f, rr * 1.3f))
            for (i in m.plate.indices) {
                val p = plateItemPos(i, m.plate.size)
                val it = m.plate[i]
                shPelmen(it.type, p.x, p.y, rr * 0.95f, 1, t, it.ph, it.squash)
            }

            // касса
            val rg = if (m.phase == PH_ORDER && isReady()) 1f + 0.06f * sin(t * 7f) else 1f
            val shk = sin(t * 40f) * m.regShake * rr * 0.12f
            if (m.phase == PH_ORDER && isReady()) drawCircle(Color(0x66FFF3A0), rr * 2.3f * rg, Offset(regX(), regY()))
            withTransform({ translate(regX() + shk, regY()); scale(rg, rg, Offset.Zero) }) {
                drawOval(Color(0x33000000), Offset(-rr * 1.7f, rr * 0.9f), Size(rr * 3.4f, rr * 0.5f))
                drawRoundRect(Color(0xFF3F8FD6), Offset(-rr * 1.6f, -rr * 0.2f), Size(rr * 3.2f, rr * 1.2f), CornerRadius(rr * 0.25f))
                drawRoundRect(Color(0xFF2E6FAE), Offset(-rr * 1.6f, rr * 0.55f), Size(rr * 3.2f, rr * 0.45f), CornerRadius(rr * 0.2f))
                val scr = Path().apply { moveTo(-rr * 1.1f, -rr * 0.2f); lineTo(-rr * 0.85f, -rr * 1.2f); lineTo(rr * 0.85f, -rr * 1.2f); lineTo(rr * 1.1f, -rr * 0.2f); close() }
                drawPath(scr, Color(0xFF2E6FAE))
                val scr2 = Path().apply { moveTo(-rr * 0.95f, -rr * 0.3f); lineTo(-rr * 0.75f, -rr * 1.08f); lineTo(rr * 0.75f, -rr * 1.08f); lineTo(rr * 0.95f, -rr * 0.3f); close() }
                drawPath(scr2, if (m.phase == PH_ORDER && isReady()) Color(0xFFB8FFD9) else Color(0xFFD7F0FF))
                for (i in 0 until 4) for (j in 0 until 2) {
                    drawCircle(listOf(Color(0xFFFFD43B), Color(0xFFFF6B8A), Color(0xFF6BE08C), Color(0xFFFFFFFF))[(i + j) % 4], rr * 0.14f, Offset(-rr * 1.05f + i * rr * 0.7f, rr * 0.05f + j * rr * 0.32f))
                }
                if (m.phase == PH_COINS) drawRoundRect(Color(0xFF1F4E7C), Offset(-rr * 1.2f, rr * 0.75f), Size(rr * 2.4f, rr * 0.2f), CornerRadius(rr * 0.1f))
            }

            // кошелёк
            val wb = 1f + 0.18f * m.walletBump
            withTransform({ translate(walletX(), walletY()); scale(wb, wb, Offset.Zero) }) {
                drawOval(Color(0x33000000), Offset(-rr * 0.95f, rr * 0.55f), Size(rr * 1.9f, rr * 0.4f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFE59A5A), Color(0xFFA85F2A)), Offset(-rr * 0.2f, -rr * 0.2f), rr * 1.2f), rr * 0.85f, Offset.Zero)
                drawArc(Color(0xFF6B3A14), 200f, 140f, false, Offset(-rr * 0.55f, -rr * 0.7f), Size(rr * 1.1f, rr * 0.9f), style = Stroke(rr * 0.12f, cap = StrokeCap.Round))
                drawCircle(Color(0xFFFFD43B), rr * 0.18f, Offset(0f, rr * 0.08f))
                shStar(Offset(0f, rr * 0.08f), rr * 0.12f, Color(0xFFB87400), -PI.toFloat() / 2f)
            }

            // монетки
            for (c in m.coins) {
                if (c.state == 0 && c.t < c.delay) continue
                val flip = if (c.state == 0) cos(c.t * 14f) else if (c.state == 1) 1f - 0.12f * (sin(t * 3f + c.ph) * 0.5f + 0.5f) else 1f - shEase(c.t / 0.45f) * 0.35f
                val rs = rr * 0.62f * (if (c.big) 1.45f else 1f) * (if (c.state == 2) 1f - 0.4f * shEase(c.t / 0.45f) else 1f + (if (c.state == 1) 0.05f * sin(t * 6f + c.ph) else 0f))
                if (c.state == 1) drawCircle(Color(0x55FFF3A0), rs * 1.5f + sin(t * 5f + c.ph) * rs * 0.1f, Offset(c.x, c.y))
                shCoin(c.x, c.y, rs, c.col, t, flip)
            }

            // летящие обратно на витрину
            for (f in m.flies) shPelmen(f.type, f.x, f.y, rr, 0, t, 0f)

            // пельмешек в руке
            if (m.carry >= 0) {
                val wob = sin(t * 12f) * 4f
                shPelmen(m.carry, m.cx, m.cy - rr * 0.4f, rr * 1.12f, 1, t, 0f, 0f, wob)
            }

            // частицы
            for (p in m.parts) {
                val k = (p.life / p.maxLife).coerceIn(0f, 1f)
                when (p.kind) {
                    0 -> withTransform({ translate(p.x, p.y); scale(p.size * (0.5f + 0.5f * k), p.size * (0.5f + 0.5f * k), Offset.Zero) }) {
                        drawPath(SH_PATHS[SH_HEART], p.color.shA(k))
                    }
                    1 -> shStar(Offset(p.x, p.y), p.size * (0.4f + 0.6f * k), p.color.shA(k), p.rot)
                    2 -> shStar(Offset(p.x, p.y), p.size * (0.4f + 0.6f * k), p.color.shA(k), p.rot)
                    else -> drawCircle(p.color.shA(k), p.size, Offset(p.x, p.y))
                }
            }

            if (m.demo) shHand(Offset(m.hx, m.hy), min(w, h) * 0.04f, m.hDown, t)
        }

        // подсказка
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 12.dp, start = 12.dp, end = 84.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xE6FFFFFF))
                .border(2.dp, Color(0xFFFF9CC2), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(hintText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF9A3B63), textAlign = TextAlign.Center)
        }
        // шкала радости
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 64.dp, start = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xCCFFFFFF))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Радость", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF9A3B63))
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .width(90.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFFF6D3E1))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressPct / 100f)
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFF6B8A), Color(0xFFFF9CC2), Color(0xFFFFC93C))))
                )
            }
        }
        if (demoOn) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 98.dp, start = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE6E0689A))
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            ) { Text("Просто смотри — потрогай экран, чтобы играть", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White) }
        }

        // кнопка альбома
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 12.dp)
                .size(60.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFD8A0FF), Color(0xFF9B59E0))))
                .border(3.dp, Color.White, CircleShape)
                .clickable { stopDemo(); m.lastTouch = m.clock; showAlbum = true; snd("fx_sh_pick", 0.8f) },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(40.dp)) { shStickerArt(2, Offset(size.width / 2, size.height / 2), size.width * 0.42f) }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 74.dp, end = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xCCFFFFFF))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Продано:", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF9A3B63))
            Text(" $served", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF9A3B63))
        }

        // альбом «Лучший продавец»
        if (showAlbum) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x992A1030))
                    .clickable { showAlbum = false; m.lastTouch = m.clock },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFFFF4F8))
                        .border(5.dp, Color(0xFFE88FB5), RoundedCornerShape(28.dp))
                        .clickable(enabled = false) {}
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Альбом «Лучший продавец»", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color(0xFF9A3B63))
                    Row {
                        Text("Обслужено покупателей:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB0507A))
                        Text(" $served", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB0507A))
                    }
                    Spacer(Modifier.height(8.dp))
                    SH_STICKERS.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { st ->
                                val unlocked = served >= st.need
                                Column(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (unlocked) Color(0xFFFFE1EE) else Color(0xFFEDE6EA))
                                        .padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Canvas(Modifier.size(70.dp)) {
                                        if (unlocked) shStickerArt(st.id, Offset(size.width / 2, size.height / 2), size.width * 0.4f)
                                        else {
                                            drawCircle(Color(0x22000000), size.width * 0.38f, Offset(size.width / 2, size.height / 2))
                                            drawRoundRect(Color(0xFF9A8A92), Offset(size.width * 0.36f, size.height * 0.46f), Size(size.width * 0.28f, size.height * 0.22f), CornerRadius(6f))
                                            drawArc(Color(0xFF9A8A92), 180f, 180f, false, Offset(size.width * 0.4f, size.height * 0.3f), Size(size.width * 0.2f, size.height * 0.2f), style = Stroke(5f))
                                        }
                                    }
                                    if (unlocked) {
                                        Text(st.name, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF9A3B63), textAlign = TextAlign.Center, maxLines = 2)
                                    } else {
                                        Row {
                                            Text("Ещё", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF9A3B63))
                                            Text(" ${st.need - served}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF9A3B63))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // награда
        val rs = rewardSticker
        if (rs != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xAA2A1030))
                    .clickable { rewardSticker = null; m.lastTouch = m.clock },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFFFF4F8))
                        .border(5.dp, Color(0xFFE88FB5), RoundedCornerShape(28.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Покупатель доволен!", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFFE0689A))
                    Canvas(Modifier.size(150.dp)) { shStickerArt(rs.id, Offset(size.width / 2, size.height / 2), size.width * 0.42f) }
                    Text(rs.name, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF9A3B63))
                    val note = rs.note
                    if (note != null) {
                        Spacer(Modifier.height(6.dp))
                        Text("Новый товар на витрине:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB0507A))
                        Text(note, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFE0780A))
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFE0689A))
                            .padding(horizontal = 28.dp, vertical = 8.dp)
                    ) { Text("Следующий покупатель", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White) }
                }
            }
        }
    }
}
