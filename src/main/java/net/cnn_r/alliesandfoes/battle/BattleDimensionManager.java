package net.cnn_r.alliesandfoes.battle;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.WeakHashMap;

public class BattleDimensionManager {
    private static final Map<MinecraftServer, BattleDimensionManager> INSTANCES = new WeakHashMap<>();

    private static final ResourceKey<Level> BATTLE_DIMENSION =
            ResourceKey.create(Registries.DIMENSION, Identifier.parse("alliesandfoes:battle"));

    private int nextOffset = 0;

    private BattleDimensionManager() {}

    public static BattleDimensionManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, s -> new BattleDimensionManager());
    }

    public int allocateOffset() {
        int offset = nextOffset;
        nextOffset += 100_000;
        return offset;
    }

    public void freeOffset(int offset) {
        // Offsets are not currently recycled — each battle gets a unique slot
    }

    public ServerLevel getBattleLevel(MinecraftServer server) {
        return server.getLevel(BATTLE_DIMENSION);
    }
}
