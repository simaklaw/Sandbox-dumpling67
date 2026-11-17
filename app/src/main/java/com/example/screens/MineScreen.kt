package com.example.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.GameRepository
import com.example.shared.*
import com.example.shared.i18n.Text
import com.example.ui.screens.MonAnim
import com.example.ui.screens.MonSpec
import com.example.ui.screens.drawMonster
import com.example.ui.screens.loadHouse
import com.example.ui.screens.loadWallet
import com.example.ui.screens.monLayout
import com.example.ui.screens.rememberMonImgs

/**
 * «Моё»: всё, что ребёнок накопил, в одном месте — монетки и рекорд, домик монстриков,
 * стикеры-награды и плюшевая коллекция. Нажал на монстрика — и он сразу бежит.
 */
@Composable
fun MineScreen(
    repository: GameRepository,
    soundManager: SoundManager,
    haptics: Haptics,
    onGoBack: () -> Unit,
    onOpenGame: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val house = remember { loadHouse(context) }
    val wallet = remember { loadWallet(context) }
    val best = remember { try { context.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE).getInt("mr_best", 0) } catch (e: Exception) { 0 } }
    val stickers by repository.allStickers.collectAsStateWithLifecycle(initialValue = emptyList())
    val mineInfo = FolderInfo("Моё", "⭐", ColorYellow)

    Box(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        PlayBackdrop(Modifier.matchParentSize(), tint = ColorYellow)
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                HomeButton(
                    modifier = Modifier.size(72.dp), size = 72.dp, haptics = haptics,
                    onClick = { soundManager.play("transition"); onGoBack() }
                )
                Spacer(Modifier.width(14.dp))
                WorldBanner(mineInfo, house.size + stickers.count { it.isUnlocked }, Modifier.weight(1f))
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MineStat("🪙", wallet.toString(), "монетки", ColorYellow, Modifier.weight(1f))
                    MineStat("🏆", "$best м", "рекорд забега", ColorOrange, Modifier.weight(1f))
                    MineStat("🏅", "${stickers.count { it.isUnlocked }}/${stickers.size}", "стикеры", ColorPink, Modifier.weight(1f))
                }

                MineSection("👾 Мои монстрики", ColorGreen) {
                    if (house.isEmpty()) {
                        Text(
                            "Домик пока пуст. Собери первого монстрика на фабрике!",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorInk, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        MineGoButton("🏭 На фабрику", ColorGreen, soundManager, haptics) { onOpenGame(32, "Монстрики и забег") }
                    } else {
                        house.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { spec ->
                                    MonThumb(spec, Modifier.weight(1f).height(86.dp), soundManager, haptics) {
                                        // этот монстрик становится героем забега и сразу бежит
                                        try {
                                            context.getSharedPreferences("playground_prefs", Context.MODE_PRIVATE).edit()
                                                .putString("mw_last", spec.encode())
                                                .putInt("mr_hero", house.indexOf(spec).coerceAtLeast(0)).apply()
                                        } catch (e: Exception) { /* не критично */ }
                                        onOpenGame(33, "Монстрики и забег")
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        MineGoButton("🏭 Собрать нового", ColorGreen, soundManager, haptics) { onOpenGame(32, "Монстрики и забег") }
                    }
                }

                MineSection("🏅 Стикеры-награды", ColorPink) {
                    if (stickers.isEmpty()) {
                        Text("Стикеры появятся, когда пробежишь первые метры в «Бешеном Дамплинге».", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorInk, textAlign = TextAlign.Center)
                    } else {
                        stickers.chunked(5).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { st ->
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.weight(1f).height(56.dp)
                                            .background(if (st.isUnlocked) Color.White else Color(0x33000000), RoundedCornerShape(16.dp))
                                            .border(BorderStroke(2.dp, ColorInk.copy(alpha = if (st.isUnlocked) 1f else 0.35f)), RoundedCornerShape(16.dp))
                                    ) { Text(if (st.isUnlocked) st.emoji else "❔", fontSize = 28.sp) }
                                }
                                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }

                MineSection("🧸 Плюшевая коллекция", ColorPurple) {
                    Text("Выигрывай игрушки в Хваталке — они попадут в альбом.", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorInk, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    MineGoButton("🕹️ В Хваталку", ColorPurple, soundManager, haptics) { onOpenGame(49, "Хваталка") }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MineStat(icon: String, value: String, label: String, color: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.5f), color)), shape)
            .border(BorderStroke(2.5.dp, ColorInk), shape)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Text(icon, fontSize = 24.sp)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = ColorInk, maxLines = 1)
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ColorInk.copy(alpha = 0.7f), maxLines = 1)
    }
}

@Composable
private fun MineSection(title: String, color: Color, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.72f), shape)
            .border(BorderStroke(3.dp, ColorInk), shape)
            .padding(12.dp)
    ) {
        Text(
            title, fontSize = 17.sp, fontWeight = FontWeight.Black, color = ColorInk,
            modifier = Modifier
                .background(color, RoundedCornerShape(16.dp))
                .border(BorderStroke(2.dp, ColorInk), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 3.dp)
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun MineGoButton(text: String, color: Color, soundManager: SoundManager, haptics: Haptics, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.45f), color)), shape)
            .border(BorderStroke(3.dp, ColorInk), shape)
            .plasticineClickable(haptics = haptics) { soundManager.play("pop"); onClick() }
    ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.Black, color = ColorInk) }
}

/** Карточка монстрика из домика: нарисован настоящими деталями фабрики, при касании подпрыгивает. */
@Composable
private fun MonThumb(spec: MonSpec, modifier: Modifier, soundManager: SoundManager, haptics: Haptics, onClick: () -> Unit) {
    val imgs = rememberMonImgs(spec)
    val anim = remember { MonAnim() }
    val d = LocalDensity.current.density
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .background(Color.White, shape)
            .border(BorderStroke(2.5.dp, ColorInk), shape)
            .plasticineClickable(haptics = haptics) { soundManager.play("pop"); onClick() }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val lay = monLayout(imgs, size.width / 2f, size.height - 4f * d, size.width * 0.9f, size.height * 0.88f)
            drawMonster(imgs, spec, lay, anim, 0f, d, 0.5f)
        }
    }
}
