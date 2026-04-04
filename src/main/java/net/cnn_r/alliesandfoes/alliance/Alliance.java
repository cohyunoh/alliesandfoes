package net.cnn_r.alliesandfoes.alliance;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class Alliance {
    private final UUID id;
    private final String name;
    private final UUID ownerUuid;
    private final Set<UUID> memberUuids = new LinkedHashSet<>();

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

    public Set<UUID> getMemberUuids() {
        return memberUuids;
    }

    public void addMember(UUID uuid) {
        memberUuids.add(uuid);
    }

    public boolean hasMember(UUID uuid) {
        return memberUuids.contains(uuid);
    }
}