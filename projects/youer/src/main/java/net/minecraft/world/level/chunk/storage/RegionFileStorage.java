package net.minecraft.world.level.chunk.storage;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.util.ExceptionCollector;
import net.minecraft.world.level.ChunkPos;

public class RegionFileStorage implements AutoCloseable, ca.spottedleaf.moonrise.patches.chunk_system.io.ChunkSystemRegionFileStorage { // Paper - rewrite chunk system
    public static final String ANVIL_EXTENSION = ".mca";
    private static final int MAX_CACHE_SIZE = 256;
    public final Long2ObjectLinkedOpenHashMap<RegionFile> regionCache = new Long2ObjectLinkedOpenHashMap<>();
    private final RegionStorageInfo info;
    private final Path folder;
    private final boolean sync;

    // Paper start - rewrite chunk system
    private static final int REGION_SHIFT = 5;
    private static final int MAX_NON_EXISTING_CACHE = 1024 * 64;
    private final it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet nonExistingRegionFiles = new it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet(MAX_NON_EXISTING_CACHE+1);
    private static String getRegionFileName(final int chunkX, final int chunkZ) {
        return "r." + (chunkX >> REGION_SHIFT) + "." + (chunkZ >> REGION_SHIFT) + ".mca";
    }

    private boolean doesRegionFilePossiblyExist(final long position) {
        synchronized (this.nonExistingRegionFiles) {
            if (this.nonExistingRegionFiles.contains(position)) {
                this.nonExistingRegionFiles.addAndMoveToFirst(position);
                return false;
            }
            return true;
        }
    }

    private void createRegionFile(final long position) {
        synchronized (this.nonExistingRegionFiles) {
            this.nonExistingRegionFiles.remove(position);
        }
    }

    private void markNonExisting(final long position) {
        synchronized (this.nonExistingRegionFiles) {
            if (this.nonExistingRegionFiles.addAndMoveToFirst(position)) {
                while (this.nonExistingRegionFiles.size() >= MAX_NON_EXISTING_CACHE) {
                    this.nonExistingRegionFiles.removeLastLong();
                }
            }
        }
    }

    @Override
    public final boolean moonrise$doesRegionFileNotExistNoIO(final int chunkX, final int chunkZ) {
        return !this.doesRegionFilePossiblyExist(ChunkPos.asLong(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT));
    }

    @Override
    public synchronized final RegionFile moonrise$getRegionFileIfLoaded(final int chunkX, final int chunkZ) {
        return this.regionCache.getAndMoveToFirst(ChunkPos.asLong(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT));
    }

    @Override
    public synchronized final RegionFile moonrise$getRegionFileIfExists(final int chunkX, final int chunkZ) throws IOException {
        final long key = ChunkPos.asLong(chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT);

        RegionFile ret = this.regionCache.getAndMoveToFirst(key);
        if (ret != null) {
            return ret;
        }

        if (!this.doesRegionFilePossiblyExist(key)) {
            return null;
        }

        if (this.regionCache.size() >= io.papermc.paper.configuration.GlobalConfiguration.get().misc.regionFileCacheSize) { // Paper
            this.regionCache.removeLast().close();
        }

        final Path regionPath = this.folder.resolve(getRegionFileName(chunkX, chunkZ));

        if (!java.nio.file.Files.exists(regionPath)) {
            this.markNonExisting(key);
            return null;
        }

        this.createRegionFile(key);

        FileUtil.createDirectoriesSafe(this.folder);

        ret = new RegionFile(this.info, regionPath, this.folder, this.sync);

        this.regionCache.putAndMoveToFirst(key, ret);

        return ret;
    }
    // Paper end - rewrite chunk system

    public RegionFileStorage(RegionStorageInfo p_326161_, Path p_196954_, boolean p_196955_) {
        this.folder = p_196954_;
        this.sync = p_196955_;
        this.info = p_326161_;
    }

    public RegionFile getRegionFile(ChunkPos p_63712_) throws IOException {
        return this.getRegionFile(p_63712_, true); // Folia - region threading - delegate to overloaded method
    }

    // Folia start - region threading - allow retrieving region file without creating it
    public RegionFile getRegionFile(final ChunkPos pos, final boolean create) throws IOException {
        if (!create) {
            return this.moonrise$getRegionFileIfExists(pos.x, pos.z); // Paper - rewrite chunk system
        }
        synchronized (this) {
            final long key = ChunkPos.asLong(pos.x >> REGION_SHIFT, pos.z >> REGION_SHIFT); // Paper - rewrite chunk system

            RegionFile ret = this.regionCache.getAndMoveToFirst(key);
            if (ret != null) {
                return ret;
            }

            if (this.regionCache.size() >= io.papermc.paper.configuration.GlobalConfiguration.get().misc.regionFileCacheSize) { // Paper - Sanitise RegionFileCache and make configurable
                this.regionCache.removeLast().close();
            }

            FileUtil.createDirectoriesSafe(this.folder);
            final Path regionPath = this.folder.resolve(getRegionFileName(pos.x, pos.z)); // Paper - rewrite chunk system
            this.createRegionFile(key); // Paper - rewrite chunk system

            ret = new RegionFile(this.info, regionPath, this.folder, this.sync);
            this.regionCache.putAndMoveToFirst(key, ret);
            return ret;
        }
    }
    // Folia end - region threading - allow retrieving region file without creating it

    // Folia start - region threading - exception for oversized chunk data
    public static final class RegionFileSizeException extends RuntimeException {
        public RegionFileSizeException(String msg) {
            super(msg);
        }
    }
    // Folia end - region threading - exception for oversized chunk data

    @Nullable
    public CompoundTag read(ChunkPos p_63707_) throws IOException {
        RegionFile regionfile = this.getRegionFile(p_63707_);

        CompoundTag compoundtag;
        try (DataInputStream datainputstream = regionfile.getChunkDataInputStream(p_63707_)) {
            if (datainputstream == null) {
                return null;
            }

            compoundtag = NbtIo.read(datainputstream);
        }

        return compoundtag;
    }

    public void scanChunk(ChunkPos p_196957_, StreamTagVisitor p_196958_) throws IOException {
        RegionFile regionfile = this.getRegionFile(p_196957_);

        try (DataInputStream datainputstream = regionfile.getChunkDataInputStream(p_196957_)) {
            if (datainputstream != null) {
                NbtIo.parse(datainputstream, p_196958_, NbtAccounter.unlimitedHeap());
            }
        }
    }

    public void write(ChunkPos p_63709_, @Nullable CompoundTag p_63710_) throws IOException {
        RegionFile regionfile = this.getRegionFile(p_63709_);
        if (p_63710_ == null) {
            regionfile.clear(p_63709_);
        } else {
            try (DataOutputStream dataoutputstream = regionfile.getChunkDataOutputStream(p_63709_)) {
                NbtIo.write(p_63710_, dataoutputstream);
            }
        }
    }

    @Override
    public void close() throws IOException {
        // Paper start - rewrite chunk system
        synchronized (this) {
            ExceptionCollector<IOException> exceptioncollector = new ExceptionCollector<>();

            for (RegionFile regionfile : this.regionCache.values()) {
                try {
                    regionfile.close();
                } catch (IOException ioexception) {
                    exceptioncollector.add(ioexception);
                }
            }

            exceptioncollector.throwIfPresent();
        }
        // Paper end - rewrite chunk system
    }

    public void flush() throws IOException {
        // Paper start - rewrite chunk system
        synchronized (this) {
            ExceptionCollector<IOException> exceptioncollector = new ExceptionCollector<>();

            for (RegionFile regionfile : this.regionCache.values()) {
                try {
                    regionfile.flush();
                } catch (IOException ioexception) {
                    exceptioncollector.add(ioexception);
                }
            }

            exceptioncollector.throwIfPresent();
        }
        // Paper end - rewrite chunk system
    }

    public RegionStorageInfo info() {
        return this.info;
    }
}
