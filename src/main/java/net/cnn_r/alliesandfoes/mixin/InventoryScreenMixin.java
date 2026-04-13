package net.cnn_r.alliesandfoes.mixin;

import net.cnn_r.alliesandfoes.map.JournalIconButton;
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

        // Map button — centered below the inventory panel
        this.addRenderableWidget(Button.builder(
                Component.literal("View Map"),
                btn -> Minecraft.getInstance().setScreen(new MapScreen()))
                .bounds((this.width / 2) - 48, btnY, 96, 20)
                .build());

        // Journal icon button — top-right corner of the inventory GUI panel
        int journalX = this.leftPos + this.imageWidth - 22;
        int journalY = this.topPos + 2;
        this.addRenderableWidget(new JournalIconButton(journalX, journalY));
	}
}