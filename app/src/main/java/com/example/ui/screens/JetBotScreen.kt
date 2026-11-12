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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.SoundManager
import com.example.shared.i18n.Text
import com.example.shared.rememberHaptics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Комната «Ракетный робот» (индекс 50). Идея взята из веб-игры Supersonic-bot (там управление голосом),
// здесь она переписана на Compose Canvas: без интернета, без микрофона, все картинки нарисованы кодом.
// Управление: палец на экране — ракетный ранец тянет робота вверх, отпустил — падает. Правила — в JetBotRules.kt (с тестами).
// Малышам: стены пещеры безопасны, три сердечка, после удара — «призрак» на пару секунд, щит спасает от удара.

private const val JB_PREFS = "jetbot_prefs"
private const val JB_READY = 0
private const val JB_PLAY = 1
private const val JB_WON = 2
private const val JB_LOST = 3

private class JbPart(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val size: Float, val color: Color)

private class JbRef {
    var world: JetWorld? = null
    val parts = ArrayList<JbPart>()
    var touching = false
    var anim = 0f
}

private val JB_GEAR: Path by lazy {
    Path().apply {
        fillType = PathFillType.EvenOdd
        val n = 8
        for (i in 0 until n) {
            val a = i * 2.0 * PI / n
            fun p(da: Double, r: Double) = Offset((cos(a + da) * r).toFloat(), (sin(a + da) * r).toFloat())
            val pts = listOf(p(-0.20, 0.78), p(-0.12, 1.0), p(0.12, 1.0), p(0.20, 0.78))
            for ((k, o) in pts.withIndex()) if (i == 0 && k == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
        }
        close()
        addOval(Rect(Offset.Zero, 0.34f))
    }
}

private val JB_STAR: Path by lazy {
    Path().apply {
        for (i in 0 until 10) {
            val a = -PI / 2 + i * PI / 5
            val r = if (i % 2 == 0) 1.0 else 0.46
            val x = (cos(a) * r).toFloat(); val y = (sin(a) * r).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

private val JB_SHIELD: Path by lazy {
    Path().apply {
        moveTo(0f, -1f); lineTo(0.85f, -0.55f); lineTo(0.7f, 0.35f); lineTo(0f, 1f)
        lineTo(-0.7f, 0.35f); lineTo(-0.85f, -0.55f); close()
    }
}

@Composable
fun JetBotScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val sm = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val prefs = remember { context.getSharedPreferences(JB_PREFS, Context.MODE_PRIVATE) }
    val ref = remember { JbRef() }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableIntStateOf(JB_READY) }
    var livesUi by remember { mutableIntStateOf(JetCfg.LIVES) }
    var progressUi by remember { mutableFloatStateOf(0f) }
    var levelUi by remember { mutableIntStateOf(0) }
    var shieldUi by remember { mutableStateOf(false) }
    var boostUi by remember { mutableStateOf(false) }
    var bestUi by remember { mutableIntStateOf(prefs.getInt("best", 0)) }

    LaunchedEffect(Unit) {
        sm.preloadRaw(listOf("fx_bd_hit", "fx_bd_pickup", "fx_bd_drop", "fx_bd_star", "fx_bd_reward", "fx_bd_whoosh"))
    }

    fun snd(n: String, v: Float = 1f, r: Float = 1f) { sm.playFxRaw(n, v, r) }

    fun logicalH(): Float = if (boxSize.width > 0) boxSize.height * JET_LW / boxSize.width else 780f

    fun burst(x: Float, y: Float, n: Int, colors: List<Color>, speed: Float) {
        for (i in 0 until n) {
            if (ref.parts.size > 300) break
            val a = Random.nextFloat() * 6.283f
            val sp = speed * (0.3f + Random.nextFloat() * 0.7f)
            ref.parts.add(JbPart(x, y, cos(a) * sp, sin(a) * sp, 1f, 3f + Random.nextFloat() * 5f, colors[Random.nextInt(colors.size)]))
        }
    }

    fun saveBest(pct: Int) {
        if (pct > bestUi) {
            bestUi = pct
            try { prefs.edit().putInt("best", pct).apply() } catch (_: Exception) { /* рекорд не критичен */ }
        }
    }

    fun startRun() {
        ref.parts.clear()
        ref.touching = false
        ref.world = JetWorld(logicalH())
        livesUi = JetCfg.LIVES; progressUi = 0f; levelUi = 0; shieldUi = false; boostUi = false
        phase = JB_PLAY
        snd("fx_bd_whoosh", 0.7f)
    }

    fun handleEvents(w: JetWorld) {
        if (w.events.isEmpty()) return
        val evs = w.events.toList()
        w.events.clear()
        for (e in evs) {
            when (e.type) {
                JetEvent.HIT -> {
                    snd("fx_bd_hit", 0.8f); haptics.vibratePop()
                    burst(e.x, e.y, 16, listOf(Color(0xFFFFB74D), Color(0xFFFF7043), Color.White), 220f)
                }
                JetEvent.SHIELD_BREAK -> {
                    snd("fx_bd_hit", 0.6f, 1.4f); haptics.vibrateShort()
                    burst(e.x, e.y, 14, listOf(Color(0xFF80DEEA), Color(0xFF00D2FF), Color.White), 200f)
                }
                JetEvent.POWER_SHIELD -> { snd("fx_bd_pickup", 0.9f); haptics.vibrateShort(); burst(e.x, e.y, 12, listOf(Color(0xFF00D2FF), Color.White), 160f) }
                JetEvent.POWER_BOOST -> { snd("fx_bd_pickup", 0.9f, 1.25f); haptics.vibrateShort(); burst(e.x, e.y, 12, listOf(Color(0xFFFFEB3B), Color.White), 160f) }
                JetEvent.LEVEL -> snd("fx_bd_star", 0.8f)
                JetEvent.WIN -> {
                    snd("fx_bd_reward", 0.95f); haptics.vibratePop()
                    burst(w.lw / 2f, w.lh / 2f, 80, listOf(Color(0xFFFFD54F), Color(0xFFFF8BCB), Color(0xFF4DD0E1), Color.White), 420f)
                    saveBest(100)
                    phase = JB_WON
                }
                JetEvent.LOST -> {
                    snd("fx_bd_drop", 0.8f)
                    saveBest((w.progress() * 100f).toInt())
                    phase = JB_LOST
                }
            }
        }
    }

    fun tick(dt: Float) {
        ref.anim += dt
        val w = ref.world ?: return
        if (phase == JB_PLAY) {
            w.step(dt, ref.touching)
            handleEvents(w)
            if (w.hold > 0.25f && Random.nextFloat() < w.hold) {
                ref.parts.add(
                    JbPart(w.playerX + 4f, w.playerY + 30f + Random.nextFloat() * 10f, -(90f + Random.nextFloat() * 70f), (Random.nextFloat() - 0.5f) * 60f - w.vy * 0.2f,
                        0.7f, 3f + Random.nextFloat() * 4f * w.hold, if (w.boosting) Color.White else Color(0xFFFFB74D))
                )
            }
            if (w.lives != livesUi) livesUi = w.lives
            val pr = w.progress()
            if (pr != progressUi) progressUi = pr
            if (w.level != levelUi) levelUi = w.level
            if (w.shield != shieldUi) shieldUi = w.shield
            if (w.boosting != boostUi) boostUi = w.boosting
        }
        val it = ref.parts.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx * dt; p.y += p.vy * dt
            p.life -= dt * 1.4f
            if (phase == JB_WON) p.vy += 220f * dt
            if (p.life <= 0f) it.remove()
        }
        frame++
    }

    LaunchedEffect(boxSize) {
        if (boxSize.width <= 0 || boxSize.height <= 0) return@LaunchedEffect
        if (ref.world == null) ref.world = JetWorld(logicalH())
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min(0.05f, (now - last) / 1_000_000_000f)
                last = now
                tick(dt)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .testTag("jetbot_root")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        ref.touching = true
                        try {
                            do {
                                val e = awaitPointerEvent()
                            } while (e.changes.any { it.pressed })
                        } finally {
                            ref.touching = false
                        }
                    }
                }
        ) {
            frame.hashCode()
            val w = ref.world ?: return@Canvas
            if (size.width <= 0f) return@Canvas
            val s = size.width / JET_LW
            withTransform({ scale(s, s, Offset.Zero) }) { drawJetWorld(w, ref) }
        }

        // ── HUD ──
        if (phase == JB_PLAY) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for (i in 0 until JetCfg.LIVES) {
                        Text(if (i < livesUi) "❤️" else "🤍", fontSize = 26.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    if (shieldUi) Text("🛡️", fontSize = 26.sp)
                    if (boostUi) Text("⚡", fontSize = 26.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("${levelUi + 1}/${JET_LEVELS.size} · ${JET_LEVELS[levelUi].name}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0x66FFFFFF))
                ) {
                    Box(Modifier.fillMaxWidth(progressUi.coerceIn(0.02f, 1f)).fillMaxHeight().background(Color(0xFFFFD54F)))
                }
            }
        }

        // ── Экраны: старт, «ещё раз», победа ──
        if (phase != JB_PLAY) {
            Box(
                Modifier.fillMaxSize().background(Color(0x99101030)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFFFF3E0))
                        .border(4.dp, Color(0xFF3A0728), RoundedCornerShape(28.dp))
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val title = when (phase) {
                        JB_WON -> "Ура! Ты долетел! 🏆"
                        JB_LOST -> "Ой! Давай ещё раз 🤗"
                        else -> "Ракетный робот 🚀"
                    }
                    Text(title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color(0xFF3A0728), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    val body = when (phase) {
                        JB_WON -> "Золотой флаг взят! Все три пещеры пройдены."
                        JB_LOST -> "Ты пролетел ${(progressUi * 100f).toInt()}% пути. Получится ещё дальше!"
                        else -> "Держи палец на экране — робот летит вверх.\nОтпусти — робот падает.\nОблетай шестерёнки, лови щит 🛡️ и ускорение ⚡.\nДолети до золотого флага!"
                    }
                    Text(body, fontSize = 17.sp, color = Color(0xFF3A0728), textAlign = TextAlign.Center)
                    if (bestUi > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("Лучший полёт: $bestUi%", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA83C7B))
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 200.dp, minHeight = 64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF8BCB))
                            .border(4.dp, Color(0xFF3A0728), CircleShape)
                            .clickable { startRun() }
                            .padding(horizontal = 28.dp, vertical = 12.dp)
                            .testTag("jetbot_start"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when (phase) { JB_READY -> "Полетели!"; JB_WON -> "Лететь ещё"; else -> "Ещё раз" },
                            fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF3A0728)
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────── рисование (логические единицы, ширина = JET_LW) ─────────────────────────

private fun DrawScope.drawJetWorld(w: JetWorld, ref: JbRef) {
    val lh = w.lh
    val t = ref.anim
    // небо
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1B1F4B), Color(0xFF4A2A6B)), 0f, lh), Offset.Zero, Size(w.lw, lh))
    // звёзды (мигают, едут медленно)
    for (i in 0 until 36) {
        val sx = ((i * 97 % 360) - (w.dist * (0.05f + (i % 3) * 0.03f))) .mod(w.lw)
        val sy = (i * 53 % 1000) / 1000f * lh
        val tw = 0.45f + 0.45f * sin(t * 2f + i)
        drawCircle(Color.White, radius = 1.2f + (i % 3) * 0.6f, center = Offset(sx, sy), alpha = tw)
    }
    // дальние холмы
    val hills = Path()
    hills.moveTo(0f, lh)
    var hx = 0f
    while (hx <= w.lw + 20f) {
        val hy = lh - 70f - 40f * sin((hx + w.dist * 0.25f) / 55f) - 25f * sin((hx + w.dist * 0.25f) / 23f)
        hills.lineTo(hx, hy); hx += 20f
    }
    hills.lineTo(w.lw + 20f, lh); hills.close()
    drawPath(hills, Color(0xFF2A2F66), alpha = 0.55f)

    // эффект ускорения: полосы скорости
    if (w.boosting) {
        for (i in 0 until 8) {
            val yy = (i * 131 % 1000) / 1000f * lh
            val xx = (w.lw - ((t * 600f + i * 80f) % (w.lw + 80f)))
            drawLine(Color.White, Offset(xx, yy), Offset(xx + 60f, yy), 2f, StrokeCap.Round, alpha = 0.35f)
        }
    }

    // частицы позади
    for (p in ref.parts) drawCircle(p.color, p.size, Offset(p.x, p.y), alpha = p.life.coerceIn(0f, 1f))

    // бонусы
    for (p in w.pows) drawPowerUp(p, t)

    // препятствия
    for (o in w.obs) drawObstacle(o, t)

    // робот
    drawRobot(w, t)

    // пещера поверх всего, чтобы закрыть всё, что вылезло
    drawCave(w)
}

private fun DrawScope.drawCave(w: JetWorld) {
    val pts = w.cave
    if (pts.size < 2) return
    val top = Path(); top.moveTo(pts.first().x, -60f)
    val bot = Path(); bot.moveTo(pts.first().x, w.lh + 60f)
    for (p in pts) { top.lineTo(p.x, p.ceil); bot.lineTo(p.x, p.floor) }
    top.lineTo(pts.last().x, -60f); top.close()
    bot.lineTo(pts.last().x, w.lh + 60f); bot.close()
    val rock = Color(0xFF241B3D)
    drawPath(top, rock); drawPath(bot, rock)
    val rimTop = Path(); val rimBot = Path()
    pts.forEachIndexed { i, p ->
        if (i == 0) { rimTop.moveTo(p.x, p.ceil); rimBot.moveTo(p.x, p.floor) } else { rimTop.lineTo(p.x, p.ceil); rimBot.lineTo(p.x, p.floor) }
    }
    val rim = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(rimTop, Color(0xFFFF8BCB), style = rim)
    drawPath(rimBot, Color(0xFFFF8BCB), style = rim)
}

private fun DrawScope.drawRobot(w: JetWorld, t: Float) {
    if (w.invuln > 0f && ((w.clock * 12f).toInt() % 2 == 0)) return
    val px = w.playerX; val py = w.playerY
    val c = Offset(px + 20f, py + 20f)
    val tilt = (w.vy / JetCfg.TERMINAL) * 22f
    withTransform({ rotate(tilt, c) }) {
        // пламя ранца
        if (w.hold > 0.05f) {
            val fl = (10f + 22f * w.hold) * (0.85f + 0.15f * sin(t * 40f))
            val flame = Path().apply {
                moveTo(px - 6f, py + 22f); lineTo(px - 2f, py + 22f + fl); lineTo(px + 2f, py + 22f); close()
            }
            drawPath(flame, if (w.boosting) Color(0xFFE1F5FE) else Color(0xFFFF7043))
            val flame2 = Path().apply {
                moveTo(px - 4.5f, py + 22f); lineTo(px - 2f, py + 22f + fl * 0.6f); lineTo(px + 0.5f, py + 22f); close()
            }
            drawPath(flame2, Color(0xFFFFEB3B))
        }
        // ранец
        drawRoundRect(Color(0xFF9E9E9E), Offset(px - 8f, py + 8f), Size(14f, 24f), CornerRadius(5f))
        drawRoundRect(Color(0xFF616161), Offset(px - 8f, py + 8f), Size(14f, 24f), CornerRadius(5f), style = Stroke(2f))
        // ножки
        drawCircle(Color(0xFF00838F), 5f, Offset(px + 13f, py + 37f))
        drawCircle(Color(0xFF00838F), 5f, Offset(px + 29f, py + 37f))
        // антенна
        drawLine(Color(0xFF00838F), Offset(px + 22f, py + 6f), Offset(px + 22f, py - 4f), 2.5f, StrokeCap.Round)
        drawCircle(Color(0xFFFF5252), 3.6f, Offset(px + 22f, py - 5f))
        // тело
        drawRoundRect(Color(0xFF4DD0E1), Offset(px + 2f, py + 5f), Size(38f, 32f), CornerRadius(13f))
        drawRoundRect(Color(0xFF00838F), Offset(px + 2f, py + 5f), Size(38f, 32f), CornerRadius(13f), style = Stroke(2.5f))
        // стекло-визор
        drawRoundRect(Color(0xFF1A237E), Offset(px + 12f, py + 12f), Size(26f, 16f), CornerRadius(8f))
        val look = if (w.vy < -40f) -1.5f else if (w.vy > 60f) 1.5f else 0f
        drawCircle(Color.White, 3.4f, Offset(px + 21f, py + 20f + look))
        drawCircle(Color.White, 3.4f, Offset(px + 31f, py + 20f + look))
        drawCircle(Color(0xFF1A237E), 1.5f, Offset(px + 21.8f, py + 20.4f + look))
        drawCircle(Color(0xFF1A237E), 1.5f, Offset(px + 31.8f, py + 20.4f + look))
        // щёчка
        drawCircle(Color(0xFFFF8BCB), 3f, Offset(px + 9f, py + 29f), alpha = 0.8f)
    }
    if (w.shield) {
        val pulse = 2f * sin(t * 6f)
        drawCircle(Color(0xFF00D2FF), 31f + pulse, c, alpha = 0.18f)
        drawCircle(Color(0xFF80DEEA), 31f + pulse, c, style = Stroke(3f))
    }
}

private fun DrawScope.drawPowerUp(p: JetPow, t: Float) {
    val c = Offset(p.x + p.size / 2f, p.y + p.size / 2f)
    val r = p.size / 2f + 2f * sin(t * 5f)
    val col = if (p.kind == JetPower.SHIELD) Color(0xFF00D2FF) else Color(0xFFFFEB3B)
    drawCircle(col, r + 6f, c, alpha = 0.2f)
    drawCircle(col, r, c, style = Stroke(3.5f))
    withTransform({ translate(c.x, c.y); scale(r * 0.55f, r * 0.55f, Offset.Zero) }) {
        if (p.kind == JetPower.SHIELD) drawPath(JB_SHIELD, Color(0xFF80DEEA))
        else drawPath(JB_STAR, Color(0xFFFFF176))
    }
}

private fun DrawScope.drawObstacle(o: JetObs, t: Float) {
    when (o.kind) {
        JetKind.GEAR, JetKind.WOBBLE -> {
            val r = max(o.w, o.h) / 2f + 4f
            val col = if (o.kind == JetKind.GEAR) Color(0xFF9BA0A6) else Color(0xFFFF8BCB)
            withTransform({
                translate(o.x + o.w / 2f, o.y + o.h / 2f)
                rotate(t * (if (o.kind == JetKind.GEAR) 60f else -90f), Offset.Zero)
                scale(r, r, Offset.Zero)
            }) { drawPath(JB_GEAR, col) }
        }
        JetKind.DASH -> {
            val c = Offset(o.x + o.w / 2f, o.y + o.h / 2f)
            drawCircle(Color(0xFFFF7043), 9f, Offset(c.x + 34f, c.y), alpha = 0.25f)
            drawCircle(Color(0xFFFF7043), 12f, Offset(c.x + 22f, c.y), alpha = 0.4f)
            drawRoundRect(Color(0xFFFF7043), Offset(o.x, o.y), Size(o.w, o.h), CornerRadius(o.h / 2f))
            drawCircle(Color.White, 4.5f, Offset(o.x + 14f, o.y + o.h / 2f - 3f))
            drawCircle(Color(0xFF3A0728), 2f, Offset(o.x + 13f, o.y + o.h / 2f - 3f))
        }
        JetKind.ZIGZAG -> {
            val c = Offset(o.x + o.w / 2f, o.y + o.h / 2f)
            val r = o.w / 2f + 3f
            drawCircle(Color(0xFFB388FF), r, c)
            drawCircle(Color(0xFF7C4DFF), r, c, style = Stroke(2.5f))
            drawCircle(Color.White, 3.5f, Offset(c.x - 5f, c.y - 3f))
            drawCircle(Color.White, 3.5f, Offset(c.x + 5f, c.y - 3f))
        }
        JetKind.SPIKE -> {
            val sp = Path()
            if (o.ceiling) { sp.moveTo(o.x, o.y); sp.lineTo(o.x + o.w, o.y); sp.lineTo(o.x + o.w / 2f, o.y + o.h) }
            else { sp.moveTo(o.x, o.y + o.h); sp.lineTo(o.x + o.w, o.y + o.h); sp.lineTo(o.x + o.w / 2f, o.y) }
            sp.close()
            drawPath(sp, Color(0xFFE0E0E0))
            drawPath(sp, Color(0xFF9E9E9E), style = Stroke(2f, join = StrokeJoin.Round))
        }
        JetKind.BAR -> {
            withTransform({ translate(o.x, o.y); rotate(o.phase * 180f / PI.toFloat(), Offset.Zero) }) {
                drawRoundRect(Color(0xFFFFB74D), Offset(-o.w / 2f, -o.h / 2f), Size(o.w, o.h), CornerRadius(o.w / 2f))
                drawRoundRect(Color(0xFFE65100), Offset(-o.w / 2f, -o.h / 2f), Size(o.w, o.h), CornerRadius(o.w / 2f), style = Stroke(2.5f))
                drawCircle(Color(0xFF3A0728), 9f, Offset.Zero)
                drawCircle(Color(0xFFFFB74D), 4f, Offset.Zero)
            }
        }
        JetKind.FLAG -> {
            drawRoundRect(Color(0xFFEEEEEE), Offset(o.x, o.y), Size(6f, o.h), CornerRadius(3f))
            val fl = Path().apply {
                val wave = 4f * sin(t * 6f)
                moveTo(o.x + 6f, o.y + 2f); lineTo(o.x + 52f, o.y + 20f + wave); lineTo(o.x + 6f, o.y + 42f)
                close()
            }
            drawPath(fl, Color(0xFFFFD54F))
            drawPath(fl, Color(0xFFFF8F00), style = Stroke(2.5f, join = StrokeJoin.Round))
            withTransform({ translate(o.x + 22f, o.y + 22f); scale(8f, 8f, Offset.Zero) }) { drawPath(JB_STAR, Color.White) }
        }
    }
}
