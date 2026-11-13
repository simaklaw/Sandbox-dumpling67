package com.example.ui.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository

private const val MW_PREFS = "playground_prefs"
private const val MW_LAST = "mw_last"

private fun mwLoadLast(ctx: Context): MonSpec? = try {
    ctx.getSharedPreferences(MW_PREFS, Context.MODE_PRIVATE).getString(MW_LAST, null)?.let { MonSpec.decode(it) }
} catch (e: Exception) { null }

private fun mwSaveLast(ctx: Context, spec: MonSpec) {
    try { ctx.getSharedPreferences(MW_PREFS, Context.MODE_PRIVATE).edit().putString(MW_LAST, spec.encode()).apply() } catch (e: Exception) { }
}

/**
 * «Монстрики» — единая комната: Фабрика (собираем, кормим, танцуем) и Забег (бежим этим же монстриком).
 * Монстрик один на обе части: что собрали в фабрике — то и бежит; не нравится облик — «Изменить монстрика»
 * прямо из меню забега. Последний монстрик запоминается и открывается снова при следующем входе.
 * Индекс 32; прежний индекс 33 («Забег монстрика») выведен из использования и ведёт сюда же, сразу в забег.
 */
@Composable
internal fun MonsterWorldScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer,
    startInRun: Boolean = false
) {
    val context = LocalContext.current
    var runMode by rememberSaveable { mutableStateOf(startInRun) }
    var spec by remember { mutableStateOf(mwLoadLast(context)) }
    var version by remember { mutableIntStateOf(0) } // меняется при каждом переходе: экран пересоздаётся с актуальным монстриком

    if (!runMode) {
        androidx.compose.runtime.key(version) {
            MonsterFactoryScreen(
                repository = repository,
                soundPlayer = soundPlayer,
                initialSpec = spec,
                onRun = { s ->
                    spec = s
                    mwSaveLast(context, s)
                    // герой забега: ищем в «Моём доме» или добавляем, если есть место
                    var house = loadHouse(context)
                    if (s !in house && house.size < MON_MAX_HOUSE) { house = house + s; saveHouse(context, house) }
                    val idx = house.indexOf(s)
                    if (idx >= 0) {
                        try { context.getSharedPreferences(MW_PREFS, Context.MODE_PRIVATE).edit().putInt("mr_hero", idx).apply() } catch (e: Exception) { }
                    }
                    version++
                    runMode = true
                }
            )
        }
    } else {
        androidx.compose.runtime.key(version) {
            MonsterRunnerScreen(
                repository = repository,
                soundPlayer = soundPlayer,
                initialSpec = spec,
                onEdit = { s ->
                    spec = s
                    mwSaveLast(context, s)
                    version++
                    runMode = false
                }
            )
        }
    }
}
