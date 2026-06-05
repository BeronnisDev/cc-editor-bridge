# CC Editor Bridge - Development Plan

## Overview
Build a Minecraft 1.21.1 NeoForge mod that bridges external editors with CC: Tweaked computers via a client-side WebSocket server. Architecture: `editor <-> client <-> server`.

---

## Phase 1: Foundation & Setup

### Task 1.1: Create Gradle Project Structure
- **Goal**: Working Gradle build that compiles Java and resolves dependencies
- **Steps**:
  1. Create `settings.gradle.kts` with root project name
  2. Create `build.gradle.kts` with NeoForge 1.21.1 plugin
  3. Add CC: Tweaked dependency (compileOnly for server, bundled in jar if needed)
  4. Configure Java 21 toolchain
  5. Add source sets: `main`, `test`
- **Test**: Run `gradle build` and verify compilation succeeds with no errors
- **Acceptance**: Build completes, sources compile, empty mod class registers

### Task 1.2: Create Basic Mod Entry Points
- **Goal**: Mod loads in Minecraft without crashing
- **Steps**:
  1. Create `CCEditorBridge` main mod class with `@Mod` annotation
  2. Create `ClientModInitializer` that prints to log on client startup
  3. Create `ServerModInitializer` (or handle in main class) that prints to log on server startup
  4. Add `META-INF/services` files for mod loading
  5. Add basic `mods.toml` with name, version, description
- **Test**: Launch Minecraft dev environment, check logs for mod registration
- **Acceptance**: Mod appears in mods list, no errors on startup

### Task 1.3: Add Configuration System
- **Goal**: Mod has configurable settings for socket port and auth token
- **Steps**:
  1. Create config class using NeoForge `ModConfigSpec`
  2. Add fields: `socketPort` (int, default 8765), `authToken` (String, optional), `enabled` (boolean)
  3. Register config on both client and server
  4. Add config reload callback
- **Test**: Launch game, verify config file is created in `config/`
- **Acceptance**: Config file exists, values can be changed and reloaded

---

## Phase 2: Client-Side WebSocket Server

### Task 2.1: Add WebSocket Dependency
- **Goal**: Project can use a WebSocket library
- **Steps**:
  1. Add `org.java-websocket:Java-WebSocket` or `io.javalin:javalin` dependency to build.gradle.kts
  2. Verify dependency resolves in IDE
- **Test**: Import WebSocket class in a test file, verify compilation
- **Acceptance**: Build succeeds with new dependency

### Task 2.2: Create Socket Server Skeleton
- **Goal**: A basic WebSocket server starts on the client
- **Steps**:
  1. Create `EditorSocketServer` class
  2. Implement server startup in `ClientModInitializer.onInitializeClient()`
  3. Bind to `localhost:<configPort>`
  4. Add basic connection handler (log connections/disconnections)
  5. Add graceful shutdown on client disconnect
- **Test**: Launch client, connect via `websocat ws://localhost:8765`, verify connection opens/closes
- **Acceptance**: Server accepts connections, logs events, shuts down cleanly

### Task 2.3: Add Basic Message Protocol
- **Goal**: Client and editor can exchange JSON messages
- **Steps**:
  1. Define message types: `PING`, `PONG`, `ERROR`
  2. Create message serialization/deserialization using Gson or Moshi
  3. Implement echo/hello handshake
  4. Add message framing/validation
- **Test**: Connect editor client, send handshake, verify response
- **Acceptance**: Editor can connect, send `{"type":"ping"}`, receive `{"type":"pong"}`

### Task 2.4: Add Authentication
- **Goal**: Unauthorized editors are rejected
- **Steps**:
  1. Require auth token on connection (first message after WebSocket open)
  2. Compare against config value
  3. Close connection with error if auth fails
  4. Add optional auth bypass (for development)
- **Test**: Connect with wrong token, verify rejection. Connect with correct token, verify acceptance.
- **Acceptance**: Invalid tokens are rejected within 1 message, valid tokens proceed

---

## Phase 3: Server-Side Packet Infrastructure

### Task 3.1: Define Custom Packets
- **Goal**: Mod can send/receive custom packets between client and server
- **Steps**:
  1. Create packet registry/handler class
  2. Define packet types: `FileListRequest`, `FileReadRequest`, `FileWriteRequest`, `FileDeleteRequest`, `ErrorResponse`
  3. Create packet classes with `ByteBuf` serialization (NeoForge networking)
  4. Register packets on mod startup
- **Test**: Write unit tests for packet serialization round-trip
- **Acceptance**: Packets serialize/deserialize correctly, test coverage >80%

### Task 3.2: Implement Client-to-Server Messaging
- **Goal**: Client can send requests to server and receive responses
- **Steps**:
  1. Create `ClientPacketSender` utility for sending packets from client to server
  2. Create `ServerPacketHandler` to process incoming client packets
  3. Implement request/response correlation (use request ID to match responses)
  4. Add timeout handling for requests
- **Test**: In dev environment, send test packet from client, verify it arrives on server
- **Acceptance**: Client can send packet and receive correlated response

### Task 3.3: Implement Server-to-Client Messaging (File Events)
- **Goal**: Server pushes file change events to client
- **Steps**:
  1. Create event types: `FileCreated`, `FileModified`, `FileDeleted`
  2. Implement server-side event emitter
  3. Add client-side packet handler for unsolicited server messages
  4. Forward events to connected editor via WebSocket
- **Test**: Trigger file event on server, verify it reaches client and is forwarded
- **Acceptance**: Server file changes are forwarded to editor in real-time

---

## Phase 4: CC: Tweaked Integration

### Task 4.1: Identify and Access CC Computer Filesystem
- **Goal**: Mod can read files from attached CC computers
- **Steps**:
  1. Research CC: Tweaked filesystem API for 1.21.1
  2. Create `CCFilesystemAccess` utility class
  3. Implement method to list files on a computer
  4. Add permission checks (verify player has access to computer)
- **Test**: In-game test with CC computer, list files via debug command
- **Acceptance**: Can list files on in-game CC computer terminal

### Task 4.2: Implement File Read Operation
- **Goal**: Server can read file content from CC computer and return to client
- **Steps**:
  1. Implement `FileReadRequest` handler
  2. Use CC filesystem API to read file content
  3. Return content in `FileReadResponse` packet
  4. Handle file-not-found errors
- **Test**: Request file read from editor, verify content returned
- **Acceptance**: Text/lua file content is correctly returned

### Task 4.3: Implement File Write Operation
- **Goal**: Server can write file content to CC computer from client
- **Steps**:
  1. Implement `FileWriteRequest` handler
  2. Write content to CC filesystem
  3. Return success/error in response
  4. Broadcast `FileModified` event to all clients
- **Test**: Edit file in editor, save, verify file changes in-game
- **Acceptance**: Writes succeed, changes visible in CC computer

### Task 4.4: Implement File Delete Operation
- **Goal**: Server can delete files on CC computer
- **Steps**:
  1. Implement `FileDeleteRequest` handler
  2. Delete file via CC API
  3. Broadcast `FileDeleted` event
  4. Handle protected files gracefully
- **Test**: Delete file from editor, verify it's gone in-game
- **Acceptance**: Delete succeeds for user-created files

---

## Phase 5: End-to-End Integration

### Task 5.1: Connect Socket Messages to Server Packets
- **Goal**: Editor commands flow through socket to server
- **Steps**:
  1. Map editor JSON messages to server packets
  2. Send packets when editor requests file operations
  3. Receive server responses and send back to editor
  4. Handle async request/response matching
- **Test**: Use simple editor client to list, read, write, delete files
- **Acceptance**: Full file CRUD works via editor

### Task 5.2: Add Editor Protocol Documentation
- **Goal**: Clear spec for external editor implementations
- **Steps**:
  1. Document message format (JSON schema)
  2. Document handshake sequence
  3. Document all request/response types
  4. Add example client code
- **Test**: Implement example editor client against spec
- **Acceptance**: Third party can implement compatible editor

### Task 5.3: Add Debug Commands
- **Goal**: Easy in-game testing and debugging
- **Steps**:
  1. Add `/cceditor status` - show socket server status
  2. Add `/cceditor test` - run self-test of server connection
  3. Add `/cceditor list <computer>` - list files on computer
  4. Add in-game config reload command
- **Test**: Run commands in single-player world
- **Acceptance**: All commands work and show useful output

---

## Phase 6: Polish & Testing

### Task 6.1: Add Error Handling & Recovery
- **Goal**: Mod handles edge cases gracefully
- **Steps**:
  1. Handle CC computer disconnection during operation
  2. Handle file permission errors
  3. Handle malformed editor messages
  4. Add reconnection logic for editor
- **Test**: Disconnect CC computer mid-operation, verify clean error
- **Acceptance**: No crashes, meaningful error messages returned

### Task 6.2: Multiplayer Testing
- **Goal**: Mod works correctly in multiplayer
- **Steps**:
  1. Test on dedicated server with multiple clients
  2. Verify per-player permissions
  3. Test socket server on each client
  4. Verify no server-side sockets are opened
- **Test**: Connect multiple players, each with editor, verify isolation
- **Acceptance**: Each player only accesses their own computers

### Task 6.3: Security Hardening
- **Goal**: Mod is safe to use on public servers
- **Steps**:
  1. Validate all file paths (prevent directory traversal)
  2. Rate-limit file operations
  3. Add optional per-connection auth tokens
  4. Document security model
- **Test**: Attempt path traversal attacks, verify they're blocked
- **Acceptance**: Security audit passes

---

## Testing Strategy

### Unit Tests (Run with `gradle test`)
- Packet serialization/deserialization
- Message protocol encoding/decoding
- Utility functions (path validation, etc.)

### Integration Tests
- Local Minecraft dev environment
- Socket connection via test client
- End-to-end file operations

### Manual Tests
- Single-player with CC computers
- Multiplayer with friends
- Various editors (VSCode, vim, etc.)

---

## Key Milestones

1. **M1**: Mod loads and compiles (Task 1.1-1.2)
2. **M2**: Socket server accepts connections (Task 2.2-2.3)
3. **M3**: Client-server packet round-trip works (Task 3.2)
4. **M4**: File operations work with CC computers (Task 4.2-4.3)
5. **M5**: Full end-to-end editor bridge (Task 5.1)
6. **M6**: Production-ready, tested, documented (Task 6.x)

---

## Future Enhancements (Post-MVP)

- Support for multiple simultaneous editors
- File change watching (auto-reload in editor on in-game changes)
- Syntax highlighting / linting integration
- Undo/redo stack sync
- Remote editing via SSH tunnel (advanced)
- Biome/file type specific editors
- Chat command integration (`/lua` from editor)

---

## Notes

- All networking is client-side; server never opens sockets
- Respect CC: Tweaked's existing permission model
- Default to disabled state; user must explicitly enable socket server
- Consider using existing libraries (WebSocket, JSON) rather than custom implementations
