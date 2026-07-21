package net.minecraft.server.network;

import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mohistmc.youer.util.I18n;
import com.mojang.logging.LogUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RateKickingConnection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

public class ServerConnectionListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int READ_TIMEOUT = Integer.parseInt(System.getProperty("neoforge.readTimeout", "30"));
    public static final Supplier<NioEventLoopGroup> SERVER_EVENT_GROUP = Suppliers.memoize(
        () -> new NioEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Server IO #%d").setDaemon(true).setThreadFactory(net.neoforged.fml.util.thread.SidedThreadGroups.SERVER).build())
    );
    public static final Supplier<EpollEventLoopGroup> SERVER_EPOLL_EVENT_GROUP = Suppliers.memoize(
        () -> new EpollEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Epoll Server IO #%d").setDaemon(true).setThreadFactory(net.neoforged.fml.util.thread.SidedThreadGroups.SERVER).build())
    );
    final MinecraftServer server;
    public volatile boolean running;
    private final List<ChannelFuture> channels = Collections.synchronizedList(Lists.newArrayList());
    final List<Connection> connections = Collections.synchronizedList(Lists.newArrayList());
    // Paper start - prevent blocking on adding a new connection while the server is ticking
    private final java.util.Queue<Connection> pending = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final void addPending() {
        Connection connection;
        while ((connection = pending.poll()) != null) {
            connections.add(connection);
        }
    }
    // Paper end - prevent blocking on adding a new connection while the server is ticking

    public ServerConnectionListener(MinecraftServer p_9707_) {
        this.server = p_9707_;
        this.running = true;
    }

    // Paper start - Unix domain socket support
    public void startTcpServerListener(@Nullable InetAddress address, int port) throws IOException {
        if (address == null) address = new java.net.InetSocketAddress(port).getAddress();
        net.neoforged.neoforge.network.DualStackUtils.checkIPv6(address);
        bind(new java.net.InetSocketAddress(address, port));
    }
    public void bind(java.net.SocketAddress address) throws IOException {
        // Paper end - Unix domain socket support
        synchronized (this.channels) {
            Class oclass;
            EventLoopGroup eventloopgroup;
            if (Epoll.isAvailable() && this.server.isEpollEnabled()) {
                // Paper start - Unix domain socket support
                if (address instanceof io.netty.channel.unix.DomainSocketAddress) {
                    oclass = io.netty.channel.epoll.EpollServerDomainSocketChannel.class;
                } else {
                    oclass = EpollServerSocketChannel.class;
                }
                // Paper end - Unix domain socket support
                eventloopgroup = SERVER_EPOLL_EVENT_GROUP.get();
                LOGGER.info(I18n.as("serverconnectionlistener.1"));
            } else {
                oclass = NioServerSocketChannel.class;
                eventloopgroup = SERVER_EVENT_GROUP.get();
                LOGGER.info(I18n.as("serverconnectionlistener.2"));
            }

            // Paper start - Warn people with console access that HAProxy is in use.
            if (io.papermc.paper.configuration.GlobalConfiguration.get().proxies.proxyProtocol) {
                ServerConnectionListener.LOGGER.warn("Using HAProxy, please ensure the server port is adequately firewalled.");
            }
            // Paper end - Warn people with console access that HAProxy is in use.

            // Paper start - Use Velocity cipher
            ServerConnectionListener.LOGGER.info("Paper: Using " + com.velocitypowered.natives.util.Natives.compress.getLoadedVariant() + " compression from Velocity.");
            ServerConnectionListener.LOGGER.info("Paper: Using " + com.velocitypowered.natives.util.Natives.cipher.getLoadedVariant() + " cipher from Velocity.");
            // Paper end - Use Velocity cipher

            this.channels.add(new ServerBootstrap().channel(oclass).childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel p_9729_) {
                    try {
                        p_9729_.config().setOption(ChannelOption.TCP_NODELAY, true);
                    } catch (ChannelException channelexception) {
                    }

                    ChannelPipeline channelpipeline = p_9729_.pipeline().addLast("timeout", new ReadTimeoutHandler(READ_TIMEOUT));
                    if (ServerConnectionListener.this.server.repliesToStatus()) {
                        channelpipeline.addLast("legacy_query", new LegacyQueryHandler(ServerConnectionListener.this.getServer()));
                    }

                    Connection.configureSerialization(channelpipeline, PacketFlow.SERVERBOUND, false, null);
                    int i = ServerConnectionListener.this.server.getRateLimitPacketsPerSecond();
                    Connection connection = (Connection)(i > 0 ? new RateKickingConnection(i) : new Connection(PacketFlow.SERVERBOUND));
                    // Paper start - Add support for Proxy Protocol
                    if (io.papermc.paper.configuration.GlobalConfiguration.get().proxies.proxyProtocol) {
                        p_9729_.pipeline().addAfter("timeout", "haproxy-decoder", new io.netty.handler.codec.haproxy.HAProxyMessageDecoder());
                        p_9729_.pipeline().addAfter("haproxy-decoder", "haproxy-handler", new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                if (msg instanceof io.netty.handler.codec.haproxy.HAProxyMessage message) {
                                    if (message.command() == io.netty.handler.codec.haproxy.HAProxyCommand.PROXY) {
                                        String realaddress = message.sourceAddress();
                                        int realport = message.sourcePort();

                                        SocketAddress socketaddr = new java.net.InetSocketAddress(realaddress, realport);

                                        Connection connection = (Connection) p_9729_.pipeline().get("packet_handler");
                                        connection.address = socketaddr;

                                        // Paper start - Add API to get player's proxy address
                                        final String proxyAddress = message.destinationAddress();
                                        final int proxyPort = message.destinationPort();

                                        connection.haProxyAddress = new java.net.InetSocketAddress(proxyAddress, proxyPort);
                                        // Paper end - Add API to get player's proxy address
                                    }
                                } else {
                                    super.channelRead(ctx, msg);
                                }
                            }
                        });
                    }
                    // Paper end - Add support for proxy protocol
                    pending.add(connection); // Paper - prevent blocking on adding a new connection while the server is ticking
                    // ServerConnectionListener.this.connections.add(connection); // Paper
                    connection.configurePacketHandler(channelpipeline);
                    connection.setListenerForServerboundHandshake(new ServerHandshakePacketListenerImpl(ServerConnectionListener.this.server, connection));
                    io.papermc.paper.network.ChannelInitializeListenerHolder.callListeners(p_9729_); // Paper - Add Channel initialization listeners
                }
            }).group(eventloopgroup).localAddress(address).option(ChannelOption.AUTO_READ, false).bind().syncUninterruptibly()); // CraftBukkit // Paper - Unix domain socket support
        }
    }

    public SocketAddress startMemoryChannel() {
        ChannelFuture channelfuture;
        synchronized (this.channels) {
            channelfuture = new ServerBootstrap()
                .channel(LocalServerChannel.class)
                .childHandler(
                    new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel p_9734_) {
                            Connection connection = new Connection(PacketFlow.SERVERBOUND);
                            connection.setListenerForServerboundHandshake(
                                new MemoryServerHandshakePacketListenerImpl(ServerConnectionListener.this.server, connection)
                            );
                            ServerConnectionListener.this.connections.add(connection);
                            ChannelPipeline channelpipeline = p_9734_.pipeline();
                            Connection.configureInMemoryPipeline(channelpipeline, PacketFlow.SERVERBOUND);
                            connection.configurePacketHandler(channelpipeline);
                        }
                    }
                )
                .group(SERVER_EVENT_GROUP.get())
                .localAddress(LocalAddress.ANY)
                .bind()
                .syncUninterruptibly();
            this.channels.add(channelfuture);
        }

        return channelfuture.channel().localAddress();
    }

    public void stop() {
        this.running = false;

        for (ChannelFuture channelfuture : this.channels) {
            try {
                channelfuture.channel().close().sync();
            } catch (InterruptedException interruptedexception) {
                LOGGER.error("Interrupted whilst closing channel");
            }
        }
    }

    public void tick() {
        synchronized (this.connections) {
            // Spigot Start
            this.addPending(); // Paper - prevent blocking on adding a new connection while the server is ticking
            // This prevents players from 'gaming' the server, and strategically relogging to increase their position in the tick order
            if ( org.spigotmc.SpigotConfig.playerShuffle > 0 && MinecraftServer.currentTick % org.spigotmc.SpigotConfig.playerShuffle == 0 )
            {
                Collections.shuffle( this.connections );
            }
            // Spigot End
            Iterator<Connection> iterator = this.connections.iterator();

            while (iterator.hasNext()) {
                Connection connection = iterator.next();
                if (!connection.isConnecting()) {
                    if (connection.isConnected()) {
                        try {
                            connection.tick();
                        } catch (Exception exception) {
                            if (connection.isMemoryConnection()) {
                                throw new ReportedException(CrashReport.forThrowable(exception, "Ticking memory connection"));
                            }

                            LOGGER.warn("Failed to handle packet for {}", connection.getLoggableAddress(this.server.logIPs()), exception);
                            Component component = Component.literal("Internal server error");
                            connection.send(new ClientboundDisconnectPacket(component), PacketSendListener.thenRun(() -> connection.disconnect(component)));
                            connection.setReadOnly();
                        }
                    } else {
                        iterator.remove();
                        connection.handleDisconnection();
                    }
                }
            }
        }
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public List<Connection> getConnections() {
        return this.connections;
    }

    // CraftBukkit start
    public void acceptConnections() {
        synchronized (this.channels) {
            for (final ChannelFuture future : this.channels) {
                future.channel().config().setAutoRead(true);
            }
        }
    }
    // CraftBukkit end

    static class LatencySimulator extends ChannelInboundHandlerAdapter {
        private static final Timer TIMER = new HashedWheelTimer();
        private final int delay;
        private final int jitter;
        private final List<ServerConnectionListener.LatencySimulator.DelayedMessage> queuedMessages = Lists.newArrayList();

        public LatencySimulator(int p_143593_, int p_143594_) {
            this.delay = p_143593_;
            this.jitter = p_143594_;
        }

        @Override
        public void channelRead(ChannelHandlerContext p_143601_, Object p_143602_) {
            this.delayDownstream(p_143601_, p_143602_);
        }

        private void delayDownstream(ChannelHandlerContext p_143596_, Object p_143597_) {
            int i = this.delay + (int)(Math.random() * (double)this.jitter);
            this.queuedMessages.add(new ServerConnectionListener.LatencySimulator.DelayedMessage(p_143596_, p_143597_));
            TIMER.newTimeout(this::onTimeout, (long)i, TimeUnit.MILLISECONDS);
        }

        private void onTimeout(Timeout p_143599_) {
            ServerConnectionListener.LatencySimulator.DelayedMessage serverconnectionlistener$latencysimulator$delayedmessage = this.queuedMessages.remove(0);
            serverconnectionlistener$latencysimulator$delayedmessage.ctx.fireChannelRead(serverconnectionlistener$latencysimulator$delayedmessage.msg);
        }

        static class DelayedMessage {
            public final ChannelHandlerContext ctx;
            public final Object msg;

            public DelayedMessage(ChannelHandlerContext p_143606_, Object p_143607_) {
                this.ctx = p_143606_;
                this.msg = p_143607_;
            }
        }
    }
}
