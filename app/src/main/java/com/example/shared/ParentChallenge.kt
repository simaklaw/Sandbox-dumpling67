package com.example.shared

import kotlin.random.Random

/** Задача родительского замка: вопрос, четыре разных варианта, один верный. */
data class ParentChallenge(
    val prompt: String,
    val options: List<Int>,
    val answer: Int
)

/**
 * Логика когнитивного замка (без Android, чтобы тестировать на JVM).
 * Два вида задач: пример на сложение с переходом через десяток и «нажмите число, написанное словами».
 * Малыш 2–5 лет не читает и не считает так; родителю хватает пары секунд.
 */
object ParentChallenges {
    private const val OPTIONS = 4

    fun create(random: Random = Random.Default): ParentChallenge =
        if (random.nextBoolean()) sum(random) else spelled(random)

    private fun sum(random: Random): ParentChallenge {
        // Оба слагаемых 6..19: сумма 12..38, единицы почти всегда дают переход через десяток.
        val a = random.nextInt(6, 20)
        val b = random.nextInt(6, 20)
        val answer = a + b
        return ParentChallenge("$a + $b = ?", options(answer, 10, 60, random), answer)
    }

    private fun spelled(random: Random): ParentChallenge {
        val answer = random.nextInt(21, 100)
        return ParentChallenge("Нажмите число: ${spell(answer)}", options(answer, 10, 99, random), answer)
    }

    /** Четыре разных числа из [lo]..[hi]: верное и три «похожих» (рядом или с переставленными цифрами). */
    internal fun options(answer: Int, lo: Int, hi: Int, random: Random): List<Int> {
        val set = linkedSetOf(answer)
        val swapped = (answer % 10) * 10 + answer / 10
        if (swapped in lo..hi) set.add(swapped)
        var guard = 0
        while (set.size < OPTIONS && guard++ < 200) {
            val delta = random.nextInt(1, 11) * (if (random.nextBoolean()) 1 else -1)
            val v = answer + delta
            if (v in lo..hi) set.add(v)
        }
        // Страховка: добираем подряд идущими числами, если диапазон оказался тесным.
        var v = lo
        while (set.size < OPTIONS && v <= hi) {
            set.add(v); v++
        }
        return set.toList().shuffled(random)
    }

    private val UNITS = listOf("", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять")
    private val TENS = listOf("", "", "двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто")

    /** Число 21..99 словами по-русски («тридцать четыре»); для круглых десятков без единиц. */
    fun spell(n: Int): String {
        require(n in 20..99) { "spell: $n вне 20..99" }
        val tens = TENS[n / 10]
        val units = UNITS[n % 10]
        return if (units.isEmpty()) tens else "$tens $units"
    }
}
