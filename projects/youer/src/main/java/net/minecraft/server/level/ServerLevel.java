package net.minecraft.server.level;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mohistmc.youer.YouerConfig;
import com.mohistmc.youer.api.event.block.PlayerMayInteractBlockEvent;
import com.mohistmc.youer.feature.ban.bans.BanEntity;
import com.mohistmc.youer.feature.world.utils.ConfigByWorlds;
import com.mohistmc.youer.neoforge.NeoForgeInjectBukkit;
import com.mohistmc.youer.neoforge.YouerDerivedWorldInfo;
import com.mohistmc.youer.util.Level2LevelStem;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportType;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Mth;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.Container;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.portal.PortalForcer;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapIndex;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.LevelTicks;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.generator.CustomChunkGenerator;
import org.bukkit.craftbukkit.generator.CustomWorldChunkManager;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.craftbukkit.util.CraftSpawnCategory;
import org.bukkit.craftbukkit.util.WorldUUID;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.slf4j.Logger;
import org.spigotmc.AsyncCatcher;
import org.spigotmc.SpigotWorldConfig;

public class ServerLevel extends Level implements WorldGenLevel, ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel, ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevelReader { // Folia - rewrite chunk system
    public static final BlockPos END_SPAWN_POINT = new BlockPos(100, 50, 0);
    public static final IntProvider RAIN_DELAY = UniformInt.of(12000, 180000);
    public static final IntProvider RAIN_DURATION = UniformInt.of(12000, 24000);
    private static final IntProvider THUNDER_DELAY = UniformInt.of(12000, 180000);
    public static final IntProvider THUNDER_DURATION = UniformInt.of(3600, 15600);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int EMPTY_TIME_NO_TICK = 300;
    private static final int MAX_SCHEDULED_TICKS_PER_TICK = 65536;
    final List<ServerPlayer> players = Lists.newArrayList();
    public final ServerChunkCache chunkSource;
    private final MinecraftServer server;
    public final ServerLevelData serverLevelData;
    // Folia start - region threading - delayed world init
    public static final int WORLD_INIT_NOT_CHECKED = 0;
    public static final int WORLD_INIT_CHECKING = 1;
    public static final int WORLD_INIT_CHECKED = 2;
    public final java.util.concurrent.atomic.AtomicInteger checkInitialised = new java.util.concurrent.atomic.AtomicInteger(WORLD_INIT_NOT_CHECKED);
    // Folia end - region threading - delayed world init
    private int lastSpawnChunkRadius;
    final EntityTickList entityTickList = new EntityTickList();
    public final PersistentEntitySectionManager<Entity> entityManager;
    private final GameEventDispatcher gameEventDispatcher;
    public boolean noSave;

    // Folia start - region threading
    public final io.papermc.paper.threadedregions.TickRegions tickRegions = new io.papermc.paper.threadedregions.TickRegions();
    public final io.papermc.paper.threadedregions.ThreadedRegionizer<io.papermc.paper.threadedregions.TickRegions.TickRegionData, io.papermc.paper.threadedregions.TickRegions.TickRegionSectionData> regioniser;
    public final io.papermc.paper.threadedregions.RegionizedTaskQueue.WorldRegionTaskData taskQueueRegionData = new io.papermc.paper.threadedregions.RegionizedTaskQueue.WorldRegionTaskData(this);
    public io.papermc.paper.threadedregions.RegionizedServer.WorldLevelData tickData;

    public static final record PendingTeleport(net.minecraft.world.entity.Entity.EntityTreeNode rootVehicle, Vec3 to) {}

    private final it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<PendingTeleport> pendingTeleports = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>();

    public void pushPendingTeleport(final PendingTeleport teleport) {
        synchronized (this.pendingTeleports) {
            this.pendingTeleports.add(teleport);
        }
    }

    public boolean removePendingTeleport(final PendingTeleport teleport) {
        synchronized (this.pendingTeleports) {
            return this.pendingTeleports.remove(teleport);
        }
    }

    public List<PendingTeleport> removeAllRegionTeleports() {
        final List<PendingTeleport> ret = new ArrayList<>();

        synchronized (this.pendingTeleports) {
            for (final Iterator<PendingTeleport> iterator = this.pendingTeleports.iterator(); iterator.hasNext(); ) {
                final PendingTeleport pendingTeleport = iterator.next();
                if (ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this, pendingTeleport.to())) {
                    ret.add(pendingTeleport);
                    iterator.remove();
                }
            }
        }

        return ret;
    }

    @Override
    public List<net.minecraft.server.level.ServerPlayer> getLocalPlayers() {
        return this.getPlayers(p -> true);
    }

    @Nullable
    public ServerPlayer getRandomLocalPlayer() {
        List<ServerPlayer> list = this.getLocalPlayers();
        list = new java.util.ArrayList<>(list);
        list.removeIf((ServerPlayer player) -> {
            return !player.isAlive();
        });

        return list.isEmpty() ? null : (ServerPlayer) list.get(this.random.nextInt(list.size()));
    }
    // Folia end - region threading

    // Paper start - rewrite chunk system
    private boolean markedClosing;
    private final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.ViewDistanceHolder viewDistanceHolder = new ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.ViewDistanceHolder();
    private final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader chunkLoader = new ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader((ServerLevel)(Object)this);
    private final ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.EntityDataController entityDataController;
    private final ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.PoiDataController poiDataController;
    private final ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.ChunkDataController chunkDataController;
    private final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler chunkTaskScheduler;
    private long lastMidTickFailure;
    private long tickedBlocksOrFluids;

    @Override
    public final LevelChunk moonrise$getFullChunkIfLoaded(final int chunkX, final int chunkZ) {
        return this.chunkSource.getChunkNow(chunkX, chunkZ);
    }

    @Override
    public final ChunkAccess moonrise$getAnyChunkIfLoaded(final int chunkX, final int chunkZ) {
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder newChunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(ca.spottedleaf.moonrise.common.util.CoordinateUtils.getChunkKey(chunkX, chunkZ));
        if (newChunkHolder == null) {
            return null;
        }
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder.ChunkCompletion lastCompletion = newChunkHolder.getLastChunkCompletion();
        return lastCompletion == null ? null : lastCompletion.chunk();
    }

    @Override
    public final ChunkAccess moonrise$getSpecificChunkIfLoaded(final int chunkX, final int chunkZ, final net.minecraft.world.level.chunk.status.ChunkStatus leastStatus) {
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder newChunkHolder = this.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkX, chunkZ);
        if (newChunkHolder == null) {
            return null;
        }
        return newChunkHolder.getChunkIfPresentUnchecked(leastStatus);
    }

    @Override
    public final void moonrise$midTickTasks() {
        ((ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer)this.server).moonrise$executeMidTickTasks();
    }

    @Override
    public final ChunkAccess moonrise$syncLoadNonFull(final int chunkX, final int chunkZ, final net.minecraft.world.level.chunk.status.ChunkStatus status) {
        return this.moonrise$getChunkTaskScheduler().syncLoadNonFull(chunkX, chunkZ, status);
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler moonrise$getChunkTaskScheduler() {
        return this.chunkTaskScheduler;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.ChunkDataController moonrise$getChunkDataController() {
        return this.chunkDataController;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.ChunkDataController moonrise$getPoiChunkDataController() {
        return this.poiDataController;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.io.RegionFileIOThread.ChunkDataController moonrise$getEntityChunkDataController() {
        return this.entityDataController;
    }

    @Override
    public final int moonrise$getRegionChunkShift() {
        return this.regioniser.sectionChunkShift;
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader moonrise$getPlayerChunkLoader() {
        return this.chunkLoader;
    }

    @Override
    public final void moonrise$loadChunksAsync(final BlockPos pos, final int radiusBlocks,
                                               final ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority priority,
                                               final java.util.function.Consumer<java.util.List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        this.moonrise$loadChunksAsync(
            (pos.getX() - radiusBlocks) >> 4,
            (pos.getX() + radiusBlocks) >> 4,
            (pos.getZ() - radiusBlocks) >> 4,
            (pos.getZ() + radiusBlocks) >> 4,
            priority, onLoad
        );
    }

    // Folia start - region threading - loadChunksForMoveAsync
    public final void loadChunksForMoveAsync(AABB axisalignedbb, ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority priority,
                                             java.util.function.Consumer<java.util.List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        int minBlockX = Mth.floor(axisalignedbb.minX - 1.0E-7D) - 3;
        int maxBlockX = Mth.floor(axisalignedbb.maxX + 1.0E-7D) + 3;
        int minBlockZ = Mth.floor(axisalignedbb.minZ - 1.0E-7D) - 3;
        int maxBlockZ = Mth.floor(axisalignedbb.maxZ + 1.0E-7D) + 3;
        int minChunkX = minBlockX >> 4;
        int maxChunkX = maxBlockX >> 4;
        int minChunkZ = minBlockZ >> 4;
        int maxChunkZ = maxBlockZ >> 4;
        this.moonrise$loadChunksAsync(minChunkX, maxChunkX, minChunkZ, maxChunkZ, priority, onLoad);
    }
    // Folia end - region threading

    @Override
    public final void moonrise$loadChunksAsync(final BlockPos pos, final int radiusBlocks,
                                               final net.minecraft.world.level.chunk.status.ChunkStatus chunkStatus, final ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority priority,
                                               final java.util.function.Consumer<java.util.List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        this.moonrise$loadChunksAsync(
            (pos.getX() - radiusBlocks) >> 4,
            (pos.getX() + radiusBlocks) >> 4,
            (pos.getZ() - radiusBlocks) >> 4,
            (pos.getZ() + radiusBlocks) >> 4,
            chunkStatus, priority, onLoad
        );
    }

    @Override
    public final void moonrise$loadChunksAsync(final int minChunkX, final int maxChunkX, final int minChunkZ, final int maxChunkZ,
                                               final ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority priority,
                                               final java.util.function.Consumer<java.util.List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        this.moonrise$loadChunksAsync(minChunkX, maxChunkX, minChunkZ, maxChunkZ, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, priority, onLoad);
    }

    @Override
    public final void moonrise$loadChunksAsync(final int minChunkX, final int maxChunkX, final int minChunkZ, final int maxChunkZ,
                                               final net.minecraft.world.level.chunk.status.ChunkStatus chunkStatus, final ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority priority,
                                               final java.util.function.Consumer<java.util.List<net.minecraft.world.level.chunk.ChunkAccess>> onLoad) {
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler chunkTaskScheduler = this.moonrise$getChunkTaskScheduler();
        final ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager chunkHolderManager = chunkTaskScheduler.chunkHolderManager;

        final int requiredChunks = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        final java.util.concurrent.atomic.AtomicInteger loadedChunks = new java.util.concurrent.atomic.AtomicInteger();
        final Long holderIdentifier = ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.getNextChunkLoadId();
        final int ticketLevel = ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.getTicketLevel(chunkStatus);

        final List<ChunkAccess> ret = new ArrayList<>(requiredChunks);

        final java.util.function.Consumer<net.minecraft.world.level.chunk.ChunkAccess> consumer = (final ChunkAccess chunk) -> {
            if (chunk != null) {
                synchronized (ret) {
                    ret.add(chunk);
                }
                chunkHolderManager.addTicketAtLevel(ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.CHUNK_LOAD, chunk.getPos(), ticketLevel, holderIdentifier);
            }
            if (loadedChunks.incrementAndGet() == requiredChunks) {
                try {
                    onLoad.accept(java.util.Collections.unmodifiableList(ret));
                } finally {
                    for (int i = 0, len = ret.size(); i < len; ++i) {
                        final ChunkPos chunkPos = ret.get(i).getPos();
                        chunkHolderManager.removeTicketAtLevel(ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler.CHUNK_LOAD, chunkPos, ticketLevel, holderIdentifier);
                    }
                }
            }
        };

        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                chunkTaskScheduler.scheduleChunkLoad(cx, cz, chunkStatus, true, priority, consumer);
            }
        }
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.ViewDistanceHolder moonrise$getViewDistanceHolder() {
        return this.viewDistanceHolder;
    }

    @Override
    public final long moonrise$getLastMidTickFailure() {
        return this.lastMidTickFailure;
    }

    @Override
    public final void moonrise$setLastMidTickFailure(final long time) {
        this.lastMidTickFailure = time;
    }

    @Override
    public final ca.spottedleaf.moonrise.common.misc.NearbyPlayers moonrise$getNearbyPlayers() {
        return this.getCurrentWorldData().getNearbyPlayers();
    }

    @Override
    public final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.server.level.ServerChunkCache.ChunkAndHolder> moonrise$getLoadedChunks() {
        return this.getCurrentWorldData().getChunks();
    }

    @Override
    public final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.server.level.ServerChunkCache.ChunkAndHolder> moonrise$getTickingChunks() {
        return this.getCurrentWorldData().getTickingChunks();
    }

    @Override
    public final ca.spottedleaf.moonrise.common.list.ReferenceList<net.minecraft.server.level.ServerChunkCache.ChunkAndHolder> moonrise$getEntityTickingChunks() {
        return this.getCurrentWorldData().getEntityTickingChunks();
    }
    // Paper end - rewrite chunk system
    // Folia end - region threading
    private final SleepStatus sleepStatus;
    private int emptyTime;
    private final PortalForcer portalForcer;
    private final LevelTicks<Block> blockTicks = new LevelTicks<>(this::isPositionTickingWithEntitiesLoaded, this.getProfilerSupplier());
    private final LevelTicks<Fluid> fluidTicks = new LevelTicks<>(this::isPositionTickingWithEntitiesLoaded, this.getProfilerSupplier());
    private final PathTypeCache pathTypesByPosCache = new PathTypeCache();
    final Set<Mob> navigatingMobs = new ObjectOpenHashSet<>();
    volatile boolean isUpdatingNavigations;
    protected final Raids raids;
    private final ObjectLinkedOpenHashSet<BlockEventData> blockEvents = new ObjectLinkedOpenHashSet<>();
    private final List<BlockEventData> blockEventsToReschedule = new ArrayList<>(64);
    private boolean handlingTick;
    private final List<CustomSpawner> customSpawners;
    @Nullable
    private EndDragonFight dragonFight;
    final Int2ObjectMap<net.neoforged.neoforge.entity.PartEntity<?>> dragonParts = new Int2ObjectOpenHashMap<>();
    private final StructureManager structureManager;
    private final StructureCheck structureCheck;
    public final boolean tickTime; // Folia - region threading - public
    private final RandomSequences randomSequences;

    public PrimaryLevelData K;
    public final LevelStorageSource.LevelStorageAccess convertable;
    public final UUID uuid;
    public ResourceKey<LevelStem> typeKey;
    public boolean hasEntityMoveEvent; // Paper - Add EntityMoveEvent
    public boolean hasPhysicsEvent = true; // Paper - BlockPhysicsEvent
    public boolean hasRidableMoveEvent = false; // Purpur

    @Override
    public ResourceKey<LevelStem> getTypeKey() {
        return typeKey == null ? super.getTypeKey() : typeKey;
    }

    public CraftWorld getWorld() {
        return this.world;
    }


    // Paper start
    public final boolean areChunksLoadedForMove(AABB axisalignedbb) {
        // copied code from collision methods, so that we can guarantee that they wont load chunks (we don't override
        // ICollisionAccess methods for VoxelShapes)
        // be more strict too, add a block (dumb plugins in move events?)
        int minBlockX = Mth.floor(axisalignedbb.minX - 1.0E-7D) - 3;
        int maxBlockX = Mth.floor(axisalignedbb.maxX + 1.0E-7D) + 3;

        int minBlockZ = Mth.floor(axisalignedbb.minZ - 1.0E-7D) - 3;
        int maxBlockZ = Mth.floor(axisalignedbb.maxZ + 1.0E-7D) + 3;

        int minChunkX = minBlockX >> 4;
        int maxChunkX = maxBlockX >> 4;

        int minChunkZ = minBlockZ >> 4;
        int maxChunkZ = maxBlockZ >> 4;

        ServerChunkCache chunkProvider = this.getChunkSource();

        for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
            for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
                if (chunkProvider.getChunkAtIfLoadedImmediately(cx, cz) == null) {
                    return false;
                }
            }
        }

        return true;
    }

    public LevelChunk getChunkIfLoaded(int x, int z) {
        return this.chunkSource.getChunkAtIfLoadedImmediately(x, z); // Paper - Use getChunkIfLoadedImmediately
    }

    // Paper start - optimise getPlayerByUUID
    @Nullable
    @Override
    public Player getPlayerByUUID(UUID uuid) {
        final Player player = this.getServer().getPlayerList().getPlayer(uuid);
        return player != null && player.level() == this ? player : null;
    }
    // Paper end - optimise getPlayerByUUID
    // Paper start - lag compensation
    private long lagCompensationTick = net.minecraft.server.MinecraftServer.SERVER_INIT;

    public long getLagCompensationTick() {
        return this.lagCompensationTick;
    }

    public void updateLagCompensationTick() {
        this.lagCompensationTick = (System.nanoTime() - net.minecraft.server.MinecraftServer.SERVER_INIT) / (java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(50L));
    }
    // Paper end - lag compensation

    public ServerLevel(
        MinecraftServer p_214999_,
        Executor p_215000_,
        LevelStorageSource.LevelStorageAccess p_215001_,
        ServerLevelData p_215002_,
        ResourceKey<Level> p_215003_,
        LevelStem p_215004_,
        ChunkProgressListener p_215005_,
        boolean p_215006_,
        long p_215007_,
        List<CustomSpawner> p_215008_,
        boolean p_215009_,
        @Nullable RandomSequences p_288977_
    ) {
        super(
            p_215002_,
            p_215003_,
            p_214999_.registryAccess(),
            p_215004_.type(),
            p_214999_::getProfiler,
            false,
            p_215006_,
            p_215007_,
            p_214999_.getMaxChainedNeighborUpdates()
        );
        // CraftBukkit start
        this.pvpMode = p_214999_.isPvpAllowed();
        this.convertable = p_215001_;
        // Youer start
        File worldFile = DimensionType.getStorageFolder(p_215003_, p_215001_.levelDirectory.path()).toFile();
        uuid = Level2LevelStem.bukkit != null ? WorldUUID.getUUID(Level2LevelStem.bukkit) : WorldUUID.getUUID(worldFile);
        name = Level2LevelStem.bukkit != null ? Level2LevelStem.bukkit_name : worldFile.getName();
        var typeKey = p_215001_.dimensionType;
        if (typeKey != null) {
            this.typeKey = typeKey;
        } else {
            var dimensions = p_214999_.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
            var key = dimensions.getResourceKey(p_215004_);
            if (key.isPresent()) {
                this.typeKey = key.get();
            } else {
                this.typeKey = ResourceKey.create(Registries.LEVEL_STEM, dimension.location());
            }
            if (p_215002_ instanceof DerivedLevelData data) {
                data.setTypeKey(this.getTypeKey());
            }
        }
        this.tickTime = p_215009_;
        this.server = p_214999_;
        this.serverLevelData = p_215002_;
        if (p_215002_ instanceof PrimaryLevelData) {
            this.K = (PrimaryLevelData) p_215002_;
        } else {
            this.K = YouerDerivedWorldInfo.create(p_215002_);
        }
        this.spigotConfig = new SpigotWorldConfig(name); // Spigot
        paperWorldConfigCreator = spigotConfig -> p_214999_.paperConfigurations.createWorldConfig(
                io.papermc.paper.configuration.PaperConfigurations.createWorldContextMap(
                        p_215001_.levelDirectory.path(),
                        name, // Youer  - use world name
                        p_215003_.location(),
                        spigotConfig,
                        p_214999_.registryAccess(),
                        (K != null ? K.getGameRules() : serverLevelData.getGameRules())));
        this.paperConfig = paperWorldConfigCreator.apply(this.spigotConfig); // Paper - create paper world config
        this.purpurConfig = new org.purpurmc.purpur.PurpurWorldConfig(name, environment); // Purpur
        // CraftBukkit Ticks things
        for (SpawnCategory spawnCategory : SpawnCategory.values()) {
            if (CraftSpawnCategory.isValidForLimits(spawnCategory) && getCraftServer() != null) {
                this.ticksPerSpawnCategory.put(spawnCategory, this.getTicksPerSpawn(spawnCategory)); // Paper
            }
        }
        ChunkGenerator chunkgenerator = p_215004_.generator();
        if (environment == null) {
            environment = NeoForgeInjectBukkit.environment.get(getTypeKey());
        }

        this.world = new CraftWorld(this, generator, biomeProvider, environment);
        if (biomeProvider != null) {
            BiomeSource worldChunkManager = new CustomWorldChunkManager(world, biomeProvider, server.registryAccess().registryOrThrow(Registries.BIOME), chunkgenerator.getBiomeSource()); // Paper - add vanillaBiomeProvider
            if (chunkgenerator instanceof NoiseBasedChunkGenerator cga) {
                chunkgenerator = new NoiseBasedChunkGenerator(worldChunkManager, cga.settings);
            } else if (chunkgenerator instanceof FlatLevelSource cpf) {
                chunkgenerator = new FlatLevelSource(cpf.settings(), worldChunkManager);
            }
        }

        if (generator != null) {
            chunkgenerator = new CustomChunkGenerator(this, chunkgenerator, generator);
        }

        boolean flag = p_214999_.forceSynchronousWrites();
        DataFixer datafixer = p_214999_.getFixerUpper();
        EntityPersistentStorage<Entity> entitypersistentstorage = new EntityStorage(
            new SimpleRegionStorage(
                new RegionStorageInfo(p_215001_.getLevelId(), p_215003_, "entities"),
                p_215001_.getDimensionPath(p_215003_).resolve("entities"),
                datafixer,
                flag,
                DataFixTypes.ENTITY_CHUNK
            ),
            this,
            p_214999_
        );
        this.entityManager = new PersistentEntitySectionManager<>(Entity.class, new ServerLevel.EntityCallbacks(), entitypersistentstorage);
        this.chunkSource = new ServerChunkCache(
            this,
            p_215001_,
            datafixer,
            p_214999_.getStructureManager(),
            p_215000_,
            chunkgenerator,
            this.spigotConfig.viewDistance, // Spigot
            this.spigotConfig.simulationDistance, // Spigot
            flag,
            p_215005_,
            this.entityManager::updateChunkStatus,
            () -> p_214999_.overworld().getDataStorage()
        );
        this.chunkSource.getGeneratorState().ensureStructuresGenerated();
        this.portalForcer = new PortalForcer(this);
        this.updateSkyBrightness();
        this.prepareWeather();
        this.getWorldBorder().setAbsoluteMaxSize(p_214999_.getAbsoluteMaxWorldSize());
        this.raids = this.getDataStorage().computeIfAbsent(Raids.factory(this), Raids.getFileId(this.dimensionTypeRegistration()));
        if (!p_214999_.isSingleplayer()) {
            p_215002_.setGameType(p_214999_.getDefaultGameType());
        }

        long i = p_214999_.getWorldData().worldGenOptions().seed();
        this.structureCheck = new StructureCheck(
            this.chunkSource.chunkScanner(),
            this.registryAccess(),
            p_214999_.getStructureManager(),
            p_215003_,
            chunkgenerator,
            this.chunkSource.randomState(),
            this,
            chunkgenerator.getBiomeSource(),
            i,
            datafixer
        );
        this.structureManager = new StructureManager(this, this.K.worldGenOptions(), this.structureCheck); // CraftBukkit
        if (this.dimension() == Level.END && this.dimensionTypeRegistration().is(BuiltinDimensionTypes.END) || environment == org.bukkit.World.Environment.THE_END) { // CraftBukkit - Allow to create EnderDragonBattle in default and custom END
            this.dragonFight = new EndDragonFight(this, this.K.worldGenOptions().seed(), this.K.endDragonFightData());
        } else {
            this.dragonFight = null;
        }

        this.sleepStatus = new SleepStatus();
        this.gameEventDispatcher = new GameEventDispatcher(this);
        this.randomSequences = Objects.requireNonNullElseGet(
            p_288977_, () -> this.getDataStorage().computeIfAbsent(RandomSequences.factory(i), "random_sequences")
        );

        net.neoforged.neoforge.attachment.LevelAttachmentsSavedData.init(this);
        // Neo: Move the initialization of customSpawners to the end of costructor
        // Providing a fully initialized ServerLevel instance for the ServerLevelEvent.CustomSpawners
        this.customSpawners = net.neoforged.neoforge.event.EventHooks.getCustomSpawners(this, p_215008_);
        this.getCraftServer().addWorld(this.getWorld()); // CraftBukkit
        this.K.setWorld(this);
        // CraftBukkit start
        getWorldBorder().world = this;
        // From PlayerList.setPlayerFileData
        getWorldBorder().addListener(new BorderChangeListener() {

            @Override
            public void onBorderSizeSet(WorldBorder pBorder, double pSize) {
                getCraftServer().getHandle().broadcastAll(new ClientboundSetBorderSizePacket(pBorder), pBorder.world);
            }

            @Override
            public void onBorderSizeLerping(WorldBorder pBorder, double pOldSize, double pNewSize, long pTime) {
                getCraftServer().getHandle().broadcastAll(new ClientboundSetBorderLerpSizePacket(pBorder), pBorder.world);
            }

            @Override
            public void onBorderCenterSet(WorldBorder pBorder, double pX, double pZ) {
                getCraftServer().getHandle().broadcastAll(new ClientboundSetBorderCenterPacket(pBorder), pBorder.world);
            }

            @Override
            public void onBorderSetWarningTime(WorldBorder pBorder, int pWarningTime) {
                getCraftServer().getHandle().broadcastAll(new ClientboundSetBorderWarningDelayPacket(pBorder), pBorder.world);
            }

            @Override
            public void onBorderSetWarningBlocks(WorldBorder pBorder, int pWarningBlocks) {
                getCraftServer().getHandle().broadcastAll(new ClientboundSetBorderWarningDistancePacket(pBorder), pBorder.world);
            }

            @Override
            public void onBorderSetDamagePerBlock(WorldBorder pBorder, double pDamagePerBlock) { }

            @Override
            public void onBorderSetDamageSafeZOne(WorldBorder pBorder, double pDamageSafeZone) { }
        });
        // CraftBukkit end
        ConfigByWorlds.initMods(this);

        // Folia start - region threading
        this.regioniser = new io.papermc.paper.threadedregions.ThreadedRegionizer<>(
            (int)Math.max(1L, (8L * 16L * 16L) / (1L << (2 * (io.papermc.paper.threadedregions.TickRegions.getRegionChunkShift())))),
            (1.0 / 6.0),
            Math.max(1, 8 / (1 << io.papermc.paper.threadedregions.TickRegions.getRegionChunkShift())),
            1,
            io.papermc.paper.threadedregions.TickRegions.getRegionChunkShift(),
            this,
            this.tickRegions
        );
        this.updateTickData();
        // Folia end - region threading

        // Paper start - rewrite chunk system
        this.entityDataController = new ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.EntityDataController(
            new ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.EntityDataController.EntityRegionFileStorage(
                new RegionStorageInfo(p_215001_.getLevelId(), p_215003_, "entities"),
                p_215001_.getDimensionPath(p_215003_).resolve("entities"),
                p_214999_.forceSynchronousWrites()
            )
        );
        this.poiDataController = new ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.PoiDataController((ServerLevel)(Object)this);
        this.chunkDataController = new ca.spottedleaf.moonrise.patches.chunk_system.io.datacontroller.ChunkDataController((ServerLevel)(Object)this);
        this.moonrise$setEntityLookup(new ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup((ServerLevel)(Object)this, ((ServerLevel)(Object)this).new EntityCallbacks()));
        this.chunkTaskScheduler = new ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler((ServerLevel)(Object)this, ca.spottedleaf.moonrise.common.util.MoonriseCommon.WORKER_POOL);
        // Paper end - rewrite chunk system
    }

    // Folia start - region threading
    public void updateTickData() {
        this.tickData = new io.papermc.paper.threadedregions.RegionizedServer.WorldLevelData(this, this.serverLevelData.getGameTime(), this.serverLevelData.getDayTime());
    }

    // Folia end - region threading

    @Deprecated
    @VisibleForTesting
    public void setDragonFight(@Nullable EndDragonFight p_287779_) {
        this.dragonFight = p_287779_;
    }

    public void setWeatherParameters(int p_8607_, int p_8608_, boolean p_8609_, boolean p_8610_) {
        this.K.setClearWeatherTime(p_8607_);
        this.K.setRainTime(p_8608_);
        this.K.setThunderTime(p_8608_);
        this.K.setRaining(p_8609_);
        this.K.setThundering(p_8610_);
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int p_203775_, int p_203776_, int p_203777_) {
        return this.getChunkSource()
            .getGenerator()
            .getBiomeSource()
            .getNoiseBiome(p_203775_, p_203776_, p_203777_, this.getChunkSource().randomState().sampler());
    }

    public StructureManager structureManager() {
        return this.structureManager;
    }

    public void tick(BooleanSupplier shouldKeepTicking) { // FoliaYouer - keep original signature for binpatch/mixin compat
        // Folia - regionised ticking - get region from thread context
        io.papermc.paper.threadedregions.TickRegions.TickRegionData region = null;
        io.papermc.paper.threadedregions.ThreadedRegionizer.ThreadedRegion<io.papermc.paper.threadedregions.TickRegions.TickRegionData, io.papermc.paper.threadedregions.TickRegions.TickRegionSectionData> currentRegion = io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegion();
        if (currentRegion != null) {
            region = currentRegion.getData();
        }
        final io.papermc.paper.threadedregions.RegionizedWorldData regionizedWorldData = this.getCurrentWorldData(); // Folia - regionised ticking
        ProfilerFiller profilerfiller = this.getProfiler();

        this.handlingTick = true; // FoliaYouer - keep field write for mixin compat
        regionizedWorldData.setHandlingTick(true); // Folia - regionised ticking
        TickRateManager tickratemanager = this.tickRateManager();
        boolean flag = tickratemanager.runsNormally();
        if (flag) {
            profilerfiller.push("world border");
            if (region == null) this.getWorldBorder().tick(); // Folia - regionised ticking
            profilerfiller.popPush("weather");
            if (region == null) this.advanceWeatherCycle(); // Folia - regionised ticking
        }

        //int i = this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE); // Folia - region threading - move into tickSleep
        long j;

        if (region == null) this.tickSleep(); // Folia - region threading

        if (region == null) this.updateSkyBrightness(); // Folia - region threading
        if (flag) {
            this.tickTime();
        }

        profilerfiller.popPush("tickPending");
        if (!this.isDebug() && flag) {
            j = regionizedWorldData.getRedstoneGameTime(); // Folia - region threading
            profilerfiller.push("blockTicks");
            regionizedWorldData.getBlockLevelTicks().tick(j, paperConfig().environment.maxBlockTicks, this::tickBlock); // Paper - configurable max block ticks // Folia - region ticking
            profilerfiller.popPush("fluidTicks");
            regionizedWorldData.getFluidLevelTicks().tick(j, paperConfig().environment.maxFluidTicks, this::tickFluid); // Paper - configurable max fluid ticks // Folia - region ticking
            profilerfiller.pop();
        }

        profilerfiller.popPush("raid");
        if (flag) {
            this.raids.tick();
        }

        profilerfiller.popPush("chunkSource");
        this.getChunkSource().tick(shouldKeepTicking, true);
        profilerfiller.popPush("blockEvents");
        if (flag) {
            this.runBlockEvents();
        }

        this.handlingTick = false; // FoliaYouer - keep field write for mixin compat
        regionizedWorldData.setHandlingTick(false); // Folia - regionised ticking
        profilerfiller.pop();
        boolean flag1 = !paperConfig().unsupportedSettings.disableWorldTickingWhenEmpty || !this.players.isEmpty() || net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.hasForcedChunks(this); // Neo: Replace vanilla's has forced chunk check with neo's that checks both the vanilla and neo added ones
        if (flag1) {
            this.resetEmptyTime();
        }

        if (flag1 || this.emptyTime++ < 300) {
            profilerfiller.push("entities");
            if (this.dragonFight != null && flag) {
                if (ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(this, this.dragonFight.origin)) { // Folia - region threading
                profilerfiller.push("dragonFight");
                this.dragonFight.tick();
                profilerfiller.pop();
                } else { // Folia start - region threading
                    // try to load dragon fight
                    ChunkPos fightCenter = new ChunkPos(this.dragonFight.origin);
                    this.chunkSource.addTicketAtLevel(
                        TicketType.UNKNOWN, fightCenter, ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager.MAX_TICKET_LEVEL,
                        fightCenter
                    );
                } // Folia end - region threading
            }

            // org.spigotmc.ActivationRange.activateEntities(this); // Spigot
            regionizedWorldData.forEachTickingEntity((p_308566_) -> { // Folia - regionised ticking
                if (!p_308566_.isRemoved()) {
                    if (this.shouldDiscardEntity(p_308566_)) {
                        p_308566_.discard();
                    } else if (!tickratemanager.isEntityFrozen(p_308566_)) {
                        profilerfiller.push("checkDespawn");
                        p_308566_.checkDespawn();
                        if (p_308566_.isRemoved()) return; // Folia - region threading - if we despawned, DON'T TICK IT!
                        profilerfiller.pop();
                        if (this.chunkSource.chunkMap.getDistanceManager().inEntityTickingRange(p_308566_.chunkPosition().toLong())) {
                            Entity entity = p_308566_.getVehicle();
                            if (entity != null) {
                                if (!entity.isRemoved() && entity.hasPassenger(p_308566_)) {
                                    return;
                                }

                                p_308566_.stopRiding();
                            }

                            profilerfiller.push("tick");
                            if (!p_308566_.isRemoved() && !(p_308566_ instanceof net.neoforged.neoforge.entity.PartEntity)) {
                                this.guardEntityTick(this::tickNonPassenger, p_308566_);
                            }
                            profilerfiller.pop();
                        }
                    }
                }
            });
            profilerfiller.pop();
            this.tickBlockEntities();
            spigotConfig.currentPrimedTnt = 0; // Spigot // Mohist move form Level#tickBlockEntities
        }

        profilerfiller.push("entityManagement");
        this.entityManager.tick();
        profilerfiller.pop();
    }

    // Folia start - region threading
    public void tickSleep() {
        int i = this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE); long j; // Folia moved from tick loop
        if (this.sleepStatus.areEnoughSleeping(i) && this.sleepStatus.areEnoughDeepSleeping(i, this.players)) {
            // CraftBukkit start
            j = this.levelData.getDayTime() + 24000L;
            TimeSkipEvent event = new TimeSkipEvent(this.getWorld(), TimeSkipEvent.SkipReason.NIGHT_SKIP, net.neoforged.neoforge.event.EventHooks.onSleepFinished(this, j - j % 24000L, this.getDayTime()) - this.getDayTime());
            if (this.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                this.getCraftServer().getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
                    this.setDayTime(this.getDayTime() + event.getSkipAmount());
                }
            }

            if (!event.isCancelled()) {
                this.wakeUpAllPlayers();
            }
            // CraftBukkit end

            if (this.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE) && this.isRaining()) {
                this.resetWeatherCycle();
            }
        }
    }
    // Folia end - region threading

    @Override
    public boolean shouldTickBlocksAt(long p_184059_) {
        return this.chunkSource.chunkMap.getDistanceManager().inBlockTickingRange(p_184059_);
    }

    protected void tickTime() {
        if (this.tickTime) {
            long i = this.levelData.getGameTime() + 1L;
            this.K.setGameTime(i);
            this.K.getScheduledEvents().tick(this.server, i);
            if (this.levelData.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                this.setDayTime(this.levelData.getDayTime() + advanceDaytime());
            }
        }
    }

    public void setDayTime(long p_8616_) {
        this.K.setDayTime(p_8616_);
    }

    public void tickCustomSpawners(boolean p_8800_, boolean p_8801_) {
        for (CustomSpawner customspawner : this.customSpawners) {
            customspawner.tick(this, p_8800_, p_8801_);
        }
    }

    private boolean shouldDiscardEntity(Entity p_143343_) {
        return this.server.isSpawningAnimals() || !(p_143343_ instanceof Animal) && !(p_143343_ instanceof WaterAnimal)
            ? !this.server.areNpcsEnabled() && p_143343_ instanceof Npc
            : true;
    }

    private void wakeUpAllPlayers() {
        this.sleepStatus.removeAllSleepers();
        this.players.stream().filter(LivingEntity::isSleeping).collect(Collectors.toList()).forEach(p_184116_ -> p_184116_.stopSleepInBed(false, false));
    }

    public void tickChunk(LevelChunk p_8715_, int p_8716_) {
        ChunkPos chunkpos = p_8715_.getPos();
        boolean flag = this.isRaining();
        int i = chunkpos.getMinBlockX();
        int j = chunkpos.getMinBlockZ();
        ProfilerFiller profilerfiller = this.getProfiler();
        profilerfiller.push("thunder");
        if (!this.paperConfig().environment.disableThunder && flag && this.isThundering() && this.spigotConfig.thunderChance > 0 && p_8715_.shouldDoLightning(this.random)) { // Spigot // Paper - Option to disable thunder
            BlockPos blockpos = this.findLightningTargetAround(this.getBlockRandomPos(i, 0, j, 15));
            if (this.isRainingAt(blockpos)) {
                DifficultyInstance difficultyinstance = this.getCurrentDifficultyAt(blockpos);
                boolean flag1 = this.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                    && this.random.nextDouble() < (double)difficultyinstance.getEffectiveDifficulty() * this.paperConfig().entities.spawning.skeletonHorseThunderSpawnChance.or(0.01D)
                    && !(this.getBlockState(blockpos.below()).getBlock() instanceof net.minecraft.world.level.block.LightningRodBlock); // Neo: support custom LightningRodBlocks
                if (flag1) {
                    SkeletonHorse skeletonhorse = EntityType.SKELETON_HORSE.create(this);
                    if (skeletonhorse != null) {
                        skeletonhorse.setTrap(true);
                        skeletonhorse.setAge(0);
                        skeletonhorse.setPos((double)blockpos.getX(), (double)blockpos.getY(), (double)blockpos.getZ());
                        skeletonhorse.spawnReason(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.LIGHTNING);
                        this.addFreshEntity(skeletonhorse);
                    }
                }

                LightningBolt lightningbolt = EntityType.LIGHTNING_BOLT.create(this);
                if (lightningbolt != null) {
                    lightningbolt.moveTo(Vec3.atBottomCenterOf(blockpos));
                    lightningbolt.setVisualOnly(flag1);
                    // Youer start - fix mixin
                    LightningStrikeEvent lightning = CraftEventFactory.callLightningStrikeEvent((org.bukkit.entity.LightningStrike) lightningbolt.getBukkitEntity(), org.bukkit.event.weather.LightningStrikeEvent.Cause.WEATHER);

                    if (lightning.isCancelled()) {
                        return;
                    }
                    this.addFreshEntity(lightningbolt);
                    // Youer end
                }
            }
        }

        profilerfiller.popPush("iceandsnow");

        if (!this.paperConfig().environment.disableIceAndSnow) { // Paper - Option to disable ice and snow
            for (int i1 = 0; i1 < p_8716_; i1++) {
            if (this.random.nextInt(48) == 0) {
                this.tickPrecipitation(this.getBlockRandomPos(i, 0, j, 15));
            }
        }
        } // Paper - Option to disable ice and snow

        profilerfiller.popPush("tickBlocks");
        if (p_8716_ > 0) {
            LevelChunkSection[] alevelchunksection = p_8715_.getSections();

            for (int j1 = 0; j1 < alevelchunksection.length; j1++) {
                LevelChunkSection levelchunksection = alevelchunksection[j1];
                if (levelchunksection.isRandomlyTicking()) {
                    int k1 = p_8715_.getSectionYFromSectionIndex(j1);
                    int k = SectionPos.sectionToBlockCoord(k1);

                    for (int l = 0; l < p_8716_; l++) {
                        BlockPos blockpos1 = this.getBlockRandomPos(i, k, j, 15);
                        profilerfiller.push("randomTick");
                        BlockState blockstate = levelchunksection.getBlockState(blockpos1.getX() - i, blockpos1.getY() - k, blockpos1.getZ() - j);
                        if (blockstate.isRandomlyTicking()) {
                            blockstate.randomTick(this, blockpos1, this.random);
                        }

                        FluidState fluidstate = blockstate.getFluidState();
                        if (fluidstate.isRandomlyTicking()) {
                            fluidstate.randomTick(this, blockpos1, this.random);
                        }

                        profilerfiller.pop();
                    }
                }
            }
        }

        profilerfiller.pop();
    }

    @VisibleForTesting
    public void tickPrecipitation(BlockPos p_295060_) {
        BlockPos blockpos = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, p_295060_);
        BlockPos blockpos1 = blockpos.below();
        Biome biome = this.getBiome(blockpos).value();
        if (this.isAreaLoaded(blockpos1, 1)) // Forge: check area to avoid loading neighbors in unloaded chunks
        if (biome.shouldFreeze(this, blockpos1)) {
            this.callEvent(true);
            this.setBlockAndUpdate(blockpos1, Blocks.ICE.defaultBlockState());
            CraftEventFactory.handleBlockFormEvent(this, blockpos1, this.youer$defaultBlockState, null); // CraftBukkit
        }

        if (this.isRaining()) {
            int i = this.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT);
            if (i > 0 && biome.shouldSnow(this, blockpos)) {
                BlockState blockstate = this.getBlockState(blockpos);
                if (blockstate.is(Blocks.SNOW)) {
                    int j = blockstate.getValue(SnowLayerBlock.LAYERS);
                    if (j < Math.min(i, 8)) {
                        BlockState blockstate1 = blockstate.setValue(SnowLayerBlock.LAYERS, Integer.valueOf(j + 1));
                        Block.pushEntitiesUp(blockstate, blockstate1, this, blockpos);
                        this.callEvent(true);
                        this.setBlockAndUpdate(blockpos, blockstate1);
                        CraftEventFactory.handleBlockFormEvent(this, blockpos, this.youer$defaultBlockState, null); // CraftBukkit
                    }
                } else {
                    this.callEvent(true);
                    this.setBlockAndUpdate(blockpos, Blocks.SNOW.defaultBlockState());
                    CraftEventFactory.handleBlockFormEvent(this, blockpos, this.youer$defaultBlockState, null); // CraftBukkit
                }
            }

            Biome.Precipitation biome$precipitation = biome.getPrecipitationAt(blockpos1);
            if (biome$precipitation != Biome.Precipitation.NONE) {
                BlockState blockstate2 = this.getBlockState(blockpos1);
                blockstate2.getBlock().handlePrecipitation(blockstate2, this, blockpos1, biome$precipitation);
            }
        }
    }

    public Optional<BlockPos> findLightningRod(BlockPos p_143249_) {
        Optional<BlockPos> optional = this.getPoiManager()
            .findClosest(
                p_215059_ -> p_215059_.is(PoiTypes.LIGHTNING_ROD),
                p_184055_ -> p_184055_.getY() == this.getHeight(Heightmap.Types.WORLD_SURFACE, p_184055_.getX(), p_184055_.getZ()) - 1,
                p_143249_,
                128,
                PoiManager.Occupancy.ANY
            );
        return optional.map(p_184053_ -> p_184053_.above(1));
    }

    public AtomicBoolean findLightningTargetAround$returnNullWhenNoTarget = new AtomicBoolean(false);

    public BlockPos findLightningTargetAround(BlockPos p_143289_) {
        // Paper end - Add methods to find targets for lightning strikes
        BlockPos blockpos = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, p_143289_);
        Optional<BlockPos> optional = this.findLightningRod(blockpos);
        if (optional.isPresent()) {
            return optional.get();
        } else {
            AABB aabb = AABB.encapsulatingFullBlocks(blockpos, new BlockPos(blockpos.atY(this.getMaxBuildHeight()))).inflate(3.0);
            List<LivingEntity> list = this.getEntitiesOfClass(
                    LivingEntity.class, aabb, p_352698_ -> p_352698_ != null && p_352698_.isAlive() && this.canSeeSky(p_352698_.blockPosition()) && !p_352698_.isSpectator() // Paper - Fix lightning being able to hit spectators (MC-262422)
            );
            if (!list.isEmpty()) {
                return list.get(this.random.nextInt(list.size())).blockPosition();
            } else {
                if (findLightningTargetAround$returnNullWhenNoTarget.getAndSet(false)) return null; // Paper - Add methods to find targets for lightning strikes
                if (blockpos.getY() == this.getMinBuildHeight() - 1) {
                    blockpos = blockpos.above(2);
                }

                return blockpos;
            }
        }
    }
    public BlockPos findLightningTargetAround(BlockPos p_143289_, boolean returnNullWhenNoTarget) {
        findLightningTargetAround$returnNullWhenNoTarget.set(returnNullWhenNoTarget);
        return findLightningTargetAround(p_143289_);
    }

    public boolean isHandlingTick() {
        return this.getCurrentWorldData().isHandlingTick(); // Folia - regionised ticking
    }

    public boolean canSleepThroughNights() {
        return this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE) <= 100;
    }

    private void announceSleepStatus() {
        if (this.canSleepThroughNights()) {
            if (!this.getServer().isSingleplayer() || this.getServer().isPublished()) {
                int i = this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
                Component component;
                if (this.sleepStatus.areEnoughSleeping(i)) {
                    component = Component.translatable("sleep.skipping_night");
                } else {
                    component = Component.translatable("sleep.players_sleeping", this.sleepStatus.amountSleeping(), this.sleepStatus.sleepersNeeded(i));
                }

                for (ServerPlayer serverplayer : this.players) {
                    serverplayer.displayClientMessage(component, true);
                }
            }
        }
    }

    public void updateSleepingPlayerList() {
        if (!this.players.isEmpty() && this.sleepStatus.update(this.players)) {
            this.announceSleepStatus();
        }
    }

    public ServerScoreboard getScoreboard() {
        return this.server.getScoreboard();
    }

    public void advanceWeatherCycle() { // Folia - region threading - public
        boolean flag = this.isRaining();
        if (this.dimensionType().hasSkyLight()) {
            if (this.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) {
                int i = this.K.getClearWeatherTime();
                int j = this.K.getThunderTime();
                int k = this.K.getRainTime();
                boolean flag1 = this.levelData.isThundering();
                boolean flag2 = this.levelData.isRaining();
                if (i > 0) {
                    i--;
                    j = flag1 ? 0 : 1;
                    k = flag2 ? 0 : 1;
                    flag1 = false;
                    flag2 = false;
                } else {
                    if (j > 0) {
                        if (--j == 0) {
                            flag1 = !flag1;
                        }
                    } else if (flag1) {
                        j = THUNDER_DURATION.sample(this.random);
                    } else {
                        j = THUNDER_DELAY.sample(this.random);
                    }

                    if (k > 0) {
                        if (--k == 0) {
                            flag2 = !flag2;
                        }
                    } else if (flag2) {
                        k = RAIN_DURATION.sample(this.random);
                    } else {
                        k = RAIN_DELAY.sample(this.random);
                    }
                }

                if (Boolean.FALSE) {
                    this.serverLevelData.setThunderTime(j);
                    this.serverLevelData.setRainTime(k);
                    this.serverLevelData.setClearWeatherTime(i);
                    this.serverLevelData.setThundering(flag1);
                    this.serverLevelData.setRaining(flag2);
                }

                this.K.setThunderTime(j);
                this.K.setRainTime(k);
                this.K.setClearWeatherTime(i);
                this.K.setThundering(flag1);
                this.K.setRaining(flag2);
            }

            this.oThunderLevel = this.thunderLevel;
            if (this.levelData.isThundering()) {
                this.thunderLevel += 0.01F;
            } else {
                this.thunderLevel -= 0.01F;
            }

            this.thunderLevel = Mth.clamp(this.thunderLevel, 0.0F, 1.0F);
            this.oRainLevel = this.rainLevel;
            if (this.levelData.isRaining()) {
                this.rainLevel += 0.01F;
            } else {
                this.rainLevel -= 0.01F;
            }

            this.rainLevel = Mth.clamp(this.rainLevel, 0.0F, 1.0F);
        }

        if (this.oRainLevel != this.rainLevel) {
            this.server
                .getPlayerList()
                .broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, this.rainLevel), this.dimension());
        }

        if (this.oThunderLevel != this.thunderLevel) {
            this.server
                .getPlayerList()
                .broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, this.thunderLevel), this.dimension());
        }

        /* The function in use here has been replaced in order to only send the weather info to players in the correct dimension,
         * rather than to all players on the server. This is what causes the client-side rain, as the
         * client believes that it has started raining locally, rather than in another dimension.
         */
        if (flag != this.isRaining()) {
            if (flag) {
                this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0F), this.dimension());
            } else {
                this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F), this.dimension());
            }

            this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, this.rainLevel), this.dimension());
            this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, this.thunderLevel), this.dimension());
        }
    }

    @VisibleForTesting
    public void resetWeatherCycle() {
        this.K.setRainTime(0);
        this.K.setRaining(false);
        this.K.setThunderTime(0);
        this.K.setThundering(false);
    }

    public void resetEmptyTime() {
        this.emptyTime = 0;
    }

    private void tickFluid(BlockPos p_184077_, Fluid p_184078_) {
        FluidState fluidstate = this.getFluidState(p_184077_);
        if (fluidstate.is(p_184078_)) {
            fluidstate.tick(this, p_184077_);
        }
    }

    private void tickBlock(BlockPos p_184113_, Block p_184114_) {
        BlockState blockstate = this.getBlockState(p_184113_);
        if (blockstate.is(p_184114_)) {
            blockstate.tick(this, p_184113_, this.random);
        }
    }

    public void tickNonPassenger(Entity p_8648_) {
        p_8648_.setOldPosAndRot();
        ProfilerFiller profilerfiller = this.getProfiler();
        p_8648_.tickCount++;
        this.getProfiler().push(() -> BuiltInRegistries.ENTITY_TYPE.getKey(p_8648_.getType()).toString());
        profilerfiller.incrementCounter("tickNonPassenger");
        // Neo: Permit cancellation of Entity#tick via EntityTickEvent.Pre
        if (!net.neoforged.neoforge.event.EventHooks.fireEntityTickPre(p_8648_).isCanceled()) {
            p_8648_.tick();
            p_8648_.postTick(); // CraftBukkit
            net.neoforged.neoforge.event.EventHooks.fireEntityTickPost(p_8648_);
        }
        this.getProfiler().pop();

        for (Entity entity : p_8648_.getPassengers()) {
            this.tickPassenger(p_8648_, entity);
        }
    }

    private void tickPassenger(Entity p_8663_, Entity p_8664_) {
        if (p_8664_.isRemoved() || p_8664_.getVehicle() != p_8663_) {
            p_8664_.stopRiding();
        } else if (p_8664_ instanceof Player || this.entityTickList.contains(p_8664_)) {
            p_8664_.setOldPosAndRot();
            p_8664_.tickCount++;
            ProfilerFiller profilerfiller = this.getProfiler();
            profilerfiller.push(() -> BuiltInRegistries.ENTITY_TYPE.getKey(p_8664_.getType()).toString());
            profilerfiller.incrementCounter("tickPassenger");
            p_8664_.rideTick();
            p_8664_.postTick(); // CraftBukkit
            profilerfiller.pop();
            for (Entity entity : p_8664_.getPassengers()) {
                this.tickPassenger(p_8664_, entity);
            }
        }
    }

    @Override
    public boolean mayInteract(Player p_8696_, BlockPos p_8697_) {
        if (PlayerMayInteractBlockEvent.getHandlerList().getRegisteredListeners().length > 0) {
            Location location = CraftLocation.toBukkit(p_8697_, world);
            PlayerMayInteractBlockEvent event = new PlayerMayInteractBlockEvent((org.bukkit.entity.Player) p_8696_.getBukkitEntity(), location);
            if (!event.callEvent()) {
                return false;
            }
        }
        return !this.server.isUnderSpawnProtection(this, p_8697_, p_8696_) && this.getWorldBorder().isWithinBounds(p_8697_);
    }

    public void save(@Nullable ProgressListener p_8644_, boolean p_8645_, boolean p_8646_) {
        ServerChunkCache serverchunkcache = this.getChunkSource();
        if (!p_8646_) {
            Bukkit.getPluginManager().callEvent(new WorldSaveEvent(getWorld())); // CraftBukkit
            if (p_8644_ != null) {
                p_8644_.progressStartNoAbort(Component.translatable("menu.savingLevel"));
            }

            this.saveLevelData();
            if (p_8644_ != null) {
                p_8644_.progressStage(Component.translatable("menu.savingChunks"));
            }

            serverchunkcache.save(p_8645_);
            if (p_8645_) {
                this.entityManager.saveAll();
            } else {
                this.entityManager.autoSave();
            }

            // CraftBukkit start
            ServerLevel worldserver1 = this;
            if (worldserver1 != this.server.overworld()) {
                this.K.setWorldBorder(worldserver1.getWorldBorder().createSettings());
                this.K.setCustomBossEvents(this.server.getCustomBossEvents().save(this.registryAccess()));
                this.convertable.saveDataTag(this.server.registryAccess(), this.K, this.server.getPlayerList().getSingleplayerData());
            }
            // CraftBukkit end

            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.LevelEvent.Save(this));
            if (p_8645_) {
                net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
            }
        }
    }

    private void saveLevelData() {
        this.saveLevelData(false);
    }

    public void saveLevelData(boolean async) { // Folia - region threading - public, async param
        if (this.dragonFight != null) {
            if (K != null) {
                this.K.setEndDragonFightData(this.dragonFight.saveData()); // CraftBukkit
            } else {
                this.server.getWorldData().setEndDragonFightData(this.dragonFight.saveData());
            }
        }

        // Folia start - moved into saveLevelData
        ServerLevel worldserver1 = this;
        this.serverLevelData.setWorldBorder(worldserver1.getWorldBorder().createSettings());
        // FoliaYouer start - DerivedLevelData (nether/end/mod dims) cannot be cast to PrimaryLevelData or WorldData
        if (this.serverLevelData instanceof net.minecraft.world.level.storage.PrimaryLevelData primaryLevelData) {
            primaryLevelData.setCustomBossEvents(this.server.getCustomBossEvents().save(this.registryAccess()));
            this.convertable.saveDataTag(this.server.registryAccess(), primaryLevelData, this.server.getPlayerList().getSingleplayerData());
        }
        // DerivedLevelData dimensions: level data is derived from overworld, no need to save separately
        // FoliaYouer end

        this.getChunkSource().getDataStorage().save(async);
    }

    public <T extends Entity> List<? extends T> getEntities(EntityTypeTest<Entity, T> p_143281_, Predicate<? super T> p_143282_) {
        List<T> list = Lists.newArrayList();
        this.getEntities(p_143281_, p_143282_, list);
        return list;
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> p_262152_, Predicate<? super T> p_261808_, List<? super T> p_261583_) {
        this.getEntities(p_262152_, p_261808_, p_261583_, Integer.MAX_VALUE);
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> p_261842_, Predicate<? super T> p_262091_, List<? super T> p_261703_, int p_261907_) {
        this.getEntities().get(p_261842_, p_261428_ -> {
            if (p_262091_.test(p_261428_)) {
                p_261703_.add(p_261428_);
                if (p_261703_.size() >= p_261907_) {
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
            }

            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
    }

    public List<? extends EnderDragon> getDragons() {
        return this.getEntities(EntityType.ENDER_DRAGON, LivingEntity::isAlive);
    }

    public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> p_8796_) {
        return this.getPlayers(p_8796_, Integer.MAX_VALUE);
    }

    public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> p_261698_, int p_262035_) {
        List<ServerPlayer> list = Lists.newArrayList();

        for (ServerPlayer serverplayer : this.players) {
            if (p_261698_.test(serverplayer)) {
                list.add(serverplayer);
                if (list.size() >= p_262035_) {
                    return list;
                }
            }
        }

        return list;
    }

    @Nullable
    public ServerPlayer getRandomPlayer() {
        List<ServerPlayer> list = this.getPlayers(LivingEntity::isAlive);
        return list.isEmpty() ? null : list.get(this.random.nextInt(list.size()));
    }

    // Mohist start
    public AtomicBoolean canaddFreshEntity = new AtomicBoolean(false);

    public boolean canAddFreshEntity() {
        return canaddFreshEntity.getAndSet(false);
    }
    // Mohist end

    @Override
    public boolean addFreshEntity(Entity p_8837_) {
        boolean add = this.addEntity(p_8837_);
        canaddFreshEntity.set(add);
        return add;
    }

    public boolean addFreshEntity(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        return this.addEntityByReason(entity, reason);
    }

    public boolean addWithUUID(Entity p_8848_) {
        return this.addEntity(p_8848_);
    }

    public boolean addWithUUID(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        return this.addEntityByReason(entity, reason);
    }

    public void addDuringTeleport(Entity p_143335_) {
        // SPIGOT-6415: Don't call spawn event for entities which travel trough worlds,
        // since it is only an implementation detail, that a new entity is created when
        // they are traveling between worlds.
        if (p_143335_ instanceof ServerPlayer serverplayer) {
            this.addPlayer(serverplayer);
        } else {
            p_143335_.spawnReason(null);
            this.addEntity(p_143335_);
        }
    }

    public void addDuringTeleport(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        if (entity instanceof ServerPlayer serverplayer) {
            this.addPlayer(serverplayer);
        } else {
            entity.spawnReason(reason);
            this.addEntity(entity);
        }
    }
    // CraftBukkit end

    public void addNewPlayer(ServerPlayer p_8835_) {
        this.addPlayer(p_8835_);
    }

    public void addRespawnedPlayer(ServerPlayer p_8846_) {
        this.addPlayer(p_8846_);
    }

    private void addPlayer(ServerPlayer p_8854_) {
        if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(p_8854_, this)).isCanceled()) return;
        Entity entity = this.getEntities().get(p_8854_.getUUID());
        if (entity != null) {
            LOGGER.warn("Force-added player with duplicate UUID {}", p_8854_.getUUID());
            entity.unRide();
            this.removePlayerImmediately((ServerPlayer)entity, Entity.RemovalReason.DISCARDED);
        }

        this.entityManager.addNewEntityWithoutEvent(p_8854_);
        p_8854_.onAddedToLevel();
    }

    private boolean addEntity(Entity p_8873_) {
        p_8873_.generation = false; // Paper - Don't fire sync event during generation; Reset flag if it was added during a ServerLevel generation process
        // Paper start - extra debug info
        if (p_8873_.valid) {
            return true;
        }
        // Paper end - extra debug info
        if (p_8873_.isRemoved()) {
            if (Boolean.parseBoolean("false")) LOGGER.warn("Tried to add entity {} but it was marked as removed already", EntityType.getKey(p_8873_.getType()));
            return false;
        } else {
            if (p_8873_ instanceof Villager && YouerConfig.custom_no_villager) return false;
            if (!YouerConfig.spawnForChunk && p_8873_.spawnReason.equals(CreatureSpawnEvent.SpawnReason.CHUNK_GEN)) {
                return false;
            }

            if (!YouerConfig.spawnForNatural && p_8873_.spawnReason.equals(CreatureSpawnEvent.SpawnReason.NATURAL)) {
                return false;
            }
            if (BanEntity.check(p_8873_)) {
                p_8873_.discard();
                return false;
            }
            if (p_8873_ instanceof net.minecraft.world.entity.item.ItemEntity itemEntity && itemEntity.getItem().isEmpty()) return false; // Paper - Prevent empty items from being added
            // Paper start - capture all item additions to the world
            if (captureDrops != null && p_8873_ instanceof net.minecraft.world.entity.item.ItemEntity) {
                captureDrops.add((net.minecraft.world.entity.item.ItemEntity) p_8873_);
                return true;
            }
            // Paper end - capture all item additions to the world
            if (!AsyncCatcher.catchAsync() && p_8873_.spawnReason != null && !CraftEventFactory.doEntityAddEventCalling(this, p_8873_, p_8873_.spawnReason)) {
                return false;
            }
            if (this.entityManager.addNewEntity(p_8873_)) {
                p_8873_.onAddedToLevel();
                return true;
            } else {
                return false;
            }
        }
    }

    // Mohist start
    public boolean addEntityByReason(Entity entity, CreatureSpawnEvent.SpawnReason spawnReason) {
        entity.spawnReason(spawnReason);
        return addEntity(entity);
    }
    // Mohist end

    public boolean tryAddFreshEntityWithPassengers(Entity pEntity, CreatureSpawnEvent.SpawnReason reason) {
        if (pEntity.getSelfAndPassengers().map(Entity::getUUID).anyMatch(this.entityManager::isLoaded)) {
            return false;
        } else {
            this.addFreshEntityWithPassengers(pEntity, reason);
            return true;
        }
    }

    public boolean tryAddFreshEntityWithPassengers(Entity p_8861_) {
        if (p_8861_.getSelfAndPassengers().map(Entity::getUUID).anyMatch(this.entityManager::isLoaded)) {
            return false;
        } else {
            this.addFreshEntityWithPassengers(p_8861_);
            return true;
        }
    }

    public void unload(LevelChunk p_8713_) {
        // Spigot Start
        for (BlockEntity tileentity : p_8713_.getBlockEntities().values()) {
            if (tileentity instanceof Container container) {
                // Paper start - this area looks like it can load chunks, change the behavior
                // chests for example can apply physics to the world
                // so instead we just change the active container and call the event
                for (org.bukkit.entity.HumanEntity h : Lists.newArrayList(container.getViewers())) {
                    if (h != null) {
                        ((org.bukkit.craftbukkit.entity.CraftHumanEntity) h).getHandle().closeUnloadedInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason.UNLOADED); // Paper - Inventory close reason
                    }
                }
                // Paper end - this area looks like it can load chunks, change the behavior
            }
        }
        // Spigot End
        p_8713_.clearAllBlockEntities();
        p_8713_.unregisterTickContainerFromLevel(this);
    }

    public void removePlayerImmediately(ServerPlayer p_143262_, Entity.RemovalReason p_143263_) {
        p_143262_.remove(p_143263_);
    }

    // CraftBukkit start
    public boolean strikeLightning(Entity entitylightning) {
        return this.strikeLightning(entitylightning, LightningStrikeEvent.Cause.UNKNOWN);
    }

    public boolean strikeLightning(Entity entitylightning, LightningStrikeEvent.Cause cause) {
        LightningStrikeEvent lightning = CraftEventFactory.callLightningStrikeEvent((org.bukkit.entity.LightningStrike) entitylightning.getBukkitEntity(), cause);

        if (lightning.isCancelled()) {
            return false;
        }
        return this.addFreshEntity(entitylightning);
    }
    // CraftBukkit end

    @Override
    public void destroyBlockProgress(int p_8612_, BlockPos p_8613_, int p_8614_) {

        // CraftBukkit start
        Player entityhuman = null;
        Entity entity = this.getEntity(p_8612_);
        if (entity instanceof Player) entityhuman = (Player) entity;
        // CraftBukkit end

        // Paper start - Add BlockBreakProgressUpdateEvent
        // If a plugin is using this method to send destroy packets for a client-side only entity id, no block progress occurred on the server.
        // Hence, do not call the event.
        if (!AsyncCatcher.catchAsync() && entity != null) {
            float progressFloat = Mth.clamp(p_8614_, 0, 10) / 10.0f;
            org.bukkit.craftbukkit.block.CraftBlock bukkitBlock = org.bukkit.craftbukkit.block.CraftBlock.at(this, p_8613_);
            new io.papermc.paper.event.block.BlockBreakProgressUpdateEvent(bukkitBlock, progressFloat, entity.getBukkitEntity())
                    .callEvent();
        }
        // Paper end - Add BlockBreakProgressUpdateEvent

        for (ServerPlayer serverplayer : this.server.getPlayerList().getPlayers()) {
            if (serverplayer != null && serverplayer.level() == this && serverplayer.getId() != p_8612_) {
                double d0 = (double)p_8613_.getX() - serverplayer.getX();
                double d1 = (double)p_8613_.getY() - serverplayer.getY();
                double d2 = (double)p_8613_.getZ() - serverplayer.getZ();

                // CraftBukkit start
                if (entityhuman != null && !(entityhuman instanceof FakePlayer) && !serverplayer.getBukkitEntity().canSee(entityhuman.getBukkitEntity())) {
                    continue;
                }
                // CraftBukkit end

                if (d0 * d0 + d1 * d1 + d2 * d2 < 1024.0) {
                    serverplayer.connection.send(new ClientboundBlockDestructionPacket(p_8612_, p_8613_, p_8614_));
                }
            }
        }
    }

    @Override
    public void playSeededSound(
        @Nullable Player p_263330_,
        double p_263393_,
        double p_263369_,
        double p_263354_,
        Holder<SoundEvent> p_263412_,
        SoundSource p_263338_,
        float p_263352_,
        float p_263390_,
        long p_263403_
    ) {
        if (!AsyncCatcher.catchAsync()) {
            net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition event = net.neoforged.neoforge.event.EventHooks.onPlaySoundAtPosition(this, p_263393_, p_263369_, p_263354_, p_263412_, p_263338_, p_263352_, p_263390_);
            if (event.isCanceled() || event.getSound() == null) return;
            p_263412_ = event.getSound();
            p_263338_ = event.getSource();
            p_263352_ = event.getNewVolume();
            p_263390_ = event.getNewPitch();
        }
        this.server
            .getPlayerList()
            .broadcast(
                p_263330_,
                p_263393_,
                p_263369_,
                p_263354_,
                (double)p_263412_.value().getRange(p_263352_),
                this.dimension(),
                new ClientboundSoundPacket(p_263412_, p_263338_, p_263393_, p_263369_, p_263354_, p_263352_, p_263390_, p_263403_)
            );
    }

    @Override
    public void playSeededSound(
        @Nullable Player p_263545_, Entity p_263544_, Holder<SoundEvent> p_263491_, SoundSource p_263542_, float p_263530_, float p_263520_, long p_263490_
    ) {
        net.neoforged.neoforge.event.PlayLevelSoundEvent.AtEntity event = net.neoforged.neoforge.event.EventHooks.onPlaySoundAtEntity(p_263544_, p_263491_, p_263542_, p_263530_, p_263520_);
        if (event.isCanceled() || event.getSound() == null) return;
        p_263491_ = event.getSound();
        p_263542_ = event.getSource();
        p_263530_ = event.getNewVolume();
        p_263520_ = event.getNewPitch();
        this.server
            .getPlayerList()
            .broadcast(
                p_263545_,
                p_263544_.getX(),
                p_263544_.getY(),
                p_263544_.getZ(),
                (double)p_263491_.value().getRange(p_263530_),
                this.dimension(),
                new ClientboundSoundEntityPacket(p_263491_, p_263542_, p_263544_, p_263530_, p_263520_, p_263490_)
            );
    }

    @Override
    public void globalLevelEvent(int p_8811_, BlockPos p_8812_, int p_8813_) {
        if (this.getGameRules().getBoolean(GameRules.RULE_GLOBAL_SOUND_EVENTS)) {
            this.server.getPlayerList().broadcastAll(new ClientboundLevelEventPacket(p_8811_, p_8812_, p_8813_, true));
        } else {
            this.levelEvent(null, p_8811_, p_8812_, p_8813_);
        }
    }

    @Override
    public void levelEvent(@Nullable Player p_8684_, int p_8685_, BlockPos p_8686_, int p_8687_) {
        this.server
            .getPlayerList()
            .broadcast(
                p_8684_,
                (double)p_8686_.getX(),
                (double)p_8686_.getY(),
                (double)p_8686_.getZ(),
                64.0,
                this.dimension(),
                new ClientboundLevelEventPacket(p_8685_, p_8686_, p_8687_, false)
            );
    }

    public int getLogicalHeight() {
        return this.dimensionType().logicalHeight();
    }

    @Override
    public void gameEvent(Holder<GameEvent> p_316597_, Vec3 p_215042_, GameEvent.Context p_215043_) {
        if (!net.neoforged.neoforge.common.CommonHooks.onVanillaGameEvent(this, p_316597_, p_215042_, p_215043_)) return;
        this.gameEventDispatcher.post(p_316597_, p_215042_, p_215043_);
    }

    @Override
    public void sendBlockUpdated(BlockPos p_8755_, BlockState p_8756_, BlockState p_8757_, int p_8758_) {
        if (this.isUpdatingNavigations) {
            String s = "recursive call to sendBlockUpdated";
            Util.logAndPauseIfInIde("recursive call to sendBlockUpdated", new IllegalStateException("recursive call to sendBlockUpdated"));
        }

        this.getChunkSource().blockChanged(p_8755_);
        this.pathTypesByPosCache.invalidate(p_8755_);
        if (this.paperConfig().misc.updatePathfindingOnBlockUpdate) { // Paper - option to disable pathfinding updates
        VoxelShape voxelshape1 = p_8756_.getCollisionShape(this, p_8755_);
        VoxelShape voxelshape = p_8757_.getCollisionShape(this, p_8755_);
        if (Shapes.joinIsNotEmpty(voxelshape1, voxelshape, BooleanOp.NOT_SAME)) {
            List<PathNavigation> list = new ObjectArrayList<>();

            for (Mob mob : this.navigatingMobs) {
                PathNavigation pathnavigation = mob.getNavigation();
                if (pathnavigation.shouldRecomputePath(p_8755_)) {
                    list.add(pathnavigation);
                }
            }

            try {
                this.isUpdatingNavigations = true;

                for (PathNavigation pathnavigation1 : list) {
                    pathnavigation1.recomputePath();
                }
            } finally {
                this.isUpdatingNavigations = false;
            }
        }
        } // Paper - option to disable pathfinding updates
    }

    @Override
    public void updateNeighborsAt(BlockPos p_215045_, Block p_215046_) {
        net.neoforged.neoforge.event.EventHooks.onNeighborNotify(this, p_215045_, this.getBlockState(p_215045_), java.util.EnumSet.allOf(Direction.class), false).isCanceled();
        this.neighborUpdater.updateNeighborsAtExceptFromFacing(p_215045_, p_215046_, null);
    }

    @Override
    public void updateNeighborsAtExceptFromFacing(BlockPos p_215052_, Block p_215053_, Direction p_215054_) {
        java.util.EnumSet<Direction> directions = java.util.EnumSet.allOf(Direction.class);
        directions.remove(p_215054_);
        if (net.neoforged.neoforge.event.EventHooks.onNeighborNotify(this, p_215052_, this.getBlockState(p_215052_), directions, false).isCanceled())
            return;
        this.neighborUpdater.updateNeighborsAtExceptFromFacing(p_215052_, p_215053_, p_215054_);
    }

    @Override
    public void neighborChanged(BlockPos p_215048_, Block p_215049_, BlockPos p_215050_) {
        this.neighborUpdater.neighborChanged(p_215048_, p_215049_, p_215050_);
    }

    @Override
    public void neighborChanged(BlockState p_215035_, BlockPos p_215036_, Block p_215037_, BlockPos p_215038_, boolean p_215039_) {
        this.neighborUpdater.neighborChanged(p_215035_, p_215036_, p_215037_, p_215038_, p_215039_);
    }

    @Override
    public void broadcastEntityEvent(Entity p_8650_, byte p_8651_) {
        this.getChunkSource().broadcastAndSend(p_8650_, new ClientboundEntityEventPacket(p_8650_, p_8651_));
    }

    @Override
    public void broadcastDamageEvent(Entity p_270420_, DamageSource p_270311_) {
        this.getChunkSource().broadcastAndSend(p_270420_, new ClientboundDamageEventPacket(p_270420_, p_270311_));
    }

    public ServerChunkCache getChunkSource() {
        return this.chunkSource;
    }

    @Override
    public Explosion explode(
        @Nullable Entity p_256039_,
        @Nullable DamageSource p_255778_,
        @Nullable ExplosionDamageCalculator p_256002_,
        double p_256067_,
        double p_256370_,
        double p_256153_,
        float p_256045_,
        boolean p_255686_,
        Level.ExplosionInteraction p_255827_,
        ParticleOptions p_312436_,
        ParticleOptions p_312391_,
        Holder<SoundEvent> p_320497_
    ) {
        Explosion explosion = this.explode(
            p_256039_, p_255778_, p_256002_, p_256067_, p_256370_, p_256153_, p_256045_, p_255686_, p_255827_, false, p_312436_, p_312391_, p_320497_
        );
        // CraftBukkit start
        if (explosion.wasCanceled) {
            return explosion;
        }
        // CraftBukkit end
        if (!explosion.interactsWithBlocks()) {
            explosion.clearToBlow();
        }

        for (ServerPlayer serverplayer : this.players) {
            if (serverplayer.distanceToSqr(p_256067_, p_256370_, p_256153_) < 4096.0) {
                serverplayer.connection
                    .send(
                        new ClientboundExplodePacket(
                            p_256067_,
                            p_256370_,
                            p_256153_,
                            p_256045_,
                            explosion.getToBlow(),
                            explosion.getHitPlayers().get(serverplayer),
                            explosion.getBlockInteraction(),
                            explosion.getSmallExplosionParticles(),
                            explosion.getLargeExplosionParticles(),
                            explosion.getExplosionSound()
                        )
                    );
            }
        }

        return explosion;
    }

    @Override
    public void blockEvent(BlockPos p_8746_, Block p_8747_, int p_8748_, int p_8749_) {
        this.blockEvents.add(new BlockEventData(p_8746_, p_8747_, p_8748_, p_8749_));
    }

    private void runBlockEvents() {
        this.blockEventsToReschedule.clear();

        while (!this.blockEvents.isEmpty()) {
            BlockEventData blockeventdata = this.blockEvents.removeFirst();
            if (this.shouldTickBlocksAt(blockeventdata.pos())) {
                if (this.doBlockEvent(blockeventdata)) {
                    this.server
                        .getPlayerList()
                        .broadcast(
                            null,
                            (double)blockeventdata.pos().getX(),
                            (double)blockeventdata.pos().getY(),
                            (double)blockeventdata.pos().getZ(),
                            64.0,
                            this.dimension(),
                            new ClientboundBlockEventPacket(blockeventdata.pos(), blockeventdata.block(), blockeventdata.paramA(), blockeventdata.paramB())
                        );
                }
            } else {
                this.blockEventsToReschedule.add(blockeventdata);
            }
        }

        this.blockEvents.addAll(this.blockEventsToReschedule);
    }

    private boolean doBlockEvent(BlockEventData p_8699_) {
        BlockState blockstate = this.getBlockState(p_8699_.pos());
        return blockstate.is(p_8699_.block()) ? blockstate.triggerEvent(this, p_8699_.pos(), p_8699_.paramA(), p_8699_.paramB()) : false;
    }

    public LevelTicks<Block> getBlockTicks() {
        return this.blockTicks;
    }

    public LevelTicks<Fluid> getFluidTicks() {
        return this.fluidTicks;
    }

    @Nonnull
    @Override
    public MinecraftServer getServer() {
        return this.server;
    }

    public PortalForcer getPortalForcer() {
        return this.portalForcer;
    }

    public StructureTemplateManager getStructureManager() {
        return this.server.getStructureManager();
    }

    public <T extends ParticleOptions> int sendParticles(
        T p_8768_, double p_8769_, double p_8770_, double p_8771_, int p_8772_, double p_8773_, double p_8774_, double p_8775_, double p_8776_
    ) {
        // CraftBukkit - visibility api support
        return sendParticles(null, p_8768_, p_8769_, p_8770_, p_8771_, p_8772_, p_8773_, p_8774_, p_8775_, p_8776_, false);
    }

    public <T extends ParticleOptions> int sendParticles(ServerPlayer sender, T p_8768_, double p_8769_, double p_8770_, double p_8771_, int p_8772_, double p_8773_, double p_8774_, double p_8775_, double p_8776_, boolean force) {
        // Paper start - Particle API
        return sendParticles(players, sender, p_8768_, p_8769_, p_8770_, p_8771_, p_8772_, p_8773_, p_8774_, p_8775_, p_8776_, force);
    }
    public <T extends ParticleOptions> int sendParticles(List<ServerPlayer> receivers, @Nullable ServerPlayer sender, T p_8768_, double p_8769_, double p_8770_, double p_8771_, int p_8772_, double p_8773_, double p_8774_, double p_8775_, double p_8776_, boolean force) {
        // Paper end - Particle API
        ClientboundLevelParticlesPacket clientboundlevelparticlespacket = new ClientboundLevelParticlesPacket( p_8768_, force, p_8769_, p_8770_, p_8771_, (float)p_8773_, (float)p_8774_, (float)p_8775_, (float)p_8776_, p_8772_);
        // CraftBukkit end
        int i = 0;

        for (Player entityhuman : receivers) { // Paper - Particle API
            ServerPlayer serverplayer = (ServerPlayer) entityhuman; // Paper - Particle API
            if (sender != null && !(sender instanceof FakePlayer) && !serverplayer.getBukkitEntity().canSee(sender.getBukkitEntity())) continue; // CraftBukkit
            if (this.sendParticles(serverplayer, force, p_8769_, p_8770_, p_8771_, clientboundlevelparticlespacket)) { // CraftBukkit
                i++;
            }
        }

        return i;
    }

    public <T extends ParticleOptions> boolean sendParticles(
        ServerPlayer p_8625_,
        T p_8626_,
        boolean p_8627_,
        double p_8628_,
        double p_8629_,
        double p_8630_,
        int p_8631_,
        double p_8632_,
        double p_8633_,
        double p_8634_,
        double p_8635_
    ) {
        Packet<?> packet = new ClientboundLevelParticlesPacket(
            p_8626_, p_8627_, p_8628_, p_8629_, p_8630_, (float)p_8632_, (float)p_8633_, (float)p_8634_, (float)p_8635_, p_8631_
        );
        return this.sendParticles(p_8625_, p_8627_, p_8628_, p_8629_, p_8630_, packet);
    }

    private boolean sendParticles(ServerPlayer p_8637_, boolean p_8638_, double p_8639_, double p_8640_, double p_8641_, Packet<?> p_8642_) {
        if (p_8637_.level() != this) {
            return false;
        } else {
            BlockPos blockpos = p_8637_.blockPosition();
            if (blockpos.closerToCenterThan(new Vec3(p_8639_, p_8640_, p_8641_), p_8638_ ? 512.0 : 32.0)) {
                p_8637_.connection.send(p_8642_);
                return true;
            } else {
                return false;
            }
        }
    }

    @Nullable
    @Override
    public Entity getEntity(int p_8597_) {
        return this.getEntities().get(p_8597_);
    }

    @Deprecated
    @Nullable
    public Entity getEntityOrPart(int p_143318_) {
        Entity entity = this.getEntities().get(p_143318_);
        return entity != null ? entity : this.dragonParts.get(p_143318_);
    }

    @Nullable
    public Entity getEntity(UUID p_8792_) {
        return this.getEntities().get(p_8792_);
    }

    @Nullable
    public BlockPos findNearestMapStructure(TagKey<Structure> p_215012_, BlockPos p_215013_, int p_215014_, boolean p_215015_) {
        if (!this.K.worldGenOptions().generateStructures()) {
            return null;
        } else {
            Optional<HolderSet.Named<Structure>> optional = this.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(p_215012_);
            if (optional.isEmpty()) {
                return null;
            } else {
                Pair<BlockPos, Holder<Structure>> pair = this.getChunkSource()
                    .getGenerator()
                    .findNearestMapStructure(this, optional.get(), p_215013_, p_215014_, p_215015_);
                return pair != null ? pair.getFirst() : null;
            }
        }
    }

    @Nullable
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(Predicate<Holder<Biome>> p_215070_, BlockPos p_215071_, int p_215072_, int p_215073_, int p_215074_) {
        return this.getChunkSource()
            .getGenerator()
            .getBiomeSource()
            .findClosestBiome3d(p_215071_, p_215072_, p_215073_, p_215074_, p_215070_, this.getChunkSource().randomState().sampler(), this);
    }

    @Override
    public RecipeManager getRecipeManager() {
        return this.server.getRecipeManager();
    }

    @Override
    public TickRateManager tickRateManager() {
        return this.server.tickRateManager();
    }

    @Override
    public boolean noSave() {
        return this.noSave;
    }

    public DimensionDataStorage getDataStorage() {
        return this.getChunkSource().getDataStorage();
    }

    @Nullable
    @Override
    public MapItemSavedData getMapData(MapId p_323746_) {
        // Paper start - Call missing map initialize event and set id
        final DimensionDataStorage storage = this.getServer().overworld().getDataStorage();

        final net.minecraft.world.level.saveddata.SavedData existing = storage.cache.get(p_323746_.key());
        if (existing == null && !storage.cache.containsKey(p_323746_.key())) {
            final MapItemSavedData worldmap = (MapItemSavedData) this.getServer().overworld().getDataStorage().get(MapItemSavedData.factory(), p_323746_.key());
            storage.cache.put(p_323746_.key(), worldmap);
            if (worldmap != null) {
                worldmap.id = p_323746_;
                new MapInitializeEvent(worldmap.mapView).callEvent();
                return worldmap;
            }
        } else if (existing instanceof MapItemSavedData mapItemSavedData) {
            mapItemSavedData.id = p_323746_;
        }

        return existing instanceof MapItemSavedData data ? data : null;
    }

    @Override
    public void setMapData(MapId p_323697_, MapItemSavedData p_143306_) {
        // CraftBukkit start
        p_143306_.id = p_323697_;
        org.bukkit.event.server.MapInitializeEvent event = new org.bukkit.event.server.MapInitializeEvent(p_143306_.mapView);
        org.bukkit.Bukkit.getServer().getPluginManager().callEvent(event);
        // CraftBukkit end
        this.getServer().overworld().getDataStorage().set(p_323697_.key(), p_143306_);
    }

    @Override
    public MapId getFreeMapId() {
        return this.getServer().overworld().getDataStorage().computeIfAbsent(MapIndex.factory(), "idcounts").getFreeAuxValueForMap();
    }

    public void setDefaultSpawnPos(BlockPos p_8734_, float p_8735_) {
        BlockPos blockpos = this.levelData.getSpawnPos();
        float f = this.levelData.getSpawnAngle();
        if (!blockpos.equals(p_8734_) || f != p_8735_) {
            org.bukkit.Location prevSpawnLoc = this.getWorld().getSpawnLocation(); // Paper - Call SpawnChangeEvent
            this.levelData.setSpawn(p_8734_, p_8735_);
            new org.bukkit.event.world.SpawnChangeEvent(this.getWorld(), prevSpawnLoc).callEvent(); // Paper - Call SpawnChangeEvent
            this.getServer().getPlayerList().broadcastAll(new ClientboundSetDefaultSpawnPositionPacket(p_8734_, p_8735_));
        }

        if (this.lastSpawnChunkRadius > 1) {
            this.getChunkSource().removeRegionTicket(TicketType.START, new ChunkPos(blockpos), this.lastSpawnChunkRadius, Unit.INSTANCE);
        }

        int i = this.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS) + 1;
        if (i > 1) {
            this.getChunkSource().addRegionTicket(TicketType.START, new ChunkPos(p_8734_), i, Unit.INSTANCE);
        }

        this.lastSpawnChunkRadius = i;
    }

    public LongSet getForcedChunks() {
        ForcedChunksSavedData forcedchunkssaveddata = this.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");
        return (LongSet)(forcedchunkssaveddata != null ? LongSets.unmodifiable(forcedchunkssaveddata.getChunks()) : LongSets.EMPTY_SET);
    }

    public boolean setChunkForced(int p_8603_, int p_8604_, boolean p_8605_) {
        ForcedChunksSavedData forcedchunkssaveddata = this.getDataStorage().computeIfAbsent(ForcedChunksSavedData.factory(), "chunks");
        ChunkPos chunkpos = new ChunkPos(p_8603_, p_8604_);
        long i = chunkpos.toLong();
        boolean flag;
        if (p_8605_) {
            flag = forcedchunkssaveddata.getChunks().add(i);
            if (flag) {
                this.getChunk(p_8603_, p_8604_);
            }
        } else {
            flag = forcedchunkssaveddata.getChunks().remove(i);
        }

        forcedchunkssaveddata.setDirty(flag);
        if (flag) {
            this.getChunkSource().updateChunkForced(chunkpos, p_8605_);
        }

        return flag;
    }

    @Override
    public List<ServerPlayer> players() {
        return this.players;
    }

    @Override
    public void onBlockStateChange(BlockPos p_8751_, BlockState p_8752_, BlockState p_8753_) {
        Optional<Holder<PoiType>> optional = PoiTypes.forState(p_8752_);
        Optional<Holder<PoiType>> optional1 = PoiTypes.forState(p_8753_);
        if (!Objects.equals(optional, optional1)) {
            BlockPos blockpos = p_8751_.immutable();
            optional.ifPresent(p_215081_ -> this.getServer().execute(() -> {
                    this.getPoiManager().remove(blockpos);
                    DebugPackets.sendPoiRemovedPacket(this, blockpos);
                }));
            optional1.ifPresent(p_215057_ -> this.getServer().execute(() -> {
                    this.getPoiManager().add(blockpos, (Holder<PoiType>)p_215057_);
                    DebugPackets.sendPoiAddedPacket(this, blockpos);
                }));
        }
    }

    public PoiManager getPoiManager() {
        return this.getChunkSource().getPoiManager();
    }

    public boolean isVillage(BlockPos p_8803_) {
        return this.isCloseToVillage(p_8803_, 1);
    }

    public boolean isVillage(SectionPos p_8763_) {
        return this.isVillage(p_8763_.center());
    }

    public boolean isCloseToVillage(BlockPos p_8737_, int p_8738_) {
        return p_8738_ > 6 ? false : this.sectionsToVillage(SectionPos.of(p_8737_)) <= p_8738_;
    }

    public int sectionsToVillage(SectionPos p_8829_) {
        return this.getPoiManager().sectionsToVillage(p_8829_);
    }

    public Raids getRaids() {
        return this.raids;
    }

    @Nullable
    public Raid getRaidAt(BlockPos p_8833_) {
        return this.raids.getNearbyRaid(p_8833_, 9216);
    }

    public boolean isRaided(BlockPos p_8844_) {
        return this.getRaidAt(p_8844_) != null;
    }

    public void onReputationEvent(ReputationEventType p_8671_, Entity p_8672_, ReputationEventHandler p_8673_) {
        p_8673_.onReputationEventFrom(p_8671_, p_8672_);
    }

    public void saveDebugReport(Path p_8787_) throws IOException {
        ChunkMap chunkmap = this.getChunkSource().chunkMap;

        try (Writer writer = Files.newBufferedWriter(p_8787_.resolve("stats.txt"))) {
            writer.write(String.format(Locale.ROOT, "spawning_chunks: %d\n", chunkmap.getDistanceManager().getNaturalSpawnChunkCount()));
            NaturalSpawner.SpawnState naturalspawner$spawnstate = this.getChunkSource().getLastSpawnState();
            if (naturalspawner$spawnstate != null) {
                for (Entry<MobCategory> entry : naturalspawner$spawnstate.getMobCategoryCounts().object2IntEntrySet()) {
                    writer.write(String.format(Locale.ROOT, "spawn_count.%s: %d\n", entry.getKey().getName(), entry.getIntValue()));
                }
            }

            writer.write(String.format(Locale.ROOT, "entities: %s\n", this.entityManager.gatherStats()));
            writer.write(String.format(Locale.ROOT, "block_entity_tickers: %d\n", this.blockEntityTickers.size()));
            writer.write(String.format(Locale.ROOT, "block_ticks: %d\n", this.getBlockTicks().count()));
            writer.write(String.format(Locale.ROOT, "fluid_ticks: %d\n", this.getFluidTicks().count()));
            writer.write("distance_manager: " + chunkmap.getDistanceManager().getDebugStatus() + "\n");
            writer.write(String.format(Locale.ROOT, "pending_tasks: %d\n", this.getChunkSource().getPendingTasksCount()));
        }

        CrashReport crashreport = new CrashReport("Level dump", new Exception("dummy"));
        this.fillReportDetails(crashreport);

        try (Writer writer3 = Files.newBufferedWriter(p_8787_.resolve("example_crash.txt"))) {
            writer3.write(crashreport.getFriendlyReport(ReportType.TEST));
        }

        Path path = p_8787_.resolve("chunks.csv");

        try (Writer writer4 = Files.newBufferedWriter(path)) {
            chunkmap.dumpChunks(writer4);
        }

        Path path1 = p_8787_.resolve("entity_chunks.csv");

        try (Writer writer5 = Files.newBufferedWriter(path1)) {
            this.entityManager.dumpSections(writer5);
        }

        Path path2 = p_8787_.resolve("entities.csv");

        try (Writer writer1 = Files.newBufferedWriter(path2)) {
            dumpEntities(writer1, this.getEntities().getAll());
        }

        Path path3 = p_8787_.resolve("block_entities.csv");

        try (Writer writer2 = Files.newBufferedWriter(path3)) {
            this.dumpBlockEntityTickers(writer2);
        }
    }

    private static void dumpEntities(Writer p_8782_, Iterable<Entity> p_8783_) throws IOException {
        CsvOutput csvoutput = CsvOutput.builder()
            .addColumn("x")
            .addColumn("y")
            .addColumn("z")
            .addColumn("uuid")
            .addColumn("type")
            .addColumn("alive")
            .addColumn("display_name")
            .addColumn("custom_name")
            .build(p_8782_);

        for (Entity entity : p_8783_) {
            Component component = entity.getCustomName();
            Component component1 = entity.getDisplayName();
            csvoutput.writeRow(
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                entity.getUUID(),
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                entity.isAlive(),
                component1.getString(),
                component != null ? component.getString() : null
            );
        }
    }

    private void dumpBlockEntityTickers(Writer p_143300_) throws IOException {
        CsvOutput csvoutput = CsvOutput.builder().addColumn("x").addColumn("y").addColumn("z").addColumn("type").build(p_143300_);

        for (TickingBlockEntity tickingblockentity : this.blockEntityTickers) {
            BlockPos blockpos = tickingblockentity.getPos();
            csvoutput.writeRow(blockpos.getX(), blockpos.getY(), blockpos.getZ(), tickingblockentity.getType());
        }
    }

    @VisibleForTesting
    public void clearBlockEvents(BoundingBox p_8723_) {
        this.blockEvents.removeIf(p_207568_ -> p_8723_.isInside(p_207568_.pos()));
    }

    @Override
    public void blockUpdated(BlockPos p_8743_, Block p_8744_) {
        if (!this.isDebug()) {
            // CraftBukkit start
            if (populating) {
                return;
            }
            // CraftBukkit end
            this.updateNeighborsAt(p_8743_, p_8744_);
        }
    }

    @Override
    public float getShade(Direction p_8760_, boolean p_8761_) {
        return 1.0F;
    }

    public Iterable<Entity> getAllEntities() {
        return this.getEntities().getAll();
    }

    @Override
    public String toString() {
        return "ServerLevel[" + this.K.getLevelName() + "]";
    }

    public boolean isFlat() {
        return this.K == null ?  this.server.getWorldData().isFlatWorld() : this.K.isFlatWorld(); // CraftBukkit // Mohist
    }

    @Override
    public long getSeed() {
        return this.K == null ?  this.server.getWorldData().worldGenOptions().seed() : this.K.worldGenOptions().seed(); // CraftBukkit // Mohist
    }

    @Nullable
    public EndDragonFight getDragonFight() {
        return this.dragonFight;
    }

    @Override
    public ServerLevel getLevel() {
        return this;
    }

    @VisibleForTesting
    public String getWatchdogStats() {
        return String.format(
            Locale.ROOT,
            "players: %s, entities: %s [%s], block_entities: %d [%s], block_ticks: %d, fluid_ticks: %d, chunk_source: %s",
            this.players.size(),
            this.entityManager.gatherStats(),
            getTypeCount(this.entityManager.getEntityGetter().getAll(), p_258244_ -> BuiltInRegistries.ENTITY_TYPE.getKey(p_258244_.getType()).toString()),
            this.blockEntityTickers.size(),
            getTypeCount(this.blockEntityTickers, TickingBlockEntity::getType),
            this.getBlockTicks().count(),
            this.getFluidTicks().count(),
            this.gatherChunkSourceStats()
        );
    }

    private static <T> String getTypeCount(Iterable<T> p_143302_, Function<T, String> p_143303_) {
        try {
            Object2IntOpenHashMap<String> object2intopenhashmap = new Object2IntOpenHashMap<>();

            for (T t : p_143302_) {
                String s = p_143303_.apply(t);
                object2intopenhashmap.addTo(s, 1);
            }

            return object2intopenhashmap.object2IntEntrySet()
                .stream()
                .sorted(Comparator.<Entry<String>, Integer>comparing(Entry::getIntValue).reversed())
                .limit(5L)
                .map(p_207570_ -> p_207570_.getKey() + ":" + p_207570_.getIntValue())
                .collect(Collectors.joining(","));
        } catch (Exception exception) {
            return "";
        }
    }

    @Override
    public LevelEntityGetter<Entity> getEntities() {
        return this.entityManager.getEntityGetter();
    }

    public void addLegacyChunkEntities(Stream<Entity> p_143312_) {
        this.entityManager.addLegacyChunkEntities(p_143312_);
    }

    public void addWorldGenChunkEntities(Stream<Entity> p_143328_) {
        this.entityManager.addWorldGenChunkEntities(p_143328_);
    }

    public void startTickingChunk(LevelChunk p_184103_) {
        p_184103_.unpackTicks(this.getLevelData().getGameTime());
    }

    public void onStructureStartsAvailable(ChunkAccess p_196558_) {
        this.server.execute(() -> this.structureCheck.onStructureLoad(p_196558_.getPos(), p_196558_.getAllStarts()));
    }

    public PathTypeCache getPathTypeCache() {
        return this.pathTypesByPosCache;
    }

    @Override
    public void close() throws IOException {
        super.close();
        this.entityManager.close();
    }

    @Override
    public String gatherChunkSourceStats() {
        return "Chunks[S] W: " + this.chunkSource.gatherStats() + " E: " + this.entityManager.gatherStats();
    }

    public boolean areEntitiesLoaded(long p_143320_) {
        return this.entityManager.areEntitiesLoaded(p_143320_);
    }

    public boolean isPositionTickingWithEntitiesLoaded(long p_184111_) { // Folia - region threading - public
        return this.areEntitiesLoaded(p_184111_) && this.chunkSource.isPositionTicking(p_184111_);
    }

    public boolean isPositionEntityTicking(BlockPos p_143341_) {
        return this.entityManager.canPositionTick(p_143341_) && this.chunkSource.chunkMap.getDistanceManager().inEntityTickingRange(ChunkPos.asLong(p_143341_));
    }

    public boolean isNaturalSpawningAllowed(BlockPos p_201919_) {
        return this.entityManager.canPositionTick(p_201919_);
    }

    public boolean isNaturalSpawningAllowed(ChunkPos p_201917_) {
        return this.entityManager.canPositionTick(p_201917_);
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.server.getWorldData().enabledFeatures();
    }

    @Override
    public PotionBrewing potionBrewing() {
        return this.server.potionBrewing();
    }

    public RandomSource getRandomSequence(ResourceLocation p_287689_) {
        return this.randomSequences.get(p_287689_);
    }

    public RandomSequences getRandomSequences() {
        return this.randomSequences;
    }

    @Override
    public CrashReportCategory fillReportDetails(CrashReport p_307518_) {
        CrashReportCategory crashreportcategory = super.fillReportDetails(p_307518_);
        crashreportcategory.setDetail("Loaded entity count", () -> String.valueOf(this.entityManager.count()));
        return crashreportcategory;
    }

    final class EntityCallbacks implements LevelCallback<Entity> {
        public void onCreated(Entity p_143355_) {
        }

        public void onDestroyed(Entity p_143359_) {
            ServerLevel.this.getScoreboard().entityRemoved(p_143359_);
        }

        public void onTickingStart(Entity p_143363_) {
            if (p_143363_ instanceof net.minecraft.world.entity.Marker && !paperConfig().entities.markers.tick) return; // Paper - Configurable marker ticking
            ServerLevel.this.entityTickList.add(p_143363_);
        }

        public void onTickingEnd(Entity p_143367_) {
            ServerLevel.this.entityTickList.remove(p_143367_);
            // Paper start - Reset pearls when they stop being ticked
            if (paperConfig().fixes.disableUnloadedChunkEnderpearlExploit && p_143367_ instanceof net.minecraft.world.entity.projectile.ThrownEnderpearl pearl) {
                pearl.cachedOwner = null;
                pearl.ownerUUID = null;
            }
            // Paper end - Reset pearls when they stop being ticked
        }

        public void onTrackingStart(Entity p_143371_) {
            // ServerLevel.this.getChunkSource().addEntity(p_143371_); // Paper - ignore and warn about illegal addEntity calls instead of crashing server; moved down below valid=true
            if (p_143371_ instanceof ServerPlayer serverplayer) {
                ServerLevel.this.players.add(serverplayer);
                ServerLevel.this.updateSleepingPlayerList();
            }

            if (p_143371_ instanceof Mob mob) {
                if (false && ServerLevel.this.isUpdatingNavigations) {
                    String s = "onTrackingStart called during navigation iteration";
                    Util.logAndPauseIfInIde(
                        "onTrackingStart called during navigation iteration", new IllegalStateException("onTrackingStart called during navigation iteration")
                    );
                }

                ServerLevel.this.navigatingMobs.add(mob);
            }

            if (p_143371_.isMultipartEntity()) {
                for(net.neoforged.neoforge.entity.PartEntity<?> enderdragonpart : p_143371_.getParts()) {
                    ServerLevel.this.dragonParts.put(enderdragonpart.getId(), enderdragonpart);
                }
            }

            p_143371_.updateDynamicGameEventListener(DynamicGameEventListener::add);
            p_143371_.inWorld = true; // CraftBukkit - Mark entity as in world
            p_143371_.valid = true; // CraftBukkit
            ServerLevel.this.getChunkSource().addEntity(p_143371_); // Paper - ignore and warn about illegal addEntity calls instead of crashing server
            // Paper start - Entity origin API
            if (p_143371_.getOriginVector() == null) {
                p_143371_.setOrigin(p_143371_.getBukkitEntity().getLocation());
            }
            // Default to current world if unknown, gross assumption but entities rarely change world
            if (p_143371_.getOriginWorld() == null) {
                p_143371_.setOrigin(p_143371_.getOriginVector().toLocation(getWorld()));
            }
            // Paper end - Entity origin API
            if (!AsyncCatcher.catchAsync()) {
                new EntityAddToWorldEvent(p_143371_.getBukkitEntity(), ServerLevel.this.getWorld()).callEvent(); // Paper - fire while valid
            }
        }

        public void onTrackingEnd(Entity p_143375_) {
            // Spigot start
            if ( p_143375_ instanceof Player )
            {
                com.google.common.collect.Streams.stream( getServer().getAllLevels() ).map( ServerLevel::getDataStorage ).forEach( (worldData) ->
                {
                    for (Object o : worldData.cache.values() )
                    {
                        if ( o instanceof MapItemSavedData )
                        {
                            MapItemSavedData map = (MapItemSavedData) o;
                            map.carriedByPlayers.remove( (Player) p_143375_ );
                            for ( java.util.Iterator<net.minecraft.world.level.saveddata.maps.MapItemSavedData.HoldingPlayer> iter = (java.util.Iterator<net.minecraft.world.level.saveddata.maps.MapItemSavedData.HoldingPlayer>) map.carriedBy.iterator(); iter.hasNext(); )
                            {
                                if ( iter.next().player == p_143375_ )
                                {
                                    map.decorations.remove(p_143375_.getName().getString()); // Paper
                                    iter.remove();
                                }
                            }
                        }
                    }
                } );
            }
            // Spigot end
            // Spigot Start
            if (p_143375_.getBukkitEntity() instanceof org.bukkit.inventory.InventoryHolder && (!(p_143375_ instanceof ServerPlayer) || p_143375_.getRemovalReason() != Entity.RemovalReason.KILLED) && !(p_143375_ instanceof FakePlayer)) { // SPIGOT-6876: closeInventory clears death message
                // Paper start - Fix merchant inventory not closing on entity removal
                if (p_143375_.getBukkitEntity() instanceof org.bukkit.inventory.Merchant merchant && merchant.getTrader() != null) {
                    merchant.getTrader().closeInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason.UNLOADED);
                }
                // Paper end - Fix merchant inventory not closing on entity removal
                for (org.bukkit.entity.HumanEntity h : Lists.newArrayList(((org.bukkit.inventory.InventoryHolder) p_143375_.getBukkitEntity()).getInventory().getViewers())) {
                    h.closeInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason.UNLOADED); // Paper - Inventory close reason
                }
            }
            // Spigot End
            ServerLevel.this.getChunkSource().removeEntity(p_143375_);
            if (p_143375_ instanceof ServerPlayer serverplayer) {
                ServerLevel.this.players.remove(serverplayer);
                ServerLevel.this.updateSleepingPlayerList();
            }

            if (p_143375_ instanceof Mob mob) {
                if (false && ServerLevel.this.isUpdatingNavigations) {
                    String s = "onTrackingStart called during navigation iteration";
                    Util.logAndPauseIfInIde(
                              "onTrackingStart called during navigation iteration", new IllegalStateException("onTrackingStart called during navigation iteration")
                    );
                }

                ServerLevel.this.navigatingMobs.remove(mob);
            }

            if (p_143375_.isMultipartEntity()) {
                for(net.neoforged.neoforge.entity.PartEntity<?> enderdragonpart : p_143375_.getParts()) {
                    ServerLevel.this.dragonParts.remove(enderdragonpart.getId());
                }
            }

            p_143375_.updateDynamicGameEventListener(DynamicGameEventListener::remove);

            // CraftBukkit start
            p_143375_.valid = false;
            if (!(p_143375_ instanceof ServerPlayer)) {
                for (ServerPlayer player : players) {
                    player.getBukkitEntity().onEntityRemove(p_143375_);
                }
            }
            // CraftBukkit end
            if (!AsyncCatcher.catchAsync()) {
                new EntityRemoveFromWorldEvent(p_143375_.getBukkitEntity(), ServerLevel.this.getWorld()).callEvent(); // Paper - fire while valid
            }
            p_143375_.onRemovedFromLevel();
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent(p_143375_, ServerLevel.this));
        }

        public void onSectionChange(Entity p_215086_) {
            p_215086_.updateDynamicGameEventListener(DynamicGameEventListener::move);
        }
    }

    @Override
    public java.util.Collection<net.neoforged.neoforge.entity.PartEntity<?>> getPartEntities() {
        return this.dragonParts.values();
    }

    @Override
    public final void syncData(net.neoforged.neoforge.attachment.AttachmentType<?> type) {
        net.neoforged.neoforge.attachment.AttachmentSync.syncLevelUpdate(this, type);
    }

    private final net.neoforged.neoforge.capabilities.CapabilityListenerHolder capListenerHolder = new net.neoforged.neoforge.capabilities.CapabilityListenerHolder();

    @Override
    public void invalidateCapabilities(BlockPos pos) {
        capListenerHolder.invalidatePos(pos);
    }

    @Override
    public void invalidateCapabilities(ChunkPos pos) {
        capListenerHolder.invalidateChunk(pos);
    }

    /**
     * Register a listener for capability invalidation.
     * @see net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener
     */
    public void registerCapabilityListener(BlockPos pos, net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener listener) {
        capListenerHolder.addListener(pos, listener);
    }

    /**
     * Internal method, used to clean capability listeners that are not referenced.
     * Do not call.
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public void cleanCapabilityListenerReferences() {
        capListenerHolder.clean();
    }

    // Neo: Variable day time code

    @org.jetbrains.annotations.ApiStatus.Internal
    public void setDayTimeFraction(float dayTimeFraction) {
        K.setDayTimeFraction(dayTimeFraction);
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public float getDayTimeFraction() {
        return K.getDayTimeFraction();
    }

    /**
     * Returns the current ratio between game ticks and clock ticks. If this value is negative, no
     * speed has been set and those two are coupled 1:1 (i.e. vanilla mode).
     */
    public float getDayTimePerTick() {
        return K.getDayTimePerTick();
    }

    /**
     * This allows mods to set the rate time flows in a level. By default, each game tick the clock time
     * also advances by one tick, with {@link Level#TICKS_PER_DAY} clock ticks (or 20 real-life minutes)
     * forming a Minecraft day.
     * <p>
     * This can be sped up for shorter days by giving a higher number, or slowed down for longer days
     * with a smaller number. A negative value will reset it back to vanilla logic.
     * <p>
     * This value can also be changed with the command <code>/neoforge day</code>, where you can set
     * either the speed or a day length in minutes.
     * <p>
     * This has no effect when time progression is stopped.
     * <p>
     * While this still technically works when vanilla clients are connected, those will desync and
     * experience a time jump once per second.
     */
    @Override
    public void setDayTimePerTick(float dayTimePerTick) {
        if (dayTimePerTick != getDayTimePerTick() && dayTimePerTick != 0f) {
            K.setDayTimePerTick(dayTimePerTick);
            server.forceTimeSynchronization();
        }
    }

}
