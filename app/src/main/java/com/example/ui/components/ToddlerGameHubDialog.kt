package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.example.shared.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.audio.SoundEffectPlayer
import kotlinx.coroutines.launch

/**
 * Visual metadata for each interactive toddler room with block category.
 */
data class ToddlerRoomInfo(
    val index: Int,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val category: PlaygroundCategory,
    val bgColors: List<Color>,
    val borderColor: Color,
    val badgeEmoji: String,
    val testTag: String
)

val TODDLER_ROOMS = listOf(
    ToddlerRoomInfo(
        index = 0,
        title = "Зверята",
        subtitle = "Гладь и слушай звуки животных",
        emoji = "🐶",
        category = PlaygroundCategory.ANIMALS,
        bgColors = listOf(Color(0xFFFFF9C4), Color(0xFFFFE082)),
        borderColor = Color(0xFF4CAF50),
        badgeEmoji = "🐱",
        testTag = "toddler_card_animals"
    ),
    ToddlerRoomInfo(
        index = 1,
        title = "Кормление",
        subtitle = "Угощай питомцев вкусняшками",
        emoji = "🍎",
        category = PlaygroundCategory.ANIMALS,
        bgColors = listOf(Color(0xFFFFEBEE), Color(0xFFFFCDD2)),
        borderColor = Color(0xFFE57373),
        badgeEmoji = "🥕",
        testTag = "toddler_card_feeding"
    ),
    ToddlerRoomInfo(
        index = 2,
        title = "Пельмешек 🥟",
        subtitle = "Мялка и догонялки с Пельмешем!",
        emoji = "🥟",
        category = PlaygroundCategory.ANTISTRESS,
        bgColors = listOf(Color(0xFFFFF8E1), Color(0xFFFFD54F)),
        borderColor = Color(0xFFFF8F00),
        badgeEmoji = "✨",
        testTag = "toddler_card_squishies"
    ),
    ToddlerRoomInfo(
        index = 3,
        title = "Рожица 🤪",
        subtitle = "Антистресс — тяни и запускай!",
        emoji = "🤪",
        category = PlaygroundCategory.ANTISTRESS,
        bgColors = listOf(Color(0xFFFFFDE7), Color(0xFFFFE082)),
        borderColor = Color(0xFFFF5722),
        badgeEmoji = "⭐",
        testTag = "toddler_card_face_antistress"
    ),
    ToddlerRoomInfo(
        index = 4,
        title = "Лук и Чучело 🏹",
        subtitle = "Стреляй из лука в чучело!",
        emoji = "🏹",
        category = PlaygroundCategory.ANTISTRESS,
        bgColors = listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9)),
        borderColor = Color(0xFFFF6F00),
        badgeEmoji = "🎯",
        testTag = "toddler_card_scarecrow"
    ),
    ToddlerRoomInfo(
        index = 5,
        title = "Бегун Буба",
        subtitle = "Смешной бегун — шлёпай его!",
        emoji = "🏃‍♂️",
        category = PlaygroundCategory.ANTISTRESS,
        bgColors = listOf(Color(0xFFFFF3E0), Color(0xFFFFCC80)),
        borderColor = Color(0xFFFF9800),
        badgeEmoji = "🥊",
        testTag = "toddler_card_runner_booba"
    ),
    ToddlerRoomInfo(
        index = 6,
        title = "Сыпучести",
        subtitle = "Пересыпай крупу и макарошки",
        emoji = "🌾",
        category = PlaygroundCategory.CREATIVE,
        bgColors = listOf(Color(0xFFEFEBE9), Color(0xFFD7CCC8)),
        borderColor = Color(0xFF00ACC1),
        badgeEmoji = "🫘",
        testTag = "toddler_card_grain"
    ),
    ToddlerRoomInfo(
        index = 7,
        title = "Шарики",
        subtitle = "Лопай шарики с конфетти",
        emoji = "🎈",
        category = PlaygroundCategory.HOLIDAY,
        bgColors = listOf(Color(0xFFE1F5FE), Color(0xFFB3E5FC)),
        borderColor = Color(0xFFE91E63),
        badgeEmoji = "🎉",
        testTag = "toddler_card_balloons"
    ),
    ToddlerRoomInfo(
        index = 8,
        title = "Чайная",
        subtitle = "Наливай чай и мешай ложечкой",
        emoji = "🫖",
        category = PlaygroundCategory.ANIMALS,
        bgColors = listOf(Color(0xFFF3E5F5), Color(0xFFE1BEE7)),
        borderColor = Color(0xFF43A047),
        badgeEmoji = "🍵",
        testTag = "toddler_card_tea"
    ),
    ToddlerRoomInfo(
        index = 9,
        title = "Рисование",
        subtitle = "Волшебные краски пальчиками",
        emoji = "🎨",
        category = PlaygroundCategory.CREATIVE,
        bgColors = listOf(Color(0xFFE0F7FA), Color(0xFFB2EBF2)),
        borderColor = Color(0xFF00BCD4),
        badgeEmoji = "🌈",
        testTag = "toddler_card_painting"
    ),
    ToddlerRoomInfo(
        index = 10,
        title = "Бизиборд",
        subtitle = "Кнопочки, шестерёнки и замочки",
        emoji = "🎛️",
        category = PlaygroundCategory.CREATIVE,
        bgColors = listOf(Color(0xFFEDE7F6), Color(0xFFD1C4E9)),
        borderColor = Color(0xFF7E57C2),
        badgeEmoji = "🔔",
        testTag = "toddler_card_busyboard"
    ),
    ToddlerRoomInfo(
        index = 11,
        title = "Сюрпризы",
        subtitle = "Открывай боксы и подарки",
        emoji = "🎁",
        category = PlaygroundCategory.HOLIDAY,
        bgColors = listOf(Color(0xFFFCE4EC), Color(0xFFF8BBD0)),
        borderColor = Color(0xFFF06292),
        badgeEmoji = "⭐",
        testTag = "toddler_card_boxes"
    ),
    ToddlerRoomInfo(
        index = 12,
        title = "Пинбол 🔮",
        subtitle = "Запускай шарик по механизмам!",
        emoji = "🔮",
        category = PlaygroundCategory.ANTISTRESS,
        bgColors = listOf(Color(0xFF251648), Color(0xFF3F1D78)),
        borderColor = Color(0xFF00E5FF),
        badgeEmoji = "✨",
        testTag = "toddler_card_pinball"
    )
)

/**
 * Toddler-Friendly Full-Screen Game Menu Dialog ("Карта Игрушек").
 * Designed specifically for 2-3 year old children with:
 * - 4 Clear Thematic Blocks (Антистресс 💥, Зверята 🐾, Творчество 🎨, Праздник 🎈)
 * - Big chunky 118dp+ colorful cards
 * - 42sp giant emojis
 * - Simple one-tap selection
 * - Immediate tactile sound and bounce feedback
 * - Clear big "❌" close button
 */
@Composable
fun ToddlerGameHubDialog(
    currentRoomIndex: Int,
    soundPlayer: SoundEffectPlayer,
    onSelectRoom: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf(PlaygroundCategory.ALL) }

    val filteredRooms = remember(selectedCategory) {
        if (selectedCategory == PlaygroundCategory.ALL) {
            TODDLER_ROOMS
        } else {
            TODDLER_ROOMS.filter { it.category == selectedCategory }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .testTag("toddler_game_hub_dialog"),
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFFFFFDE7),
            border = BorderStroke(4.dp, Color(0xFFFFB300)),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // HEADER BAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFD54F),
                            border = BorderStroke(2.dp, Color(0xFFFF8F00))
                        ) {
                            Text(
                                text = "🎪",
                                fontSize = 28.sp,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Карта Игр 🌈",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF3E2723)
                            )
                            Text(
                                text = "Нажми на картинку — игра начнется!",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                        }
                    }

                    // Big chunky Close Button for toddlers
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFF5252),
                        border = BorderStroke(2.dp, Color(0xFFD32F2F)),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .size(52.dp)
                            .toddlerBounceClick(pressedScale = 0.86f) {
                                soundPlayer.playSqueak()
                                onDismiss()
                            }
                            .testTag("hub_close_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Enchanted Toy Hub Visual Hero Banner
                Card(
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(2.dp, Color(0xFFFFB300)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_toddler_hub_banner_1790908931677),
                            contentDescription = "Детская площадка",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Soft warm gradient overlay at the bottom with encouraging text
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0x99000000)
                                        )
                                    )
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🌈", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Выбирай свой волшебный мир и играй! ✨",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ==========================================
                // 🧩 THEMATIC BLOCK FILTER TABS (FOR 2-3 Y.O.)
                // ==========================================
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(PlaygroundCategory.values()) { cat ->
                        val isCatSelected = selectedCategory == cat
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isCatSelected) cat.primaryColor else Color.White,
                            border = BorderStroke(
                                width = if (isCatSelected) 2.5.dp else 1.5.dp,
                                color = if (isCatSelected) cat.darkColor else cat.primaryColor.copy(alpha = 0.5f)
                            ),
                            shadowElevation = if (isCatSelected) 4.dp else 1.dp,
                            modifier = Modifier
                                .toddlerBounceClick(pressedScale = 0.88f) {
                                    soundPlayer.triggerLightTapPulse()
                                    soundPlayer.playBubblePop()
                                    selectedCategory = cat
                                }
                                .testTag("hub_cat_${cat.name}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cat.emoji, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = cat.shortTitle,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isCatSelected) Color.White else cat.darkColor
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ==========================================
                // 3-COLUMN GRID OF GIANT TODDLER TOY CARDS
                // ==========================================
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(filteredRooms) { _, room ->
                        val isSelected = currentRoomIndex == room.index
                        val cat = room.category

                        // Bouncy Toddler Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(116.dp)
                                .toddlerBounceClick(pressedScale = 0.88f) {
                                    soundPlayer.triggerLightTapPulse()
                                    soundPlayer.playBubblePop()
                                    onSelectRoom(room.index)
                                    onDismiss()
                                }
                                .testTag(room.testTag),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 3.5.dp else 2.dp,
                                color = if (isSelected) Color(0xFFFF6F00) else room.borderColor
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (isSelected) 8.dp else 3.dp
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(room.bgColors)
                                    )
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // Giant Character Emoji
                                    Text(
                                        text = room.emoji,
                                        fontSize = 42.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    // Title
                                    Text(
                                        text = room.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF3E2723),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }

                                // Active Selection Ring / Star or Badge
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(22.dp),
                                    shape = CircleShape,
                                    color = if (isSelected) Color(0xFFFF6F00) else Color.White.copy(alpha = 0.90f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFFFFD54F) else room.borderColor.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = if (isSelected) "⭐" else room.badgeEmoji,
                                            fontSize = if (isSelected) 11.sp else 12.sp
                                        )
                                    }
                                }

                                // Block Category badge in top left
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(cat.primaryColor)
                                )
                            }
                        }
                    }
                }

                // BOTTOM BIG PLAY BUTTON
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFFFFB300),
                    border = BorderStroke(2.5.dp, Color(0xFFFF8F00)),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .toddlerBounceClick(pressedScale = 0.90f) {
                            soundPlayer.playBubblePop()
                            onDismiss()
                        }
                        .testTag("hub_continue_playing_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎮", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ПРОДОЛЖИТЬ ИГРАТЬ ▶️",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF3E2723)
                        )
                    }
                }
            }
        }
    }
}
