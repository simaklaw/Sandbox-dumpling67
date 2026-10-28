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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun HomeButton(
    modifier: Modifier = Modifier,
    haptics: Haptics = rememberHaptics(),
    onClick: () -> Unit
) {
    var isLocked by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(80.dp)
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
            fontSize = 38.sp
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
