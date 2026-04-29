package net.cnn_r.alliesandfoes.cultivator;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.progression.AllianceProgressionService;
import net.cnn_r.alliesandfoes.alliance.war.AllianceWarService;
import net.cnn_r.alliesandfoes.feedback.FeedbackService;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.roleslot.RoleCurrencyService;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotService;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.cnn_r.alliesandfoes.territory.TerritoryClaim;
import net.cnn_r.alliesandfoes.territory.TerritoryManager;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.BonemealableBlock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Cultivator role: hold the Farmer's Almanac while walking through claimed alliance territory
 * to refresh patrol timestamps and earn currency.
 *
 * Sneak+use triggers the Harvest Ritual: costs 50 role currency, bone-meals nearby crops,
 * applies Haste I to nearby alliance members, and grants 15 alliance influence.
 */
public class CultivatorSkillService {
    private static final Map<MinecraftServer, CultivatorSkillService> INSTANCES = new WeakHashMap<>();

    private static final int RITUAL_CURRENCY_COST = 50;
    private static final int RITUAL_INFLUENCE_GRANT = 15;
    private static final long RITUAL_COOLDOWN_TICKS = 6000L; // 5 minutes

    private final MinecraftServer server;
    private final Map<UUID, Long> lastPatrolChunk  = new HashMap<>();
    private final Map<UUID, Long> lastRegenTick    = new HashMap<>();
    private final Map<UUID, Long> lastRitualTick   = new HashMap<>();

    private CultivatorSkillService(MinecraftServer server) {
        this.server = server;
    }

    public static CultivatorSkillService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, CultivatorSkillService::new);
    }

    /** Called every server tick for each player. */
    public void onPlayerTick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Alliance alliance = AllianceManager.get(server).getAllianceFor(uuid);
        if (alliance == null) return;

        int chunkX = player.blockPosition().getX() >> 4;
        int chunkZ = player.blockPosition().getZ() >> 4;
        String dimId = ((ServerLevel) player.level()).dimension().identifier().toString();
        ChunkKey key = new ChunkKey(dimId, chunkX, chunkZ);
        TerritoryClaim claim = TerritoryManager.get(server).getClaimAt(key);
        boolean inClaimedTerritory = claim != null && claim.getAllianceId().equals(alliance.getId());

        long gameTick = server.overworld().getGameTime();

        // Regen I every 5 seconds while in claimed territory during active war
        if (inClaimedTerritory && RoleSlotService.hasRoleInHand(player, RoleType.CULTIVATOR)) {
            if (AllianceWarService.get(server).getActiveWarInvolving(alliance.getId()).isPresent()) {
                Long lastRegen = lastRegenTick.get(uuid);
                if (lastRegen == null || gameTick - lastRegen >= 100) {
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 0));
                    lastRegenTick.put(uuid, gameTick);
                }
            }
        }

        // Crop growth aura: every 60 ticks in claimed territory with role active
        if (inClaimedTerritory && RoleSlotService.hasRoleInHand(player, RoleType.CULTIVATOR)
                && gameTick % 60 == 0) {
            ServerLevel sl = (ServerLevel) player.level();
            BlockPos origin = player.blockPosition();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 3; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos bp = origin.offset(dx, dy, dz);
                        net.minecraft.world.level.block.state.BlockState bs = sl.getBlockState(bp);
                        if (bs.getBlock() instanceof BonemealableBlock bonemeal
                                && bonemeal.isValidBonemealTarget(sl, bp, bs)) {
                            bonemeal.performBonemeal(sl, sl.getRandom(), bp, bs);
                        }
                    }
                }
            }
        }

        // Patrol tracking requires movement to a new chunk
        long packedChunk = ((long) chunkX << 32) | Integer.toUnsignedLong(chunkZ);
        Long lastKey = lastPatrolChunk.get(uuid);
        if (lastKey != null && lastKey == packedChunk) return;
        lastPatrolChunk.put(uuid, packedChunk);

        if (!inClaimedTerritory) return;

        boolean updated = PatrolDataService.get(server).markPatrolled(alliance.getId(), key, gameTick);

        if (updated) {
            boolean hasAlmanac = RoleSlotService.hasRoleInHand(player, RoleType.CULTIVATOR);
            int earned = hasAlmanac ? 2 : 1;
            RoleCurrencyService.get(server).add(uuid, RoleType.CULTIVATOR, earned);
            RoleCurrencyService.get(server).sync(player);
            FeedbackService.onRoleCurrencyEarned(player);
        }
    }

    /**
     * Harvest Ritual: sneak+right-click with Almanac in claimed territory.
     * Costs 50 role currency. Bone-meals crops, applies Haste to alliance, grants 15 influence.
     */
    public void tryHarvestRitual(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!RoleSlotService.hasRoleInHand(player, RoleType.CULTIVATOR)) {
            player.sendSystemMessage(Component.literal("You must hold the Farmer's Almanac to perform the Harvest Ritual."));
            return;
        }

        Alliance alliance = AllianceManager.get(server).getAllianceFor(uuid);
        if (alliance == null) {
            player.sendSystemMessage(Component.literal("You must be in an alliance."));
            return;
        }

        int chunkX = player.blockPosition().getX() >> 4;
        int chunkZ = player.blockPosition().getZ() >> 4;
        String dimId = ((ServerLevel) player.level()).dimension().identifier().toString();
        ChunkKey key = new ChunkKey(dimId, chunkX, chunkZ);
        TerritoryClaim claim = TerritoryManager.get(server).getClaimAt(key);
        if (claim == null || !claim.getAllianceId().equals(alliance.getId())) {
            player.sendSystemMessage(Component.literal("You must be in your alliance's territory."));
            return;
        }

        long gameTick = server.overworld().getGameTime();
        Long lastRitual = lastRitualTick.get(uuid);
        if (lastRitual != null && gameTick - lastRitual < RITUAL_COOLDOWN_TICKS) {
            long remaining = (RITUAL_COOLDOWN_TICKS - (gameTick - lastRitual)) / 20;
            player.sendSystemMessage(Component.literal("Harvest Ritual on cooldown (" + remaining + "s remaining)."));
            return;
        }

        int currency = RoleCurrencyService.get(server).get(uuid, RoleType.CULTIVATOR);
        if (currency < RITUAL_CURRENCY_COST) {
            player.sendSystemMessage(Component.literal(
                    "Need " + RITUAL_CURRENCY_COST + " Yield Tallies. You have " + currency + "."));
            return;
        }

        RoleCurrencyService.get(server).consume(uuid, RoleType.CULTIVATOR, RITUAL_CURRENCY_COST);
        RoleCurrencyService.get(server).sync(player);
        lastRitualTick.put(uuid, gameTick);

        ServerLevel level = (ServerLevel) player.level();
        BlockPos origin = player.blockPosition();

        // Bone meal burst in 9×9 radius
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -1; dy <= 4; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos bp = origin.offset(dx, dy, dz);
                    net.minecraft.world.level.block.state.BlockState bs = level.getBlockState(bp);
                    if (bs.getBlock() instanceof BonemealableBlock bm && bm.isValidBonemealTarget(level, bp, bs)) {
                        bm.performBonemeal(level, level.getRandom(), bp, bs);
                    }
                }
            }
        }

        // Haste I for 20 seconds to all online alliance members in same dimension
        List<ServerPlayer> members = server.getPlayerList().getPlayers().stream()
                .filter(p -> alliance.getMemberUuids().contains(p.getUUID())
                          && p.level().dimension().equals(player.level().dimension()))
                .toList();
        for (ServerPlayer member : members) {
            member.addEffect(new MobEffectInstance(MobEffects.HASTE, 400, 0));
        }

        // Grant alliance influence
        AllianceProgressionService.get(server).add(alliance.getId(), RITUAL_INFLUENCE_GRANT);

        // Feedback particles and sound
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5,
                30, 3.0, 1.0, 3.0, 0.1);
        level.sendParticles(ParticleTypes.WAX_ON,
                origin.getX() + 0.5, origin.getY() + 1.5, origin.getZ() + 0.5,
                20, 2.0, 1.0, 2.0, 0.05);
        level.playSound(null, origin, SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0f, 0.9f);
        level.playSound(null, origin, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8f, 1.1f);

        for (ServerPlayer member : members) {
            member.sendSystemMessage(Component.literal(
                    "✦ " + player.getName().getString() + " performed the Harvest Ritual! +Haste granted."));
        }
    }

    public void onPlayerDisconnect(UUID uuid) {
        lastPatrolChunk.remove(uuid);
        lastRegenTick.remove(uuid);
        lastRitualTick.remove(uuid);
    }
}
