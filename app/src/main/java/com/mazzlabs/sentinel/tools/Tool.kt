package com.mazzlabs.sentinel.tools

import android.content.Context

/**
 * Tool - Base interface for agent tools
 * 
 * Tools are capabilities the agent can invoke deterministically.
 * Each tool has a schema (for LLM to understand) and execute function.
 */
interface Tool {
    /** Unique tool identifier */
    val name: String
    
    /** Human-readable description for the LLM */
    val description: String
    
    /** Parameter schema as JSON string */
    val parametersSchema: String
    
    /** Required permissions for this tool */
    val requiredPermissions: List<String>
    
    /**
     * Execute the tool with given parameters
     * @param params Key-value parameters extracted by the LLM
     * @param context Android context for system access
     * @return ToolResult with success/failure and data
     */
    suspend fun execute(params: Map<String, Any?>, context: Context): ToolResult
    
    /**
     * Validate parameters before execution
     */
    fun validateParams(params: Map<String, Any?>): ValidationResult {
        return ValidationResult.Valid
    }
}

/**
 * Result of tool execution
 */
sealed class ToolResult {
    abstract val toolName: String
    abstract val message: String
    
    data class Success(
        override val toolName: String,
        override val message: String,
        val data: Map<String, Any?> = emptyMap()
    ) : ToolResult()
    
    data class Failure(
        override val toolName: String,
        override val message: String,
        val errorCode: ErrorCode = ErrorCode.UNKNOWN
    ) : ToolResult()
    
    fun isSuccess(): Boolean = this is Success
}

enum class ErrorCode {
    UNKNOWN,
    PERMISSION_DENIED,
    INVALID_PARAMS,
    NOT_FOUND,
    TIMEOUT,
    SYSTEM_ERROR
}

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}

/**
 * ToolRegistry - Central registry for all available tools
 */
class ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()
    
    fun register(tool: Tool) {
        tools[tool.name] = tool
    }
    
    fun get(name: String): Tool? = tools[name]
    
    fun getAll(): List<Tool> = tools.values.toList()
    
    /**
     * Generate tools description for LLM prompt
     */
    fun generateToolsPrompt(): String {
        if (tools.isEmpty()) return ""
        
        return buildString {
            appendLine("Available tools:")
            tools.values.forEach { tool ->
                appendLine("- ${tool.name}: ${tool.description}")
                appendLine("  Parameters: ${tool.parametersSchema}")
            }
        }
    }
    
    /**
     * Check if all permissions for a tool are granted
     */
    fun hasPermissions(context: Context, toolName: String): Boolean {
        val tool = tools[toolName] ?: return false
        return tool.requiredPermissions.all { permission ->
            context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}
