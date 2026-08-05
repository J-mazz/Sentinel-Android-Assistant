# Troubleshooting

## Gemma Server

### `llama-server` not found

The launcher requires a current standalone llama.cpp installation:

```bash
llama-server --version
```

If the executable has another path, set `LLAMA_SERVER` before running `scripts/start_gemma_server.sh`.

### GGUF not found

The default path is:

```text
gemma-4-E2B-it-qat-UD-Q4_K_XL.gguf
```

Keep it in the project root or set `SENTINEL_MODEL_PATH`. The file is intentionally ignored by Git.

### Unsupported `gemma4` architecture

A message such as `unknown model architecture: gemma4` means llama.cpp is too old. Install a current release, verify `llama-server --version`, and retry. Do not modify or remove llama.cpp architecture source files from a full installation.

### Server exits during model load

Check host memory and server logs. The model file is about 2.5 GB, with additional memory required for tensors, the KV cache, and runtime buffers.

Reduce context allocation if necessary:

```bash
SENTINEL_CONTEXT_SIZE=16384 ./scripts/start_gemma_server.sh
```

Keep the same `contextWindow` value in the OpenClaw provider configuration.

### Health check fails

```bash
curl http://127.0.0.1:8081/health
curl http://127.0.0.1:8081/v1/models
```

Confirm port `8081` is free and that llama-server remained running. The model list should include `gemma-4-e2b-it`.

## OpenClaw

### Model is not listed

```bash
openclaw config validate
openclaw models list
openclaw models status
```

Verify all identifiers match exactly:

- Provider: `gemma-local`
- Model ID and llama-server alias: `gemma-4-e2b-it`
- Full OpenClaw reference: `gemma-local/gemma-4-e2b-it`

### OpenClaw cannot reach llama-server

The default provider URL is `http://127.0.0.1:8081/v1`, so both processes must run on the same host. If they run in separate containers, use a private container network and do not publish llama-server publicly.

### Gateway health fails

```bash
openclaw config validate
openclaw health --verbose
```

Start the gateway with `openclaw gateway` and inspect its logs for provider or authentication errors.

## Android Connection

### Main screen shows `NOT CONNECTED`

Check:

1. OpenClaw is listening on port `18789`.
2. The saved URL uses `ws://` or `wss://`, not `http://`.
3. The Android device can reach the gateway host through the LAN or VPN.
4. The app token matches `gateway.auth.token`.
5. The gateway bind mode permits the device connection.
6. A firewall is not blocking port `18789`.

Useful logs:

```bash
adb logcat -s OpenClawGateway GatewayService GatewayConnectionManager
```

### TLS certificate error

Use a certificate trusted by the Android device. Do not disable certificate validation for production. A private VPN with a loopback-bound gateway is preferable to exposing plain WebSockets over an untrusted network.

### Inference request fails after connecting

Check OpenClaw logs for an unknown model error. Sentinel patches sessions to `gemma-local/gemma-4-e2b-it`, regardless of the OpenClaw global default.

Verify llama-server directly:

```bash
curl http://127.0.0.1:8081/v1/models
```

## Android App

### APK build fails

```bash
./gradlew clean testDebugUnitTest assembleDebug
```

The current app does not require the Android NDK, CMake, a native library, or model assets. References to `NativeBridge`, `libsentinel.so`, GBNF files, or `externalNativeBuild` indicate stale build output or old documentation.

### Accessibility service does not enable

- Confirm the app is installed for the active Android user.
- Enable Sentinel under **Settings > Accessibility**.
- Review device management or work-profile restrictions.
- Reinstall the current APK if the service entry is stale.

Inspect state with:

```bash
adb shell dumpsys accessibility | grep -i sentinel
adb logcat -s AgentAccessibilityService AndroidRuntime
```

### Actions are blocked

This can be expected. The action firewall and risk classifier may require confirmation or reject an unsafe target. Review `ActionDispatcher` and `ActionFirewall` logs without posting sensitive screen content publicly.

## Clean Generated Output

Generated Android and native caches can be removed safely because they are excluded from Git:

```bash
./gradlew clean
```

If an old `.cxx` directory remains from the deleted on-device implementation, remove `app/.cxx`. A normal build of the current project will not recreate it.
