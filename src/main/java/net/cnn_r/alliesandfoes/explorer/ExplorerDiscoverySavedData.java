package net.cnn_r.alliesandfoes.explorer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.cnn_r.alliesandfoes.map.intuition.IntuitionTarget;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent NBT-backed storage for per-player Explorer discoveries (biomes and structures)
 * and each player's active intuition search target.
 */
public class ExplorerDiscoverySavedData extends SavedData {

    private static final String DATA_NAME = "explorer_discovery_data";

    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<StoredPlayerDiscovery> STORED_ENTRY_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUID_CODEC.fieldOf("player_uuid").forGetter(StoredPlayerDiscovery::playerUuid),
                    Codec.STRING.listOf().fieldOf("biomes").forGetter(StoredPlayerDiscovery::biomes),
                    Codec.STRING.listOf().fieldOf("structures").forGetter(StoredPlayerDiscovery::structures),
                    Codec.STRING.optionalFieldOf("target_type", "NONE").forGetter(StoredPlayerDiscovery::targetType),
                    Codec.STRING.optionalFieldOf("target_id", "").forGetter(StoredPlayerDiscovery::targetId)
            ).apply(instance, StoredPlayerDiscovery::new));

    private static final Codec<ExplorerDiscoverySavedData> CODEC =
            STORED_ENTRY_CODEC.listOf().xmap(
                    ExplorerDiscoverySavedData::fromEntries,
                    ExplorerDiscoverySavedData::toEntries
            );

    private static final SavedDataType<ExplorerDiscoverySavedData> TYPE = new SavedDataType<>(
            DATA_NAME,
            ExplorerDiscoverySavedData::new,
            CODEC,
            null
    );

    // Live maps — rebuilt on load, kept in sync on mutation
    private final Map<UUID, Set<String>> biomesByPlayer      = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> structuresByPlayer  = new LinkedHashMap<>();
    private final Map<UUID, IntuitionTarget> activeTargets   = new LinkedHashMap<>();

    public ExplorerDiscoverySavedData() {
    }

    private static ExplorerDiscoverySavedData fromEntries(List<StoredPlayerDiscovery> entries) {
        ExplorerDiscoverySavedData data = new ExplorerDiscoverySavedData();
        for (StoredPlayerDiscovery entry : entries) {
            data.biomesByPlayer.put(entry.playerUuid(), new LinkedHashSet<>(entry.biomes()));
            data.structuresByPlayer.put(entry.playerUuid(), new LinkedHashSet<>(entry.structures()));
            if (!"NONE".equals(entry.targetType()) && !entry.targetId().isEmpty()) {
                try {
                    IntuitionTarget.TargetType type = IntuitionTarget.TargetType.valueOf(entry.targetType());
                    data.activeTargets.put(entry.playerUuid(),
                            new IntuitionTarget(type, Identifier.parse(entry.targetId())));
                } catch (Exception ignored) {
                }
            }
        }
        return data;
    }

    private List<StoredPlayerDiscovery> toEntries() {
        Set<UUID> allPlayers = new LinkedHashSet<>();
        allPlayers.addAll(biomesByPlayer.keySet());
        allPlayers.addAll(structuresByPlayer.keySet());

        List<StoredPlayerDiscovery> result = new ArrayList<>(allPlayers.size());
        for (UUID uuid : allPlayers) {
            Set<String> biomes = biomesByPlayer.getOrDefault(uuid, new LinkedHashSet<>());
            Set<String> structures = structuresByPlayer.getOrDefault(uuid, new LinkedHashSet<>());
            IntuitionTarget target = activeTargets.get(uuid);

            String targetType = "NONE";
            String targetId = "";
            if (target != null) {
                targetType = target.type().name();
                targetId = target.id().toString();
            }

            result.add(new StoredPlayerDiscovery(uuid, new ArrayList<>(biomes), new ArrayList<>(structures), targetType, targetId));
        }
        return result;
    }

    public static ExplorerDiscoverySavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public Set<String> getBiomes(UUID playerUuid) {
        return biomesByPlayer.getOrDefault(playerUuid, new LinkedHashSet<>());
    }

    public Set<String> getStructures(UUID playerUuid) {
        return structuresByPlayer.getOrDefault(playerUuid, new LinkedHashSet<>());
    }

    public IntuitionTarget getActiveTarget(UUID playerUuid) {
        return activeTargets.get(playerUuid);
    }

    /**
     * Adds a biome to the player's discovered set.
     * Returns true if this was a new discovery.
     */
    public boolean addBiome(UUID playerUuid, String biomeId) {
        Set<String> biomes = biomesByPlayer.computeIfAbsent(playerUuid, k -> new LinkedHashSet<>());
        if (biomes.add(biomeId)) {
            setDirty();
            return true;
        }
        return false;
    }

    /**
     * Adds a structure to the player's discovered set.
     * Returns true if this was a new discovery.
     */
    public boolean addStructure(UUID playerUuid, String structureId) {
        Set<String> structures = structuresByPlayer.computeIfAbsent(playerUuid, k -> new LinkedHashSet<>());
        if (structures.add(structureId)) {
            setDirty();
            return true;
        }
        return false;
    }

    public void setActiveTarget(UUID playerUuid, IntuitionTarget target) {
        if (target == null) {
            activeTargets.remove(playerUuid);
        } else {
            activeTargets.put(playerUuid, target);
        }
        setDirty();
    }

    public record StoredPlayerDiscovery(
            UUID playerUuid,
            List<String> biomes,
            List<String> structures,
            String targetType,
            String targetId
    ) {
    }
}
