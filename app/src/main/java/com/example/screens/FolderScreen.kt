package com.example.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.*

enum class FolderType {
    GAMES, KITCHEN
}

data class FolderItem(
    val title: String,
    val icon: String,
    val pageIndex: Int,
    val color: Color
)

/**
 * Единый источник правды: какие игры лежат в каких папках.
 * Используется и экраном папки, и навигацией «Назад/Домой» (см. [folderTypeForPage]).
 */
val FOLDER_ITEMS: Map<FolderType, List<FolderItem>> = mapOf(
    FolderType.GAMES to listOf(
        FolderItem("Комната Зелика", "👽", 27, ColorGreen),
        FolderItem("Зелик: Приключение", "🔫", 28, ColorGreen),
        FolderItem("Аквариум", "🐠", 30, ColorBlue),
        FolderItem("Дождик", "🌧️", 24, ColorBlue),
        FolderItem("Кто в коробке?", "🎁", 25, ColorPink),
        FolderItem("Фигурки-окошки", "🔷", 26, ColorGreen),
        FolderItem("Детский телефончик", "📱", 23, ColorPink),
        FolderItem("Кинетический песок", "🏖️", 22, ColorOrange),
        FolderItem("Смешинки", "😜", 21, ColorYellow),
        FolderItem("Пузырчатая пленка", "📦", 20, ColorPurple),
        FolderItem("Шарики с Водой", "🫧", 19, ColorBlue),
        FolderItem("Мялка Смайлик", "🤪", 18, ColorYellow),
        FolderItem("Пиньята с Конфетами", "🪅", 17, ColorPink),
        FolderItem("Поп-ит Антистресс", "🟣", 16, ColorPurple),
        FolderItem("Космические Сокровища", "🚀", 15, ColorPurple),
        FolderItem("День Малыша", "🍼", 14, ColorYellow),
        FolderItem("Лук", "🏹", 4, ColorPink),
        FolderItem("Тир", "🦆", 31, ColorOrange),
        FolderItem("Монстрики", "👾", 32, ColorOrange),
        FolderItem("Забег монстрика", "🏃", 33, ColorGreen),
        FolderItem("Пинбол", "🔮", 12, ColorYellow),
        FolderItem("Бизиборд", "🎛️", 10, ColorGreen),
        FolderItem("Раннер 67", "🏃‍♂️", 5, ColorBlue),
        FolderItem("Зверята", "🐮", 0, ColorPurple)
    ),
    FolderType.KITCHEN to listOf(
        FolderItem("Сыпучести", "🌾", 6, ColorYellow)
    )
)

/** В какой папке лежит игра с этим индексом (null — игра открывается прямо с главного экрана). */
fun folderTypeForPage(pageIndex: Int): FolderType? =
    FOLDER_ITEMS.entries.firstOrNull { (_, list) -> list.any { it.pageIndex == pageIndex } }?.key

@Composable
fun FolderScreen(
    modifier: Modifier = Modifier,
    title: String,
    folderType: FolderType,
    onNavigateToGame: (Int, String) -> Unit,
    onGoBack: () -> Unit,
    soundManager: SoundManager,
    haptics: Haptics = rememberHaptics()
) {
    val items = remember(folderType) { FOLDER_ITEMS.getValue(folderType) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header zone with Back/Home button & Folder Icon (No text)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home Button at top left (80 dp)
                HomeButton(
                    modifier = Modifier.size(80.dp),
                    haptics = haptics,
                    onClick = {
                        soundManager.play("transition")
                        onGoBack()
                    }
                )

                Spacer(modifier = Modifier.width(20.dp))

                // Folder Icon without text title
                Text(
                    text = if (folderType == FolderType.GAMES) "🎪" else "🍯",
                    fontSize = 38.sp
                )
            }

            // Grid of sub-games: 2 columns of 140dp square cards
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                itemsIndexed(items) { index, item ->
                    FolderGameCard(
                        item = item,
                        phaseOffset = index * 200,
                        soundManager = soundManager,
                        haptics = haptics,
                        onClick = {
                            onNavigateToGame(item.pageIndex, item.title)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FolderGameCard(
    item: FolderItem,
    phaseOffset: Int,
    soundManager: SoundManager,
    haptics: Haptics,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "folderGameBreath")
    val scaleAnim by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = phaseOffset, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(140.dp)
            .graphicsLayer {
                scaleX = scaleAnim
                scaleY = scaleAnim
            }
            .border(BorderStroke(4.dp, ColorInk), ShapeCard)
            .background(item.color, ShapeCard)
            .padding(bottom = 6.dp) // Depth shadow effect
            .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.5f)), ShapeCard)
            .clip(ShapeCard)
            .plasticineClickable(haptics = haptics) {
                soundManager.play("pop")
                onClick()
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = item.icon,
                fontSize = 48.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Text(
                text = item.title,
                style = PlayTypography.bodyLarge,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = ColorInk
            )
        }
    }
}
