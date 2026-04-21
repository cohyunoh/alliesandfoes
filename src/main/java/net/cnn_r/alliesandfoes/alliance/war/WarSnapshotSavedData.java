package net.cnn_r.alliesandfoes.alliance.war;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persists WarSnapshotService pet death records and raided chest keys across server restarts.
 * Block snapshots are intentionally not persisted (unsafe to restore after restart).
 */
public class WarSnapshotSavedData extends SavedData {
    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:war_snapshot_data");

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<StoredPetDeath> PET_DEATH_CODEC = RecordCodecBuilder.create(i -> i.group(
            UUID_CODEC.fieldOf("war_id").forGetter(StoredPetDeath::warId),
            Codec.STRING.fieldOf("dimension_id").forGetter(StoredPetDeath::dimensionId),
            Codec.INT.fieldOf("x").forGetter(StoredPetDeath::x),
            Codec.INT.fieldOf("y").forGetter(StoredPetDeath::y),
            Codec.INT.fieldOf("z").forGetter(StoredPetDeath::z),
            TagParser.LENIENT_CODEC.fieldOf("entity_nbt").forGetter(StoredPetDeath::entityNbt),
            Codec.STRING.optionalFieldOf("owner_uuid", "").forGetter(StoredPetDeath::ownerUuidString)
    ).apply(i, StoredPetDeath::new));

    private static final Codec<WarSnapshotSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            PET_DEATH_CODEC.listOf().optionalFieldOf("pet_deaths", List.of()).forGetter(WarSnapshotSavedData::getPetDeaths),
            Codec.STRING.listOf().optionalFieldOf("raided_chest_keys", List.of()).forGetter(WarSnapshotSavedData::getRaidedChestKeyEntries)
    ).apply(i, WarSnapshotSavedData::new));

    private static final SavedDataType<WarSnapshotSavedData> TYPE = new SavedDataType<>(
            DATA_NAME,
            WarSnapshotSavedData::new,
            CODEC,
            null
    );

    private List<StoredPetDeath> petDeaths = new ArrayList<>();
    private List<String> raidedChestKeyEntries = new ArrayList<>();

    public WarSnapshotSavedData() {}

    private WarSnapshotSavedData(List<StoredPetDeath> petDeaths, List<String> raidedChestKeyEntries) {
        this.petDeaths = new ArrayList<>(petDeaths);
        this.raidedChestKeyEntries = new ArrayList<>(raidedChestKeyEntries);
    }

    public static WarSnapshotSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private List<StoredPetDeath> getPetDeaths() { return petDeaths; }
    private List<String> getRaidedChestKeyEntries() { return raidedChestKeyEntries; }

    /** Saves current WarSnapshotService state into this SavedData and marks dirty. */
    public void saveFromService(WarSnapshotService service) {
        this.petDeaths.clear();
        this.raidedChestKeyEntries.clear();

        for (Map.Entry<UUID, List<WarSnapshotService.PetDeathRecord>> entry : service.getPetDeathsByWar().entrySet()) {
            UUID warId = entry.getKey();
            for (WarSnapshotService.PetDeathRecord rec : entry.getValue()) {
                this.petDeaths.add(new StoredPetDeath(
                        warId,
                        rec.dimensionId(),
                        rec.pos().getX(),
                        rec.pos().getY(),
                        rec.pos().getZ(),
                        rec.entityNbt(),
                        rec.ownerUuid() != null ? rec.ownerUuid().toString() : ""
                ));
            }
        }

        for (Map.Entry<UUID, Set<String>> entry : service.getRaidedChestKeysByWar().entrySet()) {
            String warIdStr = entry.getKey().toString();
            for (String posKey : entry.getValue()) {
                this.raidedChestKeyEntries.add(warIdStr + "|" + posKey);
            }
        }

        this.setDirty();
    }

    /** Loads persisted data into a freshly created WarSnapshotService. */
    public void loadIntoService(WarSnapshotService service) {
        Map<UUID, List<WarSnapshotService.PetDeathRecord>> petDeathMap = new HashMap<>();
        for (StoredPetDeath stored : this.petDeaths) {
            UUID ownerUuid = stored.ownerUuidString().isBlank() ? null : UUID.fromString(stored.ownerUuidString());
            WarSnapshotService.PetDeathRecord rec = new WarSnapshotService.PetDeathRecord(
                    stored.dimensionId(),
                    new BlockPos(stored.x(), stored.y(), stored.z()),
                    stored.entityNbt(),
                    ownerUuid
            );
            petDeathMap.computeIfAbsent(stored.warId(), k -> new ArrayList<>()).add(rec);
        }

        Map<UUID, Set<String>> raidedMap = new HashMap<>();
        for (String entry : this.raidedChestKeyEntries) {
            int sep = entry.indexOf('|');
            if (sep < 0) continue;
            UUID warId = UUID.fromString(entry.substring(0, sep));
            String posKey = entry.substring(sep + 1);
            raidedMap.computeIfAbsent(warId, k -> new HashSet<>()).add(posKey);
        }

        service.restoreFromSavedData(petDeathMap, raidedMap);
    }

    public record StoredPetDeath(
            UUID warId,
            String dimensionId,
            int x, int y, int z,
            CompoundTag entityNbt,
            String ownerUuidString
    ) {}
}
