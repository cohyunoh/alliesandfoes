package net.cnn_r.alliesandfoes.item;

import net.cnn_r.alliesandfoes.block.territoryanchor.TerritoryAnchorBlock;
import net.cnn_r.alliesandfoes.block.territoryanchor.TerritoryAnchorBlockEntity;
import net.cnn_r.alliesandfoes.block.territoryanchor.TerritoryAnchorScreenHandler;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
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

    public static BlockEntityType<TerritoryAnchorBlockEntity> TERRITORY_ANCHOR_BE_TYPE;

    public static ExtendedMenuType<TerritoryAnchorScreenHandler, BlockPos> TERRITORY_ANCHOR_MENU_TYPE;

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

        TERRITORY_ANCHOR_BE_TYPE = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath("alliesandfoes", "territory_anchor"),
                FabricBlockEntityTypeBuilder.create(TerritoryAnchorBlockEntity::new, TERRITORY_ANCHOR).build()
        );

        TERRITORY_ANCHOR_MENU_TYPE = Registry.register(
                BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath("alliesandfoes", "territory_anchor"),
                new ExtendedMenuType<>(
                        (syncId, inv, data) -> new TerritoryAnchorScreenHandler(syncId, inv, data),
                        BlockPos.STREAM_CODEC
                )
        );
    }
}
