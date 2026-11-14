package com.example.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.shared.i18n.Text

private const val MAX_MISTAKES = 3

/**
 * Родительский замок: решить задачу из [ParentChallenges], чтобы войти в настройки (и магазин).
 * Ошибка даёт новую задачу, после трёх ошибок подряд диалог закрывается.
 */
@Composable
fun ParentLockDialog(
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit
) {
    var challenge by remember { mutableStateOf(ParentChallenges.create()) }
    var mistakes by remember { mutableIntStateOf(0) }
    val ink = Color(0xFF2B2B2B)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFFFFDE7),
            border = BorderStroke(3.dp, ink),
            modifier = Modifier.testTag("parent_lock_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Для родителей", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = 0.7f))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = challenge.prompt,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("parent_lock_prompt")
                )
                if (mistakes > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("Не то. Попробуйте ещё раз", fontSize = 14.sp, color = Color(0xFFB71C1C))
                }
                Spacer(Modifier.height(16.dp))
                challenge.options.chunked(2).forEach { rowOptions ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        rowOptions.forEach { value ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xFFFFEB3B))
                                    .clickable {
                                        if (value == challenge.answer) {
                                            onUnlocked()
                                        } else if (mistakes + 1 >= MAX_MISTAKES) {
                                            onDismiss()
                                        } else {
                                            mistakes += 1
                                            challenge = ParentChallenges.create()
                                        }
                                    }
                                    .testTag("parent_lock_option_$value")
                            ) {
                                Text("$value", fontSize = 26.sp, fontWeight = FontWeight.Black, color = ink)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    "Отмена",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("parent_lock_cancel")
                )
            }
        }
    }
}
