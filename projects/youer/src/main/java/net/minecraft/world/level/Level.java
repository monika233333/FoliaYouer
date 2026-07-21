package net.minecraft.world.level;

import ca.spottedleaf.moonrise.common.util.TickThread;
import com.google.common.collect.Lists;
import com.mohistmc.youer.YouerConfig;
import com.mohistmc.youer.api.event.block.SetBlockEvent;
import com.mohistmc.youer.feature.ban.bans.BanBlock;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.extensions.ILevelExtension;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.server.timings.TimeTracker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CapturedBlockState;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.util.CraftSpawnCategory;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.spigotmc.AsyncCatcher;
import org.spigotmc.SpigotWorldConfig;

public abstract class Level extends AttachmentHolder implements LevelAccessor, AutoCloseable, ILevelExtension, ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel, ca.spottedleaf.moonrise.patches.chunk_system.world.ChunkSystemEntityGetter, ca.spottedleaf.moonrise.patches.collisions.world.CollisionLevel { // Folia - rewrite chunk system + optimise collisions
    public static final Codec<ResourceKey<Level>> RESOURCE_KEY_CODEC = ResourceKey.codec(Registries.DIMENSION);
    public static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
    public static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_nether"));
    public static final ResourceKey<Level> END = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_end"));
    public static final int MAX_LEVEL_SIZE = 30000000;
    public static final int LONG_PARTICLE_CLIP_RANGE = 512;
    public static final int SHORT_PARTICLE_CLIP_RANGE = 32;
    public static final int MAX_BRIGHTNESS = 15;
    public static final int TICKS_PER_DAY = 24000;
    public static final int MAX_ENTITY_SPAWN_Y = 20000000;
    public static final int MIN_ENTITY_SPAWN_Y = -20000000;
    public final List<TickingBlockEntity> blockEntityTickers = Lists.newArrayList();
    protected final NeighborUpdater neighborUpdater;
    public final int neighbourUpdateMax; // Folia - region threading
    private final List<TickingBlockEntity> pendingBlockEntityTickers = Lists.newArrayList();
    private boolean tickingBlockEntities;
    public final Thread thread;
    private final boolean isDebug;
    private int skyDarken;
    protected int randValue = RandomSource.create().nextInt();
    protected final int addend = 1013904223;
    public float oRainLevel;
    public float rainLevel;
    public float oThunderLevel;
    public float thunderLevel;
    public final RandomSource random = RandomSource.create();
    @Deprecated
    private final RandomSource threadSafeRandom = RandomSource.createThreadSafe();
    private final Holder<DimensionType> dimensionTypeRegistration;
    public final WritableLevelData levelData;
    private final Supplier<ProfilerFiller> profiler;
    public final boolean isClientSide;
    private final WorldBorder worldBorder;

    // Paper start - rewrite chunk system
    private ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup entityLookup;

    @Override
    public final ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup moonrise$getEntityLookup() {
        return this.entityLookup;
    }

    @Override
    public void moonrise$setEntityLookup(final ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup entityLookup) {
        if (this.entityLookup != null && !(this.entityLookup instanceof ca.spottedleaf.moonrise.patches.chunk_system.level.entity.dfl.DefaultEntityLookup)) {
            throw new IllegalStateException("Entity lookup already initialised");
        }
        this.entityLookup = entityLookup;
    }
    // Paper end - rewrite chunk system

    // Folia start - region ticking
    public final io.papermc.paper.threadedregions.RegionizedData<io.papermc.paper.threadedregions.RegionizedWorldData> worldRegionData
        = new io.papermc.paper.threadedregions.RegionizedData<>(
        (ServerLevel)this, () -> new io.papermc.paper.threadedregions.RegionizedWorldData((ServerLevel)Level.this),
        io.papermc.paper.threadedregions.RegionizedWorldData.REGION_CALLBACK
    );
    public volatile io.papermc.paper.threadedregions.RegionizedServer.WorldLevelData tickData;
    public final java.util.concurrent.ConcurrentHashMap.KeySetView<net.minecraft.server.level.ChunkHolder, Boolean> needsChangeBroadcasting = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // Folia end - region ticking

    @Override
    public LevelChunk moonrise$getFullChunkIfLoaded(final int chunkX, final int chunkZ) {
        return this.getChunkSource().getChunk(chunkX, chunkZ, false);
    }

    @Override
    public ChunkAccess moonrise$getAnyChunkIfLoaded(final int chunkX, final int chunkZ) {
        return this.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.EMPTY, false);
    }

    @Override
    public ChunkAccess moonrise$getSpecificChunkIfLoaded(final int chunkX, final int chunkZ, final ChunkStatus leastStatus) {
        return this.getChunkSource().getChunk(chunkX, chunkZ, leastStatus, false);
    }

    @Override
    public void moonrise$midTickTasks() {
        // no-op on Level (overridden in ServerLevel)
    }

    // Paper start - optimise collisions
    @Override
    public final int moonrise$getMinSection() {
        return this.getMinSection();
    }

    @Override
    public final int moonrise$getMaxSection() {
        return this.getMaxSection();
    }
    // Paper end - optimise collisions

    @Override
    public final java.util.List<Entity> moonrise$getHardCollidingEntities(final Entity entity, final AABB box, final Predicate<? super Entity> predicate) {
        this.getProfiler().incrementCounter("getEntities");
        final java.util.List<Entity> ret = new java.util.ArrayList<>();
        ((ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel)this).moonrise$getEntityLookup().getHardCollidingEntities(entity, box, ret, predicate);
        return ret;
    }
    private final BiomeManager biomeManager;
    public ResourceKey<Level> dimension;
    private final RegistryAccess registryAccess;
    private final DamageSources damageSources;
    private long subTickCount;
    public boolean restoringBlockSnapshots = false;
    public boolean captureBlockSnapshots = false;
    public ArrayList<BlockSnapshot> capturedBlockSnapshots = new ArrayList<>();
    private final ArrayList<BlockEntity> freshBlockEntities = new ArrayList<>();
    private final ArrayList<BlockEntity> pendingFreshBlockEntities = new ArrayList<>();

    // CraftBukkit start Added the following
    public CraftWorld world;
    public boolean pvpMode;
    @Nullable
    public static ChunkGenerator generator;
    @Nullable
    public static World.Environment environment;
    @Nullable
    public static BiomeProvider biomeProvider;

    public boolean preventPoiUpdated = false; // CraftBukkit - SPIGOT-5710
    public boolean captureBlockStates = false;
    public boolean captureTreeGeneration = false;
    public boolean captureTreeGeneration$Youer = false;
    public Map<BlockPos, org.bukkit.craftbukkit.block.CraftBlockState> capturedBlockStates = new java.util.LinkedHashMap<>(); // Paper
    public Map<BlockPos, BlockEntity> capturedTileEntities = new java.util.LinkedHashMap<>(); // Paper - Retain block place order when capturing blockstates
    public List<ItemEntity> captureDrops;
    public final Object2LongOpenHashMap<SpawnCategory> ticksPerSpawnCategory = new Object2LongOpenHashMap<>();
    public boolean populating;
    public SpigotWorldConfig spigotConfig; // Spigot
    public String name;
    public static BlockPos lastPhysicsProblem; // Spigot
    // Paper start
    public int wakeupInactiveRemainingAnimals;
    public int wakeupInactiveRemainingFlying;
    public int wakeupInactiveRemainingMonsters;
    public int wakeupInactiveRemainingVillagers;
    // Paper end
    // Paper start - add paper world config
    public io.papermc.paper.configuration.WorldConfiguration paperConfig;
    public io.papermc.paper.configuration.WorldConfiguration paperConfig() {
        return this.paperConfig;
    }
    public java.util.function.Function<org.spigotmc.SpigotWorldConfig, io.papermc.paper.configuration.WorldConfiguration> paperWorldConfigCreator;
    // Paper end - add paper world config
    public org.purpurmc.purpur.PurpurWorldConfig purpurConfig; // Purpur

    // Purpur start
    private com.google.common.cache.Cache<BreedingCooldownPair, Object> playerBreedingCooldowns;

    private com.google.common.cache.Cache<BreedingCooldownPair, Object> getNewBreedingCooldownCache() {
        return com.google.common.cache.CacheBuilder.newBuilder().expireAfterWrite(this.purpurConfig.animalBreedingCooldownSeconds, java.util.concurrent.TimeUnit.SECONDS).build();
    }

    public void resetBreedingCooldowns() {
        this.playerBreedingCooldowns = this.getNewBreedingCooldownCache();
    }

    public boolean hasBreedingCooldown(java.util.UUID player, Class<? extends net.minecraft.world.entity.animal.Animal> animalType) { // Purpur
        return this.playerBreedingCooldowns.getIfPresent(new BreedingCooldownPair(player, animalType)) != null;
    }

    public void addBreedingCooldown(java.util.UUID player, Class<? extends net.minecraft.world.entity.animal.Animal> animalType) {
        this.playerBreedingCooldowns.put(new BreedingCooldownPair(player, animalType), new Object());
    }

    private static final class BreedingCooldownPair {
        private final java.util.UUID playerUUID;
        private final Class<? extends net.minecraft.world.entity.animal.Animal> animalType;

        public BreedingCooldownPair(java.util.UUID playerUUID, Class<? extends net.minecraft.world.entity.animal.Animal> animalType) {
            this.playerUUID = playerUUID;
            this.animalType = animalType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BreedingCooldownPair that = (BreedingCooldownPair) o;
            return playerUUID.equals(that.playerUUID) && animalType.equals(that.animalType);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(playerUUID, animalType);
        }
    }
    // Purpur end

    public CraftWorld getWorld() {
        return this.world;
    }

    public CraftServer getCraftServer() {
        return (CraftServer) Bukkit.getServer();
    }

    // Paper start - per world ticks per spawn
    protected int getTicksPerSpawn(SpawnCategory spawnCategory) {
        if (this.paperConfig() != null) {
            final int perWorld = this.paperConfig().entities.spawning.ticksPerSpawn.getInt(CraftSpawnCategory.toNMS(spawnCategory));
            if (perWorld >= 0) {
                return perWorld;
            }
        }
        return this.getCraftServer().getTicksPerSpawns(spawnCategory);
    }
    // Paper end

    public ResourceKey<LevelStem> getTypeKey() {
        return Registries.levelToLevelStem(dimension);
    }

    public static void craftWorldData(World.Environment env, @Nullable ChunkGenerator cg, @Nullable BiomeProvider bp) {
        environment = env;
        biomeProvider = bp;
        generator = cg;
    }

    protected Level(
        WritableLevelData p_270739_,
        ResourceKey<Level> p_270683_,
        RegistryAccess p_270200_,
        Holder<DimensionType> p_270240_,
        Supplier<ProfilerFiller> p_270692_,
        boolean p_270904_,
        boolean p_270470_,
        long p_270248_,
        int p_270466_
    ) {
        this.profiler = p_270692_;
        this.levelData = p_270739_;
        this.dimensionTypeRegistration = p_270240_;
        final DimensionType dimensiontype = p_270240_.value();
        this.dimension = p_270683_;
        this.isClientSide = p_270904_;
        if (dimensiontype.coordinateScale() != 1.0) {
            this.worldBorder = new WorldBorder() {
                @Override
                public double getCenterX() {
                    return super.getCenterX(); // CraftBukkit
                }

                @Override
                public double getCenterZ() {
                    return super.getCenterZ(); // CraftBukkit
                }
            };
        } else {
            this.worldBorder = new WorldBorder();
        }

        this.thread = Thread.currentThread();
        this.biomeManager = new BiomeManager(this, p_270248_);
        this.isDebug = p_270470_;
        this.neighborUpdater = new CollectingNeighborUpdater(this, p_270466_);
        this.neighbourUpdateMax = p_270466_; // Folia - region threading
        this.registryAccess = p_270200_;
        this.damageSources = new DamageSources(p_270200_);
        this.entityLookup = new ca.spottedleaf.moonrise.patches.chunk_system.level.entity.dfl.DefaultEntityLookup(this); // Paper - rewrite chunk system
    }

    // Paper start - Cancel hit for vanished players
    // ret true if no collision
    public final boolean checkEntityCollision(BlockState data, Entity source, net.minecraft.world.phys.shapes.CollisionContext voxelshapedcollision,
                                              BlockPos position, boolean checkCanSee) {
        // Copied from IWorldReader#a(IBlockData, BlockPosition, VoxelShapeCollision) & EntityAccess#a(Entity, VoxelShape)
        net.minecraft.world.phys.shapes.VoxelShape voxelshape = data.getCollisionShape(this, position, voxelshapedcollision);
        if (voxelshape.isEmpty()) {
            return true;
        }

        voxelshape = voxelshape.move((double) position.getX(), (double) position.getY(), (double) position.getZ());
        if (voxelshape.isEmpty()) {
            return true;
        }

        List<Entity> entities = this.getEntities(null, voxelshape.bounds());
        for (int i = 0, len = entities.size(); i < len; ++i) {
            Entity entity = entities.get(i);

            if (checkCanSee && source instanceof net.minecraft.server.level.ServerPlayer && entity instanceof net.minecraft.server.level.ServerPlayer
                    && !((net.minecraft.server.level.ServerPlayer) source).getBukkitEntity().canSee(((net.minecraft.server.level.ServerPlayer) entity).getBukkitEntity())) {
                continue;
            }

            // !entity1.dead && entity1.i && (entity == null || !entity1.x(entity));
            // elide the last check since vanilla calls with entity = null
            // only we care about the source for the canSee check
            if (entity.isRemoved() || !entity.blocksBuilding) {
                continue;
            }

            if (net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(voxelshape, net.minecraft.world.phys.shapes.Shapes.create(entity.getBoundingBox()), net.minecraft.world.phys.shapes.BooleanOp.AND)) {
                return false;
            }
        }

        return true;
    }
    // Paper end - Cancel hit for vanished players

    @Override
    public boolean isClientSide() {
        return this.isClientSide;
    }

    @Nullable
    @Override
    public MinecraftServer getServer() {
        return null;
    }

    // Paper start
    public net.minecraft.world.phys.BlockHitResult.Type clipDirect(Vec3 start, Vec3 end, net.minecraft.world.phys.shapes.CollisionContext context) {
        // To be patched over
        return this.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context)).getType();
    }
    // Paper end

    public boolean isInWorldBounds(BlockPos p_46740_) {
        return !this.isOutsideBuildHeight(p_46740_) && isInWorldBoundsHorizontal(p_46740_);
    }

    public static boolean isInSpawnableBounds(BlockPos p_46742_) {
        return !isOutsideSpawnableHeight(p_46742_.getY()) && isInWorldBoundsHorizontal(p_46742_);
    }

    private static boolean isInWorldBoundsHorizontal(BlockPos p_46458_) {
        return p_46458_.getX() >= -30000000 && p_46458_.getZ() >= -30000000 && p_46458_.getX() < 30000000 && p_46458_.getZ() < 30000000;
    }

    private static boolean isOutsideSpawnableHeight(int p_46725_) {
        return p_46725_ < -20000000 || p_46725_ >= 20000000;
    }

    public LevelChunk getChunkAt(BlockPos p_46746_) {
        return this.getChunk(SectionPos.blockToSectionCoord(p_46746_.getX()), SectionPos.blockToSectionCoord(p_46746_.getZ()));
    }

    public LevelChunk getChunk(int p_46727_, int p_46728_) {
        return (LevelChunk)this.getChunk(p_46727_, p_46728_, ChunkStatus.FULL);
    }

    @Nullable
    @Override
    public final ChunkAccess getChunkIfLoadedImmediately(int x, int z) {
        if (this instanceof ServerLevel serverLevel) {
            return serverLevel.chunkSource.getChunkAtIfLoadedImmediately(x, z);
        }
        return null;
    }

    @Override
    @Nullable
    public final BlockState getBlockStateIfLoaded(BlockPos pos) {
        // CraftBukkit start - tree generation
        if (this.captureTreeGeneration) {
            CraftBlockState previous = this.capturedBlockStates.get(pos);
            if (previous != null) {
                return previous.getHandle();
            }
        }
        // CraftBukkit end
        if (this.isOutsideBuildHeight(pos)) {
            return Blocks.VOID_AIR.defaultBlockState();
        } else {
            ChunkAccess chunk = this.getChunkIfLoadedImmediately(pos.getX() >> 4, pos.getZ() >> 4);

            return chunk == null ? null : chunk.getBlockState(pos);
        }
    }

    public final boolean isLoadedAndInBounds(BlockPos blockposition) { // Paper - final for inline
        return getWorldBorder().isWithinBounds(blockposition) && getChunkIfLoadedImmediately(blockposition.getX() >> 4, blockposition.getZ() >> 4) != null;
    }

    public @Nullable LevelChunk getChunkIfLoaded(int x, int z) { // Overridden in WorldServer for ABI compat which has final
        if (this instanceof ServerLevel serverLevel) {
            return serverLevel.getChunkSource().getChunkAtIfLoadedImmediately(x, z);
        }
        return null;
    }
    public final @Nullable LevelChunk getChunkIfLoaded(BlockPos blockposition) {
        if (this instanceof ServerLevel serverLevel) {
            return serverLevel.getChunkSource().getChunkAtIfLoadedImmediately(blockposition.getX() >> 4, blockposition.getZ() >> 4);
        }
        return null;
    }

    //  reduces need to do isLoaded before getType
    public final @Nullable BlockState getBlockStateIfLoadedAndInBounds(BlockPos blockposition) {
        return getWorldBorder().isWithinBounds(blockposition) ? getBlockStateIfLoaded(blockposition) : null;
    }

    @Nullable
    @Override
    public ChunkAccess getChunk(int p_46502_, int p_46503_, ChunkStatus p_331611_, boolean p_46505_) {
        ChunkAccess chunkaccess = this.getChunkSource().getChunk(p_46502_, p_46503_, p_331611_, p_46505_);
        if (chunkaccess == null && p_46505_) {
            throw new IllegalStateException("Should always be able to create a chunk!");
        } else {
            return chunkaccess;
        }
    }

    public boolean fake$setBlock = false;
    public BlockState fake$setBlockBlockState = null;
    @Override
    public boolean setBlock(BlockPos p_46601_, BlockState p_46602_, int p_46603_) {
        if (fake$setBlock) {
            fake$setBlock = false;
            fake$setBlockBlockState = p_46602_;
            return false;
        }
        return this.setBlock(p_46601_, p_46602_, p_46603_, 512);
    }

    AtomicBoolean captured = new AtomicBoolean(false);
    @Override
    public boolean setBlock(BlockPos p_46605_, BlockState p_46606_, int p_46607_, int p_46608_) {
        if (BanBlock.check(p_46606_)) {
           return false;
        }
        this.captured.set(false);
        {
            if (YouerConfig.custom_disabled_spawner && p_46606_.getBlock() == Blocks.SPAWNER) {
                p_46606_ = Blocks.AIR.defaultBlockState();
            }
            if (SetBlockEvent.getHandlerList().getRegisteredListeners().length > 0 && this.getWorld() != null) {
                SetBlockEvent event = new SetBlockEvent(new Location(this.getWorld(), p_46605_.getX(), p_46605_.getY(), p_46605_.getZ()));
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    return false;
                }
            }
        }
        // CraftBukkit start - tree generation
        if (this.captureTreeGeneration) {
            CraftBlockState blockstate = capturedBlockStates.get(p_46605_);
            if (blockstate == null) {
                blockstate = CapturedBlockState.getTreeBlockState(this, p_46605_, p_46607_);
                this.capturedBlockStates.put(p_46605_.immutable(), blockstate);
            }
            blockstate.setData(p_46606_);
            blockstate.setFlag(p_46607_);
            return true;
        }
        // CraftBukkit end

        if (this.isOutsideBuildHeight(p_46605_)) {
            return false;
        } else if (!this.isClientSide && this.isDebug()) {
            return false;
        } else {
            LevelChunk levelchunk = this.getChunkAt(p_46605_);
            Block block = p_46606_.getBlock();

            p_46605_ = p_46605_.immutable(); // Forge - prevent mutable BlockPos leaks
            BlockSnapshot blockSnapshot = null;
            if (this.captureBlockSnapshots && !this.isClientSide) {
                 blockSnapshot = BlockSnapshot.create(this.dimension, this, p_46605_, p_46607_);
                 this.capturedBlockSnapshots.add(blockSnapshot);
            }

            // CraftBukkit start - capture blockstates
            if (this.captureBlockStates && !this.capturedBlockStates.containsKey(p_46605_)) {
                CraftBlockState blockstate = (CraftBlockState) world.getBlockAt(p_46605_.getX(), p_46605_.getY(), p_46605_.getZ()).getState(); // Paper - use CB getState to get a suitable snapshot
                blockstate.setFlag(p_46607_); // Paper - set flag
                this.capturedBlockStates.put(p_46605_.immutable(), blockstate);
                this.captured.set(true);
            }
            // CraftBukkit end

            BlockState old = getBlockState(p_46605_);
            int oldLight = old.getLightEmission(this, p_46605_);
            int oldOpacity = old.getLightBlock(this, p_46605_);
            levelchunk.setBlockState$doPlace.set((p_46607_ & 1024) == 0); // CraftBukkit custom NO_PLACE flag
            BlockState blockstate = levelchunk.setBlockState(p_46605_, p_46606_, (p_46607_ & 64) != 0);
            if (blockstate == null) {
                if (blockSnapshot != null) this.capturedBlockSnapshots.remove(blockSnapshot);
                // CraftBukkit start - remove blockstate if failed (or the same)
                if (this.captureBlockStates && this.captured.getAndSet(false)) {
                    this.capturedBlockStates.remove(p_46605_);
                }
                // CraftBukkit end
                return false;
            } else {
                BlockState blockstate1 = this.getBlockState(p_46605_);

                if (blockSnapshot == null && !this.captureBlockStates) { // Don't notify clients or update physics while capturing blockstates
                    // Spigot start
                    try {
                        this.markAndNotifyBlock(p_46605_, levelchunk, blockstate, p_46606_, p_46607_, p_46608_);
                    } catch (StackOverflowError ex) {
                        lastPhysicsProblem = new BlockPos(p_46605_);
                    }
                    // Spigot end
                }
                return true;
            }
        }
    }

    // Split off from original setBlockState(BlockPos, BlockState, int, int) method in order to directly send client and physic updates
    public void markAndNotifyBlock(BlockPos p_46605_, @Nullable LevelChunk levelchunk, BlockState blockstate, BlockState p_46606_, int p_46607_, int p_46608_) {
        Block block = p_46606_.getBlock();
        BlockState blockstate1 = getBlockState(p_46605_);
        {
            {
                if (blockstate1 == p_46606_) {
                    if (blockstate != blockstate1) {
                        this.setBlocksDirty(p_46605_, blockstate, blockstate1);
                    }

                    if ((p_46607_ & 2) != 0
                        && (!this.isClientSide || (p_46607_ & 4) == 0)
                        && (this.isClientSide || levelchunk.getFullStatus() != null && levelchunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING))) {
                        this.sendBlockUpdated(p_46605_, blockstate, p_46606_, p_46607_);
                    }

                    if ((p_46607_ & 1) != 0) {
                        this.blockUpdated(p_46605_, blockstate.getBlock());
                        if (!this.isClientSide && p_46606_.hasAnalogOutputSignal()) {
                            this.updateNeighbourForOutputSignal(p_46605_, block);
                        }
                    }

                    if ((p_46607_ & 16) == 0 && p_46608_ > 0) {
                        int i = p_46607_ & -34;
                        blockstate.updateIndirectNeighbourShapes(this, p_46605_, i, p_46608_ - 1);
                        // CraftBukkit start
                        if (!AsyncCatcher.catchAsync() && this instanceof ServerLevel serverLevel) {
                            CraftWorld world = serverLevel.getWorld();
                            if (world != null && serverLevel.hasPhysicsEvent) { // Paper - BlockPhysicsEvent
                                BlockPhysicsEvent event = new BlockPhysicsEvent(world.getBlockAt(p_46605_.getX(), p_46605_.getY(), p_46605_.getZ()), CraftBlockData.fromData(blockstate1));
                                this.getCraftServer().getPluginManager().callEvent(event);

                                if (event.isCancelled()) {
                                    return;
                                }
                            }
                        }
                        // CraftBukkit end
                        p_46606_.updateNeighbourShapes(this, p_46605_, i, p_46608_ - 1);
                        p_46606_.updateIndirectNeighbourShapes(this, p_46605_, i, p_46608_ - 1);
                    }

                    // CraftBukkit start - SPIGOT-5710
                    if (!preventPoiUpdated) {
                        this.onBlockStateChange(p_46605_, blockstate, blockstate1);
                    }
                    // CraftBukkit end
                    p_46606_.onBlockStateChange(this, p_46605_, blockstate);
                }
            }
        }
    }

    // CraftBukkit start - Split off from above in order to directly send client and physic updates
    public void notifyAndUpdatePhysics(BlockPos blockposition, LevelChunk chunk, BlockState oldBlock, BlockState newBlock, BlockState actualBlock, int i, int j) {
        BlockState iblockdata = newBlock;
        BlockState iblockdata1 = oldBlock;
        BlockState iblockdata2 = actualBlock;
        if (iblockdata2 == iblockdata) {
            if (iblockdata1 != iblockdata2) {
                this.setBlocksDirty(blockposition, iblockdata1, iblockdata2);
            }

            if ((i & 2) != 0 && (!this.isClientSide || (i & 4) == 0) && (this.isClientSide || chunk == null || (chunk.getFullStatus() != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)))) { // allow chunk to be null here as chunk.isReady() is false when we send our notification during block placement // Paper - rewrite chunk system - change from ticking to full
                this.sendBlockUpdated(blockposition, iblockdata1, iblockdata, i);
            }

            if ((i & 1) != 0) {
                this.blockUpdated(blockposition, iblockdata1.getBlock());
                if (!this.isClientSide && iblockdata.hasAnalogOutputSignal()) {
                    this.updateNeighbourForOutputSignal(blockposition, newBlock.getBlock());
                }
            }

            if ((i & 16) == 0 && j > 0) {
                int k = i & -34;

                // CraftBukkit start
                iblockdata1.updateIndirectNeighbourShapes(this, blockposition, k, j - 1); // Don't call an event for the old block to limit event spam
                boolean cancelledUpdates = false; // Paper - Fix block place logic
                if (!AsyncCatcher.catchAsync() && this instanceof ServerLevel serverLevel) {
                    CraftWorld world = serverLevel.getWorld();
                    if (world != null && serverLevel.hasPhysicsEvent) { // Paper - BlockPhysicsEvent
                        BlockPhysicsEvent event = new BlockPhysicsEvent(world.getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()), CraftBlockData.fromData(iblockdata));
                        this.getCraftServer().getPluginManager().callEvent(event);

                        cancelledUpdates = event.isCancelled(); // Paper - Fix block place logic
                    }
                }
                // CraftBukkit end
                if (!cancelledUpdates) { // Paper - Fix block place logic
                    iblockdata.updateNeighbourShapes(this, blockposition, k, j - 1);
                    iblockdata.updateIndirectNeighbourShapes(this, blockposition, k, j - 1);
                } // Paper - Fix block place logic
            }

            // CraftBukkit start - SPIGOT-5710
            if (!this.preventPoiUpdated) {
                this.onBlockStateChange(blockposition, iblockdata1, iblockdata2);
            }
            // CraftBukkit end
        }
    }
    // CraftBukkit end

    public void onBlockStateChange(BlockPos p_46609_, BlockState p_46610_, BlockState p_46611_) {
    }

    @Override
    public boolean removeBlock(BlockPos p_46623_, boolean p_46624_) {
        FluidState fluidstate = this.getFluidState(p_46623_);
        return this.setBlock(p_46623_, fluidstate.createLegacyBlock(), 3 | (p_46624_ ? 64 : 0));
    }

    @Override
    public boolean destroyBlock(BlockPos p_46626_, boolean p_46627_, @Nullable Entity p_46628_, int p_46629_) {
        BlockState blockstate = this.getBlockState(p_46626_);
        if (blockstate.isAir()) {
            return false;
        } else {
            FluidState fluidstate = this.getFluidState(p_46626_);
            if (this instanceof ServerLevel serverLevel) {
                // Paper start - BlockDestroyEvent; while the above setAir method is named same and looks very similar
                // they are NOT used with same intent and the above should not fire this event. The above method is more of a BlockSetToAirEvent,
                // it doesn't imply destruction of a block that plays a sound effect / drops an item.
                boolean playEffect = true;
                BlockState effectType = blockstate;
                int xp = blockstate.getBlock().getExpDrop(blockstate, serverLevel, p_46626_, ItemStack.EMPTY, true);
                if (com.destroystokyo.paper.event.block.BlockDestroyEvent.getHandlerList().getRegisteredListeners().length > 0) {
                    com.destroystokyo.paper.event.block.BlockDestroyEvent event = new com.destroystokyo.paper.event.block.BlockDestroyEvent(org.bukkit.craftbukkit.block.CraftBlock.at(this, p_46626_), fluidstate.createLegacyBlock().createCraftBlockData(), effectType.createCraftBlockData(), xp, p_46627_);
                    if (!event.callEvent()) {
                        return false;
                    }
                    effectType = ((CraftBlockData) event.getEffectBlock()).getState();
                    playEffect = event.playEffect();
                    p_46627_ = event.willDrop();
                    xp = event.getExpToDrop();
                }
                // Paper end - BlockDestroyEvent
                if (playEffect && !(effectType.getBlock() instanceof BaseFireBlock)) { // Paper - BlockDestroyEvent
                    this.levelEvent(2001, p_46626_, Block.getId(effectType)); // Paper - BlockDestroyEvent
                }
            } else {
                if (!(blockstate.getBlock() instanceof BaseFireBlock)) {
                    this.levelEvent(2001, p_46626_, Block.getId(blockstate));
                }
            }

            if (p_46627_) {
                BlockEntity blockentity = blockstate.hasBlockEntity() ? this.getBlockEntity(p_46626_) : null;
                Block.dropResources(blockstate, this, p_46626_, blockentity, p_46628_, ItemStack.EMPTY);
                // blockstate.getBlock().popExperience((ServerLevel) this, p_46626_, xp); // Paper - Properly handle xp dropping; custom amount // Youer TODO
            }

            boolean flag = this.setBlock(p_46626_, fluidstate.createLegacyBlock(), 3, p_46629_);
            if (flag) {
                this.gameEvent(GameEvent.BLOCK_DESTROY, p_46626_, GameEvent.Context.of(p_46628_, blockstate));
            }

            return flag;
        }
    }

    public void addDestroyBlockEffect(BlockPos p_151531_, BlockState p_151532_) {
    }

    // Mohist start
    private AtomicBoolean callEvent = new AtomicBoolean(false);

    public void callEvent(boolean call) {
        callEvent.set(call);
    }
    public BlockState youer$defaultBlockState;

    public boolean setBlockAndUpdate(BlockPos p_46598_, BlockState p_46599_) {
        youer$defaultBlockState = p_46599_;
        if (callEvent.getAndSet(false)) return false;
        return this.setBlock(p_46598_, p_46599_, 3);
    }
    // Mohist end

    public abstract void sendBlockUpdated(BlockPos p_46612_, BlockState p_46613_, BlockState p_46614_, int p_46615_);

    public void setBlocksDirty(BlockPos p_46678_, BlockState p_46679_, BlockState p_46680_) {
    }

    public void updateNeighborsAt(BlockPos p_46673_, Block p_46674_) {
        EventHooks.onNeighborNotify(this, p_46673_, this.getBlockState(p_46673_), EnumSet.allOf(Direction.class), false).isCanceled();
    }

    public void updateNeighborsAtExceptFromFacing(BlockPos p_46591_, Block p_46592_, Direction p_46593_) {
    }

    public void neighborChanged(BlockPos p_46587_, Block p_46588_, BlockPos p_46589_) {
    }

    public void neighborChanged(BlockState p_220379_, BlockPos p_220380_, Block p_220381_, BlockPos p_220382_, boolean p_220383_) {
    }

    @Override
    public void neighborShapeChanged(Direction p_220385_, BlockState p_220386_, BlockPos p_220387_, BlockPos p_220388_, int p_220389_, int p_220390_) {
        this.neighborUpdater.shapeUpdate(p_220385_, p_220386_, p_220387_, p_220388_, p_220389_, p_220390_);
    }

    @Override
    public int getHeight(Heightmap.Types p_46571_, int p_46572_, int p_46573_) {
        int i;
        if (p_46572_ >= -30000000 && p_46573_ >= -30000000 && p_46572_ < 30000000 && p_46573_ < 30000000) {
            if (this.hasChunk(SectionPos.blockToSectionCoord(p_46572_), SectionPos.blockToSectionCoord(p_46573_))) {
                i = this.getChunk(SectionPos.blockToSectionCoord(p_46572_), SectionPos.blockToSectionCoord(p_46573_))
                        .getHeight(p_46571_, p_46572_ & 15, p_46573_ & 15)
                    + 1;
            } else {
                i = this.getMinBuildHeight();
            }
        } else {
            i = this.getSeaLevel() + 1;
        }

        return i;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.getChunkSource().getLightEngine();
    }

    @Override
    public BlockState getBlockState(BlockPos p_46732_) {
        // CraftBukkit start - tree generation
        if (captureTreeGeneration) {
            CraftBlockState previous = capturedBlockStates.get(p_46732_);
            if (previous != null) {
                return previous.getHandle();
            }
        }
        // CraftBukkit end
        if (this.isOutsideBuildHeight(p_46732_)) {
            return Blocks.VOID_AIR.defaultBlockState();
        } else {
            LevelChunk levelchunk = this.getChunk(SectionPos.blockToSectionCoord(p_46732_.getX()), SectionPos.blockToSectionCoord(p_46732_.getZ()));
            return levelchunk.getBlockState(p_46732_);
        }
    }

    @Override
    public FluidState getFluidState(BlockPos p_46671_) {
        if (this.isOutsideBuildHeight(p_46671_)) {
            return Fluids.EMPTY.defaultFluidState();
        } else {
            LevelChunk levelchunk = this.getChunkAt(p_46671_);
            return levelchunk.getFluidState(p_46671_);
        }
    }

    public boolean isDay() {
        return !this.dimensionType().hasFixedTime() && this.skyDarken < 4;
    }

    public boolean isNight() {
        return !this.dimensionType().hasFixedTime() && !this.isDay();
    }

    public void playSound(@Nullable Entity p_252137_, BlockPos p_251749_, SoundEvent p_248842_, SoundSource p_251104_, float p_249531_, float p_250763_) {
        this.playSound(p_252137_ instanceof Player player ? player : null, p_251749_, p_248842_, p_251104_, p_249531_, p_250763_);
    }

    @Override
    public void playSound(@Nullable Player p_46560_, BlockPos p_46561_, SoundEvent p_46562_, SoundSource p_46563_, float p_46564_, float p_46565_) {
        this.playSound(
            p_46560_, (double)p_46561_.getX() + 0.5, (double)p_46561_.getY() + 0.5, (double)p_46561_.getZ() + 0.5, p_46562_, p_46563_, p_46564_, p_46565_
        );
    }

    public abstract void playSeededSound(
        @Nullable Player p_262953_,
        double p_263004_,
        double p_263398_,
        double p_263376_,
        Holder<SoundEvent> p_263359_,
        SoundSource p_263020_,
        float p_263055_,
        float p_262914_,
        long p_262991_
    );

    public void playSeededSound(
        @Nullable Player p_220363_,
        double p_220364_,
        double p_220365_,
        double p_220366_,
        SoundEvent p_220367_,
        SoundSource p_220368_,
        float p_220369_,
        float p_220370_,
        long p_220371_
    ) {
        this.playSeededSound(
            p_220363_, p_220364_, p_220365_, p_220366_, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(p_220367_), p_220368_, p_220369_, p_220370_, p_220371_
        );
    }

    public abstract void playSeededSound(
        @Nullable Player p_220372_, Entity p_220373_, Holder<SoundEvent> p_263500_, SoundSource p_220375_, float p_220376_, float p_220377_, long p_220378_
    );

    public void playSound(@Nullable Player p_309201_, double p_308925_, double p_309072_, double p_308916_, SoundEvent p_308917_, SoundSource p_308902_) {
        this.playSound(p_309201_, p_308925_, p_309072_, p_308916_, p_308917_, p_308902_, 1.0F, 1.0F);
    }

    public void playSound(
        @Nullable Player p_46543_, double p_46544_, double p_46545_, double p_46546_, SoundEvent p_46547_, SoundSource p_46548_, float p_46549_, float p_46550_
    ) {
        this.playSeededSound(p_46543_, p_46544_, p_46545_, p_46546_, p_46547_, p_46548_, p_46549_, p_46550_, this.threadSafeRandom.nextLong());
    }

    public void playSound(
        @Nullable Player p_347719_,
        double p_347460_,
        double p_347457_,
        double p_347558_,
        Holder<SoundEvent> p_347499_,
        SoundSource p_347522_,
        float p_347447_,
        float p_347667_
    ) {
        this.playSeededSound(p_347719_, p_347460_, p_347457_, p_347558_, p_347499_, p_347522_, p_347447_, p_347667_, this.threadSafeRandom.nextLong());
    }

    public void playSound(@Nullable Player p_46551_, Entity p_46552_, SoundEvent p_46553_, SoundSource p_46554_, float p_46555_, float p_46556_) {
        this.playSeededSound(
            p_46551_, p_46552_, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(p_46553_), p_46554_, p_46555_, p_46556_, this.threadSafeRandom.nextLong()
        );
    }

    public void playLocalSound(BlockPos p_250938_, SoundEvent p_252209_, SoundSource p_249161_, float p_249980_, float p_250277_, boolean p_250151_) {
        this.playLocalSound(
            (double)p_250938_.getX() + 0.5,
            (double)p_250938_.getY() + 0.5,
            (double)p_250938_.getZ() + 0.5,
            p_252209_,
            p_249161_,
            p_249980_,
            p_250277_,
            p_250151_
        );
    }

    public void playLocalSound(Entity p_312189_, SoundEvent p_312080_, SoundSource p_312905_, float p_312914_, float p_312831_) {
    }

    public void playLocalSound(
        double p_46482_, double p_46483_, double p_46484_, SoundEvent p_46485_, SoundSource p_46486_, float p_46487_, float p_46488_, boolean p_46489_
    ) {
    }

    @Override
    public void addParticle(ParticleOptions p_46631_, double p_46632_, double p_46633_, double p_46634_, double p_46635_, double p_46636_, double p_46637_) {
    }

    public void addParticle(
        ParticleOptions p_46638_, boolean p_46639_, double p_46640_, double p_46641_, double p_46642_, double p_46643_, double p_46644_, double p_46645_
    ) {
    }

    public void addAlwaysVisibleParticle(
        ParticleOptions p_46684_, double p_46685_, double p_46686_, double p_46687_, double p_46688_, double p_46689_, double p_46690_
    ) {
    }

    public void addAlwaysVisibleParticle(
        ParticleOptions p_46691_, boolean p_46692_, double p_46693_, double p_46694_, double p_46695_, double p_46696_, double p_46697_, double p_46698_
    ) {
    }

    public float getSunAngle(float p_46491_) {
        float f = this.getTimeOfDay(p_46491_);
        return f * (float) (Math.PI * 2);
    }

    public void addBlockEntityTicker(TickingBlockEntity p_151526_) {
        (this.tickingBlockEntities ? this.pendingBlockEntityTickers : this.blockEntityTickers).add(p_151526_);
    }

    public void addFreshBlockEntities(Collection<BlockEntity> beList) {
        if (this.tickingBlockEntities) {
            this.pendingFreshBlockEntities.addAll(beList);
        } else {
            this.freshBlockEntities.addAll(beList);
        }
    }

    protected void tickBlockEntities() {
        ProfilerFiller profilerfiller = this.getProfiler();
        profilerfiller.push("blockEntities");
        if (!this.pendingFreshBlockEntities.isEmpty()) {
            this.freshBlockEntities.addAll(this.pendingFreshBlockEntities);
            this.pendingFreshBlockEntities.clear();
        }
        this.tickingBlockEntities = true;
        if (!this.freshBlockEntities.isEmpty()) {
            this.freshBlockEntities.forEach(blockEntity -> {
                // Only call onLoad() on BEs which have been fully added to the level, prevents crashes with BEs that
                // were discarded due to incompatibility with the BlockState at their position
                if (!blockEntity.isRemoved() && blockEntity.hasLevel()) {
                    blockEntity.onLoad();
                }
            });
            this.freshBlockEntities.clear();
        }
        if (!this.pendingBlockEntityTickers.isEmpty()) {
            this.blockEntityTickers.addAll(this.pendingBlockEntityTickers);
            this.pendingBlockEntityTickers.clear();
        }

        Iterator<TickingBlockEntity> iterator = this.blockEntityTickers.iterator();
        boolean flag = this.tickRateManager().runsNormally();

        while (iterator.hasNext()) {
            TickingBlockEntity tickingblockentity = iterator.next();
            if (tickingblockentity.isRemoved()) {
                iterator.remove();
            } else if (flag && this.shouldTickBlocksAt(tickingblockentity.getPos())) {
                tickingblockentity.tick();
            }
        }

        this.tickingBlockEntities = false;
        profilerfiller.pop();
    }

    public <T extends Entity> void guardEntityTick(Consumer<T> p_46654_, T p_46655_) {
        try {
            TimeTracker.ENTITY_UPDATE.trackStart(p_46655_);
            p_46654_.accept(p_46655_);
        } catch (Throwable throwable) {
            if (throwable instanceof ThreadDeath) throw throwable; // Paper
            // Paper start - Prevent block entity and entity crashes
            final String msg = String.format("Entity threw exception at %s:%s,%s,%s", p_46655_.level().getWorld().getName(), p_46655_.getX(), p_46655_.getY(), p_46655_.getZ());
            MinecraftServer.LOGGER.error(msg, throwable);
            getCraftServer().getPluginManager().callEvent(new com.destroystokyo.paper.event.server.ServerExceptionEvent(new com.destroystokyo.paper.exception.ServerInternalException(msg, throwable))); // Paper - ServerExceptionEvent
            p_46655_.removeReason(org.bukkit.event.entity.EntityRemoveEvent.Cause.DISCARD);
            p_46655_.discard();
		} finally {
            net.neoforged.neoforge.server.timings.TimeTracker.ENTITY_UPDATE.trackEnd(p_46655_);
        }
    }

    public boolean shouldTickDeath(Entity p_186458_) {
        return true;
    }

    public boolean shouldTickBlocksAt(long p_186456_) {
        return true;
    }

    public boolean shouldTickBlocksAt(BlockPos p_220394_) {
        return this.shouldTickBlocksAt(ChunkPos.asLong(p_220394_));
    }

    public Explosion explode(
        @Nullable Entity p_256599_, double p_255914_, double p_255684_, double p_255843_, float p_256310_, Level.ExplosionInteraction p_256178_
    ) {
        return this.explode(
            p_256599_,
            Explosion.getDefaultDamageSource(this, p_256599_),
            null,
            p_255914_,
            p_255684_,
            p_255843_,
            p_256310_,
            false,
            p_256178_,
            ParticleTypes.EXPLOSION,
            ParticleTypes.EXPLOSION_EMITTER,
            SoundEvents.GENERIC_EXPLODE
        );
    }

    public Explosion explode(
        @Nullable Entity p_255682_,
        double p_255803_,
        double p_256403_,
        double p_256538_,
        float p_255674_,
        boolean p_256634_,
        Level.ExplosionInteraction p_256111_
    ) {
        return this.explode(
            p_255682_,
            Explosion.getDefaultDamageSource(this, p_255682_),
            null,
            p_255803_,
            p_256403_,
            p_256538_,
            p_255674_,
            p_256634_,
            p_256111_,
            ParticleTypes.EXPLOSION,
            ParticleTypes.EXPLOSION_EMITTER,
            SoundEvents.GENERIC_EXPLODE
        );
    }

    public Explosion explode(
        @Nullable Entity p_255653_,
        @Nullable DamageSource p_256558_,
        @Nullable ExplosionDamageCalculator p_255929_,
        Vec3 p_256001_,
        float p_255963_,
        boolean p_256099_,
        Level.ExplosionInteraction p_256371_
    ) {
        return this.explode(
            p_255653_,
            p_256558_,
            p_255929_,
            p_256001_.x(),
            p_256001_.y(),
            p_256001_.z(),
            p_255963_,
            p_256099_,
            p_256371_,
            ParticleTypes.EXPLOSION,
            ParticleTypes.EXPLOSION_EMITTER,
            SoundEvents.GENERIC_EXPLODE
        );
    }

    public Explosion explode(
        @Nullable Entity p_256145_,
        @Nullable DamageSource p_256004_,
        @Nullable ExplosionDamageCalculator p_255696_,
        double p_256208_,
        double p_256036_,
        double p_255746_,
        float p_256647_,
        boolean p_256098_,
        Level.ExplosionInteraction p_256104_
    ) {
        return this.explode(
            p_256145_,
            p_256004_,
            p_255696_,
            p_256208_,
            p_256036_,
            p_255746_,
            p_256647_,
            p_256098_,
            p_256104_,
            ParticleTypes.EXPLOSION,
            ParticleTypes.EXPLOSION_EMITTER,
            SoundEvents.GENERIC_EXPLODE
        );
    }

    public Explosion explode(
        @Nullable Entity p_256233_,
        @Nullable DamageSource p_255861_,
        @Nullable ExplosionDamageCalculator p_255867_,
        double p_256447_,
        double p_255732_,
        double p_255717_,
        float p_256013_,
        boolean p_256228_,
        Level.ExplosionInteraction p_255784_,
        ParticleOptions p_311886_,
        ParticleOptions p_311887_,
        Holder<SoundEvent> p_320084_
    ) {
        return this.explode(
            p_256233_, p_255861_, p_255867_, p_256447_, p_255732_, p_255717_, p_256013_, p_256228_, p_255784_, true, p_311886_, p_311887_, p_320084_
        );
    }

    public Explosion explode(
        @Nullable Entity p_311934_,
        @Nullable DamageSource p_312790_,
        @Nullable ExplosionDamageCalculator p_311975_,
        double p_312493_,
        double p_312456_,
        double p_312719_,
        float p_312292_,
        boolean p_312144_,
        Level.ExplosionInteraction p_312265_,
        boolean p_312145_,
        ParticleOptions p_312842_,
        ParticleOptions p_312060_,
        Holder<SoundEvent> p_320283_
    ) {
        Explosion.BlockInteraction explosion$blockinteraction = switch (p_312265_) {
            case NONE -> Explosion.BlockInteraction.KEEP;
            case BLOCK -> this.getDestroyType(GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY);
            case MOB -> net.neoforged.neoforge.event.EventHooks.canEntityGrief(this, p_311934_)
            ? this.getDestroyType(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY)
            : Explosion.BlockInteraction.KEEP;
            case TNT -> this.getDestroyType(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY);
            case TRIGGER -> Explosion.BlockInteraction.TRIGGER_BLOCK;
            case STANDARD -> Explosion.BlockInteraction.DESTROY;
        };
        Explosion explosion = new Explosion(
            this,
            p_311934_,
            p_312790_,
            p_311975_,
            p_312493_,
            p_312456_,
            p_312719_,
            p_312292_,
            p_312144_,
            explosion$blockinteraction,
            p_312842_,
            p_312060_,
            p_320283_
        );
        if (net.neoforged.neoforge.event.EventHooks.onExplosionStart(this, explosion)) return explosion;
        explosion.explode();
        explosion.finalizeExplosion(p_312145_);
        return explosion;
    }

    private Explosion.BlockInteraction getDestroyType(GameRules.Key<GameRules.BooleanValue> p_256250_) {
        return this.getGameRules().getBoolean(p_256250_) ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.DESTROY;
    }

    public abstract String gatherChunkSourceStats();

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos p_46716_) {
        if (this.isOutsideBuildHeight(p_46716_)) {
            return null;
        } else {
            return !this.isClientSide && !(Thread.currentThread() instanceof TickThread)
                ? null
                : this.getChunkAt(p_46716_).getBlockEntity(p_46716_, LevelChunk.EntityCreationType.IMMEDIATE);
        }
    }

    @Nullable
    public BlockEntity getBlockEntity(BlockPos p_46716_, boolean validate) {
      return getBlockEntity(p_46716_);
    }

    public void setBlockEntity(BlockEntity p_151524_) {
        BlockPos blockpos = p_151524_.getBlockPos();

        if (!this.isOutsideBuildHeight(blockpos)) {
            // CraftBukkit start
            if (captureBlockStates) {
                capturedTileEntities.put(blockpos.immutable(), p_151524_);
                // return; // Youer
            }
            // CraftBukkit end
            this.getChunkAt(blockpos).addAndRegisterBlockEntity(p_151524_);
        }
    }

    public void removeBlockEntity(BlockPos p_46748_) {
        if (!this.isOutsideBuildHeight(p_46748_)) {
            this.getChunkAt(p_46748_).removeBlockEntity(p_46748_);
        }
        this.updateNeighbourForOutputSignal(p_46748_, getBlockState(p_46748_).getBlock()); //Notify neighbors of changes
    }

    public boolean isLoaded(BlockPos p_46750_) {
        return this.isOutsideBuildHeight(p_46750_)
            ? false
            : this.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(p_46750_.getX()), SectionPos.blockToSectionCoord(p_46750_.getZ()));
    }

    public boolean loadedAndEntityCanStandOnFace(BlockPos p_46579_, Entity p_46580_, Direction p_46581_) {
        if (this.isOutsideBuildHeight(p_46579_)) {
            return false;
        } else {
            ChunkAccess chunkaccess = this.getChunk(
                SectionPos.blockToSectionCoord(p_46579_.getX()), SectionPos.blockToSectionCoord(p_46579_.getZ()), ChunkStatus.FULL, false
            );
            return chunkaccess == null ? false : chunkaccess.getBlockState(p_46579_).entityCanStandOnFace(this, p_46579_, p_46580_, p_46581_);
        }
    }

    public boolean loadedAndEntityCanStandOn(BlockPos p_46576_, Entity p_46577_) {
        return this.loadedAndEntityCanStandOnFace(p_46576_, p_46577_, Direction.UP);
    }

    public void updateSkyBrightness() {
        double d0 = 1.0 - (double)(this.getRainLevel(1.0F) * 5.0F) / 16.0;
        double d1 = 1.0 - (double)(this.getThunderLevel(1.0F) * 5.0F) / 16.0;
        double d2 = 0.5 + 2.0 * Mth.clamp((double)Mth.cos(this.getTimeOfDay(1.0F) * (float) (Math.PI * 2)), -0.25, 0.25);
        this.skyDarken = (int)((1.0 - d2 * d0 * d1) * 11.0);
    }

    public void setSpawnSettings(boolean p_46704_, boolean p_46705_) {
        this.getChunkSource().setSpawnSettings(p_46704_, p_46705_);
    }

    public BlockPos getSharedSpawnPos() {
        BlockPos blockpos = this.levelData.getSpawnPos();
        if (!this.getWorldBorder().isWithinBounds(blockpos)) {
            blockpos = this.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(this.getWorldBorder().getCenterX(), 0.0, this.getWorldBorder().getCenterZ())
            );
        }

        return blockpos;
    }

    public float getSharedSpawnAngle() {
        return this.levelData.getSpawnAngle();
    }

    protected void prepareWeather() {
        if (this.levelData.isRaining()) {
            this.rainLevel = 1.0F;
            if (this.levelData.isThundering()) {
                this.thunderLevel = 1.0F;
            }
        }
    }

    @Override
    public void close() throws IOException {
        this.getChunkSource().close();
    }

    @Nullable
    @Override
    public BlockGetter getChunkForCollisions(int p_46711_, int p_46712_) {
        return this.getChunk(p_46711_, p_46712_, ChunkStatus.FULL, false);
    }

    @Override
    public List<Entity> getEntities(@Nullable Entity p_46536_, AABB p_46537_, Predicate<? super Entity> p_46538_) {
        this.getProfiler().incrementCounter("getEntities");
        List<Entity> list = Lists.newArrayList();
        this.getEntities().get(p_46537_, p_151522_ -> {
            if (p_151522_ != p_46536_ && p_46538_ != null && p_46538_.test(p_151522_)) {
                list.add(p_151522_);
            }

            if (false)
            if (p_151522_ instanceof EnderDragon) {
                for (EnderDragonPart enderdragonpart : ((EnderDragon)p_151522_).getSubEntities()) {
                    if (p_151522_ != p_46536_ && p_46538_ != null && p_46538_.test(enderdragonpart)) {
                        list.add(enderdragonpart);
                    }
                }
            }
        });
        for (PartEntity<?> p : this.getPartEntities()) {
            if (p != p_46536_ && p.getBoundingBox().intersects(p_46537_) && p_46538_ != null && p_46538_.test(p)) {
                list.add(p);
            }
        }
        return list;
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> p_151528_, AABB p_151529_, Predicate<? super T> p_151530_) {
        List<T> list = Lists.newArrayList();
        this.getEntities(p_151528_, p_151529_, p_151530_, list);
        return list;
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> p_261899_, AABB p_261837_, Predicate<? super T> p_261519_, List<? super T> p_262046_) {
        this.getEntities(p_261899_, p_261837_, p_261519_, p_262046_, Integer.MAX_VALUE);
    }

    public <T extends Entity> void getEntities(
        EntityTypeTest<Entity, T> p_261885_, AABB p_262086_, Predicate<? super T> p_261688_, List<? super T> p_262071_, int p_261858_
    ) {
        this.getProfiler().incrementCounter("getEntities");
        this.getEntities().get(p_261885_, p_262086_, p_261454_ -> {
            if (p_261688_.test(p_261454_)) {
                p_262071_.add(p_261454_);
                if (p_262071_.size() >= p_261858_) {
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
            }


            if (false)
            if (p_261454_ instanceof EnderDragon enderdragon) {
                for (EnderDragonPart enderdragonpart : enderdragon.getSubEntities()) {
                    T t = p_261885_.tryCast(enderdragonpart);
                    if (t != null && p_261688_.test(t)) {
                        p_262071_.add(t);
                        if (p_262071_.size() >= p_261858_) {
                            return AbortableIterationConsumer.Continuation.ABORT;
                        }
                    }
                }
            }

            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
        for (net.neoforged.neoforge.entity.PartEntity<?> p : this.getPartEntities()) {
            T t = p_261885_.tryCast(p);
            if (t != null && t.getBoundingBox().intersects(p_262086_) && p_261688_.test(t)) {
                p_262071_.add(t);
                if (p_262071_.size() >= p_261858_) {
                    break;
                }
            }
        }
    }

    @Nullable
    public abstract Entity getEntity(int p_46492_);

    public void blockEntityChanged(BlockPos p_151544_) {
        if (this.hasChunkAt(p_151544_)) {
            this.getChunkAt(p_151544_).setUnsaved(true);
        }
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    public void disconnect() {
    }

    public long getGameTime() {
        return this.levelData.getGameTime();
    }

    public long getDayTime() {
        return this.levelData.getDayTime();
    }

    public boolean mayInteract(Player p_46557_, BlockPos p_46558_) {
        return true;
    }

    public void broadcastEntityEvent(Entity p_46509_, byte p_46510_) {
    }

    public void broadcastDamageEvent(Entity p_270831_, DamageSource p_270361_) {
    }

    public void blockEvent(BlockPos p_46582_, Block p_46583_, int p_46584_, int p_46585_) {
        this.getBlockState(p_46582_).triggerEvent(this, p_46582_, p_46584_, p_46585_);
    }

    @Override
    public LevelData getLevelData() {
        return this.levelData;
    }

    public GameRules getGameRules() {
        return this.levelData.getGameRules();
    }

    public abstract TickRateManager tickRateManager();

    public float getThunderLevel(float p_46662_) {
        return Mth.lerp(p_46662_, this.oThunderLevel, this.thunderLevel) * this.getRainLevel(p_46662_);
    }

    public void setThunderLevel(float p_46708_) {
        float f = Mth.clamp(p_46708_, 0.0F, 1.0F);
        this.oThunderLevel = f;
        this.thunderLevel = f;
    }

    public float getRainLevel(float p_46723_) {
        return Mth.lerp(p_46723_, this.oRainLevel, this.rainLevel);
    }

    public void setRainLevel(float p_46735_) {
        float f = Mth.clamp(p_46735_, 0.0F, 1.0F);
        this.oRainLevel = f;
        this.rainLevel = f;
    }

    public boolean isThundering() {
        return this.dimensionType().hasSkyLight() && !this.dimensionType().hasCeiling() ? (double)this.getThunderLevel(1.0F) > 0.9 : false;
    }

    public boolean isRaining() {
        return (double)this.getRainLevel(1.0F) > 0.2;
    }

    public boolean isRainingAt(BlockPos p_46759_) {
        if (!this.isRaining()) {
            return false;
        } else if (!this.canSeeSky(p_46759_)) {
            return false;
        } else if (this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, p_46759_).getY() > p_46759_.getY()) {
            return false;
        } else {
            Biome biome = this.getBiome(p_46759_).value();
            return biome.getPrecipitationAt(p_46759_) == Biome.Precipitation.RAIN;
        }
    }

    @Nullable
    public abstract MapItemSavedData getMapData(MapId p_324234_);

    public abstract void setMapData(MapId p_324009_, MapItemSavedData p_151534_);

    public abstract MapId getFreeMapId();

    public void globalLevelEvent(int p_46665_, BlockPos p_46666_, int p_46667_) {
    }

    public CrashReportCategory fillReportDetails(CrashReport p_46656_) {
        CrashReportCategory crashreportcategory = p_46656_.addCategory("Affected level", 1);
        crashreportcategory.setDetail("All players", () -> this.players().size() + " total; " + this.players());
        crashreportcategory.setDetail("Chunk stats", this.getChunkSource()::gatherStats);
        crashreportcategory.setDetail("Level dimension", () -> this.dimension().location().toString());

        try {
            this.levelData.fillCrashReportCategory(crashreportcategory, this);
        } catch (Throwable throwable) {
            crashreportcategory.setDetailError("Level Data Unobtainable", throwable);
        }

        return crashreportcategory;
    }

    public abstract void destroyBlockProgress(int p_46506_, BlockPos p_46507_, int p_46508_);

    public void createFireworks(
        double p_46475_, double p_46476_, double p_46477_, double p_46478_, double p_46479_, double p_46480_, List<FireworkExplosion> p_332050_
    ) {
    }

    public abstract Scoreboard getScoreboard();

    public void updateNeighbourForOutputSignal(BlockPos p_46718_, Block p_46719_) {
        for(Direction direction : Direction.values()) {
            BlockPos blockpos = p_46718_.relative(direction);
            if (ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor((ServerLevel)this, blockpos) && this.hasChunkAt(blockpos)) { // Folia - prevent block updates in non-loaded or non-owned chunks
                BlockState blockstate = this.getBlockState(blockpos);
                blockstate.onNeighborChange(this, blockpos, p_46718_);
                if (blockstate.isRedstoneConductor(this, blockpos)) {
                    blockpos = blockpos.relative(direction);
                    blockstate = this.getBlockState(blockpos);
                    if (blockstate.getWeakChanges(this, blockpos)) {
                        this.neighborChanged(blockstate, blockpos, p_46719_, p_46718_, false);
                    }
                }
            }
        }
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos p_46730_) {
        long i = 0L;
        float f = 0.0F;
        if (this.hasChunkAt(p_46730_)) {
            f = this.getMoonBrightness();
            i = this.getChunkAt(p_46730_).getInhabitedTime();
        }

        return new DifficultyInstance(this.getDifficulty(), this.getDayTime(), i, f);
    }

    @Override
    public int getSkyDarken() {
        return this.skyDarken;
    }

    public void setSkyFlashTime(int p_46709_) {
    }

    @Override
    public WorldBorder getWorldBorder() {
        return this.worldBorder;
    }

    public void sendPacketToServer(Packet<?> p_46657_) {
        throw new UnsupportedOperationException("Can't send packets to server unless you're on the client.");
    }

    @Override
    public DimensionType dimensionType() {
        return this.dimensionTypeRegistration.value();
    }

    public Holder<DimensionType> dimensionTypeRegistration() {
        return this.dimensionTypeRegistration;
    }

    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    @Override
    public RandomSource getRandom() {
        return this.random;
    }

    @Override
    public boolean isStateAtPosition(BlockPos p_46620_, Predicate<BlockState> p_46621_) {
        return p_46621_.test(this.getBlockState(p_46620_));
    }

    @Override
    public boolean isFluidAtPosition(BlockPos p_151541_, Predicate<FluidState> p_151542_) {
        return p_151542_.test(this.getFluidState(p_151541_));
    }

    public abstract RecipeManager getRecipeManager();

    public BlockPos getBlockRandomPos(int p_46497_, int p_46498_, int p_46499_, int p_46500_) {
        this.randValue = this.randValue * 3 + 1013904223;
        int i = this.randValue >> 2;
        return new BlockPos(p_46497_ + (i & 15), p_46498_ + (i >> 16 & p_46500_), p_46499_ + (i >> 8 & 15));
    }

    public boolean noSave() {
        return false;
    }

    public ProfilerFiller getProfiler() {
        if (gg.pufferfish.pufferfish.PufferfishConfig.disableMethodProfiler) return net.minecraft.util.profiling.InactiveProfiler.INSTANCE; // Pufferfish
        return this.profiler.get();
    }

    public Supplier<ProfilerFiller> getProfilerSupplier() {
        return this.profiler;
    }

    @Override
    public BiomeManager getBiomeManager() {
        return this.biomeManager;
    }

    private double maxEntityRadius = 2.0D;
    @Override
    public double getMaxEntityRadius() {
        return maxEntityRadius;
    }
    @Override
    public double increaseMaxEntityRadius(double value) {
        if (value > maxEntityRadius)
            maxEntityRadius = value;
        return maxEntityRadius;
    }

    public final boolean isDebug() {
        return this.isDebug;
    }

    public abstract LevelEntityGetter<Entity> getEntities();

    @Override
    public long nextSubTickCount() {
        return this.subTickCount++;
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.registryAccess;
    }

    public DamageSources damageSources() {
        return this.damageSources;
    }

    public abstract PotionBrewing potionBrewing();

    public static enum ExplosionInteraction implements StringRepresentable {
        NONE("none"),
        BLOCK("block"),
        MOB("mob"),
        TNT("tnt"),
        TRIGGER("trigger"),
        STANDARD("standard"); // CraftBukkit - Add STANDARD which will always use Explosion.Effect.DESTROY;

        public static final Codec<Level.ExplosionInteraction> CODEC = StringRepresentable.fromEnum(Level.ExplosionInteraction::values);
        private final String id;

        private ExplosionInteraction(String p_346300_) {
            this.id = p_346300_;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }
    }

    // Neo: Variable day time code

    @org.jetbrains.annotations.ApiStatus.Internal
    public abstract void setDayTimeFraction(float dayTimeFraction);

    @org.jetbrains.annotations.ApiStatus.Internal
    public abstract float getDayTimeFraction();

    /**
     * Returns the current ratio between game ticks and clock ticks. If this value is negative, no
     * speed has been set and those two are coupled 1:1 (i.e. vanilla mode).
     */
    public abstract float getDayTimePerTick();

    /**
     * DO NOT CALL.
     * <p>
     * Use {@link net.minecraft.server.level.ServerLevel#setDayTimePerTick(float)} instead.
     */
    public abstract void setDayTimePerTick(float dayTimePerTick);

    // advances the fractional daytime, returns the integer part of it
    @org.jetbrains.annotations.ApiStatus.Internal
    protected long advanceDaytime() {
        if (getDayTimePerTick() < 0) {
            return 1L; // avoid doing math (and rounding errors) if no speed has been set
        }
        float dayTimeStep = getDayTimeFraction() + getDayTimePerTick();
        long result = (long)dayTimeStep;
        setDayTimeFraction(dayTimeStep - result);
        return result;
    }

    // Folia start - region threading
    public io.papermc.paper.threadedregions.RegionizedWorldData getCurrentWorldData() {
        final io.papermc.paper.threadedregions.RegionizedWorldData ret = io.papermc.paper.threadedregions.TickRegionScheduler.getCurrentRegionizedWorldData();
        if (ret == null) {
            return ret;
        }
        Level world = ret.world;
        if (world != this) {
            throw new IllegalStateException("World mismatch: expected " + this.getWorld().getName() + " but got " + world.getWorld().getName());
        }
        return ret;
    }
    // Folia end - region threading
}
