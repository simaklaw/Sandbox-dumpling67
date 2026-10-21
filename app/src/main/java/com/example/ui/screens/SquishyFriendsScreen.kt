package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * «Мялка» — мягкие друзья на честной физике мягкого тела.
 *
 * Каждый друг — «воздушный шарик из желе»: кольцо точек, связанных пружинами,
 * с внутренним давлением (площадь стремится вернуться к исходной) и гравитацией.
 * Нажми в центр — вмятина, возьмись за край — растянется, отпусти — пружинит и
 * дрожит, брось — упадёт и расплющится о пол. Чем больше мнёшь, тем счастливее
 * друг (шкала над ним). Шкала полная — друг подпрыгивает от радости, смеётся и
 * дарит тебе звёздочку.
 */

private class SqPoint(var x: Float, var y: Float) {
    var px = x
    var py = y
}

private class SqBlob(
    cx: Float,
    cy: Float,
    val r: Float,
    val color: Color,
    val dark: Color
) {
    val n = 18
    /** 0 until n — кольцо, n — центр. */
    val pts = ArrayList<SqPoint>()
    var area0 = 0f
    var happy = 0f
    var touchedNow = false
    var squishCooldown = 0f

    init {
        for (i in 0 until n) {
            val a = (2.0 * PI * i / n).toFloat()
            pts.add(SqPoint(cx + cos(a) * r, cy + sin(a) * r))
        }
        pts.add(SqPoint(cx, cy))
        area0 = area()
    }

    fun ring(i: Int): SqPoint = pts[((i % n) + n) % n]
    val center: SqPoint get() = pts[n]

    fun area(): Float {
        var s = 0f
        for (i in 0 until n) {
            val a = pts[i]
            val b = ring(i + 1)
            s += a.x * b.y - b.x * a.y
        }
        return abs(s) * 0.5f
    }

    fun com(): Offset {
        var sx = 0f
        var sy = 0f
        for (i in 0 until n) { sx += pts[i].x; sy += pts[i].y }
        return Offset(sx / n, sy / n)
    }
}

private class SqGrab(val blob: SqBlob, val poke: Boolean, val idx: Int, var tx: Float, var ty: Float)

private class SqSpark(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    val color: Color, var life: Float, val size: Float
)

private class SquishWorld(private val dp: Float, private val count: Int) {
    var w = 0f
    var h = 0f
    val blobs = ArrayList<SqBlob>()
    val sparks = ArrayList<SqSpark>()
    val grabs = HashMap<Long, SqGrab>()
    var acc = 0f

    var onSquish: () -> Unit = {}
    var onTouchStart: () -> Unit = {}
    var onCelebrate: () -> Unit = {}

    private val palette = listOf(
        Color(0xFFFF8FB1) to Color(0xFFD81B60),
        Color(0xFF8DE0B5) to Color(0xFF2E9E6B),
        Color(0xFFFFD66B) to Color(0xFFE0A100),
        Color(0xFF9BB8FF) to Color(0xFF4A67D6)
    )

    private val floorY get() = h - 28f * dp

    fun init(width: Float, height: Float) {
        w = width
        h = height
        blobs.clear()
        val radii = if (count <= 2) listOf(92f, 82f) else listOf(78f, 66f, 86f)
        for (i in 0 until count) {
            val r = radii[i % radii.size] * dp
            val (c, d) = palette[i % palette.size]
            blobs.add(SqBlob(w * (i + 1f) / (count + 1f), floorY - r - 10f * dp, r, c, d))
        }
    }

    fun down(id: Long, x: Float, y: Float) {
        // Верхний (последний) друг под пальцем.
        val blob = blobs.lastOrNull {
            val c = it.com()
            val dx = c.x - x
            val dy = c.y - y
            sqrt(dx * dx + dy * dy) < it.r * 1.35f
        } ?: return
        val c = blob.com()
        val dCenter = sqrt((c.x - x) * (c.x - x) + (c.y - y) * (c.y - y))
        val poke = dCenter < blob.r * 0.6f
        var best = 0
        var bestD = Float.MAX_VALUE
        for (i in 0 until blob.n) {
            val p = blob.pts[i]
            val d = (p.x - x) * (p.x - x) + (p.y - y) * (p.y - y)
            if (d < bestD) { bestD = d; best = i }
        }
        grabs[id] = SqGrab(blob, poke, best, x, y)
        onTouchStart()
    }

    fun move(id: Long, x: Float, y: Float) {
        val g = grabs[id] ?: return
        val speed = abs(x - g.tx) + abs(y - g.ty)
        g.tx = x
        g.ty = y
        if (speed > 6f * dp && g.blob.squishCooldown <= 0f) {
            g.blob.squishCooldown = 0.38f
            onSquish()
        }
    }

    fun up(id: Long) {
        grabs.remove(id)
    }

    fun spark(x: Float, y: Float, color: Color, n: Int) {
        repeat(n) {
            val a = Random.nextFloat() * 6.28f
            val s = (Random.nextFloat() * 240f + 80f) * dp
            sparks.add(
                SqSpark(x, y, cos(a) * s, sin(a) * s - 140f * dp,
                    if (Random.nextBoolean()) color else Color.White,
                    1f, (Random.nextFloat() * 4f + 3f) * dp)
            )
        }
    }

    private fun satisfy(a: SqPoint, b: SqPoint, rest: Float, k: Float) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
        val diff = (d - rest) / d * 0.5f * k
        a.x += dx * diff
        a.y += dy * diff
        b.x -= dx * diff
        b.y -= dy * diff
    }

    private fun substep(dt: Float) {
        val g = 2300f * dp
        for (blob in blobs) {
            for (p in blob.pts) {
                val vx = (p.x - p.px) * 0.996f
                val vy = (p.y - p.py) * 0.996f
                p.px = p.x
                p.py = p.y
                p.x += vx
                p.y += vy + g * dt * dt
            }
        }

        for (blob in blobs) {
            val n = blob.n
            val r = blob.r
            val side = 2f * r * sin((PI / n).toFloat())
            val skip = 2f * r * sin((2.0 * PI / n).toFloat())
            for (iter in 0 until 4) {
                for (i in 0 until n) {
                    val a = blob.pts[i]
                    satisfy(a, blob.ring(i + 1), side, 1f)
                    satisfy(a, blob.ring(i + 2), skip, 0.8f)
                    satisfy(a, blob.center, r, 0.9f)
                    if (i < n / 2) satisfy(a, blob.ring(i + n / 2), 2f * r, 0.35f)
                }
                // Внутреннее давление: стремимся восстановить площадь.
                val ratio = (blob.area0 / blob.area().coerceAtLeast(1f) - 1f).coerceIn(-0.6f, 0.9f)
                val push = ratio * r * 0.05f
                for (i in 0 until n) {
                    val prev = blob.ring(i - 1)
                    val next = blob.ring(i + 1)
                    var tx = next.x - prev.x
                    var ty = next.y - prev.y
                    val len = sqrt(tx * tx + ty * ty).coerceAtLeast(0.0001f)
                    tx /= len
                    ty /= len
                    val p = blob.pts[i]
                    p.x += ty * push
                    p.y += -tx * push
                }
                applyTouches(blob)
            }
        }

        // Друзья не проходят друг сквозь друга.
        for (i in 0 until blobs.size) {
            for (j in i + 1 until blobs.size) {
                val a = blobs[i]
                val b = blobs[j]
                val ca = a.com()
                val cb = b.com()
                val dx = cb.x - ca.x
                val dy = cb.y - ca.y
                val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
                val minD = (a.r + b.r) * 0.95f
                if (d < minD) {
                    val push = (minD - d) * 0.5f
                    val nx = dx / d
                    val ny = dy / d
                    for (p in a.pts) { p.x -= nx * push; p.y -= ny * push }
                    for (p in b.pts) { p.x += nx * push; p.y += ny * push }
                }
            }
        }

        // Пол и стены.
        val fy = floorY
        for (blob in blobs) {
            for (p in blob.pts) {
                if (p.y > fy) {
                    val vy = p.y - p.py
                    p.y = fy
                    p.py = p.y + vy * 0.2f
                    val vx = p.x - p.px
                    p.px = p.x - vx * 0.82f
                }
                if (p.x < 0f) { p.x = 0f; p.px = p.x + (p.x - p.px) * -0.3f }
                if (p.x > w) { p.x = w; p.px = p.x + (p.x - p.px) * -0.3f }
                if (p.y < 0f) { p.y = 0f }
            }
        }
    }

    private fun applyTouches(blob: SqBlob) {
        for (g in grabs.values) {
            if (g.blob !== blob) continue
            if (g.poke) {
                val rad = blob.r * 0.85f
                for (i in 0 until blob.n) {
                    val p = blob.pts[i]
                    val dx = p.x - g.tx
                    val dy = p.y - g.ty
                    val d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
                    if (d < rad) {
                        val k = 0.5f
                        p.x += (g.tx + dx / d * rad - p.x) * k
                        p.y += (g.ty + dy / d * rad - p.y) * k
                    }
                }
            } else {
                val p = blob.pts[g.idx]
                p.x = g.tx
                p.y = g.ty
                p.px = g.tx
                p.py = g.ty
                for (o in listOf(-1, 1)) {
                    val q = blob.ring(g.idx + o)
                    q.x += (g.tx - q.x) * 0.2f
                    q.y += (g.ty - q.y) * 0.2f
                }
            }
        }
    }

    fun step(dtRaw: Float) {
        if (w <= 0f) return
        acc += dtRaw.coerceIn(0.001f, 0.05f)
        val sub = 1f / 120f
        for (b in blobs) b.touchedNow = false
        for (g in grabs.values) g.blob.touchedNow = true
        while (acc >= sub) {
            substep(sub)
            acc -= sub
        }
        val dt = dtRaw.coerceIn(0.001f, 0.05f)
        for (b in blobs) {
            b.squishCooldown = max(0f, b.squishCooldown - dt)
            if (b.touchedNow) b.happy += dt * 0.22f else b.happy = max(0f, b.happy - dt * 0.03f)
            if (b.happy >= 1f) {
                b.happy = 0f
                val c = b.com()
                spark(c.x, c.y - b.r * 0.4f, b.color, 26)
                // Подпрыгивает от радости (вертикальный импульс для Verlet).
                for (p in b.pts) p.py = p.y + b.r * 0.07f
                onCelebrate()
            }
        }
        val it = sparks.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.vy += 520f * dp * dt
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.life -= dt * 0.9f
            if (s.life <= 0f || s.y > h + 40f) it.remove()
        }
    }
}

@Composable
fun SquishyFriendsScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val ageProfile = LocalAgeProfile.current
    val dp = LocalDensity.current.density

    val world = remember(ageProfile.isToddler2) {
        SquishWorld(dp = dp, count = if (ageProfile.isToddler2) 2 else 3)
    }
    var stars by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }

    world.onTouchStart = {
        soundManager.play("squish")
        soundPlayer.vibrateSquishDeform()
    }
    world.onSquish = {
        soundManager.play("squish", rate = 0.85f + Random.nextFloat() * 0.4f)
        soundPlayer.vibrateDragTick()
    }
    world.onCelebrate = {
        stars += 1
        soundManager.play("giggle")
        soundPlayer.playChime(1.1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF3E0), Color(0xFFFFE0F0), Color(0xFFE1F5FE))))
            .testTag("squishy_friends_root")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (c in event.changes) {
                                val id = c.id.value
                                if (c.pressed && !c.previousPressed) {
                                    world.down(id, c.position.x, c.position.y)
                                } else if (c.pressed) {
                                    world.move(id, c.position.x, c.position.y)
                                } else if (c.previousPressed) {
                                    world.up(id)
                                }
                                c.consume()
                            }
                        }
                    }
                }
        ) {
            if (world.w != size.width || world.h != size.height) {
                if (world.w == 0f) world.init(size.width, size.height)
                else { world.w = size.width; world.h = size.height }
            }
            @Suppress("UNUSED_VARIABLE") val redraw = tick

            // Пол.
            val floor = size.height - 28f * dp
            drawRect(Color(0xFFFFCCBC), Offset(0f, floor), Size(size.width, size.height - floor))

            for (blob in world.blobs) {
                // Тень.
                val c = blob.com()
                drawOval(
                    Color.Black.copy(alpha = 0.1f),
                    Offset(c.x - blob.r * 0.9f, floor - 6f * dp),
                    Size(blob.r * 1.8f, 14f * dp)
                )
                // Тело: сглаженный контур через середины отрезков.
                val path = Path()
                val n = blob.n
                val first = blob.ring(0)
                val last = blob.ring(n - 1)
                path.moveTo((first.x + last.x) / 2f, (first.y + last.y) / 2f)
                for (i in 0 until n) {
                    val cur = blob.ring(i)
                    val nxt = blob.ring(i + 1)
                    path.quadraticTo(cur.x, cur.y, (cur.x + nxt.x) / 2f, (cur.y + nxt.y) / 2f)
                }
                path.close()
                var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
                var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
                for (i in 0 until n) {
                    val p = blob.pts[i]
                    minX = min(minX, p.x); maxX = max(maxX, p.x)
                    minY = min(minY, p.y); maxY = max(maxY, p.y)
                }
                val bw = maxX - minX
                val bh = maxY - minY
                drawPath(
                    path,
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.9f), blob.color, blob.dark.copy(alpha = 0.85f)),
                        center = Offset(c.x - bw * 0.18f, c.y - bh * 0.22f),
                        radius = max(bw, bh) * 0.85f
                    )
                )
                drawPath(path, blob.dark.copy(alpha = 0.55f), style = Stroke(width = 3f * dp))

                // Лицо.
                val squeezed = blob.touchedNow || abs(blob.area0 / blob.area().coerceAtLeast(1f) - 1f) > 0.14f
                val happyFace = blob.happy > 0.45f
                val eyeDx = bw * 0.2f
                val eyeY = c.y - bh * 0.08f
                val eyeR = bw * 0.075f
                for (side in listOf(-1f, 1f)) {
                    val ex = c.x + side * eyeDx
                    if (squeezed) {
                        // Зажмуренные глазки «^ ^».
                        drawArc(
                            color = Color(0xFF3E2723),
                            startAngle = 200f, sweepAngle = 140f, useCenter = false,
                            topLeft = Offset(ex - eyeR * 1.2f, eyeY - eyeR * 0.6f),
                            size = Size(eyeR * 2.4f, eyeR * 2f),
                            style = Stroke(width = 3.5f * dp, cap = StrokeCap.Round)
                        )
                    } else {
                        drawCircle(Color.White, eyeR * 1.35f, Offset(ex, eyeY))
                        drawCircle(Color(0xFF3E2723), eyeR * 0.85f, Offset(ex, eyeY + eyeR * 0.1f))
                        drawCircle(Color.White, eyeR * 0.3f, Offset(ex - eyeR * 0.25f, eyeY - eyeR * 0.2f))
                    }
                    drawCircle(
                        Color(0xFFFF4081).copy(alpha = 0.35f),
                        bw * 0.065f,
                        Offset(c.x + side * bw * 0.3f, c.y + bh * 0.1f)
                    )
                }
                val mouthY = c.y + bh * 0.12f
                if (happyFace || blob.touchedNow) {
                    drawOval(
                        Color(0xFF6D1B1B),
                        Offset(c.x - bw * 0.08f, mouthY),
                        Size(bw * 0.16f, bh * 0.14f)
                    )
                    drawOval(
                        Color(0xFFFF8A80),
                        Offset(c.x - bw * 0.05f, mouthY + bh * 0.07f),
                        Size(bw * 0.10f, bh * 0.06f)
                    )
                } else {
                    drawArc(
                        color = Color(0xFF3E2723),
                        startAngle = 20f, sweepAngle = 140f, useCenter = false,
                        topLeft = Offset(c.x - bw * 0.09f, mouthY - bh * 0.05f),
                        size = Size(bw * 0.18f, bh * 0.12f),
                        style = Stroke(width = 3f * dp, cap = StrokeCap.Round)
                    )
                }

                // Шкала счастья над другом.
                val barW = 56f * dp
                val barH = 8f * dp
                val bx = c.x - barW / 2f
                val by = minY - 18f * dp
                drawRoundRect(Color.White.copy(alpha = 0.7f), Offset(bx, by), Size(barW, barH),
                    androidx.compose.ui.geometry.CornerRadius(barH / 2f))
                drawRoundRect(Color(0xFFFF4081), Offset(bx, by), Size(barW * blob.happy.coerceIn(0f, 1f), barH),
                    androidx.compose.ui.geometry.CornerRadius(barH / 2f))
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
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(
                        "Мни, тяни и бросай!  ⭐ $stars",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5D4037)
                    )
                }
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
