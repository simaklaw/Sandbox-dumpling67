package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.SoundManager
import com.example.shared.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
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

private class PhoneFloat(val id: Long, val emoji: String, val dx: Float)

/**
 * «Детский телефончик»: нажми кнопку-зверя — он отзовётся, набери четыре кнопки — зазвонит друг.
 * Иногда телефон звонит сам: нажми «Ответить». Во время разговора можно послать поцелуй, песенку или подарок.
 * Поговори со всеми 11 друзьями — получишь праздник.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyPhoneScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    onBackClick: () -> Unit = {}
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val phoneContext = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(phoneContext, soundPlayer) }
    // Настоящие голоса животных: у каждой кнопки — своё животное, без повторов.
    fun animalSoundFor(key: String): String? = when (key) {
        "dog" -> "animal_dog"
        "cat" -> "animal_cat"
        "duck" -> "animal_duck"
        "cow" -> "animal_cow"
        "frog" -> "animal_frog"
        "pig" -> "animal_pig"
        "horse" -> "animal_horse"
        "sheep" -> "animal_sheep"
        "chick" -> "animal_chick"
        "rooster" -> "animal_rooster"
        "bunny" -> "animal_bunny"
        else -> null
    }

    LaunchedEffect(Unit) {
        soundManager.preloadRaw(
            listOf("animal_dog_1", "animal_dog_2", "animal_cat_1", "animal_cat_2", "animal_duck_1", "animal_duck_2",
                "animal_cow_1", "animal_cow_2", "animal_frog_1", "animal_frog_2", "animal_pig_1", "animal_pig_2",
                "animal_horse_1", "animal_horse_2", "animal_sheep_1", "animal_sheep_2", "chick_peep",
                "animal_rooster_1", "animal_rooster_2", "animal_bunny_1", "animal_bunny_2", "sm_note1", "sm_note2",
                "sm_pop", "sm_party", "sm_smooch", "sm_tada")
        )
    }

    var callState by remember { mutableStateOf(PhoneCallState.IDLE) }
    var dialedNumber by remember { mutableStateOf("") }
    var actionTick by remember { mutableIntStateOf(0) }

    // Case colors (Sunny Yellow, Bubblegum Pink, Sky Blue, Mint)
    val caseColors = listOf(
        Pair("Жёлтый", Color(0xFFFFD54F)),
        Pair("Розовый", Color(0xFFFF80AB)),
        Pair("Голубой", Color(0xFF81D4FA)),
        Pair("Мятный", Color(0xFFA7FFEB))
    )
    var caseColorIndex by remember { mutableStateOf(0) }
    val activeCaseColor = caseColors[caseColorIndex].second

    // Друзья-животные: по одному на кнопку цифры
    val friendByKey = remember {
        mapOf(
            "1" to PhoneFriend("Щенок Дружок", "🐶", "Алё-алё! Гав-гав! Давай играть!", Color(0xFFFFB74D), "dog"),
            "2" to PhoneFriend("Котёнок Мурка", "🐱", "Алё-алё! Мяу-мяу! Привет, малыш!", Color(0xFFFF8A80), "cat"),
            "3" to PhoneFriend("Утёнок Кряк", "🦆", "Алё-алё! Кря-кря! Пойдём на пруд!", Color(0xFFFFF176), "duck"),
            "4" to PhoneFriend("Коровка Бурёнка", "🐮", "Алё-алё! Му-у-у! Вкусное молочко!", Color(0xFFCE93D8), "cow"),
            "5" to PhoneFriend("Лягушонок Кваки", "🐸", "Алё-алё! Ква-ква! Прыг-скок!", Color(0xFFAED581), "frog"),
            "6" to PhoneFriend("Поросёнок Хрюша", "🐷", "Алё-алё! Хрю-хрю! Я люблю лужи!", Color(0xFFF8BBD0), "pig"),
            "7" to PhoneFriend("Лошадка Искорка", "🐴", "Алё-алё! Иго-го! Давай скакать!", Color(0xFF80CBC4), "horse"),
            "8" to PhoneFriend("Барашек Бяша", "🐑", "Алё-алё! Бе-е-е! Я пушистый!", Color(0xFFE0E0E0), "sheep"),
            "9" to PhoneFriend("Цыплёнок Цыпа", "🐥", "Алё-алё! Пи-пи-пи! Я маленький!", Color(0xFFFFF59D), "chick"),
            "*" to PhoneFriend("Петушок Гребешок", "🐓", "Алё-алё! Ку-ка-ре-ку! Пора вставать!", Color(0xFFFFAB91), "rooster"),
            "0" to PhoneFriend("Зайка Прыг-Скок", "🐰", "Алё-алё! Прыг-скок! Хочу морковку!", Color(0xFFFFCCBC), "bunny")
        )
    }
    val friends = remember { friendByKey.values.toList() }
    var peekIcon by remember { mutableStateOf<String?>(null) }
    var peekCounter by remember { mutableStateOf(0) }
    LaunchedEffect(peekCounter) {
        if (peekCounter > 0) { delay(1300); peekIcon = null }
    }
    val peekScale by animateFloatAsState(
        targetValue = if (peekIcon != null) 1f else 0.6f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "peekScale"
    )

    var activeFriend by remember { mutableStateOf(friends[0]) }
    val met = remember { mutableStateListOf<String>() }
    var celebrated by remember { mutableStateOf(false) }
    var showCelebration by remember { mutableStateOf(false) }

    // Character talking animation
    val mouthWiggle = remember { Animatable(0f) }
    val friendHop = remember { Animatable(0f) }

    // Летящие эмодзи (поцелуи, ноты, подарки)
    val floats = remember { mutableStateListOf<PhoneFloat>() }
    fun burst(emojis: List<String>, n: Int) {
        repeat(n) {
            floats.add(PhoneFloat(Random.nextLong(), emojis.random(), (Random.nextFloat() - 0.5f) * 220f))
        }
        if (floats.size > 40) repeat(floats.size - 40) { floats.removeAt(0) }
    }

    // Phone keys (12 large buttons)
    val keys = remember {
        listOf(
            PhoneKey("1", "🐶", 1, Color(0xFFFF8A80)),
            PhoneKey("2", "🐱", 2, Color(0xFFFFD54F)),
            PhoneKey("3", "🦆", 3, Color(0xFF81D4FA)),
            PhoneKey("4", "🐮", 4, Color(0xFFAED581)),
            PhoneKey("5", "🐸", 5, Color(0xFFCE93D8)),
            PhoneKey("6", "🐷", 6, Color(0xFFFFB74D)),
            PhoneKey("7", "🐴", 7, Color(0xFF80CBC4)),
            PhoneKey("8", "🐑", 8, Color(0xFFFFF59D)),
            PhoneKey("9", "🐥", 9, Color(0xFFFFAB91)),
            PhoneKey("*", "🐓", 10, Color(0xFFB39DDB)),
            PhoneKey("0", "🐰", 0, Color(0xFFFFCC80)),
            PhoneKey("#", "🎉", 11, Color(0xFF80DEEA))
        )
    }

    // Hangup routine
    fun hangUp() {
        soundManager.play("sm_pop", volume = 0.8f, rate = 0.8f)
        haptics.vibrateDialerHangup()
        callState = PhoneCallState.IDLE
        dialedNumber = ""
        actionTick++
    }

    // Разговор с другом (после гудков или сразу при ответе на входящий)
    fun connect(friend: PhoneFriend) {
        activeFriend = friend
        callState = PhoneCallState.CONNECTED
        haptics.vibratePop()
        if (friend.soundKey !in met) met.add(friend.soundKey)
        animalSoundFor(friend.soundKey)?.let { soundManager.play(it) }
        if (met.size == friends.size && !celebrated) {
            celebrated = true
            showCelebration = true
            soundManager.play("sm_tada")
            burst(listOf("🎉", "⭐", "🎈", "💖"), 18)
        }
        scope.launch {
            repeat(4) { iteration ->
                if (callState != PhoneCallState.CONNECTED) return@launch
                if (iteration == 2) soundManager.play("phone_hi_${friend.soundKey}", volume = 1f)
                launch { mouthWiggle.animateTo(1f, tween(140)) }
                launch { friendHop.animateTo(-18f, tween(140)) }
                delay(160)
                launch { mouthWiggle.animateTo(0f, tween(140)) }
                launch { friendHop.animateTo(0f, tween(140)) }
                delay(160)
            }
        }
    }

    // Call routine
    fun startCall() {
        if (callState == PhoneCallState.CONNECTED || callState == PhoneCallState.DIALING) return
        if (callState == PhoneCallState.INCOMING) {
            soundManager.play("sm_pop", volume = 0.8f)
            haptics.vibrateDialerCall()
            connect(activeFriend)
            return
        }
        callState = PhoneCallState.DIALING
        soundManager.play("sm_note1", volume = 0.6f)
        haptics.vibrateDialerCall()

        // Pick a random friend or based on last digit
        val chosenFriend = (if (dialedNumber.isNotEmpty()) friendByKey[dialedNumber.last().toString()] else null)
            ?: friends.filter { it.soundKey !in met }.ifEmpty { friends }.random()
        activeFriend = chosenFriend

        scope.launch {
            delay(600)
            if (callState == PhoneCallState.DIALING) soundManager.play("sm_note2", volume = 0.6f)
            delay(700)
            if (callState == PhoneCallState.DIALING) connect(chosenFriend)
        }
    }

    // Телефон сам звонит, если ребёнок долго ничего не нажимает
    LaunchedEffect(callState, actionTick) {
        if (callState == PhoneCallState.IDLE) {
            delay(22000)
            if (callState == PhoneCallState.IDLE) {
                activeFriend = friends.filter { it.soundKey !in met }.ifEmpty { friends }.random()
                callState = PhoneCallState.INCOMING
            }
        }
    }
    LaunchedEffect(callState) {
        if (callState == PhoneCallState.INCOMING) {
            var n = 0
            while (callState == PhoneCallState.INCOMING && n < 16) {
                soundManager.play(if (n % 2 == 0) "sm_note1" else "sm_note2", volume = 0.7f, rate = 1.3f)
                haptics.vibrateDialerCall()
                delay(750)
                n++
            }
            if (callState == PhoneCallState.INCOMING) callState = PhoneCallState.IDLE
        }
    }

    // Анимации: кольца вызова, эквалайзер голоса, покачивание звонящего зверя
    val inf = rememberInfiniteTransition(label = "phoneInf")
    val ringP by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "ring")
    val eqP by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing)), label = "eq")
    val wobble by inf.animateFloat(-12f, 12f, infiniteRepeatable(tween(180, easing = LinearEasing), RepeatMode.Reverse), label = "wobble")
    val answerPulse by inf.animateFloat(1f, 1.12f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "answerPulse")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFF3E5F5), Color(0xFFE1F5FE))))
    ) {
        // Main Toy Smartphone Body — на весь экран
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(activeCaseColor)
                .border(5.dp, Color.White, RoundedCornerShape(32.dp))
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Верхняя строка: место под кнопку «Домой», заголовок, цвет корпуса
            Row(
                modifier = Modifier.fillMaxWidth().height(72.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(80.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Детский телефончик 📱", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color(0xFF263238))
                    Text(
                        when (callState) {
                            PhoneCallState.IDLE -> "Нажми любую кнопку!"
                            PhoneCallState.DIALING -> "Идут гудки... 📞"
                            PhoneCallState.CONNECTED -> "Разговариваем с ${activeFriend.name} 💕"
                            PhoneCallState.INCOMING -> "Входящий звонок! Нажми «Ответить» 🔔"
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF37474F)
                    )
                }
                IconButton(
                    onClick = {
                        caseColorIndex = (caseColorIndex + 1) % caseColors.size
                        soundManager.play("sm_pop", volume = 0.8f, rate = 1.2f)
                        haptics.vibratePop()
                    },
                    modifier = Modifier.size(52.dp).testTag("phone_case_color_btn")
                ) {
                    Icon(Icons.Default.Palette, contentDescription = "Цвет корпуса", tint = Color(0xFF263238), modifier = Modifier.size(30.dp))
                }
            }

            // Экран телефона
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .testTag("phone_display_card"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.radialGradient(listOf(Color(0xFF2C3440), Color(0xFF14171F))))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (callState) {
                        PhoneCallState.IDLE -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(enabled = dialedNumber.isNotEmpty()) {
                                        soundManager.play("sm_pop", volume = 0.7f)
                                        haptics.vibrateShort()
                                        dialedNumber = dialedNumber.dropLast(1)
                                    }
                            ) {
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = peekIcon ?: "📱",
                                    fontSize = 72.sp,
                                    modifier = Modifier.scale(if (peekIcon != null) peekScale else 1f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (dialedNumber.isEmpty()) "Набери номер ☎️"
                                    else dialedNumber.map { friendByKey[it.toString()]?.icon ?: "🎉" }.joinToString(" "),
                                    color = Color.White,
                                    fontSize = if (dialedNumber.isEmpty()) 26.sp else 34.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (dialedNumber.isNotEmpty()) "Нажми сюда, чтобы стереть ⌫" else "Жми кнопки — звери отвечают!",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.weight(1f))
                                // Книжка друзей: с кем уже поговорили
                                Text(
                                    text = "Друзья: ${met.size}/${friends.size}",
                                    color = Color(0xFFFFE082),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                ) {
                                    friends.forEach { f ->
                                        Text(
                                            f.icon,
                                            fontSize = 17.sp,
                                            modifier = Modifier.graphicsLayer { alpha = if (f.soundKey in met) 1f else 0.25f }
                                        )
                                    }
                                }
                            }
                        }

                        PhoneCallState.DIALING, PhoneCallState.INCOMING -> {
                            val incoming = callState == PhoneCallState.INCOMING
                            Canvas(Modifier.fillMaxSize()) {
                                val c = Offset(size.width / 2f, size.height * 0.42f)
                                val base = size.minDimension * 0.18f
                                for (i in 0 until 3) {
                                    val p = (ringP + i / 3f) % 1f
                                    drawCircle(
                                        color = (if (incoming) Color(0xFF76FF03) else Color(0xFF81D4FA)).copy(alpha = (1f - p) * 0.55f),
                                        radius = base * (0.8f + p * 2.4f),
                                        center = c,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx())
                                    )
                                }
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize().clickable(enabled = incoming) { startCall() }
                            ) {
                                Text(
                                    text = activeFriend.icon,
                                    fontSize = 76.sp,
                                    modifier = Modifier.graphicsLayer { rotationZ = if (incoming) wobble else 0f }
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (incoming) "Входящий вызов!" else "Звоним ${activeFriend.name}...",
                                    color = if (incoming) Color(0xFF76FF03) else Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (incoming) "${activeFriend.name} звонит тебе! 📲" else "Туут... Туут... 📞",
                                    color = if (incoming) Color.White else Color(0xFF81D4FA),
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        PhoneCallState.CONNECTED -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures {
                                            // Tapping the animal friend triggers cute sound & bounce
                                            animalSoundFor(activeFriend.soundKey)?.let { soundManager.play(it) }
                                            haptics.vibratePop()
                                            scope.launch {
                                                friendHop.animateTo(-24f, tween(120))
                                                friendHop.animateTo(0f, tween(120))
                                            }
                                        }
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    EqualizerBars(eqP, activeFriend.primaryColor, mirrored = false)
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.offset(y = friendHop.value.dp)
                                    ) {
                                        Text(
                                            text = activeFriend.icon,
                                            fontSize = 78.sp,
                                            modifier = Modifier.scale(1f + 0.08f * mouthWiggle.value)
                                        )
                                        Text(
                                            text = activeFriend.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    EqualizerBars(eqP, activeFriend.primaryColor, mirrored = true)
                                }
                                // Речевое облачко
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color.White,
                                    shadowElevation = 6.dp,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
                                ) {
                                    Text(
                                        text = if (showCelebration) "Ты поговорил со всеми друзьями! Ура! 🎉" else activeFriend.phrase,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF263238),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                // Реакции: поцелуй, песенка, подарок
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    PhoneReaction("😘", Color(0xFFFF80AB), "phone_react_kiss") {
                                        soundManager.play("sm_smooch")
                                        haptics.vibratePop()
                                        burst(listOf("💋", "💖", "❤️"), 7)
                                    }
                                    PhoneReaction("🎵", Color(0xFF81D4FA), "phone_react_song") {
                                        scope.launch {
                                            val rates = listOf(1.0f, 1.26f, 1.5f, 1.26f, 1.0f)
                                            rates.forEach { r ->
                                                soundManager.play("sm_note1", volume = 0.85f, rate = r)
                                                burst(listOf("🎵", "🎶"), 2)
                                                delay(230)
                                            }
                                        }
                                    }
                                    PhoneReaction("🎁", Color(0xFFFFD54F), "phone_react_gift") {
                                        soundManager.play("sm_party")
                                        haptics.vibratePop()
                                        burst(listOf("🎁", "🎈", "⭐", "🎉"), 8)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Клавиатура 4×3: кнопки заполняют всё свободное место
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.55f)
            ) {
                for (row in 0 until 4) {
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        for (col in 0 until 3) {
                            val key = keys[row * 3 + col]
                            Box(
                                modifier = Modifier.weight(1f).fillMaxHeight().padding(3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BabyPhoneKeyButton(
                                    key = key,
                                    modifier = Modifier.aspectRatio(1f),
                                    onClick = {
                                        // Distinct mechanical click haptic feedback
                                        haptics.vibrateDialerKey()
                                        actionTick++
                                        val kf = friendByKey[key.label]
                                        if (kf != null) {
                                            animalSoundFor(kf.soundKey)?.let { soundManager.play(it, volume = 0.9f) }
                                            peekIcon = kf.icon
                                            peekCounter++
                                        } else {
                                            soundManager.play("sm_party", volume = 0.8f)
                                            peekIcon = "🎉"
                                            peekCounter++
                                            burst(listOf("🎉", "🎊", "⭐"), 5)
                                        }

                                        if (callState == PhoneCallState.IDLE) {
                                            if (dialedNumber.length < 10) {
                                                dialedNumber += key.label
                                            }
                                            // Четыре нажатия — телефон сам набирает номер
                                            if (dialedNumber.length >= 4) {
                                                startCall()
                                            }
                                        } else if (callState == PhoneCallState.CONNECTED) {
                                            // Во время разговора кнопка зовёт в трубку другого зверя
                                            if (kf != null) {
                                                activeFriend = kf
                                                if (kf.soundKey !in met) met.add(kf.soundKey)
                                                scope.launch {
                                                    friendHop.animateTo(-24f, tween(120))
                                                    friendHop.animateTo(0f, tween(120))
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Кнопки вызова и сброса
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BabyPhoneActionButton(
                    onClick = {
                        haptics.vibrateDialerCall()
                        startCall()
                    },
                    icon = Icons.Default.Call,
                    text = if (callState == PhoneCallState.INCOMING) "Ответить" else "Позвонить",
                    containerColor = Color(0xFF4CAF50),
                    contentDescription = "Позвонить",
                    testTag = "phone_call_button",
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .scale(if (callState == PhoneCallState.INCOMING) answerPulse else 1f)
                )
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

        // Слой летящих эмодзи (не перехватывает касания)
        Box(
            modifier = Modifier.fillMaxSize().padding(top = 120.dp),
            contentAlignment = Alignment.Center
        ) {
            floats.toList().forEach { f ->
                key(f.id) { FloatingEmoji(f) { floats.remove(f) } }
            }
        }
    }
}

@Composable
private fun FloatingEmoji(item: PhoneFloat, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(item.id) {
        p.animateTo(1f, tween(1500, easing = LinearEasing))
        onDone()
    }
    Text(
        item.emoji,
        fontSize = (30f + 12f * sin(p.value * PI.toFloat())).sp,
        modifier = Modifier
            .offset(x = (item.dx + sin(p.value * 6f) * 14f).dp, y = (60f - p.value * 300f).dp)
            .graphicsLayer { alpha = (1f - p.value * p.value).coerceIn(0f, 1f) }
    )
}

@Composable
private fun EqualizerBars(phase: Float, color: Color, mirrored: Boolean) {
    Canvas(Modifier.size(width = 44.dp, height = 70.dp)) {
        val n = 5
        val gap = size.width / n
        for (i in 0 until n) {
            val idx = if (mirrored) n - 1 - i else i
            val h = size.height * (0.25f + 0.75f * abs(sin(phase * (1f + idx * 0.35f) + idx)))
            drawRoundRect(
                color = color,
                topLeft = Offset(i * gap + gap * 0.15f, (size.height - h) / 2f),
                size = Size(gap * 0.7f, h),
                cornerRadius = CornerRadius(gap * 0.35f)
            )
        }
    }
}

@Composable
private fun PhoneReaction(emoji: String, color: Color, tag: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.85f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "react")
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = CircleShape,
        color = color,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
        modifier = Modifier.size(52.dp).scale(sc).testTag(tag)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(emoji, fontSize = 26.sp)
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
        targetValue = if (isPressed) 1.dp else 6.dp,
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
        border = androidx.compose.foundation.BorderStroke(3.dp, Color.White),
        modifier = modifier
            .scale(scale)
            .testTag("phone_key_${key.label}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = key.subText,
                fontSize = 34.sp
            )
            Text(
                text = key.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF263238)
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
        shape = RoundedCornerShape(30.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 5.dp,
            pressedElevation = 1.dp
        ),
        modifier = modifier
            .height(60.dp)
            .scale(scale)
            .testTag(testTag)
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
