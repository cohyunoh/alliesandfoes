package net.cnn_r.alliesandfoes.item;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.war.AllianceWarService;
import net.cnn_r.alliesandfoes.network.MapScreenMessagePayload;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotService;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.cnn_r.alliesandfoes.territory.TerritoryClaim;
import net.cnn_r.alliesandfoes.territory.TerritoryManager;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.cnn_r.alliesandfoes.warrior.RallyService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WarHornItem extends Item {

    private static final int COOLDOWN_TICKS = 100; // 5 seconds
    private static final int BORDER_SCAN_RADIUS = 3;

    public WarHornItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;

        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
            handleServerUse(serverLevel, sp, stack);
        }
        return InteractionResult.SUCCESS;
    }

    private void handleServerUse(ServerLevel level, ServerPlayer player, ItemStack stack) {
        MinecraftServer server = level.getServer();
        RoleSlotService roles = RoleSlotService.get(server);

        if (!roles.isRoleActive(player.getUUID(), RoleType.WARRIOR)) {
            player.sendSystemMessage(
                    Component.literal("Only an active Warrior can blow the War Horn.").withStyle(ChatFormatting.RED), true);
            return;
        }

        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());
        if (alliance == null) {
            player.sendSystemMessage(
                    Component.literal("You must be in an alliance to use the War Horn.").withStyle(ChatFormatting.RED), true);
            return;
        }

        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);

        UUID enemyAllianceId = findNearbyEnemyAlliance(level, player, alliance.getId());
        if (enemyAllianceId != null) {
            triggerDeclaration(level, player, alliance, enemyAllianceId);
        } else {
            triggerRally(level, player, alliance, roles);
        }
    }

    private UUID findNearbyEnemyAlliance(ServerLevel level, ServerPlayer player, UUID ownAllianceId) {
        int cx = player.blockPosition().getX() >> 4;
        int cz = player.blockPosition().getZ() >> 4;
        String dimId = level.dimension().identifier().toString();
        TerritoryManager tm = TerritoryManager.get(level.getServer());

        for (int dx = -BORDER_SCAN_RADIUS; dx <= BORDER_SCAN_RADIUS; dx++) {
            for (int dz = -BORDER_SCAN_RADIUS; dz <= BORDER_SCAN_RADIUS; dz++) {
                TerritoryClaim claim = tm.getClaimAt(new ChunkKey(dimId, cx + dx, cz + dz));
                if (claim != null && !claim.getAllianceId().equals(ownAllianceId)) {
                    return claim.getAllianceId();
                }
            }
        }
        return null;
    }

    private void triggerRally(ServerLevel level, ServerPlayer player, Alliance alliance, RoleSlotService roles) {
        int cx = player.blockPosition().getX() >> 4;
        int cz = player.blockPosition().getZ() >> 4;
        String dimId = level.dimension().identifier().toString();
        String name = player.getName().getString();
        MinecraftServer server = level.getServer();

        // Register marker and broadcast to alliance members
        long expiry = server.overworld().getGameTime() + RallyService.RALLY_DURATION_TICKS;
        RallyService.get(server).addRally(alliance.getId(),
                new RallyService.RallyMarker(name, cx, cz, dimId, expiry));

        // Horn sound at player's position
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 3.0f, 1.0f);

        // Toast notification to all online alliance members
        String msg = "§6⚔ " + name + " has called a rally at [" + (cx * 16) + ", " + (cz * 16) + "]!";
        for (UUID memberId : alliance.getMemberUuids()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) ServerPlayNetworking.send(member, new MapScreenMessagePayload(msg));
        }

        // Award rally currency to the Warrior
        roles.addRoleCurrency(player.getUUID(), RoleType.WARRIOR, 5);
        roles.syncPlayer(player);
    }

    private void triggerDeclaration(ServerLevel level, ServerPlayer player, Alliance alliance, UUID enemyAllianceId) {
        int cx = player.blockPosition().getX() >> 4;
        int cz = player.blockPosition().getZ() >> 4;
        String dimId = level.dimension().identifier().toString();
        TerritoryManager tm = TerritoryManager.get(level.getServer());

        // Collect enemy chunks in scan radius
        List<ChunkKey> contested = new ArrayList<>();
        for (int dx = -BORDER_SCAN_RADIUS; dx <= BORDER_SCAN_RADIUS; dx++) {
            for (int dz = -BORDER_SCAN_RADIUS; dz <= BORDER_SCAN_RADIUS; dz++) {
                ChunkKey key = new ChunkKey(dimId, cx + dx, cz + dz);
                TerritoryClaim claim = tm.getClaimAt(key);
                if (claim != null && claim.getAllianceId().equals(enemyAllianceId)) {
                    contested.add(key);
                }
            }
        }
        if (contested.isEmpty()) {
            triggerRally(level, player, alliance, RoleSlotService.get(level.getServer()));
            return;
        }

        String error = AllianceWarService.get(level.getServer()).declareWar(player, enemyAllianceId, contested);
        if (error != null) {
            ServerPlayNetworking.send(player, new MapScreenMessagePayload("§c" + error));
            return;
        }

        // Declaration fanfare — louder than a rally
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 4.0f, 0.8f);
    }
}
