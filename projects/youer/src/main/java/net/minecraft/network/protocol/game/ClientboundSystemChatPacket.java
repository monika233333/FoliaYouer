package net.minecraft.network.protocol.game;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import org.bukkit.craftbukkit.util.CraftChatMessage;

public record ClientboundSystemChatPacket(Component content, boolean overlay) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSystemChatPacket> STREAM_CODEC = StreamCodec.composite(
        ComponentSerialization.TRUSTED_STREAM_CODEC,
        ClientboundSystemChatPacket::content,
        ByteBufCodecs.BOOL,
        ClientboundSystemChatPacket::overlay,
        ClientboundSystemChatPacket::new
    );

    // Spigot start
    public ClientboundSystemChatPacket(net.md_5.bungee.api.chat.BaseComponent[] content, boolean overlay) {
        this(CraftChatMessage.fromJSON(net.md_5.bungee.chat.ComponentSerializer.toString(content)), overlay);
    }
    // Spigot end
    // Paper start
    public ClientboundSystemChatPacket(net.kyori.adventure.text.Component content, boolean overlay) {
        this(io.papermc.paper.adventure.PaperAdventure.asVanilla(content), overlay);
    }
    // Paper end

    @Override
    public PacketType<ClientboundSystemChatPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SYSTEM_CHAT;
    }

    public void handle(ClientGamePacketListener p_237864_) {
        p_237864_.handleSystemChat(this);
    }

    @Override
    public boolean isSkippable() {
        return true;
    }
}
