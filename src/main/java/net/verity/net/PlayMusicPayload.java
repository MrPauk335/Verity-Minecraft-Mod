package net.verity.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlayMusicPayload(String soundName, float volume, float pitch, boolean looping) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PlayMusicPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.parse("verity:play_music"));

    public PlayMusicPayload(String soundName, float volume, float pitch) {
        this(soundName, volume, pitch, true);
    }

    public static final StreamCodec<FriendlyByteBuf, PlayMusicPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.soundName(), 128);
                        buf.writeFloat(payload.volume());
                        buf.writeFloat(payload.pitch());
                        buf.writeBoolean(payload.looping());
                    },
                    buf -> new PlayMusicPayload(buf.readUtf(128), buf.readFloat(), buf.readFloat(), buf.readBoolean())
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
