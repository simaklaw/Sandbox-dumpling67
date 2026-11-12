package com.example.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.example.shared.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    val color: Color,
    val iconRes: Int? = null
)

/** Раздел внутри папки: игры одного жанра лежат рядом под общим заголовком. */
data class FolderSection(
    val title: String,
    val icon: String,
    val color: Color,
    val items: List<FolderItem>
)

private val DUMPLING_ICON = com.example.R.drawable.pelmen_icon

/**
 * Единый источник правды: какие игры лежат в каких папках и в каких разделах.
 * Индексы 25 («Кто в коробке?») и 45 («Гардероб героев») выведены из использования: их здесь нет и быть не должно.
 */
val FOLDER_SECTIONS: Map<FolderType, List<FolderSection>> = mapOf(
    FolderType.GAMES to listOf(
        FolderSection("Бегалки и приключения", "🏃", ColorGreen, listOf(
            FolderItem("Зелик: Приключение", "🔫", 28, ColorGreen),
            FolderItem("Комната Зелика", "👽", 27, ColorGreen),
            FolderItem("Забег монстрика", "🏃", 33, ColorOrange),
            FolderItem("Бешеный Дамплинг 67", "🥟", 5, ColorYellow, DUMPLING_ICON),
            FolderItem("Жадная акула", "🦈", 35, ColorBlue),
            FolderItem("Птичка-летунья", "🐦", 44, ColorGreen)
        )),
        FolderSection("Меткость и аркады", "🎯", ColorOrange, listOf(
            FolderItem("Тир", "🦆", 31, ColorOrange),
            FolderItem("Лук", "🏹", 4, ColorPink),
            FolderItem("Пинбол", "🔮", 12, ColorYellow),
            FolderItem("Пиньята с Конфетами", "🪅", 17, ColorPink),
            FolderItem("Блок-пазл", "🧱", 48, ColorBlue),
            FolderItem("Хваталка", "🕹️", 49, ColorPurple)
        )),
        FolderSection("Кнопочки, поп-иты, бизиборд", "🔴", ColorPink, listOf(
            FolderItem("Кнопочная радость", "🔴", 34, ColorPink),
            FolderItem("Поп-ит Антистресс", "🟣", 16, ColorPurple),
            FolderItem("Пузырчатая пленка", "📦", 20, ColorBlue),
            FolderItem("Бизиборд", "🎛️", 10, ColorGreen),
            FolderItem("Детский телефончик", "📱", 23, ColorPink)
        )),
        FolderSection("Зверята и природа", "🐾", ColorBlue, listOf(
            FolderItem("Зверята", "🐮", 0, ColorPurple),
            FolderItem("Аквариум", "🐠", 30, ColorBlue),
            FolderItem("Дождик", "🌧️", 24, ColorBlue),
            FolderItem("Погодная комната", "🌦️", 43, ColorBlue),
            FolderItem("Пушистики", "🐹", 47, ColorPink)
        )),
        FolderSection("Творчество и забавы", "🎨", ColorPurple, listOf(
            FolderItem("Монстрики", "👾", 32, ColorOrange),
            FolderItem("Фигурки-окошки", "🔷", 26, ColorGreen),
            FolderItem("Кинетический песок", "🏖️", 22, ColorOrange),
            FolderItem("Смешинки", "😜", 21, ColorYellow),
            FolderItem("Песочница с формочками", "🏖️", 40, ColorYellow),
            FolderItem("Сундучки-сюрпризы", "🎁", 46, ColorOrange)
        )),
        FolderSection("Хозяйство и магазин", "🧺", ColorBlue, listOf(
            FolderItem("Пузырьковая стиралка", "🫧", 38, ColorBlue),
            FolderItem("Автомойка для игрушек", "🚗", 41, ColorOrange),
            FolderItem("Магазин пельменей", "🛒", 42, ColorPink),
            FolderItem("Музыкальная колыбелька", "🌙", 39, ColorPurple)
        ))
    ),
    FolderType.KITCHEN to listOf(
        FolderSection("Кухня", "🍳", ColorPurple, listOf(
            FolderItem("Кастрюля чудес", "🍲", 37, ColorOrange),
            FolderItem("Нарезка овощей", "🔪", 36, ColorGreen),
            FolderItem("Сыпучести", "🌾", 6, ColorYellow)
        ))
    )
)

val FOLDER_ITEMS: Map<FolderType, List<FolderItem>> =
    FOLDER_SECTIONS.mapValues { (_, secs) -> secs.flatMap { it.items } }

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
    val sections = remember(folderType) { FOLDER_SECTIONS.getValue(folderType) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFFBF1), ColorBg, ColorBgSoft)))
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

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                sections.forEachIndexed { si, sec ->
                    val base = sections.take(si).sumOf { it.items.size }
                    if (sections.size > 1) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(sec)
                        }
                    }
                    itemsIndexed(sec.items) { i, item ->
                        val idx = base + i
                        FolderGameCard(
                            item = item,
                            phaseOffset = idx * 200,
                            soundManager = soundManager,
                            haptics = haptics,
                            onClick = { onNavigateToGame(item.pageIndex, item.title) }
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(sec: FolderSection) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(sec.color, RoundedCornerShape(20.dp))
                .border(BorderStroke(2.5.dp, ColorInk), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(sec.icon, fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Text(sec.title, fontSize = 17.sp, fontWeight = FontWeight.Black, color = ColorInk)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(4.dp)
                .background(sec.color.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
        )
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
    CandyCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(158.dp),
        title = item.title,
        icon = item.icon,
        iconRes = item.iconRes,
        cardColor = item.color,
        phaseOffset = phaseOffset,
        iconSize = 46.dp,
        titleSize = 16,
        soundManager = soundManager,
        haptics = haptics,
        onClick = onClick
    )
}
