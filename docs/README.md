# Sentinel Android Assistant Documentation

Welcome to the comprehensive documentation for Sentinel Android Assistant, a privacy-focused, on-device AI assistant for Android.

## Documentation Index

### Getting Started
- **[Setup Guide](SETUP.md)** - Installation, configuration, and deployment
- **[Quick Start](QUICKSTART.md)** - Get up and running in 5 minutes

### Architecture & Design
- **[Architecture Overview](ARCHITECTURE.md)** - System design, layers, and data flow
- **[Component Documentation](COMPONENTS.md)** - Detailed component descriptions
- **[Security Model](SECURITY.md)** - Multi-layered security architecture

### Development
- **[Development Guide](DEVELOPMENT.md)** - Development workflow and best practices
- **[Tool Development](TOOLS.md)** - Creating and integrating new tool modules
- **[API Reference](API.md)** - Key interfaces and APIs
- **[Testing Guide](TESTING.md)** - Testing strategies and coverage

### Operations
- **[Troubleshooting](TROUBLESHOOTING.md)** - Common issues and solutions
- **[Performance Tuning](PERFORMANCE.md)** - Optimization techniques
- **[Debugging Guide](DEBUGGING.md)** - Debugging strategies and tools

### Contributing
- **[Contributing Guidelines](CONTRIBUTING.md)** - How to contribute to the project
- **[Code Style](CODE_STYLE.md)** - Coding conventions and standards

## Project Overview

Sentinel is a **100% on-device AI assistant** for Android that provides:
- Natural language control of your device
- Tool-augmented capabilities (calendar, contacts, messaging, etc.)
- Multi-step reasoning and planning
- Privacy-first design with no network access

### Key Features

✅ **On-Device Processing** - All inference runs locally using llama.cpp
✅ **Multi-Layered Security** - 6 defense layers from input to action
✅ **Accessibility Integration** - Full UI control and observation
✅ **Tool Framework** - Extensible module system for device capabilities
✅ **Context-Aware** - Understands screen state and user intent
✅ **Privacy Focused** - No network permissions, no telemetry, no cloud services

### Technology Stack

- **Language**: Kotlin (app), C++23 (native inference)
- **Inference Engine**: llama.cpp with Jamba-Reasoning-3B model
- **Android Framework**: AccessibilityService, Foreground Service
- **ML Kit**: Text recognition for OCR
- **Build System**: Gradle with CMake for native code

## Quick Links

- [Main Project README](../README.md)
- [Source Code](../app/src/main/java/com/mazzlabs/sentinel/)
- [Tests](../app/src/test/java/com/mazzlabs/sentinel/)
- [Native Code](../app/src/main/cpp/)
- [Build Configuration](../app/build.gradle.kts)

## Architecture at a Glance

```
┌──────────────────────────────────────────────────────────┐
│                    User Interface                         │
│              (Overlay, Voice, Selection)                  │
└────────────────────┬─────────────────────────────────────┘
                     │
┌────────────────────▼─────────────────────────────────────┐
│              The Observer (Kotlin)                        │
│         AgentAccessibilityService                         │
│    • Captures UI state via accessibility tree             │
│    • Manages input (voice, overlay, selection)            │
│    • Orchestrates agent pipeline                          │
└────────────────────┬─────────────────────────────────────┘
                     │
┌────────────────────▼─────────────────────────────────────┐
│               The Cortex (C++23)                          │
│         llama.cpp + Jamba-Reasoning-3B                    │
│    • Input sanitization & injection detection             │
│    • Grammar-constrained inference (GBNF)                 │
│    • JSON output validation                               │
└────────────────────┬─────────────────────────────────────┘
                     │
┌────────────────────▼─────────────────────────────────────┐
│              The Actuator (Kotlin)                        │
│         ActionDispatcher + Security Layer                 │
│    • Action firewall & risk classification                │
│    • Physical confirmation for dangerous actions          │
│    • Accessibility API execution                          │
└──────────────────────────────────────────────────────────┘
```

## Security Model

Sentinel implements **defense-in-depth** with 6 security layers:

1. **Input Sanitization** (Native) - Block injection attempts
2. **Output Constraint** (Native) - Grammar-enforced valid JSON
3. **Action Firewall** (Kotlin) - Heuristic danger detection
4. **Semantic Risk Classifier** (Kotlin) - Context-aware assessment
5. **Physical Confirmation** (Hardware) - Volume button for dangerous actions
6. **Action Validation** (Kotlin) - Staleness detection and element validation

See [Security Model](SECURITY.md) for complete details.

## Common Use Cases

### Voice-Activated Control
```
User: "What's on my calendar today?"
  → Intent classification
  → Tool selection (calendar.read_events)
  → Permission check
  → Calendar query
  → Natural language response
```

### Screen Understanding
```
User: Long-press overlay
  → Screen capture
  → Region selection
  → OCR extraction
  → Agent processing with extracted text
```

### UI Automation
```
User: "Scroll down and click the first link"
  → Multi-step plan creation
  → Action sequence generation
  → Safety validation
  → Sequential execution
```

## Development Workflow

```bash
# 1. Setup
./scripts/setup_llama.sh
adb push model.gguf /data/local/tmp/

# 2. Build
./gradlew assembleDebug

# 3. Test
./gradlew test
./gradlew koverCoverageGate

# 4. Deploy
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

See [Development Guide](DEVELOPMENT.md) for complete workflow.

## Recent Updates

### Bug Fixes (Commits ea04fde - 65c8d6a)
- ✅ Fixed resource leak in TerminalModule (stream readers)
- ✅ Fixed memory leak in ElementRegistry (AccessibilityNodeInfo recycling)
- ✅ Fixed race condition in AgentAccessibilityService (AtomicReference)
- ✅ Fixed stack overflow risk in ActionDispatcher (depth limits)
- ✅ Improved error logging in GraphNodes (include failed JSON)

### Recent Enhancements
- Enhanced agent orchestration with LangGraph-inspired DAG
- Multi-step planning and reasoning
- Screen selection with OCR
- Context-aware risk classification

## Support & Contributing

- **Issues**: Report bugs and request features in GitHub Issues
- **Discussions**: Ask questions in GitHub Discussions
- **Contributing**: See [Contributing Guidelines](CONTRIBUTING.md)
- **Code of Conduct**: Be respectful and constructive

## License

See [LICENSE](../LICENSE) file in the project root.

---

**Documentation Version**: 1.0
**Last Updated**: 2026-01-18
**Codebase Version**: Compatible with commit ea04fde
