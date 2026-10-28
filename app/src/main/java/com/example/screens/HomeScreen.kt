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
import androidx.compose.material3.Text
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

/** Игры главного экрана для режима «2 года». Первые 6 — первая страница, остальные листаются стрелками/свайпом. */
private val TODDLER_HOME_GAMES = listOf(
    FolderItem("Зверята", "🐮", 0, ColorPurple),
    FolderItem("Кормление", "🍎", 1, ColorPink),
    FolderItem("Шарики", "🎈", 7, ColorBlue),
    FolderItem("Рисование", "🎨", 9, ColorGreen),
    FolderItem("Бизиборд", "🎛️", 10, ColorOrange),
    FolderItem("Кнопочная радость", "🔴", 34, ColorPink),
    FolderItem("Дождик", "🌧️", 24, ColorBlue),
    FolderItem("Кто в коробке?", "🎁", 25, ColorPink),
    FolderItem("Фигурки-окошки", "🔷", 26, ColorGreen),
    FolderItem("Комната Зелика", "👽", 27, ColorGreen),
    FolderItem("Аквариум", "🐠", 30, ColorBlue),
    FolderItem("Лук", "🏹", 4, ColorPink),
    FolderItem("Тир", "🦆", 31, ColorOrange),
    FolderItem("Монстрики", "👾", 32, ColorOrange),
    FolderItem("Забег монстрика", "🏃", 33, ColorGreen)
)

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
            .background(ColorBg)
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
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isLogoPressed = true
                                tryAwaitRelease()
                                isLogoPressed = false
                            }
                        )
                    }
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
                    // ⚙️ Parental Gate Settings Gear with 3-second hold circular animation
                    ParentGearButton(
                        onTrigger = {
                            soundManager.play("chime")
                            onOpenSettings()
                        },
                        soundManager = soundManager,
                        haptics = haptics
                    )
                }
            }

            // Grid of 6 interactive cards
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (ageProfile.isToddler2) {
                    // ==========================================
                    // 👶 2 YEARS OLD LAYOUT (прямые игры без папок, страницы по 6 — PagedGrid)
                    // ==========================================
                    PagedGrid(
                        items = TODDLER_HOME_GAMES,
                        soundManager = soundManager,
                        haptics = haptics
                    ) { game, index ->
                        HomeScreenCard(
                            title = game.title,
                            icon = game.icon,
                            cardColor = game.color,
                            phaseOffset = (index % 6) * 300,
                            soundManager = soundManager,
                            haptics = haptics,
                            onClick = { onNavigateToGame(game.pageIndex, game.title) }
                        )
                    }
                } else {
                    // ==========================================
                    // 🧸 3-5 YEARS OLD LAYOUT (With Folders)
                    // ==========================================
                    // Row 1: Кормление, Мялка
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
                        HomeScreenCard(
                            modifier = Modifier.weight(1f),
                            title = "Мялка",
                            icon = "🥟",
                            cardColor = ColorYellow,
                            phaseOffset = 400,
                            soundManager = soundManager,
                            haptics = haptics,
                            onClick = { onNavigateToGame(2, "Мялка") }
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
                            icon = "🏹",
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
        }

        // Animated Guide Hand over "Кормление" for first launch or idle
        if (isFirstLaunch && !ageProfile.isToddler2) {
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

@Composable
fun ParentGearButton(
    modifier: Modifier = Modifier,
    onTrigger: () -> Unit,
    soundManager: SoundManager,
    haptics: Haptics
) {
    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }

    val progressAnim by animateFloatAsState(
        targetValue = holdProgress,
        animationSpec = tween(100, easing = LinearEasing),
        label = "holdProgress"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(56.dp)
            .border(BorderStroke(3.dp, ColorInk), CircleShape)
            .background(ColorBgSoft, CircleShape)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        holdProgress = 0f
                        haptics.vibrateShort()
                        soundManager.play("tap")

                        holdJob = coroutineScope.launch {
                            val duration = 3000L
                            val steps = 30
                            for (i in 1..steps) {
                                delay(duration / steps)
                                holdProgress = i.toFloat() / steps
                                haptics.vibrate(10)
                            }
                            // Successful parent hold of 3 full seconds!
                            onTrigger()
                            isHolding = false
                            holdProgress = 0f
                        }

                        tryAwaitRelease()
                        isHolding = false
                        holdJob?.cancel()
                        holdProgress = 0f
                    }
                )
            }
            .testTag("parent_settings_gear_btn")
    ) {
        // Glowing progress circle around the gear
        if (isHolding && progressAnim > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = ColorYellow,
                    startAngle = -90f,
                    sweepAngle = progressAnim * 360f,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx())
                )
            }
        }

        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Родителям",
            tint = ColorInk,
            modifier = Modifier.size(32.dp)
        )
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
    // Breathing idle animation (Scale oscillates 1.0 <-> 1.04 every 2.4s, phase shifted)
    val transition = rememberInfiniteTransition(label = "cardBreath")
    val scaleAnim by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = phaseOffset, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scaleAnim
                scaleY = scaleAnim
            }
            .border(BorderStroke(4.dp, ColorInk), ShapeCard)
            .background(cardColor, ShapeCard)
            .padding(bottom = 6.dp) // Depth effect
            .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.5f)), ShapeCard)
            .clip(ShapeCard)
            .plasticineClickable(haptics = haptics) {
                soundManager.play("pop")
                onClick()
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            // Adorable icon
            Text(
                text = icon,
                fontSize = 56.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // 1 Word label
            Text(
                text = title,
                style = PlayTypography.bodyLarge,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = ColorInk
            )
        }
    }
}
