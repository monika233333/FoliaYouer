package net.minecraft.server;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.mohistmc.youer.YouerConfig;
import com.mohistmc.youer.api.ColorAPI;
import com.mohistmc.youer.api.ServerAPI;
import com.mohistmc.youer.feature.YouerPlugin;
import com.mohistmc.youer.feature.ban.bans.BanWorld;
import com.mohistmc.youer.neoforge.NeoForgeInjectBukkit;
import com.mohistmc.youer.util.I18n;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventRunner;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEventTypeProviderImpl;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.Proxy;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import joptsimple.OptionSet;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.FileUtil;
import net.minecraft.ReportType;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.Util;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.DemoMode;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;
import net.minecraft.util.ModCheck;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeModuleLister;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.debugchart.RemoteDebugSampleType;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.minecraft.util.profiling.EmptyProfileResults;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.ResultField;
import net.minecraft.util.profiling.SingleTickProfiler;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.util.profiling.metrics.profiling.ActiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.InactiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.MetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.ServerMetricsSamplersProvider;
import net.minecraft.util.profiling.metrics.storage.MetricsPersister;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.CommandStorage;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.network.payload.ClientboundCustomSetTimePayload;
import net.neoforged.neoforge.resource.ResourcePackLoader;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.help.SimpleHelpMap;
import org.bukkit.craftbukkit.scoreboard.CraftScoreboardManager;
import org.bukkit.craftbukkit.util.ServerShutdownThread;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.plugin.PluginLoadOrder;
import org.slf4j.Logger;
import org.spigotmc.SpigotConfig;

public abstract class MinecraftServer extends ReentrantBlockableEventLoop<TickTask> implements ServerInfo, ChunkIOErrorReporter, CommandSource, AutoCloseable, ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer { // Folia - rewrite chunk system
    public static final String PORT_BIND_FAILED = "PORT_BIND_FAILED"; // Youer
    private static MinecraftServer SERVER; // Paper
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final net.kyori.adventure.text.logger.slf4j.ComponentLogger COMPONENT_LOGGER = net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger(LOGGER.getName()); // Paper
    public static final String VANILLA_BRAND = "vanilla";
    private static final float AVERAGE_TICK_TIME_SMOOTHING = 0.8F;
    private static final int TICK_STATS_SPAN = 100;
    private static final long OVERLOADED_THRESHOLD_NANOS = 20L * TimeUtil.NANOSECONDS_PER_SECOND / 20L;
    private static final int OVERLOADED_TICKS_THRESHOLD = 20;
    private static final long OVERLOADED_WARNING_INTERVAL_NANOS = 10L * TimeUtil.NANOSECONDS_PER_SECOND;
    private static final int OVERLOADED_TICKS_WARNING_INTERVAL = 100;
    private static final long PREPARE_LEVELS_DEFAULT_DELAY_NANOS = 10L * TimeUtil.NANOSECONDS_PER_MILLISECOND;
    private static final int MAX_STATUS_PLAYER_SAMPLE = 12;
    private static final int SPAWN_POSITION_SEARCH_RADIUS = 5;
    private static final int AUTOSAVE_INTERVAL = 6000;
    private static final int MIMINUM_AUTOSAVE_TICKS = 100;
    private static final int MAX_TICK_LATENCY = 3;
    public static final int ABSOLUTE_MAX_WORLD_SIZE = 29999984;
    public static final LevelSettings DEMO_SETTINGS = new LevelSettings(
        "Demo World", GameType.SURVIVAL, false, Difficulty.NORMAL, false, new GameRules(), WorldDataConfiguration.DEFAULT
    );
    public static final GameProfile ANONYMOUS_PLAYER_PROFILE = new GameProfile(Util.NIL_UUID, "Anonymous Player");
    public LevelStorageSource.LevelStorageAccess storageSource;
    public final PlayerDataStorage playerDataStorage;
    private final List<Runnable> tickables = Lists.newArrayList();
    private MetricsRecorder metricsRecorder = InactiveMetricsRecorder.INSTANCE;
    private ProfilerFiller profiler = this.metricsRecorder.getProfiler();
    private Consumer<ProfileResults> onMetricsRecordingStopped = p_177903_ -> this.stopRecordingMetrics();
    private Consumer<Path> onMetricsRecordingFinished = p_177954_ -> {
    };
    private boolean willStartRecordingMetrics;
    @Nullable
    private TimeProfiler debugCommandProfiler;
    private boolean debugCommandProfilerDelayStart;
    public ServerConnectionListener connection;
    public final ChunkProgressListenerFactory progressListenerFactory;
    @Nullable
    private ServerStatus status;
    @Nullable
    private ServerStatus.Favicon statusIcon;
    private final RandomSource random = RandomSource.create();
    public final DataFixer fixerUpper;
    private String localIp;
    private int port = -1;
    private final LayeredRegistryAccess<RegistryLayer> registries;
    public Map<ResourceKey<Level>, ServerLevel> levels = Maps.newLinkedHashMap();
    private PlayerList playerList;
    private volatile boolean running = true;
    private volatile boolean isRestarting = false; // Paper - flag to signify we're attempting to restart
    private boolean stopped;
    private int tickCount;
    private int ticksUntilAutosave = 6000;
    protected final Proxy proxy;
    private boolean onlineMode;
    private boolean preventProxyConnections;
    private boolean pvp;
    private boolean allowFlight;
    @Nullable
    private net.kyori.adventure.text.Component motd; // Paper - Adventure
    private int playerIdleTimeout;
    private final long[] tickTimesNanos = new long[100];
    private long aggregatedTickTimesNanos = 0L;
    // Paper start - Add tick times API and /mspt command
    public final TickTimes tickTimes5s = new TickTimes(100);
    public final TickTimes tickTimes10s = new TickTimes(200);
    public final TickTimes tickTimes60s = new TickTimes(1200);
    // Paper end - Add tick times API and /mspt command
    @Nullable
    private KeyPair keyPair;
    @Nullable
    private GameProfile singleplayerProfile;
    private boolean isDemo;
    private volatile boolean isReady;
    private long lastOverloadWarningNanos;
    protected final Services services;
    private long lastServerStatus;
    public final Thread serverThread;
    private long lastTickNanos = Util.getNanos();
    private long taskExecutionStartNanos = Util.getNanos();
    private long idleTimeNanos;
    protected long nextTickTimeNanos = Util.getNanos();
    private long delayedTasksMaxNextTickTimeNanos;
    private boolean mayHaveDelayedTasks;
    private final PackRepository packRepository;
    private final ServerScoreboard scoreboard = new ServerScoreboard(this);
    @Nullable
    private CommandStorage commandStorage;
    private final CustomBossEvents customBossEvents = new CustomBossEvents();
    private final ServerFunctionManager functionManager;
    private boolean enforceWhitelist;
    private float smoothedTickTimeMillis;
    public final Executor executor;
    @Nullable
    private String serverId;
    public ReloadableResources resources;
    private final StructureTemplateManager structureTemplateManager;
    private final ServerTickRateManager tickRateManager;
    public WorldData worldData;
    public PotionBrewing potionBrewing;
    private volatile boolean isSaving;
    private static final AtomicReference<RuntimeException> fatalException = new AtomicReference<>();
    // CraftBukkit start
    public WorldLoader.DataLoadContext worldLoader;
    public CraftServer server;
    public OptionSet options;
    public ConsoleCommandSender console;
    public static int currentTick; // Paper - improve tick loop
    public static final long startTimeMillis = System.currentTimeMillis();
    public Queue<Runnable> processQueue = new ConcurrentLinkedQueue<Runnable>();
    public int autosavePeriod;
    // Paper - don't store the vanilla dispatcher
    public boolean forceTicks; // Paper - Improved watchdog support
    // CraftBukkit end

    // Folia start - region threading
    public final io.papermc.paper.threadedregions.RegionizedServer regionizedServer = new io.papermc.paper.threadedregions.RegionizedServer();
    public static final long STATUS_EXPIRE_TIME_NANOS = 5L * TimeUtil.NANOSECONDS_PER_SECOND; // Folia - region threading - public
    private volatile Throwable chunkSystemCrash;

    @Override
    public final void moonrise$setChunkSystemCrash(final Throwable throwable) {
        this.chunkSystemCrash = throwable;
    }

    private static final long CHUNK_TASK_QUEUE_BACKOFF_MIN_TIME = 25L * 1000L; // 25us
    private static final long MAX_CHUNK_EXEC_TIME = 1000L; // 1us
    private static final long TASK_EXECUTION_FAILURE_BACKOFF = 5L * 1000L; // 5us

    private boolean tickMidTickTasks(final io.papermc.paper.threadedregions.RegionizedWorldData worldData) {
        return false;
    }

    @Override
    public final void moonrise$executeMidTickTasks() {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData();
        if (worldData == null) return;
        final long startTime = System.nanoTime();
        if ((startTime - worldData.lastMidTickExecute) <= CHUNK_TASK_QUEUE_BACKOFF_MIN_TIME || (startTime - worldData.lastMidTickExecuteFailure) <= TASK_EXECUTION_FAILURE_BACKOFF) {
            return;
        }
        for (;;) {
            final boolean moreTasks = this.tickMidTickTasks(worldData);
            final long endTime = System.nanoTime();
            if (!moreTasks || (endTime - startTime) > MAX_CHUNK_EXEC_TIME) {
                worldData.lastMidTickExecute = endTime;
                break;
            }
        }
    }
    // Folia end - region threading

    private long nextTickTime = 0; // Youer Add fake tickField worldedit need this

    // Spigot start
    public static final int TPS = 20;
    public static final int TICK_TIME = 1000000000 / TPS;
    private static final int SAMPLE_INTERVAL = 20; // Paper - improve server tick loop
    @Deprecated(forRemoval = true) // Paper
    public final double[] recentTps = new double[ 4 ];
    // Spigot end
    public final io.papermc.paper.configuration.PaperConfigurations paperConfigurations; // Paper - add paper configuration files
    public boolean isIteratingOverLevels = false; // Paper - Throw exception on world create while being ticked
    public boolean lagging = false; // Purpur
    public volatile Thread shutdownThread; // Paper
    public volatile boolean abnormalExit = false; // Paper
    private final AtomicBoolean hasStartedShutdownThread = new AtomicBoolean(false); // Folia - region threading
    public static final long SERVER_INIT = System.nanoTime(); // Paper - Lag compensation
    public gg.pufferfish.pufferfish.util.AsyncExecutor mobSpawnExecutor = new gg.pufferfish.pufferfish.util.AsyncExecutor("MobSpawning"); // Pufferfish - optimize mob spawning

    public static <S extends MinecraftServer> S spin(Function<Thread, S> p_129873_) {
        AtomicReference<S> atomicreference = new AtomicReference<>();
        Thread thread = new ca.spottedleaf.moonrise.common.util.TickThread(() -> atomicreference.get().runServer(), "Server thread");
        thread.setUncaughtExceptionHandler((p_177909_, p_177910_) -> LOGGER.error(I18n.as("minecraftserver.1"), p_177910_));
        thread.setPriority(Thread.NORM_PRIORITY + 2); // Paper - Perf: Boost priority
        if (Runtime.getRuntime().availableProcessors() > 4) {
            thread.setPriority(8);
        }

        S s = (S)p_129873_.apply(thread);
        atomicreference.set(s);
        thread.start();
        return s;
    }

    public void worldLoader(WorldLoader.DataLoadContext worldLoader, OptionSet options) {
        this.worldLoader = worldLoader;
        this.options = options;
    }

    public MinecraftServer(
        Thread p_236723_,
        LevelStorageSource.LevelStorageAccess p_236724_,
        PackRepository p_236725_,
        WorldStem p_236726_,
        Proxy p_236727_,
        DataFixer p_236728_,
        Services p_236729_,
        ChunkProgressListenerFactory p_236730_
    ) {
        super("Server");
        SERVER = this; // Paper - better singleton
        this.registries = p_236726_.registries();
        this.worldData = p_236726_.worldData();
        if (!this.registries.compositeAccess().registryOrThrow(Registries.LEVEL_STEM).containsKey(LevelStem.OVERWORLD)) {
            throw new IllegalStateException(I18n.as("minecraftserver.2"));
        } else {
            this.proxy = p_236727_;
            this.packRepository = p_236725_;
            this.resources = new ReloadableResources(p_236726_.resourceManager(), p_236726_.dataPackResources());
            this.services = p_236729_;
            if (p_236729_.profileCache() != null) {
                p_236729_.profileCache().setExecutor(this);
            }

            this.connection = new ServerConnectionListener(this);
            this.tickRateManager = new ServerTickRateManager(this);
            this.progressListenerFactory = p_236730_;
            this.storageSource = p_236724_;
            this.playerDataStorage = p_236724_.createPlayerStorage();
            this.fixerUpper = p_236728_;
            this.functionManager = new ServerFunctionManager(this, this.resources.managers.getFunctionLibrary());
            HolderGetter<Block> holdergetter = this.registries
                .compositeAccess()
                .registryOrThrow(Registries.BLOCK)
                .asLookup()
                .filterFeatures(this.worldData.enabledFeatures());
            this.structureTemplateManager = new StructureTemplateManager(p_236726_.resourceManager(), p_236724_, p_236728_, holdergetter);
            this.serverThread = p_236723_;
            this.executor = Util.backgroundExecutor();
            this.potionBrewing = PotionBrewing.bootstrap(this.worldData.enabledFeatures(), this.registryAccess());

            io.papermc.paper.util.LogManagerShutdownThread.unhook(); // Paper
            Runtime.getRuntime().addShutdownHook(new ServerShutdownThread(this));
            this.paperConfigurations = services.paperConfigurations(); // Paper - add paper configuration files
        }
    }

    private void readScoreboard(DimensionDataStorage p_129842_) {
        p_129842_.computeIfAbsent(this.getScoreboard().dataFactory(), "scoreboard");
    }

    protected abstract boolean initServer() throws IOException;

    // CraftBukkit start
    public void initWorld(ServerLevel serverlevel, ServerLevelData serverleveldata, WorldData saveData,  WorldOptions worldoptions) {
        boolean flag = saveData.isDebugWorld();
        // CraftBukkit start
        if (serverlevel.generator != null) {
            serverlevel.getWorld().getPopulators().addAll(serverlevel.generator.getDefaultPopulators(serverlevel.getWorld()));
        }
        WorldBorder worldborder = serverlevel.getWorldBorder();
        worldborder.applySettings(serverleveldata.getWorldBorder()); // CraftBukkit - move up so that WorldBorder is set during WorldInitEvent
        Bukkit.getPluginManager().callEvent(new WorldInitEvent(serverlevel.getWorld()));

        if (!serverleveldata.isInitialized()) {
            try {
                setInitialSpawn(serverlevel, serverleveldata, worldoptions.generateBonusChest(), flag);
                serverleveldata.setInitialized(true);
                if (flag) {
                    this.setupDebugLevel(this.worldData);
                }
            } catch (Throwable throwable1) {
                CrashReport crashreport = CrashReport.forThrowable(throwable1, I18n.as("minecraftserver.3"));

                try {
                    serverlevel.fillReportDetails(crashreport);
                } catch (Throwable throwable) {
                }

                throw new ReportedException(crashreport);
            }

            serverleveldata.setInitialized(true);
        }
    }
    // CraftBukkit end

    private void executeModerately() {
        this.runAllTasks();
        LockSupport.parkNanos("executing tasks", 1000L);
        // CraftBukkit end
    }

    // Paper start - rewrite chunk system
    @Override
    public boolean isSameThread() {
        return ca.spottedleaf.moonrise.common.util.TickThread.isTickThread();
    }
    // Paper end - rewrite chunk system

    public boolean isDebugging() {
        return false;
    }

    public static MinecraftServer getServer() {
        return SERVER; // Paper
    }

    @Deprecated
    public static RegistryAccess getDefaultRegistryAccess() {
        return CraftRegistry.getMinecraftRegistry();
    }

    protected void loadLevel() {
        if (!JvmProfiler.INSTANCE.isRunning()) {
        }

        boolean flag = false;
        ProfiledDuration profiledduration = JvmProfiler.INSTANCE.onWorldLoadedStarted();
        this.worldData.setModdedInfo(this.getServerModName(), this.getModdedStatus().shouldReportAsModified());
        ChunkProgressListener chunkprogresslistener = this.progressListenerFactory
            .create(this.worldData.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS));
        this.createLevels(chunkprogresslistener);
        this.forceDifficulty();
        this.prepareLevels(chunkprogresslistener);
        if (profiledduration != null) {
            profiledduration.finish();
        }

        if (flag) {
            try {
                JvmProfiler.INSTANCE.stop();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed to stop JFR profiling", throwable);
            }
        }
    }

    protected void forceDifficulty() {
    }

    protected void createLevels(ChunkProgressListener p_129816_) {
        ServerLevelData serverleveldata = this.worldData.overworldData();
        boolean flag = this.worldData.isDebugWorld();
        Registry<LevelStem> registry = this.registries.compositeAccess().registryOrThrow(Registries.LEVEL_STEM);
        WorldOptions worldoptions = this.worldData.worldGenOptions();
        long i = worldoptions.seed();
        long j = BiomeManager.obfuscateSeed(i);
        List<CustomSpawner> list = ImmutableList.of(
            new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(serverleveldata)
        );
        LevelStem levelstem = registry.get(LevelStem.OVERWORLD);
        ServerLevel serverlevel = new ServerLevel(
            this, this.executor, this.storageSource, serverleveldata, Level.OVERWORLD, levelstem, p_129816_, flag, j, list, true, null
        );
        this.levels.put(Level.OVERWORLD, serverlevel);
        // CraftBukkit start
        if (serverlevel.generator != null) {
            serverlevel.getWorld().getPopulators().addAll(serverlevel.generator.getDefaultPopulators(serverlevel.getWorld()));
        }
        Bukkit.getPluginManager().callEvent(new WorldInitEvent(serverlevel.getWorld()));
        DimensionDataStorage dimensiondatastorage = serverlevel.getDataStorage();
        this.readScoreboard(dimensiondatastorage);
        this.server.scoreboardManager = new CraftScoreboardManager(this, serverlevel.getScoreboard());
        this.commandStorage = new CommandStorage(dimensiondatastorage);
        WorldBorder worldborder = serverlevel.getWorldBorder();
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(levels.get(Level.OVERWORLD)));

        if (!serverleveldata.isInitialized()) {
            try {
                setInitialSpawn(serverlevel, serverleveldata, worldoptions.generateBonusChest(), flag);
                serverleveldata.setInitialized(true);
                if (flag) {
                    this.setupDebugLevel(this.worldData);
                }
            } catch (Throwable throwable1) {
                CrashReport crashreport = CrashReport.forThrowable(throwable1, I18n.as("minecraftserver.3"));

                try {
                    serverlevel.fillReportDetails(crashreport);
                } catch (Throwable throwable) {
                }

                throw new ReportedException(crashreport);
            }

            serverleveldata.setInitialized(true);
        }

        this.getPlayerList().addWorldborderListener(serverlevel);
        if (this.worldData.getCustomBossEvents() != null) {
            this.getCustomBossEvents().load(this.worldData.getCustomBossEvents(), this.registryAccess());
        }

        RandomSequences randomsequences = serverlevel.getRandomSequences();

        for (Entry<ResourceKey<LevelStem>, LevelStem> entry : registry.entrySet()) {
            ResourceKey<LevelStem> resourcekey = entry.getKey();
            if (resourcekey != LevelStem.OVERWORLD) {
                if (BanWorld.checkBan(resourcekey.location())) continue;
                ResourceKey<Level> resourcekey1 = ResourceKey.create(Registries.DIMENSION, resourcekey.location());
                DerivedLevelData derivedleveldata = new DerivedLevelData(this.worldData, serverleveldata);

                String name = resourcekey == LevelStem.NETHER ? "DIM1" : "DIM-1";
                if (resourcekey == LevelStem.NETHER) {
                    if (!this.server.getAllowNether()) {
                        continue;
                    }
                } else if (resourcekey == LevelStem.END) {
                    if (!this.server.getAllowEnd()) {
                        continue;
                    }
                } else if (resourcekey != LevelStem.OVERWORLD) {
                    name = resourcekey.location().getNamespace() + "_" + resourcekey.location().getPath();
                }
                NeoForgeInjectBukkit.addEnumEnvironment(this.registryAccess().registryOrThrow(Registries.LEVEL_STEM));
                Level.craftWorldData(NeoForgeInjectBukkit.environment.get(resourcekey), this.server.getGenerator(name), this.server.getBiomeProvider(name));
                ChunkProgressListener worldloadlistener = this.progressListenerFactory.create(this.worldData.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS));
                // Paper start - option to use the dimension_type to check if spawners should be added. I imagine mojang will add some datapack-y way of managing this in the future.
                final List<CustomSpawner> spawners;
                if (io.papermc.paper.configuration.GlobalConfiguration.get().misc.useDimensionTypeForCustomSpawners && this.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).getResourceKey(entry.getValue().type().value()).orElseThrow() == net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD) {
                    spawners = list;
                } else {
                    spawners = Collections.emptyList();
                }
                ServerLevel serverlevel1 = new ServerLevel(
                    this,
                    this.executor,
                    this.storageSource,
                    derivedleveldata,
                    resourcekey1,
                    entry.getValue(),
                    worldloadlistener,
                    flag,
                    j,
                    spawners,
                    false,
                    randomsequences
                );
                // worldborder.addListener(new BorderChangeListener.DelegateBorderChangeListener(serverlevel1.getWorldBorder()));
                this.levels.put(resourcekey1, serverlevel1);
                this.initWorld(serverlevel1, derivedleveldata, worldData, worldoptions); // CraftBukkit
                this.getPlayerList().addWorldborderListener(serverlevel1);
                NeoForge.EVENT_BUS.post(new LevelEvent.Load(levels.get(resourcekey)));
            }
        }

        worldborder.applySettings(serverleveldata.getWorldBorder());
        YouerPlugin.init(); // Youer init mohist plugins
        for (ServerLevel worldserver : this.getAllLevels()) {
            worldserver.entityManager.tick(); // SPIGOT-6526: Load pending entities so they are available to the API
            this.server.getPluginManager().callEvent(new org.bukkit.event.world.WorldLoadEvent(worldserver.getWorld()));
        }
        // Paper start - Configurable player collision; Handle collideRule team for player collision toggle
        final ServerScoreboard scoreboard = this.getScoreboard();
        final Collection<String> toRemove = new ArrayList<>();
        for (PlayerTeam team : scoreboard.getPlayerTeams()) {
            if (team.getName().startsWith("collideRule_")) {
                String name = team.getName();
                toRemove.add(name);
            }
        }
        for (String teamName : toRemove) {
            scoreboard.removePlayerTeam(scoreboard.getPlayerTeam(teamName)); // Clean up after ourselves
        }

        if (!io.papermc.paper.configuration.GlobalConfiguration.get().collisions.enablePlayerCollisions) {
            this.getPlayerList().collideRuleTeamName = org.apache.commons.lang3.StringUtils.left("collideRule_" + java.util.concurrent.ThreadLocalRandom.current().nextInt(), 16);
            net.minecraft.world.scores.PlayerTeam collideTeam = scoreboard.addPlayerTeam(this.getPlayerList().collideRuleTeamName);
            collideTeam.setSeeFriendlyInvisibles(false); // Because we want to mimic them not being on a team at all
        }
        // Paper end - Configurable player collision

        this.server.enablePlugins(PluginLoadOrder.POSTWORLD);
        if (io.papermc.paper.plugin.PluginInitializerManager.instance().pluginRemapper != null) io.papermc.paper.plugin.PluginInitializerManager.instance().pluginRemapper.pluginsEnabled(); // Paper - Remap plugins
        io.papermc.paper.command.brigadier.PaperCommands.INSTANCE.setValid(); // Paper - reset invalid state for event fire below
        if (LifecycleEventTypeProviderImpl.canPrioritized()) {
            io.papermc.paper.plugin.lifecycle.event.LifecycleEventRunner.INSTANCE.callReloadableRegistrarEvent(io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS, io.papermc.paper.command.brigadier.PaperCommands.INSTANCE, org.bukkit.plugin.Plugin.class, io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause.INITIAL); // Paper - call commands event for regular plugins
        }
        ((SimpleHelpMap) this.server.getHelpMap()).initializeCommands();
        this.server.getPluginManager().callEvent(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));
        this.connection.acceptConnections();
    }

    private static void setInitialSpawn(ServerLevel p_177897_, ServerLevelData p_177898_, boolean p_177899_, boolean p_177900_) {
        if (p_177900_) {
            p_177898_.setSpawn(BlockPos.ZERO.above(80), 0.0F);
        } else {
            ServerChunkCache serverchunkcache = p_177897_.getChunkSource();
            if (EventHooks.onCreateWorldSpawn(p_177897_, p_177898_)) return;
            ChunkPos chunkpos = new ChunkPos(serverchunkcache.randomState().sampler().findSpawnPosition());
            // CraftBukkit start
            if (p_177897_.generator != null) {
                Random rand = new Random(p_177897_.getSeed());
                org.bukkit.Location spawn = p_177897_.generator.getFixedSpawnLocation(p_177897_.getWorld(), rand);

                if (spawn != null) {
                    if (spawn.getWorld() != p_177897_.getWorld()) {
                        throw new IllegalStateException("Cannot set spawn point for " + p_177898_.getLevelName() + " to be in another world (" + spawn.getWorld().getName() + ")");
                    } else {
                        p_177898_.setSpawn(new BlockPos(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ()), spawn.getYaw());
                        return;
                    }
                }
            }
            // CraftBukkit end
            int i = serverchunkcache.getGenerator().getSpawnHeight(p_177897_);
            if (i < p_177897_.getMinBuildHeight()) {
                BlockPos blockpos = chunkpos.getWorldPosition();
                i = p_177897_.getHeight(Heightmap.Types.WORLD_SURFACE, blockpos.getX() + 8, blockpos.getZ() + 8);
            }

            p_177898_.setSpawn(chunkpos.getWorldPosition().offset(8, i, 8), 0.0F);
            int j1 = 0;
            int j = 0;
            int k = 0;
            int l = -1;

            for (int i1 = 0; i1 < Mth.square(11); i1++) {
                if (j1 >= -5 && j1 <= 5 && j >= -5 && j <= 5) {
                    BlockPos blockpos1 = PlayerRespawnLogic.getSpawnPosInChunk(p_177897_, new ChunkPos(chunkpos.x + j1, chunkpos.z + j));
                    if (blockpos1 != null) {
                        p_177898_.setSpawn(blockpos1, 0.0F);
                        break;
                    }
                }

                if (j1 == j || j1 < 0 && j1 == -j || j1 > 0 && j1 == 1 - j) {
                    int k1 = k;
                    k = -l;
                    l = k1;
                }

                j1 += k;
                j += l;
            }

            if (p_177899_) {
                p_177897_.registryAccess()
                    .registry(Registries.CONFIGURED_FEATURE)
                    .flatMap(p_258226_ -> p_258226_.getHolder(MiscOverworldFeatures.BONUS_CHEST))
                    .ifPresent(p_319563_ -> p_319563_.value().place(p_177897_, serverchunkcache.getGenerator(), p_177897_.random, p_177898_.getSpawnPos()));
            }
        }
    }

    private void setupDebugLevel(WorldData p_129848_) {
        p_129848_.setDifficulty(Difficulty.PEACEFUL);
        p_129848_.setDifficultyLocked(true);
        ServerLevelData serverleveldata = p_129848_.overworldData();
        serverleveldata.setRaining(false);
        serverleveldata.setThundering(false);
        serverleveldata.setClearWeatherTime(1000000000);
        serverleveldata.setDayTime(6000L);
        serverleveldata.setGameType(GameType.SPECTATOR);
    }

    public void prepareLevels(ChunkProgressListener p_129941_, ServerLevel serverlevel) {
        markWorldsDirty();
        this.forceTicks = true;
        LOGGER.info(I18n.as("minecraftserver.4", serverlevel.dimension().location()));
        BlockPos blockpos = serverlevel.getSharedSpawnPos();
        p_129941_.updateSpawnPos(new ChunkPos(blockpos));
        ServerChunkCache serverchunkcache = serverlevel.getChunkSource();
        this.nextTickTimeNanos = Util.getNanos();
        serverlevel.setDefaultSpawnPos(blockpos, serverlevel.getSharedSpawnAngle());
        if (YouerConfig.keepSpawnLoaded) {
        int i = serverlevel.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS);
        int j = i > 0 ? Mth.square(ChunkProgressListener.calculateDiameter(i)) : 0;

        while (serverchunkcache.getTickingGenerated() < j) {
            this.executeModerately();
        }
        }

        this.executeModerately();
        ForcedChunksSavedData forcedchunkssaveddata = serverlevel.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");
        if (forcedchunkssaveddata != null) {
            LongIterator longiterator = forcedchunkssaveddata.getChunks().iterator();

            while (longiterator.hasNext()) {
                long k = longiterator.nextLong();
                ChunkPos chunkpos = new ChunkPos(k);
                serverlevel.getChunkSource().updateChunkForced(chunkpos, true);
            }
            ForcedChunkManager.reinstatePersistentChunks(serverlevel, forcedchunkssaveddata);
        }

        // CraftBukkit start
        this.executeModerately();
        p_129941_.stop();
        serverlevel.setSpawnSettings(serverlevel.K.getDifficulty() != Difficulty.PEACEFUL && ((DedicatedServer) this).settings.getProperties().spawnMonsters, this.isSpawningAnimals()); // Paper - per level difficulty (from setDifficulty(ServerLevel, Difficulty, boolean))
        this.forceTicks = false;
        // CraftBukkit end
    }

    public void prepareLevels(ChunkProgressListener p_129941_) {
        ServerLevel serverlevel = this.overworld();
        this.forceTicks = true;
        LOGGER.info(I18n.as("minecraftserver.4", serverlevel.dimension().location()));
        BlockPos blockpos = serverlevel.getSharedSpawnPos();
        p_129941_.updateSpawnPos(new ChunkPos(blockpos));
        ServerChunkCache serverchunkcache = serverlevel.getChunkSource();
        this.nextTickTimeNanos = Util.getNanos();
        serverlevel.setDefaultSpawnPos(blockpos, serverlevel.getSharedSpawnAngle());
        if (YouerConfig.keepSpawnLoaded) {
            int i = serverlevel.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS);
            int j = i > 0 ? Mth.square(ChunkProgressListener.calculateDiameter(i)) : 0;

            while (serverchunkcache.getTickingGenerated() < j) {
                this.executeModerately();
            }
        }

        this.executeModerately();
        for (ServerLevel serverlevel1 : this.levels.values()) {
            ForcedChunksSavedData forcedchunkssaveddata = serverlevel1.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");
            if (forcedchunkssaveddata != null) {
                LongIterator longiterator = forcedchunkssaveddata.getChunks().iterator();

                while (longiterator.hasNext()) {
                    long k = longiterator.nextLong();
                    ChunkPos chunkpos = new ChunkPos(k);
                    serverlevel1.getChunkSource().updateChunkForced(chunkpos, true);
                }
                ForcedChunkManager.reinstatePersistentChunks(serverlevel1, forcedchunkssaveddata);
            }
        }

        // CraftBukkit start
        this.executeModerately();
        p_129941_.stop();
        this.updateMobSpawningFlags();
        this.forceTicks = false;
        // CraftBukkit end
    }

    public GameType getDefaultGameType() {
        return this.worldData.getGameType();
    }

    public boolean isHardcore() {
        return this.worldData.isHardcore();
    }

    public abstract int getOperatorUserPermissionLevel();

    public abstract int getFunctionCompilationLevel();

    public abstract boolean shouldRconBroadcast();

    public boolean saveAllChunks(boolean p_129886_, boolean p_129887_, boolean p_129888_) {
        boolean flag = false;

        for (ServerLevel serverlevel : this.getAllLevels()) {
            if (!p_129886_) {
                LOGGER.info(I18n.as("minecraftserver.5", serverlevel, serverlevel.dimension().location()));
            }

            serverlevel.save(null, p_129887_, serverlevel.noSave && !p_129888_);
            flag = true;
        }

        ServerLevel serverlevel2 = this.overworld();
        ServerLevelData serverleveldata = this.worldData.overworldData();
        if (serverlevel2 != null) serverleveldata.setWorldBorder(serverlevel2.getWorldBorder().createSettings());
        this.worldData.setCustomBossEvents(this.getCustomBossEvents().save(this.registryAccess()));
        this.storageSource.saveDataTag(this.registryAccess(), this.worldData, this.getPlayerList().getSingleplayerData());
        if (p_129887_) {
            for (ServerLevel serverlevel1 : this.getAllLevels()) {
                LOGGER.info(I18n.as("minecraftserver.6", serverlevel1.getChunkSource().chunkMap.getStorageName()));
            }

            LOGGER.info(I18n.as("minecraftserver.7"));
        }

        return flag;
    }

    public boolean saveEverything(boolean p_195515_, boolean p_195516_, boolean p_195517_) {
        boolean flag;
        try {
            this.isSaving = true;
            this.getPlayerList().saveAll();
            flag = this.saveAllChunks(p_195515_, p_195516_, p_195517_);
        } finally {
            this.isSaving = false;
        }

        return flag;
    }

    @Override
    public void close() {
        this.stopServer();
    }

    // Folia start - region threading
    private void haltServerRegionThreading() {
        if (this.hasStartedShutdownThread.getAndSet(true)) {
            // already started shutdown
            return;
        }
        new io.papermc.paper.threadedregions.RegionShutdownThread("Region shutdown thread").start();
    }

    public void haltCurrentRegion() {
        if (!ca.spottedleaf.moonrise.common.util.TickThread.isShutdownThread()) {
            throw new IllegalStateException();
        }
    }
    // Folia end - region threading

    // CraftBukkit start
    private boolean hasStopped = false;
    private boolean hasLoggedStop = false; // Paper - Debugging
    public volatile boolean hasFullyShutdown = false; // Paper
    private final Object stopLock = new Object();
    public final boolean hasStopped() {
        synchronized (stopLock) {
            return hasStopped;
        }
    }
    // CraftBukkit end

    public void stopServer() {
        // Folia start - region threading
        io.papermc.paper.threadedregions.TickRegions.getScheduler().halt(false, 0L);
        if (!ca.spottedleaf.moonrise.common.util.TickThread.isShutdownThread()) {
            this.haltServerRegionThreading();
            return;
        }
        // Folia end - region threading
        // CraftBukkit start - prevent double stopping on multiple threads
        synchronized(stopLock) {
            if (hasStopped) return;
            hasStopped = true;
        }
        if (!hasLoggedStop && isDebugging()) io.papermc.paper.util.TraceUtil.dumpTraceForThread("Server stopped"); // Paper - Debugging
        // Paper start - kill main thread, and kill it hard
        shutdownThread = Thread.currentThread();
        org.spigotmc.WatchdogThread.doStop(); // Paper
        // Paper end
        // CraftBukkit end
        if (this.metricsRecorder.isRecording()) {
            this.cancelRecordingMetrics();
        }

        LOGGER.info(I18n.as("minecraftserver.8"));
        Commands.COMMAND_SENDING_POOL.shutdownNow(); // Paper - Perf: Async command map building; Shutdown and don't bother finishing
        // CraftBukkit start
        if (this.server != null) {
            this.server.disablePlugins();
            this.server.waitForAsyncTasksShutdown(); // Paper - Wait for Async Tasks during shutdown
        }
        // CraftBukkit end
        if (io.papermc.paper.plugin.PluginInitializerManager.instance().pluginRemapper != null) io.papermc.paper.plugin.PluginInitializerManager.instance().pluginRemapper.shutdown(); // Paper - Plugin remapping
        this.getConnection().stop();
        this.isSaving = true;
        if (this.playerList != null) {
            LOGGER.info(I18n.as("minecraftserver.9"));
            this.playerList.saveAll();
            this.playerList.removeAll();
            try { Thread.sleep(100); } catch (InterruptedException ex) {} // CraftBukkit - SPIGOT-625 - give server at least a chance to send packets
        }

        // Folia start - region threading - the rest till part 2 is handled by the region shutdown thread
        if (true) {
            return;
        }
        // Folia end - region threading

        if (ServerAPI.hasMod("c2me")) {
            LOGGER.info("Saving worlds");
        } else {
            LOGGER.info(I18n.as("minecraftserver.10"));
        }

        for (ServerLevel serverlevel : this.getAllLevels()) {
            if (serverlevel != null) {
                serverlevel.noSave = false;
            }
        }

        while (this.levels.values().stream().anyMatch(p_202480_ -> p_202480_.getChunkSource().chunkMap.hasWork())) {
            this.nextTickTimeNanos = Util.getNanos() + TimeUtil.NANOSECONDS_PER_MILLISECOND;

            for (ServerLevel serverlevel1 : this.getAllLevels()) {
                serverlevel1.getChunkSource().removeTicketsOnClosing();
                serverlevel1.getChunkSource().tick(() -> true, false);
            }

            this.waitUntilNextTick();
        }

        this.saveAllChunks(false, true, false);

        for (ServerLevel serverlevel2 : this.getAllLevels()) {
            if (serverlevel2 != null) {
                try {
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.LevelEvent.Unload(serverlevel2));
                    serverlevel2.close();
                } catch (IOException ioexception1) {
                    LOGGER.error(I18n.as("minecraftserver.11"), (Throwable)ioexception1);
                }
            }
        }

        this.isSaving = false;
        // Folia start - region threading - split stopServer into stopPart1/stopPart2
        this.stopPart2();
    }

    public void stopPart2() { // Folia - region threading
        // Folia end - region threading
        this.resources.close();

        try {
            this.storageSource.close();
        } catch (IOException ioexception) {
            LOGGER.error(I18n.as("minecraftserver.12", this.storageSource.getLevelId()), ioexception);
        }
        // Spigot start
        io.papermc.paper.util.MCUtil.asyncExecutor.shutdown(); // Paper
        try { io.papermc.paper.util.MCUtil.asyncExecutor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS); // Paper
        } catch (java.lang.InterruptedException ignored) {} // Paper
        // Spigot start
        if (SpigotConfig.saveUserCacheOnStopOnly) {
            LOGGER.info(I18n.as("minecraftserver.13"));
            this.getProfileCache().save(false); // Paper - Perf: Async GameProfileCache saving
        }
        // Spigot end
        // Paper start - move final shutdown items here
        ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.deinit(); // Paper - rewrite chunk system
        Util.shutdownExecutors();
        try {
            net.minecrell.terminalconsole.TerminalConsoleAppender.close(); // Paper - Use TerminalConsoleAppender
        } catch (final Exception ignored) {
        }
        io.papermc.paper.log.CustomLogManager.forceReset(); // Paper - Reset loggers after shutdown
        // FoliaYouer start - NeoForge: ensure handleServerStopped is called (main thread finally block never runs in region threading mode)
        // Wrap in try-catch: some mods (e.g. AE2) call assertServerThread() which fails on RegionShutdownThread
        try {
            net.neoforged.neoforge.server.ServerLifecycleHooks.handleServerStopped(this);
        } catch (Throwable t) {
            LOGGER.error("Error during ServerLifecycleHooks.handleServerStopped", t);
        }
        // FoliaYouer end
        this.onServerExit();
        // Paper end - Improved watchdog support
    }

    public String getLocalIp() {
        return this.localIp;
    }

    public void setLocalIp(String p_129914_) {
        this.localIp = p_129914_;
    }

    public boolean isRunning() {
        return this.running;
    }

    public void halt(boolean p_129884_) {
        // Paper start - allow passing of the intent to restart
        this.safeShutdown(p_129884_, false);
    }
    public void safeShutdown(boolean p_129884_, boolean isRestarting) {
        org.purpurmc.purpur.task.BossBarTask.stopAll(); // Purpur
        this.isRestarting = isRestarting;
        this.hasLoggedStop = true; // Paper - Debugging
        if (isDebugging()) io.papermc.paper.util.TraceUtil.dumpTraceForThread("Server stopped"); // Paper - Debugging
        // Paper end
        this.running = false;
        this.stopServer(); // Folia - region threading
        if (p_129884_) {
            try {
                this.serverThread.join();
            } catch (InterruptedException interruptedexception) {
                LOGGER.error(I18n.as("minecraftserver.14"), (Throwable)interruptedexception);
            }
        }
    }

    // Spigot Start
    private static double calcTps(double avg, double exp, double tps)
    {
        return ( avg * exp ) + ( tps * ( 1 - exp ) );
    }

    // Paper start - Further improve server tick loop
    private static final long SEC_IN_NANO = 1000000000;
    private static final long MAX_CATCHUP_BUFFER = TICK_TIME * TPS * 60L;
    private long lastTick = 0;
    private long catchupTime = 0;
    public final RollingAverage tps5s = new RollingAverage(5); // Purpur
    public final RollingAverage tps1 = new RollingAverage(60);
    public final RollingAverage tps5 = new RollingAverage(60 * 5);
    public final RollingAverage tps15 = new RollingAverage(60 * 15);

    public static class RollingAverage {
        private final int size;
        private long time;
        private java.math.BigDecimal total;
        private int index = 0;
        private final java.math.BigDecimal[] samples;
        private final long[] times;

        RollingAverage(int size) {
            this.size = size;
            this.time = size * SEC_IN_NANO;
            this.total = dec(TPS).multiply(dec(SEC_IN_NANO)).multiply(dec(size));
            this.samples = new java.math.BigDecimal[size];
            this.times = new long[size];
            for (int i = 0; i < size; i++) {
                this.samples[i] = dec(TPS);
                this.times[i] = SEC_IN_NANO;
            }
        }

        private static java.math.BigDecimal dec(long t) {
            return new java.math.BigDecimal(t);
        }
        public void add(java.math.BigDecimal x, long t) {
            time -= times[index];
            total = total.subtract(samples[index].multiply(dec(times[index])));
            samples[index] = x;
            times[index] = t;
            time += t;
            total = total.add(x.multiply(dec(t)));
            if (++index == size) {
                index = 0;
            }
        }

        public double getAverage() {
            return total.divide(dec(time), 30, java.math.RoundingMode.HALF_UP).doubleValue();
        }
    }
    private static final java.math.BigDecimal TPS_BASE = new java.math.BigDecimal(1E9).multiply(new java.math.BigDecimal(SAMPLE_INTERVAL));
    // Paper end
    // Spigot End

    protected void runServer() {
        try {
            long serverStartTime = Util.getNanos(); // Paper
            if (!this.initServer()) {
                throw new IllegalStateException(I18n.as("minecraftserver.15"));
            }

            ServerLifecycleHooks.handleServerStarted(this);
            this.nextTickTimeNanos = Util.getNanos();
            this.statusIcon = this.loadStatusIcon().orElse(null);
            this.status = this.buildServerStatus();
            resetStatusCache(status);

            // Folia start - region threading
            io.papermc.paper.threadedregions.RegionizedServer.getInstance().init();
            String doneTime = String.format(java.util.Locale.ROOT, "%.3fs", (double) (Util.getNanos() - serverStartTime) / 1.0E9D);
            LOGGER.info("Done ({})! For help, type \"help\"", doneTime);
            org.spigotmc.WatchdogThread.tick();
            org.spigotmc.WatchdogThread.hasStarted = true;
            Arrays.fill(recentTps, 20);
            for (;;) {
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (final InterruptedException ex) {}
            }
            // Folia end - region threading
        } catch (Throwable throwable1) {
            // Paper start
            if (throwable1 instanceof ThreadDeath) {
                MinecraftServer.LOGGER.error("Main thread terminated by WatchDog due to hard crash", throwable1);
                return;
            }
            if (throwable1 instanceof IllegalStateException && PORT_BIND_FAILED.equals(throwable1.getMessage())) {
                return;
            }
            // Paper end
            LOGGER.error(I18n.as("minecraftserver.17"), throwable1);
            CrashReport crashreport = constructOrExtractCrashReport(throwable1);
            this.fillSystemReport(crashreport.getSystemReport());
            Path path = this.getServerDirectory().resolve("crash-reports").resolve("crash-" + Util.getFilenameFormattedDateTime() + "-server.txt");
            if (crashreport.saveToFile(path, ReportType.CRASH)) {
                LOGGER.error(I18n.as("minecraftserver.18", path.toAbsolutePath()));
            } else {
                LOGGER.error(I18n.as("minecraftserver.19"));
            }

            ServerLifecycleHooks.expectServerStopped(); // Forge: Has to come before MinecraftServer#onServerCrash to avoid race conditions
            this.onServerCrash(crashreport);
        } finally {
            try {
                this.stopped = true;
                this.stopServer();
            } catch (Throwable throwable) {
                LOGGER.error(I18n.as("minecraftserver.20"), throwable);
            } finally {
                if (this.services.profileCache() != null) {
                    this.services.profileCache().clearExecutor();
                }

                net.neoforged.neoforge.server.ServerLifecycleHooks.handleServerStopped(this);
            }
        }
    }

    private void logFullTickTime() {
        long i = Util.getNanos();
        if (this.isTickTimeLoggingEnabled()) {
            this.getTickTimeLogger().logSample(i - this.lastTickNanos);
        }

        this.lastTickNanos = i;
    }

    private void startMeasuringTaskExecutionTime() {
        if (this.isTickTimeLoggingEnabled()) {
            this.taskExecutionStartNanos = Util.getNanos();
            this.idleTimeNanos = 0L;
        }
    }

    private void finishMeasuringTaskExecutionTime() {
        if (this.isTickTimeLoggingEnabled()) {
            SampleLogger samplelogger = this.getTickTimeLogger();
            samplelogger.logPartialSample(Util.getNanos() - this.taskExecutionStartNanos - this.idleTimeNanos, TpsDebugDimensions.SCHEDULED_TASKS.ordinal());
            samplelogger.logPartialSample(this.idleTimeNanos, TpsDebugDimensions.IDLE.ordinal());
        }
    }

    private static CrashReport constructOrExtractCrashReport(Throwable p_206569_) {
        ReportedException reportedexception = null;

        for (Throwable throwable = p_206569_; throwable != null; throwable = throwable.getCause()) {
            if (throwable instanceof ReportedException reportedexception1) {
                reportedexception = reportedexception1;
            }
        }

        CrashReport crashreport;
        if (reportedexception != null) {
            crashreport = reportedexception.getReport();
            if (reportedexception != p_206569_) {
                crashreport.addCategory("Wrapped in").setDetailError("Wrapping exception", p_206569_);
            }
        } else {
            crashreport = new CrashReport(I18n.as("minecraftserver.21"), p_206569_);
        }

        return crashreport;
    }

    private boolean haveTime() {
        if (isOversleep) return canOversleep(); // Paper - because of our changes, this logic is broken
        return this.forceTicks || this.runningTask() || Util.getNanos() < (this.mayHaveDelayedTasks ? this.delayedTasksMaxNextTickTimeNanos : this.nextTickTimeNanos);
    }

    // Paper start
    boolean isOversleep = false;
    private boolean canOversleep() {
        return this.mayHaveDelayedTasks && Util.getNanos() < this.delayedTasksMaxNextTickTimeNanos;
    }

    private boolean canSleepForTickNoOversleep() {
        return this.forceTicks || this.runningTask() || Util.getNanos() < this.nextTickTimeNanos;
    }
    // Paper end

    public static boolean throwIfFatalException() {
        RuntimeException runtimeexception = fatalException.get();
        if (runtimeexception != null) {
            throw runtimeexception;
        } else {
            return true;
        }
    }

    public static void setFatalException(RuntimeException p_347584_) {
        fatalException.compareAndSet(null, p_347584_);
    }

    @Override
    public void managedBlock(BooleanSupplier p_347462_) {
        super.managedBlock(() -> throwIfFatalException() && p_347462_.getAsBoolean());
    }

    protected void waitUntilNextTick() {
        //this.executeAll(); // Paper - move this into the tick method for timings
        this.managedBlock(() -> !this.canSleepForTickNoOversleep());
    }

    @Override
    public void waitForTasks() {
        boolean flag = this.isTickTimeLoggingEnabled();
        long i = flag ? Util.getNanos() : 0L;
        super.waitForTasks();
        if (flag) {
            this.idleTimeNanos = this.idleTimeNanos + (Util.getNanos() - i);
        }
    }

    public TickTask wrapRunnable(Runnable p_129852_) {
        // Paper start - anything that does try to post to main during watchdog crash, run on watchdog
        if (this.hasStopped && Thread.currentThread().equals(shutdownThread)) {
            p_129852_.run();
            p_129852_ = new Runnable() {
                @Override
                public void run() {
                }
            };
        }
        // Paper end
        return new TickTask(this.tickCount, p_129852_);
    }

    protected boolean shouldRun(TickTask p_129883_) {
        return p_129883_.getTick() + 3 < this.tickCount || this.haveTime();
    }

    @Override
    public boolean pollTask() {
        boolean flag = this.pollTaskInternal();
        this.mayHaveDelayedTasks = flag;
        return flag;
    }

    private boolean pollTaskInternal() {
        if (super.pollTask()) {
            return true;
        } else {
            boolean ret = false; // Paper - force execution of all worlds, do not just bias the first
            if (this.tickRateManager.isSprinting() || this.haveTime()) {
                for (ServerLevel serverlevel : this.getAllLevels()) {
                    if (serverlevel.getChunkSource().pollTask()) {
                        ret = true; // Paper - force execution of all worlds, do not just bias the first
                    }
                }
            }

            return ret; // Paper - force execution of all worlds, do not just bias the first
        }
    }

    public void doRunTask(TickTask p_129957_) {
        this.getProfiler().incrementCounter("runTask");
        super.doRunTask(p_129957_);
    }

    private Optional<ServerStatus.Favicon> loadStatusIcon() {
        Optional<Path> optional = Optional.of(this.getFile("server-icon.png"))
            .filter(p_272385_ -> Files.isRegularFile(p_272385_))
            .or(() -> this.storageSource.getIconFile().filter(p_272387_ -> Files.isRegularFile(p_272387_)));
        return optional.flatMap(p_272386_ -> {
            try {
                BufferedImage bufferedimage = ImageIO.read(p_272386_.toFile());
                Preconditions.checkState(bufferedimage.getWidth() == 64, "Must be 64 pixels wide");
                Preconditions.checkState(bufferedimage.getHeight() == 64, "Must be 64 pixels high");
                ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream();
                ImageIO.write(bufferedimage, "PNG", bytearrayoutputstream);
                return Optional.of(new ServerStatus.Favicon(bytearrayoutputstream.toByteArray()));
            } catch (Exception exception) {
                LOGGER.error("Couldn't load server icon", (Throwable)exception);
                return Optional.empty();
            }
        });
    }

    public Optional<Path> getWorldScreenshotFile() {
        return this.storageSource.getIconFile();
    }

    public Path getServerDirectory() {
        return Path.of("");
    }

    public void onServerCrash(CrashReport p_129874_) {
    }

    public void onServerExit() {
    }

    public boolean isPaused() {
        return false;
    }

    // Folia start - region threading - tickServer overload for regionized ticking
    public void tickServer(long startTime, long scheduledEnd, long targetBuffer,
                           io.papermc.paper.threadedregions.TickRegions.TickRegionData region) {
        if (region != null) {
            region.world.getCurrentWorldData().updateTickData();
            if (region.world.checkInitialised.get() != ServerLevel.WORLD_INIT_CHECKED) {
                synchronized (region.world.checkInitialised) {
                    if (region.world.checkInitialised.compareAndSet(ServerLevel.WORLD_INIT_NOT_CHECKED, ServerLevel.WORLD_INIT_CHECKING)) {
                        LOGGER.info("Initialising world '" + region.world.getWorld().getName() + "' before it can be ticked...");
                        this.initWorld(region.world, region.world.serverLevelData, worldData, this.worldData.worldGenOptions()); // FoliaYouer - use this.worldData
                        region.world.checkInitialised.set(ServerLevel.WORLD_INIT_CHECKED);
                        LOGGER.info("Initialised world '" + region.world.getWorld().getName() + "'");
                    }
                }
            }
        }
        BooleanSupplier shouldKeepTicking = () -> scheduledEnd - System.nanoTime() > targetBuffer;
        new com.destroystokyo.paper.event.server.ServerTickStartEvent((int)region.getCurrentTick()).callEvent();
        long i = startTime;

        if (region == null) {
            isOversleep = true;
            this.managedBlock(() -> !this.canOversleep());
            isOversleep = false;
        }

        // Folia - region threading - tick region-specific tasks
        if (region != null) {
            region.getTaskQueueData().drainTasks();
            ((io.papermc.paper.threadedregions.scheduler.FoliaRegionScheduler)Bukkit.getRegionScheduler()).tick();
            for (Entity entity : region.world.getCurrentWorldData().getLocalEntitiesCopy()) {
                if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(entity) || entity.isRemoved()) {
                    continue;
                }
                org.bukkit.craftbukkit.entity.CraftEntity bukkit = entity.getBukkitEntityRaw();
                if (bukkit != null) {
                    bukkit.taskScheduler.executeTick();
                }
            }
        }

        if (region == null) this.tickRateManager.tick();
        this.tickChildren(shouldKeepTicking); // FoliaYouer - region obtained from thread context
        if (region == null && i - this.lastServerStatus >= MinecraftServer.STATUS_EXPIRE_TIME_NANOS) {
            this.lastServerStatus = i;
            this.status = this.buildServerStatus();
            resetStatusCache(status);
        }

        // FoliaYouer: incremental saving (simplified - no saveIncrementally in Youer)
        // Paper start - Incremental chunk and player saving
        int playerSaveInterval = io.papermc.paper.configuration.GlobalConfiguration.get().playerAutoSave.rate;
        if (playerSaveInterval < 0) {
            playerSaveInterval = autosavePeriod;
        }
        this.profiler.push("save");
        final boolean fullSave = autosavePeriod > 0 && io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick() % autosavePeriod == 0;
        try {
            this.isSaving = true;
            if (playerSaveInterval > 0 && region == null) {
                this.playerList.saveAll(playerSaveInterval);
            }
        } finally {
            this.isSaving = false;
        }
        this.profiler.pop();
        // Paper end

        if (region == null) this.runAllTasks();

        long endTime = System.nanoTime();
        long remaining = scheduledEnd - endTime;
        new com.destroystokyo.paper.event.server.ServerTickEndEvent((int)io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick(), ((double)(endTime - startTime) / 1000000D), remaining).callEvent();
        org.spigotmc.WatchdogThread.tick();
    }
    // Folia end - region threading

    // Folia start - region threading - old tickServer, disabled. Use tickServer(long, long, long, TickRegionData) instead.
    @Deprecated
    public void tickServer(BooleanSupplier p_129871_) {
        long i = Util.getNanos();
        // Paper start - move oversleep into full server tick
        isOversleep = true;
        this.managedBlock(new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return !MinecraftServer.this.canOversleep();
            }
        });
        isOversleep = false;
        // Paper end
        new com.destroystokyo.paper.event.server.ServerTickStartEvent(this.tickCount+1).callEvent(); // Paper - Server Tick Events

        this.tickCount++;
        EventHooks.fireServerTickPre(p_129871_, this);
        this.tickRateManager.tick();
        this.tickChildren(p_129871_); // FoliaYouer - region from thread context
        if (i - this.lastServerStatus >= STATUS_EXPIRE_TIME_NANOS) {
            this.lastServerStatus = i;
            this.status = this.buildServerStatus();
            resetStatusCache(status);
        }

        this.ticksUntilAutosave--;
        if (this.ticksUntilAutosave <= 0) {
            this.ticksUntilAutosave = this.computeNextAutosaveInterval();
            LOGGER.debug("Autosave started");
            this.profiler.push("save");
            this.saveEverything(true, false, false);
            this.profiler.pop();
            LOGGER.debug("Autosave finished");
        }
        // Paper start - move executeAll() into full server tick timing
        this.runAllTasks();
        // Paper end
        // Paper start - Server Tick Events
        long endTime = System.nanoTime();
        long remaining = (TICK_TIME - (endTime - lastTick)) - catchupTime;
        new com.destroystokyo.paper.event.server.ServerTickEndEvent(this.tickCount, ((double)(endTime - lastTick) / 1000000D), remaining).callEvent();
        // Paper end - Server Tick Events
        this.profiler.push("tallying");
        long j = Util.getNanos() - i;
        int k = this.tickCount % 100;
        this.aggregatedTickTimesNanos = this.aggregatedTickTimesNanos - this.tickTimesNanos[k];
        this.aggregatedTickTimesNanos += j;
        this.tickTimesNanos[k] = j;
        this.smoothedTickTimeMillis = this.smoothedTickTimeMillis * 0.8F + (float)j / (float)TimeUtil.NANOSECONDS_PER_MILLISECOND * 0.19999999F;
        // Paper start - Add tick times API and /mspt command
        this.tickTimes5s.add(this.tickCount, j);
        this.tickTimes10s.add(this.tickCount, j);
        this.tickTimes60s.add(this.tickCount, j);
        // Paper end - Add tick times API and /mspt command
        this.logTickMethodTime(i);
        this.profiler.pop();
        org.spigotmc.WatchdogThread.tick(); // Spigot
        EventHooks.fireServerTickPost(p_129871_, this);
    }

    private void logTickMethodTime(long p_321837_) {
        if (this.isTickTimeLoggingEnabled()) {
            this.getTickTimeLogger().logPartialSample(Util.getNanos() - p_321837_, TpsDebugDimensions.TICK_SERVER_METHOD.ordinal());
        }
    }

    private static final Gson GSON = new Gson();
    private String cachedServerStatus; // Neo: cache the server status json in case a client spams requests
    private void resetStatusCache(ServerStatus status) {
        this.cachedServerStatus = GSON.toJson(ServerStatus.CODEC.encodeStart(JsonOps.INSTANCE, status)
                .result().orElseThrow());
    }
    public String getStatusJson() {
        return cachedServerStatus;
    }

    private int computeNextAutosaveInterval() {
        float f;
        if (this.tickRateManager.isSprinting()) {
            long i = this.getAverageTickTimeNanos() + 1L;
            f = (float)TimeUtil.NANOSECONDS_PER_SECOND / (float)i;
        } else {
            f = this.tickRateManager.tickrate();
        }

        int j = 300;
        return Math.max(100, (int)(f * 300.0F));
    }

    public void onTickRateChanged() {
        int i = this.computeNextAutosaveInterval();
        if (i < this.ticksUntilAutosave) {
            this.ticksUntilAutosave = i;
        }
    }

    protected abstract SampleLogger getTickTimeLogger();

    public abstract boolean isTickTimeLoggingEnabled();

    // Folia start - region threading
    public void rebuildServerStatus() {
        this.status = this.buildServerStatus();
    }
    // Folia end - region threading

    private ServerStatus buildServerStatus() {
        ServerStatus.Players serverstatus$players = this.buildPlayerStatus();
        return new ServerStatus(io.papermc.paper.adventure.PaperAdventure.asVanilla(this.motd),
            Optional.of(serverstatus$players),
            Optional.of(ServerStatus.Version.current()),
            Optional.ofNullable(this.statusIcon),
            this.enforceSecureProfile(),
            true //TODO Neo: Possible build a system which indicates what the status of the modded server is.
        );
    }

    private ServerStatus.Players buildPlayerStatus() {
        List<ServerPlayer> list = this.playerList.getPlayers();
        int i = this.getMaxPlayers();
        if (this.hidesOnlinePlayers()) {
            return new ServerStatus.Players(i, list.size(), List.of());
        } else {
            int j = Math.min(list.size(), org.spigotmc.SpigotConfig.playerSample); // Paper - PaperServerListPingEvent
            ObjectArrayList<GameProfile> objectarraylist = new ObjectArrayList<>(j);
            int k = Mth.nextInt(this.random, 0, list.size() - j);

            for (int l = 0; l < j; l++) {
                ServerPlayer serverplayer = list.get(k + l);
                objectarraylist.add(serverplayer.allowsListing() ? serverplayer.getGameProfile() : ANONYMOUS_PLAYER_PROFILE);
            }

            Util.shuffle(objectarraylist, this.random);
            return new ServerStatus.Players(i, list.size(), objectarraylist);
        }
    }

    public void tickChildren(BooleanSupplier p_129954_) { // FoliaYouer - keep original signature for binpatch/mixin compat
        // Folia - region threading - get region from thread context
        io.papermc.paper.threadedregions.TickRegions.TickRegionData region = null;
        io.papermc.paper.threadedregions.ThreadedRegionizer.ThreadedRegion<io.papermc.paper.threadedregions.TickRegions.TickRegionData, io.papermc.paper.threadedregions.TickRegions.TickRegionSectionData> currentRegion = io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegion();
        if (currentRegion != null) {
            region = currentRegion.getData();
        }
        final io.papermc.paper.threadedregions.RegionizedWorldData regionizedWorldData = io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData(); // Folia - regionised ticking
        if (region == null) this.getPlayerList().getPlayers().forEach((entityplayer) -> { // Folia - region threading
            entityplayer.connection.suspendFlushing();
        });
        this.server.getScheduler().mainThreadHeartbeat(this.tickCount); // CraftBukkit
        io.papermc.paper.adventure.providers.ClickCallbackProviderImpl.CALLBACK_MANAGER.handleQueue(this.tickCount); // Paper
        this.profiler.push("commandFunctions");
        if (region == null) this.getFunctions().tick(); // Folia - region threading
        this.profiler.popPush("levels");
        if (region == null) while (!processQueue.isEmpty()) { // Folia - region threading
            processQueue.remove().run();
        }
        for (final ServerLevel level : (region == null ? this.getAllLevels() : Arrays.asList(region.world))) { // Folia - region threading
            final boolean doDaylight = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
            final long dayTime = level.getDayTime();
            long worldTime = level.getGameTime();
            final ClientboundSetTimePacket worldPacket = new ClientboundSetTimePacket(worldTime, dayTime, doDaylight);
            for (Player entityhuman : level.players()) {
                if (!(entityhuman instanceof ServerPlayer) || (io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick() + entityhuman.getId()) % 20 != 0) { // Folia - region threading
                    continue;
                }
                ServerPlayer entityplayer = (ServerPlayer) entityhuman;
                long playerTime = entityplayer.getPlayerTime();
                ClientboundSetTimePacket packet = (playerTime == dayTime) ? worldPacket :
                        new ClientboundSetTimePacket(worldTime, playerTime, doDaylight);
                entityplayer.connection.send(packet);
            }
        }
        if (region == null) this.isIteratingOverLevels = true; // Folia - region threading
        Iterator iterator = region == null ? this.getAllLevels().iterator() : Arrays.asList(region.world).iterator(); // Folia - region threading
        while (iterator.hasNext()) {
            ServerLevel serverlevel = (ServerLevel) iterator.next();

            this.profiler.push(() -> serverlevel + " " + serverlevel.dimension().location());
            this.profiler.push("tick");
            EventHooks.fireLevelTickPre(serverlevel, p_129954_);

            try {
                serverlevel.tick(p_129954_); // FoliaYouer - region obtained from thread context
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception ticking world");
                serverlevel.fillReportDetails(crashreport);
                throw new ReportedException(crashreport);
            }
            EventHooks.fireLevelTickPost(serverlevel, p_129954_);

            this.profiler.pop();
            this.profiler.pop();
            regionizedWorldData.explosionDensityCache.clear(); // Paper - Optimize explosions // Folia - region threading
        }
        if (region == null) this.isIteratingOverLevels = false; // Folia - region threading

        this.profiler.popPush("connection");
        if (region == null) this.getConnection().tick(); // Folia - region threading
        if (region != null) regionizedWorldData.tickConnections(); // Folia - region threading
        this.profiler.popPush("players");
        if (region == null) this.playerList.tick(); // Folia - region threading
        if (GameTestHooks.isGametestEnabled() && this.tickRateManager.runsNormally()) {
            GameTestTicker.SINGLETON.tick();
        }

        this.profiler.popPush("server gui refresh");
        if (region == null) for (int i = 0; i < this.tickables.size(); i++) { // Folia - region threading
            this.tickables.get(i).run();
        }
        this.profiler.popPush("send chunks");
        if (region == null) for (ServerPlayer serverplayer : this.playerList.getPlayers()) { // Folia - region threading
            serverplayer.connection.chunkSender.sendNextChunks(serverplayer);
            serverplayer.connection.resumeFlushing();
        }
        this.profiler.pop();
    }

    private void synchronizeTime(ServerLevel p_276371_) {
        ClientboundSetTimePacket vanillaPacket = new ClientboundSetTimePacket(p_276371_.getGameTime(), p_276371_.getDayTime(), p_276371_.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT));
        ClientboundCustomSetTimePayload neoPacket = new ClientboundCustomSetTimePayload(p_276371_.getGameTime(), p_276371_.getDayTime(), p_276371_.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT), p_276371_.getDayTimeFraction(), p_276371_.getDayTimePerTick());
        for (ServerPlayer serverplayer : playerList.getPlayers()) {
            if (serverplayer.level().dimension() == p_276371_.dimension()) {
                if (serverplayer.connection.hasChannel(ClientboundCustomSetTimePayload.TYPE)) {
                    serverplayer.connection.send(neoPacket);
                } else {
                    serverplayer.connection.send(vanillaPacket);
                }
            }
        }
    }

    public void forceTimeSynchronization() {
        this.profiler.push("timeSync");

        for (ServerLevel serverlevel : this.getAllLevels()) {
            this.synchronizeTime(serverlevel);
        }

        this.profiler.pop();
    }

    public boolean isLevelEnabled(Level p_350377_) {
        return true;
    }

    public void addTickable(Runnable p_129947_) {
        this.tickables.add(p_129947_);
    }

    protected void setId(String p_129949_) {
        this.serverId = p_129949_;
    }

    public boolean isShutdown() {
        return !this.serverThread.isAlive();
    }

    public Path getFile(String p_129972_) {
        return this.getServerDirectory().resolve(p_129972_);
    }

    public final ServerLevel overworld() {
        return this.levels.get(Level.OVERWORLD);
    }

    @Nullable
    public ServerLevel getLevel(ResourceKey<Level> p_129881_) {
        return this.levels.get(p_129881_);
    }

    public Set<ResourceKey<Level>> levelKeys() {
        return this.levels.keySet();
    }

    // CraftBukkit start
    public void addLevel(ServerLevel level) {
        this.levels.put(level.dimension(), level); // Mohist
        markWorldsDirty();
    }

    public void removeLevel(ServerLevel level) {
        this.levels.remove(level.dimension()); // Mohist
        markWorldsDirty();
        ((CraftServer)Bukkit.getServer()).removeWorld(level);
    }
    // CraftBukkit end

    public Iterable<ServerLevel> getAllLevels() {
        return this.levels.values();
    }

    @Override
    public String getServerVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    @Override
    public int getPlayerCount() {
        return this.playerList.getPlayerCount();
    }

    @Override
    public int getMaxPlayers() {
        return this.playerList.getMaxPlayers();
    }

    public String[] getPlayerNames() {
        return this.playerList.getPlayerNamesArray();
    }

    @DontObfuscate
    public String getServerModName() {
        return org.purpurmc.purpur.PurpurConfig.serverModName; // Paper // Purpur
    }

    public SystemReport fillSystemReport(SystemReport p_177936_) {
        p_177936_.setDetail("Server Running", () -> Boolean.toString(this.running));
        if (this.playerList != null) {
            p_177936_.setDetail(
                "Player Count", () -> this.playerList.getPlayerCount() + " / " + this.playerList.getMaxPlayers() + "; " + this.playerList.getPlayers()
            );
        }

        p_177936_.setDetail("Active Data Packs", () -> PackRepository.displayPackList(this.packRepository.getSelectedPacks()));
        p_177936_.setDetail("Available Data Packs", () -> PackRepository.displayPackList(this.packRepository.getAvailablePacks()));
        p_177936_.setDetail(
            "Enabled Feature Flags",
            () -> FeatureFlags.REGISTRY.toNames(this.worldData.enabledFeatures()).stream().map(ResourceLocation::toString).collect(Collectors.joining(", "))
        );
        p_177936_.setDetail("World Generation", () -> this.worldData.worldGenSettingsLifecycle().toString());
        p_177936_.setDetail("World Seed", () -> String.valueOf(this.worldData.worldGenOptions().seed()));
        if (this.serverId != null) {
            p_177936_.setDetail("Server Id", () -> this.serverId);
        }

        return this.fillServerSystemReport(p_177936_);
    }

    public abstract SystemReport fillServerSystemReport(SystemReport p_177901_);

    public ModCheck getModdedStatus() {
        return ModCheck.identify("vanilla", this::getServerModName, "Server", MinecraftServer.class);
    }

    @Override
    public void sendSystemMessage(Component p_236736_) {
        LOGGER.info(io.papermc.paper.adventure.PaperAdventure.ANSI_SERIALIZER.serialize(io.papermc.paper.adventure.PaperAdventure.asAdventure(p_236736_))); // Paper - Log message with colors
    }

    public KeyPair getKeyPair() {
        return this.keyPair;
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int p_129802_) {
        this.port = p_129802_;
    }

    @Nullable
    public GameProfile getSingleplayerProfile() {
        return this.singleplayerProfile;
    }

    public void setSingleplayerProfile(@Nullable GameProfile p_236741_) {
        this.singleplayerProfile = p_236741_;
    }

    public boolean isSingleplayer() {
        return this.singleplayerProfile != null;
    }

    protected void initializeKeyPair() {
        LOGGER.info(I18n.as("minecraftserver.22"));

        try {
            this.keyPair = Crypt.generateKeyPair();
        } catch (CryptException cryptexception) {
            throw new IllegalStateException(I18n.as("minecraftserver.23"), cryptexception);
        }
    }

    public void setDifficulty(Difficulty p_129828_, boolean p_129829_) {
        if (p_129829_ || !this.worldData.isDifficultyLocked()) {
            this.worldData.setDifficulty(this.worldData.isHardcore() ? Difficulty.HARD : p_129828_);
            this.updateMobSpawningFlags();
            this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
        }
    }

    // Paper start - per level difficulty
    public void setDifficulty(ServerLevel level, Difficulty difficulty, boolean forceUpdate) {
        PrimaryLevelData worldData = level.K;
        if (forceUpdate || !worldData.isDifficultyLocked()) {
            worldData.setDifficulty(worldData.isHardcore() ? Difficulty.HARD : difficulty);
            level.setSpawnSettings(worldData.getDifficulty() != Difficulty.PEACEFUL && ((DedicatedServer) this).settings.getProperties().spawnMonsters, this.isSpawningAnimals());
            // this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
            // Paper end - per level difficulty
        }
    }

    public int getScaledTrackingDistance(int p_129935_) {
        return p_129935_;
    }

    private void updateMobSpawningFlags() {
        for (ServerLevel serverlevel : this.getAllLevels()) {
            serverlevel.setSpawnSettings(serverlevel.K.getDifficulty() != Difficulty.PEACEFUL && ((DedicatedServer) this).settings.getProperties().spawnMonsters, this.isSpawningAnimals()); // Paper - per level difficulty (from setDifficulty(ServerLevel, Difficulty, boolean))
        }
    }

    public void setDifficultyLocked(boolean p_129959_) {
        this.worldData.setDifficultyLocked(p_129959_);
        this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
    }

    private void sendDifficultyUpdate(ServerPlayer p_129939_) {
        LevelData leveldata = p_129939_.level().getLevelData();
        p_129939_.connection.send(new ClientboundChangeDifficultyPacket(leveldata.getDifficulty(), leveldata.isDifficultyLocked()));
    }

    public boolean isSpawningMonsters() {
        return this.worldData.getDifficulty() != Difficulty.PEACEFUL;
    }

    public boolean isDemo() {
        return this.isDemo;
    }

    public void setDemo(boolean p_129976_) {
        this.isDemo = p_129976_;
    }

    public Optional<ServerResourcePackInfo> getServerResourcePack() {
        return Optional.empty();
    }

    public boolean isResourcePackRequired() {
        return this.getServerResourcePack().filter(ServerResourcePackInfo::isRequired).isPresent();
    }

    public abstract boolean isDedicatedServer();

    public abstract int getRateLimitPacketsPerSecond();

    public boolean usesAuthentication() {
        return this.onlineMode;
    }

    public void setUsesAuthentication(boolean p_129986_) {
        this.onlineMode = p_129986_;
    }

    public boolean getPreventProxyConnections() {
        return this.preventProxyConnections;
    }

    public void setPreventProxyConnections(boolean p_129994_) {
        this.preventProxyConnections = p_129994_;
    }

    // Paper start
    public java.io.File getPluginsFolder() {
        return (java.io.File) this.options.valueOf("plugins");
    }
    // Paper end

    public boolean isSpawningAnimals() {
        return true;
    }

    public boolean areNpcsEnabled() {
        return true;
    }

    public abstract boolean isEpollEnabled();

    public boolean isPvpAllowed() {
        return this.pvp;
    }

    public void setPvpAllowed(boolean p_129998_) {
        this.pvp = p_129998_;
    }

    public boolean isFlightAllowed() {
        return this.allowFlight;
    }

    public void setFlightAllowed(boolean p_130000_) {
        this.allowFlight = p_130000_;
    }

    public abstract boolean isCommandBlockEnabled();

    @Override
    public String getMotd() {
        return LegacyComponentSerializer.legacySection().serialize(this.motd); // Paper - Adventure
    }

    public void setMotd(String motd) {
        // Paper start - Adventure
        this.motd = ColorAPI.adventure(motd);
    }

    public net.kyori.adventure.text.Component motd() {
        return this.motd;
    }

    public void motd(net.kyori.adventure.text.Component motd) {
        // Paper end - Adventure
        this.motd = motd;
    }

    public boolean isStopped() {
        return this.stopped;
    }

    public PlayerList getPlayerList() {
        return this.playerList;
    }

    public void setPlayerList(PlayerList p_129824_) {
        this.playerList = p_129824_;
    }

    public abstract boolean isPublished();

    public void setDefaultGameType(GameType p_129832_) {
        this.worldData.setGameType(p_129832_);
    }

    public ServerConnectionListener getConnection() {
        return connection;
    }

    public boolean isReady() {
        return this.isReady;
    }

    public boolean hasGui() {
        return false;
    }

    public boolean publishServer(@Nullable GameType p_129833_, boolean p_129834_, int p_129835_) {
        return false;
    }

    public int getTickCount() {
        return this.tickCount;
    }

    public int getSpawnProtectionRadius() {
        return 16;
    }

    public boolean isUnderSpawnProtection(ServerLevel p_129811_, BlockPos p_129812_, Player p_129813_) {
        return false;
    }

    public boolean repliesToStatus() {
        return true;
    }

    public boolean hidesOnlinePlayers() {
        return false;
    }

    public Proxy getProxy() {
        return this.proxy;
    }

    public int getPlayerIdleTimeout() {
        return this.playerIdleTimeout;
    }

    public void setPlayerIdleTimeout(int p_129978_) {
        this.playerIdleTimeout = p_129978_;
    }

    public MinecraftSessionService getSessionService() {
        return this.services.sessionService();
    }

    @Nullable
    public SignatureValidator getProfileKeySignatureValidator() {
        return this.services.profileKeySignatureValidator();
    }

    public GameProfileRepository getProfileRepository() {
        return this.services.profileRepository();
    }

    @Nullable
    public GameProfileCache getProfileCache() {
        return this.services.profileCache();
    }

    @Nullable
    public ServerStatus getStatus() {
        return this.status;
    }

    public void invalidateStatus() {
        this.lastServerStatus = 0L;
    }

    public int getAbsoluteMaxWorldSize() {
        return 29999984;
    }

    @Override
    public boolean scheduleExecutables() {
        return super.scheduleExecutables() && !this.isStopped();
    }

    @Override
    public void executeIfPossible(Runnable p_202482_) {
        if (this.isStopped()) {
            throw new io.papermc.paper.util.ServerStopRejectedExecutionException(I18n.as("minecraftserver.24"));
        } else {
            super.executeIfPossible(p_202482_);
        }
    }

    @Override
    public Thread getRunningThread() {
        return this.serverThread;
    }

    public int getCompressionThreshold() {
        return 256;
    }

    public boolean enforceSecureProfile() {
        return false;
    }

    public long getNextTickTime() {
        return this.nextTickTimeNanos;
    }

    public DataFixer getFixerUpper() {
        return this.fixerUpper;
    }

    public int getSpawnRadius(@Nullable ServerLevel p_129804_) {
        return p_129804_ != null ? p_129804_.getGameRules().getInt(GameRules.RULE_SPAWN_RADIUS) : 10;
    }

    public ServerAdvancementManager getAdvancements() {
        return this.resources.managers.getAdvancements();
    }

    public ServerFunctionManager getFunctions() {
        return this.functionManager;
    }

    public AtomicReference<io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause> reloadResources$Cause = new AtomicReference<>(io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause.PLUGIN);
    public CompletableFuture<Void> reloadResourcesPaper(Collection<String> p_129862_, io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause cause) {
        reloadResources$Cause.set(cause);
        return reloadResources(p_129862_);
    }

    public CompletableFuture<Void> reloadResources(Collection<String> p_129862_) {
        CompletableFuture<Void> completablefuture = CompletableFuture.<ImmutableList>supplyAsync(
                () -> this.packRepository.rebuildSelected(p_129862_).stream().map(Pack::open).collect(ImmutableList.toImmutableList()),
                this
            )
            .thenCompose(
                p_335201_ -> {
                    CloseableResourceManager closeableresourcemanager = new MultiPackResourceManager(PackType.SERVER_DATA, p_335201_);
                    return ReloadableServerResources.loadResources(
                            closeableresourcemanager,
                            this.registries,
                            this.worldData.enabledFeatures(),
                            this.isDedicatedServer() ? Commands.CommandSelection.DEDICATED : Commands.CommandSelection.INTEGRATED,
                            this.getFunctionCompilationLevel(),
                            this.executor,
                            this
                        )
                        .whenComplete((p_212907_, p_212908_) -> {
                            if (p_212908_ != null) {
                                closeableresourcemanager.close();
                            }
                        })
                        .thenApply(p_212904_ -> new MinecraftServer.ReloadableResources(closeableresourcemanager, p_212904_));
                }
            )
            .thenAcceptAsync(
                p_335203_ -> {
                    io.papermc.paper.command.brigadier.PaperBrigadier.moveBukkitCommands(this.resources.managers().getCommands(), p_335203_.managers().commands); // Paper
                    this.resources.close();
                    this.resources = p_335203_;
                    this.packRepository.setSelected(p_129862_);
                    WorldDataConfiguration worlddataconfiguration = new WorldDataConfiguration(
                        getSelectedPacks(this.packRepository, true), this.worldData.enabledFeatures()
                    );
                    this.worldData.setDataConfiguration(worlddataconfiguration);
                    this.resources.managers.updateRegistryTags();
                    this.potionBrewing = this.potionBrewing.reload(this.worldData.enabledFeatures()); // Paper - Custom Potion Mixes
                    // Paper start
                    if (Thread.currentThread() != this.serverThread) {
                        return;
                    }
                    // this.getPlayerList().saveAll(); // Paper - we don't need to save everything, just advancements // TODO Move this to a different patch
                    for (ServerPlayer player : this.getPlayerList().getPlayers()) {
                        player.getAdvancements().save();
                    }
                    // Paper end
                    this.getPlayerList().reloadResources();
                    this.functionManager.replaceLibrary(this.resources.managers.getFunctionLibrary());
                    this.structureTemplateManager.onResourceManagerReload(this.resources.resourceManager);
                    this.getPlayerList().getPlayers().forEach(this.getPlayerList()::sendPlayerPermissionLevel); //Forge: Fix newly added/modified commands not being sent to the client when commands reload.
                    CraftBlockData.reloadCache(); // Paper - cache block data strings, they can be defined by datapacks so refresh it here
                    // Paper start - brigadier command API
                    io.papermc.paper.command.brigadier.PaperCommands.INSTANCE.setValid(); // reset invalid state for event fire below
                    LifecycleEventRunner.INSTANCE.callReloadableRegistrarEvent(LifecycleEvents.COMMANDS, io.papermc.paper.command.brigadier.PaperCommands.INSTANCE, org.bukkit.plugin.Plugin.class, ReloadableRegistrarEvent.Cause.RELOAD); // call commands event for regular plugins
                    final SimpleHelpMap helpMap = (SimpleHelpMap) this.server.getHelpMap();
                    helpMap.clear();
                    helpMap.initializeGeneralTopics();
                    helpMap.initializeCommands();
                    this.server.syncCommands(); // Refresh commands after event
                    // Paper end
                    new io.papermc.paper.event.server.ServerResourcesReloadedEvent(reloadResources$Cause.getAndSet(io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause.PLUGIN)).callEvent(); // Paper - fire after everything has been reloaded
                },
                this
            );
        if (this.isSameThread()) {
            this.managedBlock(completablefuture::isDone);
        }

        return completablefuture;
    }

    public static WorldDataConfiguration configurePackRepository(
        PackRepository p_248681_, WorldDataConfiguration p_341632_, boolean p_249869_, boolean p_341620_
    ) {
        DataPackConfig datapackconfig = p_341632_.dataPacks();
        FeatureFlagSet featureflagset = p_249869_ ? FeatureFlagSet.of() : p_341632_.enabledFeatures();
        FeatureFlagSet featureflagset1 = p_249869_ ? FeatureFlags.REGISTRY.allFlags() : p_341632_.enabledFeatures();
        p_248681_.reload();
        DataPackConfig.DEFAULT.addModPacks(CommonHooks.getModDataPacks());
        datapackconfig.addModPacks(CommonHooks.getModDataPacks());
        if (p_341620_) {
            return configureRepositoryWithSelection(p_248681_, CommonHooks.getModDataPacksWithVanilla(), featureflagset, false);
        } else {
            Set<String> set = Sets.newLinkedHashSet();

            for (String s : datapackconfig.getEnabled()) {
                if (p_248681_.isAvailable(s)) {
                    set.add(s);
                } else {
                    LOGGER.warn("Missing data pack {}", s);
                }
            }

            for (Pack pack : p_248681_.getAvailablePacks()) {
                String s1 = pack.getId();
                if (!datapackconfig.getDisabled().contains(s1)) {
                    FeatureFlagSet featureflagset2 = pack.getRequestedFeatures();
                    boolean flag = set.contains(s1);
                    if (!flag && pack.getPackSource().shouldAddAutomatically()) {
                        if (featureflagset2.isSubsetOf(featureflagset1)) {
                            LOGGER.info("Found new data pack {}, loading it automatically", s1);
                            set.add(s1);
                        } else {
                            LOGGER.info(
                                "Found new data pack {}, but can't load it due to missing features {}",
                                s1,
                                FeatureFlags.printMissingFlags(featureflagset1, featureflagset2)
                            );
                        }
                    }

                    if (flag && !featureflagset2.isSubsetOf(featureflagset1)) {
                        LOGGER.warn(
                            "Pack {} requires features {} that are not enabled for this world, disabling pack.",
                            s1,
                            FeatureFlags.printMissingFlags(featureflagset1, featureflagset2)
                        );
                        set.remove(s1);
                    }
                }
            }

            if (set.isEmpty()) {
                LOGGER.info("No datapacks selected, forcing vanilla");
                set.add("vanilla");
            }

            ResourcePackLoader.reorderNewlyDiscoveredPacks(set, datapackconfig.getEnabled(), p_248681_);

            return configureRepositoryWithSelection(p_248681_, set, featureflagset, true);
        }
    }

    private static WorldDataConfiguration configureRepositoryWithSelection(
        PackRepository p_341680_, Collection<String> p_341677_, FeatureFlagSet p_341602_, boolean p_341662_
    ) {
        p_341680_.setSelected(p_341677_);
        enableForcedFeaturePacks(p_341680_, p_341602_);
        DataPackConfig datapackconfig = getSelectedPacks(p_341680_, p_341662_);
        FeatureFlagSet featureflagset = p_341680_.getRequestedFeatureFlags().join(p_341602_);
        return new WorldDataConfiguration(datapackconfig, featureflagset);
    }

    private static void enableForcedFeaturePacks(PackRepository p_341674_, FeatureFlagSet p_341598_) {
        FeatureFlagSet featureflagset = p_341674_.getRequestedFeatureFlags();
        FeatureFlagSet featureflagset1 = p_341598_.subtract(featureflagset);
        if (!featureflagset1.isEmpty()) {
            Set<String> set = new ObjectArraySet<>(p_341674_.getSelectedIds());

            for (Pack pack : p_341674_.getAvailablePacks()) {
                if (featureflagset1.isEmpty()) {
                    break;
                }

                if (pack.getPackSource() == PackSource.FEATURE) {
                    String s = pack.getId();
                    FeatureFlagSet featureflagset2 = pack.getRequestedFeatures();
                    if (!featureflagset2.isEmpty() && featureflagset2.intersects(featureflagset1) && featureflagset2.isSubsetOf(p_341598_)) {
                        if (!set.add(s)) {
                            throw new IllegalStateException("Tried to force '" + s + "', but it was already enabled");
                        }

                        LOGGER.info("Found feature pack ('{}') for requested feature, forcing to enabled", s);
                        featureflagset1 = featureflagset1.subtract(featureflagset2);
                    }
                }
            }

            p_341674_.setSelected(set);
        }
    }

    private static DataPackConfig getSelectedPacks(PackRepository p_129818_, boolean p_341596_) {
        Collection<String> collection = p_129818_.getSelectedIds();
        List<String> list = ImmutableList.copyOf(collection);
        List<String> list1 = p_341596_ ? p_129818_.getAvailableIds().stream().filter(p_212916_ -> !collection.contains(p_212916_)).toList() : List.of();
        return new DataPackConfig(list, list1);
    }

    public void kickUnlistedPlayers(CommandSourceStack p_129850_) {
        if (this.isEnforceWhitelist()) {
            PlayerList playerlist = p_129850_.getServer().getPlayerList();
            UserWhiteList userwhitelist = playerlist.getWhiteList();
            if (!((DedicatedServer) getServer()).getProperties().whiteList.get()) return; // Paper - whitelist not enabled
            for (ServerPlayer serverplayer : Lists.newArrayList(playerlist.getPlayers())) {
                if (!userwhitelist.isWhiteListed(serverplayer.getGameProfile()) && !this.getPlayerList().isOp(serverplayer.getGameProfile())) { // Paper - Fix kicking ops when whitelist is reloaded (MC-171420)
                    serverplayer.connection.disconnect(net.kyori.adventure.text.Component.text(org.spigotmc.SpigotConfig.whitelistMessage), org.bukkit.event.player.PlayerKickEvent.Cause.WHITELIST); // Paper - use configurable message & kick event cause
                }
            }
        }
    }

    public PackRepository getPackRepository() {
        return this.packRepository;
    }

    public Commands getCommands() {
        return this.resources.managers.getCommands();
    }

    public CommandSourceStack createCommandSourceStack() {
        ServerLevel serverlevel = this.overworld();
        return new CommandSourceStack(
            this,
            serverlevel == null ? Vec3.ZERO : Vec3.atLowerCornerOf(serverlevel.getSharedSpawnPos()),
            Vec2.ZERO,
            serverlevel,
            4,
            "Server",
            Component.literal("Server"),
            this,
            null
        );
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public abstract boolean shouldInformAdmins();

    public RecipeManager getRecipeManager() {
        return this.resources.managers.getRecipeManager();
    }

    public ServerScoreboard getScoreboard() {
        return this.scoreboard;
    }

    public CommandStorage getCommandStorage() {
        if (this.commandStorage == null) {
            throw new NullPointerException("Called before server init");
        } else {
            return this.commandStorage;
        }
    }

    public GameRules getGameRules() {
        return this.overworld().getGameRules();
    }

    public CustomBossEvents getCustomBossEvents() {
        return this.customBossEvents;
    }

    public boolean isEnforceWhitelist() {
        return this.enforceWhitelist;
    }

    public void setEnforceWhitelist(boolean p_130005_) {
        this.enforceWhitelist = p_130005_;
    }

    public float getCurrentSmoothedTickTime() {
        return this.smoothedTickTimeMillis;
    }

    public ServerTickRateManager tickRateManager() {
        return this.tickRateManager;
    }

    public long getAverageTickTimeNanos() {
        return this.aggregatedTickTimesNanos / (long)Math.min(100, Math.max(this.tickCount, 1));
    }

    public long[] getTickTimesNanos() {
        return this.tickTimesNanos;
    }

    public int getProfilePermissions(GameProfile p_129945_) {
        if (this.getPlayerList().isOp(p_129945_)) {
            ServerOpListEntry serveroplistentry = this.getPlayerList().getOps().get(p_129945_);
            if (serveroplistentry != null) {
                return serveroplistentry.getLevel();
            } else if (this.isSingleplayerOwner(p_129945_)) {
                return 4;
            } else if (this.isSingleplayer()) {
                return this.getPlayerList().isAllowCommandsForAllPlayers() ? 4 : 0;
            } else {
                return this.getOperatorUserPermissionLevel();
            }
        } else {
            return 0;
        }
    }

    public ProfilerFiller getProfiler() {
        if (true || gg.pufferfish.pufferfish.PufferfishConfig.disableMethodProfiler) return net.minecraft.util.profiling.InactiveProfiler.INSTANCE;
        return this.profiler;
    }

    public abstract boolean isSingleplayerOwner(GameProfile p_129840_);

    private Map<ResourceKey<Level>, long[]> perWorldTickTimes = Maps.newIdentityHashMap();
    @Nullable
    public long[] getTickTime(ResourceKey<Level> dim) {
        return perWorldTickTimes.get(dim);
    }

    @Deprecated //Forge Internal use Only, You can screw up a lot of things if you mess with this map.
    public synchronized Map<ResourceKey<Level>, ServerLevel> forgeGetWorldMap() {
        return this.levels;
    }
    private int worldArrayMarker = 0;
    private int worldArrayLast = -1;
    private ServerLevel[] worldArray;
    @Deprecated //Forge Internal use Only, use to protect against concurrent modifications in the world tick loop.
    public synchronized void markWorldsDirty() {
        worldArrayMarker++;
    }
    private ServerLevel[] getWorldArray() {
        if (worldArrayMarker == worldArrayLast && worldArray != null)
            return worldArray;
        worldArray = this.levels.values().stream().toArray(x -> new ServerLevel[x]);
        worldArrayLast = worldArrayMarker;
        return worldArray;
    }

    public void dumpServerProperties(Path p_177911_) throws IOException {
    }

    private void saveDebugReport(Path p_129860_) {
        Path path = p_129860_.resolve("levels");

        try {
            for (Entry<ResourceKey<Level>, ServerLevel> entry : this.levels.entrySet()) {
                ResourceLocation resourcelocation = entry.getKey().location();
                Path path1 = path.resolve(resourcelocation.getNamespace()).resolve(resourcelocation.getPath());
                Files.createDirectories(path1);
                entry.getValue().saveDebugReport(path1);
            }

            this.dumpGameRules(p_129860_.resolve("gamerules.txt"));
            this.dumpClasspath(p_129860_.resolve("classpath.txt"));
            this.dumpMiscStats(p_129860_.resolve("stats.txt"));
            this.dumpThreads(p_129860_.resolve("threads.txt"));
            this.dumpServerProperties(p_129860_.resolve("server.properties.txt"));
            this.dumpNativeModules(p_129860_.resolve("modules.txt"));
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to save debug report", (Throwable)ioexception);
        }
    }

    private void dumpMiscStats(Path p_129951_) throws IOException {
        try (Writer writer = Files.newBufferedWriter(p_129951_)) {
            writer.write(String.format(Locale.ROOT, "pending_tasks: %d\n", this.getPendingTasksCount()));
            writer.write(String.format(Locale.ROOT, "average_tick_time: %f\n", this.getCurrentSmoothedTickTime()));
            writer.write(String.format(Locale.ROOT, "tick_times: %s\n", Arrays.toString(this.tickTimesNanos)));
            writer.write(String.format(Locale.ROOT, "queue: %s\n", Util.backgroundExecutor()));
        }
    }

    private void dumpGameRules(Path p_129984_) throws IOException {
        try (Writer writer = Files.newBufferedWriter(p_129984_)) {
            final List<String> list = Lists.newArrayList();
            final GameRules gamerules = this.getGameRules();
            GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
                @Override
                public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> p_195531_, GameRules.Type<T> p_195532_) {
                    list.add(String.format(Locale.ROOT, "%s=%s\n", p_195531_.getId(), gamerules.getRule(p_195531_)));
                }
            });

            for (String s : list) {
                writer.write(s);
            }
        }
    }

    private void dumpClasspath(Path p_129992_) throws IOException {
        try (Writer writer = Files.newBufferedWriter(p_129992_)) {
            String s = System.getProperty("java.class.path");
            String s1 = System.getProperty("path.separator");

            for (String s2 : Splitter.on(s1).split(s)) {
                writer.write(s2);
                writer.write("\n");
            }
        }
    }

    private void dumpThreads(Path p_129996_) throws IOException {
        ThreadMXBean threadmxbean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] athreadinfo = threadmxbean.dumpAllThreads(true, true);
        Arrays.sort(athreadinfo, Comparator.comparing(ThreadInfo::getThreadName));

        try (Writer writer = Files.newBufferedWriter(p_129996_)) {
            for (ThreadInfo threadinfo : athreadinfo) {
                writer.write(threadinfo.toString());
                writer.write(10);
            }
        }
    }

    private void dumpNativeModules(Path p_195522_) throws IOException {
        try (Writer writer = Files.newBufferedWriter(p_195522_)) {
            List<NativeModuleLister.NativeModuleInfo> list;
            try {
                list = Lists.newArrayList(NativeModuleLister.listModules());
            } catch (Throwable throwable) {
                LOGGER.warn("Failed to list native modules", throwable);
                return;
            }

            list.sort(Comparator.comparing(p_212910_ -> p_212910_.name));

            for (NativeModuleLister.NativeModuleInfo nativemodulelister$nativemoduleinfo : list) {
                writer.write(nativemodulelister$nativemoduleinfo.toString());
                writer.write(10);
            }
        }
    }

    private void startMetricsRecordingTick() {
        if (true) return;
        if (this.willStartRecordingMetrics) {
            this.metricsRecorder = ActiveMetricsRecorder.createStarted(
                new ServerMetricsSamplersProvider(Util.timeSource, this.isDedicatedServer()),
                Util.timeSource,
                Util.ioPool(),
                new MetricsPersister("server"),
                this.onMetricsRecordingStopped,
                p_212927_ -> {
                    this.executeBlocking(() -> this.saveDebugReport(p_212927_.resolve("server")));
                    this.onMetricsRecordingFinished.accept(p_212927_);
                }
            );
            this.willStartRecordingMetrics = false;
        }

        this.profiler = SingleTickProfiler.decorateFiller(this.metricsRecorder.getProfiler(), SingleTickProfiler.createTickProfiler("Server"));
        this.metricsRecorder.startTick();
        this.profiler.startTick();
    }

    public void endMetricsRecordingTick() {
        this.profiler.endTick();
        this.metricsRecorder.endTick();
    }

    public boolean isRecordingMetrics() {
        return this.metricsRecorder.isRecording();
    }

    public void startRecordingMetrics(Consumer<ProfileResults> p_177924_, Consumer<Path> p_177925_) {
        this.onMetricsRecordingStopped = p_212922_ -> {
            this.stopRecordingMetrics();
            p_177924_.accept(p_212922_);
        };
        this.onMetricsRecordingFinished = p_177925_;
        this.willStartRecordingMetrics = true;
    }

    public void stopRecordingMetrics() {
        this.metricsRecorder = InactiveMetricsRecorder.INSTANCE;
    }

    public void finishRecordingMetrics() {
        this.metricsRecorder.end();
    }

    public void cancelRecordingMetrics() {
        this.metricsRecorder.cancel();
        this.profiler = this.metricsRecorder.getProfiler();
    }

    public Path getWorldPath(LevelResource p_129844_) {
        return this.storageSource.getLevelPath(p_129844_);
    }

    public boolean forceSynchronousWrites() {
        return true;
    }

    public StructureTemplateManager getStructureManager() {
        return this.structureTemplateManager;
    }

    public WorldData getWorldData() {
        return this.worldData;
    }

    public ReloadableResources getServerResources() {
         return resources;
    }

    public RegistryAccess.Frozen registryAccess() {
        return this.registries.compositeAccess();
    }

    public LayeredRegistryAccess<RegistryLayer> registries() {
        return this.registries;
    }

    public ReloadableServerRegistries.Holder reloadableRegistries() {
        return this.resources.managers.fullRegistries();
    }

    public TextFilter createTextFilterForPlayer(ServerPlayer p_129814_) {
        return TextFilter.DUMMY;
    }

    public ServerPlayerGameMode createGameModeForPlayer(ServerPlayer p_177934_) {
        return (ServerPlayerGameMode)(this.isDemo() ? new DemoMode(p_177934_) : new ServerPlayerGameMode(p_177934_));
    }

    @Nullable
    public GameType getForcedGameType() {
        return null;
    }

    public ResourceManager getResourceManager() {
        return this.resources.resourceManager;
    }

    public boolean isCurrentlySaving() {
        return this.isSaving;
    }

    public boolean isTimeProfilerRunning() {
        return this.debugCommandProfilerDelayStart || this.debugCommandProfiler != null;
    }

    public void startTimeProfiler() {
        this.debugCommandProfilerDelayStart = true;
    }

    public ProfileResults stopTimeProfiler() {
        if (this.debugCommandProfiler == null) {
            return EmptyProfileResults.EMPTY;
        } else {
            ProfileResults profileresults = this.debugCommandProfiler.stop(Util.getNanos(), this.tickCount);
            this.debugCommandProfiler = null;
            return profileresults;
        }
    }

    public int getMaxChainedNeighborUpdates() {
        return 1000000;
    }

    public void logChatMessage(Component p_241503_, ChatType.Bound p_241402_, @Nullable String p_241481_) {
        String s = p_241402_.decorate(p_241503_).getString();
        if (p_241481_ != null) {
            LOGGER.info("[{}] {}", p_241481_, s);
        } else {
            LOGGER.info("{}", s);
        }
    }

    public final ExecutorService chatExecutor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    public final ChatDecorator improvedChatDecorator = new io.papermc.paper.adventure.ImprovedChatDecorator(this); // Paper - adventure

    public ChatDecorator getChatDecoratorPaper() {
        return this.improvedChatDecorator;
    }

    public ChatDecorator getChatDecorator() {
        return this.improvedChatDecorator; // Paper - support async chat decoration events
    }

    public boolean logIPs() {
        return true;
    }

    public void subscribeToDebugSample(ServerPlayer p_324078_, RemoteDebugSampleType p_323992_) {
    }

    public boolean acceptsTransfers() {
        return false;
    }

    private void storeChunkIoError(CrashReport p_352397_, ChunkPos p_352348_, RegionStorageInfo p_352231_) {
        Util.ioPool().execute(() -> {
            try {
                Path path = this.getFile("debug");
                FileUtil.createDirectoriesSafe(path);
                String s = FileUtil.sanitizeName(p_352231_.level());
                Path path1 = path.resolve("chunk-" + s + "-" + Util.getFilenameFormattedDateTime() + "-server.txt");
                FileStore filestore = Files.getFileStore(path);
                long i = filestore.getUsableSpace();
                if (i < 8192L) {
                    LOGGER.warn("Not storing chunk IO report due to low space on drive {}", filestore.name());
                    return;
                }

                CrashReportCategory crashreportcategory = p_352397_.addCategory("Chunk Info");
                crashreportcategory.setDetail("Level", p_352231_::level);
                crashreportcategory.setDetail("Dimension", () -> p_352231_.dimension().location().toString());
                crashreportcategory.setDetail("Storage", p_352231_::type);
                crashreportcategory.setDetail("Position", p_352348_::toString);
                p_352397_.saveToFile(path1, ReportType.CHUNK_IO_ERROR);
                LOGGER.info("Saved details to {}", p_352397_.getSaveFile());
            } catch (Exception exception) {
                LOGGER.warn("Failed to store chunk IO exception", (Throwable)exception);
            }
        });
    }

    @Override
    public void reportChunkLoadFailure(Throwable p_352289_, RegionStorageInfo p_352335_, ChunkPos p_330507_) {
        LOGGER.error("Failed to load chunk {},{}", p_330507_.x, p_330507_.z, p_352289_);
        this.storeChunkIoError(CrashReport.forThrowable(p_352289_, "Chunk load failure"), p_330507_, p_352335_);
    }

    @Override
    public void reportChunkSaveFailure(Throwable p_352232_, RegionStorageInfo p_352253_, ChunkPos p_331741_) {
        LOGGER.error("Failed to save chunk {},{}", p_331741_.x, p_331741_.z, p_352232_);
        this.storeChunkIoError(CrashReport.forThrowable(p_352232_, "Chunk save failure"), p_331741_, p_352253_);
    }

    public PotionBrewing potionBrewing() {
        return this.potionBrewing;
    }

    public ServerLinks serverLinks() {
        return ServerLinks.EMPTY;
    }

    public static record ReloadableResources(CloseableResourceManager resourceManager, ReloadableServerResources managers) implements AutoCloseable {
        @Override
        public void close() {
            this.resourceManager.close();
        }
    }

    public static record ServerResourcePackInfo(UUID id, String url, String hash, boolean isRequired, @Nullable Component prompt) {
    }

    public static class TimeProfiler {
        final long startNanos;
        final int startTick;

        public TimeProfiler(long p_177958_, int p_177959_) {
            this.startNanos = p_177958_;
            this.startTick = p_177959_;
        }

        ProfileResults stop(final long p_177961_, final int p_177962_) {
            return new ProfileResults() {
                @Override
                public List<ResultField> getTimes(String p_177972_) {
                    return Collections.emptyList();
                }

                @Override
                public boolean saveResults(Path p_177974_) {
                    return false;
                }

                @Override
                public long getStartTimeNano() {
                    return TimeProfiler.this.startNanos;
                }

                @Override
                public int getStartTimeTicks() {
                    return TimeProfiler.this.startTick;
                }

                @Override
                public long getEndTimeNano() {
                    return p_177961_;
                }

                @Override
                public int getEndTimeTicks() {
                    return p_177962_;
                }

                @Override
                public String getProfilerResults() {
                    return "";
                }
            };
        }
    }

    // Paper start - Add tick times API and /mspt command
    public static class TickTimes {
        private final long[] times;

        public TickTimes(int length) {
            times = new long[length];
        }

        void add(int index, long time) {
            times[index % times.length] = time;
        }

        public long[] getTimes() {
            return times.clone();
        }

        public double getAverage() {
            long total = 0L;
            for (long value : times) {
                total += value;
            }
            return ((double) total / (double) times.length) * 1.0E-6D;
        }
    }
    // Paper end - Add tick times API and /mspt command
}
