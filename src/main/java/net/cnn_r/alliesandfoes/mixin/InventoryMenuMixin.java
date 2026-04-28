package net.cnn_r.alliesandfoes.mixin;

import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.roleslot.HasRoleSlot;
import net.cnn_r.alliesandfoes.roleslot.RoleSlotService;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin implements HasRoleSlot {

    @Unique
    private SimpleContainer alliesandfoes_roleContainer;

    @Override
    public SimpleContainer alliesandfoes_getRoleContainer() {
        return alliesandfoes_roleContainer;
    }

    @Unique
    private AbstractContainerMenuInvoker alliesandfoes_menu() {
        return (AbstractContainerMenuInvoker)(Object)this;
    }

    @Unique
    private List<Slot> alliesandfoes_slots() {
        return ((AbstractContainerMenu)(Object)this).slots;
    }

    // Adds slot 46 (after: result=0, craft=1-4, armor=5-8, inv=9-35, hotbar=36-44, offhand=45)
    @Inject(at = @At("TAIL"), method = "<init>(Lnet/minecraft/world/entity/player/Inventory;ZLnet/minecraft/world/entity/player/Player;)V")
    private void alliesandfoes_addRoleSlot(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        this.alliesandfoes_roleContainer = new SimpleContainer(1);
        alliesandfoes_menu().invokeAddSlot(new Slot(alliesandfoes_roleContainer, 0, 77, 37) {
            @Override
            public boolean isActive() {
                return !owner.isCreative();
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.CARTOGRAPHERS_JOURNAL) || stack.is(ModItems.WAR_HORN)
                        || stack.is(ModItems.FARMERS_ALMANAC) || stack.is(ModItems.DOWSING_ROD);
            }

            @Override
            public int getMaxStackSize() { return 1; }

            @Override
            public Identifier getNoItemIcon() {
                return Identifier.parse("minecraft:container/slot/shield");
            }

            // Notify RoleSlotService whenever the slot content changes server-side
            @Override
            public void set(ItemStack stack) {
                super.set(stack);
                if (!owner.level().isClientSide() && owner instanceof ServerPlayer sp) {
                    RoleSlotService.get(owner.level().getServer()).onRoleSlotChanged(sp, stack);
                }
            }
        });
    }

    @Inject(at = @At("HEAD"), method = "quickMoveStack", cancellable = true)
    private void alliesandfoes_quickMoveRoleSlot(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        List<Slot> slots = alliesandfoes_slots();
        if (index == 46) {
            // Shift-click role slot → move to inventory/hotbar
            Slot slot = slots.get(46);
            if (!slot.hasItem()) { cir.setReturnValue(ItemStack.EMPTY); return; }
            ItemStack orig = slot.getItem();
            ItemStack copy = orig.copy();
            if (!alliesandfoes_menu().invokeMoveItemStackTo(orig, 9, 45, false)) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            if (orig.isEmpty()) slot.setByPlayer(ItemStack.EMPTY, copy);
            else slot.setChanged();
            slot.onTake(player, orig);
            cir.setReturnValue(copy);
        } else if (index >= 9 && index < 45) {
            // Shift-click inventory/hotbar → move role items to role slot if empty
            Slot slot = slots.get(index);
            if (!slot.hasItem()) return;
            ItemStack item = slot.getItem();
            boolean isRole = item.is(ModItems.CARTOGRAPHERS_JOURNAL) || item.is(ModItems.WAR_HORN)
                    || item.is(ModItems.FARMERS_ALMANAC) || item.is(ModItems.DOWSING_ROD);
            if (!isRole) return;
            Slot roleSlot = slots.get(46);
            if (roleSlot.hasItem()) return; // role slot occupied — let vanilla move normally
            ItemStack copy = item.copy();
            if (!alliesandfoes_menu().invokeMoveItemStackTo(item, 46, 47, false)) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            if (item.isEmpty()) slot.setByPlayer(ItemStack.EMPTY, copy);
            else slot.setChanged();
            slot.onTake(player, item);
            cir.setReturnValue(copy);
        }
    }
}
