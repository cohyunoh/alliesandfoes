package net.cnn_r.alliesandfoes.roleslot;

import net.cnn_r.alliesandfoes.network.RoleCurrencySyncPayload;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class RoleCurrencyService {
    private static final Map<MinecraftServer, RoleCurrencyService> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final Map<UUID, int[]> balances;

    private RoleCurrencyService(MinecraftServer server) {
        this.server = server;
        this.balances = RoleCurrencySavedData.get(server).createLiveData();
    }

    public static RoleCurrencyService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, RoleCurrencyService::new);
    }

    public int get(UUID playerUuid, RoleType role) {
        int[] arr = balances.get(playerUuid);
        if (arr == null) return 0;
        return arr[role.getSlotIndex()];
    }

    public void add(UUID playerUuid, RoleType role, int amount) {
        if (amount <= 0) return;
        int[] arr = balances.computeIfAbsent(playerUuid, k -> new int[RoleType.COUNT]);
        arr[role.getSlotIndex()] += amount;
        save();
    }

    /** Returns true and deducts if the player has sufficient balance; false otherwise. */
    public boolean consume(UUID playerUuid, RoleType role, int amount) {
        if (amount <= 0) return true;
        int[] arr = balances.get(playerUuid);
        if (arr == null) return false;
        int idx = role.getSlotIndex();
        if (arr[idx] < amount) return false;
        arr[idx] -= amount;
        save();
        return true;
    }

    public void sync(ServerPlayer player) {
        int[] arr = balances.computeIfAbsent(player.getUUID(), k -> new int[RoleType.COUNT]);
        ServerPlayNetworking.send(player, new RoleCurrencySyncPayload(arr.clone()));
    }

    private void save() {
        RoleCurrencySavedData.get(server).saveFromLiveData(balances);
    }
}
