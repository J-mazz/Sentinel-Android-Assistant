#!/usr/bin/env bash

set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly DEFAULT_MODEL="$ROOT_DIR/gemma-4-E2B-it-qat-UD-Q4_K_XL.gguf"
readonly MODEL_PATH="${SENTINEL_MODEL_PATH:-$DEFAULT_MODEL}"
readonly SERVER_BIN="${LLAMA_SERVER:-llama-server}"
readonly MODEL_ALIAS="gemma-4-e2b-it"
readonly HOST="${SENTINEL_MODEL_HOST:-127.0.0.1}"
readonly PORT="${SENTINEL_MODEL_PORT:-8081}"
readonly CONTEXT_SIZE="${SENTINEL_CONTEXT_SIZE:-32768}"

if ! command -v "$SERVER_BIN" >/dev/null 2>&1; then
    echo "Error: '$SERVER_BIN' was not found. Install a current llama.cpp build or set LLAMA_SERVER." >&2
    exit 1
fi

if [[ ! -f "$MODEL_PATH" ]]; then
    echo "Error: Gemma GGUF not found at '$MODEL_PATH'." >&2
    echo "Set SENTINEL_MODEL_PATH to use another location." >&2
    exit 1
fi

exec "$SERVER_BIN" \
    --model "$MODEL_PATH" \
    --alias "$MODEL_ALIAS" \
    --host "$HOST" \
    --port "$PORT" \
    --ctx-size "$CONTEXT_SIZE" \
    --parallel 1 \
    --no-ui
