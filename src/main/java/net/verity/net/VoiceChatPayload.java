package net.verity.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VoiceChatPayload(String text) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VoiceChatPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.parse("verity:voice_chat"));

    public static final StreamCodec<FriendlyByteBuf, VoiceChatPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeUtf(payload.text(), 512),
                    buf -> new VoiceChatPayload(buf.readUtf(512))
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
