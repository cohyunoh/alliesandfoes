package net.cnn_r.alliesandfoes.territory;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashSet;
import java.util.Set;

public class ChunkFragmentSavedData extends SavedData {

    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:chunk_fragment_data");

    private static final Codec<ChunkFragmentSavedData> CODEC =
            Codec.list(Codec.STRING)
                    .xmap(
                            list -> new ChunkFragmentSavedData(new HashSet<>(list)),
                            d -> new java.util.ArrayList<>(d.excavated)
                    );

    private static final SavedDataType<ChunkFragmentSavedData> TYPE = new SavedDataType<>(
            DATA_NAME,
            () -> new ChunkFragmentSavedData(new HashSet<>()),
            CODEC,
            null
    );

    private final Set<String> excavated;

    private ChunkFragmentSavedData(Set<String> excavated) {
        this.excavated = excavated;
    }

    public static ChunkFragmentSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isExcavated(String posKey) {
        return excavated.contains(posKey);
    }

    public void markExcavated(String posKey) {
        excavated.add(posKey);
        setDirty();
    }
}
