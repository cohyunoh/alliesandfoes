package net.cnn_r.alliesandfoes.item;

import net.minecraft.world.item.Item;

/**
 * The Monocle — a craftable item that activates the Explorer HUD minimap
 * and surfaces ambient intuition text while held.
 *
 * In V1 the item itself is the only gate. Explorer skill is tracked passively
 * in the background but does not yet change the monocle's behavior.
 */
public class MonocleItem extends Item {

    public MonocleItem(Properties properties) {
        super(properties);
    }
}
