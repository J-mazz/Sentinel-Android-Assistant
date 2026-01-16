package com.mazzlabs.sentinel.tools

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import java.util.*

/**
 * AlarmCreateTool - Create system alarms
 */
class AlarmCreateTool : Tool {
    override val name = "alarm_create"
    override val description = "Create a new alarm at a specific time"
    override val parametersSchema = """
        {
            "hour": "integer (required - 0-23)",
            "minute": "integer (required - 0-59)",
            "label": "string (optional - alarm label/description)",
            "days": "array of strings (optional - MON, TUE, WED, THU, FRI, SAT, SUN for recurring)"
        }
    """.trimIndent()
    override val requiredPermissions = listOf<String>() // Uses implicit intent

    override fun validateParams(params: Map<String, Any?>): ValidationResult {
        val hour = (params["hour"] as? Number)?.toInt()
        val minute = (params["minute"] as? Number)?.toInt()
        
        if (hour == null || hour !in 0..23) {
            return ValidationResult.Invalid("hour must be 0-23")
        }
        if (minute == null || minute !in 0..59) {
            return ValidationResult.Invalid("minute must be 0-59")
        }
        return ValidationResult.Valid
    }

    override suspend fun execute(params: Map<String, Any?>, context: Context): ToolResult {
        val validation = validateParams(params)
        if (validation is ValidationResult.Invalid) {
            return ToolResult.Failure(name, validation.reason, ErrorCode.INVALID_PARAMS)
        }
        
        return try {
            val hour = (params["hour"] as Number).toInt()
            val minute = (params["minute"] as Number).toInt()
            val label = params["label"] as? String ?: "Alarm"
            val days = (params["days"] as? List<*>)?.mapNotNull { dayString ->
                when ((dayString as? String)?.uppercase()) {
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
            
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                
                if (!days.isNullOrEmpty()) {
                    putExtra(AlarmClock.EXTRA_DAYS, ArrayList(days))
                }
                
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                
                val timeStr = String.format("%02d:%02d", hour, minute)
                ToolResult.Success(
                    toolName = name,
                    message = "Alarm set for $timeStr",
                    data = mapOf("time" to timeStr, "label" to label)
                )
            } else {
                ToolResult.Failure(name, "No alarm app found", ErrorCode.NOT_FOUND)
            }
        } catch (e: Exception) {
            Log.e("AlarmCreateTool", "Error creating alarm", e)
            ToolResult.Failure(name, "Failed to create alarm: ${e.message}", ErrorCode.SYSTEM_ERROR)
        }
    }
}

/**
 * TimerCreateTool - Create countdown timers
 */
class TimerCreateTool : Tool {
    override val name = "timer_create"
    override val description = "Create a countdown timer"
    override val parametersSchema = """
        {
            "seconds": "integer (required - duration in seconds)",
            "label": "string (optional - timer label)"
        }
    """.trimIndent()
    override val requiredPermissions = listOf<String>()

    override fun validateParams(params: Map<String, Any?>): ValidationResult {
        val seconds = (params["seconds"] as? Number)?.toInt()
        if (seconds == null || seconds <= 0) {
            return ValidationResult.Invalid("seconds must be a positive integer")
        }
        return ValidationResult.Valid
    }

    override suspend fun execute(params: Map<String, Any?>, context: Context): ToolResult {
        val validation = validateParams(params)
        if (validation is ValidationResult.Invalid) {
            return ToolResult.Failure(name, validation.reason, ErrorCode.INVALID_PARAMS)
        }
        
        return try {
            val seconds = (params["seconds"] as Number).toInt()
            val label = params["label"] as? String ?: "Timer"
            
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                
                val minutes = seconds / 60
                val secs = seconds % 60
                val durationStr = if (minutes > 0) "${minutes}m ${secs}s" else "${secs}s"
                
                ToolResult.Success(
                    toolName = name,
                    message = "Timer set for $durationStr",
                    data = mapOf("duration_seconds" to seconds, "label" to label)
                )
            } else {
                ToolResult.Failure(name, "No timer app found", ErrorCode.NOT_FOUND)
            }
        } catch (e: Exception) {
            Log.e("TimerCreateTool", "Error creating timer", e)
            ToolResult.Failure(name, "Failed to create timer: ${e.message}", ErrorCode.SYSTEM_ERROR)
        }
    }
}
