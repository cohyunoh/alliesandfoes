package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C payload: syncs the player's current Explorer skill state (personal XP and survey data).
 * Sent on login and on each discovery or XP change.
 */
public record ExplorerSkillSyncPayload(int surveyData, int explorerXp) implements CustomPacketPayload {

    public static final Type<ExplorerSkillSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "explorer_skill_sync"));

    public static final StreamCodec<FriendlyByteBuf, ExplorerSkillSyncPayload> STREAM_CODEC =
            StreamCodec.of(ExplorerSkillSyncPayload::write, ExplorerSkillSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, ExplorerSkillSyncPayload payload) {
        buf.writeVarInt(payload.surveyData());
        buf.writeVarInt(payload.explorerXp());
    }

    private static ExplorerSkillSyncPayload read(FriendlyByteBuf buf) {
        int survey = buf.readVarInt();
        int xp     = buf.readVarInt();
        return new ExplorerSkillSyncPayload(survey, xp);
    }
}
