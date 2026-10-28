package com.example.shared.i18n

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.example.R
import org.json.JSONObject

/**
 * Смена языка RU ⇄ EN без переписывания всех экранов.
 * Все тексты в коде написаны по-русски; при включённом английском [tr] подставляет перевод
 * из словаря res/raw/i18n_en.json. Шаблоны с подстановками ("{}") сопоставляются как регулярные выражения,
 * а найденные значения переводятся отдельно. Неизвестная фраза остаётся по-русски.
 */
object I18n {
    private const val PREFS = "playground_prefs"
    private const val KEY = "pref_lang"

    /** "ru" или "en"; состояние Compose — смена языка сразу перерисовывает тексты. */
    var lang by mutableStateOf("ru")
        private set

    private val exact = HashMap<String, String>()
    private class Pat(val re: Regex, val tpl: String, val strict: Boolean)
    private val patterns = ArrayList<Pat>()
    private val cache = HashMap<String, String>()
    private var loaded = false

    val isEnglish: Boolean get() = lang == "en"

    fun init(context: Context) {
        try {
            lang = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "ru") ?: "ru"
        } catch (e: Throwable) { lang = "ru" }
        load(context)
    }

    fun setLang(context: Context, value: String) {
        lang = value
        synchronized(cache) { cache.clear() }
        try { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply() } catch (e: Throwable) { }
        load(context)
    }

    private fun load(context: Context) {
        if (loaded) return
        try {
            val txt = context.resources.openRawResource(R.raw.i18n_en).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val js = JSONObject(txt)
            val list = ArrayList<Pat>()
            for (k in js.keys()) {
                val v = js.getString(k)
                if (k.contains("{}")) {
                    val parts = k.split("{}")
                    val literal = parts.joinToString("")
                    val cyr = literal.count { isCyr(it) }
                    val re = Regex(parts.joinToString("(.*?)") { Regex.escape(it) }, RegexOption.DOT_MATCHES_ALL)
                    list.add(Pat(re, v, cyr < 6))
                } else exact[k] = v
            }
            list.sortByDescending { it.re.pattern.length }
            patterns.addAll(list)
            loaded = true
        } catch (e: Throwable) {
            android.util.Log.w("I18n", "dictionary not loaded", e)
        }
    }

    private fun isCyr(c: Char) = c in 'Ѐ'..'ӿ'

    /** Перевод строки на текущий язык (для русского — без изменений). */
    fun tr(s: String): String {
        if (lang != "en" || s.isEmpty()) return s
        if (!s.any { isCyr(it) }) return s
        exact[s]?.let { return it }
        synchronized(cache) { cache[s]?.let { return it } }
        val r = translate(s)
        synchronized(cache) {
            if (cache.size > 3000) cache.clear()
            cache[s] = r
        }
        return r
    }

    private fun translate(s: String): String {
        for (p in patterns) {
            val m = p.re.matchEntire(s) ?: continue
            val groups = m.groupValues.drop(1)
            if (p.strict && groups.any { g -> g.any { isCyr(it) } }) continue
            var i = 0
            val sb = StringBuilder()
            var k = 0
            val tpl = p.tpl
            while (k < tpl.length) {
                if (tpl.startsWith("{}", k)) {
                    sb.append(if (i < groups.size) tr(groups[i]) else "")
                    i++; k += 2
                } else { sb.append(tpl[k]); k++ }
            }
            return sb.toString()
        }
        if (s.contains('\n')) {
            val parts = s.split('\n')
            val out = parts.map { tr(it) }
            if (out != parts) return out.joinToString("\n")
        }
        return s
    }
}

/** Короткое имя для вызова из кода рисования. */
fun tr(s: String): String = I18n.tr(s)

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    val lang = I18n.lang // чтение состояния — перерисовка при смене языка
    androidx.compose.material3.Text(
        text = if (lang == "en") I18n.tr(text) else text,
        modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
        fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        onTextLayout = onTextLayout, style = style
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
        fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        onTextLayout = onTextLayout, style = style
    )
}
