package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.window.Dialog
import com.example.shared.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import com.example.audio.SoundEffectPlayer
import com.example.model.GameState
import com.example.ui.theme.PlaygroundEnvironmentType
import com.example.ui.viewmodel.PlaygroundViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 🍬 Toddler Tactile Press Bounce Modifier.
 * Shrinks button to 0.88f on physical press and springs back on release,
 * triggering subtle HapticFeedbackConstants vibration on press and click.
 */
@Composable
fun Modifier.toddlerBounceClick(
    pressedScale: Float = 0.88f,
    onClick: () -> Unit
): Modifier {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            try {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } catch (_: Exception) {}
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "toddler_press_scale"
    )
    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                try {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                } catch (_: Exception) {}
                onClick()
            }
        )
}

/**
 * Thematic categories / blocks of games for clear, structured toddler orientation (2-3 y.o.).
 */
enum class PlaygroundCategory(
    val title: String,
    val shortTitle: String,
    val emoji: String,
    val primaryColor: Color,
    val lightColor: Color,
    val darkColor: Color
) {
    ALL("Все игры", "Все", "🎪", Color(0xFFFF8F00), Color(0xFFFFF8E1), Color(0xFFE65100)),
    ANTISTRESS("Весёлый Антистресс", "Антистресс", "💥", Color(0xFFFF5722), Color(0xFFFBE9E7), Color(0xFFD84315)),
    ANIMALS("Зверята и Забота", "Зверята", "🐾", Color(0xFF43A047), Color(0xFFE8F5E9), Color(0xFF2E7D32)),
    CREATIVE("Творчество и Сенсорика", "Творим", "🎨", Color(0xFF00ACC1), Color(0xFFE0F7FA), Color(0xFF00838F)),
    HOLIDAY("Праздник и Подарки", "Праздник", "🎈", Color(0xFFE91E63), Color(0xFFFCE4EC), Color(0xFFC2185B))
}

enum class PlaygroundRoom(
    val title: String,
    val emoji: String,
    val secondaryEmoji: String,
    val testTag: String,
    val category: PlaygroundCategory = PlaygroundCategory.ANTISTRESS
) {
    ANIMALS("Зверята", "🐶", "🐾", "nav_animals", PlaygroundCategory.ANIMALS),
    FEEDING("Кормление", "🍎", "🍼", "nav_feeding", PlaygroundCategory.ANIMALS),
    SQUISHIES("Пельмешек", "🥟", "✨", "nav_squishies", PlaygroundCategory.ANTISTRESS),
    FACE_ANTISTRESS("Рожица", "🤪", "⭐", "nav_face_antistress", PlaygroundCategory.ANTISTRESS),
    SCARECROW("Лук и Чучело", "🏹", "🎯", "nav_scarecrow", PlaygroundCategory.ANTISTRESS),
    RUNNER_BOOBA("Бегун", "🏃‍♂️", "🥊", "nav_runner_booba", PlaygroundCategory.ANTISTRESS),
    GRAIN("Сыпучести", "🌾", "🥣", "nav_grain", PlaygroundCategory.CREATIVE),
    BALLOONS("Шарики", "🎈", "🎉", "nav_balloons", PlaygroundCategory.HOLIDAY),
    TEA_PARTY("Чайная", "🫖", "🧁", "nav_tea", PlaygroundCategory.ANIMALS),
    PAINTING("Рисование", "🎨", "🖌️", "nav_painting", PlaygroundCategory.CREATIVE),
    BUSYBOARD("Бизиборд", "🎛️", "🔔", "nav_busyboard", PlaygroundCategory.CREATIVE),
    BLIND_BOXES("Сюрпризы", "🎁", "⭐", "nav_blind_boxes", PlaygroundCategory.HOLIDAY),
    PINBALL("Пинбол", "🔮", "✨", "nav_pinball", PlaygroundCategory.ANTISTRESS)
}

/**
 * 🔒 Hidden Corner Gesture for Parents (3-second hold to open Parental Settings).
 * Prevents 2-3 year old toddlers from accidentally entering settings or exiting the app.
 * Quick taps are absorbed safely with a gentle bounce without opening settings or exiting.
 * Holding for 3 full seconds fills a glowing circular ring with progress and opens settings.
 */
@Composable
fun ParentHiddenCornerGestureButton(
    modifier: Modifier = Modifier,
    soundPlayer: SoundEffectPlayer? = null,
    onOpenParentSettings: () -> Unit
) {
    var isHolding by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }
    val buttonScale = remember { Animatable(1f) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(46.dp)
            .testTag("parent_hidden_gesture_button")
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        soundPlayer?.triggerLightTapPulse()
                        holdJob?.cancel()
                        holdJob = coroutineScope.launch {
                            buttonScale.animateTo(0.90f, tween(150))
                            progress.snapTo(0f)
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
                            )
                            // 3 SECONDS HELD - TRIGGER PARENTAL SETTINGS!
                            soundPlayer?.playLockClick(false)
                            buttonScale.animateTo(1.15f, spring(Spring.DampingRatioMediumBouncy))
                            buttonScale.animateTo(1f)
                            onOpenParentSettings()
                        }
                        val released = tryAwaitRelease()
                        isHolding = false
                        holdJob?.cancel()
                        coroutineScope.launch {
                            buttonScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                            progress.animateTo(0f, tween(180))
                        }
                        if (released && progress.value < 0.98f) {
                            // Toddler just tapped quickly or released before 3 seconds
                            soundPlayer?.triggerLightTapPulse()
                        }
                    }
                )
            }
    ) {
        // Subtle background bubble - looks like a friendly decorative lock/shield to the child
        Surface(
            shape = CircleShape,
            color = if (isHolding) Color(0xFFFFF3E0) else Color.White.copy(alpha = 0.92f),
            border = BorderStroke(
                width = if (isHolding) 2.5.dp else 1.5.dp,
                color = if (isHolding) Color(0xFFFF9800) else Color(0xFFFFCA28)
            ),
            shadowElevation = if (isHolding) 6.dp else 2.dp,
            modifier = Modifier
                .size(40.dp)
                .scale(buttonScale.value)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                if (isHolding) {
                    val secsLeft = (3f * (1f - progress.value)).toInt() + 1
                    Text(
                        text = "${secsLeft}с",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFE65100)
                    )
                } else {
                    Text(
                        text = "⚙️",
                        fontSize = 18.sp
                    )
                }
            }
        }

        // Circular progress ring showing 3-second hold fill
        if (isHolding || progress.value > 0f) {
            Canvas(modifier = Modifier.size(44.dp)) {
                val strokeWidth = 3.5.dp.toPx()
                drawArc(
                    color = Color(0xFFFF9800),
                    startAngle = -90f,
                    sweepAngle = progress.value * 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
    }
}

/**
 * Toddler-Friendly Top Action Bar with big touch targets, category blocks,
 * room swipe lock to prevent accidental room switching, and toddler safety shield.
 */
@Composable
fun PlaygroundTopBar(
    gameState: GameState,
    environmentType: PlaygroundEnvironmentType,
    isMusicPlaying: Boolean,
    currentTrackTitle: String,
    currentRoomTitle: String = "Зверята",
    currentRoomEmoji: String = "🐶",
    currentCategory: PlaygroundCategory = PlaygroundCategory.ANIMALS,
    isToddlerLockActive: Boolean = true,
    isRoomSwipeLocked: Boolean = true,
    isFullscreen: Boolean = false,
    onToggleRoomSwipeLock: () -> Unit = {},
    onToggleToddlerLock: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    onOpenGameHub: () -> Unit,
    onMusicToggle: () -> Unit,
    onEnvironmentClick: () -> Unit,
    onStarBadgeClick: () -> Unit,
    onOpenWardrobe: (() -> Unit)? = null,
    onOpenSettings: () -> Unit,
    soundPlayer: SoundEffectPlayer? = null
) {
    Surface(
        color = Color(0xFFFFCA28),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFEE58),
                            Color(0xFFFFD54F),
                            Color(0xFFFFCA28)
                        )
                    )
                )
        ) {
            // Glossy highlight strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.65f))
                    .align(Alignment.TopCenter)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Big Chunky Animated "🎪" Icon Button (No textual clutter for toddlers!)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(2.5.dp, Color(0xFFFF8F00)),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .height(44.dp)
                        .toddlerBounceClick { onOpenGameHub() }
                        .testTag("topbar_game_hub_button")
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.White, Color(0xFFFFF8E1), Color(0xFFFFE082))
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🎪", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(text = "✨", fontSize = 12.sp)
                        }
                    }
                }

                if (isToddlerLockActive) {
                    // ==========================================
                    // 🔒 TODDLER SAFE MODE: CLEAN, PROTECTED TOP BAR
                    // ==========================================
                    // Active Room Pill with giant animated room icon (e.g. 🥟 or 🏹)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = currentCategory.lightColor,
                        border = BorderStroke(2.5.dp, currentCategory.primaryColor),
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .toddlerBounceClick { onOpenGameHub() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(currentRoomEmoji, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = currentRoomTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = currentCategory.darkColor
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 🔒 Room Focus Lock Toggle ("Замок экрана / свайпа") - Icon-only, large and animated
                        Surface(
                            shape = CircleShape,
                            color = if (isRoomSwipeLocked) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                            border = BorderStroke(
                                2.dp,
                                if (isRoomSwipeLocked) Color(0xFF4CAF50) else Color(0xFFFF9800)
                            ),
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .size(40.dp)
                                .toddlerBounceClick { onToggleRoomSwipeLock() }
                                .testTag("topbar_room_swipe_lock_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(if (isRoomSwipeLocked) "🔒" else "🔓", fontSize = 17.sp)
                            }
                        }

                        // Star Coins Badge with golden shine
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(2.dp, Color(0xFFFFB300)),
                            shadowElevation = 2.dp,
                            modifier = Modifier.toddlerBounceClick { onStarBadgeClick() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⭐", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${gameState.coins}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFE65100)
                                )
                            }
                        }

                        // 📺 Fullscreen / Immersive Game Focus Toggle
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE1F5FE),
                            border = BorderStroke(2.dp, Color(0xFF03A9F4)),
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .size(40.dp)
                                .toddlerBounceClick { onToggleFullscreen() }
                                .testTag("topbar_fullscreen_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("📺", fontSize = 16.sp)
                            }
                        }

                        // 🔒 HIDDEN CORNER GESTURE FOR PARENTS (3-second hold to open parental settings!)
                        ParentHiddenCornerGestureButton(
                            soundPlayer = soundPlayer,
                            onOpenParentSettings = onOpenSettings
                        )
                    }
                } else {
                    // ==========================================
                    // 🔓 PARENT MODE: FULL CONTROLS
                    // ==========================================
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Environment Mode Pill
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.94f),
                            border = BorderStroke(1.5.dp, Color(0xFFFFB300)),
                            modifier = Modifier.clickable { onEnvironmentClick() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = environmentType.emoji, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = environmentType.title.take(5),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF3E2723)
                                )
                            }
                        }

                        // Music Pill
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isMusicPlaying) Color(0xFFFFF8E1) else Color(0xFFEEEEEE),
                            border = BorderStroke(
                                1.5.dp,
                                if (isMusicPlaying) Color(0xFFFFB300) else Color(0xFFBDBDBD)
                            ),
                            modifier = Modifier.clickable { onMusicToggle() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = if (isMusicPlaying) "🎵" else "🔇", fontSize = 14.sp)
                            }
                        }

                        // Re-lock to Toddler Mode button
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFFFF3E0),
                            border = BorderStroke(1.5.dp, Color(0xFFFF9800)),
                            modifier = Modifier.clickable { onToggleToddlerLock() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔒", fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Щит", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                            }
                        }

                        // Settings Gear (for parents)
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Родителям",
                                tint = Color(0xFF5D4037),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Big, Chunky Scrollable Candy Toy Shelf Navigation Bar for Toddlers (2-3 y.o.).
 * Large touch targets (> 68dp), giant 34-38sp animated emoji icons, distinct category colors,
 * intuitive picture buttons (e.g. 🏹 bow for scarecrow archery, 🥟 dumpling for main game),
 * idle floating wobble, and NO tiny unreadable text clutter.
 */
@Composable
fun PlaygroundBottomBar(
    currentRoomIndex: Int,
    onOpenGameHub: () -> Unit,
    onSelectRoom: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll list to center active room
    LaunchedEffect(currentRoomIndex) {
        coroutineScope.launch {
            val targetScroll = (currentRoomIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    // Playful idle breathing / hovering for toy icons
    val infiniteTransition = rememberInfiniteTransition(label = "bottom_shelf_anim")
    val idleScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_scale"
    )

    Surface(
        color = Color(0xFFFFFDE7),
        shadowElevation = 10.dp,
        border = BorderStroke(1.5.dp, Color(0xFFFFD54F)),
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("toddler_nav_bar")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White,
                            Color(0xFFFFFDE7),
                            Color(0xFFFFF8E1)
                        )
                    )
                )
        ) {
            // Soft glossy top accent line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color(0xFFFFD54F).copy(alpha = 0.5f))
                    .align(Alignment.TopCenter)
            )

            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // First item: Quick Map/Hub Button (Large animated Circus icon button)
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color.Transparent,
                        border = BorderStroke(2.5.dp, Color(0xFFFF8F00)),
                        shadowElevation = 5.dp,
                        modifier = Modifier
                            .height(74.dp)
                            .width(68.dp)
                            .toddlerBounceClick(pressedScale = 0.86f) {
                                onOpenGameHub()
                            }
                            .testTag("bottom_hub_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFFCA28))
                                    )
                                )
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("🎪", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .size(width = 16.dp, height = 4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFFFF6F00))
                                )
                            }
                            // Mini sparkle sticker
                            Text(
                                text = "✨",
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                            )
                        }
                    }
                }

                // Room Buttons: Chunky, brightly animated pictorial toy icons (no unreadable text clutter!)
                itemsIndexed(PlaygroundRoom.values()) { index, room ->
                    val isSelected = currentRoomIndex == index
                    val cat = room.category

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = if (isSelected) cat.lightColor else Color.White,
                        border = BorderStroke(
                            width = if (isSelected) 3.5.dp else 2.dp,
                            color = if (isSelected) cat.primaryColor else cat.primaryColor.copy(alpha = 0.55f)
                        ),
                        shadowElevation = if (isSelected) 9.dp else 3.dp,
                        modifier = Modifier
                            .height(74.dp)
                            .width(68.dp)
                            .scale(if (isSelected) 1.06f else idleScale)
                            .toddlerBounceClick(pressedScale = 0.85f) {
                                onSelectRoom(index)
                            }
                            .testTag(room.testTag)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isSelected) {
                                        Brush.verticalGradient(
                                            listOf(Color.White, cat.lightColor)
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(Color.White, Color(0xFFFAFAFA))
                                        )
                                    }
                                )
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Giant, intuitively recognizable emoji (e.g. 🏹 bow or 🥟 pelmen)
                                Text(
                                    text = room.emoji,
                                    fontSize = if (isSelected) 38.sp else 33.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                // Rounded Category Color Pill Indicator
                                Box(
                                    modifier = Modifier
                                        .size(
                                            width = if (isSelected) 22.dp else 14.dp,
                                            height = if (isSelected) 5.dp else 4.dp
                                        )
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(cat.primaryColor)
                                )
                            }

                            // Secondary sticker badge in corner (e.g. 🎯 on bow, ✨ on dumpling)
                            Surface(
                                shape = CircleShape,
                                color = cat.primaryColor.copy(alpha = if (isSelected) 0.95f else 0.80f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(17.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = room.secondaryEmoji,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Settings and Parent Controls Dialog.
 */
@Composable
fun PlaygroundSettingsDialog(
    gameState: GameState,
    environmentType: PlaygroundEnvironmentType,
    isMusicPlaying: Boolean,
    viewModel: PlaygroundViewModel,
    onDismiss: () -> Unit
) {
    val unlockedCount = gameState.unlockedCharacterIds.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "⚙️ Настройки и декорации",
                fontWeight = FontWeight.Black,
                color = Color(0xFF3E2723),
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Background Scenery Selector
                Text(
                    text = "Фоновые декорации площадки:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF3E2723)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(PlaygroundEnvironmentType.values()) { env ->
                        val isSelected = environmentType == env
                        val isLocked = unlockedCount < env.requiredCharactersUnlocked

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0xFFFFB300) else if (isLocked) Color(0xFFEEEEEE) else Color(0xFFFFF8E1),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFFFF6F00) else Color(0xFFFFD54F)
                            ),
                            modifier = Modifier.clickable {
                                if (!isLocked) {
                                    viewModel.setEnvironment(env)
                                } else {
                                    viewModel.soundPlayer.triggerLightTapPulse()
                                }
                            }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(if (isLocked) "🔒" else env.emoji, fontSize = 20.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = env.title,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLocked) Color.Gray else Color(0xFF3E2723)
                                )
                                if (isLocked) {
                                    Text(
                                        text = "${env.requiredCharactersUnlocked} 🎁",
                                        fontSize = 8.5.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                // Palm Rejection & Edge Shield Toggle (Защита от случайных нажатий и ладошек)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFE8F5E9),
                    border = BorderStroke(1.dp, Color(0xFFA5D6A7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🛡️", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Защита от ладошек (Palm Rejection)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF1B5E20)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Игнорирует случайные касания краев экрана и ладошек малыша",
                                fontSize = 10.sp,
                                color = Color(0xFF2E7D32),
                                lineHeight = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = gameState.palmRejectionEnabled,
                            onCheckedChange = { viewModel.togglePalmRejection(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF2E7D32),
                                checkedTrackColor = Color(0xFFA5D6A7)
                            )
                        )
                    }
                }

                // Sound Effects Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color(0xFFE65100))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Звуковые эффекты (SFX)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Switch(
                        checked = viewModel.soundPlayer.soundEnabled,
                        onCheckedChange = { viewModel.toggleSoundEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFFB300))
                    )
                }

                // Haptics Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Vibration, contentDescription = null, tint = Color(0xFFE65100))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Виброотклик (Тактильность)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Switch(
                        checked = viewModel.soundPlayer.hapticsEnabled,
                        onCheckedChange = { viewModel.toggleHapticsEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFFB300))
                    )
                }

                // Background Music Toggle & Skip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isMusicPlaying) Icons.Default.MusicNote else Icons.Default.MusicOff,
                            contentDescription = null,
                            tint = Color(0xFFE65100)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Фоновая музыка", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.nextMusicTrack() }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Следующий трек", tint = Color(0xFFE65100))
                        }
                        Switch(
                            checked = isMusicPlaying,
                            onCheckedChange = { viewModel.toggleMusic() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFFB300))
                        )
                    }
                }

                // Quick Reset Progress Button
                Button(
                    onClick = {
                        viewModel.resetProgress()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Сбросить прогресс", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
            ) {
                Text("Готово", color = Color(0xFF3E2723), fontWeight = FontWeight.Black)
            }
        }
    )
}

/**
 * Toddler Exit Protection Dialog ("Детский щит от случайного выхода").
 * Prevents 2-3 year old toddlers from accidentally exiting the app when they swipe screen edges
 * or press the Android back navigation.
 */
@Composable
fun ToddlerExitProtectionDialog(
    onContinuePlaying: () -> Unit,
    onParentUnlock: () -> Unit
) {
    Dialog(onDismissRequest = onContinuePlaying) {
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)),
            border = BorderStroke(3.5.dp, Color(0xFFFFB300)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🎈", fontSize = 56.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ты в игре! Играем дальше?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF3E2723),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE8F5E9),
                    border = BorderStroke(1.5.dp, Color(0xFF81C784))
                ) {
                    Text(
                        text = "🔒 Детский щит: защита от случайного выхода активна",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Giant Chunky Continue Playing Button for the toddler with tactile bounce!
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF4CAF50),
                    border = BorderStroke(2.5.dp, Color(0xFF2E7D32)),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .toddlerBounceClick(pressedScale = 0.88f) {
                            onContinuePlaying()
                        }
                        .testTag("exit_shield_continue_button")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF66BB6A), Color(0xFF43A047), Color(0xFF2E7D32))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("🎮", fontSize = 26.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ИГРАТЬ ДАЛЬШЕ! 🌈",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Parental unlock with Hold-to-Exit (3 seconds hold) or math gate
                HoldToExitParentSection(
                    onParentUnlock = onParentUnlock
                )
            }
        }
    }
}

/**
 * Childproof Hold-to-Exit mechanism for parents.
 * Requires holding the button for 3 seconds continuously, which 2-3 y.o. children cannot perform.
 */
@Composable
fun HoldToExitParentSection(
    onParentUnlock: () -> Unit
) {
    var isHolding by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF5F5F5),
        border = BorderStroke(1.5.dp, if (isHolding) Color(0xFFFF9800) else Color(0xFFE0E0E0)),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        val job = coroutineScope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 2600, easing = LinearEasing)
                            )
                            // Held for required time!
                            onParentUnlock()
                        }
                        tryAwaitRelease()
                        isHolding = false
                        job.cancel()
                        progress.snapTo(0f)
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(if (isHolding) "⏳" else "🔒", fontSize = 15.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isHolding) "Держите ещё немного..." else "Родителям: Удерживайте 3 сек для выхода",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHolding) Color(0xFFE65100) else Color(0xFF616161)
                )
            }
            if (isHolding || progress.value > 0f) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFFFF9800),
                    trackColor = Color(0xFFE0E0E0)
                )
            }
        }
    }
}
