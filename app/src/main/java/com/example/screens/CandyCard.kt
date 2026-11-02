package com.example.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.*
import com.example.shared.i18n.Text

/**
 * «Конфетная» карточка меню: градиент, глянцевый блик, декоративные пузыри,
 * толстая объёмная кромка снизу, иконка в светлом кружке и подпись на плашке.
 * Если задан [iconRes] — вместо эмодзи рисуется картинка.
 */
@Composable
fun CandyCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: String,
    iconRes: Int? = null,
    cardColor: Color,
    phaseOffset: Int,
    iconSize: Dp = 52.dp,
    titleSize: Int = 18,
    soundManager: SoundManager,
    haptics: Haptics,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "candyBreath")
    val scaleAnim by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, delayMillis = phaseOffset, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    val light = lerp(cardColor, Color.White, 0.42f)
    val deep = lerp(cardColor, Color(0xFFB0705A), 0.16f)
    val lipH = 8.dp

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scaleAnim; scaleY = scaleAnim }
            .drawBehind {
                // объёмная кромка (тёмное основание) + мягкая тень под ней
                drawRoundRect(
                    color = Color(0x33000000),
                    topLeft = Offset(0f, 5.dp.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(32.dp.toPx())
                )
                drawRoundRect(color = ColorInk, size = size, cornerRadius = CornerRadius(32.dp.toPx()))
            }
            .plasticineClickable(haptics = haptics) {
                soundManager.play("pop")
                onClick()
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .matchParentSize()
                .padding(bottom = lipH)
                .clip(ShapeCard)
                .background(Brush.verticalGradient(listOf(light, cardColor, deep)), ShapeCard)
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    // декоративные пузыри
                    drawCircle(Color.White.copy(alpha = 0.16f), radius = h * 0.34f, center = Offset(w * 0.98f, h * 0.02f))
                    drawCircle(Color.White.copy(alpha = 0.12f), radius = h * 0.26f, center = Offset(w * 0.02f, h * 1.0f))
                    drawCircle(Color.White.copy(alpha = 0.18f), radius = h * 0.05f, center = Offset(w * 0.86f, h * 0.30f))
                    drawCircle(Color.White.copy(alpha = 0.18f), radius = h * 0.035f, center = Offset(w * 0.12f, h * 0.62f))
                    // глянцевый блик сверху
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.0f)),
                            startY = h * 0.03f, endY = h * 0.22f
                        ),
                        topLeft = Offset(w * 0.10f, h * 0.035f),
                        size = Size(w * 0.80f, h * 0.17f),
                        cornerRadius = CornerRadius(h * 0.09f)
                    )
                }
                .border(BorderStroke(3.dp, ColorInk), ShapeCard)
                .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.55f)), ShapeCard)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                if (iconRes != null) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(iconSize + 10.dp)
                            .clip(RoundedCornerShape(22))
                            .border(BorderStroke(2.5.dp, ColorInk), RoundedCornerShape(22))
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(iconSize + 22.dp)
                            .background(
                                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.35f))),
                                CircleShape
                            )
                            .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)), CircleShape)
                    ) {
                        Text(text = icon, fontSize = (iconSize.value * 0.92f).sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = title,
                    style = PlayTypography.bodyLarge,
                    fontSize = titleSize.sp,
                    fontWeight = FontWeight.Black,
                    color = ColorInk,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = (titleSize + 2).sp,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.38f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
        }
    }
}
