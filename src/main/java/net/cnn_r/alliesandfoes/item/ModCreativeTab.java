package net.cnn_r.alliesandfoes.item;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ModCreativeTab {

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath("alliesandfoes", "main"),
                CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                        .title(Component.translatable("itemGroup.alliesandfoes.main"))
                        .icon(() -> new ItemStack(ModItems.COPPER_TOKEN))
                        .displayItems((params, output) -> {
                            output.accept(ModItems.COPPER_TOKEN);
                            output.accept(ModItems.IRON_TOKEN);
                            output.accept(ModItems.GOLD_TOKEN);
                            output.accept(ModItems.BASE_GENERATOR_ITEM);
                            output.accept(ModItems.RESOURCE_GENERATOR_ITEM);
                        })
                        .build());
    }
}
