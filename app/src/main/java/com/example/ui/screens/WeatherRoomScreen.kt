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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Комната «Погодная комната» (облачко-дождик 2.0).
// Живая лужайка с домиком и деревом. Снизу четыре стихии: солнце, дождь, снег, ветер; сверху — день/ночь и альбом.
// Солнце подмигивает и пускает зайчика, дождь разбивается о палец и наполняет лужи, снег сгребается в сугробы,
// ветер качает дерево и кружит листья. Пельмешек реагирует: зонтик, шарфик, жмурится, носит шапочки-награды.
// Награды: 6 наклеек альбома «Повелитель погоды»; каждая даёт пельмешку новую шапочку.

private const val WR_PREFS = "weather_prefs"

private const val W_SUN = 0
private const val W_RAIN = 1
private const val W_SNOW = 2
private const val W_WIND = 3

private const val WP_RAIN = 0
private const val WP_SNOW = 1
private const val WP_LEAF = 2
private const val WP_SPARK = 3
private const val WP_DROP = 4
private const val WP_SMOKE = 5
private const val WP_ZZZ = 6

private const val WR_NPUD = 4
private const val WR_NCLOUD = 7
private const val WR_NSTAR = 36

private class WrSticker(val id: Int, val name: String, val note: String, val how: String)

private val WR_STICKERS = listOf(
    WrSticker(0, "Солнечный зайчик", "Соломенная шляпка с цветочком", "Поймай солнечного зайчика"),
    WrSticker(1, "Лужа-хлюп", "Зонтик в горошек", "Разбей капельки пальцем и топни в лужах"),
    WrSticker(2, "Снежная горка", "Шапка с помпоном", "Сгреби снег в сугробы"),
    WrSticker(3, "Ветерок-проказник", "Кепка с пропеллером", "Покачай дерево и закружи листья"),
    WrSticker(4, "Звёздная ночь", "Колпачок со звёздочкой", "Ночью потрогай звёздочки"),
    WrSticker(5, "Повелитель погоды", "Золотая корона", "Собери все наклейки")
)
private val WR_NEED = intArrayOf(3, 40, 6, 7, 4)
private val WR_NEED_T = intArrayOf(2, 18, 4, 4, 3)

private class WrP(var kind: Int, var x: Float, var y: Float, var vx: Float, var vy: Float, var s: Float, var life: Float) {
    var maxLife = life
    var rot = 0f
    var spin = 0f
    var ph = 0f
    var gy = 0f
    var col = 0
    var st = 0
}

private class WrRip(var x: Float, var y: Float, var r: Float, var max: Float, var a: Float)
private class WrMound(var x: Float, var y: Float, var rad: Float, var h: Float) { var sq = 0f }
private class WrPt(val x: Float, val y: Float, var age: Float, val brk: Boolean)

private class WrModel {
    var clock = 0f
    var weather = W_SUN
    var prev = W_SUN
    var skyBlend = 1f
    var night = 0f
    var nightTarget = 0f
    var wind = 0f
    var gust = 0f
    var cloudAmt = 0.35f
    var wetness = 0f
    var snowA = 0f
    var rainbow = 0f
    var rbForce = 0f

    val cx = floatArrayOf(0.10f, 0.34f, 0.58f, 0.80f, 0.22f, 0.68f, 0.46f)
    val cy = floatArrayOf(0.10f, 0.15f, 0.09f, 0.16f, 0.22f, 0.24f, 0.05f)
    val cs = floatArrayOf(0.12f, 0.10f, 0.13f, 0.11f, 0.09f, 0.095f, 0.10f)
    val cmin = floatArrayOf(0.0f, 0.2f, 0.3f, 0.5f, 0.55f, 0.8f, 0.85f)
    val csq = FloatArray(WR_NCLOUD)

    var sunWink = 0f
    var moonT = 0f

    var bAlive = false
    var bx = 0f
    var by = 0f
    var bvx = 0f
    var bvy = 0f
    var bTx = 0f
    var bTy = 0f
    var bT = 1.2f
    var bRetarget = 0f
    var bPop = 0f
    val trail = FloatArray(24)

    val stX = FloatArray(WR_NSTAR)
    val stY = FloatArray(WR_NSTAR)
    val stP = FloatArray(WR_NSTAR)
    val stF = FloatArray(WR_NSTAR)
    var shT = 0f
    var shX = 0f
    var shY = 0f
    var shNext = 5f

    var ang = 0f
    var angV = 0f

    var hop = 0f
    var blink = 0f
    var nextBlink = 2f
    var emoteT = 0f
    var giggle = 0f
    var umb = 0f
    var lookX = 0f
    var lookY = 0f
    var zzzT = 0f
    var sneezeShake = 0f

    var fx = 0f
    var fy = 0f
    var fPx = 0f
    var fPy = 0f
    var fDown = false
    var stroke = 0f
    var plowed = false
    var plowM: WrMound? = null
    var lastCrunch = 0f
    var lastDrop = 0f
    var lastWhoosh = 0f
    var lastTrail = 0f
    var real = false

    val parts = ArrayList<WrP>()
    val rips = ArrayList<WrRip>()
    val mounds = ArrayList<WrMound>()
    val lanes = ArrayList<WrPt>()
    val pud = FloatArray(WR_NPUD)

    var accRain = 0f
    var accSnow = 0f
    var accLeaf = 0f
    var accSmoke = 0f
    var accSpark = 0f
    var birdT = 3f
    var hintT = 0f

    val c = IntArray(5)
    var mask = 0
    var switches = 0
    var visited = 0

    var demo = false
    var dStep = 0
    var dT = 0f
    var dDone = false
    var dTapped = false
    var hx = 0f
    var hy = 0f
    var hDown = 0f
    var lastTouch = 0f
    val pending = ArrayList<Int>()
}

private fun Color.wrA(a: Float) = copy(alpha = a.coerceIn(0f, 1f))
private fun wrLerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
private fun wrSm(t: Float): Float { val u = t.coerceIn(0f, 1f); return u * u * (3f - 2f * u) }

private fun wrSkyTop(w: Int) = when (w) { W_SUN -> Color(0xFF4FB4F2); W_RAIN -> Color(0xFF5B6B88); W_SNOW -> Color(0xFF8FA2BC); else -> Color(0xFF63C6C0) }
private fun wrSkyBot(w: Int) = when (w) { W_SUN -> Color(0xFFD2EEFF); W_RAIN -> Color(0xFFA3B1C6); W_SNOW -> Color(0xFFE6EDF6); else -> Color(0xFFDDF6E6) }
private fun wrCloudCol(w: Int) = when (w) { W_SUN -> Color(0xFFFFFFFF); W_RAIN -> Color(0xFF7D8CA8); W_SNOW -> Color(0xFFDCE3EE); else -> Color(0xFFF4FBFF) }
private fun wrCloudShade(w: Int) = when (w) { W_SUN -> Color(0xFFD8E8F6); W_RAIN -> Color(0xFF5E6C88); W_SNOW -> Color(0xFFB9C5D8); else -> Color(0xFFCBE3EC) }

// cx,cy,r триплеты кружков облака в единицах s
private val WR_CLOUD = floatArrayOf(
    -1.00f, 0.18f, 0.46f, -0.50f, 0.28f, 0.52f, 0.05f, 0.30f, 0.54f, 0.60f, 0.24f, 0.50f, 1.02f, 0.16f, 0.42f,
    -0.62f, -0.12f, 0.50f, 0.00f, -0.30f, 0.62f, 0.58f, -0.08f, 0.50f
)

private fun DrawScope.wrCloud(cx: Float, cy: Float, s: Float, col: Color, shade: Color, squish: Float, t: Float, alpha: Float, face: Int) {
    if (alpha <= 0.01f) return
    val sx = 1f + 0.10f * squish * sin(t * 20f)
    val sy = 1f - 0.16f * squish
    val bob = sin(t * 1.3f + cx * 0.01f) * s * 0.04f
    withTransform({ translate(cx, cy + bob); scale(sx, sy, Offset.Zero) }) {
        var i = 0
        while (i < WR_CLOUD.size) {
            drawCircle(shade.wrA(alpha), WR_CLOUD[i + 2] * s, Offset(WR_CLOUD[i] * s, WR_CLOUD[i + 1] * s + s * 0.09f))
            i += 3
        }
        i = 0
        while (i < WR_CLOUD.size) {
            drawCircle(col.wrA(alpha), WR_CLOUD[i + 2] * s, Offset(WR_CLOUD[i] * s, WR_CLOUD[i + 1] * s))
            i += 3
        }
        if (face > 0) {
            val ec = Color(0xFF4A5670).wrA(alpha)
            val ey = s * 0.28f
            val st = Stroke(s * 0.055f, cap = StrokeCap.Round)
            if (face == 1) { // сонный
                drawArc(ec, 0f, 180f, false, Offset(-0.38f * s, ey - s * 0.05f), Size(0.24f * s, 0.14f * s), style = st)
                drawArc(ec, 0f, 180f, false, Offset(0.14f * s, ey - s * 0.05f), Size(0.24f * s, 0.14f * s), style = st)
            } else { // весёлый
                drawArc(ec, 180f, 180f, false, Offset(-0.38f * s, ey - s * 0.05f), Size(0.24f * s, 0.16f * s), style = st)
                drawArc(ec, 180f, 180f, false, Offset(0.14f * s, ey - s * 0.05f), Size(0.24f * s, 0.16f * s), style = st)
            }
            drawArc(ec, 0f, 180f, false, Offset(-0.12f * s, ey + s * 0.1f), Size(0.24f * s, 0.16f * s), style = st)
            drawOval(Color(0xFFFF8FA8).wrA(alpha * 0.45f), Offset(-0.55f * s, ey + s * 0.06f), Size(0.2f * s, 0.12f * s))
            drawOval(Color(0xFFFF8FA8).wrA(alpha * 0.45f), Offset(0.35f * s, ey + s * 0.06f), Size(0.2f * s, 0.12f * s))
        }
    }
}

private fun DrawScope.wrSun(c: Offset, r: Float, t: Float, alpha: Float, wink: Float) {
    if (alpha <= 0.01f) return
    drawCircle(Brush.radialGradient(listOf(Color(0x88FFF3A0).wrA(0.5f * alpha), Color.Transparent), c, r * 2.8f), r * 2.8f, c)
    for (i in 0 until 12) {
        val a = t * 0.35f + i * (2f * PI.toFloat() / 12f)
        val len = r * (0.32f + 0.08f * sin(t * 3f + i))
        val r0 = r * 1.18f
        drawLine(Color(0xFFFFC93C).wrA(alpha), Offset(c.x + cos(a) * r0, c.y + sin(a) * r0), Offset(c.x + cos(a) * (r0 + len), c.y + sin(a) * (r0 + len)), r * 0.16f, StrokeCap.Round)
    }
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFEE7A).wrA(alpha), Color(0xFFFFB400).wrA(alpha)), c, r), r, c)
    drawOval(Color(0xFFFF7A7A).wrA(0.35f * alpha), Offset(c.x - r * 0.78f, c.y + r * 0.1f), Size(r * 0.34f, r * 0.2f))
    drawOval(Color(0xFFFF7A7A).wrA(0.35f * alpha), Offset(c.x + r * 0.44f, c.y + r * 0.1f), Size(r * 0.34f, r * 0.2f))
    val lift = wrSm(wink) * r * 0.55f
    val gy = c.y - r * 0.18f - lift
    if (wink > 0.05f) {
        val dk = Color(0xFF3B2A12).wrA(alpha)
        drawArc(dk, 200f, 140f, false, Offset(c.x - r * 0.52f, c.y - r * 0.26f), Size(r * 0.34f, r * 0.26f), style = Stroke(r * 0.07f, cap = StrokeCap.Round))
        drawCircle(dk, r * 0.09f, Offset(c.x + r * 0.35f, c.y - r * 0.12f))
    }
    val gl = Color(0xFF2B2F4A).wrA(alpha)
    drawRoundRect(gl, Offset(c.x - r * 0.72f, gy - r * 0.16f), Size(r * 0.62f, r * 0.42f), CornerRadius(r * 0.18f))
    drawRoundRect(gl, Offset(c.x + r * 0.10f, gy - r * 0.16f), Size(r * 0.62f, r * 0.42f), CornerRadius(r * 0.18f))
    drawLine(gl, Offset(c.x - r * 0.1f, gy), Offset(c.x + r * 0.1f, gy), r * 0.06f)
    drawLine(Color.White.wrA(0.5f * alpha), Offset(c.x - r * 0.6f, gy - r * 0.05f), Offset(c.x - r * 0.4f, gy - r * 0.05f), r * 0.05f, StrokeCap.Round)
    drawLine(Color.White.wrA(0.5f * alpha), Offset(c.x + r * 0.22f, gy - r * 0.05f), Offset(c.x + r * 0.42f, gy - r * 0.05f), r * 0.05f, StrokeCap.Round)
    val mw = r * (0.46f + 0.2f * wink)
    drawArc(Color(0xFFB5381E).wrA(alpha), 10f, 160f, wink > 0.3f, Offset(c.x - mw / 2, c.y + r * 0.22f), Size(mw, r * (0.32f + 0.2f * wink)), style = if (wink > 0.3f) androidx.compose.ui.graphics.drawscope.Fill else Stroke(r * 0.07f, cap = StrokeCap.Round))
}

private fun DrawScope.wrMoon(c: Offset, r: Float, t: Float, alpha: Float, blink: Float) {
    if (alpha <= 0.01f) return
    drawCircle(Brush.radialGradient(listOf(Color(0x66FFF6C0).wrA(0.5f * alpha), Color.Transparent), c, r * 2.4f), r * 2.4f, c)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF8D0).wrA(alpha), Color(0xFFF2DE92).wrA(alpha)), c, r), r, c)
    drawCircle(Color(0xFFE6CF7C).wrA(0.55f * alpha), r * 0.16f, Offset(c.x - r * 0.45f, c.y - r * 0.5f))
    drawCircle(Color(0xFFE6CF7C).wrA(0.55f * alpha), r * 0.11f, Offset(c.x + r * 0.5f, c.y + r * 0.5f))
    val ec = Color(0xFF6B5A2A).wrA(alpha)
    val st = Stroke(r * 0.07f, cap = StrokeCap.Round)
    if (blink > 0.1f) {
        drawArc(ec, 0f, 180f, false, Offset(c.x - r * 0.5f, c.y - r * 0.18f), Size(r * 0.3f, r * 0.2f), style = st)
        drawCircle(ec, r * 0.09f, Offset(c.x + r * 0.35f, c.y - r * 0.05f))
    } else {
        drawArc(ec, 0f, 180f, false, Offset(c.x - r * 0.5f, c.y - r * 0.18f), Size(r * 0.3f, r * 0.2f), style = st)
        drawArc(ec, 0f, 180f, false, Offset(c.x + r * 0.2f, c.y - r * 0.18f), Size(r * 0.3f, r * 0.2f), style = st)
    }
    drawArc(ec, 20f, 140f, false, Offset(c.x - r * 0.2f, c.y + r * 0.1f), Size(r * 0.4f, r * 0.26f), style = st)
    drawOval(Color(0xFFFF9A8A).wrA(0.35f * alpha), Offset(c.x - r * 0.72f, c.y + r * 0.06f), Size(r * 0.3f, r * 0.18f))
    drawOval(Color(0xFFFF9A8A).wrA(0.35f * alpha), Offset(c.x + r * 0.42f, c.y + r * 0.06f), Size(r * 0.3f, r * 0.18f))
}

private fun DrawScope.wrSpark4(x: Float, y: Float, r: Float, col: Color) {
    val p = Path().apply {
        moveTo(x, y - r); quadraticTo(x + r * 0.12f, y - r * 0.12f, x + r, y)
        quadraticTo(x + r * 0.12f, y + r * 0.12f, x, y + r); quadraticTo(x - r * 0.12f, y + r * 0.12f, x - r, y)
        quadraticTo(x - r * 0.12f, y - r * 0.12f, x, y - r); close()
    }
    drawPath(p, col)
}

private fun DrawScope.wrFlake(x: Float, y: Float, r: Float, rot: Float, col: Color) {
    if (r < 3.2f) { drawCircle(col, r, Offset(x, y)); return }
    for (k in 0 until 3) {
        val a = rot + k * PI.toFloat() / 3f
        val dx = cos(a) * r; val dy = sin(a) * r
        drawLine(col, Offset(x - dx, y - dy), Offset(x + dx, y + dy), r * 0.22f, StrokeCap.Round)
        val tx = cos(a + 0.9f) * r * 0.32f; val ty = sin(a + 0.9f) * r * 0.32f
        val ux = cos(a + 2.24f) * r * 0.32f; val uy = sin(a + 2.24f) * r * 0.32f
        drawLine(col, Offset(x + dx * 0.62f, y + dy * 0.62f), Offset(x + dx * 0.62f + tx, y + dy * 0.62f + ty), r * 0.16f, StrokeCap.Round)
        drawLine(col, Offset(x + dx * 0.62f, y + dy * 0.62f), Offset(x + dx * 0.62f + ux, y + dy * 0.62f + uy), r * 0.16f, StrokeCap.Round)
        drawLine(col, Offset(x - dx * 0.62f, y - dy * 0.62f), Offset(x - dx * 0.62f - tx, y - dy * 0.62f - ty), r * 0.16f, StrokeCap.Round)
        drawLine(col, Offset(x - dx * 0.62f, y - dy * 0.62f), Offset(x - dx * 0.62f - ux, y - dy * 0.62f - uy), r * 0.16f, StrokeCap.Round)
    }
}

private fun DrawScope.wrLeaf(x: Float, y: Float, s: Float, rot: Float, col: Color, alpha: Float) {
    withTransform({ rotate(rot * 57.3f, Offset(x, y)) }) {
        val p = Path().apply {
            moveTo(x - s, y); quadraticTo(x, y - s * 0.85f, x + s, y); quadraticTo(x, y + s * 0.85f, x - s, y); close()
        }
        drawPath(p, col.wrA(alpha))
        drawLine(Color.Black.wrA(0.22f * alpha), Offset(x - s, y), Offset(x + s * 0.8f, y), s * 0.09f, StrokeCap.Round)
    }
}

private fun DrawScope.wrHouse(x: Float, g: Float, w: Float, snow: Float, wet: Float, night: Float, t: Float) {
    val hb = w * 0.62f
    val rh = w * 0.50f
    // стена
    drawRoundRect(lerp(Color(0xFFFFE9CC), Color(0xFFD9C3A6), wet * 0.4f), Offset(x, g - hb), Size(w, hb), CornerRadius(w * 0.04f))
    drawRect(Color(0x14000000), Offset(x, g - hb * 0.12f), Size(w, hb * 0.12f))
    // труба
    val chx = x + w * 0.70f
    drawRect(Color(0xFFB4533F), Offset(chx, g - hb - rh * 0.78f), Size(w * 0.13f, rh * 0.7f))
    drawRect(Color(0xFF8E3F30), Offset(chx - w * 0.015f, g - hb - rh * 0.8f), Size(w * 0.16f, w * 0.04f))
    // крыша
    val roof = Path().apply {
        moveTo(x - w * 0.09f, g - hb + w * 0.02f); lineTo(x + w * 0.5f, g - hb - rh); lineTo(x + w * 1.09f, g - hb + w * 0.02f); close()
    }
    drawPath(roof, lerp(Color(0xFFE5566D), Color(0xFFB5465C), wet * 0.4f))
    drawPath(roof, Color(0x22000000), style = Stroke(w * 0.01f, join = StrokeJoin.Round))
    if (snow > 0.05f) {
        val k = 0.2f + 0.8f * min(1f, snow)
        val ax = x + w * 0.5f; val ay = g - hb - rh
        val lx = ax + (x - w * 0.09f - ax) * k; val ly = ay + (g - hb + w * 0.02f - ay) * k
        val rx = ax + (x + w * 1.09f - ax) * k; val ry = ly
        val sp = Path().apply { moveTo(ax, ay - w * 0.012f); lineTo(rx + w * 0.01f, ry); lineTo(lx - w * 0.01f, ly); close() }
        drawPath(sp, Color.White.wrA(min(1f, snow * 1.6f)))
        val n = 6
        for (i in 0..n) drawCircle(Color.White.wrA(min(1f, snow * 1.6f)), w * 0.028f, Offset(lx + (rx - lx) * i / n, ly))
    }
    // дверь
    drawRoundRect(Color(0xFF8E5628), Offset(x + w * 0.16f, g - hb * 0.62f), Size(w * 0.2f, hb * 0.62f), CornerRadius(w * 0.1f, w * 0.1f))
    drawCircle(Color(0xFFFFD43B), w * 0.014f, Offset(x + w * 0.32f, g - hb * 0.32f))
    // окно
    val wx = x + w * 0.56f; val wy = g - hb * 0.75f; val ws = w * 0.28f
    drawRoundRect(Color(0xFFB5733C), Offset(wx - w * 0.02f, wy - w * 0.02f), Size(ws + w * 0.04f, ws + w * 0.04f), CornerRadius(w * 0.03f))
    drawRoundRect(lerp(Color(0xFFAEE0FF), Color(0xFFFFD66B), night), Offset(wx, wy), Size(ws, ws), CornerRadius(w * 0.02f))
    drawLine(Color(0xFFB5733C), Offset(wx + ws / 2, wy), Offset(wx + ws / 2, wy + ws), w * 0.012f)
    drawLine(Color(0xFFB5733C), Offset(wx, wy + ws / 2), Offset(wx + ws, wy + ws / 2), w * 0.012f)
    if (night > 0.05f) drawCircle(Brush.radialGradient(listOf(Color(0x66FFD66B).wrA(0.6f * night), Color.Transparent), Offset(wx + ws / 2, wy + ws / 2), ws * 1.6f), ws * 1.6f, Offset(wx + ws / 2, wy + ws / 2))
}

private fun DrawScope.wrTree(x: Float, g: Float, s: Float, ang: Float, snow: Float, wet: Float, night: Float) {
    withTransform({ rotate(ang * 57.3f, Offset(x, g)) }) {
        val tr = Path().apply {
            moveTo(x - s * 0.13f, g); quadraticTo(x - s * 0.07f, g - s * 0.7f, x - s * 0.05f, g - s * 1.15f)
            lineTo(x + s * 0.05f, g - s * 1.15f); quadraticTo(x + s * 0.07f, g - s * 0.7f, x + s * 0.13f, g); close()
        }
        drawPath(tr, Color(0xFF8A5A2B))
        drawLine(Color(0xFF6F451F), Offset(x - s * 0.03f, g - s * 0.2f), Offset(x - s * 0.02f, g - s * 0.7f), s * 0.025f, StrokeCap.Round)
        val gc = lerp(lerp(Color(0xFF58BE4C), Color(0xFF3E9A4E), wet), Color(0xFF2E5A57), night * 0.4f)
        val gd = lerp(gc, Color.Black, 0.18f)
        val cy = g - s * 1.5f
        val bl = floatArrayOf(-0.62f, 0.12f, 0.62f, 0.62f, 0.12f, 0.62f, 0f, -0.34f, 0.74f, -0.72f, -0.18f, 0.5f, 0.74f, -0.2f, 0.5f)
        var i = 0
        while (i < bl.size) { drawCircle(gd, bl[i + 2] * s, Offset(x + bl[i] * s, cy + bl[i + 1] * s + s * 0.07f)); i += 3 }
        i = 0
        while (i < bl.size) { drawCircle(gc, bl[i + 2] * s, Offset(x + bl[i] * s, cy + bl[i + 1] * s)); i += 3 }
        drawCircle(Color.White.wrA(0.13f), s * 0.3f, Offset(x - s * 0.2f, cy - s * 0.42f))
        if (snow > 0.05f) {
            val sa = min(1f, snow * 1.5f)
            drawCircle(Color.White.wrA(sa), s * 0.5f * min(1f, snow + 0.3f), Offset(x, cy - s * 0.58f))
            drawCircle(Color.White.wrA(sa), s * 0.34f * min(1f, snow + 0.3f), Offset(x - s * 0.62f, cy - s * 0.12f))
            drawCircle(Color.White.wrA(sa), s * 0.34f * min(1f, snow + 0.3f), Offset(x + s * 0.7f, cy - s * 0.2f))
        }
    }
}

// ───────────────────────── пельмешек ─────────────────────────
// cx,cy — центр тела, r — масштаб. mood: 0 обычный, 1 жмурится от счастья, 2 сонный. mask — награды (шапочки).
private fun DrawScope.wrPelmen(
    cx: Float, cy: Float, r: Float, t: Float, weather: Int, night: Float, mask: Int, umb: Float,
    hop: Float, blink: Float, giggle: Float, lean: Float, lookX: Float, lookY: Float, shiver: Float, windV: Float
) {
    val sleepy = night > 0.6f
    val mood = if (sleepy) 2 else if (weather == W_SUN) 1 else 0
    val sh = sin(t * 38f) * r * 0.012f * shiver
    withTransform({ translate(sh, 0f); rotate(lean * 57.3f, Offset(cx, cy + r * 0.8f)) }) {
        // тень
        drawOval(Color(0x33000000), Offset(cx - r * 1.0f, cy + r * 0.86f), Size(r * 2.0f, r * 0.26f))
        // ножки
        val fw = sin(t * 9f) * giggle * r * 0.06f
        drawOval(Color(0xFFE9C58A), Offset(cx - r * 0.62f + fw, cy + r * 0.68f), Size(r * 0.46f, r * 0.26f))
        drawOval(Color(0xFFE9C58A), Offset(cx + r * 0.16f - fw, cy + r * 0.68f), Size(r * 0.46f, r * 0.26f))
        // зонтик (под телом по слоям: ручка и купол позже)
        // тело
        val body = Path().apply {
            moveTo(cx - r * 1.05f, cy + r * 0.55f)
            cubicTo(cx - r * 1.15f, cy - r * 0.55f, cx - r * 0.55f, cy - r * 0.95f, cx, cy - r * 0.95f)
            cubicTo(cx + r * 0.55f, cy - r * 0.95f, cx + r * 1.15f, cy - r * 0.55f, cx + r * 1.05f, cy + r * 0.55f)
            cubicTo(cx + r * 0.7f, cy + r * 0.82f, cx - r * 0.7f, cy + r * 0.82f, cx - r * 1.05f, cy + r * 0.55f)
            close()
        }
        drawPath(body, Brush.verticalGradient(listOf(Color(0xFFFFF6DC), Color(0xFFFFE6B0)), cy - r, cy + r * 0.85f))
        drawPath(body, Color(0xFFE2B979), style = Stroke(r * 0.05f, join = StrokeJoin.Round))
        // защипы
        for (i in 0..6) {
            val a = PI.toFloat() * (1.14f + 0.72f * i / 6f)
            val x0 = cx + cos(a) * r * 1.0f; val y0 = cy - r * 0.05f + sin(a) * r * 0.9f
            val x1 = cx + cos(a) * r * 0.84f; val y1 = cy - r * 0.05f + sin(a) * r * 0.74f
            drawLine(Color(0xFFE2B979), Offset(x0, y0), Offset(x1, y1), r * 0.05f, StrokeCap.Round)
        }
        // ручки
        val wave = sin(t * 12f) * hop * r * 0.25f
        drawOval(Color(0xFFF3D69B), Offset(cx - r * 1.2f, cy + r * 0.1f - wave), Size(r * 0.34f, r * 0.26f))
        drawOval(Color(0xFFF3D69B), Offset(cx + r * 0.86f, cy + r * 0.1f + wave), Size(r * 0.34f, r * 0.26f))
        // лицо
        val ex = r * 0.38f
        val ey = cy + r * 0.0f
        val lx = lookX * r * 0.05f; val ly = lookY * r * 0.04f
        val dk = Color(0xFF3B2A12)
        val st = Stroke(r * 0.06f, cap = StrokeCap.Round)
        if (mood == 1 || mood == 2 || blink > 0.05f) {
            if (mood == 2) {
                drawArc(dk, 0f, 180f, false, Offset(cx - ex - r * 0.13f, ey - r * 0.06f), Size(r * 0.26f, r * 0.18f), style = st)
                drawArc(dk, 0f, 180f, false, Offset(cx + ex - r * 0.13f, ey - r * 0.06f), Size(r * 0.26f, r * 0.18f), style = st)
            } else {
                drawArc(dk, 200f, 140f, false, Offset(cx - ex - r * 0.13f, ey - r * 0.08f), Size(r * 0.26f, r * 0.2f), style = st)
                drawArc(dk, 200f, 140f, false, Offset(cx + ex - r * 0.13f, ey - r * 0.08f), Size(r * 0.26f, r * 0.2f), style = st)
            }
        } else {
            for (sgn in listOf(-1f, 1f)) {
                val ox = cx + sgn * ex + lx; val oy = ey + ly
                drawOval(dk, Offset(ox - r * 0.1f, oy - r * 0.14f), Size(r * 0.2f, r * 0.28f))
                drawCircle(Color.White, r * 0.045f, Offset(ox - r * 0.03f, oy - r * 0.06f))
            }
        }
        drawOval(Color(0xFFFF8FA8).wrA(0.5f), Offset(cx - r * 0.78f, ey + r * 0.14f), Size(r * 0.28f, r * 0.17f))
        drawOval(Color(0xFFFF8FA8).wrA(0.5f), Offset(cx + r * 0.5f, ey + r * 0.14f), Size(r * 0.28f, r * 0.17f))
        if (giggle > 0.1f) {
            val mw = r * 0.3f
            drawArc(Color(0xFF9A2F3C), 0f, 180f, true, Offset(cx - mw / 2, ey + r * 0.16f), Size(mw, r * 0.3f))
            drawArc(Color(0xFFFF8FA8), 20f, 140f, true, Offset(cx - mw * 0.3f, ey + r * 0.3f), Size(mw * 0.6f, r * 0.14f))
        } else if (mood == 2) {
            drawCircle(dk, r * 0.05f, Offset(cx, ey + r * 0.3f))
        } else {
            drawArc(dk, 20f, 140f, false, Offset(cx - r * 0.17f, ey + r * 0.13f), Size(r * 0.34f, r * 0.24f), style = st)
        }
        val topY = cy - r * 0.93f

        // ── наряд по погоде ──
        if (weather == W_RAIN) {
            // жёлтая зюйдвестка
            drawOval(Color(0xFFFFD43B), Offset(cx - r * 0.95f, topY - r * 0.1f), Size(r * 1.9f, r * 0.55f))
            val dome = Path().apply { moveTo(cx - r * 0.78f, topY + r * 0.1f); quadraticTo(cx, topY - r * 0.75f, cx + r * 0.78f, topY + r * 0.1f); close() }
            drawPath(dome, Color(0xFFFFE066))
            drawLine(Color(0xFFE6A800), Offset(cx - r * 0.75f, topY + r * 0.12f), Offset(cx + r * 0.75f, topY + r * 0.12f), r * 0.05f, StrokeCap.Round)
        }
        if (weather == W_SNOW) {
            // шарфик
            val sw = sin(t * 3f) * r * 0.06f
            drawRoundRect(Color(0xFFE5566D), Offset(cx - r * 0.82f, cy + r * 0.46f), Size(r * 1.64f, r * 0.3f), CornerRadius(r * 0.15f))
            for (i in 0..4) drawLine(Color.White.wrA(0.8f), Offset(cx - r * 0.6f + i * r * 0.3f, cy + r * 0.48f), Offset(cx - r * 0.6f + i * r * 0.3f, cy + r * 0.74f), r * 0.05f)
            val tail = Path().apply {
                moveTo(cx + r * 0.5f, cy + r * 0.7f); lineTo(cx + r * 0.78f + sw, cy + r * 1.15f); lineTo(cx + r * 0.52f + sw, cy + r * 1.2f); lineTo(cx + r * 0.3f, cy + r * 0.74f); close()
            }
            drawPath(tail, Color(0xFFE5566D))
            if (mask and 4 != 0) {
                // шапка с помпоном
                val hat = Path().apply { moveTo(cx - r * 0.72f, topY + r * 0.14f); cubicTo(cx - r * 0.8f, topY - r * 0.7f, cx + r * 0.8f, topY - r * 0.7f, cx + r * 0.72f, topY + r * 0.14f); close() }
                drawPath(hat, Color(0xFF4F8DE8))
                drawRoundRect(Color(0xFFFFFFFF), Offset(cx - r * 0.76f, topY + r * 0.0f), Size(r * 1.52f, r * 0.24f), CornerRadius(r * 0.12f))
                drawCircle(Color.White, r * 0.2f, Offset(cx, topY - r * 0.52f))
            }
        }
        if (weather == W_SUN && mask and 1 != 0) {
            drawOval(Color(0xFFF7D26B), Offset(cx - r * 1.15f, topY - r * 0.05f), Size(r * 2.3f, r * 0.5f))
            val dome = Path().apply { moveTo(cx - r * 0.6f, topY + r * 0.05f); cubicTo(cx - r * 0.6f, topY - r * 0.6f, cx + r * 0.6f, topY - r * 0.6f, cx + r * 0.6f, topY + r * 0.05f); close() }
            drawPath(dome, Color(0xFFFFE08A))
            drawRect(Color(0xFFE5566D), Offset(cx - r * 0.6f, topY - r * 0.12f), Size(r * 1.2f, r * 0.14f))
            for (k in 0 until 5) {
                val a = k * 1.2566f
                drawCircle(Color.White, r * 0.07f, Offset(cx + r * 0.62f + cos(a) * r * 0.09f, topY - r * 0.28f + sin(a) * r * 0.09f))
            }
            drawCircle(Color(0xFFFFC93C), r * 0.05f, Offset(cx + r * 0.62f, topY - r * 0.28f))
        }
        if (weather == W_WIND) {
            // чубчик, который треплет ветер
            val f = windV.coerceIn(-1f, 1f)
            for (k in 0..1) {
                val p = Path().apply { moveTo(cx + (k - 0.5f) * r * 0.2f, topY + r * 0.05f); quadraticTo(cx + (k - 0.5f) * r * 0.2f + f * r * 0.3f, topY - r * 0.3f, cx + f * r * (0.55f + 0.1f * sin(t * 9f + k)), topY - r * (0.28f - 0.06f * k)) }
                drawPath(p, Color(0xFFC98E4A), style = Stroke(r * 0.07f, cap = StrokeCap.Round))
            }
            if (mask and 8 != 0) {
                val cap = Path().apply { moveTo(cx - r * 0.62f, topY + r * 0.12f); cubicTo(cx - r * 0.62f, topY - r * 0.55f, cx + r * 0.62f, topY - r * 0.55f, cx + r * 0.62f, topY + r * 0.12f); close() }
                drawPath(cap, Color(0xFF3FB27F))
                drawOval(Color(0xFF2E8F63), Offset(cx - r * 0.2f, topY + r * 0.02f), Size(r * 1.1f, r * 0.2f))
                drawLine(Color(0xFF8A5A2B), Offset(cx, topY - r * 0.5f), Offset(cx, topY - r * 0.68f), r * 0.05f)
                val sp = t * 14f
                val bl = r * 0.52f
                drawLine(Color(0xFFFFD43B), Offset(cx - cos(sp) * bl, topY - r * 0.7f - sin(sp) * bl * 0.2f), Offset(cx + cos(sp) * bl, topY - r * 0.7f + sin(sp) * bl * 0.2f), r * 0.1f, StrokeCap.Round)
                drawCircle(Color(0xFFE5566D), r * 0.07f, Offset(cx, topY - r * 0.7f))
            }
        }
        if (sleepy && weather != W_RAIN && !(weather == W_SNOW && mask and 4 != 0)) {
            val fancy = mask and 16 != 0
            val cap = Path().apply {
                moveTo(cx - r * 0.7f, topY + r * 0.12f); quadraticTo(cx - r * 0.1f, topY - r * 0.9f, cx + r * 0.95f, topY - r * 0.35f + sin(t * 2f) * r * 0.05f)
                lineTo(cx + r * 0.7f, topY + r * 0.12f); close()
            }
            drawPath(cap, if (fancy) Color(0xFF4D5BCB) else Color(0xFFEDEBFF))
            drawRoundRect(Color.White, Offset(cx - r * 0.74f, topY - r * 0.02f), Size(r * 1.48f, r * 0.2f), CornerRadius(r * 0.1f))
            drawCircle(Color.White, r * 0.13f, Offset(cx + r * 0.95f, topY - r * 0.35f + sin(t * 2f) * r * 0.05f))
            if (fancy) wrSpark4(cx + r * 0.1f, topY - r * 0.32f, r * 0.15f, Color(0xFFFFE066))
        }
        if (mask and 32 != 0) {
            val k = Path().apply {
                moveTo(cx - r * 0.78f, topY - r * 0.08f); lineTo(cx - r * 0.74f, topY - r * 0.36f); lineTo(cx - r * 0.6f, topY - r * 0.2f)
                lineTo(cx - r * 0.5f, topY - r * 0.42f); lineTo(cx - r * 0.4f, topY - r * 0.2f); lineTo(cx - r * 0.26f, topY - r * 0.36f); lineTo(cx - r * 0.24f, topY - r * 0.08f); close()
            }
            drawPath(k, Color(0xFFFFC93C))
            drawPath(k, Color(0xFFD99A00), style = Stroke(r * 0.03f, join = StrokeJoin.Round))
        }
        // зонтик
        if (umb > 0.02f) {
            val u = wrSm(umb)
            val px = cx + r * 0.95f
            val top = cy - r * 1.55f * u - r * 0.1f
            drawLine(Color(0xFF4A3B2A), Offset(px, cy + r * 0.2f), Offset(px, cy - r * 0.9f * u - r * 0.1f), r * 0.07f, StrokeCap.Round)
            val ucx = cx + r * 0.1f
            val rx = r * 1.65f * u; val ry = r * 0.8f * u
            val dome = Path().apply {
                moveTo(ucx - rx, top + ry)
                cubicTo(ucx - rx, top - ry * 0.35f, ucx + rx, top - ry * 0.35f, ucx + rx, top + ry)
                for (i in 3 downTo 0) {
                    val a = ucx - rx + (2 * rx) * i / 4f
                    quadraticTo(a + rx * 0.25f, top + ry * 1.25f, a, top + ry)
                }
                close()
            }
            val fancy = mask and 2 != 0
            drawPath(dome, if (fancy) Color(0xFFFF7FA3) else Color(0xFFFF5D73))
            if (fancy) for (i in 0..5) drawCircle(Color.White.wrA(0.9f), ry * 0.1f, Offset(ucx - rx * 0.75f + rx * 0.3f * i, top + ry * (0.35f + 0.18f * (i % 2))))
            else for (i in 1..3) drawLine(Color(0xFFD94459).wrA(0.6f), Offset(ucx, top + ry * 0.02f), Offset(ucx - rx + (2 * rx) * i / 4f, top + ry), r * 0.03f)
            drawCircle(Color(0xFFFFD43B), r * 0.06f, Offset(ucx, top + ry * 0.02f))
        }
    }
}

// ───────────────────────── интерфейс: значки и наклейки ─────────────────────────
private fun DrawScope.wrIcon(kind: Int, c: Offset, r: Float, t: Float) {
    when (kind) {
        W_SUN -> {
            for (i in 0 until 8) {
                val a = t * 0.8f + i * PI.toFloat() / 4f
                drawLine(Color.White, Offset(c.x + cos(a) * r * 0.66f, c.y + sin(a) * r * 0.66f), Offset(c.x + cos(a) * r * 0.98f, c.y + sin(a) * r * 0.98f), r * 0.16f, StrokeCap.Round)
            }
            drawCircle(Color.White, r * 0.48f, c)
            drawCircle(Color(0xFFFFB400), r * 0.38f, c)
        }
        W_RAIN -> {
            val cl = Color.White
            drawCircle(cl, r * 0.34f, Offset(c.x - r * 0.38f, c.y - r * 0.1f))
            drawCircle(cl, r * 0.46f, Offset(c.x + r * 0.05f, c.y - r * 0.3f))
            drawCircle(cl, r * 0.34f, Offset(c.x + r * 0.5f, c.y - r * 0.08f))
            drawRoundRect(cl, Offset(c.x - r * 0.72f, c.y - r * 0.12f), Size(r * 1.46f, r * 0.34f), CornerRadius(r * 0.17f))
            for (i in 0..2) {
                val ph = (t * 1.6f + i * 0.33f) % 1f
                drawLine(Color(0xFFBDE6FF), Offset(c.x - r * 0.4f + i * r * 0.4f, c.y + r * 0.3f + ph * r * 0.3f), Offset(c.x - r * 0.46f + i * r * 0.4f, c.y + r * 0.5f + ph * r * 0.3f), r * 0.12f, StrokeCap.Round)
            }
        }
        W_SNOW -> wrFlake(c.x, c.y, r * 0.95f, t * 0.4f, Color.White)
        else -> {
            val st = Stroke(r * 0.14f, cap = StrokeCap.Round)
            val sh = sin(t * 3f) * r * 0.06f
            val p1 = Path().apply { moveTo(c.x - r * 0.85f, c.y - r * 0.3f); lineTo(c.x + r * 0.35f + sh, c.y - r * 0.3f); cubicTo(c.x + r * 0.95f, c.y - r * 0.3f, c.x + r * 0.95f, c.y - r * 0.85f, c.x + r * 0.45f, c.y - r * 0.8f) }
            val p2 = Path().apply { moveTo(c.x - r * 0.85f, c.y + r * 0.05f); lineTo(c.x + r * 0.6f - sh, c.y + r * 0.05f); cubicTo(c.x + r * 1.05f, c.y + r * 0.05f, c.x + r * 1.05f, c.y + r * 0.62f, c.x + r * 0.6f, c.y + r * 0.56f) }
            val p3 = Path().apply { moveTo(c.x - r * 0.5f, c.y + r * 0.4f); lineTo(c.x + r * 0.1f + sh, c.y + r * 0.4f); cubicTo(c.x + r * 0.5f, c.y + r * 0.4f, c.x + r * 0.5f, c.y + r * 0.85f, c.x + r * 0.1f, c.y + r * 0.8f) }
            drawPath(p1, Color.White, style = st); drawPath(p2, Color.White, style = st); drawPath(p3, Color.White, style = st)
        }
    }
}

private fun DrawScope.wrMoonIcon(c: Offset, r: Float, night: Float) {
    if (night < 0.5f) {
        drawCircle(Color(0xFFFFD43B), r * 0.45f, c)
        for (i in 0 until 8) {
            val a = i * PI.toFloat() / 4f
            drawLine(Color(0xFFFFD43B), Offset(c.x + cos(a) * r * 0.64f, c.y + sin(a) * r * 0.64f), Offset(c.x + cos(a) * r * 0.9f, c.y + sin(a) * r * 0.9f), r * 0.14f, StrokeCap.Round)
        }
    } else {
        drawCircle(Color(0xFFFFF0A0), r * 0.55f, c)
        drawCircle(Color(0xFF3B4A9A), r * 0.46f, Offset(c.x + r * 0.28f, c.y - r * 0.14f))
        wrSpark4(c.x + r * 0.55f, c.y + r * 0.45f, r * 0.2f, Color(0xFFFFF0A0))
    }
}

private fun DrawScope.wrStickerArt(id: Int, c: Offset, r: Float) {
    val bg = when (id) {
        0 -> listOf(Color(0xFFFFF0A0), Color(0xFFFFC93C)); 1 -> listOf(Color(0xFFBFE8FF), Color(0xFF5DB4F0))
        2 -> listOf(Color(0xFFF4FAFF), Color(0xFFA9D2F2)); 3 -> listOf(Color(0xFFCFF7E2), Color(0xFF63C6A0))
        4 -> listOf(Color(0xFF5E6FD0), Color(0xFF1E2A6E)); else -> listOf(Color(0xFFFFE9A0), Color(0xFFFF9CC2))
    }
    drawCircle(Color(0x22000000), r * 1.02f, Offset(c.x, c.y + r * 0.06f))
    drawCircle(Brush.verticalGradient(bg, c.y - r, c.y + r), r, c)
    drawCircle(Color.White, r, c, style = Stroke(r * 0.07f))
    when (id) {
        0 -> {
            for (i in 0 until 8) { val a = i * PI.toFloat() / 4f; drawLine(Color(0xFFFF9F1C), Offset(c.x + cos(a) * r * 0.5f, c.y + sin(a) * r * 0.5f), Offset(c.x + cos(a) * r * 0.76f, c.y + sin(a) * r * 0.76f), r * 0.09f, StrokeCap.Round) }
            drawCircle(Color(0xFFFF9F1C), r * 0.38f, c)
            drawRoundRect(Color(0xFF2B2F4A), Offset(c.x - r * 0.3f, c.y - r * 0.12f), Size(r * 0.26f, r * 0.2f), CornerRadius(r * 0.06f))
            drawRoundRect(Color(0xFF2B2F4A), Offset(c.x + r * 0.04f, c.y - r * 0.12f), Size(r * 0.26f, r * 0.2f), CornerRadius(r * 0.06f))
        }
        1 -> {
            val d = Path().apply { moveTo(c.x, c.y - r * 0.6f); cubicTo(c.x + r * 0.5f, c.y - r * 0.05f, c.x + r * 0.42f, c.y + r * 0.42f, c.x, c.y + r * 0.42f); cubicTo(c.x - r * 0.42f, c.y + r * 0.42f, c.x - r * 0.5f, c.y - r * 0.05f, c.x, c.y - r * 0.6f); close() }
            drawPath(d, Color.White)
            drawOval(Color.White.wrA(0.6f), Offset(c.x - r * 0.62f, c.y + r * 0.48f), Size(r * 1.24f, r * 0.26f), style = Stroke(r * 0.06f))
        }
        2 -> wrFlake(c.x, c.y, r * 0.62f, 0f, Color(0xFF4F8DE8))
        3 -> {
            val st = Stroke(r * 0.09f, cap = StrokeCap.Round)
            val p1 = Path().apply { moveTo(c.x - r * 0.6f, c.y - r * 0.2f); lineTo(c.x + r * 0.25f, c.y - r * 0.2f); cubicTo(c.x + r * 0.7f, c.y - r * 0.2f, c.x + r * 0.7f, c.y - r * 0.6f, c.x + r * 0.3f, c.y - r * 0.55f) }
            val p2 = Path().apply { moveTo(c.x - r * 0.6f, c.y + r * 0.12f); lineTo(c.x + r * 0.4f, c.y + r * 0.12f); cubicTo(c.x + r * 0.78f, c.y + r * 0.12f, c.x + r * 0.78f, c.y + r * 0.5f, c.x + r * 0.4f, c.y + r * 0.45f) }
            drawPath(p1, Color.White, style = st); drawPath(p2, Color.White, style = st)
            wrLeaf(c.x - r * 0.35f, c.y + r * 0.5f, r * 0.2f, -0.5f, Color(0xFFFFB400), 1f)
        }
        4 -> {
            drawCircle(Color(0xFFFFF0A0), r * 0.42f, Offset(c.x - r * 0.08f, c.y))
            drawCircle(Color(0xFF2C3A8C), r * 0.36f, Offset(c.x + r * 0.08f, c.y - r * 0.08f))
            wrSpark4(c.x + r * 0.42f, c.y + r * 0.3f, r * 0.2f, Color(0xFFFFF0A0)); wrSpark4(c.x + r * 0.3f, c.y - r * 0.5f, r * 0.12f, Color.White)
        }
        else -> {
            val k = Path().apply { moveTo(c.x - r * 0.5f, c.y + r * 0.3f); lineTo(c.x - r * 0.55f, c.y - r * 0.3f); lineTo(c.x - r * 0.25f, c.y); lineTo(c.x, c.y - r * 0.45f); lineTo(c.x + r * 0.25f, c.y); lineTo(c.x + r * 0.55f, c.y - r * 0.3f); lineTo(c.x + r * 0.5f, c.y + r * 0.3f); close() }
            drawPath(k, Color(0xFFFFC93C)); drawPath(k, Color(0xFFD99A00), style = Stroke(r * 0.05f, join = StrokeJoin.Round))
            drawCircle(Color(0xFFE5566D), r * 0.06f, Offset(c.x, c.y - r * 0.05f))
        }
    }
}

private fun DrawScope.wrHand(c: Offset, r: Float, down: Float, t: Float) {
    drawCircle(Color.White.wrA(0.25f + 0.2f * down), r * (1.5f + 0.4f * sin(t * 5f)), c)
    drawCircle(Color.White.wrA(0.9f), r * (1f - 0.12f * down), c)
    drawCircle(Color(0xFFFFC27A), r * (0.82f - 0.12f * down), c)
    drawOval(Color(0xFFFFC27A), Offset(c.x - r * 0.34f, c.y + r * 0.2f), Size(r * 0.68f, r * 1.5f))
}

// ───────────────────────── экран ─────────────────────────

@Composable
fun WeatherRoomScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val sm = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val toddler = LocalAgeProfile.current.isToddler2
    val prefs = remember { context.getSharedPreferences(WR_PREFS, Context.MODE_PRIVATE) }
    val dens = LocalDensity.current.density

    LaunchedEffect(Unit) {
        sm.preloadRaw(
            listOf(
                "fx_wr_change_sun", "fx_wr_change_rain", "fx_wr_change_snow", "fx_wr_change_wind", "fx_wr_day", "fx_wr_night",
                "fx_wr_drop1", "fx_wr_drop2", "fx_wr_drop3", "fx_wr_splash", "fx_wr_puddle", "fx_wr_umbrella",
                "fx_wr_crunch1", "fx_wr_crunch2", "fx_wr_pile", "fx_wr_whoosh1", "fx_wr_whoosh2", "fx_wr_rustle",
                "fx_wr_wink", "fx_wr_glint", "fx_wr_star", "fx_wr_shoot", "fx_wr_cloud", "fx_wr_giggle1", "fx_wr_giggle2",
                "fx_wr_bird1", "fx_wr_bird2", "fx_wr_reward"
            )
        )
    }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(0) }
    val m = remember {
        WrModel().also { md ->
            val rr = Random(7)
            for (i in 0 until WR_NSTAR) { md.stX[i] = 0.03f + 0.94f * rr.nextFloat(); md.stY[i] = 0.02f + 0.40f * rr.nextFloat(); md.stP[i] = rr.nextFloat() * 6.28f }
            for (i in 0 until 5) md.c[i] = prefs.getInt("c$i", 0)
            md.mask = prefs.getInt("mask", 0)
            md.switches = prefs.getInt("sw", 0)
            md.visited = prefs.getInt("vis", 0)
            md.hx = 0.5f; md.hy = 0.5f
        }
    }
    var hintText by remember { mutableStateOf("") }
    var showAlbum by remember { mutableStateOf(false) }
    var rewardSticker by remember { mutableStateOf<WrSticker?>(null) }
    var demoOn by remember { mutableStateOf(false) }
    var selW by remember { mutableIntStateOf(W_SUN) }
    var nightUi by remember { mutableStateOf(false) }
    var stickersUi by remember { mutableIntStateOf(Integer.bitCount(m.mask)) }

    val loopH = remember { HashMap<String, LoopHandle>() }
    val loopV = remember { HashMap<String, Float>() }
    val lastSnd = remember { HashMap<String, Float>() }
    DisposableEffect(Unit) {
        onDispose { loopH.values.forEach { sm.stopLoop(it, 40) }; loopH.clear() }
    }

    fun W() = boxSize.width.toFloat()
    fun H() = boxSize.height.toFloat()
    fun dp(v: Float) = v * dens
    fun GH() = max(1f, H() - dp(108f))
    fun snd(n: String, v: Float = 1f, r: Float = 1f) { sm.play(n, volume = v, rate = r) }
    fun sndT(n: String, gap: Float, v: Float = 1f, r: Float = 1f) {
        val last = lastSnd[n] ?: -9f
        if (m.clock - last >= gap) { lastSnd[n] = m.clock; sm.play(n, volume = v, rate = r) }
    }
    fun need(i: Int) = if (toddler) WR_NEED_T[i] else WR_NEED[i]

    // ── геометрия ──
    fun yL(x: Float) = GH() * 0.66f + sin(x / max(1f, W()) * 6.283f) * GH() * 0.008f
    fun sunR() = min(W() * 0.075f, GH() * 0.075f)
    fun sunX() = W() * 0.2f
    fun sunY() = GH() * 0.2f
    fun houseX() = W() * 0.04f
    fun houseW() = W() * 0.30f
    fun houseG() = GH() * 0.745f
    fun chimX() = houseX() + houseW() * 0.765f
    fun chimY() = houseG() - houseW() * 0.62f - houseW() * 0.5f * 0.78f
    fun treeX() = W() * 0.83f
    fun treeG() = GH() * 0.775f
    fun treeS() = min(W() * 0.17f, GH() * 0.16f)
    fun pelR() = min(W() * 0.095f, GH() * 0.085f)
    fun pelX() = W() * 0.55f
    fun pelY() = GH() * 0.93f - pelR() * 0.9f
    fun pudX(i: Int) = W() * floatArrayOf(0.18f, 0.36f, 0.76f, 0.92f)[i]
    fun pudY(i: Int) = GH() * floatArrayOf(0.84f, 0.955f, 0.865f, 0.96f)[i]
    fun pudRx(i: Int) = W() * 0.085f * m.pud[i]
    fun pudRy(i: Int) = GH() * 0.024f * m.pud[i]
    fun fr() = max(dp(30f), W() * 0.07f) * (if (toddler) 1.25f else 1f)
    fun bunnyHit() = max(dp(34f), W() * 0.085f) * (if (toddler) 1.35f else 1f)
    fun cloudA(i: Int) = ((m.cloudAmt - m.cmin[i]) / 0.15f).coerceIn(0f, 1f)
    fun inPuddle(x: Float, y: Float): Int {
        for (i in 0 until WR_NPUD) {
            if (m.pud[i] < 0.12f) continue
            val dx = (x - pudX(i)) / max(1f, pudRx(i)); val dy = (y - pudY(i)) / max(1f, pudRy(i))
            if (dx * dx + dy * dy < 1.2f) return i
        }
        return -1
    }

    // ── эффекты ──
    fun addRip(x: Float, y: Float, mx: Float, a: Float = 0.8f) { if (m.rips.size < 40) m.rips.add(WrRip(x, y, 0f, mx, a)) }
    fun spark(x: Float, y: Float, n: Int, col: Int, speed: Float, life: Float) {
        for (i in 0 until n) {
            val a = Random.nextFloat() * 6.283f
            val sp = speed * (0.35f + Random.nextFloat() * 0.65f)
            val p = WrP(WP_SPARK, x, y, cos(a) * sp, sin(a) * sp, dp(3f + Random.nextFloat() * 4f), life * (0.6f + Random.nextFloat() * 0.6f))
            p.col = col; p.st = 1
            if (m.parts.size < 900) m.parts.add(p)
        }
    }
    fun droplets(x: Float, y: Float, n: Int, speed: Float) {
        for (i in 0 until n) {
            val a = -PI.toFloat() * (0.15f + 0.7f * Random.nextFloat())
            val sp = speed * (0.4f + Random.nextFloat() * 0.6f)
            val p = WrP(WP_DROP, x, y, cos(a) * sp, sin(a) * sp, dp(1.6f + Random.nextFloat() * 1.6f), 1.2f)
            p.gy = y + GH() * 0.06f
            if (m.parts.size < 900) m.parts.add(p)
        }
    }
    fun spawnLeaf(x: Float, y: Float, vx: Float, vy: Float) {
        val cols = intArrayOf(0xFF5CB85C.toInt(), 0xFFF2A93B.toInt(), 0xFFE5663F.toInt(), 0xFFB9D24A.toInt())
        val p = WrP(WP_LEAF, x, y, vx, vy, dp(5f + Random.nextFloat() * 4f), 9f)
        p.spin = (Random.nextFloat() - 0.5f) * 8f; p.col = cols[Random.nextInt(4)]; p.ph = Random.nextFloat() * 6f
        p.gy = GH() * (0.8f + 0.17f * Random.nextFloat()); p.rot = Random.nextFloat() * 6f
        if (m.parts.size < 900) m.parts.add(p)
    }
    fun spawnFlakes(x: Float, y: Float, n: Int, spread: Float) {
        for (i in 0 until n) {
            val p = WrP(WP_SNOW, x + (Random.nextFloat() - 0.5f) * spread, y + (Random.nextFloat() - 0.5f) * spread * 0.5f, (Random.nextFloat() - 0.5f) * W() * 0.1f, GH() * (0.06f + 0.08f * Random.nextFloat()), dp(3f + Random.nextFloat() * 3.5f), 8f)
            p.gy = GH() * (0.7f + 0.27f * Random.nextFloat()); p.ph = Random.nextFloat() * 6f; p.spin = (Random.nextFloat() - 0.5f) * 2f
            if (m.parts.size < 900) m.parts.add(p)
        }
    }

    // ── прогресс ──
    fun save() {
        val e = prefs.edit()
        for (i in 0 until 5) e.putInt("c$i", m.c[i])
        e.putInt("mask", m.mask).putInt("sw", m.switches).putInt("vis", m.visited).apply()
    }
    fun showNext() {
        if (m.pending.isEmpty()) return
        rewardSticker = WR_STICKERS[m.pending.removeAt(0)]
        snd("fx_wr_reward", 0.95f)
        haptics.vibratePop()
        spark(W() * 0.5f, GH() * 0.4f, 24, 0xFFFFE066.toInt(), W() * 0.5f, 1.2f)
    }
    fun checkRewards() {
        for (i in 0 until 5) if ((m.mask and (1 shl i)) == 0 && m.c[i] >= need(i)) { m.mask = m.mask or (1 shl i); m.pending.add(i) }
        if ((m.mask and 31) == 31 && (m.mask and 32) == 0) { m.mask = m.mask or 32; m.pending.add(5) }
        stickersUi = Integer.bitCount(m.mask)
        save()
        if (rewardSticker == null && m.pending.isNotEmpty()) showNext()
    }
    fun count(i: Int, k: Int = 1) { if (m.real) { m.c[i] += k; checkRewards() } }

    // ── смена погоды ──
    fun setWeather(w: Int, real: Boolean) {
        if (w == m.weather) { m.hop = 1f; m.giggle = 0.8f; snd(if (Random.nextBoolean()) "fx_wr_giggle1" else "fx_wr_giggle2", 0.8f); return }
        m.prev = m.weather; m.weather = w; m.skyBlend = 0f
        selW = w
        snd(when (w) { W_SUN -> "fx_wr_change_sun"; W_RAIN -> "fx_wr_change_rain"; W_SNOW -> "fx_wr_change_snow"; else -> "fx_wr_change_wind" }, 0.9f)
        if (w == W_RAIN) snd("fx_wr_umbrella", 0.8f)
        m.emoteT = 0.45f
        m.hop = 0.6f
        spark(W() * 0.5f, GH() * 0.25f, 16, when (w) { W_SUN -> 0xFFFFE066; W_RAIN -> 0xFFBFE3FF; W_SNOW -> 0xFFFFFFFF; else -> 0xFFBFF2D0 }.toInt(), W() * 0.4f, 0.9f)
        if (real) { m.switches++; m.visited = m.visited or (1 shl w); save() }
        if (w == W_SUN) m.bT = 3f
    }
    fun setNight(on: Boolean, real: Boolean) {
        m.nightTarget = if (on) 1f else 0f
        nightUi = on
        snd(if (on) "fx_wr_night" else "fx_wr_day", 0.85f)
        m.hop = 0.5f
    }
    fun spawnBunny() {
        m.bAlive = true; m.bx = sunX(); m.by = sunY() + sunR(); m.bvx = W() * 0.25f; m.bvy = GH() * 0.2f; m.bRetarget = 0f
        for (i in 0 until 12) { m.trail[i * 2] = m.bx; m.trail[i * 2 + 1] = m.by }
    }
    fun popBunny() {
        spark(m.bx, m.by, 16, 0xFFFFF3A0.toInt(), W() * 0.5f, 1.0f)
        spark(m.bx, m.by, 8, 0xFFFFFFFF.toInt(), W() * 0.3f, 0.8f)
        snd("fx_wr_glint", 1f)
        haptics.vibratePop()
        m.bAlive = false; m.bT = 2.5f; m.hop = 1f; m.giggle = 0.8f
        count(0)
    }

    // ── реакции на касания ──
    fun treeTap(x: Float) {
        m.angV += if (x < treeX()) 3.2f else -3.2f
        val cy = treeG() - treeS() * 1.5f
        for (i in 0 until 6) spawnLeaf(treeX() + (Random.nextFloat() - 0.5f) * treeS() * 1.4f, cy + (Random.nextFloat() - 0.4f) * treeS() * 0.8f, (Random.nextFloat() - 0.3f) * W() * 0.1f, GH() * 0.05f)
        if (m.snowA > 0.3f) spawnFlakes(treeX(), cy - treeS() * 0.4f, 14, treeS() * 1.6f)
        snd("fx_wr_rustle", 0.9f); sndT("fx_wr_whoosh1", 0.2f, 0.5f)
        haptics.vibrateShort()
        if (Random.nextFloat() < 0.4f) m.giggle = 0.6f
        count(3)
    }
    fun cloudTap(i: Int) {
        m.csq[i] = 1f
        val x = m.cx[i] * W(); val y = m.cy[i] * GH() + m.cs[i] * W() * 0.3f
        snd("fx_wr_cloud", 0.9f)
        haptics.vibrateSquish()
        when (m.weather) {
            W_RAIN -> for (k in 0 until 16) {
                val p = WrP(WP_RAIN, x + (Random.nextFloat() - 0.5f) * m.cs[i] * W() * 1.6f, y, (Random.nextFloat() - 0.5f) * W() * 0.1f, GH() * (0.9f + 0.5f * Random.nextFloat()), 1f, 5f)
                p.gy = GH() * (0.7f + 0.27f * Random.nextFloat()); m.parts.add(p)
            }
            W_SNOW -> spawnFlakes(x, y, 14, m.cs[i] * W() * 1.5f)
            W_WIND -> { m.gust += 0.8f; sndT("fx_wr_whoosh2", 0.3f, 0.7f) }
            else -> { spark(x, y, 10, 0xFFFFFFFF.toInt(), W() * 0.2f, 0.9f); m.rbForce = 7f }
        }
    }
    fun starTap(i: Int) {
        m.stF[i] = 1f
        val x = m.stX[i] * W(); val y = m.stY[i] * GH()
        spark(x, y, 10, 0xFFFFF3A0.toInt(), W() * 0.2f, 0.8f)
        snd("fx_wr_star", 0.9f)
        haptics.vibrateShort()
        count(4)
    }
    fun puddleTap(i: Int) {
        val x = pudX(i); val y = pudY(i)
        snd("fx_wr_puddle", 0.95f)
        haptics.vibrateWater()
        addRip(x, y, pudRx(i) * 1.1f); addRip(x, y, pudRx(i) * 0.7f, 0.9f); addRip(x, y, pudRx(i) * 1.4f, 0.5f)
        droplets(x, y, 9, GH() * 0.9f)
        m.hop = 0.8f; m.giggle = 0.7f
        count(1, 3)
    }
    fun pelTap() {
        m.hop = 1f; m.giggle = 1f
        snd(if (Random.nextBoolean()) "fx_wr_giggle1" else "fx_wr_giggle2", 0.95f)
        haptics.vibrateSquish()
        spark(pelX(), pelY() - pelR(), 6, 0xFFFFE066.toInt(), W() * 0.15f, 0.7f)
    }

    // ── палец: касание, движение, отпускание ──
    fun plow(dx: Float, d: Float) {
        m.lanes.add(WrPt(m.fx, m.fy, 0f, false))
        if (m.lanes.size > 110) m.lanes.removeAt(0)
        val dir = if (dx >= 0f) 1f else -1f
        val pm = m.plowM
        if (pm == null) {
            val nm = WrMound(m.fx + dir * dp(20f), m.fy, dp(14f), 0.4f)
            m.mounds.add(nm); m.plowM = nm
            if (m.mounds.size > 14) m.mounds.removeAt(0)
        } else {
            pm.x += (m.fx + dir * pm.rad * 0.8f - pm.x) * 0.5f
            pm.y += (m.fy - pm.rad * 0.1f - pm.y) * 0.5f
            pm.rad = min(W() * 0.12f, pm.rad + d * 0.05f)
            pm.h = min(1f, pm.h + d * 0.004f)
        }
        m.plowed = true
        if (m.clock - m.lastCrunch > 0.13f) {
            m.lastCrunch = m.clock
            snd(if (Random.nextBoolean()) "fx_wr_crunch1" else "fx_wr_crunch2", 0.8f, 0.9f + Random.nextFloat() * 0.25f)
            haptics.vibrateShort()
        }
        if (Random.nextFloat() < 0.5f) spark(m.fx, m.fy, 2, 0xFFFFFFFF.toInt(), W() * 0.12f, 0.6f)
    }

    fun onDown(x: Float, y: Float, real: Boolean) {
        m.real = real
        m.fx = x; m.fy = y; m.fPx = x; m.fPy = y; m.fDown = true; m.stroke = 0f; m.plowed = false; m.plowM = null
        val gh = GH()
        if (m.weather == W_SNOW) m.lanes.add(WrPt(x, y, 0f, true))
        if (m.bAlive && hypot(x - m.bx, y - m.by) < bunnyHit()) { popBunny(); return }
        if (m.weather == W_SUN && m.night < 0.5f && hypot(x - sunX(), y - sunY()) < sunR() * 1.7f) {
            m.sunWink = 1f; snd("fx_wr_wink", 0.95f); haptics.vibratePop()
            spark(sunX(), sunY(), 12, 0xFFFFE066.toInt(), W() * 0.3f, 0.9f)
            if (!m.bAlive) spawnBunny()
            m.hop = 0.6f
            return
        }
        if (m.night > 0.5f) {
            if (hypot(x - sunX(), y - sunY()) < sunR() * 1.5f && m.weather != W_RAIN) {
                m.moonT = 1f; m.shT = 1f; m.shX = W() * 0.3f; m.shY = gh * 0.06f; snd("fx_wr_shoot", 0.8f); return
            }
            if (m.weather == W_SUN || m.weather == W_WIND) {
                var bi = -1; var bd = dp(30f) * (if (toddler) 1.4f else 1f)
                for (i in 0 until WR_NSTAR) { val d = hypot(x - m.stX[i] * W(), y - m.stY[i] * gh); if (d < bd) { bd = d; bi = i } }
                if (bi >= 0) { starTap(bi); return }
            }
        }
        for (i in 0 until WR_NCLOUD) {
            if (cloudA(i) > 0.5f && abs(x - m.cx[i] * W()) < m.cs[i] * W() * 1.1f && abs(y - m.cy[i] * gh) < m.cs[i] * W() * 0.7f) { cloudTap(i); return }
        }
        if (abs(x - treeX()) < treeS() * 0.85f && y < treeG() && y > treeG() - treeS() * 2.5f) { treeTap(x); return }
        if (hypot(x - pelX(), y - pelY()) < pelR() * 1.25f) { pelTap(); return }
        val pi = inPuddle(x, y)
        if (pi >= 0 && m.weather != W_SNOW) { puddleTap(pi); return }
        for (mo in m.mounds) {
            if (hypot(x - mo.x, y - mo.y) < mo.rad) {
                mo.sq = 1f; spawnFlakes(mo.x, mo.y - mo.rad * 0.4f, 8, mo.rad); snd("fx_wr_pile", 0.9f); haptics.vibrateSquish(); return
            }
        }
        when (m.weather) {
            W_RAIN -> {
                snd("fx_wr_drop${1 + Random.nextInt(3)}", 0.8f, 0.9f + Random.nextFloat() * 0.3f)
                if (y > yL(x)) addRip(x, y, dp(46f))
                droplets(x, y, 5, gh * 0.7f)
            }
            W_SNOW -> if (y > yL(x) - gh * 0.02f) {
                val nm = WrMound(x, y, W() * 0.05f, 0.7f); nm.sq = 1f
                m.mounds.add(nm); if (m.mounds.size > 14) m.mounds.removeAt(0)
                snd("fx_wr_pile", 0.95f); haptics.vibrateSquish()
                spawnFlakes(x, y - dp(10f), 8, dp(40f))
                count(2, 1)
            } else { spawnFlakes(x, y, 6, dp(40f)); snd("fx_wr_star", 0.35f, 1.3f) }
            W_WIND -> {
                for (k in 0 until 5) spawnLeaf(x, y, W() * (0.15f + 0.2f * Random.nextFloat()), -gh * 0.08f)
                m.gust += 0.7f; snd("fx_wr_whoosh${1 + Random.nextInt(2)}", 0.7f)
            }
            else -> { spark(x, y, 8, 0xFFFFE066.toInt(), W() * 0.2f, 0.8f); snd("fx_wr_glint", 0.35f, 1.2f) }
        }
    }

    fun onMove(x: Float, y: Float) {
        val dx = x - m.fx; val dy = y - m.fy
        m.fPx = m.fx; m.fPy = m.fy; m.fx = x; m.fy = y
        val d = hypot(dx, dy)
        m.stroke += d
        m.gust = (m.gust + (dx / max(1f, W())) * 6f).coerceIn(-1.4f, 1.6f)
        when (m.weather) {
            W_SNOW -> if (y > yL(x) - GH() * 0.03f) plow(dx, d)
            W_WIND -> {
                m.lastTrail += d
                if (m.lastTrail > dp(44f)) {
                    m.lastTrail = 0f
                    spawnLeaf(x, y, dx * 6f, dy * 4f - GH() * 0.05f)
                    sndT("fx_wr_whoosh${1 + Random.nextInt(2)}", 0.4f, 0.55f)
                }
            }
            W_SUN -> {
                m.lastTrail += d
                if (m.lastTrail > dp(22f)) { m.lastTrail = 0f; spark(x, y, 2, 0xFFFFE066.toInt(), W() * 0.08f, 0.7f) }
            }
        }
    }

    fun onUp() {
        if (!m.fDown) return
        m.fDown = false
        if (m.weather == W_SNOW && m.plowed && m.stroke > W() * 0.2f) count(2)
        if (m.weather == W_WIND && m.stroke > W() * 0.3f) count(3)
        m.plowM = null
    }

    // ── авто-демо ──
    fun stopDemo() {
        if (!m.demo) return
        m.demo = false; demoOn = false
        m.fDown = false; m.plowM = null
        m.dDone = false
    }
    fun startDemo() {
        m.demo = true; demoOn = true; m.dStep = 0; m.dDone = false; m.dT = 0f; m.dTapped = false
        m.hx = W() * 0.5f; m.hy = GH() * 0.5f
    }
    val demoDur = floatArrayOf(3.6f, 4.2f, 2.6f, 5.2f, 3.2f, 4.0f)
    fun demoTick(dt: Float) {
        if (!m.dDone) {
            m.dDone = true; m.dT = 0f; m.dTapped = false
            when (m.dStep) {
                0 -> { if (m.nightTarget > 0.5f) setNight(false, false); setWeather(W_SUN, false) }
                1 -> setWeather(W_RAIN, false)
                3 -> setWeather(W_SNOW, false)
                4 -> setWeather(W_WIND, false)
                5 -> { setWeather(W_SUN, false); setNight(true, false) }
            }
        }
        m.dT += dt
        val t = m.dT
        val gh = GH()
        var tx = m.hx; var ty = m.hy
        var press = 0f
        when (m.dStep) {
            0 -> { tx = sunX(); ty = sunY(); if (t > 1.0f && !m.dTapped) { m.dTapped = true; onDown(tx, ty, false); onUp() }; press = if (t in 0.9f..1.3f) 1f else 0f }
            1 -> {
                val u = (t / 3.8f).coerceIn(0f, 1f)
                tx = W() * (0.2f + 0.6f * u); ty = gh * (0.38f + 0.06f * sin(u * 9f)); press = 1f
                if (t > 0.6f) { if (!m.fDown) onDown(tx, ty, false) else onMove(tx, ty) }
            }
            2 -> {
                if (m.fDown) onUp()
                tx = pudX(0); ty = pudY(0)
                if (t > 1.0f && !m.dTapped) { m.dTapped = true; onDown(tx, ty, false); onUp() }
                press = if (t in 0.9f..1.3f) 1f else 0f
            }
            3 -> {
                val u = (t / 4.4f).coerceIn(0f, 1f)
                tx = W() * (0.12f + 0.62f * u); ty = gh * 0.86f; press = 1f
                if (t > 0.8f) { if (!m.fDown) onDown(tx, ty, false) else onMove(tx, ty) }
            }
            4 -> {
                if (m.fDown) onUp()
                tx = treeX(); ty = treeG() - treeS() * 1.2f
                if (t > 1.1f && !m.dTapped) { m.dTapped = true; onDown(tx, ty, false); onUp() }
                press = if (t in 1.0f..1.4f) 1f else 0f
            }
            5 -> {
                tx = m.stX[5] * W(); ty = m.stY[5] * gh
                if (t > 1.8f && !m.dTapped) { m.dTapped = true; onDown(tx, ty, false); onUp() }
                press = if (t in 1.7f..2.1f) 1f else 0f
            }
        }
        m.hx += (tx - m.hx) * min(1f, dt * 5f)
        m.hy += (ty - m.hy) * min(1f, dt * 5f)
        m.hDown += (press - m.hDown) * min(1f, dt * 12f)
        if (t > demoDur[m.dStep]) {
            if (m.fDown) onUp()
            m.dStep = (m.dStep + 1) % 6; m.dDone = false
        }
    }

    fun loopTick(name: String, target: Float, dt: Float) {
        val cur = loopV[name] ?: 0f
        val nv = cur + (target - cur) * min(1f, dt * 2.5f)
        loopV[name] = nv
        var h = loopH[name]
        if (h != null && h.streamId <= 0) { sm.stopLoop(h, 0); loopH.remove(name); h = null }
        if (nv > 0.03f) {
            if (h == null) { h = sm.startLoop(name); if (h != null) loopH[name] = h }
            if (h != null) sm.setLoopVolume(h, nv)
        } else if (h != null) { sm.stopLoop(h, 200); loopH.remove(name) }
    }

    // ── обновление мира ──
    fun update(dt: Float) {
        if (W() <= 0f) return
        m.clock += dt
        val w = W(); val gh = GH()
        val overlay = showAlbum || rewardSticker != null
        if (!m.demo && !overlay && !m.fDown && m.clock - m.lastTouch > 5f) startDemo()
        if (m.demo && !overlay) demoTick(dt)

        m.skyBlend = min(1f, m.skyBlend + dt / 1.4f)
        m.night += (m.nightTarget - m.night).coerceIn(-dt / 1.2f, dt / 1.2f)

        // ветер
        val wt = when (m.weather) { W_WIND -> 1f; W_RAIN -> 0.28f; W_SNOW -> 0.14f; else -> 0.08f }
        m.gust *= exp(-1.4f * dt)
        m.wind += (wt + m.gust - m.wind) * min(1f, dt * 2.5f)

        // облака
        val ct = when (m.weather) { W_SUN -> 0.35f; W_RAIN -> 1f; W_SNOW -> 1f; else -> 0.6f }
        m.cloudAmt += (ct - m.cloudAmt) * min(1f, dt * 1.2f)
        for (i in 0 until WR_NCLOUD) {
            m.cx[i] += dt * (0.006f + m.wind * 0.04f) * (0.6f + 0.12f * i)
            if (m.cx[i] > 1.25f) m.cx[i] = -0.25f
            m.csq[i] = max(0f, m.csq[i] - dt * 2.2f)
        }

        // влажность, лужи, снег, радуга
        val rain = m.weather == W_RAIN; val snow = m.weather == W_SNOW; val sun = m.weather == W_SUN
        m.wetness = (m.wetness + dt * (if (rain) 0.12f else if (sun) -0.06f else -0.03f)).coerceIn(0f, 1f)
        for (i in 0 until WR_NPUD) m.pud[i] = (m.pud[i] + dt * (if (rain) 0.09f else if (sun) -0.05f else if (m.weather == W_WIND) -0.03f else 0f)).coerceIn(0f, 1f)
        m.snowA = (m.snowA + dt * (if (snow) 0.07f else if (sun) -0.05f else if (rain) -0.09f else -0.012f)).coerceIn(0f, 1f)
        m.rbForce = max(0f, m.rbForce - dt)
        val rbT = if (sun && m.night < 0.5f && (m.wetness > 0.12f || m.rbForce > 0f)) 1f else 0f
        m.rainbow += (rbT - m.rainbow) * min(1f, dt * 1.1f)

        // дерево
        val windF = m.wind * (0.55f + 0.25f * sin(m.clock * 1.7f))
        m.angV += (windF * 2.2f - 14f * m.ang - 1.8f * m.angV) * dt
        m.ang = (m.ang + m.angV * dt).coerceIn(-0.3f, 0.3f)

        // пельмешек
        m.hop = max(0f, m.hop - dt * 1.6f); m.giggle = max(0f, m.giggle - dt * 1.8f)
        m.umb = (m.umb + (if (rain) 1f else -1f) * dt * 3.5f).coerceIn(0f, 1f)
        m.sunWink = max(0f, m.sunWink - dt * 0.9f); m.moonT = max(0f, m.moonT - dt * 1.2f)
        m.nextBlink -= dt
        if (m.nextBlink <= 0f) { m.blink = 0.13f; m.nextBlink = 2f + Random.nextFloat() * 2.5f }
        m.blink = max(0f, m.blink - dt)
        if (m.emoteT > 0f) { m.emoteT -= dt; if (m.emoteT <= 0f) { m.giggle = 0.9f; snd(if (Random.nextBoolean()) "fx_wr_giggle1" else "fx_wr_giggle2", 0.85f) } }
        var lx = 0f; var ly = 0f
        if (m.fDown) { lx = m.fx - pelX(); ly = m.fy - pelY() } else if (m.bAlive) { lx = m.bx - pelX(); ly = m.by - pelY() }
        val ld = max(1f, hypot(lx, ly))
        m.lookX += (lx / ld - m.lookX) * min(1f, dt * 6f); m.lookY += (ly / ld - m.lookY) * min(1f, dt * 6f)
        if (m.night > 0.6f && !m.fDown) {
            m.zzzT -= dt
            if (m.zzzT <= 0f) {
                m.zzzT = 1.8f
                val p = WrP(WP_ZZZ, pelX() + pelR() * 0.9f, pelY() - pelR() * 1.2f, W() * 0.02f, -gh * 0.05f, pelR() * 0.28f, 2.6f); p.st = 0; m.parts.add(p)
            }
        }

        // зайчик
        if (!sun || m.night > 0.5f) m.bAlive = false
        else if (!m.bAlive) { m.bT += dt; if (m.bT > 6f) { m.bT = 0f; spawnBunny() } }
        if (m.bAlive) {
            m.bRetarget -= dt
            if (m.bRetarget <= 0f) { m.bTx = w * (0.1f + 0.8f * Random.nextFloat()); m.bTy = gh * (0.28f + 0.62f * Random.nextFloat()); m.bRetarget = 1.3f + Random.nextFloat() * 1.3f }
            val ddx = m.bTx - m.bx; val ddy = m.bTy - m.by
            val dd = max(1f, hypot(ddx, ddy))
            val sp = w * (if (toddler) 0.24f else 0.36f)
            m.bvx += (ddx / dd * sp - m.bvx) * min(1f, dt * 2.2f); m.bvy += (ddy / dd * sp - m.bvy) * min(1f, dt * 2.2f)
            if (m.fDown) {
                val fx = m.bx - m.fx; val fy = m.by - m.fy; val fd = max(1f, hypot(fx, fy))
                if (fd < w * 0.2f) { m.bvx += fx / fd * w * (if (toddler) 1.2f else 2.4f) * dt; m.bvy += fy / fd * w * (if (toddler) 1.2f else 2.4f) * dt }
            }
            m.bx += m.bvx * dt; m.by += m.bvy * dt
            if (m.bx < w * 0.04f) { m.bx = w * 0.04f; m.bvx = abs(m.bvx) }; if (m.bx > w * 0.96f) { m.bx = w * 0.96f; m.bvx = -abs(m.bvx) }
            if (m.by < gh * 0.1f) { m.by = gh * 0.1f; m.bvy = abs(m.bvy) }; if (m.by > gh * 0.97f) { m.by = gh * 0.97f; m.bvy = -abs(m.bvy) }
            for (i in 11 downTo 1) { m.trail[i * 2] += (m.trail[(i - 1) * 2] - m.trail[i * 2]) * min(1f, dt * 14f); m.trail[i * 2 + 1] += (m.trail[(i - 1) * 2 + 1] - m.trail[i * 2 + 1]) * min(1f, dt * 14f) }
            m.trail[0] = m.bx; m.trail[1] = m.by
        }

        // звёзды и падающая звезда
        for (i in 0 until WR_NSTAR) m.stF[i] = max(0f, m.stF[i] - dt * 1.4f)
        if (m.night > 0.6f && (sun || m.weather == W_WIND)) { m.shNext -= dt; if (m.shNext <= 0f && m.shT <= 0f) { m.shT = 1f; m.shX = w * (0.2f + 0.5f * Random.nextFloat()); m.shY = gh * (0.04f + 0.15f * Random.nextFloat()); m.shNext = 6f + Random.nextFloat() * 6f; snd("fx_wr_shoot", 0.4f) } }
        if (m.shT > 0f) { m.shT -= dt * 1.1f; m.shX += w * 0.55f * dt; m.shY += gh * 0.22f * dt }

        // птицы
        if (sun && m.night < 0.4f && !overlay) { m.birdT -= dt; if (m.birdT <= 0f) { m.birdT = 5f + Random.nextFloat() * 6f; snd(if (Random.nextBoolean()) "fx_wr_bird1" else "fx_wr_bird2", 0.45f) } }

        // рождение частиц
        if (rain) { m.accRain += dt * 170f; while (m.accRain >= 1f) { m.accRain -= 1f
            val gy = gh * (0.68f + 0.30f * Random.nextFloat()); val d = (gy - gh * 0.68f) / (gh * 0.3f)
            val p = WrP(WP_RAIN, w * (-0.1f + 1.2f * Random.nextFloat()), gh * (0.14f + 0.03f * Random.nextFloat()), m.wind * w * 0.18f, gh * (1.0f + 0.5f * Random.nextFloat()) * (0.85f + 0.3f * d), 0.7f + 0.5f * d, 5f)
            p.gy = gy; if (m.parts.size < 900) m.parts.add(p) } }
        if (snow) { m.accSnow += dt * 60f; while (m.accSnow >= 1f) { m.accSnow -= 1f; spawnFlakes(w * Random.nextFloat(), gh * 0.15f, 1, 0f) } }
        if (m.weather == W_WIND) { m.accLeaf += dt * (3f + 3f * m.wind); while (m.accLeaf >= 1f) { m.accLeaf -= 1f
            spawnLeaf(-w * 0.03f, gh * (0.15f + 0.5f * Random.nextFloat()), w * (0.25f + 0.25f * Random.nextFloat()), 0f) } }
        if (sun && m.night < 0.5f) { m.accSpark += dt * 4f; while (m.accSpark >= 1f) { m.accSpark -= 1f
            val a = Random.nextFloat() * 6.283f; val rr = sunR() * (1.3f + Random.nextFloat() * 1.6f)
            val p = WrP(WP_SPARK, sunX() + cos(a) * rr, sunY() + sin(a) * rr, 0f, -gh * 0.02f, dp(3f + Random.nextFloat() * 3f), 1.4f); p.col = 0xFFFFF3A0.toInt(); m.parts.add(p) } }
        m.accSmoke += dt * 1.3f
        while (m.accSmoke >= 1f) { m.accSmoke -= 1f
            val p = WrP(WP_SMOKE, chimX() + houseW() * 0.065f, chimY(), 0f, -gh * 0.07f, houseW() * 0.035f, 3.2f); p.ph = Random.nextFloat() * 6f; m.parts.add(p) }

        // обновление частиц
        val f = fr()
        var i = m.parts.size - 1
        while (i >= 0) {
            val p = m.parts[i]
            var dead = false
            when (p.kind) {
                WP_RAIN -> {
                    p.vx += (m.wind * w * 0.18f - p.vx) * min(1f, dt * 3f)
                    p.x += p.vx * dt; p.y += p.vy * dt
                    if (m.fDown && abs(p.x - m.fx) < f && abs(p.y - m.fy) < f && hypot(p.x - m.fx, p.y - m.fy) < f) {
                        droplets(p.x, p.y, 3, gh * 0.6f)
                        sndT("fx_wr_drop${1 + Random.nextInt(3)}", 0.07f, 0.55f, 0.9f + Random.nextFloat() * 0.35f)
                        if (m.clock - m.lastDrop > 0.3f) { m.lastDrop = m.clock; haptics.vibrateWater() }
                        count(1)
                        dead = true
                    } else if (m.umb > 0.8f) {
                        val r = pelR(); val ucx = pelX() + r * 0.1f; val ucy = pelY() - r * 1.55f - r * 0.1f + r * 0.8f
                        val ex = (p.x - ucx) / (r * 1.65f); val ey = (p.y - ucy) / (r * 0.8f)
                        if (ex * ex + ey * ey < 1f && p.y < ucy + r * 0.1f) { droplets(p.x, p.y, 2, gh * 0.4f); dead = true }
                    }
                    if (!dead && p.y >= p.gy) {
                        val pi = inPuddle(p.x, p.y)
                        if (pi >= 0) { addRip(p.x, p.y, dp(22f), 0.6f); if (Random.nextFloat() < 0.08f) sndT("fx_wr_drop${1 + Random.nextInt(3)}", 0.12f, 0.25f, 1f + Random.nextFloat() * 0.3f) }
                        else if (Random.nextFloat() < 0.5f) droplets(p.x, p.y, 2, gh * 0.25f)
                        dead = true
                    }
                }
                WP_SNOW -> {
                    p.ph += dt
                    p.vx += (m.wind * w * 0.22f - p.vx) * min(1f, dt * 1.5f)
                    p.x += (p.vx + sin(p.ph * 2.2f) * dp(16f)) * dt; p.y += p.vy * dt; p.rot += p.spin * dt
                    if (m.fDown) { val dx = p.x - m.fx; val dy = p.y - m.fy; val d = hypot(dx, dy); if (d < f * 0.9f && d > 1f) { p.x += dx / d * f * dt * 6f; p.y += dy / d * f * dt * 6f } }
                    if (p.y >= p.gy) dead = true
                }
                WP_LEAF -> {
                    if (p.st == 0) {
                        p.ph += dt * 3f
                        p.vx += (m.wind * w * 0.3f - p.vx) * min(1f, dt * 1.2f)
                        p.vy += gh * 0.05f * dt
                        p.x += (p.vx + sin(p.ph) * w * 0.04f) * dt; p.y += (p.vy + cos(p.ph * 1.3f) * gh * 0.05f) * dt
                        p.rot += p.spin * dt
                        if (m.fDown) { val dx = p.x - m.fx; val dy = p.y - m.fy; val d = hypot(dx, dy); if (d < f * 1.4f && d > 1f) { p.vx += dx / d * w * 1.2f * dt + m.gust * w * 0.2f * dt; p.vy -= gh * 0.5f * dt; p.spin += 12f * dt } }
                        if (p.y >= p.gy) { p.st = 1; p.vx = 0f; p.vy = 0f; p.spin = 0f; p.life = min(p.life, 1.6f); p.maxLife = 1.6f }
                        if (p.x > w * 1.1f) dead = true
                    }
                }
                WP_SPARK -> { p.x += p.vx * dt; p.y += p.vy * dt; if (p.st == 1) { p.vx *= exp(-1.6f * dt); p.vy = p.vy * exp(-1.6f * dt) + gh * 0.25f * dt } }
                WP_DROP -> { p.vy += gh * 2.4f * dt; p.x += p.vx * dt; p.y += p.vy * dt; if (p.y >= p.gy && p.vy > 0f) dead = true }
                WP_SMOKE -> { p.ph += dt; p.x += (m.wind * w * 0.1f + sin(p.ph * 1.5f) * dp(5f)) * dt; p.y += p.vy * dt; p.s += dt * houseW() * 0.03f }
                WP_ZZZ -> { p.x += p.vx * dt; p.y += p.vy * dt }
            }
            p.life -= dt
            if (dead || p.life <= 0f) { m.parts[i] = m.parts[m.parts.size - 1]; m.parts.removeAt(m.parts.size - 1) }
            i--
        }

        // рябь
        var ri = m.rips.size - 1
        while (ri >= 0) { val r = m.rips[ri]; r.r += dt * r.max * 1.6f; r.a -= dt * 1.1f; if (r.a <= 0f || r.r > r.max) m.rips.removeAt(ri); ri-- }

        // сугробы и дорожки
        var mi = m.mounds.size - 1
        while (mi >= 0) {
            val mo = m.mounds[mi]
            mo.sq = max(0f, mo.sq - dt * 2.5f)
            if (sun) mo.h -= dt * 0.05f else if (rain) mo.h -= dt * 0.03f
            if (mo.h < 0.06f) m.mounds.removeAt(mi)
            mi--
        }
        var li = m.lanes.size - 1
        while (li >= 0) { val p = m.lanes[li]; p.age += dt * (if (snow) 2f else 1f); if (p.age > 30f) m.lanes.removeAt(li); li-- }

        // звуковые петли
        loopTick("fx_wr_rain_loop", if (rain) 0.75f else 0f, dt)
        loopTick("fx_wr_snow_loop", if (snow) 0.6f else 0f, dt)
        loopTick("fx_wr_wind_loop", if (m.weather == W_WIND) 0.65f else ((abs(m.wind) - 0.2f) * 0.8f).coerceIn(0f, 0.5f), dt)

        // подсказка
        m.hintT -= dt
        if (m.hintT <= 0f) {
            m.hintT = 0.3f
            var mx = 0f; for (k in 0 until WR_NPUD) mx = max(mx, m.pud[k])
            val h = when {
                m.demo -> ""
                m.night > 0.6f && (sun || m.weather == W_WIND) -> "Ночь! Потрогай звёздочки"
                sun -> if (m.bAlive) "Поймай солнечного зайчика!" else "Тапни солнышко — оно подмигнёт!"
                rain -> if (mx > 0.3f) "Води пальцем по дождику и топай по лужам!" else "Води пальцем по дождику!"
                snow -> "Сгребай снег пальцем в сугробы!"
                else -> "Тапни по дереву и проведи пальцем — подует ветер!"
            }
            if (h != hintText) hintText = h
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
                last = now
                update(dt)
                frame = (frame + 1) and 0xFFFFFF
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
            val gh = GH()
            val t = m.clock
            val night = m.night
            val b = wrSm(m.skyBlend)
            val wet = m.wetness
            val sa = m.snowA

            // небо
            var top = lerp(wrSkyTop(m.prev), wrSkyTop(m.weather), b)
            var bot = lerp(wrSkyBot(m.prev), wrSkyBot(m.weather), b)
            top = lerp(top, lerp(Color(0xFF0B1433), top, 0.22f), night)
            bot = lerp(bot, lerp(Color(0xFF2A3875), bot, 0.2f), night)
            drawRect(Brush.verticalGradient(listOf(top, bot), 0f, gh * 0.7f), Offset.Zero, Size(w, h))

            // звёзды
            if (night > 0.05f) {
                val cover = ((m.cloudAmt - 0.35f) / 0.65f).coerceIn(0f, 1f)
                for (i in 0 until WR_NSTAR) {
                    val tw = 0.65f + 0.35f * sin(t * 2f + m.stP[i])
                    val a = night * tw * (1f - cover * 0.92f) + m.stF[i]
                    val sx = m.stX[i] * w; val sy = m.stY[i] * gh
                    drawCircle(Color.White.wrA(a), dp(1.4f + (i % 3) * 0.6f), Offset(sx, sy))
                    if (m.stF[i] > 0.02f) wrSpark4(sx, sy, dp(14f) * m.stF[i], Color(0xFFFFF3A0).wrA(m.stF[i]))
                    else if (i % 5 == 0) wrSpark4(sx, sy, dp(4f) * tw, Color.White.wrA(a * 0.7f))
                }
            }

            // солнце и луна
            val sunVis = (if (m.weather == W_SUN) b else if (m.prev == W_SUN) 1f - b else 0f) * (1f - night)
            val sc = Offset(sunX(), sunY() + sin(t * 1.2f) * dp(3f))
            wrSun(sc, sunR(), t, sunVis, m.sunWink)
            val moonVis = night * (1f - ((m.cloudAmt - 0.35f) / 0.65f).coerceIn(0f, 1f) * 0.75f)
            wrMoon(sc, sunR() * 0.9f, t, moonVis, m.moonT)

            // радуга
            if (m.rainbow > 0.02f) {
                val rc = listOf(Color(0xFFFF5A5A), Color(0xFFFF9F43), Color(0xFFFFD84D), Color(0xFF6BD66B), Color(0xFF4FB4F2), Color(0xFF6C7BE8), Color(0xFFB77BE8))
                val rcx = w * 0.58f; val rcy = gh * 0.66f; val rr0 = w * 0.5f; val th = w * 0.022f
                for (k in 0 until 7) {
                    val rad = rr0 - k * th
                    drawArc(rc[k].wrA(0.55f * m.rainbow), 180f, 180f, false, Offset(rcx - rad, rcy - rad), Size(rad * 2f, rad * 2f), style = Stroke(th * 1.05f))
                }
            }

            // облака
            val cc = lerp(lerp(wrCloudCol(m.prev), wrCloudCol(m.weather), b), Color(0xFF3A4570), night * 0.6f)
            val cs2 = lerp(lerp(wrCloudShade(m.prev), wrCloudShade(m.weather), b), Color(0xFF26305A), night * 0.6f)
            for (i in 0 until WR_NCLOUD) {
                val a = cloudA(i)
                val face = if (i == 0) (if (m.weather == W_SUN || m.weather == W_WIND) 2 else 1) else 0
                wrCloud(m.cx[i] * w, m.cy[i] * gh, m.cs[i] * w * (0.9f + 0.25f * m.cloudAmt), cc, cs2, m.csq[i], t, a, face)
            }

            // холмы
            val hz = gh * 0.64f
            var farCol = lerp(lerp(Color(0xFFA8DDB0), Color(0xFF84B898), wet), bot, 0.25f)
            var nearCol = lerp(Color(0xFF8AD67A), Color(0xFF5FAE70), wet)
            farCol = lerp(lerp(farCol, Color(0xFFEAF2FB), sa * 0.85f), Color(0xFF1B2A4E), night * 0.6f)
            nearCol = lerp(lerp(nearCol, Color(0xFFF2F7FD), sa * 0.9f), Color(0xFF1E3A44), night * 0.55f)
            val far = Path().apply {
                moveTo(0f, hz); cubicTo(w * 0.15f, hz - gh * 0.10f, w * 0.35f, hz - gh * 0.06f, w * 0.5f, hz - gh * 0.03f)
                cubicTo(w * 0.7f, hz - gh * 0.10f, w * 0.85f, hz - gh * 0.08f, w, hz - gh * 0.02f); lineTo(w, hz + gh * 0.1f); lineTo(0f, hz + gh * 0.1f); close()
            }
            drawPath(far, farCol)
            val near = Path().apply {
                moveTo(0f, hz + gh * 0.02f); cubicTo(w * 0.2f, hz - gh * 0.05f, w * 0.4f, hz - gh * 0.01f, w * 0.6f, hz - gh * 0.025f)
                cubicTo(w * 0.8f, hz - gh * 0.05f, w * 0.9f, hz - gh * 0.01f, w, hz + gh * 0.01f); lineTo(w, hz + gh * 0.12f); lineTo(0f, hz + gh * 0.12f); close()
            }
            drawPath(near, nearCol)

            // лужайка
            val lawnTop = lerp(lerp(Color(0xFF86D95F), Color(0xFF4CAF58), wet), Color(0xFF1E3F4A), night * 0.5f)
            val lawnBot = lerp(lerp(Color(0xFF55B948), Color(0xFF2F8A47), wet), Color(0xFF16303A), night * 0.5f)
            val lawn = Path().apply {
                moveTo(0f, yL(0f))
                for (k in 1..16) lineTo(w * k / 16f, yL(w * k / 16f))
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(lawn, Brush.verticalGradient(listOf(lawnTop, lawnBot), gh * 0.64f, h))
            if (sa > 0.01f) {
                drawPath(lawn, Brush.verticalGradient(listOf(Color(0xFFFFFFFF).wrA(sa * 0.97f), Color(0xFFE2EDF9).wrA(sa * 0.97f)), gh * 0.64f, h))
                for (k in 1 until m.lanes.size) {
                    val a = m.lanes[k - 1]; val q = m.lanes[k]
                    if (!q.brk) drawLine(lawnBot.wrA((1f - q.age / 30f).coerceIn(0f, 1f) * sa), Offset(a.x, a.y), Offset(q.x, q.y), fr() * 1.1f, StrokeCap.Round)
                }
            }

            // лужи и рябь
            for (i in 0 until WR_NPUD) {
                val lv = m.pud[i]
                if (lv < 0.04f) continue
                val px = pudX(i); val py = pudY(i); val rx = pudRx(i); val ry = pudRy(i)
                val pa = 1f - sa * 0.6f
                drawOval(Color(0xFF3F8FC8).wrA(0.35f * pa), Offset(px - rx * 1.06f, py - ry * 1.1f), Size(rx * 2.12f, ry * 2.3f))
                drawOval(lerp(bot, Color(0xFF7CC0F0), 0.5f).wrA(0.92f * pa), Offset(px - rx, py - ry), Size(rx * 2f, ry * 2f))
                drawArc(Color.White.wrA(0.6f * pa), 200f, 70f, false, Offset(px - rx * 0.6f, py - ry * 0.7f), Size(rx * 1.2f, ry * 1.1f), style = Stroke(dp(2f), cap = StrokeCap.Round))
            }
            for (rp in m.rips) {
                drawOval(Color.White.wrA(rp.a * 0.85f), Offset(rp.x - rp.r, rp.y - rp.r * 0.35f), Size(rp.r * 2f, rp.r * 0.7f), style = Stroke(dp(1.6f)))
            }

            // травинки
            if (sa < 0.9f) {
                val bc = lerp(Color(0xFF3F9A3F), Color(0xFF1E5A38), max(wet * 0.5f, night * 0.6f)).wrA(1f - sa)
                for (k in 0 until 26) {
                    val gx = w * ((k * 0.618034f) % 1f)
                    val gy = yL(gx) + gh * (0.04f + 0.30f * ((k * 0.381966f) % 1f))
                    val sw = sin(t * 2f + k) * (dp(1.5f) + abs(m.wind) * dp(7f))
                    val s = dp(7f + (k % 3) * 2f)
                    for (j in -1..1) drawLine(bc, Offset(gx + j * dp(3f), gy), Offset(gx + j * dp(3.5f) + sw, gy - s), dp(1.8f), StrokeCap.Round)
                }
            }

            // домик и дерево
            wrHouse(houseX(), houseG(), houseW(), sa, wet, night, t)
            wrTree(treeX(), treeG(), treeS(), m.ang, sa, wet, night)

            // сугробы (дальние), пельмешек, сугробы (ближние)
            val pr = pelR()
            val hopY = if (m.hop > 0f) sin((1f - min(1f, m.hop)) * PI.toFloat()) * pr * 0.55f else 0f
            fun moundDraw(mo: WrMound) {
                val sq = 1f + 0.12f * mo.sq * sin(t * 18f)
                val rw = mo.rad * sq; val rh = mo.rad * (0.35f + 0.65f * mo.h) * (1f - 0.15f * mo.sq)
                drawOval(Color(0x22304A70), Offset(mo.x - rw * 1.05f, mo.y - rh * 0.25f), Size(rw * 2.1f, rh * 0.6f))
                drawOval(Color(0xFFCADCF0), Offset(mo.x - rw, mo.y - rh * 0.9f), Size(rw * 2f, rh * 1.3f))
                drawOval(Color.White, Offset(mo.x - rw * 0.92f, mo.y - rh * 1.0f), Size(rw * 1.84f, rh * 1.15f))
                drawOval(Color(0xFFF2F8FF), Offset(mo.x - rw * 0.4f, mo.y - rh * 0.9f), Size(rw * 0.7f, rh * 0.4f))
            }
            for (mo in m.mounds) if (mo.y < pelY()) moundDraw(mo)
            wrPelmen(pelX(), pelY() - hopY, pr, t, m.weather, night, m.mask, m.umb, m.hop, m.blink, m.giggle,
                m.wind * 0.10f + sin(t * 1.1f) * 0.01f, m.lookX, m.lookY, if (m.weather == W_SNOW && b > 0.5f) 1f else 0f, m.wind)
            for (mo in m.mounds) if (mo.y >= pelY()) moundDraw(mo)

            // частицы
            for (p in m.parts) {
                val lf = (p.life / p.maxLife).coerceIn(0f, 1f)
                when (p.kind) {
                    WP_SMOKE -> drawCircle(lerp(Color(0xFFFFFFFF), Color(0xFF9AA6C0), night).wrA(0.55f * lf), p.s, Offset(p.x, p.y))
                    WP_RAIN -> drawLine(Color(0xFFD2ECFF).wrA(0.8f), Offset(p.x, p.y), Offset(p.x - p.vx * 0.04f, p.y - p.vy * 0.04f), dp(2.1f) * p.s, StrokeCap.Round)
                    WP_SNOW -> wrFlake(p.x, p.y, p.s, p.rot, Color.White.wrA(if (p.life < 0.5f) p.life * 2f else 0.95f))
                    WP_LEAF -> wrLeaf(p.x, p.y, p.s, p.rot, Color(p.col), if (p.st == 1) (p.life / 0.8f).coerceIn(0f, 1f) else 1f)
                    WP_SPARK -> wrSpark4(p.x, p.y, p.s * (0.4f + 0.6f * lf), Color(p.col).wrA(min(1f, lf * 1.6f)))
                    WP_DROP -> drawCircle(Color(0xFFCBE9FF).wrA(0.9f * min(1f, lf * 2f)), p.s, Offset(p.x, p.y))
                    WP_ZZZ -> {
                        val z = Path().apply { moveTo(p.x - p.s, p.y - p.s); lineTo(p.x + p.s, p.y - p.s); lineTo(p.x - p.s, p.y + p.s); lineTo(p.x + p.s, p.y + p.s) }
                        drawPath(z, Color(0xFFDDE6FF).wrA(lf), style = Stroke(p.s * 0.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }

            // полосы ветра
            val wa = ((m.wind - 0.3f) / 0.7f).coerceIn(0f, 1f)
            if (wa > 0.02f) {
                for (k in 0 until 6) {
                    val ph = (t * 0.4f + k * 0.17f) % 1f
                    val x0 = -w * 0.25f + ph * w * 1.5f; val y0 = gh * (0.12f + 0.55f * ((k * 0.37f) % 1f))
                    val p = Path().apply { moveTo(x0, y0); cubicTo(x0 + w * 0.08f, y0 - gh * 0.03f, x0 + w * 0.16f, y0 + gh * 0.03f, x0 + w * 0.22f, y0 - gh * 0.01f) }
                    drawPath(p, Color.White.wrA(0.55f * wa * sin(ph * PI.toFloat())), style = Stroke(dp(3f), cap = StrokeCap.Round))
                }
            }

            // ночь
            if (night > 0.02f) {
                drawRect(Color(0xFF0B1433).wrA(0.30f * night), Offset.Zero, Size(w, h))
                if (m.weather == W_SUN || m.weather == W_WIND) {
                    for (k in 0 until 9) {
                        val fx = w * (0.15f + 0.7f * ((k * 0.37f) % 1f)) + sin(t * 0.6f + k) * w * 0.05f
                        val fy = gh * (0.72f + 0.2f * ((k * 0.53f) % 1f)) + cos(t * 0.8f + k * 2f) * gh * 0.03f
                        val ga = night * (0.5f + 0.5f * sin(t * 2.2f + k * 1.7f))
                        drawCircle(Brush.radialGradient(listOf(Color(0xFFF4FF9A).wrA(0.7f * ga), Color.Transparent), Offset(fx, fy), dp(12f)), dp(12f), Offset(fx, fy))
                        drawCircle(Color(0xFFFFFFC0).wrA(ga), dp(2f), Offset(fx, fy))
                    }
                }
            }

            // солнечный зайчик
            if (m.bAlive) {
                val br = max(dp(10f), w * 0.025f)
                for (i in 11 downTo 1) {
                    val u = 1f - i / 12f
                    drawCircle(Color(0xFFFFE066).wrA(0.35f * u), br * (0.4f + 0.6f * u), Offset(m.trail[i * 2], m.trail[i * 2 + 1]))
                }
                val bc = Offset(m.bx, m.by)
                drawCircle(Brush.radialGradient(listOf(Color(0xCCFFF3A0), Color.Transparent), bc, br * 3.2f), br * 3.2f, bc)
                drawCircle(Color(0xFFFFE066), br, bc)
                drawCircle(Color.White, br * 0.55f, bc)
                wrSpark4(bc.x, bc.y, br * (1.6f + 0.3f * sin(t * 9f)), Color.White.wrA(0.7f))
            }
            // падающая звезда
            if (m.shT > 0f) {
                val a = min(1f, m.shT * 2f)
                drawLine(Color.White.wrA(0.8f * a), Offset(m.shX, m.shY), Offset(m.shX - w * 0.12f, m.shY - gh * 0.05f), dp(3f), StrokeCap.Round)
                wrSpark4(m.shX, m.shY, dp(10f), Color(0xFFFFF3A0).wrA(a))
            }

            // палец
            if (m.fDown && !m.demo) {
                drawCircle(Color.White.wrA(0.18f), fr() * 0.7f, Offset(m.fx, m.fy))
                drawCircle(Color.White.wrA(0.45f), fr() * 0.7f, Offset(m.fx, m.fy), style = Stroke(dp(2f)))
            }
            if (m.demo) wrHand(Offset(m.hx, m.hy), min(w, h) * 0.035f, m.hDown, t)
        }

        // подсказка
        if (hintText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp, start = 12.dp, end = 140.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xE6FFFFFF))
                    .border(2.dp, Color(0xFF8FD3FF), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(hintText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8), textAlign = TextAlign.Center)
            }
        }
        if (demoOn) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp, start = 12.dp, end = 140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xE63F8FE0))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("Просто смотри — потрогай экран, чтобы играть", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White) }
        }

        // день / ночь и альбом
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (nightUi) Color(0xFF3B4A9A) else Color(0xFF8FD3FF))
                    .border(3.dp, Color.White, CircleShape)
                    .clickable { stopDemo(); m.lastTouch = m.clock; setNight(!nightUi, true) },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(38.dp)) { frame.hashCode(); wrMoonIcon(Offset(size.width / 2, size.height / 2), size.width * 0.5f, m.night) }
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFD8A0FF), Color(0xFF9B59E0))))
                    .border(3.dp, Color.White, CircleShape)
                    .clickable { stopDemo(); m.lastTouch = m.clock; showAlbum = true; snd("fx_wr_star", 0.7f) },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(38.dp)) { wrStickerArt(5, Offset(size.width / 2, size.height / 2), size.width * 0.44f) }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 72.dp, end = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xCCFFFFFF))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Наклейки:", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8))
            Text(" $stickersUi/6", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8))
        }

        // панель стихий
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Color(0x99FFFFFF))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val labels = listOf("Солнце", "Дождь", "Снег", "Ветер")
            for (k in 0..3) {
                val sel = selW == k
                val g = when (k) {
                    0 -> listOf(Color(0xFFFFD84D), Color(0xFFFF9F1C)); 1 -> listOf(Color(0xFF8FD3FF), Color(0xFF3F8FE0))
                    2 -> listOf(Color(0xFFBFF0FF), Color(0xFF5FB8D8)); else -> listOf(Color(0xFFA6F0C6), Color(0xFF3FB27F))
                }
                Column(
                    modifier = Modifier
                        .width(72.dp)
                        .offset(y = if (sel) (-6).dp else 0.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.verticalGradient(g))
                        .border(if (sel) 4.dp else 2.dp, if (sel) Color.White else Color(0x88FFFFFF), RoundedCornerShape(22.dp))
                        .clickable { stopDemo(); m.lastTouch = m.clock; setWeather(k, true) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Canvas(Modifier.size(40.dp)) { frame.hashCode(); wrIcon(k, Offset(size.width / 2, size.height / 2), size.width * 0.46f, m.clock) }
                    Text(labels[k], fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
            }
        }

        // альбом «Повелитель погоды»
        if (showAlbum) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x99102040))
                    .clickable { showAlbum = false; m.lastTouch = m.clock },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFF2F8FF))
                        .border(5.dp, Color(0xFF6FB6E8), RoundedCornerShape(28.dp))
                        .clickable(enabled = false) {}
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Альбом «Повелитель погоды»", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8))
                    Spacer(Modifier.height(8.dp))
                    WR_STICKERS.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { st ->
                                val unlocked = (m.mask and (1 shl st.id)) != 0
                                Column(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (unlocked) Color(0xFFDDEEFF) else Color(0xFFE8EBF0))
                                        .padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Canvas(Modifier.size(70.dp)) {
                                        if (unlocked) wrStickerArt(st.id, Offset(size.width / 2, size.height / 2), size.width * 0.4f)
                                        else {
                                            drawCircle(Color(0x22000000), size.width * 0.38f, Offset(size.width / 2, size.height / 2))
                                            drawRoundRect(Color(0xFF8A94A4), Offset(size.width * 0.36f, size.height * 0.46f), Size(size.width * 0.28f, size.height * 0.22f), CornerRadius(6f))
                                            drawArc(Color(0xFF8A94A4), 180f, 180f, false, Offset(size.width * 0.4f, size.height * 0.3f), Size(size.width * 0.2f, size.height * 0.2f), style = Stroke(5f))
                                        }
                                    }
                                    Text(st.name, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8), textAlign = TextAlign.Center, maxLines = 2)
                                    if (!unlocked) {
                                        Text(st.how, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7A90), textAlign = TextAlign.Center, maxLines = 3)
                                        if (st.id < 5) {
                                            val left = max(1, need(st.id) - m.c[st.id])
                                            Row {
                                                Text("Ещё", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8))
                                                Text(" $left", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8))
                                            }
                                        }
                                    } else {
                                        Text(st.note, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE0780A), textAlign = TextAlign.Center, maxLines = 2)
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
                    .background(Color(0xAA102040))
                    .clickable { rewardSticker = null; m.lastTouch = m.clock; showNext() },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFF2F8FF))
                        .border(5.dp, Color(0xFF6FB6E8), RoundedCornerShape(28.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Новая наклейка!", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF3F8FE0))
                    Canvas(Modifier.size(150.dp)) { wrStickerArt(rs.id, Offset(size.width / 2, size.height / 2), size.width * 0.42f) }
                    Text(rs.name, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8))
                    Spacer(Modifier.height(6.dp))
                    Text("Пельмешку — новая шапочка:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A7FA8))
                    Text(rs.note, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFE0780A))
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF3F8FE0))
                            .padding(horizontal = 28.dp, vertical = 8.dp)
                    ) { Text("Играть дальше", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White) }
                }
            }
        }
    }
}
