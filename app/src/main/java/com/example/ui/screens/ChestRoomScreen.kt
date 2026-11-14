package com.example.ui.screens

import com.example.shared.withFrameNanosEco
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.LocalAgeProfile
import com.example.shared.SoundManager
import com.example.shared.i18n.Text
import com.example.shared.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Комната «Сундучки-сюрпризы»: 6 тематических сундучков (ферма, море, клад, космос, зима, кафе).
// Тапаешь — сундучок трясётся и открывается, внутри предмет для коллекции. Дубликаты дают звёздочки,
// после двух повторов подряд обязательно выпадает новинка. Собрал коллекцию — получаешь кубок.

private const val CH_PREFS = "chest_prefs"

private class ChP(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val size: Float, val col: Int, val kind: Int) { val maxLife = life }

private class ChModel {
    var clock = 0f
    var theme = 0
    var taps = 0
    var shake = 0f
    var state = 0            // 0 сундучок, 1 показан предмет
    var revealT = 0f
    var cur = -1
    var isNew = false
    var rare = false
    var dupStreak = 0
    var stars = 0
    var opens = 0
    var done = 0             // биты завершённых коллекций
    val masks = LongArray(7)
    val parts = ArrayList<ChP>()
    var shownAt = 0f
}

private fun chBits(x: Long) = java.lang.Long.bitCount(x)
private fun chIsRare(res: String) = Regex("golden|ruby|diamond|rainbow|treasure|trophy|crystal|egg|gold|star").containsMatchIn(res)

private val CH_BG = listOf(
    listOf(Color(0xFFB8E6A0), Color(0xFFF1F9D8)), listOf(Color(0xFF8ED8F5), Color(0xFFE3F6FF)), listOf(Color(0xFFFFE39A), Color(0xFFFFF6D6)),
    listOf(Color(0xFF2A2060), Color(0xFF6B4BB8)), listOf(Color(0xFFBFE3FF), Color(0xFFF2FAFF)), listOf(Color(0xFFF3D9C0), Color(0xFFFFF1E4)), listOf(Color(0xFFFFD6E8), Color(0xFFFFF6D9))
)

private fun DrawScope.chSpark(x: Float, y: Float, r: Float, col: Color) {
    val p = Path().apply {
        moveTo(x, y - r); quadraticTo(x + r * 0.12f, y - r * 0.12f, x + r, y)
        quadraticTo(x + r * 0.12f, y + r * 0.12f, x, y + r); quadraticTo(x - r * 0.12f, y + r * 0.12f, x - r, y)
        quadraticTo(x - r * 0.12f, y - r * 0.12f, x, y - r); close()
    }
    drawPath(p, col)
}

@Composable
fun ChestRoomScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val sm = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val toddler = LocalAgeProfile.current.isToddler2
    val prefs = remember { context.getSharedPreferences(CH_PREFS, Context.MODE_PRIVATE) }
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        val l = ArrayList<String>()
        l.addAll(listOf("fx_magic_sparkle", "btn_tada", "star_collect", "fx_squeak", "btn_pop"))
        for (i in 1..4) { l.add("fx_it_pck_${8 + i}"); l.add("fx_it_pck_${12 + i}"); l.add("fx_it_pck_$i"); l.add("fx_it_inv_${8 + i}"); l.add("fx_it_drg_${i}") }
        for (i in 9..12) l.add("fx_it_dst_$i")
        sm.preloadRaw(l)
    }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(0) }
    val m = remember {
        ChModel().also { md ->
            for (i in 0..6) md.masks[i] = prefs.getLong("m$i", 0L)
            md.stars = prefs.getInt("stars", 0); md.opens = prefs.getInt("opens", 0); md.done = prefs.getInt("done", 0); md.theme = prefs.getInt("theme", 0).coerceIn(0, 6)
        }
    }
    var themeUi by remember { mutableIntStateOf(m.theme) }
    var starsUi by remember { mutableIntStateOf(m.stars) }
    var opensUi by remember { mutableIntStateOf(m.opens) }
    var doneUi by remember { mutableIntStateOf(m.done) }
    var masksUi by remember { mutableStateOf(m.masks.copyOf()) }
    var showAlbum by remember { mutableStateOf(false) }
    var cheerTheme by remember { mutableStateOf<Int?>(null) }
    var stateUi by remember { mutableIntStateOf(0) }
    var curUi by remember { mutableIntStateOf(-1) }
    var newUi by remember { mutableStateOf(false) }
    var rareUi by remember { mutableStateOf(false) }

    // кэш картинок
    val cache = remember { mutableStateMapOf<String, ImageBitmap>() }
    fun resId(n: String) = context.resources.getIdentifier(n, "drawable", context.packageName)
    suspend fun loadAll(names: List<String>) {
        withContext(Dispatchers.Default) {
            for (n in names) if (!cache.containsKey(n)) {
                val id = resId(n); if (id == 0) continue
                val bmp = runCatching { android.graphics.BitmapFactory.decodeResource(context.resources, id)?.asImageBitmap() }.getOrNull()
                if (bmp != null) cache[n] = bmp
            }
        }
    }
    LaunchedEffect(themeUi) {
        loadAll(CH_THEMES.map { it.chest } + CH_THEMES[themeUi].items.map { it.res })
    }
    LaunchedEffect(showAlbum) { if (showAlbum) loadAll(CH_THEMES.flatMap { t -> t.items.map { it.res } }) }

    fun W() = boxSize.width.toFloat()
    fun H() = boxSize.height.toFloat()
    fun snd(n: String, v: Float = 1f, r: Float = 1f) { sm.play(n, volume = v, rate = r) }
    fun chestC() = Offset(W() / 2f, H() * 0.33f)
    fun chestSize() = min(W() * 0.55f, H() * 0.30f)
    fun needTaps() = if (toddler) 1 else 3

    fun spark(x: Float, y: Float, n: Int, spd: Float, life: Float, rainbow: Boolean) {
        val cols = intArrayOf(0xFFFF7A7A.toInt(), 0xFFFFB866.toInt(), 0xFFFFE066.toInt(), 0xFF8EDB7A.toInt(), 0xFF6CC4F2.toInt(), 0xFFB78BEA.toInt())
        for (i in 0 until n) {
            val a = Random.nextFloat() * 6.283f; val sp = spd * (0.3f + Random.nextFloat() * 0.7f)
            if (m.parts.size < 300) m.parts.add(ChP(x, y, cos(a) * sp, sin(a) * sp - spd * 0.2f, life * (0.6f + Random.nextFloat() * 0.6f), H() * (0.008f + Random.nextFloat() * 0.012f), if (rainbow) cols[Random.nextInt(6)] else 0xFFFFE066.toInt(), if (rainbow) 1 else 0))
        }
    }
    fun save() {
        val e = prefs.edit()
        for (i in 0..6) e.putLong("m$i", m.masks[i])
        e.putInt("stars", m.stars).putInt("opens", m.opens).putInt("done", m.done).putInt("theme", m.theme).apply()
    }
    fun pick(): Int {
        val th = CH_THEMES[m.theme]
        val mask = m.masks[m.theme]
        val missing = th.items.indices.filter { (mask and (1L shl it)) == 0L }
        if (missing.isNotEmpty() && m.dupStreak >= 2) return missing.random()
        // редкие выпадают реже
        val w = th.items.map { if (chIsRare(it.res)) 0.45f else 1f }
        var r = Random.nextFloat() * w.sum()
        for (i in w.indices) { r -= w[i]; if (r <= 0f) return i }
        return th.items.size - 1
    }
    fun open() {
        val th = CH_THEMES[m.theme]
        val idx = pick()
        val isNew = (m.masks[m.theme] and (1L shl idx)) == 0L
        m.cur = idx; m.isNew = isNew; m.rare = chIsRare(th.items[idx].res)
        m.state = 1; m.revealT = 0f; m.shownAt = m.clock; m.taps = 0
        m.opens++
        if (isNew) { m.masks[m.theme] = m.masks[m.theme] or (1L shl idx); m.dupStreak = 0 } else { m.dupStreak++; m.stars++ }
        starsUi = m.stars; opensUi = m.opens; masksUi = m.masks.copyOf()
        stateUi = 1; curUi = idx; newUi = isNew; rareUi = m.rare
        snd("fx_it_pck_${9 + Random.nextInt(4)}", 0.55f, 1.1f)
        snd("fx_it_pck_${13 + Random.nextInt(4)}", 0.9f)
        if (m.rare) { snd("fx_ui_unlock", 0.6f); snd("fx_it_dst_${9 + Random.nextInt(4)}", 0.5f); snd("fx_magic_sparkle", 0.8f) }
        snd(if (isNew) "fx_it_inv_${9 + Random.nextInt(4)}" else "fx_it_pck_${1 + Random.nextInt(4)}", 0.8f)
        haptics.vibratePop()
        val c = chestC()
        spark(c.x, c.y, if (m.rare) 40 else 22, W() * 0.55f, 1.2f, m.rare || isNew)
        if (chBits(m.masks[m.theme]) == th.items.size && (m.done and (1 shl m.theme)) == 0) {
            m.done = m.done or (1 shl m.theme); doneUi = m.done; cheerTheme = m.theme
            snd("btn_tada", 0.95f); snd("fx_ui_levelup", 0.8f)
            spark(W() / 2f, H() * 0.3f, 40, W() * 0.6f, 1.5f, true)
        }
        save()
    }
    fun tapChest() {
        if (m.state == 1) {
            if (m.clock - m.shownAt > 0.5f) { m.state = 0; stateUi = 0; snd("fx_squeak", 0.5f, 1.3f) }
            return
        }
        m.taps++; m.shake = 0.35f
        snd("fx_it_drg_${1 + Random.nextInt(4)}", 0.7f, 0.9f + m.taps * 0.12f)
        snd("btn_pop", 0.5f, 0.8f + m.taps * 0.15f)
        haptics.vibrateShort()
        spark(chestC().x, chestC().y, 5, W() * 0.25f, 0.6f, false)
        if (m.taps >= needTaps()) open()
    }
    fun setTheme(i: Int) {
        if (m.theme == i) return
        m.theme = i; themeUi = i; m.state = 0; stateUi = 0; m.taps = 0; save()
        snd("fx_ui_tab", 0.7f)
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanosEco { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
                last = now
                m.clock += dt
                m.shake = max(0f, m.shake - dt)
                if (m.state == 1) m.revealT = min(1f, m.revealT + dt / 0.7f)
                var i = m.parts.size - 1
                while (i >= 0) {
                    val p = m.parts[i]; p.x += p.vx * dt; p.y += p.vy * dt
                    if (p.kind == 1) p.vy += H() * 0.5f * dt
                    p.life -= dt; if (p.life <= 0f) m.parts.removeAt(i); i--
                }
                frame = (frame + 1) and 0xFFFFFF
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { boxSize = it }) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (!showAlbum && cheerTheme == null) tapChest()
                }
            }
        ) {
            frame.hashCode()
            val w = size.width; val h = size.height
            if (w <= 0f) return@Canvas
            val t = m.clock
            drawRect(Brush.verticalGradient(CH_BG[themeUi], 0f, h), Offset.Zero, Size(w, h))
            // фоновые искорки
            for (i in 0 until 14) {
                val x = ((i * 0.618f + 0.1f) % 1f) * w; val y = ((i * 0.377f + 0.05f + t * 0.01f * (1 + i % 3)) % 1f) * h * 0.6f
                chSpark(x, y, h * (0.007f + 0.004f * (i % 3)), Color.White.copy(alpha = 0.35f + 0.35f * sin(t * 2f + i)))
            }
            val c = chestC(); val cs = chestSize()
            val rv = if (m.state == 1) m.revealT else 0f
            // лучи за предметом
            if (rv > 0f) {
                val rc = if (m.rare) Color(0xFFFFD54F) else Color(0xFFFFFFFF)
                rotate(t * 25f, c) {
                    for (k in 0 until 10) {
                        val a = k * 36f
                        withTransform({ rotate(a, c) }) {
                            drawPath(Path().apply { moveTo(c.x, c.y); lineTo(c.x - cs * 0.12f, c.y - cs * 1.15f); lineTo(c.x + cs * 0.12f, c.y - cs * 1.15f); close() }, rc.copy(alpha = 0.22f * rv))
                        }
                    }
                }
            }
            // сундучок
            val chestImg = cache[CH_THEMES[themeUi].chest]
            if (chestImg != null) {
                val shake = if (m.shake > 0f) sin(m.shake * 70f) * 10f * (m.shake / 0.35f) else 0f
                val bounce = sin(t * 2.4f) * cs * 0.02f
                val alpha = 1f - rv
                val pulse = if (m.state == 0 && opensUi == 0) 1f + 0.05f * sin(t * 6f) else 1f
                drawOval(Color(0x33000000), Offset(c.x - cs * 0.4f, c.y + cs * 0.46f), Size(cs * 0.8f, cs * 0.12f))
                withTransform({ rotate(shake, Offset(c.x, c.y + cs * 0.4f)) }) {
                    drImg(chestImg, c.x, c.y + bounce, cs * pulse / chestImg.width, alpha)
                }
            }
            // предмет
            if (m.state == 1 && m.cur >= 0) {
                val img = cache[CH_THEMES[themeUi].items[m.cur].res]
                if (img != null) {
                    val e = rv; val back = 1f + 2.2f * (e - 1f) * (e - 1f) * (e - 1f) + 1.2f * (e - 1f) * (e - 1f)
                    val s = cs * 1.15f * back.coerceIn(0f, 1.2f)
                    drImg(img, c.x, c.y - cs * 0.05f - e * cs * 0.1f, s / img.width, 1f)
                }
            }
            for (p in m.parts) {
                val lf = (p.life / p.maxLife).coerceIn(0f, 1f)
                if (p.kind == 0) chSpark(p.x, p.y, p.size * (0.4f + lf), Color(p.col).copy(alpha = min(1f, lf * 1.6f)))
                else drawRect(Color(p.col).copy(alpha = lf), Offset(p.x, p.y), Size(p.size, p.size * 0.6f))
            }
        }

        // верхняя панель
        Row(
            Modifier.align(Alignment.TopStart).padding(top = 10.dp, start = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val si = cache["ci_sim_gold_coin"]
                Canvas(Modifier.size(24.dp)) { if (si != null) drImg(si, size.width / 2, size.height / 2, size.width / si.width) }
                Spacer(Modifier.width(6.dp))
                Text("$starsUi", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFE066))
            }
            Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val si = cache["ci_sim_silver_trophy_cup"]
                Canvas(Modifier.size(24.dp)) { if (si != null) drImg(si, size.width / 2, size.height / 2, size.width / si.width) }
                Spacer(Modifier.width(6.dp))
                Text("${Integer.bitCount(doneUi)}/${CH_THEMES.size}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp).size(52.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFFFE9A0), Color(0xFFFFB400)))).border(3.dp, Color.White, CircleShape)
                .clickable { showAlbum = true; snd("fx_ui_menu_open", 0.7f) },
            Alignment.Center
        ) {
            val bi = cache["ci_fishing_bestiary_book_with_fish"]
            Canvas(Modifier.size(34.dp)) { if (bi != null) drImg(bi, size.width / 2, size.height / 2, size.width / bi.width) }
        }

        // подпись предмета / подсказка
        val th = CH_THEMES[themeUi]
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = with(density) { (H() * 0.53f).toDp() }, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (stateUi == 1 && curUi >= 0) {
                val dark = themeUi == 3
                Text(th.items[curUi].name, fontSize = 24.sp, fontWeight = FontWeight.Black, color = if (dark) Color.White else Color(0xFF2F4A6A), textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(if (newUi) Color(0xFFFF8A3D) else Color(0xFF7C8CA3)).padding(horizontal = 14.dp, vertical = 4.dp)) {
                    Text(if (newUi) (if (rareUi) "Новое! Редкая находка!" else "Новое!") else "Уже есть — +1 монетка", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Spacer(Modifier.height(4.dp))
                Text("Тапни, чтобы продолжить", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (dark) Color(0xFFD0C8F0) else Color(0xFF6B7A90))
            } else if (opensUi < 2) {
                Box(Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xE6FFFFFF)).border(2.dp, Color(0xFF6FB6E8), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text("Тапай по сундучку — внутри сюрприз!", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8), textAlign = TextAlign.Center)
                }
            }
        }

        // выбор сундучка
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xE6FFFFFF))
                .border(3.dp, Color(0xFF6FB6E8), RoundedCornerShape(24.dp)).padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for (i in 0..6) {
                val tt = CH_THEMES[i]
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (themeUi == i) Color(0xFFFFE9A0) else Color.Transparent).clickable { setTheme(i) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val im = cache[tt.chest]
                    Canvas(Modifier.size(42.dp)) { if (im != null) drImg(im, size.width / 2, size.height / 2, size.width / im.width) }
                    Text(tt.title, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8), maxLines = 1)
                    Row {
                        Text("${chBits(masksUi[i])}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7A90))
                        Text("/${tt.items.size}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7A90))
                    }
                }
            }
        }

        // альбом
        if (showAlbum) {
            Box(Modifier.fillMaxSize().background(Color(0xCC102040)).clickable { showAlbum = false; snd("fx_ui_menu_close", 0.7f) }, Alignment.Center) {
                Column(
                    Modifier.padding(12.dp).fillMaxWidth().fillMaxHeight(0.88f).clip(RoundedCornerShape(28.dp)).background(Color(0xFFF2F8FF))
                        .border(5.dp, Color(0xFF6FB6E8), RoundedCornerShape(28.dp)).clickable(enabled = false) {}.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Моя коллекция", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8))
                    Spacer(Modifier.height(6.dp))
                    LazyVerticalGrid(columns = GridCells.Adaptive(76.dp), modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for ((ti, tt) in CH_THEMES.withIndex()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(tt.title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8))
                                    Spacer(Modifier.width(8.dp))
                                    Text("${chBits(masksUi[ti])}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE0780A))
                                    Text("/${tt.items.size}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE0780A))
                                    if ((doneUi and (1 shl ti)) != 0) { Spacer(Modifier.width(8.dp)); Text("Собрано!", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF3CA55C)) }
                                }
                            }
                            items(tt.items.size) { k ->
                                val own = (masksUi[ti] and (1L shl k)) != 0L
                                val im = cache[tt.items[k].res]
                                Column(Modifier.clip(RoundedCornerShape(12.dp)).background(if (own) Color(0xFFDDEEFF) else Color(0xFFE8EBF0)).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Canvas(Modifier.size(56.dp)) {
                                        if (im != null) drImg(im, size.width / 2, size.height / 2, size.width / im.width, if (own) 1f else 0.9f, if (own) null else ColorFilter.tint(Color(0xFF8A94A4), BlendMode.SrcIn))
                                    }
                                    if (own) Text(tt.items[k].name, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8), textAlign = TextAlign.Center, maxLines = 2, lineHeight = 10.sp)
                                    else Text("?", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF8A94A4))
                                }
                            }
                        }
                    }
                }
            }
        }

        // коллекция собрана
        val ct = cheerTheme
        if (ct != null) {
            Box(Modifier.fillMaxSize().background(Color(0xAA102040)).clickable { cheerTheme = null }, Alignment.Center) {
                Column(
                    Modifier.padding(24.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFFF2F8FF)).border(5.dp, Color(0xFF6FB6E8), RoundedCornerShape(28.dp)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Коллекция собрана!", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF3F8FE0))
                    val tr = cache["ci_sim_silver_trophy_cup"]
                    Canvas(Modifier.size(150.dp)) { if (tr != null) drImg(tr, size.width / 2, size.height / 2, size.width / tr.width) }
                    Text(CH_THEMES[ct].title, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8))
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF3F8FE0)).padding(horizontal = 28.dp, vertical = 8.dp)) {
                        Text("Ура!", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
            }
        }
    }
}
