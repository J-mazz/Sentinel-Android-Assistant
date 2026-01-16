package com.mazzlabs.sentinel.model

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName

/**
 * Action types supported by the agent
 * Defined in GBNF grammar for strict enforcement
 */
enum class ActionType {
    @SerializedName("CLICK") CLICK,
    @SerializedName("SCROLL") SCROLL,
    @SerializedName("TYPE") TYPE,
    @SerializedName("HOME") HOME,
    @SerializedName("BACK") BACK,
    @SerializedName("WAIT") WAIT,
    @SerializedName("NONE") NONE
}

/**
 * Scroll direction for SCROLL actions
 */
enum class ScrollDirection {
    @SerializedName("UP") UP,
    @SerializedName("DOWN") DOWN,
    @SerializedName("LEFT") LEFT,
    @SerializedName("RIGHT") RIGHT
}

/**
 * Agent Action - The output structure from inference
 * Strictly enforced by GBNF grammar at the native layer
 */
data class AgentAction(
    @SerializedName("action")
    val action: ActionType,
    
    @SerializedName("target")
    val target: String? = null,
    
    @SerializedName("text")
    val text: String? = null,
    
    @SerializedName("direction")
    val direction: ScrollDirection? = null,
    
    @SerializedName("confidence")
    val confidence: Float = 0.0f,
    
    @SerializedName("reasoning")
    val reasoning: String? = null
) {
    companion object {
        private val gson = Gson()
        
        /**
         * Parse JSON string to AgentAction
         * @throws JsonSyntaxException if parsing fails
         */
        fun fromJson(json: String): AgentAction {
            return gson.fromJson(json, AgentAction::class.java)
        }
        
        /**
         * Safe parse that returns null on failure
         * Attempts to repair common JSON issues before parsing
         */
        fun fromJsonOrNull(json: String): AgentAction? {
            return try {
                fromJson(repairJson(json))
            } catch (e: JsonSyntaxException) {
                // Try extracting JSON from response if wrapped in text
                try {
                    val extracted = extractJson(json)
                    if (extracted != null) {
                        fromJson(repairJson(extracted))
                    } else null
                } catch (e2: Exception) {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
        
        /**
         * Repair common JSON formatting issues from LLM output
         */
        private fun repairJson(json: String): String {
            var fixed = json.trim()
            
            // Remove markdown code blocks if present
            fixed = fixed.replace(Regex("^```json\\s*"), "")
            fixed = fixed.replace(Regex("^```\\s*"), "")
            fixed = fixed.replace(Regex("\\s*```$"), "")
            
            // Fix missing quotes on string values: "key": value, -> "key": "value",
            fixed = fixed.replace(Regex(""":\s*([a-zA-Z][a-zA-Z0-9_]*)\s*(,|\})""")) { match ->
                val value = match.groupValues[1]
                val ending = match.groupValues[2]
                // Don't quote if it's a boolean or null
                if (value in listOf("true", "false", "null")) {
                    ": $value$ending"
                } else {
                    ": \"$value\"$ending"
                }
            }
            
            // Fix unclosed string values: "key": "value, -> "key": "value",
            fixed = fixed.replace(Regex(""":\s*"([^"]*)(,|\n|\})""")) { match ->
                val value = match.groupValues[1]
                val ending = match.groupValues[2]
                if (ending == ",") {
                    ": \"$value\"$ending"
                } else if (ending == "}") {
                    ": \"$value\"$ending"
                } else {
                    ": \"$value\",$ending"
                }
            }
            
            // Fix trailing commas before closing brace
            fixed = fixed.replace(Regex(",\\s*}"), "}")
            
            // Ensure proper closing
            val openBraces = fixed.count { it == '{' }
            val closeBraces = fixed.count { it == '}' }
            if (openBraces > closeBraces) {
                fixed += "}".repeat(openBraces - closeBraces)
            }
            
            return fixed
        }
        
        /**
         * Extract JSON object from text that may contain other content
         */
        private fun extractJson(text: String): String? {
            val start = text.indexOf('{')
            if (start == -1) return null
            
            var depth = 0
            var end = start
            for (i in start until text.length) {
                when (text[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            end = i
                            break
                        }
                    }
                }
            }
            
            return if (end > start) text.substring(start, end + 1) else null
        }
        
        /**
         * Create a NONE action (no operation)
         */
        fun none(reasoning: String = "No action required"): AgentAction {
            return AgentAction(
                action = ActionType.NONE,
                reasoning = reasoning
            )
        }
    }
    
    fun toJson(): String = gson.toJson(this)
    
    /**
     * Check if this action requires user confirmation
     */
    fun requiresConfirmation(): Boolean {
        return when (action) {
            ActionType.TYPE -> text?.let { 
                containsDangerousContent(it) 
            } ?: false
            ActionType.CLICK -> target?.let { 
                isDangerousTarget(it) 
            } ?: false
            else -> false
        }
    }
    
    private fun containsDangerousContent(content: String): Boolean {
        val dangerousPatterns = listOf(
            "password", "credit card", "ssn", "social security",
            "bank", "account number", "pin"
        )
        val lowerContent = content.lowercase()
        return dangerousPatterns.any { lowerContent.contains(it) }
    }
    
    private fun isDangerousTarget(targetText: String): Boolean {
        val dangerousTargets = listOf(
            "delete", "remove", "uninstall", "erase", "wipe",
            "send", "submit", "confirm purchase", "pay", "transfer",
            "format", "reset", "factory reset"
        )
        val lowerTarget = targetText.lowercase()
        return dangerousTargets.any { lowerTarget.contains(it) }
    }
}

/**
 * UI Element representation for context building
 */
data class UIElement(
    val type: String,
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val bounds: String?
) {
    override fun toString(): String {
        val label = text ?: contentDescription ?: viewId ?: "unknown"
        val attributes = mutableListOf<String>()
        if (isClickable) attributes.add("clickable")
        if (isEditable) attributes.add("editable")
        val attrStr = if (attributes.isNotEmpty()) "(${attributes.joinToString(",")})" else ""
        return "$type[$label]$attrStr"
    }
}

/**
 * Screen context for inference
 */
data class ScreenContext(
    val packageName: String,
    val activityName: String?,
    val elements: List<UIElement>
) {
    fun flatten(): String {
        val header = "App: $packageName${activityName?.let { " | Activity: $it" } ?: ""}\n"
        val elementsStr = elements.joinToString(" | ") { it.toString() }
        return header + elementsStr
    }
}
