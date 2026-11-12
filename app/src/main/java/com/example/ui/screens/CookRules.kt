package com.example.ui.screens

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// Правила комнаты «Поваренок» (индекс 51): чистая логика без Compose, её проверяет CookRulesTest.
// Идея — из игры «Последняя смена» (лента → доска → сковорода → тарелка → гость у окна), но добрая и без проигрыша:
// гости — зверята, смена всегда заканчивается звёздами, пережаренное блюдо — не беда, а повод попробовать ещё раз.
// Цепочка касаний: продукт на ленте → доска (режь в золотой зоне) → сковорода (сними, пока золотая) → тарелка → гость.

enum class CFam(val emoji: String, val short: String, val dish: String) {
    VEG("🥕", "морковка", "Золотая морковка"),
    MUSH("🍄", "грибочки", "Грибочки на сковородке"),
    CHEESE("🧀", "сырок", "Хрустящий сырок"),
    SAUS("🌭", "сосисочка", "Сосисочка-румяночка"),
    FRUIT("🍎", "яблочко", "Яблочко-карамелька")
}

enum class CDone { RAW, WARM, PERFECT, BURNT }

enum class CGuest(val emoji: String, val title: String, val want: CFam?, val hello: String) {
    BUNNY("🐰", "Зайка", CFam.VEG, "fx_sh_hello_bunny"),
    BEAR("🐻", "Мишка", CFam.MUSH, "fx_sh_hello_bear"),
    CAT("🐱", "Кошка", CFam.CHEESE, "fx_sh_hello_cat"),
    DOG("🐶", "Пёсик", CFam.SAUS, "fx_sh_p_hello"),
    FOX("🦊", "Лисичка", CFam.FRUIT, "fx_sh_p_hello"),
    PANDA("🐼", "Панда", null, "fx_sh_p_hello")
}

enum class CReact(val emoji: String, val stars: Int, val word: String) {
    LOVED("😍", 3, "Обожаю!"),
    LIKED("😋", 2, "Вкусно!"),
    MEH("🙂", 1, "Спасибо!"),
    SAD("😕", 0, "Хм… ещё раз?")
}

enum class CMod(val title: String, val body: String, val emoji: String, val cap: Int) {
    GOLD("Шире золото", "Идеальная прожарка держится дольше", "✨", 3),
    PATIENCE("Терпеливые гости", "Гости ждут дольше", "⏳", 2),
    FAVS("Любимая лента", "На ленте чаще то, что просят гости", "🧲", 1),
    STOVE("Третья сковородка", "Можно жарить сразу три блюда", "🍳", 1),
    KNIFE("Острый нож", "Нарезать легче: золотая зона шире", "🔪", 2)
}

const val COOK_RECIPES_TOTAL = 20 // 5 одиночных + 15 пар (с повтором)

data class CCarry(val fam: CFam, val chopped: Boolean, val fine: Boolean, val done: CDone?) {
    val cooked get() = done != null
}

class CBelt(val id: Int, val fam: CFam, var x: Float)
class CPanFood(val fam: CFam, val chopped: Boolean, val fine: Boolean, var t: Float)
class CPan { var food: CPanFood? = null; var gold = false; var burnt = false }
class CBoard(val fam: CFam) { var chopped = false; var fine = false; var t = 0f }
class CSeat {
    var guest: CGuest? = null; var patience = 0f; var max = 1f; var arriveIn = 0f
    var react: CReact? = null; var reactT = 0f
}

class CEv(val type: Int, val idx: Int = 0, val text: String = "", val stars: Int = 0) {
    companion object {
        const val PICK = 0; const val BOARD_PUT = 1; const val CHOP_GOOD = 2; const val CHOP_OK = 3
        const val PAN_PUT = 4; const val PAN_GOLD = 5; const val PAN_BURN = 6; const val TAKE = 7
        const val PLATE = 8; const val SERVE = 9; const val LEAVE = 10; const val ARRIVE = 11
        const val TRASH = 12; const val WRONG = 13; const val RECIPE = 14; const val CLEAR = 15
    }
}

class CHint(val kind: Int, val idx: Int) {
    companion object { const val BELT = 0; const val BOARD = 1; const val PAN = 2; const val PLATE = 3; const val GUEST = 4; const val TRASH = 5 }
}

/** Профиль «Поваренка»: сколько смен пройдено, подарки, звёзды, открытые рецепты. Хранится строкой в SharedPreferences. */
class CProfile(var night: Int = 1, val mods: MutableMap<CMod, Int> = mutableMapOf(), var stars: Int = 0, val recipes: MutableSet<String> = mutableSetOf()) {
    fun level(m: CMod) = mods[m] ?: 0

    fun encode(): String =
        "$night|${mods.entries.joinToString(",") { "${it.key.name}:${it.value}" }}|$stars|${recipes.joinToString(",")}"

    companion object {
        fun decode(s: String?): CProfile {
            val p = CProfile()
            if (s.isNullOrBlank()) return p
            val parts = s.split("|")
            p.night = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(1, 999) ?: 1
            parts.getOrNull(1)?.split(",")?.forEach {
                val kv = it.split(":")
                val m = CMod.values().firstOrNull { x -> x.name == kv.getOrNull(0) }
                val n = kv.getOrNull(1)?.toIntOrNull()
                if (m != null && n != null) p.mods[m] = n.coerceIn(0, m.cap)
            }
            p.stars = parts.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            parts.getOrNull(3)?.split(",")?.filter { it.isNotBlank() }?.forEach { p.recipes.add(it) }
            return p
        }
    }
}

object CookCfg {
    const val BELT_SPEED = 40f
    const val RAW_END = 1.8f
    const val PERFECT_START = 3.2f
}

fun cookDishKey(parts: List<CCarry>): String = parts.map { it.fam }.sortedBy { it.ordinal }.joinToString("+") { it.name.lowercase() }

fun cookDishName(parts: List<CCarry>): String {
    val f = parts.map { it.fam }.sortedBy { it.ordinal }
    return when {
        f.size <= 1 -> (f.firstOrNull() ?: CFam.VEG).dish
        f[0] == f[1] -> "Двойная порция: ${f[0].short}"
        else -> "${f[0].short.replaceFirstChar { it.uppercase() }} и ${f[1].short}"
    }
}

fun cookDoneAt(t: Float, goldWindow: Float): CDone = when {
    t < CookCfg.RAW_END -> CDone.RAW
    t < CookCfg.PERFECT_START -> CDone.WARM
    t < CookCfg.PERFECT_START + goldWindow -> CDone.PERFECT
    else -> CDone.BURNT
}

/** Оценка тарелки гостем. Без «наказаний»: плохое блюдо просто не даёт звёзд, гость остаётся и ждёт ещё. */
fun cookJudge(parts: List<CCarry>, guest: CGuest): CReact {
    if (parts.isEmpty()) return CReact.SAD
    val q = parts.map {
        val base = when (it.done) { CDone.PERFECT -> 3; CDone.WARM -> 2; CDone.RAW -> 1; else -> 0 }
        if (!it.chopped) min(base, 2) else base
    }
    val avg = q.sum().toFloat() / q.size
    val match = guest.want == null || parts.any { it.fam == guest.want }
    val loved = avg >= 2.99f && (guest.want != null || parts.size >= 2) && match
    return when {
        loved -> CReact.LOVED
        match && avg >= 1.99f -> CReact.LIKED
        avg >= 1.99f -> CReact.MEH
        match && avg >= 0.99f -> CReact.MEH
        else -> CReact.SAD
    }
}

class CookSim(val profile: CProfile, val rnd: Random = Random.Default) {
    val lw = 360f
    val night = profile.night
    val seatsN = if (night <= 2) 2 else 3
    val panN = if (profile.level(CMod.STOVE) > 0) 3 else 2
    val totalGuests = min(12, 5 + night)

    val belt = ArrayList<CBelt>()
    var hand: CCarry? = null
    var board: CBoard? = null
    val pans = List(panN) { CPan() }
    val plate = ArrayList<CCarry>()
    val seats = List(seatsN) { CSeat() }
    val events = ArrayList<CEv>()
    private val queue = ArrayDeque<CGuest>()

    var time = 0f
    var cleared = false
    var starsEarned = 0
    var served = 0
    var loved = 0
    var gone = 0
    var rating = 0
    val newRecipes = ArrayList<String>()

    private var nextId = 1
    private var spawnIn = 0.6f

    val goldWindow get() = (1.9f + 0.5f * profile.level(CMod.GOLD) - 0.04f * (night - 1)).coerceAtLeast(1.3f)
    val patienceBase get() = max(24f, 48f - 2f * night) * (1f + 0.25f * profile.level(CMod.PATIENCE))
    val spawnEvery get() = max(1.3f, 2.1f - 0.06f * night)
    val chopHalf get() = (0.20f - 0.006f * (night - 1)).coerceAtLeast(0.13f) + 0.04f * profile.level(CMod.KNIFE)

    init {
        val all = CGuest.values()
        for (i in 0 until totalGuests) {
            val g = when {
                night == 1 && i == 0 -> CGuest.BUNNY
                night == 1 && i == 1 -> CGuest.BEAR
                else -> all[rnd.nextInt(all.size)]
            }
            queue.addLast(g)
        }
        seats.forEachIndexed { i, s -> s.arriveIn = 0.8f + 2.2f * i }
    }

    val guestsLeft get() = queue.size + seats.count { it.guest != null }

    /** Положение маркера на доске, 0..1 туда-обратно. */
    fun chopPos(b: CBoard): Float { val ph = (b.t * 0.9f) % 2f; return if (ph < 1f) ph else 2f - ph }

    private fun wantedFams(): List<CFam> = seats.mapNotNull { s -> if (s.reactT <= 0f) s.guest?.want else null }

    private fun rollFam(): CFam {
        val w = wantedFams()
        val favP = if (profile.level(CMod.FAVS) > 0) 0.72f else 0.5f
        return if (w.isNotEmpty() && rnd.nextFloat() < favP) w[rnd.nextInt(w.size)] else CFam.values()[rnd.nextInt(CFam.values().size)]
    }

    private fun ev(type: Int, idx: Int = 0, text: String = "", stars: Int = 0) { events.add(CEv(type, idx, text, stars)) }

    fun step(dt: Float) {
        if (cleared) return
        time += dt
        // лента
        val bi = belt.iterator()
        while (bi.hasNext()) { val b = bi.next(); b.x += CookCfg.BELT_SPEED * dt; if (b.x > lw + 40f) bi.remove() }
        spawnIn -= dt
        if (spawnIn <= 0f && belt.size < 8) {
            belt.add(CBelt(nextId++, rollFam(), -30f)); spawnIn = spawnEvery
        }
        // доска
        val bd = board
        if (bd != null && !bd.chopped) bd.t += dt
        // сковородки
        pans.forEachIndexed { i, p ->
            val f = p.food ?: return@forEachIndexed
            f.t += dt
            if (!p.gold && f.t >= CookCfg.PERFECT_START) { p.gold = true; ev(CEv.PAN_GOLD, i) }
            if (!p.burnt && f.t >= CookCfg.PERFECT_START + goldWindow) { p.burnt = true; ev(CEv.PAN_BURN, i) }
        }
        // гости
        seats.forEachIndexed { i, s ->
            val g = s.guest
            if (g == null) {
                s.arriveIn -= dt
                if (s.arriveIn <= 0f && queue.isNotEmpty()) {
                    val ng = queue.removeFirst()
                    s.guest = ng; s.react = null; s.reactT = 0f
                    s.max = patienceBase * (if (ng == CGuest.PANDA) 1.2f else 1f); s.patience = s.max
                    ev(CEv.ARRIVE, i, ng.hello)
                }
            } else if (s.reactT > 0f) {
                s.reactT -= dt
                if (s.reactT <= 0f) {
                    if (s.react == CReact.SAD) { s.react = null }
                    else { s.guest = null; s.react = null; s.arriveIn = 1.6f + rnd.nextFloat() * 1.4f; ev(CEv.LEAVE, i, "happy") }
                }
            } else {
                s.patience -= dt
                if (s.patience <= 0f) {
                    s.guest = null; s.arriveIn = 1.6f + rnd.nextFloat() * 1.4f; gone++
                    ev(CEv.LEAVE, i, "wait")
                }
            }
        }
        if (queue.isEmpty() && seats.all { it.guest == null }) finish()
    }

    private fun finish() {
        cleared = true
        val maxStars = 3 * totalGuests
        val ratio = starsEarned.toFloat() / maxStars
        rating = when { ratio >= 0.72f -> 3; ratio >= 0.42f -> 2; else -> 1 }
        profile.stars += starsEarned
        profile.night += 1
        ev(CEv.CLEAR, rating)
    }

    fun offerGifts(): List<CMod> {
        val bag = CMod.values().filter { profile.level(it) < it.cap }.toMutableList()
        val out = ArrayList<CMod>()
        while (out.size < 3 && bag.isNotEmpty()) out.add(bag.removeAt(rnd.nextInt(bag.size)))
        return out
    }

    fun pickGift(m: CMod) { profile.mods[m] = min(m.cap, profile.level(m) + 1) }

    // ───────── касания ─────────
    fun tapBelt(id: Int): Boolean {
        if (hand != null) { ev(CEv.WRONG, 0, "hand"); return false }
        val b = belt.firstOrNull { it.id == id } ?: return false
        belt.remove(b)
        hand = CCarry(b.fam, chopped = false, fine = false, done = null)
        ev(CEv.PICK, 0, b.fam.name)
        return true
    }

    fun tapBoard(): Boolean {
        val h = hand; val b = board
        if (h != null) {
            if (b == null && !h.cooked && !h.chopped) { board = CBoard(h.fam); hand = null; ev(CEv.BOARD_PUT); return true }
            ev(CEv.WRONG, 1); return false
        }
        if (b == null) { ev(CEv.WRONG, 1); return false }
        if (!b.chopped) {
            val good = kotlin.math.abs(chopPos(b) - 0.5f) <= chopHalf
            b.chopped = true; b.fine = good
            ev(if (good) CEv.CHOP_GOOD else CEv.CHOP_OK, 0, b.fam.name)
            return true
        }
        hand = CCarry(b.fam, chopped = true, fine = b.fine, done = null); board = null
        ev(CEv.TAKE, 1)
        return true
    }

    fun tapPan(i: Int): Boolean {
        val p = pans.getOrNull(i) ?: return false
        val h = hand
        if (h != null) {
            if (p.food == null && !h.cooked) {
                p.food = CPanFood(h.fam, h.chopped, h.fine, 0f); p.gold = false; p.burnt = false; hand = null
                ev(CEv.PAN_PUT, i); return true
            }
            ev(CEv.WRONG, 2, "pan"); return false
        }
        val f = p.food ?: run { ev(CEv.WRONG, 2); return false }
        hand = CCarry(f.fam, f.chopped, f.fine, cookDoneAt(f.t, goldWindow))
        p.food = null; p.gold = false; p.burnt = false
        ev(CEv.TAKE, 2, hand!!.done!!.name)
        return true
    }

    fun tapPlate(): Boolean {
        val h = hand
        if (h != null) {
            if (h.cooked && plate.size < 2) { plate.add(h); hand = null; ev(CEv.PLATE); return true }
            ev(CEv.WRONG, 3); return false
        }
        if (plate.isNotEmpty()) { hand = plate.removeAt(plate.size - 1); ev(CEv.TAKE, 3); return true }
        ev(CEv.WRONG, 3); return false
    }

    fun tapTrash(): Boolean {
        if (hand != null) { hand = null; ev(CEv.TRASH); return true }
        if (plate.isNotEmpty()) { plate.clear(); ev(CEv.TRASH); return true }
        ev(CEv.WRONG, 5); return false
    }

    fun tapGuest(i: Int): Boolean {
        val s = seats.getOrNull(i) ?: return false
        val g = s.guest ?: return false
        if (s.reactT > 0f) return false
        val h = hand
        if (h != null && !h.cooked) { ev(CEv.WRONG, 4, "raw"); return false }
        val parts = ArrayList<CCarry>(plate)
        if (h != null && parts.size < 2) parts.add(h)
        if (parts.isEmpty()) { ev(CEv.WRONG, 4, "empty"); return false }
        val r = cookJudge(parts, g)
        if (h != null && parts.size > plate.size) hand = null
        plate.clear()
        s.react = r; s.reactT = if (r == CReact.SAD) 1.4f else 1.8f
        if (r == CReact.SAD) {
            s.patience = min(s.max, s.patience + s.max * 0.25f)
        } else {
            starsEarned += r.stars; served++
            if (r == CReact.LOVED) {
                loved++
                val key = cookDishKey(parts)
                if (profile.recipes.add(key)) { val nm = cookDishName(parts); newRecipes.add(nm); ev(CEv.RECIPE, i, nm) }
            }
        }
        ev(CEv.SERVE, i, r.name, r.stars)
        return true
    }

    /** Что подсказать, если ребёнок завис: следующий очевидный шаг. */
    fun hint(): CHint? {
        val h = hand
        val wanted = wantedFams()
        fun freePan() = pans.indexOfFirst { it.food == null }
        fun guestFor(c: List<CCarry>): Int {
            val idx = seats.indexOfFirst { s -> s.guest != null && s.reactT <= 0f && c.any { it.fam == s.guest!!.want } }
            return if (idx >= 0) idx else seats.indexOfFirst { it.guest != null && it.reactT <= 0f }
        }
        if (h != null) {
            if (h.cooked) {
                val g = guestFor(plate + h)
                return if (g >= 0) CHint(CHint.GUEST, g) else CHint(CHint.PLATE, 0)
            }
            if (!h.chopped && board == null) return CHint(CHint.BOARD, 0)
            val p = freePan()
            return if (p >= 0) CHint(CHint.PAN, p) else null
        }
        val ready = pans.indexOfFirst { p -> p.food?.let { cookDoneAt(it.t, goldWindow) == CDone.PERFECT } == true }
        if (ready >= 0) return CHint(CHint.PAN, ready)
        board?.let { return CHint(CHint.BOARD, 0) }
        if (plate.isNotEmpty()) { val g = guestFor(plate); if (g >= 0) return CHint(CHint.GUEST, g) }
        val item = belt.filter { it.x in 20f..lw - 20f }.let { l -> l.firstOrNull { it.fam in wanted } ?: l.firstOrNull() }
        return item?.let { CHint(CHint.BELT, it.id) }
    }
}
