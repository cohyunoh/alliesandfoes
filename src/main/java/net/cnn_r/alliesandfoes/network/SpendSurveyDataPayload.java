package net.cnn_r.alliesandfoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S payload: player requests to convert survey data into alliance influence.
 * The server validates the request and deducts survey data before awarding influence.
 */
public record SpendSurveyDataPayload(int batches) implements CustomPacketPayload {

    public static final Type<SpendSurveyDataPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("alliesandfoes", "spend_survey_data"));

    public static final StreamCodec<FriendlyByteBuf, SpendSurveyDataPayload> STREAM_CODEC =
            StreamCodec.of(SpendSurveyDataPayload::write, SpendSurveyDataPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, SpendSurveyDataPayload payload) {
        buf.writeVarInt(payload.batches());
    }

    private static SpendSurveyDataPayload read(FriendlyByteBuf buf) {
        return new SpendSurveyDataPayload(buf.readVarInt());
    }
}
