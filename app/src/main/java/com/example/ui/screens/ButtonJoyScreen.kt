package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.SoundManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * «Кнопочная радость» — альтернатива бизиборду для самых маленьких.
 * 24 крупные яркие кнопки и зверята. У каждой — свой звук, своя вспышка и свои эффекты.
 * Каждое нажатие наполняет шкалу праздника: заполнил — вечеринка с конфетти, танцем и фанфарами.
 */

private class BCellSpec(
    val id: String,
    val character: Boolean,
    val color: Color,
    val square: Boolean,
    val glyph: String,
    val word: String
)

private val B_SPECS = listOf(
    BCellSpec("boom", false, Color(0xFFE53935), false, "💥", "БУМ!"),
    BCellSpec("dog", true, Color(0xFFA1887F), false, "", "ГАВ!"),
    BCellSpec("boing", false, Color(0xFFFFCA28), false, "🏀", "БОИНГ!"),
    BCellSpec("piano", false, Color(0xFF43A047), true, "🎹", "ДО-РЕ-МИ!"),
    BCellSpec("cat", true, Color(0xFFFFB74D), false, "", "МЯУ!"),
    BCellSpec("pop", false, Color(0xFF29B6F6), false, "🎈", "ПОП!"),
    BCellSpec("harp", false, Color(0xFFF06292), true, "💖", "ЛЯ-ЛЯ!"),
    BCellSpec("honk", false, Color(0xFFFF7043), true, "🚗", "БИП-БИП!"),
    BCellSpec("cow", true, Color(0xFFF5F5F5), false, "", "МУУ!"),
    BCellSpec("magic", false, Color(0xFF8E24AA), false, "✨", "ВЖИК!"),
    BCellSpec("drip", false, Color(0xFF26C6DA), true, "💧", "КАП!"),
    BCellSpec("duck", true, Color(0xFFFFEE58), false, "", "КРЯ!"),
    BCellSpec("bell", false, Color(0xFFFFFFFF), false, "🔔", "ДИНЬ!"),
    BCellSpec("siren", false, Color(0xFFD32F2F), true, "🚨", "УИИ-УУ!"),
    BCellSpec("frog", true, Color(0xFF66BB6A), false, "", "КВА!"),
    BCellSpec("rocket", false, Color(0xFFFFB300), false, "🚀", "ВЖУУХ!"),
    BCellSpec("drum", false, Color(0xFFFF5722), true, "🥁", "ТРА-ТА!"),
    BCellSpec("zap", false, Color(0xFF3949AB), false, "⚡", "ЗЫНЬ!"),
    BCellSpec("pig", true, Color(0xFFF8BBD0), false, "", "ХРЮ!"),
    BCellSpec("whistle", false, Color(0xFF00E5FF), true, "🌈", "ФЬЮУ!"),
    BCellSpec("giggle", false, Color(0xFFC0CA33), false, "😆", "ХИ-ХИ!"),
    BCellSpec("sheep", true, Color(0xFFECEFF1), false, "", "БЭЭ!"),
    BCellSpec("squeak", false, Color(0xFFFF4081), false, "🐥", "ПИ-ПИ!"),
    BCellSpec("kazoo", false, Color(0xFF7C4DFF), true, "🎺", "ТУ-ТУ!")
)

private class BCell(val spec: BCellSpec, val phase: Float) {
    var cx = 0f
    var cy = 0f
    var r = 0f
    var press = 0f
    var squash = 0f
    var squashV = 0f
    var hop = 0f
    var hopV = 0f
    var shakeX = 0f
    var talk = 0f
    var fly = 0f
    var blink = 0f
    var blinkT = 1f + Random.nextFloat() * 3f
    var glow = 0f
    var step = 0
}

private class BParticle(
    var x: Float, var y: Float, var vx: Float, var vy: Float, val gravity: Float,
    val glyph: String?, val color: Color, val size: Float, val kind: Int
) {
    var life = 1f
    var decay = 1.1f
    var rot = Random.nextFloat() * 6.28f
    var vr = (Random.nextFloat() - 0.5f) * 10f
}

private class BRing(val x: Float, val y: Float, val color: Color, val maxR: Float) { var t = 0f }
private class BWord(val text: String, var x: Float, var y: Float, val color: Color) { var t = 0f }

private class BtnWorld(val density: Float, val toddler: Boolean) {
    var w = 0f
    var h = 0f
    var time = 0f
    val cells = ArrayList<BCell>()
    val parts = ArrayList<BParticle>()
    val rings = ArrayList<BRing>()
    val words = ArrayList<BWord>()
    var flash = 0f
    var flashColor = Color.White
    var shake = 0f
    var meter = 0f
    var party = 0f
    var taps = 0
    var lookX = 0f
    var lookY = 0f
    var sirenFlip = false
    var onSound: (String, Float, Float) -> Unit = { _, _, _ -> }

    private val scale = floatArrayOf(1f, 1.122f, 1.26f, 1.335f, 1.498f, 1.682f, 1.888f, 2.0f)
    private val penta = floatArrayOf(0.75f, 0.84f, 0.94f, 1.12f, 1.26f, 1.5f, 1.68f)

    fun init(width: Float, height: Float) {
        if (w == width && h == height) return
        w = width; h = height
        val portrait = h > w
        val cols = if (portrait) 4 else 6
        val rows = if (portrait) 6 else 4
        val top = 62f * density
        val gw = w - 12f * density
        val gh = h - top - 8f * density
        val cw = gw / cols
        val ch = gh / rows
        if (cells.isEmpty()) B_SPECS.forEachIndexed { i, s -> cells.add(BCell(s, i * 0.7f)) }
        for ((i, c) in cells.withIndex()) {
            val col = i % cols
            val row = i / cols
            c.cx = 6f * density + cw * (col + 0.5f)
            c.cy = top + ch * (row + 0.5f)
            c.r = min(cw, ch) * 0.43f
        }
    }

    private fun burst(x: Float, y: Float, n: Int, glyphs: List<String>?, colors: List<Color>, speed: Float, gravity: Float, size: Float, kind: Int = 0) {
        repeat(n) {
            val a = Random.nextFloat() * 6.2832f
            val s = speed * (0.35f + Random.nextFloat() * 0.8f)
            val g = if (glyphs != null) glyphs[Random.nextInt(glyphs.size)] else null
            parts.add(BParticle(x, y, cos(a) * s, sin(a) * s - speed * 0.35f, gravity, g, colors[Random.nextInt(colors.size)], size * (0.7f + Random.nextFloat() * 0.6f), kind))
        }
        while (parts.size > 360) parts.removeAt(0)
    }

    private val rainbow = listOf(Color(0xFFFF5252), Color(0xFFFFAB40), Color(0xFFFFEB3B), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFE040FB))

    fun tap(x: Float, y: Float) {
        var best: BCell? = null
        var bd = Float.MAX_VALUE
        for (c in cells) {
            val d = hypot(c.cx - x, c.cy - y)
            if (d < c.r * 1.25f && d < bd) { bd = d; best = c }
        }
        val c = best
        if (c == null) {
            // мимо кнопок — всё равно праздник: конфетти и «пых»
            burst(x, y, 10, null, rainbow, 380f * density, 900f * density, 6f * density, 3)
            onSound("btn_pop", 0.6f, 0.8f + Random.nextFloat() * 0.8f)
            return
        }
        lookX = x; lookY = y
        trigger(c)
    }

    private fun trigger(c: BCell) {
        val s = c.spec
        val d = density
        val k = if (party > 0f) 1.7f else 1f
        taps++
        c.press = 1f
        c.squash = 0.35f
        c.squashV = 0f
        c.glow = 1f
        flash = 0.28f
        flashColor = s.color
        words.add(BWord(s.word, c.cx, c.cy - c.r * 0.9f, s.color))
        if (words.size > 6) words.removeAt(0)
        rings.add(BRing(c.cx, c.cy, s.color, c.r * 1.9f))
        onSound("vib", 0f, 0f)
        meter += if (toddler) 0.07f else 0.05f
        if (meter >= 1f && party <= 0f) {
            meter = 0f
            party = 8f
            onSound("btn_tada", 1f, 1f)
            onSound("pin_horn", 0.8f, 1f)
            onSound("fanfare", 0f, 0f)
            flash = 0.7f; flashColor = Color(0xFFFFEB3B)
            burst(w / 2f, h * 0.3f, 70, null, rainbow, 700f * d, 800f * d, 8f * d, 3)
            words.add(BWord("ВЕЧЕРИНКА!", w / 2f, h * 0.45f, Color(0xFFFFEB3B)))
        }
        val n = (10 * k).toInt()
        val col = listOf(s.color, Color.White, Color(0xFFFFEB3B))
        when (s.id) {
            "boom" -> {
                onSound("btn_boom", 1f, 1f)
                shake = 1f
                rings.add(BRing(c.cx, c.cy, Color.White, c.r * 3.4f))
                burst(c.cx, c.cy, n, listOf("💥", "⭐"), col, 520f * d, 900f * d, 20f * d)
            }
            "boing" -> {
                onSound("btn_boing", 1f, 0.95f + Random.nextFloat() * 0.15f)
                c.hopV = 900f * d
                burst(c.cx, c.cy, n, listOf("⭐", "🟡"), col, 420f * d, 900f * d, 18f * d)
            }
            "piano" -> {
                onSound("btn_note", 1f, scale[c.step % scale.size])
                val glyph = listOf("🎵", "🎶")
                burst(c.cx, c.cy, n, glyph, rainbow, 380f * d, 500f * d, 22f * d)
                c.step++
            }
            "pop" -> {
                onSound("btn_pop", 1f, 0.8f + Random.nextFloat() * 0.8f)
                burst(c.cx, c.cy, (14 * k).toInt(), null, listOf(Color(0xFF81D4FA), Color.White, Color(0xFFB3E5FC)), 380f * d, -200f * d, 12f * d, 4)
            }
            "harp" -> {
                onSound("btn_harp", 1f, 1f)
                burst(c.cx, c.cy, (12 * k).toInt(), listOf("💖", "💗", "💕"), col, 380f * d, -250f * d, 22f * d)
            }
            "honk" -> {
                onSound("btn_honk", 1f, 0.95f + Random.nextFloat() * 0.2f)
                c.shakeX = 1f
                rings.add(BRing(c.cx, c.cy, Color(0xFFFFEB3B), c.r * 2.6f))
                burst(c.cx, c.cy, n, listOf("💨", "⭐"), col, 400f * d, 300f * d, 20f * d)
            }
            "magic" -> {
                onSound("btn_magic", 1f, 1f)
                burst(c.cx, c.cy, (18 * k).toInt(), listOf("✨", "⭐", "🌟"), listOf(Color.White, Color(0xFFE1BEE7)), 520f * d, 300f * d, 20f * d)
            }
            "drip" -> {
                onSound("btn_drip", 1f, penta[c.step % penta.size])
                c.step++
                burst(c.cx, c.cy, n, listOf("💧"), col, 360f * d, 1100f * d, 20f * d)
            }
            "bell" -> {
                onSound("btn_bell", 1f, 1f)
                rings.add(BRing(c.cx, c.cy, Color(0xFFFFD54F), c.r * 3f))
                burst(c.cx, c.cy, n, listOf("⭐", "🌟"), listOf(Color(0xFFFFD54F)), 420f * d, 600f * d, 20f * d)
            }
            "siren" -> {
                onSound("btn_siren", 1f, 1f)
                sirenFlip = !sirenFlip
                flashColor = if (sirenFlip) Color(0xFFFF1744) else Color(0xFF2979FF)
                flash = 0.5f
                burst(c.cx, c.cy, n, listOf("🚨", "⭐"), col, 400f * d, 500f * d, 20f * d)
            }
            "rocket" -> {
                onSound("btn_rocket", 1f, 1f)
                c.fly = 1f
                burst(c.cx, c.cy, (14 * k).toInt(), listOf("🔥", "💨"), listOf(Color(0xFFFFB300)), 300f * d, 600f * d, 22f * d)
            }
            "drum" -> {
                onSound(if (c.step % 4 == 3) "btn_cymbal" else "btn_drum", 1f, if (c.step % 2 == 0) 1f else 1.25f)
                c.step++
                rings.add(BRing(c.cx, c.cy, Color.White, c.r * 3f))
                burst(c.cx, c.cy, (12 * k).toInt(), null, rainbow, 520f * d, 900f * d, 8f * d, 3)
            }
            "zap" -> {
                onSound("btn_zap", 1f, 0.9f + Random.nextFloat() * 0.3f)
                flash = 0.55f; flashColor = Color.White
                burst(c.cx, c.cy, (8 * k).toInt(), listOf("⚡", "✨"), col, 560f * d, 500f * d, 24f * d)
            }
            "whistle" -> {
                onSound("btn_whistle", 1f, 1f)
                burst(c.cx, c.cy, (5 * k).toInt(), listOf("🌈", "☁️"), col, 260f * d, -300f * d, 34f * d)
                burst(c.cx, c.cy, 10, null, rainbow, 420f * d, -200f * d, 7f * d, 3)
            }
            "giggle" -> {
                onSound("btn_giggle", 1f, 0.95f + Random.nextFloat() * 0.15f)
                c.hopV = 520f * d
                burst(c.cx, c.cy, (7 * k).toInt(), listOf("😆", "😂", "🤣", "😄"), col, 420f * d, 700f * d, 26f * d)
            }
            "squeak" -> {
                onSound("btn_squeak", 1f, 0.85f + Random.nextFloat() * 0.5f)
                c.hopV = 420f * d
                burst(c.cx, c.cy, (7 * k).toInt(), listOf("🐥", "💛"), col, 380f * d, 700f * d, 24f * d)
            }
            "kazoo" -> {
                onSound("btn_kazoo", 1f, 0.9f + Random.nextFloat() * 0.35f)
                burst(c.cx, c.cy, (10 * k).toInt(), listOf("🎶", "🎵", "🎺"), rainbow, 460f * d, 500f * d, 24f * d)
            }
            else -> { // зверята
                val snd = when (s.id) {
                    "dog" -> if (Random.nextBoolean()) "animal_dog_1" else "animal_dog_happy"
                    "cat" -> if (Random.nextBoolean()) "animal_cat_1" else "animal_cat_happy"
                    "cow" -> "animal_cow_1"
                    "duck" -> if (Random.nextBoolean()) "animal_duck_1" else "animal_duck_2"
                    "frog" -> "animal_frog_1"
                    "pig" -> "animal_pig_1"
                    else -> "animal_sheep_1"
                }
                onSound(snd, 1f, 1f)
                c.hopV = 780f * d
                c.talk = 0.5f
                burst(c.cx, c.cy, (8 * k).toInt(), listOf("💛", "⭐", "💖"), col, 420f * d, 800f * d, 22f * d)
            }
        }
    }

    fun step(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0f, 0.04f)
        time += dt
        flash = max(0f, flash - dt * 2.2f)
        shake = max(0f, shake - dt * 3.5f)
        if (party > 0f) {
            party -= dt
            if (Random.nextFloat() < dt * 30f) {
                parts.add(BParticle(Random.nextFloat() * w, -10f, (Random.nextFloat() - 0.5f) * 80f * density, 120f * density + Random.nextFloat() * 160f * density, 120f * density, null, rainbow[Random.nextInt(rainbow.size)], 7f * density, 3).also { it.decay = 0.35f })
            }
        } else meter = max(0f, meter - dt * 0.012f)
        for (c in cells) {
            c.press = max(0f, c.press - dt * 5.5f)
            c.glow = max(0f, c.glow - dt * 2.2f)
            c.squashV += (-c.squash * 380f - c.squashV * 11f) * dt
            c.squash += c.squashV * dt
            c.hopV -= 2600f * density * dt
            c.hop += c.hopV * dt
            if (c.hop < 0f) { c.hop = 0f; c.hopV = if (c.hopV < -300f * density) -c.hopV * 0.32f else 0f }
            c.shakeX = max(0f, c.shakeX - dt * 2.8f)
            c.talk = max(0f, c.talk - dt)
            if (c.fly > 0f) {
                c.fly = max(0f, c.fly - dt * 1.3f)
                if (Random.nextFloat() < 0.6f) burst(c.cx, c.cy - c.r * 2.4f * flyOffset(c.fly) + c.r * 0.6f, 1, listOf("🔥"), listOf(Color.White), 90f * density, 300f * density, 16f * density)
            }
            c.blinkT -= dt
            if (c.blinkT <= 0f) { c.blink = 0.15f; c.blinkT = 2f + Random.nextFloat() * 3f }
            c.blink = max(0f, c.blink - dt)
        }
        for (r in rings) r.t += dt * 2.2f
        rings.removeAll { it.t >= 1f }
        for (wd in words) { wd.t += dt * 1.3f; wd.y -= 40f * density * dt }
        words.removeAll { it.t >= 1f }
        val it = parts.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.vy += p.gravity * dt
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vx *= (1f - dt * 0.8f)
            p.rot += p.vr * dt
            p.life -= dt * p.decay
            if (p.life <= 0f || p.y > h + 40f) it.remove()
        }
    }

    fun flyOffset(f: Float): Float = sin((1f - f).coerceIn(0f, 1f) * PI.toFloat())
}

@Composable
fun ButtonJoyScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val density = LocalDensity.current.density
    val toddler = com.example.shared.LocalAgeProfile.current.isToddler2
    val world = remember(toddler) { BtnWorld(density, toddler) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        soundManager.preloadRaw(
            listOf(
                "btn_boom", "btn_boing", "btn_note", "btn_pop", "btn_harp", "btn_honk", "btn_magic", "btn_drip", "btn_bell",
                "btn_siren", "btn_rocket", "btn_drum", "btn_cymbal", "btn_zap", "btn_whistle", "btn_giggle", "btn_squeak",
                "btn_kazoo", "btn_tada", "pin_horn",
                "animal_dog_1", "animal_dog_happy", "animal_cat_1", "animal_cat_happy", "animal_cow_1", "animal_duck_1",
                "animal_duck_2", "animal_frog_1", "animal_pig_1", "animal_sheep_1"
            )
        )
    }
    world.onSound = { name, vol, rate ->
        when (name) {
            "vib" -> soundPlayer.vibrateSquishDeform()
            "fanfare" -> soundPlayer.playFanfare()
            else -> soundManager.play(name, volume = vol, rate = rate)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF6A1B9A))
            .testTag("button_joy_root")
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        for (ch in e.changes) {
                            if (ch.changedToDown()) {
                                ch.consume()
                                world.tap(ch.position.x, ch.position.y)
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val redraw = tick
            world.init(size.width, size.height)
            drawJoy(world)
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                if (world.w > 0f) world.step(dt)
                tick++
            }
        }
    }
}

// ───────────────────────── Рисование ─────────────────────────

private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER }
private val wordFill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = android.graphics.Paint.Align.CENTER
    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
}
private val wordStroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = android.graphics.Paint.Align.CENTER
    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    style = android.graphics.Paint.Style.STROKE
    color = 0xFF3E1A5E.toInt()
}

private fun DrawScope.glyph(s: String, x: Float, y: Float, sizePx: Float, alpha: Float = 1f) {
    fillPaint.textSize = sizePx
    fillPaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
    drawContext.canvas.nativeCanvas.drawText(s, x, y + sizePx * 0.35f, fillPaint)
}

private fun DrawScope.word(s: String, x: Float, y: Float, sizePx: Float, color: Color, alpha: Float) {
    val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
    wordStroke.textSize = sizePx; wordStroke.strokeWidth = sizePx * 0.2f; wordStroke.alpha = a
    wordFill.textSize = sizePx; wordFill.color = color.copy(alpha = 1f).toArgb(); wordFill.alpha = a
    val cv = drawContext.canvas.nativeCanvas
    cv.drawText(s, x, y, wordStroke)
    cv.drawText(s, x, y, wordFill)
}

private fun Color.toArgb(): Int =
    ((alpha * 255).toInt() shl 24) or ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or (blue * 255).toInt()

private fun shade(c: Color, k: Float): Color = Color((c.red * k).coerceIn(0f, 1f), (c.green * k).coerceIn(0f, 1f), (c.blue * k).coerceIn(0f, 1f), 1f)
private fun tint(c: Color, k: Float): Color = Color(c.red + (1f - c.red) * k, c.green + (1f - c.green) * k, c.blue + (1f - c.blue) * k, 1f)

private fun DrawScope.drawJoy(w: BtnWorld) {
    val d = w.density
    val t = w.time
    val sw = size.width
    val sh = size.height
    val partyK = if (w.party > 0f) 1f else 0f
    val hue = (280f + 50f * sin(t * 0.15f) + (if (partyK > 0f) t * 120f else 0f)) % 360f
    drawRect(
        Brush.verticalGradient(listOf(Color.hsv(hue, 0.65f, 0.55f), Color.hsv((hue + 40f) % 360f, 0.7f, 0.95f))),
        Offset.Zero, Size(sw, sh)
    )
    // боке
    for (i in 0 until 14) {
        val bx = ((i * 173f + t * (8f + i % 4 * 5f) * d) % (sw + 120f * d)) - 60f * d
        val by = (i * 97f * d + sin(t * 0.4f + i) * 30f * d) % sh
        drawCircle(Color.White.copy(alpha = 0.07f), (30f + (i % 4) * 18f) * d, Offset(bx, by))
    }
    val sx = if (w.shake > 0f) sin(t * 80f) * 8f * d * w.shake else 0f
    val sy = if (w.shake > 0f) cos(t * 70f) * 8f * d * w.shake else 0f
    withTransform({ translate(sx, sy) }) {
        // шкала праздника
        val barW = sw * 0.6f
        val barX = sw / 2f - barW / 2f + 20f * d
        val barY = 24f * d
        drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(barX, barY), Size(barW, 20f * d), CornerRadius(10f * d))
        val frac = if (w.party > 0f) 1f else w.meter.coerceIn(0f, 1f)
        if (frac > 0.01f) drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFFFF5252), Color(0xFFFFEB3B), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFE040FB)), startX = barX, endX = barX + barW),
            Offset(barX, barY), Size(barW * frac, 20f * d), CornerRadius(10f * d)
        )
        glyph(if (w.party > 0f) "🎉" else "⭐", barX - 24f * d, barY + 10f * d, 30f * d)
        glyph("🎉", barX + barW + 24f * d, barY + 10f * d, 30f * d)

        for (c in w.cells) drawCell(c, w, t, d)

        // кольца
        for (r in w.rings) {
            val k = r.t
            drawCircle(r.color.copy(alpha = (1f - k) * 0.8f), r.maxR * k + 10f * d, Offset(r.x, r.y), style = Stroke(width = (8f * (1f - k) + 1f) * d))
        }
        // частицы
        for (p in w.parts) {
            val a = p.life.coerceIn(0f, 1f)
            when {
                p.glyph != null -> glyph(p.glyph, p.x, p.y, p.size * (0.6f + 0.4f * a), a)
                p.kind == 4 -> {
                    drawCircle(p.color.copy(alpha = a * 0.8f), p.size, Offset(p.x, p.y), style = Stroke(2.5f * d))
                    drawCircle(Color.White.copy(alpha = a * 0.8f), p.size * 0.22f, Offset(p.x - p.size * 0.4f, p.y - p.size * 0.4f))
                }
                else -> withTransform({ rotate(p.rot * 57.3f, Offset(p.x, p.y)) }) {
                    drawRect(p.color.copy(alpha = a), Offset(p.x - p.size / 2f, p.y - p.size * 0.9f), Size(p.size, p.size * 1.8f))
                }
            }
        }
        // слова
        for (wd in w.words) {
            val k = wd.t
            val pop = if (k < 0.15f) 0.6f + k / 0.15f * 0.6f else 1.2f - (k - 0.15f) * 0.25f
            word(wd.text, wd.x, wd.y, 34f * d * pop, wd.color.let { tint(it, 0.25f) }, 1f - k * k)
        }
    }
    if (w.flash > 0f) drawRect(w.flashColor.copy(alpha = (w.flash * 0.35f).coerceIn(0f, 0.4f)), Offset.Zero, Size(sw, sh))
}

private fun DrawScope.drawCell(c: BCell, w: BtnWorld, t: Float, d: Float) {
    val partyDance = if (w.party > 0f) sin(t * 9f + c.phase * 2f) * 0.12f else 0f
    val idle = 1f + 0.03f * sin(t * 3f + c.phase) + partyDance
    var cy = c.cy - c.hop
    if (c.fly > 0f) cy -= c.r * 2.6f * w.flyOffset(c.fly)
    val cx = c.cx + sin(t * 60f) * c.shakeX * 7f * d
    val sq = c.squash
    // гнездо
    drawCircle(Color.Black.copy(alpha = 0.18f), c.r * 1.12f, Offset(c.cx, c.cy + c.r * 0.06f))
    drawCircle(Color.White.copy(alpha = 0.10f), c.r * 1.12f, Offset(c.cx, c.cy), style = Stroke(2f * d))
    if (c.glow > 0f) drawCircle(c.spec.color.copy(alpha = c.glow * 0.5f), c.r * (1.25f + 0.4f * (1f - c.glow)), Offset(c.cx, c.cy))
    // тень под прыгающей кнопкой
    if (c.hop > 4f || c.fly > 0f) drawOval(Color.Black.copy(alpha = 0.2f), Offset(c.cx - c.r * 0.8f, c.cy + c.r * 0.7f), Size(c.r * 1.6f, c.r * 0.35f))
    withTransform({
        scale((1f + sq * 0.5f) * idle, (1f - sq * 0.5f) * idle, Offset(cx, cy + c.r * 0.9f))
    }) {
        if (c.spec.character) drawCharacter(c, w, cx, cy, t, d) else drawButton(c, cx, cy, d)
    }
}

private fun DrawScope.drawButton(c: BCell, cx: Float, cy: Float, d: Float) {
    val r = c.r
    val col = c.spec.color
    val depth = r * 0.16f * (1f - c.press)
    val sq = c.spec.square
    val dark = shade(col, 0.55f)
    // основание
    if (sq) {
        drawRoundRect(dark, Offset(cx - r, cy - r + r * 0.18f), Size(2f * r, 2f * r), CornerRadius(r * 0.35f))
    } else drawCircle(dark, r, Offset(cx, cy + r * 0.18f))
    val top = cy - depth
    if (sq) {
        drawRoundRect(
            Brush.verticalGradient(listOf(tint(col, 0.35f), col, shade(col, 0.85f)), startY = top - r, endY = top + r),
            Offset(cx - r, top - r + r * 0.05f), Size(2f * r, 2f * r), CornerRadius(r * 0.35f)
        )
        drawRoundRect(shade(col, 0.7f), Offset(cx - r, top - r + r * 0.05f), Size(2f * r, 2f * r), CornerRadius(r * 0.35f), style = Stroke(r * 0.05f))
        drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(cx - r * 0.78f, top - r * 0.85f), Size(r * 1.1f, r * 0.28f), CornerRadius(r * 0.14f))
    } else {
        drawCircle(
            Brush.radialGradient(listOf(tint(col, 0.5f), col, shade(col, 0.8f)), center = Offset(cx - r * 0.3f, top - r * 0.35f), radius = r * 1.5f),
            r, Offset(cx, top)
        )
        drawCircle(shade(col, 0.7f), r, Offset(cx, top), style = Stroke(r * 0.05f))
        drawOval(Color.White.copy(alpha = 0.5f), Offset(cx - r * 0.62f, top - r * 0.8f), Size(r * 0.8f, r * 0.38f))
    }
    glyph(c.spec.glyph, cx, top, r * 1.05f)
}

private fun DrawScope.drawCharacter(c: BCell, w: BtnWorld, cx: Float, cy: Float, t: Float, d: Float) {
    val r = c.r
    val id = c.spec.id
    val body = c.spec.color
    val bw = r * 1.0f
    val bh = r * 0.92f
    val y = cy + r * 0.05f
    val look = Offset(
        ((w.lookX - cx) / max(1f, w.w) * 0.5f).coerceIn(-0.12f, 0.12f) * r,
        ((w.lookY - y) / max(1f, w.h) * 0.5f).coerceIn(-0.12f, 0.12f) * r
    )
    // уши/рога/пятна позади тела
    when (id) {
        "dog" -> {
            drawOval(Color(0xFF6D4C41), Offset(cx - bw * 1.1f, y - bh * 0.7f), Size(bw * 0.5f, bh * 1.2f))
            drawOval(Color(0xFF6D4C41), Offset(cx + bw * 0.6f, y - bh * 0.7f), Size(bw * 0.5f, bh * 1.2f))
        }
        "cat" -> {
            val l = Path().apply { moveTo(cx - bw * 0.95f, y - bh * 0.2f); lineTo(cx - bw * 0.7f, y - bh * 1.25f); lineTo(cx - bw * 0.15f, y - bh * 0.75f); close() }
            val rr = Path().apply { moveTo(cx + bw * 0.95f, y - bh * 0.2f); lineTo(cx + bw * 0.7f, y - bh * 1.25f); lineTo(cx + bw * 0.15f, y - bh * 0.75f); close() }
            drawPath(l, shade(body, 0.8f)); drawPath(rr, shade(body, 0.8f))
            val li = Path().apply { moveTo(cx - bw * 0.8f, y - bh * 0.45f); lineTo(cx - bw * 0.68f, y - bh * 1.0f); lineTo(cx - bw * 0.38f, y - bh * 0.72f); close() }
            val ri = Path().apply { moveTo(cx + bw * 0.8f, y - bh * 0.45f); lineTo(cx + bw * 0.68f, y - bh * 1.0f); lineTo(cx + bw * 0.38f, y - bh * 0.72f); close() }
            drawPath(li, Color(0xFFF8BBD0)); drawPath(ri, Color(0xFFF8BBD0))
        }
        "cow" -> {
            drawOval(Color(0xFFFFF59D), Offset(cx - bw * 1.0f, y - bh * 1.05f), Size(bw * 0.35f, bh * 0.6f))
            drawOval(Color(0xFFFFF59D), Offset(cx + bw * 0.65f, y - bh * 1.05f), Size(bw * 0.35f, bh * 0.6f))
            drawOval(Color(0xFFBCAAA4), Offset(cx - bw * 1.25f, y - bh * 0.4f), Size(bw * 0.5f, bh * 0.4f))
            drawOval(Color(0xFFBCAAA4), Offset(cx + bw * 0.75f, y - bh * 0.4f), Size(bw * 0.5f, bh * 0.4f))
        }
        "pig" -> {
            val l = Path().apply { moveTo(cx - bw * 0.9f, y - bh * 0.4f); lineTo(cx - bw * 0.7f, y - bh * 1.1f); lineTo(cx - bw * 0.25f, y - bh * 0.75f); close() }
            val rr = Path().apply { moveTo(cx + bw * 0.9f, y - bh * 0.4f); lineTo(cx + bw * 0.7f, y - bh * 1.1f); lineTo(cx + bw * 0.25f, y - bh * 0.75f); close() }
            drawPath(l, Color(0xFFF48FB1)); drawPath(rr, Color(0xFFF48FB1))
        }
        "sheep" -> {
            for (i in 0 until 8) {
                val a = i / 8f * 6.2832f
                drawCircle(Color.White, r * 0.38f, Offset(cx + cos(a) * bw * 0.85f, y + sin(a) * bh * 0.8f))
            }
        }
    }
    // тело
    val bodyColor = if (id == "sheep") Color(0xFF455A64) else body
    drawOval(Brush.radialGradient(listOf(tint(bodyColor, 0.3f), bodyColor, shade(bodyColor, 0.8f)), center = Offset(cx - bw * 0.3f, y - bh * 0.4f), radius = bw * 1.8f), Offset(cx - bw, y - bh), Size(bw * 2f, bh * 2f))
    drawOval(shade(bodyColor, 0.6f), Offset(cx - bw, y - bh), Size(bw * 2f, bh * 2f), style = Stroke(r * 0.04f))
    if (id == "cow") {
        drawOval(Color(0xFF424242), Offset(cx - bw * 0.85f, y - bh * 0.85f), Size(bw * 0.6f, bh * 0.45f))
        drawOval(Color(0xFF424242), Offset(cx + bw * 0.35f, y - bh * 0.1f), Size(bw * 0.5f, bh * 0.6f))
        drawOval(Color(0xFFF8BBD0), Offset(cx - bw * 0.5f, y + bh * 0.2f), Size(bw * 1.0f, bh * 0.7f))
    }
    if (id == "dog") drawOval(Color(0xFF6D4C41), Offset(cx - bw * 0.85f, y - bh * 0.8f), Size(bw * 0.55f, bh * 0.6f))
    // глаза
    val eyeY = if (id == "frog") y - bh * 0.95f else y - bh * 0.2f
    val ex = bw * (if (id == "frog") 0.5f else 0.42f)
    val er = r * (if (id == "frog") 0.33f else 0.26f)
    val blinkK = if (c.blink > 0f) 0.15f else 1f
    for (sgn in intArrayOf(-1, 1)) {
        val ecx = cx + sgn * ex
        if (id == "frog") drawCircle(shade(body, 0.9f), er * 1.25f, Offset(ecx, eyeY))
        withTransform({ scale(1f, blinkK, Offset(ecx, eyeY)) }) {
            drawCircle(Color.White, er, Offset(ecx, eyeY))
            drawCircle(Color(0xFF212121), er * 0.55f, Offset(ecx + look.x * 0.6f, eyeY + look.y * 0.6f))
            drawCircle(Color.White, er * 0.18f, Offset(ecx + look.x * 0.6f - er * 0.15f, eyeY + look.y * 0.6f - er * 0.2f))
        }
    }
    // нос / клюв / морда
    val my = y + bh * 0.38f
    when (id) {
        "dog" -> drawOval(Color(0xFF212121), Offset(cx - bw * 0.16f, y + bh * 0.02f), Size(bw * 0.32f, bh * 0.22f))
        "cat" -> {
            val nose = Path().apply { moveTo(cx - bw * 0.1f, y + bh * 0.05f); lineTo(cx + bw * 0.1f, y + bh * 0.05f); lineTo(cx, y + bh * 0.18f); close() }
            drawPath(nose, Color(0xFFEC407A))
            for (sgn in intArrayOf(-1, 1)) for (k in -1..1) drawLine(Color(0xFF5D4037), Offset(cx + sgn * bw * 0.35f, y + bh * 0.2f), Offset(cx + sgn * bw * 0.95f, y + bh * 0.2f + k * bh * 0.14f), 2.2f * d)
        }
        "duck" -> drawOval(Color(0xFFFF9800), Offset(cx - bw * 0.42f, y + bh * 0.05f), Size(bw * 0.84f, bh * 0.38f))
        "pig" -> {
            drawOval(Color(0xFFF48FB1), Offset(cx - bw * 0.3f, y - bh * 0.02f), Size(bw * 0.6f, bh * 0.42f))
            drawOval(Color(0xFFAD1457), Offset(cx - bw * 0.16f, y + bh * 0.1f), Size(bw * 0.1f, bh * 0.14f))
            drawOval(Color(0xFFAD1457), Offset(cx + bw * 0.06f, y + bh * 0.1f), Size(bw * 0.1f, bh * 0.14f))
        }
        "cow" -> {
            drawOval(Color(0xFFAD1457), Offset(cx - bw * 0.3f, y + bh * 0.4f), Size(bw * 0.1f, bh * 0.12f))
            drawOval(Color(0xFFAD1457), Offset(cx + bw * 0.2f, y + bh * 0.4f), Size(bw * 0.1f, bh * 0.12f))
        }
        "sheep" -> drawOval(Color(0xFF263238), Offset(cx - bw * 0.1f, y + bh * 0.02f), Size(bw * 0.2f, bh * 0.14f))
    }
    // щёчки
    drawCircle(Color(0xFFFF8A80).copy(alpha = 0.55f), r * 0.14f, Offset(cx - bw * 0.68f, y + bh * 0.2f))
    drawCircle(Color(0xFFFF8A80).copy(alpha = 0.55f), r * 0.14f, Offset(cx + bw * 0.68f, y + bh * 0.2f))
    // рот
    val open = if (c.talk > 0f) (0.5f + 0.5f * abs(sin(c.talk * 28f))) else 0f
    if (open > 0.05f) {
        drawOval(Color(0xFF5D1049), Offset(cx - bw * 0.26f, my - bh * 0.02f), Size(bw * 0.52f, bh * (0.1f + 0.32f * open)))
        drawOval(Color(0xFFFF8A80), Offset(cx - bw * 0.14f, my + bh * (0.16f * open)), Size(bw * 0.28f, bh * 0.12f * open))
    } else {
        drawArc(Color(0xFF5D1049), 20f, 140f, false, Offset(cx - bw * 0.22f, my - bh * 0.14f), Size(bw * 0.44f, bh * 0.28f), style = Stroke(r * 0.05f, cap = StrokeCap.Round))
    }
}
