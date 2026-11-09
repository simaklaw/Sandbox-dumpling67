package com.example.ui.screens

// Режимы комнаты «Птичка-летунья» и правила ударов. Чистая логика без Compose: её проверяет BirdRulesTest.

/** Спокойный: задела трубу — подпрыгнула и летим дальше (как для малышей). */
internal const val BF_MODE_CALM = 0
/** Сердечки: каждый удар отнимает сердечко, когда они кончились — игра окончена. */
internal const val BF_MODE_HEARTS = 1
/** Смертельный: любой удар (труба, земля, потолок) — птичка падает, игра окончена. */
internal const val BF_MODE_DEADLY = 2
/** Сколько сердечек в режиме «Сердечки». */
internal const val BF_LIVES = 3

internal enum class BfCrash { NONE, BUMP, LOSE_LIFE, DIE }

/**
 * Что произойдёт при ударе.
 * @param demo птичку ведёт бот-демо: он никогда не проигрывает, поэтому ведёт себя как в спокойном режиме
 * @param floor удар о землю (иначе о трубу)
 * @param invulnerable птичка ещё «призрак» после прошлого удара
 * @param lives сколько сердечек осталось до этого удара
 */
internal fun bfCrashOutcome(mode: Int, demo: Boolean, floor: Boolean, invulnerable: Boolean, lives: Int): BfCrash = when {
    demo || mode == BF_MODE_CALM -> if (floor) BfCrash.NONE else BfCrash.BUMP
    mode == BF_MODE_DEADLY -> BfCrash.DIE
    invulnerable -> BfCrash.NONE
    lives <= 1 -> BfCrash.DIE
    else -> BfCrash.LOSE_LIFE
}

/** Счёт побил рекорд, который был в начале забега (первый забег с нулевым счётом рекордом не считается). */
internal fun bfBeatsRecord(score: Int, recordAtStart: Int): Boolean = score > 0 && score > recordAtStart
