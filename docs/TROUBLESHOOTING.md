# Troubleshooting Guide

Common issues and solutions for Sentinel Android Assistant.

## Table of Contents

- [Installation Issues](#installation-issues)
- [Model Loading Issues](#model-loading-issues)
- [Accessibility Service Issues](#accessibility-service-issues)
- [Inference Problems](#inference-problems)
- [Tool Execution Issues](#tool-execution-issues)
- [Performance Issues](#performance-issues)
- [Device-Specific Issues](#device-specific-issues)

## Installation Issues

### APK Won't Install

**Symptom**: `adb install` fails with "INSTALL_FAILED_*"

**Solutions**:

1. **INSTALL_FAILED_INSUFFICIENT_STORAGE**
   ```bash
   # Check available space
   adb shell df -h /data

   # Clear space or use smaller model
   ```

2. **INSTALL_FAILED_UPDATE_INCOMPATIBLE**
   ```bash
   # Uninstall old version first
   adb uninstall com.mazzlabs.sentinel
   adb install app-debug.apk
   ```

3. **INSTALL_FAILED_CPU_ABI_INCOMPATIBLE**
   ```
   Error: Device is not ARM64
   Solution: Sentinel only supports arm64-v8a devices
   Verify: adb shell getprop ro.product.cpu.abi
   ```

### Native Library Missing

**Symptom**: `UnsatisfiedLinkError: dlopen failed: library "libsentinel.so" not found`

**Cause**: Native library wasn't built or included in APK

**Solutions**:
```bash
# Rebuild native code
./gradlew externalNativeBuildDebug

# Verify library in APK
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep libsentinel.so

# Should show: lib/arm64-v8a/libsentinel.so

# If missing, check NDK installation
android-studio → SDK Manager → NDK (Side by side)
```

## Model Loading Issues

### Model Not Found

**Symptom**: "Model file not found: /data/local/tmp/sentinel_model.gguf"

**Solutions**:
```bash
# Verify model exists
adb shell ls -lh /data/local/tmp/*.gguf

# If missing, push again
adb push jamba-reasoning-3b-Q4_K_M.gguf /data/local/tmp/sentinel_model.gguf

# Check file size (should be ~2-3GB for Q4_K_M)
adb shell ls -lh /data/local/tmp/sentinel_model.gguf
```

### Model Loading Hangs

**Symptom**: "Loading model..." never completes

**Debug**:
```bash
# Check logs
adb logcat -s NativeBridge

# Look for specific errors:
# - "Failed to load model"
# - "Out of memory"
# - "Invalid GGUF format"
```

**Solutions**:
1. **Out of Memory**:
   - Close other apps
   - Use smaller quantization (Q4_K_M → Q2_K)
   - Reduce context window in `native_inference.cpp`

2. **Corrupted Model**:
   - Verify file integrity
   - Re-download model
   - Check hash: `md5sum model.gguf`

3. **Wrong Format**:
   - Ensure model is GGUF (not GGML or PyTorch)
   - Convert: `llama-convert.py model.bin model.gguf`

### Model Version Mismatch

**Symptom**: "Unsupported GGUF version"

**Cause**: llama.cpp version mismatch

**Solution**:
```bash
# Update llama.cpp submodule
git submodule update --remote libs/llama.cpp

# Or checkout specific commit
cd libs/llama.cpp
git checkout <commit-hash>
cd ../..

# Rebuild
./gradlew clean assembleDebug
```

## Accessibility Service Issues

### Service Won't Enable

**Symptom**: Toggle in Settings → Accessibility won't turn on

**Causes & Solutions**:

1. **App Not Installed Properly**
   ```bash
   adb shell pm list packages | grep sentinel
   # Should show: package:com.mazzlabs.sentinel
   ```

2. **Missing Service Declaration**
   - Verify `AndroidManifest.xml` has `<service>` tag
   - Check `accessibility_service_config.xml` exists

3. **Device Security Policy**
   - GrapheneOS: Check "Special Use" permission
   - Knox/Work Profile: May restrict accessibility
   - Some OEMs block third-party accessibility services

### Service Crashes When Enabled

**Symptom**: Service enables but immediately crashes

**Debug**:
```bash
adb logcat -s AndroidRuntime AgentAccessibilityService

# Look for:
# - NullPointerException
# - UninitializedPropertyAccessException
# - SecurityException
```

**Common Causes**:
1. **Model not loaded**: Load model before enabling service
2. **Missing permissions**: Grant required permissions
3. **Native library issue**: Check `System.loadLibrary("sentinel")`

### Service Doesn't Detect UI Changes

**Symptom**: Actions don't execute or screen context is empty

**Debug**:
```bash
adb shell dumpsys accessibility | grep Sentinel

# Should show:
# - Service enabled: true
# - Event types: TYPE_WINDOW_STATE_CHANGED, TYPE_WINDOW_CONTENT_CHANGED
```

**Solutions**:
- Restart accessibility service: Toggle off/on
- Reboot device
- Check `onAccessibilityEvent()` is being called
- Verify `accessibilityEventTypes` in config XML

## Inference Problems

### Inference Too Slow

**Symptom**: Queries take 30+ seconds

**Causes**:
1. **Device throttling**: Check temperature
2. **Large model**: Q8_0 or larger
3. **Long context**: Too much screen context

**Solutions**:
```kotlin
// Reduce max tokens
nativeBridge.setInferenceParams(
    maxTokens = 256  // Instead of 512
)

// Limit screen context
elementRegistry.toPromptString(maxElements = 30)  // Instead of 60

// Use smaller model
Q4_K_M (~2GB) or Q2_K (~1.5GB)
```

### Inference Returns Garbage

**Symptom**: Output is malformed JSON or nonsense

**Causes**:
1. **Wrong grammar file**
2. **Temperature too high**
3. **Corrupted model**

**Debug**:
```bash
adb logcat -s NativeBridge

# Check for:
# - "Grammar path: ..."
# - "Inference result: {...}"
```

**Solutions**:
```kotlin
// Reduce temperature
nativeBridge.setInferenceParams(temperature = 0.3f)

// Verify grammar file
adb shell ls -l /data/data/com.mazzlabs.sentinel/files/*.gbnf

// Re-push grammar
adb push app/src/main/assets/agent.gbnf /data/local/tmp/
```

### "Grammar Constraint Violated"

**Symptom**: Error: "Failed to generate valid JSON"

**Cause**: Model couldn't satisfy grammar constraints

**Solutions**:
1. **Simplify query**: Break into simpler queries
2. **Increase max tokens**: Model ran out of space
3. **Different grammar**: Try `intent.gbnf` instead of `agent.gbnf`

## Tool Execution Issues

### "Permission Denied"

**Symptom**: Tool returns `ToolResponse.PermissionRequired`

**Solution**:
```bash
# Check what permissions app has
adb shell dumpsys package com.mazzlabs.sentinel | grep permission

# Grant missing permission
adb shell pm grant com.mazzlabs.sentinel android.permission.READ_CALENDAR

# Or grant via UI:
# Settings → Apps → Sentinel → Permissions
```

### Calendar Tool Returns Empty

**Symptom**: Calendar query returns no events (but events exist)

**Causes**:
1. **Wrong calendar ID**: Querying wrong calendar
2. **Date range issue**: Events outside query range
3. **Sync issues**: Calendar not synced

**Debug**:
```kotlin
// List all calendars
val response = calendarModule.execute(
    operationId = "get_calendars",
    params = emptyMap(),
    context = context
)

// Check date parsing
Log.d(TAG, "Start date: ${parseNaturalDate("today")}")
```

### Terminal Command Blocked

**Symptom**: "Blocked command: rm"

**Cause**: Command in firewall blacklist

**Solutions**:
- Use safe alternative
- If legitimate, add to whitelist (with confirmation)
- Check `BLOCKED_COMMANDS` in `TerminalModule.kt`

### SMS Not Sending

**Symptom**: `send_sms` fails silently

**Causes**:
1. **No SMS permission**
2. **Rate limited** (max 1 per 5 seconds)
3. **Invalid phone number**
4. **Device in airplane mode**

**Debug**:
```bash
adb logcat -s MessagingModule

# Check for:
# - "SMS permission denied"
# - "Rate limit exceeded"
# - "Invalid recipient"
```

## Performance Issues

### High Battery Drain

**Causes**:
- Foreground service running continuously
- Frequent inference calls
- Screen capture overhead

**Solutions**:
1. **Disable when not in use**:
   - Turn off accessibility service
   - Exit app

2. **Reduce inference frequency**:
   - Use UI actions instead of tools when possible
   - Batch multiple queries

3. **Optimize model**:
   - Use smaller quantization
   - Reduce context window

### High Memory Usage

**Symptom**: App uses 3-4GB RAM

**Cause**: Model + KV cache + app memory

**Normal**:
- Q4_K_M: ~2-3GB
- Q8_0: ~3.5-4GB

**If excessive (>4GB)**:
1. **Check for leaks**:
   ```bash
   adb shell dumpsys meminfo com.mazzlabs.sentinel
   ```

2. **Verify resource cleanup**:
   - AccessibilityNodeInfo recycled
   - Stream readers closed
   - Bitmaps recycled

3. **Use smaller model**

### App Crashes (OOM)

**Symptom**: "OutOfMemoryError"

**Solutions**:
```kotlin
// Reduce context window (in native_inference.cpp)
ctx_params.n_ctx = 16384;  // Instead of 32768

// Use Q2_K quantization
// Or close other apps
```

## Device-Specific Issues

### GrapheneOS

**Issue**: Service won't enable

**Solution**:
- Enable "Special Use" foreground service
- Allow sensor access if needed
- Check Storage Scopes setting

### Samsung (One UI)

**Issue**: Background service killed

**Solution**:
1. Disable battery optimization:
   - Settings → Apps → Sentinel → Battery → Unrestricted
2. Add to "Never sleeping apps"
3. Disable "Put unused apps to sleep"

### Xiaomi (MIUI)

**Issue**: Accessibility service disabled on reboot

**Solution**:
1. Enable "Autostart"
2. Lock app in recent apps
3. Settings → Battery & Performance → Power → Battery Saver → Disable for Sentinel

### OnePlus (OxygenOS)

**Issue**: Overlay button disappears

**Solution**:
- Settings → Special app access → Display over other apps → Sentinel → Allow

### Pixel (Stock Android)

**Issue**: "Special use" service warning

**Explanation**: This is expected for AI inference services
**Action**: Select "Continue" when prompted

## Logging and Diagnostics

### Enable Verbose Logging

```bash
# Set log level to DEBUG
adb shell setprop log.tag.AgentAccessibilityService DEBUG
adb shell setprop log.tag.NativeBridge DEBUG
adb shell setprop log.tag.ActionDispatcher DEBUG

# Filter logs
adb logcat -s AgentAccessibilityService:D NativeBridge:D ActionDispatcher:D
```

### Capture Complete Log

```bash
# Clear old logs
adb logcat -c

# Reproduce issue
# Then save log
adb logcat -d > sentinel_issue.log

# Or live capture
adb logcat | tee sentinel_issue.log
```

### Check System State

```bash
# Check accessibility service
adb shell dumpsys accessibility | grep Sentinel

# Check app permissions
adb shell dumpsys package com.mazzlabs.sentinel | grep permission

# Check memory
adb shell dumpsys meminfo com.mazzlabs.sentinel

# Check CPU usage
adb shell top -n 1 | grep sentinel
```

### Native Debugging

```bash
# Enable native debugging in build.gradle.kts
android {
    buildTypes {
        debug {
            isDebuggable = true
            isJniDebuggable = true
        }
    }
}

# Attach LLDB debugger in Android Studio
Run → Attach Debugger to Android Process → Select Sentinel
```

## Still Having Issues?

If your issue isn't covered here:

1. **Search GitHub Issues**: Someone may have had the same problem
2. **Enable verbose logging**: Capture detailed logs
3. **Create minimal reproducer**: Simplest steps to trigger issue
4. **Open GitHub Issue**: Include device info, logs, and steps

**Useful Information to Include**:
- Device model and Android version
- Sentinel version/commit
- Model name and quantization
- Logcat output
- Steps to reproduce
- Expected vs actual behavior

**Example Issue Report**:
```markdown
**Device**: Google Pixel 7, Android 14 (API 34)
**Sentinel Version**: Commit ea04fde
**Model**: Jamba-3B Q4_K_M (2.8GB)

**Issue**: Calendar tool returns empty even though events exist

**Steps**:
1. Grant calendar permission
2. Say "What's on my calendar today?"
3. Response: "No events found"

**Expected**: Should list 3 events
**Actual**: Returns empty

**Logs**:
```
[logcat output]
```
```

---

For security issues, email: security@mazzlabs.com (do not open public issue)
