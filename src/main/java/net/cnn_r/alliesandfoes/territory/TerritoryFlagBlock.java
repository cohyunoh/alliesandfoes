package net.cnn_r.alliesandfoes.territory;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.alliance.war.AllianceWarService;
import net.cnn_r.alliesandfoes.network.FlagScreenDataPayload;
import net.cnn_r.alliesandfoes.network.TerritoryChunkBatchPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.UUID;

/**
 * Placeable territory flag block. Placing this block founds a territory anchor for the
 * placing player's alliance. Right-clicking an existing flag opens the flag management screen.
 * Breaking the flag removes the anchor and all associated claims.
 */
public class TerritoryFlagBlock extends Block {

    public TerritoryFlagBlock(Properties properties) {
        super(properties);
    }

    /** Called by the game engine after a player successfully places this block. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(placer instanceof Player player)) return;
        onPlacedByPlayer(level, pos, state, player, stack);
    }

    private void onPlacedByPlayer(Level level, BlockPos pos, BlockState state, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!(player instanceof ServerPlayer sp)) return;

        MinecraftServer server = serverLevel.getServer();
        Alliance alliance = AllianceManager.get(server).getAllianceFor(sp.getUUID());
        if (alliance == null) {
            sp.sendSystemMessage(Component.literal("You must be in an alliance to place a territory flag."));
            level.removeBlock(pos, false);
            player.addItem(new ItemStack(this.asItem()));
            return;
        }

        TerritoryManager tm = TerritoryManager.get(server);
        ChunkKey chunkKey = ChunkKey.of(level, new net.minecraft.world.level.ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));

        TerritoryManager.ActionResult result = tm.foundAnchor(
                sp.getUUID(),
                alliance.getName() + "'s Territory",
                chunkKey,
                AnchorTier.BASIC,
                true
        );

        if (!result.success()) {
            sp.sendSystemMessage(Component.literal(result.message()));
            level.removeBlock(pos, false);
            player.addItem(new ItemStack(this.asItem()));
            return;
        }

        // Sync territory to all online players so the map updates immediately
        TerritoryQueryService queryService = new TerritoryQueryService(tm);
        TerritoryChunkBatchPayload batch = TerritoryMapSyncService.buildChunkBatch(queryService, List.of(chunkKey));
        for (ServerPlayer receiver : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(receiver, batch);
        }

        sp.sendSystemMessage(Component.literal(
                "Territory anchor founded! Claim chunks on the map. (M key)"));
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel) {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

            TerritoryManager tm = TerritoryManager.get(serverLevel.getServer());
            ChunkKey chunkKey = ChunkKey.of(level, new net.minecraft.world.level.ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
            TerritoryAnchor anchor = tm.getAnchorAtChunk(chunkKey);

            if (anchor == null) {
                sp.sendSystemMessage(Component.literal("No territory anchor at this position."));
                return InteractionResult.FAIL;
            }

            Alliance alliance = AllianceManager.get(serverLevel.getServer()).getAllianceFor(sp.getUUID());
            if (alliance == null || !alliance.getId().equals(anchor.getAllianceId())) {
                sp.sendSystemMessage(Component.literal("This territory belongs to another alliance."));
                return InteractionResult.FAIL;
            }

            int usedValue = tm.getTotalClaimValueForAnchor(anchor.getAnchorId());
            int maxValue = anchor.getTier().getMaxClaimValue();
            AnchorTier nextTier = anchor.getTier().next();

            int upgradeInfluenceCost = 0;
            String upgradeMaterialName = "";
            int upgradeMaterialCount = 0;
            if (nextTier != null) {
                upgradeInfluenceCost = switch (nextTier) {
                    case STANDARD -> 150;
                    case ADVANCED -> 300;
                    case ELITE    -> 600;
                    default       -> 0;
                };
                upgradeMaterialCount = 4;
                upgradeMaterialName = switch (nextTier) {
                    case STANDARD -> "minecraft:iron_ingot";
                    case ADVANCED -> "minecraft:gold_ingot";
                    case ELITE    -> "minecraft:diamond";
                    default       -> "";
                };
            }

            BeaconService beaconService = BeaconService.get(serverLevel.getServer());
            Alliance flagAlliance = AllianceManager.get(serverLevel.getServer()).getAllianceFor(sp.getUUID());
            boolean beaconActive = flagAlliance != null && beaconService.isBeaconActive(flagAlliance.getId());
            int beaconCount = beaconActive ? 1 : 0;
            boolean beaconHere = beaconActive;

            ServerPlayNetworking.send(sp, new FlagScreenDataPayload(
                    anchor.getTier().getId(),
                    usedValue,
                    maxValue,
                    anchor.getAnchorId(),
                    pos.getX(), pos.getY(), pos.getZ(),
                    upgradeInfluenceCost,
                    upgradeMaterialName,
                    upgradeMaterialCount,
                    anchor.getName(),
                    beaconCount,
                    BeaconService.MAX_BEACONS,
                    beaconHere
            ));
        } else {
            openFlagScreen();
        }
        return InteractionResult.SUCCESS;
    }

    /** Called server-side before the block is destroyed by a player. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
            TerritoryManager tm = TerritoryManager.get(serverLevel.getServer());
            ChunkKey chunkKey = ChunkKey.of(level, new net.minecraft.world.level.ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
            TerritoryAnchor anchor = tm.getAnchorAtChunk(chunkKey);

            if (anchor != null) {
                Alliance alliance = AllianceManager.get(serverLevel.getServer()).getAllianceFor(sp.getUUID());
                if (alliance != null && alliance.getId().equals(anchor.getAllianceId())) {
                    // Block destruction during active war
                    if (AllianceWarService.get(serverLevel.getServer())
                            .getActiveWarInvolving(alliance.getId()).isPresent()) {
                        sp.sendSystemMessage(Component.literal(
                                "Territory flags cannot be broken during an active war."));
                        return super.playerWillDestroy(level, pos, state, player);
                    }

                    // Collect ALL claimed chunks BEFORE destroying so we can clear them on the client
                    List<ChunkKey> allClaimed = tm.getClaimsForAnchor(anchor.getAnchorId())
                            .stream()
                            .map(TerritoryClaim::getChunkKey)
                            .toList();

                    String error = tm.destroyAnchor(anchor.getAnchorId());
                    if (error == null) {
                        // Sync all formerly-claimed chunks (now empty) to all clients
                        TerritoryQueryService queryService = new TerritoryQueryService(tm);
                        TerritoryChunkBatchPayload batch = TerritoryMapSyncService.buildChunkBatch(
                                queryService, allClaimed);
                        for (ServerPlayer receiver : serverLevel.getServer().getPlayerList().getPlayers()) {
                            ServerPlayNetworking.send(receiver, batch);
                        }
                        sp.sendSystemMessage(Component.literal("Territory anchor and all claims removed."));
                    }
                } else {
                    sp.sendSystemMessage(Component.literal("You cannot destroy another alliance's flag."));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Environment(EnvType.CLIENT)
    private void openFlagScreen() {
        // Client-side screen will be opened after receiving FlagScreenDataPayload
        // Nothing to do here — server sends the payload which triggers screen open on client.
    }
}
