package com.example.ui.screens

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

enum class PetCharacter(
    val title: String,
    val emoji: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val favoriteTreat: String,
    val treatEmoji: String
) {
    KITTY("Котик Мурзик", "🐱", Color(0xFFFFCC80), Color(0xFFFFA726), "Рыбка", "🐟"),
    PUPPY("Щенок Дружок", "🐶", Color(0xFFFFD54F), Color(0xFFFFB300), "Косточка", "🦴"),
    BUNNY("Зайка Пушок", "🐰", Color(0xFFF8BBD0), Color(0xFFF48FB1), "Морковка", "🥕"),
    BEAR("Мишка Топтыжка", "🐻", Color(0xFFD7CCC8), Color(0xFFBCAAA4), "Мёдик", "🍯"),
    DUMPLING("Пельмешек", "🥟", Color(0xFFFFF9C4), Color(0xFFFFE082), "Печенька", "🍪")
}

data class PetFloatingHeart(
    val id: Long,
    var x: Float,
    var y: Float,
    var vy: Float,
    var vx: Float,
    var size: Float,
    val emoji: String
)

/**
 * PetZone:
 * Soft, cuddly interactive pets that react warmly to toddler touch:
 * stroking (purr/love), tapping (giggles & jumps), feeding treats, dressing up with accessories!
 */
@Composable
fun PetZone(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    modifier: Modifier = Modifier
) {
    PetZoneScreen(repository = repository, soundPlayer = soundPlayer, modifier = modifier)
}

@Composable
fun PetZoneScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    modifier: Modifier = Modifier
) {
    var selectedPet by remember { mutableStateOf(PetCharacter.KITTY) }
    var moodEmoji by remember { mutableStateOf("💖") }
    var expressionState by remember { mutableIntStateOf(0) } // 0: Happy, 1: Wink, 2: Giggle, 3: Love-Eyes
    var selectedHatEmoji by remember { mutableStateOf("🎀") }
    var happinessMeter by remember { mutableFloatStateOf(0.75f) }

    val coroutineScope = rememberCoroutineScope()
    val heartsList = remember { mutableStateListOf<PetFloatingHeart>() }

    // Animation physics states
    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }
    val jumpOffsetY = remember { Animatable(0f) }
    val petRotation = remember { Animatable(0f) }
    val earWiggle = remember { Animatable(0f) }

    // Breathing idle animation
    val infiniteTransition = rememberInfiniteTransition(label = "pet_breath")
    val idleFloat by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_float"
    )
    val tailWag by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tail_wag"
    )

    // Particle loop for floating love hearts
    LaunchedEffect(heartsList.size) {
        while (heartsList.isNotEmpty()) {
            delay(16)
            for (i in heartsList.indices.reversed()) {
                val h = heartsList[i]
                h.y += h.vy
                h.x += h.vx
                h.size -= 0.35f
                if (h.size <= 0f) {
                    heartsList.removeAt(i)
                }
            }
        }
    }

    fun triggerPetJoy(isFeed: Boolean = false, isPetting: Boolean = false) {
        coroutineScope.launch {
            happinessMeter = (happinessMeter + 0.1f).coerceAtMost(1.0f)
            repository.addCoins(1)

            if (isFeed) {
                soundPlayer.vibrateSquish()
                soundPlayer.playEatCrunch()
                soundPlayer.playFanfare()
                moodEmoji = "😋"
            } else if (isPetting) {
                soundPlayer.vibratePurr()
                when (selectedPet) {
                    PetCharacter.KITTY -> soundPlayer.playCatPurr()
                    PetCharacter.PUPPY -> soundPlayer.playDogBark()
                    PetCharacter.BUNNY -> soundPlayer.playBunnyHop()
                    PetCharacter.BEAR -> soundPlayer.playBearGrowl()
                    PetCharacter.DUMPLING -> soundPlayer.playBabyGiggle()
                }
                moodEmoji = "💖"
            } else {
                soundPlayer.vibrateSquishDeform()
                soundPlayer.playTickleLaughter()
                moodEmoji = "✨"
            }

            // High Spring bounce
            jumpOffsetY.animateTo(-65f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
            jumpOffsetY.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
        }

        // Wobble stretch
        coroutineScope.launch {
            scaleX.snapTo(1.25f)
            scaleY.snapTo(0.75f)
            scaleX.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMediumLow))
            scaleY.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMediumLow))
        }

        // Ear & Tail wiggle
        coroutineScope.launch {
            earWiggle.snapTo(18f)
            earWiggle.animateTo(0f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessLow))
        }

        // Spawn heart and sparkle particles
        val particles = listOf("💖", "⭐", "✨", "🌸", "🥰")
        repeat(5) {
            heartsList.add(
                PetFloatingHeart(
                    id = System.nanoTime() + it,
                    x = (Random.nextFloat() - 0.5f) * 160f,
                    y = (Random.nextFloat() - 0.5f) * 140f,
                    vy = -(Random.nextFloat() * 4f + 3f),
                    vx = (Random.nextFloat() - 0.5f) * 6f,
                    size = Random.nextFloat() * 8f + 16f,
                    emoji = particles.random()
                )
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFF8E1),
                        Color(0xFFFFECB3),
                        Color(0xFFFFE082)
                    )
                )
            )
            .testTag("pet_zone_screen")
    ) {
        // Decorative background clouds and soft circles
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.45f),
                radius = size.width * 0.45f,
                center = Offset(size.width * 0.5f, size.height * 0.46f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP BAR: Select Pet Character
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.92f),
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PetCharacter.values().forEach { pet ->
                        val isSelected = selectedPet == pet
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isSelected) Color(0xFFFFB300) else Color(0xFFFFF8E1)
                                )
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color(0xFFFF6F00) else Color(0xFFFFE082),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    selectedPet = pet
                                    expressionState = 0
                                    soundPlayer.playAnimalSound(pet.name)
                                    soundPlayer.triggerLightTapPulse()
                                    triggerPetJoy(isPetting = true)
                                }
                                .testTag("pet_select_${pet.name}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pet.emoji,
                                fontSize = if (isSelected) 30.sp else 24.sp
                            )
                        }
                    }
                }
            }

            // MIDDLE: Interactive Pet Avatar with Petting & Tickling gestures
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Floating Heart Particles
                heartsList.forEach { heart ->
                    Text(
                        text = heart.emoji,
                        fontSize = (heart.size * 1.8f).sp,
                        modifier = Modifier.offset {
                            IntOffset(heart.x.roundToInt(), heart.y.roundToInt())
                        }
                    )
                }

                // Main Pet Interactive Body
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(0, (jumpOffsetY.value + idleFloat).roundToInt())
                        }
                        .scale(scaleX.value, scaleY.value)
                        .rotate(petRotation.value)
                        .size(270.dp)
                        .pointerInput(selectedPet) {
                            // Tap to tickle or pet
                            detectTapGestures(
                                onPress = {
                                    expressionState = (expressionState + 1) % 4
                                    soundPlayer.triggerLightTapPulse()
                                    tryAwaitRelease()
                                    triggerPetJoy(isPetting = false)
                                }
                            )
                        }
                        .pointerInput(selectedPet) {
                            // Drag to stroke/pet gently
                            detectDragGestures(
                                onDrag = { change, _ ->
                                    change.consume()
                                    if (Random.nextFloat() < 0.15f) {
                                        triggerPetJoy(isPetting = true)
                                    }
                                }
                            )
                        }
                        .testTag("pet_hero_character"),
                    contentAlignment = Alignment.Center
                ) {
                    // Custom Vector Character Painting
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawPetCharacter(
                            pet = selectedPet,
                            expression = expressionState,
                            earWiggleAngle = earWiggle.value,
                            tailWagAngle = tailWag
                        )
                    }

                    // Cute Hat Accessory
                    if (selectedHatEmoji.isNotEmpty()) {
                        Text(
                            text = selectedHatEmoji,
                            fontSize = 60.sp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-30).dp)
                        )
                    }

                    // Speech bubble with mood emoji
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 10.dp, y = (-10).dp),
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = moodEmoji,
                            fontSize = 24.sp,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }
            }

            // BOTTOM: Care Actions (Treats & Hats)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Treats Bar (Feed the Pet)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.95f),
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🍖", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Угостить:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF4E342E)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val treats = listOf(
                                Pair("🐟", "Рыбка"),
                                Pair("🦴", "Косточка"),
                                Pair("🥕", "Морковка"),
                                Pair("🍯", "Мёд"),
                                Pair("🍪", "Печенька")
                            )
                            treats.forEach { (emoji, _) ->
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFF3E0))
                                        .border(1.5.dp, Color(0xFFFFB74D), CircleShape)
                                        .clickable {
                                            triggerPetJoy(isFeed = true)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }

                // Hats & Outfits Bar
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.95f),
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎀", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Нарядить:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF4E342E)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val hats = listOf("🎀", "👑", "🎩", "🌸", "🕶️", "❌")
                            hats.forEach { hat ->
                                val isSelected = selectedHatEmoji == hat || (hat == "❌" && selectedHatEmoji.isEmpty())
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color(0xFFFFCC80) else Color(0xFFFFF8E1))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) Color(0xFFE65100) else Color(0xFFFFE082),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            selectedHatEmoji = if (hat == "❌") "" else hat
                                            soundPlayer.playSqueak()
                                            soundPlayer.triggerLightTapPulse()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(hat, fontSize = 18.sp)
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
 * Draws high-quality cute vectors for each pet character with animated ears, tail, and rosy cheeks.
 */
private fun DrawScope.drawPetCharacter(
    pet: PetCharacter,
    expression: Int,
    earWiggleAngle: Float,
    tailWagAngle: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val width = size.width * 0.82f
    val height = size.height * 0.78f

    // Soft drop shadow
    drawOval(
        color = Color(0x30000000),
        topLeft = Offset(center.x - width * 0.44f, center.y + height * 0.36f),
        size = Size(width * 0.88f, height * 0.22f)
    )

    when (pet) {
        PetCharacter.KITTY -> {
            // Kitty tail
            val tailPath = Path().apply {
                moveTo(center.x + width * 0.35f, center.y + height * 0.25f)
                cubicTo(
                    center.x + width * 0.55f + tailWagAngle, center.y + height * 0.1f,
                    center.x + width * 0.6f + tailWagAngle, center.y - height * 0.1f,
                    center.x + width * 0.45f + tailWagAngle, center.y - height * 0.15f
                )
            }
            drawPath(tailPath, color = pet.secondaryColor, style = Stroke(width = 18f, cap = androidx.compose.ui.graphics.StrokeCap.Round))

            // Kitty Ears
            val leftEar = Path().apply {
                moveTo(center.x - width * 0.38f, center.y - height * 0.18f)
                lineTo(center.x - width * 0.32f + earWiggleAngle, center.y - height * 0.48f)
                lineTo(center.x - width * 0.10f, center.y - height * 0.32f)
                close()
            }
            val rightEar = Path().apply {
                moveTo(center.x + width * 0.38f, center.y - height * 0.18f)
                lineTo(center.x + width * 0.32f - earWiggleAngle, center.y - height * 0.48f)
                lineTo(center.x + width * 0.10f, center.y - height * 0.32f)
                close()
            }
            drawPath(leftEar, color = pet.secondaryColor)
            drawPath(rightEar, color = pet.secondaryColor)
            drawPath(leftEar, color = Color(0xFFFF80AB), style = Fill)
            drawPath(rightEar, color = Color(0xFFFF80AB), style = Fill)

            // Kitty Body
            drawCircle(
                brush = Brush.radialGradient(listOf(pet.primaryColor, pet.secondaryColor), center = center, radius = width * 0.5f),
                radius = width * 0.46f,
                center = center
            )
            drawCircle(color = Color(0xFFE65100), radius = width * 0.46f, center = center, style = Stroke(width = 5f))

            // Whiskers
            val whiskerColor = Color(0xFF5D4037)
            drawLine(whiskerColor, Offset(center.x - width * 0.22f, center.y + 10f), Offset(center.x - width * 0.46f, center.y + 5f), strokeWidth = 5f)
            drawLine(whiskerColor, Offset(center.x - width * 0.22f, center.y + 24f), Offset(center.x - width * 0.46f, center.y + 30f), strokeWidth = 5f)
            drawLine(whiskerColor, Offset(center.x + width * 0.22f, center.y + 10f), Offset(center.x + width * 0.46f, center.y + 5f), strokeWidth = 5f)
            drawLine(whiskerColor, Offset(center.x + width * 0.22f, center.y + 24f), Offset(center.x + width * 0.46f, center.y + 30f), strokeWidth = 5f)
        }

        PetCharacter.PUPPY -> {
            // Puppy Floppy Ears
            drawOval(
                color = Color(0xFF8D6E63),
                topLeft = Offset(center.x - width * 0.48f - earWiggleAngle, center.y - height * 0.32f),
                size = Size(width * 0.28f, height * 0.55f)
            )
            drawOval(
                color = Color(0xFF8D6E63),
                topLeft = Offset(center.x + width * 0.20f + earWiggleAngle, center.y - height * 0.32f),
                size = Size(width * 0.28f, height * 0.55f)
            )

            // Puppy Body
            drawCircle(
                brush = Brush.radialGradient(listOf(pet.primaryColor, pet.secondaryColor), center = center, radius = width * 0.5f),
                radius = width * 0.46f,
                center = center
            )
            drawCircle(color = Color(0xFF795548), radius = width * 0.46f, center = center, style = Stroke(width = 5f))

            // Puppy Patch over left eye
            drawOval(
                color = Color(0xFFFFCC80),
                topLeft = Offset(center.x - width * 0.32f, center.y - height * 0.22f),
                size = Size(width * 0.32f, height * 0.35f)
            )
        }

        PetCharacter.BUNNY -> {
            // Bunny Long Ears
            val earL = Path().apply {
                moveTo(center.x - width * 0.28f, center.y - height * 0.25f)
                cubicTo(
                    center.x - width * 0.35f + earWiggleAngle, center.y - height * 0.65f,
                    center.x - width * 0.15f + earWiggleAngle, center.y - height * 0.65f,
                    center.x - width * 0.08f, center.y - height * 0.25f
                )
                close()
            }
            val earR = Path().apply {
                moveTo(center.x + width * 0.08f, center.y - height * 0.25f)
                cubicTo(
                    center.x + width * 0.15f - earWiggleAngle, center.y - height * 0.65f,
                    center.x + width * 0.35f - earWiggleAngle, center.y - height * 0.65f,
                    center.x + width * 0.28f, center.y - height * 0.25f
                )
                close()
            }
            drawPath(earL, color = pet.secondaryColor)
            drawPath(earR, color = pet.secondaryColor)
            drawPath(earL, color = Color.White, style = Stroke(width = 4f))
            drawPath(earR, color = Color.White, style = Stroke(width = 4f))

            // Bunny Body
            drawCircle(
                brush = Brush.radialGradient(listOf(pet.primaryColor, pet.secondaryColor), center = center, radius = width * 0.5f),
                radius = width * 0.45f,
                center = center
            )
            drawCircle(color = Color(0xFFC2185B), radius = width * 0.45f, center = center, style = Stroke(width = 4f))
        }

        PetCharacter.BEAR -> {
            // Bear round ears
            drawCircle(color = Color(0xFF8D6E63), radius = 34f, center = Offset(center.x - width * 0.34f, center.y - height * 0.34f))
            drawCircle(color = Color(0xFFFFCC80), radius = 18f, center = Offset(center.x - width * 0.34f, center.y - height * 0.34f))
            drawCircle(color = Color(0xFF8D6E63), radius = 34f, center = Offset(center.x + width * 0.34f, center.y - height * 0.34f))
            drawCircle(color = Color(0xFFFFCC80), radius = 18f, center = Offset(center.x + width * 0.34f, center.y - height * 0.34f))

            // Bear Head Body
            drawCircle(
                brush = Brush.radialGradient(listOf(pet.primaryColor, pet.secondaryColor), center = center, radius = width * 0.5f),
                radius = width * 0.46f,
                center = center
            )
            drawCircle(color = Color(0xFF5D4037), radius = width * 0.46f, center = center, style = Stroke(width = 5f))

            // Bear Muzzle
            drawOval(
                color = Color(0xFFFFF8E1),
                topLeft = Offset(center.x - width * 0.25f, center.y - 5f),
                size = Size(width * 0.50f, height * 0.38f)
            )
        }

        PetCharacter.DUMPLING -> {
            // Plump dumpling dumpling body
            val dumplingPath = Path().apply {
                moveTo(center.x - width * 0.45f, center.y + height * 0.1f)
                cubicTo(
                    center.x - width * 0.5f, center.y - height * 0.35f,
                    center.x - width * 0.15f, center.y - height * 0.48f,
                    center.x, center.y - height * 0.42f
                )
                cubicTo(
                    center.x + width * 0.15f, center.y - height * 0.48f,
                    center.x + width * 0.5f, center.y - height * 0.35f,
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
                path = dumplingPath,
                brush = Brush.radialGradient(listOf(pet.primaryColor, pet.secondaryColor), center = Offset(center.x, center.y - 15f), radius = width * 0.7f)
            )
            drawPath(dumplingPath, color = Color(0xFFD7CCC8), style = Stroke(width = 5f))
        }
    }

    // Rosy Pink Cheeks (Blush)
    drawCircle(color = Color(0xFFFF80AB).copy(alpha = 0.55f), radius = 22f, center = Offset(center.x - width * 0.26f, center.y + 14f))
    drawCircle(color = Color(0xFFFF80AB).copy(alpha = 0.55f), radius = 22f, center = Offset(center.x + width * 0.26f, center.y + 14f))

    // Cute Nose
    drawOval(
        color = Color(0xFF3E2723),
        topLeft = Offset(center.x - 10f, center.y + 2f),
        size = Size(20f, 14f)
    )

    // Eyes and Expressions
    val eyeL = Offset(center.x - width * 0.16f, center.y - 14f)
    val eyeR = Offset(center.x + width * 0.16f, center.y - 14f)

    when (expression) {
        0 -> { // Sparkling Joy Eyes + Smile
            drawCircle(color = Color(0xFF212121), radius = 15f, center = eyeL)
            drawCircle(color = Color.White, radius = 5f, center = Offset(eyeL.x - 4f, eyeL.y - 4f))
            drawCircle(color = Color(0xFF212121), radius = 15f, center = eyeR)
            drawCircle(color = Color.White, radius = 5f, center = Offset(eyeR.x - 4f, eyeR.y - 4f))

            val smile = Path().apply {
                moveTo(center.x - 18f, center.y + 20f)
                quadraticBezierTo(center.x, center.y + 36f, center.x + 18f, center.y + 20f)
            }
            drawPath(smile, color = Color(0xFF3E2723), style = Stroke(width = 6f))
        }
        1 -> { // Playful Wink
            drawCircle(color = Color(0xFF212121), radius = 15f, center = eyeL)
            drawCircle(color = Color.White, radius = 5f, center = Offset(eyeL.x - 4f, eyeL.y - 4f))

            val wink = Path().apply {
                moveTo(eyeR.x - 14f, eyeR.y)
                quadraticBezierTo(eyeR.x, eyeR.y - 12f, eyeR.x + 14f, eyeR.y)
            }
            drawPath(wink, color = Color(0xFF212121), style = Stroke(width = 6f))

            // Tongue out
            val mouth = Path().apply {
                moveTo(center.x - 16f, center.y + 20f)
                quadraticBezierTo(center.x, center.y + 40f, center.x + 16f, center.y + 20f)
            }
            drawPath(mouth, color = Color(0xFF3E2723), style = Stroke(width = 6f))
            drawCircle(color = Color(0xFFFF5252), radius = 10f, center = Offset(center.x, center.y + 32f))
        }
        2 -> { // Giggle Eyes (^^) + Big Laugh
            val leftArch = Path().apply {
                moveTo(eyeL.x - 16f, eyeL.y + 4f)
                quadraticBezierTo(eyeL.x, eyeL.y - 14f, eyeL.x + 16f, eyeL.y + 4f)
            }
            val rightArch = Path().apply {
                moveTo(eyeR.x - 16f, eyeR.y + 4f)
                quadraticBezierTo(eyeR.x, eyeR.y - 14f, eyeR.x + 16f, eyeR.y + 4f)
            }
            drawPath(leftArch, color = Color(0xFF212121), style = Stroke(width = 6f))
            drawPath(rightArch, color = Color(0xFF212121), style = Stroke(width = 6f))

            val laugh = Path().apply {
                moveTo(center.x - 22f, center.y + 18f)
                quadraticBezierTo(center.x, center.y + 46f, center.x + 22f, center.y + 18f)
                close()
            }
            drawPath(laugh, color = Color(0xFF3E2723))
            drawCircle(color = Color(0xFFFF5252), radius = 10f, center = Offset(center.x, center.y + 34f))
        }
        else -> { // Heart Eyes
            drawHeart(eyeL, 16f, Color(0xFFFF1744))
            drawHeart(eyeR, 16f, Color(0xFFFF1744))

            val openMouth = Path().apply {
                moveTo(center.x - 14f, center.y + 20f)
                quadraticBezierTo(center.x, center.y + 36f, center.x + 14f, center.y + 20f)
            }
            drawPath(openMouth, color = Color(0xFF3E2723), style = Stroke(width = 6f))
        }
    }
}

private fun DrawScope.drawHeart(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + size * 0.7f)
        cubicTo(
            center.x - size * 1.2f, center.y - size * 0.3f,
            center.x - size * 0.8f, center.y - size * 1.2f,
            center.x, center.y - size * 0.4f
        )
        cubicTo(
            center.x + size * 0.8f, center.y - size * 1.2f,
            center.x + size * 1.2f, center.y - size * 0.3f,
            center.x, center.y + size * 0.7f
        )
        close()
    }
    drawPath(path, color = color)
}
