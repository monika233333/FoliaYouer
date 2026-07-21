package net.minecraft.server.network;

import com.mohistmc.youer.util.ThreadUtils;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nullable;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundServerLinksPacket;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.configuration.ClientboundUpdateEnabledFeaturesPacket;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ServerboundSelectKnownPacks;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.config.JoinWorldTask;
import net.minecraft.server.network.config.ServerResourcePackConfigurationTask;
import net.minecraft.server.network.config.SynchronizeRegistriesTask;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.flag.FeatureFlags;
import org.bukkit.craftbukkit.CraftServerLinks;
import org.slf4j.Logger;

public class ServerConfigurationPacketListenerImpl extends ServerCommonPacketListenerImpl implements ServerConfigurationPacketListener, TickablePacketListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component DISCONNECT_REASON_INVALID_DATA = Component.translatable("multiplayer.disconnect.invalid_player_data");
    private final GameProfile gameProfile;
    private final Queue<ConfigurationTask> configurationTasks = new ConcurrentLinkedQueue<>();
    @Nullable
    private ConfigurationTask currentTask;
    private ClientInformation clientInformation;
    @Nullable
    private SynchronizeRegistriesTask synchronizeRegistriesTask;
    public boolean switchToMain = false; // Folia - region threading - rewrite login process

    public ServerConfigurationPacketListenerImpl(MinecraftServer p_294645_, Connection p_295787_, CommonListenerCookie p_302003_) {
        super(p_294645_, p_295787_, p_302003_);
        this.gameProfile = p_302003_.gameProfile();
        this.clientInformation = p_302003_.clientInformation();
    }

    @Override
    protected GameProfile playerProfile() {
        return this.gameProfile;
    }

    @Override
    public void onDisconnect(DisconnectionDetails p_350569_) {
        LOGGER.info("{} lost connection: {}", this.gameProfile, p_350569_.reason().getString());
        super.onDisconnect(p_350569_);
    }

    @Override
    public boolean isAcceptingMessages() {
        return this.connection.isConnected();
    }

    public void startConfiguration() {
        // Neo: Before starting vanilla configuration, reset ad-hoc channels and run modded channel negotiation.
        this.send(new net.neoforged.neoforge.network.payload.MinecraftUnregisterPayload(net.neoforged.neoforge.network.registration.NetworkRegistry.getInitialServerUnregisterChannels()));
        this.send(new net.neoforged.neoforge.network.payload.MinecraftRegisterPayload(net.neoforged.neoforge.network.registration.NetworkRegistry.getInitialListeningChannels(this.flow())));
        this.send(new net.neoforged.neoforge.network.payload.ModdedNetworkQueryPayload(java.util.Map.of()));
        this.send(new net.minecraft.network.protocol.common.ClientboundPingPacket(0));
    }

    // Neo: Hide vanilla's startConfiguration() in this method so we can call it in handlePong below.
    private void runConfiguration() {
        this.send(new ClientboundCustomPayloadPacket(new BrandPayload(this.server.getServerModName())));
        ServerLinks serverlinks = this.server.serverLinks();
        // CraftBukkit start
        CraftServerLinks wrapper = new CraftServerLinks(serverlinks);
        org.bukkit.event.player.PlayerLinksSendEvent event = new org.bukkit.event.player.PlayerLinksSendEvent(player.getBukkitEntity(), wrapper);
        player.getBukkitEntity().getServer().getPluginManager().callEvent(event);
        serverlinks = wrapper.getServerLinks();
        // CraftBukkit end
        if (!serverlinks.isEmpty()) {
            this.send(new ClientboundServerLinksPacket(serverlinks.untrust()));
        }

        LayeredRegistryAccess<RegistryLayer> layeredregistryaccess = this.server.registries();
        List<KnownPack> list = this.server.getResourceManager().listPacks().flatMap(p_325637_ -> p_325637_.location().knownPackInfo().stream()).toList();
        this.send(new ClientboundUpdateEnabledFeaturesPacket(FeatureFlags.REGISTRY.toNames(this.server.getWorldData().enabledFeatures())));
        // Neo: we must sync the registries before vanilla sends tags in SynchronizeRegistriesTask!
        net.neoforged.neoforge.network.ConfigurationInitialization.configureEarlyTasks(this, this.configurationTasks::add);
        this.synchronizeRegistriesTask = new SynchronizeRegistriesTask(list, layeredregistryaccess);
        this.configurationTasks.add(this.synchronizeRegistriesTask);
        this.addOptionalTasks();
        this.configurationTasks.add(new JoinWorldTask());
        this.startNextTask();
    }

    public void returnToWorld() {
        this.configurationTasks.add(new JoinWorldTask());
        this.startNextTask();
    }

    private void addOptionalTasks() {
        this.server.getServerResourcePack().ifPresent(p_296496_ -> this.configurationTasks.add(new ServerResourcePackConfigurationTask(p_296496_)));
        // Neo: Gather modded configuration tasks and schedule them for execution
        this.configurationTasks.addAll(net.neoforged.fml.ModLoader.postEventWithReturn(new net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent(this)).getConfigurationTasks());
    }

    @Override
    public void handleCustomPayload(net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket p_294276_) {
        // Neo: Perform modded network initialization when the client sends their channel list.
        if (p_294276_.payload() instanceof net.neoforged.neoforge.network.payload.ModdedNetworkQueryPayload moddedEnvironmentPayload) {
            this.connectionType = net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE;
            net.neoforged.neoforge.network.registration.NetworkRegistry.initializeNeoForgeConnection(this, moddedEnvironmentPayload.queries());
            return;
        }

        super.handleCustomPayload(p_294276_); // Neo: Call super to invoke modded payload handling.
    }

    @Override
    public void handlePong(net.minecraft.network.protocol.common.ServerboundPongPacket p_295142_) {
        super.handlePong(p_295142_);
        // During startConfiguration() we send a ping with id 0, if we get a pong back, we initiate the connection.
        if (p_295142_.getId() == 0) {
            if (!this.connectionType.isNeoForge() && !net.neoforged.neoforge.network.registration.NetworkRegistry.initializeOtherConnection(this)) {
                return;
            }
            ThreadUtils.executeOnMainThread(this::runConfiguration);
        }
    }

    @Override
    public void handleClientInformation(ServerboundClientInformationPacket p_302032_) {
        this.clientInformation = p_302032_.information();
        this.connection.channel.attr(io.papermc.paper.adventure.PaperAdventure.LOCALE_ATTRIBUTE).set(net.kyori.adventure.translation.Translator.parseLocale(p_302032_.information().language())); // Paper
    }

    @Override
    public void handleResourcePackResponse(ServerboundResourcePackPacket p_294250_) {
        super.handleResourcePackResponse(p_294250_);
        if (p_294250_.action().isTerminal()) {
            this.finishCurrentTask(ServerResourcePackConfigurationTask.TYPE);
        }
    }

    @Override
    public void handleSelectKnownPacks(ServerboundSelectKnownPacks p_326180_) {
        PacketUtils.ensureRunningOnSameThread(p_326180_, this, this.server);
        if (this.synchronizeRegistriesTask == null) {
            throw new IllegalStateException("Unexpected response from client: received pack selection, but no negotiation ongoing");
        } else {
            this.synchronizeRegistriesTask.handleResponse(p_326180_.knownPacks(), this::send);
            this.finishCurrentTask(SynchronizeRegistriesTask.TYPE);
        }
    }

    @Override
    public void handleConfigurationFinished(ServerboundFinishConfigurationPacket p_294283_) {
        PacketUtils.ensureRunningOnSameThread(p_294283_, this, this.server);
        this.finishCurrentTask(JoinWorldTask.TYPE);
        this.connection.setupOutboundProtocol(GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(this.server.registryAccess(), this.connectionType)));
        // Packets can only be sent after the outbound protocol is set up again
        if (this.connectionType == net.neoforged.neoforge.network.connection.ConnectionType.OTHER) {
            //We need to also initialize this here, as the client may have sent the packet before we have finished our configuration.
            net.neoforged.neoforge.network.registration.NetworkRegistry.initializeNeoForgeConnection(this, java.util.Map.of());
        }
        net.neoforged.neoforge.network.registration.NetworkRegistry.onConfigurationFinished(this);

        try {
            PlayerList playerlist = this.server.getPlayerList();
            if (playerlist.getPlayer(this.gameProfile.getId()) != null) {
                this.disconnect(PlayerList.DUPLICATE_LOGIN_DISCONNECT_MESSAGE);
                return;
            }

            Component component = null; // CraftBukkit - login checks already completed
            if (component != null) {
                this.disconnect(component);
                return;
            }

            playerlist.getPlayerForLogin$player = this.player;
            ServerPlayer serverplayer = playerlist.getPlayerForLogin(this.gameProfile, this.clientInformation); // CraftBukkit

            // Folia start - region threading - rewrite login process
            io.papermc.paper.threadedregions.RegionizedServer.ensureGlobalTickThread("Cannot handle player login off global tick thread");
            CommonListenerCookie clientData = this.createCookie(this.clientInformation, this.connectionType); // Youer - NeoForge 2-arg createCookie
            org.apache.commons.lang3.mutable.MutableObject<net.minecraft.nbt.CompoundTag> data = new org.apache.commons.lang3.mutable.MutableObject<>();
            org.apache.commons.lang3.mutable.MutableObject<String> lastKnownName = new org.apache.commons.lang3.mutable.MutableObject<>();
            ca.spottedleaf.concurrentutil.completable.Completable<org.bukkit.Location> toComplete = new ca.spottedleaf.concurrentutil.completable.Completable<>();
            // note: need to call addWaiter before completion to ensure the callback is invoked synchronously
            // the loadSpawnForNewPlayer function always completes the completable once the chunks were loaded,
            // on the load callback for those chunks (so on the same region)
            // this guarantees the chunk cannot unload under our feet
            toComplete.addWaiter((org.bukkit.Location loc, Throwable t) -> {
                int chunkX = net.minecraft.util.Mth.floor(loc.getX()) >> 4;
                int chunkZ = net.minecraft.util.Mth.floor(loc.getZ()) >> 4;

                net.minecraft.server.level.ServerLevel world = ((org.bukkit.craftbukkit.CraftWorld)loc.getWorld()).getHandle();
                // we just need to hold the chunks at loaded until the next tick
                // so we do not need to care about unique IDs for the ticket
                world.getChunkSource().addTicketAtLevel(
                    net.minecraft.server.level.TicketType.START,
                    new net.minecraft.world.level.ChunkPos(chunkX, chunkZ),
                    ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager.FULL_LOADED_TICKET_LEVEL,
                    net.minecraft.util.Unit.INSTANCE
                );

                io.papermc.paper.threadedregions.RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                    world, chunkX, chunkZ,
                    () -> {
                        // once switchToMain is set, the current ticking region now owns the connection and is responsible
                        // for cleaning it up
                        playerlist.placeNewPlayer(
                            ServerConfigurationPacketListenerImpl.this.connection,
                            serverplayer,
                            clientData,
                            java.util.Optional.ofNullable(data.getValue()),
                            lastKnownName.getValue(),
                            loc
                        );
                    },
                    ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.HIGHER
                );
            });
            this.switchToMain = true;
            try {
                // now the connection responsibility is transferred on the region
                playerlist.loadSpawnForNewPlayer(this.connection, serverplayer, clientData, data, lastKnownName, toComplete);
            } catch (final Throwable throwable) {
                // assume toComplete will not be invoked
                // ensure global tick thread owns the connection again, to properly disconnect it
                this.switchToMain = false;
                throw new RuntimeException(throwable);
            }
            // Folia end - region threading - rewrite login process
        } catch (Exception exception) {
            LOGGER.error("Couldn't place player in world", (Throwable)exception);
            this.connection.send(new ClientboundDisconnectPacket(DISCONNECT_REASON_INVALID_DATA));
            this.connection.disconnect(DISCONNECT_REASON_INVALID_DATA);
        }
    }

    @Override
    public void tick() {
        this.keepConnectionAlive();
    }

    private void startNextTask() {
        if (this.currentTask != null) {
            throw new IllegalStateException("Task " + this.currentTask.type().id() + " has not finished yet");
        } else if (this.isAcceptingMessages()) {
            ConfigurationTask configurationtask = this.configurationTasks.poll();
            if (configurationtask != null) {
                this.currentTask = configurationtask;
                configurationtask.start(this::send);
            }
        }
    }

    public void finishCurrentTask(ConfigurationTask.Type p_294853_) {
        ConfigurationTask.Type configurationtask$type = this.currentTask != null ? this.currentTask.type() : null;
        if (!p_294853_.equals(configurationtask$type)) {
            throw new IllegalStateException("Unexpected request for task finish, current task: " + configurationtask$type + ", requested: " + p_294853_);
        } else {
            this.currentTask = null;
            this.startNextTask();
        }
    }
}
