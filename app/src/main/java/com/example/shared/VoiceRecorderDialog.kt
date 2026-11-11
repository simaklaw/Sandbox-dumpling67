package com.example.shared

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.shared.i18n.Text
import kotlinx.coroutines.delay

/**
 * «Свои голоса» (родительские настройки): записать фразу, прослушать, удалить.
 * Нажали «●» — идёт запись (до 6 с), нажали «■» — сохранили. Запись звучит в играх сразу.
 */
@Composable
fun VoiceRecorderDialog(soundManager: SoundManager, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    var recordingName by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableIntStateOf(0) } // перерисовка списка после записи/удаления
    var message by remember { mutableStateOf("") }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasPermission = ok
        message = if (ok) "Микрофон разрешён. Нажмите «●» ещё раз." else "Без доступа к микрофону записать нельзя."
    }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopPlayer() {
        val p = player
        player = null
        if (p != null) {
            try { p.release() } catch (e: Exception) { Log.d("VoiceRecorderDialog", "player release failed") }
        }
    }

    fun finishRecording() {
        val name = recordingName ?: return
        recordingName = null
        if (recorder.stop()) {
            soundManager.reloadVoice(name)
            message = "Записано!"
        } else {
            message = "Слишком коротко. Попробуйте ещё раз."
        }
        version++
    }

    fun play(name: String) {
        stopPlayer()
        val f = VoiceRecordings.file(context, name)
        if (!VoiceRecordings.has(context, name)) {
            soundManager.playFxRaw(name) // встроенная заглушка
            return
        }
        try {
            val p = MediaPlayer()
            p.setDataSource(f.absolutePath)
            p.setOnCompletionListener { mp ->
                try { mp.release() } catch (e: Exception) { Log.d("VoiceRecorderDialog", "release failed") }
                if (player === mp) player = null
            }
            p.prepare()
            p.start()
            player = p
        } catch (e: Exception) {
            Log.w("VoiceRecorderDialog", "preview failed", e)
            message = "Не удалось проиграть запись."
        }
    }

    // Автостоп через MAX_MS.
    LaunchedEffect(recordingName) {
        if (recordingName != null) {
            delay(VoiceRecordings.MAX_MS)
            finishRecording()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            recorder.cancel()
            stopPlayer()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = ShapeCard,
            color = ColorBg,
            border = BorderStroke(4.dp, ColorInk),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Свои голоса", fontSize = 22.sp, fontWeight = FontWeight.Black, color = ColorInk)
                Text(
                    text = "Запишите фразы своим голосом, они заменят встроенные звуки. " +
                        "Говорите близко к микрофону, в тишине. Записи хранятся только на этом телефоне.",
                    fontSize = 12.sp,
                    color = ColorInk.copy(alpha = 0.7f)
                )
                Text(
                    text = "Записано: ${VoiceRecordings.count(context)} из ${VoiceRecordings.PHRASES.size}" +
                        if (message.isNotEmpty()) " · $message" else "",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorInk
                )
                @Suppress("UNUSED_VARIABLE") val redraw = version
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(VoiceRecordings.PHRASES, key = { it.file }) { ph ->
                        val recorded = VoiceRecordings.has(context, ph.file) && version >= 0
                        val isRec = recordingName == ph.file
                        Surface(
                            shape = ShapeCard,
                            color = if (isRec) Color(0xFFFFCDD2) else ColorBgSoft,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = ph.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorInk)
                                    Text(
                                        text = if (isRec) "идёт запись…" else if (recorded) "${ph.group} · ваша запись" else "${ph.group} · встроенный звук",
                                        fontSize = 11.sp,
                                        color = ColorInk.copy(alpha = 0.6f)
                                    )
                                }
                                TextButton(onClick = { if (!isRec) play(ph.file) }) { Text("▶", fontSize = 20.sp, color = ColorInk) }
                                TextButton(onClick = {
                                    when {
                                        isRec -> finishRecording()
                                        recordingName != null -> Unit // уже идёт запись другой фразы
                                        !hasPermission -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        else -> {
                                            stopPlayer()
                                            if (recorder.start(ph.file)) {
                                                recordingName = ph.file
                                                message = ""
                                            } else {
                                                message = "Не удалось начать запись."
                                            }
                                        }
                                    }
                                }) { Text(if (isRec) "■" else "●", fontSize = 20.sp, color = Color(0xFFC62828)) }
                                if (recorded && !isRec) {
                                    TextButton(onClick = {
                                        VoiceRecordings.delete(context, ph.file)
                                        soundManager.reloadVoice(ph.file)
                                        message = "Возвращён встроенный звук."
                                        version++
                                    }) { Text("🗑", fontSize = 18.sp) }
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorBgSoft, contentColor = ColorInk)
                ) { Text("Готово", fontWeight = FontWeight.Black, fontSize = 16.sp) }
            }
        }
    }
}
