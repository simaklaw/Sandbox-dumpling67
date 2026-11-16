package com.example.ui.screens

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.shared.SoundManager
import com.example.shared.rememberHaptics
import java.io.ByteArrayInputStream

private const val CLAW_HOST = "appassets.androidplatform.net"
private const val CLAW_URL = "https://$CLAW_HOST/claw/index.html"

/** Звуки зверят, которые игра может попросить (белый список: имя приходит из JS). */
private val CLAW_ANIMAL_SOUNDS = setOf(
    "animal_bunny_happy", "animal_cat_happy", "animal_dog_happy", "animal_pig_1",
    "animal_duck_1", "animal_frog_1", "animal_sheep_1", "animal_cow_1",
)

/** Мост JS → Android: игра шлёт события, мы отвечаем звуком и вибрацией. */
private class ClawBridge(private val onEvent: (String, String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onEvent(name: String, value: String) {
        main.post { onEvent.invoke(name, value) }
    }
}

private fun clawMime(path: String): String = when {
    path.endsWith(".html") -> "text/html"
    path.endsWith(".js") -> "text/javascript"
    path.endsWith(".css") -> "text/css"
    path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
    path.endsWith(".png") -> "image/png"
    path.endsWith(".json") -> "application/json"
    else -> "application/octet-stream"
}

/**
 * Комната «Хваталка»: 3D-автомат с когтем (Claw Game, Apache-2.0), перенесённый на Android.
 * Игра лежит в assets/claw и работает офлайн внутри WebView; ресурсы отдаются с «виртуального» https-адреса,
 * чтобы работали ES-модули.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ClawGameScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current
    val soundManager = remember(soundPlayer) { SoundManager.getInstance(context, soundPlayer) }
    val haptics = rememberHaptics()
    val main = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(soundManager) {
        soundManager.preloadRaw(CLAW_ANIMAL_SOUNDS.toList() + listOf("fx_magic_sparkle", "fx_legendary_fanfare", "btn_tada", "fx_squeak"))
    }

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#101020"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = false
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            addJavascriptInterface(
                ClawBridge { name, value ->
                    when (name) {
                        "start" -> { soundManager.play("whoosh") }
                        "drop" -> { soundManager.play("boing"); haptics.vibrateShort() }
                        "grab" -> { soundManager.play("pop"); haptics.vibratePop() }
                        "prize" -> { soundManager.play("bell_ding"); haptics.vibrateShort() }
                        "miss" -> { soundManager.playFxRaw("fx_squeak", 0.6f, 1.2f) }
                        "slip" -> { soundManager.playFxRaw("fx_squeak", 0.7f, 0.8f); haptics.vibrateShort() }
                        "win" -> {
                            // value = «звук|редкость»; поздравление: фанфара по редкости, затем голос зверька
                            val parts = value.split("|")
                            val snd = parts.getOrNull(0).orEmpty()
                            val rarity = parts.getOrNull(1)?.toIntOrNull() ?: 1
                            soundManager.playFxRaw(if (rarity >= 4) "fx_legendary_fanfare" else if (rarity >= 3) "btn_tada" else "fx_magic_sparkle", 0.9f)
                            if (snd in CLAW_ANIMAL_SOUNDS) {
                                main.postDelayed({ soundManager.playFxRaw(snd, 1.0f) }, 600)
                            }
                            haptics.vibratePop()
                        }
                        "over" -> { soundManager.play("fanfare_short") }
                    }
                },
                "Android"
            )
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val uri = request.url
                    val path = uri.path?.removePrefix("/")
                    // Игра полностью офлайн: любые запросы не к нашему хосту глушим (а не отдаём сети)
                    if (uri.host != CLAW_HOST || path == null || !path.startsWith("claw/") || path.contains("..")) {
                        return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }
                    return try {
                        WebResourceResponse(clawMime(path), "utf-8", context.assets.open(path))
                    } catch (e: Exception) {
                        WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
            }
            loadUrl(CLAW_URL)
        }
    }

    // Пауза/возобновление: иначе игра и её звук продолжают работать, когда приложение свернули
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { webView.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { webView.onResume() }

    DisposableEffect(Unit) {
        onDispose {
            main.removeCallbacksAndMessages(null)
            webView.stopLoading()
            webView.destroy()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101020))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
    }
}
