package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.LocalAgeProfile
import com.example.shared.SoundManager
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * «Сыпучести» — насыпь крупу в банку.
 *
 * Зажми и води пальцем: из стаканчика сыплется крупа. Зёрна падают с ускорением,
 * ложатся горкой (угол естественного откоса: слишком крутая горка осыпается),
 * на банку крупа не ложится — скатывается по стенкам. Наполни банку до отметки —
 * получишь звёздочку, банка опустеет, а крупа сменится (песок, рис, гречка, горох…).
 */

private class GrainType(val name: String, val color: Color, val dark: Color)

private val GRAIN_TYPES = listOf(
    GrainType("Песок", Color(0xFFE8C77A), Color(0xFFC9A24F)),
    GrainType("Рис", Color(0xFFFFFDF2), Color(0xFFE6DFC4)),
    GrainType("Гречка", Color(0xFF8D5B34), Color(0xFF6B4023)),
    GrainType("Горох", Color(0xFF8BC34A), Color(0xFF5E9A2A)),
    GrainType("Чечевица", Color(0xFFE2694A), Color(0xFFB84A30))
)

private class Grain(var x: Float, var y: Float, var vx: Float, var vy: Float, val shade: Float)

private class Spark(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    val color: Color, var life: Float, val size: Float
)

private class GrainWorld(private val dp: Float, val fillTarget: Float) {
    var w = 0f
    var h = 0f
    val colW = 4f * dp
    var cols = 0
    var heights = FloatArray(0)
    var jarL = 0   // первая колонка левой стенки
    var jarR = 0   // последняя колонка правой стенки
    val wallCols = 3
    val jarH = 230f * dp
    val grains = ArrayList<Grain>()
    val sparks = ArrayList<Spark>()
    var typeIdx = 0
    var emptying = false
    var pouring = false
    var tx = 0f
    var ty = 0f
    var spawnAcc = 0f

    var onFilled: () -> Unit = {}

    val floorY get() = h - 22f * dp
    private val grainArea = 7f * dp * dp

    fun init(width: Float, height: Float) {
        w = width
        h = height
        cols = (w / colW).toInt() + 1
        heights = FloatArray(cols)
        val jarCols = (170f * dp / colW).toInt() + wallCols * 2
        jarL = (cols - jarCols) / 2
        jarR = jarL + jarCols - 1
    }

    private fun isWall(c: Int) = (c in jarL until jarL + wallCols) || (c in jarR - wallCols + 1..jarR)

    private fun interiorRange() = (jarL + wallCols)..(jarR - wallCols)

    fun fill(): Float {
        val r = interiorRange()
        var sum = 0f
        for (c in r) sum += heights[c]
        val cnt = (r.last - r.first + 1)
        return (sum / cnt / (jarH * fillTarget)).coerceIn(0f, 1.2f)
    }

    fun spark(x: Float, y: Float, color: Color, n: Int) {
        repeat(n) {
            val a = Random.nextFloat() * 6.28f
            val s = (Random.nextFloat() * 260f + 90f) * dp
            sparks.add(
                Spark(x, y, kotlin.math.cos(a) * s, kotlin.math.sin(a) * s - 160f * dp,
                    if (Random.nextBoolean()) color else Color.White, 1f,
                    (Random.nextFloat() * 4f + 3f) * dp)
            )
        }
    }

    fun step(dtRaw: Float) {
        if (w <= 0f) return
        val dt = dtRaw.coerceIn(0.001f, 0.033f)

        if (pouring && !emptying) {
            spawnAcc += 170f * dt
            while (spawnAcc >= 1f) {
                spawnAcc -= 1f
                grains.add(
                    Grain(
                        tx + (Random.nextFloat() - 0.5f) * 10f * dp, ty,
                        (Random.nextFloat() - 0.5f) * 40f * dp, 40f * dp,
                        Random.nextFloat()
                    )
                )
            }
        }

        val it = grains.iterator()
        while (it.hasNext()) {
            val g = it.next()
            g.vy += 1900f * dp * dt
            g.x += g.vx * dt
            g.y += g.vy * dt
            var col = (g.x / colW).toInt().coerceIn(0, cols - 1)
            val wallTop = floorY - jarH
            val surface = floorY - heights[col]
            if (isWall(col)) {
                // Крупа лежит на стенке — скатывается наружу.
                if (g.y >= wallTop) {
                    col = if (col < (jarL + jarR) / 2) jarL - 1 else jarR + 1
                    col = col.coerceIn(0, cols - 1)
                    heights[col] += grainArea / colW
                    it.remove()
                }
                continue
            }
            if (g.y >= surface) {
                heights[col] += grainArea / colW
                it.remove()
            }
        }

        // Осыпание: угол естественного откоса.
        val maxDiff = colW * 0.85f
        repeat(3) {
            for (i in 0 until cols - 1) {
                if (isWall(i) || isWall(i + 1)) continue
                val d = heights[i] - heights[i + 1]
                if (d > maxDiff) {
                    val m = (d - maxDiff) * 0.35f
                    heights[i] -= m
                    heights[i + 1] += m
                } else if (d < -maxDiff) {
                    val m = (-d - maxDiff) * 0.35f
                    heights[i] += m
                    heights[i + 1] -= m
                }
            }
        }

        // Верх банки: на внутренний край стенки зерно не должно «висеть» выше стенки.
        for (c in interiorRange()) heights[c] = min(heights[c], jarH * 1.05f)

        if (emptying) {
            var any = false
            for (c in 0 until cols) {
                if (isWall(c)) continue
                heights[c] *= 0.94f
                if (heights[c] < 0.6f) heights[c] = 0f else any = true
            }
            if (!any) emptying = false
        } else if (fill() >= 1f) {
            emptying = true
            val cx = (jarL + jarR) / 2f * colW
            spark(cx, floorY - jarH * 0.6f, GRAIN_TYPES[typeIdx].color, 34)
            typeIdx = (typeIdx + 1) % GRAIN_TYPES.size
            onFilled()
        }

        val si = sparks.iterator()
        while (si.hasNext()) {
            val s = si.next()
            s.vy += 520f * dp * dt
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.life -= dt * 0.9f
            if (s.life <= 0f || s.y > h + 40f) si.remove()
        }
    }
}

@Composable
fun GrainPouringScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val ageProfile = LocalAgeProfile.current
    val dp = LocalDensity.current.density

    val world = remember(ageProfile.isToddler2) {
        GrainWorld(dp = dp, fillTarget = if (ageProfile.isToddler2) 0.55f else 0.75f)
    }
    var stars by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var loopHandle by remember { androidx.compose.runtime.mutableStateOf<com.example.shared.LoopHandle?>(null) }

    fun startPour() {
        if (loopHandle == null) loopHandle = soundManager.startLoop("grain_pour")
    }
    fun stopPour() {
        loopHandle?.let { soundManager.stopLoop(it) }
        loopHandle = null
    }

    world.onFilled = {
        stars += 1
        stopPour()
        soundPlayer.playFanfare()
    }

    DisposableEffect(Unit) {
        onDispose {
            world.pouring = false
            loopHandle?.let { soundManager.stopLoop(it, 0) }
            loopHandle = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF8E1), Color(0xFFFFECB3))))
            .testTag("grain_pouring_root")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val active = event.changes.firstOrNull { it.pressed }
                            if (active != null) {
                                world.tx = active.position.x
                                world.ty = active.position.y
                                if (!world.pouring) {
                                    world.pouring = true
                                    if (!world.emptying) startPour()
                                }
                            } else if (world.pouring) {
                                world.pouring = false
                                stopPour()
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            if (world.w != size.width || world.h != size.height) world.init(size.width, size.height)
            @Suppress("UNUSED_VARIABLE") val redraw = tick

            val type = GRAIN_TYPES[world.typeIdx]
            val floor = world.floorY
            val colW = world.colW

            // Стол.
            drawRect(Color(0xFFBCAAA4), Offset(0f, floor), Size(size.width, size.height - floor))

            // Банка (стекло) позади крупы.
            val jl = (world.jarL + world.wallCols) * colW
            val jr = (world.jarR - world.wallCols + 1) * colW
            val top = floor - world.jarH
            drawRoundRect(
                Color.White.copy(alpha = 0.35f), Offset(jl, top), Size(jr - jl, world.jarH),
                CornerRadius(10f * dp)
            )

            // Крупа: горка из столбиков.
            for (c in 0 until world.cols) {
                val hh = world.heights[c]
                if (hh <= 0.5f) continue
                val x = c * colW
                drawRect(type.color, Offset(x, floor - hh), Size(colW + 1f, hh))
                // Зернистость: тёмные полоски по высоте.
                var yy = 6f * dp
                var k = (c * 7) % 5
                while (yy < hh) {
                    if (k % 2 == 0) drawRect(type.dark.copy(alpha = 0.35f), Offset(x, floor - yy), Size(colW + 1f, 1.5f * dp))
                    yy += 7f * dp
                    k++
                }
            }

            // Падающие зёрна.
            for (g in world.grains) {
                drawCircle(
                    if (g.shade > 0.5f) type.color else type.dark,
                    2.3f * dp, Offset(g.x, g.y)
                )
            }

            // Стенки банки поверх крупы.
            val glassEdge = Color(0xFF90CAF9)
            drawLine(glassEdge, Offset(jl, top), Offset(jl, floor), 5f * dp, StrokeCap.Round)
            drawLine(glassEdge, Offset(jr, top), Offset(jr, floor), 5f * dp, StrokeCap.Round)
            drawLine(glassEdge, Offset(jl - 6f * dp, top), Offset(jr + 6f * dp, top), 4f * dp, StrokeCap.Round)

            // Отметка уровня, до которого нужно насыпать.
            val markY = floor - world.jarH * world.fillTarget
            val dash = 10f * dp
            var dx = jl
            while (dx < jr) {
                drawLine(Color(0xFFFF7043), Offset(dx, markY), Offset(min(dx + dash, jr), markY), 3f * dp)
                dx += dash * 2f
            }

            // Стаканчик в руке.
            if (world.pouring && !world.emptying) {
                val cup = Path().apply {
                    moveTo(world.tx - 26f * dp, world.ty - 52f * dp)
                    lineTo(world.tx + 26f * dp, world.ty - 52f * dp)
                    lineTo(world.tx + 16f * dp, world.ty - 6f * dp)
                    lineTo(world.tx - 16f * dp, world.ty - 6f * dp)
                    close()
                }
                drawPath(cup, Color(0xFF4DB6AC))
                drawPath(cup, Color(0xFF00897B), style = Stroke(width = 3f * dp))
            }

            for (s in world.sparks) {
                drawCircle(s.color.copy(alpha = s.life.coerceIn(0f, 1f)), s.size, Offset(s.x, s.y))
            }
        }

        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(88.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.85f),
                shadowElevation = 4.dp
            ) {
                Text(
                    "Насыпь ${GRAIN_TYPES[world.typeIdx].name.lowercase()} в банку!  ⭐ $stars",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5D4037)
                )
            }
        }
    }

    LaunchedEffect(world) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) world.step((now - last) / 1_000_000_000f)
                last = now
                tick++
            }
        }
    }
}
