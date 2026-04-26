package net.cnn_r.alliesandfoes.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {

    public static final MonocleItem MONOCLE = new MonocleItem(
            new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "monocle")))
    );

    public static final Item WAR_HORN = new Item(
            new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "war_horn")))
    );

    public static final Item FARMERS_ALMANAC = new Item(
            new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "farmers_almanac")))
    );

    public static final Item ASSAY_KIT = new Item(
            new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "assay_kit")))
    );

    public static final Item COVENANT_SHARD = new Item(
            new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "covenant_shard")))
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

    private ModItems() {
    }

    public static void register() {
        register("monocle", MONOCLE);
        register("war_horn", WAR_HORN);
        register("farmers_almanac", FARMERS_ALMANAC);
        register("assay_kit", ASSAY_KIT);
        register("covenant_shard", COVENANT_SHARD);
        register("tribute_altar", TRIBUTE_ALTAR);
        register("covenant_forge", COVENANT_FORGE);
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
