# Quick Start

## Prerequisites

- Android 14 or newer and ADB
- JDK 17
- A current `llama-server` build with Gemma 4 support
- OpenClaw 2026.4.9 or newer on the gateway host
- `gemma-4-E2B-it-qat-UD-Q4_K_XL.gguf` in the project root

## 1. Start Gemma

From the project root on the gateway host:

```bash
./scripts/start_gemma_server.sh
```

The launcher serves the model as `gemma-4-e2b-it` on `http://127.0.0.1:8081/v1`. It uses a 32K context and one parallel slot by default to keep memory use bounded.

Verify the endpoint:

```bash
curl http://127.0.0.1:8081/health
curl http://127.0.0.1:8081/v1/models
```

## 2. Register the Model in OpenClaw

Merge this provider into `~/.openclaw/openclaw.json`:

```json5
{
  models: {
    mode: "merge",
    providers: {
      "gemma-local": {
        baseUrl: "http://127.0.0.1:8081/v1",
        apiKey: "local",
        api: "openai-completions",
        models: [
          {
            id: "gemma-4-e2b-it",
            name: "Gemma 4 E2B IT QAT Q4_K_XL",
            reasoning: false,
            input: ["text"],
            cost: {
              input: 0,
              output: 0,
              cacheRead: 0,
              cacheWrite: 0
            },
            contextWindow: 32768,
            maxTokens: 4096
          }
        ]
      }
    }
  },
  gateway: {
    mode: "local",
    bind: "lan",
    port: 18789,
    auth: {
      mode: "token",
      token: "replace-with-a-long-random-token"
    }
  }
}
```

`bind: "lan"` is suitable only on a trusted private network. Prefer a VPN or Tailscale with `bind: "loopback"` for remote access.

Validate and select the model:

```bash
openclaw config validate
openclaw models set gemma-local/gemma-4-e2b-it
openclaw models status
openclaw gateway
```

## 3. Build and Install Sentinel

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 4. Connect the App

1. Open Sentinel and select **Settings**.
2. Enter `ws://GATEWAY_IP:18789`, or the corresponding `wss://` VPN/TLS address.
3. Enter the token from `gateway.auth.token`.
4. Save, connect, and confirm the main screen shows **CONNECTED**.
5. Enable Sentinel under Android Accessibility settings.
6. Use **Test Inference** to verify the full path.

## Model Overrides

The launcher accepts these environment variables:

- `SENTINEL_MODEL_PATH`: GGUF location
- `SENTINEL_MODEL_HOST`: llama-server bind address, default `127.0.0.1`
- `SENTINEL_MODEL_PORT`: llama-server port, default `8081`
- `SENTINEL_CONTEXT_SIZE`: context size, default `32768`
- `LLAMA_SERVER`: llama-server executable name or path

See [Setup](SETUP.md) for deployment details and [Troubleshooting](TROUBLESHOOTING.md) for diagnostics.
