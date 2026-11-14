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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.LocalAgeProfile
import com.example.shared.LoopHandle
import com.example.shared.SoundManager
import com.example.shared.i18n.Text
import com.example.shared.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Комната «Пушистики»: во дворике живут выдра, хомяк и котик (милые стикеры из набора). Их можно гладить,
// кормить любимым угощением (перетащи яблоко, рыбку или ракушку), укладывать спать ночью и прятать от дождя.
// Награды: 6 наклеек за заботу.

private const val PY_PREFS = "petyard_prefs"
private val PY_NAMES = listOf("Выдра Плюша", "Хомяк Пончик", "Котик Мурзик")
// фото: 0 выдра, 1 хомяк, 2 котик
private val PY_IDLE = arrayOf(intArrayOf(1, 7), intArrayOf(4, 14), intArrayOf(10, 15))
private val PY_HAPPY = arrayOf(intArrayOf(2), intArrayOf(8, 12), intArrayOf(13))
private val PY_EAT = intArrayOf(9, 6, 3)
private val PY_SLEEP = intArrayOf(5, 14, 15)
private val PY_FOOD = listOf("ci_farm_red_apple" to 1, "ci_fishing_small_blue_fish" to 2, "ci_fishing_sea_shell" to 0) // еда -> любимчик
private val PY_STICKER_PICS = intArrayOf(6, 3, 9, 5, 11, 8)

private class PyS(val id: Int) {
    var state = 0       // 0 покой, 1 радость, 2 ест, 3 спит
    var t = 0f
    var idleI = 0
    var idleT = 2f + Random.nextFloat() * 3f
    var love = 0
    var fed = 0
    var fav = 0
    var jump = 0f
    var shake = 0f
}

private class PyP(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val size: Float, val col: Int, val kind: Int) { val maxLife = life }

private class PySticker(val id: Int, val name: String, val note: String, val how: String)

private val PY_STICKERS = listOf(
    PySticker(0, "Первое «ням»", "Сердечки сыплются чаще", "Покорми любого питомца"),
    PySticker(1, "Все сыты", "Питомцы сами машут тебе", "Покорми всех троих"),
    PySticker(2, "Любимое угощение", "Радуга над двориком", "Угости питомцев их любимым лакомством"),
    PySticker(3, "Тихая ночь", "Светлячки в ночном дворике", "Уложи всех спать и подожди"),
    PySticker(4, "Дождливый день", "Радужные капли дождя", "Пусть пойдёт дождик"),
    PySticker(5, "Большая семья", "Конфетти при поглаживании", "Подружись со всеми питомцами")
)

private fun DrawScope.pyHeart(x: Float, y: Float, r: Float, col: Color) {
    val p = Path().apply {
        moveTo(x, y + r * 0.9f)
        cubicTo(x - r * 1.6f, y - r * 0.2f, x - r * 0.7f, y - r * 1.3f, x, y - r * 0.4f)
        cubicTo(x + r * 0.7f, y - r * 1.3f, x + r * 1.6f, y - r * 0.2f, x, y + r * 0.9f)
        close()
    }
    drawPath(p, col)
}

private fun DrawScope.pyZ(x: Float, y: Float, r: Float, col: Color) {
    val p = Path().apply { moveTo(x - r, y - r); lineTo(x + r, y - r); lineTo(x - r, y + r); lineTo(x + r, y + r) }
    drawPath(p, col, style = Stroke(r * 0.35f, cap = StrokeCap.Round))
}

private fun DrawScope.pyStar(x: Float, y: Float, r: Float, col: Color) {
    val p = Path().apply {
        moveTo(x, y - r); quadraticTo(x + r * 0.12f, y - r * 0.12f, x + r, y)
        quadraticTo(x + r * 0.12f, y + r * 0.12f, x, y + r); quadraticTo(x - r * 0.12f, y + r * 0.12f, x - r, y)
        quadraticTo(x - r * 0.12f, y - r * 0.12f, x, y - r); close()
    }
    drawPath(p, col)
}

@Composable
fun PetYardScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val sm = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val toddler = LocalAgeProfile.current.isToddler2
    val prefs = remember { context.getSharedPreferences(PY_PREFS, Context.MODE_PRIVATE) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        sm.preloadRaw(listOf("fx_cat_meow", "animal_cat_happy", "fx_cat_purr", "fx_squeak", "btn_pop", "chew_1", "chew_2", "pet_munch_1", "pet_munch_2", "cr_heart", "cr_snore",
            "fx_sh_yum", "fx_baby_giggle", "btn_tada", "fx_wr_night", "fx_ui_unlock", "fx_ui_levelup", "fx_ui_menu_open", "fx_ui_menu_close", "fx_ui_on", "fx_ui_off", "fx_ui_coin", "fx_ui_locked", "fx_it_inv_9", "fx_it_inv_10", "fx_it_pck_13", "fx_it_pck_14"))
    }

    val cache = remember { mutableStateMapOf<String, ImageBitmap>() }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            val names = (1..15).map { "pet_%02d".format(it) } + PY_FOOD.map { it.first }
            for (n in names) {
                val id = context.resources.getIdentifier(n, "drawable", context.packageName); if (id == 0) continue
                runCatching { android.graphics.BitmapFactory.decodeResource(context.resources, id)?.asImageBitmap() }.getOrNull()?.let { cache[n] = it }
            }
        }
    }
    fun pic(i: Int) = cache["pet_%02d".format(i)]

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(0) }
    val pets = remember { Array(3) { PyS(it) }.also { a -> for (i in 0..2) { a[i].love = prefs.getInt("love$i", 0); a[i].fed = prefs.getInt("fed$i", 0); a[i].fav = prefs.getInt("fav$i", 0) } } }
    val parts = remember { ArrayList<PyP>() }
    val pending = remember { ArrayList<Int>() }
    var mask by remember { mutableIntStateOf(prefs.getInt("mask", 0)) }
    var night by remember { mutableStateOf(false) }
    var rain by remember { mutableStateOf(false) }
    var clock by remember { mutableFloatStateOf(0f) }
    var nightSecs by remember { mutableFloatStateOf(prefs.getInt("nightS", 0).toFloat()) }
    var rainSecs by remember { mutableFloatStateOf(prefs.getInt("rainS", 0).toFloat()) }
    var feedTotal by remember { mutableIntStateOf(prefs.getInt("feedT", 0)) }
    var favTotal by remember { mutableIntStateOf(prefs.getInt("favT", 0)) }
    var loveUi by remember { mutableIntStateOf(pets.sumOf { it.love }) }
    var showAlbum by remember { mutableStateOf(false) }
    var reward by remember { mutableStateOf<PySticker?>(null) }
    var dragFood by remember { mutableIntStateOf(-1) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var toast by remember { mutableStateOf("") }
    var toastT by remember { mutableFloatStateOf(0f) }
    val loops = remember { HashMap<String, LoopHandle>() }

    fun W() = boxSize.width.toFloat()
    fun H() = boxSize.height.toFloat()
    fun snd(n: String, v: Float = 1f, r: Float = 1f) { sm.play(n, volume = v, rate = r) }
    fun groundY() = H() * 0.64f
    fun petSize() = min(W() * 0.31f, H() * 0.27f)
    fun petX(i: Int) = W() * (0.19f + 0.31f * i)
    fun trayY() = H() * 0.86f
    fun trayX(k: Int) = W() * (0.25f + 0.25f * k)
    fun need(i: Int) = when (i) { 0 -> 1; 1 -> 3; 2 -> if (toddler) 3 else 6; 3 -> if (toddler) 4 else 8; 4 -> if (toddler) 4 else 8; else -> if (toddler) 15 else 30 }

    fun save() {
        val e = prefs.edit()
        for (i in 0..2) e.putInt("love$i", pets[i].love).putInt("fed$i", pets[i].fed).putInt("fav$i", pets[i].fav)
        e.putInt("mask", mask).putInt("nightS", nightSecs.toInt()).putInt("rainS", rainSecs.toInt()).putInt("feedT", feedTotal).putInt("favT", favTotal).apply()
    }
    fun showNext() {
        if (pending.isEmpty()) return
        val id = pending.removeAt(0)
        reward = PY_STICKERS[id]
        snd("fx_ui_unlock", 0.9f); snd(if (id == 5) "btn_tada" else "fx_it_pck_${13 + Random.nextInt(2)}", 0.8f)
        haptics.vibratePop()
    }
    fun checkRewards() {
        val ok = booleanArrayOf(
            feedTotal >= 1, pets.all { it.fed > 0 }, favTotal >= need(2), nightSecs >= need(3), rainSecs >= need(4), pets.all { it.love >= need(5) }
        )
        var changed = false
        for (i in 0..5) if (ok[i] && (mask and (1 shl i)) == 0) { mask = mask or (1 shl i); pending.add(i); changed = true }
        if (changed) { save(); if (reward == null) showNext() }
    }
    fun hearts(x: Float, y: Float, n: Int) {
        val k = if ((mask and 1) != 0) n * 2 else n
        for (i in 0 until k) if (parts.size < 250) parts.add(PyP(x + (Random.nextFloat() - 0.5f) * petSize() * 0.4f, y, (Random.nextFloat() - 0.5f) * W() * 0.18f, -H() * (0.10f + Random.nextFloat() * 0.12f), 1.3f, H() * (0.012f + Random.nextFloat() * 0.012f), 0xFFFF6B8A.toInt(), 0))
    }
    fun confetti(x: Float, y: Float) {
        val cols = intArrayOf(0xFFFF7A7A.toInt(), 0xFFFFB866.toInt(), 0xFFFFE066.toInt(), 0xFF8EDB7A.toInt(), 0xFF6CC4F2.toInt(), 0xFFB78BEA.toInt())
        for (i in 0 until 18) if (parts.size < 250) parts.add(PyP(x, y, (Random.nextFloat() - 0.5f) * W() * 0.7f, -H() * (0.1f + Random.nextFloat() * 0.3f), 1.2f, H() * 0.012f, cols[Random.nextInt(6)], 1))
    }
    fun petCry(i: Int) {
        when (i) {
            2 -> snd(if (Random.nextBoolean()) "fx_cat_meow" else "animal_cat_happy", 0.9f)
            1 -> snd("fx_squeak", 0.9f, 1.5f)
            else -> { snd("fx_baby_giggle", 0.6f, 1.3f); snd("fx_squeak", 0.6f, 1.1f) }
        }
    }
    fun pet(i: Int) {
        val p = pets[i]
        if (p.state == 3) { p.shake = 0.5f; snd("cr_snore", 0.4f, 1.2f); return }
        p.state = 1; p.t = 1.6f; p.jump = 0.5f; p.idleI = Random.nextInt(2)
        p.love++; loveUi = pets.sumOf { it.love }
        petCry(i); if (i == 2) snd("fx_cat_purr", 0.5f)
        snd("cr_heart", 0.5f)
        haptics.vibratePop()
        hearts(petX(i), groundY() - petSize() * 0.9f, 4)
        if ((mask and 32) != 0) confetti(petX(i), groundY() - petSize() * 0.8f)
        checkRewards(); if (p.love % 5 == 0) save()
    }
    fun feed(i: Int, food: Int) {
        val p = pets[i]
        if (p.state == 3) { p.shake = 0.5f; snd("fx_ui_locked", 0.6f); toast = "Тсс, спит!"; toastT = 1.5f; return }
        val fav = PY_FOOD[food].second == i
        p.state = 2; p.t = 2.0f
        p.fed++; feedTotal++
        if (fav) { favTotal++; p.fav++; p.love += 3; hearts(petX(i), groundY() - petSize() * 0.9f, 7); snd("fx_sh_yum", 0.8f); toast = "Любимое угощение!"; toastT = 1.6f }
        else { p.love += 1; hearts(petX(i), groundY() - petSize() * 0.9f, 3); snd("btn_pop", 0.6f) }
        snd(listOf("chew_1", "chew_2", "pet_munch_1", "pet_munch_2")[Random.nextInt(4)], 0.8f)
        loveUi = pets.sumOf { it.love }
        haptics.vibrateSquish()
        checkRewards(); save()
    }
    fun setNight(v: Boolean) {
        night = v; snd(if (v) "fx_ui_off" else "fx_ui_on", 0.8f)
        if (v) snd("fx_wr_night", 0.5f)
        for (p in pets) { if (v) { p.state = 3; p.t = 0f } else if (p.state == 3) { p.state = 0; p.t = 0f } }
    }
    fun setRain(v: Boolean) {
        rain = v; snd("fx_ui_menu_open", 0.6f)
        if (v) { if (loops["rain_loop"] == null) sm.startLoop("rain_loop")?.let { loops["rain_loop"] = it } }
        else { loops.remove("rain_loop")?.let { sm.stopLoop(it, 300) } }
        loops["rain_loop"]?.let { sm.setLoopVolume(it, 0.4f) }
    }
    DisposableEffect(Unit) { onDispose { loops.values.forEach { sm.stopLoop(it, 40) }; loops.clear() } }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) { loops.values.forEach { sm.stopLoop(it, 60) }; loops.clear(); rain = false } }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    fun poseFor(i: Int): Int {
        val p = pets[i]
        return when {
            p.state == 3 -> PY_SLEEP[i]
            p.state == 2 -> PY_EAT[i]
            p.state == 1 -> PY_HAPPY[i][p.idleI % PY_HAPPY[i].size]
            i == 0 && rain -> 11
            else -> PY_IDLE[i][p.idleI % PY_IDLE[i].size]
        }
    }

    fun update(dt: Float) {
        if (W() <= 0f) return
        clock += dt
        toastT = max(0f, toastT - dt)
        val overlay = showAlbum || reward != null
        if (overlay) return
        for (p in pets) {
            p.shake = max(0f, p.shake - dt); p.jump = max(0f, p.jump - dt)
            if (p.state == 1 || p.state == 2) { p.t -= dt; if (p.t <= 0f) p.state = if (night) 3 else 0 }
            if (p.state == 0) {
                p.idleT -= dt
                if (p.idleT <= 0f) {
                    p.idleT = 4f + Random.nextFloat() * 4f; p.idleI = Random.nextInt(3)
                    if ((mask and 2) != 0 && Random.nextFloat() < 0.5f) { p.state = 1; p.t = 1.4f; p.idleI = Random.nextInt(2); petCry(p.id); hearts(petX(p.id), groundY() - petSize() * 0.9f, 2) }
                }
            }
            if (p.state == 3 && Random.nextFloat() < dt * 0.6f && parts.size < 250) parts.add(PyP(petX(p.id) + petSize() * 0.25f, groundY() - petSize() * 0.8f, W() * 0.02f, -H() * 0.05f, 1.6f, H() * 0.014f, 0xFFB7C8FF.toInt(), 2))
        }
        if (night && pets.all { it.state == 3 }) { nightSecs += dt; if (nightSecs.toInt() % 3 == 0) checkRewards() }
        if (rain) { rainSecs += dt; if (rainSecs.toInt() % 3 == 0) checkRewards() }
        var i = parts.size - 1
        while (i >= 0) {
            val p = parts[i]; p.x += p.vx * dt; p.y += p.vy * dt
            if (p.kind == 1) p.vy += H() * 0.6f * dt
            p.life -= dt; if (p.life <= 0f) parts.removeAt(i); i--
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanosEco { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
                last = now
                update(dt)
                frame = (frame + 1) and 0xFFFFFF
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { boxSize = it }) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (showAlbum || reward != null) return@awaitEachGesture
                    val start = down.position
                    // тап/перетаскивание из лотка
                    var food = -1
                    for (k in 0..2) if (hypot(start.x - trayX(k), start.y - trayY()) < H() * 0.075f) food = k
                    if (food >= 0) {
                        dragFood = food; dragPos = start
                        var moved = false
                        var cur = start
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: break
                            cur = ch.position; dragPos = cur
                            if (hypot(cur.x - start.x, cur.y - start.y) > 24f) moved = true
                            ch.consume()
                            if (!ch.pressed) break
                        }
                        dragFood = -1
                        if (!moved) {
                            // тап по лотку: еда летит любимчику (для малышей — любому голодному)
                            feed(PY_FOOD[food].second, food)
                        } else {
                            var best = -1; var bd = petSize() * 0.6f
                            for (i in 0..2) { val d = hypot(cur.x - petX(i), cur.y - (groundY() - petSize() * 0.45f)); if (d < bd) { bd = d; best = i } }
                            if (best >= 0) feed(best, food)
                        }
                        return@awaitEachGesture
                    }
                    // тап по питомцу
                    var hit = -1; var bd = petSize() * 0.55f
                    for (i in 0..2) { val d = hypot(start.x - petX(i), start.y - (groundY() - petSize() * 0.45f)); if (d < bd) { bd = d; hit = i } }
                    if (hit >= 0) pet(hit)
                }
            }
        ) {
            frame.hashCode()
            val w = size.width; val h = size.height
            if (w <= 0f) return@Canvas
            val g = groundY(); val ps = petSize(); val t = clock
            // небо
            val sky = if (night) listOf(Color(0xFF15204A), Color(0xFF4A4690)) else if (rain) listOf(Color(0xFF8AA0B8), Color(0xFFCBD7E0)) else listOf(Color(0xFF8ED8F5), Color(0xFFE9F8DC))
            drawRect(Brush.verticalGradient(sky, 0f, g), Offset.Zero, Size(w, h))
            if (night) {
                for (i in 0 until 24) pyStar(((i * 0.618f) % 1f) * w, ((i * 0.377f + 0.05f) % 1f) * g * 0.8f, h * (0.006f + 0.004f * (i % 3)), Color.White.copy(alpha = 0.4f + 0.5f * sin(t * 2f + i)))
                drawCircle(Color(0xFFFFF3C4), h * 0.06f, Offset(w * 0.82f, h * 0.1f)); drawCircle(Color(0xFF15204A), h * 0.052f, Offset(w * 0.82f + h * 0.028f, h * 0.092f))
                if ((mask and 8) != 0) for (i in 0 until 8) { val a = t * 0.7f + i * 1.3f; pyStar(w * (0.1f + 0.1f * i) + sin(a) * w * 0.04f, g - h * (0.05f + 0.1f * ((i * 37) % 10) / 10f) + cos(a * 1.3f) * h * 0.02f, h * 0.008f, Color(0xFFFFF59D).copy(alpha = 0.5f + 0.5f * sin(a * 3f))) }
            } else if (!rain) {
                drawCircle(Color(0xFFFFE27A), h * 0.07f, Offset(w * 0.84f, h * 0.1f)); drawCircle(Color(0x55FFF3B0), h * 0.105f, Offset(w * 0.84f, h * 0.1f))
                if ((mask and 4) != 0) {
                    val cols = listOf(0xFFFF7A7A, 0xFFFFB866, 0xFFFFE066, 0xFF8EDB7A, 0xFF6CC4F2, 0xFFB78BEA)
                    for ((i, c) in cols.withIndex()) drawArc(Color(c).copy(alpha = 0.7f), 180f, 180f, false, Offset(w * 0.08f + i * h * 0.018f, g - h * 0.46f + i * h * 0.018f), Size(w * 0.84f - i * h * 0.036f, h * 0.6f - i * h * 0.036f), style = Stroke(h * 0.018f))
                }
            }
            for ((i, c) in listOf(0.15f to 0.1f, 0.55f to 0.2f).withIndex()) {
                val x = ((c.first * w + t * (8f + i * 5f)) % (w * 1.3f)) - w * 0.15f
                val cc = if (night) Color(0x33FFFFFF) else if (rain) Color(0xFF9AA8B6) else Color(0xE6FFFFFF)
                drawOval(cc, Offset(x, h * c.second), Size(w * 0.26f, h * 0.05f)); drawOval(cc, Offset(x + w * 0.05f, h * c.second - h * 0.02f), Size(w * 0.15f, h * 0.05f))
            }
            // трава
            val gc = if (night) listOf(Color(0xFF2B4A6A), Color(0xFF1E3550)) else listOf(Color(0xFF8AD36B), Color(0xFF5DB85A))
            drawRect(Brush.verticalGradient(gc, g - h * 0.04f, h), Offset(0f, g - h * 0.04f), Size(w, h - g + h * 0.04f))
            drawOval(gc[0], Offset(-w * 0.2f, g - h * 0.09f), Size(w * 0.9f, h * 0.16f)); drawOval(gc[1], Offset(w * 0.4f, g - h * 0.08f), Size(w * 0.9f, h * 0.15f))
            // питомцы
            for (i in 0..2) {
                val p = pets[i]; val im = pic(poseFor(i)) ?: continue
                val cx = petX(i)
                val bob = if (p.state == 3) sin(t * 1.6f + i) * ps * 0.01f else sin(t * 2.4f + i * 1.7f) * ps * 0.015f
                val jumpY = if (p.jump > 0f) sin(p.jump / 0.5f * Math.PI.toFloat()) * ps * 0.18f else 0f
                val sh = if (p.shake > 0f) sin(p.shake * 60f) * ps * 0.03f else 0f
                drawOval(Color(0x33000000), Offset(cx - ps * 0.32f, g - ps * 0.04f), Size(ps * 0.64f, ps * 0.09f))
                val tint = if (night) ColorFilter.tint(Color(0x55203060), BlendMode.SrcAtop) else if (rain) ColorFilter.tint(Color(0x22506070), BlendMode.SrcAtop) else null
                drImg(im, cx + sh, g - ps * 0.46f + bob - jumpY, ps / im.width * 1.0f, 1f, tint)
                // уровень: звёздочки
                val lv = min(5, p.love / 10)
                for (k in 0 until lv) pyStar(cx - (lv - 1) * ps * 0.06f + k * ps * 0.12f, g + ps * 0.12f, ps * 0.05f, Color(0xFFFFD54F))
            }
            // дождь
            if (rain) {
                val cc = listOf(0xFF6CC4F2, 0xFFFF7A7A, 0xFFFFE066, 0xFF8EDB7A, 0xFFB78BEA)
                for (i in 0 until 60) {
                    val x = ((i * 0.37f + 0.1f) % 1f) * w + sin(i.toFloat()) * 10f
                    val y = ((i * 0.713f + t * (0.8f + (i % 4) * 0.2f)) % 1f) * h * 0.9f
                    val col = if ((mask and 16) != 0) Color(cc[i % 5]) else Color(0xFF6CB4E8)
                    drawLine(col.copy(alpha = 0.7f), Offset(x, y), Offset(x - 6f, y + h * 0.03f), strokeWidth = 3f, cap = StrokeCap.Round)
                }
            }
            // лоток с едой
            drawRoundRect(Color(0xCCFFFFFF), Offset(w * 0.08f, trayY() - h * 0.09f), Size(w * 0.84f, h * 0.18f), androidx.compose.ui.geometry.CornerRadius(h * 0.04f))
            for (k in 0..2) {
                val im = cache[PY_FOOD[k].first]
                if (im != null) drImg(im, trayX(k), trayY(), h * 0.12f / im.width, if (dragFood == k) 0.3f else 1f)
            }
            if (dragFood >= 0) { val im = cache[PY_FOOD[dragFood].first]; if (im != null) drImg(im, dragPos.x, dragPos.y - h * 0.04f, h * 0.15f / im.width) }
            // частицы
            for (p in parts) {
                val lf = (p.life / p.maxLife).coerceIn(0f, 1f)
                when (p.kind) {
                    0 -> pyHeart(p.x, p.y, p.size * (0.5f + lf * 0.5f), Color(p.col).copy(alpha = min(1f, lf * 1.5f)))
                    2 -> pyZ(p.x, p.y, p.size * (0.6f + (1f - lf) * 0.8f), Color(p.col).copy(alpha = lf))
                    else -> drawRect(Color(p.col).copy(alpha = lf), Offset(p.x, p.y), Size(p.size, p.size * 0.6f))
                }
            }
        }

        Row(
            Modifier.align(Alignment.TopStart).padding(top = 10.dp, start = 12.dp).clip(RoundedCornerShape(20.dp)).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(Modifier.size(22.dp)) { pyHeart(size.width / 2, size.height / 2, size.width * 0.35f, Color(0xFFFF6B8A)) }
            Spacer(Modifier.width(6.dp))
            Text("$loveUi", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Row(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val btn = Modifier.size(48.dp).clip(CircleShape).border(3.dp, Color.White, CircleShape)
            Box(btn.background(Brush.verticalGradient(if (night) listOf(Color(0xFFFFE9A0), Color(0xFFFFB400)) else listOf(Color(0xFF4A4690), Color(0xFF15204A)))).clickable { setNight(!night) }, Alignment.Center) {
                Canvas(Modifier.size(28.dp)) {
                    if (night) { drawCircle(Color.White, size.width * 0.22f, Offset(size.width / 2, size.height / 2)); for (k in 0 until 8) { val a = k * 0.785f; drawLine(Color.White, Offset(size.width / 2 + cos(a) * size.width * 0.32f, size.height / 2 + sin(a) * size.width * 0.32f), Offset(size.width / 2 + cos(a) * size.width * 0.45f, size.height / 2 + sin(a) * size.width * 0.45f), strokeWidth = 3f) } }
                    else { drawCircle(Color(0xFFFFF3C4), size.width * 0.38f, Offset(size.width / 2, size.height / 2)); drawCircle(Color(0xFF15204A), size.width * 0.32f, Offset(size.width * 0.62f, size.height * 0.42f)) }
                }
            }
            Box(btn.background(Brush.verticalGradient(listOf(Color(0xFFBFE3FF), Color(0xFF4FA0E8)))).clickable { setRain(!rain) }, Alignment.Center) {
                Canvas(Modifier.size(28.dp)) {
                    drawOval(Color.White, Offset(size.width * 0.1f, size.height * 0.2f), Size(size.width * 0.8f, size.height * 0.4f))
                    if (!rain) drawOval(Color.White, Offset(size.width * 0.3f, size.height * 0.1f), Size(size.width * 0.4f, size.height * 0.4f))
                    if (rain) for (k in 0..2) drawLine(Color(0xFF2F6FA8), Offset(size.width * (0.3f + 0.2f * k), size.height * 0.68f), Offset(size.width * (0.26f + 0.2f * k), size.height * 0.92f), strokeWidth = 3f, cap = StrokeCap.Round)
                }
            }
            Box(btn.background(Brush.verticalGradient(listOf(Color(0xFFD8A0FF), Color(0xFF9B59E0)))).clickable { showAlbum = true; snd("fx_ui_menu_open", 0.7f) }, Alignment.Center) {
                val im = pic(PY_STICKER_PICS[5])
                Canvas(Modifier.size(36.dp)) { if (im != null) drImg(im, size.width / 2, size.height / 2, size.width / im.width) }
            }
        }
        if (toastT > 0f) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 80.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xE6FF8A3D)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text(toast, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
        } else if (feedTotal == 0 && !night) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 72.dp, start = 12.dp, end = 12.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xE6FFFFFF)).border(2.dp, Color(0xFF6FB6E8), RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("Гладь питомцев и перетаскивай угощения на них!", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8), textAlign = TextAlign.Center)
            }
        }

        if (showAlbum) {
            Box(Modifier.fillMaxSize().background(Color(0x99102040)).clickable { showAlbum = false; snd("fx_ui_menu_close", 0.7f) }, Alignment.Center) {
                Column(
                    Modifier.padding(12.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFFF2F8FF)).border(5.dp, Color(0xFF6FB6E8), RoundedCornerShape(28.dp)).clickable(enabled = false) {}.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Альбом «Пушистики»", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (i in 0..2) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val im = pic(PY_IDLE[i][0])
                            Canvas(Modifier.size(56.dp)) { if (im != null) drImg(im, size.width / 2, size.height / 2, size.width / im.width) }
                            Text(PY_NAMES[i], fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8))
                            Row { Text("Любовь:", fontSize = 9.sp, color = Color(0xFF6B7A90)); Text(" ${pets[i].love}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE0780A)) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    PY_STICKERS.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { st ->
                                val un = (mask and (1 shl st.id)) != 0
                                Column(Modifier.width(100.dp).padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(if (un) Color(0xFFDDEEFF) else Color(0xFFE8EBF0)).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    val im = pic(PY_STICKER_PICS[st.id])
                                    Canvas(Modifier.size(64.dp)) {
                                        val c = Offset(size.width / 2, size.height / 2)
                                        drawCircle(if (un) Color(0xFFFFE9A0) else Color(0x22000000), size.width * 0.46f, c)
                                        if (im != null) drImg(im, c.x, c.y, size.width * 0.8f / im.width, if (un) 1f else 0.9f, if (un) null else ColorFilter.tint(Color(0xFF8A94A4), BlendMode.SrcIn))
                                    }
                                    Text(st.name, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8), textAlign = TextAlign.Center, maxLines = 2)
                                    if (un) Text(st.note, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE0780A), textAlign = TextAlign.Center, maxLines = 3)
                                    else Text(st.how, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7A90), textAlign = TextAlign.Center, maxLines = 3)
                                }
                            }
                        }
                    }
                }
            }
        }

        val rs = reward
        if (rs != null) {
            Box(Modifier.fillMaxSize().background(Color(0xAA102040)).clickable { reward = null; showNext() }, Alignment.Center) {
                Column(Modifier.padding(24.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFFF2F8FF)).border(5.dp, Color(0xFF6FB6E8), RoundedCornerShape(28.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Новая наклейка!", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF3F8FE0))
                    val im = pic(PY_STICKER_PICS[rs.id])
                    Canvas(Modifier.size(150.dp)) {
                        val c = Offset(size.width / 2, size.height / 2)
                        drawCircle(Color(0xFFFFE9A0), size.width * 0.46f, c); drawCircle(Color.White, size.width * 0.46f, c, style = Stroke(6f))
                        if (im != null) drImg(im, c.x, c.y, size.width * 0.82f / im.width)
                    }
                    Text(rs.name, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8))
                    Spacer(Modifier.height(6.dp))
                    Text("Подарок:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A7FA8))
                    Text(rs.note, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFE0780A), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF3F8FE0)).padding(horizontal = 28.dp, vertical = 8.dp)) { Text("Играть дальше", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White) }
                }
            }
        }
    }
}
