package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * Client-to-server territory action request.
 *
 * This is used for map-driven claim/unclaim actions.
 * The server remains authoritative and validates everything.
 */
public record RequestTerritoryActionPayload(
        ActionType actionType,
        String dimensionId,
        UUID anchorId,
        int chunkX,
        int chunkZ
) implements CustomPacketPayload {
    public static final Type<RequestTerritoryActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "request_territory_action"));

    public static final StreamCodec<FriendlyByteBuf, RequestTerritoryActionPayload> STREAM_CODEC =
            StreamCodec.of(RequestTerritoryActionPayload::write, RequestTerritoryActionPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum ActionType {
        CLAIM,
        UNCLAIM;

        public static ActionType read(FriendlyByteBuf buf) {
            int ordinal = buf.readVarInt();
            ActionType[] values = values();

            if (ordinal < 0 || ordinal >= values.length) {
                return CLAIM;
            }

            return values[ordinal];
        }

        public static void write(FriendlyByteBuf buf, ActionType type) {
            buf.writeVarInt(type.ordinal());
        }
    }

    private static void write(FriendlyByteBuf buf, RequestTerritoryActionPayload payload) {
        ActionType.write(buf, payload.actionType());
        buf.writeUtf(payload.dimensionId());

        buf.writeBoolean(payload.anchorId() != null);
        if (payload.anchorId() != null) {
            buf.writeUUID(payload.anchorId());
        }

        buf.writeInt(payload.chunkX());
        buf.writeInt(payload.chunkZ());
    }

    private static RequestTerritoryActionPayload read(FriendlyByteBuf buf) {
        ActionType actionType = ActionType.read(buf);
        String dimensionId = buf.readUtf();

        UUID anchorId = null;
        if (buf.readBoolean()) {
            anchorId = buf.readUUID();
        }

        int chunkX = buf.readInt();
        int chunkZ = buf.readInt();

        return new RequestTerritoryActionPayload(
                actionType,
                dimensionId,
                anchorId,
                chunkX,
                chunkZ
        );
    }
}