# Security Model

## Trust Boundaries

Sentinel separates observation, inference, and execution:

1. The Android accessibility service observes UI state.
2. The configured OpenClaw gateway receives prompts and returns model output.
3. The Android action layer parses, classifies, confirms, and executes actions.

The local Gemma deployment keeps inference under user control, but it is not on-device inference. Screen context leaves the Android process and reaches the configured gateway host.

## Protected Assets

- Accessibility data and screen context
- Gateway credentials
- Contacts, messages, calendar data, and tool output
- Device actions performed with accessibility privileges
- Remote files and commands exposed through gateway tools

## Network Boundary

The Android app has network permission because inference uses an authenticated WebSocket connection. Deploy with these constraints:

- Restrict the app to the intended gateway host.
- Use `wss://`, a VPN, or Tailscale outside a trusted private LAN.
- Require a long random OpenClaw gateway token.
- Keep llama-server bound to `127.0.0.1` so only OpenClaw can reach it.
- Do not expose llama-server port `8081` or an unauthenticated gateway publicly.
- Treat cloud model providers as a separate privacy decision. The default Gemma provider does not require a cloud API.

## Model Output Is Untrusted

Local weights do not make output safe. Sentinel treats Gemma responses as untrusted input and applies controls in the Android layer:

- JSON parsing and expected-field validation
- Action allowlists and risk classification
- Target and parameter validation
- Accessibility element lookup at execution time
- Physical confirmation for sensitive or destructive operations
- Failure on unknown actions

OpenClaw and llama.cpp chat-template handling improve model formatting but are not security boundaries.

## Prompt Injection

User text and screen content can contain adversarial instructions. Mitigations include:

- Clear separation of system instructions, user requests, and observed screen context
- Limiting captured context to the active task
- Parsing model output into typed actions instead of executing prose
- Applying action policy independently of model reasoning
- Requiring user confirmation for high-risk effects

Do not weaken action checks because the model is local or instruction-tuned.

## Gateway Credentials

- Store gateway tokens only through `GatewayAuthManager`.
- Never put real tokens in source, documentation, screenshots, or logs.
- Rotate a token after suspected disclosure.
- Clear saved credentials from Gateway Settings before transferring a device.
- Use separate tokens and gateway instances for development and production when practical.

## Tool Security

Tools must validate permissions, parameters, and targets before performing work. New modules should:

- Request the least Android permission needed.
- Reject unexpected operations and oversized input.
- Avoid logging message bodies, credentials, screen text, or contact data.
- Keep remote filesystem and terminal operations constrained to the intended host and workspace.
- Require confirmation for communication, financial, permission-changing, or destructive effects.

## Logging

Android and gateway logs may contain operational metadata. Capture the minimum needed for diagnosis and redact:

- Gateway tokens and authorization headers
- User prompts and screen context
- Contact, calendar, and message content
- Remote file contents and command output

## Deployment Checklist

- [ ] llama-server is bound to loopback.
- [ ] OpenClaw token authentication is enabled.
- [ ] Remote gateway traffic uses TLS or a private VPN.
- [ ] The Android app points only to the intended gateway.
- [ ] Gemma is registered as `gemma-local/gemma-4-e2b-it`.
- [ ] Sensitive actions require confirmation.
- [ ] Logs do not expose credentials or personal data.
- [ ] Accessibility and optional tool permissions are reviewed.
- [ ] The gateway and llama.cpp are kept current.

## Reporting Vulnerabilities

Do not publish credentials or exploit details in a public issue. Use the security contact listed by the project maintainers and include impact, reproduction steps, and a minimal sanitized log when available.
