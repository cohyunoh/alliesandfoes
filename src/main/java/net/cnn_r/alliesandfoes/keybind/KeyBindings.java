package net.cnn_r.alliesandfoes.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.cnn_r.alliesandfoes.explorer.MonocleScreen;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.map.MapScreen;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotClientState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {

    public static KeyMapping OPEN_MAP;
    public static KeyMapping USE_ROLE_ITEM;

    public static void register() {
        OPEN_MAP = new KeyMapping(
                "key.alliesandfoes.open_map",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                new KeyMapping.Category(Identifier.parse("alliesandfoes:category.alliesandfoes"))
        );

        USE_ROLE_ITEM = new KeyMapping(
                "key.alliesandfoes.use_role_item",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                new KeyMapping.Category(Identifier.parse("alliesandfoes:category.alliesandfoes"))
        );

        KeyMappingHelper.registerKeyMapping(OPEN_MAP);
        KeyMappingHelper.registerKeyMapping(USE_ROLE_ITEM);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MAP.consumeClick()) {
                if (client.player != null && client.level != null) {
                    if (client.screen instanceof MapScreen) {
                        client.setScreen(null);
                    } else if (client.screen == null) {
                        client.setScreen(new MapScreen());
                        client.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                    }
                }
            }

            while (USE_ROLE_ITEM.consumeClick()) {
                if (client.player != null && client.level != null && client.screen == null) {
                    ItemStack role = RoleSlotClientState.getSlot(0);
                    if (role.is(ModItems.CARTOGRAPHERS_JOURNAL)) {
                        client.setScreen(new MonocleScreen());
                    }
                }
            }
        });
    }
}
