package net.cnn_r.alliesandfoes.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.cnn_r.alliesandfoes.map.MapScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {

    public static KeyMapping OPEN_MAP;

    public static void register() {
        OPEN_MAP = new KeyMapping(
                "key.alliesandfoes.open_map",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                new KeyMapping.Category(Identifier.parse("alliesandfoes:category.alliesandfoes"))
        );

        KeyBindingHelper.registerKeyBinding(OPEN_MAP);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MAP.consumeClick()) {
                if (client.player != null && client.level != null) {
                    if (client.screen instanceof MapScreen) {
                        client.setScreen(null);
                    } else {
                        if (client.screen == null) {
                            client.setScreen(new MapScreen());
                            client.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                        }
                    }
                }
            }
        });
    }
}