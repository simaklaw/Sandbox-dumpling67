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
import androidx.compose.ui.graphics.Path
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
// Нарисованный повар ХОДИТ по кухне: тапаешь станцию — он бежит к ней и делает дело (правила — в CookRules.kt, их проверяет CookRulesTest).
// Цепочка: продукт на ленте → доска (режь, когда метка в золотой зоне) → сковорода (сними, пока дуга золотая) → тарелка → гость.
// Если действие сейчас невозможно (руки заняты, сковородка пуста), повар не бегает зря, а подсказывает словами.

private const val CK_PREFS = "cook_prefs"
private const val CK_READY = 0
private const val CK_PLAY = 1
private const val CK_CLEAR = 2

private const val CK_INK = 0xFF3A0728
private const val CK_SPEED = 300f

private const val ACT_BELT = 0
private const val ACT_BOARD = 1
private const val ACT_PAN = 2
private const val ACT_PLATE = 3
private const val ACT_TRASH = 4
private const val ACT_GUEST = 5

private class CkPart(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val size: Float, val color: Color, val text: String? = null, val g: Float = 200f)
private class CkFloat(val x: Float, var y: Float, val text: String, var life: Float, val color: Color)
private class CkFly(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val text: String, var t: Float = 0f)
private class CkAct(val kind: Int, val idx: Int)

private class CkChef {
    var x = 180f
    var y = 300f
    var face = 1
    var phase = 0f
    var moving = false
    var celebrate = 0f
    var sad = 0f
    var blink = 0f
    var blinkIn = 2.5f
    var stepT = 0f
    var stepFlip = false
    var act: CkAct? = null
}

private class CkRef {
    var sim: CookSim? = null
    val chef = CkChef()
    val parts = ArrayList<CkPart>()
    val floats = ArrayList<CkFloat>()
    val flies = ArrayList<CkFly>()
    var pendingFly: String? = null
    var anim = 0f
    var idle = 0f
    var knife = 0f
    var sizzleT = 0f
    var hintT = 0f
}

/** Раскладка кухни в логических единицах (ширина всегда 360). */
private class CkLayout(val lw: Float, val lh: Float, val seatsN: Int, val panN: Int) {
    fun seatX(i: Int) = if (seatsN == 2) (if (i == 0) 100f else 260f) else (62f + 118f * i)
    val guestY get() = lh * 0.15f
    val counterY get() = lh * 0.25f
    val plate get() = Offset(lw * 0.5f, counterY + 16f)
    val trash get() = Offset(lw - 40f, counterY + 16f)
    val serveY get() = counterY + 84f
    val rowY get() = lh * 0.56f
    val board get() = Offset(66f, rowY)
    val boardW = 104f
    val boardH = 84f
    val workFeetY get() = rowY + 66f
    fun panX(i: Int) = if (panN == 3) 158f + 79f * i else 188f + 102f * i
    val panR get() = if (panN == 3) 31f else 37f
    fun pan(i: Int) = Offset(panX(i), rowY)
    val beltY get() = lh * 0.85f
    val beltH = 60f
    val beltFeetY get() = beltY - 34f
}

private fun lerpC(a: Color, b: Color, t: Float) = lerp(a, b, t.coerceIn(0f, 1f))
private val ckInk = Color(CK_INK)

/** Куда должен встать повар, чтобы выполнить действие (null — цель пропала). */
private fun ckSpot(L: CkLayout, s: CookSim, a: CkAct): Offset? = when (a.kind) {
    ACT_BELT -> s.belt.firstOrNull { it.id == a.idx }?.let { Offset(it.x.coerceIn(30f, L.lw - 30f), L.beltFeetY) }
    ACT_BOARD -> Offset(L.board.x, L.workFeetY)
    ACT_PAN -> Offset(L.pan(a.idx).x, L.workFeetY)
    ACT_PLATE -> Offset(L.plate.x, L.serveY)
    ACT_TRASH -> Offset(L.trash.x - 6f, L.serveY)
    else -> Offset(L.seatX(a.idx), L.serveY)
}

/** Можно ли сейчас? null — можно, иначе короткая подсказка словами (повар в этом случае не бегает). */
private fun ckCan(s: CookSim, kind: Int, idx: Int): String? {
    val h = s.hand
    return when (kind) {
        ACT_BELT -> if (h != null) "Руки заняты!" else null
        ACT_BOARD -> when {
            h != null -> if (s.board == null && !h.cooked && !h.chopped) null else "Сюда нужен сырой продукт"
            s.board == null -> "Сначала возьми продукт"
            else -> null
        }
        ACT_PAN -> {
            val p = s.pans.getOrNull(idx)
            when {
                p == null -> "Тут пусто"
                h != null -> if (p.food != null) "Сковородка занята" else if (h.cooked) "Это уже готово!" else null
                p.food == null -> "Тут пока пусто"
                else -> null
            }
        }
        ACT_PLATE -> when {
            h != null -> if (!h.cooked) "Сначала пожарь!" else if (s.plate.size >= 2) "Тарелка полная" else null
            s.plate.isEmpty() -> "Тарелка пуста"
            else -> null
        }
        ACT_TRASH -> if (h == null && s.plate.isEmpty()) "Нечего выбрасывать" else null
        else -> {
            val seat = s.seats.getOrNull(idx)
            val gst = seat?.guest
            when {
                seat == null || gst == null || seat.reactT > 0f -> "Зверёк уже занят"
                h != null && !h.cooked -> "Сначала пожарь!"
                h == null && s.plate.isEmpty() -> "Нужна еда на тарелке"
                else -> null
            }
        }
    }
}

/** Подсказка-строка для первых смен: что сделать дальше. */
private fun ckHintText(s: CookSim): String {
    val hint = s.hint() ?: return ""
    return when (hint.kind) {
        CHint.BELT -> "Тапни продукт на ленте — повар за ним сбегает"
        CHint.BOARD -> when {
            s.board != null && s.board?.chopped == false -> "Тапни доску ещё раз: режь, когда метка в золотом"
            s.board != null -> "Забери нарезанное с доски"
            else -> "Неси на доску!"
        }
        CHint.PAN -> if (s.hand != null) "Положи на сковородку" else "Золотая! Снимай скорее!"
        CHint.PLATE -> "Поставь на тарелку"
        CHint.GUEST -> "Накорми зверёнка!"
        else -> ""
    }
}

@Composable
fun CookScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val sm = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val prefs = remember { context.getSharedPreferences(CK_PREFS, Context.MODE_PRIVATE) }
    val measurer = rememberTextMeasurer(cacheSize = 200)
    val ref = remember { CkRef() }
    val profile = remember { CProfile.decode(try { prefs.getString("p", null) } catch (_: Exception) { null }) }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(0) }
    var phase by remember { mutableIntStateOf(CK_READY) }
    var meta by remember { mutableIntStateOf(0) }        // растёт, когда меняется профиль (для перерисовки карточек)
    var starsUi by remember { mutableIntStateOf(0) }
    var leftUi by remember { mutableIntStateOf(0) }
    var totalUi by remember { mutableIntStateOf(1) }
    var hintUi by remember { mutableStateOf("") }
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
                "fx_sh_yum", "fx_sh_reward", "fx_sh_coin1", "fx_sh_bye", "fx_sh_wrong", "fx_sh_hello_bunny",
                "fx_sh_hello_bear", "fx_sh_hello_cat", "fx_sh_p_hello", "btn_tada",
                "ck_step1", "ck_step2", "ck_sizzle", "ck_order", "ck_serve", "ck_pick", "ck_cheer"
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
    fun say(t: String) { banner = t; bannerT = 2.6f }

    fun startShift() {
        ref.parts.clear(); ref.floats.clear(); ref.flies.clear(); ref.idle = 0f; ref.pendingFly = null
        val s = CookSim(profile)
        ref.sim = s
        val L = CkLayout(360f, logicalH(), s.seatsN, s.panN)
        ref.chef.apply { x = L.lw / 2f; y = L.workFeetY - 20f; face = 1; moving = false; act = null; celebrate = 0f; sad = 0f }
        starsUi = 0; leftUi = s.totalGuests; totalUi = s.totalGuests; hintUi = ""
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
        val ch = ref.chef
        for (e in evs) {
            when (e.type) {
                CEv.PICK -> { snd("ck_pick", 0.75f); burst(ch.x, ch.y - 36f, 5, listOf(Color.White, Color(0xFFFFE082)), 70f) }
                CEv.TAKE, CEv.BOARD_PUT -> snd("kit_plop", 0.8f, if (e.type == CEv.BOARD_PUT) 0.9f else 1f)
                CEv.CHOP_GOOD, CEv.CHOP_OK -> {
                    val fam = runCatching { CFam.valueOf(e.text) }.getOrDefault(CFam.VEG)
                    chopSound(fam, e.type == CEv.CHOP_GOOD)
                    ref.knife = 0.30f; haptics.vibrateShort()
                    val b = L.board
                    if (e.type == CEv.CHOP_GOOD) { burst(b.x, b.y, 12, listOf(Color(0xFFFFD54F), Color.White), 150f); floatText(b.x, b.y - 66f, "Ровно!", Color(0xFFFFD54F)) }
                    else floatText(b.x, b.y - 66f, "Чик!", Color.White)
                }
                CEv.PAN_PUT -> snd("kc_bub1", 0.7f)
                CEv.PAN_GOLD -> {
                    snd("kc_bell", 0.9f); haptics.vibrateShort()
                    val p = L.pan(e.idx); burst(p.x, p.y, 14, listOf(Color(0xFFFFD54F), Color.White), 160f)
                    floatText(p.x, p.y - 66f, "Готово!", Color(0xFFFFD54F))
                }
                CEv.PAN_BURN -> { snd("kc_sad", 0.5f); val p = L.pan(e.idx); floatText(p.x, p.y - 66f, "Дымок!", Color(0xFFE0E0E0)) }
                CEv.PLATE -> snd("ck_serve", 0.8f)
                CEv.TRASH -> snd("kc_clink", 0.7f)
                CEv.WRONG -> snd("fx_sh_wrong", 0.35f)
                CEv.ARRIVE -> { snd("ck_order", 0.4f); snd(e.text, 0.8f) }
                CEv.LEAVE -> if (e.text == "happy") snd("fx_sh_bye", 0.7f)
                CEv.SERVE -> {
                    val x = L.seatX(e.idx); val y = L.guestY
                    val r = runCatching { CReact.valueOf(e.text) }.getOrDefault(CReact.MEH)
                    ref.pendingFly?.let { ref.flies.add(CkFly(ch.x, ch.y - 40f, x, y + 10f, it)); ref.pendingFly = null }
                    when (r) {
                        CReact.LOVED -> { snd("kc_yum", 1f); snd("fx_sh_reward", 0.8f); haptics.vibratePop(); emojiBurst(x, y, "⭐", 8); burst(x, y, 20, listOf(Color(0xFFFFD54F), Color(0xFFFF8BCB), Color.White), 260f); ch.celebrate = 1.0f }
                        CReact.LIKED -> { snd("fx_sh_yum", 0.9f); haptics.vibrateShort(); emojiBurst(x, y, "⭐", 5); ch.celebrate = 0.5f }
                        CReact.MEH -> { snd("kc_munch1", 0.8f); emojiBurst(x, y, "⭐", 2) }
                        CReact.SAD -> { snd("kc_sigh", 0.5f); ch.sad = 1.2f }
                    }
                    if (e.stars > 0) { snd("fx_sh_coin1", 0.7f); floatText(x, y - 96f, "+${e.stars}⭐", Color(0xFFFFD54F)) }
                    else floatText(x, y - 96f, "Ещё разок!", Color.White)
                }
                CEv.RECIPE -> { snd("kc_sparkle", 0.9f); say("Новый рецепт: ${e.text}!") }
                CEv.CLEAR -> {
                    snd("ck_cheer", 0.9f); snd("btn_tada", 0.5f); haptics.vibratePop()
                    resStars = s.starsEarned; resServed = s.served; resLoved = s.loved; resGone = s.gone; resRating = s.rating
                    resRecipes = s.newRecipes.toList()
                    gifts = s.offerGifts()
                    ch.celebrate = 2f
                    emojiBurst(ch.x, ch.y - 50f, "🎉", 10)
                    saveProfile()
                    phase = CK_CLEAR
                }
            }
        }
    }

    /** Повар дошёл до станции: выполняем действие по правилам. */
    fun execute(s: CookSim, a: CkAct) {
        when (a.kind) {
            ACT_BELT -> {
                val id = if (s.belt.any { it.id == a.idx }) a.idx else (s.belt.minByOrNull { kotlin.math.abs(it.x - ref.chef.x) }?.id ?: -1)
                if (id >= 0) s.tapBelt(id)
            }
            ACT_BOARD -> s.tapBoard()
            ACT_PAN -> s.tapPan(a.idx)
            ACT_PLATE -> s.tapPlate()
            ACT_TRASH -> s.tapTrash()
            else -> {
                ref.pendingFly = (s.plate + listOfNotNull(s.hand)).joinToString("") { it.fam.emoji }.ifEmpty { null }
                if (!s.tapGuest(a.idx)) ref.pendingFly = null
            }
        }
    }

    fun tick(dt: Float) {
        ref.anim += dt
        if (ref.knife > 0f) ref.knife = max(0f, ref.knife - dt)
        if (bannerT > 0f) bannerT = max(0f, bannerT - dt)
        val ch = ref.chef
        if (ch.celebrate > 0f) ch.celebrate = max(0f, ch.celebrate - dt)
        if (ch.sad > 0f) ch.sad = max(0f, ch.sad - dt)
        ch.blinkIn -= dt
        if (ch.blinkIn <= 0f) { ch.blink = 0.12f; ch.blinkIn = 2f + Random.nextFloat() * 3f }
        if (ch.blink > 0f) ch.blink = max(0f, ch.blink - dt)
        val s = ref.sim
        if (s != null && phase == CK_PLAY) {
            val L = CkLayout(360f, logicalH(), s.seatsN, s.panN)
            // ходьба
            val act = ch.act
            if (act != null) {
                val sp = ckSpot(L, s, act)
                if (sp == null) { ch.act = null; ch.moving = false }
                else {
                    val dx = sp.x - ch.x; val dy = sp.y - ch.y
                    val d = kotlin.math.hypot(dx, dy)
                    if (d <= 4f) {
                        ch.x = sp.x; ch.y = sp.y; ch.moving = false; ch.act = null
                        execute(s, act)
                    } else {
                        val stepLen = min(d, CK_SPEED * dt)
                        ch.x += dx / d * stepLen; ch.y += dy / d * stepLen
                        if (kotlin.math.abs(dx) > 3f) ch.face = if (dx > 0f) 1 else -1
                        ch.moving = true
                        ch.phase += dt * 17f
                        ch.stepT -= dt
                        if (ch.stepT <= 0f) { ch.stepT = 0.2f; ch.stepFlip = !ch.stepFlip; snd(if (ch.stepFlip) "ck_step1" else "ck_step2", 0.45f) }
                    }
                }
            } else ch.moving = false
            s.step(dt)
            ref.idle += dt
            handleEvents(s, L)
            // шипение и пар над сковородками
            var cooking = false
            s.pans.forEachIndexed { i, p ->
                val f = p.food ?: return@forEachIndexed
                if (!p.burnt) cooking = true
                if (f.t > CookCfg.RAW_END * 0.5f && Random.nextFloat() < dt * 6f) {
                    val pc = L.pan(i)
                    ref.parts.add(CkPart(pc.x + (Random.nextFloat() - 0.5f) * 30f, pc.y - L.panR * 0.5f, (Random.nextFloat() - 0.5f) * 14f, -38f - Random.nextFloat() * 20f, 0.9f, 4f + Random.nextFloat() * 4f, Color(0x88FFFFFF), null, -10f))
                }
            }
            ref.sizzleT -= dt
            if (cooking && ref.sizzleT <= 0f) { snd("ck_sizzle", 0.28f); ref.sizzleT = 0.85f }
            if (s.starsEarned != starsUi) starsUi = s.starsEarned
            if (s.guestsLeft != leftUi) leftUi = s.guestsLeft
            ref.hintT -= dt
            if (ref.hintT <= 0f) {
                ref.hintT = 0.25f
                val t = if (profile.night <= 2 || ref.idle > 6f) ckHintText(s) else ""
                if (t != hintUi) hintUi = t
            }
        }
        val pi = ref.parts.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            p.x += p.vx * dt; p.y += p.vy * dt; p.vy += p.g * dt; p.life -= dt * 1.2f
            if (p.life <= 0f) pi.remove()
        }
        val fi = ref.floats.iterator()
        while (fi.hasNext()) { val f = fi.next(); f.y -= 34f * dt; f.life -= dt; if (f.life <= 0f) fi.remove() }
        val yi = ref.flies.iterator()
        while (yi.hasNext()) { val f = yi.next(); f.t += dt * 2.4f; if (f.t >= 1f) yi.remove() }
        frame++
    }

    fun handleTap(x: Float, y: Float) {
        val s = ref.sim ?: return
        if (phase != CK_PLAY) return
        val L = CkLayout(360f, logicalH(), s.seatsN, s.panN)
        ref.idle = 0f
        val ch = ref.chef
        fun d(ax: Float, ay: Float) = kotlin.math.hypot(x - ax, y - ay)
        fun go(kind: Int, idx: Int) {
            val why = ckCan(s, kind, idx)
            if (why != null) {
                snd("fx_sh_wrong", 0.3f)
                floatText(ch.x, ch.y - 92f, why, Color.White)
                ch.sad = 0.5f
                return
            }
            val a = CkAct(kind, idx)
            val sp = ckSpot(L, s, a) ?: return
            if (kotlin.math.hypot(sp.x - ch.x, sp.y - ch.y) <= 4f) { ch.act = null; execute(s, a) } else ch.act = a
        }
        // гости (шире: тянутся от окошка до прилавка)
        for (i in 0 until s.seatsN) if (abs(x - L.seatX(i)) < 56f && y > L.guestY - 70f && y < L.counterY + 20f) { go(ACT_GUEST, i); return }
        // продукты на ленте
        val item = s.belt.minByOrNull { d(it.x, L.beltY) }
        if (item != null && d(item.x, L.beltY) < 46f && abs(y - L.beltY) < 56f) { go(ACT_BELT, item.id); return }
        for (i in 0 until s.panN) if (d(L.pan(i).x, L.pan(i).y) < L.panR + 16f) { go(ACT_PAN, i); return }
        if (abs(x - L.board.x) < L.boardW / 2 + 8f && abs(y - L.board.y) < L.boardH / 2 + 34f) { go(ACT_BOARD, 0); return }
        if (abs(x - L.plate.x) < 56f && y > L.counterY - 14f && y < L.counterY + 60f) { go(ACT_PLATE, 0); return }
        if (abs(x - L.trash.x) < 40f && y > L.counterY - 14f && y < L.counterY + 60f) { go(ACT_TRASH, 0); return }
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

    val previewChef = remember { CkChef() }

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
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CkPill("🍳 Смена ${profile.night}", Color(0xFFFFE082))
                    Spacer(Modifier.weight(1f))
                    CkPill("⭐ $starsUi", Color(0xFFFFF59D))
                }
                Spacer(Modifier.height(6.dp))
                val done = (totalUi - leftUi).coerceIn(0, totalUi)
                Box(
                    Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(Color(0x66FFFFFF)).border(2.dp, ckInk, CircleShape)
                ) {
                    Box(Modifier.fillMaxWidth(done.toFloat() / totalUi.coerceAtLeast(1)).fillMaxHeight().background(Color(0xFFFF8BCB)))
                }
                Text(
                    "Гостей осталось: $leftUi", fontSize = 12.sp, fontWeight = FontWeight.Black, color = ckInk,
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp)
                )
                if (hintUi.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            "👆 $hintUi", fontSize = 15.sp, fontWeight = FontWeight.Black, color = ckInk, textAlign = TextAlign.Center,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xF2FFFFFF))
                                .border(3.dp, ckInk, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            if (bannerT > 0f) {
                Box(Modifier.fillMaxSize().padding(bottom = 90.dp), contentAlignment = Alignment.BottomCenter) {
                    Text(
                        banner, fontSize = 17.sp, fontWeight = FontWeight.Black, color = ckInk, textAlign = TextAlign.Center,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFE082)).border(3.dp, ckInk, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // ── Старт смены ──
        if (phase == CK_READY && !showBook) {
            meta.hashCode()
            CkCard {
                Canvas(Modifier.size(width = 130.dp, height = 120.dp)) {
                    frame.hashCode()
                    val k = size.width / 130f
                    withTransform({ scale(k, k, Offset.Zero) }) {
                        previewChef.x = 65f; previewChef.y = 112f; previewChef.face = 1
                        previewChef.celebrate = if (((ref.anim * 0.5f) % 3f) < 0.6f) 0.5f else 0f
                        ckChef(previewChef, null, measurer, ref.anim, 1.25f)
                    }
                }
                Text("Поваренок", fontSize = 30.sp, fontWeight = FontWeight.Black, color = ckInk, textAlign = TextAlign.Center)
                Text("Смена ${profile.night}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA83C7B))
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFE0B2)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for ((i, e) in listOf("🥕", "🔪", "🍳", "🍽️").withIndex()) {
                        if (i > 0) Text("›", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFFA83C7B), modifier = Modifier.padding(horizontal = 6.dp))
                        Text(e, fontSize = 30.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Тапни, куда идти — повар сам добежит и всё сделает.\nЗверята просят блюдо в облачке над ними.",
                    fontSize = 14.sp, color = ckInk, textAlign = TextAlign.Center
                )
                val owned = CMod.values().filter { profile.level(it) > 0 }
                if (owned.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (m in owned) CkPill("${m.emoji}${profile.level(m)}", Color(0xFFFFF59D))
                    }
                }
                Spacer(Modifier.height(12.dp))
                CkButton(if (profile.night == 1 && profile.stars == 0) "Открыть кухню!" else "Начать смену!", Color(0xFFFF8BCB)) { startShift() }
                Spacer(Modifier.height(8.dp))
                CkButton("📖 Книга рецептов ${profile.recipes.size}/$COOK_RECIPES_TOTAL", Color(0xFFFFE082)) { showBook = true }
                if (profile.stars > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("Всего звёзд: ${profile.stars} ⭐", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA83C7B))
                }
            }
        }

        // ── Итоги смены и подарок ──
        if (phase == CK_CLEAR) {
            CkCard {
                Text("Смена окончена! 🎉", fontSize = 26.sp, fontWeight = FontWeight.Black, color = ckInk, textAlign = TextAlign.Center)
                Text((1..3).joinToString(" ") { if (it <= resRating) "🌟" else "☆" }, fontSize = 36.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CkPill("⭐ $resStars", Color(0xFFFFF59D))
                    CkPill("🍽️ $resServed", Color(0xFFFFE0B2))
                    CkPill("😍 $resLoved", Color(0xFFF8BBD0))
                }
                if (resGone > 0) Text("Ушли ждать: $resGone", fontSize = 13.sp, color = ckInk, modifier = Modifier.padding(top = 4.dp))
                if (resRecipes.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Новые рецепты: " + resRecipes.joinToString(", "), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA83C7B), textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(10.dp))
                if (gifts.isNotEmpty()) {
                    Text("Выбери подарок:", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ckInk)
                    Spacer(Modifier.height(6.dp))
                    for (g in gifts) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFE082))
                                .border(3.dp, ckInk, RoundedCornerShape(16.dp))
                                .clickable { ref.sim?.pickGift(g); snd("kc_sparkle", 0.8f); saveProfile(); gifts = emptyList(); phase = CK_READY }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(g.emoji, fontSize = 30.sp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(g.title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = ckInk)
                                Text(g.body, fontSize = 13.sp, color = ckInk)
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
                Text("📖 Книга рецептов", fontSize = 24.sp, fontWeight = FontWeight.Black, color = ckInk)
                Text("${profile.recipes.size} из $COOK_RECIPES_TOTAL. Рецепт открывается, когда зверёк обожает блюдо.", fontSize = 13.sp, color = ckInk, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    for (row in ckAllRecipes().chunked(2)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for ((key, name, icons) in row) {
                                val open = key in profile.recipes
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                        .background(if (open) Color(0xFFFFE082) else Color(0xFFEFEBE9))
                                        .border(2.dp, if (open) ckInk else Color(0xFFBCAAA4), RoundedCornerShape(14.dp))
                                        .padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(if (open) icons else "❓", fontSize = 24.sp)
                                    Text(
                                        if (open) name else "Ещё не открыт", fontSize = 11.sp,
                                        fontWeight = if (open) FontWeight.Bold else FontWeight.Normal, color = ckInk, textAlign = TextAlign.Center
                                    )
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
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
                .padding(16.dp)
                .widthIn(max = 380.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFFFFF3E0))
                .border(4.dp, ckInk, RoundedCornerShape(28.dp))
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun CkPill(text: String, bg: Color) {
    Text(
        text, fontSize = 15.sp, fontWeight = FontWeight.Black, color = ckInk,
        modifier = Modifier.clip(CircleShape).background(bg).border(2.5.dp, ckInk, CircleShape).padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun CkButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 220.dp, minHeight = 56.dp)
            .clip(CircleShape)
            .background(color)
            .border(4.dp, ckInk, CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 19.sp, fontWeight = FontWeight.Black, color = ckInk, textAlign = TextAlign.Center) }
}

// ───────────────────────── рисование ─────────────────────────

private fun DrawScope.ckEmoji(m: TextMeasurer, text: String, cx: Float, cy: Float, sz: Float, sc: Float = 1f, alpha: Float = 1f) {
    val res = m.measure(text, TextStyle(fontSize = sz.toSp()), maxLines = 1, softWrap = false)
    val tl = Offset(cx - res.size.width / 2f, cy - res.size.height / 2f)
    if (sc == 1f) drawText(textLayoutResult = res, topLeft = tl, alpha = alpha)
    else withTransform({ scale(sc, sc, Offset(cx, cy)) }) { drawText(textLayoutResult = res, topLeft = tl, alpha = alpha) }
}

private fun DrawScope.ckLabel(m: TextMeasurer, text: String, cx: Float, cy: Float, sz: Float, color: Color, alpha: Float = 1f, outline: Boolean = false) {
    val st = TextStyle(fontSize = sz.toSp(), fontWeight = FontWeight.Black, color = color)
    val res = m.measure(text, st, maxLines = 1, softWrap = false)
    val tl = Offset(cx - res.size.width / 2f, cy - res.size.height / 2f)
    if (outline) {
        val dark = m.measure(text, TextStyle(fontSize = sz.toSp(), fontWeight = FontWeight.Black, color = ckInk), maxLines = 1, softWrap = false)
        for (o in listOf(Offset(-1.6f, 0f), Offset(1.6f, 0f), Offset(0f, -1.6f), Offset(0f, 1.6f))) drawText(textLayoutResult = dark, topLeft = tl + o, alpha = alpha)
    }
    drawText(textLayoutResult = res, topLeft = tl, alpha = alpha)
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

/** Повар: белый китель, колпак, платок. (c.x, c.y) — точка под ногами. */
private fun DrawScope.ckChef(c: CkChef, hand: CCarry?, m: TextMeasurer, t: Float, scale: Float, knifeK: Float = 0f) {
    withTransform({ scale(scale, scale, Offset(c.x, c.y)) }) {
        val cx = c.x; val fy = c.y
        val f = c.face.toFloat()
        val walk = if (c.moving) sin(c.phase) else 0f
        val bob = if (c.moving) -3f * abs(sin(c.phase)) else 1.2f * sin(t * 2.4f)
        val jump = if (c.celebrate > 0f) -14f * abs(sin(t * 10f)) else 0f
        val oy = bob + jump
        val white = Color.White
        val coat = Color(0xFFFAFAFA)
        val skin = Color(0xFFFFD7B0)
        // тень
        drawOval(Color(0x33000000), Offset(cx - 17f, fy - 5f), Size(34f, 10f))
        // ноги и ботинки
        for (k in -1..1 step 2) {
            val sw = walk * k * 5f
            val lift = if (c.moving) max(0f, walk * k) * 3f else 0f
            val foot = Offset(cx + k * 6f + sw * 1.2f, fy - 3f - lift + jump * 0.4f)
            drawLine(Color(0xFF455A64), Offset(cx + k * 6f, fy - 18f + oy), foot, 8f, StrokeCap.Round)
            drawOval(Color(0xFF5D4037), Offset(foot.x - 6f + f * 1.5f, foot.y - 3f), Size(12f, 7f))
        }
        // руки (задние)
        fun arm(sx: Float, hx: Float, hy: Float) {
            val s0 = Offset(sx, fy - 40f + oy); val h0 = Offset(hx, hy)
            drawLine(ckInk, s0, h0, 8.5f, StrokeCap.Round)
            drawLine(coat, s0, h0, 6f, StrokeCap.Round)
            drawCircle(ckInk, 5.2f, h0); drawCircle(skin, 4.2f, h0)
        }
        val carry = hand != null
        val farHand: Offset
        val nearHand: Offset
        when {
            c.celebrate > 0f -> {
                val w = 3f * sin(t * 14f)
                farHand = Offset(cx - 17f, fy - 64f + oy + w); nearHand = Offset(cx + 17f, fy - 64f + oy - w)
            }
            knifeK > 0f -> {
                farHand = Offset(cx + f * 4f, fy - 28f + oy)
                nearHand = Offset(cx + f * 17f, fy - 30f + oy - 20f * sin(knifeK * PI.toFloat()))
            }
            carry -> {
                farHand = Offset(cx + f * 4f, fy - 28f + oy); nearHand = Offset(cx + f * 14f, fy - 27f + oy)
            }
            c.moving -> {
                farHand = Offset(cx - f * 14f - walk * 4f * f, fy - 25f + oy); nearHand = Offset(cx + f * 14f + walk * 4f * f, fy - 25f + oy)
            }
            else -> {
                farHand = Offset(cx - 16f, fy - 24f + oy); nearHand = Offset(cx + 16f, fy - 24f + oy)
            }
        }
        arm(cx - f * 13f, farHand.x, farHand.y)
        // китель
        drawRoundRect(coat, Offset(cx - 15f, fy - 46f + oy), Size(30f, 31f), CornerRadius(12f))
        drawRoundRect(ckInk, Offset(cx - 15f, fy - 46f + oy), Size(30f, 31f), CornerRadius(12f), style = Stroke(2.2f))
        drawCircle(Color(0xFF90A4AE), 1.9f, Offset(cx + f * 5f, fy - 36f + oy))
        drawCircle(Color(0xFF90A4AE), 1.9f, Offset(cx + f * 5f, fy - 28f + oy))
        val kerchief = Path().apply {
            moveTo(cx - 8f, fy - 46f + oy); lineTo(cx + 8f, fy - 46f + oy); lineTo(cx, fy - 35f + oy); close()
        }
        drawPath(kerchief, Color(0xFFE53935))
        // еда в руках
        if (hand != null) ckDish(m, hand, cx + f * 9f, fy - 36f + oy, 26f, t)
        arm(cx + f * 13f, nearHand.x, nearHand.y)
        if (knifeK > 0f) {
            val kb = Offset(nearHand.x + f * 4f, nearHand.y - 2f)
            drawLine(ckInk, kb, Offset(kb.x + f * 12f, kb.y + 12f), 6f, StrokeCap.Round)
            drawLine(Color(0xFFECEFF1), kb, Offset(kb.x + f * 12f, kb.y + 12f), 3.4f, StrokeCap.Round)
        }
        // голова
        val hy = fy - 58f + oy
        drawCircle(ckInk, 14.6f, Offset(cx, hy)); drawCircle(skin, 13f, Offset(cx, hy))
        drawCircle(Color(0x55FF6F91), 3.4f, Offset(cx - 8f, hy + 3.5f)); drawCircle(Color(0x55FF6F91), 3.4f, Offset(cx + 8f, hy + 3.5f))
        for (k in -1..1 step 2) {
            val ex = cx + f * 2f + k * 4.6f
            if (c.blink > 0f) drawLine(ckInk, Offset(ex - 1.8f, hy - 1f), Offset(ex + 1.8f, hy - 1f), 1.6f, StrokeCap.Round)
            else drawCircle(ckInk, 1.9f, Offset(ex, hy - 1f))
        }
        drawCircle(Color(0xFFFFB38A), 2f, Offset(cx + f * 3.5f, hy + 2.5f))
        when {
            c.celebrate > 0f -> drawOval(Color(0xFFD81B60), Offset(cx + f * 2f - 4f, hy + 5f), Size(8f, 6f))
            c.sad > 0f -> drawArc(ckInk, 205f, 130f, false, Offset(cx + f * 2f - 4f, hy + 8f), Size(8f, 6f), style = Stroke(1.8f, cap = StrokeCap.Round))
            else -> drawArc(ckInk, 25f, 130f, false, Offset(cx + f * 2f - 4f, hy + 3f), Size(8f, 6f), style = Stroke(1.8f, cap = StrokeCap.Round))
        }
        // колпак
        val hatY = hy - 12f
        for (p in listOf(Offset(cx - 9f, hatY - 8f) to 9.5f, Offset(cx + 9f, hatY - 8f) to 9.5f, Offset(cx, hatY - 13f) to 11f)) {
            drawCircle(ckInk, p.second + 1.6f, p.first); drawCircle(white, p.second, p.first)
        }
        drawRoundRect(ckInk, Offset(cx - 14f, hatY - 3f), Size(28f, 9.5f), CornerRadius(4f))
        drawRoundRect(white, Offset(cx - 12.6f, hatY - 1.6f), Size(25.2f, 6.8f), CornerRadius(3f))
    }
}

private fun DrawScope.drawCook(sim: CookSim?, ref: CkRef, L: CkLayout, m: TextMeasurer, playing: Boolean) {
    val w = L.lw; val h = L.lh; val t = ref.anim
    // стена-плитка
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF8E7), Color(0xFFFFE0B2)), 0f, L.counterY), Offset.Zero, Size(w, L.counterY + 20f))
    var gx = 0f
    while (gx < w) { drawLine(Color(0x14A83C7B), Offset(gx, 0f), Offset(gx, L.counterY), 1.5f); gx += 40f }
    var gy = 0f
    while (gy < L.counterY) { drawLine(Color(0x14A83C7B), Offset(0f, gy), Offset(w, gy), 1.5f); gy += 40f }
    // окошко выдачи
    val wy0 = L.guestY - 66f
    drawRoundRect(Color(0xFF8D6E63), Offset(6f, wy0 - 5f), Size(w - 12f, L.counterY - wy0 + 5f), CornerRadius(18f))
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFFDE7), Color(0xFFFFE9B8)), wy0, L.counterY), Offset(11f, wy0), Size(w - 22f, L.counterY - wy0), CornerRadius(14f))
    // навес в полоску
    val stripeW = 30f
    var ax = 0f
    var odd = false
    while (ax < w) {
        drawRect(if (odd) Color.White else Color(0xFFFF8BCB), Offset(ax, 0f), Size(stripeW, 24f))
        drawCircle(if (odd) Color.White else Color(0xFFFF8BCB), stripeW / 2f, Offset(ax + stripeW / 2f, 24f))
        ax += stripeW; odd = !odd
    }
    drawLine(Color(0x33000000), Offset(0f, 38f), Offset(w, 38f), 2f)
    // пол — шахматка
    val floorY = L.counterY + 34f
    drawRect(Color(0xFFD9C7BB), Offset(0f, floorY), Size(w, h - floorY))
    var ty = floorY; var rowOdd = false
    while (ty < h) {
        var tx = if (rowOdd) 0f else 36f
        while (tx < w) { drawRect(Color(0xFFCDB8AA), Offset(tx, ty), Size(36f, 36f)); tx += 72f }
        ty += 36f; rowOdd = !rowOdd
    }
    // прилавок
    drawRoundRect(Color(0xFFBCAAA4), Offset(-10f, L.counterY - 4f), Size(w + 20f, 14f), CornerRadius(7f))
    drawRoundRect(Color(0xFF8D6E63), Offset(-10f, L.counterY + 8f), Size(w + 20f, 26f), CornerRadius(6f))
    drawRoundRect(Color(0xFF5D4037), Offset(-10f, L.counterY - 4f), Size(w + 20f, 38f), CornerRadius(8f), style = Stroke(3f))

    // гости
    val seatsN = sim?.seatsN ?: L.seatsN
    for (i in 0 until seatsN) {
        val sx = L.seatX(i)
        val seat = sim?.seats?.getOrNull(i)
        val g = seat?.guest
        if (g != null && seat != null) {
            val frLeft = (seat.patience / seat.max).coerceIn(0f, 1f)
            val nervous = if (seat.reactT <= 0f && frLeft < 0.25f) 1.6f * sin(t * 30f) else 0f
            val bob = 3f * sin(t * 2.2f + i)
            val hop = if (seat.reactT > 0f && seat.react != CReact.SAD) -9f * abs(sin(t * 9f)) else 0f
            drawOval(Color(0x22000000), Offset(sx - 26f, L.counterY - 10f), Size(52f, 10f))
            ckEmoji(m, g.emoji, sx + nervous, L.guestY + bob + hop, 62f)
            if (seat.reactT <= 0f) {
                // облачко с желанием и хвостиком
                val by0 = L.guestY - 86f
                drawRoundRect(Color.White, Offset(sx - 28f, by0), Size(56f, 44f), CornerRadius(18f))
                drawRoundRect(ckInk, Offset(sx - 28f, by0), Size(56f, 44f), CornerRadius(18f), style = Stroke(2.5f))
                val tail = Path().apply { moveTo(sx - 6f, by0 + 43f); lineTo(sx, by0 + 52f); lineTo(sx + 6f, by0 + 43f); close() }
                drawPath(tail, Color.White)
                drawLine(ckInk, Offset(sx - 6f, by0 + 44f), Offset(sx, by0 + 52f), 2.5f, StrokeCap.Round)
                drawLine(ckInk, Offset(sx + 6f, by0 + 44f), Offset(sx, by0 + 52f), 2.5f, StrokeCap.Round)
                ckEmoji(m, g.want?.emoji ?: "🍽️", sx, by0 + 22f, 28f)
                // шкала терпения под зверем
                val col = if (frLeft > 0.5f) lerpC(Color(0xFFFFB74D), Color(0xFF66BB6A), (frLeft - 0.5f) * 2f) else lerpC(Color(0xFFE53935), Color(0xFFFFB74D), frLeft * 2f)
                drawRoundRect(Color(0x55000000), Offset(sx - 26f, L.counterY - 22f), Size(52f, 8f), CornerRadius(4f))
                drawRoundRect(col, Offset(sx - 26f, L.counterY - 22f), Size(52f * frLeft, 8f), CornerRadius(4f))
                drawRoundRect(ckInk, Offset(sx - 26f, L.counterY - 22f), Size(52f, 8f), CornerRadius(4f), style = Stroke(1.6f))
            } else {
                val r = seat.react
                if (r != null) {
                    val by0 = L.guestY - 90f
                    drawRoundRect(Color.White, Offset(sx - 48f, by0), Size(96f, 42f), CornerRadius(18f))
                    drawRoundRect(ckInk, Offset(sx - 48f, by0), Size(96f, 42f), CornerRadius(18f), style = Stroke(2.5f))
                    ckEmoji(m, r.emoji, sx - 28f, by0 + 21f, 26f)
                    ckLabel(m, r.word, sx + 12f, by0 + 21f, 11f, ckInk)
                }
            }
        } else {
            ckEmoji(m, "🪑", sx, L.guestY + 8f, 34f, alpha = 0.45f)
        }
    }

    // тарелка на прилавке
    val pl = L.plate
    drawOval(Color(0x33000000), Offset(pl.x - 42f, pl.y - 6f), Size(88f, 28f))
    drawOval(Color.White, Offset(pl.x - 44f, pl.y - 14f), Size(88f, 28f))
    drawOval(Color(0xFFB0BEC5), Offset(pl.x - 44f, pl.y - 14f), Size(88f, 28f), style = Stroke(3f))
    drawOval(Color(0xFFECEFF1), Offset(pl.x - 30f, pl.y - 9f), Size(60f, 18f))
    val plateItems = sim?.plate
    plateItems?.forEachIndexed { i, c -> ckDish(m, c, pl.x + (if (plateItems.size == 1) 0f else if (i == 0) -18f else 18f), pl.y - 12f, 30f, t) }
    if (plateItems.isNullOrEmpty()) ckEmoji(m, "🍽️", pl.x, pl.y - 2f, 20f, alpha = 0.35f)
    // корзина
    val tr = L.trash
    drawRoundRect(Color(0xFF90A4AE), Offset(tr.x - 19f, tr.y - 14f), Size(38f, 36f), CornerRadius(8f))
    drawRoundRect(Color(0xFF546E7A), Offset(tr.x - 24f, tr.y - 22f), Size(48f, 10f), CornerRadius(5f))
    ckEmoji(m, "🗑️", tr.x, tr.y + 6f, 24f, alpha = 0.9f)
    // горшок с цветком слева
    drawRoundRect(Color(0xFFD7845E), Offset(28f, L.counterY - 8f), Size(26f, 20f), CornerRadius(5f))
    ckEmoji(m, "🌼", 41f, L.counterY - 20f, 26f)

    // «остров»: доска и плита
    val iy0 = L.rowY - 56f
    drawRoundRect(Color(0x33000000), Offset(6f, iy0 + 6f), Size(w - 8f, 124f), CornerRadius(16f))
    drawRoundRect(Color(0xFF8D6E63), Offset(4f, iy0), Size(w - 8f, 122f), CornerRadius(16f))
    drawRoundRect(Color(0xFFCFD8DC), Offset(4f, iy0), Size(w - 8f, 108f), CornerRadius(16f))
    drawRoundRect(Color(0xFF5D4037), Offset(4f, iy0), Size(w - 8f, 122f), CornerRadius(16f), style = Stroke(3f))

    // доска
    val bp = L.board
    val bx = bp.x - L.boardW / 2; val byy = bp.y - L.boardH / 2
    drawRoundRect(Color(0xFFD7A86E), Offset(bx, byy), Size(L.boardW, L.boardH), CornerRadius(14f))
    for (k in 1..4) drawLine(Color(0x22795548), Offset(bx + 8f, byy + k * 15f), Offset(bx + L.boardW - 8f, byy + k * 15f + 3f), 2f)
    drawRoundRect(Color(0xFF8D6E63), Offset(bx, byy), Size(L.boardW, L.boardH), CornerRadius(14f), style = Stroke(3f))
    drawCircle(Color(0xFF8D6E63), 5f, Offset(bx + L.boardW - 12f, byy + 12f))
    val bd = sim?.board
    if (bd != null) {
        val c = CCarry(bd.fam, bd.chopped, bd.fine, null)
        ckDish(m, c, bp.x, bp.y + 4f, 40f, t)
        if (!bd.chopped && sim != null) {
            val bx0 = bp.x - 50f; val by = byy - 14f
            drawRoundRect(Color(0xFF455A64), Offset(bx0, by - 7f), Size(100f, 14f), CornerRadius(7f))
            val half = sim.chopHalf * 100f
            drawRoundRect(Color(0xFFFFD54F), Offset(bx0 + 50f - half, by - 7f), Size(half * 2f, 14f), CornerRadius(7f))
            val mx = bx0 + sim.chopPos(bd) * 100f
            drawCircle(Color.White, 9f, Offset(mx, by)); drawCircle(ckInk, 9f, Offset(mx, by), style = Stroke(2.5f))
            ckLabel(m, "Режь!", bp.x, by - 20f, 15f, ckInk)
        }
    } else ckEmoji(m, "🔪", bp.x, bp.y, 26f, alpha = 0.4f)

    // плита и сковородки
    val panN = sim?.panN ?: L.panN
    val sx0 = L.panX(0) - L.panR - 14f
    val swd = L.panX(panN - 1) - L.panX(0) + L.panR * 2 + 28f
    drawRoundRect(Color(0xFF37474F), Offset(sx0, L.rowY - L.panR - 12f), Size(swd, L.panR * 2 + 26f), CornerRadius(16f))
    drawRoundRect(Color(0xFF263238), Offset(sx0, L.rowY - L.panR - 12f), Size(swd, L.panR * 2 + 26f), CornerRadius(16f), style = Stroke(3f))
    for (i in 0 until panN) {
        val pc = L.pan(i); val r = L.panR
        val food = sim?.pans?.getOrNull(i)?.food
        // конфорка и пламя
        drawCircle(Color(0xFF263238), r + 6f, Offset(pc.x, pc.y + 3f))
        if (food != null) {
            drawCircle(Color(0x55FF7043), r + 16f + 2f * sin(t * 9f + i), pc)
            for (k in 0..6) {
                val ang = PI.toFloat() * (0.12f + 0.76f * k / 6f)
                val fl = 6f + 5f * abs(sin(t * 11f + k * 1.7f))
                val px = pc.x + kotlin.math.cos(ang) * (r + 3f); val py = pc.y + 8f + kotlin.math.sin(ang) * (r + 3f)
                drawOval(Color(0xFFFF7043), Offset(px - 3.2f, py - 1f), Size(6.4f, fl))
                drawOval(Color(0xFFFFD54F), Offset(px - 1.7f, py - 1f), Size(3.4f, fl * 0.55f))
            }
        }
        drawCircle(Color(0xFF212121), r, pc)
        drawCircle(Color(0xFF546E7A), r - 5f, pc)
        drawCircle(Color(0x22FFFFFF), r - 10f, Offset(pc.x - 4f, pc.y - 4f))
        drawRoundRect(Color(0xFF212121), Offset(pc.x + r - 4f, pc.y - 5f), Size(26f, 10f), CornerRadius(5f))
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
        } else {
            ckEmoji(m, "🍳", pc.x, pc.y, r * 0.8f, alpha = 0.3f)
        }
    }

    // лента с валиками
    val by = L.beltY
    drawRoundRect(Color(0x33000000), Offset(-10f, by - L.beltH / 2 + 5f), Size(w + 20f, L.beltH), CornerRadius(12f))
    drawRoundRect(Color(0xFF455A64), Offset(-10f, by - L.beltH / 2), Size(w + 20f, L.beltH), CornerRadius(12f))
    drawRoundRect(Color(0xFF37474F), Offset(-10f, by - L.beltH / 2 + 7f), Size(w + 20f, L.beltH - 14f), CornerRadius(8f))
    var rx = -((t * CookCfg.BELT_SPEED) % 26f)
    while (rx < w + 20f) {
        drawLine(Color(0x33FFFFFF), Offset(rx, by - L.beltH / 2 + 9f), Offset(rx - 7f, by + L.beltH / 2 - 9f), 3f)
        rx += 26f
    }
    drawRoundRect(Color(0xFF90A4AE), Offset(-10f, by - L.beltH / 2), Size(w + 20f, L.beltH), CornerRadius(12f), style = Stroke(4f))
    sim?.belt?.forEach { b ->
        drawCircle(Color(0x33000000), 27f, Offset(b.x + 1f, by + 4f))
        drawCircle(Color(0xFFF5F5F5), 27f, Offset(b.x, by))
        drawCircle(Color(0xFFB0BEC5), 27f, Offset(b.x, by), style = Stroke(2.5f))
        ckEmoji(m, b.fam.emoji, b.x, by + 1.5f * sin(t * 4f + b.id), 36f)
    }

    // повар
    val ch = ref.chef
    val carried = sim?.hand
    ckChef(ch, carried, m, t, 0.82f, if (ref.knife > 0f) 1f - ref.knife / 0.30f else 0f)

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

    // блюдо летит к гостю
    for (f in ref.flies) {
        val k = f.t.coerceIn(0f, 1f)
        val px = f.x0 + (f.x1 - f.x0) * k
        val py = f.y0 + (f.y1 - f.y0) * k - 50f * sin(k * PI.toFloat())
        ckEmoji(m, f.text, px, py, 30f, sc = 1f - 0.4f * k)
    }
    // частицы и всплывающие надписи
    for (p in ref.parts) {
        val a = p.life.coerceIn(0f, 1f)
        if (p.text != null) ckEmoji(m, p.text, p.x, p.y, p.size, alpha = a) else drawCircle(p.color, p.size, Offset(p.x, p.y), alpha = a)
    }
    for (f in ref.floats) ckLabel(m, f.text, f.x, f.y, 16f, f.color, alpha = f.life.coerceIn(0f, 1f), outline = true)
}
