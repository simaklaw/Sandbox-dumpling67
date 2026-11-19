package com.example.screens

import com.example.R
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shared.*
import com.example.shared.i18n.Text

/** Шесть «миров» главного экрана: игры сгруппированы по тому, что делает ребёнок. */
enum class FolderType {
    ADVENTURE, SHOOTING, CREATIVE, HOME, NATURE, CALM
}

/** Как мир выглядит на главном экране и в шапке папки. */
data class FolderInfo(val title: String, val icon: String, val color: Color)

val FOLDER_INFO: Map<FolderType, FolderInfo> = mapOf(
    FolderType.ADVENTURE to FolderInfo("Приключения", "🗺️", ColorGreen),
    FolderType.SHOOTING to FolderInfo("Меткость", "🎯", ColorOrange),
    FolderType.CREATIVE to FolderInfo("Творчество", "🎨", ColorPink),
    FolderType.HOME to FolderInfo("Дом и кухня", "🍳", ColorYellow),
    FolderType.NATURE to FolderInfo("Зверята", "🐾", ColorBlue),
    FolderType.CALM to FolderInfo("Антистресс", "🫧", ColorPurple)
)

data class FolderItem(
    val title: String,
    val icon: String,
    val pageIndex: Int,
    val color: Color,
    val iconRes: Int? = null
)

/** Раздел внутри мира: игры одного жанра лежат рядом под общим заголовком. */
data class FolderSection(
    val title: String,
    val icon: String,
    val color: Color,
    val items: List<FolderItem>
)


/**
 * Единый источник правды: какие игры лежат в каких мирах и разделах.
 * Индексы 25 («Кто в коробке?») и 45 («Гардероб героев») выведены из использования: их здесь нет и быть не должно.
 */
val FOLDER_SECTIONS: Map<FolderType, List<FolderSection>> = mapOf(
    FolderType.ADVENTURE to listOf(
        FolderSection("Приключения", "🗺️", ColorGreen, listOf(
            FolderItem("Зелик: Приключение", "🔫", 28, ColorGreen, R.drawable.room_28),
            FolderItem("Комната Зелика", "👽", 27, ColorGreen, R.drawable.room_27),
            FolderItem("Монстрики и забег", "👾", 32, ColorOrange, R.drawable.room_32),
            FolderItem("Бешеный Дамплинг 67", "🥟", 5, ColorYellow, R.drawable.room_5),
            FolderItem("Жадная акула", "🦈", 35, ColorBlue, R.drawable.room_35),
            FolderItem("Птичка-летунья", "🐦", 44, ColorGreen, R.drawable.room_44),
            FolderItem("Ракетный робот", "🤖", 50, ColorBlue, R.drawable.room_50),
            FolderItem("Гипертрасса", "🚀", 52, ColorPurple)
        ))
    ),
    FolderType.SHOOTING to listOf(
        FolderSection("Меткость", "🎯", ColorOrange, listOf(
            FolderItem("Тир", "🦆", 31, ColorOrange, R.drawable.room_31),
            FolderItem("Лук", "🏹", 4, ColorPink, R.drawable.room_4),
            FolderItem("Шарики", "🎈", 7, ColorBlue, R.drawable.room_7),
            FolderItem("Пинбол", "🔮", 12, ColorYellow, R.drawable.room_12),
            FolderItem("Пиньята с Конфетами", "🪅", 17, ColorPink, R.drawable.room_17),
            FolderItem("Блок-пазл", "🧱", 48, ColorBlue, R.drawable.room_48),
            FolderItem("Хваталка", "🕹️", 49, ColorPurple, R.drawable.room_49)
        ))
    ),
    FolderType.CREATIVE to listOf(
        FolderSection("Творчество", "🎨", ColorPink, listOf(
            FolderItem("Рисование", "🎨", 9, ColorGreen, R.drawable.room_9),
            FolderItem("Кинетический песок", "🏖️", 22, ColorOrange, R.drawable.room_22),
            FolderItem("Песочница с формочками", "🏖️", 40, ColorYellow, R.drawable.room_40),
            FolderItem("Фигурки-окошки", "🔷", 26, ColorGreen),
            FolderItem("Сундучки-сюрпризы", "🎁", 46, ColorOrange, R.drawable.room_46)
        ))
    ),
    FolderType.HOME to listOf(
        FolderSection("Кухня", "🍳", ColorPurple, listOf(
            FolderItem("Кормление", "🍎", 1, ColorPink, R.drawable.room_1),
            FolderItem("Кастрюля чудес", "🍲", 37, ColorOrange, R.drawable.room_37),
            FolderItem("Нарезка овощей", "🔪", 36, ColorGreen, R.drawable.room_36),
            FolderItem("Сыпучести", "🌾", 6, ColorYellow, R.drawable.room_6),
            FolderItem("Поваренок", "👨‍🍳", 51, ColorPink, R.drawable.room_51)
        )),
        FolderSection("Хозяйство и магазин", "🧺", ColorBlue, listOf(
            FolderItem("Пузырьковая стиралка", "🫧", 38, ColorBlue, R.drawable.room_38),
            FolderItem("Автомойка для игрушек", "🚗", 41, ColorOrange, R.drawable.room_41),
            FolderItem("Магазин пельменей", "🛒", 42, ColorPink, R.drawable.room_42)
        ))
    ),
    FolderType.NATURE to listOf(
        FolderSection("Зверята", "🐾", ColorBlue, listOf(
            FolderItem("Зверята", "🐮", 0, ColorPurple, R.drawable.room_0),
            FolderItem("Пушистики", "🐹", 47, ColorPink, R.drawable.room_47),
            FolderItem("Аквариум", "🐠", 30, ColorBlue, R.drawable.room_30),
            FolderItem("Дождик", "🌧️", 24, ColorBlue, R.drawable.room_24),
            FolderItem("Погодная комната", "🌦️", 43, ColorBlue, R.drawable.room_43)
        ))
    ),
    FolderType.CALM to listOf(
        FolderSection("Антистресс", "🫧", ColorPurple, listOf(
            FolderItem("Смешинки", "😜", 21, ColorYellow, R.drawable.room_21),
            FolderItem("Детский телефончик", "📱", 23, ColorPink, R.drawable.room_23),
            FolderItem("Кнопочная радость", "🔴", 34, ColorPink, R.drawable.room_34),
            FolderItem("Поп-ит Антистресс", "🟣", 16, ColorPurple, R.drawable.room_16),
            FolderItem("Пузырчатая пленка", "📦", 20, ColorBlue, R.drawable.room_20),
            FolderItem("Бизиборд", "🎛️", 10, ColorGreen),
            FolderItem("Музыкальная колыбелька", "🌙", 39, ColorPurple, R.drawable.room_39)
        ))
    )
)

val FOLDER_ITEMS: Map<FolderType, List<FolderItem>> =
    FOLDER_SECTIONS.mapValues { (_, secs) -> secs.flatMap { it.items } }

/** Секции мира только с теми играми, которые доступны; пустые секции скрыты (ребёнок не видит «закрытого»). */
fun visibleSections(folderType: FolderType, canPlay: (Int) -> Boolean): List<FolderSection> =
    FOLDER_SECTIONS.getValue(folderType)
        .map { sec -> sec.copy(items = sec.items.filter { canPlay(it.pageIndex) }) }
        .filter { it.items.isNotEmpty() }

/** Миры, в которых есть хотя бы одна доступная игра (порядок — как на главном экране). */
fun visibleWorlds(canPlay: (Int) -> Boolean): List<FolderType> =
    FolderType.values().filter { visibleSections(it, canPlay).isNotEmpty() }

/** В каком мире лежит игра с этим индексом (null — игры нет в каталоге). */
fun folderTypeForPage(pageIndex: Int): FolderType? =
    FOLDER_ITEMS.entries.firstOrNull { (_, list) -> list.any { it.pageIndex == pageIndex } }?.key

/** Карточка игры из каталога по индексу (для «Продолжить»). */
fun findFolderItem(pageIndex: Int): FolderItem? =
    FOLDER_ITEMS.values.flatten().firstOrNull { it.pageIndex == pageIndex }

/** Последние игры ребёнка: хранятся в настройках, самая свежая первой. */
object RecentGames {
    private const val KEY = "recent_games"
    private const val MAX = 4

    fun load(ctx: Context): List<Int> = try {
        (ctx.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE).getString(KEY, "") ?: "")
            .split(";").mapNotNull { it.toIntOrNull() }.filter { findFolderItem(it) != null }.distinct().take(MAX)
    } catch (e: Exception) { emptyList() }

    fun push(ctx: Context, pageIndex: Int) {
        if (findFolderItem(pageIndex) == null) return
        try {
            val list = (listOf(pageIndex) + load(ctx)).distinct().take(MAX)
            ctx.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE).edit().putString(KEY, list.joinToString(";")).apply()
        } catch (e: Exception) { /* не критично */ }
    }
}

@Composable
fun FolderScreen(
    modifier: Modifier = Modifier,
    title: String,
    folderType: FolderType,
    onNavigateToGame: (Int, String) -> Unit,
    onGoBack: () -> Unit,
    soundManager: SoundManager,
    haptics: Haptics = rememberHaptics(),
    canPlay: (Int) -> Boolean = { true },
    // Состояние прокрутки приходит снаружи: экран мира пересоздаётся при входе в игру и обратно,
    // а позиция должна остаться (раньше список возвращался в самое начало).
    gridState: LazyGridState = rememberLazyGridState()
) {
    val sections = remember(folderType, canPlay) { visibleSections(folderType, canPlay) }
    val info = FOLDER_INFO.getValue(folderType)
    val count = sections.sumOf { it.items.size }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        PlayBackdrop(Modifier.matchParentSize(), tint = info.color)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Шапка: «домой» и баннер мира цвета этого мира
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeButton(
                    modifier = Modifier.size(72.dp),
                    size = 72.dp,
                    haptics = haptics,
                    onClick = {
                        soundManager.play("transition")
                        onGoBack()
                    }
                )
                Spacer(modifier = Modifier.width(14.dp))
                WorldBanner(info, count, Modifier.weight(1f))
            }

            LazyVerticalGrid(
                state = gridState,
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

/** Баннер мира: крупный значок, название и сколько игр внутри. */
@Composable
fun WorldBanner(info: FolderInfo, count: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(72.dp)
            .background(Brush.verticalGradient(listOf(lerp(info.color, Color.White, 0.45f), info.color)), shape)
            .border(BorderStroke(3.dp, ColorInk), shape)
            .padding(horizontal = 14.dp)
    ) {
        Text(info.icon, fontSize = 38.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(info.title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = ColorInk, maxLines = 1)
            Text("игр: $count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorInk.copy(alpha = 0.7f))
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
