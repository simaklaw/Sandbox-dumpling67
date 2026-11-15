package com.example.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/** Тестовая заглушка вместо рекламы. Выбирается только в debug-сборке (см. [AdServices]), в релиз не попадает в работу. */
object DebugAdProvider : AdProvider {
    override fun isAvailable(format: AdFormat) = true

    @Composable
    override fun Content(format: AdFormat, interactive: Boolean, modifier: Modifier) {
        Box(modifier.background(Color(0xFFB3E5FC)), contentAlignment = Alignment.Center) {
            Text(
                if (interactive) "Тестовая реклама (debug)\nтапы включены" else "Тестовая реклама (debug)\nтапы пока выключены",
                fontSize = 16.sp, color = Color(0xFF2B2B2B)
            )
        }
    }
}
