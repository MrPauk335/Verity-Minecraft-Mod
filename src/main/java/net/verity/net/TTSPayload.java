package net.verity.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TTSPayload(String text, int entityId, double x, double y, double z) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TTSPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.parse("verity:tts_play"));

    public static final StreamCodec<FriendlyByteBuf, TTSPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.text(), 1024);
                        buf.writeInt(payload.entityId());
                        buf.writeDouble(payload.x());
                        buf.writeDouble(payload.y());
                        buf.writeDouble(payload.z());
                    },
                    buf -> new TTSPayload(
                            buf.readUtf(1024),
                            buf.readInt(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble()
                    )
            );

    public TTSPayload(String text) {
        this(text, -1, 0, 0, 0);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
