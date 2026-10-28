package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.shared.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.model.AccessoryCategory
import com.example.model.PelmenAccessory
import com.example.model.PelmenAccessoryCatalog
import com.example.model.SparkleParticle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * UI-компонент "Гардероб" с плавными переходами, анимациями появления элементов
 * и примеркой аксессуаров в реальном времени.
 */
@Composable
fun PelmenWardrobeComponent(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    val gameState by repository.gameState.collectAsState()

    var selectedCategoryFilter by remember { mutableStateOf<AccessoryCategory?>(null) } // null = All
    var showOnlyUnlocked by remember { mutableStateOf(false) }
    var expressionIndex by remember { mutableIntStateOf(0) }
    var showPlayerCardDialog by remember { mutableStateOf(false) }

    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()
    val sparkles = remember { mutableStateListOf<SparkleParticle>() }

    // Floating breathing animation
    val infiniteTransition = rememberInfiniteTransition(label = "wardrobe_pelmen_float")
    val idleFloat by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_float"
    )

    // Sparkles animation timer loop
    LaunchedEffect(sparkles.size) {
        while (sparkles.isNotEmpty()) {
            delay(16)
            for (i in sparkles.indices.reversed()) {
                val p = sparkles[i]
                val newY = p.y + p.vy
                val newX = p.x + p.vx
                val newSize = p.size - 0.5f
                if (newSize <= 0f) {
                    sparkles.removeAt(i)
                } else {
                    sparkles[i] = p.copy(x = newX, y = newY, size = newSize)
                }
            }
        }
    }

    val filteredList = remember(selectedCategoryFilter, showOnlyUnlocked, gameState.unlockedAccessoryIds) {
        PelmenAccessoryCatalog.ALL_ACCESSORIES.filter { item ->
            val matchesCategory = selectedCategoryFilter == null || item.category == selectedCategoryFilter
            val matchesUnlocked = !showOnlyUnlocked || gameState.unlockedAccessoryIds.contains(item.id)
            matchesCategory && matchesUnlocked
        }
    }

    Card(
        modifier = modifier
            .fillMaxSize()
            .testTag("pelmen_wardrobe_component"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // HEADER BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👗", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Гардероб Пельмень-Сикса",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF3E2723)
                        )
                        Text(
                            text = "Примеряй аксессуары в реальном времени ✨",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Player Card Pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFE8F5E9),
                        border = BorderStroke(1.5.dp, Color(0xFF81C784)),
                        modifier = Modifier
                            .clickable {
                                soundPlayer.playSqueak()
                                soundPlayer.triggerLightTapPulse()
                                showPlayerCardDialog = true
                            }
                            .testTag("wardrobe_player_card_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🎴", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Карточка",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Coins Pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFFF3E0),
                        border = BorderStroke(1.5.dp, Color(0xFFFFB74D))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🪙", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${gameState.coins}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFE65100)
                            )
                        }
                    }

                    if (onClose != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("close_wardrobe_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = Color(0xFF5D4037)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // TOP SECTION: REAL-TIME PREVIEW MODEL OF PELMEN-SIX
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                border = BorderStroke(2.dp, Color(0xFFFFE082)),
                shadowElevation = 3.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Sparkles overlay
                    sparkles.forEach { p ->
                        Text(
                            text = p.emoji,
                            fontSize = (p.size * 2).sp,
                            modifier = Modifier
                                .offset { IntOffset(p.x.roundToInt(), p.y.roundToInt()) }
                        )
                    }

                    // Pelmen-Six Canvas & Accessories
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(0, idleFloat.roundToInt()) }
                            .scale(scaleX.value, scaleY.value)
                            .size(165.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        soundPlayer.playSqueak()
                                        soundPlayer.vibrateSquishDeform()
                                        expressionIndex = (expressionIndex + 1) % 4
                                        coroutineScope.launch {
                                            scaleX.animateTo(1.25f, spring(Spring.DampingRatioMediumBouncy))
                                            scaleY.animateTo(0.75f, spring(Spring.DampingRatioMediumBouncy))
                                            scaleX.animateTo(1f, spring(Spring.DampingRatioHighBouncy))
                                            scaleY.animateTo(1f, spring(Spring.DampingRatioHighBouncy))
                                        }
                                    }
                                )
                            }
                            .testTag("wardrobe_live_preview"),
                        contentAlignment = Alignment.Center
                    ) {
                        val textMeasurer = rememberTextMeasurer()

                        // Draw Pelmen Body
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawWardrobePelmenBody(
                                expressionIndex = expressionIndex,
                                textMeasurer = textMeasurer
                            )
                        }

                        // Equipments overlay in real-time with smooth animated transitions
                        val equippedHat = PelmenAccessoryCatalog.getById(gameState.equippedHatId)
                        val equippedGlasses = PelmenAccessoryCatalog.getById(gameState.equippedGlassesId)
                        val equippedBody = PelmenAccessoryCatalog.getById(gameState.equippedBodyId)

                        // 1. Neck / Body
                        androidx.compose.animation.AnimatedVisibility(
                            visible = equippedBody != null && equippedBody.id != "body_none",
                            enter = fadeIn(tween(180)) + scaleIn(spring(Spring.DampingRatioMediumBouncy)),
                            exit = fadeOut(tween(140)) + scaleOut(spring(Spring.DampingRatioNoBouncy)),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            if (equippedBody != null) {
                                Text(
                                    text = equippedBody.emoji,
                                    fontSize = 38.sp,
                                    modifier = Modifier.offset(y = (-20).dp)
                                )
                            }
                        }

                        // 2. Glasses
                        androidx.compose.animation.AnimatedVisibility(
                            visible = equippedGlasses != null && equippedGlasses.id != "glasses_none",
                            enter = fadeIn(tween(180)) + scaleIn(spring(Spring.DampingRatioMediumBouncy)),
                            exit = fadeOut(tween(140)) + scaleOut(spring(Spring.DampingRatioNoBouncy)),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            if (equippedGlasses != null) {
                                Text(
                                    text = equippedGlasses.emoji,
                                    fontSize = 48.sp,
                                    modifier = Modifier.offset(y = (-8).dp)
                                )
                            }
                        }

                        // 3. Hat
                        androidx.compose.animation.AnimatedVisibility(
                            visible = equippedHat != null && equippedHat.id != "hat_none",
                            enter = fadeIn(tween(180)) + scaleIn(spring(Spring.DampingRatioMediumBouncy)),
                            exit = fadeOut(tween(140)) + scaleOut(spring(Spring.DampingRatioNoBouncy)),
                            modifier = Modifier.align(Alignment.TopCenter)
                        ) {
                            if (equippedHat != null) {
                                Text(
                                    text = equippedHat.emoji,
                                    fontSize = 52.sp,
                                    modifier = Modifier.offset(y = (-20).dp)
                                )
                            }
                        }
                    }

                    // Tap hint overlay
                    Text(
                        text = "💡 Нажми на Пельмешка, чтобы реакция изменилась!",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8D6E63),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // CATEGORY TABS & FILTER CHIPS WITH ANIMATED TRANSITIONS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "All" tab
                FilterChip(
                    selected = selectedCategoryFilter == null,
                    onClick = {
                        selectedCategoryFilter = null
                        soundPlayer.playSqueak()
                        soundPlayer.triggerLightTapPulse()
                    },
                    label = { Text("Все ✨", fontSize = 11.sp, fontWeight = FontWeight.Black) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFFB300),
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("filter_all")
                )

                AccessoryCategory.values().forEach { cat ->
                    val isSelected = selectedCategoryFilter == cat
                    val chipScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1.0f,
                        animationSpec = spring(Spring.DampingRatioMediumBouncy),
                        label = "chip_scale"
                    )

                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedCategoryFilter = cat
                            soundPlayer.playSqueak()
                            soundPlayer.triggerLightTapPulse()
                        },
                        label = { Text("${cat.emoji} ${cat.title}", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFB300),
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier
                            .scale(chipScale)
                            .testTag("filter_${cat.name}")
                    )
                }
            }

            // Unlocked Only Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Показано аксессуаров: ${filteredList.size}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5D4037)
                )

                val toggleBgColor by animateColorAsState(
                    targetValue = if (showOnlyUnlocked) Color(0xFFE8F5E9) else Color.White,
                    animationSpec = tween(220),
                    label = "toggle_bg"
                )
                val toggleBorderColor by animateColorAsState(
                    targetValue = if (showOnlyUnlocked) Color(0xFF4CAF50) else Color(0xFFE0E0E0),
                    animationSpec = tween(220),
                    label = "toggle_border"
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = toggleBgColor,
                    border = BorderStroke(1.dp, toggleBorderColor),
                    modifier = Modifier.clickable {
                        showOnlyUnlocked = !showOnlyUnlocked
                        soundPlayer.playSqueak()
                        soundPlayer.triggerLightTapPulse()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (showOnlyUnlocked) "🔓 Только открытые" else "👁️ Все элементы", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // ACCESSORIES GRID WITH ANIMATED CONTENT TRANSITION
            AnimatedContent(
                targetState = filteredList,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.94f, animationSpec = spring(Spring.DampingRatioMediumBouncy)) togetherWith
                            fadeOut(animationSpec = tween(160)) +
                            scaleOut(targetScale = 0.96f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "accessories_grid_transition"
            ) { itemsList ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(itemsList.size, key = { itemsList[it].id }) { index ->
                        val item = itemsList[index]
                        val isUnlocked = gameState.unlockedAccessoryIds.contains(item.id)
                        val isEquipped = when (item.category) {
                            AccessoryCategory.HAT -> gameState.equippedHatId == item.id
                            AccessoryCategory.GLASSES -> gameState.equippedGlassesId == item.id
                            AccessoryCategory.BODY -> gameState.equippedBodyId == item.id
                        }

                        // Staggered entry animation
                        val itemAlpha = remember { Animatable(0f) }
                        val itemScale = remember { Animatable(0.85f) }

                        LaunchedEffect(item.id) {
                            val delayMs = (index * 35L).coerceAtMost(300L)
                            delay(delayMs)
                            launch { itemAlpha.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
                            launch { itemScale.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 400f)) }
                        }

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    this.alpha = itemAlpha.value
                                    this.scaleX = itemScale.value
                                    this.scaleY = itemScale.value
                                }
                        ) {
                            WardrobeItemGridCard(
                                item = item,
                                isUnlocked = isUnlocked,
                                isEquipped = isEquipped,
                                userCoins = gameState.coins,
                                onEquip = {
                                    repository.equipAccessory(item.category, item.id)
                                    soundPlayer.playSqueak()
                                    soundPlayer.triggerLightTapPulse()

                                    // Spawn celebratory sparkles
                                    val emojis = listOf("✨", "🎉", "🌟", "💖", "🎩", "👑")
                                    repeat(6) {
                                        sparkles.add(
                                            SparkleParticle(
                                                id = System.nanoTime() + it,
                                                x = (Random.nextFloat() - 0.5f) * 140f,
                                                y = (Random.nextFloat() - 0.5f) * 140f,
                                                vx = (Random.nextFloat() - 0.5f) * 8f,
                                                vy = (Random.nextFloat() - 1.2f) * 7f,
                                                color = Color(0xFFFFD54F),
                                                size = Random.nextFloat() * 8f + 10f,
                                                emoji = emojis.random()
                                            )
                                        )
                                    }

                                    coroutineScope.launch {
                                        scaleX.snapTo(1.2f)
                                        scaleY.snapTo(0.8f)
                                        scaleX.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                                        scaleY.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                                    }
                                },
                                onBuy = {
                                    if (repository.buyAccessoryWithCoins(item.id, item.coinPrice)) {
                                        repository.equipAccessory(item.category, item.id)
                                        soundPlayer.playSparkleChime()
                                        soundPlayer.playFanfare()
                                        soundPlayer.triggerLightTapPulse()

                                        // Big confetti burst
                                        val emojis = listOf("🪙", "✨", "🎁", "🎉", "👑", "🌟")
                                        repeat(12) {
                                            sparkles.add(
                                                SparkleParticle(
                                                    id = System.nanoTime() + it,
                                                    x = (Random.nextFloat() - 0.5f) * 180f,
                                                    y = (Random.nextFloat() - 0.5f) * 180f,
                                                    vx = (Random.nextFloat() - 0.5f) * 10f,
                                                    vy = (Random.nextFloat() - 1.5f) * 9f,
                                                    color = Color(0xFFFFB300),
                                                    size = Random.nextFloat() * 10f + 12f,
                                                    emoji = emojis.random()
                                                )
                                            )
                                        }

                                        coroutineScope.launch {
                                            scaleX.snapTo(1.28f)
                                            scaleY.snapTo(0.72f)
                                            scaleX.animateTo(1f, spring(Spring.DampingRatioHighBouncy))
                                            scaleY.animateTo(1f, spring(Spring.DampingRatioHighBouncy))
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPlayerCardDialog) {
        PlayerCardDialog(
            gameState = gameState,
            soundPlayer = soundPlayer,
            onDismiss = { showPlayerCardDialog = false }
        )
    }
}

@Composable
fun WardrobeItemGridCard(
    item: PelmenAccessory,
    isUnlocked: Boolean,
    isEquipped: Boolean,
    userCoins: Int,
    onEquip: () -> Unit,
    onBuy: () -> Unit
) {
    val cardScale = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    val cardBgColor by animateColorAsState(
        targetValue = if (isEquipped) Color(0xFFFFF3E0) else Color.White,
        animationSpec = tween(220),
        label = "card_bg"
    )
    val cardBorderColor by animateColorAsState(
        targetValue = if (isEquipped) Color(0xFFFF6F00) else if (isUnlocked) Color(0xFFFFD54F) else Color(0xFFE0E0E0),
        animationSpec = tween(220),
        label = "card_border"
    )
    val cardElevation by animateFloatAsState(
        targetValue = if (isEquipped) 6f else 2f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "card_elevation"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .scale(cardScale.value)
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                coroutineScope.launch {
                    cardScale.animateTo(0.93f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh))
                    cardScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy))
                }
                if (isUnlocked) {
                    onEquip()
                } else if (userCoins >= item.coinPrice && item.coinPrice > 0) {
                    onBuy()
                }
            }
            .testTag("wardrobe_grid_card_${item.id}"),
        shape = RoundedCornerShape(20.dp),
        color = cardBgColor,
        border = BorderStroke(
            width = if (isEquipped) 2.5.dp else 1.dp,
            color = cardBorderColor
        ),
        shadowElevation = cardElevation.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.emoji,
                    fontSize = 32.sp
                )

                AnimatedVisibility(
                    visible = isEquipped,
                    enter = scaleIn(spring(Spring.DampingRatioHighBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF4CAF50)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Надето",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(3.dp)
                                .size(14.dp)
                        )
                    }
                }

                if (!isEquipped && !isUnlocked) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Заблокировано",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = item.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF3E2723),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Text(
                    text = if (isUnlocked) item.description else "Условие: ${item.unlockRequirement}",
                    fontSize = 9.5.sp,
                    color = if (isUnlocked) Color(0xFF795548) else Color(0xFFD84315),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            // Bottom action button / tag with animated transitions
            AnimatedContent(
                targetState = Triple(isEquipped, isUnlocked, userCoins >= item.coinPrice && item.coinPrice > 0),
                transitionSpec = {
                    fadeIn(tween(180)) + scaleIn(initialScale = 0.9f) togetherWith fadeOut(tween(120))
                },
                label = "btn_state_transition"
            ) { (equipped, unlocked, canBuy) ->
                if (equipped) {
                    Button(
                        onClick = onEquip,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("✅ Надето", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                } else if (unlocked) {
                    Button(
                        onClick = onEquip,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("Надеть ✨", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                } else if (canBuy) {
                    Button(
                        onClick = onBuy,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("Купить ${item.coinPrice} 🪙", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFEEEEEE)
                    ) {
                        Text(
                            text = if (item.coinPrice > 0) "${item.coinPrice} 🪙" else "🔒 Закрыто",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Диалоговое окно с компонентом Гардероба для вызова из любой точки приложения,
 * с плавной анимацией появления и скрытия.
 */
@Composable
fun PelmenWardrobeDialog(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogScale = remember { Animatable(0.85f) }
        val dialogAlpha = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            launch {
                dialogAlpha.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
            }
            launch {
                dialogScale.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 420f))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            PelmenWardrobeComponent(
                repository = repository,
                soundPlayer = soundPlayer,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .scale(dialogScale.value),
                onClose = onDismiss
            )
        }
    }
}

internal fun DrawScope.drawWardrobePelmenBody(
    expressionIndex: Int,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val width = size.width * 0.82f
    val height = size.height * 0.78f

    // Soft drop shadow
    drawOval(
        color = Color(0x30000000),
        topLeft = Offset(center.x - width * 0.45f, center.y + height * 0.36f),
        size = Size(width * 0.9f, height * 0.22f)
    )

    val bodyPath = Path().apply {
        moveTo(center.x - width * 0.45f, center.y + height * 0.1f)
        cubicTo(
            center.x - width * 0.52f, center.y - height * 0.35f,
            center.x - width * 0.18f, center.y - height * 0.48f,
            center.x, center.y - height * 0.42f
        )
        cubicTo(
            center.x + width * 0.18f, center.y - height * 0.48f,
            center.x + width * 0.52f, center.y - height * 0.35f,
            center.x + width * 0.45f, center.y + height * 0.1f
        )
        cubicTo(
            center.x + width * 0.45f, center.y + height * 0.45f,
            center.x - width * 0.45f, center.y + height * 0.45f,
            center.x - width * 0.45f, center.y + height * 0.1f
        )
        close()
    }

    drawPath(
        path = bodyPath,
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFE082), Color(0xFFFFB300)),
            center = Offset(center.x, center.y - 12f),
            radius = width * 0.72f
        )
    )
    drawPath(
        path = bodyPath,
        color = Color(0xFFE65100),
        style = Stroke(width = 5.5f)
    )

    // "67" emblem
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFECB3), Color(0xFFFFD54F)),
            center = Offset(center.x, center.y + 24f),
            radius = 26f
        ),
        radius = 26f,
        center = Offset(center.x, center.y + 24f)
    )
    drawCircle(
        color = Color(0xFFFF6F00),
        radius = 26f,
        center = Offset(center.x, center.y + 24f),
        style = Stroke(width = 3.5f)
    )

    val layoutResult = textMeasurer.measure(
        text = "67",
        style = TextStyle(
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFFD84315)
        )
    )
    drawText(
        textLayoutResult = layoutResult,
        topLeft = Offset(center.x - layoutResult.size.width / 2f, center.y + 24f - layoutResult.size.height / 2f)
    )

    // Rosy Pink Cheeks
    drawCircle(
        color = Color(0xFFFF8A80).copy(alpha = 0.65f),
        radius = 16f,
        center = Offset(center.x - width * 0.25f, center.y + 8f)
    )
    drawCircle(
        color = Color(0xFFFF8A80).copy(alpha = 0.65f),
        radius = 16f,
        center = Offset(center.x + width * 0.25f, center.y + 8f)
    )

    // Eyes & Smile
    val eyeLeft = Offset(center.x - width * 0.16f, center.y - 10f)
    val eyeRight = Offset(center.x + width * 0.16f, center.y - 10f)

    when (expressionIndex) {
        0 -> {
            drawCircle(color = Color(0xFF212121), radius = 12f, center = eyeLeft)
            drawCircle(color = Color.White, radius = 4f, center = Offset(eyeLeft.x - 3f, eyeLeft.y - 3f))
            drawCircle(color = Color(0xFF212121), radius = 12f, center = eyeRight)
            drawCircle(color = Color.White, radius = 4f, center = Offset(eyeRight.x - 3f, eyeRight.y - 3f))

            val smile = Path().apply {
                moveTo(center.x - 16f, center.y + 6f)
                quadraticBezierTo(center.x, center.y + 22f, center.x + 16f, center.y + 6f)
            }
            drawPath(smile, color = Color(0xFF3E2723), style = Stroke(width = 5f))
        }
        1 -> {
            drawCircle(color = Color(0xFF212121), radius = 12f, center = eyeLeft)
            drawCircle(color = Color.White, radius = 4f, center = Offset(eyeLeft.x - 3f, eyeLeft.y - 3f))

            val wink = Path().apply {
                moveTo(eyeRight.x - 10f, eyeRight.y)
                quadraticBezierTo(eyeRight.x, eyeRight.y - 8f, eyeRight.x + 10f, eyeRight.y)
            }
            drawPath(wink, color = Color(0xFF212121), style = Stroke(width = 5f))

            val mouth = Path().apply {
                moveTo(center.x - 14f, center.y + 6f)
                quadraticBezierTo(center.x, center.y + 24f, center.x + 14f, center.y + 6f)
            }
            drawPath(mouth, color = Color(0xFF3E2723), style = Stroke(width = 5f))
        }
        2 -> {
            drawCircle(color = Color(0xFFFF1744), radius = 10f, center = eyeLeft)
            drawCircle(color = Color(0xFFFF1744), radius = 10f, center = eyeRight)

            drawOval(
                color = Color(0xFF3E2723),
                topLeft = Offset(center.x - 10f, center.y + 6f),
                size = Size(20f, 16f)
            )
        }
        else -> {
            val leftArch = Path().apply {
                moveTo(eyeLeft.x - 12f, eyeLeft.y + 3f)
                quadraticBezierTo(eyeLeft.x, eyeLeft.y - 10f, eyeLeft.x + 12f, eyeLeft.y + 3f)
            }
            val rightArch = Path().apply {
                moveTo(eyeRight.x - 12f, eyeRight.y + 3f)
                quadraticBezierTo(eyeRight.x, eyeRight.y - 10f, eyeRight.x + 12f, eyeRight.y + 3f)
            }
            drawPath(leftArch, color = Color(0xFF212121), style = Stroke(width = 5f))
            drawPath(rightArch, color = Color(0xFF212121), style = Stroke(width = 5f))

            val bigSmile = Path().apply {
                moveTo(center.x - 18f, center.y + 6f)
                quadraticBezierTo(center.x, center.y + 28f, center.x + 18f, center.y + 6f)
                close()
            }
            drawPath(bigSmile, color = Color(0xFF3E2723))
        }
    }
}
