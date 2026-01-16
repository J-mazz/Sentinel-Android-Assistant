#!/bin/bash
# setup_llama.sh - Clone and configure llama.cpp for Android build
# Run this from the project root directory

set -e

LLAMA_CPP_DIR="app/src/main/cpp/libs/llama.cpp"
LLAMA_REPO="https://github.com/ggerganov/llama.cpp.git"
LLAMA_TAG="b4269"  # Specify a stable release tag

echo "=== Sentinel Agent - llama.cpp Setup ==="

# Check if directory exists
if [ -d "$LLAMA_CPP_DIR" ]; then
    echo "llama.cpp already exists at $LLAMA_CPP_DIR"
    read -p "Do you want to update it? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        cd "$LLAMA_CPP_DIR"
        git fetch --tags
        git checkout "$LLAMA_TAG"
        cd -
        echo "Updated to $LLAMA_TAG"
    fi
else
    echo "Cloning llama.cpp..."
    mkdir -p "$(dirname $LLAMA_CPP_DIR)"
    git clone --depth 1 --branch "$LLAMA_TAG" "$LLAMA_REPO" "$LLAMA_CPP_DIR"
    echo "Cloned llama.cpp $LLAMA_TAG"
fi

# Create placeholder for the model (user must download separately)
MODEL_DIR="app/src/main/assets/models"
mkdir -p "$MODEL_DIR"

cat > "$MODEL_DIR/README.md" << 'EOF'
# Model Files

Place your GGUF model files here or in `/data/local/tmp/` on the device.

## Recommended Model
- **Jamba-Reasoning-3B-Q4_K_M.gguf** (~2GB)

## Download Instructions
1. Download from HuggingFace or your preferred source
2. Copy to device: `adb push model.gguf /data/local/tmp/`
3. Update path in the app settings

## Memory Requirements
- Q4_K_M: ~3GB RAM
- Q5_K_M: ~3.5GB RAM
- Q8_0: ~4GB RAM
EOF

echo ""
echo "=== Setup Complete ==="
echo ""
echo "Next steps:"
echo "1. Download a GGUF model (e.g., Jamba-Reasoning-3B-Q4_K_M.gguf)"
echo "2. Copy model to device: adb push model.gguf /data/local/tmp/"
echo "3. Copy grammar to device: adb push app/src/main/assets/agent.gbnf /data/local/tmp/"
echo "4. Build the project: ./gradlew assembleDebug"
echo ""
