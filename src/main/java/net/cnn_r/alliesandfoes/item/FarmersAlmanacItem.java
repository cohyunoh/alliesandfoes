package net.cnn_r.alliesandfoes.item;

import net.cnn_r.alliesandfoes.cultivator.CultivatorSkillService;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class FarmersAlmanacItem extends Item {

    public FarmersAlmanacItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer sp) {
            if (player.isShiftKeyDown()) {
                CultivatorSkillService.get(level.getServer()).tryHarvestRitual(sp);
                return InteractionResult.SUCCESS;
            }
        } else if (!(level instanceof ServerLevel)) {
            if (!player.isShiftKeyDown()) {
                openAlmanacScreen();
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Environment(EnvType.CLIENT)
    private void openAlmanacScreen() {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new net.cnn_r.alliesandfoes.map.AlmanacScreen());
    }
}
