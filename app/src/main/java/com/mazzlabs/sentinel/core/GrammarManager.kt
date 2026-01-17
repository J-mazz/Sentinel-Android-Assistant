package com.mazzlabs.sentinel.core

import android.content.Context
import android.util.Log
import com.mazzlabs.sentinel.SentinelApplication
import java.io.File

/**
 * GrammarManager - copies grammar assets to files dir and returns absolute paths.
 */
object GrammarManager {

    private const val TAG = "GrammarManager"

    fun getGrammarPath(assetName: String): String {
        val context = SentinelApplication.getInstance().applicationContext
        val target = File(context.filesDir, assetName)
        if (target.exists()) return target.absolutePath

        return try {
            context.assets.open(assetName).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            target.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy grammar asset: $assetName", e)
            target.absolutePath
        }
    }
}
