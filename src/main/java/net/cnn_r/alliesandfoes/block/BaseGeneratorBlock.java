package net.cnn_r.alliesandfoes.block;

import net.cnn_r.alliesandfoes.battle.BattleManager;
import net.cnn_r.alliesandfoes.network.ShopOpenPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.UUID;

public class BaseGeneratorBlock extends Block {

    public BaseGeneratorBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof net.minecraft.server.level.ServerLevel sl && player instanceof ServerPlayer sp) {
            UUID battleId = BattleManager.get(sl.getServer()).getBattleForPlayer(sp.getUUID());
            if (battleId != null) {
                ServerPlayNetworking.send(sp, new ShopOpenPayload(battleId));
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }
}
