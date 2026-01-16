package com.mazzlabs.sentinel.tools

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * CalendarReadTool - Fetch calendar events
 */
class CalendarReadTool : Tool {
    override val name = "calendar_read"
    override val description = "Read calendar events for a specific date or date range"
    override val parametersSchema = """
        {
            "date": "string (YYYY-MM-DD format, optional - defaults to today)",
            "days_ahead": "integer (number of days to look ahead, default 1)"
        }
    """.trimIndent()
    override val requiredPermissions = listOf(Manifest.permission.READ_CALENDAR)

    override suspend fun execute(params: Map<String, Any?>, context: Context): ToolResult {
        return try {
            val dateStr = params["date"] as? String
            val daysAhead = (params["days_ahead"] as? Number)?.toInt() ?: 1
            
            val calendar = Calendar.getInstance()
            if (dateStr != null) {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                calendar.time = sdf.parse(dateStr) ?: Date()
            }
            
            // Set to start of day
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            val startMillis = calendar.timeInMillis
            
            // End time
            calendar.add(Calendar.DAY_OF_YEAR, daysAhead)
            val endMillis = calendar.timeInMillis
            
            val events = mutableListOf<Map<String, Any?>>()
            
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION
            )
            
            val selection = "(${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTSTART} <= ?)"
            val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
            
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                CalendarContract.Events.DTSTART + " ASC"
            )?.use { cursor ->
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                while (cursor.moveToNext()) {
                    events.add(mapOf(
                        "id" to cursor.getLong(0),
                        "title" to cursor.getString(1),
                        "description" to cursor.getString(2),
                        "start" to sdf.format(Date(cursor.getLong(3))),
                        "end" to cursor.getLong(4).let { if (it > 0) sdf.format(Date(it)) else null },
                        "location" to cursor.getString(5)
                    ))
                }
            }
            
            ToolResult.Success(
                toolName = name,
                message = "Found ${events.size} events",
                data = mapOf("events" to events)
            )
        } catch (e: SecurityException) {
            ToolResult.Failure(name, "Calendar permission denied", ErrorCode.PERMISSION_DENIED)
        } catch (e: Exception) {
            Log.e("CalendarReadTool", "Error reading calendar", e)
            ToolResult.Failure(name, "Failed to read calendar: ${e.message}", ErrorCode.SYSTEM_ERROR)
        }
    }
}

/**
 * CalendarWriteTool - Create new calendar events
 */
class CalendarWriteTool : Tool {
    override val name = "calendar_create"
    override val description = "Create a new calendar event"
    override val parametersSchema = """
        {
            "title": "string (required - event title)",
            "date": "string (required - YYYY-MM-DD format)",
            "start_time": "string (required - HH:MM 24-hour format)",
            "end_time": "string (optional - HH:MM 24-hour format, defaults to 1 hour after start)",
            "description": "string (optional)",
            "location": "string (optional)"
        }
    """.trimIndent()
    override val requiredPermissions = listOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    override fun validateParams(params: Map<String, Any?>): ValidationResult {
        if (params["title"] == null) return ValidationResult.Invalid("title is required")
        if (params["date"] == null) return ValidationResult.Invalid("date is required")
        if (params["start_time"] == null) return ValidationResult.Invalid("start_time is required")
        return ValidationResult.Valid
    }

    override suspend fun execute(params: Map<String, Any?>, context: Context): ToolResult {
        val validation = validateParams(params)
        if (validation is ValidationResult.Invalid) {
            return ToolResult.Failure(name, validation.reason, ErrorCode.INVALID_PARAMS)
        }
        
        return try {
            val title = params["title"] as String
            val dateStr = params["date"] as String
            val startTimeStr = params["start_time"] as String
            val endTimeStr = params["end_time"] as? String
            val description = params["description"] as? String
            val location = params["location"] as? String
            
            // Parse start time
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val startTime = sdf.parse("$dateStr $startTimeStr")
                ?: return ToolResult.Failure(name, "Invalid date/time format", ErrorCode.INVALID_PARAMS)
            
            // Parse or calculate end time
            val endTime = if (endTimeStr != null) {
                sdf.parse("$dateStr $endTimeStr") ?: Date(startTime.time + 3600000)
            } else {
                Date(startTime.time + 3600000) // Default 1 hour
            }
            
            // Get default calendar ID
            val calendarId = getDefaultCalendarId(context)
                ?: return ToolResult.Failure(name, "No calendar found", ErrorCode.NOT_FOUND)
            
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, startTime.time)
                put(CalendarContract.Events.DTEND, endTime.time)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                description?.let { put(CalendarContract.Events.DESCRIPTION, it) }
                location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
            }
            
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.lastPathSegment?.toLongOrNull()
            
            if (eventId != null) {
                ToolResult.Success(
                    toolName = name,
                    message = "Event '$title' created successfully",
                    data = mapOf("event_id" to eventId, "title" to title, "start" to startTime.toString())
                )
            } else {
                ToolResult.Failure(name, "Failed to create event", ErrorCode.SYSTEM_ERROR)
            }
        } catch (e: SecurityException) {
            ToolResult.Failure(name, "Calendar permission denied", ErrorCode.PERMISSION_DENIED)
        } catch (e: Exception) {
            Log.e("CalendarWriteTool", "Error creating event", e)
            ToolResult.Failure(name, "Failed to create event: ${e.message}", ErrorCode.SYSTEM_ERROR)
        }
    }
    
    private fun getDefaultCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.IS_PRIMARY} = 1"
        
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        
        // Fallback: get any calendar
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        
        return null
    }
}
