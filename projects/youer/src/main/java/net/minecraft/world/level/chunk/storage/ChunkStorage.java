package net.minecraft.world.level.chunk.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.LegacyStructureDataHandler;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class ChunkStorage implements AutoCloseable, ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemChunkStorage { // Paper - rewrite chunk system

    public static final int LAST_MONOLYTH_STRUCTURE_DATA_VERSION = 1493;
    // Paper - rewrite chunk system
    protected final DataFixer fixerUpper;
    @Nullable
    private volatile LegacyStructureDataHandler legacyStructureHandler;

    // Paper start - rewrite chunk system
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final RegionFileStorage storage;

    @Override
    public final RegionFileStorage moonrise$getRegionStorage() {
        return this.storage;
    }
    // Paper end - rewrite chunk system

    public ChunkStorage(RegionStorageInfo p_326130_, Path p_196912_, DataFixer p_196913_, boolean p_196914_) {
        this.fixerUpper = p_196913_;
        this.storage = new IOWorker(p_326130_, p_196912_, p_196914_).storage; // Paper - rewrite chunk system
    }

    public boolean isOldChunkAround(ChunkPos p_223452_, int p_223453_) {
        return true; // Paper - rewrite chunk system
    }

    // CraftBukkit start
    private boolean check(net.minecraft.server.level.ServerChunkCache cps, int x, int z) {
        if (true) return true; // Paper - Perf: this isn't even needed anymore, light is purged updating to 1.14+, why are we holding up the conversion process reading chunk data off disk - return true, we need to set light populated to true so the converter recognizes the chunk as being "full"
        ChunkPos pos = new ChunkPos(x, z);
        if (cps != null) {
            com.google.common.base.Preconditions.checkState(org.bukkit.Bukkit.isPrimaryThread(), "primary thread");
            if (cps.hasChunk(x, z)) {
                return true;
            }
        }

        CompoundTag nbt;
        try {
            nbt = read(pos).get().orElse(null);
        } catch (InterruptedException | java.util.concurrent.ExecutionException ex) {
            throw new RuntimeException(ex);
        }
        if (nbt != null) {
            CompoundTag level = nbt.getCompound("Level");
            if (level.getBoolean("TerrainPopulated")) {
                return true;
            }

            net.minecraft.world.level.chunk.status.ChunkStatus status = net.minecraft.world.level.chunk.status.ChunkStatus.byName(level.getString("Status"));
            if (status != null && status.isOrAfter(net.minecraft.world.level.chunk.status.ChunkStatus.FEATURES)) {
                return true;
            }
        }

        return false;
    }
    // CraftBukkit end
    public ChunkPos pos; // CraftBukkit
    public @Nullable LevelAccessor generatoraccess; // CraftBukkit

    public CompoundTag upgradeChunkTagCB(ResourceKey<Level> p_188289_,
                                       Supplier<DimensionDataStorage> p_188290_,
                                       CompoundTag p_188281_,
                                       Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> p_188292_, ChunkPos pos, @Nullable LevelAccessor generatoraccess) {
        this.pos = pos;
        this.generatoraccess = generatoraccess;
        return this.upgradeChunkTag(p_188289_, p_188290_, p_188281_, p_188292_);
    }

    public CompoundTag upgradeChunkTag(
        ResourceKey<Level> p_188289_,
        Supplier<DimensionDataStorage> p_188290_,
        CompoundTag p_188281_,
        Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> p_188292_
    ) {
        int i = getVersion(p_188281_);
        if (i == SharedConstants.getCurrentVersion().getDataVersion().getVersion()) {
            return p_188281_;
        } else {
            try {
                // CraftBukkit start
                if (i < 1466 && pos != null) { // Paper - no longer needed, data converter system / DFU handles it now
                    CompoundTag level = p_188281_.getCompound("Level");
                    if (level.getBoolean("TerrainPopulated") && !level.getBoolean("LightPopulated")) {
                        ServerChunkCache cps = (generatoraccess == null) ? null : ((ServerLevel) generatoraccess).getChunkSource();
                        if (this.check(cps, pos.x - 1, pos.z) && this.check(cps, pos.x - 1, pos.z - 1) && this.check(cps, pos.x, pos.z - 1)) {
                            level.putBoolean("LightPopulated", true);
                        }
                    }
                }
                // CraftBukkit end


                if (i < 1493) {
                    p_188281_ = DataFixTypes.CHUNK.update(this.fixerUpper, p_188281_, i, 1493);
                    if (p_188281_.getCompound("Level").getBoolean("hasLegacyStructureData")) {
                        LegacyStructureDataHandler legacystructuredatahandler = this.getLegacyStructureHandler(p_188289_, p_188290_);
                        synchronized (legacystructuredatahandler) { // Paper - rewrite chunk system
                        p_188281_ = legacystructuredatahandler.updateFromLegacy(p_188281_);
                        } // Paper - rewrite chunk system
                    }
                }

                // Spigot start - SPIGOT-6806: Quick and dirty way to prevent below zero generation in old chunks, by setting the status to heightmap instead of empty
                boolean stopBelowZero = false;
                boolean belowZeroGenerationInExistingChunks = (generatoraccess != null) ? ((ServerLevel)generatoraccess).spigotConfig.belowZeroGenerationInExistingChunks : org.spigotmc.SpigotConfig.belowZeroGenerationInExistingChunks;
                generatoraccess = null;
                if (i <= 2730 && !belowZeroGenerationInExistingChunks) {
                    stopBelowZero = "full".equals(p_188281_.getCompound("Level").getString("Status"));
                }
                // Spigot end

                injectDatafixingContext(p_188281_, p_188289_, p_188292_);
                p_188281_ = DataFixTypes.CHUNK.updateToCurrentVersion(this.fixerUpper, p_188281_, Math.max(1493, i));
                // Spigot start
                if (stopBelowZero) {
                    p_188281_.putString("Status", net.minecraft.core.registries.BuiltInRegistries.CHUNK_STATUS.getKey(net.minecraft.world.level.chunk.status.ChunkStatus.SPAWN).toString());
                }
                // Spigot end
                removeDatafixingContext(p_188281_);
                NbtUtils.addCurrentDataVersion(p_188281_);
                return p_188281_;
            } catch (Exception exception) {
                CrashReport crashreport = CrashReport.forThrowable(exception, "Updated chunk");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Updated chunk details");
                crashreportcategory.setDetail("Data version", i);
                throw new ReportedException(crashreport);
            }
        }
    }

    private LegacyStructureDataHandler getLegacyStructureHandler(ResourceKey<Level> p_223449_, Supplier<DimensionDataStorage> p_223450_) {
        LegacyStructureDataHandler legacystructuredatahandler = this.legacyStructureHandler;
        if (legacystructuredatahandler == null) {
            synchronized (this) {
                legacystructuredatahandler = this.legacyStructureHandler;
                if (legacystructuredatahandler == null) {
                    this.legacyStructureHandler = legacystructuredatahandler = LegacyStructureDataHandler.getLegacyStructureHandler(p_223449_, p_223450_.get());
                }
            }
        }

        return legacystructuredatahandler;
    }

    public static void injectDatafixingContext(
        CompoundTag p_196919_, ResourceKey<Level> p_196920_, Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> p_196921_
    ) {
        CompoundTag compoundtag = new CompoundTag();
        compoundtag.putString("dimension", p_196920_.location().toString());
        p_196921_.ifPresent(p_196917_ -> compoundtag.putString("generator", p_196917_.location().toString()));
        p_196919_.put("__context", compoundtag);
    }

    private static void removeDatafixingContext(CompoundTag p_348632_) {
        p_348632_.remove("__context");
    }

    public static int getVersion(CompoundTag p_63506_) {
        return NbtUtils.getDataVersion(p_63506_, -1);
    }

    public CompletableFuture<Optional<CompoundTag>> read(ChunkPos p_223455_) {
        // Paper start - rewrite chunk system
        try {
            return CompletableFuture.completedFuture(Optional.ofNullable(this.storage.read(p_223455_)));
        } catch (final Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
        // Paper end - rewrite chunk system
    }

    public CompletableFuture<Void> write(ChunkPos p_63503_, CompoundTag p_63504_) {
        // Paper start - guard against serializing mismatching coordinates
        if (p_63504_ != null && !p_63503_.equals(ChunkSerializer.getChunkCoordinate(p_63504_))) {
            final String world = (this instanceof net.minecraft.server.level.ChunkMap) ? ((net.minecraft.server.level.ChunkMap) this).level.getWorld().getName() : null;
            throw new IllegalArgumentException("Chunk coordinate and serialized data do not have matching coordinates, trying to serialize coordinate " + p_63503_
                + " but compound says coordinate is " + ChunkSerializer.getChunkCoordinate(p_63504_) + (world == null ? " for an unknown world" : (" for world: " + world)));
        }
        // Paper end - guard against serializing mismatching coordinates
        this.handleLegacyStructureIndex(p_63503_);
        // Paper start - rewrite chunk system
        try {
            this.storage.write(p_63503_, p_63504_);
            return CompletableFuture.completedFuture(null);
        } catch (final Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
        // Paper end - rewrite chunk system
    }

    protected void handleLegacyStructureIndex(ChunkPos p_321604_) {
        if (this.legacyStructureHandler != null) {
            synchronized (this.legacyStructureHandler) { // Paper - rewrite chunk system
            this.legacyStructureHandler.removeIndex(p_321604_.toLong());
            } // Paper - rewrite chunk system
        }
    }

    public void flushWorker() {
        // Paper start - rewrite chunk system
        try {
            this.storage.flush();
        } catch (final IOException ex) {
            LOGGER.error("Failed to flush chunk storage", ex);
        }
        // Paper end - rewrite chunk system
    }

    @Override
    public void close() throws IOException {
        this.storage.close(); // Paper - rewrite chunk system
    }

    public ChunkScanAccess chunkScanner() {
        // Paper start - rewrite chunk system
        return (chunkPos, streamTagVisitor) -> {
            try {
                this.storage.scanChunk(chunkPos, streamTagVisitor);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
        // Paper end - rewrite chunk system
    }

    public RegionStorageInfo storageInfo() {
        return this.storage.info(); // Paper - rewrite chunk system
    }
}
