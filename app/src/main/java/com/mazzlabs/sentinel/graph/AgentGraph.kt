package com.mazzlabs.sentinel.graph

import android.util.Log

/**
 * AgentGraph - LangGraph-inspired DAG orchestrator
 * 
 * Defines a directed acyclic graph of processing nodes with conditional edges.
 * Execution is deterministic based on state conditions.
 */
class AgentGraph private constructor(
    private val nodes: Map<String, AgentNode>,
    private val edges: Map<String, Edge>,
    private val entryPoint: String
) {
    companion object {
        private const val TAG = "AgentGraph"
        const val START = "START"
        const val END = "END"
    }

    /**
     * Execute the graph from entry point to END
     */
    suspend fun invoke(initialState: AgentState): AgentState {
        var state = initialState.copy(currentNode = entryPoint)
        
        Log.d(TAG, "Starting graph execution from: $entryPoint")
        
        while (state.shouldContinue()) {
            val currentNodeName = state.currentNode
            
            if (currentNodeName == END) {
                Log.d(TAG, "Reached END node")
                state = state.copy(isComplete = true)
                break
            }
            
            val node = nodes[currentNodeName]
            if (node == null) {
                Log.e(TAG, "Node not found: $currentNodeName")
                state = state.copy(error = "Node not found: $currentNodeName", isComplete = true)
                break
            }
            
            // Execute node
            Log.d(TAG, "Executing node: $currentNodeName")
            state = try {
                node.process(state)
            } catch (e: Exception) {
                Log.e(TAG, "Node $currentNodeName failed", e)
                state.copy(error = "Node failed: ${e.message}", isComplete = true)
            }
            
            if (state.hasError()) break
            
            // Determine next node
            val edge = edges[currentNodeName]
            if (edge == null) {
                Log.e(TAG, "No edge from node: $currentNodeName")
                state = state.copy(error = "No edge from: $currentNodeName", isComplete = true)
                break
            }
            
            val nextNode = edge.route(state)
            Log.d(TAG, "Routing: $currentNodeName -> $nextNode")
            state = state.copy(currentNode = nextNode)
        }
        
        if (state.iteration >= state.maxIterations) {
            Log.w(TAG, "Max iterations reached")
            state = state.copy(error = "Max iterations exceeded", isComplete = true)
        }
        
        Log.d(TAG, "Graph execution complete. History: ${state.history}")
        return state
    }

    /**
     * Builder for constructing graphs
     */
    class Builder {
        private val nodes = mutableMapOf<String, AgentNode>()
        private val edges = mutableMapOf<String, Edge>()
        private var entryPoint: String = START

        /**
         * Add a processing node
         */
        fun addNode(name: String, node: AgentNode): Builder {
            nodes[name] = node
            return this
        }

        /**
         * Add unconditional edge (always goes to target)
         */
        fun addEdge(from: String, to: String): Builder {
            edges[from] = Edge.Unconditional(to)
            return this
        }

        /**
         * Add conditional edge with routing function
         */
        fun addConditionalEdge(
            from: String,
            router: (AgentState) -> String
        ): Builder {
            edges[from] = Edge.Conditional(router)
            return this
        }

        /**
         * Add conditional edge with explicit mappings
         */
        fun addConditionalEdge(
            from: String,
            condition: (AgentState) -> String,
            mappings: Map<String, String>
        ): Builder {
            edges[from] = Edge.Conditional { state ->
                val result = condition(state)
                mappings[result] ?: END
            }
            return this
        }

        /**
         * Set the entry point node
         */
        fun setEntryPoint(node: String): Builder {
            entryPoint = node
            return this
        }

        fun build(): AgentGraph {
            // Validate graph
            require(nodes.isNotEmpty()) { "Graph must have at least one node" }
            require(entryPoint in nodes || entryPoint == START) { 
                "Entry point must be a valid node" 
            }
            
            return AgentGraph(nodes.toMap(), edges.toMap(), entryPoint)
        }
    }
}

/**
 * Edge types for graph routing
 */
sealed class Edge {
    abstract fun route(state: AgentState): String

    data class Unconditional(val target: String) : Edge() {
        override fun route(state: AgentState) = target
    }

    data class Conditional(val router: (AgentState) -> String) : Edge() {
        override fun route(state: AgentState) = router(state)
    }
}

/**
 * AgentNode - Processing unit in the graph
 */
fun interface AgentNode {
    suspend fun process(state: AgentState): AgentState
}
