package net.cnn_r.alliesandfoes.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;

public class MenuScreen extends ScreenBase {

    public MenuScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        Button createTeamsWidget = Button.builder(Component.literal("Create Teams"), (btn) -> {
            // When the button is clicked, we can display a toast to the screen.
            this.minecraft.getToastManager().addToast(
                    SystemToast.multiline(this.minecraft, SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.nullToEmpty("Allies and Foes"), Component.nullToEmpty("Creating Teams"))
            );
        }).bounds(this.width/8, this.height/7, 100, 20).build();

        Button teamsWidget = Button.builder(Component.literal("View Teams"), (btn) -> {
            // When the button is clicked, we can display a toast to the screen.
            this.minecraft.getToastManager().addToast(
                    SystemToast.multiline(this.minecraft, SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.nullToEmpty("Allies and Foes"), Component.nullToEmpty("Viewing Teams"))
            );
        }).bounds(this.width/8, createTeamsWidget.getY()+30, 100, 20).build();

        if (this.minecraft != null && this.minecraft.player != null) {
           createTeamsWidget.active = this.minecraft.player.permissions().hasPermission((Permissions.COMMANDS_ADMIN));
        } else {
            createTeamsWidget.active = false; // Disable if player is null (safety)
        }

        // Register the button widget.
        this.addRenderableWidget(createTeamsWidget);
        this.addRenderableWidget(teamsWidget);
    }
}
