package com.example.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.shared.i18n.Text

/** Одна строка благодарностей: что использовано, от кого и по какой лицензии. */
data class CreditEntry(val what: String, val author: String, val license: String)

/**
 * Список атрибуций для экрана «Благодарности». Лицензии с обязательным указанием авторства (CC BY, CC BY-SA)
 * должны быть здесь; полный перечень источников — `CREDITS.md` и `SOUND_CREDITS.md`.
 */
val CREDITS: List<CreditEntry> = listOf(
    CreditEntry("Блоки и частицы «Блок-пазла»", "Block Puzzle Kit Free by heyheythere (heyheythere.itch.io/block-puzzle-kit-free)", "CC BY 4.0"),
    CreditEntry("Звуки утки", "Mudchute Animals by Secretlondon (Wikimedia Commons, через OpenGameArt.org)", "CC BY-SA 3.0+ / GFDL 1.2+"),
    CreditEntry("Аквариум, «Тир»: спрайты", "Kenney (kenney.nl)", "CC0"),
    CreditEntry("Звуки коровы, свиньи, овцы, кошки", "josephsardin, JarredGibb, sandeepkurissery, Ben Burnes (freesound.org)", "CC0"),
    CreditEntry("«Хваталка»: графика", "three.js (threejs.org); игра Claw Game by simaklaw", "MIT; Apache-2.0"),
    CreditEntry("Музыка автомойки", "Сочинена для проекта", "собственная")
)

@Composable
fun CreditsDialog(onDismiss: () -> Unit) {
    val ink = Color(0xFF2B2B2B)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFFFFDE7),
            border = BorderStroke(3.dp, ink),
            modifier = Modifier.testTag("credits_dialog")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Благодарности", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    CREDITS.forEach { c ->
                        Text(c.what, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink)
                        Text("${c.author} · ${c.license}", fontSize = 12.sp, color = ink.copy(alpha = 0.75f))
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEB3B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Закрыть", fontWeight = FontWeight.Black, color = ink)
                }
            }
        }
    }
}
