package com.example.ui.screens

import android.annotation.SuppressLint
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import java.io.ByteArrayInputStream

private const val HT_HOST = "appassets.androidplatform.net"
private const val HT_URL = "https://$HT_HOST/hypertrack/index.html"

private fun htMime(path: String): String = when {
    path.endsWith(".html") -> "text/html"
    path.endsWith(".js") -> "text/javascript"
    path.endsWith(".css") -> "text/css"
    path.endsWith(".png") -> "image/png"
    path.endsWith(".json") -> "application/json"
    else -> "application/octet-stream"
}

/**
 * Комната «Гипертрасса»: космический 3D-забег (игра владельца проекта, three.js r128, MIT) в WebView.
 * Всё лежит в assets/hypertrack и работает офлайн: запросы не к нашему виртуальному хосту глушатся,
 * шрифты системные, звук игра синтезирует сама (WebAudio), рекорды и ангар живут в localStorage WebView.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HyperTrackScreen(
    repository: GameRepository,
    soundPlayer: SoundEffectPlayer
) {
    val context = LocalContext.current

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#05020F"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = false
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val uri = request.url
                    val path = uri.path?.removePrefix("/")
                    if (uri.host != HT_HOST || path == null || !path.startsWith("hypertrack/") || path.contains("..")) {
                        return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }
                    return try {
                        WebResourceResponse(htMime(path), "utf-8", context.assets.open(path))
                    } catch (e: Exception) {
                        WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
            }
            loadUrl(HT_URL)
        }
    }

    // Пауза/возобновление: иначе игра и её звук продолжают работать, когда приложение свернули
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { webView.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { webView.onResume() }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05020F))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
    }
}
