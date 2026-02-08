#!/bin/bash
# deploy.sh - Build and deploy to connected device
# Run this from the project root directory

set -e

echo "=== Sentinel Agent - Build & Deploy ==="

# Source SDKMAN for latest Gradle
[[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"

# Check for connected device
if ! adb devices | grep -q "device$"; then
    echo "Error: No device connected. Please connect your Android device."
    exit 1
fi

DEVICE=$(adb devices | grep "device$" | head -1 | cut -f1)
echo "Target device: $DEVICE"

# Build the app
echo ""
echo "Building debug APK..."
gradle assembleDebug

# Install APK
echo ""
echo "Installing APK..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

echo ""
echo "=== Deployment Complete ==="
echo ""
echo "Next steps:"
echo "1. Open Sentinel Agent on your device"
echo "2. Configure your OpenClaw gateway in Settings"
echo "3. Enable the Accessibility Service in Settings"
echo "4. Test with a voice command or text query"
echo ""
