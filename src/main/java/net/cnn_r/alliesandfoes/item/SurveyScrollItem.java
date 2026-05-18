package net.cnn_r.alliesandfoes.item;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.network.ChunkRevealedPayload;
import net.cnn_r.alliesandfoes.territory.AllianceExploredChunksSavedData;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

public class SurveyScrollItem extends Item {

    public SurveyScrollItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (!level.isClientSide() && user instanceof ServerPlayer player) {
            doSurvey((ServerLevel) level, player);
        }
        stack.shrink(1);
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    private static void doSurvey(ServerLevel level, ServerPlayer player) {
        MinecraftServer server = level.getServer();
        Alliance alliance = AllianceManager.get(server).getAllianceFor(player.getUUID());

        ChunkPos playerChunk = player.chunkPosition();
        ChunkKey key = new ChunkKey(level.dimension().identifier().toString(), playerChunk.x(), playerChunk.z());

        if (alliance == null) {
            // Solo survey: just tell them the chunk key they're in
            player.sendSystemMessage(Component.literal(
                    "Chunk [" + playerChunk.x() + ", " + playerChunk.z() + "] surveyed — join an alliance for map sharing."));
            return;
        }

        UUID allianceId = alliance.getId();
        AllianceExploredChunksSavedData data = AllianceExploredChunksSavedData.get(server);

        if (!data.reveal(allianceId, key)) {
            player.sendSystemMessage(Component.literal("This chunk has already been surveyed."));
            return;
        }

        // Sync the newly revealed chunk to all online alliance members
        List<ChunkKey> newChunks = List.of(key);
        ChunkRevealedPayload payload = new ChunkRevealedPayload(newChunks);

        for (UUID memberUuid : alliance.getMemberUuids()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberUuid);
            if (member != null) {
                ServerPlayNetworking.send(member, payload);
            }
        }

        player.sendSystemMessage(Component.literal("Chunk surveyed — value revealed on alliance map."));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 30;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }
}
