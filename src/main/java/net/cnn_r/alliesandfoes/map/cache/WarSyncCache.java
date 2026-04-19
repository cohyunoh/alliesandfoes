package net.cnn_r.alliesandfoes.map.cache;

import net.cnn_r.alliesandfoes.network.WarStateSyncPayload;

import java.util.ArrayList;
import java.util.List;

public class WarSyncCache {
    private List<WarStateSyncPayload.WarEntry> wars = new ArrayList<>();

    public void update(List<WarStateSyncPayload.WarEntry> incoming) {
        this.wars = new ArrayList<>(incoming);
    }

    public List<WarStateSyncPayload.WarEntry> getWars() {
        return wars;
    }

    public void clear() {
        this.wars = new ArrayList<>();
    }
}
