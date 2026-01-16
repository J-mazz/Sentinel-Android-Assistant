package com.mazzlabs.sentinel.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootReceiver - Handles device boot completion
 * 
 * Can be used to restore service state after device restart.
 * Currently just logs - actual restoration depends on user preference.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed - Sentinel Agent ready")
            
            // Note: We don't auto-start the accessibility service
            // as it requires explicit user enablement for security.
            // The user must manually enable it in Accessibility Settings.
        }
    }
}
