package net.cnn_r.alliesandfoes.roleslot;

import net.cnn_r.alliesandfoes.item.ModComponents;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

public class RoleSlotClientState {
    private static final ItemStack[] slots = new ItemStack[RoleSlotSavedData.SLOT_COUNT];

    static {
        Arrays.fill(slots, ItemStack.EMPTY);
    }

    public static void setFromSync(String s0Id, int s0Currency, int s0Level) {
        slots[0] = RoleSlotSavedData.buildStack(s0Id, s0Currency, s0Level);
    }

    public static ItemStack getSlot(int index) {
        if (index < 0 || index >= slots.length) return ItemStack.EMPTY;
        return slots[index] != null ? slots[index] : ItemStack.EMPTY;
    }

    public static int getSlotCurrency(int index) {
        return getSlot(index).getOrDefault(ModComponents.ROLE_CURRENCY, 0);
    }

    public static int getSlotLevel(int index) {
        return getSlot(index).getOrDefault(ModComponents.ROLE_UPGRADE_LEVEL, 0);
    }

    public static boolean isSlotOccupied(int index) {
        return !getSlot(index).isEmpty();
    }

    public static boolean hasRoleItem(RoleType role) {
        Item expected = switch (role) {
            case EXPLORER   -> ModItems.CARTOGRAPHERS_JOURNAL;
            case WARRIOR    -> ModItems.WAR_HORN;
            case CULTIVATOR -> ModItems.FARMERS_ALMANAC;
            case PROSPECTOR -> ModItems.DOWSING_ROD;
        };
        for (ItemStack s : slots) {
            if (s != null && !s.isEmpty() && s.getItem() == expected) return true;
        }
        return false;
    }

    /** Returns the slot index holding the given role item, or -1 if not equipped. */
    public static int slotIndexForRole(RoleType role) {
        Item expected = switch (role) {
            case EXPLORER   -> ModItems.CARTOGRAPHERS_JOURNAL;
            case WARRIOR    -> ModItems.WAR_HORN;
            case CULTIVATOR -> ModItems.FARMERS_ALMANAC;
            case PROSPECTOR -> ModItems.DOWSING_ROD;
        };
        for (int i = 0; i < slots.length; i++) {
            ItemStack s = slots[i];
            if (s != null && !s.isEmpty() && s.getItem() == expected) return i;
        }
        return -1;
    }

    public static void reset() {
        Arrays.fill(slots, ItemStack.EMPTY);
    }
}
