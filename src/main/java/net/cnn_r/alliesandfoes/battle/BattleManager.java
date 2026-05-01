package net.cnn_r.alliesandfoes.battle;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.influence.AllianceInfluenceService;
import net.cnn_r.alliesandfoes.alliance.war.AllianceWar;
import net.cnn_r.alliesandfoes.alliance.war.AllianceWarService;
import net.cnn_r.alliesandfoes.alliance.war.WarStatus;
import net.cnn_r.alliesandfoes.item.ModBlocks;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.network.BattleBaseSelectPayload;
import net.cnn_r.alliesandfoes.network.BattleChallengePayload;
import net.cnn_r.alliesandfoes.network.BattleEndPayload;
import net.cnn_r.alliesandfoes.network.BattleStartPayload;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.cnn_r.alliesandfoes.territory.TerritoryAnchor;
import net.cnn_r.alliesandfoes.territory.TerritoryManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class BattleManager {
    private static final Map<MinecraftServer, BattleManager> INSTANCES = new WeakHashMap<>();

    private static final int BATTLE_TIMEOUT_TICKS = 20 * 60 * 30; // 30 minutes
    private static final int COPY_CHUNKS_PER_TICK = 2;

    private final MinecraftServer server;
    private final List<BattleSession> sessions = new ArrayList<>();

    private BattleManager(MinecraftServer server) {
        this.server = server;
    }

    public static BattleManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, BattleManager::new);
    }

    // =========================================================================
    // Challenge flow
    // =========================================================================

    public void challenge(ServerPlayer challenger, Alliance targetAlliance) {
        Alliance challengerAlliance = AllianceManager.get(server).getAllianceFor(challenger.getUUID());
        if (challengerAlliance == null) {
            challenger.sendSystemMessage(Component.literal("§cYou must be in an alliance to challenge others."), true);
            return;
        }
        if (AllianceWarService.get(server).areEngaged(challengerAlliance.getId(), targetAlliance.getId())) {
            challenger.sendSystemMessage(Component.literal("§cThese alliances are already in a battle."), true);
            return;
        }

        UUID battleId = UUID.randomUUID();
        BattleSession session = new BattleSession(battleId, challengerAlliance.getId(), targetAlliance.getId());
        for (UUID m : challengerAlliance.getMemberUuids()) session.membersA.add(m);
        for (UUID m : targetAlliance.getMemberUuids()) session.membersB.add(m);
        sessions.add(session);

        AllianceWar war = AllianceWar.create(challengerAlliance.getId(), targetAlliance.getId(), new HashSet<>());
        AllianceWarService.get(server).registerWar(war);

        ServerPlayer defenderFounder = server.getPlayerList().getPlayer(targetAlliance.getOwnerUuid());
        if (defenderFounder != null) {
            ServerPlayNetworking.send(defenderFounder, new BattleChallengePayload(
                    battleId, challengerAlliance.getId(), challengerAlliance.getName()));
        } else {
            challenger.sendSystemMessage(Component.literal("§cThe defender's founder is not online."), true);
            sessions.remove(session);
        }
    }

    public void respond(ServerPlayer responder, UUID battleId, boolean accept, PrizeType prize) {
        BattleSession session = findSession(battleId);
        if (session == null) return;

        if (!accept) {
            sessions.remove(session);
            Alliance challenger = AllianceManager.get(server).getAllianceById(session.allianceAId);
            if (challenger != null) {
                AllianceWarService.get(server).notifyAlliance(challenger,
                        Component.literal("Battle challenge declined by " + AllianceManager.get(server).getAllianceById(session.allianceBId).getName())
                                .withStyle(ChatFormatting.RED));
            }
            return;
        }

        session.prizeB = prize;

        // Send base selection screen to both founders
        sendBaseSelectToFounder(session, session.allianceAId);
        sendBaseSelectToFounder(session, session.allianceBId);
    }

    private void sendBaseSelectToFounder(BattleSession session, UUID allianceId) {
        Alliance alliance = AllianceManager.get(server).getAllianceById(allianceId);
        if (alliance == null) return;
        ServerPlayer founder = server.getPlayerList().getPlayer(alliance.getOwnerUuid());
        if (founder == null) return;

        List<TerritoryAnchor> anchors = TerritoryManager.get(server).getAnchorsForAlliance(allianceId);
        List<BattleBaseSelectPayload.AnchorEntry> entries = new ArrayList<>();
        for (TerritoryAnchor anchor : anchors) {
            int claimCount = TerritoryManager.get(server).getClaimsForAnchor(anchor.getAnchorId()).size();
            entries.add(new BattleBaseSelectPayload.AnchorEntry(anchor.getAnchorId(), anchor.getName(), claimCount));
        }
        ServerPlayNetworking.send(founder, new BattleBaseSelectPayload(session.battleId, entries));
    }

    public void onBaseChosen(ServerPlayer founder, UUID battleId, UUID anchorId) {
        BattleSession session = findSession(battleId);
        if (session == null) return;

        Alliance allianceA = AllianceManager.get(server).getAllianceById(session.allianceAId);
        Alliance allianceB = AllianceManager.get(server).getAllianceById(session.allianceBId);

        if (allianceA != null && allianceA.getOwnerUuid().equals(founder.getUUID())) {
            session.chosenAnchorA = anchorId;
        } else if (allianceB != null && allianceB.getOwnerUuid().equals(founder.getUUID())) {
            session.chosenAnchorB = anchorId;
        }

        if (session.chosenAnchorA != null && session.chosenAnchorB != null) {
            startCopying(session);
        }
    }

    // =========================================================================
    // Copy phase
    // =========================================================================

    private void startCopying(BattleSession session) {
        session.status = WarStatus.COPYING;
        session.zOffset = BattleDimensionManager.get(server).allocateOffset();

        for (ChunkKey c : TerritoryManager.get(server).getClaimsForAnchor(session.chosenAnchorA)
                .stream().map(cl -> cl.getChunkKey()).toList()) {
            session.chunksA.add(c);
        }
        for (ChunkKey c : TerritoryManager.get(server).getClaimsForAnchor(session.chosenAnchorB)
                .stream().map(cl -> cl.getChunkKey()).toList()) {
            session.chunksB.add(c);
        }

        AllianceWar war = AllianceWarService.get(server).getWarById(session.battleId);
        if (war != null) {
            AllianceWar updating = war.withStatus(WarStatus.COPYING, server.getTickCount());
            AllianceWarService.get(server).updateWar(war, updating);
        }

        notifyBoth(session, "⚔ Battle preparation starting — copying territory...", ChatFormatting.YELLOW);
    }

    // =========================================================================
    // Tick
    // =========================================================================

    public void tick(MinecraftServer server) {
        for (BattleSession session : new ArrayList<>(sessions)) {
            if (session.status == WarStatus.COPYING) {
                tickCopying(session);
            } else if (session.status == WarStatus.ACTIVE) {
                tickActive(session);
            }
        }
    }

    private void tickCopying(BattleSession session) {
        ServerLevel battleLevel = BattleDimensionManager.get(server).getBattleLevel(server);
        if (battleLevel == null) {
            // Battle dimension not loaded — skip until available
            return;
        }

        List<ChunkKey> allChunks = new ArrayList<>();
        allChunks.addAll(session.chunksA);
        allChunks.addAll(session.chunksB);

        int copied = 0;
        while (session.copyProgress < allChunks.size() && copied < COPY_CHUNKS_PER_TICK) {
            ChunkKey chunk = allChunks.get(session.copyProgress);
            boolean isA = session.chunksA.contains(chunk);
            String dimId = chunk.getDimensionId();
            ServerLevel source = getLevel(dimId);
            if (source != null) {
                int xOffset = isA ? 0 : ChunkCopyService.computeTeamBXOffset(new ArrayList<>(session.chunksA));
                ChunkCopyService.copyOneChunk(source, battleLevel, chunk, xOffset, session.zOffset);
            }
            session.copyProgress++;
            copied++;
        }

        if (session.copyProgress >= allChunks.size()) {
            activateBattle(session);
        }
    }

    private void tickActive(BattleSession session) {
        if (session.startTick < 0) return;
        long elapsed = server.getTickCount() - session.startTick;
        if (elapsed > BATTLE_TIMEOUT_TICKS) {
            // Time limit — determine winner by kills
            int aKills = session.killsA;
            int bKills = session.killsB;
            UUID winner = aKills >= bKills ? session.allianceAId : session.allianceBId;
            endBattle(session, winner);
        }
    }

    // =========================================================================
    // Activate
    // =========================================================================

    private void activateBattle(BattleSession session) {
        session.status = WarStatus.ACTIVE;
        session.startTick = server.getTickCount();

        AllianceWarService.get(server).activateWar(session.battleId);

        ServerLevel battleLevel = BattleDimensionManager.get(server).getBattleLevel(server);
        if (battleLevel == null) return;

        // Place base generators
        int xOffsetB = ChunkCopyService.computeTeamBXOffset(new ArrayList<>(session.chunksA));

        // Find a spawn pos for each base generator (use center of the first anchor chunk)
        session.generatorPosA = findSpawnPos(session.chunksA, 0, session.zOffset);
        session.generatorPosB = findSpawnPos(session.chunksB, xOffsetB, session.zOffset);

        if (session.generatorPosA != null) {
            battleLevel.setBlock(session.generatorPosA, ModBlocks.BASE_GENERATOR.defaultBlockState(), 3);
        }
        if (session.generatorPosB != null) {
            battleLevel.setBlock(session.generatorPosB, ModBlocks.BASE_GENERATOR.defaultBlockState(), 3);
        }

        // Place resource islands in the middle
        placeResourceIslands(battleLevel, session, xOffsetB);

        // Teleport and equip all participants
        for (UUID uuid : session.getAllParticipants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            boolean isTeamA = session.isOnTeamA(uuid);
            giveKit(player, isTeamA);
            BlockPos spawn = isTeamA ? session.generatorPosA : session.generatorPosB;
            if (spawn == null) spawn = new BlockPos(0, 64, session.zOffset);
            teleport(player, battleLevel, spawn.getX() + 0.5, spawn.getY() + 1.5, spawn.getZ() + 0.5);
            ServerPlayNetworking.send(player, new BattleStartPayload(session.battleId, isTeamA));
        }

        notifyBoth(session, "⚔ Battle has begun!", ChatFormatting.RED);
    }

    private BlockPos findSpawnPos(Set<ChunkKey> chunks, int xOffset, int zOffset) {
        if (chunks.isEmpty()) return new BlockPos(xOffset + 8, 64, zOffset + 8);
        ChunkKey first = chunks.iterator().next();
        int baseX = (first.getChunkX() << 4) + xOffset + 8;
        int baseZ = (first.getChunkZ() << 4) + zOffset + 8;
        return new BlockPos(baseX, 64, baseZ);
    }

    private void placeResourceIslands(ServerLevel level, BattleSession session, int xOffsetB) {
        int midX = xOffsetB / 2;
        int z = session.zOffset + 8;

        int[][] platforms = {{midX - 16, z}, {midX, z}, {midX + 16, z}};
        int[] types = {0, 1, 2}; // copper, iron, gold

        for (int i = 0; i < platforms.length; i++) {
            int px = platforms[i][0];
            int pz = platforms[i][1];

            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlock(new BlockPos(px + dx, 62, pz + dz),
                            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                }
            }

            BlockState genState = ModBlocks.RESOURCE_GENERATOR.defaultBlockState()
                    .setValue(net.cnn_r.alliesandfoes.block.ResourceGeneratorBlock.TOKEN_TYPE, types[i]);
            level.setBlock(new BlockPos(px, 63, pz), genState, 3);
        }
    }

    private void giveKit(ServerPlayer player, boolean isTeamA) {
        player.getInventory().clearContent();

        int color = isTeamA ? 0xCC3333 : 0x3333CC;

        player.setItemSlot(EquipmentSlot.HEAD, dyeLeather(new ItemStack(Items.LEATHER_HELMET), color));
        player.setItemSlot(EquipmentSlot.CHEST, dyeLeather(new ItemStack(Items.LEATHER_CHESTPLATE), color));
        player.setItemSlot(EquipmentSlot.LEGS, dyeLeather(new ItemStack(Items.LEATHER_LEGGINGS), color));
        player.setItemSlot(EquipmentSlot.FEET, dyeLeather(new ItemStack(Items.LEATHER_BOOTS), color));

        player.getInventory().add(new ItemStack(Items.WOODEN_SWORD));
        player.getInventory().add(new ItemStack(Items.CROSSBOW));
        player.getInventory().add(new ItemStack(Items.ARROW, 16));
        player.getInventory().add(new ItemStack(Items.WHITE_WOOL, 8));
        player.getInventory().add(new ItemStack(ModItems.COPPER_TOKEN, 5));
    }

    private ItemStack dyeLeather(ItemStack stack, int color) {
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
        return stack;
    }

    private void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        if (player.level() instanceof ServerLevel current && current == level) {
            player.teleportTo(x, y, z);
        } else {
            player.teleport(new TeleportTransition(level, new Vec3(x, y, z), Vec3.ZERO,
                    player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
        }
    }

    // =========================================================================
    // Kill / respawn / end
    // =========================================================================

    public void onPlayerKill(UUID battleId, ServerPlayer killer, ServerPlayer victim) {
        BattleSession session = findSession(battleId);
        if (session == null) return;

        if (session.isOnTeamA(killer.getUUID())) session.killsA++;
        else if (session.isOnTeamB(killer.getUUID())) session.killsB++;
    }

    public void onPlayerRespawn(ServerPlayer player) {
        BattleSession session = findSessionForPlayer(player.getUUID());
        if (session == null || session.status != WarStatus.ACTIVE) return;

        ServerLevel battleLevel = BattleDimensionManager.get(server).getBattleLevel(server);
        if (battleLevel == null) return;

        boolean isTeamA = session.isOnTeamA(player.getUUID());
        boolean generatorAlive = isTeamA ? session.generatorAAlive : session.generatorBAlive;

        if (!generatorAlive) {
            player.setGameMode(GameType.SPECTATOR);
        } else {
            BlockPos spawn = isTeamA ? session.generatorPosA : session.generatorPosB;
            if (spawn == null) spawn = new BlockPos(0, 64, session.zOffset);
            teleport(player, battleLevel, spawn.getX() + 0.5, spawn.getY() + 1.5, spawn.getZ() + 0.5);
        }
    }

    public void onGeneratorDestroyed(UUID battleId, UUID teamAllianceId) {
        BattleSession session = findSession(battleId);
        if (session == null) return;

        if (teamAllianceId.equals(session.allianceAId)) {
            session.generatorAAlive = false;
        } else {
            session.generatorBAlive = false;
        }

        UUID winner = teamAllianceId.equals(session.allianceAId) ? session.allianceBId : session.allianceAId;
        endBattle(session, winner);
    }

    public void endBattle(BattleSession session, UUID winnerAllianceId) {
        sessions.remove(session);

        AllianceWarService.get(server).endWar(session.battleId, winnerAllianceId);

        UUID loserAllianceId = winnerAllianceId.equals(session.allianceAId) ? session.allianceBId : session.allianceAId;
        PrizeType winnerPrize = winnerAllianceId.equals(session.allianceAId) ? session.prizeA : session.prizeB;
        PrizeType loserPrize = winnerAllianceId.equals(session.allianceAId) ? session.prizeB : session.prizeA;

        Alliance winnerAlliance = AllianceManager.get(server).getAllianceById(winnerAllianceId);
        String winnerName = winnerAlliance != null ? winnerAlliance.getName() : "Unknown";

        // Distribute prize
        if (winnerPrize != null && winnerAlliance != null) {
            distributePrize(winnerAlliance, winnerPrize);
        }

        // Return all players and restore inventories
        ServerLevel overworld = server.overworld();
        for (UUID uuid : session.getAllParticipants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            List<ItemStack> saved = AllianceWarService.get(server).popSavedInventory(uuid);
            player.getInventory().clearContent();
            if (saved != null) {
                for (int i = 0; i < Math.min(36, saved.size()); i++) {
                    player.getInventory().setItem(i, saved.get(i));
                }
            }

            player.setGameMode(GameType.SURVIVAL);
            BlockPos spawnPos = overworld.getRespawnData().pos();
            player.teleport(new TeleportTransition(overworld,
                    new Vec3(spawnPos.getX() + 0.5, spawnPos.getY() + 1.5, spawnPos.getZ() + 0.5),
                    Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));

            String wp = winnerPrize != null ? winnerPrize.displayName() : "None";
            String lp = loserPrize != null ? loserPrize.displayName() : "None";
            ServerPlayNetworking.send(player, new BattleEndPayload(session.battleId, winnerName, wp, lp));
        }

        // Wipe battle chunks
        wipeChunks(session);

        BattleDimensionManager.get(server).freeOffset(session.zOffset);
    }

    private void distributePrize(Alliance alliance, PrizeType prize) {
        for (UUID memberId : alliance.getMemberUuids()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player == null) continue;
            switch (prize) {
                case DIAMONDS -> player.getInventory().add(new ItemStack(Items.DIAMOND, 10));
                case HERO_OF_THE_VILLAGE -> player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 20 * 60 * 10, 0));
                case STRENGTH -> player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 60 * 10, 1));
                case RESISTANCE -> player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60 * 10, 0));
                case XP_LEVELS -> player.giveExperienceLevels(5);
            }
        }
    }

    private void wipeChunks(BattleSession session) {
        ServerLevel battleLevel = BattleDimensionManager.get(server).getBattleLevel(server);
        if (battleLevel == null) return;

        int xOffsetB = ChunkCopyService.computeTeamBXOffset(new ArrayList<>(session.chunksA));

        for (ChunkKey chunk : session.chunksA) {
            clearChunk(battleLevel, chunk, 0, session.zOffset);
        }
        for (ChunkKey chunk : session.chunksB) {
            clearChunk(battleLevel, chunk, xOffsetB, session.zOffset);
        }
    }

    private void clearChunk(ServerLevel level, ChunkKey chunk, int xOffset, int zOffset) {
        int baseX = (chunk.getChunkX() << 4) + xOffset;
        int baseZ = (chunk.getChunkZ() << 4) + zOffset;
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    level.setBlock(new BlockPos(baseX + x, y, baseZ + z),
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    // =========================================================================
    // Free agent join
    // =========================================================================

    public void freeAgentJoin(ServerPlayer player, UUID allianceId) {
        for (BattleSession session : sessions) {
            if (session.status != WarStatus.COPYING) continue;
            if (allianceId.equals(session.allianceAId)) {
                if (!session.freeAgentsA.contains(player.getUUID())) {
                    session.freeAgentsA.add(player.getUUID());
                    player.sendSystemMessage(Component.literal("§aYou will join the battle when it starts."), true);
                }
                return;
            } else if (allianceId.equals(session.allianceBId)) {
                if (!session.freeAgentsB.contains(player.getUUID())) {
                    session.freeAgentsB.add(player.getUUID());
                    player.sendSystemMessage(Component.literal("§aYou will join the battle when it starts."), true);
                }
                return;
            }
        }
        player.sendSystemMessage(Component.literal("§cNo active battle to join for that alliance."), true);
    }

    // =========================================================================
    // Queries
    // =========================================================================

    public UUID getBattleForPlayer(UUID playerUuid) {
        for (BattleSession session : sessions) {
            if (session.getAllParticipants().contains(playerUuid)) return session.battleId;
        }
        return null;
    }

    public BattleSession findSession(UUID battleId) {
        for (BattleSession s : sessions) {
            if (s.battleId.equals(battleId)) return s;
        }
        return null;
    }

    public BattleSession findSessionForPlayer(UUID playerUuid) {
        for (BattleSession s : sessions) {
            if (s.getAllParticipants().contains(playerUuid)) return s;
        }
        return null;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void notifyBoth(BattleSession session, String message, ChatFormatting color) {
        Component c = Component.literal(message).withStyle(color);
        Alliance a = AllianceManager.get(server).getAllianceById(session.allianceAId);
        Alliance b = AllianceManager.get(server).getAllianceById(session.allianceBId);
        if (a != null) AllianceWarService.get(server).notifyAlliance(a, c);
        if (b != null) AllianceWarService.get(server).notifyAlliance(b, c);
    }

    private ServerLevel getLevel(String dimensionId) {
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimensionId));
            return server.getLevel(key);
        } catch (Exception e) {
            return null;
        }
    }
}
