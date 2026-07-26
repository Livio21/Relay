# Relay extension template

This is a publishable starter repository for a Relay **desktop source extension**. It uses JSON-RPC over standard input/output, so the extension stays outside Relay's process.

## Start

1. Copy this directory into its own Git repository.
2. Change the `id`, `name`, author, and repository URLs in `extension.json`.
3. Replace `browse()` in `relay_extension.py` with your source implementation.
4. Keep standard output exclusively for JSON-RPC responses; write diagnostics to standard error.
5. Run `python3 -m unittest` before packaging the executable for each supported desktop architecture.

## Protocol

Relay sends one UTF-8 JSON object per line, no larger than 64 KiB. This template implements:

- `handshake` — identifies the extension, its API range, permissions, and authentication methods.
- `browse` — returns source-track DTOs for Relay to validate and display.

`streamReference` is deliberately opaque and short-lived. Do not return a filesystem path, secret, or permanent authenticated URL. Relay's future source host resolves that reference only for the approved playback request.

This sample returns no playable music. It exists to verify the extension lifecycle without distributing content or credentials.

## Repository publishing

Relay repositories are static HTTPS documents, like Mihon's extension indexes, but Relay additionally requires a P-256 ECDSA/SHA-256-signed index and an artifact SHA-256 before it can install anything. P-256 is used because Android's built-in Ed25519 verifier starts at API 33 while Relay supports API 23. `repository.json` and `index.json` are intentionally unsigned placeholders until Relay's repository verifier is added; never publish them as trusted production metadata.

Run `scripts/create-signing-key.sh`, copy its public-key value into `repository.json`, then run `scripts/sign-index.sh` after every `index.json` change. Publish the resulting Base64 DER ECDSA signature as `index.json.sig`. Never commit `keys/repository-private.pem`.

Android extensions use the same handshake and DTOs through an exported, explicitly bound service. That Binder adapter belongs in the Relay host phase; this repository does not pretend an Android APK can run until that contract is implemented.
