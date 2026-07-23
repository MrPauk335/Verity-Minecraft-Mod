package net.verity.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClientClockPayload(String localTime) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientClockPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.parse("verity:client_clock"));

    public static final StreamCodec<FriendlyByteBuf, ClientClockPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeUtf(payload.localTime(), 128),
                    buf -> new ClientClockPayload(buf.readUtf(128))
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
