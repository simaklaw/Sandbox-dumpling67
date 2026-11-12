package com.example.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun HomeButton(
    modifier: Modifier = Modifier,
    haptics: Haptics = rememberHaptics(),
    size: Dp = 80.dp,
    onClick: () -> Unit
) {
    var isLocked by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .border(BorderStroke(3.5.dp, ColorInk), CircleShape)
            .background(ColorYellow, CircleShape)
            .clip(CircleShape)
            .plasticineClickable(
                enabled = !isLocked,
                haptics = haptics,
                onClick = {
                    if (!isLocked) {
                        isLocked = true
                        onClick()
                    }
                }
            )
    ) {
        Text(
            text = "🏠",
            fontSize = (size.value * 0.47f).sp
        )
    }

    // Coroutine debounce protection
    if (isLocked) {
        LaunchedEffect(Unit) {
            delay(400)
            isLocked = false
        }
    }
}

/** Высота верхней полоски игры. Домик живёт в ней, а игра рисуется строго под ней, поэтому он ничего не перекрывает. */
val GameTopBarHeight = 56.dp

/**
 * Верхняя полоска любой игры: слева «домик» (выход), по центру название игры.
 * Игровое поле начинается под полоской, значки и надписи игр больше не прячутся под кнопкой.
 */
@Composable
fun GameTopBar(
    title: String,
    haptics: Haptics,
    onHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(GameTopBarHeight)
            .background(ColorBgSoft)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        HomeButton(haptics = haptics, size = 44.dp, onClick = onHome)
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
            color = ColorInk,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 60.dp)
        )
    }
}
