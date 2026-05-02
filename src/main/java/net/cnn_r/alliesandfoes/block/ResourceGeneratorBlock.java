package net.cnn_r.alliesandfoes.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

// 0=iron, 1=gold, 2=diamond, 3=emerald — token type spawned by BattleManager.tick
public class ResourceGeneratorBlock extends Block {
    public static final IntegerProperty TOKEN_TYPE = IntegerProperty.create("token_type", 0, 3);

    public ResourceGeneratorBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(this.stateDefinition.any().setValue(TOKEN_TYPE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOKEN_TYPE);
    }
}
