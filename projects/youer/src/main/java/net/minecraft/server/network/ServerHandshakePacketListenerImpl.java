package net.minecraft.server.network;

import com.destroystokyo.paper.network.PaperNetworkClient;
import com.mohistmc.youer.api.ColorAPI;
import java.net.InetAddress;
import java.util.HashMap;
import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.handshake.ServerHandshakePacketListener;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.network.protocol.status.StatusProtocols;
import net.minecraft.server.MinecraftServer;

public class ServerHandshakePacketListenerImpl implements ServerHandshakePacketListener {
    private static final Component IGNORE_STATUS_REASON = Component.translatable("disconnect.ignoring_status_request");
    private final MinecraftServer server;
    private final Connection connection;
    // Spigot start
    private static final com.google.gson.Gson gson = new com.google.gson.Gson();
    static final java.util.regex.Pattern HOST_PATTERN = java.util.regex.Pattern.compile("[0-9a-f\\.:]{0,45}");
    static final java.util.regex.Pattern PROP_PATTERN = java.util.regex.Pattern.compile("\\w{0,16}");
    // Spigot end
    // CraftBukkit start - add fields
    private static final HashMap<InetAddress, Long> throttleTracker = new HashMap<InetAddress, Long>();
    private static int throttleCounter = 0;
    // CraftBukkit end
    private static final boolean BYPASS_HOSTCHECK = Boolean.getBoolean("Paper.bypassHostCheck"); // Paper

    public ServerHandshakePacketListenerImpl(MinecraftServer p_9969_, Connection p_9970_) {
        this.server = p_9969_;
        this.connection = p_9970_;
    }

    @Override
    public void handleIntention(ClientIntentionPacket p_9975_) {
        this.connection.hostname = p_9975_.hostName() + ":" + p_9975_.port(); // CraftBukkit  - set hostname
        switch (p_9975_.intention()) {
            case LOGIN:
                this.beginLogin(p_9975_, false);
                break;
            case STATUS:
                ServerStatus serverstatus = this.server.getStatus();
                this.connection.setupOutboundProtocol(StatusProtocols.CLIENTBOUND);
                if (this.server.repliesToStatus() && serverstatus != null) {
                    this.connection.setupInboundProtocol(StatusProtocols.SERVERBOUND, new ServerStatusPacketListenerImpl(serverstatus, this.connection));
                } else {
                    this.connection.disconnect(IGNORE_STATUS_REASON);
                }
                break;
            case TRANSFER:
                if (!this.server.acceptsTransfers()) {
                    this.connection.setupOutboundProtocol(LoginProtocols.CLIENTBOUND);
                    Component component = Component.translatable("multiplayer.disconnect.transfers_disabled");
                    this.connection.send(new ClientboundLoginDisconnectPacket(component));
                    this.connection.disconnect(component);
                } else {
                    this.beginLogin(p_9975_, true);
                }
                break;
            default:
                throw new UnsupportedOperationException("Invalid intention " + p_9975_.intention());
        }

        // Paper start - NetworkClient implementation
        this.connection.protocolVersion = p_9975_.protocolVersion();
        this.connection.virtualHost = PaperNetworkClient.prepareVirtualHost(p_9975_.hostName(), p_9975_.port());
        // Paper end
    }

    private void beginLogin(ClientIntentionPacket p_320524_, boolean p_320853_) {
        this.connection.setupOutboundProtocol(LoginProtocols.CLIENTBOUND);
        // CraftBukkit start - Connection throttle
        try {
            if (!(this.connection.channel.localAddress() instanceof io.netty.channel.unix.DomainSocketAddress)) { // Paper - Unix domain socket support; the connection throttle is useless when you have a Unix domain socket
                long currentTime = System.currentTimeMillis();
                long connectionThrottle = this.server.server.getConnectionThrottle();
                InetAddress address = ((java.net.InetSocketAddress) this.connection.getRemoteAddress()).getAddress();

                synchronized (throttleTracker) {
                    if (throttleTracker.containsKey(address) && !"127.0.0.1".equals(address.getHostAddress()) && currentTime - throttleTracker.get(address) < connectionThrottle) {
                        throttleTracker.put(address, currentTime);
                        net.minecraft.network.chat.Component chatmessage = io.papermc.paper.adventure.PaperAdventure.asVanilla(io.papermc.paper.configuration.GlobalConfiguration.get().messages.kick.connectionThrottle);
                        this.connection.send(new ClientboundLoginDisconnectPacket(chatmessage));
                        this.connection.disconnect(chatmessage);
                        return;
                    }

                    throttleTracker.put(address, currentTime);
                    throttleCounter++;
                    if (throttleCounter > 200) {
                        throttleCounter = 0;

                        // Cleanup stale entries
                        throttleTracker.entrySet().removeIf(entry -> entry.getValue() > connectionThrottle);
                    }
                }
            } // Paper - Unix domain socket support
        } catch (Throwable t) {
            org.apache.logging.log4j.LogManager.getLogger().debug("Failed to check connection throttle", t);
        }
        // CraftBukkit end
        if (p_320524_.protocolVersion() != SharedConstants.getCurrentVersion().getProtocolVersion()) {
            net.kyori.adventure.text.Component adventureComponent; // Paper - Fix hex colors not working in some kick messages
            if (p_320524_.protocolVersion() < SharedConstants.getCurrentVersion().getProtocolVersion()) { // Spigot - SPIGOT-7546: Handle version check correctly for outdated client message
                adventureComponent = ColorAPI.adventure(java.text.MessageFormat.format(org.spigotmc.SpigotConfig.outdatedClientMessage.replaceAll("'", "''"), SharedConstants.getCurrentVersion().getName())); // Spigot // Paper - Fix hex colors not working in some kick messages
            } else {
                adventureComponent = ColorAPI.adventure(java.text.MessageFormat.format(org.spigotmc.SpigotConfig.outdatedServerMessage.replaceAll("'", "''"), SharedConstants.getCurrentVersion().getName())); // Spigot // Paper - Fix hex colors not working in some kick messages
            }
            Component ichatmutablecomponent = io.papermc.paper.adventure.PaperAdventure.asVanilla(adventureComponent); // Paper - Fix hex colors not working in some kick messages
            this.connection.send(new ClientboundLoginDisconnectPacket(ichatmutablecomponent));
            this.connection.disconnect(ichatmutablecomponent);
        } else {
            this.connection.setupInboundProtocol(LoginProtocols.SERVERBOUND, new ServerLoginPacketListenerImpl(this.server, this.connection, p_320853_));
            // Paper start - PlayerHandshakeEvent
            boolean proxyLogicEnabled = org.spigotmc.SpigotConfig.bungee;
            boolean handledByEvent = false;
            // Try and handle the handshake through the event
            if (com.destroystokyo.paper.event.player.PlayerHandshakeEvent.getHandlerList().getRegisteredListeners().length != 0) { // Hello? Can you hear me?
                java.net.SocketAddress socketAddress = this.connection.address;
                String hostnameOfRemote = socketAddress instanceof java.net.InetSocketAddress ? ((java.net.InetSocketAddress) socketAddress).getHostString() : InetAddress.getLoopbackAddress().getHostAddress();
                com.destroystokyo.paper.event.player.PlayerHandshakeEvent event = new com.destroystokyo.paper.event.player.PlayerHandshakeEvent(p_320524_.hostName(), hostnameOfRemote, !proxyLogicEnabled);
                if (event.callEvent()) {
                    // If we've failed somehow, let the client know so and go no further.
                    if (event.isFailed()) {
                        Component component = io.papermc.paper.adventure.PaperAdventure.asVanilla(event.failMessage());
                        this.connection.send(new ClientboundLoginDisconnectPacket(component));
                        this.connection.disconnect(component);
                        return;
                    }

                    if (event.getServerHostname() != null) {
                        // change hostname
                        p_320524_ = new ClientIntentionPacket(
                                p_320524_.protocolVersion(),
                                event.getServerHostname(),
                                p_320524_.port(),
                                p_320524_.intention()
                        );
                    }
                    if (event.getSocketAddressHostname() != null) this.connection.address = new java.net.InetSocketAddress(event.getSocketAddressHostname(), socketAddress instanceof java.net.InetSocketAddress ? ((java.net.InetSocketAddress) socketAddress).getPort() : 0);
                    this.connection.spoofedUUID = event.getUniqueId();
                    this.connection.spoofedProfile = gson.fromJson(event.getPropertiesJson(), com.mojang.authlib.properties.Property[].class);
                    handledByEvent = true; // Hooray, we did it!
                }
            }
            // Paper end
            // Spigot Start
            String[] split = p_320524_.hostName().split("\00");
            if (!handledByEvent && proxyLogicEnabled) { // Paper
                if ( ( split.length == 3 || split.length == 4 ) && ( BYPASS_HOSTCHECK || HOST_PATTERN.matcher( split[1] ).matches() ) ) { // Paper - Add bypass host check
                    // Paper start - Unix domain socket support
                    java.net.SocketAddress socketAddress = this.connection.getRemoteAddress();
                    this.connection.hostname = split[0];
                    this.connection.address = new java.net.InetSocketAddress(split[1], socketAddress instanceof java.net.InetSocketAddress ? ((java.net.InetSocketAddress) socketAddress).getPort() : 0);
                    // Paper end - Unix domain socket support
                    connection.spoofedUUID = com.mojang.util.UndashedUuid.fromStringLenient( split[2] );
                } else
                {
                    Component chatmessage = Component.literal("If you wish to use IP forwarding, please enable it in your BungeeCord config as well!");
                    this.connection.send(new ClientboundLoginDisconnectPacket(chatmessage));
                    this.connection.disconnect(chatmessage);
                    return;
                }
                if ( split.length == 4 )
                {
                    connection.spoofedProfile = gson.fromJson(split[3], com.mojang.authlib.properties.Property[].class);
                }
            } else if ( ( split.length == 3 || split.length == 4 ) && ( HOST_PATTERN.matcher( split[1] ).matches() ) ) {
                Component chatmessage = Component.literal("Unknown data in login hostname, did you forget to enable BungeeCord in spigot.yml?");
                this.connection.send(new ClientboundLoginDisconnectPacket(chatmessage));
                this.connection.disconnect(chatmessage);
                return;
            }
            // Spigot End
        }
    }

    @Override
    public void onDisconnect(DisconnectionDetails p_350912_) {
    }

    @Override
    public boolean isAcceptingMessages() {
        return this.connection.isConnected();
    }
}
