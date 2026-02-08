package com.mazzlabs.sentinel.tools.modules.dev

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.mazzlabs.sentinel.graph.state.DevProjectState
import com.mazzlabs.sentinel.graph.state.DevProjectStatus
import com.mazzlabs.sentinel.tools.framework.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.projectDataStore: DataStore<Preferences> by preferencesDataStore(name = "dev_projects")

/**
 * ProjectManagerModule - Local project state management
 *
 * Persists dev project state to DataStore for crash recovery and session management.
 */
class ProjectManagerModule : ToolModule {

    companion object {
        private val KEY_ACTIVE_PROJECT = stringPreferencesKey("active_project")
        private val KEY_PROJECT_LIST = stringSetPreferencesKey("project_list")
        private fun projectKey(name: String) = stringPreferencesKey("project_$name")

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    override val moduleId = "project_manager"
    override val description = "Manage local dev project state - list, create, get, and update projects"

    override val operations = listOf(
        ToolOperation(
            operationId = "list_projects",
            description = "List all dev projects",
            parameters = emptyList()
        ),
        ToolOperation(
            operationId = "get_project",
            description = "Get details of a specific project",
            parameters = listOf(
                ToolParameter("name", ParameterType.STRING, "Project name", required = true)
            )
        ),
        ToolOperation(
            operationId = "get_active_project",
            description = "Get the currently active dev project",
            parameters = emptyList()
        ),
        ToolOperation(
            operationId = "set_active_project",
            description = "Set the active dev project",
            parameters = listOf(
                ToolParameter("name", ParameterType.STRING, "Project name", required = true)
            )
        )
    )

    override val requiredPermissions = emptyList<String>()

    override suspend fun execute(
        operationId: String,
        params: Map<String, Any?>,
        context: Context
    ): ToolResponse {
        return try {
            when (operationId) {
                "list_projects" -> {
                    val projects = getProjectList(context)
                    ToolResponse.Success(moduleId, operationId, "Found ${projects.size} projects",
                        mapOf("projects" to projects.toList()))
                }
                "get_project" -> {
                    val name = params["name"] as? String
                        ?: return ToolResponse.Error(moduleId, operationId, ErrorCode.INVALID_PARAMS, "Missing 'name'")
                    val project = getProject(context, name)
                    if (project != null) {
                        ToolResponse.Success(moduleId, operationId, "Project: $name",
                            mapOf(
                                "name" to project.projectName,
                                "objective" to project.objective,
                                "status" to project.status.name,
                                "iterations" to project.iterationCount,
                                "steps" to project.plan.size
                            ))
                    } else {
                        ToolResponse.Error(moduleId, operationId, ErrorCode.NOT_FOUND, "Project not found: $name")
                    }
                }
                "get_active_project" -> {
                    val name = getActiveProjectName(context)
                    if (name != null) {
                        ToolResponse.Success(moduleId, operationId, "Active project: $name",
                            mapOf("name" to name))
                    } else {
                        ToolResponse.Success(moduleId, operationId, "No active project")
                    }
                }
                "set_active_project" -> {
                    val name = params["name"] as? String
                        ?: return ToolResponse.Error(moduleId, operationId, ErrorCode.INVALID_PARAMS, "Missing 'name'")
                    setActiveProject(context, name)
                    ToolResponse.Success(moduleId, operationId, "Active project set to: $name")
                }
                else -> ToolResponse.Error(moduleId, operationId, ErrorCode.NOT_FOUND, "Unknown operation")
            }
        } catch (e: Exception) {
            ToolResponse.Error(moduleId, operationId, ErrorCode.SYSTEM_ERROR, "Error: ${e.message}")
        }
    }

    suspend fun saveProject(context: Context, state: DevProjectState) {
        context.projectDataStore.edit { prefs ->
            val serialized = json.encodeToString(serializeProjectState(state))
            prefs[projectKey(state.projectName)] = serialized
            val projects = prefs[KEY_PROJECT_LIST]?.toMutableSet() ?: mutableSetOf()
            projects.add(state.projectName)
            prefs[KEY_PROJECT_LIST] = projects
        }
    }

    private suspend fun getProjectList(context: Context): Set<String> {
        return context.projectDataStore.data.map { prefs ->
            prefs[KEY_PROJECT_LIST] ?: emptySet()
        }.first()
    }

    private suspend fun getProject(context: Context, name: String): DevProjectState? {
        val serialized = context.projectDataStore.data.map { prefs ->
            prefs[projectKey(name)]
        }.first() ?: return null

        return try {
            deserializeProjectState(json.decodeFromString(serialized))
        } catch (_: Exception) { null }
    }

    private suspend fun getActiveProjectName(context: Context): String? {
        return context.projectDataStore.data.map { prefs ->
            prefs[KEY_ACTIVE_PROJECT]
        }.first()
    }

    private suspend fun setActiveProject(context: Context, name: String) {
        context.projectDataStore.edit { prefs ->
            prefs[KEY_ACTIVE_PROJECT] = name
        }
    }

    // Simple serialization helpers using a map
    private fun serializeProjectState(state: DevProjectState): Map<String, String> {
        return mapOf(
            "objective" to state.objective,
            "projectName" to state.projectName,
            "status" to state.status.name,
            "iterationCount" to state.iterationCount.toString(),
            "maxIterations" to state.maxIterations.toString(),
            "lastSummary" to state.lastSummary,
            "lastError" to (state.lastError ?: ""),
            "totalTokensUsed" to state.totalTokensUsed.toString(),
            "planSize" to state.plan.size.toString()
        )
    }

    private fun deserializeProjectState(data: Map<String, String>): DevProjectState {
        return DevProjectState(
            objective = data["objective"] ?: "",
            projectName = data["projectName"] ?: "",
            status = try {
                DevProjectStatus.valueOf(data["status"] ?: "INITIALIZING")
            } catch (_: Exception) { DevProjectStatus.INITIALIZING },
            iterationCount = data["iterationCount"]?.toIntOrNull() ?: 0,
            maxIterations = data["maxIterations"]?.toIntOrNull() ?: 10,
            lastSummary = data["lastSummary"] ?: "",
            lastError = data["lastError"]?.takeIf { it.isNotBlank() },
            totalTokensUsed = data["totalTokensUsed"]?.toIntOrNull() ?: 0
        )
    }
}
