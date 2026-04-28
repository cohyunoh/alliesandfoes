package net.cnn_r.alliesandfoes.roleslot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.cnn_r.alliesandfoes.item.ModComponents;
import net.cnn_r.alliesandfoes.item.ModItems;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public class RoleSlotSavedData extends SavedData {
    static final int SLOT_COUNT = 1;

    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:role_slot_data");

    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<StoredPlayerSlots> STORED_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    UUID_CODEC.fieldOf("player_uuid").forGetter(StoredPlayerSlots::playerUuid),
                    Codec.STRING.optionalFieldOf("slot0", "").forGetter(StoredPlayerSlots::slot0Id),
                    Codec.INT.optionalFieldOf("slot0_currency", 0).forGetter(StoredPlayerSlots::slot0Currency),
                    Codec.INT.optionalFieldOf("slot0_level", 0).forGetter(StoredPlayerSlots::slot0Level)
            ).apply(instance, StoredPlayerSlots::new)
    );

    private static final Codec<RoleSlotSavedData> CODEC = STORED_CODEC.listOf().xmap(
            RoleSlotSavedData::new,
            RoleSlotSavedData::getStoredEntries
    );

    private static final SavedDataType<RoleSlotSavedData> TYPE = new SavedDataType<>(
            DATA_NAME,
            RoleSlotSavedData::new,
            CODEC,
            null
    );

    private List<StoredPlayerSlots> storedEntries;

    public RoleSlotSavedData() {
        this.storedEntries = new ArrayList<>();
    }

    private RoleSlotSavedData(List<StoredPlayerSlots> entries) {
        this.storedEntries = new ArrayList<>(entries);
    }

    public static RoleSlotSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<StoredPlayerSlots> getStoredEntries() {
        return new ArrayList<>(storedEntries);
    }

    public Map<UUID, ItemStack[]> createLiveData() {
        Map<UUID, ItemStack[]> result = new LinkedHashMap<>();
        for (StoredPlayerSlots entry : storedEntries) {
            ItemStack[] slots = new ItemStack[SLOT_COUNT];
            slots[0] = buildStack(entry.slot0Id(), entry.slot0Currency(), entry.slot0Level());
            result.put(entry.playerUuid(), slots);
        }
        return result;
    }

    public void saveFromLiveData(Map<UUID, ItemStack[]> data) {
        List<StoredPlayerSlots> snapshot = new ArrayList<>();
        for (Map.Entry<UUID, ItemStack[]> entry : data.entrySet()) {
            if (entry.getKey() == null) continue;
            ItemStack[] slots = entry.getValue();
            ItemStack s0 = slots.length > 0 && slots[0] != null ? slots[0] : ItemStack.EMPTY;
            if (s0.isEmpty()) continue;
            snapshot.add(new StoredPlayerSlots(
                    entry.getKey(),
                    idFromStack(s0), currencyFromStack(s0), levelFromStack(s0)
            ));
        }
        this.storedEntries = snapshot;
        this.setDirty();
    }

    public static ItemStack buildStack(String id, int currency, int level) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        Item item = switch (id) {
            case "alliesandfoes:cartographers_journal" -> ModItems.CARTOGRAPHERS_JOURNAL;
            case "alliesandfoes:war_horn"              -> ModItems.WAR_HORN;
            case "alliesandfoes:farmers_almanac"       -> ModItems.FARMERS_ALMANAC;
            case "alliesandfoes:dowsing_rod"           -> ModItems.DOWSING_ROD;
            default -> null;
        };
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        if (currency > 0) stack.set(ModComponents.ROLE_CURRENCY, currency);
        if (level > 0) stack.set(ModComponents.ROLE_UPGRADE_LEVEL, level);
        return stack;
    }

    public static String idFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        if (stack.getItem() == ModItems.CARTOGRAPHERS_JOURNAL) return "alliesandfoes:cartographers_journal";
        if (stack.getItem() == ModItems.WAR_HORN)              return "alliesandfoes:war_horn";
        if (stack.getItem() == ModItems.FARMERS_ALMANAC)       return "alliesandfoes:farmers_almanac";
        if (stack.getItem() == ModItems.DOWSING_ROD)           return "alliesandfoes:dowsing_rod";
        return "";
    }

    public static int currencyFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return stack.getOrDefault(ModComponents.ROLE_CURRENCY, 0);
    }

    public static int levelFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return stack.getOrDefault(ModComponents.ROLE_UPGRADE_LEVEL, 0);
    }

    public record StoredPlayerSlots(
            UUID playerUuid,
            String slot0Id, int slot0Currency, int slot0Level
    ) {}
}
