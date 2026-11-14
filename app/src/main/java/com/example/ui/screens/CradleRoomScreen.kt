package com.example.ui.screens

import com.example.shared.withFrameNanosEco
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
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
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Комната «Музыкальная колыбелька».
// Стадии: AWAKE_PLAYING -> FALLING_ASLEEP -> FAST_ASLEEP. Награды: пижамка (ночной колпачок) и наклейка «Сонный ангелочек».

private const val CR_AWAKE = 0
private const val CR_FALLING = 1
private const val CR_ASLEEP = 2

private const val CR_PREFS = "cradle_prefs"
private const val CR_MAX_ANGLE = 15f
private const val CR_MAX_SPEED = 90f

private val CR_LAMP_COLORS = listOf(Color(0xFFFFD98A), Color(0xFF9DCFFF), Color(0xFFFFB9D4))
private val CR_CAPS = listOf(Color(0xFFFF8FB1), Color(0xFF7FC8FF), Color(0xFF8EDC9D), Color(0xFFB79CFF), Color(0xFFFFB36B))
private val CR_STICKER_NEED = listOf(1, 2, 4, 7)
private val CR_STICKER_NAMES = listOf("Первый сон", "Сладкие грёзы", "Лунный светлячок", "Сонный ангелочек")

private const val IT_BLANKET = 0
private const val IT_PACI = 1
private const val IT_PLUSH = 2
private const val IT_DUST = 3

private class CrItem(val kind: Int) {
    var x = 0f
    var y = 0f
    var used = false
    var dragging = false
}

/** kind: 0 сердечко, 1 звёздочка, 2 буква Z, 3 пылинка */
private class CrPart(
    val kind: Int, var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val max: Float, val size: Float, val color: Color, var rot: Float = 0f
)

private class CrStep(val kind: Int, val ax: Float, val ay: Float, val bx: Float, val by: Float, val dur: Float)

private class CrModel {
    var clock = 0f
    var lastTouch = 0f
    var stage = CR_AWAKE
    var stageT = 0f
    var hint = ""
    var character = 0
    var progress = 0f
    var angle = 0f
    var vel = 0f
    var lamp = 1 // 0 выкл, 1 жёлтый, 2 синий, 3 розовый
    var covered = false
    var paci = false
    var plush = false
    var dustGlow = 0f
    var happyT = 0f
    var yawn = 0f
    var yawnT = 3f
    var blinkT = 0f
    var nextBlink = 2.5f
    var mobAng = 0f
    var mobVel = 0.3f
    var lastCreak = -5f
    var lastChime = -5f
    var lastHeart = -5f
    var lastDir = 0
    var toddlerPush = 0f
    var accel = 0f
    var paused = false
    var musicVol = 0f
    var snored = false
    var rewardShown = false
    val items = listOf(CrItem(IT_BLANKET), CrItem(IT_PACI), CrItem(IT_PLUSH), CrItem(IT_DUST))
    val parts = mutableListOf<CrPart>()

    // жест
    var mode = 0 // 0 нет, 1 предмет, 2 гладим, 3 качаем
    var cur: CrItem? = null
    var travel = 0f
    var petAcc = 0f
    var lastX = 0f
    var lastY = 0f

    // демо
    var demo = false
    var steps = listOf<CrStep>()
    var dIdx = 0
    var dPhase = 0
    var dT = 0f
    var handX = 0f
    var handY = 0f
    var h0x = 0f
    var h0y = 0f
    var handDown = false
}

private class CrMusic(private val ctx: Context) {
    private var mp: MediaPlayer? = null

    fun apply(vol: Float) {
        try {
            if (vol > 0.01f) {
                if (mp == null) {
                    mp = MediaPlayer.create(ctx, R.raw.cr_lullaby)?.also { it.isLooping = true }
                }
                val p = mp ?: return
                p.setVolume(vol, vol)
                if (!p.isPlaying) p.start()
            } else {
                val p = mp
                if (p != null && p.isPlaying) p.pause()
            }
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            mp?.release()
        } catch (_: Exception) {
        }
        mp = null
    }
}

private fun crRot(p: Offset, pivot: Offset, deg: Float): Offset {
    val r = deg * PI.toFloat() / 180f
    val dx = p.x - pivot.x
    val dy = p.y - pivot.y
    return Offset(pivot.x + dx * cos(r) - dy * sin(r), pivot.y + dx * sin(r) + dy * cos(r))
}

private fun crLerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun crEase(t: Float): Float { val k = t.coerceIn(0f, 1f); return k * k * (3f - 2f * k) }

@Composable
fun CradleRoomScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val sm = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val toddler = LocalAgeProfile.current.isToddler2
    val prefs = remember { context.getSharedPreferences(CR_PREFS, Context.MODE_PRIVATE) }
    val music = remember { CrMusic(context.applicationContext) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        sm.preloadRaw(listOf("cr_blanket", "cr_creak", "cr_chimes", "cr_snore", "cr_chord", "cr_heart", "cr_pick", "cr_thump", "cr_dust"))
    }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(0) }
    val m = remember { CrModel() }

    var sleeps by remember { mutableIntStateOf(prefs.getInt("sleeps", 0)) }
    var musicOn by remember { mutableStateOf(prefs.getBoolean("music", true)) }
    var hintText by remember { mutableStateOf("") }
    var showAlbum by remember { mutableStateOf(false) }
    var rewardIdx by remember { mutableIntStateOf(-1) }
    var demoOn by remember { mutableStateOf(false) }
    var asleepUi by remember { mutableStateOf(false) }
    val musicOnState = rememberUpdatedState(musicOn)

    fun W() = boxSize.width.toFloat()
    fun H() = boxSize.height.toFloat()
    fun snd(n: String, v: Float = 1f, r: Float = 1f) { sm.play(n, volume = v, rate = r) }
    fun setHint(s: String) { if (m.hint != s) { m.hint = s; hintText = s } }

    // ── геометрия ──
    fun cradleC() = Offset(W() * 0.5f, H() * 0.52f)
    fun pivot() = Offset(W() * 0.5f, H() * 0.67f)
    fun charLocal() = Offset(W() * 0.5f, H() * 0.50f)
    fun charS() = W() * 0.15f
    fun headWorld(): Offset = crRot(Offset(charLocal().x, charLocal().y - charS() * 0.1f), pivot(), m.angle)
    fun lampPos() = Offset(W() * 0.13f, H() * 0.585f)
    fun mobilePos() = Offset(W() * 0.5f, H() * 0.2f)
    fun itemHome(i: Int) = Offset(W() * (0.13f + 0.248f * i), H() * 0.9f)
    fun itemR() = W() * 0.085f

    // ── эффекты ──
    fun burst(kind: Int, x: Float, y: Float, n: Int, color: Color, spd: Float, size: Float, up: Float = 0f, life: Float = 1f) {
        repeat(n) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val sp = spd * (0.3f + Random.nextFloat() * 0.7f)
            m.parts.add(
                CrPart(kind, x, y, cos(a) * sp, sin(a) * sp - up, life * (0.6f + Random.nextFloat() * 0.6f), life,
                    size * (0.7f + Random.nextFloat() * 0.6f), color, Random.nextFloat() * 6f)
            )
        }
    }

    fun setStage(s: Int) {
        m.stage = s
        m.stageT = 0f
        asleepUi = s == CR_ASLEEP
        setHint(
            when (s) {
                CR_AWAKE -> if (toddler) "Накрой малыша одеяльцем" else "Накрой малыша одеяльцем и покачай кроватку"
                CR_FALLING -> if (toddler) "Качай кроватку и гладь малыша" else "Води пальцем по кроватке, гладь лобик, звени мобилем"
                else -> "Тсс… малыш спит. Сладких снов!"
            }
        )
    }

    fun startRound() {
        m.character = sleeps % 3
        m.progress = 0f; m.angle = 0f; m.vel = 0f
        m.covered = false; m.paci = false; m.plush = false
        m.snored = false; m.rewardShown = false; m.musicVol = 0f
        m.dustGlow = 0f; m.happyT = 0f; m.yawn = 0f
        m.parts.clear()
        for (i in m.items.indices) {
            val it0 = m.items[i]
            val h = itemHome(i)
            it0.x = h.x; it0.y = h.y; it0.used = false; it0.dragging = false
        }
        m.mode = 0; m.cur = null
        setStage(CR_AWAKE)
    }

    fun addProgress(a: Float) {
        if (m.stage == CR_ASLEEP) return
        var np = m.progress + a
        if (m.demo) np = min(np, 0.8f)
        m.progress = np.coerceIn(0f, 1f)
        if (m.progress > 0.02f && m.stage == CR_AWAKE) setStage(CR_FALLING)
    }

    fun fallAsleep() {
        m.progress = 1f
        m.vel *= 0.3f
        setStage(CR_ASLEEP)
        m.snored = false
        snd("cr_chord", 0.8f)
        haptics.vibratePop()
        val ns = sleeps + 1
        sleeps = ns
        prefs.edit().putInt("sleeps", ns).apply()
        val idx = CR_STICKER_NEED.indexOf(ns)
        if (idx >= 0) rewardIdx = idx
        val h = headWorld()
        burst(1, h.x, h.y, 14, Color(0xFFFFF3A0), W() * 0.3f, W() * 0.02f, up = 60f, life = 1.2f)
    }

    // ── ввод ──
    fun applyItem(item: CrItem) {
        item.used = true
        val h = headWorld()
        when (item.kind) {
            IT_BLANKET -> {
                m.covered = true
                snd("cr_blanket", 0.9f)
                haptics.vibrateShort()
                addProgress(0.15f)
                m.happyT = 2.5f
                burst(0, h.x, h.y, 4, Color(0xFFFFB9D4), W() * 0.2f, W() * 0.03f, up = 120f)
            }
            IT_PACI -> {
                m.paci = true
                snd("cr_thump", 0.6f, 1.6f)
                haptics.vibrateShort()
                addProgress(0.10f)
            }
            IT_PLUSH -> {
                m.plush = true
                snd("cr_thump", 0.7f)
                haptics.vibrateShort()
                addProgress(0.10f)
            }
            else -> {
                snd("cr_dust", 0.8f)
                m.dustGlow = 1.6f
                addProgress(0.12f)
                burst(1, h.x, h.y - charS(), 26, Color(0xFFFFF3A0), W() * 0.35f, W() * 0.017f, up = 40f, life = 1.3f)
            }
        }
    }

    fun onDown(p: Offset, real: Boolean) {
        if (real) m.lastTouch = m.clock
        m.travel = 0f; m.lastX = p.x; m.lastY = p.y; m.petAcc = 0f
        m.mode = 0; m.cur = null
        // предметы
        if (m.stage != CR_ASLEEP) {
            for (it0 in m.items) {
                if (it0.used) continue
                if (hypot(it0.x - p.x, it0.y - p.y) < itemR() * 1.3f) {
                    it0.dragging = true
                    m.cur = it0
                    m.mode = 1
                    snd("cr_pick", 0.6f)
                    haptics.vibrateShort()
                    return
                }
            }
        }
        // ночник
        val lp = lampPos()
        if (hypot(p.x - lp.x, p.y - lp.y) < W() * 0.13f) {
            m.lamp = (m.lamp + 1) % 4
            snd("cr_pick", 0.7f, 0.8f)
            haptics.vibrateShort()
            if (m.lamp > 0) burst(1, lp.x, lp.y, 5, CR_LAMP_COLORS[m.lamp - 1], W() * 0.15f, W() * 0.012f, life = 0.8f)
            return
        }
        // мобиль
        val mp = mobilePos()
        if (hypot(p.x - mp.x, p.y - mp.y) < W() * 0.3f && p.y < H() * 0.38f) {
            m.mobVel = min(6f, m.mobVel + 2.2f)
            if (m.clock - m.lastChime > 0.5f) { snd("cr_chimes", 0.8f); m.lastChime = m.clock }
            haptics.vibrateShort()
            if (m.stage != CR_ASLEEP) addProgress(0.03f)
            return
        }
        // голова — гладим
        val hw = headWorld()
        if (m.stage != CR_ASLEEP && hypot(p.x - hw.x, p.y - hw.y) < charS() * 1.1f) {
            m.mode = 2
            return
        }
        // кроватка — качаем
        if (abs(p.x - cradleC().x) < W() * 0.36f && p.y > H() * 0.36f && p.y < H() * 0.80f) {
            m.mode = 3
        }
    }

    fun onMove(p: Offset, real: Boolean) {
        if (real) m.lastTouch = m.clock
        val dx = p.x - m.lastX
        val dy = p.y - m.lastY
        val d = hypot(dx, dy)
        m.travel += d
        m.lastX = p.x; m.lastY = p.y
        when (m.mode) {
            1 -> m.cur?.let { it.x = p.x; it.y = p.y }
            2 -> {
                m.petAcc += d
                val step = W() * 0.07f
                while (m.petAcc > step) {
                    m.petAcc -= step
                    val h = headWorld()
                    burst(0, h.x + (Random.nextFloat() - 0.5f) * charS(), h.y - charS() * 0.6f, 1, Color(0xFFFF8FB1), W() * 0.05f, W() * 0.032f, up = 150f, life = 1.2f)
                    addProgress(if (toddler) 0.022f else 0.015f)
                    m.happyT = 1.2f
                    if (m.clock - m.lastHeart > 0.35f) { snd("cr_heart", 0.6f, 0.9f + Random.nextFloat() * 0.3f); m.lastHeart = m.clock }
                    haptics.vibrateShort()
                }
            }
            3 -> {
                if (m.stage != CR_ASLEEP) {
                    m.vel = (m.vel + dx / max(1f, W()) * 320f).coerceIn(-CR_MAX_SPEED, CR_MAX_SPEED)
                }
            }
        }
    }

    fun onUp(p: Offset, real: Boolean) {
        val tap = m.travel < 14f
        val it0 = m.cur
        if (m.mode == 1 && it0 != null) {
            it0.dragging = false
            val hw = headWorld()
            if (hypot(p.x - hw.x, p.y - hw.y) < charS() * 1.9f) applyItem(it0)
        }
        if (m.mode == 3 && tap && m.stage != CR_ASLEEP) {
            val dir = if (m.vel >= 0f) -1f else 1f
            m.vel = (m.vel + dir * 34f).coerceIn(-CR_MAX_SPEED, CR_MAX_SPEED)
            haptics.vibrateShort()
        }
        m.mode = 0; m.cur = null
        m.handDown = false
    }

    // ── автодемо ──
    fun cancelGesture() {
        m.items.forEach { it.dragging = false }
        m.mode = 0; m.cur = null; m.handDown = false
    }

    fun stopDemo() {
        if (!m.demo) return
        cancelGesture()
        m.demo = false
        demoOn = false
    }

    fun startDemo() {
        if (m.stage == CR_ASLEEP) { m.lastTouch = m.clock; return }
        val list = mutableListOf<CrStep>()
        val hw = headWorld()
        if (!m.covered) {
            val h = itemHome(IT_BLANKET)
            list.add(CrStep(0, h.x, h.y, hw.x, hw.y, 1.1f))
        }
        if (m.lamp == 0) {
            val lp = lampPos()
            list.add(CrStep(1, lp.x, lp.y, lp.x, lp.y, 0.25f))
        }
        val c = cradleC()
        list.add(CrStep(2, c.x, H() * 0.6f, c.x, H() * 0.6f, 4.5f))
        list.add(CrStep(3, hw.x, hw.y, hw.x, hw.y, 2.5f))
        m.steps = list
        m.dIdx = 0; m.dPhase = 0; m.dT = 0f
        m.h0x = if (m.handX == 0f) W() * 0.5f else m.handX
        m.h0y = if (m.handY == 0f) H() * 0.7f else m.handY
        m.demo = true
        demoOn = true
        m.handDown = false
    }

    fun updateDemo(dt: Float) {
        if (m.dIdx >= m.steps.size) {
            m.demo = false; demoOn = false; m.handDown = false
            m.lastTouch = m.clock - 2f
            return
        }
        val st = m.steps[m.dIdx]
        when (m.dPhase) {
            0 -> {
                m.dT += dt / 0.7f
                val e = crEase(m.dT)
                m.handX = crLerp(m.h0x, st.ax, e); m.handY = crLerp(m.h0y, st.ay, e)
                if (m.dT >= 1f) {
                    m.handX = st.ax; m.handY = st.ay
                    onDown(Offset(st.ax, st.ay), false)
                    m.handDown = true
                    m.dPhase = 1; m.dT = 0f
                }
            }
            1 -> {
                m.dT += dt / st.dur
                val e = crEase(m.dT)
                when (st.kind) {
                    0 -> { m.handX = crLerp(st.ax, st.bx, e); m.handY = crLerp(st.ay, st.by, e) }
                    2 -> { m.handX = st.ax + sin(m.dT * PI.toFloat() * 7f) * W() * 0.2f; m.handY = st.ay }
                    3 -> { m.handX = st.ax + sin(m.dT * PI.toFloat() * 8f) * charS() * 0.7f; m.handY = st.ay - charS() * 0.2f }
                    else -> {}
                }
                if (st.kind != 1) onMove(Offset(m.handX, m.handY), false)
                if (m.dT >= 1f) {
                    onUp(Offset(m.handX, m.handY), false)
                    m.h0x = m.handX; m.h0y = m.handY
                    m.dPhase = 2; m.dT = 0f
                }
            }
            else -> {
                m.dT += dt / 0.4f
                if (m.dT >= 1f) { m.dIdx++; m.dPhase = 0; m.dT = 0f }
            }
        }
    }

    // ── обновление ──
    fun update(dt: Float) {
        if (boxSize.width <= 0) return
        if (m.stageT == 0f && m.clock == 0f) {
            m.character = sleeps % 3
            for (i in m.items.indices) { val h = itemHome(i); m.items[i].x = h.x; m.items[i].y = h.y }
            setStage(CR_AWAKE)
        }
        m.clock += dt
        m.stageT += dt
        val asleep = m.stage == CR_ASLEEP

        // предметы возвращаются на место
        for (i in m.items.indices) {
            val it0 = m.items[i]
            if (it0.dragging) continue
            val h = itemHome(i)
            val k = min(1f, dt * 10f)
            it0.x += (h.x - it0.x) * k
            it0.y += (h.y - it0.y) * k
        }

        // датчик: лёгкий импульс, мёртвая зона, без прямой записи в угол
        if (!asleep && !m.paused && abs(m.accel) > 1.5f) {
            m.vel = (m.vel + (-m.accel).coerceIn(-8f, 8f) * 6f * dt).coerceIn(-CR_MAX_SPEED, CR_MAX_SPEED)
        }
        // самопокачивание для малышей
        if (toddler && m.covered && !asleep && abs(m.vel) < 6f) {
            m.toddlerPush -= dt
            if (m.toddlerPush <= 0f) {
                m.toddlerPush = 2.5f
                m.vel += (if (m.angle >= 0f) -1f else 1f) * 18f
            }
        }

        // физика маятника
        if (asleep) {
            m.vel *= (1f - min(1f, dt * 4f))
            m.angle *= (1f - min(1f, dt * 3f))
            if (abs(m.angle) < 0.02f) m.angle = 0f
        } else {
            val d = dt.coerceIn(0f, 1f / 30f)
            val rad = m.angle * PI.toFloat() / 180f
            val acc = -(9.81f / 0.72f) * sin(rad) * (180f / PI.toFloat()) - 1.2f * m.vel
            var nv = m.vel + acc * d
            var na = m.angle + nv * d
            if (na > CR_MAX_ANGLE) { na = CR_MAX_ANGLE; nv = -abs(nv) * 0.30f }
            else if (na < -CR_MAX_ANGLE) { na = -CR_MAX_ANGLE; nv = abs(nv) * 0.30f }
            if (abs(na) < 0.025f && abs(nv) < 0.12f) { na = 0f; nv = 0f }
            // скрип на смене направления
            val dir = if (nv > 1f) 1 else if (nv < -1f) -1 else 0
            if (dir != 0 && m.lastDir != 0 && dir != m.lastDir && abs(na) > 4f && m.clock - m.lastCreak > 1.0f) {
                snd("cr_creak", 0.55f)
                m.lastCreak = m.clock
            }
            if (dir != 0) m.lastDir = dir
            m.vel = nv; m.angle = na
        }

        // мобиль
        m.mobAng += m.mobVel * dt
        m.mobVel += (0.3f - m.mobVel) * min(1f, dt * 0.6f)

        // мимика
        if (m.happyT > 0f) m.happyT -= dt
        if (m.dustGlow > 0f) m.dustGlow -= dt
        m.nextBlink -= dt
        if (m.nextBlink <= 0f) { m.blinkT = 0.14f; m.nextBlink = 2f + Random.nextFloat() * 3f }
        if (m.blinkT > 0f) m.blinkT -= dt
        m.yawnT -= dt
        if (m.yawnT <= 0f) { m.yawn = 1f; m.yawnT = 5f + Random.nextFloat() * 4f }
        if (m.yawn > 0f) m.yawn = max(0f, m.yawn - dt * 0.7f)

        // прогресс сна
        if (!asleep) {
            val rock = min(abs(m.vel), 60f) * 0.00045f
            val base = (if (m.covered) 0.012f else 0.004f) + (if (m.lamp > 0) 0.004f else 0f) + (if (m.musicVol > 0.1f) 0.006f else 0f)
            val gain = base + rock * (if (m.covered) 1f else 0.4f)
            addProgress(gain * dt)
            if (m.progress >= 1f) fallAsleep()
        } else {
            if (!m.snored && m.stageT > 0.9f) { m.snored = true; snd("cr_snore", 0.5f) }
            if (m.stageT > 0.6f) {
                m.yawnT = 99f
                if (Random.nextFloat() < dt * 1.1f) {
                    val h = headWorld()
                    m.parts.add(CrPart(2, h.x + charS() * 0.9f, h.y - charS() * 0.7f, W() * 0.02f, -H() * 0.05f, 2.6f, 2.6f, W() * (0.02f + Random.nextFloat() * 0.015f), Color(0xFFE0E7FF)))
                }
            }
            if (m.stageT > 1.8f && !m.rewardShown) { m.rewardShown = true }
        }

        // музыка: громкость плавно к цели
        val wantMusic = musicOnState.value && !asleep && !m.paused && (m.covered || abs(m.vel) > 3f)
        val target = if (wantMusic) 0.55f else 0f
        m.musicVol += (target - m.musicVol) * min(1f, dt * 1.5f)
        music.apply(if (m.paused) 0f else m.musicVol)

        // частицы
        val pi = m.parts.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            p.life -= dt
            if (p.life <= 0f) { pi.remove(); continue }
            when (p.kind) {
                1, 3 -> { p.vy += H() * 0.25f * dt }
                else -> { p.vx *= 0.99f }
            }
            p.x += p.vx * dt; p.y += p.vy * dt; p.rot += dt * 2f
        }
        if (m.parts.size > 300) repeat(40) { m.parts.removeAt(0) }

        // автодемо
        val overlay = showAlbum || rewardIdx >= 0
        if (m.demo) {
            if (overlay) stopDemo() else updateDemo(dt)
        } else if (!overlay && !asleep && m.clock - m.lastTouch > 5f) {
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

    // жизненный цикл: музыка и датчик только пока экран на переднем плане
    DisposableEffect(lifecycleOwner) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                m.accel = m.accel * 0.85f + event.values[0] * 0.15f
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        var registered = false
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> {
                    m.paused = false
                    if (sensor != null && !registered) {
                        registered = sensorManager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME) ?: false
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    m.paused = true
                    music.apply(0f)
                    if (registered) { sensorManager?.unregisterListener(listener); registered = false }
                    m.accel = 0f
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager?.unregisterListener(listener)
            music.release()
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
                        if (showAlbum || rewardIdx >= 0) return@awaitEachGesture
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
            val asleep = m.stage == CR_ASLEEP
            val lampColor = if (m.lamp > 0) CR_LAMP_COLORS[m.lamp - 1] else Color(0xFF8C86A8)

            // стена
            drawRect(Brush.verticalGradient(listOf(Color(0xFF263354), Color(0xFF3F4670), Color(0xFF6E6896)), endY = h * 0.8f))
            // полосатые обои (очень мягко)
            for (i in 0 until 9) {
                drawRect(Color.White.copy(alpha = 0.025f), Offset(w * (i * 0.125f), 0f), Size(w * 0.06f, h * 0.8f))
            }
            // окно с луной
            drawRoundRect(Color(0xFF8E7B6A), Offset(w * 0.73f, h * 0.045f), Size(w * 0.25f, h * 0.21f), CornerRadius(w * 0.02f))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF111A3A), Color(0xFF2A2F66)), startY = h * 0.05f, endY = h * 0.25f), Offset(w * 0.74f, h * 0.053f), Size(w * 0.23f, h * 0.195f), CornerRadius(w * 0.015f))
            drawCircle(Color(0xFFFFF4C9), w * 0.045f, Offset(w * 0.82f, h * 0.11f))
            drawCircle(Color(0xFF1B2250), w * 0.04f, Offset(w * 0.835f, h * 0.10f))
            for (i in 0 until 7) {
                val sx = w * (0.76f + (i * 0.031f))
                val sy = h * (0.07f + ((i * 37) % 11) * 0.014f)
                crStar(Offset(sx, sy), w * 0.008f * (1f + 0.3f * sin(t * 2f + i)), Color(0xFFFFF3C4).copy(alpha = 0.8f), 0f)
            }
            drawLine(Color(0xFF8E7B6A), Offset(w * 0.855f, h * 0.053f), Offset(w * 0.855f, h * 0.248f), w * 0.008f)
            drawLine(Color(0xFF8E7B6A), Offset(w * 0.74f, h * 0.15f), Offset(w * 0.97f, h * 0.15f), w * 0.008f)

            // проекция звёздочек от ночника
            if (m.lamp > 0) {
                for (i in 0 until 8) {
                    val sx = w * (0.1f + (i * 0.115f) + sin(t * 0.25f + i) * 0.03f)
                    val sy = h * (0.22f + ((i * 5) % 7) * 0.045f + cos(t * 0.2f + i * 1.3f) * 0.015f)
                    crStar(Offset(sx, sy), w * (0.022f + 0.008f * (i % 3)), lampColor.copy(alpha = 0.22f), t * 0.3f + i)
                }
            }

            // пол и коврик
            drawRect(Brush.verticalGradient(listOf(Color(0xFF5A4C6E), Color(0xFF3E3454)), startY = h * 0.8f, endY = h), Offset(0f, h * 0.8f), Size(w, h * 0.2f))
            drawOval(Color(0xFF8D82C8).copy(alpha = 0.45f), Offset(w * 0.08f, h * 0.76f), Size(w * 0.84f, h * 0.1f))

            // мобиль
            val mp = mobilePos()
            drawLine(Color(0xFFD6C5A6), Offset(mp.x, 0f), Offset(mp.x, mp.y - h * 0.04f), w * 0.006f)
            drawCircle(Color(0xFFD6C5A6), w * 0.012f, Offset(mp.x, mp.y - h * 0.04f))
            val orbit = w * 0.22f
            drawOval(Color(0xFFD6C5A6).copy(alpha = 0.35f), Offset(mp.x - orbit, mp.y - h * 0.012f), Size(orbit * 2f, h * 0.024f), style = Stroke(w * 0.004f))
            // луна в центре
            drawLine(Color(0xFFD6C5A6), Offset(mp.x, mp.y - h * 0.04f), Offset(mp.x, mp.y + h * 0.02f), w * 0.004f)
            drawCircle(Color(0xFFFFF0C0), w * 0.05f, Offset(mp.x, mp.y + h * 0.05f))
            drawCircle(Color(0xFF3F4670), w * 0.042f, Offset(mp.x + w * 0.02f, mp.y + h * 0.044f))
            for (i in 0 until 3) {
                val a = m.mobAng + i * 2.094f
                val tx = mp.x + cos(a) * orbit
                val depth = (sin(a) + 1f) * 0.5f
                val ty = mp.y + sin(a) * h * 0.012f + h * 0.02f
                drawLine(Color(0xFFD6C5A6), Offset(tx, mp.y + sin(a) * h * 0.012f), Offset(tx, ty + h * 0.03f), w * 0.004f)
                val col = listOf(Color(0xFFFFE27A), Color(0xFFB7D8FF), Color(0xFFFFB9D4))[i]
                crStar(Offset(tx, ty + h * 0.05f), w * (0.04f + 0.012f * depth), col, a)
            }

            // тумбочка и ночник
            val lp = lampPos()
            drawRoundRect(Color(0xFF8B6B5A), Offset(w * 0.03f, h * 0.665f), Size(w * 0.2f, h * 0.14f), CornerRadius(w * 0.02f))
            drawRoundRect(Color(0xFFA98672), Offset(w * 0.03f, h * 0.665f), Size(w * 0.2f, h * 0.02f), CornerRadius(w * 0.01f))
            drawRect(Color(0xFF6F5446), Offset(w * 0.06f, h * 0.7f), Size(w * 0.14f, h * 0.015f))
            if (m.lamp > 0) {
                drawCircle(
                    Brush.radialGradient(listOf(lampColor.copy(alpha = 0.5f), lampColor.copy(alpha = 0.12f), Color.Transparent), lp, w * 0.4f),
                    w * 0.4f, lp
                )
            }
            drawRect(Color(0xFF9AA3BE), Offset(lp.x - w * 0.008f, lp.y + w * 0.04f), Size(w * 0.016f, h * 0.055f))
            drawOval(Color(0xFF9AA3BE), Offset(lp.x - w * 0.05f, h * 0.655f), Size(w * 0.1f, h * 0.014f))
            val shade = Path().apply {
                moveTo(lp.x - w * 0.075f, lp.y + w * 0.045f)
                lineTo(lp.x + w * 0.075f, lp.y + w * 0.045f)
                lineTo(lp.x + w * 0.045f, lp.y - w * 0.06f)
                lineTo(lp.x - w * 0.045f, lp.y - w * 0.06f)
                close()
            }
            drawPath(shade, if (m.lamp > 0) lampColor else Color(0xFF6A6488))
            drawPath(shade, Color.White.copy(alpha = 0.25f), style = Stroke(w * 0.005f))
            crStar(Offset(lp.x, lp.y), w * 0.02f, Color.White.copy(alpha = if (m.lamp > 0) 0.7f else 0.2f), t * 0.4f)

            // кроватка (вращается вокруг точки опоры)
            val c = cradleC()
            val pv = pivot()
            val cw = w * 0.31f
            withTransform({ rotate(m.angle, pv) }) {
                // тень
                drawOval(Color(0x33000000), Offset(c.x - cw * 1.05f, pv.y - h * 0.012f), Size(cw * 2.1f, h * 0.035f))
                // полозья
                drawArc(Color(0xFF6B4423), 12f, 156f, false, Offset(c.x - cw * 1.0f, pv.y - h * 0.16f), Size(cw * 2f, h * 0.2f), style = Stroke(w * 0.022f, cap = StrokeCap.Round))
                // корпус (задняя стенка)
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFC08A5E), Color(0xFF8E5A38)), startY = h * 0.4f, endY = h * 0.62f), Offset(c.x - cw, h * 0.43f), Size(cw * 2f, h * 0.2f), CornerRadius(w * 0.05f))
                // изголовье и изножье
                drawRoundRect(Color(0xFFA87048), Offset(c.x - cw - w * 0.01f, h * 0.395f), Size(w * 0.075f, h * 0.2f), CornerRadius(w * 0.035f))
                drawRoundRect(Color(0xFFA87048), Offset(c.x + cw - w * 0.065f, h * 0.41f), Size(w * 0.075f, h * 0.185f), CornerRadius(w * 0.035f))
                // матрасик
                drawRoundRect(Color(0xFFFFF1D6), Offset(c.x - cw * 0.9f, h * 0.505f), Size(cw * 1.8f, h * 0.06f), CornerRadius(w * 0.03f))
                // плюшевая сплюшка рядом
                if (m.plush) crPlush(Offset(c.x - cw * 0.62f, h * 0.505f), w * 0.07f, t)
                // персонаж
                val cs = charS()
                val eye = when {
                    asleep -> 3
                    m.blinkT > 0f -> 3
                    m.happyT > 0f || m.covered && m.progress < 0.5f && m.dustGlow > 0f -> 2
                    m.covered && m.progress > 0.55f -> 1
                    m.progress > 0.75f -> 1
                    else -> 0
                }
                val breathe = 1f + (if (asleep || m.covered) sin(t * 1.6f) * 0.012f else 0f)
                val capIdx = min(sleeps, 5)
                crCharacter(m.character, Offset(c.x, charLocal().y), cs * breathe, eye, if (asleep) 0f else m.yawn, capIdx, m.paci, t)
                // блёстки от звёздной пыли
                if (m.dustGlow > 0f) {
                    crStar(Offset(c.x - cs * 0.9f, charLocal().y - cs * 0.9f), cs * 0.18f * min(1f, m.dustGlow), Color(0xFFFFF3A0), t * 2f)
                    crStar(Offset(c.x + cs * 0.95f, charLocal().y - cs * 0.5f), cs * 0.14f * min(1f, m.dustGlow), Color(0xFFFFF3A0), -t * 2f)
                }
                // одеяло
                if (m.covered) {
                    val by = h * 0.525f
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFD4C6F7), Color(0xFF9C8AD6)), startY = by, endY = by + h * 0.08f), Offset(c.x - cw * 0.93f, by), Size(cw * 1.86f, h * 0.075f), CornerRadius(w * 0.04f))
                    drawRoundRect(Color(0xFF7A67B8), Offset(c.x - cw * 0.93f, by), Size(cw * 1.86f, h * 0.075f), CornerRadius(w * 0.04f), style = Stroke(w * 0.005f))
                    for (i in 0 until 5) {
                        crStar(Offset(c.x - cw * 0.7f + i * cw * 0.35f, by + h * 0.035f), w * 0.018f, Color(0xFFFFF3C4).copy(alpha = 0.9f), 0f)
                    }
                    drawRoundRect(Color(0xFFEFE8FF), Offset(c.x - cw * 0.93f, by), Size(cw * 1.86f, h * 0.014f), CornerRadius(w * 0.02f))
                }
                // передняя планка с рейками
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFD9A577), Color(0xFFA87048)), startY = h * 0.57f, endY = h * 0.64f), Offset(c.x - cw, h * 0.57f), Size(cw * 2f, h * 0.065f), CornerRadius(w * 0.03f))
                for (i in 1 until 8) {
                    drawLine(Color(0xFF8E5A38).copy(alpha = 0.6f), Offset(c.x - cw + i * cw * 0.25f, h * 0.575f), Offset(c.x - cw + i * cw * 0.25f, h * 0.635f), w * 0.006f)
                }
            }

            // шкала сна
            val bx = w * 0.2f
            val bw = w * 0.6f
            val by = h * 0.765f
            drawRoundRect(Color(0xFF2C2D4B).copy(alpha = 0.75f), Offset(bx, by), Size(bw, h * 0.02f), CornerRadius(h * 0.01f))
            if (m.progress > 0.005f) {
                drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFFFD98A), Color(0xFFFFB9D4))), Offset(bx, by), Size(bw * m.progress, h * 0.02f), CornerRadius(h * 0.01f))
            }
            drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(bx, by), Size(bw, h * 0.02f), CornerRadius(h * 0.01f), style = Stroke(2f))
            crStar(Offset(bx + bw * m.progress, by + h * 0.01f), h * 0.016f, Color(0xFFFFF3A0), t)

            // предметы на полке
            drawRoundRect(Color(0xFF7A6590).copy(alpha = 0.55f), Offset(w * 0.02f, h * 0.835f), Size(w * 0.96f, h * 0.13f), CornerRadius(w * 0.04f))
            for (i in m.items.indices) {
                val it0 = m.items[i]
                val home = itemHome(i)
                val alpha = if (it0.used) 0.3f else 1f
                val lift = if (it0.dragging) 1.25f else 1f
                if (!it0.used && !it0.dragging) {
                    drawCircle(Color.White.copy(alpha = 0.12f + 0.06f * sin(t * 3f + i)), itemR() * 1.25f, Offset(it0.x, it0.y))
                }
                crItemIcon(it0.kind, Offset(it0.x, it0.y), itemR() * lift, alpha, t)
                if (it0.used) crStar(Offset(home.x, home.y), itemR() * 0.4f, Color(0xFFFFF3A0).copy(alpha = 0.8f), 0f)
            }

            // общее затемнение комнаты
            val dim = 0.06f + (if (m.lamp == 0) 0.14f else 0f) + (if (asleep) min(0.3f, m.stageT * 0.2f) else 0f)
            drawRect(Color(0xFF050A1E).copy(alpha = dim))

            // частицы
            for (p in m.parts) {
                val a = (p.life / p.max).coerceIn(0f, 1f)
                when (p.kind) {
                    0 -> crHeart(Offset(p.x, p.y), p.size, p.color.copy(alpha = a))
                    1 -> crStar(Offset(p.x, p.y), p.size, p.color.copy(alpha = a), p.rot)
                    2 -> {
                        val sc = 0.8f + (1f - a) * 0.8f
                        drawCircle(Color.White.copy(alpha = 0.15f * a), p.size * 1.8f * sc, Offset(p.x, p.y))
                        crZ(Offset(p.x, p.y), p.size * sc, p.color.copy(alpha = a))
                    }
                    else -> drawCircle(p.color.copy(alpha = a), p.size, Offset(p.x, p.y))
                }
            }

            // рука-подсказка
            if (m.demo) {
                val r = min(w, h) * 0.04f
                val k = if (m.handDown) 0.85f else 1f
                drawCircle(Color.White.copy(alpha = 0.3f), r * 1.7f * k, Offset(m.handX, m.handY))
                drawCircle(Color.White.copy(alpha = 0.9f), r * k, Offset(m.handX, m.handY))
                drawCircle(Color(0xFF5A4B9A), r * k, Offset(m.handX, m.handY), style = Stroke(3f))
            }
        }

        // кнопка музыки
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 10.dp, start = 12.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF9C8AD6), Color(0xFF6A58B0))))
                .border(3.dp, Color(0xFFEFE8FF), CircleShape)
                .clickable {
                    stopDemo(); m.lastTouch = m.clock
                    musicOn = !musicOn
                    prefs.edit().putBoolean("music", musicOn).apply()
                    snd("cr_pick", 0.6f)
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(30.dp)) {
                val s = size.width
                val col = if (musicOn) Color.White else Color.White.copy(alpha = 0.45f)
                drawCircle(col, s * 0.16f, Offset(s * 0.34f, s * 0.74f))
                drawLine(col, Offset(s * 0.46f, s * 0.72f), Offset(s * 0.46f, s * 0.18f), s * 0.07f, StrokeCap.Round)
                drawLine(col, Offset(s * 0.46f, s * 0.18f), Offset(s * 0.8f, s * 0.3f), s * 0.07f, StrokeCap.Round)
                if (!musicOn) drawLine(Color(0xFFFF8FB1), Offset(s * 0.1f, s * 0.9f), Offset(s * 0.9f, s * 0.1f), s * 0.08f, StrokeCap.Round)
            }
        }
        // кнопка альбома
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 12.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFFFD98A), Color(0xFFE8A325))))
                .border(3.dp, Color(0xFFFFF6E4), CircleShape)
                .clickable { stopDemo(); m.lastTouch = m.clock; showAlbum = true; snd("cr_pick", 0.6f) },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(30.dp)) { crStar(Offset(size.width / 2, size.height / 2), size.width * 0.44f, Color.White, 0f) }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 66.dp, end = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xCCFFFFFF))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text("Снов: $sleeps", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF4B3F86))
        }
        // подсказка
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 12.dp, start = 74.dp, end = 74.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xE6FFFFFF))
                .border(2.dp, Color(0xFFB7A8E8), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(hintText, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4B3F86), textAlign = TextAlign.Center)
        }
        if (demoOn) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 84.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE65FA8E8))
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            ) { Text("Просто смотри — потрогай экран, чтобы играть", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White) }
        }
        // «Ещё раз» после засыпания
        if (asleepUi && rewardIdx < 0 && !showAlbum) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF9C8AD6), Color(0xFF6A58B0))))
                    .border(3.dp, Color(0xFFEFE8FF), RoundedCornerShape(24.dp))
                    .clickable { stopDemo(); m.lastTouch = m.clock; startRound() }
                    .padding(horizontal = 28.dp, vertical = 10.dp)
            ) { Text("Уложить ещё раз", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White) }
        }

        // альбом «Сонный ангелочек»
        if (showAlbum) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xBB0C1230))
                    .clickable { showAlbum = false; m.lastTouch = m.clock },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFF2EEFF))
                        .border(5.dp, Color(0xFF9C8AD6), RoundedCornerShape(28.dp))
                        .clickable(enabled = false) {}
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Альбом «Сонный ангелочек»", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color(0xFF4B3F86))
                    Text("Уложено спать: $sleeps", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6A58B0))
                    Spacer(Modifier.height(10.dp))
                    for (row in 0 until 2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (col in 0 until 2) {
                                val idx = row * 2 + col
                                val unlocked = sleeps >= CR_STICKER_NEED[idx]
                                Column(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .padding(vertical = 5.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (unlocked) Color(0xFFE2D9FF) else Color(0xFFE6E8EE))
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Canvas(Modifier.size(78.dp)) {
                                        if (unlocked) crSticker(idx, Offset(size.width / 2, size.height / 2), size.width * 0.4f)
                                        else {
                                            drawCircle(Color(0x22000000), size.width * 0.36f, Offset(size.width / 2, size.height / 2))
                                            drawRoundRect(Color(0xFF7A8EA3), Offset(size.width * 0.36f, size.height * 0.46f), Size(size.width * 0.28f, size.height * 0.22f), CornerRadius(6f))
                                            drawArc(Color(0xFF7A8EA3), 180f, 180f, false, Offset(size.width * 0.4f, size.height * 0.3f), Size(size.width * 0.2f, size.height * 0.2f), style = Stroke(5f))
                                        }
                                    }
                                    Text(
                                        if (unlocked) CR_STICKER_NAMES[idx] else "Ещё ${CR_STICKER_NEED[idx] - sleeps}",
                                        fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4B3F86),
                                        textAlign = TextAlign.Center, maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Пижамки (колпачки)", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF4B3F86))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        for (i in 0 until 5) {
                            Canvas(Modifier.size(40.dp)) {
                                if (sleeps > i) crCap(Offset(size.width / 2, size.height * 0.85f), size.width * 0.5f, CR_CAPS[i])
                                else drawCircle(Color(0x22000000), size.width * 0.3f, Offset(size.width / 2, size.height / 2))
                            }
                        }
                    }
                }
            }
        }

        // награда: наклейка
        val ri = rewardIdx
        if (ri >= 0) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xAA0C1230))
                    .clickable { rewardIdx = -1; m.lastTouch = m.clock },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFF2EEFF))
                        .border(5.dp, Color(0xFF9C8AD6), RoundedCornerShape(28.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Новая наклейка!", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF6A58B0))
                    Canvas(Modifier.size(150.dp)) { crSticker(ri, Offset(size.width / 2, size.height / 2), size.width * 0.42f) }
                    Text(CR_STICKER_NAMES[ri], fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF4B3F86))
                    Text("И новая пижамка для малыша", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6A58B0))
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF6A58B0))
                            .padding(horizontal = 28.dp, vertical = 8.dp)
                    ) { Text("Ура!", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White) }
                }
            }
        }
    }
}

// ───────────────────────── рисование ─────────────────────────

private fun DrawScope.crStar(c: Offset, r: Float, color: Color, rot: Float) {
    val p = Path()
    for (i in 0 until 10) {
        val a = rot - PI.toFloat() / 2f + i * PI.toFloat() / 5f
        val rr = if (i % 2 == 0) r else r * 0.45f
        val x = c.x + cos(a) * rr
        val y = c.y + sin(a) * rr
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}

private fun DrawScope.crHeart(c: Offset, r: Float, color: Color) {
    val p = Path()
    p.moveTo(c.x, c.y + r * 0.9f)
    p.cubicTo(c.x - r * 1.6f, c.y - r * 0.2f, c.x - r * 0.7f, c.y - r * 1.2f, c.x, c.y - r * 0.4f)
    p.cubicTo(c.x + r * 0.7f, c.y - r * 1.2f, c.x + r * 1.6f, c.y - r * 0.2f, c.x, c.y + r * 0.9f)
    p.close()
    drawPath(p, color)
}

private fun DrawScope.crZ(c: Offset, s: Float, color: Color) {
    val p = Path()
    p.moveTo(c.x - s, c.y - s)
    p.lineTo(c.x + s, c.y - s)
    p.lineTo(c.x - s, c.y + s)
    p.lineTo(c.x + s, c.y + s)
    drawPath(p, color, style = Stroke(s * 0.38f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

private fun DrawScope.crCap(base: Offset, s: Float, color: Color) {
    val p = Path()
    p.moveTo(base.x - s, base.y)
    p.quadraticBezierTo(base.x - s * 0.6f, base.y - s * 1.3f, base.x + s * 0.8f, base.y - s * 1.5f)
    p.quadraticBezierTo(base.x + s * 0.5f, base.y - s * 0.5f, base.x + s, base.y)
    p.close()
    drawPath(p, color)
    drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(base.x - s * 1.05f, base.y - s * 0.18f), Size(s * 2.1f, s * 0.3f), CornerRadius(s * 0.15f))
    drawCircle(Color.White, s * 0.22f, Offset(base.x + s * 0.8f, base.y - s * 1.5f))
}

private fun DrawScope.crPlush(c: Offset, s: Float, t: Float) {
    drawCircle(Color(0xFFD9B8FF), s * 0.38f, Offset(c.x - s * 0.55f, c.y - s * 0.55f))
    drawCircle(Color(0xFFD9B8FF), s * 0.38f, Offset(c.x + s * 0.55f, c.y - s * 0.55f))
    drawOval(Color(0xFFE6D0FF), Offset(c.x - s, c.y - s * 0.7f), Size(s * 2f, s * 1.5f))
    drawArc(Color(0xFF4B3F86), 20f, 140f, false, Offset(c.x - s * 0.5f, c.y - s * 0.55f), Size(s * 0.4f, s * 0.35f), style = Stroke(s * 0.1f, cap = StrokeCap.Round))
    drawArc(Color(0xFF4B3F86), 20f, 140f, false, Offset(c.x + s * 0.1f, c.y - s * 0.55f), Size(s * 0.4f, s * 0.35f), style = Stroke(s * 0.1f, cap = StrokeCap.Round))
    drawCircle(Color(0xFFFF8FB1), s * 0.12f, Offset(c.x, c.y - s * 0.05f))
    crZ(Offset(c.x + s * 0.9f, c.y - s * 1.1f - sin(t * 2f) * s * 0.1f), s * 0.18f, Color(0xFFE0E7FF))
}

private fun DrawScope.crItemIcon(kind: Int, c: Offset, r: Float, alpha: Float, t: Float) {
    when (kind) {
        IT_BLANKET -> {
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFD4C6F7), Color(0xFF9C8AD6)), startY = c.y - r, endY = c.y + r).let { it }, Offset(c.x - r * 1.1f, c.y - r * 0.7f), Size(r * 2.2f, r * 1.4f), CornerRadius(r * 0.35f), alpha = alpha)
            drawRoundRect(Color(0xFF7A67B8), Offset(c.x - r * 1.1f, c.y - r * 0.7f), Size(r * 2.2f, r * 1.4f), CornerRadius(r * 0.35f), style = Stroke(r * 0.07f), alpha = alpha)
            crStar(c, r * 0.4f, Color(0xFFFFF3C4).copy(alpha = alpha), 0f)
        }
        IT_PACI -> {
            drawCircle(Color(0xFF7FC8FF).copy(alpha = alpha), r * 0.55f, Offset(c.x, c.y + r * 0.15f))
            drawCircle(Color(0xFF3B8EE8).copy(alpha = alpha), r * 0.55f, Offset(c.x, c.y + r * 0.15f), style = Stroke(r * 0.08f))
            drawCircle(Color(0xFFFFE3F0).copy(alpha = alpha), r * 0.32f, Offset(c.x, c.y + r * 0.15f))
            drawCircle(Color(0xFF3B8EE8).copy(alpha = alpha), r * 0.42f, Offset(c.x, c.y - r * 0.55f), style = Stroke(r * 0.1f))
        }
        IT_PLUSH -> crPlush(Offset(c.x, c.y + r * 0.2f), r * 0.95f, t).also {
            if (alpha < 1f) drawCircle(Color(0xFF3E3454).copy(alpha = 0.55f), r * 1.1f, c)
        }
        else -> {
            drawRoundRect(Color(0xFFBFE6FF).copy(alpha = alpha * 0.85f), Offset(c.x - r * 0.65f, c.y - r * 0.6f), Size(r * 1.3f, r * 1.5f), CornerRadius(r * 0.3f))
            drawRoundRect(Color.White.copy(alpha = alpha), Offset(c.x - r * 0.65f, c.y - r * 0.6f), Size(r * 1.3f, r * 1.5f), CornerRadius(r * 0.3f), style = Stroke(r * 0.07f))
            drawRoundRect(Color(0xFFE8A325).copy(alpha = alpha), Offset(c.x - r * 0.5f, c.y - r * 0.9f), Size(r * 1.0f, r * 0.35f), CornerRadius(r * 0.12f))
            crStar(Offset(c.x, c.y + r * 0.15f), r * 0.38f, Color(0xFFFFE27A).copy(alpha = alpha), t)
        }
    }
}

private fun DrawScope.crSticker(id: Int, c: Offset, r: Float) {
    drawCircle(Color.White, r * 1.08f, c)
    when (id) {
        0 -> {
            drawCircle(Color(0xFFBFD8FF), r * 0.8f, c)
            crStar(c, r * 0.4f, Color(0xFFFFF3A0), 0f)
        }
        1 -> {
            drawCircle(Color(0xFFD9CBFF), r * 0.9f, c)
            drawCircle(Color(0xFFFFF4C9), r * 0.5f, Offset(c.x - r * 0.1f, c.y))
            drawCircle(Color(0xFFD9CBFF), r * 0.42f, Offset(c.x + r * 0.1f, c.y - r * 0.08f))
            crStar(Offset(c.x + r * 0.45f, c.y + r * 0.35f), r * 0.2f, Color(0xFFFFD54A), 0f)
        }
        2 -> {
            drawCircle(Color(0xFF9DB4FF), r * 0.9f, c)
            drawCircle(Color(0xFFFFF4C9), r * 0.45f, Offset(c.x - r * 0.15f, c.y - r * 0.1f))
            for (i in 0 until 4) crStar(Offset(c.x + r * (0.2f + i * 0.14f), c.y + r * (0.45f - i * 0.2f)), r * 0.12f, Color(0xFFFFFFFF), 0f)
        }
        else -> {
            drawCircle(Color(0xFFFFE9A8), r * 0.9f, c)
            // ангелочек: круглая мордочка с нимбом и крылышками
            drawOval(Color.White, Offset(c.x - r * 0.75f, c.y - r * 0.05f), Size(r * 0.55f, r * 0.4f))
            drawOval(Color.White, Offset(c.x + r * 0.2f, c.y - r * 0.05f), Size(r * 0.55f, r * 0.4f))
            drawCircle(Color(0xFFFFF1D6), r * 0.42f, Offset(c.x, c.y + r * 0.05f))
            drawArc(Color(0xFF3A2B45), 20f, 140f, false, Offset(c.x - r * 0.28f, c.y - r * 0.12f), Size(r * 0.2f, r * 0.16f), style = Stroke(r * 0.05f, cap = StrokeCap.Round))
            drawArc(Color(0xFF3A2B45), 20f, 140f, false, Offset(c.x + r * 0.08f, c.y - r * 0.12f), Size(r * 0.2f, r * 0.16f), style = Stroke(r * 0.05f, cap = StrokeCap.Round))
            drawOval(Color(0xFFFFD54A), Offset(c.x - r * 0.3f, c.y - r * 0.62f), Size(r * 0.6f, r * 0.18f), style = Stroke(r * 0.06f))
        }
    }
}

/** kind: 0 пельмешек, 1 котёнок, 2 зайка. eye: 0 открыты, 1 сонные, 2 довольно прикрыты, 3 спит */
private fun DrawScope.crCharacter(kind: Int, c: Offset, s: Float, eye: Int, yawn: Float, capIdx: Int, paci: Boolean, t: Float) {
    val ink = Color(0xFF3A2B45)
    // уши за головой
    if (kind == 1) {
        for (sx in listOf(-1f, 1f)) {
            val p = Path()
            p.moveTo(c.x + sx * s * 0.92f, c.y - s * 0.3f)
            p.lineTo(c.x + sx * s * 0.75f, c.y - s * 1.15f)
            p.lineTo(c.x + sx * s * 0.2f, c.y - s * 0.72f)
            p.close()
            drawPath(p, Color(0xFFF2A066))
            val q = Path()
            q.moveTo(c.x + sx * s * 0.8f, c.y - s * 0.45f)
            q.lineTo(c.x + sx * s * 0.7f, c.y - s * 0.95f)
            q.lineTo(c.x + sx * s * 0.35f, c.y - s * 0.72f)
            q.close()
            drawPath(q, Color(0xFFFFB9C8))
        }
    } else if (kind == 2) {
        for (sx in listOf(-1f, 1f)) {
            withTransform({ translate(c.x + sx * s * 0.45f, c.y - s * 1.0f); rotate(sx * 10f, Offset.Zero) }) {
                drawOval(Color(0xFFF1ECFF), Offset(-s * 0.2f, -s * 0.7f), Size(s * 0.4f, s * 1.4f))
                drawOval(Color(0xFFFFB9C8), Offset(-s * 0.11f, -s * 0.52f), Size(s * 0.22f, s * 1.0f))
            }
        }
    }
    val base = when (kind) { 0 -> Color(0xFFFFE7A3); 1 -> Color(0xFFFFBE85); else -> Color(0xFFF7F3FF) }
    val hi = when (kind) { 0 -> Color(0xFFFFF7D8); 1 -> Color(0xFFFFDDB8); else -> Color.White }
    val edge = when (kind) { 0 -> Color(0xFFD9A93A); 1 -> Color(0xFFD48A4A); else -> Color(0xFFB9AEDD) }
    drawOval(
        Brush.radialGradient(listOf(hi, base), Offset(c.x - s * 0.3f, c.y - s * 0.4f), s * 1.7f),
        Offset(c.x - s, c.y - s * 0.85f), Size(s * 2f, s * 1.7f)
    )
    drawOval(edge, Offset(c.x - s, c.y - s * 0.85f), Size(s * 2f, s * 1.7f), style = Stroke(s * 0.05f))
    if (kind == 0) {
        for (i in 0 until 7) {
            val x = c.x - s * 0.78f + i * s * 0.26f
            val k = (x - c.x) / s
            val y = c.y - s * 0.85f * sqrt(max(0f, 1f - k * k))
            drawCircle(edge.copy(alpha = 0.55f), s * 0.07f, Offset(x, y + s * 0.07f))
        }
    }
    if (kind == 1) {
        for (i in -1..1) drawLine(Color(0xFFD48A4A), Offset(c.x + i * s * 0.14f, c.y - s * 0.8f), Offset(c.x + i * s * 0.14f, c.y - s * 0.55f), s * 0.05f, StrokeCap.Round)
    }
    // щёчки
    drawCircle(Color(0x66FF7B9C), s * 0.15f, Offset(c.x - s * 0.58f, c.y + s * 0.2f))
    drawCircle(Color(0x66FF7B9C), s * 0.15f, Offset(c.x + s * 0.58f, c.y + s * 0.2f))
    // глаза
    for (sx in listOf(-1f, 1f)) {
        val ex = c.x + sx * s * 0.34f
        val ey = c.y - s * 0.05f
        when (eye) {
            0 -> {
                drawCircle(ink, s * 0.11f, Offset(ex, ey))
                drawCircle(Color.White, s * 0.04f, Offset(ex - s * 0.035f, ey - s * 0.04f))
            }
            1 -> drawOval(ink, Offset(ex - s * 0.11f, ey - s * 0.03f), Size(s * 0.22f, s * 0.1f))
            2 -> drawArc(ink, 200f, 140f, false, Offset(ex - s * 0.11f, ey - s * 0.06f), Size(s * 0.22f, s * 0.22f), style = Stroke(s * 0.05f, cap = StrokeCap.Round))
            else -> drawArc(ink, 20f, 140f, false, Offset(ex - s * 0.11f, ey - s * 0.14f), Size(s * 0.22f, s * 0.22f), style = Stroke(s * 0.05f, cap = StrokeCap.Round))
        }
    }
    // нос и усы
    if (kind == 1) {
        val n = Path()
        n.moveTo(c.x - s * 0.06f, c.y + s * 0.1f); n.lineTo(c.x + s * 0.06f, c.y + s * 0.1f); n.lineTo(c.x, c.y + s * 0.18f); n.close()
        drawPath(n, Color(0xFFFF8FA3))
        for (sx in listOf(-1f, 1f)) {
            drawLine(ink.copy(alpha = 0.6f), Offset(c.x + sx * s * 0.5f, c.y + s * 0.15f), Offset(c.x + sx * s * 0.95f, c.y + s * 0.08f), s * 0.025f, StrokeCap.Round)
            drawLine(ink.copy(alpha = 0.6f), Offset(c.x + sx * s * 0.5f, c.y + s * 0.24f), Offset(c.x + sx * s * 0.95f, c.y + s * 0.3f), s * 0.025f, StrokeCap.Round)
        }
    } else if (kind == 2) {
        drawCircle(Color(0xFFFF8FA3), s * 0.06f, Offset(c.x, c.y + s * 0.12f))
    }
    // рот
    val my = c.y + s * 0.3f
    if (yawn > 0.05f) {
        drawOval(Color(0xFFD9566E), Offset(c.x - s * 0.12f, my - s * 0.03f), Size(s * 0.24f, s * (0.08f + 0.2f * yawn)))
    } else {
        drawArc(ink, 20f, 140f, false, Offset(c.x - s * 0.11f, my - s * 0.12f), Size(s * 0.22f, s * 0.18f), style = Stroke(s * 0.045f, cap = StrokeCap.Round))
    }
    if (paci) {
        drawCircle(Color(0xFF7FC8FF), s * 0.14f, Offset(c.x, my + s * 0.05f))
        drawCircle(Color(0xFF3B8EE8), s * 0.14f, Offset(c.x, my + s * 0.05f), style = Stroke(s * 0.03f))
        drawCircle(Color(0xFFFFE3F0), s * 0.07f, Offset(c.x, my + s * 0.05f))
    }
    if (capIdx > 0) {
        crCap(Offset(c.x - s * 0.05f, c.y - s * 0.62f), s * 0.62f, CR_CAPS[(capIdx - 1) % CR_CAPS.size])
    }
}
