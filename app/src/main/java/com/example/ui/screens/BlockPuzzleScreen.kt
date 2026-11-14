package com.example.ui.screens

import com.example.shared.withFrameNanosEco
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// Комната «Блок-пазл»: ставь фигурки на поле 8×8, собирай целые линии — они взрываются.
// Нет «проигрыша»: если фигурки некуда ставить, волшебная метёлка очищает поле и можно играть дальше.
// Блоки и частицы: Block Puzzle Kit Free by heyheythere, CC BY 4.0 (https://heyheythere.itch.io/block-puzzle-kit-free).

private const val BP_PREFS = "blockpuzzle_prefs"
private const val BP_N = 8

private val BP_SHAPES: List<Array<String>> = listOf(
    arrayOf("X"), arrayOf("XX"), arrayOf("X", "X"), arrayOf("XXX"), arrayOf("X", "X", "X"), arrayOf("XXXX"), arrayOf("X", "X", "X", "X"),
    arrayOf("XX", "XX"), arrayOf("XX", "X."), arrayOf("XX", ".X"), arrayOf("X.", "XX"), arrayOf(".X", "XX"),
    arrayOf("XXX", "X.."), arrayOf("XXX", "..X"), arrayOf("X..", "XXX"), arrayOf("..X", "XXX"), arrayOf("XXX", ".X."),
    arrayOf("XXX", "XXX"), arrayOf("XX", "XX", "XX"), arrayOf("XXX", "XXX", "XXX"), arrayOf("XXXXX"), arrayOf("X", "X", "X", "X", "X")
)
private val BP_TODDLER_OK = (0..12).toList() + listOf(16, 17)   // только небольшие
private fun bpCells(s: Int): List<Pair<Int, Int>> {
    val l = ArrayList<Pair<Int, Int>>()
    for ((r, row) in BP_SHAPES[s].withIndex()) for ((c, ch) in row.withIndex()) if (ch == 'X') l.add(r to c)
    return l
}
private fun bpW(s: Int) = BP_SHAPES[s][0].length
private fun bpH(s: Int) = BP_SHAPES[s].size

private class BpP(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val size: Float, val col: Int, val kind: Int) { val maxLife = life }

private class BpSticker(val id: Int, val name: String, val note: String, val how: String)

private val BP_STICKERS = listOf(
    BpSticker(0, "Первая линия", "Новый фон — Закат", "Собери целую линию"),
    BpSticker(1, "Двойной взрыв", "Больше блёсток при взрыве", "Взорви две линии за один ход"),
    BpSticker(2, "Цветной дождь", "Новый фон — Космос", "Взрывай линии несколько ходов подряд"),
    BpSticker(3, "Мастер блоков", "Золотая рамка поля", "Набери много очков"),
    BpSticker(4, "Чистое поле", "Конфетти при каждом взрыве", "Очисти всё поле сам"),
    BpSticker(5, "Король блоков", "Все подарки теперь твои!", "Собери все наклейки")
)
private val BP_BG = listOf(
    listOf(Color(0xFF3A4B8C), Color(0xFF1F2850)), listOf(Color(0xFFFF9A6B), Color(0xFF8A3F7A)), listOf(Color(0xFF0E1033), Color(0xFF3B2A7A))
)
private val BP_COL = intArrayOf(0xFFE5566D.toInt(), 0xFFFF9F43.toInt(), 0xFFFFD84D.toInt(), 0xFF6BD66B.toInt(), 0xFF4FD8F0.toInt(), 0xFF4F86F2.toInt(), 0xFFB77BE8.toInt(), 0xFFFF7BC0.toInt())

private class BpModel {
    val grid = IntArray(BP_N * BP_N)
    val shape = IntArray(3) { -1 }
    val color = IntArray(3)
    var score = 0
    var best = 0
    var streak = 0
    var mask = 0
    var bg = 0
    var lines = 0
    var maxStreak = 0
    val parts = ArrayList<BpP>()
    val pending = ArrayList<Int>()
    var banner = ""
    var bannerT = 0f
    var shake = 0f
    var clock = 0f
}

private fun DrawScope.bpImg(img: ImageBitmap, cx: Float, cy: Float, size: Float, alpha: Float = 1f, tint: ColorFilter? = null) {
    drImg(img, cx, cy, size / img.width, alpha, tint)
}

@Composable
fun BlockPuzzleScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val sm = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val toddler = LocalAgeProfile.current.isToddler2
    val prefs = remember { context.getSharedPreferences(BP_PREFS, Context.MODE_PRIVATE) }

    val blocks = listOf(R.drawable.bp_b0, R.drawable.bp_b1, R.drawable.bp_b2, R.drawable.bp_b3, R.drawable.bp_b4, R.drawable.bp_b5, R.drawable.bp_b6, R.drawable.bp_b7).map { ImageBitmap.imageResource(it) }
    val cellImg = ImageBitmap.imageResource(R.drawable.bp_cell)
    val cellAlt = ImageBitmap.imageResource(R.drawable.bp_cell_alt)
    val shardImg = ImageBitmap.imageResource(R.drawable.bp_shard)
    val ringImg = ImageBitmap.imageResource(R.drawable.bp_ring)
    val sparkImg = ImageBitmap.imageResource(R.drawable.bp_sparkle)
    val glowImg = ImageBitmap.imageResource(R.drawable.bp_glow)

    LaunchedEffect(Unit) {
        val l = ArrayList<String>()
        for (i in 1..5) l.add("fx_it_drp_$i")
        for (i in 1..4) { l.add("fx_it_dst_$i"); l.add("fx_it_inv_${4 + i}") }
        l.addAll(listOf("fx_ui_coin", "fx_ui_levelup", "fx_ui_locked", "fx_ui_quest", "fx_ui_xp", "fx_ui_unlock", "fx_ui_click", "fx_ui_menu_open", "fx_ui_menu_close", "btn_tada", "rainbow_chime"))
        sm.preloadRaw(l)
    }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(0) }
    val m = remember { BpModel() }
    var inited by remember { mutableStateOf(false) }
    var scoreUi by remember { mutableIntStateOf(0) }
    var bestUi by remember { mutableIntStateOf(0) }
    var maskUi by remember { mutableIntStateOf(0) }
    var bgUi by remember { mutableIntStateOf(0) }
    var showAlbum by remember { mutableStateOf(false) }
    var reward by remember { mutableStateOf<BpSticker?>(null) }
    var dragIdx by remember { mutableIntStateOf(-1) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }

    fun W() = boxSize.width.toFloat()
    fun H() = boxSize.height.toFloat()
    fun snd(n: String, v: Float = 1f, r: Float = 1f) { sm.play(n, volume = v, rate = r) }
    fun bs() = min(W() * 0.95f, H() * 0.55f)
    fun cell() = bs() / BP_N
    fun bl() = (W() - bs()) / 2f
    fun bt() = H() * 0.13f
    fun slotCx(i: Int) = W() * (1f / 6f + i / 3f)
    fun slotCy() = bt() + bs() + (H() - bt() - bs()) * 0.5f
    fun pieceCs() = min(cell() * 0.72f, W() / 3f * 0.17f)
    fun needScore() = if (toddler) 150 else 400

    fun save() {
        val sb = StringBuilder(); for (v in m.grid) sb.append(v)
        val pcs = (0..2).joinToString(";") { "${m.shape[it]},${m.color[it]}" }
        prefs.edit().putString("grid", sb.toString()).putString("pcs", pcs).putInt("score", m.score).putInt("best", m.best).putInt("mask", m.mask)
            .putInt("bg", m.bg).putInt("lines", m.lines).putInt("ms", m.maxStreak).apply()
    }
    fun newPieces() {
        val pool = if (toddler) BP_TODDLER_OK else BP_SHAPES.indices.toList()
        for (i in 0..2) { m.shape[i] = pool.random(); m.color[i] = 1 + Random.nextInt(8) }
    }
    fun fits(s: Int, gx: Int, gy: Int): Boolean {
        for ((r, c) in bpCells(s)) {
            val x = gx + c; val y = gy + r
            if (x < 0 || y < 0 || x >= BP_N || y >= BP_N || m.grid[y * BP_N + x] != 0) return false
        }
        return true
    }
    fun anyFit(s: Int): Boolean { for (y in 0 until BP_N) for (x in 0 until BP_N) if (fits(s, x, y)) return true; return false }

    fun burst(cx: Float, cy: Float, col: Int, n: Int) {
        val k = if ((m.mask and 2) != 0) n * 2 else n
        for (i in 0 until k) if (m.parts.size < 400) {
            val a = Random.nextFloat() * 6.283f; val sp = W() * (0.15f + Random.nextFloat() * 0.35f)
            m.parts.add(BpP(cx, cy, cos(a) * sp, sin(a) * sp - W() * 0.1f, 0.8f + Random.nextFloat() * 0.5f, cell() * (0.18f + Random.nextFloat() * 0.22f), col, if (Random.nextBoolean()) 0 else 1))
        }
        if (m.parts.size < 400) m.parts.add(BpP(cx, cy, 0f, 0f, 0.5f, cell() * 0.9f, col, 2))
    }
    fun confettiAll() {
        for (i in 0 until 30) if (m.parts.size < 400) m.parts.add(BpP(W() * Random.nextFloat(), H() * 0.1f, (Random.nextFloat() - 0.5f) * W() * 0.2f, H() * 0.1f, 1.4f, cell() * 0.3f, BP_COL[Random.nextInt(8)], 3))
    }
    fun showNext() {
        if (m.pending.isEmpty()) return
        val id = m.pending.removeAt(0)
        reward = BP_STICKERS[id]
        snd("fx_ui_unlock", 0.9f); snd(if (id == 5) "btn_tada" else "rainbow_chime", 0.7f)
        haptics.vibratePop()
    }
    fun checkRewards(cleared: Int, emptied: Boolean) {
        val ok = booleanArrayOf(m.lines >= 1, cleared >= 2, m.maxStreak >= (if (toddler) 2 else 3), m.score >= needScore(), emptied)
        var ch = false
        for (i in 0 until 5) if (ok[i] && (m.mask and (1 shl i)) == 0) { m.mask = m.mask or (1 shl i); m.pending.add(i); ch = true }
        if ((m.mask and 31) == 31 && (m.mask and 32) == 0) { m.mask = m.mask or 32; m.pending.add(5); ch = true }
        if (ch) { maskUi = m.mask; if (reward == null) showNext() }
    }
    fun checkStuck() {
        val avail = (0..2).filter { m.shape[it] >= 0 }
        if (avail.isNotEmpty() && avail.none { anyFit(m.shape[it]) }) {
            // волшебная метёлка
            for (y in 0 until BP_N) for (x in 0 until BP_N) {
                val v = m.grid[y * BP_N + x]
                if (v != 0) burst(bl() + (x + 0.5f) * cell(), bt() + (y + 0.5f) * cell(), BP_COL[v - 1], 2)
            }
            for (i in m.grid.indices) m.grid[i] = 0
            m.banner = "Поле переполнено — метёлка всё убрала!"; m.bannerT = 2.5f
            snd("fx_ui_quest", 0.9f); snd("rainbow_chime", 0.6f)
            haptics.vibratePop()
            m.score += 20; m.streak = 0
        }
    }
    fun place(i: Int, gx: Int, gy: Int) {
        val s = m.shape[i]; val col = m.color[i]
        for ((r, c) in bpCells(s)) m.grid[(gy + r) * BP_N + gx + c] = col
        var gained = bpCells(s).size
        m.shape[i] = -1
        snd("fx_it_drp_${1 + Random.nextInt(5)}", 0.8f)
        haptics.vibrateShort()
        // линии
        val rows = (0 until BP_N).filter { y -> (0 until BP_N).all { x -> m.grid[y * BP_N + x] != 0 } }
        val cols = (0 until BP_N).filter { x -> (0 until BP_N).all { y -> m.grid[y * BP_N + x] != 0 } }
        val clearSet = HashSet<Int>()
        for (y in rows) for (x in 0 until BP_N) clearSet.add(y * BP_N + x)
        for (x in cols) for (y in 0 until BP_N) clearSet.add(y * BP_N + x)
        val n = rows.size + cols.size
        var emptied = false
        if (n > 0) {
            for (idx in clearSet) {
                val v = m.grid[idx]; val x = idx % BP_N; val y = idx / BP_N
                burst(bl() + (x + 0.5f) * cell(), bt() + (y + 0.5f) * cell(), BP_COL[v - 1], 3)
                m.grid[idx] = 0
            }
            m.streak++; m.maxStreak = max(m.maxStreak, m.streak); m.lines += n
            gained += n * 10 * n + (m.streak - 1) * 5
            snd("fx_it_dst_${1 + Random.nextInt(4)}", 0.8f); snd("fx_ui_coin", 0.7f, 0.9f + 0.1f * min(m.streak, 5))
            if (n >= 2) { snd("fx_ui_levelup", 0.7f); m.banner = if (n >= 3) "Супер-взрыв!" else "Двойной взрыв!"; m.bannerT = 1.4f }
            else if (m.streak >= 2) { m.banner = "Серия ×${m.streak}!"; m.bannerT = 1.2f }
            m.shake = 0.25f
            haptics.vibratePop()
            if ((m.mask and 16) != 0) confettiAll()
            emptied = m.grid.all { it == 0 }
            if (emptied) { m.banner = "Чистое поле!"; m.bannerT = 2f; gained += 50; snd("btn_tada", 0.9f); confettiAll() }
        } else m.streak = 0
        m.score += gained
        if (m.score > m.best) m.best = m.score
        scoreUi = m.score; bestUi = m.best
        if (m.shape.all { it < 0 }) newPieces()
        checkRewards(n, emptied)
        checkStuck()
        save()
    }
    fun restart() {
        for (i in m.grid.indices) m.grid[i] = 0
        m.score = 0; m.streak = 0; scoreUi = 0
        newPieces(); save(); snd("fx_ui_click", 0.8f)
    }

    if (!inited) {
        inited = true
        val g = prefs.getString("grid", "") ?: ""
        if (g.length == BP_N * BP_N) for (i in g.indices) m.grid[i] = (g[i] - '0').coerceIn(0, 8)
        val pcs = (prefs.getString("pcs", "") ?: "").split(";")
        if (pcs.size == 3) for (i in 0..2) { val p = pcs[i].split(","); m.shape[i] = p.getOrNull(0)?.toIntOrNull()?.takeIf { it in BP_SHAPES.indices } ?: -1; m.color[i] = p.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 8) ?: 1 }
        if (m.shape.all { it < 0 }) newPieces()
        m.score = prefs.getInt("score", 0); m.best = prefs.getInt("best", 0); m.mask = prefs.getInt("mask", 0); m.bg = prefs.getInt("bg", 0)
        m.lines = prefs.getInt("lines", 0); m.maxStreak = prefs.getInt("ms", 0)
        scoreUi = m.score; bestUi = m.best; maskUi = m.mask; bgUi = m.bg
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanosEco { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
                last = now
                m.clock += dt
                m.bannerT = max(0f, m.bannerT - dt); m.shake = max(0f, m.shake - dt)
                var i = m.parts.size - 1
                while (i >= 0) {
                    val p = m.parts[i]; p.x += p.vx * dt; p.y += p.vy * dt
                    if (p.kind != 2) p.vy += H() * 0.7f * dt
                    p.life -= dt; if (p.life <= 0f) m.parts.removeAt(i); i--
                }
                frame = (frame + 1) and 0xFFFFFF
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { boxSize = it }) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (showAlbum || reward != null) return@awaitEachGesture
                    var idx = -1
                    for (i in 0..2) if (m.shape[i] >= 0 && Math.abs(down.position.x - slotCx(i)) < W() / 6f && Math.abs(down.position.y - slotCy()) < (H() - bt() - bs()) * 0.5f) idx = i
                    if (idx < 0) return@awaitEachGesture
                    dragIdx = idx; dragPos = down.position
                    snd("fx_it_inv_${5 + Random.nextInt(4)}", 0.6f)
                    var cur = down.position
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        cur = ch.position; dragPos = cur; ch.consume()
                        if (!ch.pressed) break
                    }
                    val s = m.shape[idx]
                    val cs = cell()
                    val pw = bpW(s) * cs; val ph = bpH(s) * cs
                    val cx = cur.x; val cy = cur.y - cs * 1.2f - ph / 2f
                    val gx = ((cx - pw / 2f - bl()) / cs).roundToInt(); val gy = ((cy - ph / 2f - bt()) / cs).roundToInt()
                    dragIdx = -1
                    if (fits(s, gx, gy)) place(idx, gx, gy) else if (cur.y < bt() + bs() + cs) { snd("fx_ui_locked", 0.6f) }
                }
            }
        ) {
            frame.hashCode()
            val w = size.width; val h = size.height
            if (w <= 0f) return@Canvas
            val cs = cell(); val b0 = Offset(bl(), bt()); val t = m.clock
            drawRect(Brush.verticalGradient(BP_BG[bgUi], 0f, h), Offset.Zero, Size(w, h))
            if (bgUi == 2) for (i in 0 until 30) { val a = 0.3f + 0.5f * sin(t * 2f + i); bpImg(sparkImg, ((i * 0.618f) % 1f) * w, ((i * 0.377f) % 1f) * h, h * 0.015f * (1 + i % 3), a) }
            val sx = if (m.shake > 0f) sin(m.shake * 90f) * cs * 0.08f else 0f
            // рамка
            val frameCol = if ((m.mask and 8) != 0) Color(0xFFFFC93C) else Color(0xFF6C7BC4)
            drawRoundRect(frameCol, Offset(b0.x - cs * 0.18f + sx, b0.y - cs * 0.18f), Size(bs() + cs * 0.36f, bs() + cs * 0.36f), CornerRadius(cs * 0.4f))
            drawRoundRect(Color(0xFF1B2146), Offset(b0.x - cs * 0.06f + sx, b0.y - cs * 0.06f), Size(bs() + cs * 0.12f, bs() + cs * 0.12f), CornerRadius(cs * 0.3f))
            // клетки поля
            for (y in 0 until BP_N) for (x in 0 until BP_N) {
                val alt = ((x / 4) + (y / 4)) % 2 == 1
                bpImg(if (alt) cellAlt else cellImg, b0.x + (x + 0.5f) * cs + sx, b0.y + (y + 0.5f) * cs, cs * 0.98f)
            }
            // призрак
            var ghostOk = false; var gxg = 0; var gyg = 0
            if (dragIdx >= 0 && m.shape[dragIdx] >= 0) {
                val s = m.shape[dragIdx]; val pw = bpW(s) * cs; val ph = bpH(s) * cs
                val cx = dragPos.x; val cy = dragPos.y - cs * 1.2f - ph / 2f
                gxg = ((cx - pw / 2f - bl()) / cs).roundToInt(); gyg = ((cy - ph / 2f - bt()) / cs).roundToInt()
                ghostOk = fits(s, gxg, gyg)
                if (ghostOk) for ((r, c) in bpCells(s)) bpImg(blocks[m.color[dragIdx] - 1], b0.x + (gxg + c + 0.5f) * cs + sx, b0.y + (gyg + r + 0.5f) * cs, cs * 0.96f, 0.4f)
            }
            // блоки
            for (y in 0 until BP_N) for (x in 0 until BP_N) {
                val v = m.grid[y * BP_N + x]
                if (v != 0) bpImg(blocks[v - 1], b0.x + (x + 0.5f) * cs + sx, b0.y + (y + 0.5f) * cs, cs * 0.98f)
            }
            // лоток
            for (i in 0..2) {
                val s = m.shape[i]; if (s < 0 || dragIdx == i) continue
                val ps = pieceCs(); val pw = bpW(s) * ps; val ph = bpH(s) * ps
                val bob = sin(t * 3f + i) * ps * 0.05f
                for ((r, c) in bpCells(s)) bpImg(blocks[m.color[i] - 1], slotCx(i) - pw / 2f + (c + 0.5f) * ps, slotCy() - ph / 2f + (r + 0.5f) * ps + bob, ps * 0.96f)
            }
            // перетаскиваемая фигурка
            if (dragIdx >= 0 && m.shape[dragIdx] >= 0) {
                val s = m.shape[dragIdx]; val pw = bpW(s) * cs; val ph = bpH(s) * cs
                val x0 = dragPos.x - pw / 2f; val y0 = dragPos.y - cs * 1.2f - ph
                for ((r, c) in bpCells(s)) bpImg(blocks[m.color[dragIdx] - 1], x0 + (c + 0.5f) * cs, y0 + (r + 0.5f) * cs, cs * 0.98f, if (ghostOk) 1f else 0.85f)
            }
            // частицы
            for (p in m.parts) {
                val lf = (p.life / p.maxLife).coerceIn(0f, 1f)
                val tint = ColorFilter.tint(Color(p.col), BlendMode.SrcIn)
                when (p.kind) {
                    0 -> bpImg(shardImg, p.x, p.y, p.size * 2f, lf, tint)
                    1 -> bpImg(sparkImg, p.x, p.y, p.size * 2.4f, lf, tint)
                    2 -> bpImg(ringImg, p.x, p.y, p.size * (1.5f - lf), lf, tint)
                    else -> drawRect(Color(p.col).copy(alpha = lf), Offset(p.x, p.y), Size(p.size, p.size * 0.6f))
                }
            }
            if (m.bannerT > 0f) {
                val a = min(1f, m.bannerT * 2f)
                drawRoundRect(Color(0xCC000000).copy(alpha = 0.7f * a), Offset(w * 0.1f, bt() + bs() * 0.42f), Size(w * 0.8f, cs * 1.4f), CornerRadius(cs * 0.5f))
            }
        }

        // верхняя панель
        Row(Modifier.align(Alignment.TopStart).padding(top = 10.dp, start = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 3.dp)) {
                Text("Очки", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB8C4F0))
                Text("$scoreUi", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 3.dp)) {
                Text("Рекорд", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB8C4F0))
                Text("$bestUi", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFE066))
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val btn = Modifier.size(44.dp).clip(CircleShape).border(3.dp, Color.White, CircleShape)
            Box(btn.background(Color(0xFF4F86F2)).clickable { restart() }, Alignment.Center) {
                Canvas(Modifier.size(24.dp)) {
                    drawArc(Color.White, 40f, 280f, false, Offset(size.width * 0.15f, size.height * 0.15f), Size(size.width * 0.7f, size.height * 0.7f), style = Stroke(5f))
                    drawPath(Path().apply { moveTo(size.width * 0.55f, size.height * 0.02f); lineTo(size.width * 0.9f, size.height * 0.2f); lineTo(size.width * 0.55f, size.height * 0.38f); close() }, Color.White)
                }
            }
            Box(btn.background(Brush.verticalGradient(listOf(Color(0xFFFFB866), Color(0xFFE5566D)))).clickable {
                var n = m.bg
                for (k in 1..3) { val c = (m.bg + k) % 3; if (c == 0 || (c == 1 && (m.mask and 1) != 0) || (c == 2 && (m.mask and 4) != 0)) { n = c; break } }
                m.bg = n; bgUi = n; save(); snd("fx_ui_click", 0.7f)
            }, Alignment.Center) { Canvas(Modifier.size(24.dp)) { drawCircle(Color(0xFFFFE27A), size.width * 0.3f, Offset(size.width / 2, size.height * 0.4f)); drawRect(Color(0xFF3A4B8C), Offset(0f, size.height * 0.62f), Size(size.width, size.height * 0.4f)) } }
            Box(btn.background(Brush.verticalGradient(listOf(Color(0xFFD8A0FF), Color(0xFF9B59E0)))).clickable { showAlbum = true; snd("fx_ui_menu_open", 0.7f) }, Alignment.Center) {
                Canvas(Modifier.size(28.dp)) { bpImg(blocks[2], size.width / 2, size.height / 2, size.width * 0.8f) }
            }
        }
        if (m.bannerT > 0f) {
            Text(m.banner, modifier = Modifier.align(Alignment.TopCenter).padding(top = with(androidx.compose.ui.platform.LocalDensity.current) { (bt() + bs() * 0.42f + cell() * 0.25f).toDp() }), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
        }

        if (showAlbum) {
            Box(Modifier.fillMaxSize().background(Color(0xCC102040)).clickable { showAlbum = false; snd("fx_ui_menu_close", 0.7f) }, Alignment.Center) {
                Column(Modifier.padding(12.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFFF2F8FF)).border(5.dp, Color(0xFF6FB6E8), RoundedCornerShape(28.dp)).clickable(enabled = false) {}.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Альбом «Блок-пазл»", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8))
                    Spacer(Modifier.height(8.dp))
                    BP_STICKERS.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { st ->
                                val un = (maskUi and (1 shl st.id)) != 0
                                Column(Modifier.width(100.dp).padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(if (un) Color(0xFFDDEEFF) else Color(0xFFE8EBF0)).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Canvas(Modifier.size(60.dp)) {
                                        val c = Offset(size.width / 2, size.height / 2)
                                        drawCircle(if (un) Color(0xFFFFE9A0) else Color(0x22000000), size.width * 0.46f, c)
                                        val im = blocks[(st.id * 3 + 1) % 8]
                                        bpImg(im, c.x, c.y, size.width * 0.6f, if (un) 1f else 0.9f, if (un) null else ColorFilter.tint(Color(0xFF8A94A4), BlendMode.SrcIn))
                                    }
                                    Text(st.name, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8), textAlign = TextAlign.Center, maxLines = 2)
                                    if (un) Text(st.note, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE0780A), textAlign = TextAlign.Center, maxLines = 3)
                                    else Text(st.how, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7A90), textAlign = TextAlign.Center, maxLines = 3)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Block Puzzle Kit Free by heyheythere — CC BY 4.0", fontSize = 9.sp, color = Color(0xFF8A94A4))
                }
            }
        }
        val rs = reward
        if (rs != null) {
            Box(Modifier.fillMaxSize().background(Color(0xAA102040)).clickable { reward = null; showNext() }, Alignment.Center) {
                Column(Modifier.padding(24.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFFF2F8FF)).border(5.dp, Color(0xFF6FB6E8), RoundedCornerShape(28.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Новая наклейка!", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF3F8FE0))
                    Canvas(Modifier.size(140.dp)) {
                        val c = Offset(size.width / 2, size.height / 2)
                        drawCircle(Color(0xFFFFE9A0), size.width * 0.46f, c); drawCircle(Color.White, size.width * 0.46f, c, style = Stroke(6f))
                        bpImg(blocks[(rs.id * 3 + 1) % 8], c.x, c.y, size.width * 0.6f)
                    }
                    Text(rs.name, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8))
                    Spacer(Modifier.height(6.dp))
                    Text("Подарок:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A7FA8))
                    Text(rs.note, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFE0780A), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF3F8FE0)).padding(horizontal = 28.dp, vertical = 8.dp)) { Text("Играть дальше", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White) }
                }
            }
        }
    }
}
