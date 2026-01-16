package com.mazzlabs.sentinel.tools.framework

import android.content.Context

/**
 * ToolModule - Base interface for all tool modules
 * 
 * Each module represents a system capability (Calendar, Contacts, etc.)
 * with multiple operations. The model sees the schema and calls operations.
 */
interface ToolModule {
    /** Module identifier (e.g., "calendar", "clock", "contacts") */
    val moduleId: String
    
    /** Human-readable description for the model */
    val description: String
    
    /** List of operations this module supports */
    val operations: List<ToolOperation>
    
    /** Android permissions required by this module */
    val requiredPermissions: List<String>
    
    /** Check if module is available on this device */
    fun isAvailable(context: Context): Boolean = true
    
    /** Execute an operation by name */
    suspend fun execute(
        operationId: String,
        params: Map<String, Any?>,
        context: Context
    ): ToolResponse
}

/**
 * ToolOperation - A single operation within a module
 */
data class ToolOperation(
    /** Operation identifier (e.g., "create_event", "read_events") */
    val operationId: String,
    
    /** Description for the model to understand when to use this */
    val description: String,
    
    /** JSON Schema for parameters */
    val parameters: List<ToolParameter>,
    
    /** Example usage for few-shot prompting */
    val examples: List<ToolExample> = emptyList()
)

/**
 * ToolParameter - Schema for an operation parameter
 */
data class ToolParameter(
    val name: String,
    val type: ParameterType,
    val description: String,
    val required: Boolean = true,
    val enumValues: List<String>? = null,
    val default: Any? = null
)

enum class ParameterType {
    STRING,
    INTEGER,
    FLOAT,
    BOOLEAN,
    DATE,      // ISO 8601 date
    TIME,      // ISO 8601 time  
    DATETIME,  // ISO 8601 datetime
    DURATION,  // ISO 8601 duration (PT1H30M)
    ARRAY,
    OBJECT
}

/**
 * ToolExample - Example for few-shot prompting
 */
data class ToolExample(
    val userQuery: String,
    val operationId: String,
    val params: Map<String, Any?>
)

/**
 * ToolResponse - Result from executing a tool operation
 */
sealed class ToolResponse {
    abstract val moduleId: String
    abstract val operationId: String
    
    data class Success(
        override val moduleId: String,
        override val operationId: String,
        val message: String,
        val data: Map<String, Any?> = emptyMap()
    ) : ToolResponse()
    
    data class Error(
        override val moduleId: String,
        override val operationId: String,
        val errorCode: ErrorCode,
        val message: String
    ) : ToolResponse()
    
    data class PermissionRequired(
        override val moduleId: String,
        override val operationId: String,
        val permissions: List<String>
    ) : ToolResponse()
    
    data class Confirmation(
        override val moduleId: String,
        override val operationId: String,
        val message: String,
        val pendingAction: Map<String, Any?>
    ) : ToolResponse()
}

enum class ErrorCode {
    INVALID_PARAMS,
    NOT_FOUND,
    PERMISSION_DENIED,
    NOT_AVAILABLE,
    SYSTEM_ERROR,
    TIMEOUT,
    CANCELLED
}

/**
 * Generate schema description for the model
 */
fun ToolModule.toSchemaString(): String {
    val sb = StringBuilder()
    sb.appendLine("## $moduleId")
    sb.appendLine(description)
    sb.appendLine()
    sb.appendLine("Operations:")
    
    for (op in operations) {
        sb.appendLine("  - ${op.operationId}: ${op.description}")
        sb.appendLine("    Parameters:")
        for (param in op.parameters) {
            val req = if (param.required) "required" else "optional"
            sb.appendLine("      * ${param.name} (${param.type.name.lowercase()}, $req): ${param.description}")
        }
        if (op.examples.isNotEmpty()) {
            sb.appendLine("    Examples:")
            for (ex in op.examples) {
                sb.appendLine("      \"${ex.userQuery}\" -> ${ex.operationId}(${ex.params})")
            }
        }
        sb.appendLine()
    }
    
    return sb.toString()
}
