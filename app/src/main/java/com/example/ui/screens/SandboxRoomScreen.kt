package com.example.ui.screens

import com.example.shared.withFrameNanosEco
import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.LocalAgeProfile
import com.example.shared.SoundManager
import com.example.shared.i18n.Text
import com.example.shared.rememberHaptics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// Комната «Песочница с формочками».
// Формочку тащим на песок (она наполняется сама) -> поливаем из лейки (песок темнеет только там, где полили)
// -> тапаем по формочке: «Пхыть!» -> куличик с глазками. Куличик можно рассыпать, а лопаткой —
// разровнять песок или (на мокром песке) построить замок. Награды: наклейки «Master Builder» и новые формочки.

private const val SB_PREFS = "sandbox_prefs"
private const val SB_GW = 28
private const val SB_GH = 16

private const val SB_PELMEN = 0
private const val SB_STAR = 1
private const val SB_BEAR = 2
private const val SB_CROWN = 3
private const val SB_ROCKET = 4
private const val SB_CAN = 10
private const val SB_SHOVEL = 11
private const val SB_CASTLE = 20

private class SbSticker(val id: Int, val name: String, val need: Int, val desc: String)

private val SB_STICKERS = listOf(
    SbSticker(0, "Первый куличик", 1, "Сделай 1 куличик"),
    SbSticker(1, "Король песка", 3, "Сделай 3 куличика"),
    SbSticker(2, "Ракетный строитель", 6, "Сделай 6 куличиков"),
    SbSticker(3, "Master Builder", 10, "Сделай 10 куличиков")
)

private fun sbMoldName(t: Int) = when (t) {
    SB_PELMEN -> "Пельмешек"
    SB_STAR -> "Звёздочка"
    SB_BEAR -> "Мишка"
    SB_CROWN -> "Корона"
    else -> "Ракета"
}

private class SbMold(val type: Int, var x: Float, var y: Float, val real: Boolean) {
    var phase = 0          // 0 наполняется, 1 полная (сухая), 2 готова (влажная), 3 снимается
    var t = 0f
    var fill = 0f
    var wob = 0f
    var glow = 0f
    var liftT = 0f
    var spawned = false
    var dead = false
}

private class SbCake(val type: Int, var x: Float, var y: Float, val real: Boolean) {
    var age = 0f
    var blink = 0f
    var nextBlink = 1.5f + Random.nextFloat() * 2f
    var poke = 0f
    var collapsing = false
    var ct = 0f
    var soak = 0f
    var dead = false
}

private class SbPart(
    var kind: Int, var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float, val size: Float, val color: Color, var rot: Float, val vr: Float, val gy: Float
)

private class SbDent(val x: Float, val y: Float, val r: Float, var a: Float)
private class SbHeap(val x: Float, val y: Float, val r: Float, var a: Float)

private class SbAuto(val kind: Int, val real: Boolean, val sx: Float, val sy: Float, val tx: Float, val ty: Float, val actDur: Float) {
    var t = 0f
    var placed = false
}

private class SbModel {
    var clock = 0f
    var lastTouch = 0f
    var demo = false
    var demoStep = 0
    var demoT = 0f
    var handX = 0f
    var handY = 0f
    var handTx = 0f
    var handTy = 0f
    var handDown = false

    val moist = FloatArray(SB_GW * SB_GH)
    var moistDirty = true
    var moistTimer = 0f

    val molds = ArrayList<SbMold>()
    val cakes = ArrayList<SbCake>()
    val parts = ArrayList<SbPart>()
    val dents = ArrayList<SbDent>()
    val heaps = ArrayList<SbHeap>()

    var carry = -1
    var cx = 0f
    var cy = 0f
    var carryReal = true
    var carryFromBar = false
    var downX = 0f
    var downY = 0f
    var downT = 0f
    var travel = 0f
    var sandTravel = 0f
    var carryTravelTotal = 0f
    var pouring = false
    var shoveling = false
    var auto: SbAuto? = null

    var tWater = 0f
    var tSmooth = 0f
    var tTap = 0f
    var tDrop = 0f
    var tTrail = 0f
    var usedCan = false
    var hint = ""
    var hintTimer = 0f
    var pulseSlot = -1
}

private val SB_DOTS: List<FloatArray> = run {
    val r = Random(7)
    List(300) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.8f + r.nextFloat() * 1.6f, r.nextFloat()) }
}
private val SB_FLOWERS: List<FloatArray> = run {
    val r = Random(11)
    List(16) { floatArrayOf(r.nextFloat(), 0.28f + r.nextFloat() * 0.5f, r.nextInt(4).toFloat()) }
}
private val SB_FLOWER_COLORS = listOf(Color(0xFFFF7BA9), Color(0xFFFFF176), Color(0xFFFFFFFF), Color(0xFFB39DDB))

private fun sbEase(t: Float): Float { val k = t.coerceIn(0f, 1f); return k * k * (3f - 2f * k) }
private fun sbLerp(a: Float, b: Float, t: Float) = a + (b - a) * t

// ───────────────────────── формы (единичные координаты) ─────────────────────────

private fun sbCircle(cx: Float, cy: Float, r: Float): Path =
    Path().apply { addOval(Rect(Offset(cx - r, cy - r), Size(2f * r, 2f * r))) }

private fun sbPoly(vararg p: Float): Path {
    val path = Path()
    path.moveTo(p[0], p[1])
    var i = 2
    while (i < p.size) { path.lineTo(p[i], p[i + 1]); i += 2 }
    path.close()
    return path
}

private val SB_SHAPES: Map<Int, List<Path>> by lazy {
    val pelmen = Path().apply {
        moveTo(-1f, 0.25f)
        cubicTo(-1.05f, -0.95f, 1.05f, -0.95f, 1f, 0.25f)
        cubicTo(0.75f, 0.62f, -0.75f, 0.62f, -1f, 0.25f)
        close()
    }
    val pelmenList = ArrayList<Path>()
    pelmenList.add(pelmen)
    for (i in 0..6) {
        val x = -0.84f + i * 0.28f
        pelmenList.add(sbCircle(x, 0.27f + 0.27f * (1f - x * x), 0.13f))
    }
    val starPts = FloatArray(20)
    for (i in 0 until 10) {
        val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
        val r = if (i % 2 == 0) 1.08f else 0.5f
        starPts[i * 2] = cos(a) * r
        starPts[i * 2 + 1] = sin(a) * r + 0.06f
    }
    val rocketBody = Path().apply {
        moveTo(0f, -1f)
        cubicTo(0.7f, -0.5f, 0.58f, 0.4f, 0.42f, 0.74f)
        lineTo(-0.42f, 0.74f)
        cubicTo(-0.58f, 0.4f, -0.7f, -0.5f, 0f, -1f)
        close()
    }
    mapOf(
        SB_PELMEN to pelmenList,
        SB_STAR to listOf(sbPoly(*starPts)),
        SB_BEAR to listOf(sbCircle(0f, 0.12f, 0.8f), sbCircle(-0.62f, -0.58f, 0.34f), sbCircle(0.62f, -0.58f, 0.34f)),
        SB_CROWN to listOf(
            sbPoly(-0.92f, 0.78f, -0.92f, -0.35f, -0.46f, 0.05f, 0f, -0.7f, 0.46f, 0.05f, 0.92f, -0.35f, 0.92f, 0.78f),
            sbCircle(-0.92f, -0.4f, 0.14f), sbCircle(0f, -0.76f, 0.14f), sbCircle(0.92f, -0.4f, 0.14f)
        ),
        SB_ROCKET to listOf(
            rocketBody,
            sbPoly(0.4f, 0.15f, 0.92f, 0.85f, 0.4f, 0.74f),
            sbPoly(-0.4f, 0.15f, -0.92f, 0.85f, -0.4f, 0.74f)
        )
    )
}

private fun sbMoldColor(t: Int): Color = when (t) {
    SB_PELMEN -> Color(0xFFFF9A52)
    SB_STAR -> Color(0xFFFFC93C)
    SB_BEAR -> Color(0xFFC98B58)
    SB_CROWN -> Color(0xFFFFD54A)
    else -> Color(0xFF5CAEFF)
}

private fun sbMoldDark(t: Int): Color = when (t) {
    SB_PELMEN -> Color(0xFFC96A2A)
    SB_STAR -> Color(0xFFD99A12)
    SB_BEAR -> Color(0xFF8E5A30)
    SB_CROWN -> Color(0xFFC9A000)
    else -> Color(0xFF2E7BD0)
}

private fun Color.sbA(a: Float): Color = copy(alpha = (alpha * a).coerceIn(0f, 1f))

// ───────────────────────── рисование предметов ─────────────────────────

private fun DrawScope.sbMold(type: Int, cx: Float, cy: Float, s: Float, fill: Float, glow: Float, tilt: Float, a: Float, inner: Boolean) {
    val shapes = SB_SHAPES[type] ?: return
    drawOval(Color(0x33000000).sbA(a), Offset(cx - s * 0.95f, cy + s * 0.7f), Size(s * 1.9f, s * 0.4f))
    withTransform({ translate(cx, cy); rotate(tilt, Offset.Zero); scale(s, s, Offset.Zero) }) {
        for (p in shapes) drawPath(p, sbMoldDark(type).sbA(a), style = Stroke(0.2f, join = StrokeJoin.Round, cap = StrokeCap.Round))
        for (p in shapes) drawPath(p, sbMoldColor(type).sbA(a))
        if (inner) {
            withTransform({ scale(0.74f, 0.74f, Offset.Zero) }) {
                for (p in shapes) drawPath(p, Color(0xFF5E3F22).sbA(a * 0.9f))
                if (fill > 0.01f) {
                    clipRect(-1.4f, 1.0f - 2.2f * fill, 1.4f, 1.4f) {
                        for (p in shapes) drawPath(p, Brush.verticalGradient(listOf(Color(0xFFF3D27E), Color(0xFFE0B35C)), -1f, 1f), alpha = a)
                    }
                }
            }
        }
        drawCircle(Color.White.sbA(0.35f * a), 0.16f, Offset(-0.5f, -0.42f))
        if (glow > 0.01f) for (p in shapes) drawPath(p, Color.White.sbA(glow * a), style = Stroke(0.11f, join = StrokeJoin.Round))
    }
}

private fun DrawScope.sbFace(type: Int, blink: Float, happy: Float) {
    val dy = when (type) {
        SB_PELMEN -> -0.04f
        SB_STAR -> 0.1f
        SB_BEAR -> 0.2f
        SB_CROWN -> 0.22f
        SB_ROCKET -> -0.16f
        else -> 0.12f
    }
    val ex = if (type == SB_STAR) 0.2f else 0.27f
    val er = if (type == SB_STAR) 0.09f else 0.11f
    val ink = Color(0xFF3A2410)
    if (type == SB_BEAR) {
        drawOval(Color(0xFFF0C99A), Offset(-0.3f, dy + 0.02f), Size(0.6f, 0.46f))
        drawOval(Color(0xFF3A2410), Offset(-0.1f, dy + 0.05f), Size(0.2f, 0.13f))
    }
    for (sgn in listOf(-1f, 1f)) {
        val ex0 = sgn * ex
        if (blink > 0.5f) {
            drawLine(ink, Offset(ex0 - er, dy - 0.1f), Offset(ex0 + er, dy - 0.1f), 0.05f, StrokeCap.Round)
        } else {
            drawCircle(Color.White, er * 1.35f, Offset(ex0, dy - 0.1f))
            drawCircle(ink, er, Offset(ex0 + 0.01f, dy - 0.09f))
            drawCircle(Color.White, er * 0.35f, Offset(ex0 + er * 0.35f, dy - 0.09f - er * 0.4f))
        }
        drawCircle(Color(0xFFFF8FA3).sbA(0.55f), 0.09f, Offset(sgn * (ex + 0.2f), dy + 0.1f))
    }
    val mw = 0.3f + 0.12f * happy
    if (type != SB_BEAR) {
        drawArc(ink, 20f, 140f, false, Offset(-mw / 2f, dy + 0.0f), Size(mw, 0.26f + 0.1f * happy), style = Stroke(0.05f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.sbCake(type: Int, cx: Float, cy: Float, s: Float, sx: Float, sy: Float, blink: Float, happy: Float, a: Float) {
    drawOval(Color(0x44000000).sbA(a), Offset(cx - s * 1.0f * sx, cy + s * 0.62f), Size(s * 2f * sx, s * 0.45f))
    withTransform({ translate(cx, cy + s * 0.75f); scale(s * sx, s * sy, Offset.Zero); translate(0f, -0.75f) }) {
        if (type == SB_CASTLE) {
            sbCastleBody(a)
            sbFaceAt(0f, 0.12f, blink, happy, a)
            return@withTransform
        }
        val shapes = SB_SHAPES[type] ?: return@withTransform
        withTransform({ translate(0f, 0.13f) }) {
            for (p in shapes) drawPath(p, Color(0xFFA9792F).sbA(a), style = Stroke(0.1f, join = StrokeJoin.Round))
            for (p in shapes) drawPath(p, Color(0xFFB98638).sbA(a))
        }
        val br = Brush.radialGradient(
            listOf(Color(0xFFF8DC96), Color(0xFFE3B866), Color(0xFFC79848)),
            Offset(-0.35f, -0.4f), 1.9f
        )
        for (p in shapes) drawPath(p, brush = br, alpha = a)
        for (p in shapes) drawPath(p, Color(0xFF9A6C2E).sbA(a), style = Stroke(0.045f, join = StrokeJoin.Round))
        for (i in 0 until 9) {
            val dx = SB_DOTS[i][0] * 1.4f - 0.7f
            val dy = SB_DOTS[i][1] * 1.0f - 0.5f
            drawCircle(Color(0xFF9A6C2E).sbA(0.35f * a), 0.025f, Offset(dx, dy))
        }
        drawOval(Color.White.sbA(0.3f * a), Offset(-0.62f, -0.62f), Size(0.4f, 0.22f))
        withTransform({ scale(1f, 1f, Offset.Zero) }) { sbFaceA(type, blink, happy, a) }
    }
}

private fun DrawScope.sbFaceA(type: Int, blink: Float, happy: Float, a: Float) {
    if (a >= 0.99f) sbFace(type, blink, happy) else {
        // при растворении лицо просто бледнеет
        sbFace(type, blink, happy)
    }
}

private fun DrawScope.sbFaceAt(x: Float, y: Float, blink: Float, happy: Float, a: Float) {
    withTransform({ translate(x, y) }) { sbFaceA(SB_PELMEN, blink, happy, a) }
}

private fun DrawScope.sbCastleBody(a: Float) {
    val sand = Color(0xFFE3B866).sbA(a)
    val dark = Color(0xFFB98638).sbA(a)
    val line = Color(0xFF9A6C2E).sbA(a)
    fun block(l: Float, t: Float, r: Float, b: Float) {
        drawRoundRect(dark, Offset(l, t + 0.1f), Size(r - l, b - t), CornerRadius(0.06f))
        drawRoundRect(sand, Offset(l, t), Size(r - l, b - t), CornerRadius(0.06f))
        drawRoundRect(line, Offset(l, t), Size(r - l, b - t), CornerRadius(0.06f), style = Stroke(0.04f))
    }
    block(-0.95f, -0.45f, -0.5f, 0.72f)
    block(0.5f, -0.45f, 0.95f, 0.72f)
    block(-0.62f, -0.2f, 0.62f, 0.78f)
    for (k in 0 until 3) {
        val bx = -0.55f + k * 0.4f
        block(bx, -0.36f, bx + 0.26f, -0.18f)
    }
    for (side in listOf(-0.72f, 0.72f)) {
        drawRect(sand, Offset(side - 0.07f, -0.58f), Size(0.14f, 0.15f))
        drawRect(line, Offset(side - 0.07f, -0.58f), Size(0.14f, 0.15f), style = Stroke(0.035f))
    }
    drawLine(Color(0xFF7A5A3A).sbA(a), Offset(0f, -0.36f), Offset(0f, -0.82f), 0.045f)
    drawPath(sbPoly(0f, -0.82f, 0.36f, -0.7f, 0f, -0.58f), Color(0xFFFF6F61).sbA(a))
    drawRoundRect(Color(0xFF6B4A2A).sbA(a), Offset(-0.15f, 0.45f), Size(0.3f, 0.33f), CornerRadius(0.15f))
}

private fun DrawScope.sbCan(cx: Float, cy: Float, s: Float, tilt: Float) {
    drawOval(Color(0x2A000000), Offset(cx - s * 0.7f, cy + s * 0.78f), Size(s * 1.4f, s * 0.3f))
    withTransform({ translate(cx, cy); rotate(tilt, Offset.Zero); scale(s, s, Offset.Zero) }) {
        val dark = Color(0xFF2E7BD0)
        val body = Path().apply {
            moveTo(-0.55f, -0.35f); lineTo(0.5f, -0.35f); lineTo(0.62f, 0.7f); lineTo(-0.67f, 0.7f); close()
        }
        // ручка
        drawArc(dark, 180f, 180f, false, Offset(-0.5f, -0.88f), Size(0.95f, 1.0f), style = Stroke(0.12f, cap = StrokeCap.Round))
        // носик
        drawLine(dark, Offset(0.55f, 0.35f), Offset(1.05f, -0.2f), 0.26f, StrokeCap.Round)
        drawLine(Color(0xFF5CAEFF), Offset(0.55f, 0.35f), Offset(1.05f, -0.2f), 0.18f, StrokeCap.Round)
        drawOval(Color(0xFF9AD0FF), Offset(0.92f, -0.42f), Size(0.3f, 0.34f))
        drawOval(dark, Offset(0.92f, -0.42f), Size(0.3f, 0.34f), style = Stroke(0.05f))
        drawPath(body, dark, style = Stroke(0.16f, join = StrokeJoin.Round))
        drawPath(body, Brush.verticalGradient(listOf(Color(0xFF8CCBFF), Color(0xFF4FA6F5)), -0.35f, 0.7f))
        drawOval(Color(0xFF2E7BD0), Offset(-0.55f, -0.45f), Size(1.05f, 0.2f))
        drawRoundRect(Color.White.sbA(0.45f), Offset(-0.4f, -0.2f), Size(0.14f, 0.7f), CornerRadius(0.07f))
        // капелька на боку
        drawCircle(Color.White.sbA(0.85f), 0.1f, Offset(0.08f, 0.22f))
    }
}

private fun DrawScope.sbShovel(cx: Float, cy: Float, s: Float) {
    drawOval(Color(0x2A000000), Offset(cx - s * 0.5f, cy + s * 0.9f), Size(s * 1.0f, s * 0.26f))
    withTransform({ translate(cx, cy); rotate(18f, Offset.Zero); scale(s, s, Offset.Zero) }) {
        drawLine(Color(0xFF8E5A2B), Offset(0f, -1.0f), Offset(0f, 0.1f), 0.17f, StrokeCap.Round)
        drawArc(Color(0xFF8E5A2B), 0f, 360f, false, Offset(-0.26f, -1.28f), Size(0.52f, 0.42f), style = Stroke(0.1f))
        val blade = Path().apply {
            moveTo(-0.32f, 0.0f); lineTo(0.32f, 0.0f)
            cubicTo(0.42f, 0.5f, 0.2f, 0.95f, 0f, 1.05f)
            cubicTo(-0.2f, 0.95f, -0.42f, 0.5f, -0.32f, 0.0f); close()
        }
        drawPath(blade, Color(0xFF1F9A5C), style = Stroke(0.12f, join = StrokeJoin.Round))
        drawPath(blade, Brush.verticalGradient(listOf(Color(0xFF6BE0A0), Color(0xFF2FBF76)), 0f, 1f))
        drawRoundRect(Color.White.sbA(0.4f), Offset(-0.16f, 0.1f), Size(0.1f, 0.6f), CornerRadius(0.05f))
    }
}

private fun DrawScope.sbSparkle(c: Offset, r: Float, color: Color, rot: Float) {
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

private fun DrawScope.sbHand(c: Offset, r: Float, down: Boolean) {
    val k = if (down) 0.85f else 1f
    drawCircle(Color.White.copy(alpha = 0.35f), r * 1.7f * k, c)
    drawCircle(Color.White.copy(alpha = 0.9f), r * k, c)
    drawCircle(Color(0xFF8E5A2B), r * k, c, style = Stroke(3f))
}

private fun DrawScope.sbSticker(id: Int, c: Offset, r: Float) {
    drawCircle(Color(0xFFFFF1C9), r * 1.05f, c)
    drawCircle(Color(0xFFE3B866), r * 1.05f, c, style = Stroke(r * 0.09f))
    when (id) {
        0 -> sbCake(SB_PELMEN, c.x, c.y - r * 0.05f, r * 0.62f, 1f, 1f, 0f, 1f, 1f)
        1 -> sbCake(SB_CROWN, c.x, c.y - r * 0.05f, r * 0.58f, 1f, 1f, 0f, 1f, 1f)
        2 -> sbCake(SB_ROCKET, c.x, c.y - r * 0.05f, r * 0.58f, 1f, 1f, 0f, 1f, 1f)
        else -> {
            sbCake(SB_CASTLE, c.x, c.y - r * 0.02f, r * 0.55f, 1f, 1f, 0f, 1f, 1f)
            sbSparkle(Offset(c.x + r * 0.7f, c.y - r * 0.65f), r * 0.3f, Color(0xFFFFD54A), 0f)
            sbSparkle(Offset(c.x - r * 0.72f, c.y - r * 0.5f), r * 0.2f, Color(0xFFFFF3A0), 0.4f)
        }
    }
}

// ───────────────────────── экран ─────────────────────────

@Composable
fun SandboxRoomScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val sm = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val toddler = LocalAgeProfile.current.isToddler2
    val prefs = remember { context.getSharedPreferences(SB_PREFS, Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) {
        sm.preloadRaw(
            listOf(
                "fx_sb_pour", "fx_sb_water", "fx_sb_lift", "fx_sb_collapse", "fx_sb_giggle",
                "fx_sb_smooth", "fx_sb_tap", "fx_sb_dry", "fx_sb_place", "wl_sparkle", "wl_win", "wl_pin"
            )
        )
    }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(0) }
    val m = remember { SbModel() }
    val bmp = remember { Bitmap.createBitmap(SB_GW * 2, SB_GH * 2, Bitmap.Config.ARGB_8888) }
    var moistImg by remember { mutableStateOf<ImageBitmap?>(null) }

    var built by remember { mutableIntStateOf(prefs.getInt("built", 0)) }
    var smashed by remember { mutableIntStateOf(prefs.getInt("smashed", 0)) }
    var hintText by remember { mutableStateOf("") }
    var showAlbum by remember { mutableStateOf(false) }
    var rewardSticker by remember { mutableStateOf<SbSticker?>(null) }
    var rewardMold by remember { mutableStateOf<Int?>(null) }
    var demoOn by remember { mutableStateOf(false) }

    fun W() = boxSize.width.toFloat()
    fun H() = boxSize.height.toFloat()
    fun snd(n: String, v: Float = 1f, r: Float = 1f) { sm.play(n, volume = v, rate = r) }

    // ── геометрия ──
    fun sandX() = W() * 0.075f
    fun sandY() = H() * 0.37f
    fun sandW() = W() * 0.85f
    fun sandH() = H() * 0.40f
    fun barY() = H() * 0.82f
    fun inSand(x: Float, y: Float) = x >= sandX() && x <= sandX() + sandW() && y >= sandY() && y <= sandY() + sandH()
    fun moldS() = W() * 0.085f
    fun cakeS() = W() * 0.1f
    fun canOffX() = W() * 0.11f
    fun canOffY() = H() * 0.085f
    fun shovelOffY() = H() * 0.07f

    fun unlockedMolds(): List<Int> {
        val l = arrayListOf(SB_PELMEN, SB_STAR, SB_BEAR)
        if (built >= 3) l.add(SB_CROWN)
        if (built >= 6) l.add(SB_ROCKET)
        return l
    }
    fun barItems(): List<Int> = unlockedMolds() + listOf(SB_CAN, SB_SHOVEL)
    fun slotCenter(i: Int, n: Int) = Offset(W() * (i + 0.5f) / n, barY() + (H() - barY()) * 0.52f)
    fun slotSize(n: Int) = min(W() / n * 0.8f, (H() - barY()) * 0.8f) * 0.5f
    fun slotAt(x: Float, y: Float): Int {
        if (y < barY() - H() * 0.02f) return -1
        val n = barItems().size
        val i = (x / (W() / n)).toInt().coerceIn(0, n - 1)
        return i
    }

    // ── влажность ──
    fun cellX(i: Int) = sandX() + sandW() * (i + 0.5f) / SB_GW
    fun cellY(j: Int) = sandY() + sandH() * (j + 0.5f) / SB_GH

    fun addMoist(x: Float, y: Float, r: Float, amount: Float) {
        for (j in 0 until SB_GH) for (i in 0 until SB_GW) {
            val d = hypot(cellX(i) - x, cellY(j) - y)
            if (d < r) {
                val k = 1f - d / r
                val idx = j * SB_GW + i
                m.moist[idx] = (m.moist[idx] + amount * k * k).coerceIn(0f, 1f)
            }
        }
        m.moistDirty = true
    }

    fun moistAt(x: Float, y: Float, r: Float): Float {
        var sum = 0f
        var n = 0
        for (j in 0 until SB_GH) for (i in 0 until SB_GW) {
            if (hypot(cellX(i) - x, cellY(j) - y) < r) { sum += m.moist[j * SB_GW + i]; n++ }
        }
        return if (n == 0) 0f else sum / n
    }

    fun readyThreshold() = if (toddler) 0.3f else 0.45f

    fun burst(kind: Int, x: Float, y: Float, n: Int, color: Color, spd: Float, size: Float, up: Float = 0f, life: Float = 0.8f, gy: Float = 1e9f) {
        repeat(n) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val sp = spd * (0.35f + Random.nextFloat() * 0.65f)
            m.parts.add(
                SbPart(
                    kind, x, y, cos(a) * sp, sin(a) * sp - up,
                    life * (0.6f + Random.nextFloat() * 0.6f), life, size * (0.6f + Random.nextFloat() * 0.8f),
                    color, Random.nextFloat() * 6f, (Random.nextFloat() - 0.5f) * 8f, gy
                )
            )
        }
    }

    fun sandColor() = if (Random.nextBoolean()) Color(0xFFEBC26B) else Color(0xFFD9A857)

    fun addDent(x: Float, y: Float, r: Float, a: Float = 0.5f) {
        m.dents.add(SbDent(x, y, r, a))
        if (m.dents.size > 110) m.dents.removeAt(0)
    }

    // ── прогресс ──
    fun saveProgress() {
        prefs.edit().putInt("built", built).putInt("smashed", smashed).apply()
    }

    fun onBuilt() {
        val old = built
        built += 1
        saveProgress()
        val st = SB_STICKERS.firstOrNull { it.need == built }
        val newMold = when (built) { 3 -> SB_CROWN; 6 -> SB_ROCKET; else -> null }
        if (st != null && old < st.need) {
            rewardSticker = st
            rewardMold = newMold
            snd("wl_win", 0.9f)
            haptics.vibratePop()
        }
    }

    fun collapseCake(c: SbCake, byUser: Boolean) {
        if (c.collapsing || c.dead) return
        c.collapsing = true
        c.ct = 0f
        val sz = cakeS()
        burst(0, c.x, c.y, 26, Color(0xFFD9A857), sz * 3f, sz * 0.09f, up = sz * 1.5f, life = 0.9f, gy = c.y + sz * 0.7f)
        burst(0, c.x, c.y, 14, Color(0xFFEBC26B), sz * 2f, sz * 0.07f, up = sz * 1.0f, life = 0.8f, gy = c.y + sz * 0.7f)
        if (c.real) {
            snd("fx_sb_collapse", 0.9f)
            snd("fx_sb_giggle", 0.6f)
            haptics.vibrateSquish()
            if (byUser) { smashed += 1; saveProgress() }
        } else {
            snd("fx_sb_collapse", 0.7f)
        }
    }

    fun placeMold(type: Int, x0: Float, y0: Float, real: Boolean) {
        val s = moldS()
        val x = x0.coerceIn(sandX() + s * 1.1f, sandX() + sandW() - s * 1.1f)
        val y = y0.coerceIn(sandY() + s * 0.9f, sandY() + sandH() - s * 0.9f)
        for (c in m.cakes) if (!c.collapsing && hypot(c.x - x, c.y - y) < cakeS() * 1.5f) collapseCake(c, false)
        for (o in m.molds) if (!o.dead && o.phase < 3 && hypot(o.x - x, o.y - y) < s * 1.8f) o.dead = true
        while (m.molds.count { !it.dead } >= 3) m.molds.firstOrNull { !it.dead }?.dead = true
        m.molds.add(SbMold(type, x, y, real))
        addDent(x, y + s * 0.5f, s * 1.1f, 0.35f)
        burst(3, x, y + s * 0.6f, 7, Color(0xFFE3B866), s * 1.2f, s * 0.2f, up = s * 0.3f, life = 0.5f)
        snd("fx_sb_place", 0.8f)
        snd("fx_sb_pour", 0.7f)
        haptics.vibrateShort()
    }

    fun tapMold(mo: SbMold, real: Boolean) {
        if (mo.dead || mo.phase == 3) return
        val s = moldS()
        when (mo.phase) {
            0 -> { mo.wob = 0.4f; snd("fx_sb_tap", 0.6f) }
            1 -> {
                mo.wob = 0.7f
                burst(3, mo.x, mo.y - s * 0.5f, 10, Color(0xFFF3D27E), s * 1.4f, s * 0.22f, up = s * 0.4f, life = 0.6f)
                snd("fx_sb_dry", 0.8f)
                haptics.vibrateShort()
            }
            else -> {
                mo.phase = 3
                mo.liftT = 0f
                snd("fx_sb_lift", 1f)
                haptics.vibratePop()
            }
        }
    }

    fun tapCake(c: SbCake) {
        if (c.collapsing || c.dead) return
        collapseCake(c, c.real)
    }

    fun tryCastle(x: Float, y: Float, real: Boolean): Boolean {
        val s = cakeS()
        if (moistAt(x, y, W() * 0.1f) < readyThreshold() + 0.05f) {
            m.hint = "Для замка нужен мокрый песок — полей из лейки"; hintText = m.hint; m.hintTimer = 2.5f
            snd("fx_sb_dry", 0.6f)
            return false
        }
        val cx = x.coerceIn(sandX() + s * 1.2f, sandX() + sandW() - s * 1.2f)
        val cy = y.coerceIn(sandY() + s * 1.0f, sandY() + sandH() - s * 0.9f)
        for (c in m.cakes) if (!c.collapsing && hypot(c.x - cx, c.y - cy) < s * 1.6f) collapseCake(c, false)
        m.cakes.add(SbCake(SB_CASTLE, cx, cy, real))
        while (m.cakes.count { !it.collapsing && !it.dead } > 4) m.cakes.firstOrNull { !it.collapsing && !it.dead }?.let { collapseCake(it, false) }
        burst(3, cx, cy + s * 0.5f, 14, Color(0xFFE3B866), s * 1.8f, s * 0.14f, up = s * 0.5f, life = 0.6f)
        burst(2, cx, cy - s * 0.3f, 8, Color(0xFFFFF3A0), s * 1.8f, s * 0.12f, life = 0.7f)
        snd("fx_sb_lift", 0.9f)
        snd("fx_sb_giggle", 0.7f)
        haptics.vibratePop()
        if (real) onBuilt()
        return true
    }

    // ── авто-действия (тап по инструменту в панели и демо) ──
    fun contactOffset(kind: Int): Offset = when (kind) {
        SB_CAN -> Offset(canOffX(), canOffY())
        SB_SHOVEL -> Offset(0f, shovelOffY())
        else -> Offset.Zero
    }

    fun autoSpot(): Offset {
        val s = moldS()
        val cands = listOf(0.5f to 0.5f, 0.28f to 0.45f, 0.72f to 0.45f, 0.35f to 0.75f, 0.65f to 0.75f, 0.5f to 0.25f)
        for ((fx, fy) in cands) {
            val x = sandX() + sandW() * fx
            val y = sandY() + sandH() * fy
            val free = m.molds.none { !it.dead && hypot(it.x - x, it.y - y) < s * 2.4f } &&
                m.cakes.none { !it.dead && !it.collapsing && hypot(it.x - x, it.y - y) < s * 2.4f }
            if (free) return Offset(x, y)
        }
        return Offset(sandX() + sandW() * 0.5f, sandY() + sandH() * 0.5f)
    }

    fun startAuto(kind: Int, real: Boolean) {
        val items = barItems()
        val idx = items.indexOf(kind).let { if (it < 0) 0 else it }
        val sc = slotCenter(idx, items.size)
        val target: Offset
        var act = 0.2f
        when (kind) {
            SB_CAN -> {
                val need = m.molds.firstOrNull { !it.dead && it.phase in 1..2 }
                val cakeT = m.cakes.firstOrNull { !it.dead && !it.collapsing && it.real == real }
                target = if (need != null) Offset(need.x, need.y) else if (cakeT != null && !real) Offset(cakeT.x, cakeT.y) else Offset(sandX() + sandW() * 0.5f, sandY() + sandH() * 0.55f)
                act = 2.3f
            }
            SB_SHOVEL -> {
                target = Offset(sandX() + sandW() * 0.5f, sandY() + sandH() * 0.6f)
                act = 1.5f
            }
            else -> {
                target = autoSpot()
            }
        }
        m.auto = SbAuto(kind, real, sc.x, sc.y, target.x, target.y, act)
    }

    fun cancelAuto() {
        val a = m.auto
        if (a != null) {
            m.auto = null
            if (m.carry != -1 && !m.carryReal) m.carry = -1
            if (m.carry == a.kind) m.carry = -1
        }
        m.pouring = false
        m.shoveling = false
    }

    fun stopDemo() {
        if (!m.demo) return
        m.demo = false
        demoOn = false
        m.demoStep = 0
        cancelAuto()
        for (mo in m.molds) if (!mo.real) mo.dead = true
        for (c in m.cakes) if (!c.real && !c.collapsing) { c.collapsing = true; c.ct = 0.4f }
    }

    fun startDemo() {
        m.demo = true
        demoOn = true
        m.demoStep = 0
        m.demoT = 0f
        m.handX = W() * 0.5f
        m.handY = H() * 0.9f
        m.handTx = m.handX
        m.handTy = m.handY
    }

    // ── применение инструмента в руке ──
    fun applyCarry(dt: Float) {
        val kind = m.carry
        m.pouring = false
        m.shoveling = false
        if (kind == SB_CAN) {
            val ix = m.cx + canOffX()
            val iy = m.cy + canOffY()
            val over = inSand(ix, iy)
            m.pouring = over
            if (over) {
                m.usedCan = true
                val rate = if (toddler) 1.1f else 0.8f
                addMoist(ix, iy, W() * 0.095f, rate * dt)
                m.tDrop -= dt
                while (m.tDrop <= 0f) {
                    m.tDrop += 0.03f
                    val sx = m.cx + W() * 0.095f + (Random.nextFloat() - 0.5f) * W() * 0.04f
                    val sy = m.cy + H() * 0.0f
                    m.parts.add(
                        SbPart(1, sx, sy, (Random.nextFloat() - 0.5f) * 10f, H() * 0.1f, 1f, 1f, W() * 0.012f, Color(0xFF5CC0FF), 0f, 0f, iy + (Random.nextFloat() - 0.5f) * H() * 0.03f)
                    )
                }
                m.tWater -= dt
                if (m.tWater <= 0f) { m.tWater = 0.5f; snd("fx_sb_water", 0.55f); if (m.carryReal) haptics.vibrateWater() }
                for (c in m.cakes) {
                    if (!c.collapsing && !c.dead && hypot(c.x - ix, c.y - iy) < W() * 0.1f) {
                        c.soak += dt
                        if (c.soak > 1.3f) collapseCake(c, false)
                    }
                }
            }
        } else if (kind == SB_SHOVEL) {
            val px = m.cx
            val py = m.cy + shovelOffY()
            if (inSand(px, py)) {
                val speed = m.sandTravel
                m.shoveling = true
                // разравниваем: вмятины и кучки исчезают
                for (d in m.dents) if (hypot(d.x - px, d.y - py) < W() * 0.1f) d.a -= dt * 2.5f
                for (h in m.heaps) if (hypot(h.x - px, h.y - py) < W() * 0.11f) h.a -= dt * 2.5f
                m.dents.removeAll { it.a <= 0f }
                m.heaps.removeAll { it.a <= 0f }
                // толкаем куличики
                for (c in m.cakes) {
                    if (!c.collapsing && !c.dead && hypot(c.x - px, c.y - py) < cakeS() * 1.0f) collapseCake(c, c.real)
                }
                if (speed > 0.002f * W()) {
                    m.tSmooth -= dt
                    if (m.tSmooth <= 0f) { m.tSmooth = 0.45f; snd("fx_sb_smooth", 0.5f); if (m.carryReal) haptics.vibrateShort() }
                    if (Random.nextFloat() < 0.5f) burst(0, px, py, 1, Color(0xFFEBC26B), W() * 0.25f, W() * 0.008f, up = W() * 0.1f, life = 0.5f, gy = py + H() * 0.02f)
                }
            }
        }
        m.sandTravel = 0f
    }

    // ── ввод ──
    fun onDown(x: Float, y: Float, real: Boolean) {
        m.downX = x; m.downY = y; m.downT = m.clock; m.travel = 0f; m.sandTravel = 0f; m.carryTravelTotal = 0f
        m.carryReal = real
        val slot = slotAt(x, y)
        if (slot >= 0) {
            val items = barItems()
            m.carry = items[slot]
            m.carryFromBar = true
            m.cx = x
            m.cy = y - H() * 0.06f
            snd("wl_pin", 0.5f)
            if (m.carry == SB_CAN) {
                m.hint = "Води лейкой по песку — песок станет мокрым"; hintText = m.hint; m.hintTimer = 3f
            } else if (m.carry == SB_SHOVEL) {
                m.hint = if (toddler) "Води лопаткой — песок разгладится" else "Води лопаткой, чтобы разровнять. Отпусти на мокром песке — будет замок!"
                hintText = m.hint; m.hintTimer = 3.5f
            }
            return
        }
        m.carryFromBar = false
        // формочки на песке
        for (mo in m.molds) {
            if (!mo.dead && mo.phase < 3 && hypot(mo.x - x, mo.y - y) < moldS() * 1.35f) {
                tapMold(mo, real)
                return
            }
        }
        // куличики
        for (c in m.cakes) {
            if (!c.collapsing && !c.dead && hypot(c.x - x, (c.y - cakeS() * 0.1f) - y) < cakeS() * 1.25f) {
                tapCake(c)
                return
            }
        }
        // просто песок: пальчиком
        if (inSand(x, y)) {
            m.carry = -1
            m.tTap -= 1f
            snd("fx_sb_tap", 0.6f)
            addDent(x, y, W() * 0.022f, 0.4f)
            burst(0, x, y, 6, Color(0xFFEBC26B), W() * 0.3f, W() * 0.008f, up = W() * 0.12f, life = 0.5f, gy = y + H() * 0.02f)
            haptics.vibrateShort()
        }
    }

    fun onMove(x: Float, y: Float, real: Boolean) {
        if (m.carry != -1) {
            val nx = x
            val ny = y - H() * 0.06f
            val d = hypot(nx - m.cx, ny - m.cy)
            m.travel += d
            if (inSand(nx + contactOffset(m.carry).x, ny + contactOffset(m.carry).y)) { m.sandTravel += d; if (m.carry == SB_SHOVEL) m.carryTravelTotal += d }
            m.cx = nx
            m.cy = ny
        } else if (inSand(x, y)) {
            m.tTrail += hypot(x - m.downX, y - m.downY)
            if (hypot(x - m.downX, y - m.downY) > W() * 0.03f) {
                addDent(x, y, W() * 0.02f, 0.35f)
                burst(0, x, y, 1, Color(0xFFEBC26B), W() * 0.2f, W() * 0.007f, up = W() * 0.06f, life = 0.4f, gy = y + H() * 0.015f)
                m.downX = x; m.downY = y
                m.tSmooth -= 0.1f
                if (m.tSmooth <= 0f) { m.tSmooth = 0.5f; snd("fx_sb_smooth", 0.35f) }
            }
        }
    }

    fun onUp(x: Float, y: Float, real: Boolean) {
        val kind = m.carry
        if (kind == -1) return
        val tapInBar = m.carryFromBar && m.travel < W() * 0.035f && (m.clock - m.downT) < 0.45f
        m.carry = -1
        m.pouring = false
        m.shoveling = false
        if (tapInBar) {
            // тап по значку в панели: делаем всё сами (для малышей)
            startAuto(kind, real)
            return
        }
        when {
            kind <= SB_ROCKET -> {
                if (m.cy < barY() - H() * 0.03f) placeMold(kind, m.cx, m.cy, real)
            }
            kind == SB_SHOVEL -> {
                val px = m.cx
                val py = m.cy + shovelOffY()
                if (inSand(px, py) && m.carryTravelTotal < W() * 0.12f) tryCastle(px, py, real)
            }
        }
        m.carryTravelTotal = 0f
    }

    fun computeHint(): String {
        val c = m.carry
        if (c == SB_CAN) return "Води лейкой по песку — песок станет мокрым"
        val readyMold = m.molds.firstOrNull { !it.dead && it.phase == 2 }
        if (readyMold != null) return "Готово! Тапни по формочке"
        val full = m.molds.firstOrNull { !it.dead && it.phase == 1 }
        if (full != null) return if (toddler) "Нужна вода! Тапни по лейке" else "Песок сухой — возьми лейку и полей формочку"
        if (m.molds.any { !it.dead && it.phase == 0 }) return "Песок сыплется в формочку…"
        if (m.cakes.any { !it.dead && !it.collapsing && it.real }) return "Тапни по куличику — он рассыплется!"
        return if (toddler) "Тапни по формочке — она встанет на песок" else "Перетащи формочку на песок"
    }

    // ── цикл ──
    fun update(dt: Float) {
        m.clock += dt
        val clock = m.clock

        // авто-действие
        val a = m.auto
        if (a != null) {
            a.t += dt
            val fly = 0.75f
            val off = contactOffset(a.kind)
            val back = 0.45f
            var px: Float
            var py: Float
            if (a.t < fly) {
                val k = sbEase(a.t / fly)
                px = sbLerp(a.sx, a.tx - off.x, k)
                py = sbLerp(a.sy, a.ty - off.y, k)
            } else if (a.t < fly + a.actDur) {
                val u = (a.t - fly) / max(0.01f, a.actDur)
                px = a.tx - off.x
                py = a.ty - off.y
                if (a.kind == SB_CAN) { px += sin(u * 18f) * W() * 0.03f; py += sin(u * 11f) * H() * 0.012f }
                if (a.kind == SB_SHOVEL) px += sin(u * 2f * PI.toFloat() * 1.5f) * W() * 0.28f
            } else {
                val k = sbEase((a.t - fly - a.actDur) / back)
                px = sbLerp(a.tx - off.x, a.sx, k)
                py = sbLerp(a.ty - off.y, a.sy, k)
            }
            if (a.kind <= SB_ROCKET) {
                if (a.t >= fly && !a.placed) {
                    a.placed = true
                    placeMold(a.kind, a.tx, a.ty, a.real)
                    m.carry = -1
                }
                if (!a.placed) { m.carry = a.kind; m.cx = px; m.cy = py; m.carryReal = a.real }
                if (a.t > fly + 0.1f) m.auto = null
            } else {
                m.carry = a.kind; m.cx = px; m.cy = py; m.carryReal = a.real
                m.sandTravel = if (a.kind == SB_SHOVEL) W() * 0.01f else 0f
                if (a.t >= fly + a.actDur + back) {
                    m.auto = null
                    m.carry = -1
                    m.pouring = false
                    m.shoveling = false
                }
            }
            if (!m.demo) { m.lastTouch = clock }
        }

        if (m.carry == SB_CAN || m.carry == SB_SHOVEL) {
            if (m.carry == SB_SHOVEL && m.auto == null) { /* вручную: sandTravel накапливается в onMove */ }
            if (m.auto != null && a != null && a.t < 0.75f) { m.pouring = false } else applyCarry(dt)
        } else {
            m.pouring = false
            m.shoveling = false
        }

        // формочки
        for (mo in m.molds) {
            mo.t += dt
            mo.wob = max(0f, mo.wob - dt * 1.8f)
            when (mo.phase) {
                0 -> {
                    val dur = if (toddler) 0.8f else 1.1f
                    mo.fill = min(1f, mo.t / dur)
                    if (Random.nextFloat() < 0.5f && mo.fill < 1f) {
                        burst(0, mo.x + (Random.nextFloat() - 0.5f) * moldS(), mo.y - moldS() * 1.6f, 1, Color(0xFFEBC26B), 0f, moldS() * 0.1f, life = 0.35f, gy = mo.y - moldS() * 0.2f)
                    }
                    if (mo.fill >= 1f) { mo.phase = 1; mo.t = 0f }
                }
                1 -> {
                    mo.fill = 1f
                    if (moistAt(mo.x, mo.y, W() * 0.095f) >= readyThreshold()) {
                        mo.phase = 2
                        mo.t = 0f
                        burst(2, mo.x, mo.y - moldS() * 0.3f, 8, Color(0xFFFFF3A0), moldS() * 2.5f, moldS() * 0.2f, life = 0.7f)
                        snd("wl_sparkle", 0.8f)
                        if (mo.real) haptics.vibratePop()
                    }
                }
                2 -> {
                    mo.fill = 1f
                    mo.glow = 0.35f + 0.3f * sin(clock * 6f)
                    if (moistAt(mo.x, mo.y, W() * 0.095f) < readyThreshold() * 0.6f) { mo.phase = 1; mo.glow = 0f }
                    if (Random.nextFloat() < 0.04f) burst(2, mo.x + (Random.nextFloat() - 0.5f) * moldS() * 1.6f, mo.y - moldS() * 0.6f, 1, Color(0xFFFFF3A0), moldS() * 0.3f, moldS() * 0.15f, life = 0.6f)
                }
                else -> {
                    mo.liftT += dt / 0.8f
                    if (mo.liftT > 0.4f && !mo.spawned) {
                        mo.spawned = true
                        val c = SbCake(mo.type, mo.x, mo.y, mo.real)
                        m.cakes.add(c)
                        while (m.cakes.count { !it.collapsing && !it.dead } > 4) m.cakes.firstOrNull { !it.collapsing && !it.dead }?.let { collapseCake(it, false) }
                        burst(3, mo.x, mo.y + moldS() * 0.5f, 12, Color(0xFFE3B866), moldS() * 2f, moldS() * 0.2f, up = moldS() * 0.8f, life = 0.55f)
                        burst(2, mo.x, mo.y - moldS() * 0.2f, 8, Color(0xFFFFF3A0), moldS() * 2.8f, moldS() * 0.2f, life = 0.7f)
                        snd("fx_sb_giggle", 0.8f)
                        if (mo.real) onBuilt()
                    }
                    if (mo.liftT >= 1f) mo.dead = true
                }
            }
        }
        m.molds.removeAll { it.dead }

        // куличики
        for (c in m.cakes) {
            c.age += dt
            c.poke = max(0f, c.poke - dt * 2f)
            c.nextBlink -= dt
            if (c.nextBlink <= 0f) { c.blink = 0.18f; c.nextBlink = 2f + Random.nextFloat() * 2.5f }
            c.blink = max(0f, c.blink - dt)
            if (c.collapsing) { c.ct += dt / 0.7f; if (c.ct >= 1f) { c.dead = true; m.heaps.add(SbHeap(c.x, c.y + cakeS() * 0.3f, cakeS() * 0.9f, 1f)); if (m.heaps.size > 20) m.heaps.removeAt(0) } }
        }
        m.cakes.removeAll { it.dead }

        // следы и кучки блекнут
        for (d in m.dents) d.a -= dt * 0.018f
        for (h in m.heaps) h.a -= dt * 0.04f
        m.dents.removeAll { it.a <= 0f }
        m.heaps.removeAll { it.a <= 0f }

        // испарение
        m.moistTimer += dt
        if (m.moistTimer > 0.4f) {
            m.moistTimer = 0f
            var any = false
            for (i in m.moist.indices) if (m.moist[i] > 0f) { m.moist[i] = max(0f, m.moist[i] - 0.4f * 0.006f); any = true }
            if (any) m.moistDirty = true
        }
        if (m.moistDirty || moistImg == null) {
            m.moistDirty = false
            val bw = SB_GW * 2
            val bh = SB_GH * 2
            val px = IntArray(bw * bh)
            for (y in 0 until bh) for (x in 0 until bw) {
                val gx = ((x + 0.5f) / 2f - 0.5f).coerceIn(0f, SB_GW - 1.001f)
                val gy = ((y + 0.5f) / 2f - 0.5f).coerceIn(0f, SB_GH - 1.001f)
                val x0 = gx.toInt(); val y0 = gy.toInt()
                val fx = gx - x0; val fy = gy - y0
                val v00 = m.moist[y0 * SB_GW + x0]; val v10 = m.moist[y0 * SB_GW + x0 + 1]
                val v01 = m.moist[(y0 + 1) * SB_GW + x0]; val v11 = m.moist[(y0 + 1) * SB_GW + x0 + 1]
                val v = (v00 * (1 - fx) + v10 * fx) * (1 - fy) + (v01 * (1 - fx) + v11 * fx) * fy
                val al = (v.coerceIn(0f, 1f) * 0.8f * 255f).toInt()
                val sheen = ((v - 0.75f).coerceIn(0f, 0.25f) * 4f)
                val r = (150 + 40 * sheen).toInt()
                val g = (104 + 34 * sheen).toInt()
                val b = (46 + 22 * sheen).toInt()
                px[y * bw + x] = (al shl 24) or (r shl 16) or (g shl 8) or b
            }
            bmp.setPixels(px, 0, bw, 0, 0, bw, bh)
            moistImg = bmp.asImageBitmap()
        }

        // частицы
        val grav = H() * 1.8f
        val spawnRings = ArrayList<SbPart>()
        for (p in m.parts) {
            p.life -= dt
            if (p.kind == 1) {
                p.vy += H() * 2.2f * dt
                p.y += p.vy * dt
                p.x += p.vx * dt
                if (p.y >= p.gy) {
                    p.life = 0f
                    spawnRings.add(SbPart(4, p.x, p.y, 0f, 0f, 0.35f, 0.35f, W() * 0.02f, Color(0xFFBFE6FF), 0f, 0f, 1e9f))
                }
            } else if (p.kind == 0) {
                p.vy += grav * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
                if (p.y >= p.gy && p.vy > 0f) { p.life = 0f }
            } else {
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.vy += (if (p.kind == 3) grav * 0.5f else 0f) * dt
                p.rot += p.vr * dt
            }
        }
        m.parts.addAll(spawnRings)
        m.parts.removeAll { it.life <= 0f }
        if (m.parts.size > 360) repeat(60) { m.parts.removeAt(0) }

        // демо
        val overlay = showAlbum || rewardSticker != null
        if (m.demo) {
            if (overlay) stopDemo() else {
                m.demoT += dt
                val hk = 1f - 0.0001f.pow(dt)
                m.handX += (m.handTx - m.handX) * hk
                m.handY += (m.handTy - m.handY) * hk
                val demoMold = m.molds.firstOrNull { !it.real && !it.dead }
                val demoCake = m.cakes.firstOrNull { !it.real && !it.collapsing }
                when (m.demoStep) {
                    0 -> if (m.demoT > 0.7f) { startAuto(SB_PELMEN, false); m.demoStep = 1; m.demoT = 0f }
                    1 -> {
                        if (m.auto != null) { m.handTx = m.cx; m.handTy = m.cy; m.handDown = true }
                        else if (m.demoT > 0.6f) { m.handDown = false; startAuto(SB_CAN, false); m.demoStep = 2; m.demoT = 0f }
                    }
                    2 -> {
                        if (m.auto != null) { m.handTx = m.cx; m.handTy = m.cy; m.handDown = true }
                        else { m.handDown = false; m.demoStep = 3; m.demoT = 0f }
                    }
                    3 -> {
                        if (demoMold == null) { m.demoStep = 6; m.demoT = 0f }
                        else {
                            m.handTx = demoMold.x; m.handTy = demoMold.y
                            if (m.demoT > 0.9f) { m.handDown = true; tapMold(demoMold, false); m.demoStep = 4; m.demoT = 0f }
                        }
                    }
                    4 -> {
                        if (m.demoT > 0.3f) m.handDown = false
                        if (m.demoT > 2.0f) m.demoStep = 5
                    }
                    5 -> {
                        if (demoCake == null) { m.demoStep = 6; m.demoT = 0f }
                        else {
                            m.handTx = demoCake.x; m.handTy = demoCake.y
                            if (m.demoT > 1.2f) { m.handDown = true; collapseCake(demoCake, false); m.demoStep = 6; m.demoT = 0f }
                        }
                    }
                    else -> {
                        if (m.demoT > 0.4f) m.handDown = false
                        if (m.demoT > 1.6f) { stopDemo(); m.lastTouch = m.clock }
                    }
                }
            }
        } else if (!overlay && m.auto == null && m.carry == -1 && m.molds.none { it.real && !it.dead } && clock - m.lastTouch > 5f) {
            startDemo()
        }

        // подсказка
        m.hintTimer -= dt
        if (m.hintTimer <= 0f) {
            val h = computeHint()
            if (h != m.hint) { m.hint = h; hintText = h }
        }
        // какой значок пульсирует
        val items = barItems()
        m.pulseSlot = when {
            m.carry != -1 -> -1
            m.molds.any { !it.dead && it.phase == 1 } -> items.indexOf(SB_CAN)
            m.molds.none { !it.dead } && m.cakes.none { !it.dead && !it.collapsing } -> 0
            else -> -1
        }
        frame++
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanosEco { now ->
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
                            if (ch.changedToUp()) { onUp(ch.position.x, ch.position.y, true); ended = true; break }
                            if (ch.positionChanged()) { onMove(ch.position.x, ch.position.y, true); ch.consume() }
                        }
                        if (!ended) onUp(lastPos.x, lastPos.y, true)
                    }
                }
        ) {
            frame.hashCode()
            val w = size.width
            val h = size.height
            if (w <= 0f) return@Canvas
            val t = m.clock
            val sx = sandX(); val sy = sandY(); val sw = sandW(); val sh = sandH()
            val fr = w * 0.03f

            // небо
            drawRect(Brush.verticalGradient(listOf(Color(0xFF7DD3FC), Color(0xFFC7ECFB)), 0f, h * 0.32f), Offset.Zero, Size(w, h * 0.34f))
            // солнце
            val sunC = Offset(w * 0.82f, h * 0.085f)
            drawCircle(Brush.radialGradient(listOf(Color(0x66FFF3A0), Color(0x00FFF3A0)), sunC, w * 0.2f), w * 0.2f, sunC)
            for (i in 0 until 10) {
                val ang = t * 0.4f + i * PI.toFloat() / 5f
                drawLine(Color(0xFFFFD54A), Offset(sunC.x + cos(ang) * w * 0.075f, sunC.y + sin(ang) * w * 0.075f), Offset(sunC.x + cos(ang) * w * 0.1f, sunC.y + sin(ang) * w * 0.1f), w * 0.012f, StrokeCap.Round)
            }
            drawCircle(Color(0xFFFFE066), w * 0.062f, sunC)
            drawCircle(Color(0xFFFFD54A), w * 0.062f, sunC, style = Stroke(w * 0.008f))
            // облака
            for (k in 0 until 2) {
                val cxk = ((t * (8f + k * 5f) + k * w * 0.55f) % (w * 1.4f)) - w * 0.2f
                val cyk = h * (0.1f + 0.06f * k)
                val rr = w * (0.05f + 0.01f * k)
                for (o in listOf(-1.1f, 0f, 1.0f)) drawCircle(Color.White.copy(alpha = 0.92f), rr * (if (o == 0f) 1.2f else 0.85f), Offset(cxk + o * rr, cyk))
                drawRoundRect(Color.White.copy(alpha = 0.92f), Offset(cxk - rr * 1.9f, cyk), Size(rr * 3.8f, rr * 0.8f), CornerRadius(rr * 0.4f))
            }
            // лужайка
            drawRect(Brush.verticalGradient(listOf(Color(0xFF6FD06B), Color(0xFF3FA84A)), h * 0.26f, h), Offset(0f, h * 0.26f), Size(w, h * 0.74f))
            drawOval(Color(0xFF86DC7C), Offset(-w * 0.2f, h * 0.2f), Size(w * 0.8f, h * 0.14f))
            drawOval(Color(0xFF7AD472), Offset(w * 0.35f, h * 0.22f), Size(w * 0.9f, h * 0.13f))
            for (f in SB_FLOWERS) {
                val fx = f[0] * w
                val fy = h * f[1] * 0.58f + h * 0.0f + h * 0.2f
                if (fy > sy - fr && fy < sy + sh + fr * 2f && fx > sx - fr && fx < sx + sw + fr) continue
                if (fy > barY() - 6f) continue
                val col = SB_FLOWER_COLORS[f[2].toInt()]
                for (q in 0 until 5) {
                    val an = q * 2f * PI.toFloat() / 5f
                    drawCircle(col, w * 0.012f, Offset(fx + cos(an) * w * 0.014f, fy + sin(an) * w * 0.014f))
                }
                drawCircle(Color(0xFFFFB300), w * 0.008f, Offset(fx, fy))
            }
            // бабочка
            val bx = w * (0.5f + 0.35f * sin(t * 0.5f))
            val by = h * (0.3f + 0.04f * sin(t * 1.7f))
            val flap = 0.4f + 0.6f * abs(sin(t * 14f))
            drawOval(Color(0xFFFF8FB1), Offset(bx - w * 0.026f, by - w * 0.016f * flap - w * 0.006f), Size(w * 0.026f, w * 0.032f * flap))
            drawOval(Color(0xFFFF8FB1), Offset(bx, by - w * 0.016f * flap - w * 0.006f), Size(w * 0.026f, w * 0.032f * flap))
            drawCircle(Color(0xFF5A3A2A), w * 0.006f, Offset(bx, by))

            // песочница: задний борт
            drawRoundRect(Color(0xFF6E431C), Offset(sx - fr, sy - fr), Size(sw + fr * 2f, sh + fr * 2f), CornerRadius(fr * 0.9f))
            drawRoundRect(Color(0xFF9A6330), Offset(sx - fr * 0.8f, sy - fr * 0.85f), Size(sw + fr * 1.6f, fr * 0.8f), CornerRadius(fr * 0.4f))
            // песок
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF8DC96), Color(0xFFEBC26B)), sy, sy + sh), Offset(sx, sy), Size(sw, sh), CornerRadius(fr * 0.35f))
            for (d in SB_DOTS) {
                drawCircle(
                    if (d[3] > 0.5f) Color(0x22A8782E) else Color(0x33FFF6D0),
                    d[2], Offset(sx + d[0] * sw, sy + d[1] * sh)
                )
            }
            moistImg?.let {
                drawImage(
                    it, dstOffset = IntOffset(sx.toInt(), sy.toInt()), dstSize = IntSize(sw.toInt(), sh.toInt()),
                    filterQuality = FilterQuality.Low
                )
            }
            // вмятины
            for (d in m.dents) {
                drawOval(Color(0xFF8A5E24).copy(alpha = (d.a * 0.35f).coerceIn(0f, 1f)), Offset(d.x - d.r, d.y - d.r * 0.55f), Size(d.r * 2f, d.r * 1.1f))
                drawOval(Color(0xFFFFF1C0).copy(alpha = (d.a * 0.35f).coerceIn(0f, 1f)), Offset(d.x - d.r * 0.8f, d.y - d.r * 0.2f), Size(d.r * 1.6f, d.r * 0.9f), style = Stroke(d.r * 0.12f))
            }
            // кучки
            for (hp in m.heaps) {
                val al = hp.a.coerceIn(0f, 1f)
                drawOval(Color(0xFFB98638).copy(alpha = al), Offset(hp.x - hp.r, hp.y - hp.r * 0.35f), Size(hp.r * 2f, hp.r * 0.8f))
                drawOval(Color(0xFFE3B866).copy(alpha = al), Offset(hp.x - hp.r * 0.8f, hp.y - hp.r * 0.5f), Size(hp.r * 1.6f, hp.r * 0.8f))
            }

            // формочки на песке и куличики (по глубине)
            val moldS = moldS()
            val cakeS = cakeS()
            val drawables = ArrayList<Pair<Float, () -> Unit>>()
            for (mo in m.molds) {
                drawables.add(mo.y to {
                    val lift = if (mo.phase == 3) sbEase(mo.liftT) else 0f
                    val tilt = if (mo.phase == 3) -10f * lift else sin(mo.t * 40f) * mo.wob * 12f
                    val fa = if (mo.phase == 3) (1f - max(0f, (mo.liftT - 0.55f) / 0.45f)) else 1f
                    val pulse = if (mo.phase == 2) 1f + 0.05f * sin(t * 8f) else 1f
                    sbMold(mo.type, mo.x, mo.y - lift * moldS * 2.8f - (if (mo.phase == 2) moldS * 0.06f * abs(sin(t * 6f)) else 0f), moldS * pulse, if (mo.phase == 3) 1f else mo.fill, mo.glow, tilt, fa, mo.phase != 3)
                })
            }
            for (c in m.cakes) {
                drawables.add(c.y to {
                    val grow = sbEase(c.age / 0.45f)
                    val overshoot = 1f + 0.12f * sin(min(1f, c.age / 0.6f) * PI.toFloat()) * (1f - min(1f, c.age / 0.6f))
                    var sxx = (0.3f + 0.7f * grow) * overshoot + 0.12f * sin(c.poke * 10f) * c.poke
                    var syy = (0.2f + 0.8f * grow) * overshoot - 0.1f * sin(c.poke * 10f) * c.poke
                    var al = 1f
                    if (c.collapsing) {
                        val k = c.ct.coerceIn(0f, 1f)
                        syy *= (1f - 0.9f * k * k)
                        sxx *= (1f + 0.25f * k)
                        al = (1f - k * k).coerceIn(0f, 1f)
                    }
                    sbCake(c.type, c.x, c.y, cakeS, sxx, syy, if (c.blink > 0f) 1f else 0f, 1f, al)
                })
            }
            drawables.sortBy { it.first }
            for (d in drawables) d.second()

            // частицы
            for (p in m.parts) {
                val k = (p.life / p.maxLife).coerceIn(0f, 1f)
                when (p.kind) {
                    0 -> drawCircle(p.color.copy(alpha = min(1f, k * 2f)), p.size, Offset(p.x, p.y))
                    1 -> {
                        drawOval(p.color.copy(alpha = 0.9f), Offset(p.x - p.size * 0.5f, p.y - p.size), Size(p.size, p.size * 2.2f))
                    }
                    2 -> sbSparkle(Offset(p.x, p.y), p.size * (0.4f + 0.6f * k), p.color.copy(alpha = k), p.rot)
                    3 -> drawCircle(p.color.copy(alpha = k * 0.6f), p.size * (1.5f - k * 0.5f), Offset(p.x, p.y))
                    else -> drawCircle(p.color.copy(alpha = k * 0.8f), p.size * (2f - k), Offset(p.x, p.y), style = Stroke(w * 0.003f))
                }
            }

            // передний борт
            drawRoundRect(Color(0xFF6E431C), Offset(sx - fr, sy + sh - fr * 0.3f), Size(sw + fr * 2f, fr * 1.55f), CornerRadius(fr * 0.5f))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFC98945), Color(0xFF9A6330)), sy + sh - fr * 0.2f, sy + sh + fr * 1.1f), Offset(sx - fr * 0.9f, sy + sh - fr * 0.2f), Size(sw + fr * 1.8f, fr * 1.3f), CornerRadius(fr * 0.45f))
            drawLine(Color(0x55FFFFFF), Offset(sx - fr * 0.6f, sy + sh - fr * 0.05f), Offset(sx + sw + fr * 0.6f, sy + sh - fr * 0.05f), fr * 0.1f, StrokeCap.Round)
            for (ix in listOf(sx - fr * 0.5f, sx + sw + fr * 0.5f)) {
                drawCircle(Color(0xFF6E431C), fr * 0.12f, Offset(ix, sy + sh + fr * 0.45f))
            }

            // нижняя полка с инструментами
            val by0 = barY()
            drawRect(Brush.verticalGradient(listOf(Color(0xFFD59A57), Color(0xFFA9692C)), by0, h), Offset(0f, by0), Size(w, h - by0))
            drawRect(Color(0xFFEBC08A), Offset(0f, by0), Size(w, h * 0.008f))
            drawLine(Color(0x33000000), Offset(0f, by0 + (h - by0) * 0.5f), Offset(w, by0 + (h - by0) * 0.5f), 2f)
            val items = barItems()
            val n = items.size
            val ss = slotSize(n)
            for (i in 0 until n) {
                val c = slotCenter(i, n)
                val kind = items[i]
                val pul = if (i == m.pulseSlot) 1f + 0.1f * sin(t * 7f) else 1f
                drawCircle(Color(0x33FFFFFF), ss * 1.12f * pul, c)
                if (i == m.pulseSlot) drawCircle(Color(0xAAFFF3A0), ss * 1.2f * pul, c, style = Stroke(w * 0.007f))
                val al = if (m.carry == kind && m.carryFromBar) 0.3f else 1f
                when {
                    kind <= SB_ROCKET -> sbMold(kind, c.x, c.y, ss * 0.8f * pul, 0f, 0f, 0f, al, false)
                    kind == SB_CAN -> if (al > 0.5f) sbCan(c.x - ss * 0.1f, c.y, ss * 0.62f * pul, 0f)
                    else -> if (al > 0.5f) sbShovel(c.x, c.y - ss * 0.1f, ss * 0.5f * pul)
                }
            }

            // инструмент в руке
            if (m.carry != -1) {
                val k = m.carry
                when {
                    k <= SB_ROCKET -> sbMold(k, m.cx, m.cy, moldS * 1.05f, 0f, 0f, sin(t * 6f) * 4f, 1f, false)
                    k == SB_CAN -> sbCan(m.cx, m.cy, w * 0.07f, if (m.pouring || (m.auto != null && m.auto?.kind == SB_CAN)) -22f else 0f)
                    else -> sbShovel(m.cx, m.cy, w * 0.07f)
                }
            }

            // рука-подсказка
            if (m.demo) sbHand(Offset(m.handX, m.handY), min(w, h) * 0.04f, m.handDown)
        }

        // подсказка
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 12.dp, start = 12.dp, end = 84.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xE6FFFFFF))
                .border(2.dp, Color(0xFFE3B866), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(hintText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7A4A1C), textAlign = TextAlign.Center)
        }
        if (demoOn) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 80.dp, start = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE6C98945))
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
                .background(Brush.verticalGradient(listOf(Color(0xFFFFD98A), Color(0xFFE3A23C))))
                .border(3.dp, Color.White, CircleShape)
                .clickable { stopDemo(); m.lastTouch = m.clock; showAlbum = true; snd("wl_pin", 0.8f) },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(40.dp)) { sbSticker(0, Offset(size.width / 2, size.height / 2), size.width * 0.42f) }
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
            Text("Куличики:", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF7A4A1C))
            Text(" $built", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF7A4A1C))
        }

        // альбом «Master Builder»
        if (showAlbum) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x99402810))
                    .clickable { showAlbum = false; m.lastTouch = m.clock },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFFFF6E0))
                        .border(5.dp, Color(0xFFE3A23C), RoundedCornerShape(28.dp))
                        .clickable(enabled = false) {}
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Альбом «Master Builder»", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF7A4A1C))
                    Row {
                        Text("Куличиков:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8E5A2B))
                        Text(" $built", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8E5A2B))
                    }
                    Spacer(Modifier.height(10.dp))
                    SB_STICKERS.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { st ->
                                val unlocked = built >= st.need
                                Column(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .padding(vertical = 5.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (unlocked) Color(0xFFFFE7B0) else Color(0xFFEDE6D8))
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Canvas(Modifier.size(80.dp)) {
                                        if (unlocked) sbSticker(st.id, Offset(size.width / 2, size.height / 2), size.width * 0.4f)
                                        else {
                                            drawCircle(Color(0x22000000), size.width * 0.38f, Offset(size.width / 2, size.height / 2))
                                            drawRoundRect(Color(0xFF9A8A74), Offset(size.width * 0.36f, size.height * 0.46f), Size(size.width * 0.28f, size.height * 0.22f), CornerRadius(6f))
                                            drawArc(Color(0xFF9A8A74), 180f, 180f, false, Offset(size.width * 0.4f, size.height * 0.3f), Size(size.width * 0.2f, size.height * 0.2f), style = Stroke(5f))
                                        }
                                    }
                                    Text(
                                        if (unlocked) st.name else st.desc,
                                        fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7A4A1C),
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
                    .background(Color(0xAA402810))
                    .clickable { rewardSticker = null; rewardMold = null; m.lastTouch = m.clock },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFFFF6E0))
                        .border(5.dp, Color(0xFFE3A23C), RoundedCornerShape(28.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Новая наклейка!", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFFC9731A))
                    Canvas(Modifier.size(150.dp)) { sbSticker(rs.id, Offset(size.width / 2, size.height / 2), size.width * 0.42f) }
                    Text(rs.name, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF7A4A1C))
                    val rm = rewardMold
                    if (rm != null) {
                        Spacer(Modifier.height(6.dp))
                        Text("Открыта новая формочка:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8E5A2B))
                        Text(sbMoldName(rm), fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFC9731A))
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFE3A23C))
                            .padding(horizontal = 28.dp, vertical = 8.dp)
                    ) { Text("Ура!", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White) }
                }
            }
        }
    }
}
