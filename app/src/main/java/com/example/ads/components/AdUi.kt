package com.example.ads.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ads.AdConfig
import com.example.ads.AdFormat
import com.example.ads.AdProvider
import com.example.shared.i18n.Text
import kotlinx.coroutines.delay

/** Креатив с защитой от случайных тапов: первые [AdConfig.CLICKABILITY_DELAY_MS] все касания гасятся. */
@Composable
private fun GuardedCreative(provider: AdProvider, format: AdFormat, modifier: Modifier) {
    var interactive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(AdConfig.CLICKABILITY_DELAY_MS)
        interactive = true
    }
    Box(modifier) {
        provider.Content(format, interactive, Modifier.fillMaxSize())
        if (!interactive) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture { awaitFirstDown().consume() }
                }
            )
        }
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(maxOf(AdConfig.CLOSE_BUTTON_MIN_DP, 56).dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.dp, Color(0xFF2B2B2B), CircleShape)
            .clickable(onClick = onClose)
            .semantics { contentDescription = "Закрыть рекламу" }
            .testTag("ad_close"),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text("✕", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF2B2B2B))
    }
}

/**
 * Реклама при выходе из игры. Показывается поверх родительского экрана (папка/главный), не внутри игры.
 * Пометка «Реклама» и кнопка закрытия ≥48dp видны сразу; тапы по креативу включаются с задержкой.
 */
@Composable
fun AdInterstitial(provider: AdProvider, onClosed: () -> Unit) {
    Dialog(
        onDismissRequest = onClosed,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xCC000000)).padding(24.dp).testTag("ad_interstitial")) {
            Column(Modifier.align(Alignment.Center).fillMaxWidth().widthIn(max = 480.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Реклама", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                    CloseButton(onClosed)
                }
                GuardedCreative(
                    provider, AdFormat.INTERSTITIAL,
                    Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp))
                )
            }
        }
    }
}

/** Нативная карточка на главном экране (при входе): пометка «Реклама», закрытие ≥48dp, тапы с задержкой. */
@Composable
fun AdCard(provider: AdProvider, onClosed: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .widthIn(max = 360.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF2FFFFFF))
            .border(2.dp, Color(0xFF2B2B2B), RoundedCornerShape(16.dp))
            .padding(8.dp)
            .testTag("ad_card")
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Реклама", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF555555), modifier = Modifier.weight(1f))
                CloseButton(onClosed)
            }
            GuardedCreative(provider, AdFormat.NATIVE_CARD, Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(10.dp)))
        }
    }
}
