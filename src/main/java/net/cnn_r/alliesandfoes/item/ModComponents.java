package net.cnn_r.alliesandfoes.item;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

public final class ModComponents {

    public static DataComponentType<Integer> ROLE_CURRENCY;
    public static DataComponentType<Integer> ROLE_UPGRADE_LEVEL;

    public static void register() {
        ROLE_CURRENCY = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath("alliesandfoes", "role_currency"),
                DataComponentType.<Integer>builder()
                        .persistent(Codec.INT)
                        .networkSynchronized(ByteBufCodecs.VAR_INT)
                        .build()
        );
        ROLE_UPGRADE_LEVEL = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath("alliesandfoes", "role_upgrade_level"),
                DataComponentType.<Integer>builder()
                        .persistent(Codec.INT)
                        .networkSynchronized(ByteBufCodecs.VAR_INT)
                        .build()
        );
    }

    private ModComponents() {}
}
