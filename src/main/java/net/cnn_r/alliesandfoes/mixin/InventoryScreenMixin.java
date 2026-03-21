package net.cnn_r.alliesandfoes.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
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
    private Button teamsWidget;

	public InventoryScreenMixin(InventoryMenu recipeBookMenu, RecipeBookComponent<?> recipeBookComponent, Inventory inventory, Component component) {
		super(recipeBookMenu, recipeBookComponent, inventory, component);
	}

	@Inject(at = @At("RETURN"), method = "init")
	private void addCustomButtons(CallbackInfo info) {
		teamsWidget = Button.builder(Component.literal("View Teams"), (btn) -> {
			// When the button is clicked, we can display a toast to the screen.
			this.minecraft.getToastManager().addToast(
					SystemToast.multiline(this.minecraft, SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.nullToEmpty("Allies and Foes"), Component.nullToEmpty("Viewing Teams"))
			);
		}).bounds((this.width / 2) - (96/2), this.height-((this.height - this.imageHeight) / 2), 96, 20).build();
		this.addRenderableWidget(teamsWidget);
	}
}