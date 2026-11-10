package com.example.screens.Busyboard

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.ColorBlue
import com.example.shared.ColorGreen
import com.example.shared.ColorInk
import com.example.shared.ColorOrange
import com.example.shared.ColorPink
import com.example.shared.ColorPurple
import com.example.shared.ColorYellow
import com.example.shared.Haptics
import com.example.shared.i18n.Text
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Общие настройки и «руки» для всех деталей доски: звук, вибрация, награда, касание. */
internal class BbCtx(
    val audio: BbAudio,
    private val haptics: Haptics,
    val toddler: Boolean,
    private val onReward: (BbElement) -> Unit,
    private val onTouch: () -> Unit
) {
    private val buzzGate = BbGate(longArrayOf(80L), globalLimit = 3, globalWindowMs = 250L)

    fun touch() = onTouch()

    fun reward(e: BbElement) = onReward(e)

    /** Лёгкий «тык» в пальцы — не чаще раза в 80 мс, чтобы вибрация не жужжала. */
    fun buzz() {
        if (buzzGate.allow(0, SystemClock.uptimeMillis())) haptics.vibrateShort()
    }

    /** Весомый «бум» (рычаг дошёл до упора, замок открылся). */
    fun thump() {
        if (buzzGate.allow(0, SystemClock.uptimeMillis())) haptics.vibrate(28L)
    }

    /** Сколько частиц пускать: малышам вдвое меньше (спокойнее для глаз). */
    fun n(count: Int): Int = if (toddler) max(3, count / 2) else count
}

// ───────────────────────── кадры и перерисовка ─────────────────────────

/** Счётчик перерисовки: Canvas читает [value] первой строкой, а жесты и кадры вызывают [bump]. */
internal class BbTick {
    private val s = mutableIntStateOf(0)
    val value: Int get() = s.intValue

    fun bump() {
        s.intValue = s.intValue + 1
    }
}

/**
 * Кадровый цикл детали. [onFrame] получает dt в секундах и возвращает true, если надо перерисоваться.
 * Пока ничего не движется, возвращайте false — тогда перерисовок нет.
 */
@Composable
internal fun rememberBbTick(onFrame: (dt: Float) -> Boolean): BbTick {
    val tick = remember { BbTick() }
    val frame by rememberUpdatedState(onFrame)
    LaunchedEffect(Unit) {
        var last = 0L
        while (isActive) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                if (frame(dt)) tick.bump()
            }
        }
    }
    return tick
}

// ───────────────────────── жесты ─────────────────────────

/**
 * Один палец: касание, перетаскивание, отпускание. [onUp] вызывается ВСЕГДА (даже при отмене жеста),
 * поэтому деталь никогда не «залипает» в состоянии «держат» и не звучит после того, как палец убран.
 * Колбэки захватываются один раз — ссылайтесь только на стабильные объекты (remember { ... }).
 */
internal fun Modifier.bbGesture(
    onDown: (Offset) -> Unit = {},
    onMove: (pos: Offset, delta: Offset) -> Unit = { _, _ -> },
    onUp: (pos: Offset, wasTap: Boolean) -> Unit = { _, _ -> }
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val start = down.position
        var pos = start
        var moved = false
        onDown(pos)
        while (true) {
            val ev = awaitPointerEvent()
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) {
                ch.consume()
                break
            }
            val d = ch.positionChange()
            if (d != Offset.Zero) {
                pos = ch.position
                if ((pos - start).getDistance() > viewConfiguration.touchSlop) moved = true
                onMove(pos, d)
                ch.consume()
            }
        }
        onUp(pos, !moved)
    }
}

// ───────────────────────── частицы ─────────────────────────

internal const val BB_DOT = 0
internal const val BB_STAR = 1
internal const val BB_SPARK = 2

internal val BB_CONFETTI: List<Color> = listOf(ColorPink, ColorYellow, ColorGreen, ColorOrange, ColorBlue, ColorPurple)

/** Пул частиц без аллокаций на кадр: точки, звёздочки и искры-чёрточки. */
internal class BbParticles(private val cap: Int) {
    private val x = FloatArray(cap)
    private val y = FloatArray(cap)
    private val vx = FloatArray(cap)
    private val vy = FloatArray(cap)
    private val life = FloatArray(cap)
    private val maxLife = FloatArray(cap)
    private val size = FloatArray(cap)
    private val color = IntArray(cap)
    private val kind = IntArray(cap)
    private val star = Path()

    var count = 0
        private set

    fun emit(px: Float, py: Float, pvx: Float, pvy: Float, ttl: Float, sz: Float, c: Color, k: Int = BB_DOT) {
        if (count >= cap) return
        val i = count++
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy
        life[i] = ttl; maxLife[i] = ttl; size[i] = sz; color[i] = c.toArgb(); kind[i] = k
    }

    /** Веер частиц во все стороны из точки. */
    fun burst(cx: Float, cy: Float, n: Int, speed: Float, ttl: Float, sz: Float, colors: List<Color>, k: Int = BB_STAR) {
        repeat(n) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val s = speed * (0.35f + Random.nextFloat() * 0.65f)
            emit(cx, cy, cos(a) * s, sin(a) * s, ttl * (0.7f + Random.nextFloat() * 0.5f), sz * (0.7f + Random.nextFloat() * 0.6f), colors.random(), k)
        }
    }

    /** true, если после шага остались живые частицы (нужна ещё перерисовка). */
    fun update(dt: Float, gravity: Float): Boolean {
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f) {
                val last = --count
                x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]
                life[i] = life[last]; maxLife[i] = maxLife[last]; size[i] = size[last]
                color[i] = color[last]; kind[i] = kind[last]
                continue
            }
            vy[i] += gravity * dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            i++
        }
        return count > 0
    }

    fun clear() {
        count = 0
    }

    fun draw(scope: DrawScope) {
        for (i in 0 until count) {
            val a = (life[i] / maxLife[i]).coerceIn(0f, 1f)
            val c = Color(color[i]).copy(alpha = a)
            when (kind[i]) {
                BB_STAR -> {
                    star.rewind()
                    val r = size[i] * (0.6f + 0.4f * a)
                    for (k in 0 until 10) {
                        val rr = if (k % 2 == 0) r else r * 0.45f
                        val ang = (-PI / 2 + k * PI / 5).toFloat()
                        val px = x[i] + cos(ang) * rr
                        val py = y[i] + sin(ang) * rr
                        if (k == 0) star.moveTo(px, py) else star.lineTo(px, py)
                    }
                    star.close()
                    scope.drawPath(star, c)
                }
                BB_SPARK -> scope.drawLine(
                    c, Offset(x[i], y[i]), Offset(x[i] - vx[i] * 0.04f, y[i] - vy[i] * 0.04f),
                    strokeWidth = size[i], cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                else -> scope.drawCircle(c, size[i] * (0.5f + 0.5f * a), Offset(x[i], y[i]))
            }
        }
    }
}

// ───────────────────────── эмодзи в Canvas ─────────────────────────

internal fun bbEmojiPaint(): android.graphics.Paint =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER }

internal fun DrawScope.bbEmoji(text: String, cx: Float, cy: Float, sizePx: Float, paint: android.graphics.Paint, alpha: Float = 1f) {
    paint.textSize = sizePx
    paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
    drawContext.canvas.nativeCanvas.drawText(text, cx, cy + sizePx * 0.35f, paint)
}

// ───────────────────────── карточка детали ─────────────────────────

/** Рамка детали: название сверху слева, звёздочка сверху справа, мягкое мигание рамки при подсказке. */
@Composable
internal fun BbCard(
    title: String,
    done: Boolean,
    hint: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val pulse = if (hint) {
        val t = rememberInfiniteTransition(label = "bbHint")
        val v by t.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
            label = "bbHintPulse"
        )
        v
    } else 0f
    Box(
        modifier = modifier
            .clip(shape)
            .background(tint)
            .border(
                BorderStroke(if (hint) (3f + 2f * pulse).dp else 3.dp, if (hint) ColorYellow else ColorInk),
                shape
            )
    ) {
        Box(Modifier.fillMaxSize().padding(top = 30.dp), content = content)
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = ColorInk,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Text(
            text = if (done) "⭐" else "☆",
            fontSize = 18.sp,
            color = ColorInk,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        )
    }
}
