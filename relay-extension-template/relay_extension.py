#!/usr/bin/env python3
"""Relay desktop extension template. JSON-RPC messages are one line each."""

import json
import sys

MAX_MESSAGE_BYTES = 64 * 1024


def response(request_id, result=None, error=None):
    payload = {"id": request_id}
    if error is None:
        payload["result"] = result
    else:
        payload["error"] = error
    return payload


def browse():
    # Replace with validated DTOs from your source. streamReference must stay opaque.
    return {"tracks": []}


def handle(request):
    request_id = request.get("id")
    method = request.get("method")
    if not isinstance(request_id, str) or not request_id:
        return response(None, error="Request ID is required.")
    if method == "handshake":
        return response(request_id, {
            "id": "example.relay.source",
            "version": "1.0.0",
            "kind": "SOURCE",
            "api": {"minimum": 1, "maximum": 2},
            "capabilities": ["browse"],
            "permissions": ["NETWORK"],
            "settingsSchemaVersion": 1,
            "authentication": ["NONE"],
        })
    if method == "browse":
        return response(request_id, browse())
    return response(request_id, error="Unknown method.")


def main(input_stream=sys.stdin, output_stream=sys.stdout):
    for line in input_stream:
        if len(line.encode("utf-8")) > MAX_MESSAGE_BYTES:
            print(json.dumps(response(None, error="Message exceeds 64 KiB.")), file=output_stream, flush=True)
            continue
        try:
            request = json.loads(line)
            if not isinstance(request, dict):
                raise ValueError
            payload = handle(request)
        except (ValueError, json.JSONDecodeError):
            payload = response(None, error="Invalid request.")
        print(json.dumps(payload, separators=(",", ":")), file=output_stream, flush=True)


if __name__ == "__main__":
    main()
