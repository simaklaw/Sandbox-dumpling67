package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class PhoneCallState {
    IDLE,       // Набор номера
    DIALING,    // Гудки вызова
    CONNECTED,  // Разговор с животным
    INCOMING    // Входящий звонок-сюрприз
}

data class PhoneFriend(
    val name: String,
    val icon: String,
    val phrase: String,
    val primaryColor: Color,
    val soundKey: String
)

data class PhoneKey(
    val label: String,
    val subText: String,
    val digit: Int,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyPhoneScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    onBackClick: () -> Unit = {}
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    var callState by remember { mutableStateOf(PhoneCallState.IDLE) }
    var dialedNumber by remember { mutableStateOf("") }

    // Case colors (Sunny Yellow, Bubblegum Pink, Sky Blue, Mint)
    val caseColors = listOf(
        Pair("Жёлтый", Color(0xFFFFD54F)),
        Pair("Розовый", Color(0xFFFF80AB)),
        Pair("Голубой", Color(0xFF81D4FA)),
        Pair("Мятный", Color(0xFFA7FFEB))
    )
    var caseColorIndex by remember { mutableStateOf(0) }
    val activeCaseColor = caseColors[caseColorIndex].second

    // List of friendly talking animal characters
    val friends = remember {
        listOf(
            PhoneFriend("Щенок Дружок", "🐶", "Алё-алё! Гав-гав! Давай играть!", Color(0xFFFFB74D), "dog"),
            PhoneFriend("Котёнок Мурка", "🐱", "Алё-алё! Мяу-мяу! Привет, малыш!", Color(0xFFFF8A80), "cat"),
            PhoneFriend("Утёнок Кряк", "🦆", "Алё-алё! Кря-кря! Пойдём на пруд!", Color(0xFFFFF176), "duck"),
            PhoneFriend("Коровка Бурёнка", "🐮", "Алё-алё! Мууу-мууу! Вкусное молочко!", Color(0xFFCE93D8), "cow"),
            PhoneFriend("Лягушонок Кваки", "🐸", "Алё-алё! Ква-ква! Прыг-скок!", Color(0xFFAED581), "frog"),
            PhoneFriend("Мишка Потап", "🐻", "Алё-алё! Медок очень сладкий!", Color(0xFFBCAAA4), "bear")
        )
    }

    var activeFriend by remember { mutableStateOf(friends[0]) }

    // Character talking animation
    val mouthWiggle = remember { Animatable(0f) }
    val friendHop = remember { Animatable(0f) }

    // Phone keys (12 large buttons)
    val keys = remember {
        listOf(
            PhoneKey("1", "🐶", 1, Color(0xFFFF8A80)),
            PhoneKey("2", "🐱", 2, Color(0xFFFFD54F)),
            PhoneKey("3", "🦆", 3, Color(0xFF81D4FA)),
            PhoneKey("4", "🐮", 4, Color(0xFFAED581)),
            PhoneKey("5", "🐸", 5, Color(0xFFCE93D8)),
            PhoneKey("6", "🐷", 6, Color(0xFFFFB74D)),
            PhoneKey("7", "🐰", 7, Color(0xFF80CBC4)),
            PhoneKey("8", "🐻", 8, Color(0xFFFFF59D)),
            PhoneKey("9", "🦁", 9, Color(0xFFFFAB91)),
            PhoneKey("*", "✨", 10, Color(0xFFB39DDB)),
            PhoneKey("0", "🦊", 0, Color(0xFFFFCC80)),
            PhoneKey("#", "🎉", 11, Color(0xFF80DEEA))
        )
    }

    // Hangup routine
    fun hangUp() {
        soundPlayer.playPhoneHangup()
        haptics.vibrateDialerHangup()
        callState = PhoneCallState.IDLE
        dialedNumber = ""
    }

    // Call routine
    fun startCall() {
        if (callState == PhoneCallState.CONNECTED || callState == PhoneCallState.DIALING) return
        callState = PhoneCallState.DIALING
        soundPlayer.playPhoneRingtone()
        haptics.vibrateDialerCall()

        // Pick a random friend or based on last digit
        val chosenFriend = if (dialedNumber.isNotEmpty() && dialedNumber.last().isDigit()) {
            val d = dialedNumber.last().digitToInt()
            friends[d % friends.size]
        } else {
            friends.random()
        }
        activeFriend = chosenFriend

        scope.launch {
            delay(1200)
            if (callState == PhoneCallState.DIALING) {
                callState = PhoneCallState.CONNECTED
                soundPlayer.playAlloGreeting(chosenFriend.soundKey)
                haptics.vibratePop()

                // Animated talking mouth and hop
                repeat(4) {
                    launch { mouthWiggle.animateTo(1f, tween(140)) }
                    launch { friendHop.animateTo(-18f, tween(140)) }
                    delay(160)
                    launch { mouthWiggle.animateTo(0f, tween(140)) }
                    launch { friendHop.animateTo(0f, tween(140)) }
                    delay(160)
                }
            }
        }
    }

    // Auto-surprise incoming call if idle for 35 seconds
    LaunchedEffect(callState) {
        if (callState == PhoneCallState.IDLE) {
            delay(35000L)
            if (callState == PhoneCallState.IDLE) {
                activeFriend = friends.random()
                callState = PhoneCallState.INCOMING
                repeat(4) {
                    if (callState == PhoneCallState.INCOMING) {
                        soundPlayer.playPhoneRingtone()
                        haptics.vibrate(200)
                        delay(1400)
                    }
                }
                if (callState == PhoneCallState.INCOMING) {
                    callState = PhoneCallState.IDLE
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Детский телефончик 📱", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            when (callState) {
                                PhoneCallState.IDLE -> "Нажми любую кнопку!"
                                PhoneCallState.DIALING -> "Идут гудки... 📞"
                                PhoneCallState.CONNECTED -> "Разговариваем с ${activeFriend.name} 💕"
                                PhoneCallState.INCOMING -> "Входящий звонок! Возьми трубку! 🔔"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                // Общая кнопка «Домой» (80 dp) рисуется поверх верхнего левого угла — оставляем под неё место
                navigationIcon = { androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(88.dp)) },
                actions = {
                    IconButton(
                        onClick = {
                            caseColorIndex = (caseColorIndex + 1) % caseColors.size
                            soundPlayer.playBubblePop()
                            haptics.vibratePop()
                        },
                        modifier = Modifier.testTag("phone_case_color_btn")
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = "Цвет корпуса", tint = activeCaseColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = activeCaseColor.copy(alpha = 0.35f)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFF3E5F5), Color(0xFFE1F5FE))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Main Toy Smartphone Body
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(activeCaseColor)
                    .border(6.dp, Color.White, RoundedCornerShape(36.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Screen / Video Call Display
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.95f)
                        .clip(RoundedCornerShape(24.dp))
                        .testTag("phone_display_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A24)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF2C3440), Color(0xFF14171F))
                                )
                            )
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (callState) {
                            PhoneCallState.IDLE -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .clickable(enabled = dialedNumber.isNotEmpty()) {
                                            soundPlayer.playBubblePop()
                                            haptics.vibrateShort()
                                            dialedNumber = dialedNumber.dropLast(1)
                                        }
                                ) {
                                    Text(
                                        text = if (dialedNumber.isEmpty()) "Набери номер ☎️" else dialedNumber,
                                        color = Color.White,
                                        fontSize = if (dialedNumber.length > 8) 22.sp else 30.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (dialedNumber.isNotEmpty()) "Нажми сюда, чтобы стереть ⌫" else "Звони щенку, котику или мишке! 🐶🐱🐻",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            PhoneCallState.DIALING -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = activeFriend.icon,
                                        fontSize = 58.sp,
                                        modifier = Modifier.scale(1.1f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Звоним ${activeFriend.name}...",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Туут... Туут... 📞",
                                        color = Color(0xFF81D4FA),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            PhoneCallState.CONNECTED -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            detectTapGestures {
                                                // Tapping the animal friend triggers cute giggle & bounce
                                                soundPlayer.playBabyGiggle()
                                                haptics.vibratePop()
                                                scope.launch {
                                                    friendHop.animateTo(-24f, tween(120))
                                                    friendHop.animateTo(0f, tween(120))
                                                }
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    // Animated Talking Friend
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.offset(y = friendHop.value.dp)
                                    ) {
                                        Text(
                                            text = activeFriend.icon,
                                            fontSize = 68.sp
                                        )
                                        Text(
                                            text = activeFriend.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }

                                    // Speech Bubble
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = Color.White,
                                        shadowElevation = 6.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = activeFriend.phrase,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF263238),
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "🎵 Разговариваем",
                                                fontSize = 11.sp,
                                                color = Color(0xFF4CAF50),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            PhoneCallState.INCOMING -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = activeFriend.icon,
                                        fontSize = 62.sp
                                    )
                                    Text(
                                        text = "Входящий вызов!",
                                        color = Color(0xFF76FF03),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp
                                    )
                                    Text(
                                        text = "${activeFriend.name} звонит тебе! 📲",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Keypad: 4 rows x 3 columns with tactile spring physics and punchy haptics
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.4f),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (row in 0 until 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (col in 0 until 3) {
                                val keyIndex = row * 3 + col
                                val key = keys[keyIndex]

                                BabyPhoneKeyButton(
                                    key = key,
                                    onClick = {
                                        // Distinct mechanical click haptic feedback
                                        haptics.vibrateDialerKey()
                                        soundPlayer.playPhoneDialTone(key.digit)

                                        if (callState == PhoneCallState.IDLE) {
                                            if (dialedNumber.length < 10) {
                                                dialedNumber += key.label
                                            }
                                            // If toddler presses 4 digits, automatically place the call!
                                            if (dialedNumber.length >= 4) {
                                                startCall()
                                            }
                                        } else if (callState == PhoneCallState.CONNECTED) {
                                            // Pressing keys during call sends funny dial tones!
                                            soundPlayer.playPhoneDialTone(key.digit)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom Call / Hang Up Controls with bouncy tactile feedback
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Call Button (Green)
                    BabyPhoneActionButton(
                        onClick = {
                            haptics.vibrateDialerCall()
                            startCall()
                        },
                        icon = Icons.Default.Call,
                        text = "Позвонить",
                        containerColor = Color(0xFF4CAF50),
                        contentDescription = "Позвонить",
                        testTag = "phone_call_button",
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )

                    // Hangup Button (Red)
                    BabyPhoneActionButton(
                        onClick = {
                            haptics.vibrateDialerHangup()
                            hangUp()
                        },
                        icon = Icons.Default.CallEnd,
                        text = "Сбросить",
                        containerColor = Color(0xFFF44336),
                        contentDescription = "Сбросить",
                        testTag = "phone_hangup_button",
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Large tactile dialer button with spring bounce physics and rich haptic feedback.
 */
@Composable
private fun BabyPhoneKeyButton(
    key: PhoneKey,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile spring physics for authentic rubbery toy button feeling
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.84f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "key_scale_${key.label}"
    )

    val elevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else 5.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "key_elevation_${key.label}"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CircleShape,
        color = if (isPressed) key.color.copy(alpha = 0.85f) else key.color,
        shadowElevation = elevation,
        border = androidx.compose.foundation.BorderStroke(2.5.dp, Color.White),
        modifier = modifier
            .size(62.dp)
            .scale(scale)
            .testTag("phone_key_${key.label}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = key.label,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF263238)
            )
            Text(
                text = key.subText,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Action button (Call / Hangup) with spring bounce and tactile feedback.
 */
@Composable
private fun BabyPhoneActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    text: String,
    containerColor: Color,
    contentDescription: String,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "btn_scale_$testTag"
    )

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(28.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 5.dp,
            pressedElevation = 1.dp
        ),
        modifier = modifier
            .height(56.dp)
            .scale(scale)
            .testTag(testTag)
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
