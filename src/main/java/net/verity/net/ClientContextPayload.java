package net.verity.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClientContextPayload(
        String pcName,
        String osName,
        String osVersion,
        String osArch,
        String userName,
        String userHome,
        String cpuName,
        int cpuCores,
        int totalMemoryGB,
        int maxJvmMemoryMB,
        String gpuName,
        int screenWidth,
        int screenHeight,
        String gameDirectory,
        String localTime,
        String timezone,
        int fps,
        float masterVolume,
        String installedGames
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientContextPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.parse("verity:client_context"));

    public static final StreamCodec<FriendlyByteBuf, ClientContextPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.pcName(), 256);
                        buf.writeUtf(payload.osName(), 256);
                        buf.writeUtf(payload.osVersion(), 128);
                        buf.writeUtf(payload.osArch(), 64);
                        buf.writeUtf(payload.userName(), 256);
                        buf.writeUtf(payload.userHome(), 512);
                        buf.writeUtf(payload.cpuName(), 256);
                        buf.writeInt(payload.cpuCores());
                        buf.writeInt(payload.totalMemoryGB());
                        buf.writeInt(payload.maxJvmMemoryMB());
                        buf.writeUtf(payload.gpuName(), 256);
                        buf.writeInt(payload.screenWidth());
                        buf.writeInt(payload.screenHeight());
                        buf.writeUtf(payload.gameDirectory(), 512);
                        buf.writeUtf(payload.localTime(), 128);
                        buf.writeUtf(payload.timezone(), 128);
                        buf.writeInt(payload.fps());
                        buf.writeFloat(payload.masterVolume());
                        buf.writeUtf(payload.installedGames(), 1024);
                    },
                    buf -> new ClientContextPayload(
                            buf.readUtf(256),
                            buf.readUtf(256),
                            buf.readUtf(128),
                            buf.readUtf(64),
                            buf.readUtf(256),
                            buf.readUtf(512),
                            buf.readUtf(256),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readUtf(256),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readUtf(512),
                            buf.readUtf(128),
                            buf.readUtf(128),
                            buf.readInt(),
                            buf.readFloat(),
                            buf.readUtf(1024)
                    )
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
