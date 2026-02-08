# Sentinel Agent Android

A Lightweight, Gateway-Connected Android Agent designed for high-security environments (GrapheneOS). All reasoning happens via the OpenClaw gateway, while the Android app focuses on observation and action execution.

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
  - Provides access to cloud models (Anthropic, OpenAI, etc.)
  - OR local models via Ollama/llama.cpp integration
  - See [OpenClaw documentation](https://github.com/openclaw/openclaw) for setup

## Setup

### 1. Set up OpenClaw Gateway
Follow the OpenClaw gateway installation guide to set up your inference backend.

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

The previous version ran a 3GB model on-device, which caused:
- Memory pressure and Low Memory Killer (LMK) kills
- GrapheneOS timeout issues
- Limited model capabilities (small quantized models only)
- Complex NDK/C++ build chain

The gateway-based approach provides:
- Access to powerful cloud models (Claude, GPT-4, etc.)
- OR local models via Ollama without device memory constraints
- Simpler Android build (pure Kotlin, no NDK)
- Better reliability (no OOM kills)
- Still private (your infrastructure, your models)

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
- [GrapheneOS](https://grapheneos.org/) - Security-focused Android
