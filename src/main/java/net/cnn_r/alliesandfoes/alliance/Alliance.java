package net.cnn_r.alliesandfoes.alliance;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class Alliance {
    private final UUID id;
    private final String name;
    private UUID ownerUuid;
    private final Set<UUID> memberUuids = new LinkedHashSet<>();
    private final Set<UUID> pendingInviteUuids = new LinkedHashSet<>();

    public Alliance(UUID id, String name, UUID ownerUuid) {
        this.id = id;
        this.name = name;
        this.ownerUuid = ownerUuid;
        this.memberUuids.add(ownerUuid);
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
        this.ownerUuid = ownerUuid;
        this.memberUuids.add(ownerUuid);
    }

    public Set<UUID> getMemberUuids() {
        return memberUuids;
    }

    public Set<UUID> getPendingInviteUuids() {
        return pendingInviteUuids;
    }

    public void addMember(UUID uuid) {
        memberUuids.add(uuid);
        pendingInviteUuids.remove(uuid);
    }

    public void removeMember(UUID uuid) {
        memberUuids.remove(uuid);
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
}