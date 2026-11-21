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
    haptics: Haptics = rememberHaptics(),
    canPlay: (Int) -> Boolean = { true },
    onOpenMine: () -> Unit = {}
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
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        PlayBackdrop(Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
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
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(46.dp)
                            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White, ColorYellow)), CircleShape)
                            .border(BorderStroke(2.5.dp, ColorInk), CircleShape)
                    ) { Text(text = "🧸", fontSize = 26.sp) }
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
                            .border(BorderStroke(2.5.dp, ColorInk), CircleShape)
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
                            .border(BorderStroke(2.5.dp, ColorInk), CircleShape)
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

            val canPlayNow = canPlay
            val worlds = remember(canPlayNow) { visibleWorlds(canPlayNow) }
            val recents = remember(canPlayNow) { RecentGames.load(context).filter { canPlayNow(it) }.take(3) }

            // «Продолжить»: последние игры одним касанием (пока ничего не играли — полоски нет)
            if (recents.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("▶", fontSize = 20.sp, color = ColorInk, fontWeight = FontWeight.Black)
                    recents.forEach { idx ->
                        val item = findFolderItem(idx) ?: return@forEach
                        RecentChip(
                            item = item,
                            modifier = Modifier.weight(1f),
                            soundManager = soundManager,
                            haptics = haptics,
                            onClick = { onNavigateToGame(item.pageIndex, item.title) }
                        )
                    }
                    repeat(3 - recents.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // Шесть миров 2×3: большие тайлы, внутри видно, какие игры там живут
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val rows = (worlds.size + 1) / 2
                val gap = 14.dp
                val tileH = (maxHeight - gap * (rows - 1)) / rows.coerceAtLeast(1)
                val showPeek = tileH >= 150.dp
                Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxSize()) {
                    worlds.chunked(2).forEachIndexed { ri, pair ->
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            pair.forEachIndexed { ci, type ->
                                val info = FOLDER_INFO.getValue(type)
                                val games = visibleSections(type, canPlayNow).flatMap { it.items }
                                CandyCard(
                                    modifier = Modifier.weight(1f).fillMaxHeight().testTag("world_${type.name.lowercase()}"),
                                    title = info.title,
                                    icon = info.icon,
                                    cardColor = info.color,
                                    phaseOffset = (ri * 2 + ci) * 220,
                                    collage = games.mapNotNull { it.iconRes }.take(3),
                                    iconSize = if (showPeek) 64.dp else 50.dp,
                                    titleSize = 19,
                                    badge = games.size.toString(),
                                    soundManager = soundManager,
                                    haptics = haptics,
                                    onClick = {
                                        if (isFirstLaunch) {
                                            prefs.edit().putBoolean("first_launch_step1", false).apply()
                                            isFirstLaunch = false
                                        }
                                        onNavigateToFolder(info.title, type)
                                    }
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Нижняя полоса: «Моё» (коллекции и награды) и вход для родителей
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MineButton(
                    modifier = Modifier.weight(1f),
                    soundManager = soundManager,
                    haptics = haptics,
                    onClick = onOpenMine
                )
                ParentGearButton(
                    showLabel = true,
                    onTrigger = onOpenSettings,
                    soundManager = soundManager,
                    haptics = haptics
                )
            }
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


/** Мини-карточка «Продолжить»: значок и название последней игры. */
@Composable
private fun RecentChip(
    item: FolderItem,
    modifier: Modifier,
    soundManager: SoundManager,
    haptics: Haptics,
    onClick: () -> Unit
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(64.dp)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(item.color, Color.White, 0.5f), item.color)), shape)
            .border(BorderStroke(2.5.dp, ColorInk), shape)
            .plasticineClickable(haptics = haptics) {
                soundManager.play("pop")
                onClick()
            }
            .padding(horizontal = 6.dp)
    ) {
        if (item.iconRes != null) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = item.iconRes),
                contentDescription = null,
                modifier = Modifier.size(44.dp)
            )
        } else {
            Text(item.icon, fontSize = 24.sp)
        }
        Spacer(Modifier.width(2.dp))
        Text(item.title, fontSize = 11.sp, fontWeight = FontWeight.Black, color = ColorInk, maxLines = 2, lineHeight = 12.sp)
    }
}

/** Большая кнопка «Моё»: коллекция монстриков, стикеры, монетки. */
@Composable
private fun MineButton(modifier: Modifier, soundManager: SoundManager, haptics: Haptics, onClick: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(48.dp)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFE9A6), ColorYellow)), shape)
            .border(BorderStroke(3.dp, ColorInk), shape)
            .plasticineClickable(haptics = haptics) {
                soundManager.play("chime")
                onClick()
            }
            .testTag("mine_button")
    ) {
        Text("⭐", fontSize = 24.sp)
        Spacer(Modifier.width(8.dp))
        Text("Моё", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ColorInk)
    }
}
