package com.mazzlabs.sentinel.core

import android.content.Context
import android.os.Build
import android.util.Log
import com.mazzlabs.sentinel.SentinelApplication
import java.io.File

/**
 * GrammarManager - copies grammar assets to a versioned grammars/ subdirectory
 * inside filesDir and returns absolute paths.
 *
 * A `.grammar_version` marker file is stored alongside the grammars. When the
 * app's versionCode changes (i.e. after an update), all grammar files are
 * re-copied from assets so that updated grammars take effect.
 */
object GrammarManager {

    private const val TAG = "GrammarManager"
    private const val GRAMMAR_DIR = "grammars"
    private const val VERSION_MARKER = ".grammar_version"

    @Volatile
    private var versionChecked = false

    fun getGrammarPath(assetName: String): String {
        val context = SentinelApplication.getInstance().applicationContext
        ensureGrammarsUpToDate(context)

        val target = File(grammarDir(context), assetName)

        if (target.exists()) return target.absolutePath

        return try {
            copyAsset(context, assetName, target)
            target.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy grammar asset: $assetName", e)
            target.absolutePath
        }
    }

    private fun grammarDir(context: Context): File =
        File(context.filesDir, GRAMMAR_DIR)

    private fun ensureGrammarsUpToDate(context: Context) {
        if (versionChecked) return

        synchronized(this) {
            if (versionChecked) return

            try {
                val currentVersion = getAppVersionCode(context)
                val dir = grammarDir(context)
                val marker = File(dir, VERSION_MARKER)

                val storedVersion = if (marker.exists()) {
                    marker.readText().trim().toLongOrNull()
                } else {
                    null
                }

                if (storedVersion == currentVersion) {
                    versionChecked = true
                    return
                }

                Log.i(TAG, "Grammar cache outdated (stored=$storedVersion, " +
                        "current=$currentVersion). Re-copying grammar assets.")

                if (dir.exists()) {
                    dir.deleteRecursively()
                }
                dir.mkdirs()

                val assets = context.assets.list("") ?: emptyArray()
                for (asset in assets) {
                    if (asset.endsWith(".gbnf")) {
                        try {
                            copyAsset(context, asset, File(dir, asset))
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to copy grammar asset during refresh: $asset", e)
                        }
                    }
                }

                marker.writeText(currentVersion.toString())
                Log.i(TAG, "Grammar cache updated to version $currentVersion")
            } catch (e: Exception) {
                Log.e(TAG, "Error during grammar version check", e)
            } finally {
                versionChecked = true
            }
        }
    }

    private fun copyAsset(context: Context, assetName: String, target: File) {
        target.parentFile?.mkdirs()
        context.assets.open(assetName).use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getAppVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
    }
}
