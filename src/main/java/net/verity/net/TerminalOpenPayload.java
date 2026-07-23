package net.verity.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C payload to open the Verity Terminal screen.
 */
public record TerminalOpenPayload(BlockPos pos) implements CustomPacketPayload {

    public static final TerminalOpenPayload INSTANCE = new TerminalOpenPayload(BlockPos.ZERO);

    public static final Type<TerminalOpenPayload> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.parse("verity:terminal_open"));

    public static final StreamCodec<FriendlyByteBuf, TerminalOpenPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TerminalOpenPayload decode(FriendlyByteBuf buf) {
            return new TerminalOpenPayload(buf.readBlockPos());
        }

        @Override
        public void encode(FriendlyByteBuf buf, TerminalOpenPayload payload) {
            buf.writeBlockPos(payload.pos());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
