package net.cnn_r.alliesandfoes.map.cache;

import net.cnn_r.alliesandfoes.map.data.PlayerMarker;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerMarkerCache {
    private final Map<UUID, PlayerMarker> markers = new ConcurrentHashMap<>();

    public void upsert(PlayerMarker marker) {
        this.markers.put(marker.uuid, marker);
    }

    public Collection<PlayerMarker> values() {
        return this.markers.values();
    }

    public void clear() {
        this.markers.clear();
    }
}