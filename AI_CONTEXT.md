# Allies & Foes AI Context

## Project identity

Allies & Foes is a Minecraft Fabric mod for competitive-but-vanilla-like survival multiplayer. The current uploaded source uses package `net.cnn_r.alliesandfoes`, mod id `alliesandfoes`, and the visible `fabric.mod.json` declares:

- Fabric Loader: `>=0.18.4`
- Minecraft: `>=26.1.2`
- Java: `>=25`
- Fabric API: `*`
- Entrypoints:
  - main: `net.cnn_r.alliesandfoes.Alliesandfoes`
  - client: `net.cnn_r.alliesandfoes.AlliesandfoesClient`
  - datagen: `net.cnn_r.alliesandfoes.AlliesandfoesDataGenerator`

Important: earlier development context may mention Minecraft `1.21.11`, but this uploaded `src` currently says `minecraft >=26.1.2` and `java >=25`. Before coding, verify the actual Gradle/project version and mappings in the current workspace.

## Hard technical assumptions

- Fabric mod.
- Use Mojang mappings / current workspace mappings. Do not invent Yarn names.
- Server authoritative gameplay logic.
- Client screens/HUD/cache state should display and request actions, not own final game authority.
- Networking uses modern `CustomPacketPayload` records with `TYPE` and `STREAM_CODEC` fields.
- Payloads are registered in `Alliesandfoes.onInitialize()` via `PayloadTypeRegistry.clientboundPlay()` / `serverboundPlay()` and handled with `ServerPlayNetworking.registerGlobalReceiver(...)` or `ClientPlayNetworking.registerGlobalReceiver(...)`.
- Use `net.minecraft.resources.Identifier` as shown in the current source, commonly via `Identifier.fromNamespaceAndPath("alliesandfoes", "...")`.
- Do not add fake Fabric APIs or assume APIs from old tutorials.

## Main entrypoint responsibilities

### `Alliesandfoes.java`

Primary server/common initializer. It currently handles:

- Config load via `ModConfig.load()`.
- Registration of components, blocks, items.
- Territory, progression, and war commands.
- Territory protection hooks.
- Payload type registration for alliance, territory, map, war, rollback, pet revive, intuition, role slot, tribute, and covenant forge systems.
- Server packet receivers for alliance creation/join/view/invite/role actions.
- Server packet receivers for territory preview/action, war declaration/responses, rollback, pet revive, intuition target, tribute conversion, and covenant forge upgrades.
- Server lifecycle/player events/chunk events/tick events for syncing map/player/territory/war state and role progression.

When changing networking, inspect this file first so the new payload is registered on the correct side before any receiver sends or consumes it.

### `AlliesandfoesClient.java`

Primary client initializer. It currently handles:

- Client packet receivers that update client-only state/caches and open screens.
- Map cache updates for player markers, chunk structure data, territory chunks, territory previews, war state, rollback chunks, dead pets, role slots, and intuition target location.
- Keybind registration and client tick behavior.
- Screen opening behavior for alliance, join, invite, war invite, map, monocle, tribute altar, and covenant forge flows.
- HUD registration for intuition display.
- Chunk load/unload client hooks for map scanning.

When changing UI behavior or client sync, inspect this file plus the relevant screen/cache class.

## Feature modules and architecture

### Alliance system

Package: `net.cnn_r.alliesandfoes.alliance`

Key files:

- `Alliance.java`: alliance domain model.
- `AllianceManager.java`: server-side alliance runtime authority.
- `AllianceSavedData.java`: persistence.
- `AllianceClientState.java`: client-only cached alliance state for UI/screens.
- Screens under `alliance/screen`: create, join, invite management, join requests, view, action confirm, war invite.

Current features include creating alliances, joining/requesting, invites, accepting/declining requests, leaving, kicking, transferring ownership, role editing, and client UI feedback.

### Alliance progression / influence

Package: `net.cnn_r.alliesandfoes.alliance.progression`

Key files:

- `AllianceProgressionService.java`
- `AllianceProgressionSavedData.java`
- `AllianceProgressionCommands.java`

This is the shared alliance progression/influence economy. Territory founding and expansion payment currently routes through `AllianceProgressionTerritoryPaymentService`, not player inventory.

### War system

Package: `net.cnn_r.alliesandfoes.alliance.war`

Key files:

- `AllianceWar.java`
- `AllianceWarService.java`
- `AllianceWarSavedData.java`
- `WarSnapshotService.java`
- `WarSnapshotSavedData.java`
- `WarStatus.java`
- `AllianceWarCommands.java`

War state is synced to the map via `WarStateSyncPayload` and displayed/acted on in `MapScreen` and `WarInviteScreen`. Rollback/pet revival systems also connect to war state.

### Territory system

Package: `net.cnn_r.alliesandfoes.territory`

Key files:

- `TerritoryManager.java`: server-side territory authority and persistence coordination.
- `TerritorySavedData.java`: persisted anchors, claims, and cached chunk values.
- `TerritoryAnchor.java`: anchor/base model.
- `TerritoryClaim.java`: claimed chunk model.
- `ChunkKey.java`: dimension + chunkX + chunkZ identity. Use this instead of raw `ChunkPos` when dimension matters.
- `AnchorTier.java`: anchor capacity/tier logic.
- `TerritoryPlacementRules.java`: founding rules.
- `TerritoryClaimRules.java`: expansion/unclaim rules.
- `TerritoryCostService.java`: founding/expansion cost calculation.
- `TerritoryValueService.java`: server-side value lookup/cache.
- `TerritoryMapSyncService.java`: sync claimed territory to clients.
- `TerritoryPreviewSyncService.java`: sync valid/invalid previews to clients.
- `TerritoryQueryService.java`: read/query helper.

Current territory design:

- Alliances found territory anchors/bases.
- Anchor chunk is automatically claimed.
- Expansion must be cardinally adjacent to the same anchor.
- Diagonal adjacency does not count.
- Same-alliance different-anchor adjacency is rejected as ambiguous.
- Same-alliance founding spacing is currently `3` chunks; foreign-alliance founding spacing is currently `2` chunks.
- Claims are dimension-aware via `ChunkKey`.
- Founding/expansion costs use alliance progression/influence.
- Anchor capacity is value-based; used value must not exceed max value.
- Unclaiming is edge-only; interior unclaims are rejected.

### Map system

Package: `net.cnn_r.alliesandfoes.map`

Key files:

- `MapScreen.java`: main map UI, modes, territory preview, war selection/review, rollback repair selection, pet revive panel, player markers, tooltips, and intuition rendering.
- `MapState.java`: client-side global map state, caches, loaded chunks, dirty flags, current render mode, world identity, influence balance, rollback/dead-pet state.
- `MapPersistence.java`: persistent client cache storage.
- `WorldIdentity.java`: world + dimension cache identity; important for avoiding cache collisions between worlds/dimensions.
- `MapRenderMode.java` and `ModeResolver.java`: surface/nether/end/cave mode logic.
- `MapRenderer.java`, `MapTexture.java`: rendering/texture update path.
- `cache/*`: map-side caches for chunks, values, structures, players, territory, territory previews, and war state.
- `scan/*`: chunk scanning and value analysis.
- `value/*`: chunk value scoring rules and server/client evaluation.
- `util/BlockColorResolver.java`: block color sampling/resolution.

Important map behavior:

- Cache data is dimension/world scoped; do not collapse by world name alone.
- `ChunkKey` is preferred whenever dimension identity matters.
- Map scanning is incremental and dirty-flag based.
- `MapState` keeps separate cache paths for surface/nether/end and tracks loaded chunks.
- `MapScreen` has multiple mode states: territory preview (`FOUND`, `CLAIM`, `UNCLAIM`), war declaration selection, repair/rollback selection, anchor cycle mode, and pet revive panel.
- Avoid large UI rewrites. Preserve existing mode state machines and button lifecycle unless intentionally changing them.

### Explorer / intuition system

Packages:

- `net.cnn_r.alliesandfoes.explorer`
- `net.cnn_r.alliesandfoes.map.intuition`
- `net.cnn_r.alliesandfoes.hud`

Key files:

- `ExplorerDiscoveryRules.java`
- `ExplorerDiscoveryService.java`
- `ExplorerDiscoverySavedData.java`
- `ExplorerDiscoveryClientState.java`
- `ExplorerSkillService.java`
- `ExplorerSkillSavedData.java`
- `ExplorerSkillClientState.java`
- `MonocleScreen.java`
- `MonocleItem.java`
- `ExplorerIntuitionEvaluator.java`
- `ExplorerIntuitionProfile.java`
- `IntuitionTarget.java`
- `IntuitionDirection.java`
- `IntuitionResult.java`
- `MapIntuitionRenderer.java`
- `MapIntuitionMessageController.java`
- `HudIntuitionRenderer.java`
- `SetIntuitionTargetPayload.java`
- `IntuitionTargetLocationPayload.java`

Current design direction:

- The monocle/journal lets explorer players select unlocked biome/structure targets.
- Biome/structure unlocks are based on discoveries/related blocks.
- Intuition should feel vanilla-like and guided, not like arbitrary GPS.
- Avoid arbitrary monocle pointing with no selected target unless explicitly requested.
- Explorer progression currently relates to discovering new chunks and can be extended into alliance influence generation.

### Roles and upgrades

Package highlights:

- `upgrade/RoleType.java`: `EXPLORER`, `WARRIOR`, `CULTIVATOR`, `PROSPECTOR`.
- `roleslot/*`: inventory role slot behavior, renderer, saved data, client state, service, and `HasRoleSlot` interface.
- `explorer/*`, `warrior/*`, `cultivator/*`, `prospector/*`: role skill services.
- `covenantforge/*`: role upgrade UI/service/block.
- `tributealtar/*`: tribute conversion UI/service/block.

Role gameplay direction:

- Roles should feed the alliance economy and territory loop.
- Explorer: discovery, biome/structure intuition, map intel.
- Prospector: ore/resource intuition and mining contribution.
- Cultivator: farming/food/nature contribution.
- Warrior: combat/war contribution.

### Items, blocks, and assets

Key files:

- `item/ModItems.java`
- `item/ModBlocks.java`
- `item/ModComponents.java`
- `item/MonocleItem.java`
- `covenantforge/CovenantForgeBlock.java`
- `tributealtar/TributeAltarBlock.java`

Visible assets include journal GUI textures, monocle texture, map rollback fire animation, block/item models, recipes, loot tables, and `en_us.json`.

### Mixins

Packages:

- `mixin/*`
- `mixin/client/*`

Current mixins include:

- Inventory role slot injection/rendering.
- Container menu invoker/access widening support.
- Explosion snapshot/chest protection hooks.
- Client block change hooks for map dirtying.
- Monocle arm pose.

Before changing mixins, inspect `alliesandfoes.mixins.json` and `alliesandfoes.accesswidener`.

## UI / screen design rules

Alliance screens use a parchment / ledger / journal visual language. Existing screens should be treated as references, especially:

- `AllianceViewScreen`
- `AllianceCreateScreen`
- `AllianceInviteScreen`
- `AllianceInviteManagementScreen`
- `AllianceJoinRequestScreen`
- `AllianceJoinScreen`

Preferred UI pattern:

- Centered parchment/journal card.
- Dark outer overlay.
- Explicit layout math.
- Header/top controls are computed top-down.
- Footer/status/actions are computed bottom-up.
- Middle list/content fills remaining space.
- Stable at high GUI scale and small windows.
- Avoid aggressive responsive scaling that makes buttons/text inconsistent.
- Use consistent padding and avoid footer/list collision.
- Do not redesign working screen layout systems unless specifically asked.

Map UI has its own mode-heavy layout. Preserve mode clarity and avoid hidden state changes. If a key toggles a mode, pressing it again should visibly exit that mode unless the requested behavior says otherwise.

## Networking conventions

Payload records are in `network/*` and commonly follow this shape:

- `public record NamePayload(...) implements CustomPacketPayload`
- `public static final Type<NamePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "..."));`
- `public static final StreamCodec<FriendlyByteBuf, NamePayload> STREAM_CODEC = StreamCodec.of(NamePayload::write, NamePayload::read);`
- Override `type()` to return `TYPE`.
- Use explicit `write` and `read` methods when needed.

When adding a new payload:

1. Create the payload record in `network`.
2. Register it in `Alliesandfoes.onInitialize()` on the correct side.
3. Register the receiver in `Alliesandfoes` for serverbound or `AlliesandfoesClient` for clientbound.
4. Ensure sends only happen after registration and only to players with a live network connection.
5. Keep server-side validation authoritative.

## Persistence conventions

Server state commonly uses `SavedData`:

- `AllianceSavedData`
- `AllianceProgressionSavedData`
- `AllianceWarSavedData`
- `WarSnapshotSavedData`
- `ExplorerDiscoverySavedData`
- `ExplorerSkillSavedData`
- `RoleSlotSavedData`
- `TerritorySavedData`

When modifying persisted data:

- Preserve backward compatibility when possible.
- Mark data dirty after changes.
- Save through existing service/manager patterns.
- Do not store client-only UI state in server `SavedData`.

## Common development mistakes to avoid

- Do not use Yarn method/class names in a Mojang-mapped project.
- Do not use old Fabric networking examples if they conflict with current `CustomPacketPayload` / `StreamCodec` patterns.
- Do not put final authority on the client.
- Do not create a second source of truth for alliance, territory, war, role, or map state.
- Do not ignore `ChunkKey` and dimension identity.
- Do not collapse cache keys by display world name.
- Do not rewrite `MapScreen` or alliance screens wholesale for small changes.
- Do not add new systems without first checking for an existing service/cache/payload that already does part of it.
- Do not assume the uploaded source is on the same Minecraft version as older chat instructions; verify current project files.

## Best prompt to use with Continue/Coding Agent

Before coding, read `docs/AI_CONTEXT.md` and `.continue/rules/fabric-modding.md`. Then inspect the relevant existing files in the current workspace. This is a Fabric mod using the current workspace mappings/Mojang names. Do not invent APIs. Keep gameplay server-authoritative. Reuse existing managers, services, payload patterns, map caches, and UI layout patterns. Explain the minimal file changes first, then provide only the changed sections unless I ask for full files.
