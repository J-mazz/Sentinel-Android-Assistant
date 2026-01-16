package com.mazzlabs.sentinel.tools.modules

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import com.mazzlabs.sentinel.tools.framework.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * NotesModule - Local notes/scratchpad storage
 * 
 * Uses app-internal storage for quick notes that don't need a full document app.
 * 
 * Operations:
 * - create_note: Create a new note
 * - read_note: Read an existing note
 * - list_notes: List all notes
 * - append_note: Add to an existing note
 * - delete_note: Delete a note
 * - search_notes: Search notes by content
 */
class NotesModule : ToolModule {
    
    companion object {
        private const val TAG = "NotesModule"
        private const val NOTES_DIR = "sentinel_notes"
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    }
    
    override val moduleId = "notes"
    
    override val description = "Quick notes and scratchpad - create, read, search local notes"
    
    override val requiredPermissions = listOf<String>() // Uses app-internal storage
    
    override val operations = listOf(
        ToolOperation(
            operationId = "create_note",
            description = "Create a new note",
            parameters = listOf(
                ToolParameter("title", ParameterType.STRING, "Note title (used as filename)", required = true),
                ToolParameter("content", ParameterType.STRING, "Note content", required = true),
                ToolParameter("tags", ParameterType.ARRAY, "Optional tags for organization", required = false)
            ),
            examples = listOf(
                ToolExample("Make a note about grocery list", "create_note", mapOf("title" to "Grocery List", "content" to "Milk, bread, eggs")),
                ToolExample("Save this idea", "create_note", mapOf("title" to "Ideas", "content" to "Build an AI assistant"))
            )
        ),
        ToolOperation(
            operationId = "read_note",
            description = "Read a note by title",
            parameters = listOf(
                ToolParameter("title", ParameterType.STRING, "Note title", required = true)
            )
        ),
        ToolOperation(
            operationId = "list_notes",
            description = "List all notes",
            parameters = listOf(
                ToolParameter("max_results", ParameterType.INTEGER, "Maximum notes to return", required = false, default = 50)
            )
        ),
        ToolOperation(
            operationId = "append_note",
            description = "Append content to an existing note",
            parameters = listOf(
                ToolParameter("title", ParameterType.STRING, "Note title", required = true),
                ToolParameter("content", ParameterType.STRING, "Content to append", required = true)
            )
        ),
        ToolOperation(
            operationId = "delete_note",
            description = "Delete a note",
            parameters = listOf(
                ToolParameter("title", ParameterType.STRING, "Note title to delete", required = true)
            )
        ),
        ToolOperation(
            operationId = "search_notes",
            description = "Search notes by content",
            parameters = listOf(
                ToolParameter("query", ParameterType.STRING, "Search term", required = true)
            )
        )
    )
    
    override suspend fun execute(
        operationId: String,
        params: Map<String, Any?>,
        context: Context
    ): ToolResponse {
        return when (operationId) {
            "create_note" -> createNote(params, context)
            "read_note" -> readNote(params, context)
            "list_notes" -> listNotes(params, context)
            "append_note" -> appendNote(params, context)
            "delete_note" -> deleteNote(params, context)
            "search_notes" -> searchNotes(params, context)
            else -> ToolResponse.Error(moduleId, operationId, ErrorCode.NOT_FOUND, "Unknown operation: $operationId")
        }
    }
    
    private fun getNotesDir(context: Context): File {
        val dir = File(context.filesDir, NOTES_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    private fun sanitizeFilename(title: String): String {
        return title.replace(Regex("[^a-zA-Z0-9\\s\\-_]"), "")
            .trim()
            .take(50)
            .ifEmpty { "untitled" }
    }
    
    private fun createNote(params: Map<String, Any?>, context: Context): ToolResponse {
        val title = params["title"] as? String
            ?: return ToolResponse.Error(moduleId, "create_note", ErrorCode.INVALID_PARAMS, "title required")
        val content = params["content"] as? String
            ?: return ToolResponse.Error(moduleId, "create_note", ErrorCode.INVALID_PARAMS, "content required")
        
        @Suppress("UNCHECKED_CAST")
        val tags = (params["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        
        try {
            val filename = sanitizeFilename(title) + ".txt"
            val file = File(getNotesDir(context), filename)
            
            val metadata = buildString {
                appendLine("# $title")
                appendLine("Created: ${dateFormat.format(Date())}")
                if (tags.isNotEmpty()) {
                    appendLine("Tags: ${tags.joinToString(", ")}")
                }
                appendLine()
                appendLine("---")
                appendLine()
            }
            
            file.writeText(metadata + content)
            
            Log.i(TAG, "Created note: $filename")
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "create_note",
                message = "Note '$title' created",
                data = mapOf("title" to title, "filename" to filename, "size" to file.length())
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating note", e)
            return ToolResponse.Error(moduleId, "create_note", ErrorCode.SYSTEM_ERROR, "Failed: ${e.message}")
        }
    }
    
    private fun readNote(params: Map<String, Any?>, context: Context): ToolResponse {
        val title = params["title"] as? String
            ?: return ToolResponse.Error(moduleId, "read_note", ErrorCode.INVALID_PARAMS, "title required")
        
        try {
            val filename = sanitizeFilename(title) + ".txt"
            val file = File(getNotesDir(context), filename)
            
            if (!file.exists()) {
                // Try fuzzy match
                val notesDir = getNotesDir(context)
                val match = notesDir.listFiles()?.firstOrNull { 
                    it.nameWithoutExtension.contains(sanitizeFilename(title), ignoreCase = true) 
                }
                
                if (match != null) {
                    val content = match.readText()
                    return ToolResponse.Success(
                        moduleId = moduleId,
                        operationId = "read_note",
                        message = "Note found: ${match.nameWithoutExtension}",
                        data = mapOf("title" to match.nameWithoutExtension, "content" to content)
                    )
                }
                
                return ToolResponse.Error(moduleId, "read_note", ErrorCode.NOT_FOUND, "Note not found: $title")
            }
            
            val content = file.readText()
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "read_note",
                message = "Note: $title",
                data = mapOf("title" to title, "content" to content, "size" to file.length())
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reading note", e)
            return ToolResponse.Error(moduleId, "read_note", ErrorCode.SYSTEM_ERROR, "Failed: ${e.message}")
        }
    }
    
    private fun listNotes(params: Map<String, Any?>, context: Context): ToolResponse {
        val maxResults = (params["max_results"] as? Number)?.toInt() ?: 50
        
        try {
            val notesDir = getNotesDir(context)
            val notes = notesDir.listFiles()
                ?.filter { it.extension == "txt" }
                ?.sortedByDescending { it.lastModified() }
                ?.take(maxResults)
                ?.map { file ->
                    mapOf(
                        "title" to file.nameWithoutExtension,
                        "size" to file.length(),
                        "modified" to dateFormat.format(Date(file.lastModified()))
                    )
                } ?: emptyList()
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "list_notes",
                message = "${notes.size} note(s)",
                data = mapOf("notes" to notes)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error listing notes", e)
            return ToolResponse.Error(moduleId, "list_notes", ErrorCode.SYSTEM_ERROR, "Failed: ${e.message}")
        }
    }
    
    private fun appendNote(params: Map<String, Any?>, context: Context): ToolResponse {
        val title = params["title"] as? String
            ?: return ToolResponse.Error(moduleId, "append_note", ErrorCode.INVALID_PARAMS, "title required")
        val content = params["content"] as? String
            ?: return ToolResponse.Error(moduleId, "append_note", ErrorCode.INVALID_PARAMS, "content required")
        
        try {
            val filename = sanitizeFilename(title) + ".txt"
            val file = File(getNotesDir(context), filename)
            
            if (!file.exists()) {
                return ToolResponse.Error(moduleId, "append_note", ErrorCode.NOT_FOUND, "Note not found: $title")
            }
            
            file.appendText("\n\n[${dateFormat.format(Date())}]\n$content")
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "append_note",
                message = "Content appended to '$title'",
                data = mapOf("title" to title, "new_size" to file.length())
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error appending to note", e)
            return ToolResponse.Error(moduleId, "append_note", ErrorCode.SYSTEM_ERROR, "Failed: ${e.message}")
        }
    }
    
    private fun deleteNote(params: Map<String, Any?>, context: Context): ToolResponse {
        val title = params["title"] as? String
            ?: return ToolResponse.Error(moduleId, "delete_note", ErrorCode.INVALID_PARAMS, "title required")
        
        try {
            val filename = sanitizeFilename(title) + ".txt"
            val file = File(getNotesDir(context), filename)
            
            if (!file.exists()) {
                return ToolResponse.Error(moduleId, "delete_note", ErrorCode.NOT_FOUND, "Note not found: $title")
            }
            
            if (file.delete()) {
                return ToolResponse.Success(moduleId, "delete_note", "Note '$title' deleted", mapOf("title" to title))
            } else {
                return ToolResponse.Error(moduleId, "delete_note", ErrorCode.SYSTEM_ERROR, "Failed to delete")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting note", e)
            return ToolResponse.Error(moduleId, "delete_note", ErrorCode.SYSTEM_ERROR, "Failed: ${e.message}")
        }
    }
    
    private fun searchNotes(params: Map<String, Any?>, context: Context): ToolResponse {
        val query = params["query"] as? String
            ?: return ToolResponse.Error(moduleId, "search_notes", ErrorCode.INVALID_PARAMS, "query required")
        
        try {
            val notesDir = getNotesDir(context)
            val results = mutableListOf<Map<String, Any?>>()
            
            notesDir.listFiles()?.filter { it.extension == "txt" }?.forEach { file ->
                val content = file.readText()
                if (content.contains(query, ignoreCase = true)) {
                    // Find snippet around match
                    val lowerContent = content.lowercase()
                    val lowerQuery = query.lowercase()
                    val idx = lowerContent.indexOf(lowerQuery)
                    val snippet = if (idx >= 0) {
                        val start = maxOf(0, idx - 30)
                        val end = minOf(content.length, idx + query.length + 30)
                        "..." + content.substring(start, end) + "..."
                    } else ""
                    
                    results.add(mapOf(
                        "title" to file.nameWithoutExtension,
                        "snippet" to snippet,
                        "modified" to dateFormat.format(Date(file.lastModified()))
                    ))
                }
            }
            
            return ToolResponse.Success(
                moduleId = moduleId,
                operationId = "search_notes",
                message = "${results.size} note(s) match '$query'",
                data = mapOf("results" to results, "query" to query)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching notes", e)
            return ToolResponse.Error(moduleId, "search_notes", ErrorCode.SYSTEM_ERROR, "Failed: ${e.message}")
        }
    }
}
