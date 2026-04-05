package net.cnn_r.alliesandfoes.alliance;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Alliance {
    private static final String DEFAULT_MEMBER_ROLE = "Member";
    private static final String OWNER_ROLE = "Founder";

    private final UUID id;
    private final String name;
    private UUID ownerUuid;
    private final Set<UUID> memberUuids = new LinkedHashSet<>();
    private final Set<UUID> pendingInviteUuids = new LinkedHashSet<>();
    private final Map<UUID, String> memberRoles = new LinkedHashMap<>();

    public Alliance(UUID id, String name, UUID ownerUuid) {
        this.id = id;
        this.name = name;
        this.ownerUuid = ownerUuid;
        this.memberUuids.add(ownerUuid);
        this.memberRoles.put(ownerUuid, OWNER_ROLE);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        UUID previousOwner = this.ownerUuid;
        this.ownerUuid = ownerUuid;
        this.memberUuids.add(ownerUuid);

        if (previousOwner != null && this.memberUuids.contains(previousOwner)) {
            this.memberRoles.put(previousOwner, DEFAULT_MEMBER_ROLE);
        }

        this.memberRoles.put(ownerUuid, OWNER_ROLE);
    }

    public Set<UUID> getMemberUuids() {
        return memberUuids;
    }

    public Set<UUID> getPendingInviteUuids() {
        return pendingInviteUuids;
    }

    public Map<UUID, String> getMemberRoles() {
        return memberRoles;
    }

    public void addMember(UUID uuid) {
        memberUuids.add(uuid);
        pendingInviteUuids.remove(uuid);
        memberRoles.putIfAbsent(uuid, DEFAULT_MEMBER_ROLE);
    }

    public void removeMember(UUID uuid) {
        memberUuids.remove(uuid);
        memberRoles.remove(uuid);
    }

    public boolean hasMember(UUID uuid) {
        return memberUuids.contains(uuid);
    }

    public void addPendingInvite(UUID uuid) {
        if (!memberUuids.contains(uuid)) {
            pendingInviteUuids.add(uuid);
        }
    }

    public void removePendingInvite(UUID uuid) {
        pendingInviteUuids.remove(uuid);
    }

    public boolean hasPendingInvite(UUID uuid) {
        return pendingInviteUuids.contains(uuid);
    }

    public String getRole(UUID uuid) {
        if (uuid == null || !memberUuids.contains(uuid)) {
            return "";
        }

        if (uuid.equals(ownerUuid)) {
            return OWNER_ROLE;
        }

        return memberRoles.getOrDefault(uuid, DEFAULT_MEMBER_ROLE);
    }

    public void setRole(UUID uuid, String rawRole) {
        if (uuid == null || !memberUuids.contains(uuid)) {
            return;
        }

        if (uuid.equals(ownerUuid)) {
            memberRoles.put(uuid, OWNER_ROLE);
            return;
        }

        String role = sanitizeRole(rawRole);
        memberRoles.put(uuid, role);
    }

    public static String sanitizeRole(String rawRole) {
        if (rawRole == null) {
            return DEFAULT_MEMBER_ROLE;
        }

        String trimmed = rawRole.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_MEMBER_ROLE;
        }

        if (trimmed.length() > 20) {
            trimmed = trimmed.substring(0, 20).trim();
        }

        return trimmed.isEmpty() ? DEFAULT_MEMBER_ROLE : trimmed;
    }
}