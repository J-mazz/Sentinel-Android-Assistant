package com.mazzlabs.sentinel.tools.modules

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import com.mazzlabs.sentinel.tools.framework.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * CalendarModule - Full calendar access via CalendarContract
 * 
 * Operations:
 * - read_events: Query events by date range
 * - create_event: Add new calendar event
 * - update_event: Modify existing event
 * - delete_event: Remove event
 * - get_calendars: List available calendars
 */
class CalendarModule : ToolModule {
    
    companion object {
        private const val TAG = "CalendarModule"
        private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    private fun safeParseDatetime(input: String): Long? {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
        )

        for (format in formats) {
            format.isLenient = false
            try {
                return format.parse(input)?.time
            } catch (e: Exception) {
                // try next
            }
        }

        val lower = input.lowercase()
        val cal = Calendar.getInstance()

        return when {
            lower == "today" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            lower == "tomorrow" -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            lower.startsWith("in ") -> {
                parseRelativeTime(lower.removePrefix("in ").trim())
            }
            else -> null
        }
    }

    private fun parseRelativeTime(relative: String): Long? {
        val cal = Calendar.getInstance()
        val parts = relative.split(" ")
        if (parts.size != 2) return null

        val amount = parts[0].toIntOrNull() ?: return null

        when (parts[1].lowercase().trimEnd('s')) {
            "minute", "min" -> cal.add(Calendar.MINUTE, amount)
            "hour", "hr" -> cal.add(Calendar.HOUR_OF_DAY, amount)
            "day" -> cal.add(Calendar.DAY_OF_YEAR, amount)
            "week" -> cal.add(Calendar.WEEK_OF_YEAR, amount)
            else -> return null
        }

        return cal.timeInMillis
    }
    
    override val moduleId = "calendar"
    
    override val description = "Manage calendar events - create, read, update, delete appointments and meetings"
    
    override val requiredPermissions = listOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )
    
    override val operations = listOf(
        ToolOperation(
            operationId = "read_events",
            description = "Query calendar events within a date range",
            parameters = listOf(
                ToolParameter("start_date", ParameterType.DATE, "Start date (YYYY-MM-DD)", required = true),
                ToolParameter("end_date", ParameterType.DATE, "End date (YYYY-MM-DD)", required = false),
                ToolParameter("search", ParameterType.STRING, "Search term for event title", required = false),
                ToolParameter("max_results", ParameterType.INTEGER, "Maximum events to return", required = false, default = 10)
            ),
            examples = listOf(
                ToolExample("What's on my calendar today?", "read_events", mapOf("start_date" to "2026-01-10")),
                ToolExample("Show meetings this week", "read_events", mapOf("start_date" to "2026-01-10", "end_date" to "2026-01-17")),
                ToolExample("Find dentist appointment", "read_events", mapOf("start_date" to "2026-01-01", "end_date" to "2026-12-31", "search" to "dentist"))
            )
        ),
        ToolOperation(
            operationId = "create_event",
            description = "Create a new calendar event",
            parameters = listOf(
                ToolParameter("title", ParameterType.STRING, "Event title", required = true),
                ToolParameter("start_time", ParameterType.DATETIME, "Start time (YYYY-MM-DDTHH:MM:SS)", required = true),
                ToolParameter("end_time", ParameterType.DATETIME, "End time (YYYY-MM-DDTHH:MM:SS)", required = false),
                ToolParameter("duration_minutes", ParameterType.INTEGER, "Duration in minutes (if no end_time)", required = false, default = 60),
                ToolParameter("location", ParameterType.STRING, "Event location", required = false),
                ToolParameter("description", ParameterType.STRING, "Event description/notes", required = false),
                ToolParameter("all_day", ParameterType.BOOLEAN, "All-day event", required = false, default = false),
                ToolParameter("reminder_minutes", ParameterType.INTEGER, "Reminder before event (minutes)", required = false, default = 15)
            ),
            examples = listOf(
                ToolExample("Schedule meeting with Bob at 3pm", "create_event", mapOf("title" to "Meeting with Bob", "start_time" to "2026-01-10T15:00:00")),
                ToolExample("Add dentist appointment tomorrow at 10am for 30 minutes", "create_event", mapOf("title" to "Dentist", "start_time" to "2026-01-11T10:00:00", "duration_minutes" to 30))
            )
        ),
        ToolOperation(
            operationId = "delete_event",
            description = "Delete a calendar event by ID",
            parameters = listOf(
                ToolParameter("event_id", ParameterType.STRING, "Event ID to delete", required = true)
            )
        ),
        ToolOperation(
            operationId = "get_calendars",
            description = "List available calendars on the device",
            parameters = listOf()
        )
    )
    
    override suspend fun execute(
        operationId: String,
        params: Map<String, Any?>,
        context: Context
    ): ToolResponse {
        return when (operationId) {
            "read_events" -> readEvents(params, context)
            "create_event" -> createEvent(params, context)
            "delete_event" -> deleteEvent(params, context)
            "get_calendars" -> getCalendars(context)
            else -> ToolResponse.Error(moduleId, operationId, ErrorCode.NOT_FOUND, "Unknown operation: $operationId")
        }
    }
    
    private fun readEvents(params: Map<String, Any?>, context: Context): ToolResponse {
        val startDateStr = params["start_date"] as? String
            ?: return ToolResponse.Error(moduleId, "read_events", ErrorCode.INVALID_PARAMS, "start_date required")
        
        val endDateStr = params["end_date"] as? String ?: startDateStr
        val search = params["search"] as? String
        val maxResults = (params["max_results"] as? Number)?.toInt() ?: 10
        
        try {
            val startMillis = safeParseDatetime(startDateStr)
                ?: return ToolResponse.Error(
                    moduleId,
                    "read_events",
                    ErrorCode.INVALID_PARAMS,
                    "Invalid start_date format: $startDateStr"
                )
            val endMillis = safeParseDatetime(endDateStr)
                ?: return ToolResponse.Error(
                    moduleId,
                    "read_events",
                    ErrorCode.INVALID_PARAMS,
                    "Invalid end_date format: $endDateStr"
                )

            val startDate = Date(startMillis)
            val endDate = Calendar.getInstance().apply {
                timeInMillis = endMillis
                add(Calendar.DAY_OF_MONTH, 1)  // Include full end day
            }.time
            
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.ALL_DAY
            )
            
            var selection = "(${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTSTART} < ?)"
            val selectionArgs = mutableListOf(startDate.time.toString(), endDate.time.toString())
            
            if (!search.isNullOrBlank()) {
                selection += " AND (${CalendarContract.Events.TITLE} LIKE ?)"
                selectionArgs.add("%$search%")
            }
            
            val events = mutableListOf<Map<String, Any?>>()
            
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs.toTypedArray(),
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext() && events.size < maxResults) {
                    val event = mapOf(
                        "id" to cursor.getLong(0).toString(),
                        "title" to cursor.getString(1),
                        "start" to dateTimeFormat.format(Date(cursor.getLong(2))),
                        "end" to cursor.getLong(3).let { if (it > 0) dateTimeFormat.format(Date(it)) else null },
                        "location" to cursor.getString(4),
                        "description" to cursor.getString(5),
                        "all_day" to (cursor.getInt(6) == 1)
                    )
                    events.add(event)
                }
            }
            
            val message = if (events.isEmpty()) {
                "No events found"
            } else {
                "${events.size} event(s) found"
            }
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "read_events",
                message = message,
                data = mapOf("events" to events)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reading events", e)
            return ToolResponse.Error(moduleId, "read_events", ErrorCode.SYSTEM_ERROR, "Failed to read events: ${e.message}")
        }
    }
    
    private fun createEvent(params: Map<String, Any?>, context: Context): ToolResponse {
        val title = params["title"] as? String
            ?: return ToolResponse.Error(moduleId, "create_event", ErrorCode.INVALID_PARAMS, "title required")
        val startTimeStr = params["start_time"] as? String
            ?: return ToolResponse.Error(moduleId, "create_event", ErrorCode.INVALID_PARAMS, "start_time required")
        
        try {
            val startTime = safeParseDatetime(startTimeStr)
                ?: return ToolResponse.Error(
                    moduleId,
                    "create_event",
                    ErrorCode.INVALID_PARAMS,
                    "Invalid start_time format: $startTimeStr"
                )
            val durationMinutes = (params["duration_minutes"] as? Number)?.toInt() ?: 60
            
            val endTime = (params["end_time"] as? String)?.let {
                safeParseDatetime(it)
                    ?: return ToolResponse.Error(
                        moduleId,
                        "create_event",
                        ErrorCode.INVALID_PARAMS,
                        "Invalid end_time format: $it"
                    )
            } ?: (startTime + durationMinutes * 60 * 1000)
            
            val location = params["location"] as? String
            val description = params["description"] as? String
            val allDay = params["all_day"] as? Boolean ?: false
            val reminderMinutes = (params["reminder_minutes"] as? Number)?.toInt() ?: 15
            
            // Get default calendar
            val calendarId = getDefaultCalendarId(context)
                ?: return ToolResponse.Error(moduleId, "create_event", ErrorCode.NOT_FOUND, "No calendar found")
            
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, startTime)
                put(CalendarContract.Events.DTEND, endTime)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
                location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
                description?.let { put(CalendarContract.Events.DESCRIPTION, it) }
            }
            
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: return ToolResponse.Error(moduleId, "create_event", ErrorCode.SYSTEM_ERROR, "Failed to create event")
            
            val eventId = ContentUris.parseId(uri)
            
            // Add reminder
            if (reminderMinutes > 0) {
                val reminderValues = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, reminderMinutes)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            }
            
            Log.i(TAG, "Created event: $title (ID: $eventId)")
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "create_event",
                message = "Event '$title' created",
                data = mapOf(
                    "event_id" to eventId.toString(),
                    "title" to title,
                    "start" to startTimeStr
                )
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating event", e)
            return ToolResponse.Error(moduleId, "create_event", ErrorCode.SYSTEM_ERROR, "Failed to create event: ${e.message}")
        }
    }
    
    private fun deleteEvent(params: Map<String, Any?>, context: Context): ToolResponse {
        val eventId = params["event_id"] as? String
            ?: return ToolResponse.Error(moduleId, "delete_event", ErrorCode.INVALID_PARAMS, "event_id required")
        
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId.toLong())
            val rows = context.contentResolver.delete(uri, null, null)
            
            return if (rows > 0) {
                ToolResponse.Success(moduleId, "delete_event", "Event deleted", mapOf("event_id" to eventId))
            } else {
                ToolResponse.Error(moduleId, "delete_event", ErrorCode.NOT_FOUND, "Event not found: $eventId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting event", e)
            return ToolResponse.Error(moduleId, "delete_event", ErrorCode.SYSTEM_ERROR, "Failed to delete event: ${e.message}")
        }
    }
    
    private fun getCalendars(context: Context): ToolResponse {
        try {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.OWNER_ACCOUNT
            )
            
            val calendars = mutableListOf<Map<String, Any?>>()
            
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    calendars.add(mapOf(
                        "id" to cursor.getLong(0).toString(),
                        "name" to cursor.getString(1),
                        "account" to cursor.getString(2),
                        "owner" to cursor.getString(3)
                    ))
                }
            }
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "get_calendars",
                message = "${calendars.size} calendar(s) found",
                data = mapOf("calendars" to calendars)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting calendars", e)
            return ToolResponse.Error(moduleId, "get_calendars", ErrorCode.SYSTEM_ERROR, "Failed to get calendars: ${e.message}")
        }
    }
    
    private fun getDefaultCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.IS_PRIMARY} = 1"
        
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
        
        // Fallback: get any visible calendar
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE} = 1",
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
