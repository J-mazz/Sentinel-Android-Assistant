# Architecture Overview

This document provides a comprehensive view of Sentinel's system architecture, design patterns, and data flow.

## Table of Contents

- [Core Principles](#core-principles)
- [Three-Layer Architecture](#three-layer-architecture)
- [System Layers](#system-layers)
- [Data Flow](#data-flow)
- [Design Patterns](#design-patterns)
- [Concurrency Model](#concurrency-model)
- [Memory Management](#memory-management)

## Core Principles

Sentinel's architecture is built on these fundamental principles:

### 1. Security by Design
Every layer has security responsibilities. No single point of failure.

### 2. Separation of Concerns
Clear boundaries between observation, reasoning, and action.

### 3. Immutability
State flows through the system without mutation, enabling rollback and auditing.

### 4. Fail-Safe Defaults
Unknown or dangerous operations default to rejection or confirmation.

### 5. Privacy First
Zero network access. All processing on-device. No telemetry.

## Three-Layer Architecture

Sentinel implements a strict three-boundary design inspired by security-critical systems:

```
┌───────────────────────────────────────────────────────────────┐
│                     LAYER 1: THE OBSERVER                      │
│                         (Kotlin)                               │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │           AgentAccessibilityService                      │  │
│  │  • Passive UI state collection                          │  │
│  │  • Element registry management                          │  │
│  │  • Input handling (voice, overlay, selection)           │  │
│  │  • No business logic or decisions                       │  │
│  └─────────────────────────────────────────────────────────┘  │
└────────────────────────────┬──────────────────────────────────┘
                             │ Screen Context + User Query
┌────────────────────────────▼──────────────────────────────────┐
│                     LAYER 2: THE CORTEX                        │
│                         (C++23)                                │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │              llama.cpp + Jamba-3B                       │  │
│  │  1. Input Sanitization (injection detection)            │  │
│  │  2. Context Wrapping (chat template)                    │  │
│  │  3. Grammar-Constrained Inference (GBNF)                │  │
│  │  4. JSON Validation                                     │  │
│  │  • Isolated from Kotlin logic                           │  │
│  │  • Deterministic output structure                       │  │
│  └─────────────────────────────────────────────────────────┘  │
└────────────────────────────┬──────────────────────────────────┘
                             │ Validated JSON Action/Tool Call
┌────────────────────────────▼──────────────────────────────────┐
│                    LAYER 3: THE ACTUATOR                       │
│                         (Kotlin)                               │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │          ActionDispatcher + Security Layer              │  │
│  │  1. Action Firewall (heuristic danger detection)        │  │
│  │  2. Risk Classifier (semantic analysis)                 │  │
│  │  3. Physical Confirmation (Volume Up button)            │  │
│  │  4. Element Validation (staleness check)                │  │
│  │  5. Accessibility API Execution                         │  │
│  └─────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────┘
```

### Layer 1: The Observer

**Responsibility**: Collect UI state and user input

**Components**:
- `AgentAccessibilityService`: Main service entry point
- `ElementRegistry`: Indexes UI elements for grounded actions
- `OverlayManager`: Floating action button
- `VoiceInputManager`: Speech recognition
- `SelectionOverlayManager`: Screen region selection
- `ScreenCaptureManager`: Screenshot capture

**Security Boundary**:
- No decision-making logic
- Pure observation and data collection
- Forwards raw data to orchestration layer

**File**: `/app/src/main/java/com/mazzlabs/sentinel/service/AgentAccessibilityService.kt` (722 lines)

### Layer 2: The Cortex

**Responsibility**: Reason about intent and generate actions

**Components**:
- Native inference engine (llama.cpp)
- Jamba-Reasoning-3B model (Q4_K_M quantization)
- GBNF grammar enforcement
- Input sanitizer
- Chat template formatter

**Security Boundary**:
- Input sanitization: Strip control tokens, limit length
- Injection detection: Block malicious prompts
- Grammar constraint: Only valid JSON can be produced
- Thread-safe: Mutex-protected model access

**Files**:
- `/app/src/main/cpp/native-lib.cpp` (352 lines)
- `/app/src/main/cpp/native_inference.cpp`
- `/app/src/main/cpp/sentinel.hpp`

### Layer 3: The Actuator

**Responsibility**: Execute validated actions safely

**Components**:
- `ActionDispatcher`: Translates JSON to accessibility actions
- `ActionFirewall`: Heuristic danger detection
- `ActionRiskClassifier`: LLM-based semantic risk assessment
- Physical confirmation system

**Security Boundary**:
- Firewall blocks known dangerous patterns
- Risk classifier reduces false positives
- User must physically confirm dangerous actions
- Staleness detection prevents acting on old UI state

**File**: `/app/src/main/java/com/mazzlabs/sentinel/service/ActionDispatcher.kt` (319 lines)

## System Layers

Beyond the three-layer security architecture, the codebase is organized into functional layers:

### UI Layer

**Components**:
- `MainActivity`: Main activity for model loading and status
- `OverlayManager`: Floating overlay UI
- `SelectionOverlayManager`: Screen selection UI
- `VoiceInputManager`: Voice input UI

**Responsibilities**:
- User interaction
- Permission requests
- Model loading status
- Overlay rendering

**Threading**: Main thread (UI thread)

### Service Layer

**Components**:
- `AgentAccessibilityService`: Core accessibility service
- `InferenceService`: Foreground service for sustained inference
- `ScreenCaptureManager`: Screenshot capture service

**Responsibilities**:
- Long-running operations
- System service integration
- Accessibility event handling
- Foreground notification

**Threading**: Main thread + service scope coroutines

### Orchestration Layer

**Components**:
- `EnhancedAgentOrchestrator`: DAG execution coordinator
- `AgentGraph`: Graph execution engine
- `SessionManager`: Conversation history management
- Graph nodes (Intent, Entity, Tool, UI, Response, Selection)

**Responsibilities**:
- Multi-step reasoning
- State transformation
- Node routing
- Session persistence

**Threading**: Dispatchers.Default

**Files**:
- `/app/src/main/java/com/mazzlabs/sentinel/graph/EnhancedAgentOrchestrator.kt` (177 lines)
- `/app/src/main/java/com/mazzlabs/sentinel/graph/AgentGraph.kt` (170 lines)
- `/app/src/main/java/com/mazzlabs/sentinel/graph/AgentState.kt` (148 lines)
- `/app/src/main/java/com/mazzlabs/sentinel/graph/nodes/` (various node implementations)

### Core Layer

**Components**:
- `AgentController`: Main orchestration controller
- `NativeBridge`: JNI bridge to native code
- `SystemPromptBuilder`: Dynamic system prompt generation
- `JsonExtractor`: JSON parsing and validation
- `ToolExecutor`: Tool invocation coordinator
- `ToolRouter`: Routes tool calls to modules

**Responsibilities**:
- Request routing (tool vs UI action)
- Native inference invocation
- JSON parsing and validation
- Tool execution
- Response formatting

**Threading**: Dispatchers.IO for inference

**Files**:
- `/app/src/main/java/com/mazzlabs/sentinel/core/AgentController.kt` (187 lines)
- `/app/src/main/java/com/mazzlabs/sentinel/core/NativeBridge.kt` (76 lines)
- `/app/src/main/java/com/mazzlabs/sentinel/core/SystemPromptBuilder.kt`

### Tool Layer

**Components**:
- `ToolRegistry`: Centralized tool registration
- `ToolModule` interface: Base interface for all tools
- Tool implementations:
  - `CalendarModule`: Calendar event management
  - `ClockModule`: Alarms and timers
  - `ContactsModule`: Contact management
  - `MessagingModule`: SMS operations
  - `NotesModule`: Note-taking
  - `TerminalModule`: Shell command execution

**Responsibilities**:
- Device capability access
- Permission management
- Operation execution
- Result formatting

**Threading**: Dispatchers.IO

**Files**:
- `/app/src/main/java/com/mazzlabs/sentinel/tools/framework/` (framework)
- `/app/src/main/java/com/mazzlabs/sentinel/tools/modules/` (implementations)

### Security Layer

**Components**:
- `ActionFirewall`: Keyword-based danger detection
- `ActionRiskClassifier`: LLM-based semantic analysis

**Responsibilities**:
- Action validation
- Danger scoring
- Confirmation requirements

**Threading**: Main thread (synchronous checks)

**Files**:
- `/app/src/main/java/com/mazzlabs/sentinel/security/ActionFirewall.kt` (178 lines)
- `/app/src/main/java/com/mazzlabs/sentinel/security/ActionRiskClassifier.kt` (102 lines)

### Native Layer

**Components**:
- llama.cpp inference engine
- GGML (GPU Metal for Mobile)
- GBNF grammar parser
- Input sanitizer
- Chat template engine

**Responsibilities**:
- Model loading and management
- Token encoding/decoding
- Inference execution
- Grammar constraint enforcement

**Threading**: Native threads (mutex-protected)

**Files**:
- `/app/src/main/cpp/native-lib.cpp` (352 lines)
- `/app/src/main/cpp/native_inference.cpp`
- `/app/src/main/cpp/sentinel.hpp`
- `/libs/llama.cpp/` (submodule)

## Data Flow

### Voice Query Flow

```
┌────────────────────────────────────────────────────────────────┐
│ 1. User Input                                                   │
│    • User taps overlay button                                   │
│    • VoiceInputManager starts listening                         │
│    • Speech → Text transcription                                │
└──────────────────────────┬─────────────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────────────┐
│ 2. Screen State Capture                                         │
│    • ElementRegistry.rebuild(rootNode)                          │
│    • Traverses accessibility tree                               │
│    • Filters to interactive elements                            │
│    • Generates prompt-friendly element list                     │
└──────────────────────────┬─────────────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────────────┐
│ 3. Agent Orchestration                                          │
│    • EnhancedAgentOrchestrator.process(query, screenContext)    │
│    • Create initial AgentState                                  │
│    • Execute graph nodes in sequence:                           │
│      - IntentParserNode: Classify intent (via LLM)              │
│      - EntityExtractorNode: Extract parameters (via LLM)        │
│      - ContextAnalyzerNode: Analyze screen context              │
│      - Conditional routing:                                     │
│        * Tool intent → ToolSelector → ParamExtractor → ToolExec │
│        * UI intent → UIActionNode                               │
│        * Selection intent → SelectionProcessor                  │
│      - ResponseGeneratorNode: Format final response             │
└──────────────────────────┬─────────────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────────────┐
│ 4. Tool Execution (if tool call)                                │
│    • ToolExecutor parses JSON tool call                         │
│    • Validate parameters against schema                         │
│    • Check permissions (request if missing)                     │
│    • Module.execute(operation, params, context)                 │
│    • Format ToolResponse (Success/Error/Confirmation)           │
└──────────────────────────┬─────────────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────────────┐
│ 5. Action Dispatch (if UI action)                               │
│    • ActionFirewall.isDangerous(action)                         │
│    • If dangerous:                                              │
│      - ActionRiskClassifier.assess(action, context)             │
│      - If high risk: requestPhysicalConfirmation()              │
│      - Wait for Volume Up button press                          │
│    • ActionDispatcher.dispatch(service, root, action)           │
│    • Translate to accessibility API calls                       │
│    • Execute gesture/click/type/scroll                          │
└──────────────────────────┬─────────────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────────────┐
│ 6. Result Broadcasting                                          │
│    • Broadcast intent with result                               │
│    • Update UI overlay with status                              │
│    • TTS (if enabled)                                           │
└─────────────────────────────────────────────────────────────────┘
```

### Screen Selection Flow

```
┌────────────────────────────────────────────────────────────────┐
│ 1. Initiation                                                   │
│    • User long-presses overlay button                           │
│    • AgentAccessibilityService.enterSelectionMode()             │
└──────────────────────────┬─────────────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────────────┐
│ 2. Screen Capture                                               │
│    • ScreenCaptureManager.takeScreenshot()                      │
│    • MediaProjection API                                        │
│    • Bitmap at full screen resolution                           │
└──────────────────────────┬─────────────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────────────┐
│ 3. Selection UI                                                 │
│    • SelectionOverlayManager.showSelection(bitmap)              │
│    • User drags to select rectangular region                    │
│    • Visual feedback with bounding box                          │
└──────────────────────────┬─────────────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────────────┐
│ 4. OCR Processing                                               │
│    • Extract selected region from bitmap                        │
│    • Downscale to max 1280px (performance optimization)         │
│    • ML Kit TextRecognition.process(image)                      │
│    • Aggregate text blocks                                      │
│    • Calculate confidence score                                 │
│    • Detect language hint (Latin, Cyrillic, Arabic, CJK)        │
└──────────────────────────┬─────────────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────────────┐
│ 5. Confidence Check                                             │
│    • If confidence ≥ 0.6:                                       │
│      → Continue to agent processing                             │
│    • If confidence < 0.6:                                       │
│      → Show selection actions dialog                            │
│      → User provides explicit query                             │
└──────────────────────────┬─────────────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────────────┐
│ 6. Agent Processing                                             │
│    • SelectionProcessorNode.process(state)                      │
│    • Update AgentState with:                                    │
│      - extractedText                                            │
│      - selectedRegion bounds                                    │
│      - languageHint                                             │
│      - confidence score                                         │
│    • Continue through agent graph                               │
│    • LLM reasons about extracted text + screen context          │
└─────────────────────────────────────────────────────────────────┘
```

### Native Inference Flow

```
┌────────────────────────────────────────────────────────────────┐
│ Kotlin: NativeBridge.infer(userQuery, screenContext)           │
└──────────────────────────┬─────────────────────────────────────┘
                           │ JNI Call
┌──────────────────────────▼─────────────────────────────────────┐
│ C++: Java_com_...NativeBridge_infer(...)                        │
│                                                                 │
│  1. Lock model mutex (thread safety)                            │
│  2. Check model ready                                           │
│  3. sentinel::contains_injection(userQuery)                     │
│     → Return error if injection detected                        │
│  4. sentinel::sanitize(userQuery, 2048)                         │
│     sentinel::sanitize(screenContext, 32000)                    │
│     → Strip control tokens, limit length, XML escape            │
│  5. SystemPromptBuilder.build(availableActions, tools)          │
│     → Dynamic system prompt with current capabilities           │
│  6. apply_chat_template(systemPrompt, userQuery)                │
│     → Uses model's Jinja template                               │
│     → Formats: <|system|>...<|user|>...<|assistant|>            │
│  7. llama_tokenize(ctx, prompt)                                 │
│     → Convert text to token IDs                                 │
│  8. llama_decode(ctx, batch)                                    │
│     → Run transformer inference                                 │
│     → Grammar constrains output to valid JSON                   │
│     → Sample tokens greedily until EOS                          │
│  9. llama_detokenize(ctx, tokens)                               │
│     → Convert token IDs back to text                            │
│ 10. Validate JSON structure                                     │
│ 11. Return std::expected<std::string, std::string>              │
└──────────────────────────┬─────────────────────────────────────┘
                           │ JNI Return
┌──────────────────────────▼─────────────────────────────────────┐
│ Kotlin: Parse jstring result                                    │
│  • JsonExtractor.extractAction(json)                            │
│  • Or: JsonExtractor.extractToolCall(json)                      │
│  • Or: Parse error message                                      │
└─────────────────────────────────────────────────────────────────┘
```

## Design Patterns

Sentinel employs several design patterns for maintainability and extensibility:

### 1. Three-Tier Architecture Pattern

**Separates**: Observer (data), Cortex (logic), Actuator (execution)

**Benefits**:
- Clear security boundaries
- Testable in isolation
- Independent evolution

### 2. DAG Workflow Pattern (LangGraph-inspired)

**Implementation**: `AgentGraph` executes nodes in directed acyclic graph

**Benefits**:
- Declarative workflow definition
- Conditional routing
- State transformation tracking

**Example**:
```kotlin
val graph = AgentGraph(
    nodes = mapOf(
        "intent_parser" to IntentParserNode(llm),
        "tool_selector" to ToolSelectorNode(toolRegistry),
        "tool_executor" to ToolExecutorNode(toolRegistry, context)
    ),
    edges = listOf(
        Edge("intent_parser", "tool_selector", condition = { it.intent != null }),
        Edge("tool_selector", "tool_executor", condition = { it.selectedTool != null })
    )
)
```

### 3. Immutable State Pattern

**Implementation**: `AgentState` is immutable; nodes return new state

**Benefits**:
- Thread-safe
- Auditable history
- Rollback capability

**Example**:
```kotlin
data class AgentState(
    val userQuery: String,
    val screenContext: String,
    val intent: AgentIntent? = null,
    val response: String? = null,
    // ... other fields
)

interface AgentNode {
    suspend fun process(state: AgentState): AgentState  // Returns NEW state
}
```

### 4. Strategy Pattern (Tool Modules)

**Implementation**: `ToolModule` interface with multiple implementations

**Benefits**:
- Pluggable tool system
- Easy to add new capabilities
- Consistent API

**Example**:
```kotlin
interface ToolModule {
    val moduleId: String
    val operations: List<ToolOperation>
    suspend fun execute(operationId: String, params: Map<String, Any?>, context: Context): ToolResponse
}

class CalendarModule : ToolModule { ... }
class MessagingModule : ToolModule { ... }
```

### 5. Command Pattern (Actions)

**Implementation**: `AgentAction` represents executable commands

**Benefits**:
- Actions as data
- Queueable and replayable
- Validation before execution

**Example**:
```kotlin
data class AgentAction(
    val action: ActionType,
    val target: String? = null,
    val elementId: Int? = null,
    val text: String? = null,
    val reasoning: String
)
```

### 6. Bridge Pattern (JNI)

**Implementation**: `NativeBridge` connects Kotlin to C++

**Benefits**:
- Language interop
- Performance optimization
- Legacy library integration (llama.cpp)

### 7. Registry Pattern

**Implementation**: `ToolRegistry`, `ElementRegistry`

**Benefits**:
- Centralized management
- Dynamic lookup
- Lifecycle control

### 8. Builder Pattern

**Implementation**: `SystemPromptBuilder`, `AgentGraph.Builder`

**Benefits**:
- Complex object construction
- Fluent API
- Readable configuration

### 9. Observer Pattern

**Implementation**: Android's AccessibilityService callbacks

**Benefits**:
- Event-driven UI observation
- Decoupled event handling

### 10. Result/Expected Pattern

**Implementation**: `AgentResult` sealed class, C++ `std::expected`

**Benefits**:
- Explicit error handling
- Type-safe success/failure
- Composable operations

**Example**:
```kotlin
sealed class AgentResult {
    data class ToolResult(val response: ToolResponse) : AgentResult()
    data class UIAction(val action: AgentAction) : AgentResult()
    data class Response(val message: String) : AgentResult()
    data class Error(val message: String) : AgentResult()
}
```

## Concurrency Model

Sentinel uses Kotlin coroutines with structured concurrency:

### Threading Strategy

**Main Thread**:
- Accessibility operations (must be on main thread)
- UI updates
- Gesture dispatch

**Dispatchers.Default**:
- Agent graph execution
- State transformation
- Business logic

**Dispatchers.IO**:
- Inference calls (blocking native code)
- Tool execution (database queries, content provider access)
- File I/O

**Dispatchers.Main**:
- Coroutine context switches for accessibility operations
- UI callbacks

### Coroutine Scopes

```kotlin
class AgentAccessibilityService {
    // Service-lifetime scope
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Cancellable agent job
    private var currentAgentJob: Job? = null

    fun triggerAgent(userQuery: String) {
        currentAgentJob?.cancel()  // Cancel previous job
        currentAgentJob = serviceScope.launch {
            // Agent processing
        }
    }
}
```

### Thread Safety Mechanisms

**Kotlin**:
- `AtomicLong` for counters
- `AtomicReference` for compound state (recent fix)
- Coroutine mutex for exclusive access
- Immutable data classes

**C++**:
- `std::shared_mutex` for model access
- `std::lock_guard` for RAII locking
- Thread-local caches

### Race Condition Prevention

**Problem**: Multiple volatile variables read separately can be inconsistent

**Solution** (commit ecb55c8):
```kotlin
// Before: Race condition
@Volatile private var lastTimestamp: Long = 0
@Volatile private var lastPackageName: String = ""
// Reading these separately can get mismatched values!

// After: Atomic compound state
private data class CachedScreenState(
    val timestamp: Long,
    val packageName: String
)
private val cachedState = AtomicReference(CachedScreenState())
// Reading atomically gets consistent snapshot
```

## Memory Management

Sentinel carefully manages memory to prevent leaks and crashes:

### Kotlin Memory Management

**Accessibility Node Recycling** (commit 74aa7b4):
```kotlin
fun clear() {
    // Recycle all AccessibilityNodeInfo objects to prevent memory leaks
    nodeMap.values.forEach { node ->
        node.recycle()
    }
    elements.clear()
    nodeMap.clear()
}
```

**Stream Resource Management** (commit 65c8d6a):
```kotlin
BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
    // Read data
}  // Automatically closed here
```

**Bitmap Recycling**:
```kotlin
fun releaseScreenshot() {
    currentScreenshot?.recycle()
    currentScreenshot = null
}
```

### Native Memory Management

**Model Lifecycle**:
```cpp
// Initialization
g_llama_model = llama_load_model_from_file(modelPath, model_params);
g_llama_ctx = llama_new_context_with_model(g_llama_model, ctx_params);

// Cleanup
void Java_...NativeBridge_releaseModel(...) {
    std::lock_guard lock(g_model_mutex);
    if (g_llama_ctx) llama_free(g_llama_ctx);
    if (g_llama_model) llama_free_model(g_llama_model);
}
```

**String Allocation**:
```cpp
// Return string to Java
jstring jresult = env->NewStringUTF(result.c_str());
// JVM takes ownership, will GC
```

### Memory Limits

**Context Window**: 32K tokens (~24KB text)
**Max Query**: 2KB (sanitization limit)
**Max Screen Context**: 32KB (sanitization limit)
**Element Limit**: 60 interactive elements (toPromptString)
**Traversal Depth**: 50 levels (stack overflow prevention)

## Performance Considerations

### Optimization Techniques

**ARM64 CPU Optimizations**:
- NEON SIMD instructions
- DOT product extensions
- FP16 acceleration
- CMake flags: `-march=armv8.4-a+dotprod+fp16`

**Inference Optimization**:
- GPU layer offloading (99 layers)
- Context caching (reuse KV cache)
- Greedy decoding (no beam search overhead)

**UI Performance**:
- Async accessibility tree traversal
- Limited element list (60 max in prompt)
- Debounced window events
- Bitmap downscaling for OCR

### Resource Constraints

**Inference Latency**: 2-10 seconds typical
**Memory**: 2-3GB for model + KV cache
**Battery**: Foreground service, not optimized for continuous use
**CPU**: Single-threaded inference (llama.cpp limitation)

## Extensibility Points

Sentinel is designed for extension:

### Adding New Tools
Implement `ToolModule` interface and register in `ToolRegistry`

### Adding Graph Nodes
Implement `AgentNode` interface and add to graph definition

### Custom Grammars
Create `.gbnf` file and reference in inference call

### Native Function Extension
Add JNI method in `native-lib.cpp` and Kotlin declaration in `NativeBridge`

### Custom Actions
Add to `ActionType` enum and implement in `ActionDispatcher`

See [Tool Development Guide](TOOLS.md) for detailed instructions.

---

**Next**: See [Component Documentation](COMPONENTS.md) for detailed component descriptions.
