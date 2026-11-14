package com.example.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToGame: (Int, String) -> Unit,
    onNavigateToFolder: (String, FolderType) -> Unit,
    onOpenSettings: () -> Unit,
    musicOn: Boolean = true,
    onToggleMusic: () -> Unit = {},
    soundManager: SoundManager,
    haptics: Haptics = rememberHaptics()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val ageProfile = LocalAgeProfile.current

    // Preferences for first launch hint
    val prefs = remember { context.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE) }
    var isFirstLaunch by remember { mutableStateOf(prefs.getBoolean("first_launch_step1", true)) }

    // Touch target audit trigger state (5s hold on logo)
    var showTouchAuditDialog by remember { mutableStateOf(false) }
    var isLogoPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isLogoPressed) {
        if (isLogoPressed) {
            delay(5000L)
            if (isLogoPressed) {
                soundManager.play("chime")
                showTouchAuditDialog = true
                isLogoPressed = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFFBF1), ColorBg, ColorBgSoft)))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: App logo/title & settings gear
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large child-friendly app logo / text (5s hold opens touch target audit)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (com.example.BuildConfig.DEBUG) Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isLogoPressed = true
                                tryAwaitRelease()
                                isLogoPressed = false
                            }
                        )
                    } else Modifier
                ) {
                    Text(
                        text = "🧸",
                        fontSize = 32.sp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = "Детская площадка",
                        style = PlayTypography.titleLarge,
                        fontSize = 24.sp,
                        color = ColorInk,
                        fontWeight = FontWeight.Black
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 🎵 Фоновая музыка вкл/выкл
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (musicOn) Color(0xFFFFF8E1) else Color(0xFFE0E0E0))
                            .clickable { onToggleMusic() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (musicOn) "🎵" else "🔇", fontSize = 24.sp)
                    }
                    // 🌐 Язык RU / EN
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE3F2FD))
                            .clickable {
                                com.example.shared.i18n.I18n.setLang(context, if (com.example.shared.i18n.I18n.isEnglish) "ru" else "en")
                                soundManager.preloadSpeech()
                                soundManager.play("pop")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Text(text = if (com.example.shared.i18n.I18n.isEnglish) "EN" else "RU", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ColorInk)
                    }
                }
            }

            // Grid of 6 interactive cards
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                    // ==========================================
                    // 🧸 3-5 YEARS OLD LAYOUT (With Folders)
                    // ==========================================
                    // Row 1: Кормление (одна широкая карточка)
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HomeScreenCard(
                            modifier = Modifier.weight(1f),
                            title = "Кормление",
                            icon = "🍎",
                            cardColor = ColorPink,
                            phaseOffset = 0,
                            soundManager = soundManager,
                            haptics = haptics,
                            onClick = {
                                if (isFirstLaunch) {
                                    isFirstLaunch = false
                                    prefs.edit().putBoolean("first_launch_step1", false).apply()
                                }
                                onNavigateToGame(1, "Кормление")
                            }
                        )
                    }

                    // Row 2: Рисование, Шарики
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HomeScreenCard(
                            modifier = Modifier.weight(1f),
                            title = "Рисование",
                            icon = "🎨",
                            cardColor = ColorGreen,
                            phaseOffset = 800,
                            soundManager = soundManager,
                            haptics = haptics,
                            onClick = { onNavigateToGame(9, "Рисование") }
                        )
                        HomeScreenCard(
                            modifier = Modifier.weight(1f),
                            title = "Шарики",
                            icon = "🎈",
                            cardColor = ColorBlue,
                            phaseOffset = 1200,
                            soundManager = soundManager,
                            haptics = haptics,
                            onClick = { onNavigateToGame(7, "Шарики") }
                        )
                    }

                    // Row 3: Игры, Кухня
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HomeScreenCard(
                            modifier = Modifier.weight(1f),
                            title = "Игры",
                            icon = "🎪",
                            cardColor = ColorOrange,
                            phaseOffset = 1600,
                            soundManager = soundManager,
                            haptics = haptics,
                            onClick = { onNavigateToFolder("Игры", FolderType.GAMES) }
                        )
                        HomeScreenCard(
                            modifier = Modifier.weight(1f),
                            title = "Кухня",
                            icon = "🫖",
                            cardColor = ColorPurple,
                            phaseOffset = 2000,
                            soundManager = soundManager,
                            haptics = haptics,
                            onClick = { onNavigateToFolder("Кухня", FolderType.KITCHEN) }
                        )
                    }
            }

            // Bottom quick launch row for "Смешинки 😜" and "Телефончик 📱"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    onClick = {
                        soundManager.play("tap")
                        haptics.vibratePop()
                        onNavigateToGame(21, "Смешинки")
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    color = ColorYellow,
                    shadowElevation = 6.dp,
                    border = BorderStroke(2.dp, ColorInk),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("smeshinki_home_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("😜", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Смешинки",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ColorInk
                        )
                    }
                }

                Surface(
                    onClick = {
                        soundManager.play("tap")
                        haptics.vibratePop()
                        onNavigateToGame(23, "Телефончик")
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    color = ColorPink,
                    shadowElevation = 6.dp,
                    border = BorderStroke(2.dp, ColorInk),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("baby_phone_home_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("📱", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Телефончик",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ColorInk
                        )
                    }
                }
            }

            // Настройки для родителей: видимая подписанная кнопка внизу (раньше была безымянная шестерёнка в углу)
            Spacer(modifier = Modifier.height(10.dp))
            ParentGearButton(
                showLabel = true,
                onTrigger = onOpenSettings,
                soundManager = soundManager,
                haptics = haptics
            )
        }

        // Animated Guide Hand over "Кормление" for first launch or idle
        if (isFirstLaunch) {
            HintHand(
                from = Offset(60f, 180f),
                mode = "tap",
                delayMs = 2000
            )
        }

        if (showTouchAuditDialog) {
            com.example.ui.components.TouchTargetAuditDialog(
                ageProfile = ageProfile,
                onDismiss = { showTouchAuditDialog = false }
            )
        }
    }
}

/**
 * Кнопка «Для родителей»: открывает родительский замок (задача в `ParentLockDialog`, её показывает `MainActivity`).
 * Сама кнопка ничего не защищает: малыш может её нажать, но задачу не решит.
 */
@Composable
fun ParentGearButton(
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    onTrigger: () -> Unit,
    soundManager: SoundManager,
    haptics: Haptics
) {
    val shape = if (showLabel) androidx.compose.foundation.shape.RoundedCornerShape(24.dp) else CircleShape
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .then(if (showLabel) Modifier.height(48.dp).widthIn(min = 56.dp) else Modifier.size(56.dp))
            .border(BorderStroke(3.dp, ColorInk), shape)
            .background(if (showLabel) ColorYellow else ColorBgSoft, shape)
            .clip(shape)
            .clickable {
                haptics.vibrateShort()
                soundManager.play("tap")
                onTrigger()
            }
            .testTag("parent_settings_gear_btn")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = if (showLabel) 16.dp else 0.dp)) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Родителям",
                tint = ColorInk,
                modifier = Modifier.size(if (showLabel) 26.dp else 32.dp)
            )
            if (showLabel) {
                Text(
                    text = "Для родителей",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = ColorInk,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun HomeScreenCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: String,
    cardColor: Color,
    phaseOffset: Int,
    soundManager: SoundManager,
    haptics: Haptics,
    onClick: () -> Unit
) {
    CandyCard(
        modifier = modifier.fillMaxSize(),
        title = title,
        icon = icon,
        cardColor = cardColor,
        phaseOffset = phaseOffset,
        iconSize = 58.dp,
        titleSize = 20,
        soundManager = soundManager,
        haptics = haptics,
        onClick = onClick
    )
}
