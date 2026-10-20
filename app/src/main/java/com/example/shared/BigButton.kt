package com.example.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BigButton(
    modifier: Modifier = Modifier,
    icon: String, // String Emoji
    backgroundColor: Color,
    label: String? = null,
    size: String = "md", // "md" = 72, "lg" = 96, "xl" = 120
    haptics: Haptics = rememberHaptics(),
    onClick: () -> Unit
) {
    val diameter = when (size) {
        "lg" -> 96.dp
        "xl" -> 120.dp
        else -> 72.dp
    }

    val emojiSize = when (size) {
        "lg" -> 40.sp
        "xl" -> 52.sp
        else -> 32.sp
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
    ) {
        // Inner plasticine styled circle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(diameter)
                // Double Shadow Border effect
                .border(BorderStroke(3.dp, ColorInk), ShapeButton)
                .background(backgroundColor, ShapeButton)
                .padding(bottom = 4.dp) // Bottom padding gives depth
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)), ShapeButton)
                .clip(ShapeButton)
                .plasticineClickable(haptics = haptics, onClick = onClick)
        ) {
            Text(
                text = icon,
                fontSize = emojiSize
            )
        }

        if (label != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = PlayTypography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = ColorInk
            )
        }
    }
}
