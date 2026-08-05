# Setup

## Components

Sentinel uses three independently managed components:

1. The Android app for UI observation and action execution.
2. OpenClaw for authenticated sessions, model routing, and agent orchestration.
3. llama.cpp for local inference with `gemma-4-E2B-it-qat-UD-Q4_K_XL.gguf`.

The model is not copied to Android or packaged in the APK.

## Gemma Model Server

Install a current llama.cpp release that includes `llama-server`. Keep the GGUF in the repository root or set `SENTINEL_MODEL_PATH`.

```bash
./scripts/start_gemma_server.sh
```

Equivalent server options are:

```text
--model gemma-4-E2B-it-qat-UD-Q4_K_XL.gguf
--alias gemma-4-e2b-it
--host 127.0.0.1
--port 8081
--ctx-size 32768
--parallel 1
--no-ui
```

llama.cpp uses the chat template embedded in the GGUF metadata. Do not force an unrelated template.

The file advertises a 131072-token training context. Sentinel defaults to 32768 because allocating the full KV cache is unnecessary for normal mobile-agent requests. Increase `SENTINEL_CONTEXT_SIZE` only after measuring gateway memory use.

## OpenClaw Provider

Create a custom provider named `gemma-local` with:

- Base URL: `http://127.0.0.1:8081/v1`
- API: `openai-completions`
- Model ID: `gemma-4-e2b-it`
- Context window: the same value passed to llama-server
- Maximum output: `4096`

The resulting OpenClaw model reference must be `gemma-local/gemma-4-e2b-it`. This exact value is used by `GatewayConfig.Models.LOCAL_DEFAULT`.

After editing OpenClaw configuration:

```bash
openclaw config validate
openclaw models set gemma-local/gemma-4-e2b-it
openclaw models status
openclaw gateway
```

See [Quick Start](QUICKSTART.md) for a complete JSON5 provider example.

## Gateway Exposure

OpenClaw listens on port `18789` in this project.

Recommended options:

- Same host only: `bind: "loopback"`
- Android on a trusted private LAN: `bind: "lan"` with token authentication
- Remote access: loopback binding behind Tailscale, a VPN, or a TLS reverse proxy

Never expose an unauthenticated `ws://` gateway to the public internet. The llama.cpp endpoint should remain loopback-only because OpenClaw is the intended security and session boundary.

## Android Build

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The project requires Android SDK 34 and JDK 17. It does not require the Android NDK or CMake.

## App Configuration

In **Settings > Gateway Settings**:

- Set the gateway URL to `ws://HOST:18789` for a private LAN or `wss://HOST` for TLS.
- Enter the same token configured under `gateway.auth.token`.
- Enable auto-connect if desired.
- Connect and verify the status before enabling the accessibility service.

Credentials are stored through the app's `GatewayAuthManager`. Do not commit tokens or local OpenClaw configuration.

## Selecting Another Model

If the provider or model ID changes, update both OpenClaw and `GatewayConfig.Models.LOCAL_DEFAULT`. The app patches each architect, engineer, and fixer session with that model ID, so changing only the OpenClaw global default is not sufficient.
