package net.verity.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TTSPayload(String text) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TTSPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.parse("verity:tts_play"));

    public static final StreamCodec<FriendlyByteBuf, TTSPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeUtf(payload.text(), 1024),
                    buf -> new TTSPayload(buf.readUtf(1024))
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
