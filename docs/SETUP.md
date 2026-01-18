# Setup Guide

Complete installation and configuration guide for Sentinel Android Assistant.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Environment Setup](#environment-setup)
- [Model Setup](#model-setup)
- [Building from Source](#building-from-source)
- [Installation](#installation)
- [Configuration](#configuration)
- [Verification](#verification)
- [Troubleshooting Setup](#troubleshooting-setup)

## Prerequisites

### Hardware Requirements

**Minimum**:
- ARM64 Android device (arm64-v8a)
- Android 14 (API 34) or higher
- 4GB RAM
- 8GB free storage

**Recommended**:
- ARM64 with NEON and DOT product support
- Android 14 (API 34) or higher
- 6GB+ RAM
- 16GB free storage
- Support for ARMv8.4-a extensions

**Tested Devices**:
- Google Pixel 7/8 series
- GrapheneOS devices
- Samsung Galaxy S23+

### Software Requirements

**Development Machine**:
- Linux, macOS, or Windows with WSL2
- Android Studio Koala (2024.1.1) or newer
- Android SDK 34
- Android NDK r29 or newer (r27+ works)
- CMake 4.1.2+
- Git
- Python 3.8+ (for model conversion if needed)

**Device Requirements**:
- AccessibilityService permission
- Special Use Foreground Service permission
- Sufficient storage for model (~3GB)

## Environment Setup

### 1. Install Android Studio

Download from: https://developer.android.com/studio

```bash
# Linux (snap)
sudo snap install android-studio --classic

# macOS (Homebrew)
brew install --cask android-studio
```

### 2. Install SDK Components

Open Android Studio → SDK Manager:

**SDK Platforms**:
- ☑ Android 14.0 (API 34)

**SDK Tools**:
- ☑ Android SDK Build-Tools 34.0.0+
- ☑ NDK (Side by side) version 29.0.14206865
- ☑ CMake version 4.1.2+
- ☑ Android Emulator (optional, for testing)

### 3. Configure Environment Variables

```bash
# Add to ~/.bashrc or ~/.zshrc
export ANDROID_HOME=$HOME/Android/Sdk
export NDK_HOME=$ANDROID_HOME/ndk/29.0.14206865
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
```

Reload shell:
```bash
source ~/.bashrc  # or ~/.zshrc
```

### 4. Clone Repository

```bash
git clone https://github.com/your-org/Sentinel-Android-Assistant.git
cd Sentinel-Android-Assistant
```

### 5. Initialize Submodules

The project uses llama.cpp as a submodule:

```bash
git submodule update --init --recursive
```

Or use the setup script:

```bash
./scripts/setup_llama.sh
```

This script:
1. Initializes llama.cpp submodule
2. Checks out a stable commit
3. Verifies required files exist

## Model Setup

Sentinel requires a GGUF format model. We recommend Jamba-Reasoning-3B.

### Option 1: Download Pre-Quantized Model (Recommended)

```bash
# Download from Hugging Face
wget https://huggingface.co/ai21labs/Jamba-Reasoning-3B-GGUF/resolve/main/jamba-reasoning-3b-Q4_K_M.gguf

# Or using huggingface-cli
pip install huggingface-hub
huggingface-cli download ai21labs/Jamba-Reasoning-3B-GGUF \
    jamba-reasoning-3b-Q4_K_M.gguf --local-dir ./models/
```

### Option 2: Convert and Quantize Custom Model

If you have a different model:

```bash
# Install llama.cpp Python requirements
cd libs/llama.cpp
pip install -r requirements.txt

# Convert HuggingFace model to GGUF
python convert_hf_to_gguf.py /path/to/model --outfile model-f16.gguf

# Quantize to Q4_K_M (recommended)
./llama-quantize model-f16.gguf model-Q4_K_M.gguf Q4_K_M
```

**Supported Quantizations**:
- Q4_K_M: 4-bit, medium quality, ~2GB (recommended)
- Q5_K_M: 5-bit, high quality, ~2.5GB
- Q8_0: 8-bit, very high quality, ~3.5GB
- Q2_K: 2-bit, experimental, ~1.5GB

### Push Model to Device

```bash
# Enable USB debugging on your device
# Settings → About → Tap "Build number" 7 times
# Settings → Developer options → USB debugging

# Connect device via USB
adb devices  # Verify device is connected

# Push model to device
adb push jamba-reasoning-3b-Q4_K_M.gguf /data/local/tmp/sentinel_model.gguf

# Verify model was transferred
adb shell ls -lh /data/local/tmp/sentinel_model.gguf
```

**Model Path in Code**:

Update `MainActivity.kt` if using a different path:
```kotlin
private val MODEL_PATH = "/data/local/tmp/sentinel_model.gguf"
```

Or use app's files directory (requires copying via app):
```kotlin
private val MODEL_PATH = "${context.filesDir}/sentinel_model.gguf"
```

### Grammar Files

Grammar files are included in `app/src/main/assets/*.gbnf`. They are automatically bundled in the APK.

**Available Grammars**:
- `agent.gbnf`: Generic JSON output
- `intent.gbnf`: Intent classification
- `entities.gbnf`: Entity extraction
- `risk.gbnf`: Risk assessment
- `plan.gbnf`: Multi-step planning
- `tool_params.gbnf`: Tool parameter extraction

## Building from Source

### Debug Build

```bash
# From project root
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Release Build

First, create a keystore:

```bash
keytool -genkey -v -keystore sentinel-release-key.jks \
    -alias sentinel -keyalg RSA -keysize 2048 -validity 10000
```

Create `keystore.properties` in project root:
```properties
storePassword=your_store_password
keyPassword=your_key_password
keyAlias=sentinel
storeFile=sentinel-release-key.jks
```

Build release APK:
```bash
./gradlew assembleRelease

# Output: app/build/outputs/apk/release/app-release.apk
```

### Build with Tests and Coverage

```bash
# Run all checks (tests + coverage gate)
./gradlew check

# Generate coverage report
./gradlew koverXmlReport
# Report: build/reports/kover/report.xml

# View HTML report
./gradlew koverHtmlReport
open build/reports/kover/html/index.html
```

### Native Library Build

Native libraries are built automatically during APK assembly via CMake.

To rebuild native code only:
```bash
./gradlew externalNativeBuildDebug
```

Build artifacts:
- `app/build/intermediates/cmake/debug/obj/arm64-v8a/libsentinel.so`

## Installation

### Install via ADB

```bash
# Install debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or use Gradle
./gradlew installDebug

# Launch app
adb shell am start -n com.mazzlabs.sentinel/.ui.MainActivity
```

### Install via Android Studio

1. Open project in Android Studio
2. Connect device or start emulator
3. Click "Run" (▶) button
4. Select device
5. App will build, install, and launch

### Install via F-Droid (Future)

Not yet available. Planned for future release.

## Configuration

### 1. Grant Permissions

On first launch, the app will request:

**Required**:
- ☑ **Accessibility Service**: Required for UI control
  - Settings → Accessibility → Sentinel → Enable
- ☑ **Notification Access**: For foreground service status
  - Automatically granted when accessibility is enabled

**Optional** (per-tool):
- Calendar: Read/Write calendar
- Contacts: Read/Write contacts
- SMS: Send/Receive messages
- Alarms: Set alarms

### 2. Load Model

In MainActivity:
1. Tap "Load Model"
2. Wait for model to initialize (10-30 seconds)
3. Status will show "Model Loaded Successfully"

**Model Info**:
- Path: `/data/local/tmp/sentinel_model.gguf`
- Vocab size: ~100K tokens
- Context length: 32K tokens
- Load time: 10-30 seconds (device-dependent)

### 3. Configure Inference Parameters (Optional)

Default parameters are tuned for best performance:

```kotlin
nativeBridge.setInferenceParams(
    temperature = 0.7f,  // Creativity (0.0-1.0)
    topP = 0.9f,         // Nucleus sampling
    maxTokens = 512      // Max output length
)
```

To modify, edit in `MainActivity.kt` after model load.

### 4. Enable Accessibility Service

**Critical Step**:

1. Go to Settings → Accessibility
2. Find "Sentinel Agent"
3. Toggle ON
4. Confirm security warning
5. Service should now appear in notification area

**Verify**:
```bash
adb shell dumpsys accessibility | grep Sentinel
# Should show service as enabled
```

### 5. Configure Overlay Permission (Optional)

For floating overlay button:

1. Settings → Apps → Sentinel → Display over other apps
2. Toggle ON

The overlay button appears after enabling accessibility service.

## Verification

### System Check

```bash
# Check app is installed
adb shell pm list packages | grep sentinel

# Check accessibility service is running
adb shell dumpsys accessibility | grep Sentinel

# Check model file exists
adb shell ls -lh /data/local/tmp/sentinel_model.gguf

# View logs
adb logcat -s AgentAccessibilityService NativeBridge
```

### Functionality Test

**Test 1: Voice Command**
1. Tap overlay button
2. Say "What time is it?"
3. Verify response appears

**Test 2: Screen Action**
1. Say "Scroll down"
2. Verify screen scrolls

**Test 3: Tool Call**
1. Say "What's on my calendar today?"
2. Grant calendar permission if prompted
3. Verify calendar events are listed

### Verify Native Library

```bash
# Check native library is included in APK
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep libsentinel.so

# Should show:
# lib/arm64-v8a/libsentinel.so

# Check library info
adb shell
cd /data/app/com.mazzlabs.sentinel-*/lib/arm64/
file libsentinel.so
```

## Troubleshooting Setup

### Build Issues

**Problem**: "NDK not found"
```
Solution:
- Open Android Studio → SDK Manager
- Install NDK (Side by side)
- Sync Gradle
```

**Problem**: "CMake not found"
```
Solution:
- Android Studio → SDK Manager → SDK Tools
- Install CMake
- Sync Gradle
```

**Problem**: "llama.cpp files not found"
```
Solution:
./scripts/setup_llama.sh
git submodule update --init --recursive
```

**Problem**: "C++23 features not supported"
```
Solution:
- Ensure NDK r27+ is installed
- Check app/build.gradle.kts has: cppFlags += "-std=c++23"
```

### Model Issues

**Problem**: "Model not loading"
```
Check:
1. Model file exists: adb shell ls /data/local/tmp/*.gguf
2. File size is correct: ~2-3GB for Q4_K_M
3. Logs: adb logcat -s NativeBridge
```

**Problem**: "Model format not supported"
```
Solution:
- Ensure model is in GGUF format (not GGML or PyTorch)
- Re-download or re-convert model
- Use llama.cpp version matching submodule commit
```

**Problem**: "Out of memory when loading model"
```
Solution:
- Use smaller quantization (Q4_K_M → Q2_K)
- Reduce context window in native_inference.cpp
- Close other apps
```

### Permission Issues

**Problem**: "Accessibility service won't enable"
```
Check:
1. App is installed correctly
2. AndroidManifest has <service> declaration
3. accessibility_service_config.xml exists
4. Device is not restricted (work profile, Knox, etc.)
```

**Problem**: "Tool permissions not working"
```
Solution:
- Request at runtime in tool module
- Check AndroidManifest has <uses-permission>
- Verify device security policies allow app access
```

### Runtime Issues

**Problem**: "App crashes on launch"
```
Debug:
adb logcat -s AndroidRuntime
# Check for stack trace
```

**Problem**: "Inference takes too long"
```
Optimize:
- Reduce maxTokens parameter
- Use smaller model
- Increase GPU layers (if supported)
- Check device isn't thermal throttling
```

**Problem**: "Actions not executing"
```
Check:
1. Accessibility service is enabled
2. adb logcat -s ActionDispatcher
3. Action firewall isn't blocking
4. UI hasn't changed (staleness detection)
```

### Device-Specific Issues

**GrapheneOS**:
- May require enabling "Allow sensor access" in app settings
- Verify "Special Use" foreground service is allowed

**Samsung Devices**:
- Disable battery optimization for Sentinel
- Add to "Never sleeping apps" list

**Xiaomi/MIUI**:
- Grant "Autostart" permission
- Disable battery restrictions

## Next Steps

After successful setup:

1. Read [Quick Start Guide](QUICKSTART.md) for first steps
2. Explore [Tool Documentation](TOOLS.md) for available capabilities
3. Review [Security Model](SECURITY.md) to understand protections
4. See [Development Guide](DEVELOPMENT.md) if you want to contribute

---

**Support**: If you encounter issues not covered here, please open an issue on GitHub with:
- Device model and Android version
- Build logs (if build failure)
- Logcat output (if runtime failure)
- Steps to reproduce
