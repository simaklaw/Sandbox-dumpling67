package com.example.ui.screens

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** «Кастрюля чудес»: вся графика рисуется векторно (Canvas), без эмодзи и растровых картинок. */

enum class KcIng(val tint: Color) {
    FLOUR(Color(0xFFF3E6C8)), MEAT(Color(0xFFE0675A)), EGG(Color(0xFFFFE08A)),
    GREENS(Color(0xFF5DBB63)), CHEESE(Color(0xFFFFC93C))
}

enum class KcResult { PERFECT, UNDER, OVER }

class KcSkin(val id: String, val name: String, val need: Int)

val KC_SKINS = listOf(
    KcSkin("classic", "Румяный мастер", 0),
    KcSkin("ninja", "Пельмень-ниндзя", 2),
    KcSkin("royal", "Царский пельмень", 4),
    KcSkin("star", "Звёздочка", 7),
    KcSkin("chef", "Шеф-пельмень", 10),
    KcSkin("cosmo", "Пельмень-космонавт", 14)
)

private val INK = Color(0xFF4A2E1A)

// ───────────────────────── ингредиенты ─────────────────────────

fun DrawScope.kcIngredient(type: KcIng, c: Offset, r: Float, rot: Float = 0f) {
    withTransform({ translate(c.x, c.y); rotate(rot, Offset.Zero) }) {
        when (type) {
            KcIng.FLOUR -> {
                val w = r * 1.55f
                val h = r * 1.75f
                // облачко муки
                drawCircle(Color.White.copy(alpha = 0.85f), r * 0.28f, Offset(-r * 0.3f, -h * 0.62f))
                drawCircle(Color.White.copy(alpha = 0.7f), r * 0.22f, Offset(r * 0.15f, -h * 0.72f))
                drawCircle(Color.White.copy(alpha = 0.55f), r * 0.16f, Offset(r * 0.42f, -h * 0.58f))
                // мешок
                val body = Path().apply {
                    moveTo(-w * 0.46f, -h * 0.40f)
                    quadraticTo(-w * 0.62f, h * 0.05f, -w * 0.52f, h * 0.48f)
                    quadraticTo(0f, h * 0.58f, w * 0.52f, h * 0.48f)
                    quadraticTo(w * 0.62f, h * 0.05f, w * 0.46f, -h * 0.40f)
                    close()
                }
                drawPath(body, Brush.horizontalGradient(listOf(Color(0xFFD9C093), Color(0xFFFFF3D6), Color(0xFFE0C898)), -w / 2, w / 2))
                drawPath(body, Color(0xFFB8975F), style = Stroke(r * 0.06f))
                // горловина мешка
                drawRoundRect(Color(0xFFD1B27A), Offset(-w * 0.5f, -h * 0.55f), Size(w, h * 0.22f), CornerRadius(r * 0.18f))
                drawRoundRect(Color(0xFFB8975F), Offset(-w * 0.5f, -h * 0.55f), Size(w, h * 0.22f), CornerRadius(r * 0.18f), style = Stroke(r * 0.05f))
                drawLine(Color(0xFFC0392B), Offset(-w * 0.48f, -h * 0.30f), Offset(w * 0.48f, -h * 0.30f), r * 0.1f, StrokeCap.Round)
                // колосок
                val stem = Path().apply { moveTo(0f, h * 0.38f); quadraticTo(r * 0.06f, h * 0.05f, 0f, -h * 0.12f) }
                drawPath(stem, Color(0xFF9A6B2F), style = Stroke(r * 0.05f, cap = StrokeCap.Round))
                for (i in 0..4) {
                    val yy = -h * 0.12f + i * h * 0.1f
                    drawOval(Color(0xFFE6A93A), Offset(-r * 0.2f, yy - r * 0.07f), Size(r * 0.17f, r * 0.12f))
                    drawOval(Color(0xFFE6A93A), Offset(r * 0.03f, yy - r * 0.02f), Size(r * 0.17f, r * 0.12f))
                }
                drawOval(Color.White.copy(alpha = 0.35f), Offset(-w * 0.4f, -h * 0.2f), Size(w * 0.12f, h * 0.55f))
            }
            KcIng.MEAT -> {
                val p = Path().apply {
                    moveTo(-r, 0f)
                    cubicTo(-r, -r * 0.9f, -r * 0.2f, -r, r * 0.5f, -r * 0.8f)
                    cubicTo(r * 1.1f, -r * 0.6f, r * 1.1f, r * 0.5f, r * 0.4f, r * 0.8f)
                    cubicTo(-r * 0.2f, r * 1.0f, -r, r * 0.8f, -r, 0f)
                    close()
                }
                // косточка
                drawLine(Color(0xFFFFF4DC), Offset(r * 0.35f, r * 0.45f), Offset(r * 1.05f, r * 0.95f), r * 0.24f, StrokeCap.Round)
                drawCircle(Color(0xFFFFF4DC), r * 0.2f, Offset(r * 1.1f, r * 0.82f))
                drawCircle(Color(0xFFFFF4DC), r * 0.2f, Offset(r * 0.95f, r * 1.05f))
                drawPath(p, Brush.radialGradient(listOf(Color(0xFFF59A8D), Color(0xFFE0675A), Color(0xFFB9453A)), Offset(-r * 0.3f, -r * 0.3f), r * 1.5f))
                drawPath(p, Color(0xFFFFC4B8), style = Stroke(r * 0.1f))
                for (i in 0..2) {
                    val m = Path().apply {
                        moveTo(-r * 0.5f, -r * 0.3f + i * r * 0.32f)
                        quadraticTo(0f, -r * 0.5f + i * r * 0.36f, r * 0.55f, -r * 0.2f + i * r * 0.3f)
                    }
                    drawPath(m, Color(0xFFFFD3C9), style = Stroke(r * 0.06f, cap = StrokeCap.Round))
                }
                drawOval(Color.White.copy(alpha = 0.45f), Offset(-r * 0.7f, -r * 0.65f), Size(r * 0.5f, r * 0.25f))
            }
            KcIng.EGG -> {
                val w = r * 1.15f
                val h = r * 1.45f
                drawOval(Brush.radialGradient(listOf(Color(0xFFFFFFF6), Color(0xFFFBEBC4), Color(0xFFE3C07C)), Offset(-w * 0.2f, -h * 0.2f), h * 0.85f), Offset(-w / 2, -h / 2), Size(w, h))
                drawOval(Color(0xFFD4AE63).copy(alpha = 0.8f), Offset(-w / 2, -h / 2), Size(w, h), style = Stroke(r * 0.05f))
                val sp = listOf(Offset(0.15f, 0.2f), Offset(-0.2f, 0.32f), Offset(0.25f, -0.1f), Offset(-0.05f, -0.3f), Offset(0.05f, 0.42f), Offset(-0.28f, -0.05f))
                for (s in sp) drawCircle(Color(0xFFB8864A).copy(alpha = 0.45f), r * 0.05f, Offset(s.x * w, s.y * h))
                drawOval(Color.White.copy(alpha = 0.7f), Offset(-w * 0.33f, -h * 0.36f), Size(w * 0.2f, h * 0.3f))
            }
            KcIng.GREENS -> {
                val tops = listOf(Offset(-r * 0.7f, -r * 0.95f), Offset(0f, -r * 1.15f), Offset(r * 0.7f, -r * 0.95f))
                for ((si, top) in tops.withIndex()) {
                    val stem = Path().apply { moveTo(0f, r * 0.9f); quadraticTo(top.x * 0.3f, 0f, top.x, top.y) }
                    drawPath(stem, Color(0xFF2E8B3E), style = Stroke(r * 0.07f, cap = StrokeCap.Round))
                    for (i in 0..6) {
                        val u = i / 6f
                        val x = top.x * u * (0.4f + 0.6f * u)
                        val y = r * 0.9f + (top.y - r * 0.9f) * u
                        val side = if (i % 2 == 0) -1f else 1f
                        val lr = r * (0.34f - 0.18f * u)
                        withTransform({ translate(x + side * lr * 0.5f, y); rotate(side * 35f + si * 8f, Offset.Zero) }) {
                            drawOval(if (i % 2 == 0) Color(0xFF6FCF6F) else Color(0xFF4CAF50), Offset(-lr * 0.5f, -lr * 0.25f), Size(lr, lr * 0.5f))
                        }
                    }
                }
                drawRoundRect(Color(0xFFC0392B), Offset(-r * 0.3f, r * 0.45f), Size(r * 0.6f, r * 0.16f), CornerRadius(r * 0.08f))
            }
            KcIng.CHEESE -> {
                val wedge = Path().apply {
                    moveTo(-r * 1.0f, r * 0.45f)
                    lineTo(r * 0.95f, -r * 0.6f)
                    quadraticTo(r * 1.1f, -r * 0.5f, r * 1.05f, -r * 0.3f)
                    lineTo(r * 1.05f, r * 0.7f)
                    quadraticTo(r * 1.0f, r * 0.8f, r * 0.85f, r * 0.8f)
                    lineTo(-r * 0.85f, r * 0.8f)
                    quadraticTo(-r * 1.05f, r * 0.8f, -r * 1.0f, r * 0.45f)
                    close()
                }
                drawPath(wedge, Brush.verticalGradient(listOf(Color(0xFFFFE680), Color(0xFFFFC93C), Color(0xFFF0A81C)), -r, r))
                drawPath(wedge, Color(0xFFD08A10), style = Stroke(r * 0.06f))
                // верхняя грань
                val top = Path().apply {
                    moveTo(-r * 0.98f, r * 0.42f); lineTo(r * 0.93f, -r * 0.58f)
                    lineTo(r * 0.93f, -r * 0.4f); lineTo(-r * 0.9f, r * 0.58f); close()
                }
                drawPath(top, Color(0xFFFFF3B0).copy(alpha = 0.85f))
                drawRect(Color(0xFFE58A2B), Offset(r * 0.82f, -r * 0.3f), Size(r * 0.23f, r * 1.1f))
                for (h in listOf(Offset(-0.35f, 0.38f), Offset(0.15f, 0.18f), Offset(0.45f, 0.5f))) {
                    drawCircle(Color(0xFFDB9812), r * 0.17f, Offset(h.x * r, h.y * r))
                    drawCircle(Color(0xFFFFE9A0).copy(alpha = 0.8f), r * 0.07f, Offset(h.x * r - r * 0.04f, h.y * r + r * 0.05f))
                }
            }
        }
    }
}

// ───────────────────────── пельмень ─────────────────────────

private fun dumplingPath(s: Float, inset: Float = 1f): Path {
    val p = Path()
    val n = 84
    for (i in 0..n) {
        val a = (2.0 * PI * i / n).toFloat()
        val ca = cos(a)
        val sa = sin(a)
        var rx = s * ca
        var ry = s * 0.80f * sa
        if (sa < 0f) {
            val k = abs(sa)
            ry = -s * 0.66f * k.pow(0.8f)
            val crimp = 1f + 0.05f * abs(sin(a * 12f))
            ry *= crimp
            rx *= 1f + 0.02f * abs(sin(a * 12f))
        }
        rx *= inset; ry *= inset
        if (i == 0) p.moveTo(rx, ry) else p.lineTo(rx, ry)
    }
    p.close()
    return p
}

private fun DrawScope.star5(c: Offset, rOut: Float, rIn: Float, rot: Float): Path {
    val p = Path()
    for (i in 0 until 10) {
        val a = rot + (PI * i / 5.0).toFloat() - (PI / 2.0).toFloat()
        val r = if (i % 2 == 0) rOut else rIn
        val x = c.x + r * cos(a)
        val y = c.y + r * sin(a)
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

/** s — половина ширины пельменя. res == null — «сырой/классический» вид. */
fun DrawScope.kcPelmen(
    c: Offset, s: Float, res: KcResult?, skin: String, t: Float,
    blink: Boolean = false, mouth: Float = 0f, squashY: Float = 1f, tilt: Float = 0f
) {
    val trem = if (res == KcResult.UNDER) sin(t * 38f) * s * 0.018f else 0f
    withTransform({
        translate(c.x + trem, c.y)
        rotate(tilt, Offset.Zero)
        scale(1f / squashY.coerceIn(0.6f, 1.4f).pow(0.5f), squashY, Offset.Zero)
    }) {
        // звёздное сияние за пельменем
        if (skin == "star") {
            val rot = t * 0.6f
            drawPath(star5(Offset.Zero, s * 1.55f, s * 0.8f, rot), Color(0xFFFFE066).copy(alpha = 0.55f))
            drawPath(star5(Offset.Zero, s * 1.3f, s * 0.7f, -rot * 0.7f), Color(0xFFFFF3B0).copy(alpha = 0.55f))
        }
        val light: Color
        val base: Color
        val edge: Color
        when (res) {
            KcResult.PERFECT -> { light = Color(0xFFFFEFC4); base = Color(0xFFFFD58A); edge = Color(0xFFEFA55B) }
            KcResult.UNDER -> { light = Color(0xFFF6F9FB); base = Color(0xFFDCE6EC); edge = Color(0xFFB4C4CE) }
            KcResult.OVER -> { light = Color(0xFFE9B676); base = Color(0xFFC98A4B); edge = Color(0xFF93602F) }
            null -> { light = Color(0xFFFFF4D6); base = Color(0xFFFBE3A8); edge = Color(0xFFE5BE78) }
        }
        // тень под пельменем
        drawOval(Color(0x22000000), Offset(-s * 0.9f, s * 0.62f), Size(s * 1.8f, s * 0.3f))
        val body = dumplingPath(s)
        drawPath(body, Brush.radialGradient(listOf(light, base, edge), Offset(-s * 0.3f, -s * 0.3f), s * 1.6f))
        drawPath(body, edge.copy(alpha = 0.9f).let { Color(it.red * 0.8f, it.green * 0.7f, it.blue * 0.6f, 0.75f) }, style = Stroke(s * 0.045f))
        // защипы вдоль края
        val fold = Path()
        val steps = 30
        for (i in 0..steps) {
            val a = (PI + 0.35 + (2 * PI - 0.7) * i / steps).toFloat()
            val x = s * 0.86f * cos(a)
            val y = -s * 0.66f * 0.78f * abs(sin(a)).pow(0.8f)
            if (i == 0) fold.moveTo(x, y) else fold.lineTo(x, y)
        }
        drawPath(fold, Color.White.copy(alpha = 0.55f), style = Stroke(s * 0.05f, cap = StrokeCap.Round))
        for (i in 1 until 11) {
            val a = (PI + 0.3 + (2 * PI - 0.6) * i / 11).toFloat()
            val x1 = s * 0.8f * cos(a); val y1 = -s * 0.5f * abs(sin(a)).pow(0.8f)
            val x2 = s * 0.97f * cos(a); val y2 = -s * 0.64f * abs(sin(a)).pow(0.8f)
            drawLine(edge.copy(alpha = 0.55f), Offset(x1, y1), Offset(x2, y2), s * 0.025f, StrokeCap.Round)
        }
        // объём брюшка
        drawOval(Brush.radialGradient(listOf(Color(0x00000000), Color(0x22703C10)), Offset(0f, s * 0.2f), s), Offset(-s * 0.9f, -s * 0.1f), Size(s * 1.8f, s * 0.85f))
        drawOval(Color.White.copy(alpha = 0.35f), Offset(-s * 0.62f, -s * 0.38f), Size(s * 0.38f, s * 0.16f))

        // лицо
        val eyeY = s * 0.04f
        val ex = s * 0.34f
        val er = s * 0.135f
        val dark = Color(0xFF3B2418)
        // румянец
        val cheek = if (res == KcResult.UNDER) Color(0xFFB9D3E6) else Color(0xFFFF8F8F)
        val cheekA = when (res) { KcResult.PERFECT -> 0.65f; KcResult.OVER -> 0.3f; else -> 0.45f }
        drawOval(cheek.copy(alpha = cheekA), Offset(-s * 0.68f, s * 0.2f), Size(s * 0.26f, s * 0.15f))
        drawOval(cheek.copy(alpha = cheekA), Offset(s * 0.42f, s * 0.2f), Size(s * 0.26f, s * 0.15f))
        if (blink || res == KcResult.OVER) {
            val lids = Stroke(s * 0.05f, cap = StrokeCap.Round)
            for (sx in listOf(-1f, 1f)) {
                val e = Path().apply {
                    moveTo(sx * ex - er * 1.1f, eyeY)
                    quadraticTo(sx * ex, eyeY + (if (res == KcResult.OVER) er * 0.9f else -er * 0.9f), sx * ex + er * 1.1f, eyeY)
                }
                drawPath(e, dark, style = lids)
            }
            if (res == KcResult.OVER) {
                for (sx in listOf(-1f, 1f)) drawOval(Color(0x335A3A20), Offset(sx * ex - er, eyeY + er * 0.6f), Size(er * 2f, er * 0.7f))
            }
        } else {
            val big = if (res == KcResult.UNDER) 1.18f else 1f
            for (sx in listOf(-1f, 1f)) {
                drawOval(dark, Offset(sx * ex - er * big, eyeY - er * 1.15f * big), Size(er * 2f * big, er * 2.3f * big))
                drawCircle(Color.White, er * 0.46f * big, Offset(sx * ex - er * 0.28f * big, eyeY - er * 0.55f * big))
                drawCircle(Color.White.copy(alpha = 0.85f), er * 0.2f * big, Offset(sx * ex + er * 0.35f * big, eyeY + er * 0.35f * big))
            }
            if (res == KcResult.UNDER) {
                // слезинка
                val ty = eyeY + er * 1.6f + ((t * 1.3f) % 1f) * er * 2.2f
                val tear = Path().apply {
                    moveTo(-ex, ty - er * 0.7f); quadraticTo(-ex - er * 0.55f, ty + er * 0.3f, -ex, ty + er * 0.5f)
                    quadraticTo(-ex + er * 0.55f, ty + er * 0.3f, -ex, ty - er * 0.7f)
                }
                drawPath(tear, Color(0xFF7FC8FF))
                // грустные брови
                for (sx in listOf(-1f, 1f)) drawLine(dark, Offset(sx * ex + sx * er * 1.0f, eyeY - er * 1.75f), Offset(sx * ex - sx * er * 1.0f, eyeY - er * 2.35f), s * 0.04f, StrokeCap.Round)
            }
        }
        // рот
        val mw = s * 0.17f
        val my = s * 0.3f
        when {
            mouth > 0.15f -> {
                val mh = s * 0.2f * mouth
                drawOval(Color(0xFF6B2A2A), Offset(-mw, my - mh * 0.3f), Size(mw * 2f, mh * 1.4f))
                drawOval(Color(0xFFFF8FA3), Offset(-mw * 0.6f, my + mh * 0.5f), Size(mw * 1.2f, mh * 0.6f))
            }
            res == KcResult.UNDER -> {
                val m = Path().apply { moveTo(-mw, my + s * 0.1f); quadraticTo(0f, my - s * 0.06f, mw, my + s * 0.1f) }
                drawPath(m, dark, style = Stroke(s * 0.045f, cap = StrokeCap.Round))
            }
            res == KcResult.OVER -> {
                val o = s * (0.07f + 0.015f * sin(t * 3f))
                drawOval(dark, Offset(-o, my - o * 0.2f), Size(o * 2f, o * 2.4f))
            }
            else -> {
                val m = Path().apply { moveTo(-mw, my); quadraticTo(0f, my + s * 0.2f, mw, my) }
                drawPath(m, dark, style = Stroke(s * 0.045f, cap = StrokeCap.Round))
            }
        }
        // дымок над переваренным
        if (res == KcResult.OVER) {
            for (i in 0..2) {
                val k = (t * 0.8f + i * 0.33f) % 1f
                val px = (i - 1) * s * 0.3f + sin(t * 2f + i) * s * 0.06f
                drawCircle(Color(0xCCEFEFEF), s * (0.13f + 0.12f * k), Offset(px, -s * 0.7f - k * s * 0.5f))
            }
        }
        // скины
        when (skin) {
            "ninja" -> {
                withTransform({ rotate(-5f, Offset(0f, -s * 0.3f)) }) {
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF3D4F91), Color(0xFF1F2A52)), -s * 0.46f, -s * 0.16f), Offset(-s * 1.03f, -s * 0.46f), Size(s * 2.06f, s * 0.28f), CornerRadius(s * 0.07f))
                    drawRoundRect(Color(0xFFD9DEE8), Offset(-s * 0.26f, -s * 0.44f), Size(s * 0.52f, s * 0.24f), CornerRadius(s * 0.05f))
                    drawPath(star5(Offset(0f, -s * 0.32f), s * 0.1f, s * 0.045f, 0f), Color(0xFF3D4F91))
                    drawLine(Color.White.copy(alpha = 0.3f), Offset(-s * 1.0f, -s * 0.42f), Offset(-s * 0.3f, -s * 0.42f), s * 0.03f, StrokeCap.Round)
                    for (k in 0..1) {
                        val sw = sin(t * 4f + k) * s * 0.05f
                        drawLine(Color(0xFF1F2A52), Offset(s * 0.98f, -s * 0.32f), Offset(s * (1.22f + k * 0.04f) + sw, -s * (0.05f - k * 0.2f)), s * 0.12f, StrokeCap.Round)
                    }
                }
            }
            "royal" -> {
                withTransform({ rotate(7f, Offset(0f, -s * 0.6f)) }) {
                    val cr = Path().apply {
                        moveTo(-s * 0.44f, -s * 0.5f); lineTo(-s * 0.46f, -s * 0.9f); lineTo(-s * 0.22f, -s * 0.7f)
                        lineTo(0f, -s * 1.0f); lineTo(s * 0.22f, -s * 0.7f); lineTo(s * 0.46f, -s * 0.9f); lineTo(s * 0.44f, -s * 0.5f); close()
                    }
                    drawPath(cr, Brush.verticalGradient(listOf(Color(0xFFFFE680), Color(0xFFE5A91A)), -s * 1.0f, -s * 0.5f))
                    drawPath(cr, Color(0xFF9A6B0A), style = Stroke(s * 0.04f))
                    for (px in listOf(-0.46f, 0f, 0.46f)) drawCircle(Color(0xFFFFD84D), s * 0.06f, Offset(px * s, (if (px == 0f) -1.02f else -0.92f) * s))
                    drawCircle(Color(0xFFE63946), s * 0.07f, Offset(0f, -s * 0.6f))
                    drawCircle(Color(0xFF3DA5E0), s * 0.05f, Offset(-s * 0.27f, -s * 0.58f))
                    drawCircle(Color(0xFF52C878), s * 0.05f, Offset(s * 0.27f, -s * 0.58f))
                    drawCircle(Color.White.copy(alpha = 0.7f), s * 0.025f, Offset(-s * 0.015f, -s * 0.625f))
                }
            }
            "star" -> {
                drawPath(star5(Offset(s * 0.55f, -s * 0.42f), s * 0.2f, s * 0.09f, t * 0.8f), Color(0xFFFFD83D))
                drawPath(star5(Offset(-s * 0.5f, -s * 0.5f), s * 0.12f, s * 0.055f, -t), Color(0xFFFFF3B0))
            }
            "chef" -> {
                drawRoundRect(Color.White, Offset(-s * 0.48f, -s * 0.56f), Size(s * 0.96f, s * 0.2f), CornerRadius(s * 0.05f))
                for ((cx, cy, rr) in listOf(Triple(-0.36f, -0.78f, 0.27f), Triple(0f, -0.92f, 0.32f), Triple(0.36f, -0.78f, 0.27f), Triple(0f, -0.72f, 0.3f))) {
                    drawCircle(Color.White, s * rr, Offset(cx * s, cy * s))
                    drawCircle(Color(0xFFD9D9D9), s * rr, Offset(cx * s, cy * s), style = Stroke(s * 0.025f))
                }
                drawRoundRect(Color(0xFFD9D9D9), Offset(-s * 0.48f, -s * 0.56f), Size(s * 0.96f, s * 0.2f), CornerRadius(s * 0.05f), style = Stroke(s * 0.025f))
            }
            "cosmo" -> {
                drawCircle(Color(0x1FBEE6FF), s * 1.22f, Offset(0f, -s * 0.02f))
                drawCircle(Color(0xCCFFFFFF), s * 1.22f, Offset(0f, -s * 0.02f), style = Stroke(s * 0.07f))
                drawArc(Color.White.copy(alpha = 0.75f), 200f, 45f, false, Offset(-s * 1.0f, -s * 1.0f), Size(s * 2.0f, s * 2.0f), style = Stroke(s * 0.06f, cap = StrokeCap.Round))
                drawRoundRect(Color(0xFFB8C2CC), Offset(-s * 0.7f, s * 0.9f), Size(s * 1.4f, s * 0.2f), CornerRadius(s * 0.08f))
                drawLine(Color(0xFFB8C2CC), Offset(s * 0.6f, -s * 1.1f), Offset(s * 0.75f, -s * 1.4f), s * 0.05f, StrokeCap.Round)
                drawCircle(Color(0xFFFF5D5D), s * 0.08f, Offset(s * 0.75f, -s * 1.42f))
            }
        }
    }
}

// ───────────────────────── мишка ─────────────────────────

/** c — центр головы, s — единица масштаба (радиус головы ≈ 0.62 s). */
fun DrawScope.kcBear(c: Offset, s: Float, t: Float, mouth: Float, chew: Float, blink: Boolean, cheer: Float, wink: Boolean) {
    val fur = Color(0xFFB9784A)
    val furDark = Color(0xFF8E5530)
    val furLight = Color(0xFFE7B98A)
    val dark = Color(0xFF3B2418)
    val bob = sin(t * 2.2f) * s * 0.012f
    withTransform({ translate(c.x, c.y + bob - cheer * s * 0.12f) }) {
        // тень
        drawOval(Color(0x22000000), Offset(-s * 0.9f, s * 1.62f), Size(s * 1.8f, s * 0.22f))
        // руки (за телом)
        val armUp = cheer
        for (sx in listOf(-1f, 1f)) {
            withTransform({ translate(sx * s * 0.62f, s * 0.95f); rotate(sx * (28f - armUp * 150f) + sin(t * 2f) * 2f, Offset(0f, -s * 0.2f)) }) {
                drawOval(Brush.horizontalGradient(listOf(furDark, fur), -s * 0.2f, s * 0.2f), Offset(-s * 0.19f, -s * 0.22f), Size(s * 0.38f, s * 0.66f))
                drawOval(furLight, Offset(-s * 0.1f, s * 0.28f), Size(s * 0.2f, s * 0.13f))
            }
        }
        // ноги
        for (sx in listOf(-1f, 1f)) {
            drawOval(Brush.radialGradient(listOf(fur, furDark), Offset(sx * s * 0.35f, s * 1.45f), s * 0.4f), Offset(sx * s * 0.34f - s * 0.22f, s * 1.38f), Size(s * 0.44f, s * 0.3f))
            drawOval(furLight, Offset(sx * s * 0.34f - s * 0.12f, s * 1.5f), Size(s * 0.24f, s * 0.12f))
        }
        // тело
        drawOval(Brush.radialGradient(listOf(Color(0xFFD08F5E), fur, furDark), Offset(-s * 0.2f, s * 0.8f), s * 1.0f), Offset(-s * 0.68f, s * 0.5f), Size(s * 1.36f, s * 1.1f))
        drawOval(Brush.radialGradient(listOf(Color(0xFFFFE2BC), furLight), Offset(0f, s * 1.0f), s * 0.6f), Offset(-s * 0.4f, s * 0.78f), Size(s * 0.8f, s * 0.7f))
        // слюнявчик
        val bib = Path().apply {
            moveTo(-s * 0.34f, s * 0.58f); quadraticTo(0f, s * 0.78f, s * 0.34f, s * 0.58f)
            lineTo(s * 0.26f, s * 1.0f); quadraticTo(0f, s * 1.1f, -s * 0.26f, s * 1.0f); close()
        }
        drawPath(bib, Color(0xFFFFFFFF))
        drawPath(bib, Color(0xFFE63946), style = Stroke(s * 0.035f))
        for (i in 0..2) drawCircle(Color(0xFFE63946).copy(alpha = 0.7f), s * 0.035f, Offset((i - 1) * s * 0.14f, s * (0.85f + 0.04f * (i % 2))))
        // уши
        for (sx in listOf(-1f, 1f)) {
            drawCircle(Brush.radialGradient(listOf(fur, furDark), Offset(sx * s * 0.5f, -s * 0.5f), s * 0.3f), s * 0.24f, Offset(sx * s * 0.5f, -s * 0.5f))
            drawCircle(Color(0xFFF4B6A0), s * 0.13f, Offset(sx * s * 0.5f, -s * 0.5f))
        }
        // голова
        drawCircle(Brush.radialGradient(listOf(Color(0xFFD99A68), fur, furDark), Offset(-s * 0.2f, -s * 0.25f), s * 0.9f), s * 0.64f, Offset.Zero)
        drawCircle(Color.White.copy(alpha = 0.18f), s * 0.12f, Offset(-s * 0.32f, -s * 0.32f))
        // мордочка
        drawOval(Brush.radialGradient(listOf(Color(0xFFFFE9CC), furLight), Offset(0f, s * 0.22f), s * 0.4f), Offset(-s * 0.3f, s * 0.04f), Size(s * 0.6f, s * 0.44f))
        // щёки (надуваются при жевании)
        val puff = 1f + chew * 0.18f
        for (sx in listOf(-1f, 1f)) drawOval(Color(0xFFFF8F8F).copy(alpha = 0.5f), Offset(sx * s * 0.43f - s * 0.12f * puff, s * 0.15f), Size(s * 0.24f * puff, s * 0.16f))
        // нос
        drawOval(Brush.verticalGradient(listOf(Color(0xFF5A3A2A), dark), s * 0.08f, s * 0.2f), Offset(-s * 0.1f, s * 0.07f), Size(s * 0.2f, s * 0.14f))
        drawOval(Color.White.copy(alpha = 0.6f), Offset(-s * 0.055f, s * 0.085f), Size(s * 0.06f, s * 0.03f))
        // рот
        val my = s * 0.28f
        if (mouth > 0.08f) {
            val mw = s * (0.13f + 0.05f * mouth)
            val mh = s * 0.22f * mouth
            drawOval(Color(0xFF5E1E22), Offset(-mw, my - mh * 0.1f), Size(mw * 2f, mh))
            drawOval(Color(0xFFFF7E92), Offset(-mw * 0.6f, my + mh * 0.5f), Size(mw * 1.2f, mh * 0.45f))
        } else {
            val m = Path().apply { moveTo(0f, s * 0.21f); lineTo(0f, my) }
            drawPath(m, dark, style = Stroke(s * 0.035f, cap = StrokeCap.Round))
            val sm = Path().apply { moveTo(-s * 0.16f, my - s * 0.01f); quadraticTo(0f, my + s * 0.14f + cheer * s * 0.06f, s * 0.16f, my - s * 0.01f) }
            drawPath(sm, dark, style = Stroke(s * 0.035f, cap = StrokeCap.Round))
        }
        // глаза
        val eyeY = -s * 0.1f
        val ex = s * 0.25f
        val er = s * 0.075f
        for ((idx, sx) in listOf(-1f, 1f).withIndex()) {
            val closed = blink || (wink && idx == 1) || cheer > 0.6f
            if (closed) {
                val e = Path().apply { moveTo(sx * ex - er * 1.3f, eyeY + er * 0.4f); quadraticTo(sx * ex, eyeY - er * 1.4f, sx * ex + er * 1.3f, eyeY + er * 0.4f) }
                drawPath(e, dark, style = Stroke(s * 0.04f, cap = StrokeCap.Round))
            } else {
                drawOval(dark, Offset(sx * ex - er, eyeY - er * 1.25f), Size(er * 2f, er * 2.5f))
                drawCircle(Color.White, er * 0.5f, Offset(sx * ex - er * 0.3f, eyeY - er * 0.6f))
                drawCircle(Color.White.copy(alpha = 0.8f), er * 0.22f, Offset(sx * ex + er * 0.35f, eyeY + er * 0.4f))
            }
        }
    }
}

// ───────────────────────── кастрюля ─────────────────────────

class KcPot(val cx: Float, val rimY: Float, val rw: Float, val bh: Float) {
    val ry: Float get() = rw * 0.27f
    val bottom: Float get() = rimY + bh
    val brothY: Float get() = rimY + ry * 0.02f
}

fun DrawScope.kcStove(g: KcPot, boil: Float, t: Float) {
    val y0 = g.bottom + g.rw * 0.03f
    // конфорка
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF5A5A66), Color(0xFF2E2E38)), y0, y0 + g.rw * 0.22f), Offset(g.cx - g.rw * 1.05f, y0 + g.rw * 0.06f), Size(g.rw * 2.1f, g.rw * 0.2f), CornerRadius(g.rw * 0.06f))
    drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(g.cx - g.rw * 1.0f, y0 + g.rw * 0.075f), Size(g.rw * 2.0f, g.rw * 0.03f), CornerRadius(g.rw * 0.02f))
    // пламя
    val n = 9
    for (i in 0 until n) {
        val u = i / (n - 1f)
        val x = g.cx + (u - 0.5f) * g.rw * 1.45f
        val k = 0.5f + 0.5f * sin(t * 9f + i * 1.7f)
        val h = g.rw * (0.07f + 0.09f * boil + 0.04f * k * boil)
        val w = g.rw * 0.07f
        val f = Path().apply {
            moveTo(x - w, y0 + g.rw * 0.07f)
            quadraticTo(x - w * 0.8f, y0 + g.rw * 0.07f - h * 0.6f, x + sin(t * 7f + i) * w * 0.4f, y0 + g.rw * 0.07f - h)
            quadraticTo(x + w * 0.8f, y0 + g.rw * 0.07f - h * 0.6f, x + w, y0 + g.rw * 0.07f)
            close()
        }
        drawPath(f, Color(0xFFFF8A2B))
        withTransform({ scale(0.55f, 0.6f, Offset(x, y0 + g.rw * 0.07f)) }) { drawPath(f, Color(0xFFFFD84D)) }
        withTransform({ scale(0.28f, 0.3f, Offset(x, y0 + g.rw * 0.07f)) }) { drawPath(f, Color(0xFF5AB8FF)) }
    }
}

/** Корпус, горловина, бульон. Рисуется до содержимого и до летящих ингредиентов. */
fun DrawScope.kcPotBody(g: KcPot, broth: Color, boil: Float, t: Float) {
    val rw = g.rw
    val ry = g.ry
    val clay1 = Color(0xFF9E3F25)
    val clay2 = Color(0xFFE2754A)
    val clay3 = Color(0xFFF59A6B)
    // ручки
    for (sx in listOf(-1f, 1f)) {
        val y = g.rimY + g.bh * 0.17f
        drawArc(Color(0xFF8C3620), if (sx < 0) 90f else -90f, 180f, false, Offset(g.cx + sx * rw * 1.02f - rw * 0.26f, y - rw * 0.16f), Size(rw * 0.52f, rw * 0.34f), style = Stroke(rw * 0.11f))
        drawArc(Color(0xFFD45F35), if (sx < 0) 90f else -90f, 180f, false, Offset(g.cx + sx * rw * 1.02f - rw * 0.26f, y - rw * 0.16f), Size(rw * 0.52f, rw * 0.34f), style = Stroke(rw * 0.05f))
    }
    // донышко
    drawOval(Color(0xFF7A2D18), Offset(g.cx - rw * 0.62f, g.bottom - rw * 0.13f), Size(rw * 1.24f, rw * 0.26f))
    val body = Path().apply {
        moveTo(g.cx - rw, g.rimY)
        cubicTo(g.cx - rw * 1.12f, g.rimY + g.bh * 0.45f, g.cx - rw * 0.96f, g.rimY + g.bh * 0.95f, g.cx - rw * 0.62f, g.bottom)
        lineTo(g.cx + rw * 0.62f, g.bottom)
        cubicTo(g.cx + rw * 0.96f, g.rimY + g.bh * 0.95f, g.cx + rw * 1.12f, g.rimY + g.bh * 0.45f, g.cx + rw, g.rimY)
        close()
    }
    drawPath(body, Brush.horizontalGradient(listOf(clay1, clay2, clay3, clay2, clay1), g.cx - rw * 1.1f, g.cx + rw * 1.1f))
    clipPath(body) {
        // орнаментальный пояс
        val by = g.rimY + g.bh * 0.34f
        val bhh = g.bh * 0.2f
        drawRect(Color(0xFFFFF1D6), Offset(g.cx - rw * 1.2f, by), Size(rw * 2.4f, bhh))
        drawRect(Color(0xFF3E7CB1), Offset(g.cx - rw * 1.2f, by + bhh * 0.08f), Size(rw * 2.4f, bhh * 0.14f))
        drawRect(Color(0xFF3E7CB1), Offset(g.cx - rw * 1.2f, by + bhh * 0.78f), Size(rw * 2.4f, bhh * 0.14f))
        val n = 11
        for (i in 0 until n) {
            val x = g.cx - rw * 1.0f + i * (rw * 2.0f / (n - 1))
            val zig = Path().apply {
                moveTo(x - rw * 0.08f, by + bhh * 0.7f); lineTo(x, by + bhh * 0.3f); lineTo(x + rw * 0.08f, by + bhh * 0.7f); close()
            }
            drawPath(zig, if (i % 2 == 0) Color(0xFFE2754A) else Color(0xFF3E7CB1))
            drawCircle(Color(0xFFE2754A), rw * 0.018f, Offset(x, by + bhh * 0.5f))
        }
        // нижний декор: капельки
        for (i in 0 until 7) {
            val x = g.cx - rw * 0.8f + i * rw * 0.267f
            drawCircle(Color(0xFFFFF1D6).copy(alpha = 0.55f), rw * 0.03f, Offset(x, g.rimY + g.bh * 0.78f))
        }
        // глазурь: блик и тень
        drawRoundRect(Color.White.copy(alpha = 0.32f), Offset(g.cx - rw * 0.78f, g.rimY + g.bh * 0.12f), Size(rw * 0.12f, g.bh * 0.6f), CornerRadius(rw * 0.06f))
        drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(g.cx - rw * 0.76f, g.rimY + g.bh * 0.14f), Size(rw * 0.05f, g.bh * 0.18f), CornerRadius(rw * 0.03f))
        drawRect(Brush.verticalGradient(listOf(Color(0x00000000), Color(0x44401A0A)), g.rimY + g.bh * 0.7f, g.bottom), Offset(g.cx - rw * 1.2f, g.rimY + g.bh * 0.7f), Size(rw * 2.4f, g.bh * 0.35f))
    }
    // горловина (обод)
    drawOval(Brush.horizontalGradient(listOf(Color(0xFF8C3620), Color(0xFFF59A6B), Color(0xFF8C3620)), g.cx - rw, g.cx + rw), Offset(g.cx - rw * 1.06f, g.rimY - ry * 1.12f), Size(rw * 2.12f, ry * 2.24f))
    drawOval(Color(0xFF4A1E10), Offset(g.cx - rw * 0.93f, g.rimY - ry * 0.93f), Size(rw * 1.86f, ry * 1.86f))
    // бульон
    val bx = g.cx - rw * 0.88f
    val by2 = g.rimY - ry * 0.8f
    drawOval(Brush.verticalGradient(listOf(broth.copy(alpha = 1f), Color(broth.red * 0.82f, broth.green * 0.78f, broth.blue * 0.7f)), by2, by2 + ry * 1.6f), Offset(bx, by2), Size(rw * 1.76f, ry * 1.6f))
    // зыбь на поверхности
    val ripple = Path().apply { addOval(Rect(bx, by2, bx + rw * 1.76f, by2 + ry * 1.6f)) }
    clipPath(ripple) {
        for (i in 0..3) {
            val k = (t * (0.3f + boil * 0.9f) + i * 0.25f) % 1f
            drawOval(Color.White.copy(alpha = 0.22f * (1f - k)), Offset(g.cx - rw * 0.8f * k, g.rimY - ry * 0.7f * k - ry * 0.05f), Size(rw * 1.6f * k, ry * 1.4f * k), style = Stroke(rw * 0.012f))
        }
        drawOval(Color.White.copy(alpha = 0.18f), Offset(g.cx - rw * 0.6f, g.rimY - ry * 0.55f), Size(rw * 0.7f, ry * 0.3f))
    }
}

fun DrawScope.kcBrothClip(g: KcPot, block: DrawScope.() -> Unit) {
    val p = Path().apply { addOval(Rect(g.cx - g.rw * 0.88f, g.rimY - g.ry * 0.8f, g.cx + g.rw * 0.88f, g.rimY + g.ry * 0.8f)) }
    clipPath(p) { block() }
}

/** Всё, что падает в кастрюлю, обрезается по линии бульона — предмет «тонет» за передним краем. */
fun DrawScope.kcAboveBroth(g: KcPot, block: DrawScope.() -> Unit) {
    clipRect(left = 0f, top = 0f, right = size.width, bottom = g.brothY + g.ry * 0.2f) { block() }
}

/** Передняя кромка кастрюли — поверх падающих ингредиентов. */
fun DrawScope.kcPotLip(g: KcPot) {
    val rw = g.rw
    val ry = g.ry
    drawArc(Brush.horizontalGradient(listOf(Color(0xFF8C3620), Color(0xFFF59A6B), Color(0xFF8C3620)), g.cx - rw, g.cx + rw), 0f, 180f, false, Offset(g.cx - rw * 1.0f, g.rimY - ry * 1.0f), Size(rw * 2.0f, ry * 2.0f), style = Stroke(rw * 0.1f))
    drawArc(Color.White.copy(alpha = 0.35f), 20f, 70f, false, Offset(g.cx - rw * 0.97f, g.rimY - ry * 0.97f), Size(rw * 1.94f, ry * 1.94f), style = Stroke(rw * 0.025f, cap = StrokeCap.Round))
}

// ───────────────────────── окружение ─────────────────────────

fun DrawScope.kcWall(w: Float, h: Float, t: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF3DC), Color(0xFFFFDFB0), Color(0xFFF7C98A)), 0f, h))
    // плитка
    val tile = min(w, h) * 0.13f
    var y = h * 0.02f
    var row = 0
    while (y < h) {
        var x = if (row % 2 == 0) 0f else -tile / 2
        while (x < w) {
            drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(x + 2f, y + 2f), Size(tile - 4f, tile * 0.62f - 4f), CornerRadius(tile * 0.08f))
            x += tile
        }
        y += tile * 0.62f
        row++
    }
    // окно
    val ww = min(w * 0.34f, h * 0.2f)
    val wx = w * 0.07f
    val wy = h * 0.115f
    drawRoundRect(Color(0xFFB9773F), Offset(wx - ww * 0.06f, wy - ww * 0.06f), Size(ww * 1.12f, ww * 1.12f), CornerRadius(ww * 0.1f))
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF8ED1FF), Color(0xFFDFF3FF)), wy, wy + ww), Offset(wx, wy), Size(ww, ww), CornerRadius(ww * 0.06f))
    drawCircle(Color(0xFFFFE680), ww * 0.14f, Offset(wx + ww * 0.72f, wy + ww * 0.28f))
    drawCircle(Color(0x55FFE680), ww * 0.24f, Offset(wx + ww * 0.72f, wy + ww * 0.28f))
    drawOval(Color.White.copy(alpha = 0.9f), Offset(wx + ww * 0.1f, wy + ww * 0.4f + sin(t * 0.5f) * ww * 0.02f), Size(ww * 0.4f, ww * 0.16f))
    drawRect(Color(0xFFB9773F), Offset(wx + ww * 0.47f, wy), Size(ww * 0.06f, ww))
    drawRect(Color(0xFFB9773F), Offset(wx, wy + ww * 0.47f), Size(ww, ww * 0.06f))
    drawRoundRect(Color(0xFFD6935A), Offset(wx - ww * 0.1f, wy + ww * 1.02f), Size(ww * 1.2f, ww * 0.1f), CornerRadius(ww * 0.04f))
    // горшочек на подоконнике
    drawRoundRect(Color(0xFFD45F35), Offset(wx + ww * 0.62f, wy + ww * 0.86f), Size(ww * 0.28f, ww * 0.18f), CornerRadius(ww * 0.03f))
    for (i in 0..2) drawOval(Color(0xFF4CAF50), Offset(wx + ww * (0.6f + i * 0.09f), wy + ww * (0.62f - (i % 2) * 0.05f)), Size(ww * 0.1f, ww * 0.28f))
    // рейка с половниками
    val rx = w * 0.62f
    val ry = h * 0.065f
    drawLine(Color(0xFF8C5A2F), Offset(rx, ry), Offset(w * 0.96f, ry), 5f, StrokeCap.Round)
    for ((i, px) in listOf(0.68f, 0.78f, 0.88f).withIndex()) {
        val x = w * px
        val sway = sin(t * 1.3f + i) * 2f
        drawLine(Color(0xFFB9B9C4), Offset(x, ry), Offset(x + sway, ry + h * 0.07f), 4f, StrokeCap.Round)
        if (i == 0) drawOval(Color(0xFFD7D7E0), Offset(x - w * 0.025f + sway, ry + h * 0.065f), Size(w * 0.05f, h * 0.03f))
        if (i == 1) drawOval(Color(0xFFD9A066), Offset(x - w * 0.02f + sway, ry + h * 0.065f), Size(w * 0.04f, h * 0.045f), style = Stroke(3f))
        if (i == 2) drawRoundRect(Color(0xFFD9A066), Offset(x - w * 0.014f + sway, ry + h * 0.06f), Size(w * 0.028f, h * 0.05f), CornerRadius(8f))
    }
    // мягкая виньетка
    drawRect(Brush.radialGradient(listOf(Color(0x00000000), Color(0x22703C10)), Offset(w / 2, h * 0.45f), max(w, h) * 0.75f))
}

fun DrawScope.kcCounter(w: Float, h: Float, topY: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFE0A66A), Color(0xFFC98A4B)), topY, h), Offset(0f, topY), Size(w, h - topY))
    drawRect(Color(0xFFF0C48A), Offset(0f, topY), Size(w, h * 0.012f))
    drawRect(Color(0x33000000), Offset(0f, topY + h * 0.012f), Size(w, h * 0.006f))
    var x = 0f
    val plank = w / 5f
    while (x < w) {
        drawLine(Color(0x22703C10), Offset(x, topY + h * 0.02f), Offset(x, h), 3f)
        x += plank
    }
}

fun DrawScope.kcTable(w: Float, h: Float, topY: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFD9945A), Color(0xFFB97A45)), topY, h), Offset(0f, topY), Size(w, h - topY))
    drawRect(Color(0xFFF0C48A), Offset(0f, topY), Size(w, h * 0.012f))
    // клетчатая скатерть
    val cloth = Path().apply {
        moveTo(w * 0.02f, topY + h * 0.03f); lineTo(w * 0.98f, topY + h * 0.03f)
        lineTo(w * 1.03f, h); lineTo(-w * 0.03f, h); close()
    }
    clipPath(cloth) {
        drawRect(Color(0xFFFFF7EC), Offset(0f, topY), Size(w, h - topY))
        val cell = w / 9f
        var x = -cell
        var i = 0
        while (x < w + cell) {
            drawRect(Color(0xFFE63946).copy(alpha = 0.22f), Offset(x, topY), Size(cell / 2, h - topY))
            x += cell; i++
        }
        var y = topY
        while (y < h) {
            drawRect(Color(0xFFE63946).copy(alpha = 0.22f), Offset(0f, y), Size(w, cell / 2))
            y += cell
        }
    }
    drawPath(cloth, Color(0x33703C10), style = Stroke(3f))
}

fun DrawScope.kcPlate(c: Offset, r: Float) {
    drawOval(Color(0x2A000000), Offset(c.x - r * 1.02f, c.y + r * 0.1f), Size(r * 2.04f, r * 0.62f))
    drawOval(Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFD9E2EC)), c.y - r * 0.3f, c.y + r * 0.5f), Offset(c.x - r, c.y - r * 0.3f), Size(r * 2f, r * 0.8f))
    drawOval(Color(0xFFB8C6D6), Offset(c.x - r, c.y - r * 0.3f), Size(r * 2f, r * 0.8f), style = Stroke(r * 0.02f))
    drawOval(Brush.verticalGradient(listOf(Color(0xFFE9EFF5), Color(0xFFFFFFFF)), c.y - r * 0.2f, c.y + r * 0.4f), Offset(c.x - r * 0.72f, c.y - r * 0.2f), Size(r * 1.44f, r * 0.58f))
    drawOval(Color(0xFF6FA8DC).copy(alpha = 0.6f), Offset(c.x - r * 0.82f, c.y - r * 0.24f), Size(r * 1.64f, r * 0.66f), style = Stroke(r * 0.03f))
}

fun DrawScope.kcShelf(w: Float, h: Float, top: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFC98A4B), Color(0xFF9A6234)), top, h), Offset(0f, top), Size(w, h - top))
    drawRect(Color(0xFFF0C48A), Offset(0f, top), Size(w, h * 0.008f))
    drawRect(Color(0xFF7A4A24), Offset(0f, top + h * 0.008f), Size(w, h * 0.008f))
}

fun DrawScope.kcBowl(c: Offset, r: Float) {
    // миска-ячейка полки
    drawOval(Color(0x33000000), Offset(c.x - r * 1.05f, c.y + r * 0.15f), Size(r * 2.1f, r * 0.45f))
    val bowl = Path().apply {
        moveTo(c.x - r, c.y - r * 0.1f)
        quadraticTo(c.x - r * 0.95f, c.y + r * 0.55f, c.x - r * 0.45f, c.y + r * 0.55f)
        lineTo(c.x + r * 0.45f, c.y + r * 0.55f)
        quadraticTo(c.x + r * 0.95f, c.y + r * 0.55f, c.x + r, c.y - r * 0.1f)
        close()
    }
    drawPath(bowl, Brush.horizontalGradient(listOf(Color(0xFFD9C3A0), Color(0xFFFFF3DC), Color(0xFFD9C3A0)), c.x - r, c.x + r))
    drawPath(bowl, Color(0xFFB8975F), style = Stroke(r * 0.04f))
    drawOval(Color(0xFFFFF8E8), Offset(c.x - r, c.y - r * 0.28f), Size(r * 2f, r * 0.36f))
    drawOval(Color(0xFFB8975F), Offset(c.x - r, c.y - r * 0.28f), Size(r * 2f, r * 0.36f), style = Stroke(r * 0.04f))
}

/** Половник; tip — конец, которым мешают бульон. */
fun DrawScope.kcLadle(tip: Offset, len: Float, angleDeg: Float) {
    withTransform({ translate(tip.x, tip.y); rotate(angleDeg, Offset.Zero) }) {
        drawLine(Color(0xFF8C5A2F), Offset(0f, 0f), Offset(0f, -len), len * 0.07f, StrokeCap.Round)
        drawLine(Color(0xFFC98A4B), Offset(-len * 0.012f, -len * 0.05f), Offset(-len * 0.012f, -len * 0.95f), len * 0.025f, StrokeCap.Round)
        drawOval(Color(0xFF7A4A24), Offset(-len * 0.1f, -len * 0.08f), Size(len * 0.2f, len * 0.12f))
        drawOval(Color(0xFFD9A066), Offset(-len * 0.1f, -len * 0.1f), Size(len * 0.2f, len * 0.12f))
    }
}

fun DrawScope.kcHand(p: Offset, r: Float, down: Boolean) {
    val k = if (down) 0.9f else 1f
    drawCircle(Color(0x55FFFFFF), r * 1.9f * k, p)
    withTransform({ translate(p.x, p.y); rotate(-18f, Offset.Zero); scale(k, k, Offset.Zero) }) {
        drawOval(Color(0x33000000), Offset(-r * 0.55f, r * 0.15f), Size(r * 1.2f, r * 2.4f))
        drawRoundRect(Color(0xFFFFD2B0), Offset(-r * 0.5f, 0f), Size(r, r * 2.3f), CornerRadius(r * 0.5f))
        drawRoundRect(Color(0xFFE5A982), Offset(-r * 0.5f, 0f), Size(r, r * 2.3f), CornerRadius(r * 0.5f), style = Stroke(r * 0.07f))
        drawRoundRect(Color(0xFFFFEFE0), Offset(-r * 0.28f, r * 0.12f), Size(r * 0.56f, r * 0.5f), CornerRadius(r * 0.25f))
        drawRoundRect(Color(0xFFFFC9A3), Offset(-r * 1.1f, r * 1.7f), Size(r * 2.2f, r * 1.6f), CornerRadius(r * 0.7f))
    }
}
