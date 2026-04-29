package net.cnn_r.alliesandfoes.item;

import net.cnn_r.alliesandfoes.covenantforge.CovenantForgeBlock;
import net.cnn_r.alliesandfoes.territory.TerritoryFlagBlock;
import net.cnn_r.alliesandfoes.tributealtar.TributeAltarBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {

    public static final TributeAltarBlock TRIBUTE_ALTAR = new TributeAltarBlock(
            BlockBehaviour.Properties.of()
                    .strength(2.5f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "tribute_altar")))
    );

    public static final CovenantForgeBlock COVENANT_FORGE = new CovenantForgeBlock(
            BlockBehaviour.Properties.of()
                    .strength(5.0f, 1200.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "covenant_forge")))
    );

    public static final TerritoryFlagBlock TERRITORY_FLAG = new TerritoryFlagBlock(
            BlockBehaviour.Properties.of()
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.WOOD)
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "territory_flag")))
    );

    public static void register() {
        register("tribute_altar", TRIBUTE_ALTAR);
        register("covenant_forge", COVENANT_FORGE);
        register("territory_flag", TERRITORY_FLAG);
    }

    private static <T extends Block> T register(String name, T block) {
        Registry.register(BuiltInRegistries.BLOCK,
                Identifier.fromNamespaceAndPath("alliesandfoes", name), block);
        return block;
    }

    private ModBlocks() {}
}
