package com.example.shared

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.audio.SoundCategory
import com.example.ui.components.SoundAuditDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ParentSettingsDialog(
    soundManager: SoundManager,
    onResetProgress: () -> Unit,
    onNavigateToGame: ((Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    // Sound settings (3 categories + master volume)
    var isSfxEnabled by remember { mutableStateOf(soundManager.isSfxEnabled) }
    var isVoiceEnabled by remember { mutableStateOf(soundManager.isVoiceEnabled) }
    var isAnimalEnabled by remember { mutableStateOf(soundManager.isAnimalEnabled) }
    var masterVolume by remember { mutableFloatStateOf(soundManager.masterVolume) }

    // Parent preferences
    var vibrationEnabled by remember { mutableStateOf(prefs.getBoolean("pref_vibration", true)) }
    var selectedAgeGroup by remember { mutableStateOf(prefs.getString("pref_age_group", "3-5") ?: "3-5") }
    var selectedRestTimer by remember { mutableStateOf(prefs.getString("pref_rest_timer", "off") ?: "off") }
    var showOldSections by remember { mutableStateOf(prefs.getBoolean("pref_old_sections", false)) }

    var showResetConfirm by remember { mutableStateOf(false) }
    var showSoundAuditDialog by remember { mutableStateOf(false) }

    // Gear long-press 6-second timer state
    var gearPressStartTime by remember { mutableLongStateOf(0L) }
    var isGearPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isGearPressed) {
        if (isGearPressed) {
            delay(6000L)
            if (isGearPressed) {
                soundManager.play("chime")
                showSoundAuditDialog = true
                isGearPressed = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = ShapeCard,
            color = ColorBg,
            border = BorderStroke(4.dp, ColorInk),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header with secret 6-second long-press on gear
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isGearPressed = true
                                    gearPressStartTime = System.currentTimeMillis()
                                    tryAwaitRelease()
                                    isGearPressed = false
                                }
                            )
                        }
                        .padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "⚙️ Настройки для родителей",
                        style = PlayTypography.titleLarge,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = ColorInk
                    )
                }

                Text(
                    text = "🔊 Звуковое сопровождение",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorInk.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.Start)
                )

                // 1. 🎵 SFX Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Звуковые эффекты",
                            style = PlayTypography.bodyLarge,
                            fontSize = 16.sp,
                            color = ColorInk,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Клики, пружинки, хлопки, взмахи",
                            fontSize = 12.sp,
                            color = ColorInk.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = isSfxEnabled,
                        onCheckedChange = {
                            isSfxEnabled = it
                            soundManager.setCategoryEnabled(SoundCategory.SFX, it)
                            if (it) soundManager.play("pop")
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ColorPink,
                            checkedTrackColor = ColorBgSoft
                        )
                    )
                }

                // 2. 🗣️ VOICE Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Голос и подсказки",
                            style = PlayTypography.bodyLarge,
                            fontSize = 16.sp,
                            color = ColorInk,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Похвала, ку-ку, озвучка стрел",
                            fontSize = 12.sp,
                            color = ColorInk.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = isVoiceEnabled,
                        onCheckedChange = {
                            isVoiceEnabled = it
                            soundManager.setCategoryEnabled(SoundCategory.VOICE, it)
                            if (it) soundManager.play("praise")
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ColorPink,
                            checkedTrackColor = ColorBgSoft
                        )
                    )
                }

                // 3. 🐮 ANIMAL Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Голоса животных",
                            style = PlayTypography.bodyLarge,
                            fontSize = 16.sp,
                            color = ColorInk,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Реальные звуки собачек, котиков и др.",
                            fontSize = 12.sp,
                            color = ColorInk.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = isAnimalEnabled,
                        onCheckedChange = {
                            isAnimalEnabled = it
                            soundManager.setCategoryEnabled(SoundCategory.ANIMAL, it)
                            if (it) soundManager.play("animal_dog")
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ColorPink,
                            checkedTrackColor = ColorBgSoft
                        )
                    )
                }

                // 🎚️ Master Volume Slider (Safe limit: max 1.0)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Общая громкость",
                            style = PlayTypography.bodyLarge,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorInk
                        )
                        Text(
                            text = "${(masterVolume * 100).roundToInt()}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorPurple
                        )
                    }
                    Slider(
                        value = masterVolume,
                        onValueChange = {
                            masterVolume = it
                            soundManager.setMasterVolume(it)
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = ColorPurple,
                            activeTrackColor = ColorPurple,
                            inactiveTrackColor = ColorBgSoft
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Divider(color = ColorInk.copy(alpha = 0.2f), thickness = 2.dp)

                // 📳 Vibration Control Option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Вибрация (тактильный отклик)",
                        style = PlayTypography.bodyLarge,
                        fontSize = 15.sp,
                        color = ColorInk,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = vibrationEnabled,
                        onCheckedChange = {
                            vibrationEnabled = it
                            prefs.edit().putBoolean("pref_vibration", it).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ColorPink,
                            checkedTrackColor = ColorBgSoft
                        )
                    )
                }

                Divider(color = ColorInk.copy(alpha = 0.2f), thickness = 2.dp)

                // 📦 Legacy Sections Toggle ("Старые разделы")
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Старые разделы",
                                style = PlayTypography.bodyLarge,
                                fontSize = 15.sp,
                                color = ColorInk,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Альбом сюрпризов, Бегун (доступны только отсюда)",
                                fontSize = 11.5.sp,
                                color = ColorInk.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = showOldSections,
                            onCheckedChange = {
                                showOldSections = it
                                prefs.edit().putBoolean("pref_old_sections", it).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ColorPurple,
                                checkedTrackColor = ColorBgSoft
                            )
                        )
                    }

                    if (showOldSections && onNavigateToGame != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onNavigateToGame(11) // BlindBox67
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorGreen),
                                border = BorderStroke(2.dp, ColorInk),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🎁 Сюрпризы", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorInk)
                            }

                            Button(
                                onClick = {
                                    onNavigateToGame(5) // Booba Runner
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorBlue),
                                border = BorderStroke(2.dp, ColorInk),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🏃‍♂️ Бегун", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorInk)
                            }
                        }
                    }
                }

                Divider(color = ColorInk.copy(alpha = 0.2f), thickness = 2.dp)

                // 👶 Age selection: "2 years" / "3-5 years"
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Возраст ребенка:",
                        style = PlayTypography.bodyLarge,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorInk,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AgeGroupButton(
                            text = "👶 2 года",
                            selected = selectedAgeGroup == "2",
                            onClick = {
                                selectedAgeGroup = "2"
                                prefs.edit().putString("pref_age_group", "2").apply()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        AgeGroupButton(
                            text = "🧸 3-5 лет",
                            selected = selectedAgeGroup == "3-5",
                            onClick = {
                                selectedAgeGroup = "3-5"
                                prefs.edit().putString("pref_age_group", "3-5").apply()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ⏱️ Rest Timer: "Off / 10 / 20 / 30 mins"
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Таймер ограничения:",
                        style = PlayTypography.bodyLarge,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorInk,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val timers = listOf("off" to "Выкл", "10" to "10 м", "20" to "20 м", "30" to "30 м")
                        timers.forEach { (key, label) ->
                            AgeGroupButton(
                                text = label,
                                selected = selectedRestTimer == key,
                                onClick = {
                                    selectedRestTimer = key
                                    prefs.edit().putString("pref_rest_timer", key).apply()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Divider(color = ColorInk.copy(alpha = 0.2f), thickness = 2.dp)

                // 🗑️ Reset Progress Trigger Button
                Button(
                    onClick = { showResetConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE)),
                    border = BorderStroke(2.dp, Color(0xFFE57373)),
                    shape = ShapeSmall,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Сбросить прогресс",
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                // Done Button to exit Parent panel
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorYellow),
                    border = BorderStroke(3.dp, ColorInk),
                    shape = ShapeButton,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = "Готово",
                        color = ColorInk,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }

    // Confirmation dialog for Progress Reset
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = {
                Text(
                    text = "Вы уверены?",
                    fontWeight = FontWeight.Black,
                    color = ColorInk
                )
            },
            text = {
                Text(
                    text = "Весь прогресс и открытые зверяты будут удалены навсегда.",
                    color = ColorInk
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetProgress()
                        showResetConfirm = false
                    }
                ) {
                    Text("Да, удалить", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Отмена", color = ColorInk)
                }
            }
        )
    }

    // 🔍 Secret Debug Sound Audit Dialog
    if (showSoundAuditDialog) {
        SoundAuditDialog(
            soundManager = soundManager,
            onDismiss = { showSoundAuditDialog = false }
        )
    }
}

@Composable
fun AgeGroupButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .border(
                border = BorderStroke(
                    width = if (selected) 3.dp else 1.5.dp,
                    color = if (selected) ColorInk else ColorInk.copy(alpha = 0.3f)
                ),
                shape = ShapeSmall
            )
            .background(
                color = if (selected) ColorYellow else Color.White,
                shape = ShapeSmall
            )
            .clip(ShapeSmall)
            .plasticineClickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = PlayTypography.labelMedium,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
            color = if (selected) ColorInk else ColorInk.copy(alpha = 0.6f)
        )
    }
}
