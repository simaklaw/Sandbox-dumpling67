package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaPlayer
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.LocalAgeProfile
import com.example.shared.LoopHandle
import com.example.shared.SoundManager
import com.example.shared.i18n.Text
import com.example.shared.rememberHaptics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// Комната «Автомойка для игрушек».
// Грязная игрушка въезжает на мойку -> губка (пена стирает грязь) -> душ (смывает пену) -> фен (сушит и наводит блеск)
// -> игрушка сигналит и ТАНЦУЕТ под музыку (качается с боку на бок, поднимая то один, то другой бок, прыгает, крутится),
// потом танцуя уезжает. Награды: наклейки альбома «Главный мойщик» и тюнинг (диски, корона, неон).
// Музыка — три оригинальные мелодии cw2_dance_*.ogg (generate_carwash_music.py), по одной на машинку, автобус и паровоз.

private const val CW_PREFS = "carwash_prefs"
private const val CW_GX = 36
private const val CW_GY = 22
private const val CW_U0 = -1.05f
private const val CW_V0 = -0.85f
private const val CW_UW = 2.1f
private const val CW_VH = 1.5f

private const val CW_SPONGE = 0
private const val CW_SHOWER = 1
private const val CW_DRYER = 2

private const val TY_CAR = 0
private const val TY_BUS = 1
private const val TY_TRAIN = 2

// Танец чистой игрушки. Темп каждой мелодии (cw2_dance_*.ogg), 6 тактов по 4 доли:
// 16 долей игрушка танцует на месте, ещё 8 долей танцуя уезжает; меняя темп, перегенерируй музыку.
private val CW_BPM = floatArrayOf(130f, 120f, 126f)
private const val CW_LEAD = 0.5f          // пауза перед музыкой: успевают прозвучать «ура» и гудок
private const val CW_SHOW_BEATS = 16f
private const val CW_GO_BEATS = 8f
private val CW_DANCE_RES = intArrayOf(R.raw.cw2_dance_car, R.raw.cw2_dance_bus, R.raw.cw2_dance_train)

// Точки касания земли левого и правого колеса (u, v): вокруг них игрушка наклоняется, приподнимая другой бок
private val CW_PIV = arrayOf(
    floatArrayOf(-0.58f, 0.75f, 0.58f, 0.75f),
    floatArrayOf(-0.6f, 0.75f, 0.6f, 0.75f),
    floatArrayOf(-0.65f, 0.79f, 0.62f, 0.79f)
)
private val CW_CHEER = listOf("Блестит!", "Чистюля!", "Красота!", "Ура!")
private val CW_NOTE_COLORS = listOf(Color(0xFFFF4DA6), Color(0xFF3FD0FF), Color(0xFFFFC21A), Color(0xFF3FD07A), Color(0xFF9B6BFF))
private val CW_CONFETTI = listOf(Color(0xFFFF4DA6), Color(0xFF3FD0FF), Color(0xFFFFD54A), Color(0xFF3FD07A), Color(0xFFFF7A3C), Color(0xFF9B6BFF))

private class CwSticker(val id: Int, val name: String, val need: Int, val upgrade: String?)

private val CW_STICKERS = listOf(
    CwSticker(0, "Чистюля", 1, null),
    CwSticker(1, "Супер-Губка", 2, "Яркие диски"),
    CwSticker(2, "Мастер Воды", 4, "Корона-спойлер"),
    CwSticker(3, "Золотой Блеск", 7, "Неоновая подсветка"),
    CwSticker(4, "Герой Мойки", 10, null),
    CwSticker(5, "Король Пельмешек", 14, null)
)

private class CwPart(
    var kind: Int, var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float, val size: Float, val color: Color, var rot: Float, val vr: Float, val gy: Float
)

private class CwAuto(val kind: Int, val real: Boolean, val sx: Float, val sy: Float, val actDur: Float) {
    var t = 0f
}

private class CwModel {
    var clock = 0f
    var lastTouch = 0f
    var demo = false
    var demoT = 0f
    var handX = 0f
    var handY = 0f
    var handTx = 0f
    var handTy = 0f
    var handDown = false

    var toyType = 0
    var toyIdx = 0
    var phase = 0          // 0 въезжает, 1 моется, 2 радуется, 3 уезжает
    var phaseT = 0f
    var offX = 0f
    var bounce = 0f
    var wheelRot = 0f
    var sadShake = 0f
    var hop = 0f
    var blink = 0f
    var nextBlink = 2f
    var toyReal = false
    var rewardPending: CwSticker? = null
    var waitPopup = false

    // танец и музыка
    var musicStarted = false
    var musicFading = false
    var musicGain = 1f
    var musicVol = 0.6f
    var bgPaused = false
    var skipped = false
    var lastBeat = -1
    var dAng = 0f
    var dPivU = 0f
    var dPivV = 0.75f
    var dHop = 0f
    var dSq = 1f
    var dFlip = 1f
    var dShift = 0f
    var dBeat = 0f
    var showK = 0f
    var starT = 0f
    var bannerT = 0f

    val dirt = FloatArray(CW_GX * CW_GY)
    val foam = FloatArray(CW_GX * CW_GY)
    val wet = FloatArray(CW_GX * CW_GY)
    var mask = BooleanArray(CW_GX * CW_GY)
    var maskN = 1
    var gridDirty = true
    var dirtF = 1f
    var foamF = 0f
    var wetF = 0f
    var stage = 1

    val parts = ArrayList<CwPart>()

    var carry = -1
    var cx = 0f
    var cy = 0f
    var carryReal = true
    var carryFromBar = false
    var downX = 0f
    var downY = 0f
    var downT = 0f
    var travel = 0f
    var moveDist = 0f
    var using = false
    var auto: CwAuto? = null

    var tScrub = 0f
    var tSpray = 0f
    var tBlow = 0f
    var tRinse = 0f
    var tFoam = 0f
    var tSpark = 0f
    var tDrip = 0f
    var hint = ""
    var hintTimer = 0f
    var pulseSlot = -1
    var barrier = 1f
    var brushSpin = 0f
}

private val CW_BUBBLES: List<FloatArray> = run {
    val r = Random(21)
    List(260) { floatArrayOf(CW_U0 + r.nextFloat() * CW_UW, CW_V0 + r.nextFloat() * CW_VH, 0.045f + r.nextFloat() * 0.09f) }
}
private val CW_DROPS: List<FloatArray> = run {
    val r = Random(33)
    List(150) { floatArrayOf(CW_U0 + r.nextFloat() * CW_UW, CW_V0 + r.nextFloat() * CW_VH, 0.02f + r.nextFloat() * 0.03f) }
}
private val CW_NOISE: FloatArray = run {
    val r = Random(5)
    FloatArray(CW_GX * 2 * CW_GY * 2) { r.nextFloat() }
}

private fun cwEase(t: Float): Float { val k = t.coerceIn(0f, 1f); return k * k * (3f - 2f * k) }
private fun cwLerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun Color.cwA(a: Float): Color = copy(alpha = (alpha * a).coerceIn(0f, 1f))

// ───────────────────────── формы игрушек ─────────────────────────

private fun cwInside(type: Int, u: Float, v: Float): Boolean = when (type) {
    TY_CAR -> {
        val a = (u / 1.0f) * (u / 1.0f) + ((v - 0.05f) / 0.5f) * ((v - 0.05f) / 0.5f) <= 1f
        val b = (u / 0.62f) * (u / 0.62f) + ((v + 0.25f) / 0.5f) * ((v + 0.25f) / 0.5f) <= 1f
        a || b
    }
    TY_BUS -> u in -1.0f..1.0f && v in -0.7f..0.45f
    else -> (u in -1.0f..0.25f && v in -0.35f..0.45f) || (u in 0.2f..1.0f && v in -0.75f..0.45f) || (u in -0.8f..-0.5f && v in -0.6f..-0.35f)
}

private val CW_SHAPES: Map<Int, Path> by lazy {
    val car = Path().apply {
        addOval(Rect(Offset(-1.0f, -0.45f), Size(2.0f, 1.0f)))
        addOval(Rect(Offset(-0.62f, -0.75f), Size(1.24f, 1.0f)))
    }
    val bus = Path().apply { addRoundRect(RoundRect(-1.0f, -0.7f, 1.0f, 0.45f, CornerRadius(0.18f))) }
    val train = Path().apply {
        addRect(Rect(Offset(-1.0f, -0.35f), Size(1.25f, 0.8f)))
        addRect(Rect(Offset(0.2f, -0.75f), Size(0.8f, 1.2f)))
        addRect(Rect(Offset(-0.8f, -0.6f), Size(0.3f, 0.25f)))
    }
    mapOf(TY_CAR to car, TY_BUS to bus, TY_TRAIN to train)
}

private fun cwBodyColor(t: Int): Color = when (t) {
    TY_CAR -> Color(0xFFFFD166)
    TY_BUS -> Color(0xFF06D6A0)
    else -> Color(0xFF2FA8E0)
}

private fun cwAccent(t: Int): Color = when (t) {
    TY_CAR -> Color(0xFFEF476F)
    TY_BUS -> Color(0xFFFFD166)
    else -> Color(0xFFEF476F)
}

private fun cwUpgrades(washed: Int) = when {
    washed >= 7 -> 3
    washed >= 4 -> 2
    washed >= 2 -> 1
    else -> 0
}

private fun DrawScope.cwStar(c: Offset, r: Float, color: Color, rot: Float) {
    val p = Path()
    for (i in 0 until 8) {
        val a = rot + i * PI.toFloat() / 4f
        val rr = if (i % 2 == 0) r else r * 0.3f
        val x = c.x + cos(a) * rr
        val y = c.y + sin(a) * rr
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}

private fun DrawScope.cwWheel(x: Float, y: Float, r: Float, rot: Float, upg: Int) {
    drawCircle(Color(0xFF1E293B), r, Offset(x, y))
    val rim = if (upg >= 1) Color(0xFFFF4DA6) else Color(0xFF9FB0C4)
    drawCircle(rim, r * 0.58f, Offset(x, y))
    for (i in 0 until 5) {
        val a = rot + i * 2f * PI.toFloat() / 5f
        drawLine(Color(0xFF1E293B).cwA(0.55f), Offset(x, y), Offset(x + cos(a) * r * 0.55f, y + sin(a) * r * 0.55f), r * 0.1f, StrokeCap.Round)
    }
    drawCircle(Color.White.cwA(0.8f), r * 0.14f, Offset(x, y))
}

private fun DrawScope.cwFace(x: Float, y: Float, k: Float, mood: Int, blink: Float, shake: Float) {
    val ink = Color(0xFF2A1A12)
    val sx = x + sin(shake * 40f) * 0.02f * shake
    for (sg in listOf(-1f, 1f)) {
        val ex = sx + sg * 0.22f * k
        val er = 0.1f * k
        if (mood == 3) {
            // поёт и жмурится от радости: глазки-«домики»
            drawArc(ink, 200f, 140f, false, Offset(ex - er, y - er * 0.5f), Size(er * 2f, er * 1.5f), style = Stroke(0.05f * k, cap = StrokeCap.Round))
        } else if (blink > 0f || mood == 0) {
            if (mood == 0) {
                // грустные полуприкрытые глазки
                drawArc(ink, 0f, 180f, false, Offset(ex - er, y - er), Size(er * 2f, er * 1.6f), style = Stroke(0.045f * k, cap = StrokeCap.Round))
                drawLine(ink, Offset(ex - er * 1.3f * sg, y - er * 1.5f - 0.02f), Offset(ex + er * 1.0f * sg, y - er * 1.0f - 0.06f * k), 0.035f * k, StrokeCap.Round)
            } else drawLine(ink, Offset(ex - er, y), Offset(ex + er, y), 0.045f * k, StrokeCap.Round)
        } else {
            drawCircle(Color.White, er * 1.35f, Offset(ex, y))
            drawCircle(ink, er, Offset(ex + 0.01f * k, y + 0.005f))
            drawCircle(Color.White, er * 0.36f, Offset(ex + er * 0.35f, y - er * 0.4f))
        }
        if (mood >= 2) drawCircle(Color(0xFFFF8FA3).cwA(0.6f), 0.07f * k, Offset(ex + sg * 0.13f * k, y + 0.14f * k))
    }
    when (mood) {
        0 -> drawArc(ink, 200f, 140f, false, Offset(sx - 0.1f * k, y + 0.2f * k), Size(0.2f * k, 0.14f * k), style = Stroke(0.04f * k, cap = StrokeCap.Round))
        1 -> drawLine(ink, Offset(sx - 0.07f * k, y + 0.2f * k), Offset(sx + 0.07f * k, y + 0.2f * k), 0.04f * k, StrokeCap.Round)
        3 -> {
            drawArc(ink, 0f, 180f, true, Offset(sx - 0.15f * k, y + 0.07f * k), Size(0.3f * k, 0.3f * k))
            drawArc(Color(0xFFFF8FA3), 20f, 140f, true, Offset(sx - 0.09f * k, y + 0.2f * k), Size(0.18f * k, 0.14f * k))
        }
        else -> drawArc(ink, 10f, 160f, false, Offset(sx - 0.14f * k, y + 0.1f * k), Size(0.28f * k, 0.2f * k), style = Stroke(0.045f * k, cap = StrokeCap.Round))
    }
}

private fun DrawScope.cwToy(type: Int, upg: Int, mood: Int, blink: Float, shake: Float, wheelRot: Float, clock: Float, beat: Float = 0f, shadow: Boolean = true) {
    val body = cwBodyColor(type)
    val acc = cwAccent(type)
    val outline = Color(0xFF334155)
    // неон
    if (upg >= 3) {
        val glow = 0.55f + 0.2f * sin(clock * 4f)
        drawOval(Brush.radialGradient(listOf(Color(0xFF3CF0FF).cwA(glow), Color(0x003CF0FF)), Offset(0f, 0.75f), 1.3f), Offset(-1.3f, 0.45f), Size(2.6f, 0.7f))
    }
    if (shadow) drawOval(Color(0x44000000), Offset(-1.05f, 0.62f), Size(2.1f, 0.3f))
    val shape = CW_SHAPES[type] ?: return
    when (type) {
        TY_CAR -> {
            // кабина (цветная) и кузов
            drawOval(acc, Offset(-0.62f, -0.75f), Size(1.24f, 1.0f))
            drawOval(Color(0xFFD6F0FF), Offset(-0.5f, -0.66f), Size(1.0f, 0.82f))
            drawOval(body, Offset(-1.0f, -0.45f), Size(2.0f, 1.0f))
            drawOval(outline, Offset(-1.0f, -0.45f), Size(2.0f, 1.0f), style = Stroke(0.06f))
            drawArc(outline, 190f, 160f, false, Offset(-0.62f, -0.75f), Size(1.24f, 1.0f), style = Stroke(0.06f, cap = StrokeCap.Round))
            drawOval(Color.White.cwA(0.35f), Offset(-0.75f, -0.32f), Size(0.55f, 0.16f))
            drawCircle(Color(0xFFFFF3A0), 0.1f, Offset(0.88f, 0.08f))
            if (beat > 0.02f) drawCircle(Color(0xFFFFF3A0).cwA(0.55f * beat), 0.28f, Offset(0.88f, 0.08f))
            drawCircle(Color(0xFFEF476F), 0.07f, Offset(-0.9f, 0.1f))
            drawCircle(Color.White.cwA(0.7f), 0.04f, Offset(0.86f, 0.05f))
            cwFace(0f, -0.28f, 1f, mood, blink, shake)
            cwWheel(-0.58f, 0.5f, 0.25f, wheelRot, upg)
            cwWheel(0.58f, 0.5f, 0.25f, wheelRot, upg)
            if (upg >= 2) cwCrown(0f, -0.76f, 0.3f)
        }
        TY_BUS -> {
            drawPath(shape, outline, style = Stroke(0.14f, join = StrokeJoin.Round))
            drawPath(shape, Brush.verticalGradient(listOf(body, Color(0xFF05B58A)), -0.7f, 0.45f))
            drawRect(acc, Offset(-1.0f, 0.18f), Size(2.0f, 0.1f))
            for (i in 0 until 3) {
                val wx = -0.85f + i * 0.5f
                drawRoundRect(Color(0xFFD6F0FF), Offset(wx, -0.55f), Size(0.4f, 0.38f), CornerRadius(0.06f))
                drawRoundRect(outline, Offset(wx, -0.55f), Size(0.4f, 0.38f), CornerRadius(0.06f), style = Stroke(0.035f))
            }
            drawRoundRect(Color(0xFFD6F0FF), Offset(0.6f, -0.58f), Size(0.35f, 0.5f), CornerRadius(0.08f))
            drawOval(Color(0xFFFF9F43), Offset(0.88f, -0.05f), Size(0.36f, 0.22f))
            drawOval(outline, Offset(0.88f, -0.05f), Size(0.36f, 0.22f), style = Stroke(0.035f))
            cwFace(0.775f, -0.34f, 0.6f, mood, blink, shake)
            drawCircle(Color(0xFFFFF3A0), 0.07f, Offset(0.92f, 0.28f))
            if (beat > 0.02f) drawCircle(Color(0xFFFFF3A0).cwA(0.55f * beat), 0.22f, Offset(0.92f, 0.28f))
            cwWheel(-0.6f, 0.5f, 0.25f, wheelRot, upg)
            cwWheel(0.6f, 0.5f, 0.25f, wheelRot, upg)
            if (upg >= 2) cwCrown(-0.2f, -0.7f, 0.3f)
        }
        else -> {
            drawPath(shape, outline, style = Stroke(0.14f, join = StrokeJoin.Round))
            drawRect(body, Offset(-1.0f, -0.35f), Size(1.25f, 0.8f))
            drawRect(acc, Offset(0.2f, -0.75f), Size(0.8f, 1.2f))
            drawRect(Color(0xFFFFD166), Offset(0.15f, -0.82f), Size(0.9f, 0.1f))
            drawRect(Color(0xFF334155), Offset(-0.8f, -0.6f), Size(0.3f, 0.25f))
            drawRect(Color(0xFFFFD166), Offset(-0.84f, -0.66f), Size(0.38f, 0.08f))
            drawCircle(Color(0xFFFFF3A0), 0.12f, Offset(-0.95f, 0.05f))
            if (beat > 0.02f) drawCircle(Color(0xFFFFF3A0).cwA(0.55f * beat), 0.3f, Offset(-0.95f, 0.05f))
            drawRoundRect(Color(0xFFD6F0FF), Offset(0.3f, -0.62f), Size(0.55f, 0.4f), CornerRadius(0.07f))
            cwFace(0.575f, -0.4f, 0.6f, mood, blink, shake)
            drawRect(Color.White.cwA(0.3f), Offset(-0.9f, -0.28f), Size(1.0f, 0.1f))
            cwWheel(-0.65f, 0.55f, 0.24f, wheelRot, upg)
            cwWheel(-0.05f, 0.55f, 0.24f, wheelRot, upg)
            cwWheel(0.62f, 0.55f, 0.24f, wheelRot, upg)
            if (upg >= 2) cwCrown(0.6f, -0.82f, 0.3f)
        }
    }
}

/** Поза танца на доле bt (0..24): качание с боку на бок вокруг опорного колеса, прыжки, поворот, шаг «чух-чух». */
private fun cwDancePose(m: CwModel, type: Int, bt: Float) {
    val pv = CW_PIV[type]
    val fr = bt - floor(bt)
    val lift = sin(PI.toFloat() * bt)          // больше нуля — приподнят левый бок, меньше — правый
    val heavy = if (type == 1) 0.8f else 1f    // автобус потяжелее
    var amp = 0f
    var hop = 0f
    var sq = 1f
    var flip = 1f
    when {
        bt < 4f -> amp = 9f                    // такт 1: плавно качается
        bt < 8f -> amp = 14f                   // такт 2: качается сильнее
        bt < 12f -> {                          // такт 3: прыгает на каждую долю
            amp = 5f
            hop = 0.17f * sin(PI.toFloat() * fr) * heavy
            sq = 1f + 0.09f * cos(2f * PI.toFloat() * fr) * (if (type == 1) 1.4f else 1f)
        }
        bt < 14f -> amp = 12f
        bt < 16f -> {                          // разворот вокруг себя
            amp = 4f
            flip = cos(PI.toFloat() * (bt - 14f))
            hop = 0.1f * sin(PI.toFloat() * (bt - 14f) / 2f)
        }
        else -> {                              // танцуя уезжает
            amp = 8f
            hop = 0.03f * abs(lift)
        }
    }
    m.dShift = if (type == 2) 0.06f * lift else 0f   // паровозик ещё и «чухает» вперёд-назад
    if (type == 2) amp *= 0.6f
    m.dAng = amp * lift
    if (lift >= 0f) { m.dPivU = pv[2]; m.dPivV = pv[3] } else { m.dPivU = pv[0]; m.dPivV = pv[1] }
    m.dHop = hop
    m.dSq = sq
    m.dFlip = flip
    m.dBeat = (1f - fr) * (1f - fr)
}

private fun DrawScope.cwNote(c: Offset, r: Float, color: Color) {
    drawOval(color, Offset(c.x - r * 0.55f, c.y - r * 0.35f), Size(r * 1.1f, r * 0.8f))
    drawLine(color, Offset(c.x + r * 0.5f, c.y), Offset(c.x + r * 0.5f, c.y - r * 1.9f), r * 0.2f, StrokeCap.Round)
    drawLine(color, Offset(c.x + r * 0.5f, c.y - r * 1.9f), Offset(c.x + r * 1.3f, c.y - r * 1.2f), r * 0.22f, StrokeCap.Round)
}

/** Музыка танца (MediaPlayer, как в «Колыбельной»): файлы длинные, SoundPool для них не годится. */
private class CwMusic(private val ctx: Context) {
    private var mp: MediaPlayer? = null
    private var pausedByUs = false

    val playing: Boolean
        get() = try { mp?.isPlaying == true } catch (e: IllegalStateException) { false }

    fun start(resId: Int, volume: Float) {
        stop()
        try {
            val p = MediaPlayer.create(ctx, resId) ?: return
            p.setVolume(volume, volume)
            p.setOnCompletionListener { it.release(); if (mp === it) mp = null }
            p.start()
            mp = p
        } catch (e: Exception) {
            android.util.Log.w("CarWash", "dance music failed", e)
        }
    }

    fun setVolume(v: Float) {
        try { mp?.setVolume(v, v) } catch (e: IllegalStateException) { android.util.Log.w("CarWash", "music volume", e) }
    }

    fun pause() {
        try {
            val p = mp
            if (p != null && p.isPlaying) { p.pause(); pausedByUs = true }
        } catch (e: IllegalStateException) { android.util.Log.w("CarWash", "music pause", e) }
    }

    fun resume() {
        if (!pausedByUs) return
        pausedByUs = false
        try { mp?.start() } catch (e: IllegalStateException) { android.util.Log.w("CarWash", "music resume", e) }
    }

    fun stop() {
        val p = mp
        mp = null
        pausedByUs = false
        if (p != null) {
            try { p.release() } catch (e: IllegalStateException) { android.util.Log.w("CarWash", "music release", e) }
        }
    }
}

private fun DrawScope.cwCrown(cx: Float, by: Float, s: Float) {
    val p = Path().apply {
        moveTo(cx - s, by); lineTo(cx - s, by - s * 0.9f); lineTo(cx - s * 0.5f, by - s * 0.4f)
        lineTo(cx, by - s * 1.2f); lineTo(cx + s * 0.5f, by - s * 0.4f); lineTo(cx + s, by - s * 0.9f); lineTo(cx + s, by); close()
    }
    drawPath(p, Color(0xFFFFD54A))
    drawPath(p, Color(0xFFC9A000), style = Stroke(0.03f, join = StrokeJoin.Round))
    drawCircle(Color(0xFFEF476F), 0.04f, Offset(cx, by - s * 0.4f))
}

private fun DrawScope.cwSponge(cx: Float, cy: Float, s: Float, rot: Float) {
    drawOval(Color(0x2A000000), Offset(cx - s * 0.8f, cy + s * 0.55f), Size(s * 1.6f, s * 0.3f))
    withTransform({ translate(cx, cy); rotate(rot, Offset.Zero); scale(s, s, Offset.Zero) }) {
        drawRoundRect(Color(0xFFC98A00), Offset(-0.9f, -0.5f), Size(1.8f, 1.05f), CornerRadius(0.28f), style = Stroke(0.12f))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFE066), Color(0xFFFFC21A)), -0.5f, 0.55f), Offset(-0.9f, -0.5f), Size(1.8f, 1.05f), CornerRadius(0.28f))
        drawRoundRect(Color(0xFF3FD07A), Offset(-0.9f, 0.3f), Size(1.8f, 0.25f), CornerRadius(0.12f))
        for ((px, py) in listOf(-0.5f to -0.2f, 0.1f to -0.3f, 0.55f to -0.05f, -0.15f to 0.05f, 0.35f to 0.15f, -0.65f to 0.12f)) {
            drawCircle(Color(0xFFC98A00).cwA(0.7f), 0.09f, Offset(px, py))
        }
        drawRoundRect(Color.White.cwA(0.4f), Offset(-0.75f, -0.4f), Size(0.8f, 0.12f), CornerRadius(0.06f))
    }
}

private fun DrawScope.cwShowerHead(cx: Float, cy: Float, s: Float, on: Boolean) {
    drawOval(Color(0x2A000000), Offset(cx - s * 0.7f, cy + s * 0.9f), Size(s * 1.4f, s * 0.25f))
    withTransform({ translate(cx, cy); scale(s, s, Offset.Zero) }) {
        drawLine(Color(0xFF2E7BD0), Offset(0f, -1.3f), Offset(0f, -0.35f), 0.3f, StrokeCap.Round)
        drawLine(Color(0xFF5CAEFF), Offset(0f, -1.3f), Offset(0f, -0.35f), 0.2f, StrokeCap.Round)
        val head = Path().apply {
            moveTo(-0.28f, -0.4f); lineTo(0.28f, -0.4f); lineTo(0.85f, 0.45f); lineTo(-0.85f, 0.45f); close()
        }
        drawPath(head, Color(0xFF2E7BD0), style = Stroke(0.14f, join = StrokeJoin.Round))
        drawPath(head, Brush.verticalGradient(listOf(Color(0xFF8CCBFF), Color(0xFF4FA6F5)), -0.4f, 0.45f))
        drawOval(Color(0xFF2E7BD0), Offset(-0.9f, 0.32f), Size(1.8f, 0.35f))
        for (i in -3..3) drawCircle(if (on) Color(0xFFE8F6FF) else Color(0xFF1F5FA8), 0.05f, Offset(i * 0.23f, 0.5f))
        drawRoundRect(Color.White.cwA(0.4f), Offset(-0.18f, -0.25f), Size(0.1f, 0.5f), CornerRadius(0.05f))
    }
}

private fun DrawScope.cwDryer(cx: Float, cy: Float, s: Float, on: Boolean) {
    drawOval(Color(0x2A000000), Offset(cx - s * 0.8f, cy + s * 0.9f), Size(s * 1.6f, s * 0.25f))
    withTransform({ translate(cx, cy); rotate(-18f, Offset.Zero); scale(s, s, Offset.Zero) }) {
        // ручка
        drawRoundRect(Color(0xFFB8501A), Offset(-0.18f, 0.2f), Size(0.4f, 1.05f), CornerRadius(0.15f))
        drawRoundRect(Color(0xFFFF8A4C), Offset(-0.12f, 0.2f), Size(0.28f, 1.0f), CornerRadius(0.12f))
        // корпус
        drawRoundRect(Color(0xFFB8501A), Offset(-0.95f, -0.62f), Size(1.9f, 0.95f), CornerRadius(0.4f), style = Stroke(0.14f))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFB066), Color(0xFFFF7A3C)), -0.62f, 0.33f), Offset(-0.95f, -0.62f), Size(1.9f, 0.95f), CornerRadius(0.4f))
        // сопло
        drawRoundRect(Color(0xFF444B5A), Offset(-1.15f, -0.5f), Size(0.4f, 0.7f), CornerRadius(0.1f))
        drawRoundRect(if (on) Color(0xFFBFE6FF) else Color(0xFF2A303C), Offset(-1.1f, -0.4f), Size(0.2f, 0.5f), CornerRadius(0.06f))
        drawCircle(Color(0xFFFFE066), 0.1f, Offset(0.5f, -0.15f))
        drawRoundRect(Color.White.cwA(0.4f), Offset(-0.6f, -0.52f), Size(1.0f, 0.12f), CornerRadius(0.06f))
    }
}

private fun DrawScope.cwHand(c: Offset, r: Float, down: Boolean) {
    val k = if (down) 0.85f else 1f
    drawCircle(Color.White.copy(alpha = 0.35f), r * 1.7f * k, c)
    drawCircle(Color.White.copy(alpha = 0.9f), r * k, c)
    drawCircle(Color(0xFF2E7BD0), r * k, c, style = Stroke(3f))
}

private fun DrawScope.cwStickerArt(id: Int, c: Offset, r: Float) {
    drawCircle(Color(0xFFE6F6FF), r * 1.05f, c)
    drawCircle(Color(0xFF5CAEFF), r * 1.05f, c, style = Stroke(r * 0.09f))
    when (id) {
        0 -> {
            drawCircle(Color(0xFFBFE6FF), r * 0.6f, c)
            drawCircle(Color.White, r * 0.6f, c, style = Stroke(r * 0.08f))
            drawCircle(Color.White, r * 0.14f, Offset(c.x - r * 0.22f, c.y - r * 0.24f))
            drawCircle(Color(0xFFBFE6FF), r * 0.22f, Offset(c.x + r * 0.45f, c.y + r * 0.3f))
        }
        1 -> withTransform({ translate(c.x, c.y); scale(r * 0.6f, r * 0.6f, Offset.Zero) }) {
            drawRoundRect(Color(0xFFC98A00), Offset(-0.9f, -0.5f), Size(1.8f, 1.05f), CornerRadius(0.28f), style = Stroke(0.12f))
            drawRoundRect(Color(0xFFFFD21A), Offset(-0.9f, -0.5f), Size(1.8f, 1.05f), CornerRadius(0.28f))
            drawRoundRect(Color(0xFF3FD07A), Offset(-0.9f, 0.3f), Size(1.8f, 0.25f), CornerRadius(0.12f))
            drawCircle(Color(0xFFC98A00).cwA(0.7f), 0.09f, Offset(-0.4f, -0.15f))
            drawCircle(Color(0xFFC98A00).cwA(0.7f), 0.09f, Offset(0.3f, -0.2f))
        }
        2 -> {
            val p = Path().apply {
                moveTo(c.x, c.y - r * 0.7f)
                cubicTo(c.x + r * 0.7f, c.y - r * 0.1f, c.x + r * 0.6f, c.y + r * 0.65f, c.x, c.y + r * 0.65f)
                cubicTo(c.x - r * 0.6f, c.y + r * 0.65f, c.x - r * 0.7f, c.y - r * 0.1f, c.x, c.y - r * 0.7f)
                close()
            }
            drawPath(p, Color(0xFF4FB3FF))
            drawCircle(Color.White.cwA(0.7f), r * 0.12f, Offset(c.x - r * 0.2f, c.y))
        }
        3 -> {
            cwStar(c, r * 0.8f, Color(0xFFFFD54A), 0f)
            cwStar(Offset(c.x + r * 0.55f, c.y - r * 0.5f), r * 0.28f, Color.White, 0f)
        }
        4 -> {
            drawCircle(Color(0xFFFFC93C), r * 0.6f, c)
            drawCircle(Color(0xFFC9A000), r * 0.6f, c, style = Stroke(r * 0.07f))
            cwStar(c, r * 0.4f, Color.White, -PI.toFloat() / 2f)
        }
        else -> withTransform({ translate(c.x, c.y + r * 0.1f); scale(r * 0.9f, r * 0.9f, Offset.Zero) }) { cwCrown(0f, 0.5f, 0.75f) }
    }
}

// ───────────────────────── экран ─────────────────────────

@Composable
fun CarWashRoomScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    bgMusicPlaying: () -> Boolean = { false },
    setBgMusic: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val sm = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val toddler = LocalAgeProfile.current.isToddler2
    val prefs = remember { context.getSharedPreferences(CW_PREFS, Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) {
        sm.preloadRaw(
            listOf(
                "fx_cw_scrub", "fx_cw_spray", "fx_cw_blow", "fx_cw_beep", "fx_cw_sigh", "fx_cw_drive",
                "wl_pin", "wl_pop",
                "cw2_arrive", "cw2_brush_loop", "cw2_dryer_loop", "cw2_foam", "cw2_honk", "cw2_reward", "cw2_rinse", "cw2_scrub", "cw2_sparkle", "cw2_spray"
            )
        )
    }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(0) }
    val m = remember { CwModel() }
    val dirtBmp = remember { Bitmap.createBitmap(CW_GX * 2, CW_GY * 2, Bitmap.Config.ARGB_8888) }
    val foamBmp = remember { Bitmap.createBitmap(CW_GX * 2, CW_GY * 2, Bitmap.Config.ARGB_8888) }
    val wetBmp = remember { Bitmap.createBitmap(CW_GX * 2, CW_GY * 2, Bitmap.Config.ARGB_8888) }
    var dirtImg by remember { mutableStateOf<ImageBitmap?>(null) }
    var foamImg by remember { mutableStateOf<ImageBitmap?>(null) }
    var wetImg by remember { mutableStateOf<ImageBitmap?>(null) }

    var washed by remember { mutableIntStateOf(prefs.getInt("washed", 0)) }
    var hintText by remember { mutableStateOf("") }
    var progressPct by remember { mutableIntStateOf(0) }
    var showAlbum by remember { mutableStateOf(false) }
    var rewardSticker by remember { mutableStateOf<CwSticker?>(null) }
    var demoOn by remember { mutableStateOf(false) }
    var bannerOn by remember { mutableStateOf(false) }
    var bannerText by remember { mutableStateOf("") }
    var bannerPhase by remember { mutableFloatStateOf(0f) }
    var counterPulse by remember { mutableFloatStateOf(0f) }
    var showSkip by remember { mutableStateOf(false) }
    var musicOn by remember { mutableStateOf(prefs.getBoolean("dance_music", true)) }
    val musicOnState = rememberUpdatedState(musicOn)
    val music = remember { CwMusic(context.applicationContext) }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun W() = boxSize.width.toFloat()
    fun H() = boxSize.height.toFloat()
    fun snd(n: String, v: Float = 1f, r: Float = 1f) { sm.play(n, volume = v, rate = r) }

    // зацикленные звуки (щётка, фен): живут, пока их «удерживают» каждый кадр, потом плавно гаснут
    val loopH = remember { HashMap<String, LoopHandle>() }
    val loopT = remember { HashMap<String, Float>() }
    fun hold(name: String, vol: Float) {
        loopT[name] = m.clock
        val cur = loopH[name]
        if (cur != null && cur.streamId <= 0) { sm.stopLoop(cur, 0); loopH.remove(name) }
        if (!loopH.containsKey(name)) {
            sm.startLoop(name)?.let { sm.setLoopVolume(it, vol); loopH[name] = it }
        }
    }
    fun tickLoops() {
        val itr = loopH.entries.iterator()
        while (itr.hasNext()) {
            val e = itr.next()
            if (m.clock - (loopT[e.key] ?: 0f) > 0.25f) { sm.stopLoop(e.value, 220); itr.remove() }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            loopH.values.forEach { sm.stopLoop(it, 40) }
            loopH.clear()
            music.stop()
            if (m.bgPaused) { m.bgPaused = false; setBgMusic(true) }
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) music.pause()
            else if (e == Lifecycle.Event.ON_RESUME) {
                music.resume()
                if (m.bgPaused) setBgMusic(false) // Activity на возврате включает фоновую музыку — на время танца держим её на паузе
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── геометрия ──
    fun toyS() = W() * 0.36f
    fun toyCx() = W() * 0.5f
    fun toyCy() = H() * 0.5f
    fun barY() = H() * 0.83f
    fun toyPx(u: Float) = toyCx() + m.offX + u * toyS()
    fun toyPy(v: Float) = toyCy() + m.bounce + v * toyS()

    fun cellU(i: Int) = CW_U0 + CW_UW * (i + 0.5f) / CW_GX
    fun cellV(j: Int) = CW_V0 + CW_VH * (j + 0.5f) / CW_GY

    fun loadToy(idx: Int) {
        m.toyIdx = idx
        m.toyType = idx % 3
        var n = 0
        for (j in 0 until CW_GY) for (i in 0 until CW_GX) {
            val k = j * CW_GX + i
            val inside = cwInside(m.toyType, cellU(i), cellV(j))
            m.mask[k] = inside
            if (inside) n++
            m.dirt[k] = if (inside) 1f else 0f
            m.foam[k] = 0f
            m.wet[k] = 0f
        }
        m.maskN = max(1, n)
        m.gridDirty = true
        m.phase = 0
        m.phaseT = 0f
        m.offX = -W() * 1.3f
        m.toyReal = false
        m.waitPopup = false
        m.hop = 0f
        m.barrier = 1f
        m.parts.clear()
        m.musicStarted = false
        m.skipped = false
        m.lastBeat = -1
        m.dAng = 0f; m.dHop = 0f; m.dSq = 1f; m.dFlip = 1f; m.dShift = 0f; m.dBeat = 0f
        m.starT = 0f
    }

    fun burst(kind: Int, x: Float, y: Float, n: Int, color: Color, spd: Float, size: Float, up: Float = 0f, life: Float = 0.8f, gy: Float = 1e9f) {
        repeat(n) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val sp = spd * (0.35f + Random.nextFloat() * 0.65f)
            m.parts.add(
                CwPart(
                    kind, x, y, cos(a) * sp, sin(a) * sp - up,
                    life * (0.6f + Random.nextFloat() * 0.6f), life, size * (0.6f + Random.nextFloat() * 0.8f),
                    color, Random.nextFloat() * 6f, (Random.nextFloat() - 0.5f) * 8f, gy
                )
            )
        }
    }

    // ── музыка и эффекты танца ──
    fun releaseBg() {
        if (m.bgPaused) { m.bgPaused = false; setBgMusic(true) }
    }

    fun startDanceMusic() {
        if (!musicOnState.value || sm.isMuted || sm.masterVolume <= 0.001f) return
        if (!m.bgPaused && bgMusicPlaying()) { setBgMusic(false); m.bgPaused = true }
        m.musicVol = (0.85f * sm.masterVolume).coerceIn(0f, 1f)
        m.musicGain = 1f
        m.musicFading = false
        music.start(CW_DANCE_RES[m.toyType], m.musicVol)
    }

    fun fadeOutMusic() {
        if (m.bgPaused || music.playing) m.musicFading = true
    }

    fun danceBeatFx(bt: Float) {
        val bi = floor(bt).toInt()
        if (bi == m.lastBeat) return
        m.lastBeat = bi
        val s = toyS()
        // нотки летят от игрушки
        m.parts.add(
            CwPart(
                6, toyPx(Random.nextFloat() * 1.6f - 0.8f), toyPy(-0.75f),
                (Random.nextFloat() - 0.5f) * W() * 0.12f, -H() * (0.12f + Random.nextFloat() * 0.06f),
                1.8f, 1.8f, s * 0.14f, CW_NOTE_COLORS[bi % CW_NOTE_COLORS.size], Random.nextFloat() * 6f, 0f, 1e9f
            )
        )
        if (bi % 4 == 0) { // начало такта — конфетти сверху
            repeat(16) {
                m.parts.add(
                    CwPart(
                        5, W() * Random.nextFloat(), -H() * 0.02f,
                        (Random.nextFloat() - 0.5f) * W() * 0.1f, H() * (0.08f + Random.nextFloat() * 0.1f),
                        3.2f, 3.2f, W() * 0.016f, CW_CONFETTI[Random.nextInt(CW_CONFETTI.size)],
                        Random.nextFloat() * 6f, (Random.nextFloat() - 0.5f) * 10f, 1e9f
                    )
                )
            }
        }
        if (m.toyType == TY_TRAIN) { // дым из трубы
            m.parts.add(CwPart(7, toyPx(-0.65f), toyPy(-0.65f), W() * 0.01f, -H() * 0.08f, 1.4f, 1.4f, s * 0.12f, Color(0xFFF1F5F9), 0f, 0f, 1e9f))
        }
        if (bi in 8..11) burst(2, toyPx(0f), toyPy(0.6f), 4, Color(0xFFFFF3A0), s * 0.5f, s * 0.1f, life = 0.6f) // искры при приземлении
    }

    fun skipShow() {
        if (m.phase != 2) return
        m.skipped = true
        m.phase = 3
        m.phaseT = 0f
        fadeOutMusic()
        snd("cw2_arrive", 0.8f)
    }

    fun contactOffset(kind: Int): Offset = when (kind) {
        CW_SHOWER -> Offset(0f, H() * 0.1f)
        CW_DRYER -> Offset(-W() * 0.06f, H() * 0.085f)
        else -> Offset.Zero
    }

    fun forCells(px: Float, py: Float, r: Float, f: (Int, Float) -> Unit) {
        val s = toyS()
        for (j in 0 until CW_GY) for (i in 0 until CW_GX) {
            val k = j * CW_GX + i
            if (!m.mask[k]) continue
            val d = hypot(toyPx(cellU(i)) - px, toyPy(cellV(j)) - py)
            if (d < r) { val q = 1f - d / r; f(k, q) }
        }
        if (s < 0f) return
    }

    fun toyHit(x: Float, y: Float): Boolean {
        val s = toyS()
        val u = (x - toyPx(0f)) / s
        val v = (y - toyPy(0f)) / s
        return u in -1.05f..1.05f && v in -0.85f..0.7f
    }

    fun startAuto(kind: Int, real: Boolean) {
        val sc = Offset(W() * (kind + 0.5f) / 3f, barY() + (H() - barY()) * 0.5f)
        val dur = when (kind) { CW_SPONGE -> 4.2f; CW_SHOWER -> 3.4f; else -> 3.2f }
        m.auto = CwAuto(kind, real, sc.x, sc.y, dur)
    }

    fun cancelAuto() {
        val a = m.auto
        if (a != null) {
            m.auto = null
            if (m.carry == a.kind) m.carry = -1
        }
        m.using = false
    }

    fun stopDemo() {
        if (!m.demo) return
        m.demo = false
        demoOn = false
        cancelAuto()
    }

    fun startDemo() {
        m.demo = true
        demoOn = true
        m.demoT = 0f
        m.handX = W() * 0.5f
        m.handY = H() * 0.9f
        m.handTx = m.handX
        m.handTy = m.handY
    }

    // ── применение инструмента ──
    fun applyTool(dt: Float) {
        val kind = m.carry
        m.using = false
        if (kind < 0) return
        val off = contactOffset(kind)
        val px = m.cx + off.x
        val py = m.cy + off.y
        val real = m.carryReal
        val mult = (if (toddler) 1.7f else 1f) * (if (real) 1f else 2f)
        val over = toyHit(px, py) && m.phase == 1
        val s = toyS()
        when (kind) {
            CW_SPONGE -> {
                val rub = min(1f, m.moveDist / (W() * 0.025f))
                m.moveDist = 0f
                if (over) {
                    m.using = true
                    if (real) m.toyReal = true
                    val r = s * 0.42f * (if (toddler) 1.3f else 1f)
                    forCells(px, py, r) { k, q ->
                        m.foam[k] = min(1f, m.foam[k] + rub * dt * 5f * q)
                        if (m.foam[k] > 0.2f) m.dirt[k] = max(0f, m.dirt[k] - rub * dt * 2.2f * q * mult)
                    }
                    m.gridDirty = true
                    if (rub > 0.1f) {
                        m.tScrub -= dt
                        hold("cw2_brush_loop", 0.5f)
                        m.tFoam -= dt
                        if (m.tFoam <= 0f) { m.tFoam = 3f; snd("cw2_foam", 0.35f) }
                        if (m.tScrub <= 0f) { m.tScrub = 1.25f; snd("cw2_scrub", 0.45f); if (real) haptics.vibrateWater() }
                        if (Random.nextFloat() < 0.6f) burst(0, px + (Random.nextFloat() - 0.5f) * s * 0.4f, py, 1, Color.White, s * 0.25f, s * 0.06f, up = s * 0.3f, life = 0.8f)
                    }
                }
            }
            CW_SHOWER -> {
                // струя из лейки: всегда льёт, когда рука над мойкой
                m.using = true
                val nx = m.cx
                val ny = m.cy - H() * 0.03f
                m.tSpray -= dt
                if (m.tSpray <= 0f) { m.tSpray = 1.0f; snd("cw2_spray", 0.5f); if (real) haptics.vibrateWater() }
                m.tRinse -= dt
                if (over && m.tRinse <= 0f) { m.tRinse = 1.8f; snd("cw2_rinse", 0.4f) }
                repeat(2) {
                    m.parts.add(
                        CwPart(1, nx + (Random.nextFloat() - 0.5f) * W() * 0.12f, ny, (Random.nextFloat() - 0.5f) * 20f, H() * 0.15f, 1f, 1f, W() * 0.011f, Color(0xFF5CC0FF), 0f, 0f, py + (Random.nextFloat() - 0.5f) * H() * 0.04f)
                    )
                }
                if (over) {
                    if (real) m.toyReal = true
                    val r = s * 0.5f
                    forCells(px, py, r) { k, q ->
                        m.foam[k] = max(0f, m.foam[k] - dt * 2.4f * q * mult)
                        m.wet[k] = min(1f, m.wet[k] + dt * 3f * q)
                        m.dirt[k] = max(0f, m.dirt[k] - dt * 0.3f * q * mult)
                    }
                    m.gridDirty = true
                }
            }
            else -> {
                m.using = true
                m.tBlow -= dt
                hold("cw2_dryer_loop", 0.5f)
                if (m.tBlow <= 0f) { m.tBlow = 0.7f; if (real) haptics.vibrateShort() }
                if (Random.nextFloat() < 0.7f) {
                    m.parts.add(CwPart(3, px - W() * 0.12f + Random.nextFloat() * W() * 0.12f, py - H() * 0.07f + Random.nextFloat() * H() * 0.05f, W() * 0.5f, H() * 0.12f, 0.35f, 0.35f, W() * 0.06f, Color.White, 0f, 0f, 1e9f))
                }
                if (over) {
                    if (real) m.toyReal = true
                    val r = s * 0.55f
                    forCells(px, py, r) { k, q ->
                        m.wet[k] = max(0f, m.wet[k] - dt * 1.6f * q * mult)
                    }
                    m.gridDirty = true
                    if (m.dirtF < 0.1f && m.foamF < 0.1f) {
                        m.tSpark -= dt
                        if (m.tSpark <= 0f) {
                            m.tSpark = 0.12f
                            burst(2, px + (Random.nextFloat() - 0.5f) * s * 0.8f, py + (Random.nextFloat() - 0.5f) * s * 0.4f, 1, Color(0xFFFFF3A0), s * 0.15f, s * 0.12f, life = 0.7f)
                        }
                    }
                }
            }
        }
    }

    fun toyTap() {
        if (m.phase == 2 || m.phase == 3) { // пока игрушка танцует, на неё можно жать — она сигналит и сыплет звёздами
            snd("cw2_honk", 0.7f, 0.9f + Random.nextFloat() * 0.4f)
            burst(2, toyPx(0f), toyPy(-0.5f), 6, Color(0xFFFFF3A0), toyS() * 0.6f, toyS() * 0.1f, life = 0.7f)
            haptics.vibratePop()
            return
        }
        if (m.phase != 1) return
        if (m.dirtF > 0.3f) {
            m.sadShake = 1f
            snd("fx_cw_sigh", 0.8f)
            haptics.vibrateShort()
        } else {
            m.hop = 1f
            snd("cw2_honk", 0.7f)
            burst(2, toyPx(0f), toyPy(-0.5f), 5, Color(0xFFFFF3A0), toyS() * 0.5f, toyS() * 0.1f, life = 0.7f)
            haptics.vibratePop()
        }
    }

    // ── ввод ──
    fun onDown(x: Float, y: Float, real: Boolean) {
        m.downX = x; m.downY = y; m.downT = m.clock; m.travel = 0f; m.moveDist = 0f
        m.carryReal = real
        if (y >= barY() - H() * 0.02f) {
            val slot = (x / (W() / 3f)).toInt().coerceIn(0, 2)
            m.carry = slot
            m.carryFromBar = true
            m.cx = x
            m.cy = y - H() * 0.07f
            snd("wl_pin", 0.5f)
            return
        }
        m.carryFromBar = false
        if (toyHit(x, y)) toyTap()
    }

    fun onMove(x: Float, y: Float) {
        if (m.carry == -1) return
        val nx = x
        val ny = y - H() * 0.07f
        val d = hypot(nx - m.cx, ny - m.cy)
        m.travel += d
        m.moveDist += d
        m.cx = nx
        m.cy = ny
    }

    fun onUp(real: Boolean) {
        val kind = m.carry
        if (kind == -1) return
        val tapInBar = m.carryFromBar && m.travel < W() * 0.035f && (m.clock - m.downT) < 0.45f
        m.carry = -1
        m.using = false
        if (tapInBar) startAuto(kind, real)
    }

    // ── прогресс ──
    fun finishToy() {
        m.phase = 2
        m.phaseT = 0f
        m.musicStarted = false
        m.skipped = false
        m.lastBeat = -1
        burst(2, toyPx(0f), toyPy(-0.3f), 18, Color(0xFFFFF3A0), toyS() * 1.4f, toyS() * 0.14f, up = toyS() * 0.4f, life = 1.0f)
        snd("cw2_sparkle", 0.9f)
        snd("cw2_honk", 0.8f)
        haptics.vibratePop()
        bannerText = CW_CHEER.random()
        bannerPhase = 0f
        bannerOn = true
        m.bannerT = 0.001f
        if (m.toyReal) {
            washed += 1
            prefs.edit().putInt("washed", washed).apply()
            m.rewardPending = CW_STICKERS.firstOrNull { it.need == washed }
            m.starT = 0.001f // звёздочка за работу летит в счётчик «Помыто»
        }
    }

    fun computeHint(): String {
        return when {
            m.phase == 0 -> "Грязнулька приехала!"
            m.phase == 2 -> "Ура! Какая чистая!"
            m.phase >= 3 -> "Чистюля танцует и едет домой!"
            m.stage == 1 && m.foamF < 0.05f -> if (toddler) "Тапни по губке — она помоет!" else "Возьми губку и потри грязь"
            m.stage == 1 -> "Три ещё — грязь исчезает под пеной!"
            m.stage == 2 -> if (toddler) "Тапни по душу — смоем пену!" else "Смой пену душем"
            else -> if (toddler) "Тапни по фену — высушим!" else "Высуши феном — до блеска!"
        }
    }

    // ── цикл ──
    fun update(dt: Float) {
        m.clock += dt
        val clock = m.clock
        val overlay = showAlbum || rewardSticker != null

        // авто-действие
        val a = m.auto
        if (a != null) {
            a.t += dt
            val fly = 0.7f
            val back = 0.45f
            val off = contactOffset(a.kind)
            val tx = toyPx(0f)
            val ty = toyPy(if (a.kind == CW_SPONGE) -0.1f else -0.2f)
            var px: Float
            var py: Float
            if (a.t < fly) {
                val k = cwEase(a.t / fly)
                px = cwLerp(a.sx, tx - off.x, k)
                py = cwLerp(a.sy, ty - off.y, k)
            } else if (a.t < fly + a.actDur) {
                val u = (a.t - fly) / max(0.01f, a.actDur)
                val tp = 2f * PI.toFloat()
                px = tx - off.x + sin(u * tp * (if (a.kind == CW_SPONGE) 2.6f else 1.7f)) * W() * 0.3f
                py = ty - off.y + (if (a.kind == CW_SPONGE) sin(u * tp * 3.4f) * W() * 0.09f else sin(u * tp * 2.1f) * W() * 0.03f)
            } else {
                val k = cwEase((a.t - fly - a.actDur) / back)
                px = cwLerp(tx - off.x, a.sx, k)
                py = cwLerp(ty - off.y, a.sy, k)
            }
            m.moveDist += hypot(px - m.cx, py - m.cy)
            m.carry = a.kind; m.cx = px; m.cy = py; m.carryReal = a.real
            if (a.t >= fly + a.actDur + back) { m.auto = null; m.carry = -1; m.using = false }
            if (!m.demo) m.lastTouch = clock
        }
        if (m.carry >= 0 && !(a != null && a.t < 0.7f)) applyTool(dt) else { m.using = false }
        tickLoops()

        // игрушка
        m.brushSpin += dt * (if (m.using && m.carry == CW_SPONGE) 6f else if (m.showK > 0.3f) 4f else 1.2f)
        m.sadShake = max(0f, m.sadShake - dt * 1.5f)
        m.hop = max(0f, m.hop - dt * 2.2f)
        m.nextBlink -= dt
        if (m.nextBlink <= 0f) { m.blink = 0.15f; m.nextBlink = 2f + Random.nextFloat() * 2.5f }
        m.blink = max(0f, m.blink - dt)
        m.phaseT += dt
        var hopY = -abs(sin(m.hop * PI.toFloat())) * toyS() * 0.25f * (if (m.hop > 0f) 1f else 0f)
        when (m.phase) {
            0 -> {
                val d = 1.5f
                val k = cwEase(m.phaseT / d)
                m.offX = cwLerp(-W() * 1.3f, 0f, k)
                m.wheelRot += dt * 14f * (1f - k + 0.1f)
                m.barrier = if (m.phaseT < 1.1f) 1f else 0f
                if (m.phaseT < dt * 1.5f) snd("cw2_arrive", 0.8f)
                if (m.phaseT >= d) { m.phase = 1; m.phaseT = 0f; m.offX = 0f; snd("fx_cw_sigh", 0.8f); m.sadShake = 0.8f }
            }
            1 -> {
                m.offX = 0f
                m.barrier = 0f
            }
            2 -> {
                m.offX = 0f
                m.barrier = 0f
                val spb = 60f / CW_BPM[m.toyType]
                val bt = (m.phaseT - CW_LEAD) / spb
                if (!m.musicStarted && m.phaseT >= CW_LEAD) { m.musicStarted = true; startDanceMusic() }
                if (bt >= 0f) {
                    cwDancePose(m, m.toyType, bt)
                    danceBeatFx(bt)
                } else { // «ура»: радостный прыжок до начала музыки
                    m.dAng = 0f; m.dFlip = 1f; m.dSq = 1f; m.dShift = 0f; m.dBeat = 0f
                    m.dHop = abs(sin(m.phaseT / CW_LEAD * PI.toFloat())) * 0.18f
                }
                hopY = -m.dHop * toyS()
                if (Random.nextFloat() < 0.12f) burst(2, toyPx(Random.nextFloat() * 2f - 1f), toyPy(-0.7f + Random.nextFloat() * 0.8f), 1, Color(0xFFFFF3A0), toyS() * 0.2f, toyS() * 0.12f, life = 0.7f)
                if (m.phaseT >= CW_LEAD + CW_SHOW_BEATS * spb) { m.phase = 3; m.phaseT = 0f; snd("cw2_arrive", 0.8f) }
            }
            else -> {
                val spb = 60f / CW_BPM[m.toyType]
                if (m.skipped) m.phaseT += dt * 1.6f
                val d = CW_GO_BEATS * spb
                val k = cwEase(m.phaseT / (d * 0.88f))
                m.offX = cwLerp(0f, W() * 1.3f, k)
                m.wheelRot += dt * 14f * (k + 0.2f)
                m.barrier = if (m.phaseT > 0.1f) 1f else 0f
                val bt = CW_SHOW_BEATS + m.phaseT / spb
                cwDancePose(m, m.toyType, bt)
                if (bt < CW_SHOW_BEATS + CW_GO_BEATS) danceBeatFx(bt) // пока ждём награду, игрушка уже уехала — праздник не сыплем
                hopY = -m.dHop * toyS()
                if (m.phaseT >= d) {
                    if (m.musicStarted) { m.musicStarted = false; fadeOutMusic() }
                    val st = m.rewardPending
                    if (st != null && rewardSticker == null && !overlay) {
                        m.rewardPending = null
                        rewardSticker = st
                        snd("cw2_reward", 0.9f)
                    } else if (st == null && !overlay) {
                        loadToy(m.toyIdx + 1)
                    }
                }
            }
        }
        m.bounce = hopY
        m.showK += ((if (m.phase == 2 || m.phase == 3) 1f else 0f) - m.showK) * min(1f, dt * 4f)
        if (m.starT > 0f) {
            m.starT += dt
            if (m.starT >= 1.2f) { m.starT = 0f; counterPulse = 1f; snd("cw2_sparkle", 0.5f, 1.4f) }
        }
        if (counterPulse > 0f) counterPulse = max(0f, counterPulse - dt * 1.6f)
        if (m.bannerT > 0f) {
            m.bannerT += dt
            bannerPhase = (m.bannerT / 2.6f).coerceAtMost(1f)
            if (m.bannerT >= 2.6f) { m.bannerT = 0f; bannerOn = false }
        }
        val wantSkip = m.phase == 2 && m.phaseT > 2.5f
        if (wantSkip != showSkip) showSkip = wantSkip
        if (m.musicFading) {
            m.musicGain = max(0f, m.musicGain - dt / 0.4f)
            music.setVolume(m.musicVol * m.musicGain)
            if (m.musicGain <= 0f) { music.stop(); m.musicFading = false; releaseBg() }
        }

        // пересчёт прогресса
        if (m.gridDirty) {
            var sd = 0f; var sf = 0f; var sw = 0f
            for (k in 0 until CW_GX * CW_GY) if (m.mask[k]) { sd += m.dirt[k]; sf += m.foam[k]; sw += m.wet[k] }
            m.dirtF = sd / m.maskN
            m.foamF = sf / m.maskN
            m.wetF = sw / m.maskN
        }
        // «магическая» дочистка остатков
        if (m.phase == 1) {
            val fadeD = m.dirtF < 0.2f && m.dirtF > 0.001f
            val fadeF = m.dirtF < 0.1f && m.foamF < 0.15f && m.foamF > 0.001f
            val fadeW = m.dirtF < 0.1f && m.foamF < 0.08f && m.wetF < 0.15f && m.wetF > 0.001f
            if (fadeD || fadeF || fadeW) {
                for (k in 0 until CW_GX * CW_GY) if (m.mask[k]) {
                    if (fadeD) m.dirt[k] = max(0f, m.dirt[k] - dt * 0.5f)
                    if (fadeF) m.foam[k] = max(0f, m.foam[k] - dt * 0.6f)
                    if (fadeW) m.wet[k] = max(0f, m.wet[k] - dt * 0.5f)
                }
                m.gridDirty = true
            }
        }
        val oldStage = m.stage
        m.stage = when {
            m.dirtF > 0.07f -> 1
            m.foamF > 0.06f -> 2
            m.wetF > 0.1f -> 3
            else -> 4
        }
        if (m.phase == 1 && m.stage != oldStage && m.stage in 2..3) { snd("cw2_sparkle", 0.6f); burst(2, toyPx(0f), toyPy(-0.2f), 6, Color(0xFFFFF3A0), toyS() * 0.8f, toyS() * 0.1f, life = 0.7f) }
        if (m.phase == 1 && m.stage == 4) finishToy()
        val prog = 0.4f * (1f - m.dirtF) +
            0.3f * (if (m.dirtF < 0.1f) 1f - m.foamF else 0f) +
            0.3f * (if (m.dirtF < 0.1f && m.foamF < 0.08f) 1f - m.wetF else 0f)
        val pct = if (m.phase >= 2) 100 else (prog * 100f).toInt().coerceIn(0, 99)
        if (pct != progressPct) progressPct = pct

        // капли с мокрой игрушки
        if (m.phase == 1 && m.wetF > 0.2f) {
            m.tDrip -= dt
            if (m.tDrip <= 0f) {
                m.tDrip = 0.25f
                val u = -0.8f + Random.nextFloat() * 1.6f
                m.parts.add(CwPart(1, toyPx(u), toyPy(0.55f), 0f, 0f, 1f, 1f, W() * 0.008f, Color(0xFF9AD8FF), 0f, 0f, toyPy(0.9f)))
            }
        }

        // битмапы слоёв
        if (m.gridDirty || dirtImg == null) {
            m.gridDirty = false
            val bw = CW_GX * 2
            val bh = CW_GY * 2
            val pd = IntArray(bw * bh)
            val pf = IntArray(bw * bh)
            val pw = IntArray(bw * bh)
            for (y in 0 until bh) for (x in 0 until bw) {
                val gx = ((x + 0.5f) / 2f - 0.5f).coerceIn(0f, CW_GX - 1.001f)
                val gy = ((y + 0.5f) / 2f - 0.5f).coerceIn(0f, CW_GY - 1.001f)
                val x0 = gx.toInt(); val y0 = gy.toInt()
                val fx = gx - x0; val fy = gy - y0
                fun bil(arr: FloatArray): Float {
                    val v00 = arr[y0 * CW_GX + x0]; val v10 = arr[y0 * CW_GX + x0 + 1]
                    val v01 = arr[(y0 + 1) * CW_GX + x0]; val v11 = arr[(y0 + 1) * CW_GX + x0 + 1]
                    return (v00 * (1 - fx) + v10 * fx) * (1 - fy) + (v01 * (1 - fx) + v11 * fx) * fy
                }
                val nz = CW_NOISE[y * bw + x]
                val d = bil(m.dirt).coerceIn(0f, 1f)
                val dAlpha = (d * 1.25f - 0.12f).coerceIn(0f, 1f) * (0.86f + 0.12f * nz)
                val dr = (88 + 34 * nz).toInt(); val dg = (58 + 26 * nz).toInt(); val db = (36 + 18 * nz).toInt()
                pd[y * bw + x] = ((dAlpha * 255f).toInt() shl 24) or (dr shl 16) or (dg shl 8) or db
                val f = bil(m.foam).coerceIn(0f, 1f)
                val fAlpha = (f * 1.3f - 0.1f).coerceIn(0f, 1f) * 0.92f
                pf[y * bw + x] = ((fAlpha * 255f).toInt() shl 24) or (245 shl 16) or (250 shl 8) or 255
                val wv = bil(m.wet).coerceIn(0f, 1f)
                pw[y * bw + x] = (((wv * 0.2f) * 255f).toInt() shl 24) or (190 shl 16) or (230 shl 8) or 255
            }
            dirtBmp.setPixels(pd, 0, bw, 0, 0, bw, bh)
            foamBmp.setPixels(pf, 0, bw, 0, 0, bw, bh)
            wetBmp.setPixels(pw, 0, bw, 0, 0, bw, bh)
            dirtImg = dirtBmp.asImageBitmap()
            foamImg = foamBmp.asImageBitmap()
            wetImg = wetBmp.asImageBitmap()
        }

        // частицы
        val spawn = ArrayList<CwPart>()
        for (p in m.parts) {
            p.life -= dt
            when (p.kind) {
                1 -> {
                    p.vy += H() * 2.4f * dt
                    p.y += p.vy * dt
                    p.x += p.vx * dt
                    if (p.y >= p.gy) {
                        p.life = 0f
                        spawn.add(CwPart(4, p.x, p.y, 0f, 0f, 0.35f, 0.35f, W() * 0.02f, Color(0xFFBFE6FF), 0f, 0f, 1e9f))
                    }
                }
                0 -> { p.y -= H() * 0.05f * dt; p.x += sin(clock * 5f + p.rot) * W() * 0.03f * dt; p.vy += 0f }
                5 -> { // конфетти
                    p.vy = min(p.vy + H() * 0.05f * dt, H() * 0.28f)
                    p.x += (p.vx + sin(clock * 3f + p.rot) * W() * 0.05f) * dt
                    p.y += p.vy * dt
                    p.rot += p.vr * dt
                }
                6 -> { p.y += p.vy * dt; p.x += (p.vx + sin(clock * 4f + p.rot) * W() * 0.06f) * dt } // нотка
                7 -> { p.y += p.vy * dt; p.x += p.vx * dt } // дым
                else -> { p.x += p.vx * dt; p.y += p.vy * dt; p.rot += p.vr * dt }
            }
        }
        m.parts.addAll(spawn)
        m.parts.removeAll { it.life <= 0f }
        if (m.parts.size > 340) repeat(60) { m.parts.removeAt(0) }

        // демо
        if (m.demo) {
            if (overlay || m.phase != 1) { stopDemo(); m.lastTouch = clock } else {
                m.demoT += dt
                val hk = 1f - 0.0001f.pow(dt)
                if (m.auto != null) { m.handTx = m.cx + contactOffset(m.carry.coerceAtLeast(0)).x; m.handTy = m.cy + contactOffset(m.carry.coerceAtLeast(0)).y; m.handDown = true }
                else { m.handDown = false; m.handTx = toyPx(0f); m.handTy = toyPy(0f) }
                m.handX += (m.handTx - m.handX) * hk
                m.handY += (m.handTy - m.handY) * hk
                if (m.auto == null && m.demoT > 0.6f) {
                    when (m.stage) {
                        1 -> startAuto(CW_SPONGE, false)
                        2 -> startAuto(CW_SHOWER, false)
                        3 -> startAuto(CW_DRYER, false)
                    }
                    m.demoT = 0f
                }
            }
        } else if (!overlay && m.auto == null && m.carry == -1 && m.phase == 1 && clock - m.lastTouch > 5f) {
            startDemo()
        }

        // подсказка и пульсация
        m.hintTimer -= dt
        if (m.hintTimer <= 0f) {
            val h = computeHint()
            if (h != m.hint) { m.hint = h; hintText = h }
        }
        m.pulseSlot = when {
            m.carry != -1 || m.phase != 1 -> -1
            m.stage == 1 && m.foamF < 0.05f -> 0
            m.stage == 2 -> 1
            m.stage == 3 -> 2
            else -> -1
        }
        frame++
    }

    LaunchedEffect(Unit) {
        loadToy(0)
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
                        cancelAuto()
                        m.lastTouch = m.clock
                        onDown(down.position.x, down.position.y, true)
                        var lastPos = down.position
                        var ended = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id }
                            if (ch == null) break
                            lastPos = ch.position
                            m.lastTouch = m.clock
                            if (ch.changedToUp()) { onUp(true); ended = true; break }
                            if (ch.positionChanged()) { onMove(ch.position.x, ch.position.y); ch.consume() }
                        }
                        if (!ended) onUp(true)
                        lastPos.hashCode()
                    }
                }
        ) {
            frame.hashCode()
            val w = size.width
            val h = size.height
            if (w <= 0f) return@Canvas
            val t = m.clock
            val floorY = h * 0.64f

            // задняя стена
            drawRect(Brush.verticalGradient(listOf(Color(0xFF3FA9F5), Color(0xFF8FD3FF)), 0f, floorY), Offset.Zero, Size(w, floorY))
            for (i in 0 until 8) {
                if (i % 2 == 0) drawRect(Color.White.cwA(0.12f), Offset(w * i / 8f, 0f), Size(w / 8f, floorY))
            }
            // вывеска
            val signW = w * 0.62f
            val signX = (w - signW) / 2f
            drawRoundRect(Color(0xFFFFC21A), Offset(signX, h * 0.095f), Size(signW, h * 0.07f), CornerRadius(w * 0.04f))
            drawRoundRect(Color(0xFFE08A00), Offset(signX, h * 0.095f), Size(signW, h * 0.07f), CornerRadius(w * 0.04f), style = Stroke(w * 0.008f))
            for (i in 0 until 6) {
                val bx = signX + signW * (0.12f + 0.15f * i)
                val by = h * 0.13f + sin(t * 2f + i) * h * 0.006f
                drawCircle(Color.White.cwA(0.9f), w * (0.014f + 0.004f * (i % 3)), Offset(bx, by))
                drawCircle(Color(0xFF5CAEFF), w * (0.014f + 0.004f * (i % 3)), Offset(bx, by), style = Stroke(w * 0.003f))
            }
            // рама с душем/феном
            drawRoundRect(Color(0xFF52627A), Offset(w * 0.05f, h * 0.2f), Size(w * 0.9f, h * 0.025f), CornerRadius(w * 0.01f))
            for (hx in listOf(0.28f, 0.72f)) {
                drawRect(Color(0xFF52627A), Offset(w * hx - w * 0.008f, h * 0.2f), Size(w * 0.016f, h * 0.03f))
                drawRoundRect(Color(0xFF2E7BD0), Offset(w * hx - w * 0.05f, h * 0.225f), Size(w * 0.1f, h * 0.02f), CornerRadius(w * 0.01f))
                for (q in -2..2) drawCircle(Color(0xFFE8F6FF), w * 0.005f, Offset(w * hx + q * w * 0.017f, h * 0.245f))
            }
            // вращающиеся щётки по бокам
            for (bxr in listOf(0.045f, 0.955f)) {
                val bx = w * bxr
                drawRoundRect(Color(0xFF3B4A63), Offset(bx - w * 0.03f, h * 0.24f), Size(w * 0.06f, floorY - h * 0.24f), CornerRadius(w * 0.03f))
                val rows = 18
                for (i in 0 until rows) {
                    val yy = h * 0.25f + (floorY - h * 0.27f) * ((i + (m.brushSpin * 2f) % 1f) / rows)
                    if (yy < floorY - 4f) {
                        drawLine(if (i % 2 == 0) Color(0xFFFF6FA5) else Color(0xFF3FD0FF), Offset(bx - w * 0.034f, yy), Offset(bx + w * 0.034f, yy + h * 0.004f), w * 0.01f, StrokeCap.Round)
                    }
                }
            }
            // пол
            drawRect(Brush.verticalGradient(listOf(Color(0xFF8E9BB0), Color(0xFF5E6B80)), floorY, h), Offset(0f, floorY), Size(w, h - floorY))
            for (i in 0 until 5) drawLine(Color.White.cwA(0.12f), Offset(0f, floorY + (barY() - floorY) * i / 5f), Offset(w, floorY + (barY() - floorY) * i / 5f), 2f)
            // платформа с решёткой
            drawOval(Color(0xFF3E4A5C), Offset(w * 0.1f, h * 0.575f), Size(w * 0.8f, h * 0.11f))
            drawOval(Color(0xFF29323F), Offset(w * 0.16f, h * 0.59f), Size(w * 0.68f, h * 0.08f))
            for (i in 0 until 9) drawLine(Color(0xFF4F5D72), Offset(w * (0.2f + 0.075f * i), h * 0.6f), Offset(w * (0.2f + 0.075f * i), h * 0.66f), 3f)
            // предупредительная полоса
            for (i in 0 until 16) {
                drawRect(if (i % 2 == 0) Color(0xFFFFC21A) else Color(0xFF2A2F3A), Offset(w * i / 16f, barY() - h * 0.014f), Size(w / 16f, h * 0.014f))
            }

            // дискотека: лучи света и вспышки в такт (пока чистая игрушка танцует)
            if (m.showK > 0.01f) {
                val kk = m.showK
                val srcX = floatArrayOf(0.28f, 0.5f, 0.72f)
                val cols = listOf(Color(0xFFFF4DA6), Color(0xFFFFE066), Color(0xFF3FD0FF))
                for (i in 0 until 3) {
                    val sx0 = w * srcX[i]
                    val sy0 = h * 0.245f
                    val tx0 = toyPx(0f) + sin(t * 1.7f + i * 2.1f) * w * 0.3f
                    val ty0 = floorY + h * 0.03f
                    val half = w * 0.07f
                    val ray = Path().apply {
                        moveTo(sx0 - w * 0.012f, sy0); lineTo(sx0 + w * 0.012f, sy0)
                        lineTo(tx0 + half, ty0); lineTo(tx0 - half, ty0); close()
                    }
                    drawPath(ray, cols[i].cwA((0.16f + 0.14f * m.dBeat) * kk))
                    drawOval(cols[i].cwA((0.28f + 0.2f * m.dBeat) * kk), Offset(tx0 - half, ty0 - h * 0.012f), Size(half * 2f, h * 0.03f))
                }
                drawRect(Color.White.cwA(0.07f * m.dBeat * kk), Offset.Zero, Size(w, floorY))
            }

            // игрушка
            val s = toyS()
            val upg = cwUpgrades(washed)
            val mood = when {
                m.phase >= 2 -> 3
                m.dirtF > 0.55f -> 0
                m.dirtF > 0.08f -> 1
                else -> 2
            }
            if (m.phase >= 2) { // на танце тень остаётся на земле и не наклоняется вместе с игрушкой
                withTransform({ translate(toyPx(0f), toyCy()); scale(s, s, Offset.Zero) }) {
                    val hu = m.dHop
                    drawOval(Color(0x44000000).cwA((1f - hu * 2.2f).coerceIn(0.25f, 1f)), Offset(-1.05f + hu * 0.4f, 0.62f), Size(2.1f - hu * 0.8f, 0.3f))
                }
            }
            withTransform({
                translate(toyPx(0f), toyPy(0f))
                scale(s, s, Offset.Zero)
                if (m.phase >= 2) { // танец: наклон вокруг опорного колеса (другой бок приподнимается), приседание, поворот
                    translate(m.dShift, 0f)
                    rotate(m.dAng, Offset(m.dPivU, m.dPivV))
                    scale(m.dFlip * m.dSq, 1f / m.dSq, Offset(0f, 0.75f))
                }
            }) {
                cwToy(m.toyType, upg, mood, if (m.blink > 0f) 1f else 0f, m.sadShake, m.wheelRot, t, if (m.phase >= 2) m.dBeat else 0f, m.phase < 2)
                val body = CW_SHAPES[m.toyType]
                if (body != null) {
                    clipPath(body) {
                        withTransform({ scale(1f / s, 1f / s, Offset.Zero) }) {
                            val dst = IntSize((CW_UW * s).toInt() + 1, (CW_VH * s).toInt() + 1)
                            val off = IntOffset((CW_U0 * s).toInt(), (CW_V0 * s).toInt())
                            wetImg?.let { drawImage(it, dstOffset = off, dstSize = dst, filterQuality = FilterQuality.Low) }
                            dirtImg?.let { drawImage(it, dstOffset = off, dstSize = dst, filterQuality = FilterQuality.Low) }
                            foamImg?.let { drawImage(it, dstOffset = off, dstSize = dst, filterQuality = FilterQuality.Low) }
                        }
                        for (b in CW_BUBBLES) {
                            val gi = ((b[0] - CW_U0) / CW_UW * CW_GX).toInt().coerceIn(0, CW_GX - 1)
                            val gj = ((b[1] - CW_V0) / CW_VH * CW_GY).toInt().coerceIn(0, CW_GY - 1)
                            val fv = m.foam[gj * CW_GX + gi]
                            if (fv > 0.3f) {
                                val rr = b[2] * min(1f, fv)
                                drawCircle(Color.White.cwA(0.85f), rr, Offset(b[0], b[1]))
                                drawCircle(Color(0xFF9AD8FF).cwA(0.7f), rr, Offset(b[0], b[1]), style = Stroke(0.012f))
                                drawCircle(Color.White, rr * 0.22f, Offset(b[0] - rr * 0.35f, b[1] - rr * 0.35f))
                            }
                        }
                        for (dd in CW_DROPS) {
                            val gi = ((dd[0] - CW_U0) / CW_UW * CW_GX).toInt().coerceIn(0, CW_GX - 1)
                            val gj = ((dd[1] - CW_V0) / CW_VH * CW_GY).toInt().coerceIn(0, CW_GY - 1)
                            val wv = m.wet[gj * CW_GX + gi]
                            if (wv > 0.35f && m.foam[gj * CW_GX + gi] < 0.3f && m.dirt[gj * CW_GX + gi] < 0.4f) {
                                drawCircle(Color(0xFFBFE6FF).cwA(0.6f * wv), dd[2], Offset(dd[0], dd[1]))
                                drawCircle(Color.White.cwA(0.9f * wv), dd[2] * 0.35f, Offset(dd[0] - dd[2] * 0.3f, dd[1] - dd[2] * 0.3f))
                            }
                        }
                    }
                }
            }

            // шлагбаум
            val postX = w * 0.1f
            val postY = floorY + h * 0.02f
            drawRoundRect(Color(0xFF52627A), Offset(postX - w * 0.02f, postY - h * 0.04f), Size(w * 0.04f, h * 0.08f), CornerRadius(w * 0.01f))
            val armAng = -70f * m.barrier
            withTransform({ rotate(armAng, Offset(postX, postY - h * 0.015f)) }) {
                val armLen = w * 0.3f
                drawRoundRect(Color.White, Offset(postX, postY - h * 0.02f), Size(armLen, h * 0.014f), CornerRadius(w * 0.005f))
                for (i in 0 until 5) drawRect(Color(0xFFEF476F), Offset(postX + armLen * (0.08f + 0.2f * i), postY - h * 0.02f), Size(armLen * 0.1f, h * 0.014f))
            }

            // частицы
            for (p in m.parts) {
                val k = (p.life / p.maxLife).coerceIn(0f, 1f)
                when (p.kind) {
                    0 -> {
                        drawCircle(Color.White.cwA(0.85f * k), p.size, Offset(p.x, p.y))
                        drawCircle(Color(0xFF9AD8FF).cwA(0.8f * k), p.size, Offset(p.x, p.y), style = Stroke(w * 0.0025f))
                    }
                    1 -> drawOval(p.color.cwA(0.9f), Offset(p.x - p.size * 0.5f, p.y - p.size), Size(p.size, p.size * 2.2f))
                    2 -> cwStar(Offset(p.x, p.y), p.size * (0.4f + 0.6f * k), p.color.cwA(k), p.rot)
                    3 -> drawLine(Color.White.cwA(0.55f * k), Offset(p.x, p.y), Offset(p.x + p.size, p.y + p.size * 0.2f), w * 0.004f, StrokeCap.Round)
                    5 -> withTransform({ rotate(p.rot * 57.2958f, Offset(p.x, p.y)) }) {
                        drawRect(p.color.cwA(min(1f, k * 3f)), Offset(p.x - p.size / 2f, p.y - p.size * 0.3f), Size(p.size, p.size * 0.6f))
                    }
                    6 -> cwNote(Offset(p.x, p.y), p.size, p.color.cwA(min(1f, k * 2.5f)))
                    7 -> drawCircle(Color(0xFFF1F5F9).cwA(0.55f * k), p.size * (1.2f + (1f - k) * 2.2f), Offset(p.x, p.y))
                    else -> drawCircle(p.color.cwA(k * 0.8f), p.size * (2f - k), Offset(p.x, p.y), style = Stroke(w * 0.003f))
                }
            }

            // звёздочка за работу летит в счётчик «Помыто»
            if (m.starT > 0f) {
                val q = cwEase(m.starT / 1.2f)
                val from = Offset(toyPx(0f), toyPy(-0.3f))
                val to = Offset(w - 52.dp.toPx(), 86.dp.toPx())
                val sc = Offset(cwLerp(from.x, to.x, q), cwLerp(from.y, to.y, q) - sin(q * PI.toFloat()) * h * 0.12f)
                drawCircle(Color(0xFFFFF3A0).cwA(0.35f), s * 0.3f, sc)
                cwStar(sc, s * 0.2f * (1f - 0.3f * q), Color(0xFFFFD54A), t * 6f)
                cwStar(sc, s * 0.1f, Color.White, -t * 4f)
            }

            // нижняя полка с инструментами
            val by0 = barY()
            drawRect(Brush.verticalGradient(listOf(Color(0xFF4A5B78), Color(0xFF2E3A52)), by0, h), Offset(0f, by0), Size(w, h - by0))
            drawRect(Color(0xFF7B8DAB), Offset(0f, by0), Size(w, h * 0.007f))
            val ss = min(w / 3f * 0.4f, (h - by0) * 0.42f)
            for (i in 0 until 3) {
                val c = Offset(w * (i + 0.5f) / 3f, by0 + (h - by0) * 0.52f)
                val pul = if (i == m.pulseSlot) 1f + 0.1f * sin(t * 7f) else 1f
                drawCircle(Color(0x33FFFFFF), ss * 1.15f * pul, c)
                if (i == m.pulseSlot) drawCircle(Color(0xAAFFF3A0), ss * 1.25f * pul, c, style = Stroke(w * 0.007f))
                val taken = m.carry == i && m.carryFromBar
                if (!taken) when (i) {
                    CW_SPONGE -> cwSponge(c.x, c.y, ss * 0.85f * pul, -8f)
                    CW_SHOWER -> cwShowerHead(c.x, c.y - ss * 0.1f, ss * 0.62f * pul, false)
                    else -> cwDryer(c.x + ss * 0.1f, c.y - ss * 0.05f, ss * 0.65f * pul, false)
                }
            }

            // инструмент в руке
            if (m.carry != -1) {
                when (m.carry) {
                    CW_SPONGE -> cwSponge(m.cx, m.cy, w * 0.085f, sin(t * 14f) * (if (m.using) 8f else 2f))
                    CW_SHOWER -> cwShowerHead(m.cx, m.cy, w * 0.075f, true)
                    else -> cwDryer(m.cx, m.cy, w * 0.075f, true)
                }
            }

            if (m.demo) cwHand(Offset(m.handX, m.handY), min(w, h) * 0.04f, m.handDown)
        }

        // подсказка
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 12.dp, start = 12.dp, end = 84.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xE6FFFFFF))
                .border(2.dp, Color(0xFF5CAEFF), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(hintText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1F5FA8), textAlign = TextAlign.Center)
        }
        // чистота
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 64.dp, start = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xCCFFFFFF))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Чистота", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF1F5FA8))
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .width(90.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFFCFE2F5))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressPct / 100f)
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFFC21A), Color(0xFF3FD07A), Color(0xFF3FD0FF))))
                )
            }
            Spacer(Modifier.width(6.dp))
            Text("$progressPct%", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF1F5FA8))
        }
        if (demoOn) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 98.dp, start = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE65FA8E8))
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
                .background(Brush.verticalGradient(listOf(Color(0xFF7FC8FF), Color(0xFF3B8EE8))))
                .border(3.dp, Color.White, CircleShape)
                .clickable { stopDemo(); m.lastTouch = m.clock; showAlbum = true; snd("wl_pin", 0.8f) },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(40.dp)) { cwStickerArt(0, Offset(size.width / 2, size.height / 2), size.width * 0.42f) }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 74.dp, end = 12.dp)
                .graphicsLayer { val c = counterPulse; scaleX = 1f + 0.4f * c; scaleY = 1f + 0.4f * c }
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xCCFFFFFF))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Помыто:", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF1F5FA8))
            Text(" $washed", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF1F5FA8))
        }

        // кнопка музыки танца
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 106.dp, end = 22.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFFF8FC0), Color(0xFFE0407E))))
                .border(2.dp, Color.White, CircleShape)
                .clickable {
                    stopDemo(); m.lastTouch = m.clock
                    musicOn = !musicOn
                    prefs.edit().putBoolean("dance_music", musicOn).apply()
                    if (!musicOn) fadeOutMusic()
                    snd("wl_pin", 0.6f)
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(24.dp)) {
                val s = size.width
                val col = if (musicOn) Color.White else Color.White.copy(alpha = 0.5f)
                drawCircle(col, s * 0.16f, Offset(s * 0.34f, s * 0.74f))
                drawLine(col, Offset(s * 0.46f, s * 0.72f), Offset(s * 0.46f, s * 0.18f), s * 0.07f, StrokeCap.Round)
                drawLine(col, Offset(s * 0.46f, s * 0.18f), Offset(s * 0.8f, s * 0.3f), s * 0.07f, StrokeCap.Round)
                if (!musicOn) drawLine(Color(0xFF5B1A38), Offset(s * 0.1f, s * 0.9f), Offset(s * 0.9f, s * 0.1f), s * 0.09f, StrokeCap.Round)
            }
        }
        // «дальше» — пропустить танец
        if (showSkip) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 152.dp, end = 22.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF7FC8FF), Color(0xFF3B8EE8))))
                    .border(2.dp, Color.White, CircleShape)
                    .clickable { m.lastTouch = m.clock; skipShow() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(22.dp)) {
                    val s = size.width
                    for (dx in listOf(0.08f, 0.46f)) {
                        val tri = Path().apply {
                            moveTo(s * dx, s * 0.2f); lineTo(s * (dx + 0.42f), s * 0.5f); lineTo(s * dx, s * 0.8f); close()
                        }
                        drawPath(tri, Color.White)
                    }
                }
            }
        }
        // надпись-фейерверк: работа сделана!
        if (bannerOn) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 130.dp)
                    .graphicsLayer {
                        val k = bannerPhase
                        val pop = if (k < 0.15f) k / 0.15f else 1f
                        scaleX = 0.6f + 0.5f * pop + 0.05f * sin(k * 18f)
                        scaleY = scaleX
                        rotationZ = sin(k * 12f) * 4f
                        alpha = if (k > 0.8f) ((1f - k) / 0.2f).coerceIn(0f, 1f) else 1f
                    }
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFFF4DA6), Color(0xFFFF8A3D))))
                    .border(4.dp, Color.White, RoundedCornerShape(26.dp))
                    .padding(horizontal = 26.dp, vertical = 8.dp)
            ) { Text(bannerText, fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White) }
        }

        // альбом «Главный мойщик»
        if (showAlbum) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x99102A4A))
                    .clickable { showAlbum = false; m.lastTouch = m.clock },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFF2F8FF))
                        .border(5.dp, Color(0xFF6DB3F2), RoundedCornerShape(28.dp))
                        .clickable(enabled = false) {}
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Альбом «Главный мойщик»", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E5C8A))
                    Row {
                        Text("Помыто игрушек:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3F6FA0))
                        Text(" $washed", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3F6FA0))
                    }
                    Spacer(Modifier.height(8.dp))
                    CW_STICKERS.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { st ->
                                val unlocked = washed >= st.need
                                Column(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (unlocked) Color(0xFFD9ECFF) else Color(0xFFE6EBF1))
                                        .padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Canvas(Modifier.size(70.dp)) {
                                        if (unlocked) cwStickerArt(st.id, Offset(size.width / 2, size.height / 2), size.width * 0.4f)
                                        else {
                                            drawCircle(Color(0x22000000), size.width * 0.38f, Offset(size.width / 2, size.height / 2))
                                            drawRoundRect(Color(0xFF7A8EA3), Offset(size.width * 0.36f, size.height * 0.46f), Size(size.width * 0.28f, size.height * 0.22f), CornerRadius(6f))
                                            drawArc(Color(0xFF7A8EA3), 180f, 180f, false, Offset(size.width * 0.4f, size.height * 0.3f), Size(size.width * 0.2f, size.height * 0.2f), style = Stroke(5f))
                                        }
                                    }
                                    Text(
                                        if (unlocked) st.name else "Ещё ${st.need - washed}",
                                        fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E5C8A),
                                        textAlign = TextAlign.Center, maxLines = 2
                                    )
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
                    .background(Color(0xAA102A4A))
                    .clickable { rewardSticker = null; m.lastTouch = m.clock },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFF2F8FF))
                        .border(5.dp, Color(0xFF6DB3F2), RoundedCornerShape(28.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Отличная работа!", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF3B8EE8))
                    Canvas(Modifier.size(150.dp)) { cwStickerArt(rs.id, Offset(size.width / 2, size.height / 2), size.width * 0.42f) }
                    Text(rs.name, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E5C8A))
                    val up = rs.upgrade
                    if (up != null) {
                        Spacer(Modifier.height(6.dp))
                        Text("Новый тюнинг:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3F6FA0))
                        Text(up, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFE0780A))
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF3B8EE8))
                            .padding(horizontal = 28.dp, vertical = 8.dp)
                    ) { Text("Следующая игрушка", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White) }
                }
            }
        }
    }
}
