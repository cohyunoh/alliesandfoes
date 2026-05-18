package net.cnn_r.alliesandfoes.item;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.territory.TerritoryAnchor;
import net.cnn_r.alliesandfoes.territory.TerritoryManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Set;

public class AllianceRecallStoneItem extends Item {

    public AllianceRecallStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (level.isClientSide() || !(user instanceof ServerPlayer player)) {
            return stack;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        Alliance alliance = AllianceManager.get(serverLevel.getServer()).getAllianceFor(player.getUUID());
        if (alliance == null) {
            player.sendSystemMessage(Component.literal("You are not in an alliance."));
            return stack;
        }

        List<TerritoryAnchor> anchors = TerritoryManager.get(serverLevel.getServer()).getAnchorsForAlliance(alliance.getId());
        if (anchors.isEmpty()) {
            player.sendSystemMessage(Component.literal("Your alliance has no territory anchor to recall to."));
            return stack;
        }

        TerritoryAnchor nearest = findNearestAnchor(anchors, player.chunkPosition());
        if (nearest == null) {
            return stack;
        }

        int blockX = nearest.getOrigin().getChunkX() * 16 + 8;
        int blockZ = nearest.getOrigin().getChunkZ() * 16 + 8;
        int blockY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);

        player.teleportTo(serverLevel, blockX + 0.5, blockY, blockZ + 0.5, Set.of(), player.getYRot(), 0f, true);
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);

        player.getCooldowns().addCooldown(stack, 18000);
        player.sendSystemMessage(Component.literal("Recalled to alliance territory."));
        return stack;
    }

    private static TerritoryAnchor findNearestAnchor(List<TerritoryAnchor> anchors, ChunkPos playerChunk) {
        TerritoryAnchor nearest = null;
        double minDist = Double.MAX_VALUE;
        for (TerritoryAnchor anchor : anchors) {
            double dx = anchor.getOrigin().getChunkX() - playerChunk.x();
            double dz = anchor.getOrigin().getChunkZ() - playerChunk.z();
            double dist = dx * dx + dz * dz;
            if (dist < minDist) {
                minDist = dist;
                nearest = anchor;
            }
        }
        return nearest;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 80;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }
}
