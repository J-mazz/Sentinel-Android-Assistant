# Sentinel Agent Android

A Local, Firewall-Protected, Accessibility-Based Android Agent designed for high-security environments (GrapheneOS) using the Jamba-Reasoning-3B model via JNI.

## Architecture

The system is defined by three strict boundaries:

### 1. The Observer (Kotlin)
- **Component**: `AgentAccessibilityService`
- **Role**: Passive collection of UI state
- **Security**: No business logic - pure observation

### 2. The Cortex (C++23)
- **Component**: `native-lib.cpp` (JNI) with C++23 modules
- **Role**: Host to llama.cpp instance running Jamba-3B
- **Security Layers**:
  - **Ingress**: Regex sanitizer strips control tokens
  - **Context**: Wraps user data in XML tags
  - **Egress**: GBNF grammar enforces strict JSON output

### 3. The Actuator (Kotlin)
- **Component**: `ActionDispatcher`
- **Role**: Parses validated JSON from the Cortex
- **Security**: Checks actions against safe list, requires physical Volume Up confirmation for destructive actions

## Project Structure

```
app/
├── src/main/
│   ├── cpp/                          # Native C++23 code
│   │   ├── sentinel.cppm             # C++23 module
│   │   ├── native-lib.cpp            # JNI bridge
│   │   └── CMakeLists.txt            # Build config
│   ├── java/com/mazzlabs/sentinel/
│   │   ├── core/                     # Native bridge
│   │   ├── service/                  # Accessibility service
│   │   ├── security/                 # Action firewall
│   │   ├── input/                    # Voice input
│   │   ├── overlay/                  # Floating button
│   │   ├── tts/                      # Text-to-speech
│   │   └── ui/                       # Configuration UI
│   ├── assets/
│   │   └── agent.gbnf                # JSON grammar
│   └── res/                          # Android resources
└── build.gradle.kts
```

## Requirements

- **Android Studio**: Ladybug (2024.2.1) or newer
- **Android NDK**: r27+ (for C++23 module support)
- **CMake**: 3.28+
- **Target SDK**: 34 (Android 14)
- **Min SDK**: 34
- **Device**: ARM64 (arm64-v8a)

## Setup

### 1. Clone llama.cpp
```bash
./scripts/setup_llama.sh
```

### 2. Download Model
Download a GGUF model (e.g., Jamba-Reasoning-3B-Q4_K_M.gguf) and copy to device:
```bash
adb push jamba-3b-q4.gguf /data/local/tmp/
```

### 3. Build & Deploy
```bash
./scripts/deploy.sh
```

Or manually:
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb push app/src/main/assets/agent.gbnf /data/local/tmp/
```

## Usage

1. **Enable Service**: Open Sentinel Agent → Enable accessibility service
2. **Load Model**: Enter model path → Load Model
3. **Activate**: Tap floating overlay button or use voice trigger
4. **Command**: Speak or type your command
5. **Confirm**: For destructive actions, press Volume Up to confirm

## Security Features

### Input Sanitization
- Strips LLM control tokens (`<|system|>`, `[INST]`, etc.)
- Blocks dangerous shell commands (`rm -rf`, `dd`, etc.)
- XML-escapes user content to prevent injection

### Output Constraint
- GBNF grammar ensures ONLY valid JSON output
- Restricted action vocabulary: `CLICK`, `SCROLL`, `TYPE`, `HOME`, `BACK`, `WAIT`, `NONE`

### Action Firewall
- Heuristic analysis of action targets
- Dangerous actions require physical Volume Up confirmation
- Safe list for benign operations

### Privacy
- 100% on-device processing
- No network permissions
- No data exfiltration possible

## GBNF Grammar

The grammar enforces this JSON structure:
```json
{
  "action": "CLICK|SCROLL|TYPE|HOME|BACK|WAIT|NONE",
  "target": "optional element identifier",
  "text": "optional text for TYPE",
  "direction": "UP|DOWN|LEFT|RIGHT for SCROLL",
  "confidence": 0.0-1.0,
  "reasoning": "explanation"
}
```

## License

MIT License - See LICENSE file

## Acknowledgments

- [llama.cpp](https://github.com/ggerganov/llama.cpp) - Inference engine
- [GrapheneOS](https://grapheneos.org/) - Security-focused Android
