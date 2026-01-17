package com.mazzlabs.sentinel.graph

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * SessionManager - persists AgentState between turns.
 */
class SessionManager(private val context: Context) {

    companion object {
        private const val TAG = "SessionManager"
        private const val SESSION_FILE_NAME = "agent_sessions.json"
        private const val MAX_SESSIONS = 20
        private const val MAX_HISTORY_PER_SESSION = 50
        private const val MAX_FILE_BYTES = 2 * 1024 * 1024
    }

    private val gson = Gson()
    private val sessions = mutableMapOf<String, AgentState>()
    private val sessionFile = File(context.filesDir, SESSION_FILE_NAME)

    init {
        loadAllSessions()
    }

    fun getOrCreateSession(sessionId: String): AgentState {
        return sessions[sessionId] ?: createNewSession(sessionId)
    }

    fun updateSession(state: AgentState) {
        sessions[state.sessionId] = trimHistory(state)
        persistAllSessions()
    }

    private fun createNewSession(sessionId: String): AgentState {
        val state = AgentState(sessionId = sessionId)
        sessions[sessionId] = state
        persistAllSessions()
        return state
    }

    private fun trimHistory(state: AgentState): AgentState {
        val trimmedHistory = if (state.conversationHistory.size > MAX_HISTORY_PER_SESSION) {
            state.conversationHistory.takeLast(MAX_HISTORY_PER_SESSION)
        } else {
            state.conversationHistory
        }

        return state.copy(conversationHistory = trimmedHistory)
    }

    private fun loadAllSessions() {
        if (!sessionFile.exists()) return

        try {
            val content = sessionFile.readText()
            if (content.isBlank()) return

            val type = object : TypeToken<Map<String, AgentState>>() {}.type
            val loaded: Map<String, AgentState> = gson.fromJson(content, type) ?: emptyMap()
            sessions.clear()
            sessions.putAll(loaded)
            pruneSessions()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load sessions", e)
        }
    }

    private fun persistAllSessions() {
        try {
            pruneSessions()
            var json = gson.toJson(sessions)

            while (json.toByteArray().size > MAX_FILE_BYTES && sessions.size > 1) {
                dropOldestSession()
                json = gson.toJson(sessions)
            }

            if (json.toByteArray().size > MAX_FILE_BYTES) {
                reduceHistoryFurther()
                json = gson.toJson(sessions)
            }

            sessionFile.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist sessions", e)
        }
    }

    private fun pruneSessions() {
        // Trim history for each session
        sessions.keys.toList().forEach { key ->
            sessions[key]?.let { sessions[key] = trimHistory(it) }
        }

        // Enforce max sessions by last activity
        if (sessions.size <= MAX_SESSIONS) return

        val ordered = sessions.values.sortedBy { state ->
            state.conversationHistory.lastOrNull()?.timestamp ?: 0L
        }

        val toDrop = ordered.take(sessions.size - MAX_SESSIONS).map { it.sessionId }
        toDrop.forEach { sessions.remove(it) }
    }

    private fun dropOldestSession() {
        val oldest = sessions.values.minByOrNull { state ->
            state.conversationHistory.lastOrNull()?.timestamp ?: 0L
        }
        oldest?.let { sessions.remove(it.sessionId) }
    }

    private fun reduceHistoryFurther() {
        sessions.keys.toList().forEach { key ->
            val state = sessions[key] ?: return@forEach
            val reduced = state.conversationHistory.takeLast(MAX_HISTORY_PER_SESSION / 2)
            sessions[key] = state.copy(conversationHistory = reduced)
        }
    }
}
