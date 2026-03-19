package net.cnn_r.alliesandfoes.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractRecipeBookScreen<InventoryMenu> {
	@Unique
    private Button createTeamsWidget;
	@Unique
    private Button teamsWidget;

	RecipeBookComponent<?> recipeBook = ((AbstractRecipeBookScreenAccessor)(Object)this).getRecipeBookComponent();

	public InventoryScreenMixin(InventoryMenu recipeBookMenu, RecipeBookComponent<?> recipeBookComponent, Inventory inventory, Component component) {
		super(recipeBookMenu, recipeBookComponent, inventory, component);
	}

	@Inject(at = @At("RETURN"), method = "init")
	private void addCustomButtons(CallbackInfo info) {
		createTeamsWidget = Button.builder(Component.literal("Create Teams"), (btn) -> {
			// When the button is clicked, we can display a toast to the screen.
			this.minecraft.getToastManager().addToast(
					SystemToast.multiline(this.minecraft, SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.nullToEmpty("Allies and Foes"), Component.nullToEmpty("Creating Teams"))
			);
		}).bounds((this.width - this.imageWidth) / 2 - 110, (this.height - this.imageHeight) / 2, 100, 20).build();

		teamsWidget = Button.builder(Component.literal("View Teams"), (btn) -> {
			// When the button is clicked, we can display a toast to the screen.
			this.minecraft.getToastManager().addToast(
					SystemToast.multiline(this.minecraft, SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.nullToEmpty("Allies and Foes"), Component.nullToEmpty("Viewing Teams"))
			);
		}).bounds((this.width - this.imageWidth) / 2 - 110, createTeamsWidget.getY()+30, 100, 20).build();

		if (this.minecraft.player != null) {
			createTeamsWidget.active = this.minecraft.player.permissions().hasPermission((Permissions.COMMANDS_ADMIN));
		} else {
			createTeamsWidget.active = false; // Disable if player is null (safety)
		}
		// Register the button widget.
		this.addRenderableWidget(createTeamsWidget);
		this.addRenderableWidget(teamsWidget);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void updateAndRender(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {

		if (createTeamsWidget != null && teamsWidget != null) {

			int guiLeft = (this.width - this.imageWidth) / 2;
			int guiTop = (this.height - this.imageHeight) / 2;

			int x;

			if (this.recipeBook.isVisible()) {
				x = guiLeft - 110 - 98;
			} else {
				x = guiLeft - 110;
			}

			createTeamsWidget.setPosition(x, guiTop);
			teamsWidget.setPosition(x, guiTop + 30);

			createTeamsWidget.render(graphics, mouseX, mouseY, delta);
			teamsWidget.render(graphics, mouseX, mouseY, delta);
		}
	}
}