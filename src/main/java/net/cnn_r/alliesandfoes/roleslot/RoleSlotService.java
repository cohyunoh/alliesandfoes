package net.cnn_r.alliesandfoes.roleslot;

import net.cnn_r.alliesandfoes.item.ModComponents;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.network.RoleSlotSyncPayload;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class RoleSlotService {
    private static final Map<MinecraftServer, RoleSlotService> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final Map<UUID, ItemStack[]> slotsByPlayer;

    private RoleSlotService(MinecraftServer server) {
        this.server = server;
        this.slotsByPlayer = RoleSlotSavedData.get(server).createLiveData();
    }

    public static RoleSlotService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, RoleSlotService::new);
    }

    /** True if the player has a role item for the given role equipped in either slot. */
    public boolean isRoleActive(UUID playerUuid, RoleType role) {
        ItemStack[] slots = slotsByPlayer.get(playerUuid);
        if (slots == null) return false;
        Item expected = roleItem(role);
        for (ItemStack s : slots) {
            if (s != null && !s.isEmpty() && s.getItem() == expected) return true;
        }
        return false;
    }

    /** Returns the upgrade level of the role item if equipped, or 0. */
    public int getRoleItemLevel(UUID playerUuid, RoleType role) {
        ItemStack[] slots = slotsByPlayer.get(playerUuid);
        if (slots == null) return 0;
        Item expected = roleItem(role);
        for (ItemStack s : slots) {
            if (s != null && !s.isEmpty() && s.getItem() == expected) {
                return s.getOrDefault(ModComponents.ROLE_UPGRADE_LEVEL, 0);
            }
        }
        return 0;
    }

    /**
     * Adds currency to the role item in the player's slot.
     * Does nothing if the item is not equipped.
     */
    public void addRoleCurrency(UUID playerUuid, RoleType role, int amount) {
        if (amount <= 0) return;
        ItemStack[] slots = slotsByPlayer.computeIfAbsent(playerUuid, k -> emptySlots());
        Item expected = roleItem(role);
        for (ItemStack s : slots) {
            if (s != null && !s.isEmpty() && s.getItem() == expected) {
                int current = s.getOrDefault(ModComponents.ROLE_CURRENCY, 0);
                s.set(ModComponents.ROLE_CURRENCY, current + amount);
                save();
                return;
            }
        }
    }

    /**
     * Consumes up to {@code amount} currency from the role item.
     * Returns how many were actually consumed (0 if item not equipped or no currency).
     */
    public int consumeRoleCurrency(UUID playerUuid, RoleType role, int amount) {
        if (amount <= 0) return 0;
        ItemStack[] slots = slotsByPlayer.computeIfAbsent(playerUuid, k -> emptySlots());
        Item expected = roleItem(role);
        for (ItemStack s : slots) {
            if (s != null && !s.isEmpty() && s.getItem() == expected) {
                int current = s.getOrDefault(ModComponents.ROLE_CURRENCY, 0);
                int consumed = Math.min(current, amount);
                if (consumed > 0) {
                    s.set(ModComponents.ROLE_CURRENCY, current - consumed);
                    save();
                }
                return consumed;
            }
        }
        return 0;
    }

    /** Sets the upgrade level on the equipped role item. Does nothing if not equipped. */
    public void setRoleItemLevel(UUID playerUuid, RoleType role, int level) {
        ItemStack[] slots = slotsByPlayer.computeIfAbsent(playerUuid, k -> emptySlots());
        Item expected = roleItem(role);
        for (ItemStack s : slots) {
            if (s != null && !s.isEmpty() && s.getItem() == expected) {
                s.set(ModComponents.ROLE_UPGRADE_LEVEL, level);
                save();
                return;
            }
        }
    }

    /** Returns how much currency is stored in the equipped role item, or 0. */
    public int getRoleCurrency(UUID playerUuid, RoleType role) {
        ItemStack[] slots = slotsByPlayer.get(playerUuid);
        if (slots == null) return 0;
        Item expected = roleItem(role);
        for (ItemStack s : slots) {
            if (s != null && !s.isEmpty() && s.getItem() == expected) {
                return s.getOrDefault(ModComponents.ROLE_CURRENCY, 0);
            }
        }
        return 0;
    }

    /**
     * Called on player join: populates slot 46 of the InventoryMenu from saved data.
     * Subsequent changes are driven by the Slot.set() override in InventoryMenuMixin.
     */
    public void initPlayerMenu(ServerPlayer player) {
        if (!(player.containerMenu instanceof HasRoleSlot hrs)) return;
        UUID uuid = player.getUUID();
        ItemStack[] playerSlots = slotsByPlayer.computeIfAbsent(uuid, k -> emptySlots());
        hrs.alliesandfoes_getRoleContainer().setItem(0, playerSlots[0].copy());
    }

    /** Called by InventoryMenuMixin.Slot.set() whenever the role slot content changes server-side. */
    public void onRoleSlotChanged(ServerPlayer player, ItemStack newItem) {
        UUID uuid = player.getUUID();
        ItemStack[] playerSlots = slotsByPlayer.computeIfAbsent(uuid, k -> emptySlots());
        playerSlots[0] = newItem.copy();
        save();
        syncPlayer(player);
    }

    public void syncPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        ItemStack[] slots = slotsByPlayer.computeIfAbsent(uuid, k -> emptySlots());
        ItemStack s0 = slots[0];
        ServerPlayNetworking.send(player, new RoleSlotSyncPayload(
                RoleSlotSavedData.idFromStack(s0),
                RoleSlotSavedData.currencyFromStack(s0),
                RoleSlotSavedData.levelFromStack(s0)
        ));
    }

    public void onPlayerDisconnect(UUID uuid) {
        // no session state to clear
    }

    private void save() {
        RoleSlotSavedData.get(server).saveFromLiveData(slotsByPlayer);
    }

    private static ItemStack[] emptySlots() {
        ItemStack[] slots = new ItemStack[RoleSlotSavedData.SLOT_COUNT];
        Arrays.fill(slots, ItemStack.EMPTY);
        return slots;
    }

    private static Item roleItem(RoleType role) {
        return switch (role) {
            case EXPLORER   -> ModItems.CARTOGRAPHERS_JOURNAL;
            case WARRIOR    -> ModItems.WAR_HORN;
            case CULTIVATOR -> ModItems.FARMERS_ALMANAC;
            case PROSPECTOR -> ModItems.DOWSING_ROD;
        };
    }

    private static boolean isRoleItem(Item item) {
        return item == ModItems.CARTOGRAPHERS_JOURNAL || item == ModItems.WAR_HORN
                || item == ModItems.FARMERS_ALMANAC || item == ModItems.DOWSING_ROD;
    }
}
