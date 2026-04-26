package net.cnn_r.alliesandfoes.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuInvoker {
    @Invoker("addSlot")
    <T extends Slot> T invokeAddSlot(T slot);

    @Invoker("moveItemStackTo")
    boolean invokeMoveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverse);
}
