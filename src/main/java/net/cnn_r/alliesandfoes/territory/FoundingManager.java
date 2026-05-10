package net.cnn_r.alliesandfoes.territory;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.block.TerritoryAnchorBlock;
import net.cnn_r.alliesandfoes.network.TerritoryChunkBatchPayload;
import net.cnn_r.alliesandfoes.territory.TerritoryManager.ActionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class FoundingManager {

    private static final Map<MinecraftServer, FoundingManager> INSTANCES = new WeakHashMap<>();

    private final Map<String, FoundingSession> sessions = new HashMap<>();
    private final Map<String, CustomBossEvent> waveBars = new HashMap<>();
    private final Map<String, CustomBossEvent> healthBars = new HashMap<>();
    private final MinecraftServer server;

    private FoundingManager(MinecraftServer server) {
        this.server = server;
    }

    public static FoundingManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, FoundingManager::new);
    }

    // ---- public API ----

    public boolean isActive(ServerLevel level, BlockPos pos) {
        return sessions.containsKey(key(level, pos));
    }

    public void tryStartFounding(ServerLevel level, BlockPos pos, ServerPlayer player) {
        String k = key(level, pos);
        if (sessions.containsKey(k)) {
            player.sendSystemMessage(Component.literal("A ritual is already in progress here.").withStyle(ChatFormatting.RED));
            return;
        }

        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) {
            player.sendSystemMessage(Component.literal("You must be in an alliance to found territory.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!alliance.getOwnerUuid().equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Only the alliance owner may start a founding ritual.").withStyle(ChatFormatting.RED));
            return;
        }

        ChunkKey chunk = ChunkKey.of(level, new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
        if (TerritoryManager.get(server).isClaimed(chunk)) {
            player.sendSystemMessage(Component.literal("This chunk is already claimed.").withStyle(ChatFormatting.RED));
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TerritoryAnchorBlock)) return;
        level.setBlockAndUpdate(pos, state.setValue(TerritoryAnchorBlock.ACTIVE, true));

        FoundingSession session = new FoundingSession(
                pos,
                level.dimension().identifier().toString(),
                player.getUUID(),
                alliance.getId()
        );
        sessions.put(k, session);

        createHealthBar(k, session, level);
        startWave(level, session, 1);
    }

    public void onAnchorDestroyed(ServerLevel level, BlockPos pos) {
        String k = key(level, pos);
        FoundingSession session = sessions.remove(k);
        if (session != null && !session.isFailed()) {
            broadcastNear(level, pos, Component.literal("The anchor was destroyed — ritual failed!").withStyle(ChatFormatting.RED));
            removeBars(k);
        }
    }

    public void tick() {
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, FoundingSession> entry : sessions.entrySet()) {
            String k = entry.getKey();
            FoundingSession session = entry.getValue();

            // Anchor health → 0: session.fail(reason) was called from AnchorAttackGoal
            if (session.isFailed()) {
                ServerLevel failLevel = resolveLevel(session.getDimensionId());
                if (failLevel != null && session.getFailureReason() != null) {
                    broadcastNear(failLevel, session.getAnchorPos(),
                            Component.literal(session.getFailureReason()).withStyle(ChatFormatting.RED));
                    BlockState bs = failLevel.getBlockState(session.getAnchorPos());
                    if (bs.getBlock() instanceof TerritoryAnchorBlock) {
                        failLevel.setBlockAndUpdate(session.getAnchorPos(), bs.setValue(TerritoryAnchorBlock.ACTIVE, false));
                    }
                }
                removeBars(k);
                toRemove.add(k);
                continue;
            }

            ServerLevel level = resolveLevel(session.getDimensionId());
            if (level == null) { session.fail(null); toRemove.add(k); continue; }

            BlockState bs = level.getBlockState(session.getAnchorPos());
            if (!(bs.getBlock() instanceof TerritoryAnchorBlock)) {
                broadcastNear(level, session.getAnchorPos(),
                        Component.literal("Anchor removed — ritual failed!").withStyle(ChatFormatting.RED));
                removeBars(k);
                toRemove.add(k);
                continue;
            }

            // Refresh bar players every second (alliance-scoped)
            if (server.getTickCount() % 20 == 0) {
                refreshBarPlayers(k, level, session.getAnchorPos(), session.getAllianceId());
            }

            // Tick glow timer
            if (session.tickGlow()) {
                clearGlow(session, level);
            }

            if (session.isInDelay()) {
                if (session.tickDelay()) {
                    int nextWave = session.getCurrentWave() + 1;
                    if (nextWave > FoundingSession.TOTAL_WAVES) {
                        completeRitual(level, session, k);
                        toRemove.add(k);
                    } else {
                        startWave(level, session, nextWave);
                    }
                }
                continue;
            }

            session.pruneDeadMobs(uuid -> {
                Entity e = level.getEntity(uuid);
                return e != null && e.isAlive();
            });

            int alive = session.getActiveMobUuids().size();
            updateWaveBar(k, session, alive);

            // Steer live ritual mobs toward anchor; damage anchor when a mob reaches it
            BlockPos anchor = session.getAnchorPos();
            boolean mobAdjacent = false;
            for (UUID uuid : session.getActiveMobUuids()) {
                Entity e = level.getEntity(uuid);
                if (!(e instanceof Mob mob) || !mob.isAlive()) continue;
                double distSq = mob.distanceToSqr(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
                if (distSq <= 2.25) {
                    mobAdjacent = true;
                } else {
                    mob.getNavigation().moveTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, 1.2);
                }
            }
            if (session.tickAnchorAttack(mobAdjacent)) {
                session.damageAnchor(2);
                updateHealthBar(k, session);
                if (session.isAnchorDestroyed()) {
                    session.fail("The anchor was overwhelmed — ritual failed!");
                }
            }

            if (!session.hasLivingMobs()) {
                if (session.isLastWave()) {
                    updateWaveBarTitle(k, "✦ All waves survived! Founding...");
                    session.startCompleteDelay();
                } else {
                    updateWaveBarTitle(k, "Wave " + session.getCurrentWave() + " cleared — next wave soon!");
                    session.startInterWaveDelay();
                }
            }
        }

        toRemove.forEach(sessions::remove);
    }

    /** Called when a ritual mob dies — emits soul particles toward the anchor. */
    public void onRitualMobDied(UUID mobUuid, Vec3 deathPos, ServerLevel level) {
        String dimId = level.dimension().identifier().toString();
        for (FoundingSession session : sessions.values()) {
            if (!session.getDimensionId().equals(dimId)) continue;
            if (!session.getActiveMobUuids().contains(mobUuid)) continue;

            BlockPos anchor = session.getAnchorPos();
            Vec3 dir = new Vec3(
                    anchor.getX() + 0.5 - deathPos.x,
                    anchor.getY() + 0.5 - deathPos.y,
                    anchor.getZ() + 0.5 - deathPos.z
            ).normalize().scale(0.3);

            for (int i = 0; i < 5; i++) {
                double sx = (level.getRandom().nextDouble() - 0.5) * 0.15;
                double sy = (level.getRandom().nextDouble() - 0.5) * 0.15;
                double sz = (level.getRandom().nextDouble() - 0.5) * 0.15;
                level.sendParticles(ParticleTypes.SOUL,
                        deathPos.x + sx, deathPos.y + 0.5 + sy, deathPos.z + sz,
                        0, dir.x, dir.y, dir.z, 1.0);
            }
            break;
        }
    }

    /** Called when the active anchor is right-clicked — glows all live ritual mobs for 10 s. */
    public void handleAnchorClick(BlockPos pos, ServerLevel level) {
        String k = key(level, pos);
        FoundingSession session = sessions.get(k);
        if (session == null || session.getCurrentWave() == 0 || session.getActiveMobUuids().isEmpty()) return;

        for (UUID uuid : session.getActiveMobUuids()) {
            Entity e = level.getEntity(uuid);
            if (e != null && e.isAlive()) e.setGlowingTag(true);
        }
        session.startGlow(200);
    }

    // ---- internal ----

    private void startWave(ServerLevel level, FoundingSession session, int wave) {
        session.startWave(wave);
        List<EntityType<?>> mobs = getMobsForWave(wave);
        session.setTotalWaveMobs(mobs.size());

        String k = key(session);
        createWaveBar(k, session, wave, level);

        for (EntityType<?> type : mobs) {
            UUID uuid = spawnMob(level, type, session);
            if (uuid != null) session.addMob(uuid);
        }
    }

    private List<EntityType<?>> getMobsForWave(int wave) {
        return switch (wave) {
            case 1 -> List.of(EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.ZOMBIE);
            case 2 -> List.of(EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.ZOMBIE,
                    EntityType.SKELETON, EntityType.SKELETON);
            case 3 -> List.of(EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.ZOMBIE,
                    EntityType.SKELETON, EntityType.SKELETON, EntityType.SKELETON, EntityType.CREEPER);
            default -> List.of();
        };
    }

    private UUID spawnMob(ServerLevel level, EntityType<?> type, FoundingSession session) {
        int inner = 12, outer = 20;
        var rng = level.getRandom();
        int dx = (inner + rng.nextInt(outer - inner + 1)) * (rng.nextBoolean() ? 1 : -1);
        int dz = (inner + rng.nextInt(outer - inner + 1)) * (rng.nextBoolean() ? 1 : -1);
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                session.getAnchorPos().offset(dx, 0, dz));

        var entity = type.create(level, EntitySpawnReason.EVENT);
        if (entity == null) return null;

        entity.setPos(surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5);
        entity.setYRot(level.getRandom().nextFloat() * 360);
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(surface), EntitySpawnReason.EVENT, null);
        }
        level.addFreshEntity(entity);
        return entity.getUUID();
    }

    private void completeRitual(ServerLevel level, FoundingSession session, String k) {
        removeBars(k);

        BlockState bs = level.getBlockState(session.getAnchorPos());
        if (bs.getBlock() instanceof TerritoryAnchorBlock) {
            level.setBlockAndUpdate(session.getAnchorPos(), bs.setValue(TerritoryAnchorBlock.ACTIVE, false));
        }

        ChunkKey chunk = new ChunkKey(session.getDimensionId(),
                session.getAnchorPos().getX() >> 4, session.getAnchorPos().getZ() >> 4);

        TerritoryManager tm = TerritoryManager.get(server);
        ActionResult result = tm.foundAnchor(
                session.getInitiatorUuid(),
                "Territory Anchor",
                chunk,
                AnchorTier.BASIC,
                true
        );

        if (result.success()) {
            broadcastNear(level, session.getAnchorPos(),
                    Component.literal("Territory founded! This chunk now belongs to your alliance.")
                            .withStyle(ChatFormatting.GOLD));
            TerritoryQueryService qs = new TerritoryQueryService(tm);
            TerritoryChunkBatchPayload payload = TerritoryMapSyncService.buildChunkBatch(qs, List.of(chunk));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, payload);
            }
        } else {
            broadcastNear(level, session.getAnchorPos(),
                    Component.literal("Ritual complete but founding failed: " + result.message())
                            .withStyle(ChatFormatting.RED));
        }
    }

    // ---- boss bar helpers ----

    private void createWaveBar(String k, FoundingSession session, int wave, ServerLevel level) {
        CustomBossEvent old = waveBars.remove(k);
        if (old != null) { old.removeAllPlayers(); server.getCustomBossEvents().remove(old); }

        Identifier barId = Identifier.parse(
                "alliesandfoes:fw" + Integer.toHexString(k.hashCode() & 0x7fffffff) + "w" + wave);
        CustomBossEvent stale = server.getCustomBossEvents().get(barId);
        if (stale != null) server.getCustomBossEvents().remove(stale);

        CustomBossEvent bar = server.getCustomBossEvents().create(level.getRandom(), barId,
                Component.literal("⚔ Wave " + wave + " / " + FoundingSession.TOTAL_WAVES));
        bar.setColor(BossEvent.BossBarColor.YELLOW);
        bar.setOverlay(BossEvent.BossBarOverlay.NOTCHED_10);
        bar.setCreateWorldFog(false);
        bar.setDarkenScreen(false);
        bar.setPlayBossMusic(false);
        bar.setProgress(1.0f);
        waveBars.put(k, bar);
        refreshBarPlayers(k, level, session.getAnchorPos(), session.getAllianceId());
    }

    private void updateWaveBar(String k, FoundingSession session, int alive) {
        CustomBossEvent bar = waveBars.get(k);
        if (bar == null) return;
        int total = session.getTotalWaveMobs();
        bar.setProgress(total > 0 ? Math.max(0f, (float) alive / total) : 0f);
    }

    private void updateWaveBarTitle(String k, String title) {
        CustomBossEvent bar = waveBars.get(k);
        if (bar != null) bar.setName(Component.literal(title));
    }

    private void createHealthBar(String k, FoundingSession session, ServerLevel level) {
        Identifier barId = Identifier.parse("alliesandfoes:fh" + Integer.toHexString(k.hashCode() & 0x7fffffff));
        CustomBossEvent stale = server.getCustomBossEvents().get(barId);
        if (stale != null) server.getCustomBossEvents().remove(stale);

        CustomBossEvent bar = server.getCustomBossEvents().create(level.getRandom(), barId,
                Component.literal("✦ Anchor"));
        bar.setColor(BossEvent.BossBarColor.GREEN);
        bar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
        bar.setCreateWorldFog(false);
        bar.setDarkenScreen(false);
        bar.setPlayBossMusic(false);
        bar.setProgress(1.0f);
        healthBars.put(k, bar);
        refreshBarPlayers(k, level, session.getAnchorPos(), session.getAllianceId());
    }

    private void updateHealthBar(String k, FoundingSession session) {
        CustomBossEvent bar = healthBars.get(k);
        if (bar == null) return;
        float frac = session.anchorHealthFraction();
        bar.setProgress(Math.max(0f, frac));
        if (frac < 0.3f) bar.setColor(BossEvent.BossBarColor.RED);
        else if (frac < 0.6f) bar.setColor(BossEvent.BossBarColor.YELLOW);
    }

    private void refreshBarPlayers(String k, ServerLevel level, BlockPos pos, UUID allianceId) {
        CustomBossEvent wBar = waveBars.get(k);
        CustomBossEvent hBar = healthBars.get(k);
        if (wBar == null && hBar == null) return;

        double rangeSq = 128.0 * 128.0;
        List<ServerPlayer> members = level.players().stream()
                .filter(p -> p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) <= rangeSq)
                .filter(p -> {
                    net.cnn_r.alliesandfoes.alliance.Alliance a =
                            net.cnn_r.alliesandfoes.alliance.AllianceManager.get(server).getAllianceFor(p.getUUID());
                    return a != null && a.getId().equals(allianceId);
                })
                .toList();

        if (wBar != null) { wBar.removeAllPlayers(); members.forEach(wBar::addPlayer); }
        if (hBar != null) { hBar.removeAllPlayers(); members.forEach(hBar::addPlayer); }
    }

    private void removeBars(String k) {
        CustomBossEvent wBar = waveBars.remove(k);
        if (wBar != null) { wBar.removeAllPlayers(); server.getCustomBossEvents().remove(wBar); }
        CustomBossEvent hBar = healthBars.remove(k);
        if (hBar != null) { hBar.removeAllPlayers(); server.getCustomBossEvents().remove(hBar); }
    }

    // ---- glow helper ----

    private void clearGlow(FoundingSession session, ServerLevel level) {
        for (UUID uuid : session.getActiveMobUuids()) {
            Entity e = level.getEntity(uuid);
            if (e != null) e.setGlowingTag(false);
        }
    }

    // ---- misc helpers ----

    private ServerLevel resolveLevel(String dimensionId) {
        for (ServerLevel l : server.getAllLevels()) {
            if (l.dimension().identifier().toString().equals(dimensionId)) return l;
        }
        return null;
    }

    private void broadcastNear(ServerLevel level, BlockPos pos, Component message) {
        double rangeSq = 64 * 64;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) <= rangeSq) {
                player.sendSystemMessage(message);
            }
        }
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().identifier() + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    private static String key(FoundingSession session) {
        BlockPos p = session.getAnchorPos();
        return session.getDimensionId() + ":" + p.getX() + ":" + p.getY() + ":" + p.getZ();
    }
}
