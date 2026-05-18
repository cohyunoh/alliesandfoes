package net.cnn_r.alliesandfoes.block.territoryanchor;

import net.cnn_r.alliesandfoes.item.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class TerritoryAnchorScreenHandler extends AbstractContainerMenu {

    public static final int BANNER_SLOT_X = 216;
    public static final int BANNER_SLOT_Y = 162;

    // Player inventory layout (for 282px wide screen)
    public static final int INV_START_X = 8;
    public static final int INV_ROW1_Y = 140;
    static final int HOTBAR_Y   = 198;

    public final BlockPos anchorPos;
    private final SimpleContainer bannerContainer;
    private final TerritoryAnchorBlockEntity blockEntity;

    /** Server-side constructor (real block entity). */
    public TerritoryAnchorScreenHandler(int syncId, Inventory playerInventory,
                                        BlockPos anchorPos, ItemStack currentBanner,
                                        TerritoryAnchorBlockEntity blockEntity) {
        super(ModBlocks.TERRITORY_ANCHOR_MENU_TYPE, syncId);
        this.anchorPos = anchorPos;
        this.blockEntity = blockEntity;
        this.bannerContainer = new SimpleContainer(1);
        this.bannerContainer.setItem(0, currentBanner.copy());
        addBannerSlot();
        addPlayerInventory(playerInventory);
    }

    /** Client-side constructor (received via ExtendedMenuType, no block entity). */
    public TerritoryAnchorScreenHandler(int syncId, Inventory playerInventory, BlockPos anchorPos) {
        super(ModBlocks.TERRITORY_ANCHOR_MENU_TYPE, syncId);
        this.anchorPos = anchorPos;
        this.blockEntity = null;
        this.bannerContainer = new SimpleContainer(1);
        addBannerSlot();
        addPlayerInventory(playerInventory);
    }

    private void addBannerSlot() {
        this.addSlot(new Slot(this.bannerContainer, 0, BANNER_SLOT_X, BANNER_SLOT_Y) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (blockEntity != null) {
                    blockEntity.setBannerStack(bannerContainer.getItem(0));
                }
            }
        });
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory,
                        col + row * 9 + 9,
                        INV_START_X + col * 18,
                        INV_ROW1_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, INV_START_X + col * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null) return true;
        return blockEntity.getLevel() != null
                && player.distanceToSqr(anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (slotIndex == 0) {
            if (!this.moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY;
        } else {
            if (!this.moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        return copy;
    }
}
