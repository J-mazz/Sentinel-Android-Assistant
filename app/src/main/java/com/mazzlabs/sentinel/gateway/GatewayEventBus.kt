package com.mazzlabs.sentinel.gateway

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * GatewayEventBus - SharedFlow-based event distribution
 *
 * Replaces the TypeScript EventEmitter pattern with Kotlin SharedFlow.
 * Supports filtered subscriptions and one-shot waiting.
 */
class GatewayEventBus {

    private val _events = MutableSharedFlow<GatewayEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )

    val events: SharedFlow<GatewayEvent> = _events.asSharedFlow()

    /**
     * Emit an event to all subscribers
     */
    suspend fun emit(event: GatewayEvent) {
        _events.emit(event)
    }

    /**
     * Try to emit without suspending (for non-coroutine contexts)
     */
    fun tryEmit(event: GatewayEvent): Boolean {
        return _events.tryEmit(event)
    }

    /**
     * Wait for a specific event type with timeout
     */
    suspend inline fun <reified T : GatewayEvent> awaitEvent(
        timeoutMs: Long = 30000L,
        crossinline predicate: (T) -> Boolean = { true }
    ): T? {
        return withTimeoutOrNull(timeoutMs) {
            _events
                .filterIsInstance<T>()
                .filter { predicate(it) }
                .first()
        }
    }

    /**
     * Wait for an agent completion event for a specific session
     */
    suspend fun awaitAgentComplete(
        sessionKey: String,
        timeoutMs: Long = 120000L
    ): GatewayEvent.AgentComplete? {
        return awaitEvent<GatewayEvent.AgentComplete>(timeoutMs) {
            it.sessionKey == sessionKey
        }
    }
}
