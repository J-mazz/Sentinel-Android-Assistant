# Sentinel Agent Android

A lightweight, gateway-connected Android agent designed for high-security environments such as GrapheneOS. The default backend is `gemma-4-E2B-it-qat-UD-Q4_K_XL.gguf`, served by llama.cpp through an OpenClaw gateway. The Android app focuses on observation and validated action execution.

## Architecture

The system is defined by three strict boundaries:

### 1. The Observer (Kotlin)
- **Component**: `AgentAccessibilityService`
- **Role**: Passive collection of UI state
- **Security**: No business logic - pure observation

### 2. The Cortex (OpenClaw Gateway - Remote)
- **Component**: OpenClaw gateway instance (running on your own infrastructure)
- **Role**: Hosts inference models and handles reasoning
- **Security Layers**:
  - **Connection**: TLS with token authentication
  - **Firewall**: Network egress policy restricts connections to configured gateway only
  - **Privacy**: All inference happens on YOUR infrastructure (not third-party cloud)

### 3. The Actuator (Kotlin)
- **Component**: `ActionDispatcher`
- **Role**: Parses JSON responses from the gateway and executes actions
- **Security**: Checks actions against safe list, requires physical Volume Up confirmation for destructive actions

## Project Structure

```
app/
├── src/main/
│   ├── java/com/mazzlabs/sentinel/
│   │   ├── core/                     # Agent controller
│   │   ├── gateway/                  # OpenClaw gateway client
│   │   ├── inference/                # Inference routing (remote only)
│   │   ├── service/                  # Accessibility service
│   │   ├── security/                 # Action firewall
│   │   ├── tools/                    # Tool modules
│   │   ├── input/                    # Voice input
│   │   ├── overlay/                  # Floating button
│   │   ├── tts/                      # Text-to-speech
│   │   └── ui/                       # Configuration UI
│   └── res/                          # Android resources
└── build.gradle.kts
```

## Requirements

### Android Device
- **Target SDK**: 34 (Android 14)
- **Min SDK**: 34
- **Device**: ARM64 (arm64-v8a) or any modern Android device
- **OS**: GrapheneOS recommended, but any Android 14+ works

### Development
- **Android Studio**: Ladybug (2024.2.1) or newer
- **JDK**: 17+

### Infrastructure
- **OpenClaw Gateway**: You need a running OpenClaw gateway instance
  - Can be self-hosted (local network or VPS)
  - Defaults to the local Gemma 4 GGUF through llama.cpp
  - Can be reconfigured for another OpenClaw model provider
  - See [OpenClaw documentation](https://github.com/openclaw/openclaw) for setup

## Setup

### 1. Set up the Gemma backend and OpenClaw Gateway
Keep `gemma-4-E2B-it-qat-UD-Q4_K_XL.gguf` in the project root, install a current llama.cpp build, then follow the [Quick Start](docs/QUICKSTART.md). The model file is intentionally excluded from Git.

### 2. Build & Deploy Sentinel
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Configure Gateway Connection
1. Open Sentinel Agent on your device
2. Tap "Settings" → "Gateway Settings"
3. Enter your gateway URL (e.g., `https://gateway.yourdomain.com`)
4. Enter your gateway authentication token
5. Tap "Connect" and verify connection status

### 4. Enable Accessibility Service
1. In Sentinel app, tap "Enable Service"
2. Grant accessibility permissions in system settings
3. Return to app and verify "ACTIVE" status

## Usage

1. **Verify Connection**: Ensure gateway shows "CONNECTED" in main screen
2. **Activate**: Tap floating overlay button or use voice trigger
3. **Command**: Speak or type your command
4. **Confirm**: For destructive actions, press Volume Up to confirm

## Security Features

### Network Security
- TLS-only connections to gateway
- Token-based authentication
- Network egress firewall restricts connections to configured gateway
- No third-party API calls - all inference via YOUR gateway

### Action Firewall
- Heuristic analysis of action targets
- Dangerous actions require physical Volume Up confirmation
- Safe list for benign operations

### Privacy
- All reasoning happens on YOUR infrastructure (self-hosted gateway or gateway with your API keys)
- UI observation data only sent to YOUR gateway
- No third-party telemetry or analytics
- Full data sovereignty - you control the model and infrastructure

### GrapheneOS Integration
- Designed for GrapheneOS's strict security model
- Respects network permissions and restrictions
- Compatible with sandboxed Google Play (if needed)

## Why Gateway-Based?

Gemma runs on gateway hardware instead of inside the Android process. This keeps the app pure Kotlin, avoids shipping a multi-gigabyte model in the APK, reduces device memory pressure, and preserves a user-controlled inference boundary.

## Development

### Running Tests
```bash
./gradlew test
```

### Code Coverage
```bash
./gradlew koverXmlReport
```

## License

MIT License - See LICENSE file

## Acknowledgments

- [OpenClaw](https://github.com/openclaw/openclaw) - Gateway and inference platform
- [llama.cpp](https://github.com/ggml-org/llama.cpp) - Local GGUF inference server
- [GrapheneOS](https://grapheneos.org/) - Security-focused Android
