package net.cnn_r.alliesandfoes.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {

    public static final Item COPPER_TOKEN = new Item(
            new Item.Properties()
                    .stacksTo(64)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "copper_token")))
    );

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

    private ModItems() {}

    public static void register() {
        register("copper_token", COPPER_TOKEN);
        register("iron_token", IRON_TOKEN);
        register("gold_token", GOLD_TOKEN);
        register("base_generator", BASE_GENERATOR_ITEM);
        register("resource_generator", RESOURCE_GENERATOR_ITEM);
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
