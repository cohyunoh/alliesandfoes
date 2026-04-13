package net.cnn_r.alliesandfoes.map.intuition;

import net.minecraft.resources.Identifier;

/**
 * An active intuition search target selected by the player in the Explorer Journal.
 *
 * When a target is set, the intuition evaluator applies a heavy score bonus to
 * chunks that match the target biome or structure, focusing the directional signal
 * toward previously-discovered locations of interest.
 */
public record IntuitionTarget(TargetType type, Identifier id) {

    public enum TargetType {
        BIOME,
        STRUCTURE
    }

    /**
     * Returns a human-readable display name by title-casing the resource path.
     * e.g. "minecraft:village_plains" → "Village Plains"
     */
    public String displayName() {
        String path = id.getPath();
        String[] words = path.split("[/_]");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (sb.length() > 0) sb.append(' ');
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
