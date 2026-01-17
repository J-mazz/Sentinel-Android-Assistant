package com.mazzlabs.sentinel.graph.nodes

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mazzlabs.sentinel.SentinelApplication
import com.mazzlabs.sentinel.core.GrammarManager
import com.mazzlabs.sentinel.core.JsonExtractor
import com.mazzlabs.sentinel.graph.AgentIntent
import com.mazzlabs.sentinel.graph.AgentNode
import com.mazzlabs.sentinel.graph.AgentState
import com.mazzlabs.sentinel.graph.Plan
import com.mazzlabs.sentinel.graph.PlanStep
import com.mazzlabs.sentinel.graph.Role
import com.mazzlabs.sentinel.graph.Message
import com.mazzlabs.sentinel.model.UIElement
import com.mazzlabs.sentinel.tools.ToolResult
import org.json.JSONObject

class IntentParserNode : AgentNode {

    companion object {
        private const val TAG = "IntentParserNode"
    }

    override suspend fun process(state: AgentState): AgentState {
        val prompt = buildIntentPrompt(state)

        return try {
            val grammar = GrammarManager.getGrammarPath("intent.gbnf")
            val response = SentinelApplication.getInstance()
                .nativeBridge
                .inferWithGrammar(prompt, "", grammar)

            val parsed = parseIntentResponse(response)
            state.copy(
                intent = parsed.intent,
                confidence = parsed.confidence,
                extractedEntities = parsed.entities,
                currentNode = "intent_parser"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Intent parsing failed", e)
            state.copy(
                intent = AgentIntent.UNKNOWN,
                confidence = 0f,
                error = "Intent parsing failed: ${e.message}",
                currentNode = "intent_parser"
            )
        }
    }

    private fun buildIntentPrompt(state: AgentState): String {
        val history = state.conversationHistory
            .takeLast(3)
            .joinToString("\n") { "${it.role}: ${it.content}" }

        val screenContext = state.screenContext.take(4000)

        return """
Classify the user's intent with confidence score.

Conversation history:
$history

Screen context (may include element_id list):
$screenContext

Current query: "${state.userQuery}"

Respond with JSON:
{
    "intent": "READ_CALENDAR|CREATE_EVENT|UPDATE_EVENT|DELETE_EVENT|CREATE_ALARM|LIST_ALARMS|DELETE_ALARM|CALL_CONTACT|SEND_SMS|CLICK_ELEMENT|SCROLL_SCREEN|TYPE_TEXT|GO_BACK|GO_HOME|SEARCH|ANSWER_QUESTION|SEARCH_SELECTED|TRANSLATE_SELECTED|COPY_SELECTED|SAVE_SELECTED|SHARE_SELECTED|EXTRACT_DATA_FROM_SELECTION|UNKNOWN",
  "confidence": 0.0,
  "entities": {"key": "value"},
  "reasoning": "brief explanation"
}
""".trimIndent()
    }

    private data class IntentParseResult(
        val intent: AgentIntent,
        val confidence: Float,
        val entities: Map<String, String>
    )

    private fun parseIntentResponse(response: String): IntentParseResult {
        return try {
            val extraction = JsonExtractor.extract(response)
            val json = when (extraction) {
                is JsonExtractor.ExtractionResult.Success -> extraction.json
                is JsonExtractor.ExtractionResult.ArraySuccess -> extraction.json.optJSONObject(0) ?: JSONObject()
                is JsonExtractor.ExtractionResult.Failure -> JSONObject()
            }

            val intentStr = json.optString("intent", "UNKNOWN").uppercase()
            val intent = try {
                AgentIntent.valueOf(intentStr)
            } catch (_: Exception) {
                AgentIntent.UNKNOWN
            }

            val confidence = json.optDouble("confidence", 0.5).toFloat().coerceIn(0f, 1f)

            val entitiesObj = json.optJSONObject("entities")
            val entities = mutableMapOf<String, String>()
            entitiesObj?.keys()?.forEach { key ->
                if (entities.size < 20) {
                    entities[key] = entitiesObj.optString(key)
                }
            }

            IntentParseResult(intent, confidence, entities)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse intent response", e)
            IntentParseResult(AgentIntent.UNKNOWN, 0.5f, emptyMap())
        }
    }
}

class EntityExtractorNode : AgentNode {

    companion object {
        private const val TAG = "EntityExtractorNode"
    }

    override suspend fun process(state: AgentState): AgentState {
        if (state.extractedEntities.isNotEmpty()) {
            return state.copy(currentNode = "entity_extractor")
        }

        val prompt = buildEntityPrompt(state)

        return try {
            val grammar = GrammarManager.getGrammarPath("entities.gbnf")
            val response = SentinelApplication.getInstance()
                .nativeBridge
                .inferWithGrammar(prompt, "", grammar)

            val entities = parseEntities(response)
            state.copy(
                extractedEntities = entities,
                currentNode = "entity_extractor"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Entity extraction failed", e)
            state.copy(error = "Entity extraction failed: ${e.message}")
        }
    }

    private fun buildEntityPrompt(state: AgentState): String {
        val uiHint = when (state.intent) {
            AgentIntent.CLICK_ELEMENT,
            AgentIntent.TYPE_TEXT,
            AgentIntent.SCROLL_SCREEN ->
                "If a UI element is needed, include its element_id from the screen context list."
            else -> ""
        }

        return """
Extract relevant entities for intent: ${state.intent}.

User query: "${state.userQuery}"

Screen context (may include element_id list):
${state.screenContext.take(4000)}

$uiHint

Respond with JSON:
{
  "entities": {"key": "value"}
}
""".trimIndent()
    }

    private fun parseEntities(response: String): Map<String, String> {
        return try {
            val extraction = JsonExtractor.extract(response)
            val json = when (extraction) {
                is JsonExtractor.ExtractionResult.Success -> extraction.json
                is JsonExtractor.ExtractionResult.ArraySuccess -> extraction.json.optJSONObject(0) ?: JSONObject()
                is JsonExtractor.ExtractionResult.Failure -> JSONObject()
            }

            val entitiesObj = json.optJSONObject("entities") ?: json
            val entities = mutableMapOf<String, String>()
            entitiesObj.keys().forEach { key ->
                if (entities.size < 20) {
                    entities[key] = entitiesObj.optString(key)
                }
            }
            entities
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse entities", e)
            emptyMap()
        }
    }
}

class ContextAnalyzerNode : AgentNode {

    override suspend fun process(state: AgentState): AgentState {
        val needsScreen = when (state.intent) {
            AgentIntent.CLICK_ELEMENT,
            AgentIntent.SCROLL_SCREEN,
            AgentIntent.TYPE_TEXT -> true
            else -> false
        }

        if (!needsScreen) {
            return state.copy(currentNode = "context_analyzer")
        }

        val focusedElement = extractFocusedElement(state.screenContext)

        return state.copy(
            focusedElement = focusedElement,
            currentNode = "context_analyzer"
        )
    }

    private fun extractFocusedElement(screenContext: String): UIElement? {
        if (screenContext.isBlank()) return null

        val elements = screenContext.split(" | ")
            .mapNotNull { parseElement(it) }

        return elements.firstOrNull { it.isEditable } ?: 
            elements.firstOrNull { it.isClickable } ?: 
            elements.firstOrNull()
    }

    private fun parseElement(raw: String): UIElement? {
        val pattern = Regex("""([^\[]+)\[([^]]+)](\(([^)]+)\))?""")
        val match = pattern.find(raw.trim()) ?: return null

        val type = match.groupValues[1].trim()
        val label = match.groupValues[2].trim()
        val attrs = match.groupValues.getOrNull(4)?.split(",")?.map { it.trim() } ?: emptyList()

        val isClickable = attrs.any { it.equals("clickable", true) }
        val isEditable = attrs.any { it.equals("editable", true) }

        return UIElement(
            type = type,
            text = label,
            contentDescription = null,
            viewId = null,
            isClickable = isClickable,
            isEditable = isEditable,
            bounds = null
        )
    }
}

class PlanGeneratorNode : AgentNode {

    companion object {
        private const val TAG = "PlanGeneratorNode"
    }

    override suspend fun process(state: AgentState): AgentState {
        val prompt = """
Create a step-by-step plan for: "${state.userQuery}"

Available tools: calendar, contacts, clock, messaging, notes

Respond with JSON:
{
  "needs_plan": true,
  "steps": [
    {"description": "...", "intent": "...", "tool": "...", "required_entities": ["..."]}
  ]
}
""".trimIndent()

        return try {
            val grammar = GrammarManager.getGrammarPath("plan.gbnf")
            val response = SentinelApplication.getInstance()
                .nativeBridge
                .inferWithGrammar(prompt, "", grammar)

            val plan = parsePlanResponse(response, state.userQuery)
            val validatedPlan = plan?.let { validatePlan(it) }
            state.copy(plan = validatedPlan, currentNode = "plan_generator")
        } catch (e: Exception) {
            Log.e(TAG, "Plan generation failed", e)
            state.copy(error = "Plan generation failed: ${e.message}")
        }
    }

    private fun parsePlanResponse(response: String, goal: String): Plan? {
        return try {
            val extraction = JsonExtractor.extract(response)
            val json = when (extraction) {
                is JsonExtractor.ExtractionResult.Success -> extraction.json
                is JsonExtractor.ExtractionResult.ArraySuccess -> extraction.json.optJSONObject(0) ?: JSONObject()
                is JsonExtractor.ExtractionResult.Failure -> JSONObject()
            }

            val needsPlan = json.optBoolean("needs_plan", false)
            if (!needsPlan) return null

            val stepsJson = json.optJSONArray("steps") ?: return null
            val steps = mutableListOf<PlanStep>()

            for (i in 0 until stepsJson.length()) {
                val stepObj = stepsJson.optJSONObject(i) ?: continue
                val desc = stepObj.optString("description")
                if (desc.isBlank()) continue
                val intentStr = stepObj.optString("intent", "UNKNOWN").uppercase()
                val intent = try {
                    AgentIntent.valueOf(intentStr)
                } catch (_: Exception) {
                    AgentIntent.UNKNOWN
                }
                val tool = stepObj.optString("tool", null)
                val requiredEntities = mutableListOf<String>()
                val reqArray = stepObj.optJSONArray("required_entities")
                if (reqArray != null) {
                    for (j in 0 until reqArray.length()) {
                        requiredEntities.add(reqArray.optString(j))
                    }
                }
                steps.add(PlanStep(desc, intent, requiredEntities, tool))
                if (steps.size >= 10) break
            }

            if (steps.isEmpty()) null else Plan(goal = goal, steps = steps)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse plan", e)
            null
        }
    }
}
    private fun validatePlan(plan: Plan): Plan? {
        val validSteps = plan.steps.filter { step ->
            step.intent != AgentIntent.UNKNOWN && step.description.isNotBlank()
        }
        return if (validSteps.isEmpty()) null else plan.copy(steps = validSteps)
    }

class PlanExecutorNode : AgentNode {
    override suspend fun process(state: AgentState): AgentState {
        val plan = state.plan ?: return state.copy(error = "No plan to execute")

        if (plan.currentStepIndex >= plan.steps.size) {
            return state.copy(plan = null, currentNode = "plan_executor")
        }

        val currentStep = plan.steps[plan.currentStepIndex]

        return state.copy(
            intent = currentStep.intent,
            selectedTool = currentStep.toolCall,
            plan = plan.copy(currentStepIndex = plan.currentStepIndex + 1),
            currentNode = "plan_executor"
        )
    }
}

class ClarificationNode : AgentNode {
    override suspend fun process(state: AgentState): AgentState {
        val clarificationQuestion = generateClarification(state)

        return state.copy(
            response = clarificationQuestion,
            needsUserInput = true,
            isComplete = true,
            currentNode = "clarification_handler"
        )
    }

    private fun generateClarification(state: AgentState): String {
        return when {
            state.intent == AgentIntent.UNKNOWN ->
                "I'm not sure what you want me to do. Could you rephrase that?"
            state.extractedEntities.isEmpty() ->
                "I understood you want to ${state.intent}, but I need more details. Can you provide more information?"
            else ->
                "Did you mean to ${state.intent}?"
        }
    }
}

class EnhancedResponseGeneratorNode : AgentNode {
    override suspend fun process(state: AgentState): AgentState {
        val response = when {
            state.plan != null && state.plan.currentStepIndex < state.plan.steps.size -> {
                "Completed step ${state.plan.currentStepIndex} of ${state.plan.steps.size}. Continuing..."
            }
            state.toolResults.isNotEmpty() -> {
                formatToolResults(state.toolResults.last())
            }
            state.action != null -> {
                "Performing ${state.action.action}: ${state.action.reasoning ?: ""}"
            }
            else -> "I've processed your request."
        }

        return state.copy(
            response = response,
            isComplete = true,
            currentNode = "response_generator"
        )
    }

    private fun formatToolResults(result: ToolResult): String {
        return when (result) {
            is ToolResult.Success -> result.message
            is ToolResult.Failure -> "Error: ${result.message}"
        }
    }
}
