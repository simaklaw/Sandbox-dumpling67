package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.audio.SoundAuditManager
import com.example.audio.SoundAuditReport
import com.example.shared.*
import kotlinx.coroutines.launch

@Composable
fun SoundAuditDialog(
    soundManager: SoundManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<SoundAuditReport?>(null) }
    var isPlayingAll by remember { mutableStateOf(false) }
    var playStatus by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Overview, 1: P1, 2: P2, 3: P3

    LaunchedEffect(Unit) {
        report = SoundAuditManager.runAudit(context)
    }

    Dialog(onDismissRequest = {
        SoundAuditManager.stopSequentialPlay()
        onDismiss()
    }) {
        Surface(
            shape = ShapeCard,
            color = ColorBg,
            border = BorderStroke(4.dp, ColorInk),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔍 Отладочный аудит звуков",
                        style = PlayTypography.titleLarge,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = ColorInk
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val curReport = report
                if (curReport == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ColorPurple)
                    }
                } else {
                    // Summary Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ColorBgSoft,
                        border = BorderStroke(2.dp, ColorInk),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Найдено в res/raw: ${curReport.totalFilesFound} / ${curReport.totalFilesExpected}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (curReport.totalFilesFound > 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                                Text(
                                    text = "${curReport.totalSizeBytes / 1024} KB",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ColorInk
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Отсутствует: P1 (критичных) = ${curReport.missingP1.size}, P2 = ${curReport.missingP2.size}, P3 = ${curReport.missingP3.size}",
                                fontSize = 12.sp,
                                color = ColorInk
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action Controls: Play all button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (isPlayingAll) {
                                    SoundAuditManager.stopSequentialPlay()
                                    isPlayingAll = false
                                    playStatus = "Остановлено"
                                } else {
                                    isPlayingAll = true
                                    SoundAuditManager.playAllSequential(
                                        soundManager = soundManager,
                                        scope = scope,
                                        onProgress = { cur, total, name ->
                                            playStatus = "Играет [$cur/$total]: $name"
                                        },
                                        onComplete = {
                                            isPlayingAll = false
                                            playStatus = "Готово!"
                                        }
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPlayingAll) ColorPink else ColorGreen
                            ),
                            border = BorderStroke(2.dp, ColorInk),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isPlayingAll) "⏹️ Стоп" else "▶️ Проиграть всё (0.8с)",
                                color = ColorInk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = {
                                report = SoundAuditManager.runAudit(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorYellow),
                            border = BorderStroke(2.dp, ColorInk),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🔄 Пересканировать", color = ColorInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    if (playStatus.isNotEmpty()) {
                        Text(
                            text = playStatus,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ColorPurple,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Priority Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("P1 (Критично)", "P2 (Важно)", "P3 (Остальное)").forEachIndexed { idx, label ->
                            val isSelected = selectedTab == idx
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) ColorPurple else ColorBgSoft,
                                border = BorderStroke(1.dp, ColorInk),
                                onClick = { selectedTab = idx },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else ColorInk,
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Missing list
                    val currentList = when (selectedTab) {
                        0 -> curReport.missingP1
                        1 -> curReport.missingP2
                        else -> curReport.missingP3
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.7f),
                        border = BorderStroke(2.dp, ColorInk),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (currentList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "🎉 Все файлы этой категории добавлены в res/raw!",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(currentList) { item ->
                                    Text(
                                        text = "• $item",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = ColorInk
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            SoundAuditManager.stopSequentialPlay()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorBlue),
                        border = BorderStroke(2.dp, ColorInk),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Закрыть", color = ColorInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
