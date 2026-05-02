package net.cnn_r.alliesandfoes.battle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GeneratorPedestalSavedData extends SavedData {

    public record PedestalData(BlockPos pos, String dimensionId) {
        public static final Codec<PedestalData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(PedestalData::pos),
                Codec.STRING.fieldOf("dimensionId").forGetter(PedestalData::dimensionId)
        ).apply(inst, PedestalData::new));
    }

    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:generator_pedestal_data");

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<Map<UUID, PedestalData>> DATA_CODEC =
            Codec.unboundedMap(UUID_CODEC, PedestalData.CODEC).xmap(HashMap::new, m -> m);

    private static final Codec<GeneratorPedestalSavedData> CODEC =
            DATA_CODEC.xmap(GeneratorPedestalSavedData::new, d -> d.pedestals);

    private static final SavedDataType<GeneratorPedestalSavedData> TYPE = new SavedDataType<>(
            DATA_NAME,
            () -> new GeneratorPedestalSavedData(new HashMap<>()),
            CODEC,
            null
    );

    private final Map<UUID, PedestalData> pedestals;

    private GeneratorPedestalSavedData(Map<UUID, PedestalData> pedestals) {
        this.pedestals = pedestals;
    }

    public static GeneratorPedestalSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void setPedestal(UUID allianceId, BlockPos pos, String dimensionId) {
        pedestals.put(allianceId, new PedestalData(pos, dimensionId));
        setDirty();
    }

    public PedestalData getPedestal(UUID allianceId) {
        return pedestals.get(allianceId);
    }

    public void removePedestal(UUID allianceId) {
        pedestals.remove(allianceId);
        setDirty();
    }
}
