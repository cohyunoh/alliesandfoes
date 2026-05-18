package net.cnn_r.alliesandfoes.block.territoryanchor;

import net.cnn_r.alliesandfoes.item.ModBlocks;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class TerritoryAnchorBlockEntity extends BlockEntity
        implements ExtendedMenuProvider<BlockPos> {

    private ItemStack bannerStack = ItemStack.EMPTY;

    public TerritoryAnchorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlocks.TERRITORY_ANCHOR_BE_TYPE, pos, blockState);
    }

    public ItemStack getBannerStack() {
        return this.bannerStack;
    }

    public void setBannerStack(ItemStack stack) {
        this.bannerStack = stack.copy();
        this.setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        if (!this.bannerStack.isEmpty()) {
            output.store("banner", ItemStack.CODEC, this.bannerStack);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        this.bannerStack = input.read("banner", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new TerritoryAnchorScreenHandler(syncId, playerInventory, this.getBlockPos(), this.bannerStack, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return this.getBlockPos();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alliesandfoes.territory_anchor");
    }
}
