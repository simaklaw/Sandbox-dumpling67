package com.example.ui.screens

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Враги «Зелика»: скелеты с оружием, герои из модульного набора, гигантский скелет-босс и злой дух.
 * Скелеты и дух рисуются кодом (ходят ногами, замахиваются, стоят подошвами на земле), герои собираются из картинок ch_*.
 * Все фигуры нарисованы лицом вправо в «своих» единицах (рост скелета = 100) и отражаются параметром face.
 */

private const val R2D = 57.29578f

// ---------- оружие ----------
// Виды: 0 меч, 1 топор, 2 копьё, 3 булава, 4 лук, 5 молот, 6 коса, 7 дубина, 8 катана, 9 сабля, 10 посох,
// 11 волшебная палочка, 12 игрушечный молот, 20 огромный меч босса.

/** Оружие скелетов по номеру внешности: меч, топор, копьё, булава, лук, молот, коса, сабля. */
internal val SKEL_WEAPONS = intArrayOf(0, 1, 2, 3, 4, 5, 6, 9)

/** Оружие героев: Лучник — лук, Пещерный — дубина, Клоун — молот, Монах — посох, Ниндзя — катана, Пират — сабля, Каска — молот, Богатырь — топор, Волшебник — палочка. */
internal val HERO_WEAPON = intArrayOf(4, 7, 12, 10, 8, 9, 5, 1, 11)
internal val HERO_HP = intArrayOf(20, 30, 20, 20, 10, 20, 30, 40, 20)
internal val HERO_DMG = intArrayOf(2, 3, 2, 2, 2, 2, 3, 3, 2)
internal val HERO_SPEED = floatArrayOf(1f, 0.9f, 1.2f, 1f, 1.7f, 1.1f, 0.9f, 0.8f, 0.9f)
internal val HERO_HEIGHT = floatArrayOf(66f, 70f, 66f, 64f, 60f, 66f, 66f, 74f, 68f)

/** Дальность удара оружием (мировые единицы). */
internal fun weaponReach(w: Int): Float = when (w) {
    2 -> 82f
    10 -> 78f
    6 -> 70f
    1, 5 -> 56f
    0, 8, 9 -> 54f
    else -> 50f
}

/** Сколько тиков длится замах до удара/выстрела. */
internal fun weaponWindup(w: Int): Int = when (w) {
    1, 5 -> 30
    3, 6, 7 -> 26
    12 -> 24
    8 -> 16
    4 -> 34
    11 -> 30
    else -> 22
}

internal fun weaponRanged(w: Int): Boolean = w == 4 || w == 11

/** Поза руки с оружием: 0 — «наготове», 0..1 — замах назад, 1..-1 — удар, потом возврат. t — тик атаки (0 — нет атаки). */
internal fun atkPose(t: Int, windup: Int): Float {
    if (t <= 0) return 0f
    if (t < windup) {
        val k = t.toFloat() / windup
        return k * k * (3f - 2f * k)
    }
    if (t < windup + 6) return 1f - 2f * (t - windup) / 6f
    return min(0f, -1f + (t - windup - 6) / 12f)
}

// ---------- стили скелетов ----------
internal class SkelStyle(
    val weapon: Int,
    val hat: Int,
    val bone: Color,
    val bone2: Color,
    val shade: Color,
    val eye: Color,
    val shield: Boolean = false,
    val blade: Color = FO_STEEL,
    val glow: Color? = null
)

private val FO_STEEL = Color(0xFFCFD8DC)
private val FO_STEEL_D = Color(0xFF546E7A)
private val FO_WOOD = Color(0xFF6D4C41)
private val FO_GOLD = Color(0xFFFBC02D)

private val BIO_BONE = arrayOf(Color(0xFFF1EBDD), Color(0xFFD6E3B5), Color(0xFFE4E1F2), Color(0xFF6F5B52))
private val BIO_BONE2 = arrayOf(Color(0xFFD9D0BC), Color(0xFFB3C48C), Color(0xFFC2BEDA), Color(0xFF54433C))
private val BIO_SHADE = arrayOf(Color(0xFF4B4136), Color(0xFF33401F), Color(0xFF2D2848), Color(0xFF1E1210))
private val BIO_EYE = arrayOf(Color(0xFFFF5252), Color(0xFF76FF03), Color(0xFFB388FF), Color(0xFFFF9100))

/** Рядовой скелет: цвет костей и глаз зависит от локации (0 поля, 1 лес, 2 замок, 3 вулкан). */
internal fun skelStyle(weapon: Int, hat: Int, biome: Int, shield: Boolean): SkelStyle {
    val b = biome.coerceIn(0, 3)
    return SkelStyle(weapon, hat, BIO_BONE[b], BIO_BONE2[b], BIO_SHADE[b], BIO_EYE[b], shield)
}

/** Гигантский скелет-босс по локации: у каждого свой шлем, цвет костей и огромный меч. */
internal val BOSS_STYLES: List<SkelStyle> = listOf(
    SkelStyle(20, 2, BIO_BONE[0], BIO_BONE2[0], BIO_SHADE[0], BIO_EYE[0], false, Color(0xFFA1887F), null),
    SkelStyle(20, 5, BIO_BONE[1], BIO_BONE2[1], BIO_SHADE[1], BIO_EYE[1], false, Color(0xFF9CCC65), Color(0xFF76FF03)),
    SkelStyle(20, 1, BIO_BONE[2], BIO_BONE2[2], BIO_SHADE[2], BIO_EYE[2], false, Color(0xFFB0BEC5), Color(0xFFB388FF)),
    SkelStyle(20, 7, BIO_BONE[3], BIO_BONE2[3], BIO_SHADE[3], BIO_EYE[3], false, Color(0xFFFF7043), Color(0xFFFFAB00))
)

// ---------- примитивы ----------
private fun DrawScope.foLn(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, col: Color) =
    drawLine(col, Offset(x1, y1), Offset(x2, y2), w, StrokeCap.Round)

private fun DrawScope.foCirc(x: Float, y: Float, r: Float, col: Color) = drawCircle(col, r, Offset(x, y))

private fun DrawScope.foOval(cx: Float, cy: Float, rx: Float, ry: Float, fill: Color?, stroke: Color? = null, sw: Float = 1f) {
    if (fill != null) drawOval(fill, Offset(cx - rx, cy - ry), Size(rx * 2f, ry * 2f))
    if (stroke != null) drawOval(stroke, Offset(cx - rx, cy - ry), Size(rx * 2f, ry * 2f), style = Stroke(sw))
}

private fun DrawScope.foPoly(pts: FloatArray, fill: Color?, stroke: Color?, sw: Float) {
    val p = Path()
    p.moveTo(pts[0], pts[1])
    var i = 2
    while (i < pts.size) { p.lineTo(pts[i], pts[i + 1]); i += 2 }
    p.close()
    if (fill != null) drawPath(p, fill)
    if (stroke != null) drawPath(p, stroke, style = Stroke(width = sw, join = StrokeJoin.Round))
}

/** Кость с тёмным контуром. */
private fun DrawScope.foBone(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, shade: Color, col: Color) {
    foLn(x1, y1, x2, y2, w + 2.2f, shade)
    foLn(x1, y1, x2, y2, w, col)
}

private fun DrawScope.foStar(x: Float, y: Float, r: Float, col: Color) {
    val p = Path()
    for (i in 0 until 10) {
        val a = i * PI.toFloat() / 5f - PI.toFloat() / 2f
        val rr = if (i % 2 == 0) r else r * 0.45f
        if (i == 0) p.moveTo(x + cos(a) * rr, y + sin(a) * rr) else p.lineTo(x + cos(a) * rr, y + sin(a) * rr)
    }
    p.close()
    drawPath(p, col)
}

/** Оружие: хват в (0,0), «клинок» направлен вверх (-y). */
private fun DrawScope.foWeapon(kind: Int, blade: Color, glow: Color?, charge: Float) {
    val dark = Color(0xFF37474F)
    when (kind) {
        0 -> {
            foLn(0f, 4f, 0f, -2f, 3f, FO_WOOD); foCirc(0f, 5f, 2f, FO_GOLD); foLn(-4.5f, -2f, 4.5f, -2f, 2.6f, FO_GOLD)
            foPoly(floatArrayOf(-2.2f, -3f, -2.2f, -30f, 0f, -37f, 2.2f, -30f, 2.2f, -3f), blade, FO_STEEL_D, 1f)
            foLn(-0.7f, -5f, -0.7f, -29f, 0.9f, Color(0xCCFFFFFF))
        }
        1 -> {
            foLn(0f, 6f, 0f, -34f, 2.8f, FO_WOOD)
            foPoly(floatArrayOf(1f, -35f, 10f, -40f, 14f, -30f, 10f, -21f, 1f, -25f), blade, FO_STEEL_D, 1f)
            foLn(6f, -37f, 10f, -30f, 1f, Color(0xB3FFFFFF))
        }
        2 -> {
            foLn(0f, 10f, 0f, -46f, 2.4f, FO_WOOD)
            foPoly(floatArrayOf(-3.4f, -46f, 0f, -59f, 3.4f, -46f), blade, FO_STEEL_D, 1f)
            foLn(-2.5f, -44f, 2.5f, -44f, 2f, Color(0xFFE53935))
        }
        3 -> {
            foLn(0f, 6f, 0f, -26f, 3f, FO_WOOD)
            for (i in 0 until 8) {
                val a = i * PI.toFloat() / 4f
                foPoly(
                    floatArrayOf(
                        cos(a - 0.3f) * 6f, -31f + sin(a - 0.3f) * 6f, cos(a) * 11f, -31f + sin(a) * 11f,
                        cos(a + 0.3f) * 6f, -31f + sin(a + 0.3f) * 6f
                    ), Color(0xFF78909C), FO_STEEL_D, 0.8f
                )
            }
            foCirc(0f, -31f, 6.5f, Color(0xFF90A4AE)); foCirc(-2f, -33f, 2f, Color(0x99FFFFFF))
        }
        4 -> {
            val p = Path().apply { moveTo(0f, -21f); quadraticTo(10f, 0f, 0f, 21f) }
            drawPath(p, FO_WOOD, style = Stroke(width = 3f, cap = StrokeCap.Round))
            foLn(0f, -21f, 0f, 21f, 0.8f, Color(0xFFEEEEEE))
        }
        5 -> {
            foLn(0f, 6f, 0f, -28f, 3f, FO_WOOD)
            drawRect(Color(0xFF90A4AE), Offset(-9f, -38f), Size(18f, 13f))
            drawRect(FO_STEEL_D, Offset(-9f, -38f), Size(18f, 13f), style = Stroke(1f))
            foLn(-9f, -31.5f, 9f, -31.5f, 1f, FO_STEEL_D)
        }
        6 -> {
            foLn(0f, 10f, 0f, -44f, 2.6f, Color(0xFF5D4037))
            val p = Path().apply {
                moveTo(0f, -45f); quadraticTo(14f, -55f, 24f, -36f); quadraticTo(12f, -46f, 0f, -38f); close()
            }
            drawPath(p, blade); drawPath(p, FO_STEEL_D, style = Stroke(1f))
        }
        7 -> {
            foPoly(floatArrayOf(-2f, 6f, 2f, 6f, 6f, -24f, 3f, -30f, -3f, -30f, -6f, -24f), Color(0xFF8D6E63), Color(0xFF4E342E), 1f)
            foCirc(3.5f, -20f, 1f, Color(0xFFECEFF1)); foCirc(-3.5f, -14f, 1f, Color(0xFFECEFF1)); foCirc(3f, -26f, 1f, Color(0xFFECEFF1))
        }
        8 -> {
            foLn(0f, 6f, 0f, -3f, 3f, Color(0xFF263238)); foCirc(0f, -3.5f, 3.2f, FO_GOLD)
            foPoly(floatArrayOf(-1.4f, -5f, -1.4f, -36f, -0.6f, -42f, 2.4f, -45f, 1.6f, -36f, 1.6f, -5f), Color(0xFFECEFF1), FO_STEEL_D, 0.8f)
        }
        9 -> {
            foLn(0f, 4f, 0f, -2f, 3f, FO_WOOD)
            drawArc(FO_GOLD, 180f, 180f, true, Offset(-5f, -7f), Size(10f, 10f))
            val p = Path().apply {
                moveTo(-1.8f, -3f); quadraticTo(-4f, -20f, 6f, -34f); lineTo(7.8f, -31.5f); quadraticTo(2.4f, -18f, 2f, -3f); close()
            }
            drawPath(p, blade); drawPath(p, FO_STEEL_D, style = Stroke(1f))
        }
        10 -> {
            foLn(0f, 12f, 0f, -52f, 3f, Color(0xFF8D6E63))
            foLn(-3f, -49f, 3f, -49f, 2f, FO_GOLD); foLn(-3f, -41f, 3f, -41f, 2f, FO_GOLD); foCirc(0f, -54f, 3.2f, FO_GOLD)
        }
        11 -> {
            foLn(0f, 5f, 0f, -18f, 2.2f, Color(0xFF6A1B9A))
            foCirc(0f, -24f, 7f + charge * 6f, Color(0xFFFFD600).copy(alpha = 0.25f + charge * 0.35f))
            foStar(0f, -24f, 6f, Color(0xFFFFEB3B))
        }
        12 -> {
            foLn(0f, 6f, 0f, -20f, 2.6f, Color(0xFFFFB300))
            drawRect(Color(0xFFE53935), Offset(-9f, -33f), Size(18f, 14f))
            drawRect(Color(0xFF7F0000), Offset(-9f, -33f), Size(18f, 14f), style = Stroke(1f))
            foLn(-4f, -33f, -4f, -19f, 2f, Color.White); foLn(3f, -33f, 3f, -19f, 2f, Color.White)
            foLn(-9f, -26f, 9f, -26f, 1.2f, FO_GOLD)
        }
        20 -> { // огромный меч босса
            foLn(0f, 14f, 0f, 0f, 5f, Color(0xFF4E342E)); foCirc(0f, 16f, 3.5f, FO_GOLD)
            foPoly(floatArrayOf(-15f, 0f, 15f, 0f, 13f, -4f, -13f, -4f), Color(0xFF8D6E63), Color(0xFF3E2723), 1.4f)
            foCirc(-15f, -2f, 2.6f, FO_GOLD); foCirc(15f, -2f, 2.6f, FO_GOLD)
            val bp = floatArrayOf(-6.5f, -4f, -6.5f, -74f, 0f, -88f, 6.5f, -74f, 6.5f, -4f)
            if (glow != null) {
                foPoly(bp, null, glow.copy(alpha = 0.22f), 12f)
                foPoly(bp, null, glow.copy(alpha = 0.42f), 6f)
            }
            foPoly(bp, blade, dark, 1.6f)
            foLn(0f, -8f, 0f, -76f, 1.8f, Color(0x47000000)); foLn(-4f, -10f, -4f, -72f, 1.1f, Color(0x8CFFFFFF))
            for (i in 0 until 4) foPoly(floatArrayOf(6.5f, -20f - i * 14f, 9.5f, -24f - i * 14f, 6.5f, -28f - i * 14f), blade, dark, 1f)
            if (glow != null) for (i in 0 until 5) foCirc(0f, -16f - i * 13f, 1.6f, glow)
        }
    }
}

// ---------- скелет ----------
private val POSE_CARRY = floatArrayOf(0.35f, 1.25f, 0.45f)
private val POSE_WIND = floatArrayOf(-2.4f, -2.9f, -1.15f)
private val POSE_STRIKE = floatArrayOf(1.15f, 1.45f, 2.2f)

/** i-й угол позы руки (0 плечо, 1 предплечье, 2 оружие) для значения замаха arm из atkPose. */
private fun poseVal(a: Float, i: Int): Float {
    val t = if (a >= 0f) POSE_WIND else POSE_STRIKE
    return POSE_CARRY[i] + (t[i] - POSE_CARRY[i]) * abs(a).coerceAtMost(1f)
}

/** Нога: [колено x, колено y, стопа x, стопа y] относительно бедра. */
private fun legPose(ph: Float, stride: Float, tuck: Float): FloatArray {
    val a = sin(ph) * 0.62f * stride + tuck * 0.7f
    val bend = (0.12f + 0.95f * max(0f, cos(ph))) * stride + tuck * 1.1f
    val b = a - bend
    val kx = sin(a) * 22f
    val ky = cos(a) * 22f
    return floatArrayOf(kx, ky, kx + sin(b) * 21f, ky + cos(b) * 21f)
}

private fun DrawScope.foLeg(l: FloatArray, hx: Float, hipY: Float, shade: Color, col: Color) {
    val kx = hx + l[0]; val ky = hipY + l[1]; val fx = hx + l[2]; val fy = hipY + l[3]
    foBone(hx, hipY, kx, ky, 4f, shade, col)
    foBone(kx, ky, fx, fy, 3.6f, shade, col)
    foCirc(kx, ky, 2.4f, col)
    foBone(fx - 1f, fy, fx + 6f, fy + 0.2f, 3.4f, shade, col)
}

private fun DrawScope.foHat(hat: Int) {
    when (hat) {
        1, 2 -> { // шлем (2 — с рогами)
            if (hat == 2) for (s in floatArrayOf(-1f, 1f)) {
                val p = Path().apply {
                    moveTo(3f + s * 9f, -50f); quadraticTo(3f + s * 18f, -52f, 3f + s * 15f, -64f)
                    quadraticTo(3f + s * 11f, -56f, 3f + s * 6f, -53f); close()
                }
                drawPath(p, Color(0xFFFFF8E1)); drawPath(p, FO_WOOD, style = Stroke(1f))
            }
            drawArc(Color(0xFF90A4AE), 180f, 180f, true, Offset(3f - 10.2f, -46f - 10.2f), Size(20.4f, 20.4f))
            drawArc(Color(0xFF37474F), 180f, 180f, false, Offset(3f - 10.2f, -46f - 10.2f), Size(20.4f, 20.4f), style = Stroke(1.3f))
            foLn(-7.2f, -46f, 13.2f, -46f, 2f, Color(0xFF546E7A))
            foLn(9f, -46f, 9f, -40f, 2f, Color(0xFF78909C)); foCirc(3f, -55f, 1.2f, Color(0xFFECEFF1))
        }
        3 -> { // пиратская треуголка
            foPoly(floatArrayOf(-9f, -50f, 3f, -62f, 15f, -50f, 10f, -48f, 3f, -52f, -4f, -48f), Color(0xFF212121), FO_GOLD, 1f)
            foCirc(3f, -54f, 1.7f, Color(0xFFF5F5F5))
        }
        4 -> { // капюшон
            val p = Path().apply {
                moveTo(-8f, -36f); quadraticTo(-12f, -58f, 3f, -57f); quadraticTo(14f, -55f, 12f, -47f)
                lineTo(8f, -50f); quadraticTo(2f, -54f, -2f, -46f); lineTo(-4f, -34f); close()
            }
            drawPath(p, Color(0xFF4A148C)); drawPath(p, Color(0xFF1A0033), style = Stroke(1.2f))
        }
        5 -> { // корона
            foPoly(floatArrayOf(-5f, -51f, -6f, -62f, -1f, -57f, 3f, -65f, 7f, -57f, 12f, -62f, 11f, -51f), FO_GOLD, Color(0xFFB8860B), 1.1f)
            foCirc(3f, -55f, 1.4f, Color(0xFFE53935))
        }
        6 -> { // красная бандана
            foLn(-6f, -49f, 12f, -49f, 3.2f, Color(0xFFD32F2F))
            foPoly(floatArrayOf(-6f, -49f, -14f, -44f, -9f, -42f), Color(0xFFD32F2F), Color(0xFF7F0000), 0.8f)
            foPoly(floatArrayOf(-6f, -49f, -15f, -50f, -11f, -46f), Color(0xFFD32F2F), Color(0xFF7F0000), 0.8f)
        }
        7 -> { // огненная корона
            for (i in 0 until 5) {
                val x = -3f + i * 4.5f
                foPoly(floatArrayOf(x - 2.2f, -51f, x, -59f - (i % 2) * 5f, x + 2.2f, -51f), if (i % 2 == 1) Color(0xFFFFEB3B) else Color(0xFFFF6D00), null, 0f)
            }
        }
    }
}

/**
 * Скелет (рядовой или гигантский босс). Ноги шагают по фазе walk, подошвы всегда касаются линии feetY.
 * arm — замах оружия (см. atkPose), lean — наклон корпуса (рад), tuck — подогнутые ноги в прыжке (0..1),
 * jaw — открытый рот, bowPull — натяжение тетивы (лук), charge — свечение палочки.
 */
internal fun DrawScope.drawBoneFighter(
    cx: Float, feetY: Float, height: Float, face: Float, st: SkelStyle,
    walk: Float, stride: Float, arm: Float,
    lean: Float = 0f, tuck: Float = 0f, flash: Boolean = false,
    jaw: Float = 0f, bowPull: Float = 0f, charge: Float = 0f
) {
    val u = height / 100f
    val bone0 = if (flash) Color.White else st.bone
    val bone2 = if (flash) Color(0xFFF5F5F5) else st.bone2
    val shade = if (flash) Color(0xFF9E9E9E) else st.shade
    val tk = tuck.coerceIn(0f, 1f)
    withTransform({ translate(cx, feetY); scale(face * u, u, Offset.Zero) }) {
        val l1 = legPose(walk, stride, tk)
        val l2 = legPose(walk + PI.toFloat(), stride, tk)
        val grounded = -max(l1[3], l2[3]) - 1.8f
        val hipY = grounded + (-42f - grounded) * tk
        foLeg(l2, -2f, hipY, shade, bone2)
        val bob = abs(sin(walk)) * 1.4f * stride
        withTransform({
            translate(0f, hipY - bob)
            rotate((lean + 0.045f * sin(walk * 2f) * stride) * R2D, Offset.Zero)
        }) {
            val shx = 1.5f
            val shy = -31f
            // задняя рука (со щитом или машет в такт шагу)
            val r0 = if (st.shield) 0.95f else 0.1f - sin(walk) * 0.6f * stride
            val r1 = if (st.shield) 1.3f else 0.55f - sin(walk) * 0.5f * stride
            val rex = shx + sin(r0) * 12.5f
            val rey = shy + cos(r0) * 12.5f
            val rhx = rex + sin(r1) * 11.5f
            val rhy = rey + cos(r1) * 11.5f
            foBone(shx - 1f, shy, rex, rey, 3.2f, shade, bone2)
            foBone(rex, rey, rhx, rhy, 3f, shade, bone2)
            foCirc(rhx, rhy, 2.2f, bone2)
            if (st.shield) {
                withTransform({ translate(rhx + 3f, rhy - 1f) }) {
                    foCirc(0f, 0f, 10.5f, Color(0xFF5D4037)); foCirc(0f, 0f, 8.2f, Color(0xFF8D6E63)); foCirc(0f, 0f, 2.8f, Color(0xFFB0BEC5))
                    drawCircle(Color(0xFF3E2723), 10.5f, Offset.Zero, style = Stroke(1.2f))
                }
            }
            // позвоночник и таз
            val spine = Path().apply { moveTo(0f, 0f); quadraticTo(-2.5f, -15f, 1.5f, -33f) }
            drawPath(spine, shade, style = Stroke(width = 5.4f, cap = StrokeCap.Round))
            drawPath(spine, bone0, style = Stroke(width = 3.2f, cap = StrokeCap.Round))
            foOval(0f, -2f, 7.5f, 4f, bone0, shade, 1.2f)
            // рёбра
            foOval(1.5f, -21f, 8.2f, 11.5f, Color(0xFF281E19).copy(alpha = 0.55f))
            for (i in 0 until 4) {
                val y = -28f + i * 5f
                val rx = 8f - i * 0.55f
                foLn(1.5f - rx, y, 1.5f + rx, y + 1.6f, 2.9f, shade)
                foLn(1.5f - rx, y, 1.5f + rx, y + 1.6f, 2.5f, bone0)
            }
            foLn(2f, -31f, 2f, -14f, 2.6f, bone0)
            // череп
            withTransform({ translate(1.5f, -33f); rotate(-lean * 0.6f * R2D, Offset.Zero) }) {
                drawRoundRect(bone0, Offset(-2f, -3f + jaw * 3f), Size(13f, 5.5f), CornerRadius(2f))
                drawRoundRect(shade, Offset(-2f, -3f + jaw * 3f), Size(13f, 5.5f), CornerRadius(2f), style = Stroke(1.2f))
                for (i in 0 until 4) foLn(0.5f + i * 3f, -2.8f + jaw * 3f, 0.5f + i * 3f, -0.5f + jaw * 3f, 0.9f, shade)
                withTransform({ translate(0f, -2.5f) }) {
                    foOval(2.2f, -8f, 10.2f, 9.6f, bone0, shade, 1.3f)
                    val nose = Path().apply { moveTo(6f, -1.5f); lineTo(11.5f, -3.5f); lineTo(11f, -0.5f); close() }
                    drawPath(nose, bone0); drawPath(nose, shade, style = Stroke(1.1f))
                    for (ex in floatArrayOf(-0.6f, 6.2f)) {
                        foOval(ex + 1.5f, -8.5f, 2.9f, 3.5f, Color(0xFF1A1411))
                        foCirc(ex + 2.4f, -8.5f, 1.15f, if (flash) Color.White else st.eye)
                    }
                    foCirc(9.8f, -5.1f, 0.8f, Color(0xFF1A1411))
                    foLn(-1.8f, -12.8f, 3.2f, -12f, 1.2f, shade); foLn(5.2f, -12.1f, 9.8f, -13f, 1.2f, shade)
                }
            }
            withTransform({ translate(0.7f, 2.5f) }) { foHat(st.hat) }
            // передняя рука и оружие
            val bow = st.weapon == 4
            val ua = if (bow) 1.5f else poseVal(arm, 0)
            val fa = if (bow) 1.58f else poseVal(arm, 1)
            val wa = if (bow) 0f else poseVal(arm, 2)
            val ex = shx + sin(ua) * 13f
            val ey = shy + cos(ua) * 13f
            val hx = ex + sin(fa) * 12f
            val hy = ey + cos(fa) * 12f
            if (bow) {
                withTransform({ translate(hx, hy) }) { foWeapon(4, st.blade, null, 0f) }
                val bx = hx - 2f - bowPull * 15f
                foLn(hx, hy - 20.5f, bx, hy, 0.9f, Color(0xFFEEEEEE)); foLn(hx, hy + 20.5f, bx, hy, 0.9f, Color(0xFFEEEEEE))
                val mx = (shx + bx) / 2f - 1f
                foBone(shx - 1f, shy + 1f, mx, shy + 6f, 3f, shade, bone2)
                foBone(mx, shy + 6f, bx, hy, 3f, shade, bone2)
                foCirc(bx, hy, 2.2f, bone2)
                if (bowPull > 0.05f) {
                    foLn(bx, hy, hx + 8f, hy, 1.4f, Color(0xFF8D6E63))
                    foPoly(floatArrayOf(hx + 8f, hy - 2f, hx + 14f, hy, hx + 8f, hy + 2f), FO_STEEL, FO_STEEL_D, 0.6f)
                }
            }
            foBone(shx, shy, ex, ey, 3.4f, shade, bone0)
            foBone(ex, ey, hx, hy, 3.2f, shade, bone0)
            foCirc(hx, hy, 2.5f, bone0)
            if (!bow) withTransform({ translate(hx, hy); rotate(wa * R2D, Offset.Zero) }) { foWeapon(st.weapon, st.blade, st.glow, charge) }
        }
        foLeg(l1, 2f, hipY, shade, bone0)
    }
}

// ---------- герои из набора ----------
private val GLOVE = Color(0xFFFFF3E0)
private val GLOVE_D = Color(0xFF5D4037)

/**
 * Герой-враг: тело, голова и ножки из картинок, руки и оружие дорисованы кодом.
 * height — рост от макушки до подошв; tint — цветовой фильтр (вспышка попадания, золотой герой).
 */
internal fun DrawScope.drawHeroFighter(
    set: HeroSet, hero: Int, cx: Float, feetY: Float, height: Float, face: Float,
    walk: Float, stride: Float, arm: Float, tint: ColorFilter?,
    bowPull: Float = 0f, charge: Float = 0f
) {
    val h = hero.coerceIn(0, HERO_COUNT - 1)
    val o = HERO_OFF[h]
    val sc = height / (set.topPx[h] + set.botPx[h])
    val u = height / 100f
    val bob = abs(sin(walk)) * 2.2f * sc * stride
    val by = feetY - set.botPx[h] * sc - bob
    val body = set.bodies[h]
    val bw = body.width * sc
    val bh = body.height * sc
    val wk = HERO_WEAPON[h]
    withTransform({ translate(cx, 0f); scale(face, 1f, Offset.Zero) }) {
        // ножки стоят на земле, шагая — поднимаются
        val lf = set.lfs[h]
        val rf = set.rfs[h]
        val pr = walk + PI.toFloat()
        drImg(rf, o[4] * sc + sin(pr) * 8f * sc * stride, feetY - rf.height * sc / 2f - max(0f, cos(pr)) * 7f * sc * stride, sc, 1f, tint)
        drImg(lf, o[2] * sc + sin(walk) * 8f * sc * stride, feetY - lf.height * sc / 2f - max(0f, cos(walk)) * 7f * sc * stride, sc, 1f, tint)
        val shx = bw * 0.38f
        val shy = by - bh * 0.1f
        // задняя рука
        val ra = -sin(walk) * 0.7f * stride + 0.15f
        val rhx = -shx + sin(ra) * 19.8f * u
        val rhy = shy + cos(ra) * 19.8f * u
        foLn(-shx, shy, rhx, rhy, 5.4f * u, GLOVE_D); foLn(-shx, shy, rhx, rhy, 3.4f * u, GLOVE); foCirc(rhx, rhy, 3.3f * u, GLOVE)
        drImg(body, 0f, by, sc, 1f, tint)
        drImg(set.heads[h], o[0] * sc, by + o[1] * sc + sin(walk * 0.5f) * 1.5f * sc * stride, sc, 1f, tint)
        // передняя рука и оружие
        val bow = wk == 4
        val ua = if (bow) 1.5f else poseVal(arm, 0)
        val fa = if (bow) 1.58f else poseVal(arm, 1)
        val wa = if (bow) 0f else poseVal(arm, 2)
        val ex = shx + sin(ua) * 13f * u
        val ey = shy + cos(ua) * 13f * u
        val hx = ex + sin(fa) * 12f * u
        val hy = ey + cos(fa) * 12f * u
        if (bow) {
            withTransform({ translate(hx, hy); scale(u, u, Offset.Zero) }) { foWeapon(4, FO_STEEL, null, 0f) }
            val bx = hx - (2f + bowPull * 15f) * u
            foLn(hx, hy - 20.5f * u, bx, hy, 0.9f, Color(0xFFEEEEEE)); foLn(hx, hy + 20.5f * u, bx, hy, 0.9f, Color(0xFFEEEEEE))
            foCirc(bx, hy, 3f * u, GLOVE)
            if (bowPull > 0.05f) {
                foLn(bx, hy, hx + 8f * u, hy, 1.4f, Color(0xFF8D6E63))
                foPoly(floatArrayOf(hx + 8f * u, hy - 2f * u, hx + 14f * u, hy, hx + 8f * u, hy + 2f * u), FO_STEEL, FO_STEEL_D, 0.6f)
            }
        }
        foLn(shx, shy, ex, ey, 5.4f * u, GLOVE_D); foLn(ex, ey, hx, hy, 5.4f * u, GLOVE_D)
        foLn(shx, shy, ex, ey, 3.4f * u, GLOVE); foLn(ex, ey, hx, hy, 3.4f * u, GLOVE)
        foCirc(hx, hy, 3.8f * u, GLOVE_D); foCirc(hx, hy, 2.9f * u, GLOVE)
        if (!bow) withTransform({ translate(hx, hy); rotate(wa * R2D, Offset.Zero); scale(u, u, Offset.Zero) }) { foWeapon(wk, FO_STEEL, null, charge) }
    }
}

// ---------- злой дух ----------
/**
 * Злой дух: парящий призрак в капюшоне с рогами. (cx, cy) — центр, w/h — размеры; charge 0..1 — копит воздушный удар.
 */
internal fun DrawScope.drawSpirit(cx: Float, cy: Float, w: Float, h: Float, t: Int, flash: Boolean, charge: Float) {
    withTransform({ translate(cx, cy) }) {
        val hw = w * 0.5f
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFBA68FF).copy(alpha = 0.42f + charge * 0.3f), Color(0x00BA68FF)), center = Offset.Zero, radius = w * 1.15f),
            w * 1.15f, Offset.Zero
        )
        // вихри воздуха вокруг
        for (i in 0 until 3) {
            val a0 = (t * (0.07f + i * 0.03f) + i * 2.1f) * R2D
            val r = w * (0.78f + i * 0.12f)
            drawArc(Color(0xFFC8F0FF).copy(alpha = 0.55f - i * 0.12f), a0, 86f, false, Offset(-r, -r), Size(r * 2f, r * 2f), style = Stroke(3f - i * 0.6f, cap = StrokeCap.Round))
        }
        // рваный подол
        val body = Path()
        body.moveTo(-hw, h * 0.05f)
        body.cubicTo(-hw * 1.05f, -h * 0.52f, hw * 1.05f, -h * 0.52f, hw, h * 0.05f)
        val n = 6
        for (i in 0..n) {
            val x = hw - i.toFloat() / n * 2f * hw
            val y = h * 0.42f + sin(i * 1.9f + t * 0.18f) * h * 0.07f + (if (i % 2 == 1) h * 0.1f else 0f)
            body.lineTo(x, y)
        }
        body.close()
        val top = if (flash) Color.White else Color(0xFFF3E5F5)
        val mid = if (flash) Color.White else Color(0xFFB39DDB)
        val bot = if (flash) Color.White else Color(0x8C673AB7)
        drawPath(body, Brush.verticalGradient(listOf(top, mid, bot), startY = -h * 0.5f, endY = h * 0.5f), alpha = 0.93f)
        drawPath(body, Color(0xFF311B92), style = Stroke(2.4f, join = StrokeJoin.Round))
        // капюшон и рога
        val hood = Path().apply {
            moveTo(-hw * 0.95f, -h * 0.12f); quadraticTo(-hw * 0.9f, -h * 0.58f, 0f, -h * 0.56f)
            quadraticTo(hw * 0.9f, -h * 0.58f, hw * 0.95f, -h * 0.12f); quadraticTo(hw * 0.5f, -h * 0.3f, 0f, -h * 0.28f)
            quadraticTo(-hw * 0.5f, -h * 0.3f, -hw * 0.95f, -h * 0.12f); close()
        }
        drawPath(hood, Color(0xFF311B92))
        for (s in floatArrayOf(-1f, 1f)) {
            val horn = Path().apply {
                moveTo(s * hw * 0.5f, -h * 0.5f); quadraticTo(s * hw * 1.1f, -h * 0.58f, s * hw * 1.05f, -h * 0.82f)
                quadraticTo(s * hw * 0.75f, -h * 0.62f, s * hw * 0.28f, -h * 0.5f); close()
            }
            drawPath(horn, Color(0xFFFFF8E1)); drawPath(horn, Color(0xFF4A148C), style = Stroke(1.6f))
        }
        // лицо
        foOval(0f, -h * 0.13f, hw * 0.62f, h * 0.17f, Color(0xFF12001F))
        for (s in floatArrayOf(-1f, 1f)) {
            withTransform({ translate(s * hw * 0.28f, -h * 0.15f); rotate(s * -0.25f * R2D, Offset.Zero) }) {
                foOval(0f, 0f, hw * 0.17f, h * 0.045f, Color(0xFFFFAB00))
                foOval(s * 2f, 0f, hw * 0.07f, h * 0.035f, Color(0xFFFF1744))
            }
        }
        val mouth = Path().apply {
            moveTo(-hw * 0.35f, 0f)
            for (i in 0..6) lineTo(-hw * 0.35f + i * hw * 0.117f, if (i % 2 == 1) h * 0.07f else 0f)
            lineTo(hw * 0.35f, h * 0.1f); lineTo(-hw * 0.35f, h * 0.1f); close()
        }
        drawPath(mouth, Color(0xFF12001F))
        for (i in 0 until 6 step 2) {
            val tooth = Path().apply {
                moveTo(-hw * 0.35f + i * hw * 0.117f, 0f); lineTo(-hw * 0.35f + (i + 1) * hw * 0.117f, h * 0.07f)
                lineTo(-hw * 0.35f + (i + 2) * hw * 0.117f, 0f); close()
            }
            drawPath(tooth, Color.White)
        }
        // руки-вихри
        for (s in floatArrayOf(-1f, 1f)) {
            val a = Path().apply {
                moveTo(s * hw * 0.8f, h * 0.08f)
                quadraticTo(s * hw * 1.4f, h * (0f - charge * 0.3f), s * hw * 1.55f, h * (-0.2f - charge * 0.3f))
            }
            drawPath(a, Color(0xFFE6F5FF).copy(alpha = 0.9f), style = Stroke(5f, cap = StrokeCap.Round))
        }
    }
}

// ---------- снаряды врагов ----------
/** kind: 0 стрела, 1 звезда волшебника, 2 порыв воздуха злого духа. ang — направление полёта (рад). */
internal fun DrawScope.drawFoeShot(kind: Int, x: Float, y: Float, ang: Float, t: Int, big: Boolean = false) {
    when (kind) {
        0 -> withTransform({ translate(x, y); rotate(ang * R2D, Offset.Zero) }) {
            foLn(-14f, 0f, 8f, 0f, 2f, Color(0xFF8D6E63))
            foPoly(floatArrayOf(8f, -3f, 15f, 0f, 8f, 3f), FO_STEEL, FO_STEEL_D, 0.8f)
            foLn(-14f, 0f, -18f, -3f, 1.6f, Color(0xFFE53935)); foLn(-14f, 0f, -18f, 3f, 1.6f, Color(0xFFE53935))
        }
        1 -> {
            drawCircle(Color(0xFFCE93D8).copy(alpha = 0.35f), 15f, Offset(x, y))
            withTransform({ translate(x, y); rotate(t * 9f, Offset.Zero) }) { foStar(0f, 0f, 10f, Color(0xFFE040FB)); foStar(0f, 0f, 5f, Color(0xFFFFF59D)) }
        }
        else -> {
            val r = if (big) 26f else 17f
            drawCircle(Brush.radialGradient(listOf(Color(0xCCFFFFFF), Color(0x0081D4FA)), center = Offset(x, y), radius = r * 1.5f), r * 1.5f, Offset(x, y))
            for (i in 0 until 3) {
                val rr = r * (0.45f + i * 0.28f)
                drawArc(Color.White.copy(alpha = 0.95f - i * 0.2f), (t * 0.25f + i * 2.1f) * R2D, 172f, false, Offset(x - rr, y - rr), Size(rr * 2f, rr * 2f), style = Stroke(3f - i * 0.6f, cap = StrokeCap.Round))
            }
        }
    }
}
