package net.minecraft.network.protocol.login;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import net.minecraft.network.protocol.login.custom.DiscardedQueryAnswerPayload;

public record ServerboundCustomQueryAnswerPacket(int transactionId, @Nullable CustomQueryAnswerPayload payload) implements Packet<ServerLoginPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundCustomQueryAnswerPacket> STREAM_CODEC = Packet.codec(
        ServerboundCustomQueryAnswerPacket::write, ServerboundCustomQueryAnswerPacket::read
    );
    private static final int MAX_PAYLOAD_SIZE = 1048576;

    private static ServerboundCustomQueryAnswerPacket read(FriendlyByteBuf p_295711_) {
        int i = p_295711_.readVarInt();
        return new ServerboundCustomQueryAnswerPacket(i, readPayload(i, p_295711_));
    }

    private static CustomQueryAnswerPayload readPayload(int p_296215_, FriendlyByteBuf p_295168_) {
        // Paper start - MC Utils - default query payloads
        boolean hasPayload = p_295168_.readBoolean();
        if (!hasPayload) {
            return DiscardedQueryAnswerPayload.INSTANCE;
        }
        int i = p_295168_.readableBytes();
        if (i >= 0 && i < MAX_PAYLOAD_SIZE) {
            var payload = Unpooled.buffer(i);
            p_295168_.readBytes(payload);
            return new net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket.QueryAnswerPayload(Unpooled.wrappedBuffer(Unpooled.copyBoolean(true), payload));
        } else {
            throw new IllegalArgumentException("Payload may not be larger than 1048576 bytes");
        }
        // Paper end - MC Utils - default query payloads
    }

    private static CustomQueryAnswerPayload readUnknownPayload(FriendlyByteBuf p_294928_) {
        int i = p_294928_.readableBytes();
        if (i >= 0 && i <= 1048576) {
            p_294928_.skipBytes(i);
            return DiscardedQueryAnswerPayload.INSTANCE;
        } else {
            throw new IllegalArgumentException("Payload may not be larger than 1048576 bytes");
        }
    }

    private void write(FriendlyByteBuf p_296127_) {
        p_296127_.writeVarInt(this.transactionId);
        p_296127_.writeNullable(this.payload, (p_295443_, p_295588_) -> p_295588_.write(p_295443_));
    }

    @Override
    public PacketType<ServerboundCustomQueryAnswerPacket> type() {
        return LoginPacketTypes.SERVERBOUND_CUSTOM_QUERY_ANSWER;
    }

    public void handle(ServerLoginPacketListener p_294750_) {
        p_294750_.handleCustomQueryPacket(this);
    }

    // Paper start - MC Utils - default query payloads
    public record QueryAnswerPayload(ByteBuf buffer) implements CustomQueryAnswerPayload {

        @Override
        public void write(final FriendlyByteBuf buf) {
            buf.writeBytes(buf.slice());
        }
    }
    // Paper end - MC Utils - default query payloads
}
