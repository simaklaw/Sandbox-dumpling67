package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * «Кинетический песок» — настоящая карта высот: песок лежит клеточками разной высоты, формочки вдавливаются
 * ровно своим силуэтом (с бортиком по краю и рельефными деталями), нож прорезает тонкую бороздку,
 * пальчик проводит мягкую канавку, грабельки — четыре, гладилка выравнивает. Освещение считается по рельефу,
 * а крутые края потом чуть осыпаются — как у настоящего кинетического песка.
 */
enum class SandTool(val title: String, val icon: String, val isMold: Boolean = false) {
    STAR("Звезда", "⭐", true),
    HEART("Сердце", "💖", true),
    CASTLE("Замок", "🏰", true),
    MUSHROOM("Гриб", "🍄", true),
    PAW("Лапка", "🐾", true),
    FLOWER("Цветок", "🌸", true),
    KNIFE("Нож", "🔪"),
    FINGER("Пальчик", "👆"),
    RAKE("Грабельки", "🍴"),
    SMOOTH("Гладилка", "🧽")
}

// ───────────────────────────── Формочки (маски силуэтов) ─────────────────────────────

private const val MS = 64

private class Mold(val depthMap: FloatArray, val rimMap: FloatArray)

private fun boxBlur(src: FloatArray, r: Int): FloatArray {
    val tmp = FloatArray(src.size)
    val out = FloatArray(src.size)
    val k = 2 * r + 1
    for (y in 0 until MS) for (x in 0 until MS) {
        var s = 0f
        for (dx in -r..r) s += src[y * MS + (x + dx).coerceIn(0, MS - 1)]
        tmp[y * MS + x] = s / k
    }
    for (y in 0 until MS) for (x in 0 until MS) {
        var s = 0f
        for (dy in -r..r) s += tmp[(y + dy).coerceIn(0, MS - 1) * MS + x]
        out[y * MS + x] = s / k
    }
    return out
}

private fun buildMold(tool: SandTool): Mold {
    val bmp = Bitmap.createBitmap(MS, MS, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawColor(android.graphics.Color.BLACK)
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
    val det = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(150, 150, 150); style = Paint.Style.FILL }
    when (tool) {
        SandTool.STAR -> {
            val p = android.graphics.Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 27f else 11.5f
                val a = (-PI / 2 + i * PI / 5).toFloat()
                val x = 32f + cos(a) * r
                val y = 34f + sin(a) * r
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            c.drawPath(p, body)
        }
        SandTool.HEART -> {
            val p = android.graphics.Path()
            p.moveTo(32f, 57f)
            p.cubicTo(2f, 36f, 8f, 8f, 32f, 22f)
            p.cubicTo(56f, 8f, 62f, 36f, 32f, 57f)
            p.close()
            c.drawPath(p, body)
        }
        SandTool.CASTLE -> {
            c.drawRect(10f, 28f, 54f, 57f, body)
            // три башни с зубцами
            val towers = listOf(6f to 20f, 24f to 40f, 44f to 58f)
            for ((a, b) in towers) {
                val top = if (a == 24f) 8f else 14f
                c.drawRect(a, top + 6f, b, 57f, body)
                val m = (b - a) / 5f
                for (k in 0 until 3) c.drawRect(a + k * 2f * m, top, a + k * 2f * m + m, top + 7f, body)
            }
            // дверь и окошки — рельеф (мельче)
            c.drawRoundRect(RectF(27.5f, 41f, 36.5f, 57f), 4f, 4f, det)
            c.drawRect(10f, 26f, 16f, 34f, det)
            c.drawRect(48f, 26f, 54f, 34f, det)
            c.drawRect(29f, 20f, 35f, 28f, det)
        }
        SandTool.MUSHROOM -> {
            c.drawArc(RectF(4f, 6f, 60f, 66f), 180f, 180f, true, body)
            c.drawRoundRect(RectF(24f, 34f, 40f, 59f), 6f, 6f, body)
            c.drawCircle(20f, 25f, 5f, det)
            c.drawCircle(37f, 17f, 5.5f, det)
            c.drawCircle(47f, 27f, 4.5f, det)
        }
        SandTool.PAW -> {
            c.drawOval(RectF(17f, 31f, 47f, 59f), body)
            c.drawCircle(11f, 30f, 7f, body)
            c.drawCircle(23f, 15f, 7.5f, body)
            c.drawCircle(41f, 15f, 7.5f, body)
            c.drawCircle(53f, 30f, 7f, body)
        }
        SandTool.FLOWER -> {
            for (i in 0 until 6) {
                val a = (i * PI / 3).toFloat()
                c.drawCircle(32f + cos(a) * 17f, 32f + sin(a) * 17f, 11.5f, body)
            }
            c.drawCircle(32f, 32f, 10f, det)
        }
        else -> {}
    }
    val px = IntArray(MS * MS)
    bmp.getPixels(px, 0, MS, 0, 0, MS, MS)
    val m = FloatArray(MS * MS) { ((px[it] shr 16) and 0xFF) / 255f }
    bmp.recycle()
    val d = boxBlur(m, 1)
    val wide = boxBlur(m, 5)
    val rim = FloatArray(MS * MS) { ((wide[it] - d[it]).coerceAtLeast(0f) * 2.2f).coerceAtMost(1f) }
    return Mold(d, rim)
}

private fun sampleMap(a: FloatArray, u: Float, v: Float): Float {
    val fx = (u * (MS - 1)).coerceIn(0f, MS - 1.001f)
    val fy = (v * (MS - 1)).coerceIn(0f, MS - 1.001f)
    val x0 = fx.toInt()
    val y0 = fy.toInt()
    val tx = fx - x0
    val ty = fy - y0
    val a00 = a[y0 * MS + x0]
    val a10 = a[y0 * MS + x0 + 1]
    val a01 = a[(y0 + 1) * MS + x0]
    val a11 = a[(y0 + 1) * MS + x0 + 1]
    return (a00 * (1 - tx) + a10 * tx) * (1 - ty) + (a01 * (1 - tx) + a11 * tx) * ty
}

// ───────────────────────────── Песок: карта высот ─────────────────────────────

private class SandField(val gw: Int, val gh: Int, val cellPx: Float) {
    val h = FloatArray(gw * gh)
    private val noise = FloatArray(gw * gh) { (Random.nextFloat() - 0.5f) * 0.07f }
    private val sparkle = BooleanArray(gw * gh) { Random.nextFloat() < 0.012f }
    private val tmp = FloatArray(gw * gh)
    private val pix = IntArray(gw * gh)
    val bmp: Bitmap = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
    val image = bmp.asImageBitmap()

    private var br = 0f
    private var bg = 0f
    private var bb = 0f

    private var dMinX = 0; private var dMaxX = gw - 1; private var dMinY = 0; private var dMaxY = gh - 1
    private var aMinX = gw; private var aMaxX = -1; private var aMinY = gh; private var aMaxY = -1
    var relaxLeft = 0
    var flattenLeft = 0

    fun setColor(c: Color) {
        br = c.red; bg = c.green; bb = c.blue
        dMinX = 0; dMaxX = gw - 1; dMinY = 0; dMaxY = gh - 1
    }

    private fun touch(x0: Int, y0: Int, x1: Int, y1: Int) {
        val ax0 = max(0, x0); val ay0 = max(0, y0); val ax1 = min(gw - 1, x1); val ay1 = min(gh - 1, y1)
        if (ax1 < ax0 || ay1 < ay0) return
        if (dMaxX < dMinX) { dMinX = ax0; dMaxX = ax1; dMinY = ay0; dMaxY = ay1 }
        else { dMinX = min(dMinX, ax0); dMaxX = max(dMaxX, ax1); dMinY = min(dMinY, ay0); dMaxY = max(dMaxY, ay1) }
        aMinX = min(aMinX, ax0); aMaxX = max(aMaxX, ax1); aMinY = min(aMinY, ay0); aMaxY = max(aMaxY, ay1)
        relaxLeft = 14
    }

    /** Вдавить формочку: size — ширина силуэта в клетках, ang — поворот, depth — глубина. */
    fun pressMold(m: Mold, cx: Float, cy: Float, size: Float, ang: Float, depth: Float) {
        val half = (size * 0.78f).toInt() + 2
        val ca = cos(ang); val sa = sin(ang)
        val x0 = (cx - half).toInt(); val x1 = (cx + half).toInt()
        val y0 = (cy - half).toInt(); val y1 = (cy + half).toInt()
        for (y in max(0, y0)..min(gh - 1, y1)) for (x in max(0, x0)..min(gw - 1, x1)) {
            val dx = x + 0.5f - cx
            val dy = y + 0.5f - cy
            val u = (dx * ca + dy * sa) / size + 0.5f
            val v = (-dx * sa + dy * ca) / size + 0.5f
            if (u < 0f || u > 1f || v < 0f || v > 1f) continue
            val i = y * gw + x
            val dm = sampleMap(m.depthMap, u, v)
            val rm = sampleMap(m.rimMap, u, v)
            if (rm > 0.01f && dm < 0.05f) h[i] = min(h[i] + 0.34f * rm, 0.42f)
            if (dm > 0.03f) {
                val c = min(1f, dm * 3.5f)
                h[i] = h[i] * (1f - c) + (-depth * dm) * c
            }
        }
        touch(x0 - 1, y0 - 1, x1 + 1, y1 + 1)
    }

    /** Один «мазок» бороздки: halfW — полуширина в клетках, soft — круглый профиль (пальчик) или V-образный (нож). */
    private fun dab(cx: Float, cy: Float, halfW: Float, depth: Float, ridge: Float, soft: Boolean) {
        val reach = halfW + 2.2f
        val x0 = (cx - reach).toInt(); val x1 = (cx + reach).toInt() + 1
        val y0 = (cy - reach).toInt(); val y1 = (cy + reach).toInt() + 1
        for (y in max(0, y0)..min(gh - 1, y1)) for (x in max(0, x0)..min(gw - 1, x1)) {
            val r = hypot(x + 0.5f - cx, y + 0.5f - cy)
            val i = y * gw + x
            if (r < halfW) {
                val t = r / halfW
                val prof = if (soft) (cos(t * PI.toFloat()) * 0.5f + 0.5f) else (1f - t)
                h[i] = min(h[i], -depth * prof)
            } else if (r < reach) {
                val f = 1f - (r - halfW) / (reach - halfW)
                val target = ridge * f * f
                if (h[i] < target) h[i] = min(target, h[i] + 0.5f)
            }
        }
        touch(x0 - 1, y0 - 1, x1 + 1, y1 + 1)
    }

    fun groove(x0: Float, y0: Float, x1: Float, y1: Float, tool: SandTool) {
        val dx = x1 - x0; val dy = y1 - y0
        val len = hypot(dx, dy)
        if (len < 0.01f) return
        val steps = max(1, (len / 0.6f).toInt())
        val nx = -dy / len; val ny = dx / len
        for (s in 0..steps) {
            val t = s / steps.toFloat()
            val px = x0 + dx * t; val py = y0 + dy * t
            when (tool) {
                SandTool.KNIFE -> dab(px, py, 1.25f, 1.0f, 0.34f, false)
                SandTool.FINGER -> dab(px, py, 3.6f, 0.85f, 0.38f, true)
                SandTool.RAKE -> for (k in -1..2) {
                    val o = (k - 0.5f) * 3.6f
                    dab(px + nx * o, py + ny * o, 0.95f, 0.75f, 0.22f, false)
                }
                else -> {}
            }
        }
    }

    fun smooth(cx: Float, cy: Float, radius: Float) {
        val x0 = (cx - radius).toInt(); val x1 = (cx + radius).toInt() + 1
        val y0 = (cy - radius).toInt(); val y1 = (cy + radius).toInt() + 1
        for (y in max(0, y0)..min(gh - 1, y1)) for (x in max(0, x0)..min(gw - 1, x1)) {
            val r = hypot(x + 0.5f - cx, y + 0.5f - cy)
            if (r >= radius) continue
            val f = 1f - r / radius
            val i = y * gw + x
            h[i] += (0f - h[i]) * (0.35f * f + 0.08f)
        }
        touch(x0 - 1, y0 - 1, x1 + 1, y1 + 1)
    }

    fun flattenAll() { flattenLeft = 34 }

    /** Осыпание слишком крутых склонов и общее выравнивание при «разглаживании». */
    fun step() {
        if (flattenLeft > 0) {
            flattenLeft--
            for (i in h.indices) h[i] *= 0.82f
            if (flattenLeft == 0) java.util.Arrays.fill(h, 0f)
            dMinX = 0; dMaxX = gw - 1; dMinY = 0; dMaxY = gh - 1
            return
        }
        if (relaxLeft <= 0 || aMaxX < aMinX) return
        val left = relaxLeft - 1
        val x0 = max(0, aMinX - 1); val x1 = min(gw - 2, aMaxX + 1)
        val y0 = max(0, aMinY - 1); val y1 = min(gh - 2, aMaxY + 1)
        val repose = 0.55f
        for (y in y0..y1) for (x in x0..x1) {
            val i = y * gw + x
            val r = i + 1
            val d = i + gw
            var diff = h[i] - h[r]
            if (abs(diff) > repose) {
                val f = (abs(diff) - repose) * 0.2f * (if (diff > 0f) 1f else -1f)
                tmp[i] -= f; tmp[r] += f
            }
            diff = h[i] - h[d]
            if (abs(diff) > repose) {
                val f = (abs(diff) - repose) * 0.2f * (if (diff > 0f) 1f else -1f)
                tmp[i] -= f; tmp[d] += f
            }
        }
        for (y in y0..y1 + 1) for (x in x0..x1 + 1) {
            val i = y * gw + x
            if (i < h.size) { h[i] += tmp[i]; tmp[i] = 0f }
        }
        touch(x0, y0, x1 + 1, y1 + 1) // перерисовать изменённое (touch заводит счётчик заново)
        relaxLeft = left
        if (left <= 0) { aMinX = gw; aMaxX = -1; aMinY = gh; aMaxY = -1 }
    }

    /** Пересчитать освещение изменённой области и отправить в картинку. Возвращает true, если что-то поменялось. */
    fun flush(): Boolean {
        if (dMaxX < dMinX) return false
        val x0 = max(0, dMinX - 1); val x1 = min(gw - 1, dMaxX + 1)
        val y0 = max(0, dMinY - 1); val y1 = min(gh - 1, dMaxY + 1)
        for (y in y0..y1) for (x in x0..x1) {
            val i = y * gw + x
            val hl = h[if (x > 0) i - 1 else i]
            val hr = h[if (x < gw - 1) i + 1 else i]
            val hu = h[if (y > 0) i - gw else i]
            val hd = h[if (y < gh - 1) i + gw else i]
            val gx = (hr - hl) * 0.5f * 1.5f
            val gy = (hd - hu) * 0.5f * 1.5f
            val inv = 1f / sqrt(gx * gx + gy * gy + 1f)
            val lam = (-gx * -0.55f + -gy * -0.70f + 0.45f) * inv
            var b = 1f + 1.45f * (lam - 0.45f)
            b = b.coerceIn(0.5f, 1.55f)
            val hh = h[i]
            val ao = 1f + 0.2f * min(0f, hh) + 0.05f * max(0f, hh)
            val n = 1f + noise[i]
            var rr = br * b * ao * n
            var gg = bg * b * ao * n
            var bl = bb * b * ao * n
            if (b > 1.12f) {
                val k = ((b - 1.12f) * 0.8f).coerceAtMost(0.4f)
                rr += (1f - rr) * k; gg += (1f - gg) * k; bl += (1f - bl) * k
            }
            if (sparkle[i]) { rr += 0.12f; gg += 0.12f; bl += 0.12f }
            val ri = (rr.coerceIn(0f, 1f) * 255f).toInt()
            val gi = (gg.coerceIn(0f, 1f) * 255f).toInt()
            val bi = (bl.coerceIn(0f, 1f) * 255f).toInt()
            pix[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
        bmp.setPixels(pix, 0, gw, 0, 0, gw, gh)
        dMinX = gw; dMaxX = -1; dMinY = gh; dMaxY = -1
        return true
    }
}

private class SandGrain(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Color, val size: Float, var life: Float)

private val SAND_COLORS = listOf(
    "Бирюзовый" to Color(0xFF26C6DA),
    "Розовый" to Color(0xFFFF6F9C),
    "Солнечный" to Color(0xFFFFCA28),
    "Фиолетовый" to Color(0xFFAB7BD8),
    "Мятный" to Color(0xFF4DB6AC)
)

@Composable
fun KineticSandScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val dens = LocalDensity.current.density
    val haptics = rememberHaptics()
    val age = LocalAgeProfile.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }

    val tools = remember(age.id) {
        if (age.isToddler2) listOf(SandTool.STAR, SandTool.HEART, SandTool.PAW, SandTool.FLOWER, SandTool.FINGER, SandTool.SMOOTH)
        else SandTool.values().toList()
    }
    var activeTool by remember { mutableStateOf(tools[0]) }
    var colorIndex by remember { mutableIntStateOf(0) }
    val sandColor = SAND_COLORS[colorIndex].second

    val molds = remember { SandTool.values().filter { it.isMold }.associateWith { buildMold(it) } }

    var field by remember { mutableStateOf<SandField?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var touched by remember { mutableStateOf(false) }
    val grains = remember { ArrayList<SandGrain>() }
    val stamina = remember { LongArray(4) } // 0 — звук, 1 — вибро, 2 — время последнего штампа-следа
    val fieldRef = rememberUpdatedState(field)
    val toolRef = rememberUpdatedState(activeTool)
    val colorRef = rememberUpdatedState(sandColor)

    LaunchedEffect(Unit) {
        soundManager.preloadRaw(listOf("sand_press_1", "sand_press_2", "sand_scrape_1", "sand_scrape_2", "sand_scrape_3", "sand_wave"))
    }
    LaunchedEffect(sandColor, field) { field?.setColor(sandColor) }

    fun spray(x: Float, y: Float, n: Int) {
        val col = colorRef.value
        repeat(n) {
            val a = Random.nextFloat() * 6.2832f
            val s = (40f + Random.nextFloat() * 160f) * dens
            grains.add(SandGrain(x, y, cos(a) * s, sin(a) * s - 60f * dens, col.copy(alpha = 0.8f), (1.5f + Random.nextFloat() * 2.2f) * dens, 0.6f + Random.nextFloat() * 0.4f))
        }
        if (grains.size > 220) repeat(grains.size - 220) { grains.removeAt(0) }
    }

    fun pressAt(f: SandField, px: Float, py: Float, tool: SandTool) {
        val mold = molds[tool] ?: return
        val size = if (age.isToddler2) 36f else 28f
        f.pressMold(mold, px / f.cellPx, py / f.cellPx, size, (Random.nextFloat() - 0.5f) * 0.35f, 1.0f)
        soundManager.play("sand_press_${1 + Random.nextInt(2)}", volume = 0.95f, rate = 0.92f + Random.nextFloat() * 0.16f)
        haptics.vibrateSquish()
        spray(px, py, 14)
    }

    fun scrapeFeedback(tool: SandTool, speedPxS: Float, nowMs: Long) {
        val v = (speedPxS / (900f * dens)).coerceIn(0f, 1f)
        if (v < 0.04f) return
        if (nowMs - stamina[0] > 170) {
            stamina[0] = nowMs
            val rate = when (tool) {
                SandTool.KNIFE -> 1.2f
                SandTool.FINGER -> 0.8f
                SandTool.RAKE -> 1.0f
                else -> 0.7f
            }
            soundManager.play("sand_scrape_${1 + Random.nextInt(3)}", volume = 0.25f + 0.65f * v, rate = rate * (0.95f + Random.nextFloat() * 0.1f))
        }
        if (nowMs - stamina[1] > 70) {
            stamina[1] = nowMs
            haptics.vibrate(5)
        }
    }

    fun onDown(p: Offset, nowMs: Long) {
        val f = fieldRef.value ?: return
        touched = true
        val tool = toolRef.value
        stamina[2] = 0
        when {
            tool.isMold -> pressAt(f, p.x, p.y, tool)
            tool == SandTool.SMOOTH -> { f.smooth(p.x / f.cellPx, p.y / f.cellPx, 6f); soundManager.play("sand_scrape_2", volume = 0.5f, rate = 0.7f) }
            tool == SandTool.KNIFE -> {
                f.groove(p.x / f.cellPx, p.y / f.cellPx, p.x / f.cellPx + 0.01f, p.y / f.cellPx, tool)
                soundManager.play("sand_scrape_1", volume = 0.5f, rate = 1.25f)
                spray(p.x, p.y, 4)
            }
            else -> {
                f.groove(p.x / f.cellPx, p.y / f.cellPx, p.x / f.cellPx + 0.01f, p.y / f.cellPx, tool)
                soundManager.play("sand_press_2", volume = 0.6f, rate = 1.1f)
                spray(p.x, p.y, 6)
            }
        }
    }

    var trailAcc = 0f
    fun onMove(prev: Offset, cur: Offset, dtMs: Long, nowMs: Long) {
        val f = fieldRef.value ?: return
        val tool = toolRef.value
        val dist = hypot(cur.x - prev.x, cur.y - prev.y)
        val speed = if (dtMs > 0) dist / (dtMs / 1000f) else 0f
        val c = f.cellPx
        when {
            tool.isMold -> {
                // след из отпечатков вдоль пальца
                trailAcc += dist
                val step = (if (age.isToddler2) 120f else 92f) * dens
                if (trailAcc >= step) { trailAcc = 0f; pressAt(f, cur.x, cur.y, tool) }
            }
            tool == SandTool.SMOOTH -> {
                f.smooth(cur.x / c, cur.y / c, 6.5f)
                scrapeFeedback(tool, speed, nowMs)
            }
            else -> {
                f.groove(prev.x / c, prev.y / c, cur.x / c, cur.y / c, tool)
                scrapeFeedback(tool, speed, nowMs)
                if (Random.nextFloat() < 0.5f && speed > 100f * dens) spray(cur.x, cur.y, 2)
            }
        }
    }

    // Игровой цикл: осыпание склонов, освещение, песчинки
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                last = now
                var changed = false
                field?.let { f ->
                    f.step()
                    if (f.flush()) changed = true
                }
                if (grains.isNotEmpty()) {
                    val it = grains.iterator()
                    while (it.hasNext()) {
                        val g = it.next()
                        g.vy += 900f * dens * dt
                        g.x += g.vx * dt; g.y += g.vy * dt
                        g.life -= dt * 1.6f
                        if (g.life <= 0f) it.remove()
                    }
                    changed = true
                }
                if (changed) tick++
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFF7EFE2), Color(0xFFE8D9BF))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Верхняя строка: место под «Домой», название, цвета песка, разгладить всё
            Row(
                modifier = Modifier.fillMaxWidth().height(60.dp).padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(84.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SAND_COLORS.forEachIndexed { i, (_, col) ->
                        Box(
                            modifier = Modifier
                                .size(if (i == colorIndex) 38.dp else 30.dp)
                                .border(if (i == colorIndex) 4.dp else 2.dp, if (i == colorIndex) Color(0xFF5D4037) else Color.White, CircleShape)
                                .background(col, CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    colorIndex = i
                                    soundManager.play("sand_press_1", volume = 0.5f, rate = 1.3f)
                                    haptics.vibratePop()
                                }
                                .testTag(if (i == 0) "sand_color_btn" else "sand_color_$i")
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .border(3.dp, Color(0xFF5D4037), CircleShape)
                        .background(Color.White, CircleShape)
                        .clip(CircleShape)
                        .clickable {
                            fieldRef.value?.flattenAll()
                            touched = true
                            soundManager.play("sand_wave", volume = 0.9f)
                            haptics.vibrateSquish()
                        }
                        .testTag("sand_refresh_btn")
                ) { Text("🌊", fontSize = 24.sp) }
            }

            // Песочница
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .border(6.dp, Color(0xFF8D6E63), RoundedCornerShape(26.dp))
                    .onSizeChanged { sz: IntSize ->
                        val gw = max(24, (sz.width / (3.2f * dens)).toInt())
                        val gh = max(24, (sz.height / (3.2f * dens)).toInt())
                        val cur = field
                        if (cur == null || cur.gw != gw || cur.gh != gh) {
                            val f = SandField(gw, gh, sz.width / gw.toFloat())
                            f.setColor(colorRef.value)
                            field = f
                        }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            var prev = down.position
                            var lastT = down.uptimeMillis
                            trailAcc = 0f
                            onDown(prev, down.uptimeMillis)
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                val cur = ch.position
                                if (cur != prev) {
                                    onMove(prev, cur, ch.uptimeMillis - lastT, ch.uptimeMillis)
                                    prev = cur
                                    lastT = ch.uptimeMillis
                                }
                                ch.consume()
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    @Suppress("UNUSED_VARIABLE") val redraw = tick
                    field?.let { f ->
                        drawImage(
                            f.image,
                            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                            filterQuality = FilterQuality.Medium
                        )
                    }
                    for (g in grains) {
                        drawCircle(g.color.copy(alpha = g.life.coerceIn(0f, 1f)), g.size, Offset(g.x, g.y))
                    }
                }
                if (!touched) {
                    Text(
                        text = if (age.isToddler2) "Нажми на песок — останется отпечаток!" else "Нажми — отпечаток, води пальцем — след",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Инструменты
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tools.forEach { tool ->
                    val sel = activeTool == tool
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(66.dp)
                            .border(if (sel) 4.dp else 2.dp, if (sel) Color(0xFF5D4037) else Color(0xFFBCAAA4), RoundedCornerShape(18.dp))
                            .background(if (sel) sandColor else Color.White, RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                activeTool = tool
                                soundManager.play("sand_press_1", volume = 0.45f, rate = 1.4f)
                                haptics.vibratePop()
                            }
                            .padding(vertical = 6.dp)
                            .testTag("tool_${tool.name}")
                    ) {
                        Text(tool.icon, fontSize = 28.sp)
                        Text(tool.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else Color(0xFF5D4037), maxLines = 1)
                    }
                }
            }
        }
    }
}
