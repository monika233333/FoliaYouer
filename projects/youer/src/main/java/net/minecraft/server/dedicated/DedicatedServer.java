package net.minecraft.server.dedicated;

import com.destroystokyo.paper.console.PaperConsole;
import com.mohistmc.youer.Metrics;
import com.mohistmc.youer.WatchMohist;
import com.mohistmc.youer.YouerConfig;
import com.mohistmc.youer.api.ServerAPI;
import com.mohistmc.youer.util.I18n;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.DefaultUncaughtExceptionHandlerWithName;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.ConsoleInput;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerInterface;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.gui.MinecraftServerGui;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.network.TextFilterClient;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.server.rcon.RconConsoleSource;
import net.minecraft.server.rcon.thread.QueryThreadGs4;
import net.minecraft.server.rcon.thread.RconThread;
import net.minecraft.util.Mth;
import net.minecraft.util.debugchart.DebugSampleSubscriptionTracker;
import net.minecraft.util.debugchart.RemoteDebugSampleType;
import net.minecraft.util.debugchart.RemoteSampleLogger;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.minecraft.util.monitoring.jmx.MinecraftServerStatistics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.io.IoBuilder;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.Main;
import org.bukkit.craftbukkit.util.ForwardLogHandler;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.slf4j.Logger;

public class DedicatedServer extends MinecraftServer implements ServerInterface {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int CONVERSION_RETRY_DELAY_MS = 5000;
    private static final int CONVERSION_RETRIES = 2;
    private final java.util.Queue<ConsoleInput> serverCommandQueue = new java.util.concurrent.ConcurrentLinkedQueue<>(); // Paper - Perf: use a proper queue
    @Nullable
    private QueryThreadGs4 queryThreadGs4;
    // public final RconConsoleSource rconConsoleSource;
    @Nullable
    private RconThread rconThread;
    public DedicatedServerSettings settings;
    @Nullable
    private MinecraftServerGui gui;
    @Nullable
    private final TextFilterClient textFilterClient;
    @Nullable
    private RemoteSampleLogger tickTimeLogger;
    @Nullable
    private DebugSampleSubscriptionTracker debugSampleSubscriptionTracker;
    public ServerLinks serverLinks;
    @Nullable
    private net.minecraft.client.server.LanServerPinger dediLanPinger;

    public DedicatedServer(
        Thread p_214789_,
        LevelStorageSource.LevelStorageAccess p_214790_,
        PackRepository p_214791_,
        WorldStem p_214792_,
        DedicatedServerSettings p_214793_,
        DataFixer p_214794_,
        Services p_214795_,
        ChunkProgressListenerFactory p_214796_
    ) {
        super(p_214789_, p_214790_, p_214791_, p_214792_, Proxy.NO_PROXY, p_214794_, p_214795_, p_214796_);
        this.settings = p_214793_;
        // this.rconConsoleSource = new RconConsoleSource(this);
        this.textFilterClient = TextFilterClient.createFromConfig(p_214793_.getProperties().textFilteringConfig);
        this.serverLinks = createServerLinks(p_214793_);
    }

    @Override
    public boolean initServer() throws IOException {
        Thread thread = new Thread("Server console handler") {
            @Override
            public void run() {
                if (net.neoforged.neoforge.server.console.TerminalHandler.handleCommands(DedicatedServer.this)) return;
                if (!Main.useConsole) {
                    return;
                }
                new PaperConsole(DedicatedServer.this).start();
            }
        };
        // CraftBukkit start - TODO: handle command-line logging arguments
        java.util.logging.Logger global = java.util.logging.Logger.getLogger("");
        global.setUseParentHandlers(false);
        for (java.util.logging.Handler handler : global.getHandlers()) {
            global.removeHandler(handler);
        }
        global.addHandler(new ForwardLogHandler());

        // Paper start - Not needed with TerminalConsoleAppender
        final org.apache.logging.log4j.Logger logger = LogManager.getRootLogger();
        System.setOut(IoBuilder.forLogger(logger).setLevel(org.apache.logging.log4j.Level.INFO).buildPrintStream());
        System.setErr(IoBuilder.forLogger(logger).setLevel(org.apache.logging.log4j.Level.WARN).buildPrintStream());
        // CraftBukkit end

        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
        // thread.start(); // Paper - Enhance console tab completions for brigadier commands; moved down
        LOGGER.info(I18n.as("minecraftserver.minecraftversion",(Object)SharedConstants.getCurrentVersion().getName()));
        if (Runtime.getRuntime().maxMemory() / 1024L / 1024L < 512L) {
            LOGGER.warn(I18n.as("minecraftserver.javamemtoolow"));
        }

        // Paper start - detect running as root
        if (io.papermc.paper.util.ServerEnvironment.userIsRootOrAdmin()) {
            DedicatedServer.LOGGER.warn("****************************");
            DedicatedServer.LOGGER.warn("YOU ARE RUNNING THIS SERVER AS AN ADMINISTRATIVE OR ROOT USER. THIS IS NOT ADVISED.");
            DedicatedServer.LOGGER.warn("YOU ARE OPENING YOURSELF UP TO POTENTIAL RISKS WHEN DOING THIS.");
            DedicatedServer.LOGGER.warn("FOR MORE INFORMATION, SEE https://madelinemiller.dev/blog/root-minecraft-server/");
            DedicatedServer.LOGGER.warn("****************************");
        }
        // Paper end - detect running as root

        LOGGER.info(I18n.as("minecraftserver.loadingproperties"));
        DedicatedServerProperties dedicatedserverproperties = this.settings.getProperties();
        if (this.isSingleplayer()) {
            this.setLocalIp("127.0.0.1");
        } else {
            this.setUsesAuthentication(dedicatedserverproperties.onlineMode);
            this.setPreventProxyConnections(dedicatedserverproperties.preventProxyConnections);
            this.setLocalIp(dedicatedserverproperties.serverIp);
        }

        this.setPlayerList(new DedicatedPlayerList(this, this.registries(), this.playerDataStorage));
        // Spigot start
        org.spigotmc.SpigotConfig.init((java.io.File) options.valueOf("spigot-settings"));
        org.spigotmc.SpigotConfig.registerCommands();
        // Spigot end
        io.papermc.paper.util.ObfHelper.INSTANCE.getClass(); // Paper - load mappings for stacktrace deobf and etc.
        // Purpur start
        try {
            org.purpurmc.purpur.PurpurConfig.init((java.io.File) options.valueOf("purpur-settings"));
        } catch (Exception e) {
            DedicatedServer.LOGGER.error("Unable to load server configuration", e);
            return false;
        }
        org.purpurmc.purpur.PurpurConfig.registerCommands();
        // Purpur end
        // Paper start - initialize global and world-defaults configuration
        this.paperConfigurations.initializeGlobalConfiguration(this.registryAccess());
        this.paperConfigurations.initializeWorldDefaultsConfiguration(this.registryAccess());
        // Paper end - initialize global and world-defaults configuration
        // Youer start
        YouerConfig.init((java.io.File) options.valueOf("youer-settings"));
        YouerConfig.registerCommands();
        // Youer end
        // Paper start - fix converting txt to json file; convert old users earlier after PlayerList creation but before file load/save
        if (this.convertOldUsers()) {
            this.getProfileCache().save(false); // Paper
        }
        this.getPlayerList().loadAndSaveFiles(); // Must be after convertNames
        // Paper end - fix converting txt to json file
        org.spigotmc.WatchdogThread.doStart(org.spigotmc.SpigotConfig.timeoutTime, org.spigotmc.SpigotConfig.restartOnCrash); // Paper - start watchdog thread
        thread.start(); // Paper - Enhance console tab completions for brigadier commands; start console thread after MinecraftServer.console & PaperConfig are initialized
        io.papermc.paper.command.PaperCommands.registerCommands(this); // Paper - setup /paper command
        gg.pufferfish.pufferfish.PufferfishConfig.pufferfishFile = (java.io.File) options.valueOf("pufferfish-settings"); // Purpur
        gg.pufferfish.pufferfish.PufferfishConfig.load(); // Pufferfish
        gg.pufferfish.pufferfish.PufferfishCommand.init(); // Pufferfish

        this.setPvpAllowed(dedicatedserverproperties.pvp);
        this.setFlightAllowed(dedicatedserverproperties.allowFlight);
        this.setMotd(dedicatedserverproperties.motd);
        super.setPlayerIdleTimeout(dedicatedserverproperties.playerIdleTimeout.get());
        this.setEnforceWhitelist(dedicatedserverproperties.enforceWhitelist);
        // this.worldData.setGameType(dedicatedserverproperties.gamemode); // CraftBukkit - moved to world loading
        LOGGER.info(I18n.as("minecraftserver.defaultgamemode", dedicatedserverproperties.gamemode));
        // Paper start - Unix domain socket support
        java.net.SocketAddress bindAddress = null;
        InetAddress inetaddress = null;
        if (this.getLocalIp().startsWith("unix:")) {
            if (!io.netty.channel.epoll.Epoll.isAvailable()) {
                DedicatedServer.LOGGER.error("**** INVALID CONFIGURATION!");
                DedicatedServer.LOGGER.error("You are trying to use a Unix domain socket but you're not on a supported OS.");
                return false;
            } else if (!io.papermc.paper.configuration.GlobalConfiguration.get().proxies.velocity.enabled && !org.spigotmc.SpigotConfig.bungee) {
                DedicatedServer.LOGGER.error("**** INVALID CONFIGURATION!");
                DedicatedServer.LOGGER.error("Unix domain sockets require IPs to be forwarded from a proxy.");
                return false;
            }
            bindAddress = new io.netty.channel.unix.DomainSocketAddress(this.getLocalIp().substring("unix:".length()));
        } else {
            if (!this.getLocalIp().isEmpty()) {
                inetaddress = InetAddress.getByName(this.getLocalIp());
            }

            if (this.getPort() < 0) {
                this.setPort(dedicatedserverproperties.serverPort);
            }
        }
        // Paper end - Unix domain socket support

        this.initializeKeyPair();
        LOGGER.info(I18n.as("minecraftserver.startingserver",this.getLocalIp().isEmpty() ? "*" : this.getLocalIp(), this.getPort()) );

        try {
            // Youer start - fix sable mod UDP
            if (bindAddress != null) {
                this.getConnection().bind(bindAddress); // Paper - Unix domain socket support
            } else {
                this.getConnection().startTcpServerListener(inetaddress, this.getPort());
            }
            // Youer end
        } catch (IOException ioexception) {
            LOGGER.warn(I18n.as("minecraftserver.bindport1"));
            LOGGER.warn(I18n.as("minecraftserver.bindport2", ioexception.getMessage()));
            LOGGER.warn(I18n.as("minecraftserver.bindport3",this.getPort()));
            throw new IllegalStateException(MinecraftServer.PORT_BIND_FAILED); // Youer
        }

        if (!this.usesAuthentication()) {
            LOGGER.warn(I18n.as("minecraftserver.playerauth1"));
            LOGGER.warn(I18n.as("minecraftserver.playerauth2"));
            LOGGER.warn(I18n.as("minecraftserver.playerauth3"));
            LOGGER.warn(I18n.as("minecraftserver.playerauth4"));
        }

        // CraftBukkit start
        server.loadPlugins();
        server.enablePlugins(org.bukkit.plugin.PluginLoadOrder.STARTUP);
        // CraftBukkit end

        if (!OldUsersConverter.serverReadyAfterUserconversion(this)) {
            return false;
        } else {
            this.debugSampleSubscriptionTracker = new DebugSampleSubscriptionTracker(this.getPlayerList());
            this.tickTimeLogger = new RemoteSampleLogger(
                TpsDebugDimensions.values().length, this.debugSampleSubscriptionTracker, RemoteDebugSampleType.TICK_TIME
            );
            long i = Util.getNanos();
            SkullBlockEntity.setup(this.services, this);
            GameProfileCache.setUsesAuthentication(this.usesAuthentication());
            net.neoforged.neoforge.server.ServerLifecycleHooks.handleServerAboutToStart(this);
            LOGGER.info("Preparing level \"{}\"", this.getLevelIdName());
            this.loadLevel();
            long j = Util.getNanos() - i;
            String s = String.format(Locale.ROOT, "%.3fs", (double)j / 1.0E9);
            DedicatedServer.LOGGER.info("Done preparing level \"{}\" ({})", this.getLevelIdName(), s); // Paper - clarify startup log messages & add total time
            this.nextTickTimeNanos = Util.getNanos(); // Neo: Update server time to prevent watchdog/spaming during long load.
            if (dedicatedserverproperties.announcePlayerAchievements != null) {
                this.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(dedicatedserverproperties.announcePlayerAchievements, this.overworld()); // CraftBukkit - per-world
            }

            if (dedicatedserverproperties.enableQuery) {
                LOGGER.info("Starting GS4 status listener");
                this.queryThreadGs4 = QueryThreadGs4.create(this);
            }

            if (dedicatedserverproperties.enableRcon) {
                LOGGER.info("Starting remote control listener");
                this.rconThread = RconThread.create(this);
            }

            if (false && this.getMaxTickLength() > 0L) { // Spigot - disable
                Thread thread1 = new Thread(new ServerWatchdog(this));
                thread1.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandlerWithName(LOGGER));
                thread1.setName("Server Watchdog");
                thread1.setDaemon(true);
                thread1.start();
            }

            if (dedicatedserverproperties.enableJmxMonitoring) {
                MinecraftServerStatistics.registerJmxMonitoring(this);
                LOGGER.info("JMX monitoring enabled");
            }

            if (net.neoforged.neoforge.common.NeoForgeConfig.SERVER.advertiseDedicatedServerToLan.get()) {
                this.dediLanPinger = new net.minecraft.client.server.LanServerPinger(this.getMotd(), String.valueOf(this.getServerPort()));
                this.dediLanPinger.start();
            }
            Metrics.MohistMetrics.startMetrics();
            WatchMohist.start();
            net.neoforged.neoforge.server.ServerLifecycleHooks.handleServerStarting(this);
            org.purpurmc.purpur.task.BossBarTask.startAll(); // Purpur
            return true;
        }
    }

    // Paper start
    public java.io.File getPluginsFolder() {
        return (java.io.File) this.options.valueOf("plugins");
    }
    // Paper end

    @Override
    public boolean isSpawningAnimals() {
        return this.getProperties().spawnAnimals && super.isSpawningAnimals();
    }

    @Override
    public boolean isSpawningMonsters() {
        return this.settings.getProperties().spawnMonsters && super.isSpawningMonsters();
    }

    @Override
    public boolean areNpcsEnabled() {
        return this.settings.getProperties().spawnNpcs && super.areNpcsEnabled();
    }

    @Override
    public DedicatedServerProperties getProperties() {
        return this.settings.getProperties();
    }

    @Override
    public void forceDifficulty() {
        // this.setDifficulty(this.getProperties().difficulty, true); // Paper - per level difficulty; Don't overwrite level.dat's difficulty, keep current
    }

    @Override
    public boolean isHardcore() {
        return this.getProperties().hardcore;
    }

    @Override
    public SystemReport fillServerSystemReport(SystemReport p_142870_) {
        p_142870_.setDetail("Is Modded", () -> this.getModdedStatus().fullDescription());
        p_142870_.setDetail("Type", () -> "Dedicated Server (map_server.txt)");
        return p_142870_;
    }

    @Override
    public void dumpServerProperties(Path p_142872_) throws IOException {
        DedicatedServerProperties dedicatedserverproperties = this.getProperties();

        try (Writer writer = Files.newBufferedWriter(p_142872_)) {
            writer.write(String.format(Locale.ROOT, "sync-chunk-writes=%s%n", dedicatedserverproperties.syncChunkWrites));
            writer.write(String.format(Locale.ROOT, "gamemode=%s%n", dedicatedserverproperties.gamemode));
            writer.write(String.format(Locale.ROOT, "spawn-monsters=%s%n", dedicatedserverproperties.spawnMonsters));
            writer.write(String.format(Locale.ROOT, "entity-broadcast-range-percentage=%d%n", dedicatedserverproperties.entityBroadcastRangePercentage));
            writer.write(String.format(Locale.ROOT, "max-world-size=%d%n", dedicatedserverproperties.maxWorldSize));
            writer.write(String.format(Locale.ROOT, "spawn-npcs=%s%n", dedicatedserverproperties.spawnNpcs));
            writer.write(String.format(Locale.ROOT, "view-distance=%d%n", dedicatedserverproperties.viewDistance));
            writer.write(String.format(Locale.ROOT, "simulation-distance=%d%n", dedicatedserverproperties.simulationDistance));
            writer.write(String.format(Locale.ROOT, "spawn-animals=%s%n", dedicatedserverproperties.spawnAnimals));
            writer.write(String.format(Locale.ROOT, "generate-structures=%s%n", dedicatedserverproperties.worldOptions.generateStructures()));
            writer.write(String.format(Locale.ROOT, "use-native=%s%n", dedicatedserverproperties.useNativeTransport));
            writer.write(String.format(Locale.ROOT, "rate-limit=%d%n", dedicatedserverproperties.rateLimitPacketsPerSecond));
        }
    }

    @Override
    public void onServerExit() {
        if (this.textFilterClient != null) {
            this.textFilterClient.close();
        }

        if (this.gui != null) {
            this.gui.close();
        }

        if (this.rconThread != null) {
            this.rconThread.stopNonBlocking(); // Paper - don't wait for remote connections
        }

        if (this.queryThreadGs4 != null) {
            // this.remoteStatusListener.stop(); // Paper - don't wait for remote connections
        }
        if (this.dediLanPinger != null) {
            this.dediLanPinger.interrupt();
            this.dediLanPinger = null;
        }

        if (ServerAPI.yes_steve_model()) {
            System.exit(this.abnormalExit ? 70 : 0); // CraftBukkit // Paper
        } else {
            Runtime.getRuntime().halt(this.abnormalExit ? 70 : 0); // CraftBukkit // Paper
        }
    }

    @Override
    public void tickChildren(BooleanSupplier p_139661_) { // FoliaYouer - keep original signature
        super.tickChildren(p_139661_);
        if (io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegion() == null) this.handleConsoleInputs(); // Folia - region threading
    }

    @Override
    public boolean isLevelEnabled(Level p_350654_) {
        return p_350654_.dimension() == Level.NETHER ? this.getProperties().allowNether : true;
    }

    public void handleConsoleInput(String p_139646_, CommandSourceStack p_139647_) {
        this.serverCommandQueue.add(new ConsoleInput(p_139646_, p_139647_)); // Paper - Perf: use proper queue
    }

    public void handleConsoleInputs() {
        // Paper start - Perf: use proper queue
        ConsoleInput servercommand;
        while ((servercommand = this.serverCommandQueue.poll()) != null) {
            // Paper end - Perf: use proper queue

            // CraftBukkit start - ServerCommand for preprocessing
            ServerCommandEvent event = new ServerCommandEvent(this.console, servercommand.msg);
            this.server.getPluginManager().callEvent(event);
            if (event.isCancelled()) continue;
            servercommand = new ConsoleInput(event.getCommand(), servercommand.source);

            // this.getCommands().performPrefixedCommand(servercommand.source, servercommand.msg); // Called in dispatchServerCommand
            this.server.dispatchServerCommand(this.console, servercommand);
            // CraftBukkit end
        }
    }

    @Override
    public boolean isDedicatedServer() {
        return true;
    }

    @Override
    public int getRateLimitPacketsPerSecond() {
        return this.getProperties().rateLimitPacketsPerSecond;
    }

    @Override
    public boolean isEpollEnabled() {
        return this.getProperties().useNativeTransport;
    }

    public DedicatedPlayerList getPlayerList() {
        return (DedicatedPlayerList)super.getPlayerList();
    }

    @Override
    public boolean isPublished() {
        return true;
    }

    @Override
    public String getServerIp() {
        return this.getLocalIp();
    }

    @Override
    public int getServerPort() {
        return this.getPort();
    }

    @Override
    public String getServerName() {
        return this.getMotd();
    }

    public void showGui() {
        if (this.gui == null) {
            this.gui = MinecraftServerGui.showFrameFor(this);
        }
    }

    @Override
    public boolean hasGui() {
        return this.gui != null;
    }

    @Override
    public boolean isCommandBlockEnabled() {
        return this.getProperties().enableCommandBlock;
    }

    @Override
    public int getSpawnProtectionRadius() {
        return this.getProperties().spawnProtection;
    }

    @Override
    public boolean isUnderSpawnProtection(ServerLevel p_139630_, BlockPos p_139631_, Player p_139632_) {
        if (p_139630_.dimension() != Level.OVERWORLD) {
            return false;
        } else if (this.getPlayerList().getOps().isEmpty()) {
            return false;
        } else if (this.getPlayerList().isOp(p_139632_.getGameProfile())) {
            return false;
        } else if (this.getSpawnProtectionRadius() <= 0) {
            return false;
        } else {
            BlockPos blockpos = p_139630_.getSharedSpawnPos();
            int i = Mth.abs(p_139631_.getX() - blockpos.getX());
            int j = Mth.abs(p_139631_.getZ() - blockpos.getZ());
            int k = Math.max(i, j);
            return k <= this.getSpawnProtectionRadius();
        }
    }

    @Override
    public boolean repliesToStatus() {
        return this.getProperties().enableStatus;
    }

    @Override
    public boolean hidesOnlinePlayers() {
        return this.getProperties().hideOnlinePlayers;
    }

    @Override
    public int getOperatorUserPermissionLevel() {
        return this.getProperties().opPermissionLevel;
    }

    @Override
    public int getFunctionCompilationLevel() {
        return this.getProperties().functionPermissionLevel;
    }

    @Override
    public void setPlayerIdleTimeout(int p_139676_) {
        super.setPlayerIdleTimeout(p_139676_);
        this.settings.update(p_349960_ -> p_349960_.playerIdleTimeout.update(this.registryAccess(), p_139676_));
    }

    @Override
    public boolean shouldRconBroadcast() {
        return this.getProperties().broadcastRconToOps;
    }

    @Override
    public boolean shouldInformAdmins() {
        return this.getProperties().broadcastConsoleToOps;
    }

    @Override
    public int getAbsoluteMaxWorldSize() {
        return this.getProperties().maxWorldSize;
    }

    @Override
    public int getCompressionThreshold() {
        return this.getProperties().networkCompressionThreshold;
    }

    @Override
    public boolean enforceSecureProfile() {
        DedicatedServerProperties dedicatedserverproperties = this.getProperties();
        return dedicatedserverproperties.enforceSecureProfile && io.papermc.paper.configuration.GlobalConfiguration.get().proxies.isProxyOnlineMode() && this.services.canValidateProfileKeys();
    }

    @Override
    public boolean logIPs() {
        return this.getProperties().logIPs;
    }

    protected boolean convertOldUsers() {
        boolean flag = false;

        for (int i = 0; !flag && i <= 2; i++) {
            if (i > 0) {
                LOGGER.warn("Encountered a problem while converting the user banlist, retrying in a few seconds");
                this.waitForRetry();
            }

            flag = OldUsersConverter.convertUserBanlist(this);
        }

        boolean flag1 = false;

        for (int j = 0; !flag1 && j <= 2; j++) {
            if (j > 0) {
                LOGGER.warn("Encountered a problem while converting the ip banlist, retrying in a few seconds");
                this.waitForRetry();
            }

            flag1 = OldUsersConverter.convertIpBanlist(this);
        }

        boolean flag2 = false;

        for (int k = 0; !flag2 && k <= 2; k++) {
            if (k > 0) {
                LOGGER.warn("Encountered a problem while converting the op list, retrying in a few seconds");
                this.waitForRetry();
            }

            flag2 = OldUsersConverter.convertOpsList(this);
        }

        boolean flag3 = false;

        for (int l = 0; !flag3 && l <= 2; l++) {
            if (l > 0) {
                LOGGER.warn("Encountered a problem while converting the whitelist, retrying in a few seconds");
                this.waitForRetry();
            }

            flag3 = OldUsersConverter.convertWhiteList(this);
        }

        boolean flag4 = false;

        for (int i1 = 0; !flag4 && i1 <= 2; i1++) {
            if (i1 > 0) {
                LOGGER.warn("Encountered a problem while converting the player save files, retrying in a few seconds");
                this.waitForRetry();
            }

            flag4 = OldUsersConverter.convertPlayers(this);
        }

        return flag || flag1 || flag2 || flag3 || flag4;
    }

    private void waitForRetry() {
        try {
            Thread.sleep(5000L);
        } catch (InterruptedException interruptedexception) {
        }
    }

    public long getMaxTickLength() {
        return this.getProperties().maxTickTime;
    }

    @Override
    public int getMaxChainedNeighborUpdates() {
        return this.getProperties().maxChainedNeighborUpdates;
    }

    @Override
    public String getPluginNames() {
        // CraftBukkit start - Whole method
        StringBuilder result = new StringBuilder();
        org.bukkit.plugin.Plugin[] plugins = this.server.getPluginManager().getPlugins();

        result.append(this.server.getName());
        result.append(" on Bukkit ");
        result.append(this.server.getBukkitVersion());

        if (plugins.length > 0 && this.server.getQueryPlugins()) {
            result.append(": ");

            for (int i = 0; i < plugins.length; i++) {
                if (i > 0) {
                    result.append("; ");
                }

                result.append(plugins[i].getDescription().getName());
                result.append(" ");
                result.append(plugins[i].getDescription().getVersion().replaceAll(";", ","));
            }
        }

        return result.toString();
        // CraftBukkit end
    }

    @Override
    public String runCommand(String p_139644_) {
        throw new UnsupportedOperationException("Not supported - remote source required.");
    }

    public String runCommand(RconConsoleSource rconConsoleSource, String s) {
        rconConsoleSource.prepareForCommand();
        this.executeBlocking(() -> {
            CommandSourceStack wrapper = rconConsoleSource.createCommandSourceStack();
            RemoteServerCommandEvent event = new RemoteServerCommandEvent(rconConsoleSource.getBukkitSender(wrapper), s);
            server.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
            ConsoleInput serverCommand = new ConsoleInput(event.getCommand(), wrapper);
            server.dispatchServerCommand(event.getSender(), serverCommand);
        });
        return rconConsoleSource.getCommandResponse();
        // CraftBukkit end
    }

    public void storeUsingWhiteList(boolean p_139689_) {
        this.settings.update(p_349962_ -> p_349962_.whiteList.update(this.registryAccess(), p_139689_));
    }

    @Override
    public void stopServer() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.GameShuttingDownEvent());
        super.stopServer();
        if (this.dediLanPinger != null) {
            this.dediLanPinger.interrupt();
            this.dediLanPinger = null;
        }
        //Util.shutdownExecutors(); // Paper - moved into super
        SkullBlockEntity.clear();
    }

    @Override
    public boolean isSingleplayerOwner(GameProfile p_139642_) {
        return false;
    }

    @Override
    public int getScaledTrackingDistance(int p_139659_) {
        return this.getProperties().entityBroadcastRangePercentage * p_139659_ / 100;
    }

    @Override
    public String getLevelIdName() {
        return this.storageSource.getLevelId();
    }

    @Override
    public boolean forceSynchronousWrites() {
        return this.settings.getProperties().syncChunkWrites;
    }

    @Override
    public TextFilter createTextFilterForPlayer(ServerPlayer p_139634_) {
        return this.textFilterClient != null ? this.textFilterClient.createContext(p_139634_.getGameProfile()) : TextFilter.DUMMY;
    }

    @Nullable
    @Override
    public GameType getForcedGameType() {
        return this.settings.getProperties().forceGameMode ? this.worldData.getGameType() : null;
    }

    @Override
    public Optional<MinecraftServer.ServerResourcePackInfo> getServerResourcePack() {
        return this.settings.getProperties().serverResourcePackInfo;
    }

    @Override
    public void endMetricsRecordingTick() {
        super.endMetricsRecordingTick();
        this.debugSampleSubscriptionTracker.tick(this.getTickCount());
    }

    @Override
    public SampleLogger getTickTimeLogger() {
        return this.tickTimeLogger;
    }

    @Override
    public boolean isTickTimeLoggingEnabled() {
        return this.debugSampleSubscriptionTracker.shouldLogSamples(RemoteDebugSampleType.TICK_TIME);
    }

    @Override
    public void subscribeToDebugSample(ServerPlayer p_324272_, RemoteDebugSampleType p_324213_) {
        this.debugSampleSubscriptionTracker.subscribe(p_324272_, p_324213_);
    }

    @Override
    public boolean acceptsTransfers() {
        return this.settings.getProperties().acceptsTransfers;
    }

    @Override
    public ServerLinks serverLinks() {
        return this.serverLinks;
    }

    private static ServerLinks createServerLinks(DedicatedServerSettings p_352317_) {
        Optional<URI> optional = parseBugReportLink(p_352317_.getProperties());
        return optional.<ServerLinks>map(p_351772_ -> new ServerLinks(List.of(ServerLinks.KnownLinkType.BUG_REPORT.create(p_351772_))))
            .orElse(ServerLinks.EMPTY);
    }

    private static Optional<URI> parseBugReportLink(DedicatedServerProperties p_352150_) {
        String s = p_352150_.bugReportLink;
        if (s.isEmpty()) {
            return Optional.empty();
        } else {
            try {
                return Optional.of(Util.parseAndValidateUntrustedUri(s));
            } catch (Exception exception) {
                LOGGER.warn("Failed to parse bug link {}", s, exception);
                return Optional.empty();
            }
        }
    }

    @Override
    public boolean isDebugging() {
        return this.getProperties().debug;
    }

    @Override
    public CommandSender getBukkitSender(CommandSourceStack wrapper) {
        return console;
    }
}
