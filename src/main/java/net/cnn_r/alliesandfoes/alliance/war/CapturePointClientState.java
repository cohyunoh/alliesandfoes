package net.cnn_r.alliesandfoes.alliance.war;

import net.cnn_r.alliesandfoes.network.CapturePointSyncPayload;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Client-side storage for the current active war's capture point state. */
public final class CapturePointClientState {
    private static UUID currentWarId = null;
    private static List<CapturePointSyncPayload.Entry> points = Collections.emptyList();

    private CapturePointClientState() {}

    public static void update(UUID warId, List<CapturePointSyncPayload.Entry> incoming) {
        currentWarId = warId;
        points = List.copyOf(incoming);
    }

    public static void clear() {
        currentWarId = null;
        points = Collections.emptyList();
    }

    public static UUID getWarId() { return currentWarId; }

    public static List<CapturePointSyncPayload.Entry> getPoints() { return points; }
}
