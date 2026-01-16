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

# Copy grammar file to device
echo ""
echo "Copying grammar file..."
adb push app/src/main/assets/agent.gbnf /data/local/tmp/agent.gbnf

# Copy model if it exists locally
MODEL_LOCAL="jamba-reasoning-3b-Q4_K_M.gguf"
MODEL_DEST="/data/local/tmp/jamba-reasoning-3b-Q4_K_M.gguf"

if [ -f "$MODEL_LOCAL" ]; then
    echo ""
    echo "Copying model file (this may take a while)..."
    adb push "$MODEL_LOCAL" "$MODEL_DEST"
elif adb shell "[ -f $MODEL_DEST ]" 2>/dev/null; then
    echo "Model file already on device at $MODEL_DEST"
else
    echo ""
    echo "WARNING: Model file not found locally or on device"
    echo "Please copy your GGUF model to project root or device"
fi

echo ""
echo "=== Deployment Complete ==="
echo ""
echo "Next steps:"
echo "1. Open Sentinel Agent on your device"
echo "2. Enable the Accessibility Service in Settings"
echo "3. Load the model in the app"
echo "4. Test with a voice command or text query"
echo ""
