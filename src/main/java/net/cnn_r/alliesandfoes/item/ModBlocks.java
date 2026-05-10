package net.cnn_r.alliesandfoes.item;

import net.cnn_r.alliesandfoes.block.TerritoryAnchorBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {

    private static final ResourceKey<Block> TERRITORY_ANCHOR_KEY =
            ResourceKey.create(Registries.BLOCK,
                    Identifier.fromNamespaceAndPath("alliesandfoes", "territory_anchor"));

    public static final TerritoryAnchorBlock TERRITORY_ANCHOR = new TerritoryAnchorBlock(
            BlockBehaviour.Properties.of()
                    .setId(TERRITORY_ANCHOR_KEY)
                    .strength(3.5f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
    );

    private ModBlocks() {}

    public static void register() {
        Registry.register(BuiltInRegistries.BLOCK,
                Identifier.fromNamespaceAndPath("alliesandfoes", "territory_anchor"),
                TERRITORY_ANCHOR);

        Registry.register(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath("alliesandfoes", "territory_anchor"),
                new BlockItem(TERRITORY_ANCHOR,
                        new Item.Properties().setId(ResourceKey.create(Registries.ITEM,
                                Identifier.fromNamespaceAndPath("alliesandfoes", "territory_anchor")))));
    }
}
