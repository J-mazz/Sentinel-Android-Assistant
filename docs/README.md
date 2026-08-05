# Sentinel Documentation

Sentinel is a Kotlin Android client that observes UI state, sends inference requests to a user-controlled OpenClaw gateway, and validates actions before executing them. The default model is `gemma-4-E2B-it-qat-UD-Q4_K_XL.gguf`, served by llama.cpp on the gateway host.

## Guides

- [Quick Start](QUICKSTART.md): Start Gemma, configure OpenClaw, and install the app.
- [Setup](SETUP.md): Configuration details and supported environment overrides.
- [Architecture](ARCHITECTURE.md): Components and request flow.
- [Security](SECURITY.md): Trust boundaries and deployment guidance.
- [Tools](TOOLS.md): Available Android and remote tool modules.
- [Troubleshooting](TROUBLESHOOTING.md): Build, model server, gateway, and device diagnostics.
- [Contributing](CONTRIBUTING.md): Development and review workflow.

## Runtime Flow

```text
Android app
    |
    | authenticated WebSocket
    v
OpenClaw gateway :18789
    |
    | OpenAI-compatible HTTP
    v
llama-server :8081
    |
    v
gemma-4-E2B-it-qat-UD-Q4_K_XL.gguf
```

The GGUF remains outside Git and outside the APK. The Android project has no C++, CMake, native bridge, bundled tokenizer, or on-device model-loading path.
