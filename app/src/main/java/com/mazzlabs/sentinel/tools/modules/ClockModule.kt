package com.mazzlabs.sentinel.tools.modules

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import com.mazzlabs.sentinel.tools.framework.*
import java.util.*

/**
 * ClockModule - Alarms and timers via AlarmClock intents
 * 
 * Operations:
 * - create_alarm: Set an alarm for a specific time
 * - create_timer: Start a countdown timer
 * - show_alarms: Open alarm app
 * - show_timers: Open timer app
 */
class ClockModule : ToolModule {
    
    companion object {
        private const val TAG = "ClockModule"
    }
    
    override val moduleId = "clock"
    
    override val description = "Set alarms and timers - wake up calls, reminders, countdowns"
    
    override val requiredPermissions = listOf<String>() // Uses intents, no permissions needed
    
    override val operations = listOf(
        ToolOperation(
            operationId = "create_alarm",
            description = "Set an alarm for a specific time",
            parameters = listOf(
                ToolParameter("hour", ParameterType.INTEGER, "Hour (0-23)", required = true),
                ToolParameter("minute", ParameterType.INTEGER, "Minute (0-59)", required = true),
                ToolParameter("label", ParameterType.STRING, "Alarm label/message", required = false),
                ToolParameter("days", ParameterType.ARRAY, "Repeat days: MON,TUE,WED,THU,FRI,SAT,SUN", required = false),
                ToolParameter("vibrate", ParameterType.BOOLEAN, "Enable vibration", required = false, default = true),
                ToolParameter("skip_ui", ParameterType.BOOLEAN, "Skip alarm app UI", required = false, default = true)
            ),
            examples = listOf(
                ToolExample("Set alarm for 6 AM", "create_alarm", mapOf("hour" to 6, "minute" to 0)),
                ToolExample("Wake me up at 7:30", "create_alarm", mapOf("hour" to 7, "minute" to 30, "label" to "Wake up")),
                ToolExample("Set weekday alarm for 6:30 AM", "create_alarm", mapOf("hour" to 6, "minute" to 30, "days" to listOf("MON", "TUE", "WED", "THU", "FRI")))
            )
        ),
        ToolOperation(
            operationId = "create_timer",
            description = "Start a countdown timer",
            parameters = listOf(
                ToolParameter("seconds", ParameterType.INTEGER, "Duration in seconds", required = false),
                ToolParameter("minutes", ParameterType.INTEGER, "Duration in minutes", required = false),
                ToolParameter("hours", ParameterType.INTEGER, "Duration in hours", required = false),
                ToolParameter("label", ParameterType.STRING, "Timer label", required = false),
                ToolParameter("skip_ui", ParameterType.BOOLEAN, "Skip timer app UI", required = false, default = true)
            ),
            examples = listOf(
                ToolExample("Set timer for 5 minutes", "create_timer", mapOf("minutes" to 5)),
                ToolExample("Timer for 30 seconds", "create_timer", mapOf("seconds" to 30)),
                ToolExample("Set a 2 hour timer for laundry", "create_timer", mapOf("hours" to 2, "label" to "Laundry done"))
            )
        ),
        ToolOperation(
            operationId = "show_alarms",
            description = "Open the alarms list in clock app",
            parameters = listOf()
        ),
        ToolOperation(
            operationId = "show_timers",
            description = "Open the timers list in clock app",
            parameters = listOf()
        ),
        ToolOperation(
            operationId = "dismiss_alarm",
            description = "Dismiss or snooze a ringing alarm",
            parameters = listOf(
                ToolParameter("action", ParameterType.STRING, "Action: dismiss or snooze", required = true, enumValues = listOf("dismiss", "snooze"))
            )
        )
    )
    
    override suspend fun execute(
        operationId: String,
        params: Map<String, Any?>,
        context: Context
    ): ToolResponse {
        return when (operationId) {
            "create_alarm" -> createAlarm(params, context)
            "create_timer" -> createTimer(params, context)
            "show_alarms" -> showAlarms(context)
            "show_timers" -> showTimers(context)
            "dismiss_alarm" -> dismissAlarm(params, context)
            else -> ToolResponse.Error(moduleId, operationId, ErrorCode.NOT_FOUND, "Unknown operation: $operationId")
        }
    }
    
    private fun createAlarm(params: Map<String, Any?>, context: Context): ToolResponse {
        val hour = (params["hour"] as? Number)?.toInt()
            ?: return ToolResponse.Error(moduleId, "create_alarm", ErrorCode.INVALID_PARAMS, "hour required (0-23)")
        val minute = (params["minute"] as? Number)?.toInt()
            ?: return ToolResponse.Error(moduleId, "create_alarm", ErrorCode.INVALID_PARAMS, "minute required (0-59)")
        
        if (hour !in 0..23) {
            return ToolResponse.Error(moduleId, "create_alarm", ErrorCode.INVALID_PARAMS, "hour must be 0-23")
        }
        if (minute !in 0..59) {
            return ToolResponse.Error(moduleId, "create_alarm", ErrorCode.INVALID_PARAMS, "minute must be 0-59")
        }
        
        val label = params["label"] as? String ?: "Alarm"
        val vibrate = params["vibrate"] as? Boolean ?: true
        val skipUi = params["skip_ui"] as? Boolean ?: true
        
        @Suppress("UNCHECKED_CAST")
        val days = (params["days"] as? List<*>)?.mapNotNull { dayStr ->
            when ((dayStr as? String)?.uppercase()) {
                "MON" -> Calendar.MONDAY
                "TUE" -> Calendar.TUESDAY
                "WED" -> Calendar.WEDNESDAY
                "THU" -> Calendar.THURSDAY
                "FRI" -> Calendar.FRIDAY
                "SAT" -> Calendar.SATURDAY
                "SUN" -> Calendar.SUNDAY
                else -> null
            }
        }
        
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_VIBRATE, vibrate)
                putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                
                if (!days.isNullOrEmpty()) {
                    putExtra(AlarmClock.EXTRA_DAYS, ArrayList(days))
                }
                
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                
                val timeStr = String.format("%02d:%02d", hour, minute)
                val amPm = if (hour < 12) "AM" else "PM"
                val hour12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                val friendlyTime = "$hour12:${String.format("%02d", minute)} $amPm"
                
                Log.i(TAG, "Alarm set for $friendlyTime")
                
                return ToolResponse.Success(
                    moduleId = moduleId,
                    operationId = "create_alarm",
                    message = "Alarm set for $friendlyTime",
                    data = mapOf(
                        "hour" to hour,
                        "minute" to minute,
                        "label" to label,
                        "time_24h" to timeStr,
                        "time_12h" to friendlyTime
                    )
                )
            } else {
                return ToolResponse.Error(moduleId, "create_alarm", ErrorCode.NOT_AVAILABLE, "No clock app found")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating alarm", e)
            return ToolResponse.Error(moduleId, "create_alarm", ErrorCode.SYSTEM_ERROR, "Failed to set alarm: ${e.message}")
        }
    }
    
    private fun createTimer(params: Map<String, Any?>, context: Context): ToolResponse {
        val hours = (params["hours"] as? Number)?.toInt() ?: 0
        val minutes = (params["minutes"] as? Number)?.toInt() ?: 0
        val seconds = (params["seconds"] as? Number)?.toInt() ?: 0
        
        val totalSeconds = hours * 3600 + minutes * 60 + seconds
        
        if (totalSeconds <= 0) {
            return ToolResponse.Error(moduleId, "create_timer", ErrorCode.INVALID_PARAMS, "Duration must be positive (provide hours, minutes, or seconds)")
        }
        
        val label = params["label"] as? String ?: "Timer"
        val skipUi = params["skip_ui"] as? Boolean ?: true
        
        try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, totalSeconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                
                val durationStr = buildString {
                    if (hours > 0) append("${hours}h ")
                    if (minutes > 0) append("${minutes}m ")
                    if (seconds > 0 || (hours == 0 && minutes == 0)) append("${seconds}s")
                }.trim()
                
                Log.i(TAG, "Timer set for $durationStr")
                
                return ToolResponse.Success(
                    moduleId = moduleId,
                    operationId = "create_timer",
                    message = "Timer set for $durationStr",
                    data = mapOf(
                        "duration_seconds" to totalSeconds,
                        "duration_formatted" to durationStr,
                        "label" to label
                    )
                )
            } else {
                return ToolResponse.Error(moduleId, "create_timer", ErrorCode.NOT_AVAILABLE, "No timer app found")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating timer", e)
            return ToolResponse.Error(moduleId, "create_timer", ErrorCode.SYSTEM_ERROR, "Failed to set timer: ${e.message}")
        }
    }
    
    private fun showAlarms(context: Context): ToolResponse {
        try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return ToolResponse.Success(moduleId, "show_alarms", "Opening alarms")
            } else {
                return ToolResponse.Error(moduleId, "show_alarms", ErrorCode.NOT_AVAILABLE, "No clock app found")
            }
        } catch (e: Exception) {
            return ToolResponse.Error(moduleId, "show_alarms", ErrorCode.SYSTEM_ERROR, "Failed to open alarms: ${e.message}")
        }
    }
    
    private fun showTimers(context: Context): ToolResponse {
        try {
            val intent = Intent(AlarmClock.ACTION_SHOW_TIMERS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return ToolResponse.Success(moduleId, "show_timers", "Opening timers")
            } else {
                return ToolResponse.Error(moduleId, "show_timers", ErrorCode.NOT_AVAILABLE, "No clock app found")
            }
        } catch (e: Exception) {
            return ToolResponse.Error(moduleId, "show_timers", ErrorCode.SYSTEM_ERROR, "Failed to open timers: ${e.message}")
        }
    }
    
    private fun dismissAlarm(params: Map<String, Any?>, context: Context): ToolResponse {
        val action = params["action"] as? String
            ?: return ToolResponse.Error(moduleId, "dismiss_alarm", ErrorCode.INVALID_PARAMS, "action required: dismiss or snooze")
        
        try {
            val intent = when (action.lowercase()) {
                "dismiss" -> Intent(AlarmClock.ACTION_DISMISS_ALARM)
                "snooze" -> Intent(AlarmClock.ACTION_SNOOZE_ALARM)
                else -> return ToolResponse.Error(moduleId, "dismiss_alarm", ErrorCode.INVALID_PARAMS, "action must be 'dismiss' or 'snooze'")
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return ToolResponse.Success(moduleId, "dismiss_alarm", "Alarm ${action}ed")
            } else {
                return ToolResponse.Error(moduleId, "dismiss_alarm", ErrorCode.NOT_AVAILABLE, "No clock app found")
            }
        } catch (e: Exception) {
            return ToolResponse.Error(moduleId, "dismiss_alarm", ErrorCode.SYSTEM_ERROR, "Failed to $action alarm: ${e.message}")
        }
    }
}
