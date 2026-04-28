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
                        .icon(() -> new ItemStack(ModItems.CARTOGRAPHERS_JOURNAL))
                        .displayItems((params, output) -> {
                            output.accept(ModItems.CARTOGRAPHERS_JOURNAL);
                            output.accept(ModItems.WAR_HORN);
                            output.accept(ModItems.FARMERS_ALMANAC);
                            output.accept(ModItems.DOWSING_ROD);
                            output.accept(ModItems.COVENANT_SHARD);
                            output.accept(ModItems.TRIBUTE_ALTAR);
                            output.accept(ModItems.COVENANT_FORGE);
                        })
                        .build());
    }
}
