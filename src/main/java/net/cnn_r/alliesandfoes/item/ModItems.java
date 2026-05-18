package net.cnn_r.alliesandfoes.item;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {

    private static final ResourceKey<Item> ALLIANCE_RECALL_STONE_KEY =
            ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("alliesandfoes", "alliance_recall_stone"));

    private static final ResourceKey<Item> SURVEY_SCROLL_KEY =
            ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("alliesandfoes", "survey_scroll"));

    public static final AllianceRecallStoneItem ALLIANCE_RECALL_STONE = new AllianceRecallStoneItem(
            new Item.Properties().setId(ALLIANCE_RECALL_STONE_KEY).stacksTo(1)
    );

    public static final SurveyScrollItem SURVEY_SCROLL = new SurveyScrollItem(
            new Item.Properties().setId(SURVEY_SCROLL_KEY).stacksTo(16)
    );

    private ModItems() {}

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath("alliesandfoes", "alliance_recall_stone"),
                ALLIANCE_RECALL_STONE);

        Registry.register(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath("alliesandfoes", "survey_scroll"),
                SURVEY_SCROLL);
    }
}
