# Tool Development Guide

Complete guide to understanding, using, and creating tool modules in Sentinel.

## Table of Contents

- [Tool Framework Overview](#tool-framework-overview)
- [Available Tools](#available-tools)
- [Using Tools](#using-tools)
- [Creating a New Tool](#creating-a-new-tool)
- [Tool Testing](#tool-testing)
- [Best Practices](#best-practices)

## Tool Framework Overview

Sentinel's tool framework allows the agent to interact with device capabilities beyond UI automation.

### Architecture

```
┌──────────────────────────────────────────────────────────┐
│                  Agent Orchestrator                       │
│  (Decides when to use a tool vs UI action)               │
└─────────────────────┬────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│                  ToolExecutor                            │
│  • Parses JSON tool calls from LLM                      │
│  • Validates parameters against schema                   │
│  • Routes to appropriate module                          │
│  • Formats results for LLM                              │
└─────────────────────┬───────────────────────────────────┘
                      │
         ┌────────────┴────────────┬──────────────┐
         ▼                         ▼              ▼
┌──────────────┐          ┌──────────────┐  ┌─────────────┐
│CalendarModule│          │ContactsModule│  │TerminalModule│
│              │          │              │  │             │
│ • read_events│          │ • search     │  │ • execute   │
│ • create     │          │ • get        │  │ • list_files│
│ • delete     │          │ • add        │  │ • read_file │
└──────────────┘          └──────────────┘  └─────────────┘
```

### Core Interfaces

**ToolModule** (`/app/src/main/java/com/mazzlabs/sentinel/tools/framework/ToolModule.kt`):
```kotlin
interface ToolModule {
    val moduleId: String              // Unique identifier (e.g., "calendar")
    val description: String           // Human-readable description
    val operations: List<ToolOperation>  // Available operations
    val requiredPermissions: List<String>  // Android permissions needed

    suspend fun execute(
        operationId: String,
        params: Map<String, Any?>,
        context: Context
    ): ToolResponse
}
```

**ToolOperation** (Operation metadata):
```kotlin
data class ToolOperation(
    val operationId: String,          // e.g., "read_events"
    val description: String,
    val parameters: List<ToolParameter>,
    val examples: List<ToolExample> = emptyList()
)
```

**ToolResponse** (Result types):
```kotlin
sealed class ToolResponse {
    data class Success(
        val moduleId: String,
        val operationId: String,
        val message: String,
        val data: Map<String, Any?> = emptyMap()
    ) : ToolResponse()

    data class Error(
        val moduleId: String,
        val operationId: String,
        val errorCode: ErrorCode,
        val message: String
    ) : ToolResponse()

    data class PermissionRequired(
        val moduleId: String,
        val operationId: String,
        val permissions: List<String>
    ) : ToolResponse()

    data class Confirmation(
        val moduleId: String,
        val operationId: String,
        val message: String,
        val pendingAction: Map<String, Any?>
    ) : ToolResponse()
}
```

## Available Tools

### 1. Calendar Module

**Module ID**: `calendar`
**File**: `/app/src/main/java/com/mazzlabs/sentinel/tools/modules/CalendarModule.kt`
**Permissions**: `READ_CALENDAR`, `WRITE_CALENDAR`

**Operations**:

**read_events**: Retrieve calendar events
```kotlin
Parameters:
- start_date: String (ISO 8601 or natural language like "today")
- end_date: String (optional, defaults to 7 days)
- calendar_id: Long (optional, defaults to all calendars)
- max_events: Int (optional, defaults to 100)

Example:
{
  "tool": "calendar.read_events",
  "params": {
    "start_date": "today",
    "end_date": "in 1 week"
  }
}

Response:
{
  "events": [
    {
      "id": "123",
      "title": "Team Meeting",
      "start": "2026-01-18T10:00:00",
      "end": "2026-01-18T11:00:00",
      "location": "Conference Room A",
      "description": "Weekly sync"
    }
  ]
}
```

**create_event**: Create new calendar event
```kotlin
Parameters:
- title: String (required)
- start_time: String (ISO 8601 or natural)
- end_time: String (optional, defaults to +1 hour)
- description: String (optional)
- location: String (optional)
- calendar_id: Long (optional)

Example:
{
  "tool": "calendar.create_event",
  "params": {
    "title": "Dentist Appointment",
    "start_time": "tomorrow at 2pm",
    "end_time": "tomorrow at 3pm",
    "location": "123 Main St"
  }
}
```

**delete_event**: Delete calendar event
```kotlin
Parameters:
- event_id: String (required)
- confirm: Boolean (optional, requires confirmation if true)
```

**get_calendars**: List available calendars
```kotlin
Parameters: None

Response:
{
  "calendars": [
    {"id": "1", "name": "Personal", "account": "user@gmail.com"},
    {"id": "2", "name": "Work", "account": "user@company.com"}
  ]
}
```

### 2. Clock Module

**Module ID**: `clock`
**Permissions**: `SET_ALARM`

**Operations**:

**create_alarm**:
```kotlin
Parameters:
- time: String (e.g., "7:30 AM", "19:00")
- label: String (optional)
- days: List<String> (e.g., ["MON", "TUE", "WED"])
- vibrate: Boolean (default: true)
```

**list_alarms**: Get all alarms
**delete_alarm**: Delete alarm by ID
**create_timer**: Set countdown timer

### 3. Contacts Module

**Module ID**: `contacts`
**Permissions**: `READ_CONTACTS`, `WRITE_CONTACTS`

**Operations**:

**search_contacts**:
```kotlin
Parameters:
- query: String (name or phone number)
- limit: Int (default: 10)

Response:
{
  "contacts": [
    {
      "id": "123",
      "name": "John Doe",
      "phone": "+1234567890",
      "email": "john@example.com"
    }
  ]
}
```

**get_contact**: Get contact by ID
**add_contact**: Create new contact

### 4. Messaging Module

**Module ID**: `messaging`
**Permissions**: `SEND_SMS`, `READ_SMS`

**Operations**:

**send_sms**:
```kotlin
Parameters:
- recipient: String (phone number)
- message: String (max 160 chars per segment)

Security:
- Rate limited (1 SMS per 5 seconds)
- Confirmation required for unknown recipients
- No premium numbers allowed
```

**read_messages**:
```kotlin
Parameters:
- limit: Int (default: 20)
- contact: String (optional filter)
- since: String (timestamp, optional)
```

### 5. Notes Module

**Module ID**: `notes`
**Permissions**: None (uses app-private storage)

**Operations**:

**create_note**:
```kotlin
Parameters:
- title: String (required)
- content: String (required)
- tags: List<String> (optional)

Storage: ${context.filesDir}/notes/{id}.json
```

**read_notes**:
```kotlin
Parameters:
- query: String (search in title/content)
- tag: String (filter by tag)
- limit: Int (default: 20)
```

**update_note**: Modify existing note
**delete_note**: Delete note by ID

### 6. Terminal Module

**Module ID**: `terminal`
**Permissions**: None (runs as app UID)

**Operations**:

**execute**:
```kotlin
Parameters:
- command: String (shell command)
- timeout_ms: Int (default: 30000)
- working_dir: String (optional)

Security:
- Blocked commands: su, sudo, rm -rf /, dd, mkfs
- Dangerous pattern detection
- Sandboxed (runs as app, no root)
- Output truncated to 50K chars
```

**list_files**: List directory contents
**read_file**: Read file (with size limits)
**write_file**: Write to app-writable locations only

## Using Tools

### Automatic Tool Selection

The agent automatically selects tools based on user intent:

```
User: "What's on my calendar today?"
  ↓
Intent Classifier: READ_CALENDAR
  ↓
Tool Selector: calendar.read_events
  ↓
Parameter Extractor: {start_date: "today", end_date: "today"}
  ↓
Tool Executor: Execute calendar.read_events
  ↓
Response Generator: "Found 3 events: Meeting at 10am, ..."
```

### Manual Tool Invocation

For testing or direct use:

```kotlin
val toolRegistry = ToolRegistry(context)
val calendarModule = CalendarModule()
toolRegistry.register(calendarModule)

val response = calendarModule.execute(
    operationId = "read_events",
    params = mapOf(
        "start_date" to "today",
        "end_date" to "tomorrow"
    ),
    context = applicationContext
)

when (response) {
    is ToolResponse.Success -> {
        val events = response.data["events"] as List<*>
        Log.d(TAG, "Found ${events.size} events")
    }
    is ToolResponse.Error -> {
        Log.e(TAG, "Error: ${response.message}")
    }
    is ToolResponse.PermissionRequired -> {
        requestPermissions(response.permissions.toTypedArray(), REQUEST_CODE)
    }
}
```

## Creating a New Tool

Let's create a **Weather Tool** as an example.

### Step 1: Create Module Class

**File**: `/app/src/main/java/com/mazzlabs/sentinel/tools/modules/WeatherModule.kt`

```kotlin
package com.mazzlabs.sentinel.tools.modules

import android.content.Context
import android.location.LocationManager
import com.mazzlabs.sentinel.tools.framework.*

class WeatherModule : ToolModule {

    override val moduleId = "weather"

    override val description = "Get current weather and forecasts"

    override val requiredPermissions = listOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    override val operations = listOf(
        ToolOperation(
            operationId = "get_current",
            description = "Get current weather conditions",
            parameters = listOf(
                ToolParameter(
                    name = "location",
                    type = ParameterType.STRING,
                    description = "City name or 'current' for device location",
                    required = false,
                    default = "current"
                )
            ),
            examples = listOf(
                ToolExample(
                    description = "Get local weather",
                    operation = "get_current",
                    params = mapOf()
                )
            )
        ),
        ToolOperation(
            operationId = "get_forecast",
            description = "Get weather forecast",
            parameters = listOf(
                ToolParameter("location", ParameterType.STRING, "Location", required = false),
                ToolParameter("days", ParameterType.INTEGER, "Days ahead", required = false, default = 5)
            )
        )
    )

    override suspend fun execute(
        operationId: String,
        params: Map<String, Any?>,
        context: Context
    ): ToolResponse {
        return when (operationId) {
            "get_current" -> getCurrentWeather(params, context)
            "get_forecast" -> getForecast(params, context)
            else -> ToolResponse.Error(
                moduleId, operationId,
                ErrorCode.NOT_FOUND,
                "Unknown operation: $operationId"
            )
        }
    }

    private suspend fun getCurrentWeather(
        params: Map<String, Any?>,
        context: Context
    ): ToolResponse {
        val location = params["location"] as? String ?: "current"

        // Check permission
        if (!hasLocationPermission(context)) {
            return ToolResponse.PermissionRequired(
                moduleId, "get_current",
                listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }

        // Get location
        val (lat, lon) = if (location == "current") {
            getCurrentLocation(context)
        } else {
            geocodeLocation(location)
        } ?: return ToolResponse.Error(
            moduleId, "get_current",
            ErrorCode.NOT_FOUND,
            "Could not determine location"
        )

        // Fetch weather (NOTE: This would need a local weather API or cached data)
        // For privacy, we don't make network requests, so this would use:
        // 1. System weather data (if available via Android API)
        // 2. Cached data from user's weather app
        // 3. Return error if no data available

        val weatherData = getLocalWeatherData(context) ?: return ToolResponse.Error(
            moduleId, "get_current",
            ErrorCode.SYSTEM_ERROR,
            "No weather data available. Please use a weather app first."
        )

        return ToolResponse.Success(
            moduleId = moduleId,
            operationId = "get_current",
            message = "Current weather: ${weatherData["condition"]}",
            data = weatherData
        )
    }

    private fun getCurrentLocation(context: Context): Pair<Double, Double>? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        return location?.let { it.latitude to it.longitude }
    }

    private fun geocodeLocation(location: String): Pair<Double, Double>? {
        // Use Geocoder (offline) to convert city name to coordinates
        // ... implementation ...
        return null
    }

    private fun getLocalWeatherData(context: Context): Map<String, Any?>? {
        // Try to read from system weather provider or cached data
        // This maintains privacy by not making network requests
        return null
    }

    private fun hasLocationPermission(context: Context): Boolean {
        return context.checkSelfPermission(
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private suspend fun getForecast(
        params: Map<String, Any?>,
        context: Context
    ): ToolResponse {
        // Similar implementation for forecast
        return ToolResponse.Error(
            moduleId, "get_forecast",
            ErrorCode.NOT_IMPLEMENTED,
            "Forecast not yet implemented"
        )
    }
}
```

### Step 2: Register Module

**File**: `/app/src/main/java/com/mazzlabs/sentinel/tools/framework/ToolRegistry.kt`

```kotlin
class ToolRegistry(private val context: Context) {
    private val modules = mutableMapOf<String, ToolModule>()

    init {
        // Existing modules
        register(CalendarModule())
        register(ClockModule())
        register(ContactsModule())
        register(MessagingModule())
        register(NotesModule())
        register(TerminalModule())

        // NEW: Register weather module
        register(WeatherModule())
    }

    fun register(module: ToolModule) {
        modules[module.moduleId] = module
        Log.d(TAG, "Registered tool module: ${module.moduleId}")
    }

    // ... rest of implementation
}
```

### Step 3: Add Permissions to Manifest

**File**: `/app/src/main/AndroidManifest.xml`

```xml
<manifest>
    <!-- Existing permissions -->

    <!-- NEW: Weather module permissions -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
</manifest>
```

### Step 4: Update System Prompt

The system prompt builder automatically includes all registered tools, so no changes needed.

### Step 5: Test the Tool

**File**: `/app/src/test/java/com/mazzlabs/sentinel/tools/modules/WeatherModuleTest.kt`

```kotlin
package com.mazzlabs.sentinel.tools.modules

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mazzlabs.sentinel.tools.framework.ToolResponse
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.google.common.truth.Truth.assertThat

@RunWith(RobolectricTestRunner::class)
class WeatherModuleTest {

    private lateinit var module: WeatherModule
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        module = WeatherModule()
    }

    @Test
    fun `moduleId is correct`() {
        assertThat(module.moduleId).isEqualTo("weather")
    }

    @Test
    fun `operations include get_current`() {
        val operations = module.operations.map { it.operationId }
        assertThat(operations).contains("get_current")
    }

    @Test
    fun `get_current requires location permission`() = runTest {
        val response = module.execute(
            operationId = "get_current",
            params = emptyMap(),
            context = context
        )

        assertThat(response).isInstanceOf(ToolResponse.PermissionRequired::class.java)
        val permResponse = response as ToolResponse.PermissionRequired
        assertThat(permResponse.permissions).contains(
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    @Test
    fun `unknown operation returns error`() = runTest {
        val response = module.execute(
            operationId = "invalid_op",
            params = emptyMap(),
            context = context
        )

        assertThat(response).isInstanceOf(ToolResponse.Error::class.java)
    }
}
```

## Tool Testing

### Unit Testing Tools

**Test Structure**:
1. Setup module and mock context
2. Test each operation with valid/invalid inputs
3. Verify permission checks
4. Test error handling

**Example Pattern**:
```kotlin
@Test
fun `operation succeeds with valid params`() = runTest {
    // Grant necessary permissions in test
    grantPermission(android.Manifest.permission.READ_CALENDAR)

    val response = module.execute(
        operationId = "read_events",
        params = mapOf("start_date" to "today"),
        context = context
    )

    assertThat(response).isInstanceOf(ToolResponse.Success::class.java)
    val success = response as ToolResponse.Success
    assertThat(success.data).containsKey("events")
}
```

### Integration Testing

Test tools with actual agent:

```kotlin
@Test
fun `agent can call calendar tool`() = runTest {
    val query = "What's on my calendar today?"

    val result = agentController.process(query, screenContext = "")

    assertThat(result).isInstanceOf(AgentResult.ToolResult::class.java)
    val toolResult = result as AgentResult.ToolResult
    assertThat(toolResult.response).isInstanceOf(ToolResponse.Success::class.java)
}
```

### Manual Testing

```bash
# Grant permissions
adb shell pm grant com.mazzlabs.sentinel android.permission.READ_CALENDAR

# Trigger agent
# Say: "What's on my calendar?"

# Check logs
adb logcat -s ToolExecutor CalendarModule
```

## Best Practices

### Security

1. **Validate All Inputs**: Check types, ranges, formats
2. **Use Parameterized Queries**: Prevent SQL injection
3. **Check Permissions**: Always verify before accessing sensitive data
4. **Limit Results**: Cap query sizes (e.g., max 100 events)
5. **No Network Access**: Maintain privacy guarantee
6. **Sanitize Outputs**: Remove sensitive data from logs

### Performance

1. **Async Operations**: Use `suspend` functions
2. **Timeout Handling**: Set reasonable timeouts
3. **Batch Operations**: Prefer batch over multiple single operations
4. **Cache When Possible**: Reduce repeated expensive queries
5. **Resource Cleanup**: Close cursors, recycle bitmaps

### Error Handling

1. **Specific Error Codes**: Use appropriate `ErrorCode` enum
2. **Descriptive Messages**: Help user understand what went wrong
3. **Graceful Degradation**: Return partial results if possible
4. **Permission Prompts**: Guide user to grant needed permissions

### Documentation

1. **Document Parameters**: Clear types and requirements
2. **Provide Examples**: Include realistic use cases
3. **Explain Limitations**: Document what the tool cannot do
4. **Security Notes**: Highlight any security considerations

### Code Organization

```
/tools/
  /framework/
    ToolModule.kt          # Base interface
    ToolExecutor.kt        # Execution coordinator
    ToolRegistry.kt        # Module registration
    ToolResponse.kt        # Response types
  /modules/
    CalendarModule.kt      # Calendar operations
    ContactsModule.kt      # Contact operations
    WeatherModule.kt       # NEW: Your module
```

---

**Next Steps**:
- Review existing tools in `/app/src/main/java/com/mazzlabs/sentinel/tools/modules/`
- Check tests in `/app/src/test/java/com/mazzlabs/sentinel/tools/modules/`
- See [Development Guide](DEVELOPMENT.md) for general development practices
