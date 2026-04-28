package net.cnn_r.alliesandfoes.item;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.server.level.ServerLevel;
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
        if (!(level instanceof ServerLevel)) {
            openAlmanacScreen();
        }
        return InteractionResult.SUCCESS;
    }

    @Environment(EnvType.CLIENT)
    private void openAlmanacScreen() {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new net.cnn_r.alliesandfoes.map.AlmanacScreen());
    }
}
