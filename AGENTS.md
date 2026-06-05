# CC Editor API - Modding Agent Documentation

## Project Name
**CC Editor Bridge** - A Minecraft 1.21.1 mod integrating with CC: Tweaked to allow external editors to edit files via a client-side socket bridge.

## Project Context

This mod creates a bridge between external code editors (VSCode, Vim, etc.) and in-game CC: Tweaked computers (Computercraft). The architecture is:

```
editor <-> client <-> server
```

The client runs on the player's machine and exposes a local socket that editors can connect to. Communication flows from editor -> client -> server (Minecraft server with CC: Tweaked). No new server-side sockets are exposed to the network.

## Key Technologies

- **Minecraft Version**: 1.21.1
- **Mod Framework**: NeoForge (preferred) or Fabric
- **Integration**: CC: Tweaked (cc-tweaked)
- **Communication**: WebSockets on client side (localhost only)
- **Language**: Java (or Kotlin if using KCommons patterns)

## Entry Points

### Client Side
- `ClientModInitializer` - Register client-only components
- Socket server startup (localhost, configurable port)
- Packet handlers for CC computer file events
- Network connection to editor

### Server Side
- `ModInitializer` / `ServerModInitializer`
- Custom packet types for file operations
- Permission/interface checks for CC computers
- Integration with CC: Tweaked filesystem API

## Code Conventions
- Use standard Mojang mappings
- Follow NeoForge/Fabric modding patterns
- Separate client/server modules clearly
- Use events where available rather than mixins
- Package structure: `dev.yourname.cceditor.*`

## Important Constraints
- **NEVER** introduce server-side sockets - all networking is client-side only
- Respect CC: Tweaked's permission model - don't allow arbitrary file access without appropriate permissions
- Keep mod compatible with single-player and multiplayer servers
- External editor connection should be opt-in/secure (localhost only, password/token optional)

## Testing Approach
- Unit tests for packet serialization
- Integration tests using a local Minecraft server
- Manual testing with actual CC computers and external editors
- Test both single-player and multiplayer scenarios

## References
- CC: Tweaked Wiki: https://github.com/cc-tweaked/cc-tweaked/wiki
- CC: Tweaked API: https://javadoc.cc-tweaked.dev/1.21.1/
- NeoForge Docs: https://docs.neoforge.net/
- Fabric Docs: https://fabricmc.net/develop/
