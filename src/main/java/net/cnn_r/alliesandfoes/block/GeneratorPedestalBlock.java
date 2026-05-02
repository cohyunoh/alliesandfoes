package net.cnn_r.alliesandfoes.block;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;

public class GeneratorPedestalBlock extends Block {

    public GeneratorPedestalBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendSystemMessage(
                    Component.literal("§6This pedestal marks where your generator will be placed in battle."),
                    true);
        }
        return InteractionResult.SUCCESS;
    }
}
