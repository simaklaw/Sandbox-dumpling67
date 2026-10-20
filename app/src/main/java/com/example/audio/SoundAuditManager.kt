package com.example.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.util.Log
import com.example.shared.SoundManager
import kotlinx.coroutines.*

data class SoundFileInfo(
    val fileName: String,
    val soundName: String,
    val category: SoundCategory,
    val priority: SoundPriority,
    val isPresent: Boolean,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L
)

data class SoundAuditReport(
    val totalSoundsInCatalog: Int,
    val totalFilesExpected: Int,
    val totalFilesFound: Int,
    val totalFilesMissing: Int,
    val totalSizeBytes: Long,
    val missingP1: List<String>,
    val missingP2: List<String>,
    val missingP3: List<String>,
    val longFilesOver3s: List<SoundFileInfo>,
    val allFiles: List<SoundFileInfo>
)

object SoundAuditManager {
    private const val TAG = "SoundAudit"

    private var playbackJob: Job? = null

    /**
     * Executes a complete audit of all sounds declared in SoundCatalog vs res/raw.
     * Logs the full report to Logcat under tag "SoundAudit" and returns the report model.
     */
    fun runAudit(context: Context): SoundAuditReport {
        val appContext = context.applicationContext
        val allFiles = mutableListOf<SoundFileInfo>()
        var totalSizeBytes = 0L

        val missingP1 = mutableListOf<String>()
        val missingP2 = mutableListOf<String>()
        val missingP3 = mutableListOf<String>()
        val longFiles = mutableListOf<SoundFileInfo>()

        for (entry in SoundCatalog.entries) {
            for (fileName in entry.files) {
                val resId = SoundResourceResolver.getRawResId(appContext, fileName)
                if (resId != 0) {
                    var durationMs = 0L
                    var sizeBytes = 0L
                    try {
                        appContext.resources.openRawResourceFd(resId)?.use { afd: AssetFileDescriptor ->
                            sizeBytes = afd.length
                            if (sizeBytes > 0) {
                                totalSizeBytes += sizeBytes
                                // Calculate estimated duration from compressed audio file size (~10 KB/sec for 80kbps Vorbis)
                                durationMs = (sizeBytes / 10L)
                            }
                        }
                    } catch (_: Exception) {}

                    val info = SoundFileInfo(
                        fileName = fileName,
                        soundName = entry.name,
                        category = entry.category,
                        priority = entry.priority,
                        isPresent = true,
                        durationMs = durationMs,
                        sizeBytes = sizeBytes
                    )
                    allFiles.add(info)

                    if (durationMs > 3000L) {
                        longFiles.add(info)
                    }
                } else {
                    val info = SoundFileInfo(
                        fileName = fileName,
                        soundName = entry.name,
                        category = entry.category,
                        priority = entry.priority,
                        isPresent = false
                    )
                    allFiles.add(info)

                    when (entry.priority) {
                        SoundPriority.P1_CRITICAL -> missingP1.add("$fileName.ogg (${entry.name} - ${entry.description})")
                        SoundPriority.P2_IMPORTANT -> missingP2.add("$fileName.ogg (${entry.name} - ${entry.description})")
                        SoundPriority.P3_DESIRABLE -> missingP3.add("$fileName.ogg (${entry.name} - ${entry.description})")
                    }
                }
            }
        }

        val foundCount = allFiles.count { it.isPresent }
        val missingCount = allFiles.count { !it.isPresent }

        val report = SoundAuditReport(
            totalSoundsInCatalog = SoundCatalog.entries.size,
            totalFilesExpected = allFiles.size,
            totalFilesFound = foundCount,
            totalFilesMissing = missingCount,
            totalSizeBytes = totalSizeBytes,
            missingP1 = missingP1,
            missingP2 = missingP2,
            missingP3 = missingP3,
            longFilesOver3s = longFiles,
            allFiles = allFiles
        )

        logReport(report)
        return report
    }

    private fun logReport(report: SoundAuditReport) {
        val sb = StringBuilder()
        sb.appendLine("==================================================")
        sb.appendLine("📊 SOUND ENGINE AUDIT REPORT (Step 9)")
        sb.appendLine("==================================================")
        sb.appendLine("Catalog Sounds: ${report.totalSoundsInCatalog}")
        sb.appendLine("Expected Audio Files: ${report.totalFilesExpected}")
        sb.appendLine("Found in res/raw: ${report.totalFilesFound}")
        sb.appendLine("Missing from res/raw: ${report.totalFilesMissing}")
        sb.appendLine("Total Audio Size: ${report.totalSizeBytes / 1024} KB (${String.format("%.2f", report.totalSizeBytes / (1024f * 1024f))} MB)")
        sb.appendLine("--------------------------------------------------")

        if (report.missingP1.isNotEmpty()) {
            sb.appendLine("🔴 CRITICAL MISSING (P1 - Priority 1): ${report.missingP1.size} files")
            report.missingP1.forEach { sb.appendLine("   - $it") }
        } else {
            sb.appendLine("✅ All P1 Critical sounds are present!")
        }

        if (report.missingP2.isNotEmpty()) {
            sb.appendLine("🟡 IMPORTANT MISSING (P2 - Priority 2): ${report.missingP2.size} files")
            report.missingP2.forEach { sb.appendLine("   - $it") }
        } else {
            sb.appendLine("✅ All P2 Important sounds are present!")
        }

        if (report.missingP3.isNotEmpty()) {
            sb.appendLine("🟢 DESIRABLE MISSING (P3 - Priority 3): ${report.missingP3.size} files")
            report.missingP3.forEach { sb.appendLine("   - $it") }
        }

        if (report.longFilesOver3s.isNotEmpty()) {
            sb.appendLine("⚠️ FILES LONGER THAN 3 SECONDS (>3s):")
            report.longFilesOver3s.forEach {
                sb.appendLine("   - ${it.fileName}.ogg (${it.durationMs}ms)")
            }
        } else {
            sb.appendLine("✅ No files exceeding 3.0 seconds.")
        }
        sb.appendLine("==================================================")

        Log.i(TAG, sb.toString())
    }

    /**
     * Plays all available sounds sequentially with a 0.8s interval between each for volume check.
     */
    fun playAllSequential(
        soundManager: SoundManager,
        scope: CoroutineScope,
        onProgress: (currentIndex: Int, total: Int, currentName: String) -> Unit,
        onComplete: () -> Unit
    ) {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            val soundList = SoundCatalog.entries
            for (i in soundList.indices) {
                val entry = soundList[i]
                onProgress(i + 1, soundList.size, entry.name)
                soundManager.play(entry.name, volume = 1.0f)
                delay(800L)
            }
            onComplete()
        }
    }

    fun stopSequentialPlay() {
        playbackJob?.cancel()
        playbackJob = null
    }
}
