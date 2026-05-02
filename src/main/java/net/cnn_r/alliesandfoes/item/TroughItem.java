package net.cnn_r.alliesandfoes.item;

import net.cnn_r.alliesandfoes.protect.BlockOwnerService;
import net.cnn_r.alliesandfoes.territory.ChunkFragmentSavedData;
import net.cnn_r.alliesandfoes.territory.ChunkFragmentService;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

public class TroughItem extends Item {

    public TroughItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide()) return InteractionResult.PASS;
        ServerLevel level = (ServerLevel) ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();

        if (!(ctx.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;

        ChunkKey chunk = ChunkKey.of(level, new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
        List<BlockPos> fragments = ChunkFragmentService.getFragmentPositions(level, chunk);
        String posKey = BlockOwnerService.toKey(level, pos);

        int matchIdx = -1;
        for (int i = 0; i < fragments.size(); i++) {
            if (fragments.get(i).equals(pos)) {
                matchIdx = i;
                break;
            }
        }

        if (matchIdx == -1 || ChunkFragmentSavedData.get(level.getServer()).isExcavated(posKey)) {
            level.playSound(null, pos, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 0.5f, 1.2f);
            return InteractionResult.SUCCESS;
        }

        ChunkFragmentSavedData.get(level.getServer()).markExcavated(posKey);
        boolean isWhole = ChunkFragmentService.isWhole(level, chunk, matchIdx);
        ItemStack drop = new ItemStack(isWhole ? ModItems.CHUNK_FRAGMENT : ModItems.CHUNK_FRAGMENT_SHARD);
        player.getInventory().add(drop);
        level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1f, 0.8f);
        ctx.getItemInHand().hurtAndBreak(1, player, ctx.getHand());
        return InteractionResult.SUCCESS;
    }
}
