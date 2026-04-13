package net.cnn_r.alliesandfoes.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {

    public static final MonocleItem MONOCLE = new MonocleItem(
            new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "monocle")))
    );

    private ModItems() {
    }

    public static void register() {
        register("monocle", MONOCLE);
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
