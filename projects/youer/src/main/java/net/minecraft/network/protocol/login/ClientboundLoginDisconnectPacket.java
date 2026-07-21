package net.minecraft.network.protocol.login;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ClientboundLoginDisconnectPacket implements Packet<ClientLoginPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundLoginDisconnectPacket> STREAM_CODEC = Packet.codec(
        ClientboundLoginDisconnectPacket::write, ClientboundLoginDisconnectPacket::new
    );
    private final Component reason;

    public ClientboundLoginDisconnectPacket(Component p_134812_) {
        this.reason = p_134812_;
    }

    private ClientboundLoginDisconnectPacket(FriendlyByteBuf p_179820_) {
        this.reason = Component.Serializer.fromJsonLenient(p_179820_.readUtf(FriendlyByteBuf.MAX_COMPONENT_STRING_LENGTH), RegistryAccess.EMPTY); // Paper - diff on change
    }

    private void write(FriendlyByteBuf p_134821_) {
        // Paper start - Adventure
        // buf.writeUtf(Component.Serializer.toJson(this.reason, RegistryAccess.EMPTY));
        // In the login phase, buf.adventure$locale field is most likely null, but plugins may use internals to set it via the channel attribute
        java.util.Locale bufLocale = p_134821_.adventure$locale;
        p_134821_.writeJsonWithCodec(net.minecraft.network.chat.ComponentSerialization.localizedCodec(bufLocale == null ? java.util.Locale.US : bufLocale), this.reason, FriendlyByteBuf.MAX_COMPONENT_STRING_LENGTH);
        // Paper end - Adventure
    }

    @Override
    public PacketType<ClientboundLoginDisconnectPacket> type() {
        return LoginPacketTypes.CLIENTBOUND_LOGIN_DISCONNECT;
    }

    public void handle(ClientLoginPacketListener p_134818_) {
        p_134818_.handleDisconnect(this);
    }

    public Component getReason() {
        return this.reason;
    }
}
