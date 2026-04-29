package net.cnn_r.alliesandfoes.item;

import net.cnn_r.alliesandfoes.prospector.ProspectorSkillService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class DowsingRodItem extends Item {

    public DowsingRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
            ProspectorSkillService.get(serverLevel.getServer()).assessImmediate(sp);
        }
        return InteractionResult.SUCCESS;
    }
}
