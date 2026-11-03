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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.shared.SoundManager
import com.example.shared.i18n.Text
import com.example.shared.rememberHaptics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ───────────────────────── модель ─────────────────────────

private const val PH_CHOOSE = 0
private const val PH_BOIL = 1
private const val PH_EXTRACT = 2
private const val PH_TO_FEED = 3
private const val PH_FEED = 4
private const val PH_CHEW = 5
private const val PH_CELEBRATE = 6
private const val PH_TO_POT = 7

private class KcFly(val type: KcIng, val x0: Float, val y0: Float, val x1: Float, val y1: Float, val dur: Float, val arc: Float) {
    var t = 0f
    var rot = 0f
    val spin = (Random.nextFloat() - 0.5f) * 520f
}

private class KcFloat(val type: KcIng, val ox: Float, val oy: Float, val ph: Float)

/** kind: 0 капля, 1 пар, 2 искра, 3 сердечко, 4 конфетти, 5 крошка */
private class KcPart(
    val kind: Int, var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val max: Float, val size: Float, val color: Color, var rot: Float = 0f, val vr: Float = 0f
)

private class KcBubble(val ox: Float, val oy: Float, val maxR: Float) { var age = 0f }

private class KcModel {
    var phase = PH_CHOOSE
    var clock = 0f
    var inited = false
    val ings = ArrayList<KcIng>()
    val flies = ArrayList<KcFly>()
    val floats = ArrayList<KcFloat>()
    val parts = ArrayList<KcPart>()
    val bubbles = ArrayList<KcBubble>()

    // варка
    var heat = 0f
    var boilGain = 0f
    var stirLastAng = 0f
    var stirring = false
    var stirMoved = 0f
    var stirDownT = 0f
    var ladleX = 0f
    var ladleY = 0f
    var ladleVis = 0f
    var stirSfxAt = 0f
    var bubSfxAt = 0f
    var stirHaptAt = 0f
    var potSquash = 0f
    var potVel = 0f
    var potShake = 0f
    var brothR = 1f
    var brothG = 0.78f
    var brothB = 0.42f

    // ввод
    var dragIng: KcIng? = null
    var dragX = 0f
    var dragY = 0f
    var downX = 0f
    var downY = 0f
    var moved = false
    var dragPel = false
    var lastTouch = 0f

    // пельмень (абсолютные координаты экрана)
    var result: KcResult? = null
    var pelX = 0f
    var pelY = 0f
    var pelS = 0f
    var pelTilt = 0f
    var pelAlpha = 0f
    var pelHome = false
    var pelGoalX = 0f
    var pelGoalY = 0f
    var extractT = 0f
    var extractFromX = 0f
    var extractFromY = 0f

    // сцены
    var sceneT = 0f
    var phaseT = 0f
    var chewT = 0f
    var chewHits = 0
    var mouthOpen = 0f
    var cheer = 0f
    var blinkT = 0f
    var bearWink = false
    var jump = 0f
    var stars = 0

    // демо
    var demo = false
    var demoStep = 0
    var demoT = 0f
    var handX = 0f
    var handY = 0f
    var handDown = false
    var handFromX = 0f
    var handFromY = 0f
    var demoStirAng = 0f

    var hint = "Брось в кастрюлю три вкусных продукта"
}

private fun ease(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

private const val PREFS = "kastrulya_prefs"

@Composable
fun KastrulyaScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val sm = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val toddler = LocalAgeProfile.current.isToddler2
    val density = LocalDensity.current.density
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) {
        sm.preloadRaw(
            listOf(
                "kc_flour", "kc_meat", "kc_egg", "kc_greens", "kc_cheese", "kc_splash", "kc_whoosh",
                "kc_bub1", "kc_bub2", "kc_bub3", "kc_bub4", "kc_boil", "kc_stir", "kc_bell", "kc_sparkle",
                "kc_sad", "kc_sigh", "kc_munch1", "kc_munch2", "kc_yum", "kc_clink", "kc_pop"
            )
        )
    }

    var size by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(0) }
    val m = remember { KcModel() }

    var cooked by remember { mutableIntStateOf(prefs.getInt("cooked", 0)) }
    var perfect by remember { mutableIntStateOf(prefs.getInt("perfect", 0)) }
    var skinId by remember { mutableStateOf(prefs.getString("skin", "classic") ?: "classic") }
    var hintText by remember { mutableStateOf(m.hint) }
    var showAlbum by remember { mutableStateOf(false) }
    var rewardSkin by remember { mutableStateOf<KcSkin?>(null) }
    var demoOn by remember { mutableStateOf(false) }

    fun W() = size.width.toFloat()
    fun H() = size.height.toFloat()
    fun setHint(s: String) { if (m.hint != s) { m.hint = s; hintText = s } }
    fun snd(n: String, v: Float = 1f, r: Float = 1f) { sm.play(n, volume = v, rate = r) }
    fun skinNeed(id: String) = KC_SKINS.firstOrNull { it.id == id }?.need ?: 0

    // ── геометрия ──
    fun pot(): KcPot {
        val rw = min(W() * 0.31f, H() * 0.185f)
        return KcPot(W() * 0.5f, H() * 0.40f, rw, rw * 0.95f)
    }
    fun shelfTop() = H() * 0.765f
    fun slotX(i: Int) = W() * (i + 0.5f) / 5f
    fun slotY() = H() * 0.855f
    fun slotR() = min(W() / 5f * 0.33f, H() * 0.075f)
    fun bearS() = min(W() * 0.32f, H() * 0.19f)
    fun bearC() = Offset(W() * 0.5f, H() * 0.27f)
    fun mouthPos(): Offset { val s = bearS(); val c = bearC(); return Offset(c.x, c.y + s * 0.3f) }
    fun platePos() = Offset(W() * 0.5f, H() * 0.74f)
    fun plateR() = min(W() * 0.3f, H() * 0.17f)
    fun pelSize() = min(W() * 0.15f, H() * 0.095f)
    fun hoverPos() = Offset(W() * 0.5f, H() * 0.2f)

    fun brothColor(): Color = Color(m.brothR, m.brothG, m.brothB)

    fun burst(kind: Int, x: Float, y: Float, n: Int, color: Color, spd: Float, size: Float, up: Float = 0f, life: Float = 0.9f) {
        repeat(n) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val sp = spd * (0.35f + Random.nextFloat() * 0.65f)
            m.parts.add(KcPart(kind, x, y, cos(a) * sp, sin(a) * sp - up, life * (0.6f + Random.nextFloat() * 0.6f), life, size * (0.6f + Random.nextFloat() * 0.8f), color, Random.nextFloat() * 6f, (Random.nextFloat() - 0.5f) * 8f))
        }
    }

    fun startRound() {
        m.phase = PH_CHOOSE
        m.ings.clear(); m.floats.clear(); m.flies.clear(); m.bubbles.clear()
        m.heat = 0f; m.result = null; m.stirring = false; m.dragIng = null; m.dragPel = false
        m.pelAlpha = 0f; m.pelHome = false
        m.brothR = 1f; m.brothG = 0.78f; m.brothB = 0.42f
        m.phaseT = 0f; m.mouthOpen = 0f
        setHint("Брось в кастрюлю три вкусных продукта")
    }

    fun finishRound() {
        // награды
        val res = m.result ?: KcResult.PERFECT
        val before = cooked
        cooked += 1
        if (res == KcResult.PERFECT) perfect += 1
        prefs.edit().putInt("cooked", cooked).putInt("perfect", perfect).apply()
        val newSkin = KC_SKINS.firstOrNull { it.need > before && it.need <= cooked }
        if (newSkin != null) {
            rewardSkin = newSkin
            skinId = newSkin.id
            prefs.edit().putString("skin", newSkin.id).apply()
            snd("kc_bell")
            snd("kc_sparkle", 0.8f)
        }
    }

    fun landIngredient(f: KcFly) {
        val g = pot()
        m.ings.add(f.type)
        m.potVel += 3.2f
        m.floats.add(KcFloat(f.type, (Random.nextFloat() - 0.5f) * 1.2f, (Random.nextFloat() - 0.5f) * 0.9f, Random.nextFloat() * 6f))
        // бульон смешивает цвет
        val tt = f.type.tint
        m.brothR = lerpF(m.brothR, tt.red, 0.18f)
        m.brothG = lerpF(m.brothG, tt.green, 0.18f)
        m.brothB = lerpF(m.brothB, tt.blue, 0.18f)
        burst(0, f.x1, g.brothY, 14, Color(0xFFBFE3FF), g.rw * 2.2f, g.rw * 0.035f, up = g.rw * 1.6f, life = 0.8f)
        when (f.type) {
            KcIng.FLOUR -> { burst(1, f.x1, g.brothY - g.ry, 6, Color(0xFFFFF8E8), g.rw * 0.4f, g.rw * 0.13f, up = g.rw * 0.5f, life = 1.0f); snd("kc_flour") }
            KcIng.MEAT -> snd("kc_meat")
            KcIng.EGG -> snd("kc_egg")
            KcIng.GREENS -> snd("kc_greens")
            KcIng.CHEESE -> snd("kc_cheese")
        }
        snd("kc_splash", 0.55f, 0.95f + Random.nextFloat() * 0.15f)
        haptics.vibrateWater()
        val n = m.ings.size
        if (n < 3) setHint(if (n == 1) "Плюх! Давай ещё" else "Ещё один — и варим!")
        else {
            m.phase = PH_BOIL
            m.phaseT = 0f
            m.heat = 0f
            snd("kc_clink")
            setHint(if (toddler) "Бульк-бульк! Варится…" else "Води пальцем по кастрюле — мешай бульон!")
        }
    }

    fun throwIng(type: KcIng, x0: Float, y0: Float, fromDrag: Boolean) {
        val g = pot()
        val x1 = g.cx + (Random.nextFloat() - 0.5f) * g.rw * 0.9f
        val y1 = g.brothY + (Random.nextFloat() - 0.5f) * g.ry * 0.5f
        val dist = hypot(x1 - x0, y1 - y0)
        val dur = if (fromDrag) 0.32f + dist / H() * 0.25f else 0.62f
        val arc = if (fromDrag) H() * 0.05f else H() * 0.2f
        m.flies.add(KcFly(type, x0, y0, x1, y1, dur, arc))
        snd("kc_whoosh", 0.5f, 0.9f + Random.nextFloat() * 0.3f)
    }

    fun addStir(da: Float, px: Float, py: Float) {
        val g = pot()
        m.ladleX = px; m.ladleY = py; m.ladleVis = 1f
        if (m.phase != PH_BOIL) return
        val gain = da * (if (toddler) 0.06f else 0.05f)
        m.heat += gain
        m.boilGain = min(1f, m.boilGain + da * 0.12f)
        if (m.clock > m.stirSfxAt) { m.stirSfxAt = m.clock + 0.38f; snd("kc_stir", 0.55f, 0.9f + Random.nextFloat() * 0.3f) }
        if (m.clock > m.stirHaptAt) { m.stirHaptAt = m.clock + 0.22f; haptics.vibrate(8L) }
        if (Random.nextFloat() < 0.25f) burst(0, g.cx + (Random.nextFloat() - 0.5f) * g.rw, g.brothY, 1, Color(0xFFD8EEFF), g.rw * 0.8f, g.rw * 0.025f, up = g.rw * 0.9f, life = 0.5f)
    }

    fun takeOut() {
        if (m.phase != PH_BOIL) return
        val g = pot()
        val h = m.heat
        if (h < 0.22f) {
            m.potShake = 1f
            snd("kc_clink", 0.8f, 0.8f)
            setHint("Ещё сыровато — помешай ещё!")
            return
        }
        val res = when {
            h < 0.75f -> KcResult.UNDER
            h <= 1.2f -> KcResult.PERFECT
            else -> KcResult.OVER
        }
        m.result = res
        m.phase = PH_EXTRACT
        m.extractT = 0f
        m.phaseT = 0f
        m.pelS = pelSize() * 0.6f
        m.extractFromX = g.cx; m.extractFromY = g.brothY
        m.pelX = g.cx; m.pelY = g.brothY; m.pelAlpha = 1f; m.pelTilt = 0f
        m.stirring = false
        m.ladleVis = 0f
        burst(0, g.cx, g.brothY, 18, Color(0xFFBFE3FF), g.rw * 2.4f, g.rw * 0.04f, up = g.rw * 2f)
        snd("kc_pop")
        snd("kc_splash", 0.6f)
        haptics.vibratePop()
        setHint(
            when (res) {
                KcResult.PERFECT -> "Ура! Румяный пельмешек!"
                KcResult.UNDER -> "Ой, он ещё бледненький…"
                KcResult.OVER -> "Ох, немного переварился!"
            }
        )
    }

    fun startFeed() {
        val mp = mouthPos()
        m.dragPel = false
        m.pelHome = false
        m.pelGoalX = mp.x; m.pelGoalY = mp.y
        m.phase = PH_CHEW
        m.chewT = 0f; m.chewHits = 0
        setHint("Ам-ням-ням!")
    }

    fun stopDemo() {
        if (m.demo) {
            m.demo = false; demoOn = false
            m.handDown = false
            m.dragIng = null; m.dragPel = false; m.stirring = false
        }
    }

    // ── ввод ──
    fun slotAt(p: Offset): Int {
        if (p.y < shelfTop() - 10f) return -1
        val i = (p.x / (W() / 5f)).toInt().coerceIn(0, 4)
        return i
    }

    fun onDown(p: Offset) {
        m.lastTouch = m.clock
        stopDemo()
        m.downX = p.x; m.downY = p.y; m.moved = false
        when (m.phase) {
            PH_CHOOSE -> {
                val s = slotAt(p)
                if (s >= 0) {
                    m.dragIng = KcIng.entries[s]
                    m.dragX = p.x; m.dragY = p.y
                    snd("kc_clink", 0.4f, 1.4f)
                } else {
                    val g = pot()
                    if (hypot(p.x - g.cx, p.y - g.rimY) < g.rw * 1.1f) { m.potVel += 2f; snd("kc_bub" + Random.nextInt(1, 5), 0.6f) }
                }
            }
            PH_BOIL -> {
                val g = pot()
                m.stirring = true
                m.stirMoved = 0f
                m.stirDownT = m.clock
                m.stirLastAng = atan2(p.y - g.rimY, p.x - g.cx)
                m.ladleX = p.x; m.ladleY = p.y
            }
            PH_FEED -> {
                if (hypot(p.x - m.pelX, p.y - m.pelY) < m.pelS * 1.5f) { m.dragPel = true; m.pelHome = false }
            }
        }
    }

    fun onMove(p: Offset) {
        val dx = p.x - m.downX
        val dy = p.y - m.downY
        if (hypot(dx, dy) > 12f * density) m.moved = true
        when (m.phase) {
            PH_CHOOSE -> if (m.dragIng != null) { m.dragX = p.x; m.dragY = p.y }
            PH_BOIL -> if (m.stirring) {
                val g = pot()
                val ang = atan2(p.y - g.rimY, p.x - g.cx)
                var d = ang - m.stirLastAng
                while (d > PI) d -= (2 * PI).toFloat()
                while (d < -PI) d += (2 * PI).toFloat()
                m.stirLastAng = ang
                m.stirMoved += abs(d)
                val inside = abs(p.x - g.cx) < g.rw * 1.35f && abs(p.y - g.rimY) < g.rw * 1.1f
                if (inside) {
                    val sc = hypot(p.x - g.cx, (p.y - g.rimY) * 2.5f) / g.rw
                    addStir(abs(d) * min(1f, 0.4f + sc), p.x, p.y)
                }
            }
            PH_FEED -> if (m.dragPel) { m.pelX = p.x; m.pelY = p.y - m.pelS * 0.4f }
        }
    }

    fun onUp(p: Offset) {
        when (m.phase) {
            PH_CHOOSE -> {
                val t = m.dragIng
                if (t != null) {
                    if (!m.moved) {
                        val i = t.ordinal
                        throwIng(t, slotX(i), slotY(), false)
                    } else {
                        val g = pot()
                        val over = p.y < shelfTop() && abs(p.x - g.cx) < g.rw * 1.35f && p.y < g.bottom
                        if (over) throwIng(t, p.x, p.y, true) else { burst(1, p.x, p.y, 4, Color(0xFFFFF3DC), 40f, 14f, life = 0.5f) }
                    }
                    m.dragIng = null
                }
            }
            PH_BOIL -> {
                if (m.stirring) {
                    if (!m.moved && m.clock - m.stirDownT < 0.4f) takeOut()
                    m.stirring = false
                }
            }
            PH_FEED -> {
                if (m.dragPel) {
                    val mp = mouthPos()
                    val near = hypot(m.pelX - mp.x, m.pelY - mp.y) < bearS() * 0.85f
                    if (near || !m.moved) startFeed() else {
                        m.pelHome = true
                        m.pelGoalX = platePos().x; m.pelGoalY = platePos().y - m.pelS * 0.3f
                    }
                    m.dragPel = false
                }
            }
        }
    }

    // ── демо ──
    fun startDemo() {
        m.demo = true; demoOn = true
        m.demoStep = 0; m.demoT = 0f
        m.handFromX = W() * 0.5f; m.handFromY = H() * 0.6f
        m.handX = m.handFromX; m.handY = m.handFromY
        setHint("Смотри, как это делается!")
    }

    fun demoMove(tx: Float, ty: Float, dur: Float, dt: Float): Boolean {
        m.demoT += dt
        val u = ease(m.demoT / dur)
        m.handX = lerpF(m.handFromX, tx, u)
        m.handY = lerpF(m.handFromY, ty, u)
        return m.demoT >= dur
    }
    fun demoNext(step: Int) { m.demoStep = step; m.demoT = 0f; m.handFromX = m.handX; m.handFromY = m.handY }

    fun updateDemo(dt: Float) {
        if (!m.demo) return
        val order = intArrayOf(0, 2, 4)
        when (m.phase) {
            PH_CHOOSE -> {
                val k = m.demoStep / 3
                val phase = m.demoStep % 3
                if (k >= 3) { return }
                val idx = order[k]
                when (phase) {
                    0 -> { m.handDown = false; if (demoMove(slotX(idx), slotY(), 0.75f, dt)) demoNext(m.demoStep + 1) }
                    1 -> { m.handDown = true; m.demoT += dt; if (m.demoT > 0.22f) { throwIng(KcIng.entries[idx], slotX(idx), slotY(), false); demoNext(m.demoStep + 1) } }
                    else -> { m.handDown = false; m.demoT += dt; if (m.demoT > 0.5f) demoNext(m.demoStep + 1) }
                }
            }
            PH_BOIL -> {
                val g = pot()
                m.handDown = true
                m.demoStirAng += dt * 5.5f
                m.handX = g.cx + cos(m.demoStirAng) * g.rw * 0.5f
                m.handY = g.rimY + sin(m.demoStirAng) * g.ry * 0.55f
                if (!toddler) addStir(dt * 5.5f, m.handX, m.handY)
                if (m.heat >= 0.88f) { m.stirring = true; takeOut() }
            }
            PH_FEED -> {
                val mp = mouthPos()
                when (m.demoStep) {
                    0, 1 -> { m.handDown = false; m.demoT += dt; val u = ease(m.demoT / 0.7f); m.handX = lerpF(m.handFromX, m.pelX, u); m.handY = lerpF(m.handFromY, m.pelY, u); if (m.demoT > 0.8f) demoNext(2) }
                    2 -> {
                        m.handDown = true; m.demoT += dt
                        val u = ease(m.demoT / 1.0f)
                        m.handX = lerpF(m.handFromX, mp.x, u); m.handY = lerpF(m.handFromY, mp.y + m.pelS * 0.4f, u)
                        m.pelX = m.handX; m.pelY = m.handY - m.pelS * 0.4f
                        if (m.demoT > 1.05f) { startFeed(); m.demoStep = 3 }
                    }
                }
            }
            else -> { m.handDown = false }
        }
    }

    // ── обновление кадра ──
    fun update(dt: Float) {
        if (size.width == 0) return
        val w = W(); val h = H()
        val g = pot()
        m.clock += dt
        m.phaseT += dt
        if (!m.inited) {
            m.inited = true
            m.pelS = pelSize()
            startRound()
        }
        // бездействие → автодемо
        if (!m.demo && m.phase == PH_CHOOSE && m.ings.isEmpty() && m.dragIng == null && !showAlbum && rewardSkin == null &&
            m.clock - m.lastTouch > (if (toddler) 5f else 9f)
        ) startDemo()
        if (m.demo && m.phase == PH_CHOOSE && m.demoStep >= 9 && m.flies.isEmpty() && m.ings.size >= 3) { /* ждём перехода варки */ }
        updateDemo(dt)

        // пружина кастрюли
        m.potVel += (-m.potSquash * 90f - m.potVel * 7f) * dt
        m.potSquash += m.potVel * dt
        m.potShake = max(0f, m.potShake - dt * 2.5f)
        m.ladleVis = max(0f, m.ladleVis - dt * (if (m.stirring) 0f else 2.2f))

        // летящие ингредиенты
        val it = m.flies.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.t += dt
            f.rot += f.spin * dt
            if (f.t >= f.dur) { landIngredient(f); it.remove() }
        }

        // варка
        val boiling = m.phase == PH_BOIL
        if (boiling) {
            m.boilGain = max(0f, m.boilGain - dt * 0.35f)
            val passive = if (toddler) 1f / 3.4f else 0.028f
            m.heat += passive * dt
            if (toddler && m.heat >= 0.92f) { m.stirring = true; takeOut() }
            else if (m.heat >= 1.45f) { m.stirring = true; takeOut() }
            // звук бульканья
            val inten = 0.35f + min(1f, m.heat) * 0.5f + m.boilGain * 0.3f
            if (m.clock > m.bubSfxAt) {
                m.bubSfxAt = m.clock + max(0.22f, 0.65f - inten * 0.4f) + Random.nextFloat() * 0.15f
                snd(if (Random.nextFloat() < 0.35f) "kc_boil" else "kc_bub" + Random.nextInt(1, 5), 0.35f + inten * 0.4f, 0.9f + Random.nextFloat() * 0.3f)
            }
            if (m.heat >= 0.75f && !m.stirring) setHint(if (toddler) "Бульк-бульк! Варится…" else "Готово! Нажми на кастрюлю, чтобы достать")
            else if (!toddler && m.heat < 0.75f) setHint("Води пальцем по кастрюле — мешай бульон!")
            if (!toddler && m.heat >= 1.2f) setHint("Ой, пельмешек переваривается! Доставай!")
        }
        val inten = if (boiling) 0.35f + min(1.2f, m.heat) * 0.55f + m.boilGain * 0.25f else if (m.ings.isNotEmpty()) 0.2f else 0f
        // пузыри
        if (inten > 0f && Random.nextFloat() < inten * dt * 11f && m.bubbles.size < 22) {
            m.bubbles.add(KcBubble((Random.nextFloat() - 0.5f) * 1.5f, (Random.nextFloat() - 0.5f) * 1.1f, 0.1f + Random.nextFloat() * 0.13f))
        }
        val bi = m.bubbles.iterator()
        while (bi.hasNext()) {
            val b = bi.next()
            b.age += dt * (1.6f + inten)
            if (b.age >= 1f) {
                burst(0, g.cx + b.ox * g.rw * 0.55f, g.brothY + b.oy * g.ry * 0.5f, 3, Color(0xFFD8EEFF), g.rw * 0.5f, g.rw * 0.02f, up = g.rw * 0.9f, life = 0.45f)
                bi.remove()
            }
        }
        // пар
        val steamRate = if (boiling) 4f + min(1.3f, m.heat) * 6f else if (m.ings.isNotEmpty() && m.phase < PH_EXTRACT) 1.5f else 0f
        if (steamRate > 0f && Random.nextFloat() < steamRate * dt) {
            val over = m.heat > 1.2f
            m.parts.add(KcPart(1, g.cx + (Random.nextFloat() - 0.5f) * g.rw * 1.2f, g.rimY - g.ry * 0.3f, (Random.nextFloat() - 0.5f) * g.rw * 0.15f, -g.rw * (0.35f + Random.nextFloat() * 0.3f), 1.6f, 1.6f, g.rw * (0.14f + Random.nextFloat() * 0.1f), if (over) Color(0xFFB8B8C0) else Color(0xFFFFFFFF)))
        }

        // фазы
        when (m.phase) {
            PH_EXTRACT -> {
                m.extractT += dt / 1.1f
                val u = m.extractT.coerceIn(0f, 1f)
                val hp = hoverPos()
                val e = ease(u)
                m.pelX = lerpF(m.extractFromX, hp.x, e)
                m.pelY = lerpF(m.extractFromY, hp.y, e) - sin(u * PI.toFloat()) * h * 0.1f
                m.pelS = lerpF(pelSize() * 0.6f, pelSize() * 1.45f, e)
                m.pelTilt = (1f - e) * 25f
                if (m.extractT >= 1f && m.phaseT < 1.2f) { /* ждём */ }
                if (m.extractT >= 1f && !m.pelHome) {
                    m.pelHome = true // флаг «реакция проиграна»
                    when (m.result) {
                        KcResult.PERFECT -> { snd("kc_bell"); snd("kc_sparkle", 0.7f); burst(2, m.pelX, m.pelY, 18, Color(0xFFFFE066), w * 0.45f, w * 0.03f, life = 1.0f); haptics.vibratePop() }
                        KcResult.UNDER -> { snd("kc_sad") }
                        KcResult.OVER -> { snd("kc_sigh") }
                        null -> {}
                    }
                }
                if (m.extractT >= 1f && m.phaseT > 2.3f) {
                    m.phase = PH_TO_FEED; m.phaseT = 0f; m.pelHome = false
                    m.sceneT = 0f
                    snd("kc_whoosh", 0.6f, 0.8f)
                }
            }
            PH_TO_FEED -> {
                m.sceneT = min(1f, m.phaseT / 1.0f)
                val e = ease(m.sceneT)
                val pp = platePos()
                val hp = hoverPos()
                m.pelX = lerpF(hp.x, pp.x, e)
                m.pelY = lerpF(hp.y, pp.y - pelSize() * 0.3f, e)
                m.pelS = lerpF(pelSize() * 1.45f, pelSize() * 1.3f, e)
                m.pelTilt = 0f
                if (m.sceneT >= 1f) { m.phase = PH_FEED; m.phaseT = 0f; setHint("Покорми мишку — перетащи пельмешка ему в рот!") ; m.demoStep = 0; m.demoT = 0f; m.handFromX = m.handX; m.handFromY = m.handY }
            }
            PH_FEED -> {
                m.sceneT = 1f
                if (m.pelHome && !m.dragPel) {
                    m.pelX += (m.pelGoalX - m.pelX) * min(1f, dt * 10f)
                    m.pelY += (m.pelGoalY - m.pelY) * min(1f, dt * 10f)
                }
                // мишка открывает рот, когда пельмень рядом
                val mp = mouthPos()
                val dist = hypot(m.pelX - mp.x, m.pelY - mp.y)
                val want = if (m.dragPel && dist < bearS() * 1.5f) 1f else if (m.dragPel) 0.35f else 0f
                m.mouthOpen += (want - m.mouthOpen) * min(1f, dt * 9f)
                if (m.demo == false && m.phaseT > 6f && !m.dragPel) setHint("Перетащи пельмешка мишке в рот — или просто нажми на него")
            }
            PH_CHEW -> {
                val mp = mouthPos()
                m.sceneT = 1f
                // пельмень летит в рот и уменьшается
                val u = ease(m.chewT / 0.3f)
                m.pelX += (mp.x - m.pelX) * min(1f, dt * 14f)
                m.pelY += (mp.y - m.pelY) * min(1f, dt * 14f)
                m.pelS = lerpF(m.pelS, pelSize() * 0.2f, min(1f, dt * 8f))
                m.chewT += dt
                m.mouthOpen = if (m.chewT < 0.35f) 1f else (0.5f + 0.5f * sin(m.chewT * 22f)) * 0.6f
                if (m.chewT > 0.3f) m.pelAlpha = 0f
                val hits = (m.chewT / 0.42f).toInt()
                if (m.chewT > 0.35f && hits > m.chewHits && m.chewHits < 4) {
                    m.chewHits = hits
                    snd(if (hits % 2 == 0) "kc_munch1" else "kc_munch2", 0.9f, 0.95f + Random.nextFloat() * 0.1f)
                    haptics.vibrateShort()
                    val bc = bearC()
                    burst(5, mp.x, mp.y + bearS() * 0.1f, 4, Color(0xFFFFE9B8), bearS() * 0.8f, bearS() * 0.05f, up = bearS() * 0.6f, life = 0.5f)
                }
                if (m.chewT > 2.0f) {
                    m.phase = PH_CELEBRATE; m.phaseT = 0f; m.mouthOpen = 0f
                    snd("kc_yum")
                    val good = m.result == KcResult.PERFECT
                    val bc = bearC()
                    m.bearWink = good
                    m.stars = if (good) 3 else if (m.result == KcResult.OVER) 2 else 1
                    burst(3, bc.x, bc.y - bearS() * 0.5f, if (good) 16 else 8, Color(0xFFFF5D8F), bearS() * 1.6f, bearS() * 0.12f, up = bearS() * 1.2f, life = 1.4f)
                    burst(2, bc.x, bc.y, if (good) 20 else 10, Color(0xFFFFD83D), bearS() * 2f, bearS() * 0.08f, up = bearS() * 0.8f, life = 1.3f)
                    if (good) burst(4, bc.x, bc.y - bearS(), 26, Color(0xFF6FCF97), bearS() * 2.2f, bearS() * 0.06f, up = bearS() * 1.6f, life = 1.6f)
                    snd("kc_sparkle", 0.8f)
                    haptics.vibratePop()
                    finishRound()
                }
            }
            PH_CELEBRATE -> {
                m.cheer = min(1f, m.cheer + dt * 4f)
                m.jump = abs(sin(m.phaseT * 7f)) * max(0f, 1f - m.phaseT / 1.8f)
                if (m.phaseT > 1.9f && rewardSkin == null && !showAlbum) {
                    m.phase = PH_TO_POT; m.phaseT = 0f
                    snd("kc_whoosh", 0.5f, 1.1f)
                }
            }
            PH_TO_POT -> {
                m.cheer = max(0f, m.cheer - dt * 3f)
                m.sceneT = max(0f, 1f - m.phaseT / 0.9f)
                if (m.sceneT <= 0f) { m.cheer = 0f; m.bearWink = false; startRound() }
            }
        }
        // мигание мишки
        m.blinkT -= dt
        if (m.blinkT < -2.6f - Random.nextFloat() * 2f) m.blinkT = 0.14f

        // частицы
        val pi = m.parts.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            p.life -= dt
            if (p.life <= 0f) { pi.remove(); continue }
            when (p.kind) {
                0, 5 -> { p.vy += h * 2.1f * dt }
                1 -> { p.vx *= 0.99f }
                2, 3, 4 -> { p.vy += h * (if (p.kind == 3) 0.1f else 0.9f) * dt; p.vx *= 0.995f }
            }
            p.x += p.vx * dt; p.y += p.vy * dt; p.rot += p.vr * dt
        }
        if (m.parts.size > 400) repeat(60) { m.parts.removeAt(0) }
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
            .onSizeChanged { size = it }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (showAlbum || rewardSkin != null) return@awaitEachGesture
                        onDown(down.position)
                        var lastPos = down.position
                        var ended = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id }
                            if (ch == null) break
                            lastPos = ch.position
                            if (ch.changedToUp()) { onUp(ch.position); ended = true; break }
                            if (ch.positionChanged()) { onMove(ch.position); ch.consume() }
                        }
                        if (!ended) onUp(lastPos)
                    }
                }
        ) {
            frame.hashCode()
            val w = size.width
            val h = size.height
            if (w <= 0f) return@Canvas
            val t = m.clock
            val g = pot()
            val e = ease(m.sceneT)

            // сцена с кастрюлей
            withTranslate(-e * w) {
                kcWall(w, h, t)
                kcCounter(w, h, g.bottom + g.rw * 0.12f)
                val boilK = if (m.phase == PH_BOIL) 0.35f + min(1.2f, m.heat) * 0.55f else if (m.ings.isNotEmpty() && m.phase < PH_EXTRACT) 0.25f else 0.1f
                kcStove(g, boilK, t)
                // кастрюля с пружиной
                val sq = m.potSquash.coerceIn(-0.4f, 0.4f)
                val shake = if (m.potShake > 0f) sin(t * 60f) * m.potShake * g.rw * 0.03f else 0f
                val rattle = if (m.phase == PH_BOIL && m.heat > 1.0f) sin(t * 45f) * g.rw * 0.012f * (m.heat - 0.9f) else 0f
                withTransformPot(g, 1f + sq * 0.05f, 1f - sq * 0.07f, shake + rattle) {
                    kcPotBody(g, brothColor(), boilK, t)
                    // поплавки и пузыри
                    kcBrothClip(g) {
                        for (f in m.floats) {
                            val bx = g.cx + f.ox * g.rw * 0.5f + sin(t * 1.3f + f.ph) * g.rw * 0.04f
                            val by = g.rimY + f.oy * g.ry * 0.5f + sin(t * 2f + f.ph) * g.ry * 0.05f
                            kcIngredient(f.type, Offset(bx, by), g.rw * 0.17f, sin(t + f.ph) * 20f)
                        }
                        for (b in m.bubbles) {
                            val bx = g.cx + b.ox * g.rw * 0.55f
                            val by = g.brothY + b.oy * g.ry * 0.5f
                            val r = b.maxR * g.rw * 0.3f * (0.3f + 0.7f * min(1f, b.age * 1.6f)) * (if (b.age > 0.88f) 1.25f else 1f)
                            drawOval(Color.White.copy(alpha = 0.28f), Offset(bx - r, by - r * 0.62f), Size(r * 2f, r * 1.24f))
                            drawOval(Color.White.copy(alpha = 0.85f), Offset(bx - r, by - r * 0.62f), Size(r * 2f, r * 1.24f), style = Stroke(r * 0.18f))
                            drawCircle(Color.White.copy(alpha = 0.9f), r * 0.2f, Offset(bx - r * 0.4f, by - r * 0.2f))
                        }
                        // половник
                        if (m.ladleVis > 0.02f) { /* рисуется ниже, поверх кромки */ }
                    }
                    kcAboveBroth(g) {
                        for (f in m.flies) {
                            val u = (f.t / f.dur).coerceIn(0f, 1f)
                            val x = lerpF(f.x0, f.x1, u)
                            val y = lerpF(f.y0, f.y1, u) - sin(u * PI.toFloat()) * f.arc * 1.0f
                            kcIngredient(f.type, Offset(x, y), slotR() * (1f - 0.22f * u), f.rot)
                        }
                    }
                    kcPotLip(g)
                }
                // половник поверх кромки
                if (m.ladleVis > 0.02f && m.phase == PH_BOIL) {
                    val lx = m.ladleX.coerceIn(g.cx - g.rw * 0.9f, g.cx + g.rw * 0.9f)
                    val ly = m.ladleY.coerceIn(g.rimY - g.ry * 0.7f, g.rimY + g.ry * 0.8f)
                    kcLadle(Offset(lx, ly), g.rw * 1.9f, 16f + sin(t * 8f) * 4f)
                }
                // подсказка «пора доставать»
                if (m.phase == PH_BOIL && m.heat in 0.75f..1.3f) {
                    val k = 0.5f + 0.5f * sin(t * 7f)
                    drawCircle(Color(0xFF6FCF97).copy(alpha = 0.25f + 0.25f * k), g.rw * (1.1f + 0.1f * k), Offset(g.cx, g.rimY), style = Stroke(g.rw * 0.06f))
                }
            }

            // сцена кормления
            withTranslate((1f - e) * w) {
                kcWall(w, h, t)
                kcTable(w, h, h * 0.58f)
                val bc = bearC()
                val bs = bearS()
                val blink = m.blinkT > 0f && m.blinkT < 0.14f
                kcBear(Offset(bc.x, bc.y - m.jump * bs * 0.25f), bs, t, m.mouthOpen, if (m.phase == PH_CHEW) (0.5f + 0.5f * sin(m.chewT * 22f)) else 0f, blink, m.cheer, m.bearWink && m.phase == PH_CELEBRATE)
                kcPlate(platePos(), plateR())
            }

            // верхняя панель: слоты ингредиентов / шкала варки
            val hudY = h * 0.055f
            val sr = min(w * 0.045f, h * 0.034f)
            if (m.phase <= PH_BOIL) {
                for (i in 0 until 3) {
                    val cx = w * 0.5f + (i - 1) * sr * 2.5f
                    drawCircle(Color.White.copy(alpha = 0.75f), sr, Offset(cx, hudY))
                    drawCircle(Color(0xFFB8975F), sr, Offset(cx, hudY), style = Stroke(sr * 0.14f))
                    if (i < m.ings.size) kcIngredient(m.ings[i], Offset(cx, hudY), sr * 0.62f)
                }
            }
            if (m.phase == PH_BOIL && !toddler) {
                val bw = w * 0.5f
                val bx = w * 0.25f
                val by = hudY + sr * 1.7f
                val bh = sr * 0.55f
                drawRoundRect(Color.White.copy(alpha = 0.8f), Offset(bx, by), Size(bw, bh), androidx.compose.ui.geometry.CornerRadius(bh / 2))
                val full = 1.45f
                drawRoundRect(Color(0xFF6FCF97).copy(alpha = 0.55f), Offset(bx + bw * 0.75f / full, by), Size(bw * (1.2f - 0.75f) / full, bh), androidx.compose.ui.geometry.CornerRadius(bh / 2))
                val fw = bw * min(1f, m.heat / full)
                if (fw > 1f) drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFFFD35C), Color(0xFFFF8C3C), Color(0xFFE84545)), bx, bx + bw), Offset(bx, by), Size(fw, bh), androidx.compose.ui.geometry.CornerRadius(bh / 2))
                drawRoundRect(Color(0xFFB8975F), Offset(bx, by), Size(bw, bh), androidx.compose.ui.geometry.CornerRadius(bh / 2), style = Stroke(bh * 0.14f))
            }

            // полка с ингредиентами
            if (e < 0.999f) {
                withTranslate(-e * w) {
                    kcShelf(w, h, shelfTop())
                    val sr2 = slotR()
                    for (i in 0 until 5) {
                        val cx = slotX(i)
                        kcBowl(Offset(cx, h * 0.915f), min(w / 5f * 0.4f, h * 0.06f))
                        val dragging = m.dragIng == KcIng.entries[i] && m.moved
                        val bob = sin(t * 2f + i) * sr2 * 0.04f
                        val active = m.phase == PH_CHOOSE
                        val sc = if (active) 1f else 0.85f
                        if (!dragging || true) kcIngredient(KcIng.entries[i], Offset(cx, slotY() + bob), sr2 * sc, 0f)
                    }
                }
            }
            // перетаскиваемый ингредиент
            val di = m.dragIng
            if (di != null && m.moved && m.phase == PH_CHOOSE) {
                drawCircle(Color(0x22000000), slotR() * 0.9f, Offset(m.dragX + 6f, m.dragY + slotR() * 0.9f))
                kcIngredient(di, Offset(m.dragX, m.dragY - slotR() * 0.4f), slotR() * 1.25f, sin(t * 6f) * 6f)
            }

            // пельмень (поверх обеих сцен)
            if (m.pelAlpha > 0f && m.phase >= PH_EXTRACT && m.phase <= PH_CHEW) {
                val sk = skinId
                val blinkP = (t * 1.3f) % 4f < 0.12f
                val squash = if (m.phase == PH_FEED && m.dragPel) 1.06f else 1f + 0.04f * sin(t * 3.2f)
                if (m.phase == PH_FEED || m.phase == PH_TO_FEED) {
                    // подсветка «возьми меня»
                    if (m.phase == PH_FEED && !m.dragPel) drawCircle(Color(0xFFFFE066).copy(alpha = 0.25f + 0.15f * sin(t * 5f)), m.pelS * 1.5f, Offset(m.pelX, m.pelY))
                }
                kcPelmen(Offset(m.pelX, m.pelY), m.pelS, m.result, sk, t, blinkP, if (m.phase == PH_CHEW) 0.6f else 0f, squash, m.pelTilt)
            }

            // частицы
            for (p in m.parts) {
                val a = (p.life / p.max).coerceIn(0f, 1f)
                when (p.kind) {
                    0 -> drawCircle(p.color.copy(alpha = a), p.size, Offset(p.x, p.y))
                    5 -> drawCircle(p.color.copy(alpha = a), p.size, Offset(p.x, p.y))
                    1 -> drawCircle(p.color.copy(alpha = 0.45f * a), p.size * (1.6f - a * 0.8f), Offset(p.x, p.y))
                    2 -> drawSparkle(Offset(p.x, p.y), p.size * (0.5f + a), p.color.copy(alpha = a), p.rot)
                    3 -> drawHeart(Offset(p.x, p.y), p.size, p.color.copy(alpha = a))
                    4 -> withRot(Offset(p.x, p.y), p.rot * 40f) { drawRect(p.color.copy(alpha = a), Offset(-p.size * 0.5f, -p.size * 0.25f), Size(p.size, p.size * 0.5f)) }
                }
            }

            // рука-подсказка
            if (m.demo) kcHand(Offset(m.handX, m.handY), min(w, h) * 0.035f, m.handDown)
        }

        // кнопка альбома
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 12.dp)
                .size(60.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFC77DD9), Color(0xFF9B59B6))))
                .border(3.dp, Color(0xFFFFF1D8), CircleShape)
                .clickable { stopDemo(); m.lastTouch = m.clock; showAlbum = true; snd("kc_clink", 0.8f, 1.3f) },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(44.dp)) { kcPelmen(Offset(size.width / 2, size.height * 0.55f), size.width * 0.36f, KcResult.PERFECT, skinId, 0f) }
        }
        // счётчик приготовленных
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 74.dp, end = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xCCFFF1D8))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(Modifier.size(16.dp)) { drawSparkle(Offset(size.width / 2, size.height / 2), size.width * 0.5f, Color(0xFFFFB300), 0f) }
            Spacer(Modifier.width(4.dp))
            Text("$cooked", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF7A4E2E))
        }
        // подсказка
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 104.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xE6FFFFFF))
                .border(2.dp, Color(0xFFE3C08C), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(hintText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7A4E2E), textAlign = TextAlign.Center)
        }
        if (demoOn) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE65FA8E8))
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            ) { Text("Просто смотри — потрогай экран, чтобы играть", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White) }
        }

        // альбом
        if (showAlbum) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x99301A0A))
                    .clickable { showAlbum = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFFFF6E4))
                        .border(5.dp, Color(0xFFF2B84B), RoundedCornerShape(28.dp))
                        .clickable(enabled = false) {}
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Альбом пельменей", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF7A4E2E))
                    Text("Приготовлено: $cooked", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8C5A2F))
                    Spacer(Modifier.height(10.dp))
                    KC_SKINS.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { sk ->
                                val unlocked = cooked >= sk.need
                                val selected = skinId == sk.id
                                Column(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .padding(vertical = 5.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (selected) Color(0xFFFFE9A8) else Color(0xFFF3DDBA))
                                        .border(if (selected) 3.dp else 1.dp, if (selected) Color(0xFFE8A325) else Color(0xFFD3AC74), RoundedCornerShape(18.dp))
                                        .clickable(enabled = unlocked) {
                                            skinId = sk.id
                                            prefs.edit().putString("skin", sk.id).apply()
                                            snd("kc_sparkle", 0.6f)
                                        }
                                        .padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                                        if (unlocked) {
                                            Canvas(Modifier.fillMaxSize()) { kcPelmen(Offset(size.width / 2, size.height * 0.55f), size.width * 0.3f, KcResult.PERFECT, sk.id, 1.2f) }
                                        } else {
                                            Canvas(Modifier.fillMaxSize()) {
                                                drawCircle(Color(0x33000000), size.width * 0.34f, Offset(size.width / 2, size.height / 2))
                                                drawRoundRect(Color(0xFF8C5A2F), Offset(size.width * 0.36f, size.height * 0.46f), Size(size.width * 0.28f, size.height * 0.22f), androidx.compose.ui.geometry.CornerRadius(6f))
                                                drawArc(Color(0xFF8C5A2F), 180f, 180f, false, Offset(size.width * 0.4f, size.height * 0.3f), Size(size.width * 0.2f, size.height * 0.2f), style = Stroke(5f))
                                            }
                                        }
                                    }
                                    Text(if (unlocked) sk.name else "Ещё ${sk.need - cooked}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7A4E2E), textAlign = TextAlign.Center, maxLines = 2)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Нажми на пельмешка, чтобы выбрать наряд", fontSize = 11.sp, color = Color(0xFF8C5A2F), textAlign = TextAlign.Center)
                }
            }
        }

        // награда
        val rs = rewardSkin
        if (rs != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xAA301A0A))
                    .clickable { rewardSkin = null; m.lastTouch = m.clock },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color(0xFFFFF6E4))
                        .border(5.dp, Color(0xFFF2B84B), RoundedCornerShape(30.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Новая наклейка!", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF9B59B6))
                    var tt by remember { mutableFloatStateOf(0f) }
                    LaunchedEffect(Unit) {
                        var last = 0L
                        while (true) androidx.compose.runtime.withFrameNanos { n -> if (last != 0L) tt += (n - last) / 1e9f; last = n }
                    }
                    Canvas(Modifier.size(190.dp)) {
                        for (i in 0 until 8) {
                            val a = tt * 0.7f + i * PI.toFloat() / 4f
                            drawCircle(Color(0x55FFD83D), size.width * 0.04f, Offset(size.width / 2 + cos(a) * size.width * 0.46f, size.height / 2 + sin(a) * size.height * 0.46f))
                        }
                        kcPelmen(Offset(size.width / 2, size.height * 0.56f), size.width * 0.32f, KcResult.PERFECT, rs.id, tt, mouth = 0f, squashY = 1f + 0.04f * sin(tt * 4f))
                    }
                    Text(rs.name, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF7A4E2E))
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.verticalGradient(listOf(Color(0xFF52D36E), Color(0xFF2BA84A))))
                            .padding(horizontal = 28.dp, vertical = 10.dp)
                    ) { Text("Ура!", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White) }
                }
            }
        }
    }
}

// ───────────────────────── вспомогательные рисовалки ─────────────────────────

private inline fun DrawScope.withTranslate(dx: Float, block: DrawScope.() -> Unit) {
    androidx.compose.ui.graphics.drawscope.withTransform({ translate(dx, 0f) }) { block() }
}

private inline fun DrawScope.withRot(c: Offset, deg: Float, block: DrawScope.() -> Unit) {
    androidx.compose.ui.graphics.drawscope.withTransform({ translate(c.x, c.y); rotate(deg, Offset.Zero) }) { block() }
}

private inline fun DrawScope.withTransformPot(g: KcPot, sx: Float, sy: Float, dx: Float, block: DrawScope.() -> Unit) {
    androidx.compose.ui.graphics.drawscope.withTransform({
        translate(dx, 0f)
        scale(sx, sy, Offset(g.cx, g.bottom))
    }) { block() }
}

private fun DrawScope.drawSparkle(c: Offset, r: Float, color: Color, rot: Float) {
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

private fun DrawScope.drawHeart(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + r * 0.9f)
        cubicTo(c.x - r * 1.6f, c.y - r * 0.2f, c.x - r * 0.7f, c.y - r * 1.3f, c.x, c.y - r * 0.4f)
        cubicTo(c.x + r * 0.7f, c.y - r * 1.3f, c.x + r * 1.6f, c.y - r * 0.2f, c.x, c.y + r * 0.9f)
        close()
    }
    drawPath(p, color)
    drawCircle(Color.White.copy(alpha = color.alpha * 0.6f), r * 0.16f, Offset(c.x - r * 0.5f, c.y - r * 0.5f))
}
