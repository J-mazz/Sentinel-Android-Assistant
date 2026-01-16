package com.mazzlabs.sentinel.core

import android.content.Context
import com.mazzlabs.sentinel.tools.framework.Tools
import java.text.SimpleDateFormat
import java.util.*

/**
 * SystemPromptBuilder - Constructs system prompts with tool schemas
 * 
 * Generates context-aware prompts that include:
 * - Current date/time
 * - Available tools and their schemas
 * - Output format instructions
 */
object SystemPromptBuilder {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    private val dayFormat = SimpleDateFormat("EEEE", Locale.US)
    
    /**
     * Build full system prompt with tools
     */
    fun build(context: Context, includeTools: Boolean = true): String {
        val now = Date()
        val toolExecutor = Tools.getInstance(context)
        
        return buildString {
            appendLine("You are Sentinel, an intelligent Android assistant.")
            appendLine()
            appendLine("## Current Context")
            appendLine("- Date: ${dateFormat.format(now)} (${dayFormat.format(now)})")
            appendLine("- Time: ${timeFormat.format(now)}")
            appendLine("- Timezone: ${TimeZone.getDefault().id}")
            appendLine()
            
            if (includeTools) {
                appendLine("## Instructions")
                appendLine("1. Analyze the user's request to determine their intent")
                appendLine("2. If a tool can help, use it by outputting a tool call")
                appendLine("3. If no tool applies, output a UI action or helpful response")
                appendLine()
                appendLine("## Output Format")
                appendLine("For tool calls, output ONLY valid JSON:")
                appendLine("```")
                appendLine("{\"tool\": \"module.operation\", \"params\": {...}}")
                appendLine("```")
                appendLine()
                appendLine("For UI actions (when no tool applies):")
                appendLine("```")
                appendLine("{\"action\": \"tap|type|scroll|back|home|none\", \"target\": \"...\", \"reasoning\": \"...\"}")
                appendLine("```")
                appendLine()
                append(toolExecutor.getToolSchema(compact = false))
            } else {
                appendLine("## Output Format")
                appendLine("Output ONLY valid JSON with an action:")
                appendLine("```")
                appendLine("{\"action\": \"tap|type|scroll|back|home|none\", \"target\": \"...\", \"text\": \"...\", \"reasoning\": \"...\"}")
                appendLine("```")
            }
        }
    }
    
    /**
     * Build compact prompt for limited context windows
     */
    fun buildCompact(context: Context): String {
        val now = Date()
        val toolExecutor = Tools.getInstance(context)
        
        return buildString {
            appendLine("Sentinel Android assistant. Date: ${dateFormat.format(now)} ${timeFormat.format(now)}")
            appendLine()
            appendLine("Output JSON: {\"tool\": \"module.operation\", \"params\": {...}} OR {\"action\": \"...\", \"target\": \"...\"}")
            appendLine()
            append(toolExecutor.getToolSchema(compact = true))
        }
    }
    
    /**
     * Build tool-only prompt for tool selection
     */
    fun buildToolPrompt(context: Context, userQuery: String): String {
        val now = Date()
        val toolExecutor = Tools.getInstance(context)
        
        return buildString {
            appendLine("Select the best tool for this request. Current time: ${timeFormat.format(now)}, Date: ${dateFormat.format(now)}")
            appendLine()
            appendLine(toolExecutor.getToolSchema(compact = true))
            appendLine()
            appendLine("User request: $userQuery")
            appendLine()
            appendLine("Output ONLY JSON: {\"tool\": \"module.operation\", \"params\": {...}}")
            appendLine("If no tool applies, output: {\"tool\": \"none\", \"reason\": \"...\"}")
        }
    }
}
