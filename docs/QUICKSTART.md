# Quick Start Guide

Get Sentinel up and running in 10 minutes.

## Prerequisites

- ARM64 Android device with Android 14+
- USB cable
- Computer with ADB installed
- Separate machine for OpenClaw Gateway (desktop, NAS, or VM)
- Network connectivity between device and gateway

## Step-by-Step

### 1. Set Up OpenClaw Gateway

**On your gateway server** (desktop, NAS, or VM):

```bash
# Install OpenClaw
curl -fsSL https://openclaw.com/install.sh | sh

# Configure gateway
openclaw gateway init
openclaw gateway config set model.provider anthropic  # or openai, local, etc.
openclaw gateway config set model.name claude-sonnet-4
openclaw gateway config set api.anthropic.key sk-ant-...  # if using cloud provider

# Set authentication token
openclaw gateway config set auth.token $(openssl rand -hex 32)

# Start gateway
openclaw gateway start

# Note your gateway IP address
hostname -I  # or ifconfig
# Example: 192.168.1.100
```

**Verify gateway is running**:
```bash
openclaw gateway status
# Should show: Running on ws://0.0.0.0:8080
```

### 2. Enable Developer Options on Device

On your Android device:
1. Settings → About phone
2. Tap "Build number" 7 times
3. Settings → Developer options
4. Enable "USB debugging"

### 3. Install App

**Option A: Pre-built APK**
```bash
# Connect device via USB
adb devices  # Verify device is connected

# Install APK
adb install -r sentinel-release.apk
```

**Option B: Build from source**
```bash
git clone https://github.com/your-org/Sentinel-Android-Assistant.git
cd Sentinel-Android-Assistant
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4. Configure Gateway Connection

On your device:

1. Open Sentinel app
2. Tap "Settings"
3. Enter Gateway URL: `ws://192.168.1.100:8080` (replace with your IP)
4. Enter Auth Token (from gateway config):
   ```bash
   # On gateway server, get token:
   openclaw gateway config get auth.token
   ```
5. Tap "Save"
6. Tap "Test Connection"
7. Verify "Connected" status

### 5. Grant Permissions

On your device:

1. Go to Settings → Accessibility
2. Find "Sentinel Agent"
3. Toggle ON
4. Confirm security warning
5. Return to Sentinel app
6. Grant overlay permission if prompted

### 6. Try It Out!

**Test 1: Simple Query**
1. Tap the floating overlay button
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

### "Cannot connect to gateway"
- Check gateway is running: `openclaw gateway status`
- Verify network connectivity: `ping 192.168.1.100` (from device)
- Check firewall on gateway server: `sudo ufw allow 8080`
- Verify correct URL in app settings

### "Gateway connection timeout"
- Check network latency between device and gateway
- Verify gateway is responsive: `curl http://192.168.1.100:8080/health`
- Check gateway logs: `openclaw gateway logs`

### "Accessibility service won't enable"
- Ensure app is installed
- Check device restrictions (Knox, work profile)
- Restart device

### "No response to voice commands"
- Verify gateway connection (green status in app)
- Check accessibility service is ON
- See logs: `adb logcat -s AgentAccessibilityService GatewayBridge`
- Check gateway logs: `openclaw gateway logs --follow`

## Tips for Best Experience

**Performance**:
- Use gateway on same LAN for lowest latency
- Claude Haiku or GPT-4 Mini for fastest responses
- Keep queries concise

**Security**:
- Use TLS for gateway connection: `wss://` instead of `ws://`
- Keep auth token secure
- Use VPN if accessing gateway remotely
- Review [Security Model](SECURITY.md) for details

**Privacy**:
- Gateway stays on your network (LAN or VPN)
- No third-party services involved
- You control both device and gateway
- Traffic never leaves your infrastructure

**Battery**:
- Disable accessibility service when not in use
- Exit app when done
- Gateway connection uses minimal battery (WebSocket keepalive)

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

## Gateway Model Options

You can configure different model backends on the gateway:

**Cloud Models** (requires API key):
```bash
# Anthropic Claude
openclaw gateway config set model.provider anthropic
openclaw gateway config set model.name claude-sonnet-4

# OpenAI GPT
openclaw gateway config set model.provider openai
openclaw gateway config set model.name gpt-4-turbo
```

**Local Models** (privacy-focused):
```bash
# Local llama.cpp
openclaw gateway config set model.provider local
openclaw gateway config set model.path /path/to/model.gguf
openclaw gateway config set model.context_size 32768
```

**Performance Comparison**:
- **Claude Opus**: Highest quality, slower (~2-5s)
- **Claude Sonnet**: Balanced (~1-3s)
- **Claude Haiku**: Fast, good quality (~0.5-1.5s)
- **GPT-4 Turbo**: High quality (~1-3s)
- **Local llama.cpp**: Privacy-first, speed varies by hardware

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

**Welcome to Sentinel!** Enjoy your privacy-focused AI assistant with the power of modern LLMs.
