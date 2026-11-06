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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.LocalAgeProfile
import com.example.shared.SoundManager
import com.example.shared.i18n.Text
import com.example.shared.rememberHaptics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Комната «Гардероб героев»: 9 милых героев из модульного набора. У каждого отдельно голова, наряд и ножки —
// их можно смешивать как угодно (пират с телом клоуна и ножками волшебника). Оружие из набора не используется.
// Награды: 6 наклеек, за них открываются фоны, звёздное сияние, конфетти и корона.

private const val DR_PREFS = "dressup_prefs"

private val DR_NAMES = listOf("Лучник", "Пещерный", "Клоун", "Монах", "Ниндзя", "Пират", "Каска", "Богатырь", "Волшебник")

// смещения относительно центра тела: голова x,y; левая нога x,y; правая нога x,y (в пикселях исходных картинок)
private val DR_OFF = arrayOf(
    intArrayOf(0, -63, -7, 38, 36, 38),
    intArrayOf(1, -67, -19, 37, 24, 36),
    intArrayOf(-2, -51, -17, 35, 25, 34),
    intArrayOf(1, -42, -18, 36, 25, 36),
    intArrayOf(1, -43, -18, 37, 25, 37),
    intArrayOf(-3, -59, -19, 34, 28, 37),
    intArrayOf(2, -60, -18, 37, 26, 37),
    intArrayOf(0, -46, -17, 33, 25, 33),
    intArrayOf(3, -53, -17, 34, 24, 34)
)

private class DrSticker(val id: Int, val name: String, val note: String, val how: String)

private val DR_STICKERS = listOf(
    DrSticker(0, "Первый наряд", "Новый фон — Ночь", "Переодень героя несколько раз"),
    DrSticker(1, "Парад голов", "Звёздное сияние вокруг героя", "Примерь все 9 голов"),
    DrSticker(2, "Парад нарядов", "Новый фон — Конфетный", "Примерь все 9 нарядов"),
    DrSticker(3, "Весёлый микс", "Конфетти при тапе по герою", "Собери героя из трёх разных персонажей"),
    DrSticker(4, "Модник", "Золотая корона для героя", "Переодевай героя много раз"),
    DrSticker(5, "Весь гардероб", "Все подарки теперь твои!", "Собери все наклейки")
)

private class DrP(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val size: Float, val col: Int, val kind: Int) { val maxLife = life }

private class DrModel {
    var clock = 0f
    var jumpT = 1f
    var wobble = 0f
    var head = 5
    var body = 2
    var feet = 8
    var bg = 0
    var changes = 0
    var seenH = 0
    var seenB = 0
    var mask = 0
    var mix = false
    val parts = ArrayList<DrP>()
    val pending = ArrayList<Int>()
}

private fun DrawScope.drImg(img: ImageBitmap, cx: Float, cy: Float, sc: Float, alpha: Float = 1f) {
    val w = img.width * sc; val h = img.height * sc
    drawImage(
        img, IntOffset.Zero, IntSize(img.width, img.height),
        IntOffset(Math.round(cx - w / 2f), Math.round(cy - h / 2f)), IntSize(max(1, Math.round(w)), max(1, Math.round(h))),
        alpha, filterQuality = FilterQuality.Medium
    )
}

private fun DrawScope.drSpark(x: Float, y: Float, r: Float, col: Color) {
    val p = Path().apply {
        moveTo(x, y - r); quadraticTo(x + r * 0.12f, y - r * 0.12f, x + r, y)
        quadraticTo(x + r * 0.12f, y + r * 0.12f, x, y + r); quadraticTo(x - r * 0.12f, y + r * 0.12f, x - r, y)
        quadraticTo(x - r * 0.12f, y - r * 0.12f, x, y - r); close()
    }
    drawPath(p, col)
}

private fun DrawScope.drCrown(cx: Float, cy: Float, s: Float) {
    val k = Path().apply {
        moveTo(cx - s, cy); lineTo(cx - s * 1.05f, cy - s * 0.9f); lineTo(cx - s * 0.5f, cy - s * 0.4f)
        lineTo(cx, cy - s * 1.1f); lineTo(cx + s * 0.5f, cy - s * 0.4f); lineTo(cx + s * 1.05f, cy - s * 0.9f); lineTo(cx + s, cy); close()
    }
    drawPath(k, Color(0xFFFFC93C)); drawPath(k, Color(0xFFD99A00), style = Stroke(s * 0.12f))
    drawCircle(Color(0xFFE5566D), s * 0.14f, Offset(cx, cy - s * 0.45f))
}

private fun DrawScope.drBackdrop(id: Int, t: Float, ground: Float) {
    val w = size.width; val h = size.height
    when (id) {
        0 -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF8ED8F5), Color(0xFFE6F7D6)), 0f, ground), Offset.Zero, Size(w, h))
            drawCircle(Color(0xFFFFE27A), h * 0.075f, Offset(w * 0.82f, h * 0.12f)); drawCircle(Color(0x55FFF3B0), h * 0.11f, Offset(w * 0.82f, h * 0.12f))
            for ((i, c) in listOf(0.2f to 0.1f, 0.55f to 0.18f).withIndex()) {
                val x = ((c.first * w + t * (10f + i * 6f)) % (w * 1.3f)) - w * 0.15f
                drawOval(Color(0xE6FFFFFF), Offset(x, h * c.second), Size(w * 0.24f, h * 0.05f)); drawOval(Color(0xE6FFFFFF), Offset(x + w * 0.05f, h * c.second - h * 0.02f), Size(w * 0.14f, h * 0.05f))
            }
            drawOval(Color(0xFF8AD36B), Offset(-w * 0.2f, ground - h * 0.06f), Size(w * 0.9f, h * 0.2f)); drawOval(Color(0xFF6CC45A), Offset(w * 0.35f, ground - h * 0.04f), Size(w * 0.95f, h * 0.2f))
        }
        1 -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF16224F), Color(0xFF5A4AA0)), 0f, ground), Offset.Zero, Size(w, h))
            for (i in 0 until 26) {
                val x = (i * 0.618f % 1f) * w; val y = ((i * 0.377f + 0.07f) % 1f) * ground * 0.8f
                val a = 0.4f + 0.6f * (0.5f + 0.5f * sin(t * 2f + i))
                drSpark(x, y, h * (0.008f + 0.006f * (i % 3)), Color.White.copy(alpha = a))
            }
            drawCircle(Color(0xFFFFF3C4), h * 0.07f, Offset(w * 0.8f, h * 0.13f)); drawCircle(Color(0xFF16224F), h * 0.06f, Offset(w * 0.8f + h * 0.03f, h * 0.12f))
            drawOval(Color(0xFF2E3A78), Offset(-w * 0.2f, ground - h * 0.06f), Size(w * 1.4f, h * 0.22f))
        }
        else -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFD6EA), Color(0xFFFFF4C9)), 0f, ground), Offset.Zero, Size(w, h))
            val cols = listOf(0xFFFF7A7A, 0xFFFFB866, 0xFFFFE066, 0xFF8EDB7A, 0xFF6CC4F2, 0xFFB78BEA)
            for ((i, c) in cols.withIndex()) drawArc(Color(c), 180f, 180f, false, Offset(w * 0.1f + i * h * 0.02f, ground - h * 0.5f + i * h * 0.02f), Size(w * 0.8f - i * h * 0.04f, h * 0.7f - i * h * 0.04f), style = Stroke(h * 0.022f))
            drawOval(Color(0xFFFFB3D1), Offset(-w * 0.2f, ground - h * 0.05f), Size(w * 1.4f, h * 0.22f))
        }
    }
}

@Composable
fun DressUpRoomScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val sm = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val toddler = LocalAgeProfile.current.isToddler2
    val prefs = remember { context.getSharedPreferences(DR_PREFS, Context.MODE_PRIVATE) }

    val heads = listOf(R.drawable.ch_h0, R.drawable.ch_h1, R.drawable.ch_h2, R.drawable.ch_h3, R.drawable.ch_h4, R.drawable.ch_h5, R.drawable.ch_h6, R.drawable.ch_h7, R.drawable.ch_h8).map { ImageBitmap.imageResource(it) }
    val bodies = listOf(R.drawable.ch_b0, R.drawable.ch_b1, R.drawable.ch_b2, R.drawable.ch_b3, R.drawable.ch_b4, R.drawable.ch_b5, R.drawable.ch_b6, R.drawable.ch_b7, R.drawable.ch_b8).map { ImageBitmap.imageResource(it) }
    val lfs = listOf(R.drawable.ch_lf0, R.drawable.ch_lf1, R.drawable.ch_lf2, R.drawable.ch_lf3, R.drawable.ch_lf4, R.drawable.ch_lf5, R.drawable.ch_lf6, R.drawable.ch_lf7, R.drawable.ch_lf8).map { ImageBitmap.imageResource(it) }
    val rfs = listOf(R.drawable.ch_rf0, R.drawable.ch_rf1, R.drawable.ch_rf2, R.drawable.ch_rf3, R.drawable.ch_rf4, R.drawable.ch_rf5, R.drawable.ch_rf6, R.drawable.ch_rf7, R.drawable.ch_rf8).map { ImageBitmap.imageResource(it) }
    val shadow = ImageBitmap.imageResource(R.drawable.ch_shadow)
    val icons = listOf(R.drawable.ic_star, R.drawable.ic_heart, R.drawable.ic_gift, R.drawable.ic_gem, R.drawable.ic_bolt, R.drawable.ic_trophy).map { ImageBitmap.imageResource(it) }

    LaunchedEffect(Unit) {
        sm.preloadRaw(listOf("fx_squeak", "btn_pop", "fx_boing", "fx_wr_giggle1", "fx_baby_giggle", "fx_magic_sparkle", "btn_tada", "star_collect", "whoosh_soft", "fx_sparkle_chime", "fx_popper"))
    }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(0) }
    val m = remember {
        DrModel().also { md ->
            md.head = prefs.getInt("h", 5); md.body = prefs.getInt("b", 2); md.feet = prefs.getInt("f", 8)
            md.bg = prefs.getInt("bg", 0); md.changes = prefs.getInt("ch", 0)
            md.seenH = prefs.getInt("sh", 0); md.seenB = prefs.getInt("sb", 0); md.mask = prefs.getInt("mask", 0); md.mix = prefs.getBoolean("mix", false)
        }
    }
    var headUi by remember { mutableIntStateOf(m.head) }
    var bodyUi by remember { mutableIntStateOf(m.body) }
    var feetUi by remember { mutableIntStateOf(m.feet) }
    var bgUi by remember { mutableIntStateOf(m.bg) }
    var maskUi by remember { mutableIntStateOf(m.mask) }
    var changesUi by remember { mutableIntStateOf(m.changes) }
    var showAlbum by remember { mutableStateOf(false) }
    var rewardSticker by remember { mutableStateOf<DrSticker?>(null) }

    fun W() = boxSize.width.toFloat()
    fun H() = boxSize.height.toFloat()
    fun snd(n: String, v: Float = 1f, r: Float = 1f) { sm.play(n, volume = v, rate = r) }
    fun needCh(i: Int) = if (i == 0) (if (toddler) 3 else 5) else (if (toddler) 20 else 40)
    fun bits(x: Int) = Integer.bitCount(x)

    fun sc() = min(W() / 330f, H() * 0.55f / 230f)
    fun groundY() = H() * 0.50f
    fun bodyC() = Offset(W() / 2f, groundY() - 66f * sc() * 0.9f)

    fun spark(x: Float, y: Float, n: Int, spd: Float, life: Float, rainbow: Boolean = false) {
        val cols = intArrayOf(0xFFFF7A7A.toInt(), 0xFFFFB866.toInt(), 0xFFFFE066.toInt(), 0xFF8EDB7A.toInt(), 0xFF6CC4F2.toInt(), 0xFFB78BEA.toInt())
        for (i in 0 until n) {
            val a = Random.nextFloat() * 6.283f; val sp = spd * (0.3f + Random.nextFloat() * 0.7f)
            if (m.parts.size < 300) m.parts.add(DrP(x, y, cos(a) * sp, sin(a) * sp - spd * 0.3f, life * (0.6f + Random.nextFloat() * 0.6f), H() * (0.008f + Random.nextFloat() * 0.01f), if (rainbow) cols[Random.nextInt(6)] else 0xFFFFE066.toInt(), if (rainbow) 1 else 0))
        }
    }
    fun save() {
        prefs.edit().putInt("h", m.head).putInt("b", m.body).putInt("f", m.feet).putInt("bg", m.bg).putInt("ch", m.changes)
            .putInt("sh", m.seenH).putInt("sb", m.seenB).putInt("mask", m.mask).putBoolean("mix", m.mix).apply()
    }
    fun showNext() {
        if (m.pending.isEmpty()) return
        val id = m.pending.removeAt(0)
        rewardSticker = DR_STICKERS[id]
        snd(if (id == 5) "btn_tada" else "fx_sparkle_chime", 0.95f)
        haptics.vibratePop()
        spark(W() / 2f, H() * 0.3f, 26, W() * 0.5f, 1.3f, true)
    }
    fun checkRewards() {
        val ok = booleanArrayOf(m.changes >= needCh(0), bits(m.seenH) >= 9, bits(m.seenB) >= 9, m.mix, m.changes >= needCh(4))
        for (i in 0 until 5) if (ok[i] && (m.mask and (1 shl i)) == 0) { m.mask = m.mask or (1 shl i); m.pending.add(i) }
        if ((m.mask and 31) == 31 && (m.mask and 32) == 0) { m.mask = m.mask or 32; m.pending.add(5) }
        maskUi = m.mask
        save()
        if (rewardSticker == null && m.pending.isNotEmpty()) showNext()
    }
    fun progressLeft(i: Int): Int = when (i) {
        0 -> max(1, needCh(0) - m.changes); 1 -> 9 - bits(m.seenH); 2 -> 9 - bits(m.seenB); 4 -> max(1, needCh(4) - m.changes); else -> 1
    }
    fun afterChange() {
        m.changes++; changesUi = m.changes
        m.seenH = m.seenH or (1 shl m.head); m.seenB = m.seenB or (1 shl m.body)
        if (m.head != m.body && m.body != m.feet && m.head != m.feet) m.mix = true
        headUi = m.head; bodyUi = m.body; feetUi = m.feet
        m.wobble = 0.6f
        val c = bodyC()
        spark(c.x, c.y - 40f * sc(), 8, W() * 0.3f, 0.7f)
        haptics.vibrateShort()
        checkRewards()
    }
    fun cycle(row: Int, d: Int) {
        when (row) { 0 -> m.head = (m.head + d + 9) % 9; 1 -> m.body = (m.body + d + 9) % 9; else -> m.feet = (m.feet + d + 9) % 9 }
        snd(if (row == 0) "btn_pop" else "fx_squeak", 0.8f, 0.9f + Random.nextFloat() * 0.3f)
        afterChange()
    }
    fun shuffle() {
        m.head = Random.nextInt(9); m.body = Random.nextInt(9); m.feet = Random.nextInt(9)
        snd("whoosh_soft", 0.8f); snd("fx_boing", 0.6f, 1.1f)
        m.jumpT = 0f
        afterChange()
    }
    fun cheer() {
        if (m.jumpT < 0.7f) return
        m.jumpT = 0f
        snd(if (Random.nextBoolean()) "fx_wr_giggle1" else "fx_baby_giggle", 0.85f)
        snd("fx_boing", 0.5f, 1.2f)
        haptics.vibratePop()
        val c = bodyC()
        spark(c.x, c.y - 50f * sc(), if ((m.mask and 8) != 0) 26 else 10, W() * 0.45f, 0.9f, (m.mask and 8) != 0)
    }
    fun nextBg() {
        var n = m.bg
        for (i in 1..3) { val c = (m.bg + i) % 3; val open = c == 0 || (c == 1 && (m.mask and 1) != 0) || (c == 2 && (m.mask and 4) != 0); if (open) { n = c; break } }
        m.bg = n; bgUi = n; save(); snd("whoosh_soft", 0.7f)
    }

    fun update(dt: Float) {
        if (W() <= 0f) return
        m.clock += dt
        if (m.jumpT < 1f) m.jumpT = min(1f, m.jumpT + dt / 0.65f)
        m.wobble = max(0f, m.wobble - dt)
        if ((m.mask and 2) != 0 && Random.nextFloat() < dt * 6f) {
            val c = bodyC(); val a = Random.nextFloat() * 6.283f; val r = sc() * 95f
            if (m.parts.size < 300) m.parts.add(DrP(c.x + cos(a) * r, c.y - 30f * sc() + sin(a) * r * 0.9f, 0f, -H() * 0.03f, 0.9f, H() * 0.012f, 0xFFFFE066.toInt(), 0))
        }
        var i = m.parts.size - 1
        while (i >= 0) {
            val p = m.parts[i]; p.x += p.vx * dt; p.y += p.vy * dt
            if (p.kind == 1) p.vy += H() * 0.6f * dt
            p.life -= dt; if (p.life <= 0f) m.parts.removeAt(i); i--
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
                last = now
                if (rewardSticker == null && !showAlbum) update(dt) else m.clock += dt
                frame = (frame + 1) and 0xFFFFFF
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { boxSize = it }) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (!showAlbum && rewardSticker == null) cheer()
                }
            }
        ) {
            frame.hashCode()
            val w = size.width; val h = size.height
            if (w <= 0f) return@Canvas
            val s = sc(); val t = m.clock; val g = groundY()
            drBackdrop(bgUi, t, g)
            drawRect(Color(0x00000000), Offset.Zero, Size(w, h))
            // герой
            val jump = if (m.jumpT < 1f) sin(PI.toFloat() * m.jumpT) * h * 0.09f else 0f
            val squash = if (m.jumpT < 0.12f) 1f - 0.12f * (1f - m.jumpT / 0.12f) else if (m.jumpT > 0.85f) 1f - 0.1f * sin((m.jumpT - 0.85f) / 0.15f * PI.toFloat()) else 1f
            val bc0 = bodyC()
            val bob = sin(t * 2.6f) * 2f * s
            val bc = Offset(bc0.x, bc0.y - jump + bob)
            // тень
            val shK = 1f - jump / (h * 0.2f)
            drImg(shadow, bc0.x, g + 6f * s, s * 1.1f * shK, 0.55f * shK)
            withTransform({ scale(1f, squash, Offset(bc0.x, g)) }) {
                val hOff = DR_OFF[headUi]; val fOff = DR_OFF[feetUi]
                drImg(lfs[feetUi], bc.x + fOff[2] * s, bc.y + fOff[3] * s - bob * 0.5f, s)
                drImg(rfs[feetUi], bc.x + fOff[4] * s, bc.y + fOff[5] * s - bob * 0.5f, s)
                drImg(bodies[bodyUi], bc.x, bc.y, s)
                val wob = if (m.wobble > 0f) sin(m.wobble * 40f) * 4f * (m.wobble / 0.6f) else 0f
                val hx = bc.x + hOff[0] * s; val hy = bc.y + hOff[1] * s + sin(t * 2.6f + 0.7f) * 1.5f * s
                withTransform({ rotate(wob, Offset(hx, hy + heads[headUi].height * s * 0.4f)) }) {
                    drImg(heads[headUi], hx, hy, s)
                    if ((m.mask and 16) != 0) drCrown(hx, hy - heads[headUi].height * s * 0.5f + 4f * s, 22f * s)
                }
            }
            // частицы
            for (p in m.parts) {
                val lf = (p.life / p.maxLife).coerceIn(0f, 1f)
                if (p.kind == 0) drSpark(p.x, p.y, p.size * (0.4f + lf), Color(p.col).copy(alpha = min(1f, lf * 1.6f)))
                else drawRect(Color(p.col).copy(alpha = lf), Offset(p.x, p.y), Size(p.size, p.size * 0.6f))
            }
        }

        // верхние кнопки
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val btn = Modifier.size(48.dp).clip(CircleShape).border(3.dp, Color.White, CircleShape)
            Box(btn.background(Brush.verticalGradient(listOf(Color(0xFFFFE9A0), Color(0xFFFFB400)))).clickable { shuffle() }, Alignment.Center) {
                Canvas(Modifier.size(30.dp)) {
                    val u = size.width
                    drawRoundRect(Color.White, Offset(u * 0.1f, u * 0.1f), Size(u * 0.8f, u * 0.8f), androidx.compose.ui.geometry.CornerRadius(u * 0.18f), style = androidx.compose.ui.graphics.drawscope.Fill)
                    for ((x, y) in listOf(0.3f to 0.3f, 0.7f to 0.3f, 0.5f to 0.5f, 0.3f to 0.7f, 0.7f to 0.7f)) drawCircle(Color(0xFF3A3A3A), u * 0.07f, Offset(u * x, u * y))
                }
            }
            Box(btn.background(Brush.verticalGradient(listOf(Color(0xFFBFE3FF), Color(0xFF4FA0E8)))).clickable { nextBg() }, Alignment.Center) {
                Canvas(Modifier.size(30.dp)) {
                    drawCircle(Color(0xFFFFE27A), size.width * 0.2f, Offset(size.width * 0.7f, size.height * 0.3f))
                    drawOval(Color(0xFF6CC45A), Offset(-size.width * 0.1f, size.height * 0.55f), Size(size.width * 1.2f, size.height * 0.7f))
                }
            }
            Box(btn.background(Brush.verticalGradient(listOf(Color(0xFFD8A0FF), Color(0xFF9B59E0)))).clickable { showAlbum = true; snd("star_collect", 0.7f) }, Alignment.Center) {
                Canvas(Modifier.size(34.dp)) { drImg(icons[5], size.width / 2, size.height / 2, size.width / icons[5].width) }
            }
        }
        if (changesUi < 3) {
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 12.dp, end = 170.dp, start = 12.dp)
                    .clip(RoundedCornerShape(18.dp)).background(Color(0xE6FFFFFF)).border(2.dp, Color(0xFF6FB6E8), RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("Жми на стрелки — переодень героя! Тапни по нему — он обрадуется", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8), textAlign = TextAlign.Center) }
        }

        // панель выбора
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp)
                .clip(RoundedCornerShape(24.dp)).background(Color(0xE6FFFFFF)).border(3.dp, Color(0xFF6FB6E8), RoundedCornerShape(24.dp)).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (row in 0..2) {
                val idx = when (row) { 0 -> headUi; 1 -> bodyUi; else -> feetUi }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF3F8FE0)).border(2.dp, Color.White, CircleShape).clickable { cycle(row, -1) },
                        Alignment.Center
                    ) { Canvas(Modifier.size(18.dp)) { drawPath(Path().apply { moveTo(size.width * 0.8f, 0f); lineTo(size.width * 0.15f, size.height / 2); lineTo(size.width * 0.8f, size.height); close() }, Color.White) } }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(52.dp)) {
                            val c = Offset(size.width / 2, size.height / 2)
                            when (row) {
                                0 -> { val im = heads[idx]; drImg(im, c.x, c.y, min(size.width / im.width, size.height / im.height) * 0.95f) }
                                1 -> { val im = bodies[idx]; drImg(im, c.x, c.y, min(size.width / im.width, size.height / im.height) * 0.95f) }
                                else -> { val k = size.width / 70f; drImg(lfs[idx], c.x - 14f * k, c.y, k * 1.1f); drImg(rfs[idx], c.x + 14f * k, c.y, k * 1.1f) }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(when (row) { 0 -> "Голова"; 1 -> "Наряд"; else -> "Ножки" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7A90))
                            Text(DR_NAMES[idx], fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8))
                        }
                    }
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF3F8FE0)).border(2.dp, Color.White, CircleShape).clickable { cycle(row, 1) },
                        Alignment.Center
                    ) { Canvas(Modifier.size(18.dp)) { drawPath(Path().apply { moveTo(size.width * 0.2f, 0f); lineTo(size.width * 0.85f, size.height / 2); lineTo(size.width * 0.2f, size.height); close() }, Color.White) } }
                }
            }
        }

        // альбом
        if (showAlbum) {
            Box(Modifier.fillMaxSize().background(Color(0x99102040)).clickable { showAlbum = false }, Alignment.Center) {
                Column(
                    Modifier.padding(12.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFFF2F8FF)).border(5.dp, Color(0xFF6FB6E8), RoundedCornerShape(28.dp))
                        .clickable(enabled = false) {}.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Альбом «Гардероб героев»", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8))
                    Spacer(Modifier.height(8.dp))
                    DR_STICKERS.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { st ->
                                val un = (maskUi and (1 shl st.id)) != 0
                                Column(
                                    Modifier.width(100.dp).padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(if (un) Color(0xFFDDEEFF) else Color(0xFFE8EBF0)).padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Canvas(Modifier.size(64.dp)) {
                                        val c = Offset(size.width / 2, size.height / 2)
                                        drawCircle(if (un) Color(0xFFFFE9A0) else Color(0x22000000), size.width * 0.46f, c)
                                        if (un) drImg(icons[st.id], c.x, c.y, size.width * 0.62f / icons[st.id].width)
                                    }
                                    Text(st.name, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8), textAlign = TextAlign.Center, maxLines = 2)
                                    if (!un) {
                                        Text(st.how, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7A90), textAlign = TextAlign.Center, maxLines = 3)
                                        if (st.id != 3 && st.id != 5) Row {
                                            Text("Ещё", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8))
                                            Text(" ${progressLeft(st.id)}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8))
                                        }
                                    } else Text(st.note, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE0780A), textAlign = TextAlign.Center, maxLines = 3)
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
            Box(Modifier.fillMaxSize().background(Color(0xAA102040)).clickable { rewardSticker = null; showNext() }, Alignment.Center) {
                Column(
                    Modifier.padding(24.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFFF2F8FF)).border(5.dp, Color(0xFF6FB6E8), RoundedCornerShape(28.dp)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Новая наклейка!", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF3F8FE0))
                    Canvas(Modifier.size(140.dp)) {
                        val c = Offset(size.width / 2, size.height / 2)
                        drawCircle(Color(0xFFFFE9A0), size.width * 0.46f, c); drawCircle(Color.White, size.width * 0.46f, c, style = Stroke(6f))
                        drImg(icons[rs.id], c.x, c.y, size.width * 0.62f / icons[rs.id].width)
                    }
                    Text(rs.name, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8))
                    Spacer(Modifier.height(6.dp))
                    Text("Подарок:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A7FA8))
                    Text(rs.note, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFE0780A), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF3F8FE0)).padding(horizontal = 28.dp, vertical = 8.dp)) {
                        Text("Играть дальше", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
            }
        }
    }
}
