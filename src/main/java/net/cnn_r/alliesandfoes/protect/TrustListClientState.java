package net.cnn_r.alliesandfoes.protect;

import net.cnn_r.alliesandfoes.network.TrustListSyncPayload;

import java.util.List;

public class TrustListClientState {
    public static final TrustListClientState INSTANCE = new TrustListClientState();

    private List<TrustListSyncPayload.TrustEntry> entries = List.of();

    private TrustListClientState() {}

    public void update(TrustListSyncPayload payload) {
        this.entries = payload.entries();
    }

    public List<TrustListSyncPayload.TrustEntry> getEntries() {
        return entries;
    }

    public void reset() {
        this.entries = List.of();
    }
}
