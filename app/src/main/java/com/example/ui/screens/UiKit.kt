package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Набор «конфетного» интерфейса: глянцевые кнопки и окно с красной лентой.
 * Картинки — ui_*.webp (из присланного пользователем GUI-набора).
 */

/** Окно с лентой-заголовком. Размер сохраняет пропорции картинки (560×453). */
@Composable
fun KitWindow(
    width: Dp,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val height = width * 453f / 560f
    Box(modifier.size(width, height)) {
        Image(painterResource(R.drawable.ui_window), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Text(
            title,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = height * 0.09f)
                .width(width * 0.46f),
            fontSize = (width.value * 0.052f).sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = height * 0.31f, bottom = height * 0.12f)
                .width(width * 0.80f),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

/** Длинная кнопка с подписью. res — ui_btn_long_blue / green / red. */
@Composable
fun KitLongButton(res: Int, text: String, width: Dp, fontSize: TextUnit = 16.sp, selected: Boolean = false, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(width, width * 204f / 440f)
            .scale(if (pressed) 0.94f else if (selected) 1.06f else 1f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(res), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        val fs = minOf(fontSize.value, width.value * 0.70f / (text.length.coerceAtLeast(1) * 0.58f)).sp
        Text(
            text, fontSize = fs, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1,
            modifier = Modifier.padding(horizontal = width * 0.10f).padding(bottom = width * 0.012f)
        )
    }
}
