package net.cnn_r.alliesandfoes.mixin;

import net.cnn_r.alliesandfoes.map.MapScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractRecipeBookScreen<InventoryMenu> {

    public InventoryScreenMixin(InventoryMenu recipeBookMenu, RecipeBookComponent<?> recipeBookComponent, Inventory inventory, Component component) {
		super(recipeBookMenu, recipeBookComponent, inventory, component);
	}

	@Inject(at = @At("RETURN"), method = "init")
	private void addCustomButton(CallbackInfo info) {
        int btnY = this.height - ((this.height - this.imageHeight) / 2);

        this.addRenderableWidget(Button.builder(
                Component.literal("View Map"),
                btn -> Minecraft.getInstance().setScreen(new MapScreen()))
                .bounds((this.width / 2) - 48, btnY, 96, 20)
                .build());
	}
}
