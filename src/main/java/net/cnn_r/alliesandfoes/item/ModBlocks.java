package net.cnn_r.alliesandfoes.item;

import net.cnn_r.alliesandfoes.block.BaseGeneratorBlock;
import net.cnn_r.alliesandfoes.block.ResourceGeneratorBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {

    public static final BaseGeneratorBlock BASE_GENERATOR = new BaseGeneratorBlock(
            BlockBehaviour.Properties.of()
                    .strength(50f, 1200f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "base_generator")))
    );

    public static final ResourceGeneratorBlock RESOURCE_GENERATOR = new ResourceGeneratorBlock(
            BlockBehaviour.Properties.of()
                    .strength(3f, 6f)
                    .sound(SoundType.STONE)
                    .randomTicks()
                    .setId(ResourceKey.create(Registries.BLOCK,
                            Identifier.fromNamespaceAndPath("alliesandfoes", "resource_generator")))
    );

    public static void register() {
        register("base_generator", BASE_GENERATOR);
        register("resource_generator", RESOURCE_GENERATOR);
    }

    private static <T extends Block> T register(String name, T block) {
        Registry.register(BuiltInRegistries.BLOCK,
                Identifier.fromNamespaceAndPath("alliesandfoes", name), block);
        return block;
    }

    private ModBlocks() {}
}
