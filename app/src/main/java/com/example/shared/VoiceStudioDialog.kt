package com.example.shared

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat

/**
 * «Свои голоса» (родительские настройки): записать, прослушать и удалить фразы.
 * Запись заменяет встроенный звук с тем же именем на обоих языках; удалить запись — вернуть встроенный.
 * Микрофон запрашивается только в момент первой записи.
 */
@Composable
fun VoiceStudioDialog(soundManager: SoundManager, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val recorder = remember { CustomVoices.Recorder(context) }
    var recordingKey by remember { mutableStateOf<String?>(null) }
    var pendingKey by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { recorder.stopQuietly() }
    }

    fun finishRecording() {
        val saved = recorder.finish()
        recordingKey = null
        if (saved != null) {
            soundManager.reloadCustomVoice(saved)
            version++
            message = "Записано. Нажмите ▶, чтобы послушать."
        } else {
            message = "Запись получилась слишком короткой. Попробуйте ещё раз."
        }
    }

    fun begin(key: String) {
        message = null
        if (recorder.start(key) { finishRecording() }) {
            recordingKey = key
        } else {
            message = "Не удалось включить микрофон. Проверьте, что он не занят другим приложением."
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val key = pendingKey
        pendingKey = null
        if (granted && key != null) {
            begin(key)
        } else if (!granted) {
            message = "Без доступа к микрофону записать голос нельзя. Разрешение можно выдать в настройках телефона."
        }
    }

    fun onRecordClick(key: String) {
        val active = recordingKey
        when {
            active == key -> finishRecording()
            active != null -> Unit
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> begin(key)
            else -> {
                pendingKey = key
                permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (recordingKey == null) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = ShapeCard,
            color = ColorBg,
            border = BorderStroke(3.dp, ColorInk),
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.88f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🎤 Свои голоса", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ColorInk)
                Text(
                    "Нажмите ● и скажите фразу (до ${CustomVoices.MAX_MS / 1000} секунд), потом ■. " +
                        "Запись заменит встроенный звук; ✕ вернёт встроенный. Записи хранятся только на этом телефоне.",
                    fontSize = 12.sp,
                    color = ColorInk.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                val note = message
                if (note != null) {
                    Text(note, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorInk, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (group in CustomVoices.groups) {
                        item(key = "h_" + group.title) {
                            Text(group.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorInk, modifier = Modifier.padding(top = 8.dp))
                        }
                        items(group.phrases, key = { it.key }) { phrase ->
                            val has = remember(version) { CustomVoices.has(context, phrase.key) }
                            val recordingThis = recordingKey == phrase.key
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(phrase.text, fontSize = 14.sp, color = ColorInk)
                                    if (has) Text("✓ ваша запись", fontSize = 11.sp, color = ColorInk.copy(alpha = 0.6f))
                                }
                                SmallButton("▶", enabled = recordingKey == null) { soundManager.play(phrase.key) }
                                SmallButton(if (recordingThis) "■" else "●", color = if (recordingThis) ColorPink else ColorGreen, enabled = recordingKey == null || recordingThis) {
                                    onRecordClick(phrase.key)
                                }
                                SmallButton("✕", enabled = has && recordingKey == null) {
                                    CustomVoices.delete(context, phrase.key)
                                    soundManager.reloadCustomVoice(phrase.key)
                                    version++
                                    message = null
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onDismiss,
                    enabled = recordingKey == null,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorPurple),
                    border = BorderStroke(2.dp, ColorInk),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Готово", fontWeight = FontWeight.Bold, color = ColorInk)
                }
            }
        }
    }
}

@Composable
private fun SmallButton(
    label: String,
    color: androidx.compose.ui.graphics.Color = ColorBgSoft,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = ColorBgSoft.copy(alpha = 0.5f)),
        border = BorderStroke(1.5.dp, ColorInk.copy(alpha = if (enabled) 1f else 0.3f)),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
        modifier = Modifier.padding(start = 6.dp).height(40.dp).widthIn(min = 44.dp)
    ) {
        Text(label, fontSize = 16.sp, color = ColorInk)
    }
}
