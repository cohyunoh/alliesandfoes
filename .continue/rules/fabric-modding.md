# Fabric Modding Rules for Allies & Foes

## Version and mappings

- Use the current workspace Gradle/fabric files as the source of truth.
- The uploaded `fabric.mod.json` currently declares Minecraft `>=26.1.2`, Java `>=25`, Fabric Loader `>=0.18.4`, and Fabric API `*`.
- Earlier conversation context may mention Minecraft `1.21.11`; verify before coding.
- Use Mojang/current workspace mappings only.
- Do not use Yarn names unless the current workspace is explicitly Yarn-mapped.
- If unsure about a method/class name, inspect existing project usage and mappings before writing code.

## API safety

- Do not invent Fabric API methods.
- Do not copy outdated Fabric tutorial APIs without checking the current project patterns.
- Match the project’s existing imports and naming style.
- Prefer existing patterns over “cleaner” rewrites.
- If a method name is uncertain, say so and ask for the exact mapping/reference or inspect the workspace.

## Architecture

- Gameplay authority belongs on the server.
- Client code may render, cache, open screens, and send requests, but the server validates and applies state changes.
- Use existing service/manager classes before adding new state:
  - `AllianceManager`
  - `TerritoryManager`
  - `AllianceProgressionService`
  - `AllianceWarService`
  - `WarSnapshotService`
  - `ExplorerDiscoveryService`
  - `ExplorerSkillService`
  - `RoleSlotService`
  - `CovenantForgeService`
  - `TributeAltarService`
- Do not introduce parallel state stores for existing systems.

## Networking

- Payloads live in `net.cnn_r.alliesandfoes.network`.
- Follow the existing `CustomPacketPayload` + `StreamCodec` record pattern.
- Register payload types in `Alliesandfoes.onInitialize()` with `PayloadTypeRegistry.clientboundPlay()` or `PayloadTypeRegistry.serverboundPlay()`.
- Serverbound receivers go in `Alliesandfoes` via `ServerPlayNetworking.registerGlobalReceiver(...)`.
- Clientbound receivers go in `AlliesandfoesClient` via `ClientPlayNetworking.registerGlobalReceiver(...)`.
- Clientbound receivers should use `context.client().execute(...)` before mutating client state or screens.
- Serverbound receivers must validate alliance membership, roles/permissions, territory ownership, and costs before mutating state.

## Identifiers

- Use `net.minecraft.resources.Identifier` as shown in this project.
- Prefer `Identifier.fromNamespaceAndPath("alliesandfoes", "path")` when matching existing code.
- Do not use `ResourceLocation` unless the current mappings/project actually require it.

## Territory rules

- Use `ChunkKey` for territory/map identity because it includes dimension + chunk coordinates.
- Do not use raw `ChunkPos` as persistent territory identity when dimension matters.
- Territory is alliance-owned and anchor-based.
- Founding requires alliance membership and permission.
- Founding rejects claimed chunks, disallowed dimensions, same-alliance spacing violations, foreign-alliance spacing violations, and anchor capacity overflow.
- Expansion must be cardinally adjacent to the same anchor.
- Diagonal adjacency does not count.
- Expansion adjacent to another same-alliance anchor is ambiguous and should be rejected.
- Expansion cost is paid through alliance progression/influence, not direct player inventory, unless explicitly changing that design.
- Unclaiming is edge-only.
- Keep `TerritoryPlacementRules` and `TerritoryClaimRules` as the rule source of truth.

## Map rules

- Preserve dimension/world-scoped caches.
- Use `WorldIdentity` and `ChunkKey` for cache separation.
- Avoid cache collisions between worlds/dimensions.
- Do not remove flicker-prevention or dirty-flag behavior without replacing it intentionally.
- `MapState` owns client map caches and loaded chunk tracking.
- `MapScreen` owns map UI mode state and selection behavior.
- `MapScreen` already has territory preview, war declaration/review, rollback repair, pet revive, anchor cycling, player markers, structure intel, and explorer intuition behavior. Modify minimally.
- If changing mode toggles, make state transitions visually obvious and reversible.

## UI rules

- Preserve the parchment/journal/ledger style for alliance and role screens.
- Use explicit layout math.
- Header/top sections should be laid out top-down.
- Footer/status/actions should be laid out bottom-up.
- Content/list areas should fill the middle without overlapping the footer.
- Keep padding consistent.
- Do not aggressively responsive-scale text/buttons unless asked.
- Prioritize stable behavior at GUI scale 4 and small windows.
- Do not rewrite working screens wholesale for small behavior fixes.

## Explorer / intuition rules

- The monocle/journal system should feel vanilla-like and guided.
- Intuition should generally require a selected target rather than arbitrarily pointing at unrelated “points of interest.”
- Unlocks/discoveries should flow through existing explorer discovery state and sync payloads.
- Preserve separation between server-discovered/unlocked data and client display state.
- Reuse `IntuitionTarget`, `ExplorerIntuitionEvaluator`, `ExplorerIntuitionProfile`, `IntuitionResult`, `MapIntuitionRenderer`, `MapIntuitionMessageController`, and `HudIntuitionRenderer` where possible.

## Role system rules

- Role types are currently `EXPLORER`, `WARRIOR`, `CULTIVATOR`, and `PROSPECTOR`.
- Role slots are implemented through inventory mixins and `roleslot` services/state.
- Role systems should support the larger loop: explore/generate influence/expand territory/war.
- Do not bypass role slot state when adding role-specific behavior.

## Persistence rules

- Use existing `SavedData` classes for persistent server state.
- Mark saved data dirty after mutations.
- Preserve existing NBT/data formats where possible.
- Do not store transient UI mode state in server persistence.
- Do not store server authority in client cache files.

## Code output preferences

- Work file-by-file.
- First explain which files need to change and why.
- Prefer minimal patches over broad rewrites.
- Show only changed methods/sections unless a full file is requested.
- Keep code explicit, readable, and consistent with existing project style.
- Do not silently change unrelated formatting/imports across large files.

## Debugging rules

- For compile errors, inspect the exact file and mapping/API mismatch before proposing a fix.
- For networking bugs, check payload type registration, receiver side, send side, and thread execution.
- For client screens not opening, verify the packet is registered clientbound/serverbound correctly and the receiver calls `setScreen` on the client thread.
- For map bugs, check `MapState`, `MapScreen`, dimension keys, cache invalidation, dirty flags, and client chunk events.
- For territory bugs, check `TerritoryManager`, rules classes, payment service, saved data, and sync services.

## Progress reporting

For any multi-file or nontrivial task:

1. First write a short plan.
2. Before editing, list the files you inspected.
3. After each major step, summarize:
    - what changed
    - what still needs to be checked
    - any uncertainty
4. Do not silently perform large changes.
5. If blocked, explain the exact blocker before continuing.

## Claude-Code-Style Workflow

Before coding, always do a planning pass.

### Phase 1 — Plan First
For any nontrivial task:
1. Restate the goal in one sentence.
2. Inspect relevant files before proposing edits.
3. List the files inspected.
4. Explain the current architecture involved.
5. Propose a step-by-step implementation plan.
6. Identify risks, edge cases, or mapping/API uncertainty.
7. Stop and wait for approval before editing.

Do not write code during the planning phase.

### Phase 2 — Execute Carefully
After approval:
1. Work file-by-file.
2. Make the smallest safe change.
3. Prefer modifying existing systems over creating new abstractions.
4. Show changed sections unless the whole file is necessary.
5. After each file, summarize:
    - what changed
    - why it changed
    - what still needs testing

### Fabric / Minecraft Rules
- Minecraft version: 1.21.11.
- Fabric mod.
- Use Mojang mappings only.
- Do not use Yarn names.
- Do not invent Minecraft/Fabric APIs.
- Check existing project patterns before coding.
- Server owns authoritative game state.
- Client receives synced state through packets/payloads.
- Do not rewrite working UI layout systems unless explicitly requested.
- Preserve the parchment/ledger GUI design system.
- Prefer explicit readable code over clever abstractions.

### MCP Usage
Use `mcmodding-mcp` when:
- unsure about Mojang mapping names
- unsure about class/method signatures
- working with Minecraft internals
- working with Fabric networking, screens, rendering, saved data, or commands

Do not guess APIs when MCP can verify them.

