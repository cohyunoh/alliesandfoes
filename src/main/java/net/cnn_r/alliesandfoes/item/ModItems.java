package net.cnn_r.alliesandfoes.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {

    public static final CartographerJournalItem CARTOGRAPHERS_JOURNAL = new CartographerJournalItem(
            new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "cartographers_journal")))
    );

    public static final WarHornItem WAR_HORN = new WarHornItem(
            new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "war_horn")))
    );

    public static final FarmersAlmanacItem FARMERS_ALMANAC = new FarmersAlmanacItem(
            new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "farmers_almanac")))
    );

    public static final DowsingRodItem DOWSING_ROD = new DowsingRodItem(
            new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "dowsing_rod")))
    );

    public static final Item COVENANT_SHARD = new Item(
            new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "covenant_shard")))
    );

    public static final Item CRUDE_COVENANT_SHARD = new Item(
            new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "crude_covenant_shard")))
    );

    public static final BlockItem TRIBUTE_ALTAR = new BlockItem(
            ModBlocks.TRIBUTE_ALTAR,
            new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "tribute_altar")))
    );

    public static final BlockItem COVENANT_FORGE = new BlockItem(
            ModBlocks.COVENANT_FORGE,
            new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "covenant_forge")))
    );

    public static final BlockItem TERRITORY_FLAG = new BlockItem(
            ModBlocks.TERRITORY_FLAG,
            new Item.Properties()
                    .stacksTo(16)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "territory_flag")))
    );

    private ModItems() {}

    public static void register() {
        register("cartographers_journal", CARTOGRAPHERS_JOURNAL);
        register("war_horn", WAR_HORN);
        register("farmers_almanac", FARMERS_ALMANAC);
        register("dowsing_rod", DOWSING_ROD);
        register("covenant_shard", COVENANT_SHARD);
        register("crude_covenant_shard", CRUDE_COVENANT_SHARD);
        register("tribute_altar", TRIBUTE_ALTAR);
        register("covenant_forge", COVENANT_FORGE);
        register("territory_flag", TERRITORY_FLAG);
    }

    private static <T extends Item> T register(String name, T item) {
        net.minecraft.core.Registry.register(
                BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath("alliesandfoes", name),
                item
        );
        return item;
    }
}
