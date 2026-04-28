package net.cnn_r.alliesandfoes.map.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Client-side store for rally markers broadcast by the server. Replaced in full each sync. */
public class RallyMarkerCache {

    public record Entry(String warriorName, int chunkX, int chunkZ, String dimensionId, long expiryTick) {}

    private List<Entry> markers = new ArrayList<>();

    public void update(List<Entry> incoming) {
        this.markers = new ArrayList<>(incoming);
    }

    public List<Entry> getAll() {
        return Collections.unmodifiableList(markers);
    }

    public void clear() {
        markers.clear();
    }
}
