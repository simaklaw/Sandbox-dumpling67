package com.example.screens

import com.example.shared.withFrameNanosEco
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.shared.ColorInk
import com.example.shared.SoundManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private const val INTRO_LETTER_START = 0.7f
private const val INTRO_LETTER_STEP = 0.16f
private const val INTRO_TOTAL = 4.6f
private const val INTRO_FADE = 0.45f

private val INTRO_WORDS = listOf("ДЕТСКАЯ", "ПЛОЩАДКА")
private val INTRO_COLORS = listOf(
    Color(0xFFFF8FB1), Color(0xFFFFC93C), Color(0xFF7BD88F), Color(0xFF7FC8FF), Color(0xFFB69CFF), Color(0xFFFF9F68)
)
private val INTRO_NOTES = listOf("rb_red", "rb_orange", "rb_yellow", "rb_green", "rb_blue", "rb_purple")

/** Время появления буквы с глобальным номером [n] (по обоим словам подряд). */
internal fun introLetterTime(n: Int): Float = INTRO_LETTER_START + n * INTRO_LETTER_STEP

/**
 * Заставка при запуске: буквы «ДЕТСКАЯ ПЛОЩАДКА» выпрыгивают по одной, каждая со своей нотой,
 * пельмешек подпрыгивает и машет, в конце — колокольчик и конфетти. Нажатие пропускает заставку.
 */
@Composable
fun IntroScreen(soundManager: SoundManager, onFinished: () -> Unit) {
    var t by remember { mutableFloatStateOf(0f) }
    var played by remember { mutableFloatStateOf(0f) } // сколько букв уже «прозвучало»
    var finishedCalled by remember { mutableFloatStateOf(0f) }
    val totalLetters = INTRO_WORDS.sumOf { it.length }

    LaunchedEffect(Unit) {
        var last = 0L
        soundManager.play("whoosh_soft", 0.7f)
        while (true) {
            withFrameNanosEco { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                last = now
                t += dt
                // ноты букв
                while (played.toInt() < totalLetters && t >= introLetterTime(played.toInt())) {
                    val n = played.toInt()
                    soundManager.play(INTRO_NOTES[n % INTRO_NOTES.size], 0.75f)
                    played += 1f
                    if (n == totalLetters - 1) {
                        soundManager.play("chime", 0.9f)
                        soundManager.play("fanfare_short", 0.8f)
                    }
                }
                if (t >= INTRO_TOTAL && finishedCalled < 1f) {
                    finishedCalled = 1f
                    onFinished()
                }
            }
        }
    }

    val fade = if (t > INTRO_TOTAL - INTRO_FADE) ((INTRO_TOTAL - t) / INTRO_FADE).coerceIn(0f, 1f) else 1f
    val allIn = introLetterTime(totalLetters - 1) + 0.35f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(fade)
            .background(Brush.verticalGradient(listOf(Color(0xFF8ED3FF), Color(0xFFFFF3C4), Color(0xFFFFE0B2))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (t > 0.4f && finishedCalled < 1f) { finishedCalled = 1f; onFinished() }
            },
        contentAlignment = Alignment.Center
    ) {
        // Облачка, лучи и конфетти
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val d = density
            // мягкие облака
            for (i in 0 until 3) {
                val cx = ((i * 0.4f + 0.05f + t * 0.02f) % 1.4f - 0.2f) * w
                val cy = h * (0.12f + i * 0.09f)
                val c = Color.White.copy(alpha = 0.7f)
                drawCircle(c, 22f * d, Offset(cx - 30f * d, cy + 6f * d))
                drawCircle(c, 30f * d, Offset(cx, cy - 4f * d))
                drawCircle(c, 24f * d, Offset(cx + 30f * d, cy + 4f * d))
            }
            // холмы
            drawCircle(Color(0xFFA5D86E), w * 0.75f, Offset(w * 0.15f, h * 1.08f))
            drawCircle(Color(0xFF8CCB52), w * 0.8f, Offset(w * 0.95f, h * 1.14f))
            // конфетти после последней буквы
            if (t > allIn) {
                val k = t - allIn
                for (i in 0 until 40) {
                    val seed = i * 7919 % 1000 / 1000f
                    val x = (seed + sin(i * 1.7f + k * 2f) * 0.04f) * w
                    val y = (k * (0.18f + seed * 0.3f) + (i % 5) * 0.02f - 0.05f) * h
                    drawCircle(INTRO_COLORS[i % INTRO_COLORS.size], (3f + (i % 4)) * d, Offset(x, y))
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            var n = 0
            INTRO_WORDS.forEachIndexed { wi, word ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (ch in word) {
                        val idx = n++
                        val k = ((t - introLetterTime(idx)) / 0.4f).coerceIn(0f, 1f)
                        val pop = if (k <= 0f) 0f else (1f - (1f - k) * (1f - k)) * (1f + 0.35f * sin(k * PI.toFloat()))
                        val wiggle = if (t > allIn) sin(t * 6f + idx * 0.6f) * 4f else 0f
                        Box(
                            modifier = Modifier
                                .size(width = if (word.length > 7) 38.dp else 42.dp, height = if (word.length > 7) 50.dp else 54.dp)
                                .graphicsLayer { translationY = -abs(wiggle) * 2f * density }
                                .scale(pop)
                                .rotate(if (k < 1f) (1f - k) * (if (idx % 2 == 0) -25f else 25f) else wiggle)
                                .background(INTRO_COLORS[idx % INTRO_COLORS.size], RoundedCornerShape(12.dp))
                                .border(3.dp, ColorInk, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(ch.toString(), fontSize = 28.sp, fontWeight = FontWeight.Black, color = ColorInk)
                        }
                    }
                }
                if (wi == 0) Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(26.dp))
            // Пельмешек: выпрыгивает снизу, подпрыгивает в такт и машет
            val enter = ((t - 0.2f) / 0.6f).coerceIn(0f, 1f)
            val hop = if (t > 0.8f) abs(sin(t * 5.2f)) * 18f else 0f
            Image(
                painter = painterResource(R.drawable.wl_dumpling),
                contentDescription = null,
                modifier = Modifier
                    .size(150.dp)
                    .graphicsLayer {
                        translationY = (1f - enter) * 260.dp.toPx() - hop.dp.toPx()
                        rotationZ = if (t > allIn) sin(t * 7f) * 8f else 0f
                        val sq = 1f + (if (hop < 2f && t > 0.8f) -0.06f else 0.03f)
                        scaleX = 2f - sq
                        scaleY = sq
                    }
            )
            Text(
                "Играем вместе!",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = ColorInk.copy(alpha = ((t - allIn) / 0.4f).coerceIn(0f, 1f)),
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
