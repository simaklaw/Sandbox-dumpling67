package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.*
import com.example.shared.scene.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

fun getFeedingPlateColor(type: String): Color = when (type) {
    "bone" -> Color(0xFF8FD8FF)
    "meat" -> Color(0xFFFF9EBB)
    "fish" -> Color(0xFF6EC6FF)
    "milk" -> Color(0xFFFFFFFF)
    "carrot" -> Color(0xFFFFB347)
    "apple" -> Color(0xFF9EE7A5)
    else -> Color(0xFFFFE082)
}

// Food model
data class FoodData(
    val emoji: String,
    val type: String
)

val FEEDING_FOODS = listOf(
    FoodData("🦴", "bone"),
    FoodData("🥩", "meat"),
    FoodData("🐟", "fish"),
    FoodData("🥛", "milk"),
    FoodData("🥕", "carrot"),
    FoodData("🍎", "apple")
)

// Active states of pet
enum class PetState {
    IDLE, WAITING, MOUTH_OPEN, CHEWING, HAPPY, REFUSE, FULL
}

// Particle model for success burst
data class RewardParticle(
    val id: Long,
    val emoji: String,
    val startX: Float,
    val startY: Float,
    val velocityX: Float,
    val velocityY: Float
)

@Composable
fun AnimalFeedingScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val coroutineScope = rememberCoroutineScope()
    val ageProfile = LocalAgeProfile.current

    // 1. App State for Pets & Satiety
    val pets = remember { listOf("dog" to "🐶", "cat" to "🐱", "bunny" to "🐰") }
    var selectedPetId by remember { mutableStateOf("dog") }

    // Satiety map for puppy, kitty, bunny (0 to 3)
    val satietyMap = remember { mutableStateMapOf("dog" to 0, "cat" to 0, "bunny" to 0) }

    // Active state machine for the selected pet
    var petState by remember { mutableStateOf(PetState.IDLE) }

    // 2. Active "Want" cloud food target state
    val wantFoodMap = remember { mutableStateMapOf<String, FoodData>() }
    
    // Choose favorite food for pet
    fun getFavoriteFoodForPet(petId: String): FoodData {
        val favorites = when (petId) {
            "dog" -> listOf(FEEDING_FOODS[0], FEEDING_FOODS[1]) // bone, meat
            "cat" -> listOf(FEEDING_FOODS[2], FEEDING_FOODS[3]) // fish, milk
            else -> listOf(FEEDING_FOODS[4], FEEDING_FOODS[5])  // carrot, apple
        }
        return favorites.random()
    }

    // Initialize favorite clouds
    LaunchedEffect(selectedPetId) {
        if (!wantFoodMap.containsKey(selectedPetId)) {
            wantFoodMap[selectedPetId] = getFavoriteFoodForPet(selectedPetId)
        }
    }

    val currentWantFood = wantFoodMap[selectedPetId] ?: FEEDING_FOODS[0]

    // 3. Drag and Drop Tracking States
    var draggedFoodIndex by remember { mutableStateOf(-1) }
    var draggedFoodType by remember { mutableStateOf("") }
    var draggedFoodEmoji by remember { mutableStateOf("") }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    // Auto-flight animation offset for quick taps
    val autoFlightOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var isAutoFlying by remember { mutableStateOf(false) }

    // Coordinates of mouth & food items for collision and flying
    var mouthCenterScreen by remember { mutableStateOf(Offset.Zero) }
    val foodSlotPositions = remember { mutableStateMapOf<Int, Offset>() }

    // Consecutive declines tracker for tutorial pointer
    var consecutiveDeclines by remember { mutableIntStateOf(0) }
    var lastDeclineTime by remember { mutableStateOf(0L) }
    var showTutorialHint by remember { mutableStateOf(false) }

    // Particles list
    val particles = remember { mutableStateListOf<RewardParticle>() }
    var isConfettiActive by remember { mutableStateOf(false) }

    // Idle timer to show HintHand after 6 seconds of inactivity
    var lastUserActionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var showIdleHint by remember { mutableStateOf(false) }

    LaunchedEffect(lastUserActionTime) {
        showIdleHint = false
        delay(6000)
        showIdleHint = true
    }

    // Reset progress callback
    val onReset = {
        satietyMap["dog"] = 0
        satietyMap["cat"] = 0
        satietyMap["bunny"] = 0
        wantFoodMap["dog"] = getFavoriteFoodForPet("dog")
        wantFoodMap["cat"] = getFavoriteFoodForPet("cat")
        wantFoodMap["bunny"] = getFavoriteFoodForPet("bunny")
        petState = PetState.IDLE
        consecutiveDeclines = 0
        showTutorialHint = false
    }

    // Particles animation loop
    if (particles.isNotEmpty()) {
        LaunchedEffect(particles.size) {
            val stepTime = 16L
            while (particles.isNotEmpty()) {
                delay(stepTime)
                for (i in particles.indices.reversed()) {
                    val p = particles.getOrNull(i) ?: continue
                    val newY = p.startY + p.velocityY
                    val newX = p.startX + p.velocityX
                    // Apply drag and gravity
                    if (newY > 1500f || newX < -500f || newX > 1500f) {
                        particles.removeAt(i)
                    } else {
                        particles[i] = p.copy(
                            startX = newX,
                            startY = newY,
                            velocityY = p.velocityY + 0.6f // Gravity effect
                        )
                    }
                }
            }
        }
    }

    // Check if food type matches pet's favorite
    fun isFavoriteFood(petId: String, foodType: String): Boolean {
        return when (petId) {
            "dog" -> foodType == "bone" || foodType == "meat"
            "cat" -> foodType == "fish" || foodType == "milk"
            else -> foodType == "carrot" || foodType == "apple"
        }
    }

    // Perform actual feeding logic
    fun executeFeeding(foodEmoji: String, foodType: String) {
        lastUserActionTime = System.currentTimeMillis()
        val isFavorite = if (!ageProfile.allowRefusal) true else isFavoriteFood(selectedPetId, foodType)

        if (isFavorite) {
            consecutiveDeclines = 0
            showTutorialHint = false
            coroutineScope.launch {
                // Phase 1: Eat in
                petState = PetState.MOUTH_OPEN
                soundManager.play("eat_in")
                delay(250)

                // Phase 2: Chewing
                petState = PetState.CHEWING
                for (i in 1..3) {
                    soundManager.play("chew")
                    delay(200)
                }

                // Phase 3: Happy and hearts reward
                petState = PetState.HAPPY
                val currentSatiety = satietyMap[selectedPetId] ?: 0
                val nextSatiety = (currentSatiety + 1).coerceAtMost(3)
                satietyMap[selectedPetId] = nextSatiety

                // Emit happy reward particles
                soundManager.play("yum")
                val playedHappy = soundManager.play("animal_${selectedPetId}_happy")
                if (!playedHappy) {
                    soundManager.play("animal_${selectedPetId}")
                }
                haptics.vibrate(30)

                repeat(15) {
                    particles.add(
                        RewardParticle(
                            id = Random.nextLong(),
                            emoji = if (Random.nextBoolean()) "❤️" else "⭐️",
                            startX = mouthCenterScreen.x,
                            startY = mouthCenterScreen.y,
                            velocityX = Random.nextFloat() * 14f - 7f,
                            velocityY = Random.nextFloat() * -16f - 5f
                        )
                    )
                }

                delay(400)
                // Update favorite cloud
                wantFoodMap[selectedPetId] = getFavoriteFoodForPet(selectedPetId)

                // Check Satiety limit of 3
                if (nextSatiety >= 3) {
                    petState = PetState.FULL
                    isConfettiActive = true
                    soundManager.play("fanfare_short")
                    delay(2000)
                    // Reset satiety and start over!
                    satietyMap[selectedPetId] = 0
                    isConfettiActive = false
                }

                delay(400)
                petState = PetState.IDLE
            }
        } else {
            // Unfavorite food: Soft refusal and bounce back
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastDeclineTime < 15000L) {
                consecutiveDeclines++
            } else {
                consecutiveDeclines = 1
            }
            lastDeclineTime = currentTime

            if (consecutiveDeclines >= 2) {
                showTutorialHint = true
            }

            coroutineScope.launch {
                petState = PetState.REFUSE
                soundManager.play("soft_no")
                delay(700)
                petState = PetState.IDLE
            }
        }
    }

    // Automatic flight helper for Tap gesture
    fun startAutoFlight(foodIndex: Int, foodEmoji: String, foodType: String) {
        lastUserActionTime = System.currentTimeMillis()
        val startOffset = foodSlotPositions[foodIndex] ?: Offset.Zero
        val targetOffset = mouthCenterScreen

        coroutineScope.launch {
            isAutoFlying = true
            draggedFoodEmoji = foodEmoji
            autoFlightOffset.snapTo(startOffset)
            autoFlightOffset.animateTo(
                targetOffset,
                tween(400, easing = FastOutSlowInEasing)
            )
            isAutoFlying = false
            executeFeeding(foodEmoji, foodType)
        }
    }

    val farmDecors = remember {
        listOf(
            DecorSpec(DecorKind.SUN, 0.88f, 0.08f, 76f),
            DecorSpec(DecorKind.CLOUD, 0.20f, 0.10f, 84f),
            DecorSpec(DecorKind.CLOUD, 0.62f, 0.14f, 76f),
            DecorSpec(DecorKind.BUTTERFLY, 0.12f, 0.28f, 64f),
            DecorSpec(DecorKind.BIRD, 0.86f, 0.32f, 64f),
            DecorSpec(DecorKind.TREE, 0.08f, 0.48f, 84f),
            DecorSpec(DecorKind.BEE, 0.90f, 0.48f, 60f),
            DecorSpec(DecorKind.FLOWER, 0.14f, 0.70f, 60f),
            DecorSpec(DecorKind.FLOWER, 0.86f, 0.70f, 60f),
            DecorSpec(DecorKind.MUSHROOM, 0.92f, 0.60f, 56f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Vector 3-Layer Scene Background: FARM
        SceneBackground(theme = SceneTheme.FARM)

        // 2. Interactive harmless decorations layer
        DecorLayer(items = farmDecors)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row: Pet selectors (Hidden for 2yo)
            if (!ageProfile.isToddler2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Invisible placeholder to align correctly
                    Spacer(modifier = Modifier.width(72.dp))

                    // 🐶 🐱 🐰 Round selectors (72dp) with thick brown borders
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        pets.forEach { (id, emoji) ->
                            val isSelected = selectedPetId == id
                            val scale by animateFloatAsState(
                                targetValue = if (isSelected) 1.15f else 1.0f,
                                animationSpec = spring(Spring.DampingRatioMediumBouncy),
                                label = "petScale"
                            )

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(72.dp)
                                    .scale(scale)
                                    .border(
                                        border = BorderStroke(
                                            width = if (isSelected) 4.dp else 2.dp,
                                            color = if (isSelected) ColorPink else ColorInk.copy(alpha = 0.4f)
                                        ),
                                        shape = CircleShape
                                    )
                                    .background(
                                        color = if (isSelected) ColorWhite else ColorBgSoft,
                                        shape = CircleShape
                                    )
                                    .clip(CircleShape)
                                    .plasticineClickable(haptics = haptics) {
                                        if (selectedPetId != id) {
                                            selectedPetId = id
                                            soundManager.play("pop")
                                            soundManager.play("animal_$id")
                                            petState = PetState.IDLE
                                            consecutiveDeclines = 0
                                            showTutorialHint = false
                                        }
                                    }
                            ) {
                                Text(text = emoji, fontSize = 38.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(72.dp))
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Want cloud bubble (shows loved emoji) - Hidden for 2yo
            Box(
                modifier = Modifier
                    .height(if (ageProfile.isToddler2) 20.dp else 60.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val currentSatiety = satietyMap[selectedPetId] ?: 0
                if (!ageProfile.isToddler2 && currentSatiety < 3 && petState != PetState.HAPPY && petState != PetState.FULL) {
                    Row(
                        modifier = Modifier
                            .background(ColorWhite, CircleShape)
                            .border(BorderStroke(2.5.dp, ColorInk), CircleShape)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "хочу:",
                            style = PlayTypography.labelMedium,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = ColorInk
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = currentWantFood.emoji, fontSize = 24.sp)
                    }
                }
            }

            // Interactive Morphing Pet Face with Polka-Dot Rug & Toys (takes 45-50% height)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { layoutCoordinates ->
                        val bounds = layoutCoordinates.localToWindow(Offset.Zero)
                        mouthCenterScreen = Offset(
                            bounds.x + layoutCoordinates.size.width / 2f,
                            bounds.y + layoutCoordinates.size.height / 1.7f
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Round Polka Dot Rug under the pet (Pink with white dots)
                Canvas(
                    modifier = Modifier
                        .size(width = 280.dp, height = 150.dp)
                        .align(Alignment.Center)
                ) {
                    val rugW = size.width
                    val rugH = size.height
                    // Soft shadow
                    drawOval(
                        color = Color(0x33000000),
                        topLeft = Offset(8.dp.toPx(), 14.dp.toPx()),
                        size = Size(rugW - 16.dp.toPx(), rugH - 16.dp.toPx())
                    )
                    // Pink base
                    drawOval(
                        color = Color(0xFFFFB6C9),
                        topLeft = Offset(0f, 0f),
                        size = Size(rugW, rugH)
                    )
                    drawOval(
                        color = ColorInk,
                        topLeft = Offset(0f, 0f),
                        size = Size(rugW, rugH),
                        style = Stroke(width = 3.dp.toPx())
                    )
                    // White polka dots
                    val dotR = 6.dp.toPx()
                    val dots = listOf(
                        Offset(rugW * 0.18f, rugH * 0.35f),
                        Offset(rugW * 0.82f, rugH * 0.35f),
                        Offset(rugW * 0.15f, rugH * 0.65f),
                        Offset(rugW * 0.85f, rugH * 0.65f),
                        Offset(rugW * 0.32f, rugH * 0.82f),
                        Offset(rugW * 0.68f, rugH * 0.82f),
                        Offset(rugW * 0.50f, rugH * 0.88f)
                    )
                    dots.forEach { dotPos ->
                        drawCircle(color = Color.White, radius = dotR, center = dotPos)
                    }
                }

                // Interactive Toys on the Rug
                val petToys = remember(selectedPetId, ageProfile.isToddler2) {
                    val all = when (selectedPetId) {
                        "dog" -> listOf("🎾" to "decor_ball", "🦴" to "decor_ball", "🔔" to "bell")
                        "cat" -> listOf("🧶" to "decor_yarn", "🐟" to "decor_fish", "🔔" to "bell")
                        else -> listOf("🥕" to "decor_plant", "🎾" to "decor_ball", "🔔" to "bell")
                    }
                    if (ageProfile.isToddler2) all.take(1) else all
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    petToys.forEach { (toyEmoji, toySound) ->
                        val toyScale = remember { Animatable(1.0f) }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(54.dp)
                                .scale(toyScale.value)
                                .clip(CircleShape)
                                .background(ColorWhite.copy(alpha = 0.95f))
                                .border(BorderStroke(2.5.dp, ColorInk), CircleShape)
                                .clickable {
                                    soundManager.play(toySound)
                                    soundManager.play("animal_$selectedPetId")
                                    haptics.vibrateShort()
                                    coroutineScope.launch {
                                        toyScale.animateTo(1.35f, spring(Spring.DampingRatioMediumBouncy))
                                        toyScale.animateTo(1.0f)
                                    }
                                    coroutineScope.launch {
                                        petState = PetState.HAPPY
                                        delay(1200)
                                        if (petState == PetState.HAPPY) petState = PetState.IDLE
                                    }
                                }
                        ) {
                            Text(toyEmoji, fontSize = 28.sp)
                        }
                    }
                }

                // Interactive visual representation of Morphing Pet faces (Dog, Cat, Bunny)
                InteractivePetFace(
                    petId = selectedPetId,
                    state = petState,
                    isFoodOverMouth = isDragging && hypot(dragOffset.x - mouthCenterScreen.x, dragOffset.y - mouthCenterScreen.y) < 180f
                )
            }

            // Heart satiety index (3 hearts, 48dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentSatiety = satietyMap[selectedPetId] ?: 0
                for (i in 1..3) {
                    val isFilled = i <= currentSatiety
                    val heartScale by animateFloatAsState(
                        targetValue = if (isFilled) 1.25f else 1.0f,
                        animationSpec = spring(Spring.DampingRatioHighBouncy),
                        label = "heartScale"
                    )

                    Text(
                        text = if (isFilled) "💖" else "🩶",
                        fontSize = 38.sp,
                        modifier = Modifier
                            .scale(heartScale)
                            .padding(horizontal = 6.dp)
                    )
                }
            }

            // Food tray panel (static, 120dp, bgSoft, always in place)
            Surface(
                shape = ShapeCard,
                color = ColorBgSoft,
                border = BorderStroke(3.dp, ColorInk),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .padding(bottom = 4.dp)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val foodCount = if (ageProfile.isToddler2) 3 else 4
                    val calculatedSizeDp = ((maxWidth.value - 24 - (foodCount - 1) * 10) / foodCount).coerceIn(72f, 96f).dp
                    val emojiFontSize = (calculatedSizeDp.value * 0.52f).sp

                    // Determine 4 products (2 favorite + 2 other) or 3 products for 2yo
                    val favs = when (selectedPetId) {
                        "dog" -> listOf(FEEDING_FOODS[0], FEEDING_FOODS[1]) // bone, meat
                        "cat" -> listOf(FEEDING_FOODS[2], FEEDING_FOODS[3]) // fish, milk
                        else -> listOf(FEEDING_FOODS[4], FEEDING_FOODS[5])  // carrot, apple
                    }
                    val others = FEEDING_FOODS.filter { it !in favs }
                    val displayedFoods = remember(selectedPetId, ageProfile.isToddler2) {
                        if (ageProfile.isToddler2) {
                            (favs + others.take(1)).take(3)
                        } else {
                            (favs + others.take(2)).take(4)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        displayedFoods.forEachIndexed { index, food ->
                            val isBeingDragged = isDragging && draggedFoodIndex == index
                            val opacity = if (isBeingDragged) 0.35f else 1.0f

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(calculatedSizeDp)
                                    .onGloballyPositioned { coords ->
                                        val positionInWindow = coords.localToWindow(Offset.Zero)
                                        foodSlotPositions[index] = Offset(
                                            positionInWindow.x + coords.size.width / 2f,
                                            positionInWindow.y + coords.size.height / 2f
                                        )
                                    }
                                    .graphicsLayer { alpha = opacity }
                                    .border(BorderStroke(2.8.dp, ColorInk), CircleShape)
                                    .clip(CircleShape)
                                    .background(getFeedingPlateColor(food.type), CircleShape)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                soundManager.play("tap")
                                                haptics.vibrateShort()
                                                startAutoFlight(index, food.emoji, food.type)
                                            }
                                        )
                                    }
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { startPos ->
                                                draggedFoodIndex = index
                                                draggedFoodType = food.type
                                                draggedFoodEmoji = food.emoji
                                                isDragging = true
                                                soundManager.play("pickup")
                                                haptics.vibrateShort()
                                                
                                                val startWindowOffset = foodSlotPositions[index] ?: Offset.Zero
                                                dragOffset = startWindowOffset
                                            },
                                            onDrag = { change, dragAmount ->
                                                dragOffset += Offset(dragAmount.x, dragAmount.y)
                                            },
                                            onDragEnd = {
                                                isDragging = false
                                                val distanceToMouth = hypot(
                                                    dragOffset.x - mouthCenterScreen.x,
                                                    dragOffset.y - mouthCenterScreen.y
                                                )
                                                // Soft tolerance area: 120% of zone
                                                if (distanceToMouth < 240f) {
                                                    executeFeeding(draggedFoodEmoji, draggedFoodType)
                                                } else {
                                                    draggedFoodIndex = -1
                                                }
                                            },
                                            onDragCancel = {
                                                isDragging = false
                                                draggedFoodIndex = -1
                                            }
                                        )
                                    }
                            ) {
                                Text(text = food.emoji, fontSize = emojiFontSize)
                            }
                        }
                    }
                }
            }
        }

        // 👻 Active Ghost food following finger or flying
        if (isDragging) {
            val tiltAngle = if (dragOffset.x > mouthCenterScreen.x) 3f else -3f
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (dragOffset.x - 44.dp.toPx()).roundToInt(),
                            y = (dragOffset.y - 44.dp.toPx() - 24.dp.toPx()).roundToInt() // Offset 24dp above finger
                        )
                    }
                    .size(88.dp)
                    .scale(1.25f)
                    .rotate(tiltAngle)
                    .background(ColorWhite.copy(alpha = 0.9f), CircleShape)
                    .border(BorderStroke(2.5.dp, ColorInk), CircleShape)
            ) {
                Text(text = draggedFoodEmoji, fontSize = 42.sp)
            }
        }

        // Ghost during Auto-Flight
        if (isAutoFlying) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (autoFlightOffset.value.x - 44.dp.toPx()).roundToInt(),
                            y = (autoFlightOffset.value.y - 44.dp.toPx()).roundToInt()
                        )
                    }
                    .size(88.dp)
                    .scale(1.25f)
                    .background(ColorWhite.copy(alpha = 0.9f), CircleShape)
                    .border(BorderStroke(2.5.dp, ColorInk), CircleShape)
            ) {
                Text(text = draggedFoodEmoji, fontSize = 42.sp)
            }
        }

        // Confetti Canvas for 3-heart satiety celebration!
        if (isConfettiActive) {
            ConfettiSplashLayer()
        }

        // Particle Burst Canvas
        particles.forEach { p ->
            Box(
                modifier = Modifier.offset {
                    IntOffset(p.startX.roundToInt(), p.startY.roundToInt())
                }
            ) {
                Text(text = p.emoji, fontSize = 24.sp)
            }
        }

        // 🖐️ Animated guide hand hint (Idle hint or successive mistakes)
        if (showIdleHint || showTutorialHint) {
            val targetFood = FEEDING_FOODS.firstOrNull { it.type == currentWantFood.type }
            val targetIndex = FEEDING_FOODS.indexOf(targetFood).coerceAtLeast(0)
            val startOffset = foodSlotPositions[targetIndex] ?: Offset(150f, 650f)

            HintHand(
                from = Offset(startOffset.x / 3f, startOffset.y / 3f),
                to = Offset(mouthCenterScreen.x / 3f, mouthCenterScreen.y / 3f),
                mode = "drag",
                delayMs = 100
            )
        }
    }
}

// Complete beautiful interactive morphing face for Puppy, Kitty, Bunny
@Composable
fun InteractivePetFace(
    petId: String,
    state: PetState,
    isFoodOverMouth: Boolean
) {
    val scaleAnim by animateFloatAsState(
        targetValue = when (state) {
            PetState.MOUTH_OPEN -> 1.05f
            PetState.CHEWING -> 1.03f
            PetState.HAPPY -> 1.12f
            PetState.REFUSE -> 0.94f
            else -> 1.0f
        },
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "petFaceScale"
    )

    val rotationAnim by animateFloatAsState(
        targetValue = when (state) {
            PetState.REFUSE -> 12f
            else -> 0f
        },
        animationSpec = spring(Spring.DampingRatioHighBouncy),
        label = "petFaceRotation"
    )

    val faceColor = when (petId) {
        "dog" -> Color(0xFFFFCC80) // Soft Dog Brown
        "cat" -> Color(0xFFEEEEEE) // Kitten Grey
        else -> Color(0xFFFFF176)  // Bunny Soft Yellow
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(240.dp)
            .scale(scaleAnim)
            .rotate(rotationAnim)
    ) {
        // EARS (Specific to pet type with animation twitches)
        EarsLayout(petId = petId, state = state)

        // Main Head Shape
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(BorderStroke(4.dp, ColorInk), CircleShape)
                .background(faceColor, CircleShape)
        ) {
            // Rosy cheeks during happy states
            if (state == PetState.HAPPY || state == PetState.FULL) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 32.dp, top = 40.dp)
                        .size(36.dp)
                        .background(ColorPink.copy(alpha = 0.6f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 32.dp, top = 40.dp)
                        .size(36.dp)
                        .background(ColorPink.copy(alpha = 0.6f), CircleShape)
                )
            }

            // Eyes layout (open, happy, blinking or squished)
            EyesLayout(state = state)

            // Nose
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 12.dp)
                    .size(22.dp, 16.dp)
                    .background(ColorInk, CircleShape)
            )

            // Morphing mouth
            MouthLayout(state = state, isFoodOverMouth = isFoodOverMouth)
        }
    }
}

@Composable
fun EarsLayout(petId: String, state: PetState) {
    val twitchAnim by animateFloatAsState(
        targetValue = if (state == PetState.WAITING) -10f else 0f,
        animationSpec = spring(Spring.DampingRatioHighBouncy),
        label = "earsTwitch"
    )

    when (petId) {
        "dog" -> {
            // Floppy puppy ears hanging down
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-16).dp, y = 20.dp)
                        .size(45.dp, 110.dp)
                        .rotate(15f + twitchAnim)
                        .border(BorderStroke(4.dp, ColorInk), RoundedCornerShape(24.dp))
                        .background(Color(0xFF8D6E63), RoundedCornerShape(24.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 16.dp, y = 20.dp)
                        .size(45.dp, 110.dp)
                        .rotate(-15f - twitchAnim)
                        .border(BorderStroke(4.dp, ColorInk), RoundedCornerShape(24.dp))
                        .background(Color(0xFF8D6E63), RoundedCornerShape(24.dp))
                )
            }
        }
        "cat" -> {
            // Triangular kitty ears
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 14.dp, y = (-20).dp)
                        .size(54.dp, 64.dp)
                        .rotate(-12f + twitchAnim)
                        .border(BorderStroke(4.dp, ColorInk), RoundedCornerShape(12.dp))
                        .background(Color(0xFFBDBDBD), RoundedCornerShape(12.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-14).dp, y = (-20).dp)
                        .size(54.dp, 64.dp)
                        .rotate(12f - twitchAnim)
                        .border(BorderStroke(4.dp, ColorInk), RoundedCornerShape(12.dp))
                        .background(Color(0xFFBDBDBD), RoundedCornerShape(12.dp))
                )
            }
        }
        else -> {
            // Long Bunny ears upright
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 34.dp, y = (-100).dp)
                        .size(38.dp, 120.dp)
                        .rotate(-8f + twitchAnim)
                        .border(BorderStroke(4.dp, ColorInk), RoundedCornerShape(20.dp))
                        .background(ColorWhite, RoundedCornerShape(20.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-34).dp, y = (-100).dp)
                        .size(38.dp, 120.dp)
                        .rotate(8f - twitchAnim)
                        .border(BorderStroke(4.dp, ColorInk), RoundedCornerShape(20.dp))
                        .background(ColorWhite, RoundedCornerShape(20.dp))
                )
            }
        }
    }
}

@Composable
fun EyesLayout(state: PetState) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (state) {
            PetState.HAPPY, PetState.FULL -> {
                // Curved happy eye arches ^^
                Text(
                    text = "^  ^",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = ColorInk,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 46.dp)
                )
            }
            PetState.REFUSE -> {
                // squinted closed eyes ><
                Text(
                    text = ">  <",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = ColorInk,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 46.dp)
                )
            }
            else -> {
                // Friendly round open eyes
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 54.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(ColorInk, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(ColorInk, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun MouthLayout(state: PetState, isFoodOverMouth: Boolean) {
    val mouthScaleY = remember { Animatable(1.0f) }

    if (state == PetState.CHEWING) {
        val infiniteTransition = rememberInfiniteTransition(label = "chewing")
        val animateChew by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(180, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "chewHeight"
        )
        LaunchedEffect(animateChew) {
            mouthScaleY.snapTo(animateChew)
        }
    } else {
        LaunchedEffect(state) {
            mouthScaleY.animateTo(1.0f)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val showOpenMouth = state == PetState.MOUTH_OPEN || isFoodOverMouth

        if (showOpenMouth) {
            // Large glowing round open mouth (for feeding)
            Box(
                modifier = Modifier
                    .padding(bottom = 32.dp)
                    .size(64.dp)
                    .border(BorderStroke(4.dp, ColorYellow), CircleShape)
                    .background(Color(0xFF42221D), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Cute pink tongue
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(34.dp, 18.dp)
                        .background(ColorPink, RoundedCornerShape(12.dp))
                )
            }
        } else {
            // Friendly warm smile (adjustable scaleY for chewing)
            Box(
                modifier = Modifier
                    .padding(bottom = 44.dp)
                    .size(40.dp, 20.dp)
                    .graphicsLayer { scaleY = mouthScaleY.value }
                    .border(BorderStroke(3.dp, ColorInk), RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                    .background(Color.Transparent)
            )
        }
    }
}

@Composable
fun ConfettiSplashLayer() {
    val items = remember { List(25) { Random.nextInt(360) } }
    Canvas(modifier = Modifier.fillMaxSize()) {
        items.forEachIndexed { idx, angle ->
            val radian = angle * Math.PI / 180f
            val distance = 300f * (idx % 3 + 1) / 3f
            val x = (size.width / 2f) + (distance * Math.cos(radian)).toFloat()
            val y = (size.height / 3f) + (distance * Math.sin(radian)).toFloat()

            drawCircle(
                color = when (idx % 4) {
                    0 -> ColorPink
                    1 -> ColorYellow
                    2 -> ColorBlue
                    else -> ColorGreen
                },
                radius = 12f,
                center = Offset(x, y)
            )
        }
    }
}
