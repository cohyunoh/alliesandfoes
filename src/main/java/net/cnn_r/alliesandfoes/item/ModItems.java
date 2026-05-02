package net.cnn_r.alliesandfoes.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {

    public static final Item IRON_TOKEN = new Item(
            new Item.Properties()
                    .stacksTo(64)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "iron_token")))
    );

    public static final Item GOLD_TOKEN = new Item(
            new Item.Properties()
                    .stacksTo(64)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "gold_token")))
    );

    public static final Item DIAMOND_TOKEN = new Item(
            new Item.Properties()
                    .stacksTo(64)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "diamond_token")))
    );

    public static final Item EMERALD_TOKEN = new Item(
            new Item.Properties()
                    .stacksTo(64)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "emerald_token")))
    );

    public static final Item CHUNK_FRAGMENT = new Item(
            new Item.Properties()
                    .stacksTo(16)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "chunk_fragment")))
    );

    public static final Item CHUNK_FRAGMENT_SHARD = new Item(
            new Item.Properties()
                    .stacksTo(16)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "chunk_fragment_shard")))
    );

    public static final DowsingRodItem DOWSING_ROD = new DowsingRodItem(
            new Item.Properties()
                    .stacksTo(1)
                    .durability(64)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "dowsing_rod")))
    );

    public static final TroughItem TROUGH = new TroughItem(
            new Item.Properties()
                    .stacksTo(1)
                    .durability(32)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "trough")))
    );

    public static final BlockItem BASE_GENERATOR_ITEM = new BlockItem(
            ModBlocks.BASE_GENERATOR,
            new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "base_generator")))
    );

    public static final BlockItem RESOURCE_GENERATOR_ITEM = new BlockItem(
            ModBlocks.RESOURCE_GENERATOR,
            new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "resource_generator")))
    );

    public static final BlockItem GENERATOR_PEDESTAL_ITEM = new BlockItem(
            ModBlocks.GENERATOR_PEDESTAL,
            new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "generator_pedestal")))
    );

    private ModItems() {}

    public static void register() {
        register("iron_token", IRON_TOKEN);
        register("gold_token", GOLD_TOKEN);
        register("diamond_token", DIAMOND_TOKEN);
        register("emerald_token", EMERALD_TOKEN);
        register("chunk_fragment", CHUNK_FRAGMENT);
        register("chunk_fragment_shard", CHUNK_FRAGMENT_SHARD);
        register("dowsing_rod", DOWSING_ROD);
        register("trough", TROUGH);
        register("base_generator", BASE_GENERATOR_ITEM);
        register("resource_generator", RESOURCE_GENERATOR_ITEM);
        register("generator_pedestal", GENERATOR_PEDESTAL_ITEM);
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
