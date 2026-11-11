package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.LocalAgeProfile
import com.example.shared.SoundManager
import com.example.shared.i18n.Text
import com.example.shared.rememberHaptics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Комната «Птичка-летунья» (по пиксельным ассетам: птички, труба, фон, пол, монетка, звуки).
// Тап по экрану — птичка взмахивает крыльями. Нужно пролетать в ворота между трубами и собирать монетки.
// Три режима (кнопка слева под монетами). «Спокойный» — для малышей: задел трубу — птичка мягко подпрыгивает и ненадолго
// становится «призраком», игра не кончается. «Сердечки» — три удара. «Смертельный» — любой удар (труба, земля, потолок) и игра проиграна.
// Правила ударов лежат в BirdRules.kt (их проверяет BirdRulesTest). Рекорд у каждого режима свой: SharedPreferences rec_0..rec_2.
// Награды: 6 наклеек альбома «Птичка-летунья»; открывают новых птичек-друзей, блестящие следы и корону.

private const val BF_PREFS = "birdfly_prefs"
private const val BF_READY = 0
private const val BF_PLAY = 1
private const val BF_DEAD = 2

private class BfSticker(val id: Int, val name: String, val note: String, val how: String)

private val BF_STICKERS = listOf(
    BfSticker(0, "Первый полёт", "Новая подружка — зелёная птичка", "Пролети в ворота между трубами"),
    BfSticker(1, "Монетная куча", "Золотой блестящий след", "Собери много монеток"),
    BfSticker(2, "Без ойков", "Новый друг — жёлтая птичка", "Пролети несколько ворот подряд, не задев трубы"),
    BfSticker(3, "Дальний перелёт", "Радужный след", "Пролети много ворот"),
    BfSticker(4, "Богач", "Корона для птички", "Собери целую гору монеток"),
    BfSticker(5, "Повелитель неба", "Все подарки теперь твои!", "Собери все наклейки")
)
private val BF_NEED = intArrayOf(3, 30, 6, 40, 150)
private val BF_NEED_T = intArrayOf(2, 15, 3, 20, 70)
private val BF_RAINBOW = intArrayOf(0xFFFF5A5A.toInt(), 0xFFFF9F43.toInt(), 0xFFFFD84D.toInt(), 0xFF6BD66B.toInt(), 0xFF4FB4F2.toInt(), 0xFFB77BE8.toInt())

private class BfMode(val emoji: String, val name: String, val about: String)

// Порядок совпадает с BF_MODE_CALM / BF_MODE_HEARTS / BF_MODE_DEADLY.
private val BF_MODES = listOf(
    BfMode("🐥", "Спокойный", "Задела трубу — подпрыгнула и летим дальше. Считаем ворота подряд без ойков."),
    BfMode("❤️", "Сердечки", "Три сердечка. Каждый ойк — минус сердечко. Сердечки кончились — игра окончена."),
    BfMode("💀", "Смертельный", "Труба, земля или потолок — птичка падает, игра окончена. Лети на рекорд!")
)

private class BfPipe(var x: Float, val gy: Float) { var passed = false }
private class BfCoin(var x: Float, val y: Float) { var taken = false; val ph = Random.nextFloat() * 6f }
private class BfP(var kind: Int, var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val size: Float, val col: Int) { val maxLife = life }

private class BfModel {
    var clock = 0f
    var state = BF_READY
    var by = 0f
    var vy = 0f
    var tilt = 0f
    var flapT = 0f
    var inv = 0f
    var cd = 0f
    var dist = 0f
    var lastGy = -1f
    val pipes = ArrayList<BfPipe>()
    val coins = ArrayList<BfCoin>()
    val parts = ArrayList<BfP>()
    var streak = 0
    var runPipes = 0
    var runCoins = 0
    var tp = 0
    var tc = 0
    var sb = 0
    var mask = 0
    var bird = 0
    var demo = false
    var lastTouch = 0f
    var hintT = 0f
    var trailT = 0f
    var tweetT = 4f
    var groundSnd = 0f
    val pending = ArrayList<Int>()
    // режимы и рекорды
    var mode = BF_MODE_CALM
    var lives = BF_LIVES
    val rec = IntArray(3)       // рекорд по каждому режиму (спокойный — серия ворот без ойков)
    var recBase = 0             // рекорд режима в начале забега
    var recMsgT = 0f            // сколько ещё показывать «Новый рекорд!»
    var recMsgDone = false
    var flash = 0f
    var deadT = 0f
    var landed = false
}

private class BfSheet(val img: ImageBitmap, val fw: Int, val fh: Int)

// Пиксельная тема рисуется без сглаживания, мультяшная — со сглаживанием.
private var bfFq: FilterQuality = FilterQuality.None

private fun DrawScope.bfBird(s: BfSheet, frame: Int, dx: Float, dy: Float, dw: Float, dh: Float, alpha: Float = 1f) =
    bfImg(s.img, frame * s.fw, 0, s.fw, s.fh, dx, dy, dw, dh, alpha)

// Слой параллакса: зеркальная плитка, поэтому стыков не видно.
private fun DrawScope.bfLayer(img: ImageBitmap, scroll: Float, bottom: Float, hh: Float, alpha: Float = 1f) {
    val tw = hh * img.width / img.height
    val off = -(scroll % (2f * tw))
    var i = 0
    while (off + i * tw < size.width) {
        val x = off + i * tw
        if (x + tw > 0f) {
            if (i % 2 == 1) withTransform({ scale(-1f, 1f, Offset(x + tw / 2f, 0f)) }) { bfImg(img, 0, 0, img.width, img.height, x, bottom - hh, tw + 1f, hh, alpha) }
            else bfImg(img, 0, 0, img.width, img.height, x, bottom - hh, tw + 1f, hh, alpha)
        }
        i++
    }
}

private fun DrawScope.bfThemeIcon(theme: Int) {
    val w = size.width; val h = size.height
    if (theme == 0) {
        val u = w / 5f
        for (a in 0..2) for (b in 0..2) if ((a + b) % 2 == 0) drawRect(Color(0xFF3F8FE0), Offset(u * (0.5f + a), u * (0.5f + b)), Size(u, u)) else drawRect(Color(0xFF9CCBF2), Offset(u * (0.5f + a), u * (0.5f + b)), Size(u, u))
    } else {
        drawCircle(Color(0xFF3F8FE0), w * 0.2f, Offset(w * 0.36f, h * 0.55f)); drawCircle(Color(0xFF3F8FE0), w * 0.26f, Offset(w * 0.55f, h * 0.45f))
        drawCircle(Color(0xFF3F8FE0), w * 0.18f, Offset(w * 0.72f, h * 0.58f)); drawRect(Color(0xFF3F8FE0), Offset(w * 0.3f, h * 0.55f), Size(w * 0.5f, h * 0.2f))
    }
}

private fun DrawScope.bfImg(img: ImageBitmap, sx: Int, sy: Int, sw: Int, sh: Int, dx: Float, dy: Float, dw: Float, dh: Float, alpha: Float = 1f) {
    drawImage(
        img, IntOffset(sx, sy), IntSize(sw, sh),
        IntOffset(Math.round(dx), Math.round(dy)), IntSize(max(1, Math.round(dw)), max(1, Math.round(dh))),
        alpha, filterQuality = bfFq
    )
}

private fun DrawScope.bfSpark(x: Float, y: Float, r: Float, col: Color) {
    val p = Path().apply {
        moveTo(x, y - r); quadraticTo(x + r * 0.12f, y - r * 0.12f, x + r, y)
        quadraticTo(x + r * 0.12f, y + r * 0.12f, x, y + r); quadraticTo(x - r * 0.12f, y + r * 0.12f, x - r, y)
        quadraticTo(x - r * 0.12f, y - r * 0.12f, x, y - r); close()
    }
    drawPath(p, col)
}

private fun DrawScope.bfCrown(cx: Float, cy: Float, s: Float) {
    val k = Path().apply {
        moveTo(cx - s, cy); lineTo(cx - s * 1.05f, cy - s * 0.9f); lineTo(cx - s * 0.5f, cy - s * 0.4f)
        lineTo(cx, cy - s * 1.1f); lineTo(cx + s * 0.5f, cy - s * 0.4f); lineTo(cx + s * 1.05f, cy - s * 0.9f); lineTo(cx + s, cy); close()
    }
    drawPath(k, Color(0xFFFFC93C))
    drawPath(k, Color(0xFFD99A00), style = Stroke(s * 0.12f))
    drawCircle(Color(0xFFE5566D), s * 0.14f, Offset(cx, cy - s * 0.45f))
}

private fun DrawScope.bfHeart(cx: Float, cy: Float, s: Float, col: Color) {
    val p = Path().apply {
        moveTo(cx, cy + s * 0.9f)
        cubicTo(cx - s * 1.5f, cy - s * 0.1f, cx - s * 0.8f, cy - s * 1.2f, cx, cy - s * 0.45f)
        cubicTo(cx + s * 0.8f, cy - s * 1.2f, cx + s * 1.5f, cy - s * 0.1f, cx, cy + s * 0.9f)
        close()
    }
    drawPath(p, col)
}

private fun DrawScope.bfStickerArt(id: Int, c: Offset, r: Float, birds: List<BfSheet>, coin: ImageBitmap) {
    val bg = when (id) {
        0 -> listOf(Color(0xFFBFF0C8), Color(0xFF5DBE6E)); 1 -> listOf(Color(0xFFFFF0A0), Color(0xFFFFB400))
        2 -> listOf(Color(0xFFFFE0B8), Color(0xFFFF9F43)); 3 -> listOf(Color(0xFFD0E8FF), Color(0xFF4FA0E8))
        4 -> listOf(Color(0xFFFFD0E4), Color(0xFFE5669A)); else -> listOf(Color(0xFFFFE9A0), Color(0xFFFFA2C2))
    }
    drawCircle(Color(0x22000000), r * 1.02f, Offset(c.x, c.y + r * 0.06f))
    drawCircle(Brush.verticalGradient(bg, c.y - r, c.y + r), r, c)
    drawCircle(Color.White, r, c, style = Stroke(r * 0.07f))
    val bw = r * 1.2f; val bh = bw * birds[0].fh / birds[0].fw
    when (id) {
        0 -> bfBird(birds[1], 0, c.x - bw / 2, c.y - bh / 2, bw, bh)
        1 -> for (k in 0..2) { val s = r * 0.62f; bfImg(coin, 0, 0, coin.width, coin.height, c.x - s / 2 + (k - 1) * r * 0.4f, c.y - s / 2 + (1 - k % 2) * r * 0.15f, s, s) }
        2 -> bfBird(birds[2], 0, c.x - bw / 2, c.y - bh / 2, bw, bh)
        3 -> {
            drawRect(Color(0xFF6BBE45), Offset(c.x - r * 0.62f, c.y - r * 0.8f), Size(r * 0.28f, r * 0.55f))
            drawRect(Color(0xFF6BBE45), Offset(c.x - r * 0.62f, c.y + r * 0.25f), Size(r * 0.28f, r * 0.55f))
            drawRect(Color(0xFF6BBE45), Offset(c.x + r * 0.34f, c.y - r * 0.8f), Size(r * 0.28f, r * 0.45f))
            drawRect(Color(0xFF6BBE45), Offset(c.x + r * 0.34f, c.y + r * 0.15f), Size(r * 0.28f, r * 0.65f))
            val s = r * 0.7f; bfBird(birds[0], 0, c.x - s / 2, c.y - s / 2, s, s)
        }
        4 -> { val s = r * 0.8f; bfImg(coin, 0, 0, coin.width, coin.height, c.x - s / 2, c.y - s * 0.3f, s, s); bfCrown(c.x, c.y - r * 0.15f, r * 0.34f) }
        else -> { bfBird(birds[2], 0, c.x - bw / 2, c.y - bh * 0.35f, bw, bh); bfCrown(c.x, c.y - r * 0.28f, r * 0.34f) }
    }
}

@Composable
fun BirdFlightScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val sm = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val toddler = LocalAgeProfile.current.isToddler2
    val prefs = remember { context.getSharedPreferences(BF_PREFS, Context.MODE_PRIVATE) }

    val bgImg = ImageBitmap.imageResource(com.example.R.drawable.bd_bg)
    val floorImg = ImageBitmap.imageResource(com.example.R.drawable.bd_floor)
    val pipeImg = ImageBitmap.imageResource(com.example.R.drawable.bd_pipe)
    var theme by remember { mutableIntStateOf(prefs.getInt("theme", 0)) }
    val coinPix = ImageBitmap.imageResource(com.example.R.drawable.bd_coin)
    val coin2 = ImageBitmap.imageResource(com.example.R.drawable.ic_coin)
    val skyImg = ImageBitmap.imageResource(com.example.R.drawable.bd2_sky)
    val m1Img = ImageBitmap.imageResource(com.example.R.drawable.bd2_m1)
    val m2Img = ImageBitmap.imageResource(com.example.R.drawable.bd2_m2)
    val cl1Img = ImageBitmap.imageResource(com.example.R.drawable.bd2_cl1)
    val cl2Img = ImageBitmap.imageResource(com.example.R.drawable.bd2_cl2)
    val groundImg = ImageBitmap.imageResource(com.example.R.drawable.bd2_ground)
    val towerTop = ImageBitmap.imageResource(com.example.R.drawable.bd2_tower_top)
    val towerBot = ImageBitmap.imageResource(com.example.R.drawable.bd2_tower_bot)
    val bushes = listOf(
        ImageBitmap.imageResource(com.example.R.drawable.bd2_bush1),
        ImageBitmap.imageResource(com.example.R.drawable.bd2_bush2),
        ImageBitmap.imageResource(com.example.R.drawable.bd2_bush3)
    )
    val cb0 = ImageBitmap.imageResource(com.example.R.drawable.bd2_bird0)
    val cb1 = ImageBitmap.imageResource(com.example.R.drawable.bd2_bird1)
    val cb2 = ImageBitmap.imageResource(com.example.R.drawable.bd2_bird2)
    val coinImg = if (theme == 1) coin2 else coinPix
    bfFq = if (theme == 1) FilterQuality.Medium else FilterQuality.None
    val birdRed = ImageBitmap.imageResource(com.example.R.drawable.bd_bird_red)
    val birdGreen = ImageBitmap.imageResource(com.example.R.drawable.bd_bird_green)
    val birdYellow = ImageBitmap.imageResource(com.example.R.drawable.bd_bird_yellow)
    val birds = remember(birdRed, birdGreen, birdYellow, cb0, cb1, cb2, theme) {
        if (theme == 1) listOf(BfSheet(cb0, 128, 128), BfSheet(cb1, 128, 128), BfSheet(cb2, 128, 128))
        else listOf(BfSheet(birdRed, 60, 58), BfSheet(birdGreen, 60, 58), BfSheet(birdYellow, 60, 58))
    }
    val pixelFont = remember { FontFamily(Font(com.example.R.font.jersey10_regular)) }

    LaunchedEffect(Unit) {
        sm.preloadRaw(
            listOf("fx_bd_flap", "fx_bd_hit", "fx_bd_pickup", "fx_bd_drop", "fx_bd_star", "fx_bd_unlock", "fx_bd_reward", "fx_bd_whoosh", "fx_wr_bird1", "fx_wr_bird2")
        )
    }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(0) }
    val m = remember {
        BfModel().also { md ->
            md.tp = prefs.getInt("tp", 0); md.tc = prefs.getInt("tc", 0); md.sb = prefs.getInt("sb", 0)
            md.mask = prefs.getInt("mask", 0); md.bird = prefs.getInt("bird", 0)
            md.rec[BF_MODE_CALM] = prefs.getInt("rec_0", md.sb) // рекорд спокойного режима начинается с лучшей серии без ойков
            md.rec[BF_MODE_HEARTS] = prefs.getInt("rec_1", 0)
            md.rec[BF_MODE_DEADLY] = prefs.getInt("rec_2", 0)
            // малыши всегда начинают со спокойного режима
            md.mode = if (toddler) BF_MODE_CALM else prefs.getInt("mode", BF_MODE_CALM).coerceIn(BF_MODE_CALM, BF_MODE_DEADLY)
            md.recBase = md.rec[md.mode]
        }
    }
    var hintText by remember { mutableStateOf("") }
    var showAlbum by remember { mutableStateOf(false) }
    var rewardSticker by remember { mutableStateOf<BfSticker?>(null) }
    var demoOn by remember { mutableStateOf(false) }
    var coinsUi by remember { mutableIntStateOf(m.tc) }
    var scoreUi by remember { mutableIntStateOf(0) }
    var modeUi by remember { mutableIntStateOf(m.mode) }
    var livesUi by remember { mutableIntStateOf(BF_LIVES) }
    var recUi by remember { mutableIntStateOf(m.rec[m.mode]) }
    var recBanner by remember { mutableStateOf(false) }
    var showModes by remember { mutableStateOf(false) }
    var showOver by remember { mutableStateOf(false) }
    var overScore by remember { mutableIntStateOf(0) }
    var overCoins by remember { mutableIntStateOf(0) }
    var overNew by remember { mutableStateOf(false) }
    var birdUi by remember { mutableIntStateOf(m.bird) }
    var maskUi by remember { mutableIntStateOf(m.mask) }

    fun W() = boxSize.width.toFloat()
    fun H() = boxSize.height.toFloat()
    fun floorH() = H() * 0.12f
    fun yF() = H() - floorH()
    fun br() = yF() * 0.045f
    fun bx() = W() * 0.28f
    fun pw() = W() * (if (toddler) 0.17f else 0.15f)
    fun gap() = yF() * (if (toddler) 0.44f else 0.36f)
    fun speed() = W() * ((if (toddler) 0.20f else 0.27f) + min(0.08f, m.tp.coerceAtMost(30) * 0.0027f))
    fun spacing() = W() * (if (toddler) 0.95f else 0.80f)
    fun grav() = yF() * (if (toddler) 1.5f else 1.9f)
    fun flapV() = yF() * (if (toddler) 0.58f else 0.62f)
    fun snd(n: String, v: Float = 1f, r: Float = 1f) { sm.play(n, volume = v, rate = r) }
    fun need(i: Int) = if (toddler) BF_NEED_T[i] else BF_NEED[i]
    fun progress(i: Int) = when (i) { 0, 3 -> m.tp; 1, 4 -> m.tc; else -> m.sb }
    fun birdOpen(b: Int) = b == 0 || (b == 1 && (m.mask and 1) != 0) || (b == 2 && (m.mask and 4) != 0)

    fun spark(x: Float, y: Float, n: Int, col: Int, speed: Float, life: Float) {
        for (i in 0 until n) {
            val a = Random.nextFloat() * 6.283f
            val sp = speed * (0.35f + Random.nextFloat() * 0.65f)
            if (m.parts.size < 400) m.parts.add(BfP(0, x, y, cos(a) * sp, sin(a) * sp, life * (0.6f + Random.nextFloat() * 0.6f), br() * (0.35f + Random.nextFloat() * 0.35f), col))
        }
    }
    fun save() {
        prefs.edit().putInt("tp", m.tp).putInt("tc", m.tc).putInt("sb", m.sb).putInt("mask", m.mask).putInt("bird", m.bird)
            .putInt("rec_0", m.rec[0]).putInt("rec_1", m.rec[1]).putInt("rec_2", m.rec[2]).apply()
    }
    fun showNext() {
        if (m.pending.isEmpty()) return
        val id = m.pending.removeAt(0)
        rewardSticker = BF_STICKERS[id]
        snd(if (id == 5) "fx_bd_reward" else "fx_bd_unlock", 0.95f)
        haptics.vibratePop()
        spark(W() * 0.5f, H() * 0.4f, 24, 0xFFFFE066.toInt(), W() * 0.5f, 1.2f)
    }
    fun checkRewards() {
        for (i in 0 until 5) if ((m.mask and (1 shl i)) == 0 && progress(i) >= need(i)) { m.mask = m.mask or (1 shl i); m.pending.add(i) }
        if ((m.mask and 31) == 31 && (m.mask and 32) == 0) { m.mask = m.mask or 32; m.pending.add(5) }
        maskUi = m.mask
        save()
        if (rewardSticker == null && m.pending.isNotEmpty()) showNext()
    }

    // Счёт забега: в спокойном режиме — ворота подряд без ойков, в остальных — ворота за весь забег.
    fun curScore() = if (m.mode == BF_MODE_CALM) m.streak else m.runPipes
    fun lethal() = !m.demo && m.mode == BF_MODE_DEADLY

    // После пролёта ворот (только в настоящей игре, не в демо): рекорд режима растёт сразу, «Новый рекорд!» — один раз за забег.
    fun scoreCheck() {
        val sc = curScore()
        if (sc > m.rec[m.mode]) { m.rec[m.mode] = sc; recUi = sc }
        if (!m.recMsgDone && m.recBase > 0 && bfBeatsRecord(sc, m.recBase)) {
            m.recMsgDone = true; m.recMsgT = 2.4f
            snd("fx_bd_unlock", 0.85f, 1.1f)
            haptics.vibratePop()
            spark(W() * 0.5f, H() * 0.22f, 16, 0xFFFFE066.toInt(), W() * 0.4f, 1.0f)
        }
    }

    fun spawnPipe() {
        val w = W(); val pf = yF(); val g = gap()
        val lo = g / 2f + pf * 0.08f; val hi = pf - g / 2f - pf * 0.06f
        val prev = if (m.lastGy < 0f) pf * 0.45f else m.lastGy
        val gy = (prev + (Random.nextFloat() - 0.5f) * pf * 0.44f).coerceIn(lo, hi)
        val x = if (m.pipes.isEmpty()) w * 1.15f else m.pipes.last().x + spacing()
        // монетки: ведут от прошлых ворот к новым и лежат в центре ворот
        for (k in 1..3) { val f = k * 0.25f; m.coins.add(BfCoin(x - spacing() * (1f - f) , prev + (gy - prev) * f)) }
        m.coins.add(BfCoin(x, gy))
        m.pipes.add(BfPipe(x, gy))
        m.lastGy = gy
    }

    // Птичка снова в центре, мир пустой, счёт нулевой: ждём первого тапа.
    fun resetToReady() {
        m.state = BF_READY
        m.pipes.clear(); m.coins.clear(); m.lastGy = -1f
        m.runPipes = 0; m.runCoins = 0; m.streak = 0
        m.by = yF() * 0.45f; m.vy = 0f; m.tilt = 0f; m.inv = 0f
        m.lives = BF_LIVES; m.flash = 0f; m.deadT = 0f; m.landed = false
        m.recBase = m.rec[m.mode]; m.recMsgT = 0f; m.recMsgDone = false
        livesUi = BF_LIVES; scoreUi = 0; recUi = m.rec[m.mode]; recBanner = false
    }

    fun flap(real: Boolean) {
        if (m.state == BF_DEAD) return
        if (m.state == BF_READY) {
            m.state = BF_PLAY; m.pipes.clear(); m.coins.clear(); m.lastGy = -1f; m.runPipes = 0; m.runCoins = 0; m.streak = 0; m.dist = 0f
            m.lives = BF_LIVES; livesUi = BF_LIVES; scoreUi = 0
            m.recBase = m.rec[m.mode]; m.recMsgT = 0f; m.recMsgDone = false
        }
        m.vy = -flapV()
        m.flapT = 0.35f
        snd("fx_bd_flap", 0.7f, 0.95f + Random.nextFloat() * 0.15f)
        if (real) haptics.vibrateShort()
        if (m.parts.size < 400) for (i in 0 until 3) m.parts.add(BfP(1, bx() - br(), m.by + br() * 0.5f, -W() * 0.05f - Random.nextFloat() * W() * 0.05f, Random.nextFloat() * yF() * 0.05f, 0.4f, br() * 0.25f, 0xFFFFFFFF.toInt()))
    }

    fun stopDemo() { if (m.demo) { m.demo = false; demoOn = false } }
    fun startDemo() { m.demo = true; demoOn = true; m.lastTouch = m.clock }

    // Мягкий удар: подпрыгнула и стала «призраком». floor=true — удар о землю (подброс уже сделан, «призрак» короче).
    fun bump(floor: Boolean = false) {
        if (m.inv > 0f) return
        if (floor) {
            m.inv = 1.0f
        } else {
            val px = m.pipes.firstOrNull { it.x + pw() / 2f + br() > bx() }?.x ?: bx()
            m.inv = max(1.2f, (px + pw() / 2f + br() - bx()) / speed() + 0.25f)
            m.vy = -yF() * 0.32f
        }
        m.streak = 0
        if (m.mode == BF_MODE_CALM) { m.recBase = m.rec[BF_MODE_CALM]; m.recMsgDone = false; m.recMsgT = 0f; recBanner = false }
        snd("fx_bd_hit", 0.7f); snd("fx_it_drp_${6 + Random.nextInt(6)}", 0.5f)
        haptics.vibrateSquish()
        spark(bx(), m.by, 10, 0xFFFFFFFF.toInt(), W() * 0.35f, 0.7f)
        spark(bx(), m.by, 6, 0xFFFFC93C.toInt(), W() * 0.3f, 0.7f)
    }

    // Птичка разбилась: мир замирает, она падает; через секунду появится экран проигрыша.
    fun die() {
        if (m.state != BF_PLAY) return
        m.state = BF_DEAD
        m.deadT = 0f; m.landed = false
        m.inv = 0f; m.flapT = 0f
        m.vy = -yF() * 0.30f
        m.flash = 0.3f
        m.recMsgT = 0f; recBanner = false
        snd("fx_bd_hit", 0.9f); snd("fx_it_drp_${6 + Random.nextInt(6)}", 0.5f)
        haptics.vibrateSquish()
        spark(bx(), m.by, 14, 0xFFFFFFFF.toInt(), W() * 0.45f, 0.8f)
        spark(bx(), m.by, 10, 0xFFFFC93C.toInt(), W() * 0.4f, 0.8f)
        save()
    }

    // Удар о трубу (floor=false) или о землю (floor=true): что будет, решает bfCrashOutcome.
    fun crash(floor: Boolean) {
        if (m.state != BF_PLAY) return
        when (bfCrashOutcome(m.mode, m.demo, floor, m.inv > 0f, m.lives)) {
            BfCrash.NONE -> Unit
            BfCrash.BUMP -> bump()
            BfCrash.LOSE_LIFE -> { m.lives--; livesUi = m.lives; bump(floor) }
            BfCrash.DIE -> { m.lives = 0; livesUi = 0; die() }
        }
    }

    fun openOver() {
        val sc = curScore()
        overScore = sc
        overCoins = m.runCoins
        overNew = bfBeatsRecord(sc, m.recBase)
        showOver = true
        if (overNew) {
            snd("fx_bd_reward", 0.95f)
            haptics.vibratePop()
            spark(W() * 0.5f, H() * 0.35f, 26, 0xFFFFE066.toInt(), W() * 0.55f, 1.3f)
        }
    }

    fun setMode(k: Int) {
        m.mode = k; modeUi = k
        prefs.edit().putInt("mode", k).apply()
        showModes = false; showOver = false
        resetToReady()
        m.lastTouch = m.clock
        snd("fx_bd_whoosh", 0.7f)
    }

    fun retry() {
        showOver = false
        resetToReady()
        m.lastTouch = m.clock
        snd("fx_bd_pickup", 0.6f, 1.3f)
    }

    fun update(dt: Float) {
        if (W() <= 0f) return
        m.clock += dt
        val w = W(); val pf = yF(); val b = br()
        val overlay = showAlbum || rewardSticker != null || showModes || showOver
        if (!m.demo && !overlay && m.state != BF_DEAD && m.clock - m.lastTouch > 5f) startDemo()
        if (overlay) return

        m.flapT = max(0f, m.flapT - dt); m.inv = max(0f, m.inv - dt); m.cd = max(0f, m.cd - dt); m.groundSnd = max(0f, m.groundSnd - dt)
        m.flash = max(0f, m.flash - dt); m.recMsgT = max(0f, m.recMsgT - dt)
        val real = !m.demo

        if (m.state == BF_READY) {
            m.by = pf * 0.45f + sin(m.clock * 3f) * pf * 0.025f
            m.vy = 0f
            m.tilt = sin(m.clock * 3f) * 6f
            m.tweetT -= dt
            if (m.tweetT <= 0f) { m.tweetT = 5f + Random.nextFloat() * 4f; snd(if (Random.nextBoolean()) "fx_wr_bird1" else "fx_wr_bird2", 0.5f) }
            if (m.demo && m.clock - m.lastTouch > 0.8f) flap(false)
        } else if (m.state == BF_DEAD) {
            // птичка падает, мир замер
            m.deadT += dt
            m.vy = min(m.vy + grav() * dt, pf * 1.1f)
            m.by += m.vy * dt
            if (!m.landed) m.tilt = min(95f, m.tilt + 360f * dt)
            if (m.by - b < 0f) { m.by = b; if (m.vy < 0f) m.vy = 0f }
            if (m.by + b >= pf) {
                m.by = pf - b
                m.vy = 0f
                if (!m.landed) {
                    m.landed = true
                    snd("fx_bd_drop", 0.8f, 0.85f)
                    spark(bx(), pf - 2f, 8, 0xFFE8D9A8.toInt(), W() * 0.25f, 0.6f)
                }
            }
            if (!showOver && ((m.landed && m.deadT > 1.0f) || m.deadT > 2.2f)) openOver()
        } else {
            // бот-демо
            if (m.demo && m.cd <= 0f) {
                val np = m.pipes.firstOrNull { it.x + pw() / 2f > bx() - b }
                val target = np?.gy ?: pf * 0.45f
                if (m.by > target + b * 0.7f && m.vy > -pf * 0.15f) { flap(false); m.cd = 0.18f }
            }
            m.vy = min(m.vy + grav() * dt, pf * 1.1f)
            m.by += m.vy * dt
            m.tilt += ((m.vy / pf * 45f).coerceIn(-25f, 70f) - m.tilt) * min(1f, dt * 10f)
            if (m.by - b < 0f) { m.by = b; if (m.vy < 0f) m.vy = 0f; if (lethal()) die() }
            if (m.state == BF_PLAY && m.by + b > pf) {
                m.by = pf - b
                if (lethal()) {
                    die()
                } else {
                    m.vy = -pf * 0.45f
                    if (m.groundSnd <= 0f) { m.groundSnd = 0.3f; snd("fx_bd_drop", 0.7f); if (real) haptics.vibrateShort() }
                    spark(bx(), pf - 2f, 5, 0xFFE8D9A8.toInt(), W() * 0.2f, 0.5f)
                    crash(true)
                }
            }
            val sp = speed()
            m.dist += sp * dt
            for (p in m.pipes) p.x -= sp * dt
            for (c in m.coins) c.x -= sp * dt
            while (m.pipes.isEmpty() || m.pipes.last().x < w + pw()) spawnPipe()
            // трубы: столкновение и пролёт
            val half = pw() / 2f
            for (p in m.pipes) {
                if (abs(p.x - bx()) < half + b * 0.8f) {
                    val top = p.gy - gap() / 2f; val bot = p.gy + gap() / 2f
                    if (m.by < top + b * 0.4f || m.by > bot - b * 0.4f) crash(false)
                }
                if (m.state == BF_PLAY && !p.passed && p.x + half < bx() - b) {
                    p.passed = true
                    m.streak++; m.runPipes++
                    if (real) { m.tp++; if (m.streak > m.sb) m.sb = m.streak; scoreCheck() }
                    snd("fx_bd_star", 0.8f)
                    spark(bx(), m.by, 8, 0xFFFFF3A0.toInt(), W() * 0.25f, 0.7f)
                    if (real) checkRewards()
                }
            }
            // монетки
            val rad = b + b * 0.75f * (if (toddler) 1.8f else 1f)
            for (c in m.coins) {
                if (m.state == BF_PLAY && !c.taken && abs(c.x - bx()) < rad * 1.5f && Math.hypot((c.x - bx()).toDouble(), (c.y - m.by).toDouble()).toFloat() < rad) {
                    c.taken = true
                    m.runCoins++
                    if (real) { m.tc++; coinsUi = m.tc }
                    snd("fx_bd_pickup", 0.8f, 0.95f + Random.nextFloat() * 0.2f); snd("fx_it_inv_${13 + Random.nextInt(4)}", 0.45f)
                    if (real) haptics.vibrateShort()
                    spark(c.x, c.y, 8, 0xFFFFD84D.toInt(), W() * 0.2f, 0.6f)
                    if (real) checkRewards()
                }
            }
            m.pipes.removeAll { it.x < -pw() * 2f }
            m.coins.removeAll { it.x < -b * 3f || it.taken }
        }

        // след
        val trail = (m.mask and 8) != 0 || (m.mask and 2) != 0
        if (trail) {
            m.trailT -= dt
            if (m.trailT <= 0f) {
                m.trailT = 0.04f
                val col = if ((m.mask and 8) != 0) BF_RAINBOW[((m.clock * 12f).toInt()) % 6] else 0xFFFFE066.toInt()
                m.parts.add(BfP(0, bx() - b * 1.2f, m.by + (Random.nextFloat() - 0.5f) * b, -speed() * 0.4f, (Random.nextFloat() - 0.5f) * pf * 0.05f, 0.6f, b * 0.45f, col))
            }
        }
        var i = m.parts.size - 1
        while (i >= 0) {
            val p = m.parts[i]
            p.x += p.vx * dt; p.y += p.vy * dt
            if (p.kind == 1) p.vy += pf * 0.3f * dt
            p.life -= dt
            if (p.life <= 0f) m.parts.removeAt(i)
            i--
        }

        val scoreNow = curScore()
        if (scoreUi != scoreNow) scoreUi = scoreNow
        if (recUi != m.rec[m.mode]) recUi = m.rec[m.mode]
        val bannerNow = m.recMsgT > 0f
        if (recBanner != bannerNow) recBanner = bannerNow
        m.hintT -= dt
        if (m.hintT <= 0f) {
            m.hintT = 0.3f
            val h = when {
                m.demo -> ""
                m.state == BF_READY -> when (m.mode) {
                    BF_MODE_HEARTS -> "Три сердечка. Тапай — птичка полетит!"
                    BF_MODE_DEADLY -> "Осторожно! Заденешь что-нибудь — птичка упадёт"
                    else -> "Тапай по экрану — птичка полетит!"
                }
                m.state == BF_PLAY && m.tp < 3 && m.runPipes < 2 -> "Лети в ворота и собирай монетки!"
                else -> ""
            }
            if (h != hintText) hintText = h
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
                last = now
                update(dt)
                frame = (frame + 1) and 0xFFFFFF
            }
        }
    }

    // ───────────────────────── интерфейс ─────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        if (showAlbum || rewardSticker != null || showModes || showOver || m.state == BF_DEAD) return@awaitEachGesture
                        // игрок перехватил птичку у бота-демо: забег начинается заново, очки бота не засчитываются
                        if (m.demo) { stopDemo(); if (m.state == BF_PLAY) resetToReady() }
                        m.lastTouch = m.clock
                        flap(true)
                    }
                }
        ) {
            frame.hashCode()
            val w = size.width
            val h = size.height
            if (w <= 0f) return@Canvas
            val pf = yF(); val b = br(); val fh = floorH()
            val t = m.clock

            // небо и город
            if (theme == 0) {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF8CC4EA), Color(0xFFB9DDF2)), 0f, pf), Offset.Zero, Size(w, h))
                val bgW = pf * 0.5f
                val bgOff = -((m.dist * 0.12f) % bgW)
                var bxp = bgOff
                while (bxp < w) { bfImg(bgImg, 0, 0, 256, 512, bxp, 0f, bgW + 1f, pf); bxp += bgW }
            } else {
                bfImg(skyImg, 0, 0, skyImg.width, skyImg.height, 0f, 0f, w, pf)
                bfLayer(cl1Img, m.dist * 0.03f, pf * 0.75f, pf * 0.30f)
                bfLayer(m2Img, m.dist * 0.07f, pf, pf * 0.36f)
                bfLayer(cl2Img, m.dist * 0.10f, pf * 0.90f, pf * 0.20f, 0.9f)
                bfLayer(m1Img, m.dist * 0.14f, pf, pf * 0.25f)
                val spanW = pf * 0.85f; val sc = m.dist * 0.45f
                for (j in 0..((w / spanW).toInt() + 2)) {
                    val k = j + (sc / spanW).toInt()
                    val bi = bushes[k % 3]
                    val bhh = pf * 0.075f; val bww = bhh * bi.width / bi.height
                    bfImg(bi, 0, 0, bi.width, bi.height, k * spanW - sc, pf - bhh + 3f, bww, bhh)
                }
            }

            // трубы
            val pwv = pw() * 18f / 17f
            val capH = pwv * 6f / 18f
            for (p in m.pipes) {
                val top = p.gy - gap() / 2f; val bot = p.gy + gap() / 2f
                if (theme == 1) {
                    val tw2 = pw() * 1.22f; val cap2 = tw2 * 92f / 144f; val capB = tw2 * 100f / 144f; val tx = p.x - tw2 / 2f
                    if (top - cap2 > 0f) bfImg(towerTop, 0, 40, 144, 430, tx, -1f, tw2, top - cap2 + 2f)
                    bfImg(towerTop, 0, 470, 144, 92, tx, top - cap2, tw2, cap2)
                    bfImg(towerBot, 0, 0, 144, 100, tx, bot, tw2, capB)
                    if (pf - bot - capB > 0f) bfImg(towerBot, 0, 100, 144, 370, tx, bot + capB - 1f, tw2, pf - bot - capB + 1f)
                    continue
                }
                val x0 = p.x - pwv / 2f
                // нижняя
                bfImg(pipeImg, 0, 0, 18, 6, x0, bot, pwv, capH)
                bfImg(pipeImg, 0, 6, 18, 122, x0, bot + capH - 1f, pwv, pf - bot - capH + 1f)
                // верхняя (перевёрнута)
                if (top > capH) bfImg(pipeImg, 0, 6, 18, 122, x0, 0f, pwv, top - capH + 1f)
                withTransform({ scale(1f, -1f, Offset(p.x, top - capH / 2f)) }) { bfImg(pipeImg, 0, 0, 18, 6, x0, top - capH, pwv, capH) }
            }
            // монетки
            for (c in m.coins) {
                if (c.taken) continue
                val sc = abs(cos(t * 4f + c.ph)).coerceAtLeast(0.25f)
                val s = b * 1.5f
                bfImg(coinImg, 0, 0, coinImg.width, coinImg.height, c.x - s * sc / 2f, c.y - s / 2f + sin(t * 3f + c.ph) * b * 0.15f, s * sc, s)
            }

            // пол
            val tw = fh * 128f / 118f
            val fOff = -((m.dist) % tw)
            var fxp = fOff
            if (theme == 1) { val gw = fh * 960f / 105f; var gx = -(m.dist % gw); while (gx < w) { bfImg(groundImg, 0, 0, 960, 105, gx, pf - fh * 0.12f, gw + 1f, fh * 1.12f + 1f); gx += gw } }
            else while (fxp < w) { bfImg(floorImg, 0, 0, 128, 118, fxp, pf, tw + 1f, fh + 1f); fxp += tw }

            // частицы
            for (p in m.parts) {
                val lf = (p.life / p.maxLife).coerceIn(0f, 1f)
                if (p.kind == 0) bfSpark(p.x, p.y, p.size * (0.4f + 0.6f * lf), Color(p.col).copy(alpha = min(1f, lf * 1.6f)))
                else drawCircle(Color(p.col).copy(alpha = lf * 0.8f), p.size, Offset(p.x, p.y))
            }

            // птичка
            val blink = m.inv > 0f && ((t * 12f).toInt() % 2 == 0)
            val frameIdx = if (m.flapT > 0f) (((0.35f - m.flapT) * 14f).toInt() % 4).let { if (it == 3) 1 else it } else if (m.state == BF_READY) ((t * 6f).toInt() % 3) else 1
            val sheet = birds[m.bird.coerceIn(0, 2)]
            val bw = b * (if (theme == 1) 3.7f else 3.3f); val bh = bw * sheet.fh / sheet.fw
            val bc = Offset(bx(), m.by)
            drawOval(Color(0x22000000), Offset(bc.x - b, min(pf - 4f, bc.y + b * 2.2f)), Size(b * 2f, b * 0.3f))
            withTransform({ rotate(m.tilt, bc); scale(1f, if (theme == 1 && m.flapT > 0f) 1f + 0.10f * sin(m.flapT * 30f) else 1f, bc) }) {
                bfBird(sheet, frameIdx, bc.x - bw / 2f, bc.y - bh / 2f, bw, bh, if (blink) 0.4f else 1f)
                if ((m.mask and 16) != 0 || (m.mask and 32) != 0) bfCrown(bc.x + b * 0.1f, bc.y - bh * 0.42f, b * 0.7f)
            }
            if (m.state == BF_DEAD) {
                // «звёздочки» над оглушённой птичкой
                for (k in 0..2) {
                    val a = t * 6f + k * 2.094f
                    bfSpark(bc.x + cos(a) * b * 1.6f, bc.y - b * 2.2f + sin(a) * b * 0.4f, b * 0.55f, Color(0xFFFFE066))
                }
            }
            if (m.demo) {
                // подсказка-«палец» над птичкой
                drawCircle(Color.White.copy(alpha = 0.3f + 0.2f * sin(t * 6f)), b * 1.6f, Offset(bx() + b * 3f, m.by - b * 3f))
                drawCircle(Color.White.copy(alpha = 0.85f), b * 0.9f, Offset(bx() + b * 3f, m.by - b * 3f))
            }
            // белая вспышка в момент удара
            if (m.flash > 0f) drawRect(Color.White.copy(alpha = (m.flash / 0.3f).coerceIn(0f, 1f) * 0.6f), Offset.Zero, Size(w, h))
        }

        // монеты
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 12.dp, start = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x99000000))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(Modifier.size(26.dp)) { bfImg(coinImg, 0, 0, coinImg.width, coinImg.height, 0f, 0f, size.width, size.height) }
            Spacer(Modifier.width(6.dp))
            Text("$coinsUi", fontSize = 26.sp, fontFamily = pixelFont, color = Color(0xFFFFE066))
        }
        // счёт: в спокойном режиме — ворота подряд без ойков, в остальных — ворота за забег
        if (scoreUi > 0 && m.state == BF_PLAY) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "$scoreUi",
                    fontSize = 60.sp, fontFamily = pixelFont, color = Color.White,
                    style = TextStyle(shadow = Shadow(Color(0xFF1A2A3A), Offset(4f, 4f), 6f))
                )
                if (modeUi == BF_MODE_CALM) {
                    Text(
                        "без ойков подряд",
                        fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                        style = TextStyle(shadow = Shadow(Color(0xFF1A2A3A), Offset(2f, 2f), 4f))
                    )
                }
            }
        }
        // сердечки
        if (modeUi == BF_MODE_HEARTS) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (k in 0 until BF_LIVES) {
                    Canvas(Modifier.size(30.dp)) {
                        bfHeart(size.width / 2f, size.height / 2f, size.width * 0.34f, if (k < livesUi) Color(0xFFFF4D6D) else Color(0x66000000))
                    }
                }
            }
        }
        // режим и рекорд
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(top = 58.dp, start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCCFFFFFF))
                    .border(2.dp, Color.White, RoundedCornerShape(16.dp))
                    .clickable { stopDemo(); m.lastTouch = m.clock; showModes = true; snd("fx_bd_star", 0.7f) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(BF_MODES[modeUi].emoji, fontSize = 16.sp)
                Spacer(Modifier.width(5.dp))
                Text(BF_MODES[modeUi].name, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8))
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🏆", fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                Text("$recUi", fontSize = 20.sp, fontFamily = pixelFont, color = Color(0xFFFFE066))
            }
        }
        // «Новый рекорд!» посреди забега
        if (recBanner) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 150.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFFFC93C))
                    .border(3.dp, Color.White, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) { Text("Новый рекорд!", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF7A4B00)) }
        }
        // подсказка
        if (hintText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp, start = 12.dp, end = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xE6FFFFFF))
                    .border(2.dp, Color(0xFF6FB6E8), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) { Text(hintText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8), textAlign = TextAlign.Center) }
        }
        if (demoOn) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp, start = 12.dp, end = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xE63F8FE0))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("Просто смотри — тапни по экрану, чтобы играть", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White) }
        }

        // альбом и выбор птички
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFD8A0FF), Color(0xFF9B59E0))))
                    .border(3.dp, Color.White, CircleShape)
                    .clickable { stopDemo(); m.lastTouch = m.clock; showAlbum = true; snd("fx_bd_star", 0.7f) },
                contentAlignment = Alignment.Center
            ) { Canvas(Modifier.size(36.dp)) { bfStickerArt(5, Offset(size.width / 2, size.height / 2), size.width * 0.46f, birds, coinImg) } }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (k in 0..2) {
                    val open = (k == 0) || (k == 1 && (maskUi and 1) != 0) || (k == 2 && (maskUi and 4) != 0)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (birdUi == k) Color(0xFFFFE066) else Color(0x99FFFFFF))
                            .border(2.dp, if (birdUi == k) Color.White else Color(0x66FFFFFF), RoundedCornerShape(12.dp))
                            .clickable(enabled = open) { m.bird = k; birdUi = k; m.lastTouch = m.clock; save(); snd("fx_bd_pickup", 0.6f, 1.3f) },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.size(32.dp)) { bfBird(birds[k], 0, 0f, 0f, size.width, size.height, if (open) 1f else 0.25f) }
                    }
                }
            }
        }

        // переключатель мира
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 118.dp, end = 12.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xCCFFFFFF))
                .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                .clickable {
                    theme = 1 - theme; m.lastTouch = m.clock
                    prefs.edit().putInt("theme", theme).apply()
                    snd("fx_bd_whoosh", 0.7f)
                },
            contentAlignment = Alignment.Center
        ) { Canvas(Modifier.size(28.dp)) { bfThemeIcon(theme) } }

        // альбом
        if (showAlbum) {
            Box(
                Modifier.fillMaxSize().background(Color(0x99102040)).clickable { showAlbum = false; m.lastTouch = m.clock },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFF2F8FF))
                        .border(5.dp, Color(0xFF6FB6E8), RoundedCornerShape(28.dp))
                        .clickable(enabled = false) {}
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Альбом «Птичка-летунья»", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8))
                    Spacer(Modifier.height(8.dp))
                    BF_STICKERS.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { st ->
                                val unlocked = (m.mask and (1 shl st.id)) != 0
                                Column(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (unlocked) Color(0xFFDDEEFF) else Color(0xFFE8EBF0))
                                        .padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Canvas(Modifier.size(70.dp)) {
                                        if (unlocked) bfStickerArt(st.id, Offset(size.width / 2, size.height / 2), size.width * 0.4f, birds, coinImg)
                                        else {
                                            drawCircle(Color(0x22000000), size.width * 0.38f, Offset(size.width / 2, size.height / 2))
                                            drawRoundRect(Color(0xFF8A94A4), Offset(size.width * 0.36f, size.height * 0.46f), Size(size.width * 0.28f, size.height * 0.22f), CornerRadius(6f))
                                            drawArc(Color(0xFF8A94A4), 180f, 180f, false, Offset(size.width * 0.4f, size.height * 0.3f), Size(size.width * 0.2f, size.height * 0.2f), style = Stroke(5f))
                                        }
                                    }
                                    Text(st.name, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8), textAlign = TextAlign.Center, maxLines = 2)
                                    if (!unlocked) {
                                        Text(st.how, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7A90), textAlign = TextAlign.Center, maxLines = 3)
                                        if (st.id < 5) {
                                            val left = max(1, need(st.id) - progress(st.id))
                                            Row {
                                                Text("Ещё", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8))
                                                Text(" $left", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2F6FA8))
                                            }
                                        }
                                    } else {
                                        Text(st.note, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE0780A), textAlign = TextAlign.Center, maxLines = 2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // выбор режима
        if (showModes) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x99102040))
                    .clickable {
                        showModes = false; m.lastTouch = m.clock
                        // закрыли выбор, не выбрав: забег с риском проигрыша начинается заново, а не с места паузы
                        if (m.mode != BF_MODE_CALM) resetToReady()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFF2F8FF))
                        .border(5.dp, Color(0xFF6FB6E8), RoundedCornerShape(28.dp))
                        .clickable(enabled = false) {}
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Как будем летать?", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8))
                    BF_MODES.forEachIndexed { k, mode ->
                        val sel = k == modeUi
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (sel) Color(0xFFFFE9A0) else Color(0xFFDDEEFF))
                                .border(3.dp, if (sel) Color(0xFFFF9F43) else Color.Transparent, RoundedCornerShape(18.dp))
                                .clickable { setMode(k) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(mode.emoji, fontSize = 30.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(mode.name, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8))
                                Text(mode.about, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A7FA8))
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🏆", fontSize = 14.sp)
                                Text("${m.rec[k]}", fontSize = 20.sp, fontFamily = pixelFont, color = Color(0xFFE0780A))
                            }
                        }
                    }
                }
            }
        }

        // проигрыш (сердечки и смертельный режим)
        if (showOver) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xAA102040))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFF2F8FF))
                        .border(5.dp, Color(0xFF6FB6E8), RoundedCornerShape(28.dp))
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (modeUi == BF_MODE_HEARTS) "Сердечки закончились!" else "Птичка упала!",
                        fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFFE5566D), textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Ворот пролетела:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A7FA8))
                    Text("$overScore", fontSize = 64.sp, fontFamily = pixelFont, color = Color(0xFF3F8FE0))
                    if (overNew) {
                        Text("Новый рекорд!", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFFE0780A))
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏆", fontSize = 16.sp)
                            Spacer(Modifier.width(6.dp))
                            Text("$recUi", fontSize = 26.sp, fontFamily = pixelFont, color = Color(0xFFE0780A))
                        }
                    }
                    if (overCoins > 0) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Монеток:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A7FA8))
                            Text(" $overCoins", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF5A7FA8))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF3F8FE0))
                                .clickable { retry() }
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        ) { Text("Ещё раз", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White) }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFDDEEFF))
                                .clickable { showOver = false; resetToReady(); showModes = true }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) { Text("Режимы", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8)) }
                    }
                }
            }
        }

        // награда
        val rs = rewardSticker
        if (rs != null) {
            Box(
                Modifier.fillMaxSize().background(Color(0xAA102040)).clickable { rewardSticker = null; m.lastTouch = m.clock; showNext() },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFF2F8FF))
                        .border(5.dp, Color(0xFF6FB6E8), RoundedCornerShape(28.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Новая наклейка!", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF3F8FE0))
                    Canvas(Modifier.size(150.dp)) { bfStickerArt(rs.id, Offset(size.width / 2, size.height / 2), size.width * 0.42f, birds, coinImg) }
                    Text(rs.name, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF2F6FA8))
                    Spacer(Modifier.height(6.dp))
                    Text("Подарок:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A7FA8))
                    Text(rs.note, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFFE0780A), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF3F8FE0)).padding(horizontal = 28.dp, vertical = 8.dp)
                    ) { Text("Лететь дальше", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White) }
                }
            }
        }
    }
}
