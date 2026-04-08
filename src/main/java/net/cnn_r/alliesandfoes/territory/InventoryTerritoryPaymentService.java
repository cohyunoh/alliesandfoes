package net.cnn_r.alliesandfoes.territory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

public class InventoryTerritoryPaymentService implements TerritoryPaymentService {
    public static final Item DEFAULT_PAYMENT_ITEM = Items.EMERALD;

    private final MinecraftServer server;
    private final Item paymentItem;

    public InventoryTerritoryPaymentService(MinecraftServer server) {
        this(server, DEFAULT_PAYMENT_ITEM);
    }

    public InventoryTerritoryPaymentService(MinecraftServer server, Item paymentItem) {
        if (server == null) {
            throw new IllegalArgumentException("server cannot be null");
        }
        if (paymentItem == null) {
            throw new IllegalArgumentException("paymentItem cannot be null");
        }

        this.server = server;
        this.paymentItem = paymentItem;
    }

    @Override
    public PaymentResult canPayFoundingCost(UUID playerUuid, ChunkKey chunkKey, int cost) {
        return this.canPay(playerUuid, cost);
    }

    @Override
    public PaymentResult payFoundingCost(UUID playerUuid, ChunkKey chunkKey, int cost) {
        return this.pay(playerUuid, cost);
    }

    @Override
    public PaymentResult canPayExpansionCost(UUID playerUuid, UUID anchorId, ChunkKey chunkKey, int cost) {
        return this.canPay(playerUuid, cost);
    }

    @Override
    public PaymentResult payExpansionCost(UUID playerUuid, UUID anchorId, ChunkKey chunkKey, int cost) {
        return this.pay(playerUuid, cost);
    }

    public Item getPaymentItem() {
        return this.paymentItem;
    }

    private PaymentResult canPay(UUID playerUuid, int cost) {
        if (playerUuid == null) {
            return PaymentResult.deny("Player UUID cannot be null.");
        }
        if (cost < 0) {
            return PaymentResult.deny("Cost cannot be negative.");
        }
        if (cost == 0) {
            return PaymentResult.allow();
        }

        ServerPlayer player = this.server.getPlayerList().getPlayer(playerUuid);
        if (player == null) {
            return PaymentResult.deny("Player must be online to pay territory costs.");
        }

        int available = this.countPaymentItem(player.getInventory());
        if (available < cost) {
            return PaymentResult.deny(
                    "Not enough payment items. Need " + cost + ", have " + available + "."
            );
        }

        return PaymentResult.allow();
    }

    private PaymentResult pay(UUID playerUuid, int cost) {
        PaymentResult affordability = this.canPay(playerUuid, cost);
        if (!affordability.allowed()) {
            return affordability;
        }
        if (cost == 0) {
            return PaymentResult.allow();
        }

        ServerPlayer player = this.server.getPlayerList().getPlayer(playerUuid);
        if (player == null) {
            return PaymentResult.deny("Player must be online to pay territory costs.");
        }

        Inventory inventory = player.getInventory();
        int remaining = cost;

        for (int slot = 0; slot < inventory.getNonEquipmentItems().size() && remaining > 0; slot++) {
            ItemStack stack = inventory.getNonEquipmentItems().get(slot);
            if (stack.isEmpty() || !stack.is(this.paymentItem)) {
                continue;
            }

            int removeAmount = Math.min(stack.getCount(), remaining);
            int newCount = stack.getCount() - removeAmount;

            stack.setCount(newCount);
            remaining -= removeAmount;
        }

        inventory.setChanged();

        if (remaining > 0) {
            return PaymentResult.deny("Failed to remove the full territory cost from inventory.");
        }

        return PaymentResult.allow();
    }

    private int countPaymentItem(Inventory inventory) {
        int total = 0;

        for (ItemStack stack : inventory.getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.is(this.paymentItem)) {
                total += stack.getCount();
            }
        }

        return total;
    }
}