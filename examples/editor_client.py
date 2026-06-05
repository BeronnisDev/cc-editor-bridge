#!/usr/bin/env python3
"""Minimal CC Editor Bridge client example.

Requires: pip install websockets

Usage:
    python editor_client.py --port 8765 --token your-token --computer label:my-controller
"""

from __future__ import annotations

import argparse
import asyncio
import json
from typing import Any


async def recv_json(ws) -> dict[str, Any]:
    raw = await ws.recv()
    message = json.loads(raw)
    print(f"<< {message}")
    return message


async def send_json(ws, payload: dict[str, Any]) -> None:
    print(f">> {payload}")
    await ws.send(json.dumps(payload))


async def expect_type(ws, message_type: str) -> dict[str, Any]:
    message = await recv_json(ws)
    if message.get("type") != message_type:
        raise RuntimeError(f"Expected {message_type}, got {message.get('type')}: {message}")
    return message


async def run(host: str, port: int, token: str | None, computer_id: str, path: str) -> None:
    import websockets

    uri = f"ws://{host}:{port}/"
    async with websockets.connect(uri) as ws:
        hello = await recv_json(ws)
        if hello.get("type") != "hello":
            raise RuntimeError(f"Unexpected hello: {hello}")

        if token:
            await send_json(ws, {"type": "auth", "token": token})
            await expect_type(ws, "auth_ok")

        await send_json(ws, {"type": "ping"})
        await expect_type(ws, "pong")

        await send_json(ws, {"type": "file_list", "computerId": computer_id, "path": path})
        listing = await recv_json(ws)
        if listing.get("type") == "error":
            raise RuntimeError(listing.get("message", "file_list failed"))

        files = listing.get("files", [])
        if not files:
            print("No files listed.")
            return

        first_file = files[0]
        if first_file.endswith("/"):
            print(f"Skipping directory entry: {first_file}")
            return

        await send_json(ws, {"type": "file_read", "computerId": computer_id, "path": first_file})
        content = await recv_json(ws)
        if content.get("type") == "error":
            raise RuntimeError(content.get("message", "file_read failed"))

        print(f"\n--- {first_file} ---")
        print(content.get("content", ""))


def main() -> None:
    parser = argparse.ArgumentParser(description="CC Editor Bridge example client")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--token", default=None, help="Auth token if configured")
    parser.add_argument("--computer", required=True, help="e.g. label:my-controller or pos:minecraft:overworld:0:64:0")
    parser.add_argument("--path", default="/", help="Directory to list")
    args = parser.parse_args()

    asyncio.run(run(args.host, args.port, args.token, args.computer, args.path))


if __name__ == "__main__":
    main()
