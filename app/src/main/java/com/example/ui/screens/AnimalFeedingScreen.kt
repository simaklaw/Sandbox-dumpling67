package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import com.example.shared.i18n.Text
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
    "icecream" -> Color(0xFFFFC1DC)
    "banana" -> Color(0xFFFFF59D)
    "cookie" -> Color(0xFFFFCC80)
    "cheese" -> Color(0xFFFFF176)
    else -> Color(0xFFFFE082)
}

// Food model
data class FoodData(
    val emoji: String,
    val type: String
)

/** Нарисованная иконка еды (по эмодзи-ключу); если картинки нет — показываем эмодзи. */
private val FOOD_ICONS = mapOf(
    "🦴" to com.example.R.drawable.food_bone,
    "🥩" to com.example.R.drawable.food_meat,
    "🐟" to com.example.R.drawable.food_fish,
    "🥛" to com.example.R.drawable.food_milk,
    "🥕" to com.example.R.drawable.food_carrot,
    "🍎" to com.example.R.drawable.food_apple,
    "🍦" to com.example.R.drawable.food_icecream,
    "🍌" to com.example.R.drawable.food_banana,
    "🍪" to com.example.R.drawable.food_cookie,
    "🧀" to com.example.R.drawable.food_cheese
)

@Composable
private fun FoodIcon(emoji: String, size: androidx.compose.ui.unit.Dp) {
    val res = FOOD_ICONS[emoji]
    if (res != null) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(res),
            contentDescription = null,
            modifier = Modifier.size(size)
        )
    } else Text(text = emoji, fontSize = (size.value * 0.8f).sp)
}

val FEEDING_FOODS = listOf(
    FoodData("🦴", "bone"),
    FoodData("🥩", "meat"),
    FoodData("🐟", "fish"),
    FoodData("🥛", "milk"),
    FoodData("🥕", "carrot"),
    FoodData("🍎", "apple"),
    FoodData("🍦", "icecream"),
    FoodData("🍌", "banana"),
    FoodData("🍪", "cookie"),
    FoodData("🧀", "cheese")
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
    val pets = remember { listOf("dog" to "🐶", "cat" to "🐱", "mouse" to "🐭") }
    var selectedPetId by remember { mutableStateOf("dog") }

    // Satiety map for puppy, kitty, piglet (0 to 3)
    val satietyMap = remember { mutableStateMapOf("dog" to 0, "cat" to 0, "mouse" to 0) }

    // Active state machine for the selected pet
    var petState by remember { mutableStateOf(PetState.IDLE) }

    // 2. Active "Want" cloud food target state
    val wantFoodMap = remember { mutableStateMapOf<String, FoodData>() }
    
    // Choose favorite food for pet
    fun getFavoriteFoodForPet(petId: String): FoodData {
        val favorites = when (petId) {
            "dog" -> listOf(FEEDING_FOODS[0], FEEDING_FOODS[1], FEEDING_FOODS[8]) // bone, meat, cookie
            "cat" -> listOf(FEEDING_FOODS[2], FEEDING_FOODS[3], FEEDING_FOODS[6]) // fish, milk, ice cream
            else -> listOf(FEEDING_FOODS[9], FEEDING_FOODS[4], FEEDING_FOODS[5])  // cheese, carrot, apple
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
    val foodSlotTypes = remember { mutableStateMapOf<String, Int>() }

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
        satietyMap["mouse"] = 0
        wantFoodMap["dog"] = getFavoriteFoodForPet("dog")
        wantFoodMap["cat"] = getFavoriteFoodForPet("cat")
        wantFoodMap["mouse"] = getFavoriteFoodForPet("mouse")
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
            "dog" -> foodType == "bone" || foodType == "meat" || foodType == "cookie"
            "cat" -> foodType == "fish" || foodType == "milk" || foodType == "icecream"
            else -> foodType == "cheese" || foodType == "carrot" || foodType == "apple"
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
                // Фаза 1: рот открывается
                petState = PetState.MOUTH_OPEN
                delay(260)

                // Фаза 2: жуём — звук зависит от еды
                petState = PetState.CHEWING
                val chewMs: Long
                when (foodType) {
                    "milk", "icecream" -> { soundManager.play("pet_lap", volume = 0.9f); chewMs = 880 }
                    "bone", "carrot", "apple", "cookie" -> { soundManager.play("pet_crunch_${Random.nextInt(1, 4)}", volume = 1f); chewMs = 620 }
                    else -> { soundManager.play("pet_munch_${Random.nextInt(1, 3)}", volume = 1f); chewMs = 640 }
                }
                delay(chewMs)
                soundManager.play("pet_gulp", volume = 0.8f)
                delay(180)

                // Фаза 3: довольное животное отвечает голосом
                petState = PetState.HAPPY
                val currentSatiety = satietyMap[selectedPetId] ?: 0
                val nextSatiety = (currentSatiety + 1).coerceAtMost(3)
                satietyMap[selectedPetId] = nextSatiety
                playPetVoice(soundManager, selectedPetId)
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
                                            playPetVoice(soundManager, id)
                                            petState = PetState.IDLE
                                            consecutiveDeclines = 0
                                            showTutorialHint = false
                                        }
                                    }
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(petRes(id)),
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    alignment = Alignment.TopCenter,
                                    modifier = Modifier.fillMaxSize().padding(6.dp).clip(CircleShape)
                                )
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
                        FoodIcon(currentWantFood.emoji, 30.dp)
                    }
                }
            }

            // Питомец (картинка) на коврике; рот и анимации рисуются поверх
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Canvas(
                    modifier = Modifier
                        .size(width = 300.dp, height = 110.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    val rugW = size.width
                    val rugH = size.height
                    drawOval(color = Color(0x33000000), topLeft = Offset(8.dp.toPx(), 12.dp.toPx()), size = Size(rugW - 16.dp.toPx(), rugH - 14.dp.toPx()))
                    drawOval(color = Color(0xFFFFB6C9), topLeft = Offset(0f, 0f), size = Size(rugW, rugH - 8.dp.toPx()))
                    drawOval(color = ColorInk, topLeft = Offset(0f, 0f), size = Size(rugW, rugH - 8.dp.toPx()), style = Stroke(width = 3.dp.toPx()))
                    listOf(0.18f to 0.35f, 0.82f to 0.35f, 0.15f to 0.65f, 0.85f to 0.65f, 0.32f to 0.82f, 0.68f to 0.82f)
                        .forEach { (fx, fy) -> drawCircle(Color.White, 6.dp.toPx(), Offset(rugW * fx, rugH * fy)) }
                }
                PetPortrait(
                    petId = selectedPetId,
                    state = petState,
                    modifier = Modifier
                        .padding(bottom = 26.dp)
                        .height(minOf(maxHeight - 30.dp, 400.dp)),
                    onMouthPos = { mouthCenterScreen = it }
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
                        "dog" -> listOf(FEEDING_FOODS[0], FEEDING_FOODS[1], FEEDING_FOODS[8]) // bone, meat, cookie
                        "cat" -> listOf(FEEDING_FOODS[2], FEEDING_FOODS[3], FEEDING_FOODS[6]) // fish, milk, ice cream
                        else -> listOf(FEEDING_FOODS[9], FEEDING_FOODS[4], FEEDING_FOODS[5])  // cheese, carrot, apple
                    }
                    val others = FEEDING_FOODS.filter { it !in favs }
                    val displayedFoods = remember(selectedPetId, ageProfile.isToddler2) {
                        if (ageProfile.isToddler2) {
                            (favs.shuffled().take(2) + others.shuffled().take(1)).take(3)
                        } else {
                            (favs + others.shuffled().take(1)).take(4)
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

                            // key: при смене питомца слоты не должны наследовать жесты прежнего лакомства
                            key(selectedPetId, food.type) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(calculatedSizeDp)
                                    .onGloballyPositioned { coords ->
                                        foodSlotTypes[food.type] = index
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
                                    .pointerInput(selectedPetId, food.type, index) {
                                        detectTapGestures(
                                            onTap = {
                                                soundManager.play("tap")
                                                haptics.vibrateShort()
                                                startAutoFlight(index, food.emoji, food.type)
                                            }
                                        )
                                    }
                                    .pointerInput(selectedPetId, food.type, index) {
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
                                FoodIcon(food.emoji, (calculatedSizeDp.value * 0.78f).dp)
                            }
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
                FoodIcon(draggedFoodEmoji, 60.dp)
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
                FoodIcon(draggedFoodEmoji, 60.dp)
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
            val targetIndex = foodSlotTypes[currentWantFood.type] ?: 0
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

// ---------------------------------------------------------------------------------------------
// Питомцы-картинки: анимация дыхания, жевания, прыжков и открывающийся рот поверх рисунка.
// ---------------------------------------------------------------------------------------------
private fun petRes(id: String): Int = when (id) {
    "cat" -> com.example.R.drawable.feed_cat
    "dog" -> com.example.R.drawable.feed_dog
    else -> com.example.R.drawable.feed_mouse
}

/** Голос животного: кошка мяукает, собака лает, мышь пищит. */
private fun playPetVoice(sm: SoundManager, id: String) {
    when (id) {
        "mouse" -> sm.play("pet_mouse_${Random.nextInt(1, 4)}", volume = 1f)
        else -> {
            val ok = sm.play("animal_${id}_happy")
            if (!ok) sm.play("animal_$id")
        }
    }
}

// доля положения рта и ширины рта (в долях ширины картинки) для каждой картинки
private fun mouthSpec(id: String): FloatArray = when (id) {
    "cat" -> floatArrayOf(0.457f, 0.468f, 0.085f)
    "dog" -> floatArrayOf(0.420f, 0.478f, 0.115f)
    else -> floatArrayOf(0.461f, 0.546f, 0.062f)
}

@Composable
fun PetPortrait(
    petId: String,
    state: PetState,
    modifier: Modifier = Modifier,
    onMouthPos: (Offset) -> Unit = {}
) {
    val inf = rememberInfiniteTransition(label = "pet")
    val phase by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "phase")
    val chew by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(310, easing = LinearEasing)), label = "chew")
    val hop by animateFloatAsState(
        if (state == PetState.HAPPY || state == PetState.FULL) 1f else 0f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "hop"
    )
    val lean by animateFloatAsState(if (state == PetState.MOUTH_OPEN) 1f else 0f, spring(Spring.DampingRatioMediumBouncy), label = "lean")
    val openBase by animateFloatAsState(
        when (state) { PetState.MOUTH_OPEN -> 1f; PetState.HAPPY, PetState.FULL -> 0.55f; else -> 0f },
        tween(140), label = "open"
    )
    val spec = mouthSpec(petId)
    val twoPi = (2.0 * Math.PI).toFloat()
    val open = if (state == PetState.CHEWING) 0.22f + 0.6f * kotlin.math.abs(kotlin.math.sin(chew * Math.PI.toFloat())) else openBase
    val chewing = state == PetState.CHEWING

    Box(
        modifier = modifier
            .aspectRatio(0.87f)
            .onGloballyPositioned { c ->
                val p = c.localToWindow(Offset.Zero)
                onMouthPos(Offset(p.x + c.size.width * spec[0], p.y + c.size.height * spec[1]))
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.96f)
                    val breath = kotlin.math.sin(phase * twoPi)
                    var sy = 1f + 0.014f * breath
                    var sx = 1f - 0.008f * breath
                    if (chewing) {
                        val c = kotlin.math.sin(chew * twoPi)
                        sy = 1f - 0.035f * c; sx = 1f + 0.025f * c
                    }
                    sx += 0.04f * lean; sy += 0.04f * lean
                    scaleX = sx; scaleY = sy
                    translationY = -hop * size.height * 0.07f * kotlin.math.abs(kotlin.math.sin(phase * twoPi * 2f))
                    rotationZ = when {
                        state == PetState.REFUSE -> 7f * kotlin.math.sin(phase * twoPi * 5f)
                        hop > 0f -> hop * 4f * kotlin.math.sin(phase * twoPi * 2f)
                        else -> 0.8f * kotlin.math.sin(phase * twoPi)
                    }
                }
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(petRes(petId)),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(Modifier.fillMaxSize()) {
                if (open > 0.03f) {
                    val cx = size.width * spec[0]
                    val cy = size.height * spec[1]
                    val mw = size.width * spec[2]
                    val w = mw * (0.55f + 0.45f * open)
                    val h = mw * 0.95f * open
                    val top = cy - h * 0.25f
                    drawOval(Color(0xFF5A0F1E), Offset(cx - w / 2f, top), Size(w, h))
                    // язычок
                    drawOval(Color(0xFFFF8A80), Offset(cx - w * 0.3f, top + h * 0.5f), Size(w * 0.6f, h * 0.5f))
                    drawOval(Color.Black, Offset(cx - w / 2f, top), Size(w, h), style = Stroke(width = size.width * 0.007f))
                }
            }
        }
    }
}
