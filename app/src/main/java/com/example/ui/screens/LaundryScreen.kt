package com.example.ui.screens

import com.example.shared.withFrameNanosEco
import android.content.Context
import android.content.res.Resources
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
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
import kotlinx.coroutines.awaitCancellation
import com.example.shared.LocalAgeProfile
import com.example.shared.SoundManager
import com.example.shared.i18n.Text
import com.example.shared.rememberHaptics
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Комната «Пузырьковая стиралка»
// Этапы: COLLECT (собрать бельё) -> POWDER (порошок) -> TEMP (вода) -> WASH (стирка)
//        -> OPEN («Пум!») -> UNHANG (повесить на верёвку) -> DONE (награда: наклейка альбома «Чистюля»)

private const val ST_COLLECT = 0
private const val ST_POWDER = 1
private const val ST_TEMP = 2
private const val ST_WASH = 3
private const val ST_OPEN = 4
private const val ST_UNHANG = 5
private const val ST_DONE = 6

private const val IS_FLOOR = 0
private const val IS_DRAG = 1
private const val IS_TO_DRUM = 2
private const val IS_DRUM = 3
private const val IS_FLOOR_CLEAN = 4
private const val IS_FROM_DRUM = 5
private const val IS_TO_LINE = 6
private const val IS_HUNG = 7
private const val IS_BACK = 8

private const val LW_PREFS = "laundry_prefs"
private const val SLOTS = 5

private class LwSticker(val id: Int, val name: String, val need: Int)

private val LW_STICKERS = listOf(
    LwSticker(0, "Мыльный пузырь", 1),
    LwSticker(1, "Чистая звёздочка", 2),
    LwSticker(2, "Пушистое облачко", 4),
    LwSticker(3, "Золотая прищепка", 7)
)

private val LW_TEMP_COLORS = listOf(Color(0xFF4AA8FF), Color(0xFFFF6FA5), Color(0xFFFFC93C))

private class LwImgs(private val r: Resources) {
    private fun load(id: Int): ImageBitmap = ImageBitmap.imageResource(r, id)
    val sockDirty = load(R.drawable.wl_sock_dirty)
    val sockClean = load(R.drawable.wl_sock_blue)
    val teeDirty = load(R.drawable.wl_tee_dirty)
    val teeClean = load(R.drawable.wl_tee_blue)
    val bib = load(R.drawable.wl_bib)
    val cloth = load(R.drawable.wl_cloth)
    val dress = load(R.drawable.wl_dress)
    val shorts = load(R.drawable.wl_shorts)
    val cup = load(R.drawable.wl_cup)
    val foam = load(R.drawable.wl_foam)
    val dumpling = load(R.drawable.wl_dumpling)

    fun cleanOf(kind: Int): ImageBitmap = when (kind) {
        0 -> sockClean
        1 -> teeClean
        2 -> bib
        3 -> cloth
        4 -> dress
        else -> shorts
    }

    fun dirtyOf(kind: Int): ImageBitmap? = when (kind) {
        0 -> sockDirty
        1 -> teeDirty
        else -> null
    }
}

private class LwItem(val kind: Int, val idx: Int) {
    var state = IS_FLOOR
    var x = 0f
    var y = 0f
    var rot = 0f
    var homeX = 0f
    var homeY = 0f
    var clean = 0f
    var sparked = false
    var fx0 = 0f
    var fy0 = 0f
    var fx1 = 0f
    var fy1 = 0f
    var ft = 1f
    var fdur = 0.6f
    var farc = 0f
    var landState = IS_FLOOR
    var relDelay = 0f
    var slot = -1
    var dragDX = 0f
    var dragDY = 0f
    val phase = Random.nextFloat() * 6.28f
}

private class LwBub(var x: Float, var y: Float, val r: Float, val vy: Float, val ph: Float, val tint: Color) {
    var alive = true
}

/** kind: 0 пузырёк-осколок, 1 порошинка, 2 звёздочка, 3 конфетти */
private class LwPart(
    val kind: Int, var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val max: Float, val size: Float, val color: Color, var rot: Float = 0f, val vr: Float = 0f
)

private class LwModel {
    var stage = ST_COLLECT
    var stageT = 0f
    var clock = 0f
    var lastTouch = 0f
    var hint = ""
    var items = mutableListOf<LwItem>()
    val bubbles = mutableListOf<LwBub>()
    val parts = mutableListOf<LwPart>()
    val slotTaken = BooleanArray(SLOTS)

    var progress = 0f
    var temp = 1
    var foam = 0f
    var powderAmt = 0f
    var loadT = 0f
    var afterT = 0f
    var bubT = 0f

    // финал: пельмешек танцует и целует
    var nextKissT = 0f
    var nextSparkT = 0f
    var smackDone = false
    var smackT = 0f
    var pendingSticker: LwSticker? = null

    var cupX = 0f
    var cupY = 0f
    var cupTilt = 0f
    var cupDrag = false
    var cupAuto = false
    var cupInit = false
    var pourT = 0f

    var spinning = false
    var lastAng = 0f
    var spinAcc = 0f
    var spinRate = 0f
    var drumRot = 0f
    var drumShake = 0f
    var boomT = 0f
    var released = false
    var popGuard = 0f

    var drag: LwItem? = null
    var downX = 0f
    var downY = 0f
    var lastX = 0f
    var lastY = 0f
    var travel = 0f

    // автодемо
    var demo = false
    var dKind = 0 // 0 перетаскивание, 1 тап, 2 круг
    var dPhase = 0
    var dT = 0f
    var ax = 0f
    var ay = 0f
    var bx = 0f
    var by = 0f
    var h0x = 0f
    var h0y = 0f
    var handX = 0f
    var handY = 0f
    var handDown = false
}

private fun lwLerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun lwEase(t: Float): Float { val k = t.coerceIn(0f, 1f); return k * k * (3f - 2f * k) }

@Composable
fun LaundryScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val sm = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val toddler = LocalAgeProfile.current.isToddler2
    val prefs = remember { context.getSharedPreferences(LW_PREFS, Context.MODE_PRIVATE) }
    val imgs = remember { LwImgs(context.resources) }

    LaunchedEffect(Unit) {
        sm.preloadRaw(
            listOf(
                "lw2_kiss", "wl_drop", "wl_open", "wl_pin", "wl_pop", "wl_pop2", "wl_powder",
                "wl_sparkle", "wl_splash", "wl_wash", "wl_win",
                "lw2_drop", "lw2_open", "lw2_pin", "lw2_powder", "lw2_start", "lw2_stain", "lw2_success", "lw2_reward", "lw2_wash_loop"
            )
        )
    }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(0) }
    val m = remember { LwModel() }

    var washed by remember { mutableIntStateOf(prefs.getInt("washed", 0)) }
    var hintText by remember { mutableStateOf("") }
    var uiStage by remember { mutableIntStateOf(ST_COLLECT) }
    var showAlbum by remember { mutableStateOf(false) }
    var rewardSticker by remember { mutableStateOf<LwSticker?>(null) }
    var demoOn by remember { mutableStateOf(false) }
    var boomVis by remember { mutableStateOf(false) }

    fun W() = boxSize.width.toFloat()
    fun H() = boxSize.height.toFloat()
    fun snd(n: String, v: Float = 1f, r: Float = 1f) { sm.play(n, volume = v, rate = r) }
    fun setHint(s: String) { if (m.hint != s) { m.hint = s; hintText = s } }

    // ── геометрия ──
    // Стиральная машина рисуется кодом (чётко на любом экране); всё её устройство считается от барабана.
    fun drumR() = min(W() * 0.27f, H() * 0.14f)
    fun drumC() = Offset(W() / 2f, H() * 0.53f)
    fun itemSz() = W() * 0.19f
    fun drumItemSz() = drumR() * 0.5f
    fun floorY() = H() * 0.885f
    fun floorSlot(i: Int) = Offset(W() * (0.12f + 0.19f * i), floorY())
    fun lineYAt(x: Float): Float {
        val u = 2f * x / max(1f, W()) - 1f
        return H() * 0.2f + H() * 0.014f * (1f - u * u)
    }
    fun lineSlotX(i: Int) = W() * (0.14f + 0.18f * i)
    fun ballPos(i: Int) = Offset(W() * (0.2f + 0.3f * i), floorY())
    fun ballR() = W() * 0.085f
    fun cupHome() = Offset(W() * 0.5f, floorY())
    fun cupSz() = W() * 0.26f
    fun dumplingPos() = Offset(W() * 0.88f, H() * 0.79f)

    fun drumPos(item: LwItem): Offset {
        val c = drumC()
        val dr = drumR()
        val ang = item.phase + m.drumRot * (if (item.idx % 2 == 0) 1f else -0.8f)
        val rho = dr * (0.28f + 0.12f * (item.idx % 3))
        return Offset(c.x + cos(ang) * rho, c.y + sin(ang) * rho)
    }

    fun curSize(item: LwItem): Float {
        val t = lwEase(item.ft)
        return when (item.state) {
            IS_TO_DRUM -> lwLerp(itemSz(), drumItemSz(), t)
            IS_FROM_DRUM -> lwLerp(drumItemSz(), itemSz(), t)
            IS_TO_LINE -> lwLerp(itemSz(), itemSz() * 0.72f, t)
            IS_HUNG -> itemSz() * 0.72f
            IS_DRUM -> drumItemSz()
            IS_DRAG -> itemSz() * 1.12f
            else -> itemSz()
        }
    }

    fun burst(kind: Int, x: Float, y: Float, n: Int, color: Color, spd: Float, size: Float, up: Float = 0f, life: Float = 0.9f) {
        repeat(n) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val sp = spd * (0.35f + Random.nextFloat() * 0.65f)
            m.parts.add(
                LwPart(
                    kind, x, y, cos(a) * sp, sin(a) * sp - up,
                    life * (0.6f + Random.nextFloat() * 0.6f), life, size * (0.6f + Random.nextFloat() * 0.8f),
                    color, Random.nextFloat() * 6f, (Random.nextFloat() - 0.5f) * 8f
                )
            )
        }
    }

    fun startFlight(item: LwItem, x1: Float, y1: Float, dur: Float, arc: Float, land: Int, state: Int) {
        item.fx0 = item.x; item.fy0 = item.y; item.fx1 = x1; item.fy1 = y1
        item.ft = 0f; item.fdur = dur; item.farc = arc; item.landState = land; item.state = state
    }

    fun setStage(s: Int) {
        m.stage = s
        m.stageT = 0f
        uiStage = s
        if (s == ST_DONE) { m.nextKissT = 0.9f; m.nextSparkT = 0.6f; m.smackDone = false; m.smackT = 0f }
        setHint(
            when (s) {
                ST_COLLECT -> "Брось грязное бельё в барабан"
                ST_POWDER -> if (toddler) "Сыплем порошок!" else "Насыпь порошок: перетащи стаканчик к барабану"
                ST_TEMP -> "Выбери воду: синяя — холодная, розовая — тёплая, жёлтая — горячая"
                ST_WASH -> if (toddler) "Лопай пузыри!" else "Крути пальцем вокруг барабана и лопай пузыри!"
                ST_OPEN -> "Готово!"
                ST_UNHANG -> "Повесь чистое бельё на верёвку"
                else -> "Всё чистое! Нажми, чтобы постирать ещё"
            }
        )
    }

    fun startRound() {
        val kinds = (0..5).shuffled().take(SLOTS)
        m.items = kinds.mapIndexed { i, k ->
            val it0 = LwItem(k, i)
            val p = floorSlot(i)
            it0.homeX = p.x; it0.homeY = p.y; it0.x = p.x; it0.y = p.y
            it0.rot = (Random.nextFloat() - 0.5f) * 14f
            it0
        }.toMutableList()
        m.bubbles.clear()
        for (i in 0 until SLOTS) m.slotTaken[i] = false
        m.progress = 0f; m.foam = 0f; m.powderAmt = 0f; m.loadT = 0f; m.afterT = 0f
        m.cupAuto = false; m.cupDrag = false; m.drag = null; m.spinning = false
        m.released = false; m.boomT = 0f; m.drumShake = 0f
        val h = cupHome(); m.cupX = h.x; m.cupY = h.y; m.cupTilt = 0f; m.cupInit = true
        setStage(ST_COLLECT)
    }

    fun beginWash() {
        m.progress = 0f
        m.bubT = 0f
        m.spinning = false
        setStage(ST_WASH)
        snd("lw2_start", 0.9f)
        snd("wl_splash", 0.6f)
    }

    fun finishWash() {
        setStage(ST_OPEN)
        m.boomT = 1f
        m.released = false
        snd("lw2_open", 1f)
        haptics.vibratePop()
        val c = drumC()
        burst(2, c.x, c.y, 18, Color(0xFFFFFFFF), W() * 0.5f, W() * 0.02f, up = 40f)
    }

    fun nearestFreeSlot(x: Float): Int {
        var best = -1
        var bd = Float.MAX_VALUE
        for (i in 0 until SLOTS) {
            if (m.slotTaken[i]) continue
            val d = abs(lineSlotX(i) - x)
            if (d < bd) { bd = d; best = i }
        }
        return best
    }

    fun hangTo(item: LwItem, slot: Int) {
        m.slotTaken[slot] = true
        item.slot = slot
        val sx = lineSlotX(slot)
        val hangH = itemSz() * 0.72f
        startFlight(item, sx, lineYAt(sx) + hangH * 0.5f, 0.45f, H() * 0.03f, IS_HUNG, IS_TO_LINE)
    }

    fun goHome(item: LwItem) {
        val land = if (item.clean >= 0.99f) IS_FLOOR_CLEAN else IS_FLOOR
        startFlight(item, item.homeX, item.homeY, 0.35f, H() * 0.02f, land, IS_BACK)
    }

    fun sendToDrum(item: LwItem) {
        val c = drumC()
        startFlight(item, c.x + (Random.nextFloat() - 0.5f) * drumR() * 0.3f, c.y, 0.5f, H() * 0.07f, IS_DRUM, IS_TO_DRUM)
        snd("wl_pop2", 0.5f, 1.4f)
    }

    fun cancelGesture() {
        m.drag?.let { goHome(it) }
        m.drag = null
        m.cupDrag = false
        m.spinning = false
        m.handDown = false
    }

    fun popBubble(b: LwBub) {
        b.alive = false
        burst(0, b.x, b.y, 7, Color.White, b.r * 4f, b.r * 0.16f, life = 0.45f)
        burst(0, b.x, b.y, 3, b.tint, b.r * 3f, b.r * 0.14f, life = 0.4f)
        m.progress = min(1f, m.progress + (if (toddler) 0.028f else 0.02f))
        if (m.popGuard <= 0f) {
            snd(if (Random.nextBoolean()) "wl_pop" else "wl_pop2", 0.8f, 0.9f + Random.nextFloat() * 0.5f)
            m.popGuard = 0.05f
        }
        haptics.vibratePop()
    }

    // ── ввод ──
    fun onDown(p: Offset, real: Boolean) {
        if (real) m.lastTouch = m.clock
        m.downX = p.x; m.downY = p.y; m.lastX = p.x; m.lastY = p.y; m.travel = 0f
        when (m.stage) {
            ST_COLLECT, ST_UNHANG -> {
                val want = if (m.stage == ST_COLLECT) IS_FLOOR else IS_FLOOR_CLEAN
                var best: LwItem? = null
                var bd = itemSz() * 0.65f
                for (item in m.items) {
                    if (item.state != want) continue
                    val d = hypot(item.x - p.x, item.y - p.y)
                    if (d < bd) { bd = d; best = item }
                }
                best?.let {
                    it.state = IS_DRAG
                    it.dragDX = it.x - p.x; it.dragDY = it.y - p.y
                    m.drag = it
                    haptics.vibrateShort()
                    snd("wl_pop2", 0.35f, 1.8f)
                }
            }
            ST_POWDER -> {
                if (!toddler && hypot(m.cupX - p.x, m.cupY - p.y) < cupSz() * 0.7f) {
                    m.cupDrag = true
                    m.cupAuto = false
                }
            }
            ST_TEMP -> {
                for (i in 0 until 3) {
                    val b = ballPos(i)
                    if (hypot(b.x - p.x, b.y - p.y) < ballR() * 1.35f) {
                        m.temp = i
                        haptics.vibrateWater()
                        snd("wl_splash", 0.7f, 0.9f + i * 0.15f)
                        burst(2, b.x, b.y, 8, LW_TEMP_COLORS[i], W() * 0.25f, W() * 0.018f, up = 60f)
                        beginWash()
                        break
                    }
                }
            }
            ST_WASH -> {
                var popped = false
                val k = if (toddler) 1.6f else 1.25f
                for (b in m.bubbles) {
                    if (b.alive && hypot(b.x - p.x, b.y - p.y) < b.r * k + W() * 0.02f) { popBubble(b); popped = true; break }
                }
                val c = drumC()
                val d = hypot(p.x - c.x, p.y - c.y)
                if (d < drumR() * 1.8f && d > drumR() * 0.25f) {
                    m.spinning = true
                    m.lastAng = atan2(p.y - c.y, p.x - c.x)
                }
                if (popped) haptics.vibrateShort()
            }
            ST_DONE -> {
                if (real && m.stageT > 2.5f && rewardSticker == null) startRound()
            }
        }
    }

    fun onMove(p: Offset, real: Boolean) {
        if (real) m.lastTouch = m.clock
        m.travel += hypot(p.x - m.lastX, p.y - m.lastY)
        m.lastX = p.x; m.lastY = p.y
        m.drag?.let {
            it.x = p.x + it.dragDX
            it.y = p.y + it.dragDY
        }
        if (m.cupDrag) { m.cupX = p.x; m.cupY = p.y }
        if (m.spinning && m.stage == ST_WASH) {
            val c = drumC()
            val d = hypot(p.x - c.x, p.y - c.y)
            if (d > drumR() * 0.3f) {
                val a = atan2(p.y - c.y, p.x - c.x)
                var da = a - m.lastAng
                while (da > PI.toFloat()) da -= 2f * PI.toFloat()
                while (da < -PI.toFloat()) da += 2f * PI.toFloat()
                val ad = abs(da)
                m.spinAcc += ad
                m.progress = min(1f, m.progress + ad * 0.011f)
                m.lastAng = a
                // пузыри под пальцем лопаются сами
                for (b in m.bubbles) {
                    if (b.alive && hypot(b.x - p.x, b.y - p.y) < b.r + W() * 0.03f) { popBubble(b); break }
                }
            }
        }
    }

    fun onUp(p: Offset, real: Boolean) {
        val tap = m.travel < 14f
        val dragged = m.drag
        if (dragged != null) {
            m.drag = null
            if (m.stage == ST_COLLECT) {
                val c = drumC()
                if (tap || hypot(p.x - c.x, p.y - c.y) < drumR() * 1.25f) sendToDrum(dragged) else goHome(dragged)
            } else if (m.stage == ST_UNHANG) {
                val slot = nearestFreeSlot(if (tap) dragged.homeX else p.x)
                if (slot >= 0 && (tap || p.y < H() * 0.45f)) hangTo(dragged, slot) else goHome(dragged)
            } else {
                goHome(dragged)
            }
        }
        if (m.cupDrag) {
            m.cupDrag = false
            if (tap && m.powderAmt < 1f) m.cupAuto = true
        }
        m.spinning = false
        m.handDown = false
    }

    // ── автодемо ──
    fun stopDemo() {
        if (!m.demo) return
        cancelGesture()
        m.demo = false
        demoOn = false
    }

    fun startDemo() {
        val c = drumC()
        var ok = true
        when (m.stage) {
            ST_COLLECT -> {
                val item = m.items.firstOrNull { it.state == IS_FLOOR }
                if (item == null) ok = false else { m.ax = item.x; m.ay = item.y; m.bx = c.x; m.by = c.y; m.dKind = 0 }
            }
            ST_POWDER -> { m.ax = m.cupX; m.ay = m.cupY; m.dKind = 1; if (toddler || m.cupAuto) ok = false }
            ST_TEMP -> { val b = ballPos(1); m.ax = b.x; m.ay = b.y; m.dKind = 1 }
            ST_WASH -> { m.ax = c.x + drumR() * 0.85f; m.ay = c.y; m.dKind = 2 }
            ST_UNHANG -> {
                val item = m.items.firstOrNull { it.state == IS_FLOOR_CLEAN }
                val slot = nearestFreeSlot(item?.x ?: 0f)
                if (item == null || slot < 0) ok = false else {
                    m.ax = item.x; m.ay = item.y; m.bx = lineSlotX(slot); m.by = lineYAt(lineSlotX(slot)) + H() * 0.05f; m.dKind = 0
                }
            }
            else -> ok = false
        }
        if (!ok) { m.lastTouch = m.clock - 3.5f; return }
        m.demo = true
        demoOn = true
        m.dPhase = 0
        m.dT = 0f
        m.h0x = if (m.handX == 0f) W() * 0.5f else m.handX
        m.h0y = if (m.handY == 0f) H() * 0.6f else m.handY
        m.handDown = false
    }

    fun updateDemo(dt: Float) {
        when (m.dPhase) {
            0 -> {
                m.dT += dt / 0.7f
                val e = lwEase(m.dT)
                m.handX = lwLerp(m.h0x, m.ax, e); m.handY = lwLerp(m.h0y, m.ay, e)
                if (m.dT >= 1f) {
                    m.handX = m.ax; m.handY = m.ay
                    onDown(Offset(m.ax, m.ay), false)
                    m.handDown = true
                    m.dPhase = 1; m.dT = 0f
                }
            }
            1 -> {
                when (m.dKind) {
                    1 -> {
                        m.dT += dt / 0.25f
                        if (m.dT >= 1f) { onUp(Offset(m.ax, m.ay), false); m.dPhase = 2; m.dT = 0f }
                    }
                    0 -> {
                        m.dT += dt / 1.1f
                        val e = lwEase(m.dT)
                        m.handX = lwLerp(m.ax, m.bx, e); m.handY = lwLerp(m.ay, m.by, e)
                        onMove(Offset(m.handX, m.handY), false)
                        if (m.dT >= 1f) { onUp(Offset(m.handX, m.handY), false); m.dPhase = 2; m.dT = 0f }
                    }
                    else -> {
                        m.dT += dt / 3.4f
                        val c = drumC()
                        val ang = m.dT * 4f * PI.toFloat()
                        m.handX = c.x + cos(ang) * drumR() * 0.85f
                        m.handY = c.y + sin(ang) * drumR() * 0.85f
                        onMove(Offset(m.handX, m.handY), false)
                        if (m.dT >= 1f || m.stage != ST_WASH) { onUp(Offset(m.handX, m.handY), false); m.dPhase = 2; m.dT = 0f }
                    }
                }
            }
            else -> {
                m.dT += dt / 0.6f
                if (m.dT >= 1f) {
                    m.demo = false
                    m.handDown = false
                    m.h0x = m.handX; m.h0y = m.handY
                    startDemo()
                }
            }
        }
    }

    // ── цикл обновления ──
    fun update(dt: Float) {
        if (boxSize.width <= 0) return
        m.clock += dt
        m.stageT += dt
        if (m.popGuard > 0f) m.popGuard -= dt
        if (m.items.isEmpty()) startRound()
        val c = drumC()
        val dr = drumR()
        val w = W()
        val h = H()

        // полёты предметов
        for (item in m.items) {
            if (item.relDelay > 0f) {
                item.relDelay -= dt
                if (item.relDelay <= 0f) {
                    val p = drumPos(item)
                    item.x = p.x; item.y = p.y
                    val f = floorSlot(item.idx)
                    startFlight(item, f.x, f.y, 0.7f, h * 0.08f, IS_FLOOR_CLEAN, IS_FROM_DRUM)
                    snd("wl_pop2", 0.5f, 1.2f)
                }
            }
            if (item.state == IS_TO_DRUM || item.state == IS_FROM_DRUM || item.state == IS_TO_LINE || item.state == IS_BACK) {
                item.ft += dt / item.fdur
                val t = lwEase(item.ft.coerceIn(0f, 1f))
                item.x = lwLerp(item.fx0, item.fx1, t)
                item.y = lwLerp(item.fy0, item.fy1, t) - sin(t * PI.toFloat()) * item.farc
                item.rot += dt * 120f * (1f - t)
                if (item.ft >= 1f) {
                    item.ft = 1f
                    item.state = item.landState
                    item.x = item.fx1; item.y = item.fy1
                    when (item.landState) {
                        IS_DRUM -> {
                            snd("lw2_drop", 0.9f)
                            haptics.vibrateWater()
                            m.drumShake = 0.3f
                            burst(0, c.x, c.y, 8, Color(0xFFBFE6FF), w * 0.3f, w * 0.012f, up = 80f, life = 0.6f)
                        }
                        IS_HUNG -> {
                            snd("lw2_pin", 1f)
                            haptics.vibrateShort()
                            burst(2, item.x, lineYAt(item.x), 6, Color(0xFFFFE27A), w * 0.2f, w * 0.015f, life = 0.7f)
                        }
                        IS_FLOOR_CLEAN -> { item.rot = (Random.nextFloat() - 0.5f) * 10f; snd("wl_drop", 0.4f, 1.3f) }
                        else -> { item.rot = (Random.nextFloat() - 0.5f) * 10f }
                    }
                }
            }
        }
        if (m.drumShake > 0f) m.drumShake -= dt
        if (m.boomT > 0f) m.boomT = max(0f, m.boomT - dt * 1.1f)
        val bv = m.boomT > 0.05f
        if (bv != boomVis) boomVis = bv

        when (m.stage) {
            ST_COLLECT -> {
                if (m.items.all { it.state == IS_DRUM }) {
                    m.loadT += dt
                    if (m.loadT > 0.6f) {
                        setStage(ST_POWDER)
                        if (toddler) m.cupAuto = true
                    }
                } else m.loadT = 0f
            }
            ST_POWDER -> {
                val home = cupHome()
                val tgt = Offset(c.x + dr * 0.25f, c.y - dr * 1.45f)
                val k = min(1f, dt * 5f)
                if (m.cupAuto && m.powderAmt < 1f) {
                    m.cupX += (tgt.x - m.cupX) * k; m.cupY += (tgt.y - m.cupY) * k
                } else if (!m.cupDrag) {
                    m.cupX += (home.x - m.cupX) * k; m.cupY += (home.y - m.cupY) * k
                }
                val near = hypot(m.cupX - c.x, m.cupY - c.y) < dr * 1.9f && m.cupY < c.y - dr * 0.5f
                val pouring = m.powderAmt < 1f && (m.cupDrag || m.cupAuto) && near
                m.cupTilt += ((if (pouring) -48f else 0f) - m.cupTilt) * min(1f, dt * 8f)
                if (pouring) {
                    m.powderAmt = min(1f, m.powderAmt + dt * 0.55f)
                    m.pourT -= dt
                    if (m.pourT <= 0f) { snd("lw2_powder", 0.7f); m.pourT = 0.9f }
                    repeat(2) {
                        val lx = m.cupX - cupSz() * 0.35f
                        val ly = m.cupY + cupSz() * 0.1f
                        m.parts.add(
                            LwPart(
                                1, lx + (Random.nextFloat() - 0.5f) * cupSz() * 0.15f, ly,
                                (Random.nextFloat() - 0.5f) * w * 0.06f, h * (0.3f + Random.nextFloat() * 0.2f),
                                0.6f, 0.6f, w * (0.006f + Random.nextFloat() * 0.006f),
                                listOf(Color.White, Color(0xFFBFE6FF), Color(0xFFFFD6E8), Color(0xFFFFF3B0)).random()
                            )
                        )
                    }
                    m.foam = m.powderAmt
                }
                if (m.powderAmt >= 1f) {
                    m.cupAuto = false
                    m.cupDrag = false
                    m.afterT += dt
                    if (m.afterT > 0.7f) {
                        m.afterT = 0f
                        if (toddler) { m.temp = 1; beginWash() } else setStage(ST_TEMP)
                    }
                }
            }
            ST_WASH -> {
                val base = when (m.temp) { 0 -> 0.040f; 1 -> 0.055f; else -> 0.070f } * (if (toddler) 1.5f else 1f)
                m.progress = min(1f, m.progress + base * dt)
                m.spinRate = m.spinRate * 0.9f + (m.spinAcc / max(dt, 0.001f)) * 0.1f
                m.spinAcc = 0f
                m.drumRot += (1.6f + min(10f, m.spinRate * 0.8f)) * dt
                // пузыри
                m.bubT -= dt
                if (m.bubT <= 0f && m.bubbles.size < 26) {
                    m.bubT = (0.16f + Random.nextFloat() * 0.22f) / (0.6f + m.foam * 0.6f)
                    val a = Random.nextFloat() * 2f * PI.toFloat()
                    val rho = sqrt(Random.nextFloat()) * dr * 0.8f
                    val rr = w * (0.024f + Random.nextFloat() * 0.03f) * (if (toddler) 1.4f else 1f)
                    m.bubbles.add(
                        LwBub(
                            c.x + cos(a) * rho, c.y + sin(a) * rho, rr, -h * (0.04f + Random.nextFloat() * 0.05f),
                            Random.nextFloat() * 6.28f, LW_TEMP_COLORS[m.temp]
                        )
                    )
                }
                // чистота вещей
                for (item in m.items) {
                    val target = ((m.progress * 1.15f - 0.05f - item.idx * 0.06f) / 0.5f).coerceIn(0f, 1f)
                    item.clean += (target - item.clean) * min(1f, dt * 6f)
                    if (!item.sparked && item.clean > 0.97f) {
                        item.sparked = true
                        item.clean = 1f
                        val p = drumPos(item)
                        burst(2, p.x, p.y, 9, Color(0xFFFFF3A0), w * 0.35f, w * 0.022f, life = 0.8f)
                        snd("lw2_stain", 0.6f, 0.9f + item.idx * 0.05f)
                    }
                }
                if (m.progress >= 1f) {
                    for (item in m.items) { item.clean = 1f }
                    finishWash()
                }
            }
            ST_OPEN -> {
                m.drumRot += 1.2f * dt
                if (!m.released && m.stageT > 0.55f) {
                    m.released = true
                    for (item in m.items) { item.clean = 1f; item.relDelay = 0.0001f + item.idx * 0.09f }
                }
                if (m.released && m.items.all { it.state == IS_FLOOR_CLEAN }) setStage(ST_UNHANG)
            }
            ST_UNHANG -> {
                if (m.items.all { it.state == IS_HUNG }) {
                    val nw = washed + 1
                    washed = nw
                    prefs.edit().putInt("washed", nw).apply()
                    setStage(ST_DONE)
                    snd("lw2_success", 1f)
                    snd("lw2_reward", 0.7f)
                    burst(3, w * 0.5f, h * 0.3f, 60, Color(0xFFFFC93C), w * 0.8f, w * 0.018f, up = 200f, life = 1.6f)
                    LW_STICKERS.firstOrNull { it.need == nw }?.let { m.pendingSticker = it }
                }
            }
        }

        // финал: пельмешек танцует, шлёт воздушные поцелуи и в конце целует «в экран»
        if (m.stage == ST_DONE) {
            val ft = m.stageT
            val pose = lwFinalePose(ft, w, h, dumplingPos(), m.clock)
            val mouthX = pose[0] - pose[2] * 0.05f
            val mouthY = pose[1] + pose[2] * 0.02f
            if (ft >= m.nextSparkT && ft < 5.4f) {
                m.nextSparkT = ft + 0.18f
                burst(2, pose[0], pose[1] - pose[2] * 0.1f, 2, Color(0xFFFFE066), w * 0.35f, w * 0.014f, up = 40f, life = 0.9f)
            }
            if (ft >= m.nextKissT && ft < 3.3f) {
                m.nextKissT = ft + 0.55f
                for (k in 0 until 2) {
                    m.parts.add(
                        LwPart(
                            4, mouthX, mouthY, (Random.nextFloat() - 0.5f) * w * 0.5f, -h * (0.12f + Random.nextFloat() * 0.1f),
                            1.5f, 1.5f, w * 0.03f, if (k == 0) Color(0xFFFF5C8A) else Color(0xFFFF8FB1),
                            0f, (Random.nextFloat() - 0.5f) * 2f
                        )
                    )
                }
            }
            if (!m.smackDone && ft > 3.5f) {
                m.smackDone = true
                m.smackT = 0f
                sm.playFxRaw("lw2_kiss", volume = 0.9f)
                haptics.vibrateShort()
                burst(4, w * 0.5f, h * 0.33f, 14, Color(0xFFFF5C8A), w * 0.55f, w * 0.035f, up = 120f, life = 1.5f)
                burst(2, w * 0.5f, h * 0.33f, 18, Color(0xFFFFE066), w * 0.7f, w * 0.02f, up = 80f, life = 1.2f)
            }
            if (m.smackDone) m.smackT += dt
        }
        // награда-наклейка выезжает после танца (или сразу, если ребёнок уже начал новую стирку)
        m.pendingSticker?.let { st ->
            if (m.stage != ST_DONE || m.stageT > 5.6f) { rewardSticker = st; m.pendingSticker = null }
        }

        // пузыри: движение
        val bi = m.bubbles.iterator()
        while (bi.hasNext()) {
            val b = bi.next()
            if (!b.alive) { bi.remove(); continue }
            b.y += b.vy * dt
            b.x += sin(m.clock * 2f + b.ph) * w * 0.02f * dt
            if (b.y < c.y - dr * 1.9f || m.stage != ST_WASH) {
                if (m.stage != ST_WASH) { b.alive = false }
                else bi.remove()
            }
        }
        // частицы
        val pi = m.parts.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            p.life -= dt
            if (p.life <= 0f) { pi.remove(); continue }
            when (p.kind) {
                0 -> { p.vy -= h * 0.05f * dt }
                1 -> { p.vy += h * 0.4f * dt }
                4 -> { p.vy -= h * 0.06f * dt; p.vx *= 0.99f }
                else -> { p.vy += h * (if (p.kind == 3) 0.9f else 0.2f) * dt; p.vx *= 0.995f }
            }
            p.x += p.vx * dt; p.y += p.vy * dt; p.rot += p.vr * dt
        }
        if (m.parts.size > 420) repeat(60) { m.parts.removeAt(0) }

        // автодемо
        val overlay = showAlbum || rewardSticker != null
        if (m.demo) {
            if (overlay) stopDemo() else updateDemo(dt)
        } else if (!overlay && m.stage != ST_DONE && m.stage != ST_OPEN && m.clock - m.lastTouch > 5f) {
            startDemo()
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

    // звук стирки: короткий фрагмент повторяется, пока идёт стирка (отменяется при смене этапа/выходе)
    LaunchedEffect(uiStage) {
        if (uiStage == ST_WASH) {
            val h = sm.startLoop("lw2_wash_loop")
            if (h != null) {
                sm.setLoopVolume(h, 0.3f)
                try { awaitCancellation() } finally { sm.stopLoop(h, 300) }
            } else {
                while (true) {
                    snd("wl_wash", 0.45f)
                    delay(1500)
                }
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
                        onDown(down.position, true)
                        var lastPos = down.position
                        var ended = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id }
                            if (ch == null) break
                            lastPos = ch.position
                            if (ch.changedToUp()) { onUp(ch.position, true); ended = true; break }
                            if (ch.positionChanged()) { onMove(ch.position, true); ch.consume() }
                        }
                        if (!ended) onUp(lastPos, true)
                    }
                }
        ) {
            frame.hashCode()
            val w = size.width
            val h = size.height
            if (w <= 0f) return@Canvas
            val t = m.clock
            val c = drumC()
            val dr = drumR()
            val tint = LW_TEMP_COLORS[m.temp.coerceIn(0, 2)]

            // комната и стиральная машина (рисуются кодом)
            lwScene(c, dr, m.stage, m.temp, m.progress, m.drumRot)

            // верёвка
            lwLine(w, h, { x -> lineYAt(x) }, m.stage == ST_UNHANG, t)

            // барабан
            val shake = if (m.drumShake > 0f) sin(t * 70f) * m.drumShake * dr * 0.04f else 0f
            val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x - dr * 0.97f, c.y - dr * 0.97f), Size(dr * 1.94f, dr * 1.94f))) }
            clipPath(circle) {
                if (m.stage >= ST_WASH && m.stage <= ST_OPEN) {
                    drawRect(tint.copy(alpha = 0.28f))
                    drawCircle(Color.White.copy(alpha = 0.10f), dr * 0.7f, Offset(c.x - dr * 0.3f, c.y - dr * 0.4f))
                }
                // пена
                val foamA = (m.foam * (if (m.stage >= ST_WASH && m.stage <= ST_OPEN) 0.95f else 0.6f)).coerceIn(0f, 1f)
                if (foamA > 0.02f && m.stage <= ST_OPEN) {
                    for (i in 0 until 4) {
                        val fx = c.x + cos(i * 1.7f + t * 0.8f) * dr * 0.55f + shake
                        val fy = c.y + dr * 0.45f + sin(i * 2.3f + t * 1.1f) * dr * 0.18f - (i % 2) * dr * 0.25f
                        lwSpr(imgs.foam, fx, fy, dr * 1.0f, 0f, foamA * 0.9f)
                    }
                }
                // бельё в барабане
                for (item in m.items) {
                    if (item.state != IS_DRUM) continue
                    val p = drumPos(item)
                    val ang = (item.phase + m.drumRot * (if (item.idx % 2 == 0) 1f else -0.8f)) * 57.3f
                    lwItem(imgs, item, p.x + shake, p.y, drumItemSz(), ang, 1f)
                }
                // блик стекла
                drawArc(
                    Color.White.copy(alpha = 0.22f), 200f, 70f, false,
                    Offset(c.x - dr * 0.85f, c.y - dr * 0.85f), Size(dr * 1.7f, dr * 1.7f),
                    style = Stroke(dr * 0.07f, cap = StrokeCap.Round)
                )
            }
            if (m.boomT > 0f) {
                drawCircle(Color.White.copy(alpha = m.boomT * 0.7f), dr * (1.0f + (1f - m.boomT) * 0.7f), c)
            }
            // кольцо прогресса
            if (m.stage == ST_WASH) {
                val rr = dr * 1.5f
                drawCircle(Color.White.copy(alpha = 0.35f), rr, c, style = Stroke(w * 0.022f))
                drawArc(
                    tint, -90f, 360f * m.progress, false,
                    Offset(c.x - rr, c.y - rr), Size(rr * 2f, rr * 2f),
                    style = Stroke(w * 0.022f, cap = StrokeCap.Round)
                )
            }

            // бельё: на полу, в полёте, на верёвке
            for (item in m.items) {
                when (item.state) {
                    IS_DRUM -> {}
                    IS_HUNG -> {
                        val swing = sin(t * 2f + item.phase) * 3f
                        lwItem(imgs, item, item.x, item.y, curSize(item), swing, 1f)
                        lwPin(item.x, lineYAt(item.x), w * 0.035f)
                    }
                    else -> {
                        val wob = if (item.state == IS_FLOOR || item.state == IS_FLOOR_CLEAN) sin(t * 2f + item.phase) * 1.5f else 0f
                        val shadowY = item.y + curSize(item) * 0.45f
                        if (item.state == IS_FLOOR || item.state == IS_FLOOR_CLEAN || item.state == IS_DRAG || item.state == IS_BACK) {
                            drawOval(Color(0x22000000), Offset(item.x - curSize(item) * 0.35f, shadowY), Size(curSize(item) * 0.7f, curSize(item) * 0.1f))
                        }
                        lwItem(imgs, item, item.x, item.y, curSize(item), item.rot + wob, 1f)
                    }
                }
            }

            // стаканчик с порошком
            if (m.stage == ST_POWDER) {
                lwSpr(imgs.cup, m.cupX, m.cupY, cupSz(), m.cupTilt)
            }
            // шарики температуры
            if (m.stage == ST_TEMP) {
                for (i in 0 until 3) {
                    val b = ballPos(i)
                    val pulse = 1f + sin(t * 4f + i) * 0.05f
                    lwBall(b, ballR() * pulse, LW_TEMP_COLORS[i], i)
                }
            }

            // пельмешек-помощник
            val dp = dumplingPos()
            if (m.stage == ST_DONE) {
                val pose = lwFinalePose(m.stageT, w, h, dp, t)
                lwSprXY(imgs.dumpling, pose[0], pose[1], pose[2], pose[3], pose[4], pose[5])
                // «губки бантиком» вместо открытой улыбки, пока пельмешек целует
                val puck = lwPucker(m.stageT)
                if (puck > 0.01f) {
                    withTransform({ translate(pose[0], pose[1]); rotate(pose[3], Offset.Zero) }) {
                        lwLips(Offset(-pose[2] * 0.045f, pose[2] * 0.03f), pose[2] * 0.2f * puck, Color(0xFFE8456B))
                    }
                }
            } else {
                val jump = if (m.stage == ST_OPEN) abs(sin(t * 6f)) * h * 0.02f else sin(t * 2f) * h * 0.004f
                lwSpr(imgs.dumpling, dp.x, dp.y - jump, w * 0.2f, sin(t * 1.5f) * 4f)
            }

            // пузыри
            for (b in m.bubbles) {
                if (!b.alive) continue
                val a = ((b.y - (c.y - dr * 1.9f)) / (dr * 0.6f)).coerceIn(0.25f, 1f)
                drawCircle(Color.White.copy(alpha = 0.22f * a), b.r, Offset(b.x, b.y))
                drawCircle(b.tint.copy(alpha = 0.35f * a), b.r, Offset(b.x, b.y), style = Stroke(b.r * 0.12f))
                drawCircle(Color.White.copy(alpha = 0.9f * a), b.r * 0.2f, Offset(b.x - b.r * 0.35f, b.y - b.r * 0.38f))
            }

            // частицы
            for (p in m.parts) {
                val a = (p.life / p.max).coerceIn(0f, 1f)
                when (p.kind) {
                    0 -> drawCircle(p.color.copy(alpha = a), p.size, Offset(p.x, p.y))
                    1 -> drawCircle(p.color.copy(alpha = a), p.size, Offset(p.x, p.y))
                    2 -> lwSparkle(Offset(p.x, p.y), p.size * (0.5f + a), p.color.copy(alpha = a), p.rot)
                    4 -> lwHeart(Offset(p.x, p.y), p.size * (0.6f + (1f - a) * 1.7f), p.color.copy(alpha = (a * 1.4f).coerceIn(0f, 1f)))
                    else -> withTransform({ translate(p.x, p.y); rotate(p.rot * 40f, Offset.Zero) }) {
                        drawRect(p.color.copy(alpha = a), Offset(-p.size * 0.5f, -p.size * 0.25f), Size(p.size, p.size * 0.5f))
                    }
                }
            }

            // большой «чмок» на экране
            if (m.stage == ST_DONE && m.smackDone) {
                val pop = lwEase(m.smackT / 0.16f) * (1f + 0.08f * sin(m.smackT * 9f))
                val fade = (1f - (m.smackT - 1.2f) / 0.9f).coerceIn(0f, 1f)
                if (fade > 0.01f) {
                    val c0 = Offset(w * 0.5f, h * 0.33f)
                    withTransform({ rotate(-12f, c0) }) {
                        lwLips(c0, w * 0.4f * pop, Color(0xFFE8456B).copy(alpha = 0.88f * fade))
                    }
                }
            }

            // рука-подсказка
            if (m.demo) lwHand(Offset(m.handX, m.handY), min(w, h) * 0.04f, m.handDown)
        }

        // «Пум!»
        if (boomVis) {
            Text(
                "Пум!", fontSize = 64.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF6FA5),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 260.dp)
            )
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
            Canvas(Modifier.size(38.dp)) { lwSticker(0, Offset(size.width / 2, size.height / 2), size.width * 0.42f, imgs) }
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
            Text("Стирок: $washed", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E5C8A))
        }
        // подсказка
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 12.dp, start = 12.dp, end = 84.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xE6FFFFFF))
                .border(2.dp, Color(0xFF9CC9F5), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(hintText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E5C8A), textAlign = TextAlign.Center)
        }
        if (demoOn) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 80.dp, start = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE65FA8E8))
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            ) { Text("Просто смотри — потрогай экран, чтобы играть", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White) }
        }

        // альбом «Чистюля»
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
                        .padding(16.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFF2F8FF))
                        .border(5.dp, Color(0xFF6DB3F2), RoundedCornerShape(28.dp))
                        .clickable(enabled = false) {}
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Альбом «Чистюля»", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E5C8A))
                    Text("Стирок: $washed", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3F6FA0))
                    Spacer(Modifier.height(10.dp))
                    LW_STICKERS.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { st ->
                                val unlocked = washed >= st.need
                                Column(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .padding(vertical = 5.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (unlocked) Color(0xFFD9ECFF) else Color(0xFFE6EBF1))
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Canvas(Modifier.size(80.dp)) {
                                        if (unlocked) lwSticker(st.id, Offset(size.width / 2, size.height / 2), size.width * 0.4f, imgs)
                                        else {
                                            drawCircle(Color(0x22000000), size.width * 0.36f, Offset(size.width / 2, size.height / 2))
                                            drawRoundRect(Color(0xFF7A8EA3), Offset(size.width * 0.36f, size.height * 0.46f), Size(size.width * 0.28f, size.height * 0.22f), CornerRadius(6f))
                                            drawArc(Color(0xFF7A8EA3), 180f, 180f, false, Offset(size.width * 0.4f, size.height * 0.3f), Size(size.width * 0.2f, size.height * 0.2f), style = Stroke(5f))
                                        }
                                    }
                                    Text(
                                        if (unlocked) st.name else "Ещё ${st.need - washed}",
                                        fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E5C8A),
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
                    Text("Новая наклейка!", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF3B8EE8))
                    Canvas(Modifier.size(150.dp)) { lwSticker(rs.id, Offset(size.width / 2, size.height / 2), size.width * 0.42f, imgs) }
                    Text(rs.name, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E5C8A))
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF3B8EE8))
                            .padding(horizontal = 28.dp, vertical = 8.dp)
                    ) { Text("Ура!", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White) }
                }
            }
        }
    }
}

// ───────────────────────── рисование ─────────────────────────

/**
 * Поза пельмешка в финале: 0–0.9 с выезжает в центр и растёт, до 3.3 с танцует (прыжки с растяжением,
 * покачивание), 3.3–4.2 с «целует» (наклон к зрителю и пульс), к 5.4 с уезжает домой.
 * Возвращает [x, y, размер, поворот°, scaleX, scaleY].
 */
private fun lwFinalePose(ft: Float, w: Float, h: Float, home: Offset, t: Float): FloatArray {
    val inK = lwEase(ft / 0.9f)
    val outK = lwEase((ft - 5.4f) / 0.9f)
    val k = inK * (1f - outK)
    val dance = lwEase((ft - 0.5f) / 0.5f) * (1f - outK) * (1f - lwPucker(ft))
    val beat = ft * 7.2f
    val s = abs(sin(beat))
    val hop = s * h * 0.05f * dance
    val sway = sin(beat * 0.5f) * w * 0.07f * dance
    val rot = sin(beat * 0.5f) * 13f * dance
    var size = lwLerp(w * 0.2f, w * 0.5f, k)
    val kissPulse = lwPucker(ft)
    size *= 1f + 0.12f * kissPulse
    val x = lwLerp(home.x, w * 0.5f, k) + sway
    val y = lwLerp(home.y, h * 0.46f, k) - hop - kissPulse * h * 0.02f
    val sy = 1f + (s - 0.5f) * 0.16f * dance + 0.04f * sin(t * 2f) * (1f - k)
    val sx = 1f - (s - 0.5f) * 0.12f * dance
    return floatArrayOf(x, y, size, rot - kissPulse * 6f, sx, sy)
}

/** 0..1: насколько пельмешек «сложил губки» (плавный вход и выход вокруг 3.3–4.2 с). */
private fun lwPucker(ft: Float): Float = lwEase((ft - 3.2f) / 0.2f) * (1f - lwEase((ft - 4.0f) / 0.3f))

private fun DrawScope.lwSprXY(img: ImageBitmap, cx: Float, cy: Float, maxDim: Float, rot: Float, sx: Float, sy: Float) {
    val k = maxDim / max(img.width, img.height).toFloat()
    val w = max(1, (img.width * k).toInt())
    val h = max(1, (img.height * k).toInt())
    withTransform({
        translate(cx, cy)
        rotate(rot, Offset.Zero)
        scale(sx, sy, Offset(0f, h / 2f)) // масштаб от «ножек»: прыжок тянет вверх, приземление сплющивает
    }) {
        drawImage(
            img,
            dstOffset = IntOffset(-w / 2, -h / 2),
            dstSize = IntSize(w, h),
            filterQuality = FilterQuality.Medium
        )
    }
}

/** Сердечко с бликом. c — центр, r — «радиус». */
private fun DrawScope.lwHeart(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + r * 0.95f)
        cubicTo(c.x - r * 1.25f, c.y + r * 0.1f, c.x - r * 1.1f, c.y - r * 0.9f, c.x - r * 0.5f, c.y - r * 0.85f)
        cubicTo(c.x - r * 0.15f, c.y - r * 0.85f, c.x, c.y - r * 0.55f, c.x, c.y - r * 0.4f)
        cubicTo(c.x, c.y - r * 0.55f, c.x + r * 0.15f, c.y - r * 0.85f, c.x + r * 0.5f, c.y - r * 0.85f)
        cubicTo(c.x + r * 1.1f, c.y - r * 0.9f, c.x + r * 1.25f, c.y + r * 0.1f, c.x, c.y + r * 0.95f)
        close()
    }
    drawPath(p, color)
    drawCircle(Color.White.copy(alpha = color.alpha * 0.6f), r * 0.14f, Offset(c.x - r * 0.55f, c.y - r * 0.45f))
}

/** Губы-«поцелуйчик»: две дольки верхней губы и нижняя, с бликом. c — центр линии рта, wd — ширина. */
private fun DrawScope.lwLips(c: Offset, wd: Float, color: Color) {
    if (wd <= 1f) return
    val hh = wd * 0.55f
    val upper = Path().apply {
        moveTo(c.x - wd / 2f, c.y)
        cubicTo(c.x - wd * 0.32f, c.y - hh * 0.62f, c.x - wd * 0.08f, c.y - hh * 0.62f, c.x, c.y - hh * 0.28f)
        cubicTo(c.x + wd * 0.08f, c.y - hh * 0.62f, c.x + wd * 0.32f, c.y - hh * 0.62f, c.x + wd / 2f, c.y)
        cubicTo(c.x + wd * 0.25f, c.y + hh * 0.08f, c.x - wd * 0.25f, c.y + hh * 0.08f, c.x - wd / 2f, c.y)
        close()
    }
    val lower = Path().apply {
        moveTo(c.x - wd / 2f, c.y)
        cubicTo(c.x - wd * 0.25f, c.y + hh * 0.08f, c.x + wd * 0.25f, c.y + hh * 0.08f, c.x + wd / 2f, c.y)
        cubicTo(c.x + wd * 0.3f, c.y + hh * 0.62f, c.x - wd * 0.3f, c.y + hh * 0.62f, c.x - wd / 2f, c.y)
        close()
    }
    drawPath(lower, color)
    drawPath(upper, Color(color.red * 0.88f, color.green * 0.88f, color.blue * 0.88f, color.alpha))
    drawOval(Color.White.copy(alpha = color.alpha * 0.4f), Offset(c.x - wd * 0.17f, c.y + hh * 0.14f), Size(wd * 0.34f, hh * 0.13f))
}

private fun DrawScope.lwSpr(img: ImageBitmap, cx: Float, cy: Float, maxDim: Float, rot: Float = 0f, alpha: Float = 1f) {
    val k = maxDim / max(img.width, img.height).toFloat()
    val w = max(1, (img.width * k).toInt())
    val h = max(1, (img.height * k).toInt())
    withTransform({ translate(cx, cy); rotate(rot, Offset.Zero) }) {
        drawImage(
            img,
            dstOffset = IntOffset(-w / 2, -h / 2),
            dstSize = IntSize(w, h),
            alpha = alpha.coerceIn(0f, 1f),
            filterQuality = FilterQuality.Medium
        )
    }
}

private val LW_STAINS = listOf(
    floatArrayOf(-0.12f, -0.05f, 0.13f),
    floatArrayOf(0.10f, 0.10f, 0.10f),
    floatArrayOf(0.02f, -0.18f, 0.08f),
    floatArrayOf(0.14f, -0.08f, 0.06f)
)

private fun DrawScope.lwItem(im: LwImgs, item: LwItem, cx: Float, cy: Float, sz: Float, rot: Float, alpha: Float) {
    val dirt = (1f - item.clean).coerceIn(0f, 1f)
    lwSpr(im.cleanOf(item.kind), cx, cy, sz, rot, alpha)
    val dirty = im.dirtyOf(item.kind)
    if (dirty != null) {
        if (dirt > 0.01f) lwSpr(dirty, cx, cy, sz, rot, alpha * dirt)
    } else if (dirt > 0.01f) {
        withTransform({ translate(cx, cy); rotate(rot, Offset.Zero) }) {
            for (b in LW_STAINS) {
                drawCircle(Color(0xFF8B5A2B).copy(alpha = 0.7f * dirt * alpha), b[2] * sz, Offset(b[0] * sz, b[1] * sz))
            }
        }
    }
}

/** Бельевая верёвка: концы завязаны на два крюка, вкрученных в стену, провисает по параболе lineYAt. */
private fun DrawScope.lwLine(w: Float, h: Float, lineY: (Float) -> Float, glow: Boolean, t: Float) {
    val lx = w * 0.055f
    val rx = w * 0.945f
    val metal = Color(0xFF78909C)
    val metalDark = Color(0xFF455A64)
    for ((i, px) in listOf(lx, rx).withIndex()) {
        val py = lineY(px)
        val dir = if (i == 0) 1f else -1f
        val pr = w * 0.026f
        // тень и пластина крепления на стене
        drawCircle(Color.Black.copy(alpha = 0.15f), pr, Offset(px + w * 0.004f, py + w * 0.006f))
        drawCircle(Color(0xFFCFD8DC), pr, Offset(px, py))
        drawCircle(metal, pr, Offset(px, py), style = Stroke(2.5f))
        // шуруп
        drawCircle(metalDark, pr * 0.28f, Offset(px, py - pr * 0.45f))
        drawLine(Color(0xFFCFD8DC), Offset(px - pr * 0.18f, py - pr * 0.45f), Offset(px + pr * 0.18f, py - pr * 0.45f), 2f)
        // проушина крюка
        drawCircle(metalDark, pr * 0.5f, Offset(px + dir * pr * 0.1f, py + pr * 0.1f), style = Stroke(w * 0.007f))
    }
    val path = Path()
    val steps = 28
    val ex0 = lx + w * 0.004f
    val ex1 = rx - w * 0.004f
    for (i in 0..steps) {
        val x = ex0 + (ex1 - ex0) * i / steps
        val y = lineY(x) + w * 0.0035f
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    if (glow) drawPath(path, Color(0xFFFFF3A0).copy(alpha = 0.35f + 0.2f * sin(t * 4f)), style = Stroke(w * 0.024f, cap = StrokeCap.Round))
    // тень верёвки на стене
    withTransform({ translate(0f, h * 0.006f) }) {
        drawPath(path, Color.Black.copy(alpha = 0.12f), style = Stroke(w * 0.009f, cap = StrokeCap.Round))
    }
    // сама верёвка: светлая основа и тёмная витая «ёлочка»
    drawPath(path, Color(0xFFD9B77C), style = Stroke(w * 0.0095f, cap = StrokeCap.Round))
    drawPath(path, Color(0xFF9C7A45), style = Stroke(w * 0.0095f, cap = StrokeCap.Butt, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(w * 0.006f, w * 0.0075f))))
    // узлы на крюках
    for (kx in listOf(ex0, ex1)) drawCircle(Color(0xFFB08850), w * 0.0085f, Offset(kx, lineY(kx) + w * 0.0035f))
}

/** Вся комната: плиточная стена, пол с ковриком и стиральная машина с дверцей, панелью, дисплеем и ручкой. */
private fun DrawScope.lwScene(c: Offset, dr: Float, stage: Int, temp: Int, progress: Float, rot: Float) {
    val w = size.width
    val h = size.height
    val bw = min(w * 0.88f, dr * 3.2f)
    val x0 = c.x - bw / 2f
    val x1 = c.x + bw / 2f
    val top = c.y - dr * 1.95f
    val bottom = c.y + dr * 1.62f
    val floorTop = bottom + dr * 0.1f
    val ink = Color(0xFF263447)

    // стена: градиент и плитка
    drawRect(Brush.verticalGradient(listOf(Color(0xFF84C3EE), Color(0xFFB4DBF6)), 0f, floorTop), size = Size(w, floorTop))
    val tile = w / 5f
    var j = 0
    while (j * tile < floorTop) {
        for (i in 0..5) {
            val v = (i * 7 + j * 13) % 3
            if (v == 1) drawRect(Color.White.copy(alpha = 0.07f), Offset(i * tile, j * tile), Size(tile, tile))
            else if (v == 2) drawRect(Color(0xFF5AA7E0).copy(alpha = 0.06f), Offset(i * tile, j * tile), Size(tile, tile))
        }
        drawLine(Color.White.copy(alpha = 0.45f), Offset(0f, j * tile), Offset(w, j * tile), 3f)
        j++
    }
    for (i in 0..5) drawLine(Color.White.copy(alpha = 0.45f), Offset(i * tile, 0f), Offset(i * tile, floorTop), 3f)
    // свет из окна: мягкое пятно слева сверху
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), Offset(w * 0.15f, h * 0.12f), w * 0.8f), w * 0.8f, Offset(w * 0.15f, h * 0.12f))

    // пол: плитка «шашечкой» и плинтус
    drawRect(Brush.verticalGradient(listOf(Color(0xFFF2B07C), Color(0xFFE59A62)), floorTop, h), Offset(0f, floorTop), Size(w, h - floorTop))
    val ft = max(24f, (h - floorTop) / 6f)
    var fy = floorTop + ft
    var row = 0
    while (fy < h + ft) {
        drawLine(Color(0xFFC77D45).copy(alpha = 0.45f), Offset(0f, fy), Offset(w, fy), 3f)
        var fx = if (row % 2 == 0) 0f else ft
        while (fx < w) { drawLine(Color(0xFFC77D45).copy(alpha = 0.35f), Offset(fx, fy - ft), Offset(fx, fy), 3f); fx += ft * 2f }
        fy += ft; row++
    }
    drawRect(Color(0xFFEAF1F8), Offset(0f, floorTop - h * 0.012f), Size(w, h * 0.012f))
    drawLine(Color(0xFFB9C7D6), Offset(0f, floorTop - h * 0.012f), Offset(w, floorTop - h * 0.012f), 2.5f)

    // коврик
    val rugY = h * 0.85f
    val rugH = h * 0.1f
    drawRoundRect(Color.Black.copy(alpha = 0.12f), Offset(w * 0.045f, rugY + 8f), Size(w * 0.91f, rugH), CornerRadius(rugH * 0.3f))
    drawRoundRect(Color(0xFF4A90D9), Offset(w * 0.04f, rugY), Size(w * 0.92f, rugH), CornerRadius(rugH * 0.3f))
    drawRoundRect(Color(0xFF7DB6EE), Offset(w * 0.06f, rugY + rugH * 0.12f), Size(w * 0.88f, rugH * 0.76f), CornerRadius(rugH * 0.25f), style = Stroke(rugH * 0.05f))
    for (k in 0 until 9) drawLine(Color.White.copy(alpha = 0.16f), Offset(w * (0.12f + 0.095f * k), rugY + rugH * 0.3f), Offset(w * (0.12f + 0.095f * k) + w * 0.03f, rugY + rugH * 0.7f), rugH * 0.07f, StrokeCap.Round)

    // тень и ножки машины
    drawOval(Color.Black.copy(alpha = 0.22f), Offset(x0 - dr * 0.05f, bottom - dr * 0.02f), Size(bw + dr * 0.1f, dr * 0.2f))
    for (fx in listOf(x0 + dr * 0.18f, x1 - dr * 0.18f - dr * 0.3f)) drawRoundRect(Color(0xFF37474F), Offset(fx, bottom - dr * 0.02f), Size(dr * 0.3f, dr * 0.1f), CornerRadius(dr * 0.03f))

    // корпус
    val cr = CornerRadius(dr * 0.14f)
    drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFF7F9FC), Color(0xFFFFFFFF), Color(0xFFE3E8F0), Color(0xFFC9D2DF)), x0, x1), Offset(x0, top), Size(bw, bottom - top), cr)
    drawRoundRect(Color(0xFF9FAEC2), Offset(x0, top), Size(bw, bottom - top), cr, style = Stroke(3.5f))
    drawLine(Color.White.copy(alpha = 0.7f), Offset(x0 + dr * 0.1f, top + dr * 0.08f), Offset(x1 - dr * 0.1f, top + dr * 0.08f), dr * 0.03f, StrokeCap.Round)

    // верхняя панель
    val ph = dr * 0.5f
    val panelY = top + dr * 0.1f
    drawLine(Color(0xFFB5C0D0), Offset(x0 + 2f, top + dr * 0.5f + dr * 0.1f), Offset(x1 - 2f, top + dr * 0.5f + dr * 0.1f), 3f)
    // лоток для порошка
    val dw = dr * 0.95f
    val dx = x0 + dr * 0.16f
    drawRoundRect(Color(0xFFD5DCE8), Offset(dx, panelY + ph * 0.15f), Size(dw, ph * 0.62f), CornerRadius(dr * 0.05f))
    drawRoundRect(Color(0xFF9FAEC2), Offset(dx, panelY + ph * 0.15f), Size(dw, ph * 0.62f), CornerRadius(dr * 0.05f), style = Stroke(2.5f))
    drawRoundRect(Color(0xFF8FA0B5), Offset(dx + dw * 0.18f, panelY + ph * 0.38f), Size(dw * 0.64f, ph * 0.16f), CornerRadius(dr * 0.04f))
    // ручка выбора температуры: стрелка показывает выбранную
    val kc = Offset(c.x - dr * 0.2f, panelY + ph * 0.46f)
    val kr = dr * 0.2f
    for (q in 0..4) {
        val a = (-150f + q * 60f) * (PI.toFloat() / 180f)
        drawCircle(if (q <= temp + 1 && stage >= ST_WASH) Color(0xFF29B6F6) else Color(0xFF8FA0B5), dr * 0.016f, Offset(kc.x + cos(a) * kr * 1.5f, kc.y + sin(a) * kr * 1.5f))
    }
    drawCircle(Color.Black.copy(alpha = 0.18f), kr, Offset(kc.x + 2f, kc.y + kr * 0.12f))
    drawCircle(Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFAEBBCC)), Offset(kc.x - kr, kc.y - kr), Offset(kc.x + kr, kc.y + kr)), kr, kc)
    drawCircle(Color(0xFF8FA0B5), kr, kc, style = Stroke(2.5f))
    withTransform({ rotate(-90f + temp * 60f + (if (stage == ST_WASH) rot * 4f else 0f), kc) }) {
        drawLine(Color(0xFF455A64), Offset(kc.x + kr * 0.2f, kc.y), Offset(kc.x + kr * 0.85f, kc.y), dr * 0.035f, StrokeCap.Round)
    }
    // дисплей: три огонька температуры и полоса хода стирки
    val sx = c.x + dr * 0.35f
    val sw = x1 - dr * 0.16f - sx
    val sy = panelY + ph * 0.1f
    val sh = ph * 0.72f
    drawRoundRect(ink, Offset(sx, sy), Size(sw, sh), CornerRadius(dr * 0.06f))
    drawRoundRect(Color(0xFF0B1320), Offset(sx + 4f, sy + 4f), Size(sw - 8f, sh - 8f), CornerRadius(dr * 0.05f))
    val tcols = listOf(Color(0xFF4FC3F7), Color(0xFFFFB74D), Color(0xFFFF7043))
    for (q in 0..2) {
        val on = q == temp
        val dc = Offset(sx + sw * (0.2f + 0.3f * q), sy + sh * 0.32f)
        if (on) drawCircle(tcols[q].copy(alpha = 0.3f), dr * 0.08f, dc)
        drawCircle(if (on) tcols[q] else Color(0xFF1F2E44), dr * 0.04f, dc)
    }
    val barX = sx + sw * 0.1f
    val barW = sw * 0.8f
    val barY = sy + sh * 0.68f
    drawRoundRect(Color(0xFF1F2E44), Offset(barX, barY), Size(barW, sh * 0.12f), CornerRadius(sh * 0.06f))
    val fill = when {
        stage == ST_WASH -> progress
        stage > ST_WASH -> 1f
        else -> 0f
    }.coerceIn(0f, 1f)
    if (fill > 0f) drawRoundRect(Color(0xFF69F0AE), Offset(barX, barY), Size(barW * fill, sh * 0.12f), CornerRadius(sh * 0.06f))
    // кнопка пуск
    drawCircle(Color(0xFF9FAEC2), dr * 0.055f, Offset(x1 - dr * 0.1f, panelY + ph * 0.46f), style = Stroke(2.5f))

    // дверца: хромированное кольцо, резиновая манжета, стекло
    val dc = c
    drawCircle(Color.Black.copy(alpha = 0.18f), dr * 1.4f, Offset(dc.x + dr * 0.03f, dc.y + dr * 0.05f))
    drawCircle(Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFA9B4C4), Color(0xFFF2F5F9), Color(0xFF7D8A9C)), Offset(dc.x - dr * 1.4f, dc.y - dr * 1.4f), Offset(dc.x + dr * 1.4f, dc.y + dr * 1.4f)), dr * 1.4f, dc)
    drawCircle(Color(0xFF6C7A8E), dr * 1.4f, dc, style = Stroke(3.5f))
    drawCircle(Brush.linearGradient(listOf(Color(0xFF8795A8), Color(0xFFE8EDF4)), Offset(dc.x - dr, dc.y - dr), Offset(dc.x + dr, dc.y + dr)), dr * 1.2f, dc)
    drawCircle(Color(0xFF3A4658), dr * 1.1f, dc)
    drawCircle(Color(0xFF55637A), dr * 1.1f, dc, style = Stroke(dr * 0.03f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFF3F6A9C), Color(0xFF1B3556), Color(0xFF0F2038)), Offset(dc.x - dr * 0.25f, dc.y - dr * 0.3f), dr * 1.1f), dr * 1.0f, dc)
    // барабан за стеклом: вращающиеся отверстия
    for (ring in 0..1) {
        val rr = dr * (0.84f - 0.26f * ring)
        val n = 22 - ring * 8
        for (k in 0 until n) {
            val a = rot * (if (ring == 0) 0.5f else -0.4f) + k * 2f * PI.toFloat() / n
            drawCircle(Color(0xFF0A1626).copy(alpha = 0.55f), dr * 0.03f, Offset(dc.x + cos(a) * rr, dc.y + sin(a) * rr))
        }
    }
    // три ребра-лопасти барабана
    for (k in 0 until 3) {
        val a = rot * 0.5f + k * 2.0944f
        drawLine(Color(0xFF7FA3CC).copy(alpha = 0.25f), Offset(dc.x + cos(a) * dr * 0.2f, dc.y + sin(a) * dr * 0.2f), Offset(dc.x + cos(a) * dr * 0.95f, dc.y + sin(a) * dr * 0.95f), dr * 0.05f, StrokeCap.Round)
    }
    // петли дверцы слева и замок справа
    for (hy in listOf(-0.45f, 0.45f)) drawRoundRect(Color(0xFF8795A8), Offset(dc.x - dr * 1.52f, dc.y + dr * hy - dr * 0.1f), Size(dr * 0.16f, dr * 0.2f), CornerRadius(dr * 0.04f))
    drawRoundRect(Color(0xFF8795A8), Offset(dc.x + dr * 1.36f, dc.y - dr * 0.12f), Size(dr * 0.14f, dr * 0.24f), CornerRadius(dr * 0.04f))

    // нижняя панель и лючок фильтра
    val lineY = bottom - dr * 0.3f
    drawLine(Color(0xFFB5C0D0), Offset(x0 + 2f, lineY), Offset(x1 - 2f, lineY), 3f)
    drawRoundRect(Color(0xFFD5DCE8), Offset(x1 - dr * 1.0f, lineY + dr * 0.06f), Size(dr * 0.8f, dr * 0.2f), CornerRadius(dr * 0.04f))
    drawRoundRect(Color(0xFF9FAEC2), Offset(x1 - dr * 1.0f, lineY + dr * 0.06f), Size(dr * 0.8f, dr * 0.2f), CornerRadius(dr * 0.04f), style = Stroke(2.5f))
}

private fun DrawScope.lwPin(x: Float, y: Float, s: Float) {
    drawRoundRect(Color(0xFFE0A24A), Offset(x - s * 0.3f, y - s * 0.5f), Size(s * 0.6f, s * 1.3f), CornerRadius(s * 0.2f))
    drawRoundRect(Color(0xFF9A6A3A), Offset(x - s * 0.3f, y - s * 0.5f), Size(s * 0.6f, s * 1.3f), CornerRadius(s * 0.2f), style = Stroke(2f))
    drawLine(Color(0xFF9A6A3A), Offset(x, y + s * 0.1f), Offset(x, y + s * 0.8f), 2f)
}

private fun DrawScope.lwBall(c: Offset, r: Float, color: Color, kind: Int) {
    drawCircle(Color(0x33000000), r, Offset(c.x, c.y + r * 0.12f))
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), color, color.copy(alpha = 0.85f)), Offset(c.x - r * 0.3f, c.y - r * 0.35f), r * 1.4f),
        r, c
    )
    drawCircle(Color.White, r, c, style = Stroke(r * 0.1f))
    val ink = Color.White
    when (kind) {
        0 -> for (i in 0 until 3) {
            val a = i * PI.toFloat() / 3f
            drawLine(ink, Offset(c.x - cos(a) * r * 0.5f, c.y - sin(a) * r * 0.5f), Offset(c.x + cos(a) * r * 0.5f, c.y + sin(a) * r * 0.5f), r * 0.1f, StrokeCap.Round)
        }
        else -> for (j in 0 until kind) {
            val p = Path()
            val oy = c.y + (j - (kind - 1) * 0.5f) * r * 0.45f
            p.moveTo(c.x - r * 0.45f, oy)
            for (k in 1..6) p.lineTo(c.x - r * 0.45f + r * 0.15f * k, oy + (if (k % 2 == 0) 0f else -r * 0.12f))
            drawPath(p, ink, style = Stroke(r * 0.1f, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.lwSparkle(c: Offset, r: Float, color: Color, rot: Float) {
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

private fun DrawScope.lwHand(c: Offset, r: Float, down: Boolean) {
    val k = if (down) 0.85f else 1f
    drawCircle(Color.White.copy(alpha = 0.35f), r * 1.7f * k, c)
    drawCircle(Color.White.copy(alpha = 0.9f), r * k, c)
    drawCircle(Color(0xFF2E5C8A), r * k, c, style = Stroke(3f))
}

private fun DrawScope.lwSticker(id: Int, c: Offset, r: Float, im: LwImgs) {
    when (id) {
        0 -> {
            drawCircle(Color(0xFFBFE6FF), r, c)
            drawCircle(Color.White, r, c, style = Stroke(r * 0.1f))
            drawCircle(Color.White.copy(alpha = 0.9f), r * 0.2f, Offset(c.x - r * 0.4f, c.y - r * 0.42f))
            drawCircle(Color(0xFF7FC8FF), r * 0.55f, Offset(c.x + r * 0.1f, c.y + r * 0.15f), style = Stroke(r * 0.08f))
        }
        1 -> {
            val p = Path()
            for (i in 0 until 10) {
                val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
                val rr = if (i % 2 == 0) r else r * 0.45f
                val x = c.x + cos(a) * rr
                val y = c.y + sin(a) * rr
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            drawPath(p, Color(0xFFFFD54A))
            drawPath(p, Color(0xFFE8A325), style = Stroke(r * 0.08f))
        }
        2 -> {
            drawCircle(Color(0xFFE6F3FF), r * 0.95f, c)
            lwSpr(im.foam, c.x, c.y, r * 1.8f)
        }
        else -> {
            drawRoundRect(Color(0xFFFFC93C), Offset(c.x - r * 0.35f, c.y - r), Size(r * 0.7f, r * 2f), CornerRadius(r * 0.25f))
            drawRoundRect(Color(0xFFB8860B), Offset(c.x - r * 0.35f, c.y - r), Size(r * 0.7f, r * 2f), CornerRadius(r * 0.25f), style = Stroke(r * 0.07f))
            drawLine(Color(0xFFB8860B), Offset(c.x, c.y - r * 0.1f), Offset(c.x, c.y + r * 0.9f), r * 0.07f)
            lwSparkle(Offset(c.x + r * 0.7f, c.y - r * 0.6f), r * 0.3f, Color(0xFFFFF3A0), 0f)
        }
    }
}
