# CC Editor Bridge — Editor Protocol

This document describes the JSON-over-WebSocket protocol used by **CC Editor Bridge** to connect external editors to CC: Tweaked computers in Minecraft.

The WebSocket server runs on the **Minecraft client** only, bound to `127.0.0.1`. File operations are forwarded to the integrated/dedicated server via custom packets.

## Connection

| Setting | Default | Config key |
|---------|---------|------------|
| Host | `127.0.0.1` | (fixed) |
| Port | `8765` | `socketPort` |
| Enabled | `false` | `enabled` |
| Auth token | (none) | `authToken` |

Connect with any WebSocket client:

```
ws://127.0.0.1:8765/
```

## Message format

All messages are JSON objects with a required `type` field (string). Other fields depend on the message type.

```json
{
  "type": "message_type",
  "message": "optional string",
  "token": "optional string",
  "computerId": "optional string",
  "path": "optional string",
  "content": "optional string",
  "files": ["optional", "array"]
}
```

Maximum message size: **65536 bytes**.

## Handshake

### 1. Connect

The server sends `hello` immediately after the WebSocket opens.

**Server → Editor**

```json
{"type":"hello","message":"Authentication required"}
```

or, if auth is disabled:

```json
{"type":"hello","message":"CC Editor Bridge cceditorbridge"}
```

### 2. Authenticate (if required)

When `authToken` is set in config (and `skipAuthForDev` is false), the **first** message from the editor must be `auth`. Otherwise the connection is closed.

**Editor → Server**

```json
{"type":"auth","token":"your-token-here"}
```

**Server → Editor (success)**

```json
{"type":"auth_ok"}
```

**Server → Editor (failure)**

```json
{"type":"error","message":"Invalid auth token"}
```

The connection is then closed with WebSocket code `1008`.

### 3. Keepalive

**Editor → Server**

```json
{"type":"ping"}
```

**Server → Editor**

```json
{"type":"pong"}
```

## Computer identifiers

File operations require a `computerId` identifying the target CC computer.

| Format | Example | Notes |
|--------|---------|-------|
| Position | `pos:minecraft:overworld:10:64:-5` | Always works for placed computers |
| Label | `label:my-controller` | Uses `os.setComputerLabel("my-controller")` in CC |

Use `/cceditor id` in-game (while looking at a computer) to see available ids.

**Label caveats:**
- Labels are not guaranteed unique — duplicate labels return an ambiguity error
- Label lookup searches running computers and loaded chunks near online players
- For offline/unloaded computers, use a `pos:` id

## File operations

All file requests require the editor to be authenticated (when auth is enabled) and the player must have access to the target computer (CC permission checks apply).

Paths use forward slashes. Root is `/` or `""`. Path traversal (`..`) is rejected.

### List files

**Editor → Server**

```json
{"type":"file_list","computerId":"label:my-controller","path":"/"}
```

**Server → Editor (success)**

```json
{"type":"file_list_ok","computerId":"label:my-controller","path":"/","files":["startup.lua","programs"]}
```

**Server → Editor (failure)**

```json
{"type":"error","message":"No accessible computer with label 'my-controller'..."}
```

### Read file

**Editor → Server**

```json
{"type":"file_read","computerId":"pos:minecraft:overworld:10:64:-5","path":"startup.lua"}
```

**Server → Editor (success)**

```json
{"type":"file_read_ok","computerId":"pos:minecraft:overworld:10:64:-5","path":"startup.lua","content":"print('hello')\n"}
```

### Write file

**Editor → Server**

```json
{"type":"file_write","computerId":"label:my-controller","path":"test.lua","content":"print('hi')\n"}
```

**Server → Editor (success)**

```json
{"type":"file_write_ok","computerId":"label:my-controller","path":"test.lua"}
```

### Delete file

**Editor → Server**

```json
{"type":"file_delete","computerId":"label:my-controller","path":"test.lua"}
```

**Server → Editor (success)**

```json
{"type":"file_delete_ok","computerId":"label:my-controller","path":"test.lua"}
```

## Server-initiated events

When files change in-game (e.g. via another editor or in-computer edit), the server pushes events to all authenticated editors:

```json
{"type":"file_created","computerId":"label:my-controller","path":"newfile.lua"}
{"type":"file_modified","computerId":"label:my-controller","path":"startup.lua"}
{"type":"file_deleted","computerId":"label:my-controller","path":"oldfile.lua"}
```

## Message reference

| Type | Direction | Description |
|------|-----------|-------------|
| `hello` | S→E | Sent on connect |
| `auth` | E→S | Authenticate with token |
| `auth_ok` | S→E | Auth succeeded |
| `ping` | E→S | Keepalive |
| `pong` | S→E | Keepalive response |
| `error` | S→E | Error description in `message` |
| `file_list` | E→S | List directory |
| `file_list_ok` | S→E | Directory listing in `files` |
| `file_read` | E→S | Read file |
| `file_read_ok` | S→E | File content in `content` |
| `file_write` | E→S | Write file |
| `file_write_ok` | S→E | Write succeeded |
| `file_delete` | E→S | Delete file |
| `file_delete_ok` | S→E | Delete succeeded |
| `file_created` | S→E | In-game file created |
| `file_modified` | S→E | In-game file modified |
| `file_deleted` | S→E | In-game file deleted |

S→E = server (bridge) to editor, E→S = editor to server (bridge).

## Example: websocat

```powershell
websocat ws://127.0.0.1:8765
{"type":"auth","token":"your-token"}
{"type":"ping"}
{"type":"file_list","computerId":"label:my-controller","path":"/"}
```

## Example client

See [`examples/editor_client.py`](examples/editor_client.py) for a minimal Python client.

## Debug commands

In-game client commands (single-player / client):

| Command | Description |
|---------|-------------|
| `/cceditor status` | Show bridge status |
| `/cceditor test` | Self-test WebSocket + packet path |
| `/cceditor reload` | Reload client config from disk |
| `/cceditor id` | Show computer ids for targeted block |
| `/cceditor list [computer]` | List files on a computer |

## Security model

- WebSocket binds to **localhost only** — not exposed to the network
- Optional token auth on connect
- File access respects CC: Tweaked permission checks (`isUsable`)
- Path traversal is blocked
- The Minecraft server never opens a socket to editors
