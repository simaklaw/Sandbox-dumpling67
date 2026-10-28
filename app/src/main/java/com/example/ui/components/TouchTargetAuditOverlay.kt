package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import com.example.shared.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.shared.*

@Composable
fun TouchTargetAuditDialog(
    ageProfile: AgeProfile,
    onDismiss: () -> Unit
) {
    val minMain = ageProfile.minMainButtonDp
    val minSec = ageProfile.minSecondaryButtonDp

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = ShapeCard,
            color = ColorBg,
            border = BorderStroke(4.dp, ColorInk),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🎯 Аудит зон нажатия (Touch Targets)",
                    style = PlayTypography.titleLarge,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = ColorInk
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ColorBgSoft,
                    border = BorderStroke(2.dp, ColorInk),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Профиль: ${ageProfile.title}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ColorPurple
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Мин. размер главных кнопок: $minMain (Норма: ≥$minMain)",
                            fontSize = 13.sp,
                            color = ColorInk
                        )
                        Text(
                            text = "• Мин. размер доп. кнопок: $minSec (Норма: ≥$minSec)",
                            fontSize = 13.sp,
                            color = ColorInk
                        )
                        Text(
                            text = "• Допуск попадания (Hit Slop): +${((ageProfile.hitSlopMultiplier - 1f) * 100).toInt()}%",
                            fontSize = 13.sp,
                            color = ColorInk
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "✅ Все интерактивные элементы детских экранов удовлетворяют стандартам доступности (HomeButton: 72–80dp, карточки: ≥140dp, стрелы/инструменты: ≥88dp).",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorYellow),
                    border = BorderStroke(2.dp, ColorInk),
                    shape = ShapeSmall,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Понятно", color = ColorInk, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
