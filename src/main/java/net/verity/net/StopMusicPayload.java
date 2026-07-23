package net.verity.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StopMusicPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StopMusicPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.parse("verity:stop_music"));

    public static final StreamCodec<FriendlyByteBuf, StopMusicPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {},
                    buf -> new StopMusicPayload()
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
