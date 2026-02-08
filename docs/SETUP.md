# Setup Guide

Complete installation and configuration guide for Sentinel Android Assistant.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Environment Setup](#environment-setup)
- [OpenClaw Gateway Setup](#openclaw-gateway-setup)
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
- 2GB RAM
- 500MB free storage

**Recommended**:
- Android 14 (API 34) or higher
- 4GB+ RAM
- 1GB free storage

**Tested Devices**:
- Google Pixel 7/8 series
- GrapheneOS devices
- Samsung Galaxy S23+

**Gateway Server** (separate machine):
- Desktop, NAS, or cloud VM
- Linux, macOS, or Windows
- 4GB+ RAM for local models
- Network access to device (LAN or VPN)

### Software Requirements

**Development Machine**:
- Linux, macOS, or Windows with WSL2
- Android Studio Koala (2024.1.1) or newer
- Android SDK 34
- Git

**Device Requirements**:
- AccessibilityService permission
- Special Use Foreground Service permission
- Network connectivity to gateway

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
- ☑ Android Emulator (optional, for testing)

### 3. Configure Environment Variables

```bash
# Add to ~/.bashrc or ~/.zshrc
export ANDROID_HOME=$HOME/Android/Sdk
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

## OpenClaw Gateway Setup

Sentinel requires an OpenClaw Gateway server for inference. The gateway runs on your own hardware and routes requests to your chosen model backend.

### Option 1: Install OpenClaw (Recommended)

**On Linux/macOS**:

```bash
# Install OpenClaw
curl -fsSL https://openclaw.com/install.sh | sh

# Or download binary
wget https://openclaw.com/releases/latest/openclaw-linux-x64
chmod +x openclaw-linux-x64
sudo mv openclaw-linux-x64 /usr/local/bin/openclaw

# Verify installation
openclaw --version
```

**On Windows**:
```powershell
# Download installer from https://openclaw.com/releases/latest
# Or use Windows binary
```

### Option 2: Docker (Alternative)

```bash
# Pull OpenClaw Gateway image
docker pull openclaw/gateway:latest

# Run gateway
docker run -d \
  --name openclaw-gateway \
  -p 8080:8080 \
  -v ~/.openclaw:/root/.openclaw \
  openclaw/gateway:latest
```

### Configure OpenClaw Gateway

```bash
# Initialize configuration
openclaw gateway init

# Configure model backend
openclaw gateway config set model.provider anthropic  # or openai, local, etc.
openclaw gateway config set model.name claude-sonnet-4

# Set API key (if using cloud provider)
openclaw gateway config set api.anthropic.key sk-ant-...

# Or configure local model
openclaw gateway config set model.provider local
openclaw gateway config set model.path /path/to/model.gguf

# Configure authentication
openclaw gateway config set auth.token your-secure-token-here

# Enable TLS (recommended)
openclaw gateway config set tls.enabled true
openclaw gateway config set tls.cert /path/to/cert.pem
openclaw gateway config set tls.key /path/to/key.pem
```

### Start OpenClaw Gateway

```bash
# Start gateway service
openclaw gateway start

# Verify gateway is running
openclaw gateway status

# View logs
openclaw gateway logs

# Gateway should be accessible at:
# - Local: ws://localhost:8080 (or wss:// if TLS enabled)
# - Network: ws://YOUR_IP:8080
```

### Network Configuration

**For Local Network Access**:
1. Note your gateway server's IP address:
   ```bash
   # Linux/macOS
   ifconfig | grep "inet "
   
   # Or
   hostname -I
   ```

2. Ensure firewall allows connections on port 8080:
   ```bash
   # Linux (ufw)
   sudo ufw allow 8080
   
   # Linux (firewalld)
   sudo firewall-cmd --add-port=8080/tcp --permanent
   sudo firewall-cmd --reload
   ```

**For VPN Access**:
- Configure VPN on both gateway server and Android device
- Use VPN IP address for gateway URL
- Traffic stays encrypted end-to-end

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

## Installation

### Install via ADB

```bash
# Enable USB debugging on your device
# Settings → About → Tap "Build number" 7 times
# Settings → Developer options → USB debugging

# Connect device via USB
adb devices  # Verify device is connected

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

### 1. Configure Gateway Connection

In MainActivity:
1. Tap "Settings"
2. Enter Gateway URL:
   - Local: `ws://192.168.1.100:8080` (replace with your IP)
   - TLS: `wss://192.168.1.100:8080`
3. Enter authentication token (from gateway config)
4. Tap "Save"
5. Tap "Test Connection" to verify

**Configuration File** (alternative):

Create `app/src/main/assets/gateway_config.json`:
```json
{
  "gateway_url": "wss://192.168.1.100:8080",
  "auth_token": "your-secure-token-here",
  "tls_verify": true,
  "allowed_hosts": ["192.168.1.100", "gateway.local"],
  "timeout_ms": 30000
}
```

### 2. Grant Permissions

On first launch, the app will request:

**Required**:
- ☑ **Accessibility Service**: Required for UI control
  - Settings → Accessibility → Sentinel → Enable
- ☑ **Notification Access**: For foreground service status
  - Automatically granted when accessibility is enabled
- ☑ **Network Access**: For gateway communication
  - Automatically granted

**Optional** (per-tool):
- Calendar: Read/Write calendar
- Contacts: Read/Write contacts
- SMS: Send/Receive messages
- Alarms: Set alarms

### 3. Test Gateway Connection

In MainActivity:
1. Tap "Test Connection"
2. Wait for connection status
3. Status should show "Connected to Gateway"
4. Connection info shows:
   - Gateway version
   - Model backend
   - Latency

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

# Check gateway connection
adb logcat -s GatewayBridge | grep "Connected"

# View logs
adb logcat -s AgentAccessibilityService GatewayBridge
```

### Functionality Test

**Test 1: Gateway Connection**
1. Open app
2. Verify "Connected" status in UI
3. Check gateway logs: `openclaw gateway logs`

**Test 2: Voice Command**
1. Tap overlay button
2. Say "What time is it?"
3. Verify response appears

**Test 3: Screen Action**
1. Say "Scroll down"
2. Verify screen scrolls

**Test 4: Tool Call**
1. Say "What's on my calendar today?"
2. Grant calendar permission if prompted
3. Verify calendar events are listed

### Network Verification

```bash
# Monitor gateway traffic (on gateway server)
openclaw gateway logs --follow

# You should see:
# - WebSocket connection established
# - Request: {userQuery: "...", screenContext: "..."}
# - Response: {action: "...", reasoning: "..."}

# Check network connectivity from device
adb shell ping YOUR_GATEWAY_IP
```

## Troubleshooting Setup

### Build Issues

**Problem**: "INTERNET permission denied"
```
Solution: This is expected. INTERNET permission is now required for gateway communication.
Check AndroidManifest.xml includes:
<uses-permission android:name="android.permission.INTERNET" />
```

### Gateway Connection Issues

**Problem**: "Cannot connect to gateway"
```
Check:
1. Gateway is running: openclaw gateway status
2. Network connectivity: ping gateway IP from device
3. Firewall allows port 8080
4. Correct gateway URL in app config
5. Valid authentication token
6. Gateway logs: openclaw gateway logs
```

**Problem**: "TLS certificate verification failed"
```
Solution:
- Use self-signed cert: Set tls_verify: false in config (not recommended)
- Or use proper TLS cert from Let's Encrypt
- Or use ws:// instead of wss:// on trusted network
```

**Problem**: "Connection timeout"
```
Check:
1. Network latency: ping gateway server
2. Gateway timeout settings
3. Device firewall settings
4. VPN configuration (if using VPN)
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
- Check network latency to gateway
- Use faster model on gateway (e.g., Claude Haiku vs Opus)
- Reduce maxTokens parameter
- Check gateway server resources
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
- Network permission required for gateway

**Samsung Devices**:
- Disable battery optimization for Sentinel
- Add to "Never sleeping apps" list
- Allow background network usage

**Xiaomi/MIUI**:
- Grant "Autostart" permission
- Disable battery restrictions
- Allow background data

## Next Steps

After successful setup:

1. Read [Quick Start Guide](QUICKSTART.md) for first steps
2. Explore [Tool Documentation](TOOLS.md) for available capabilities
3. Review [Security Model](SECURITY.md) to understand protections
4. See [Development Guide](DEVELOPMENT.md) if you want to contribute

---

**Support**: If you encounter issues not covered here, please open an issue on GitHub with:
- Device model and Android version
- Gateway configuration
- Build logs (if build failure)
- Logcat output (if runtime failure)
- Gateway logs (if connection failure)
- Steps to reproduce
