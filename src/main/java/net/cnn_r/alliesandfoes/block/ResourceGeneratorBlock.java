package net.cnn_r.alliesandfoes.block;

import net.cnn_r.alliesandfoes.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class ResourceGeneratorBlock extends Block {
    public static final IntegerProperty TOKEN_TYPE = IntegerProperty.create("token_type", 0, 2);

    public ResourceGeneratorBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(this.stateDefinition.any().setValue(TOKEN_TYPE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOKEN_TYPE);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int tokenType = state.getValue(TOKEN_TYPE);
        ItemStack token = switch (tokenType) {
            case 1 -> new ItemStack(ModItems.IRON_TOKEN);
            case 2 -> new ItemStack(ModItems.GOLD_TOKEN);
            default -> new ItemStack(ModItems.COPPER_TOKEN);
        };
        BlockPos spawnPos = pos.above();
        Block.popResource(level, spawnPos, token);
    }
}
