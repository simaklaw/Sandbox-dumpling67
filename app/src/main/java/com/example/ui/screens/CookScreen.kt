package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.SoundManager
import com.example.shared.i18n.Text
import com.example.shared.rememberHaptics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Комната «Поваренок» (индекс 51, папка «Кухня»). Идея — из игры «Последняя смена» (Grok), но добрая и для малышей:
// гости — зверята, смена никогда не проигрывается, звёзды копятся, между сменами — подарок-улучшение, есть книга рецептов.
// Цепочка касаний (срабатывает сразу при касании, без «мимо»): продукт на ленте → доска (режь в золотой зоне) →
// сковорода (сними, пока дуга золотая) → тарелка → гость. Правила — в CookRules.kt (их проверяет CookRulesTest).

private const val CK_PREFS = "cook_prefs"
private const val CK_READY = 0
private const val CK_PLAY = 1
private const val CK_CLEAR = 2

private class CkPart(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val size: Float, val color: Color, val text: String? = null)
private class CkFloat(val x: Float, var y: Float, val text: String, var life: Float, val color: Color)

private class CkRef {
    var sim: CookSim? = null
    val parts = ArrayList<CkPart>()
    val floats = ArrayList<CkFloat>()
    var anim = 0f
    var idle = 0f
    var knife = 0f
    var shake = 0f
}

/** Раскладка кухни в логических единицах (ширина всегда 360). */
private class CkLayout(val lw: Float, val lh: Float, val seatsN: Int, val panN: Int) {
    fun seatX(i: Int) = if (seatsN == 2) (if (i == 0) 100f else 260f) else (62f + 118f * i)
    val guestY get() = lh * 0.175f
    val counterY get() = lh * 0.255f
    val plate get() = Offset(lw * 0.5f, lh * 0.345f)
    val trash get() = Offset(lw - 38f, lh * 0.345f)
    val hand get() = Offset(46f, lh * 0.345f)
    val rowY get() = lh * 0.53f
    val board get() = Offset(66f, rowY)
    val boardW = 104f
    val boardH = 84f
    fun panX(i: Int) = if (panN == 3) 158f + 79f * i else 188f + 102f * i
    val panR get() = if (panN == 3) 33f else 40f
    fun pan(i: Int) = Offset(panX(i), rowY)
    val beltY get() = lh * 0.80f
    val beltH = 64f
}

private fun lerpC(a: Color, b: Color, t: Float) = lerp(a, b, t.coerceIn(0f, 1f))

@Composable
fun CookScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val sm = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val prefs = remember { context.getSharedPreferences(CK_PREFS, Context.MODE_PRIVATE) }
    val measurer = rememberTextMeasurer(cacheSize = 160)
    val ref = remember { CkRef() }
    val profile = remember { CProfile.decode(try { prefs.getString("p", null) } catch (_: Exception) { null }) }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableIntStateOf(CK_READY) }
    var meta by remember { mutableIntStateOf(0) }        // растёт, когда меняется профиль (для перерисовки карточек)
    var starsUi by remember { mutableIntStateOf(0) }
    var leftUi by remember { mutableIntStateOf(0) }
    var showBook by remember { mutableStateOf(false) }
    var gifts by remember { mutableStateOf<List<CMod>>(emptyList()) }
    var banner by remember { mutableStateOf("") }
    var bannerT by remember { mutableFloatStateOf(0f) }
    var resStars by remember { mutableIntStateOf(0) }
    var resServed by remember { mutableIntStateOf(0) }
    var resLoved by remember { mutableIntStateOf(0) }
    var resGone by remember { mutableIntStateOf(0) }
    var resRating by remember { mutableIntStateOf(1) }
    var resRecipes by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        sm.preloadRaw(
            listOf(
                "kit_plop", "kit_chop_carrot_1", "kit_chop_carrot_2", "kit_chop_tomato_1", "kit_chop_cucumber_1", "kit_chop_hard_1", "kit_chop_hard_2",
                "kc_bell", "kc_sad", "kc_sigh", "kc_yum", "kc_munch1", "kc_sparkle", "kc_clink", "kc_bub1",
                "fx_sh_plate", "fx_sh_yum", "fx_sh_reward", "fx_sh_coin1", "fx_sh_bye", "fx_sh_wrong", "fx_sh_hello_bunny",
                "fx_sh_hello_bear", "fx_sh_hello_cat", "fx_sh_p_hello", "btn_tada"
            )
        )
    }

    fun snd(n: String, v: Float = 1f, r: Float = 1f) { sm.playFxRaw(n, v, r) }
    fun logicalH(): Float = if (boxSize.width > 0) boxSize.height * 360f / boxSize.width else 740f
    fun saveProfile() {
        try { prefs.edit().putString("p", profile.encode()).apply() } catch (_: Exception) { /* прогресс best-effort */ }
        meta++
    }

    fun burst(x: Float, y: Float, n: Int, colors: List<Color>, speed: Float) {
        for (i in 0 until n) {
            if (ref.parts.size > 260) break
            val a = Random.nextFloat() * 6.283f
            val sp = speed * (0.3f + Random.nextFloat() * 0.7f)
            ref.parts.add(CkPart(x, y, kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp - 30f, 1f, 3f + Random.nextFloat() * 4f, colors[Random.nextInt(colors.size)]))
        }
    }
    fun emojiBurst(x: Float, y: Float, text: String, n: Int) {
        for (i in 0 until n) {
            if (ref.parts.size > 260) break
            val a = -1.57f + (Random.nextFloat() - 0.5f) * 2.6f
            val sp = 90f + Random.nextFloat() * 110f
            ref.parts.add(CkPart(x, y, kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp, 1.2f, 18f, Color.White, text))
        }
    }
    fun floatText(x: Float, y: Float, t: String, c: Color) { ref.floats.add(CkFloat(x, y, t, 1.3f, c)) }
    fun say(t: String) { banner = t; bannerT = 2.2f }

    fun startShift() {
        ref.parts.clear(); ref.floats.clear(); ref.idle = 0f
        val s = CookSim(profile)
        ref.sim = s
        starsUi = 0; leftUi = s.totalGuests
        phase = CK_PLAY
        snd("kit_plop", 0.7f)
    }

    fun chopSound(f: CFam, good: Boolean) {
        val n = when (f) {
            CFam.VEG -> if (Random.nextBoolean()) "kit_chop_carrot_1" else "kit_chop_carrot_2"
            CFam.FRUIT -> "kit_chop_tomato_1"
            CFam.MUSH -> "kit_chop_cucumber_1"
            else -> if (Random.nextBoolean()) "kit_chop_hard_1" else "kit_chop_hard_2"
        }
        snd(n, if (good) 0.95f else 0.7f, if (good) 1f else 0.9f)
    }

    fun handleEvents(s: CookSim, L: CkLayout) {
        if (s.events.isEmpty()) return
        val evs = s.events.toList(); s.events.clear()
        for (e in evs) {
            when (e.type) {
                CEv.PICK, CEv.TAKE, CEv.BOARD_PUT -> snd("kit_plop", 0.8f, if (e.type == CEv.BOARD_PUT) 0.9f else 1f)
                CEv.CHOP_GOOD, CEv.CHOP_OK -> {
                    val fam = runCatching { CFam.valueOf(e.text) }.getOrDefault(CFam.VEG)
                    chopSound(fam, e.type == CEv.CHOP_GOOD)
                    ref.knife = 0.28f; haptics.vibrateShort()
                    val b = L.board
                    if (e.type == CEv.CHOP_GOOD) { burst(b.x, b.y, 12, listOf(Color(0xFFFFD54F), Color.White), 150f); floatText(b.x, b.y - 50f, "Ровно!", Color(0xFFFFD54F)) }
                    else floatText(b.x, b.y - 50f, "Чик!", Color.White)
                }
                CEv.PAN_PUT -> snd("kc_bub1", 0.7f)
                CEv.PAN_GOLD -> {
                    snd("kc_bell", 0.9f); haptics.vibrateShort()
                    val p = L.pan(e.idx); burst(p.x, p.y, 14, listOf(Color(0xFFFFD54F), Color.White), 160f)
                    floatText(p.x, p.y - 56f, "Готово!", Color(0xFFFFD54F))
                }
                CEv.PAN_BURN -> { snd("kc_sad", 0.5f); val p = L.pan(e.idx); floatText(p.x, p.y - 56f, "Дымок!", Color(0xFFBDBDBD)) }
                CEv.PLATE -> snd("fx_sh_plate", 0.8f)
                CEv.TRASH -> snd("kc_clink", 0.7f)
                CEv.WRONG -> snd("fx_sh_wrong", 0.35f)
                CEv.ARRIVE -> snd(e.text, 0.8f)
                CEv.LEAVE -> if (e.text == "happy") snd("fx_sh_bye", 0.7f)
                CEv.SERVE -> {
                    val x = L.seatX(e.idx); val y = L.guestY
                    val r = runCatching { CReact.valueOf(e.text) }.getOrDefault(CReact.MEH)
                    when (r) {
                        CReact.LOVED -> { snd("kc_yum", 1f); snd("fx_sh_reward", 0.8f); haptics.vibratePop(); emojiBurst(x, y, "⭐", 8); burst(x, y, 20, listOf(Color(0xFFFFD54F), Color(0xFFFF8BCB), Color.White), 260f) }
                        CReact.LIKED -> { snd("fx_sh_yum", 0.9f); haptics.vibrateShort(); emojiBurst(x, y, "⭐", 5) }
                        CReact.MEH -> { snd("kc_munch1", 0.8f); emojiBurst(x, y, "⭐", 2) }
                        CReact.SAD -> snd("kc_sigh", 0.5f)
                    }
                    if (e.stars > 0) { snd("fx_sh_coin1", 0.7f); floatText(x, y - 70f, "+${e.stars}⭐", Color(0xFFFFD54F)) }
                    else floatText(x, y - 70f, "Ещё разок!", Color.White)
                }
                CEv.RECIPE -> { snd("kc_sparkle", 0.9f); say("Новый рецепт: ${e.text}!") }
                CEv.CLEAR -> {
                    snd("btn_tada", 0.9f); haptics.vibratePop()
                    resStars = s.starsEarned; resServed = s.served; resLoved = s.loved; resGone = s.gone; resRating = s.rating
                    resRecipes = s.newRecipes.toList()
                    gifts = s.offerGifts()
                    saveProfile()
                    phase = CK_CLEAR
                }
            }
        }
    }

    fun tick(dt: Float) {
        ref.anim += dt
        if (ref.knife > 0f) ref.knife = max(0f, ref.knife - dt)
        if (bannerT > 0f) bannerT = max(0f, bannerT - dt)
        val s = ref.sim
        if (s != null && phase == CK_PLAY) {
            val L = CkLayout(360f, logicalH(), s.seatsN, s.panN)
            s.step(dt)
            ref.idle += dt
            handleEvents(s, L)
            if (s.starsEarned != starsUi) starsUi = s.starsEarned
            if (s.guestsLeft != leftUi) leftUi = s.guestsLeft
        }
        val pi = ref.parts.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            p.x += p.vx * dt; p.y += p.vy * dt; p.vy += 200f * dt; p.life -= dt * 1.2f
            if (p.life <= 0f) pi.remove()
        }
        val fi = ref.floats.iterator()
        while (fi.hasNext()) { val f = fi.next(); f.y -= 34f * dt; f.life -= dt; if (f.life <= 0f) fi.remove() }
        frame++
    }

    fun handleTap(x: Float, y: Float) {
        val s = ref.sim ?: return
        if (phase != CK_PLAY) return
        val L = CkLayout(360f, logicalH(), s.seatsN, s.panN)
        ref.idle = 0f
        fun d(ax: Float, ay: Float) = kotlin.math.hypot(x - ax, y - ay)
        // гости (шире: тянутся от окошка до прилавка)
        for (i in 0 until s.seatsN) if (abs(x - L.seatX(i)) < 56f && y > L.guestY - 70f && y < L.counterY + 20f) { s.tapGuest(i); return }
        // продукты на ленте
        val item = s.belt.minByOrNull { d(it.x, L.beltY) }
        if (item != null && d(item.x, L.beltY) < 46f && abs(y - L.beltY) < 56f) { s.tapBelt(item.id); return }
        for (i in 0 until s.panN) if (d(L.pan(i).x, L.pan(i).y) < L.panR + 12f) { s.tapPan(i); return }
        if (abs(x - L.board.x) < L.boardW / 2 + 6f && abs(y - L.board.y) < L.boardH / 2 + 30f) { s.tapBoard(); return }
        if (d(L.plate.x, L.plate.y) < 54f) { s.tapPlate(); return }
        if (d(L.trash.x, L.trash.y) < 40f) { s.tapTrash(); return }
    }

    LaunchedEffect(boxSize) {
        if (boxSize.width <= 0 || boxSize.height <= 0) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min(0.05f, (now - last) / 1_000_000_000f)
                last = now
                tick(dt)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .testTag("cook_root")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val sc = size.width / 360f
                        if (sc > 0f) handleTap(down.position.x / sc, down.position.y / sc)
                        do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed })
                    }
                }
        ) {
            frame.hashCode()
            if (size.width <= 0f) return@Canvas
            val sc = size.width / 360f
            val lh = size.height / sc
            withTransform({ scale(sc, sc, Offset.Zero) }) {
                drawCook(ref.sim, ref, CkLayout(360f, lh, ref.sim?.seatsN ?: 2, ref.sim?.panN ?: 2), measurer, phase == CK_PLAY)
            }
        }

        // ── HUD ──
        if (phase == CK_PLAY) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Смена ${profile.night}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF3A0728))
                Spacer(Modifier.weight(1f))
                Text("⭐ $starsUi", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF3A0728))
                Spacer(Modifier.weight(1f))
                Text("Гостей: $leftUi", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF3A0728))
            }
            if (bannerT > 0f) {
                Box(Modifier.fillMaxWidth().padding(top = 44.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        banner, fontSize = 17.sp, fontWeight = FontWeight.Black, color = Color(0xFF3A0728), textAlign = TextAlign.Center,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFE082)).border(3.dp, Color(0xFF3A0728), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // ── Старт смены ──
        if (phase == CK_READY && !showBook) {
            meta.hashCode()
            CkCard {
                Text("Поваренок 👨‍🍳", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFF3A0728), textAlign = TextAlign.Center)
                Text("Смена ${profile.night}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA83C7B))
                Spacer(Modifier.height(10.dp))
                Text(
                    "Зверята пришли поесть! Смотри, что они просят в облачке.\n" +
                        "1. Тапни продукт на ленте 🥕\n2. Тапни доску и ещё раз — режь, когда метка в золотой зоне 🔪\n" +
                        "3. Тапни сковородку, а потом ещё раз, пока дуга золотая ✨\n4. Положи на тарелку и тапни зверя 🍽️",
                    fontSize = 15.sp, color = Color(0xFF3A0728), textAlign = TextAlign.Start
                )
                Spacer(Modifier.height(14.dp))
                CkButton(if (profile.night == 1 && profile.stars == 0) "Открыть кухню!" else "Начать смену!", Color(0xFFFF8BCB)) { startShift() }
                Spacer(Modifier.height(8.dp))
                CkButton("📖 Книга рецептов ${profile.recipes.size}/$COOK_RECIPES_TOTAL", Color(0xFFFFE082)) { showBook = true }
                if (profile.stars > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("Всего звёзд: ${profile.stars}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA83C7B))
                }
            }
        }

        // ── Итоги смены и подарок ──
        if (phase == CK_CLEAR) {
            CkCard {
                Text("Смена окончена! 🎉", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color(0xFF3A0728), textAlign = TextAlign.Center)
                Text((1..3).joinToString(" ") { if (it <= resRating) "🌟" else "☆" }, fontSize = 34.sp)
                Text(
                    "Звёзд: $resStars · накормлено: $resServed\nОбожают: $resLoved" + if (resGone > 0) " · ушли ждать: $resGone" else "",
                    fontSize = 15.sp, color = Color(0xFF3A0728), textAlign = TextAlign.Center
                )
                if (resRecipes.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Новые рецепты: " + resRecipes.joinToString(", "), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA83C7B), textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(10.dp))
                if (gifts.isNotEmpty()) {
                    Text("Выбери подарок:", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF3A0728))
                    Spacer(Modifier.height(6.dp))
                    for (g in gifts) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFE082))
                                .border(3.dp, Color(0xFF3A0728), RoundedCornerShape(16.dp))
                                .clickable { ref.sim?.pickGift(g); saveProfile(); gifts = emptyList(); phase = CK_READY }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(g.emoji, fontSize = 30.sp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(g.title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF3A0728))
                                Text(g.body, fontSize = 13.sp, color = Color(0xFF3A0728))
                            }
                        }
                    }
                } else {
                    CkButton("Дальше", Color(0xFFFF8BCB)) { phase = CK_READY }
                }
            }
        }

        // ── Книга рецептов ──
        if (showBook) {
            meta.hashCode()
            CkCard {
                Text("📖 Книга рецептов", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF3A0728))
                Text("${profile.recipes.size} из $COOK_RECIPES_TOTAL. Рецепт открывается, когда зверёк обожает блюдо.", fontSize = 13.sp, color = Color(0xFF3A0728), textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    for ((key, name, icons) in ckAllRecipes()) {
                        val open = key in profile.recipes
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (open) icons else "❓", fontSize = 22.sp, modifier = Modifier.width(70.dp))
                            Text(if (open) name else "Ещё не открыт", fontSize = 15.sp, fontWeight = if (open) FontWeight.Bold else FontWeight.Normal, color = Color(0xFF3A0728))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                CkButton("Назад", Color(0xFFFF8BCB)) { showBook = false }
            }
        }
    }
}

private fun ckAllRecipes(): List<Triple<String, String, String>> {
    val out = ArrayList<Triple<String, String, String>>()
    val all = CFam.values()
    for (a in all) out.add(Triple(a.name.lowercase(), a.dish, a.emoji))
    for (i in all.indices) for (j in i until all.size) {
        val parts = listOf(CCarry(all[i], true, true, CDone.PERFECT), CCarry(all[j], true, true, CDone.PERFECT))
        out.add(Triple(cookDishKey(parts), cookDishName(parts), all[i].emoji + all[j].emoji))
    }
    return out
}

@Composable
private fun CkCard(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99101030)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFFFFF3E0))
                .border(4.dp, Color(0xFF3A0728), RoundedCornerShape(28.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun CkButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 220.dp, minHeight = 56.dp)
            .clip(CircleShape)
            .background(color)
            .border(4.dp, Color(0xFF3A0728), CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color(0xFF3A0728), textAlign = TextAlign.Center) }
}

// ───────────────────────── рисование ─────────────────────────

private fun DrawScope.ckEmoji(m: TextMeasurer, text: String, cx: Float, cy: Float, sz: Float, sc: Float = 1f, alpha: Float = 1f) {
    val res = m.measure(text, TextStyle(fontSize = sz.toSp()), maxLines = 1, softWrap = false)
    val tl = Offset(cx - res.size.width / 2f, cy - res.size.height / 2f)
    if (sc == 1f) drawText(textLayoutResult = res, topLeft = tl, alpha = alpha)
    else withTransform({ scale(sc, sc, Offset(cx, cy)) }) { drawText(textLayoutResult = res, topLeft = tl, alpha = alpha) }
}

private fun DrawScope.ckLabel(m: TextMeasurer, text: String, cx: Float, cy: Float, sz: Float, color: Color, alpha: Float = 1f) {
    val res = m.measure(text, TextStyle(fontSize = sz.toSp(), fontWeight = FontWeight.Black, color = color), maxLines = 1, softWrap = false)
    drawText(textLayoutResult = res, topLeft = Offset(cx - res.size.width / 2f, cy - res.size.height / 2f), alpha = alpha)
}

private fun doneTint(d: CDone?): Color? = when (d) {
    CDone.WARM -> Color(0xFFFFB74D); CDone.PERFECT -> Color(0xFFFFD54F); CDone.BURNT -> Color(0xFF616161); else -> null
}

private fun DrawScope.ckDish(m: TextMeasurer, c: CCarry, cx: Float, cy: Float, sz: Float, t: Float) {
    doneTint(c.done)?.let { drawCircle(it, sz * 0.62f, Offset(cx, cy), alpha = 0.45f) }
    if (c.chopped && c.done == null) {
        for (k in -1..1) ckEmoji(m, c.fam.emoji, cx + k * sz * 0.34f, cy + (if (k == 0) -2f else 4f), sz * 0.6f)
    } else ckEmoji(m, c.fam.emoji, cx, cy, sz)
    when (c.done) {
        CDone.PERFECT -> ckEmoji(m, "✨", cx + sz * 0.42f, cy - sz * 0.42f + 2f * sin(t * 6f), sz * 0.42f)
        CDone.BURNT -> ckEmoji(m, "💨", cx + sz * 0.42f, cy - sz * 0.42f, sz * 0.42f)
        else -> {}
    }
}

private fun DrawScope.drawCook(sim: CookSim?, ref: CkRef, L: CkLayout, m: TextMeasurer, playing: Boolean) {
    val w = L.lw; val h = L.lh; val t = ref.anim
    // стена-плитка
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2)), 0f, h), Offset.Zero, Size(w, h))
    var gx = 0f
    while (gx < w) { drawLine(Color(0x14A83C7B), Offset(gx, 0f), Offset(gx, L.counterY), 1.5f); gx += 40f }
    var gy = 0f
    while (gy < L.counterY) { drawLine(Color(0x14A83C7B), Offset(0f, gy), Offset(w, gy), 1.5f); gy += 40f }
    // прилавок-окошко
    drawRoundRect(Color(0xFFA1887F), Offset(-10f, L.counterY - 4f), Size(w + 20f, 34f), CornerRadius(10f))
    drawRoundRect(Color(0xFF6D4C41), Offset(-10f, L.counterY - 4f), Size(w + 20f, 34f), CornerRadius(10f), style = Stroke(3f))
    // пол и рабочая зона
    drawRect(Color(0xFFBCAAA4), Offset(0f, L.counterY + 30f), Size(w, h))
    drawRect(Color(0xFF8D6E63), Offset(0f, L.rowY + 70f), Size(w, 6f))

    // гости
    val seatsN = sim?.seatsN ?: L.seatsN
    for (i in 0 until seatsN) {
        val sx = L.seatX(i)
        val seat = sim?.seats?.getOrNull(i)
        val g = seat?.guest
        if (g != null && seat != null) {
            val bob = 3f * sin(t * 2.2f + i)
            val hop = if (seat.reactT > 0f && seat.react != CReact.SAD) -8f * abs(sin(t * 9f)) else 0f
            ckEmoji(m, g.emoji, sx, L.guestY + bob + hop, 60f)
            // облачко с желанием
            if (seat.reactT <= 0f) {
                drawRoundRect(Color.White, Offset(sx - 28f, L.guestY - 82f), Size(56f, 44f), CornerRadius(18f))
                drawRoundRect(Color(0xFF3A0728), Offset(sx - 28f, L.guestY - 82f), Size(56f, 44f), CornerRadius(18f), style = Stroke(2.5f))
                ckEmoji(m, g.want?.emoji ?: "🍽️", sx, L.guestY - 60f, 28f)
                // кольцо терпения
                val fr = (seat.patience / seat.max).coerceIn(0f, 1f)
                val col = if (fr > 0.5f) lerpC(Color(0xFFFFB74D), Color(0xFF66BB6A), (fr - 0.5f) * 2f) else lerpC(Color(0xFFE53935), Color(0xFFFFB74D), fr * 2f)
                drawArc(Color(0x33000000), -90f, 360f, false, Offset(sx - 38f, L.guestY - 38f + bob), Size(76f, 76f), style = Stroke(5f))
                drawArc(col, -90f, 360f * fr, false, Offset(sx - 38f, L.guestY - 38f + bob), Size(76f, 76f), style = Stroke(5f, cap = StrokeCap.Round))
            } else {
                val r = seat.react
                if (r != null) {
                    drawRoundRect(Color.White, Offset(sx - 46f, L.guestY - 92f), Size(92f, 42f), CornerRadius(18f))
                    drawRoundRect(Color(0xFF3A0728), Offset(sx - 46f, L.guestY - 92f), Size(92f, 42f), CornerRadius(18f), style = Stroke(2.5f))
                    ckEmoji(m, r.emoji, sx - 26f, L.guestY - 71f, 26f)
                    ckLabel(m, r.word, sx + 12f, L.guestY - 71f, 11f, Color(0xFF3A0728))
                }
            }
        } else {
            // пустое место: табличка
            ckEmoji(m, "🪑", sx, L.guestY + 8f, 34f, alpha = 0.5f)
        }
    }

    // тарелка
    val pl = L.plate
    drawCircle(Color(0x33000000), 44f, Offset(pl.x + 2f, pl.y + 6f))
    drawCircle(Color.White, 44f, pl)
    drawCircle(Color(0xFFB0BEC5), 44f, pl, style = Stroke(3f))
    drawCircle(Color(0xFFECEFF1), 30f, pl)
    val plateItems = sim?.plate
    plateItems?.forEachIndexed { i, c -> ckDish(m, c, pl.x + (if (plateItems.size == 1) 0f else if (i == 0) -17f else 17f), pl.y, 30f, t) }
    // корзина
    val tr = L.trash
    drawRoundRect(Color(0xFF90A4AE), Offset(tr.x - 22f, tr.y - 22f), Size(44f, 48f), CornerRadius(8f))
    drawRoundRect(Color(0xFF546E7A), Offset(tr.x - 26f, tr.y - 30f), Size(52f, 10f), CornerRadius(5f))
    ckEmoji(m, "🗑️", tr.x, tr.y, 28f, alpha = 0.9f)
    // руки
    val hp = L.hand
    drawCircle(Color(0x22000000), 34f, hp)
    drawCircle(Color(0xFFFFCC80), 34f, hp, style = Stroke(3f))
    val hand = sim?.hand
    if (hand != null) ckDish(m, hand, hp.x, hp.y + 2f * sin(t * 5f), 34f, t) else ckEmoji(m, "🤲", hp.x, hp.y, 30f, alpha = 0.55f)

    // доска
    val bp = L.board
    drawRoundRect(Color(0xFFD7A86E), Offset(bp.x - L.boardW / 2, bp.y - L.boardH / 2), Size(L.boardW, L.boardH), CornerRadius(14f))
    drawRoundRect(Color(0xFF8D6E63), Offset(bp.x - L.boardW / 2, bp.y - L.boardH / 2), Size(L.boardW, L.boardH), CornerRadius(14f), style = Stroke(3f))
    drawRoundRect(Color(0x22000000), Offset(bp.x - L.boardW / 2 + 8f, bp.y - L.boardH / 2 + 8f), Size(L.boardW - 16f, L.boardH - 16f), CornerRadius(10f))
    val bd = sim?.board
    if (bd != null) {
        val c = CCarry(bd.fam, bd.chopped, bd.fine, null)
        ckDish(m, c, bp.x, bp.y, 40f, t)
        if (!bd.chopped && sim != null) {
            val bx0 = bp.x - 50f; val by = bp.y + L.boardH / 2 + 18f
            drawRoundRect(Color(0xFF455A64), Offset(bx0, by - 7f), Size(100f, 14f), CornerRadius(7f))
            val half = sim.chopHalf * 100f
            drawRoundRect(Color(0xFFFFD54F), Offset(bx0 + 50f - half, by - 7f), Size(half * 2f, 14f), CornerRadius(7f))
            val mx = bx0 + sim.chopPos(bd) * 100f
            drawCircle(Color.White, 9f, Offset(mx, by)); drawCircle(Color(0xFF3A0728), 9f, Offset(mx, by), style = Stroke(2.5f))
            ckLabel(m, "Режь!", bp.x, bp.y - L.boardH / 2 - 10f, 15f, Color(0xFF3A0728))
        }
    } else ckEmoji(m, "🔪", bp.x, bp.y, 28f, alpha = 0.45f)
    if (ref.knife > 0f) {
        val k = ref.knife / 0.28f
        ckEmoji(m, "🔪", bp.x + 22f, bp.y - 20f - 36f * k, 34f)
    }

    // плита и сковородки
    val panN = sim?.panN ?: L.panN
    drawRoundRect(Color(0xFF455A64), Offset(L.panX(0) - L.panR - 18f, L.rowY - L.panR - 14f), Size(L.panX(panN - 1) - L.panX(0) + L.panR * 2 + 36f, L.panR * 2 + 40f), CornerRadius(18f))
    for (i in 0 until panN) {
        val pc = L.pan(i); val r = L.panR
        val food = sim?.pans?.getOrNull(i)?.food
        drawCircle(if (food != null) Color(0xFFFF7043) else Color(0xFF37474F), r + 5f, Offset(pc.x, pc.y + 4f), alpha = if (food != null) 0.55f else 1f)
        drawCircle(Color(0xFF263238), r, pc)
        drawCircle(Color(0xFF455A64), r - 5f, pc)
        drawRoundRect(Color(0xFF263238), Offset(pc.x + r - 4f, pc.y - 5f), Size(26f, 10f), CornerRadius(5f))
        if (food != null && sim != null) {
            val done = cookDoneAt(food.t, sim.goldWindow)
            val c = CCarry(food.fam, food.chopped, food.fine, null)
            val wob = if (done == CDone.PERFECT) 3f * sin(t * 14f) else 0f
            ckDish(m, c, pc.x + wob * 0.3f, pc.y + wob * 0.2f, r * 0.95f, t)
            // дуга готовности
            val total = CookCfg.PERFECT_START + sim.goldWindow + 1.6f
            val ar = r + 11f
            val tl = Offset(pc.x - ar, pc.y - ar); val sz = Size(ar * 2f, ar * 2f)
            val a0 = 135f; val sweep = 270f
            fun seg(from: Float, to: Float, col: Color, wd: Float) =
                drawArc(col, a0 + sweep * (from / total), sweep * ((to - from) / total), false, tl, sz, style = Stroke(wd))
            seg(0f, CookCfg.RAW_END, Color(0xFFCFD8DC), 6f)
            seg(CookCfg.RAW_END, CookCfg.PERFECT_START, Color(0xFFFFB74D), 6f)
            seg(CookCfg.PERFECT_START, CookCfg.PERFECT_START + sim.goldWindow, Color(0xFFFFD54F), if (done == CDone.PERFECT) 10f else 7f)
            seg(CookCfg.PERFECT_START + sim.goldWindow, total, Color(0xFF424242), 6f)
            val ang = (a0 + sweep * (min(food.t, total) / total)) * (PI.toFloat() / 180f)
            drawCircle(Color.White, 6f, Offset(pc.x + ar * kotlin.math.cos(ang), pc.y + ar * kotlin.math.sin(ang)))
            if (done == CDone.PERFECT) drawCircle(Color(0xFFFFD54F), r + 20f + 3f * sin(t * 12f), pc, alpha = 0.18f)
            if (done == CDone.BURNT) ckEmoji(m, "💨", pc.x + 6f * sin(t * 3f), pc.y - r - 22f - 6f * ((t * 2f) % 1f), 24f, alpha = 0.8f)
        }
    }

    // лента
    val by = L.beltY
    drawRoundRect(Color(0xFF455A64), Offset(-10f, by - L.beltH / 2), Size(w + 20f, L.beltH), CornerRadius(12f))
    drawRoundRect(Color(0xFF78909C), Offset(-10f, by - L.beltH / 2), Size(w + 20f, L.beltH), CornerRadius(12f), style = Stroke(4f))
    var sx = -((t * CookCfg.BELT_SPEED) % 30f)
    sx += 30f
    while (sx < w + 20f) { drawLine(Color(0x33FFFFFF), Offset(sx, by - L.beltH / 2 + 6f), Offset(sx - 8f, by + L.beltH / 2 - 6f), 3f); sx += 30f }
    sim?.belt?.forEach { b ->
        drawCircle(Color(0x33FFFFFF), 27f, Offset(b.x, by))
        ckEmoji(m, b.fam.emoji, b.x, by + 2f * sin(t * 4f + b.id), 38f)
    }
    ckLabel(m, "лента", 28f, by + L.beltH / 2 + 14f, 11f, Color(0x887A5A4E))

    // подсказка, если завис
    if (playing && sim != null && ref.idle > 3.5f) {
        val hint = sim.hint()
        if (hint != null) {
            val p: Offset? = when (hint.kind) {
                CHint.BELT -> sim.belt.firstOrNull { it.id == hint.idx }?.let { Offset(it.x, by) }
                CHint.BOARD -> L.board
                CHint.PAN -> L.pan(hint.idx)
                CHint.PLATE -> L.plate
                CHint.GUEST -> Offset(L.seatX(hint.idx), L.guestY)
                else -> L.trash
            }
            if (p != null) {
                val pulse = 0.5f + 0.5f * sin(t * 6f)
                drawCircle(Color(0xFFFFD54F), 40f + 6f * pulse, p, alpha = 0.35f)
                drawCircle(Color(0xFFFFD54F), 40f + 6f * pulse, p, style = Stroke(4f))
                ckEmoji(m, "👆", p.x + 14f, p.y + 40f + 6f * pulse, 28f)
            }
        }
    }

    // частицы и всплывающие надписи
    for (p in ref.parts) {
        val a = p.life.coerceIn(0f, 1f)
        if (p.text != null) ckEmoji(m, p.text, p.x, p.y, p.size, alpha = a) else drawCircle(p.color, p.size, Offset(p.x, p.y), alpha = a)
    }
    for (f in ref.floats) ckLabel(m, f.text, f.x, f.y, 17f, f.color, alpha = f.life.coerceIn(0f, 1f))
}
