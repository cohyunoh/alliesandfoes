package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record AllianceStatePayload(
        boolean inAlliance,
        String allianceName,
        String memberRole,
        UUID ownerUuid,
        UUID allianceId
) implements CustomPacketPayload {

    public static final Type<AllianceStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "alliance_state"));

    public static final StreamCodec<FriendlyByteBuf, AllianceStatePayload> STREAM_CODEC =
            StreamCodec.of(AllianceStatePayload::write, AllianceStatePayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, AllianceStatePayload payload) {
        buf.writeBoolean(payload.inAlliance());
        buf.writeUtf(payload.allianceName());
        buf.writeUtf(payload.memberRole());
        buf.writeBoolean(payload.ownerUuid() != null);
        if (payload.ownerUuid() != null) {
            buf.writeUUID(payload.ownerUuid());
        }
        buf.writeBoolean(payload.allianceId() != null);
        if (payload.allianceId() != null) {
            buf.writeUUID(payload.allianceId());
        }
    }

    private static AllianceStatePayload read(FriendlyByteBuf buf) {
        boolean inAlliance = buf.readBoolean();
        String allianceName = buf.readUtf();
        String memberRole = buf.readUtf();
        UUID ownerUuid = null;
        if (buf.readBoolean()) {
            ownerUuid = buf.readUUID();
        }
        UUID allianceId = null;
        if (buf.readBoolean()) {
            allianceId = buf.readUUID();
        }
        return new AllianceStatePayload(inAlliance, allianceName, memberRole, ownerUuid, allianceId);
    }
}
