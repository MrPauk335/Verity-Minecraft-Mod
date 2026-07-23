package net.verity.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TriggerFinalPhasePayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TriggerFinalPhasePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.parse("verity:trigger_final_phase"));

    public static final StreamCodec<FriendlyByteBuf, TriggerFinalPhasePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {},
                    buf -> new TriggerFinalPhasePayload()
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
