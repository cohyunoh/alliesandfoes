package net.cnn_r.alliesandfoes.alliance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AllianceSavedData extends SavedData {
    private static final Identifier DATA_NAME = Identifier.parse("alliesandfoes:alliance_data");

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private static final Codec<LinkedHashSet<UUID>> UUID_SET_CODEC = UUID_CODEC.listOf().xmap(
            list -> {
                LinkedHashSet<UUID> set = new LinkedHashSet<>();
                set.addAll(list);
                return set;
            },
            set -> new ArrayList<>(set)
    );

    private static final Codec<LinkedHashMap<UUID, String>> ROLE_MAP_CODEC = Codec.unboundedMap(UUID_CODEC, Codec.STRING).xmap(
            map -> {
                LinkedHashMap<UUID, String> ordered = new LinkedHashMap<>();
                ordered.putAll(map);
                return ordered;
            },
            map -> {
                LinkedHashMap<UUID, String> ordered = new LinkedHashMap<>();
                ordered.putAll(map);
                return ordered;
            }
    );

    private static final Codec<LinkedHashSet<MemberPermission>> PERMISSION_SET_CODEC =
            MemberPermission.CODEC.listOf().xmap(
                    list -> new LinkedHashSet<>(list),
                    set -> new ArrayList<>(set)
            );

    private static final Codec<LinkedHashMap<UUID, LinkedHashSet<MemberPermission>>> PERMISSIONS_MAP_CODEC =
            Codec.unboundedMap(UUID_CODEC, PERMISSION_SET_CODEC).xmap(
                    map -> {
                        LinkedHashMap<UUID, LinkedHashSet<MemberPermission>> ordered = new LinkedHashMap<>();
                        ordered.putAll(map);
                        return ordered;
                    },
                    map -> {
                        LinkedHashMap<UUID, LinkedHashSet<MemberPermission>> ordered = new LinkedHashMap<>();
                        ordered.putAll(map);
                        return ordered;
                    }
            );

    private static final Codec<StoredAlliance> STORED_ALLIANCE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("id").forGetter(StoredAlliance::id),
            Codec.STRING.fieldOf("name").forGetter(StoredAlliance::name),
            UUID_CODEC.fieldOf("owner_uuid").forGetter(StoredAlliance::ownerUuid),
            UUID_SET_CODEC.fieldOf("members").forGetter(StoredAlliance::members),
            ROLE_MAP_CODEC.fieldOf("roles").forGetter(StoredAlliance::roles),
            UUID_SET_CODEC.fieldOf("pending_invites").forGetter(StoredAlliance::pendingInvites),
            UUID_SET_CODEC.fieldOf("pending_join_requests").forGetter(StoredAlliance::pendingJoinRequests),
            PERMISSIONS_MAP_CODEC.optionalFieldOf("permissions", new LinkedHashMap<>()).forGetter(StoredAlliance::permissions)
    ).apply(instance, StoredAlliance::new));

    private static final Codec<AllianceSavedData> CODEC = STORED_ALLIANCE_CODEC.listOf().xmap(
            AllianceSavedData::new,
            AllianceSavedData::getStoredAlliances
    );

    private static final SavedDataType<AllianceSavedData> TYPE = new SavedDataType<>(
            DATA_NAME,
            AllianceSavedData::new,
            CODEC,
            null
    );

    private List<StoredAlliance> storedAlliances;

    public AllianceSavedData() {
        this.storedAlliances = new ArrayList<>();
    }

    private AllianceSavedData(List<StoredAlliance> storedAlliances) {
        this.storedAlliances = new ArrayList<>(storedAlliances);
    }

    public static AllianceSavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<StoredAlliance> getStoredAlliances() {
        return new ArrayList<>(this.storedAlliances);
    }

    public List<Alliance> createLiveAlliances() {
        List<Alliance> alliances = new ArrayList<>(this.storedAlliances.size());

        for (StoredAlliance storedAlliance : this.storedAlliances) {
            alliances.add(storedAlliance.toLiveAlliance());
        }

        return alliances;
    }

    public void saveFromLiveAlliances(Collection<Alliance> alliances) {
        List<StoredAlliance> snapshot = new ArrayList<>(alliances.size());

        for (Alliance alliance : alliances) {
            snapshot.add(StoredAlliance.fromLiveAlliance(alliance));
        }

        this.storedAlliances = snapshot;
        this.setDirty();
    }

    public record StoredAlliance(
            UUID id,
            String name,
            UUID ownerUuid,
            LinkedHashSet<UUID> members,
            LinkedHashMap<UUID, String> roles,
            LinkedHashSet<UUID> pendingInvites,
            LinkedHashSet<UUID> pendingJoinRequests,
            LinkedHashMap<UUID, LinkedHashSet<MemberPermission>> permissions
    ) {
        public static StoredAlliance fromLiveAlliance(Alliance alliance) {
            LinkedHashSet<UUID> members = new LinkedHashSet<>(alliance.getMemberUuids());
            LinkedHashMap<UUID, String> roles = new LinkedHashMap<>(alliance.getMemberRoles());
            LinkedHashSet<UUID> pendingInvites = new LinkedHashSet<>(alliance.getPendingInviteUuids());
            LinkedHashSet<UUID> pendingJoinRequests = new LinkedHashSet<>(alliance.getPendingJoinRequestUuids());
            LinkedHashMap<UUID, LinkedHashSet<MemberPermission>> permissions = new LinkedHashMap<>();
            for (Map.Entry<UUID, Set<MemberPermission>> e : alliance.getMemberPermissions().entrySet()) {
                permissions.put(e.getKey(), new LinkedHashSet<>(e.getValue()));
            }

            return new StoredAlliance(
                    alliance.getId(),
                    alliance.getName(),
                    alliance.getOwnerUuid(),
                    members,
                    roles,
                    pendingInvites,
                    pendingJoinRequests,
                    permissions
            );
        }

        public Alliance toLiveAlliance() {
            Alliance alliance = new Alliance(this.id, this.name, this.ownerUuid);

            for (UUID memberUuid : this.members) {
                if (!memberUuid.equals(this.ownerUuid)) {
                    alliance.addMember(memberUuid);
                }
            }

            for (Map.Entry<UUID, String> entry : this.roles.entrySet()) {
                UUID memberUuid = entry.getKey();

                if (alliance.hasMember(memberUuid)) {
                    alliance.setRole(memberUuid, entry.getValue());
                }
            }

            for (UUID inviteUuid : this.pendingInvites) {
                alliance.addPendingInvite(inviteUuid);
            }

            for (UUID requesterUuid : this.pendingJoinRequests) {
                alliance.addPendingJoinRequest(requesterUuid);
            }

            for (Map.Entry<UUID, LinkedHashSet<MemberPermission>> e : this.permissions.entrySet()) {
                for (MemberPermission perm : e.getValue()) {
                    alliance.setPermission(e.getKey(), perm, true);
                }
            }

            return alliance;
        }
    }
}
