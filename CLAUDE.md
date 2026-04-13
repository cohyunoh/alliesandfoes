/b# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

**alliesandfoes** is a Fabric Minecraft mod (1.21.11, Java 21) that adds player alliances, chunk-based territory claiming, and a client-side strategic map to survival multiplayer. Players form alliances, earn progression points, and spend them to found territory anchors and claim nearby chunks.

## Build and run commands

```bash
# Build the mod jar
./gradlew build

# Run the Minecraft client with the mod loaded
./gradlew runClient

# Run the dedicated server with the mod loaded
./gradlew runServer

# Regenerate data (runs AlliesandfoesDataGenerator)
./gradlew runDatagen
```

There are no automated tests. Verification is done by running the client/server and exercising the mod in-game.

## Architecture

### Entry points

- `Alliesandfoes.java` — server-side `ModInitializer`: registers all payload types, server-side network handlers, server tick events, and chunk load events.
- `AlliesandfoesClient.java` — client-side `ClientModInitializer`: registers client-side network receivers and chunk load/unload hooks that feed `MapState`.

### Server singleton pattern

Both core managers follow the same pattern — a `WeakHashMap<MinecraftServer, T>` keyed by server instance, with a static `.get(server)` accessor. Always use `.get(server)` to access them; never instantiate them directly.

- `AllianceManager.get(server)` — live alliance state, invite/join flows, member management
- `TerritoryManager.get(server)` — anchors, claims, value cache, cost/payment pipeline
- `AllianceProgressionService.get(server)` — alliance XP balances (spent on territory)

### Persistence

Each system has a `*SavedData` class backed by Minecraft's NBT-based `SavedData`. Managers call `.save()` on themselves after every mutation, which delegates to the saved data class.

- `AllianceSavedData` — alliance roster and pending invites
- `TerritorySavedData` — anchors, claims, cached chunk values
- `AllianceProgressionSavedData` — per-alliance progression balances

### Network layer

All packets are Java records in `network/`. Each record has a `TYPE` (packet ID) and `STREAM_CODEC` (serializer). Both must be registered in `Alliesandfoes.onInitialize()` before use. Server-to-client payloads are prefixed by what they carry (e.g., `AllianceStatePayload`); client-to-server payloads are prefixed with `Request*` or `Send*`.

### Chunk value system

Chunks are scored 1–10 using four factors with fixed weights (`ChunkValueWeights`): biome (40%), water (20%), ore (25%), structure (15%). The interface `ChunkValueEvaluator` has two implementations:

- `ServerChunkValueEvaluator` — runs server-side; queries actual world data
- Client-side scanning in `map/scan/ChunkScanner` — runs on a background thread, fills `ChunkValueCache`

Structure values are computed server-side by `StructureChunkValueCalculator` and synced to clients via `ChunkStructurePayload` on chunk load and player join.

### Territory system

`TerritoryManager` orchestrates the full claim pipeline:

1. **Rules check** — `TerritoryPlacementRules` / `TerritoryClaimRules` validate adjacency, dimension, tier capacity
2. **Cost calculation** — `TerritoryCostService` computes `chunkValue × multiplier + surcharge`
3. **Payment** — `TerritoryPaymentService` interface; currently `AllianceProgressionTerritoryPaymentService` deducts from the alliance's progression balance
4. **Registration** — anchor/claim stored in in-memory maps, then persisted

`AnchorTier.BASIC` is the only tier; it caps total claim value at 64.

### Client-side map (`map/`)

- `MapState` — static client singleton holding all caches (lazy-initialized)
- `MapScreen` — 512×512 texture rendered top-down; supports pan/zoom, territory overlays, player markers, value heatmaps
- `map/cache/` — `ChunkCache` (block colors), `ChunkValueCache` (scores), `ChunkStructureSyncCache` (server-synced structure data), `TerritoryChunkSyncCache` / `TerritoryPreviewSyncCache` (server-synced territory state), `PlayerMarkerCache` (live positions)
- `map/intuition/` — `ExplorerIntuitionEvaluator` evaluates chunk values in the player's facing direction; `MapIntuitionMessageController` throttles and filters the resulting hint messages

### Alliance progression

`AllianceProgressionService` tracks a simple integer balance per alliance UUID. Balances are spent by the territory payment service and currently can only be granted via the debug command `/allianceprogress add <amount>`. Gameplay-based gain is not yet implemented.

## Debug commands

These are temporary and require the player to be the command source:

| Command | Description |
|---|---|
| `/territory found [name]` | Found a territory anchor at the player's current chunk |
| `/allianceprogress add <amount>` | Add progression to the player's alliance |
| `/allianceprogress balance` | Show the player's alliance progression balance |

## Key binding

`M` — open/close the map screen (registered in `KeyBindings.java`).
