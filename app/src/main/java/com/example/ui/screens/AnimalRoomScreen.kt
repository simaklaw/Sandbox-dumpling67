package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Surface
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

data class AnimalSoundCardItem(
    val soundKey: String,
    val name: String,
    val voiceNameKey: String,
    val imageRes: Int,
    /** Записи голоса (res/raw): при каждом нажатии берётся другая, подряд не повторяется. */
    val voices: List<String>,
    val bgColor: Color,
    val badgeColor: Color
)

private val AR_COW = AnimalSoundCardItem("animal_cow", "Коровка", "voice_name_cow", R.drawable.ra_cow, listOf("ar_moo", "ar_moo2"), Color(0xFFE8F5E9), Color(0xFF81C784))
private val AR_DOG = AnimalSoundCardItem("animal_dog", "Собачка", "voice_name_dog", R.drawable.ra_dog, listOf("ar_bark"), Color(0xFFFFF8E1), Color(0xFFFFB300))
private val AR_CAT = AnimalSoundCardItem("animal_cat", "Кошечка", "voice_name_cat", R.drawable.ra_cat, listOf("ar_meow", "ar_mwa"), Color(0xFFFBE9E7), Color(0xFFFF8A65))
private val AR_PIG = AnimalSoundCardItem("animal_pig", "Свинка", "voice_name_pig", R.drawable.ra_pig, listOf("ar_oink1", "ar_oink2", "ar_oink3", "ar_squeal"), Color(0xFFFCE4EC), Color(0xFFF48FB1))
private val AR_SHEEP = AnimalSoundCardItem("animal_sheep", "Барашек", "voice_name_sheep", R.drawable.ra_sheep, listOf("ar_baa", "ar_baa2", "ar_lamb"), Color(0xFFE0F2F1), Color(0xFF80CBC4))
private val AR_GOAT = AnimalSoundCardItem("animal_goat", "Козочка", "voice_name_sheep", R.drawable.ra_goat, listOf("ar_bleat"), Color(0xFFECEFF1), Color(0xFF90A4AE))
private val AR_CHICK = AnimalSoundCardItem("animal_chick", "Цыплёнок", "voice_name_chick", R.drawable.ra_chicken, listOf("ar_peeps"), Color(0xFFFFFDE7), Color(0xFFFFCA28))
private val AR_ZEBRA = AnimalSoundCardItem("animal_horse", "Зебра", "voice_name_cow", R.drawable.ra_zebra, listOf("ar_whinny"), Color(0xFFEFEBE9), Color(0xFFA1887F))

val FULL_ANIMAL_SOUND_LIST = listOf(AR_COW, AR_DOG, AR_CAT, AR_PIG, AR_SHEEP, AR_CHICK, AR_GOAT, AR_ZEBRA)

val TODDLER_ANIMAL_SOUND_LIST = listOf(AR_COW, AR_DOG, AR_CAT, AR_PIG, AR_SHEEP, AR_CHICK)

private val AR_ALL_SOUNDS = listOf(
    "ar_moo", "ar_moo2", "ar_bark", "ar_meow", "ar_mwa", "ar_oink1", "ar_oink2", "ar_oink3", "ar_squeal",
    "ar_baa", "ar_baa2", "ar_lamb", "ar_bleat", "ar_peeps", "ar_whinny"
)

/**
 * Прямой проигрыватель голосов зверей (SoundPool): не зависит от каталога звуков, лимитов и «процедурных»
 * замен — нажатие всегда даёт настоящую запись без задержки.
 */
class AnimalVoices(ctx: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    private val ids = HashMap<String, Int>()
    private val ready = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    private val last = HashMap<String, Int>()
    private var stream = 0

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready.add(id) }
        for (n in AR_ALL_SOUNDS) {
            val res = ctx.resources.getIdentifier(n, "raw", ctx.packageName)
            if (res != 0) ids[n] = pool.load(ctx, res, 1)
        }
    }

    fun play(names: List<String>, volume: Float) {
        val loaded = names.filter { ids[it]?.let { id -> id in ready } == true }
        if (loaded.isEmpty()) return
        val key = names.first()
        val prev = last[key] ?: -1
        val pick = (loaded.indices).filter { it != prev }.ifEmpty { loaded.indices.toList() }.random()
        last[key] = pick
        if (stream != 0) pool.stop(stream)
        stream = pool.play(ids[loaded[pick]]!!, volume, volume, 1, 0, 1f)
    }

    fun release() { try { pool.release() } catch (e: Exception) { } }
}

@Composable
fun AnimalRoomScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val ageProfile = LocalAgeProfile.current

    val animalList = if (ageProfile.isToddler2) TODDLER_ANIMAL_SOUND_LIST else FULL_ANIMAL_SOUND_LIST

    val voices = remember { AnimalVoices(context.applicationContext) }
    DisposableEffect(voices) { onDispose { voices.release() } }

    // Preload animal sounds group
    DisposableEffect(Unit) {
        soundManager.preload("animals")
        onDispose {
            soundManager.release("animals")
        }
    }

    // Active animation states for each card
    val jumpAnimatables = remember(animalList.size) {
        animalList.map { Animatable(1.0f) }
    }
    val pulseAnimatables = remember(animalList.size) {
        animalList.map { Animatable(1.0f) }
    }

    // Choir state
    var isChoirPlaying by remember { mutableStateOf(false) }
    var highlightedIndex by remember { mutableStateOf<Int?>(null) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun triggerAnimal(index: Int, isAutoDemo: Boolean = false) {
        if (index !in animalList.indices) return
        val item = animalList[index]
        if (!isAutoDemo) {
            lastInteractionTime = System.currentTimeMillis()
            haptics.vibrate(20)
        }
        if (!soundManager.isMuted) voices.play(item.voices, (0.55f + 0.45f * soundManager.masterVolume).coerceIn(0.4f, 1f))

        scope.launch {
            // Jump scale & bounce
            launch {
                jumpAnimatables[index].animateTo(
                    targetValue = 1.25f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                )
                jumpAnimatables[index].animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioHighBouncy))
            }
            // Card pulse
            launch {
                pulseAnimatables[index].animateTo(1.06f, tween(100))
                pulseAnimatables[index].animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }

            // In 2yo mode: after 0.8s, voice speaks animal name (voice_name_<kind>)
            if (ageProfile.isToddler2) {
                delay(800L)
                soundManager.play(item.voiceNameKey)
            }
        }
    }

    // Auto-demonstration for 2yo mode after 10s idle
    LaunchedEffect(lastInteractionTime, ageProfile.isToddler2) {
        if (ageProfile.isToddler2 && ageProfile.autoDemoIdleDelaySeconds > 0) {
            delay(ageProfile.autoDemoIdleDelaySeconds * 1000L)
            val randomIdx = Random.nextInt(animalList.size)
            triggerAnimal(randomIdx, isAutoDemo = true)
        }
    }

    fun startChoir() {
        if (isChoirPlaying) return
        isChoirPlaying = true
        haptics.vibrate(40)

        scope.launch {
            val indices = animalList.indices.shuffled().take(4)
            for (idx in indices) {
                highlightedIndex = idx
                triggerAnimal(idx)
                delay(600L)
            }
            highlightedIndex = null
            isChoirPlaying = false
        }
    }

    var currentPage by remember { mutableIntStateOf(0) }
    val pageSize = 6
    val totalPages = (animalList.size + pageSize - 1) / pageSize
    val pageItems = animalList.drop(currentPage * pageSize).take(pageSize)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectTapGestures {
                    lastInteractionTime = System.currentTimeMillis()
                }
            }
            .pointerInput(totalPages) {
                var dragSum = 0f
                detectDragGestures(
                    onDragStart = { dragSum = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragSum += dragAmount.x
                    },
                    onDragEnd = {
                        if (dragSum < -80f && currentPage < totalPages - 1) {
                            currentPage++
                            soundManager.play("transition")
                            haptics.vibrateShort()
                        } else if (dragSum > 80f && currentPage > 0) {
                            currentPage--
                            soundManager.play("transition")
                            haptics.vibrateShort()
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Spacer for HomeButton (80 dp) + 12 dp gap + 🐮 Icon (No text title)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(80.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "🐮",
                    fontSize = 38.sp
                )
            }

            // 2 x 3 Grid of 6 Cards for Current Page (No vertical scrolling)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                for (row in 0 until 3) {
                    val idx0 = row * 2
                    val idx1 = row * 2 + 1
                    if (idx0 < pageItems.size) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val globalIdx0 = currentPage * pageSize + idx0
                            val item0 = pageItems[idx0]
                            val isHigh0 = highlightedIndex == globalIdx0
                            val scale0 = pulseAnimatables[globalIdx0].value * (if (isHigh0) 1.08f else 1.0f)
                            val jump0 = jumpAnimatables[globalIdx0].value

                            Surface(
                                shape = ShapeCard,
                                color = item0.bgColor,
                                border = BorderStroke(
                                    width = if (isHigh0) 5.dp else 3.5.dp,
                                    color = if (isHigh0) ColorPurple else ColorInk
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .scale(scale0)
                                    .shadow(if (isHigh0) 12.dp else 4.dp, ShapeCard)
                                    .plasticineClickable(haptics = haptics) {
                                        triggerAnimal(globalIdx0)
                                    }
                                    .testTag("animal_card_${item0.soundKey}")
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize(0.78f)
                                            .clip(CircleShape)
                                            .background(item0.badgeColor.copy(alpha = 0.25f))
                                    )
                                    Image(
                                        painter = painterResource(item0.imageRes),
                                        contentDescription = item0.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize(0.84f)
                                            .graphicsLayer { scaleX = jump0; scaleY = jump0 }
                                    )
                                }
                            }

                            if (idx1 < pageItems.size) {
                                val globalIdx1 = currentPage * pageSize + idx1
                                val item1 = pageItems[idx1]
                                val isHigh1 = highlightedIndex == globalIdx1
                                val scale1 = pulseAnimatables[globalIdx1].value * (if (isHigh1) 1.08f else 1.0f)
                                val jump1 = jumpAnimatables[globalIdx1].value

                                Surface(
                                    shape = ShapeCard,
                                    color = item1.bgColor,
                                    border = BorderStroke(
                                        width = if (isHigh1) 5.dp else 3.5.dp,
                                        color = if (isHigh1) ColorPurple else ColorInk
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .scale(scale1)
                                        .shadow(if (isHigh1) 12.dp else 4.dp, ShapeCard)
                                        .plasticineClickable(haptics = haptics) {
                                            triggerAnimal(globalIdx1)
                                        }
                                        .testTag("animal_card_${item1.soundKey}")
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize(0.78f)
                                            .clip(CircleShape)
                                            .background(item1.badgeColor.copy(alpha = 0.25f))
                                    )
                                    Image(
                                        painter = painterResource(item1.imageRes),
                                        contentDescription = item1.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize(0.84f)
                                            .graphicsLayer { scaleX = jump1; scaleY = jump1 }
                                    )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp)) // Space for bottom choir button
        }

        // Left Navigation Arrow (88 dp)
        if (currentPage > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .size(88.dp)
                    .border(3.5.dp, ColorInk, CircleShape)
                    .background(ColorYellow, CircleShape)
                    .clip(CircleShape)
                    .plasticineClickable(haptics = haptics) {
                        currentPage--
                        soundManager.play("transition")
                    }
            ) {
                Text("◀️", fontSize = 42.sp)
            }
        }

        // Right Navigation Arrow (88 dp)
        if (currentPage < totalPages - 1) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .size(88.dp)
                    .border(3.5.dp, ColorInk, CircleShape)
                    .background(ColorYellow, CircleShape)
                    .clip(CircleShape)
                    .plasticineClickable(haptics = haptics) {
                        currentPage++
                        soundManager.play("transition")
                    }
            ) {
                Text("▶️", fontSize = 42.sp)
            }
        }

        // 🎵 88dp Choir Button ("Хор") - Fixed in Bottom Center
        if (!ageProfile.isToddler2) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .size(88.dp)
                    .border(4.dp, ColorInk, CircleShape)
                    .background(ColorPurple, CircleShape)
                    .clip(CircleShape)
                    .plasticineClickable(haptics = haptics) {
                        startChoir()
                    }
                    .testTag("choir_button")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("🎵", fontSize = 28.sp)
                    Text(
                        text = "Хор",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }
        }
    }
}
