package net.cnn_r.alliesandfoes.roleslot;

import net.cnn_r.alliesandfoes.upgrade.RoleType;

public class RoleCurrencyClientState {
    private static int[] balances = new int[RoleType.COUNT];

    public static int get(RoleType role) {
        return balances[role.getSlotIndex()];
    }

    public static void set(int[] values) {
        balances = values.clone();
    }

    public static void reset() {
        balances = new int[RoleType.COUNT];
    }
}
