package net.cnn_r.alliesandfoes.map.data;

import java.util.UUID;

public class PlayerMarker {
    public final UUID uuid;
    public final String name;
    public double x;
    public double z;
    public long lastSeenTick;

    public PlayerMarker(UUID uuid, String name, double x, double z, long lastSeenTick) {
        this.uuid = uuid;
        this.name = name;
        this.x = x;
        this.z = z;
        this.lastSeenTick = lastSeenTick;
    }
}