package com.example.audio

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance resource ID resolver for audio assets.
 * Solves package name mismatches between Android applicationId and compile-time namespace.
 */
object SoundResourceResolver {
    private val resIdCache = ConcurrentHashMap<String, Int>()

    fun getRawResId(context: Context, fileName: String): Int {
        val cleanName = fileName.lowercase().trim()
        val cached = resIdCache[cleanName]
        if (cached != null) return cached

        // 1. Direct compile-time R.raw reflection lookup (100% immune to applicationId mismatches)
        try {
            val field = com.example.R.raw::class.java.getField(cleanName)
            val id = field.getInt(null)
            if (id != 0) {
                resIdCache[cleanName] = id
                return id
            }
        } catch (_: Throwable) {}

        // 2. Fallback: getIdentifier with R namespace package
        val appContext = context.applicationContext
        var id = appContext.resources.getIdentifier(cleanName, "raw", "com.example")
        if (id == 0) {
            // 3. Fallback: getIdentifier with appContext.packageName
            id = appContext.resources.getIdentifier(cleanName, "raw", appContext.packageName)
        }

        if (id != 0) {
            resIdCache[cleanName] = id
        }
        return id
    }
}
