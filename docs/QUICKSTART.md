# Quick Start Guide

Get Sentinel up and running in 5 minutes.

## Prerequisites

- ARM64 Android device with Android 14+
- USB cable
- Computer with ADB installed
- ~5GB free space on device

## Step-by-Step

### 1. Download Model

```bash
# Download Jamba-3B Q4_K_M (~2.8GB)
wget https://huggingface.co/ai21labs/Jamba-Reasoning-3B-GGUF/resolve/main/jamba-reasoning-3b-Q4_K_M.gguf
```

### 2. Enable Developer Options

On your device:
1. Settings → About phone
2. Tap "Build number" 7 times
3. Settings → Developer options
4. Enable "USB debugging"

### 3. Push Model to Device

```bash
# Connect device via USB
adb devices  # Verify device is connected

# Push model (takes 2-3 minutes)
adb push jamba-reasoning-3b-Q4_K_M.gguf /data/local/tmp/sentinel_model.gguf
```

### 4. Install App

**Option A: Pre-built APK**
```bash
# If you have a pre-built APK
adb install -r sentinel-release.apk
```

**Option B: Build from source**
```bash
git clone https://github.com/your-org/Sentinel-Android-Assistant.git
cd Sentinel-Android-Assistant
./scripts/setup_llama.sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 5. Grant Permissions

On your device:

1. Open Sentinel app
2. Tap "Load Model"
3. Wait 10-30 seconds for model to load
4. Go to Settings → Accessibility
5. Find "Sentinel Agent"
6. Toggle ON
7. Confirm security warning

### 6. Try It Out!

**Test 1: Simple Query**
1. Tap the floating overlay button (appears after enabling accessibility)
2. Say "What time is it?"
3. See response appear

**Test 2: UI Action**
1. Tap overlay button
2. Say "Scroll down"
3. Watch screen scroll

**Test 3: Tool Use**
1. Grant calendar permission when prompted:
   - Settings → Apps → Sentinel → Permissions → Calendar → Allow
2. Tap overlay button
3. Say "What's on my calendar today?"
4. See your events

## Next Steps

- **Learn more**: Read [Architecture Overview](ARCHITECTURE.md)
- **Create tools**: See [Tool Development](TOOLS.md)
- **Security**: Understand [Security Model](SECURITY.md)
- **Troubleshoot**: Check [Troubleshooting Guide](TROUBLESHOOTING.md)

## Common First-Time Issues

### "Model not loading"
- Check model exists: `adb shell ls /data/local/tmp/*.gguf`
- Verify size: Should be ~2-3GB
- See logs: `adb logcat -s NativeBridge`

### "Accessibility service won't enable"
- Ensure app is installed
- Check device restrictions (Knox, work profile)
- Restart device

### "No response to voice commands"
- Verify model loaded successfully (green checkmark in app)
- Check accessibility service is ON
- See logs: `adb logcat -s AgentAccessibilityService`

## Tips for Best Experience

**Performance**:
- Close other apps when using Sentinel
- Use Q4_K_M quantization for best speed/quality balance
- Keep queries concise

**Battery**:
- Disable accessibility service when not in use
- Exit app when done

**Privacy**:
- Verify no network access: `adb shell dumpsys package com.mazzlabs.sentinel | grep INTERNET`
- Should show nothing (no INTERNET permission)

**Security**:
- Review [Security Model](SECURITY.md) to understand protections
- Physical confirmation (Volume Up) required for dangerous actions

## Example Commands

**Information Queries**:
- "What's on my calendar?"
- "Who is John Doe?" (requires contacts permission)
- "Read my recent messages" (requires SMS permission)

**UI Actions**:
- "Scroll down"
- "Click the search button"
- "Type 'hello world'"
- "Go back"

**Multi-Step**:
- "Open settings and scroll to accessibility"
- "Find my calendar event tomorrow and tell me the location"

**Tool Operations**:
- "Create a note titled 'Shopping List' with 'milk, eggs, bread'"
- "Set an alarm for 7:30 AM tomorrow"
- "Send a message to Mom saying 'Running late'"

## Support

**Documentation**:
- [Setup Guide](SETUP.md) - Detailed installation
- [Architecture](ARCHITECTURE.md) - System design
- [Tools](TOOLS.md) - Available capabilities
- [Troubleshooting](TROUBLESHOOTING.md) - Common issues

**Community**:
- GitHub Issues - Bug reports and feature requests
- GitHub Discussions - Questions and community support

**Security**:
- Email: security@mazzlabs.com (for security vulnerabilities)

---

**Welcome to Sentinel!** Enjoy your privacy-focused AI assistant.
