package net.minecraft.server.players;

import com.destroystokyo.paper.console.TerminalConsoleCommandSender;
import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import com.destroystokyo.paper.event.server.WhitelistToggleEvent;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mohistmc.youer.YouerConfig;
import com.mohistmc.youer.api.ColorAPI;
import com.mohistmc.youer.bukkit.inventory.InventoryOwner;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.slf4j.Logger;

public abstract class PlayerList {
    public static final File USERBANLIST_FILE = new File("banned-players.json");
    public static final File IPBANLIST_FILE = new File("banned-ips.json");
    public static final File OPLIST_FILE = new File("ops.json");
    public static final File WHITELIST_FILE = new File("whitelist.json");
    public static final Component CHAT_FILTERED_FULL = Component.translatable("chat.filtered_full");
    public static final Component DUPLICATE_LOGIN_DISCONNECT_MESSAGE = Component.translatable("multiplayer.disconnect.duplicate_login");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SEND_PLAYER_INFO_INTERVAL = 600;
    private static final SimpleDateFormat BAN_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
    private final MinecraftServer server;
    public final List<ServerPlayer> players = Lists.newArrayList();
    private final Map<UUID, ServerPlayer> playersByUUID = Maps.newHashMap();
    private final UserBanList bans = new UserBanList(USERBANLIST_FILE);
    private final IpBanList ipBans = new IpBanList(IPBANLIST_FILE);
    private final ServerOpList ops = new ServerOpList(OPLIST_FILE);
    private final UserWhiteList whitelist = new UserWhiteList(WHITELIST_FILE);
    private final Map<UUID, ServerStatsCounter> stats = Maps.newHashMap();
    private final Map<UUID, PlayerAdvancements> advancements = Maps.newHashMap();
    public final PlayerDataStorage playerIo;
    private boolean doWhiteList;
    private final LayeredRegistryAccess<RegistryLayer> registries;
    public int maxPlayers;
    private int viewDistance;
    private int simulationDistance;
    private boolean allowCommandsForAllPlayers;
    private static final boolean ALLOW_LOGOUTIVATOR = false;
    private int sendAllPlayerInfoIn;
    private final List<ServerPlayer> playersView = java.util.Collections.unmodifiableList(players);

    // CraftBukkit start
    private CraftServer cserver;
    private final Map<String,ServerPlayer> playersByName = new java.util.HashMap<>();
    public @Nullable String collideRuleTeamName; // Paper - Configurable player collision

    public PlayerList(MinecraftServer p_203842_, LayeredRegistryAccess<RegistryLayer> p_251844_, PlayerDataStorage p_203844_, int p_203845_) {
        this.cserver = p_203842_.server = new CraftServer((DedicatedServer) p_203842_, this);
        p_203842_.console = new TerminalConsoleCommandSender(); // Paper
        // CraftBukkit end
        this.server = p_203842_;
        this.registries = p_251844_;
        this.maxPlayers = p_203845_;
        this.playerIo = p_203844_;
    }
    public void loadAndSaveFiles() {}; // Paper - fix converting txt to json file; moved from DedicatedPlayerList constructor

    public void loadSpawnForNewPlayer(final Connection connection, final ServerPlayer player, final CommonListenerCookie clientData, org.apache.commons.lang3.mutable.MutableObject<CompoundTag> data, org.apache.commons.lang3.mutable.MutableObject<String> lastKnownName, ca.spottedleaf.concurrentutil.completable.Completable<org.bukkit.Location> toComplete) { // Folia - region threading - rewrite login process
        player.isRealPlayer = true; // Paper
        player.loginTime = System.currentTimeMillis(); // Paper - Replace OfflinePlayer#getLastPlayed
        GameProfile gameprofile = player.getGameProfile();
        GameProfileCache gameprofilecache = this.server.getProfileCache();
        String s;
        if (gameprofilecache != null) {
            Optional<GameProfile> optional = gameprofilecache.get(gameprofile.getId());
            s = optional.map(GameProfile::getName).orElse(gameprofile.getName());
            gameprofilecache.add(gameprofile);
        } else {
            s = gameprofile.getName();
        }

        Optional<CompoundTag> optional1 = this.load(player);
        // CraftBukkit start - Better rename detection
        if (optional1.isPresent()) {
            CompoundTag nbttagcompound = optional1.get();
            if (nbttagcompound.contains("bukkit")) {
                CompoundTag bukkit = nbttagcompound.getCompound("bukkit");
                s = bukkit.contains("lastKnownName", 8) ? bukkit.getString("lastKnownName") : s;
            }
        }
        // CraftBukkit end
        ResourceKey<Level> resourcekey = optional1.<ResourceKey<Level>>flatMap(
                p_337568_ -> DimensionType.parseLegacy(new Dynamic<>(NbtOps.INSTANCE, p_337568_.get("Dimension"))).resultOrPartial(LOGGER::error)
            )
            .orElse(player.serverLevel().dimension());
        ServerLevel serverlevel = this.server.getLevel(resourcekey);
        ServerLevel serverlevel1;
        if (serverlevel == null) {
            LOGGER.warn("Unknown respawn dimension {}, defaulting to overworld", resourcekey);
            serverlevel1 = this.server.overworld();
        } else {
            serverlevel1 = serverlevel;
        }
        if (optional1.isEmpty() ) {
            player.spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT; // set Player SpawnReason to DEFAULT on first login
            // Paper end - reset to main world spawn if first spawn or invalid world
        }
        // Folia start - region threading - rewrite login process
        // must write to these before toComplete is invoked
        data.setValue(optional1.orElse(null));
        lastKnownName.setValue(s);
        // Folia end - region threading - rewrite login process
        if (optional1.isEmpty()) {
            // Paper end - reset to main world spawn if first spawn or invalid world
            // FoliaYouer start - inline fudgeSpawnLocation (not available in Youer)
            net.minecraft.core.BlockPos spawnPos = serverlevel1.getSharedSpawnPos();
            serverlevel1.loadChunksForMoveAsync(
                player.getBoundingBoxAt(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5),
                ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.HIGHER,
                (c) -> {
                    player.moveTo(player.adjustSpawnLocation(serverlevel1, spawnPos).getBottomCenter(), serverlevel1.getSharedSpawnAngle(), 0.0F);
                    toComplete.complete(io.papermc.paper.util.MCUtil.toLocation(serverlevel1, player.blockPosition()));
                }
            );
            // FoliaYouer end
        } else {
            serverlevel1.loadChunksForMoveAsync(
                player.getBoundingBox(),
                ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.HIGHER,
                (c) -> {
                    toComplete.complete(io.papermc.paper.util.MCUtil.toLocation(serverlevel1, player.blockPosition()));
                }
            );
        }
        // Folia end - region threading - rewrite login process
    }
    // FoliaYouer start - keep original 3-arg signature for mod mixin compatibility
    public void placeNewPlayer(Connection connection, ServerPlayer player, CommonListenerCookie clientData) {
        LOGGER.info("[FoliaYouer-Debug] placeNewPlayer(3-arg) called on thread: " + Thread.currentThread().getName());
        // balm mixin @Inject targets ClientboundPlayerAbilitiesPacket <init> inside this method
        // keep the call here so mixin can find its injection point
        new net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket(player.getAbilities());
        // delegate to Folia's async region-threaded login
        org.apache.commons.lang3.mutable.MutableObject<net.minecraft.nbt.CompoundTag> data = new org.apache.commons.lang3.mutable.MutableObject<>();
        org.apache.commons.lang3.mutable.MutableObject<String> lastKnownName = new org.apache.commons.lang3.mutable.MutableObject<>();
        ca.spottedleaf.concurrentutil.completable.Completable<org.bukkit.Location> toComplete = new ca.spottedleaf.concurrentutil.completable.Completable<>();
        CommonListenerCookie cookie = clientData;
        toComplete.addWaiter((org.bukkit.Location loc, Throwable t) -> {
            int chunkX = net.minecraft.util.Mth.floor(loc.getX()) >> 4;
            int chunkZ = net.minecraft.util.Mth.floor(loc.getZ()) >> 4;
            net.minecraft.server.level.ServerLevel world = ((org.bukkit.craftbukkit.CraftWorld)loc.getWorld()).getHandle();
            world.getChunkSource().addTicketAtLevel(
                net.minecraft.server.level.TicketType.START,
                new net.minecraft.world.level.ChunkPos(chunkX, chunkZ),
                ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager.FULL_LOADED_TICKET_LEVEL,
                net.minecraft.util.Unit.INSTANCE
            );
            io.papermc.paper.threadedregions.RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                world, chunkX, chunkZ,
                () -> placeNewPlayerInternal(connection, player, cookie, java.util.Optional.ofNullable(data.getValue()), lastKnownName.getValue(), loc),
                ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.HIGHER
            );
        });
        loadSpawnForNewPlayer(connection, player, clientData, data, lastKnownName, toComplete);
    }
    // FoliaYouer end
    // optional -> player data
    // s -> last known name
    public void placeNewPlayerInternal(Connection connection, ServerPlayer player, CommonListenerCookie clientData, Optional<CompoundTag> optional, String s, org.bukkit.Location selectedSpawn) { // Folia - region threading - rewrite login process
        ServerLevel serverlevel1 = ((org.bukkit.craftbukkit.CraftWorld)selectedSpawn.getWorld()).getHandle();
        player.setPosRaw(selectedSpawn.getX(), selectedSpawn.getY(), selectedSpawn.getZ());
        player.lastSave = System.nanoTime(); // changed to nanoTime
        // Folia end - region threading - rewrite login process
        player.setServerLevel(serverlevel1);
        String s1 = connection.getLoggableAddress(this.server.logIPs());

        // Spigot start - spawn location event
        org.bukkit.entity.Player spawnPlayer = player.getBukkitEntity();
        org.spigotmc.event.player.PlayerSpawnLocationEvent ev = new org.spigotmc.event.player.PlayerSpawnLocationEvent(spawnPlayer, spawnPlayer.getLocation());
        cserver.getPluginManager().callEvent(ev);
        org.bukkit.Location loc = ev.getSpawnLocation();
        serverlevel1 = ((CraftWorld) loc.getWorld()).getHandle();
        player.spawnIn(serverlevel1);
        player.gameMode.setLevel((net.minecraft.server.level.ServerLevel) player.level());
        // Paper start - set raw so we aren't fully joined to the world (not added to chunk or world)
        player.setPosRaw(loc.getX(), loc.getY(), loc.getZ());
        player.setRot(loc.getYaw(), loc.getPitch());
        // Paper end - set raw so we aren't fully joined to the world
        // Spigot end

        LevelData leveldata = serverlevel1.getLevelData();
        player.loadGameTypes(optional.orElse(null));
        ServerGamePacketListenerImpl servergamepacketlistenerimpl = new ServerGamePacketListenerImpl(this.server, connection, player, clientData);
        connection.setupInboundProtocol(
            GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(this.server.registryAccess(), servergamepacketlistenerimpl.getConnectionType())), servergamepacketlistenerimpl
        );
        GameRules gamerules = serverlevel1.getGameRules();
        boolean flag = gamerules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
        boolean flag1 = gamerules.getBoolean(GameRules.RULE_REDUCEDDEBUGINFO);
        boolean flag2 = gamerules.getBoolean(GameRules.RULE_LIMITED_CRAFTING);
        // Spigot - view distance
        servergamepacketlistenerimpl.send(
            new ClientboundLoginPacket(
                player.getId(),
                leveldata.isHardcore(),
                this.server.levelKeys(),
                this.getMaxPlayers(),
                serverlevel1.spigotConfig.viewDistance, // Spigot
                serverlevel1.spigotConfig.simulationDistance, // Spigot
                flag1,
                !flag,
                flag2,
                player.createCommonSpawnInfo(serverlevel1),
                this.server.enforceSecureProfile()
            )
        );
        servergamepacketlistenerimpl.send(new ClientboundChangeDifficultyPacket(leveldata.getDifficulty(), leveldata.isDifficultyLocked()));
        servergamepacketlistenerimpl.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
        servergamepacketlistenerimpl.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.OnDatapackSyncEvent(this, player));
        servergamepacketlistenerimpl.send(new ClientboundUpdateRecipesPacket(this.server.getRecipeManager().getOrderedRecipes()));
        this.sendPlayerPermissionLevel(player);
        player.getStats().markAllDirty();
        player.getRecipeBook().sendInitialRecipeBook(player);
        this.updateEntireScoreboard(serverlevel1.getScoreboard(), player);
        this.server.invalidateStatus();
        MutableComponent mutablecomponent;
        if (player.getGameProfile().getName().equalsIgnoreCase(s)) {
            mutablecomponent = Component.translatable("multiplayer.player.joined", player.getDisplayName());
        } else {
            mutablecomponent = Component.translatable("multiplayer.player.joined.renamed", player.getDisplayName(), s);
        }

        // CraftBukkit start
        mutablecomponent.withStyle(ChatFormatting.YELLOW);
        Component joinMessage = mutablecomponent; // Paper - Adventure

        servergamepacketlistenerimpl.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        ServerStatus serverstatus = this.server.getStatus();
        if (serverstatus != null && !clientData.transferred()) {
            player.sendServerStatus(serverstatus);
        }

        // p_11263_.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(this.players));
        this.players.add(player);
        this.playersByName.put(player.getScoreboardName().toLowerCase(java.util.Locale.ROOT), player); // Spigot
        this.playersByUUID.put(player.getUUID(), player);
        // this.broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(p_11263_)));

        // Paper start - Fire PlayerJoinEvent when Player is actually ready; correctly register player BEFORE PlayerJoinEvent, so the entity is valid and doesn't require tick delay hacks
        player.supressTrackerForLogin = true;
        serverlevel1.addNewPlayer(player);
        this.server.getCustomBossEvents().onPlayerConnect(player); // see commented out section below worldserver.addPlayerJoin(entityplayer);
        this.mountSavedVehicle(player, serverlevel1, optional);
        // Paper end - Fire PlayerJoinEvent when Player is actually ready

        // CraftBukkit start
        CraftPlayer bukkitPlayer = player.getBukkitEntity();
        // Ensure that player inventory is populated with its viewer
        player.containerMenu.transferTo(player.containerMenu, bukkitPlayer);
        PlayerJoinEvent playerJoinEvent = new PlayerJoinEvent(bukkitPlayer, io.papermc.paper.adventure.PaperAdventure.asAdventure(joinMessage)); // Paper - Adventure
        cserver.getPluginManager().callEvent(playerJoinEvent);
        if (!player.connection.isAcceptingMessages()) {
            return;
        }
        final net.kyori.adventure.text.Component jm = playerJoinEvent.joinMessage();
        if (YouerConfig.join_message && jm != null && !jm.equals(net.kyori.adventure.text.Component.empty())) { // Paper - Adventure
            joinMessage = io.papermc.paper.adventure.PaperAdventure.asVanilla(jm); // Paper - Adventure
            this.server.getPlayerList().broadcastSystemMessage(joinMessage, false); // Paper - Adventure
        }
        // CraftBukkit end
        // CraftBukkit start - sendAll above replaced with this loop
        ClientboundPlayerInfoUpdatePacket packet = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player));

        final List<ServerPlayer> onlinePlayers = Lists.newArrayListWithExpectedSize(this.players.size() - 1); // Paper - Use single player info update packet on join
        for (int i = 0; i < this.players.size(); ++i) {
            ServerPlayer entityplayer1 = (ServerPlayer) this.players.get(i);
            if (entityplayer1.getBukkitEntity().canSee(bukkitPlayer)) {
                // Paper start - Add Listing API for Player
                if (entityplayer1.getBukkitEntity().isListed(bukkitPlayer)) {
                    // Paper end - Add Listing API for Player
                    entityplayer1.connection.send(packet);
                    // Paper start - Add Listing API for Player
                } else {
                    entityplayer1.connection.send(ClientboundPlayerInfoUpdatePacket.createSinglePlayerInitializing(player, false));
                }
                // Paper end - Add Listing API for Player
            }
            if (entityplayer1 == player || !bukkitPlayer.canSee(entityplayer1.getBukkitEntity())) { // Paper - Use single player info update packet on join; Don't include joining player
                continue;
            }
            onlinePlayers.add(entityplayer1); // Paper - Use single player info update packet on join
        }
        // Paper start - Use single player info update packet on join
        if (!onlinePlayers.isEmpty()) {
            player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(onlinePlayers, player)); // Paper - Add Listing API for Player
        }
        // Paper end - Use single player info update packet on join
        player.sentListPacket = true;
        player.supressTrackerForLogin = false; // Paper - Fire PlayerJoinEvent when Player is actually ready
        ((ServerLevel)player.level()).getChunkSource().chunkMap.addEntity(player); // Paper - Fire PlayerJoinEvent when Player is actually ready; track entity now
        // CraftBukkit end

        // p_11263_.refreshEntityData(p_11263_); // CraftBukkit - BungeeCord#2321, send complete data to self on spawn

        this.sendLevelInfo(player, serverlevel1);

        // CraftBukkit start - Only add if the player wasn't moved in the event
        if (player.level() == serverlevel1 && !serverlevel1.players().contains(player)) {
            serverlevel1.addNewPlayer(player);
            this.server.getCustomBossEvents().onPlayerConnect(player);
        }
        serverlevel1 = player.serverLevel(); // CraftBukkit - Update in case join event changed it
        // CraftBukkit end

        this.sendActivePlayerEffects(player);
        // Paper start - Fire PlayerJoinEvent when Player is actually ready; move vehicle into method so it can be called above - short circuit around that code
        this.onPlayerJoinFinish(player, serverlevel1, s1);
        // Paper end - Fire PlayerJoinEvent when Player is actually ready
    }

    private void mountSavedVehicle(ServerPlayer player, ServerLevel worldserver1, Optional<CompoundTag> optional) {
        // Paper end - Fire PlayerJoinEvent when Player is actually ready
        if (optional.isPresent() && ((CompoundTag) optional.get()).contains("RootVehicle", 10)) {
            CompoundTag nbttagcompound = ((CompoundTag) optional.get()).getCompound("RootVehicle");
            ServerLevel finalWorldServer = worldserver1; // CraftBukkit - decompile error
            Vec3 playerPos = player.position(); // Folia - sync vehicle position to player position on player data load
            Entity entity = EntityType.loadEntityRecursive(nbttagcompound.getCompound("Entity"), worldserver1, (entity1) -> {
                // Folia start - sync vehicle position to player position on player data load
                if (entity1.distanceToSqr(player) > (5.0 * 5.0)) {
                    entity1.setPosRaw(playerPos.x, playerPos.y, playerPos.z, true);
                }
                // Folia end - sync vehicle position to player position on player data load
                return !finalWorldServer.addWithUUID(entity1, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.MOUNT) ? null : entity1; // CraftBukkit - decompile error // Paper - Entity#getEntitySpawnReason
            });

            if (entity != null) {
                UUID uuid;

                if (nbttagcompound.hasUUID("Attach")) {
                    uuid = nbttagcompound.getUUID("Attach");
                } else {
                    uuid = null;
                }

                Iterator iterator;
                Entity entity1;

                if (entity.getUUID().equals(uuid)) {
                    player.startRiding(entity, true);
                } else {
                    iterator = entity.getIndirectPassengers().iterator();

                    while (iterator.hasNext()) {
                        entity1 = (Entity) iterator.next();
                        if (entity1.getUUID().equals(uuid)) {
                            player.startRiding(entity1, true);
                            break;
                        }
                    }
                }

                if (!player.isPassenger()) {
                    PlayerList.LOGGER.warn("Couldn't reattach entity to player");
                    entity.discard(null); // CraftBukkit - add Bukkit remove cause
                    iterator = entity.getIndirectPassengers().iterator();

                    while (iterator.hasNext()) {
                        entity1 = (Entity) iterator.next();
                        entity1.discard(null); // CraftBukkit - add Bukkit remove cause
                    }
                }
            }
        }

        // Paper start - Fire PlayerJoinEvent when Player is actually ready
    }
    // Paper end - Fire PlayerJoinEvent when Player is actually ready
    public void onPlayerJoinFinish(ServerPlayer player, ServerLevel worldserver1, String s1) {
        // Paper start - Fire PlayerJoinEvent when Player is actually ready; move vehicle into method so it can be called above - short circuit around that code
        player.initInventoryMenu();
        // Paper start - Configurable player collision; Add to collideRule team if needed
        final net.minecraft.world.scores.Scoreboard scoreboard = this.getServer().getLevel(Level.OVERWORLD).getScoreboard();
        final PlayerTeam collideRuleTeam = scoreboard.getPlayerTeam(this.collideRuleTeamName);
        if (this.collideRuleTeamName != null && collideRuleTeam != null && player.getTeam() == null) {
            scoreboard.addPlayerToTeam(player.getScoreboardName(), collideRuleTeam);
        }
        // Paper end - Configurable player collision
        net.neoforged.neoforge.attachment.AttachmentSync.syncInitialPlayerAttachments(player);
        net.neoforged.neoforge.event.EventHooks.firePlayerLoggedIn( player );
        // CraftBukkit - Moved from above, added world
        org.purpurmc.purpur.task.BossBarTask.addToAll(player); // Purpur
        LOGGER.info(
                "{}[{}] logged in with entity id {} at ({}, {}, {})",
                player.getName().getString(),
                s1,
                player.getId(),
                player.getX(),
                player.getY(),
                player.getZ()
        );
    }

    public void updateEntireScoreboard(ServerScoreboard p_11274_, ServerPlayer p_11275_) {
        Set<Objective> set = Sets.newHashSet();

        for (PlayerTeam playerteam : p_11274_.getPlayerTeams()) {
            p_11275_.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(playerteam, true));
        }

        for (DisplaySlot displayslot : DisplaySlot.values()) {
            Objective objective = p_11274_.getDisplayObjective(displayslot);
            if (objective != null && !set.contains(objective)) {
                for (Packet<?> packet : p_11274_.getStartTrackingPackets(objective)) {
                    p_11275_.connection.send(packet);
                }

                set.add(objective);
            }
        }
    }

    public void addWorldborderListener(ServerLevel p_184210_) {
        if (playerIo != null) return; // CraftBukkit
        p_184210_.getWorldBorder().addListener(new BorderChangeListener() {
            @Override
            public void onBorderSizeSet(WorldBorder p_11321_, double p_11322_) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderSizePacket(p_11321_), p_11321_.world); // CraftBukkit
            }

            @Override
            public void onBorderSizeLerping(WorldBorder p_11328_, double p_11329_, double p_11330_, long p_11331_) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderLerpSizePacket(p_11328_), p_11328_.world); // CraftBukkit
            }

            @Override
            public void onBorderCenterSet(WorldBorder p_11324_, double p_11325_, double p_11326_) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderCenterPacket(p_11324_), p_11324_.world); // CraftBukkit
            }

            @Override
            public void onBorderSetWarningTime(WorldBorder p_11333_, int p_11334_) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderWarningDelayPacket(p_11333_), p_11333_.world); // CraftBukkit
            }

            @Override
            public void onBorderSetWarningBlocks(WorldBorder p_11339_, int p_11340_) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderWarningDistancePacket(p_11339_), p_11339_.world); // CraftBukkit
            }

            @Override
            public void onBorderSetDamagePerBlock(WorldBorder p_11336_, double p_11337_) {
            }

            @Override
            public void onBorderSetDamageSafeZOne(WorldBorder p_11342_, double p_11343_) {
            }
        });
    }

    public Optional<CompoundTag> load(ServerPlayer p_11225_) {
        CompoundTag compoundtag = this.server.getWorldData().getLoadedPlayerTag();
        Optional<CompoundTag> optional;
        if (this.server.isSingleplayerOwner(p_11225_.getGameProfile()) && compoundtag != null) {
            optional = Optional.of(compoundtag);
            p_11225_.load(compoundtag);
            LOGGER.debug("loading single player");
            net.neoforged.neoforge.event.EventHooks.firePlayerLoadingEvent(p_11225_, this.playerIo, p_11225_.getUUID().toString());
        } else {
            optional = this.playerIo.load(p_11225_);
        }

        return optional;
    }

    protected void save(ServerPlayer p_11277_) {
        if (!p_11277_.getBukkitEntity().isPersistent()) return; // CraftBukkit
        p_11277_.lastSave = MinecraftServer.currentTick; // Paper - Incremental chunk and player saving
        this.playerIo.save(p_11277_);
        ServerStatsCounter serverstatscounter = this.stats.get(p_11277_.getUUID());
        if (serverstatscounter != null) {
            serverstatscounter.save();
        }

        PlayerAdvancements playeradvancements = this.advancements.get(p_11277_.getUUID());
        if (playeradvancements != null) {
            playeradvancements.save();
        }
    }

    public net.kyori.adventure.text.Component quitMessage;
    public net.kyori.adventure.text.Component remove$leaveMessage;
    public void remove(ServerPlayer p_11287_) {
        org.purpurmc.purpur.task.BossBarTask.removeFromAll(p_11287_.getBukkitEntity()); // Purpur
        net.neoforged.neoforge.event.EventHooks.firePlayerLoggedOut(p_11287_);
        ServerLevel serverlevel = p_11287_.serverLevel();
        p_11287_.awardStat(Stats.LEAVE_GAME);

        // CraftBukkit start - Quitting must be before we do final save of data, in case plugins need to modify it
        // See SPIGOT-5799, SPIGOT-6145
        if (p_11287_.containerMenu != p_11287_.inventoryMenu) {
            InventoryOwner.setClose$Reason(org.bukkit.event.inventory.InventoryCloseEvent.Reason.DISCONNECT); // Paper - Inventory close reason
            p_11287_.closeContainer();
        }
        if (remove$leaveMessage == null) {
            remove$leaveMessage = net.kyori.adventure.text.Component.translatable("multiplayer.player.left", net.kyori.adventure.text.format.NamedTextColor.YELLOW, io.papermc.paper.configuration.GlobalConfiguration.get().messages.useDisplayNameInQuitMessage ? p_11287_.getBukkitEntity().displayName() : io.papermc.paper.adventure.PaperAdventure.asAdventure(p_11287_.getDisplayName()));
        }
        PlayerQuitEvent playerQuitEvent = new PlayerQuitEvent(p_11287_.getBukkitEntity(), remove$leaveMessage, p_11287_.quitReason); // Paper - Adventure & Add API for quit reason
        remove$leaveMessage = null; // Youer
        cserver.getPluginManager().callEvent(playerQuitEvent);
        p_11287_.getBukkitEntity().disconnect(playerQuitEvent.getQuitMessage());
        if (server.isSameThread()) p_11287_.doTick(); // SPIGOT-924
        // CraftBukkit end

        // Paper start - Configurable player collision; Remove from collideRule team if needed
        if (this.collideRuleTeamName != null) {
            final net.minecraft.world.scores.Scoreboard scoreBoard = this.server.getLevel(Level.OVERWORLD).getScoreboard();
            final PlayerTeam team = scoreBoard.getPlayersTeam(this.collideRuleTeamName);
            if (p_11287_.getTeam() == team && team != null) {
                scoreBoard.removePlayerFromTeam(p_11287_.getScoreboardName(), team);
            }
        }
        // Paper end - Configurable player collision

        // Paper - Drop carried item when player has disconnected
        if (!p_11287_.containerMenu.getCarried().isEmpty()) {
            net.minecraft.world.item.ItemStack carried = p_11287_.containerMenu.getCarried();
            p_11287_.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
            p_11287_.drop(carried, false);
        }
        // Paper end - Drop carried item when player has disconnected

        this.save(p_11287_);
        if (p_11287_.isPassenger()) {
            Entity entity = p_11287_.getRootVehicle();
            if (entity.hasExactlyOnePlayerPassenger()) {
                LOGGER.debug("Removing player mount");
                p_11287_.stopRiding();
                entity.getPassengersAndSelf().forEach(p_215620_ -> {
                    // Paper start - Fix villager boat exploit
                    if (p_215620_ instanceof net.minecraft.world.entity.npc.AbstractVillager villager) {
                        final net.minecraft.world.entity.player.Player human = villager.getTradingPlayer();
                        if (human != null) {
                            villager.setTradingPlayer(null);
                        }
                    }
                    // Paper end - Fix villager boat exploit
                    p_215620_.setRemovedCB(Entity.RemovalReason.UNLOADED_WITH_PLAYER, org.bukkit.event.entity.EntityRemoveEvent.Cause.PLAYER_QUIT);
                });
            }
        }

        p_11287_.unRide();
        serverlevel.removePlayerImmediately(p_11287_, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        p_11287_.retireScheduler(); // Paper - Folia schedulers
        p_11287_.getAdvancements().stopListening();
        this.players.remove(p_11287_);
        this.playersByName.remove(p_11287_.getScoreboardName().toLowerCase(java.util.Locale.ROOT)); // Spigot
        this.server.getCustomBossEvents().onPlayerDisconnect(p_11287_);
        UUID uuid = p_11287_.getUUID();
        ServerPlayer serverplayer = this.playersByUUID.get(uuid);
        if (serverplayer == p_11287_) {
            this.playersByUUID.remove(uuid);
            this.stats.remove(uuid);
            this.advancements.remove(uuid);
        }

        // CraftBukkit start
        // this.broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(p_11287_.getUUID())));
        ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(p_11287_.getUUID()));
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer entityplayer2 = (ServerPlayer) this.players.get(i);
            if (entityplayer2.getBukkitEntity().canSee(p_11287_.getBukkitEntity())) {
                entityplayer2.connection.send(packet);
            } else {
                entityplayer2.getBukkitEntity().onEntityRemove(p_11287_);
            }
        }
        // This removes the scoreboard (and player reference) for the specific player in the manager
        cserver.getScoreboardManager().removePlayer(p_11287_.getBukkitEntity());
        // CraftBukkit end

        quitMessage = playerQuitEvent.quitMessage();
    }

    // Mohist start
    public final AtomicReference<ServerPlayer> youer$canPlayerLogin$entity = new AtomicReference<>(null);
    private final AtomicReference<ServerLoginPacketListenerImpl> handler = new AtomicReference<>(null);

    public void youer$putHandler(ServerLoginPacketListenerImpl handler) {
        this.handler.set(handler);
    }
    // CraftBukkit start - Whole method, SocketAddress to LoginListener, added hostname to signature, return EntityPlayer
    public Component canPlayerLogin(SocketAddress p_11257_, GameProfile p_11258_) {
        // Moved from processLogin
        UUID uuid = p_11258_.getId();
        List<ServerPlayer> list = Lists.newArrayList();

        ServerPlayer entityplayer;

        for (int i = 0; i < this.players.size(); ++i) {
            entityplayer = (ServerPlayer) this.players.get(i);
            if (entityplayer.getUUID().equals(uuid)) {
                list.add(entityplayer);
            }
        }

        java.util.Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            entityplayer = (ServerPlayer) iterator.next();
            save(entityplayer); // CraftBukkit - Force the player's inventory to be saved
            entityplayer.connection.disconnect(Component.translatable("multiplayer.disconnect.duplicate_login"), org.bukkit.event.player.PlayerKickEvent.Cause.DUPLICATE_LOGIN); // Paper - kick event cause
        }
        // Instead of kicking then returning, we need to store the kick reason
        // in the event, check with plugins to see if it's ok, and THEN kick
        // depending on the outcome.
        ServerPlayer entity = new ServerPlayer(this.server, this.server.getLevel(Level.OVERWORLD), p_11258_, ClientInformation.createDefault());
        youer$canPlayerLogin$entity.set(entity);

        ServerLoginPacketListenerImpl handleR = handler.getAndSet(null);
        String hostname = handleR == null ? "" : handleR.connection.hostname;
        InetAddress realAddress = handleR == null ? ((InetSocketAddress) p_11257_).getAddress() : ((InetSocketAddress) handleR.connection.channel.remoteAddress()).getAddress();

        entity.transferCookieConnection = handleR;
        org.bukkit.entity.Player player = entity.getBukkitEntity();
        org.bukkit.event.player.PlayerLoginEvent event = new org.bukkit.event.player.PlayerLoginEvent(player, hostname, ((java.net.InetSocketAddress) p_11257_).getAddress(), realAddress);

        // Paper start - Fix MC-158900
        UserBanListEntry userbanlistentry;
        if (this.bans.isBanned(p_11258_) && (userbanlistentry = this.bans.get(p_11258_)) != null) {
            MutableComponent mutablecomponent1 = Component.translatable("multiplayer.disconnect.banned.reason", userbanlistentry.getReason());
            if (userbanlistentry.getExpires() != null) {
                mutablecomponent1.append(
                    Component.translatable("multiplayer.disconnect.banned.expiration", BAN_DATE_FORMAT.format(userbanlistentry.getExpires()))
                );
            }

            event.disallow(org.bukkit.event.player.PlayerLoginEvent.Result.KICK_BANNED, io.papermc.paper.adventure.PaperAdventure.asAdventure(mutablecomponent1)); // Paper - Adventure
        } else if (!this.isWhiteListed(p_11258_, event)) { // Paper - ProfileWhitelistVerifyEvent
            //MutableComponent ichatmutablecomponent = Component.translatable("multiplayer.disconnect.not_whitelisted");
            //event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(org.spigotmc.SpigotConfig.whitelistMessage)); // Spigot // Paper - Adventure
        } else if (this.getIpBans().isBanned(p_11257_) && !this.getIpBans().get(p_11257_).hasExpired()) {
            IpBanListEntry ipbanlistentry = this.ipBans.get(p_11257_);
            MutableComponent mutablecomponent = Component.translatable("multiplayer.disconnect.banned_ip.reason", ipbanlistentry.getReason());
            if (ipbanlistentry.getExpires() != null) {
                mutablecomponent.append(
                    Component.translatable("multiplayer.disconnect.banned_ip.expiration", BAN_DATE_FORMAT.format(ipbanlistentry.getExpires()))
                );
            }

            event.disallow(org.bukkit.event.player.PlayerLoginEvent.Result.KICK_BANNED, io.papermc.paper.adventure.PaperAdventure.asAdventure(mutablecomponent)); // Paper - Adventure
        } else {
            if (this.players.size() >= this.maxPlayers && !this.canBypassPlayerLimit(p_11258_)) {
                event.disallow(org.bukkit.event.player.PlayerLoginEvent.Result.KICK_FULL, ColorAPI.adventure(org.spigotmc.SpigotConfig.serverFullMessage)); // Spigot // Paper - Adventure
            }
        }

        cserver.getPluginManager().callEvent(event);
        if (event.getResult() != org.bukkit.event.player.PlayerLoginEvent.Result.ALLOWED) {
            youer$canPlayerLogin$entity.set(null);
            return io.papermc.paper.adventure.PaperAdventure.asVanilla(event.kickMessage());
        }
        return null;
    }

    public ServerPlayer getPlayerForLogin$player = null;
    public ServerPlayer getPlayerForLogin(GameProfile p_215625_, ClientInformation p_302018_) {
        var d = this.server.overworld();
        ServerPlayer player = getPlayerForLogin$player;
        player.updateOptions(p_302018_);
        return player;
        // CraftBukkit end
    }

    public boolean disconnectAllPlayersWithProfile(GameProfile p_295670_, ServerPlayer player) { // CraftBukkit - added EntityPlayer
      /* CraftBukkit startMoved up
        UUID uuid = p_295670_.getId();
        Set<ServerPlayer> set = Sets.newIdentityHashSet();

        for (ServerPlayer serverplayer : this.players) {
            if (serverplayer.getUUID().equals(uuid)) {
                set.add(serverplayer);
            }
        }

        ServerPlayer serverplayer2 = this.playersByUUID.get(p_295670_.getId());
        if (serverplayer2 != null) {
            set.add(serverplayer2);
        }

        for (ServerPlayer serverplayer1 : set) {
            serverplayer1.connection.disconnect(DUPLICATE_LOGIN_DISCONNECT_MESSAGE);
        }

        return !set.isEmpty();
        */
        return player == null;
        // CraftBukkit end
    }

    // CraftBukkit start
    public ServerPlayer respawn(ServerPlayer p_11237_, boolean p_11238_, Entity.RemovalReason p_348558_) {
        p_11237_.stopRiding();
        this.players.remove(p_11237_);
        this.playersByName.remove(p_11237_.getScoreboardName().toLowerCase(java.util.Locale.ROOT)); // Spigot
        p_11237_.serverLevel().removePlayerImmediately(p_11237_, p_348558_);
        DimensionTransition dimensiontransition;
        if (p_11237_.respawn$location == null) {
            dimensiontransition = p_11237_.findRespawnPositionAndUseSpawnBlock(p_11238_, DimensionTransition.DO_NOTHING);

            if (!p_11238_) p_11237_.reset(); // SPIGOT-4785
        } else {
            dimensiontransition = new DimensionTransition(((CraftWorld) p_11237_.respawn$location.getWorld()).getHandle(), CraftLocation.toVec3D(p_11237_.respawn$location), Vec3.ZERO, p_11237_.respawn$location.getYaw(), p_11237_.respawn$location.getPitch(), DimensionTransition.DO_NOTHING);
        }
        // Neo: Allow changing the respawn position of players. The local dimension transition is updated with the new target.
        var event = net.neoforged.neoforge.event.EventHooks.firePlayerRespawnPositionEvent(p_11237_, dimensiontransition, p_11238_);
        dimensiontransition = event.getDimensionTransition();

        ServerLevel serverlevel = dimensiontransition.newLevel();
        ServerPlayer serverplayer = new ServerPlayer(this.server, serverlevel, p_11237_.getGameProfile(), p_11237_.clientInformation());
        serverplayer.wonGame = false;
        serverplayer.connection = p_11237_.connection;
        serverplayer.restoreFrom(p_11237_, p_11238_);
        serverplayer.setId(p_11237_.getId());
        serverplayer.setMainArm(p_11237_.getMainArm());

        // Neo: Allow the event to control if the original spawn position is copied
        if (event.copyOriginalSpawnPosition()) {
            serverplayer.copyRespawnPosition(p_11237_);
        }

        for (String s : p_11237_.getTags()) {
            serverplayer.addTag(s);
        }
        serverplayer.spawnIn(serverlevel);
        serverplayer.unsetRemoved();;
        serverplayer.setShiftKeyDown(false);

        Vec3 vec3 = dimensiontransition.pos();
        serverplayer.forceSetPositionRotation(vec3.x, vec3.y, vec3.z, dimensiontransition.yRot(), dimensiontransition.xRot());
        serverlevel.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.POST_TELEPORT, new net.minecraft.world.level.ChunkPos(net.minecraft.util.Mth.floor(vec3.x()) >> 4, net.minecraft.util.Mth.floor(vec3.z()) >> 4), 1, p_11237_.getId()); // Paper
        if (dimensiontransition.missingRespawnBlock()) {
            serverplayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
            serverplayer.setRespawnPosition$setSpawnCause(PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN);
            serverplayer.setRespawnPosition(null, null, 0f, false, false); // CraftBukkit - SPIGOT-5988: Clear respawn location when obstructed // Paper - PlayerSetSpawnEvent
        }

        byte b0 = (byte)(p_11238_ ? 1 : 0);
        ServerLevel serverlevel1 = serverplayer.serverLevel();
        LevelData leveldata = serverlevel1.getLevelData();
        serverplayer.connection.send(new ClientboundRespawnPacket(serverplayer.createCommonSpawnInfo(serverlevel1), b0));
        serverplayer.connection.send(new ClientboundSetChunkCacheRadiusPacket(serverlevel1.spigotConfig.viewDistance)); // Spigot
        serverplayer.connection.send(new ClientboundSetSimulationDistancePacket(serverlevel1.spigotConfig.simulationDistance)); // Spigot
        serverplayer.connection.teleport(CraftLocation.toBukkit(serverplayer.position(), serverlevel1.getWorld(), serverplayer.getYRot(), serverplayer.getXRot())); // CraftBukkit
        serverplayer.connection.send(new ClientboundSetDefaultSpawnPositionPacket(serverlevel.getSharedSpawnPos(), serverlevel.getSharedSpawnAngle()));
        serverplayer.connection.send(new ClientboundChangeDifficultyPacket(leveldata.getDifficulty(), leveldata.isDifficultyLocked()));
        serverplayer.connection
                .send(new ClientboundSetExperiencePacket(serverplayer.experienceProgress, serverplayer.totalExperience, serverplayer.experienceLevel));
        this.sendActivePlayerEffects(serverplayer);
        this.sendLevelInfo(serverplayer, serverlevel);
        this.sendPlayerPermissionLevel(serverplayer);
        if (!p_11237_.connection.isDisconnected()) {
            serverlevel.addRespawnedPlayer(serverplayer);
            this.players.add(serverplayer);
            this.playersByName.put(serverplayer.getScoreboardName().toLowerCase(java.util.Locale.ROOT), serverplayer); // Spigot
            this.playersByUUID.put(serverplayer.getUUID(), serverplayer);
        }
        serverplayer.initInventoryMenu();
        serverplayer.setHealth(serverplayer.getHealth());
        net.neoforged.neoforge.attachment.AttachmentSync.syncInitialPlayerAttachments(serverplayer);
        net.neoforged.neoforge.event.EventHooks.firePlayerRespawnEvent(serverplayer, p_11238_);
        // Paper start - Add PlayerPostRespawnEvent
        boolean isBedSpawn = false;
        // Paper end - Add PlayerPostRespawnEvent
        if (!p_11238_) {
            BlockPos blockpos = BlockPos.containing(dimensiontransition.pos());
            BlockState blockstate = serverlevel.getBlockState(blockpos);
            if (blockstate.is(Blocks.RESPAWN_ANCHOR)) {
                serverplayer.connection
                        .send(
                                new ClientboundSoundPacket(
                                        SoundEvents.RESPAWN_ANCHOR_DEPLETE,
                                        SoundSource.BLOCKS,
                                        (double)blockpos.getX(),
                                        (double)blockpos.getY(),
                                        (double)blockpos.getZ(),
                                        1.0F,
                                        1.0F,
                                        serverlevel.getRandom().nextLong()
                                )
                        );
            }
            // Paper start - Add PlayerPostRespawnEvent
            if (blockstate.is(net.minecraft.tags.BlockTags.BEDS) && !dimensiontransition.missingRespawnBlock()) {
                isBedSpawn = true;
            }
            // Paper end - Add PlayerPostRespawnEvent
        }
        // Added from changeDimension
        this.sendAllPlayerInfo(serverplayer); // Update health, etc...
        serverplayer.onUpdateAbilities();
        for (MobEffectInstance mobEffect : serverplayer.getActiveEffects()) {
            serverplayer.connection.send(new ClientboundUpdateMobEffectPacket(serverplayer.getId(), mobEffect, false)); // blend = false
        }

        // Fire advancement trigger
        serverplayer.triggerDimensionChangeTriggers(serverlevel);
        Level fromWorld = p_11237_.level();
        // Don't fire on respawn
        if (fromWorld != serverlevel) {
            PlayerChangedWorldEvent eventCWE = new PlayerChangedWorldEvent(p_11237_.getBukkitEntity(), fromWorld.getWorld());
            this.server.server.getPluginManager().callEvent(eventCWE);
        }

        // Save player file again if they were disconnected
        if (serverplayer.connection.isDisconnected()) {
            this.save(serverplayer);
        }
        cserver.getPluginManager().callEvent(new com.destroystokyo.paper.event.player.PlayerPostRespawnEvent(serverplayer.getBukkitEntity(),
                CraftLocation.toBukkit(dimensiontransition.pos(), dimensiontransition.newLevel().getWorld(), dimensiontransition.yRot(), dimensiontransition.xRot()),
                isBedSpawn));
        return serverplayer;
    }

    public ServerPlayer respawn(ServerPlayer entityplayer, boolean flag, Entity.RemovalReason entity_removalreason, PlayerRespawnEvent.RespawnReason reason) {
        return this.respawn(entityplayer, flag, entity_removalreason, reason, null);
    }

    public ServerPlayer respawn(ServerPlayer entityplayer, boolean flag, Entity.RemovalReason entity_removalreason, PlayerRespawnEvent.RespawnReason reason, Location location) {
        entityplayer.stopRiding(); // CraftBukkit
        this.players.remove(entityplayer);
        this.playersByName.remove(entityplayer.getScoreboardName().toLowerCase(java.util.Locale.ROOT)); // Spigot
        entityplayer.serverLevel().removePlayerImmediately(entityplayer, entity_removalreason);

        ServerPlayer entityplayer1 = entityplayer;
        Level fromWorld = entityplayer.level();
        entityplayer.wonGame = false;
        // CraftBukkit end

        entityplayer1.connection = entityplayer.connection;
        entityplayer1.restoreFrom(entityplayer, flag);
        entityplayer1.setId(entityplayer.getId());
        entityplayer1.setMainArm(entityplayer.getMainArm());

        for (String s : entityplayer.getTags()) {
            entityplayer1.addTag(s);
        }
        // Paper start - Add PlayerPostRespawnEvent
        boolean isBedSpawn = false;
        boolean isRespawn = false;
        // Paper end - Add PlayerPostRespawnEvent

        // CraftBukkit start - fire PlayerRespawnEvent
        DimensionTransition dimensiontransition;
        if (location == null) {
            dimensiontransition = entityplayer.findRespawnPositionAndUseSpawnBlockCB(flag, DimensionTransition.DO_NOTHING, reason);

            if (!flag) entityplayer.reset(); // SPIGOT-4785
            // Paper start - Add PlayerPostRespawnEvent
            if (dimensiontransition == null) return entityplayer; // Early exit, mirrors belows early return for disconnected players in respawn event
            isRespawn = true;
            location = CraftLocation.toBukkit(dimensiontransition.pos(), dimensiontransition.newLevel().getWorld(), dimensiontransition.yRot(), dimensiontransition.xRot());
            // Paper end - Add PlayerPostRespawnEvent
        } else {
            dimensiontransition = new DimensionTransition(((CraftWorld) location.getWorld()).getHandle(), CraftLocation.toVec3D(location), Vec3.ZERO, location.getYaw(), location.getPitch(), DimensionTransition.DO_NOTHING);
        }
        // Spigot Start
        if (dimensiontransition == null) { // Paper - Add PlayerPostRespawnEvent - diff on change - spigot early returns if respawn pos is null, that is how they handle disconnected player in respawn event
            return entityplayer;
        }
        // Spigot
        if (isRespawn) {
            // Neo: Allow changing the respawn position of players. The local dimension transition is updated with the new target.
            var event = net.neoforged.neoforge.event.EventHooks.firePlayerRespawnPositionEvent(entityplayer, dimensiontransition, flag);
            dimensiontransition = event.getDimensionTransition();
        }

        ServerLevel worldserver = dimensiontransition.newLevel();
        entityplayer1.spawnIn(worldserver);
        entityplayer1.unsetRemoved();
        entityplayer1.setShiftKeyDown(false);
        Vec3 vec3d = dimensiontransition.pos();

        entityplayer1.forceSetPositionRotation(vec3d.x, vec3d.y, vec3d.z, dimensiontransition.yRot(), dimensiontransition.xRot());
        worldserver.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.POST_TELEPORT, new net.minecraft.world.level.ChunkPos(net.minecraft.util.Mth.floor(vec3d.x()) >> 4, net.minecraft.util.Mth.floor(vec3d.z()) >> 4), 1, entityplayer.getId()); // Paper
        // CraftBukkit end
        if (dimensiontransition.missingRespawnBlock()) {
            entityplayer1.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
            entityplayer1.setRespawnPosition(null, null, 0f, false, false, com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN); // CraftBukkit - SPIGOT-5988: Clear respawn location when obstructed // Paper - PlayerSetSpawnEvent
        }

        int i = flag ? 1 : 0;
        ServerLevel worldserver1 = entityplayer1.serverLevel();
        LevelData worlddata = worldserver1.getLevelData();

        entityplayer1.connection.send(new ClientboundRespawnPacket(entityplayer1.createCommonSpawnInfo(worldserver1), (byte) i));
        entityplayer1.connection.send(new ClientboundSetChunkCacheRadiusPacket(worldserver1.spigotConfig.viewDistance)); // Spigot
        entityplayer1.connection.send(new ClientboundSetSimulationDistancePacket(worldserver1.spigotConfig.simulationDistance)); // Spigot
        entityplayer1.connection.teleport(CraftLocation.toBukkit(entityplayer1.position(), worldserver1.getWorld(), entityplayer1.getYRot(), entityplayer1.getXRot())); // CraftBukkit
        entityplayer1.connection.send(new ClientboundSetDefaultSpawnPositionPacket(worldserver.getSharedSpawnPos(), worldserver.getSharedSpawnAngle()));
        entityplayer1.connection.send(new ClientboundChangeDifficultyPacket(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
        entityplayer1.connection.send(new ClientboundSetExperiencePacket(entityplayer1.experienceProgress, entityplayer1.totalExperience, entityplayer1.experienceLevel));
        this.sendActivePlayerEffects(entityplayer1);
        this.sendLevelInfo(entityplayer1, worldserver);
        this.sendPlayerPermissionLevel(entityplayer1);
        if (!entityplayer.connection.isDisconnected()) {
            worldserver.addRespawnedPlayer(entityplayer1);
            this.players.add(entityplayer1);
            this.playersByName.put(entityplayer1.getScoreboardName().toLowerCase(java.util.Locale.ROOT), entityplayer1); // Spigot
            this.playersByUUID.put(entityplayer1.getUUID(), entityplayer1);
        }
        // entityplayer1.initInventoryMenu();
        entityplayer1.setHealth(entityplayer1.getHealth());
        net.neoforged.neoforge.attachment.AttachmentSync.syncInitialPlayerAttachments(entityplayer1);
        if (!flag) {
            BlockPos blockposition = BlockPos.containing(dimensiontransition.pos());
            BlockState iblockdata = worldserver.getBlockState(blockposition);

            if (iblockdata.is(Blocks.RESPAWN_ANCHOR)) {
                entityplayer1.connection.send(new ClientboundSoundPacket(SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.BLOCKS, (double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), 1.0F, 1.0F, worldserver.getRandom().nextLong()));
            }
            // Paper start - Add PlayerPostRespawnEvent
            if (iblockdata.is(net.minecraft.tags.BlockTags.BEDS) && !dimensiontransition.missingRespawnBlock()) {
                isBedSpawn = true;
            }
            // Paper end - Add PlayerPostRespawnEvent
        }
        // Added from changeDimension
        this.sendAllPlayerInfo(entityplayer); // Update health, etc...
        entityplayer.onUpdateAbilities();
        for (MobEffectInstance mobEffect : entityplayer.getActiveEffects()) {
            entityplayer.connection.send(new ClientboundUpdateMobEffectPacket(entityplayer.getId(), mobEffect, false)); // blend = false
        }

        // Fire advancement trigger
        entityplayer.triggerDimensionChangeTriggers(worldserver);

        // Don't fire on respawn
        if (fromWorld != worldserver) {
            net.neoforged.neoforge.event.EventHooks.firePlayerChangedDimensionEvent(entityplayer1, fromWorld.dimension(), worldserver.dimension());
            PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(entityplayer.getBukkitEntity(), fromWorld.getWorld());
            this.server.server.getPluginManager().callEvent(event);
        }

        // Save player file again if they were disconnected
        if (entityplayer.connection.isDisconnected()) {
            this.save(entityplayer);
        }

        // Paper start - Add PlayerPostRespawnEvent
        if (isRespawn) {
            net.neoforged.neoforge.event.EventHooks.firePlayerRespawnEvent(entityplayer1, flag);
            cserver.getPluginManager().callEvent(new com.destroystokyo.paper.event.player.PlayerPostRespawnEvent(entityplayer.getBukkitEntity(), location, isBedSpawn));
        }
        // Paper end - Add PlayerPostRespawnEvent

        // CraftBukkit end

        return entityplayer1;
    }

    public void sendActivePlayerEffects(ServerPlayer p_348494_) {
        this.sendActiveEffects(p_348494_, p_348494_.connection);
    }

    public void sendActiveEffects(LivingEntity p_348624_, ServerGamePacketListenerImpl p_348496_) {
        for (MobEffectInstance mobeffectinstance : p_348624_.getActiveEffects()) {
            p_348496_.send(new ClientboundUpdateMobEffectPacket(p_348624_.getId(), mobeffectinstance, false));
        }
    }

    public void sendActiveEffects(LivingEntity entity, java.util.function.Consumer<Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> packetConsumer) {
        // Paper end - collect packets
        for (MobEffectInstance mobeffect : entity.getActiveEffects()) {
            packetConsumer.accept(new ClientboundUpdateMobEffectPacket(entity.getId(), mobeffect, false)); // Paper - collect packets
        }
    }

    public void sendPlayerPermissionLevel(ServerPlayer p_11290_) {
        GameProfile gameprofile = p_11290_.getGameProfile();
        int i = this.server.getProfilePermissions(gameprofile);
        this.sendPlayerPermissionLevel(p_11290_, i);
    }

    public void sendPlayerPermissionLevel(ServerPlayer player, boolean recalculatePermissions) {
        // Paper end - avoid recalculating permissions if possible
        GameProfile gameprofile = player.getGameProfile();
        int i = this.server.getProfilePermissions(gameprofile);

        this.sendPlayerPermissionLevel(player, i, recalculatePermissions); // Paper - avoid recalculating permissions if possible
    }

    public void tick() {
        if (++this.sendAllPlayerInfoIn > 600) {
            // CraftBukkit start
            for (int i = 0; i < this.players.size(); ++i) {
                final ServerPlayer target = this.players.get(i);
                target.connection.send(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY), this.players.stream().filter(new Predicate<ServerPlayer>() {
                    @Override
                    public boolean test(ServerPlayer input) {
                        return !(input instanceof FakePlayer) && target.getBukkitEntity().canSee(input.getBukkitEntity());
                    }
                }).collect(java.util.stream.Collectors.toList())));
            }
            // CraftBukkit end
            this.sendAllPlayerInfoIn = 0;
        }
    }

    public void broadcastAll(Packet<?> p_11269_) {
        for (ServerPlayer serverplayer : this.players) {
            serverplayer.connection.send(p_11269_);
        }
    }

    // CraftBukkit start - add a world/entity limited version
    public void broadcastAll(Packet<?> packet, Player entityhuman) {
        for (int i = 0; i < this.players.size(); ++i) {
            ServerPlayer entityplayer =  this.players.get(i);
            if (entityhuman != null && !(entityhuman instanceof FakePlayer) && !entityplayer.getBukkitEntity().canSee(entityhuman.getBukkitEntity())) {
                continue;
            }
            this.players.get(i).connection.send(packet);
        }
    }

    public void broadcastAll(Packet<?> packet, Level world) {
        for (int i = 0; i < world.players().size(); ++i) {
            ((ServerPlayer) world.players().get(i)).connection.send(packet);
        }
    }
    // CraftBukkit end

    // Purpur Start
    public void broadcastMiniMessage(@Nullable String message, boolean overlay) {
        if (message != null && !message.isEmpty()) {
            this.broadcastMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(message), overlay);
        }
    }

    public void broadcastMessage(@Nullable net.kyori.adventure.text.Component message, boolean overlay) {
        if (message != null) {
            this.broadcastSystemMessage(io.papermc.paper.adventure.PaperAdventure.asVanilla(message), overlay);
        }
    }
    // Purpur end

    public void broadcastAll(Packet<?> p_11271_, ResourceKey<Level> p_11272_) {
        for (ServerPlayer serverplayer : this.players) {
            if (serverplayer.level().dimension() == p_11272_) {
                serverplayer.connection.send(p_11271_);
            }
        }
    }

    public void broadcastSystemToTeam(Player p_215622_, Component p_215623_) {
        Team team = p_215622_.getTeam();
        if (team != null) {
            for (String s : team.getPlayers()) {
                ServerPlayer serverplayer = this.getPlayerByName(s);
                if (serverplayer != null && serverplayer != p_215622_) {
                    serverplayer.sendSystemMessage(p_215623_);
                }
            }
        }
    }

    public void broadcastSystemToAllExceptTeam(Player p_215650_, Component p_215651_) {
        Team team = p_215650_.getTeam();
        if (team == null) {
            this.broadcastSystemMessage(p_215651_, false);
        } else {
            for (int i = 0; i < this.players.size(); i++) {
                ServerPlayer serverplayer = this.players.get(i);
                if (serverplayer.getTeam() != team) {
                    serverplayer.sendSystemMessage(p_215651_);
                }
            }
        }
    }

    public String[] getPlayerNamesArray() {
        String[] astring = new String[this.players.size()];

        for (int i = 0; i < this.players.size(); i++) {
            astring[i] = this.players.get(i).getGameProfile().getName();
        }

        return astring;
    }

    public UserBanList getBans() {
        return this.bans;
    }

    public IpBanList getIpBans() {
        return this.ipBans;
    }

    public void op(GameProfile p_11254_) {
        if (net.neoforged.neoforge.event.EventHooks.onPermissionChanged(p_11254_, this.server.getOperatorUserPermissionLevel(), this)) return;
        this.ops.add(new ServerOpListEntry(p_11254_, this.server.getOperatorUserPermissionLevel(), this.ops.canBypassPlayerLimit(p_11254_)));
        ServerPlayer serverplayer = this.getPlayer(p_11254_.getId());
        if (serverplayer != null) {
            this.sendPlayerPermissionLevel(serverplayer);
        }
    }

    public void deop(GameProfile p_11281_) {
        if (net.neoforged.neoforge.event.EventHooks.onPermissionChanged(p_11281_, 0, this)) return;
        this.ops.remove(p_11281_);
        ServerPlayer serverplayer = this.getPlayer(p_11281_.getId());
        if (serverplayer != null) {
            this.sendPlayerPermissionLevel(serverplayer);
        }
    }

    private void sendPlayerPermissionLevel(ServerPlayer p_11227_, int p_11228_) {
        // Paper start - Add sendOpLevel API
        this.sendPlayerPermissionLevel(p_11227_, p_11228_, true);
    }
    public void sendPlayerPermissionLevel(ServerPlayer p_11227_, int p_11228_, boolean recalculatePermissions) {
        // Paper end - Add sendOpLevel API
        if (p_11227_.connection != null) {
            byte b0;
            if (p_11228_ <= 0) {
                b0 = 24;
            } else if (p_11228_ >= 4) {
                b0 = 28;
            } else {
                b0 = (byte)(24 + p_11228_);
            }

            p_11227_.connection.send(new ClientboundEntityEventPacket(p_11227_, b0));
        }

        if (recalculatePermissions) { // Paper - Add sendOpLevel API
            p_11227_.getBukkitEntity().recalculatePermissions(); // CraftBukkit
            this.server.getCommands().sendCommands(p_11227_);
        } // Paper - Add sendOpLevel API
    }

    public boolean isWhiteListed(GameProfile p_11294_) {
        // Paper start - ProfileWhitelistVerifyEvent
        return this.isWhiteListed(p_11294_, null);
    }
    public boolean isWhiteListed(GameProfile gameprofile, @Nullable org.bukkit.event.player.PlayerLoginEvent loginEvent) {
        boolean isOp = this.ops.contains(gameprofile);
        boolean isWhitelisted = !this.doWhiteList || isOp || this.whitelist.contains(gameprofile);
        final com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent event;

        final net.kyori.adventure.text.Component configuredMessage = ColorAPI.adventure(org.spigotmc.SpigotConfig.whitelistMessage);
        event = new com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent(com.destroystokyo.paper.profile.CraftPlayerProfile.asBukkitMirror(gameprofile), this.doWhiteList, isWhitelisted, isOp, configuredMessage);
        event.callEvent();
        if (!event.isWhitelisted()) {
            if (loginEvent != null) {
                loginEvent.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, event.kickMessage() == null ? configuredMessage : event.kickMessage());
            }
            return false;
        }
        return true;
        // Paper end - ProfileWhitelistVerifyEvent
    }

    public boolean isOp(GameProfile p_11304_) {
        return this.ops.contains(p_11304_)
            || this.server.isSingleplayerOwner(p_11304_) && this.server.getWorldData().isAllowCommands()
            || this.allowCommandsForAllPlayers;
    }

    @Nullable
    public ServerPlayer getPlayerByName(String p_11256_) {
        return this.playersByName.get(p_11256_.toLowerCase(java.util.Locale.ROOT)); // Spigot
    }

    public void broadcast(
        @Nullable Player p_11242_, double p_11243_, double p_11244_, double p_11245_, double p_11246_, ResourceKey<Level> p_11247_, Packet<?> p_11248_
    ) {
        for (int i = 0; i < this.players.size(); i++) {
            ServerPlayer serverplayer = this.players.get(i);

            // CraftBukkit start - Test if player receiving packet can see the source of the packet
            if (p_11242_ != null && !(p_11242_ instanceof FakePlayer) && !serverplayer.getBukkitEntity().canSee(p_11242_.getBukkitEntity())) {
                continue;
            }
            // CraftBukkit end

            if (serverplayer != p_11242_ && serverplayer.level().dimension() == p_11247_) {
                double d0 = p_11243_ - serverplayer.getX();
                double d1 = p_11244_ - serverplayer.getY();
                double d2 = p_11245_ - serverplayer.getZ();
                if (d0 * d0 + d1 * d1 + d2 * d2 < p_11246_ * p_11246_) {
                    serverplayer.connection.send(p_11248_);
                }
            }
        }
    }

    public void saveAll() {
        // Paper start - Incremental chunk and player saving
        this.saveAll(-1);
    }

    public void saveAll(int interval) {
        io.papermc.paper.util.MCUtil.ensureMain("Save Players" , () -> { // Paper - Ensure main
            int numSaved = 0;
            long now = MinecraftServer.currentTick;
            for (int i = 0; i < this.players.size(); ++i) {
                ServerPlayer entityplayer = this.players.get(i);
                if (interval == -1 || now - entityplayer.lastSave >= interval) {
                    this.save(entityplayer);
                    if (interval != -1 && ++numSaved >= io.papermc.paper.configuration.GlobalConfiguration.get().playerAutoSave.maxPerTick()) { break; }
                }
                // Paper end - Incremental chunk and player saving
            }
            return null; }); // Paper - ensure main
    }
    public UserWhiteList getWhiteList() {
        return this.whitelist;
    }

    public String[] getWhiteListNames() {
        return this.whitelist.getUserList();
    }

    public ServerOpList getOps() {
        return this.ops;
    }

    public String[] getOpNames() {
        return this.ops.getUserList();
    }

    public void reloadWhiteList() {
    }

    public void sendLevelInfo(ServerPlayer p_11230_, ServerLevel p_11231_) {
        WorldBorder worldborder0 = this.server.overworld().getWorldBorder();
        WorldBorder worldborder = p_11230_.level().getWorldBorder(); // CraftBukkit
        p_11230_.connection.send(new ClientboundInitializeBorderPacket(worldborder));
        if (p_11230_.connection.hasChannel(net.neoforged.neoforge.network.payload.ClientboundCustomSetTimePayload.TYPE)) {
            p_11230_.connection.send(new net.neoforged.neoforge.network.payload.ClientboundCustomSetTimePayload(p_11231_.getGameTime(), p_11231_.getDayTime(), p_11231_.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT), p_11231_.getDayTimeFraction(), p_11231_.getDayTimePerTick()));
        } else {
        p_11230_.connection
            .send(new ClientboundSetTimePacket(p_11231_.getGameTime(), p_11231_.getDayTime(), p_11231_.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)));
        }
        p_11230_.connection.send(new ClientboundSetDefaultSpawnPositionPacket(p_11231_.getSharedSpawnPos(), p_11231_.getSharedSpawnAngle()));
        if (p_11231_.isRaining()) {
            // CraftBukkit start - handle player weather
            p_11230_.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL, false);
            p_11230_.updateWeather(-p_11231_.rainLevel, p_11231_.rainLevel, -p_11231_.thunderLevel, p_11231_.thunderLevel);
            // CraftBukkit end
        }

        p_11230_.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
        this.server.tickRateManager().updateJoiningPlayer(p_11230_);
        net.neoforged.neoforge.attachment.AttachmentSync.syncInitialLevelAttachments(p_11231_, p_11230_);
    }

    public void sendAllPlayerInfo(ServerPlayer p_11293_) {
        p_11293_.inventoryMenu.sendAllDataToRemote();
        p_11293_.getBukkitEntity().updateScaledHealth(); // CraftBukkit - Update scaled health on respawn and worldchange
        p_11293_.refreshEntityData(p_11293_); // CraftBukkkit - SPIGOT-7218: sync metadata
        p_11293_.connection.send(new ClientboundSetCarriedItemPacket(p_11293_.getInventory().selected));
        // CraftBukkit start - from GameRules
        int i = p_11293_.level().getGameRules().getBoolean(GameRules.RULE_REDUCEDDEBUGINFO) ? 22 : 23;
        p_11293_.connection.send(new ClientboundEntityEventPacket(p_11293_, (byte) i));
        float immediateRespawn = p_11293_.level().getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN) ? 1.0F: 0.0F;
        p_11293_.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.IMMEDIATE_RESPAWN, immediateRespawn));
        // CraftBukkit end
    }

    public int getPlayerCount() {
        return this.players.size();
    }

    public int getMaxPlayers() {
        return this.maxPlayers;
    }

    public boolean isUsingWhitelist() {
        return this.doWhiteList;
    }

    public void setUsingWhiteList(boolean p_11276_) {
        new WhitelistToggleEvent(p_11276_).callEvent(); // Paper - WhitelistToggleEvent
        this.doWhiteList = p_11276_;
    }

    public List<ServerPlayer> getPlayersWithAddress(String p_11283_) {
        List<ServerPlayer> list = Lists.newArrayList();

        for (ServerPlayer serverplayer : this.players) {
            if (serverplayer.getIpAddress().equals(p_11283_)) {
                list.add(serverplayer);
            }
        }

        return list;
    }

    public int getViewDistance() {
        return this.viewDistance;
    }

    public int getSimulationDistance() {
        return this.simulationDistance;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    @Nullable
    public CompoundTag getSingleplayerData() {
        return null;
    }

    public void setAllowCommandsForAllPlayers(boolean p_321498_) {
        this.allowCommandsForAllPlayers = p_321498_;
    }

    public void removeAll() {
        for (int i = 0; i < this.players.size(); i++) {
            this.players.get(i).connection.disconnect(java.util.Objects.requireNonNullElseGet(this.server.server.shutdownMessage(), net.kyori.adventure.text.Component::empty)); // CraftBukkit - add custom shutdown message // Paper - Adventure
        }

        // Paper start - Configurable player collision; Remove collideRule team if it exists
        if (this.collideRuleTeamName != null) {
            final net.minecraft.world.scores.Scoreboard scoreboard = this.getServer().getLevel(Level.OVERWORLD).getScoreboard();
            final PlayerTeam team = scoreboard.getPlayersTeam(this.collideRuleTeamName);
            if (team != null) scoreboard.removePlayerTeam(team);
        }
        // Paper end - Configurable player collision
    }

    // CraftBukkit start
    public void broadcastMessage(Component[] iChatBaseComponents) {
        for (Component component : iChatBaseComponents) {
            broadcastSystemMessage(component, false);
        }
    }
    // CraftBukkit end

    public void broadcastSystemMessage(Component p_240618_, boolean p_240644_) {
        this.broadcastSystemMessage(p_240618_, p_215639_ -> p_240618_, p_240644_);
    }

    public void broadcastSystemMessage(Component p_240526_, Function<ServerPlayer, Component> p_240594_, boolean p_240648_) {
        this.server.sendSystemMessage(p_240526_);

        for (ServerPlayer serverplayer : this.players) {
            Component component = p_240594_.apply(serverplayer);
            if (component != null) {
                serverplayer.sendSystemMessage(component, p_240648_);
            }
        }
    }

    public void broadcastChatMessage(PlayerChatMessage p_243229_, CommandSourceStack p_243254_, ChatType.Bound p_243255_) {
        this.broadcastChatMessage(p_243229_, p_243254_::shouldFilterMessageTo, p_243254_.getPlayer(), p_243255_);
    }

    public void broadcastChatMessage(PlayerChatMessage p_243264_, ServerPlayer p_243234_, ChatType.Bound p_243204_) {
        // Paper start
        this.broadcastChatMessage(p_243264_, p_243234_, p_243204_, null);
    }

    public void broadcastChatMessage(PlayerChatMessage p_243264_, ServerPlayer p_243234_, ChatType.Bound p_243204_, @Nullable Function<net.kyori.adventure.audience.Audience, Component> unsignedFunction) {
        // Paper end
        this.broadcastChatMessage(p_243264_, p_243234_::shouldFilterMessageTo, p_243234_, p_243204_, unsignedFunction);
    }

    // Youer start
    AtomicReference<Function<net.kyori.adventure.audience.Audience, Component>> broadcastChatMessage$unsignedFunction = new AtomicReference<>(null);
    public void broadcastChatMessage(
            PlayerChatMessage p_249952_, Predicate<ServerPlayer> p_250784_, @Nullable ServerPlayer p_249623_, ChatType.Bound p_250276_
    ) {
        // Paper end
        boolean flag = this.verifyChatTrusted(p_249952_);
        Function<net.kyori.adventure.audience.Audience, Component> unsignedFunction = broadcastChatMessage$unsignedFunction.getAndSet(null);
        this.server.logChatMessage((unsignedFunction == null ? p_249952_.decoratedContent() : unsignedFunction.apply(this.server.console)), p_250276_, flag ? null : "Not Secure"); // Paper
        OutgoingChatMessage outgoingchatmessage = OutgoingChatMessage.create(p_249952_);
        boolean flag1 = false;
        Packet<?> disguised = p_249623_ != null && unsignedFunction == null ? new net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket(outgoingchatmessage.content(), p_250276_) : null; // Paper - don't send player chat packets from vanished players

        for (ServerPlayer serverplayer : this.players) {
            boolean flag2 = p_250784_.test(serverplayer);
            // Paper start - don't send player chat packets from vanished players
            if (p_249623_ != null && !(p_249623_ instanceof FakePlayer) && !serverplayer.getBukkitEntity().canSee(p_249623_.getBukkitEntity())) {
                serverplayer.connection.send(unsignedFunction != null
                        ? new net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket(unsignedFunction.apply(serverplayer.getBukkitEntity()), p_250276_)
                        : disguised);
                continue;
            }
            // Paper end
            serverplayer.sendChatMessage$unsigned = unsignedFunction == null ? null : unsignedFunction.apply(serverplayer.getBukkitEntity()); // Paper
            serverplayer.sendChatMessage(outgoingchatmessage, flag2, p_250276_);
            serverplayer.sendChatMessage$unsigned = null; // Youer
            flag1 |= flag2 && p_249952_.isFullyFiltered();
        }

        if (flag1 && p_249623_ != null) {
            p_249623_.sendSystemMessage(CHAT_FILTERED_FULL);
        }
    }
    public void broadcastChatMessage(PlayerChatMessage p_249952_, Predicate<ServerPlayer> p_250784_, @Nullable ServerPlayer p_249623_, ChatType.Bound p_250276_, @Nullable Function<net.kyori.adventure.audience.Audience, Component> unsignedFunction) {
        broadcastChatMessage$unsignedFunction.set(unsignedFunction);
        broadcastChatMessage(p_249952_, p_250784_, p_249623_, p_250276_);
    }
    // Youer end

    public boolean verifyChatTrusted(PlayerChatMessage p_251384_) {  // Paper - private -> public
        return p_251384_.hasSignature() && !p_251384_.hasExpiredServer(Instant.now());
    }

    // CraftBukkit start
    public ServerStatsCounter getPlayerStats(ServerPlayer p_11240_) {
        ServerStatsCounter serverstatisticmanager = p_11240_.getStats();
        return serverstatisticmanager == null ? getPlayerStats(p_11240_.getUUID(), p_11240_.getGameProfile().getName()) : serverstatisticmanager; // Paper - use username and not display name
    }

    public ServerStatsCounter getPlayerStats(UUID uuid, String displayName) {
        ServerPlayer entityhuman = this.getPlayer(uuid);
        ServerStatsCounter serverstatscounter = entityhuman == null ? null : (ServerStatsCounter) entityhuman.getStats();
        // CraftBukkit end
        if (serverstatscounter == null) {
            File file1 = this.server.getWorldPath(LevelResource.PLAYER_STATS_DIR).toFile();
            File file2 = new File(file1, displayName + ".json"); // CraftBukkit

            serverstatscounter = new ServerStatsCounter(this.server, file2);
            this.stats.put(uuid, serverstatscounter);  // CraftBukkit
        }

        return serverstatscounter;
    }

    public PlayerAdvancements getPlayerAdvancements(ServerPlayer p_11297_) {
        // Neo: return a no-op PlayerAdvancements for fake players to avoid leaking CriteriaTrigger entries (#1487)
        if (p_11297_.isFakePlayer()) {
            Path path = this.server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve("_fake.json");
            return new net.neoforged.neoforge.common.util.FakePlayer.FakePlayerAdvancements(
                    this.server.getFixerUpper(), this, this.server.getAdvancements(), path, p_11297_);
        }
        UUID uuid = p_11297_.getUUID();
        PlayerAdvancements playeradvancements = this.advancements.get(uuid);
        if (playeradvancements == null) {
            Path path = this.server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(uuid + ".json");
            playeradvancements = new PlayerAdvancements(this.server.getFixerUpper(), this, this.server.getAdvancements(), path, p_11297_);
            this.advancements.put(uuid, playeradvancements);
        }

        playeradvancements.setPlayer(p_11297_);
        return playeradvancements;
    }

    public void setViewDistance(int p_11218_) {
        this.viewDistance = p_11218_;
        this.broadcastAll(new ClientboundSetChunkCacheRadiusPacket(p_11218_));

        for (ServerLevel serverlevel : this.server.getAllLevels()) {
            if (serverlevel != null) {
                serverlevel.getChunkSource().setViewDistance(p_11218_);
            }
        }
    }

    public void setSimulationDistance(int p_184212_) {
        this.simulationDistance = p_184212_;
        this.broadcastAll(new ClientboundSetSimulationDistancePacket(p_184212_));

        for (ServerLevel serverlevel : this.server.getAllLevels()) {
            if (serverlevel != null) {
                serverlevel.getChunkSource().setSimulationDistance(p_184212_);
            }
        }
    }

    public List<ServerPlayer> getPlayers() {
        return this.playersView; //Unmodifiable view, we don't want people removing things without us knowing.
    }

    @Nullable
    public ServerPlayer getPlayer(UUID p_11260_) {
        return this.playersByUUID.get(p_11260_);
    }

    public boolean canBypassPlayerLimit(GameProfile p_11298_) {
        return false;
    }

    public void reloadResources() {
        for (PlayerAdvancements playeradvancements : this.advancements.values()) {
            playeradvancements.reload(this.server.getAdvancements());
        }

        for (ServerPlayer player: this.players) {
            player.getAdvancements().flushDirty(player); // CraftBukkit - trigger immediate flush of advancements
        }

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.OnDatapackSyncEvent(this, null));
        this.broadcastAll(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.registries)));
        ClientboundUpdateRecipesPacket clientboundupdaterecipespacket = new ClientboundUpdateRecipesPacket(this.server.getRecipeManager().getOrderedRecipes());

        for (ServerPlayer serverplayer : this.players) {
            serverplayer.connection.send(clientboundupdaterecipespacket);
            serverplayer.getRecipeBook().sendInitialRecipeBook(serverplayer);
        }
    }

    public void reloadAdvancementData() {
        for (ServerPlayer player : this.players) {
            player.getAdvancements().reload(this.server.getAdvancements());
            player.getAdvancements().flushDirty(player); // CraftBukkit - trigger immediate flush of advancements
        }
    }

    public void reloadTagData() {
        // Paper end - API for updating recipes on clients
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.OnDatapackSyncEvent(this, null));
        this.broadcastAll(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.registries)));
        // Paper start - API for updating recipes on clients
    }
    public void reloadRecipeData() {
        // Paper end - API for updating recipes on clients
        ClientboundUpdateRecipesPacket packetplayoutrecipeupdate = new ClientboundUpdateRecipesPacket(this.server.getRecipeManager().getOrderedRecipes());

        for (ServerPlayer entityplayer : this.players) {
            entityplayer.connection.send(packetplayoutrecipeupdate);
            entityplayer.getRecipeBook().sendInitialRecipeBook(entityplayer);
        }

    }

    public boolean isAllowCommandsForAllPlayers() {
        return this.allowCommandsForAllPlayers;
    }
}
