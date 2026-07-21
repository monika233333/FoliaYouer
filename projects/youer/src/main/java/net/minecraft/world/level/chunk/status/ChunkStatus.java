package net.minecraft.world.level.chunk.status;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.VisibleForTesting;

public class ChunkStatus implements ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkSystemChunkStatus { // Paper - rewrite chunk system
    public static final int MAX_STRUCTURE_DISTANCE = 8;
    private static final EnumSet<Heightmap.Types> WORLDGEN_HEIGHTMAPS = EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG);
    public static final EnumSet<Heightmap.Types> FINAL_HEIGHTMAPS = EnumSet.of(
        Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE, Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
    );
    public static final ChunkStatus EMPTY = register("empty", null, WORLDGEN_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus STRUCTURE_STARTS = register("structure_starts", EMPTY, WORLDGEN_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus STRUCTURE_REFERENCES = register("structure_references", STRUCTURE_STARTS, WORLDGEN_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus BIOMES = register("biomes", STRUCTURE_REFERENCES, WORLDGEN_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus NOISE = register("noise", BIOMES, WORLDGEN_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus SURFACE = register("surface", NOISE, WORLDGEN_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus CARVERS = register("carvers", SURFACE, FINAL_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus FEATURES = register("features", CARVERS, FINAL_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus INITIALIZE_LIGHT = register("initialize_light", FEATURES, FINAL_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus LIGHT = register("light", INITIALIZE_LIGHT, FINAL_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus SPAWN = register("spawn", LIGHT, FINAL_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus FULL = register("full", SPAWN, FINAL_HEIGHTMAPS, ChunkType.LEVELCHUNK);
    private final int index;
    private final ChunkStatus parent;
    private final ChunkType chunkType;
    private final EnumSet<Heightmap.Types> heightmapsAfter;

    // Paper start - rewrite chunk system
    private boolean isParallelCapable;
    private boolean emptyLoadTask;
    private int writeRadius;
    private ChunkStatus nextStatus;
    private java.util.concurrent.atomic.AtomicBoolean warnedAboutNoImmediateComplete;

    @Override
    public final boolean moonrise$isParallelCapable() {
        return this.isParallelCapable;
    }

    @Override
    public final void moonrise$setParallelCapable(final boolean value) {
        this.isParallelCapable = value;
    }

    @Override
    public final int moonrise$getWriteRadius() {
        return this.writeRadius;
    }

    @Override
    public final void moonrise$setWriteRadius(final int value) {
        this.writeRadius = value;
    }

    @Override
    public final ChunkStatus moonrise$getNextStatus() {
        return this.nextStatus;
    }

    @Override
    public final boolean moonrise$isEmptyLoadStatus() {
        return this.emptyLoadTask;
    }

    @Override
    public void moonrise$setEmptyLoadStatus(final boolean value) {
        this.emptyLoadTask = value;
    }

    @Override
    public final boolean moonrise$isEmptyGenStatus() {
        return (Object)this == ChunkStatus.EMPTY;
    }

    @Override
    public final java.util.concurrent.atomic.AtomicBoolean moonrise$getWarnedAboutNoImmediateComplete() {
        return this.warnedAboutNoImmediateComplete;
    }
    // Paper end - rewrite chunk system

    private static ChunkStatus register(String p_330494_, @Nullable ChunkStatus p_331829_, EnumSet<Heightmap.Types> p_330717_, ChunkType p_331982_) {
        return Registry.register(BuiltInRegistries.CHUNK_STATUS, p_330494_, new ChunkStatus(p_331829_, p_330717_, p_331982_));
    }

    public static List<ChunkStatus> getStatusList() {
        List<ChunkStatus> list = Lists.newArrayList();

        ChunkStatus chunkstatus;
        for (chunkstatus = FULL; chunkstatus.getParent() != chunkstatus; chunkstatus = chunkstatus.getParent()) {
            list.add(chunkstatus);
        }

        list.add(chunkstatus);
        Collections.reverse(list);
        return list;
    }

    @VisibleForTesting
    protected ChunkStatus(@Nullable ChunkStatus p_330316_, EnumSet<Heightmap.Types> p_331442_, ChunkType p_331412_) {
        // Paper start - rewrite chunk system
        this.isParallelCapable = false;
        this.writeRadius = -1;
        this.nextStatus = (ChunkStatus)(Object)this;
        if (p_330316_ != null) {
            p_330316_.nextStatus = (ChunkStatus)(Object)this;
        }
        this.warnedAboutNoImmediateComplete = new java.util.concurrent.atomic.AtomicBoolean();
        // Paper end - rewrite chunk system
        this.parent = p_330316_ == null ? this : p_330316_;
        this.chunkType = p_331412_;
        this.heightmapsAfter = p_331442_;
        this.index = p_330316_ == null ? 0 : p_330316_.getIndex() + 1;
        EnumSet<Heightmap.Types> chunkSaveHeightmaps = EnumSet.copyOf(this.heightmapsAfter);
        if (this.chunkType != ChunkType.LEVELCHUNK) {
            chunkSaveHeightmaps.add(Heightmap.Types.WORLD_SURFACE_WG);
            chunkSaveHeightmaps.add(Heightmap.Types.OCEAN_FLOOR_WG);
        }
        this.chunkSaveHeightmaps = chunkSaveHeightmaps;
    }

    public int getIndex() {
        return this.index;
    }

    public ChunkStatus getParent() {
        return this.parent;
    }

    public ChunkType getChunkType() {
        return this.chunkType;
    }

    public static ChunkStatus byName(String p_330923_) {
        return BuiltInRegistries.CHUNK_STATUS.get(ResourceLocation.tryParse(p_330923_));
    }

    public EnumSet<Heightmap.Types> heightmapsAfter() {
        return this.heightmapsAfter;
    }

    public boolean isOrAfter(ChunkStatus p_330216_) {
        return this.getIndex() >= p_330216_.getIndex();
    }

    public boolean isAfter(ChunkStatus p_347553_) {
        return this.getIndex() > p_347553_.getIndex();
    }

    public boolean isOrBefore(ChunkStatus p_347528_) {
        return this.getIndex() <= p_347528_.getIndex();
    }

    public boolean isBefore(ChunkStatus p_347551_) {
        return this.getIndex() < p_347551_.getIndex();
    }

    public static ChunkStatus max(ChunkStatus p_347651_, ChunkStatus p_347554_) {
        return p_347651_.isAfter(p_347554_) ? p_347651_ : p_347554_;
    }

    @Override
    public String toString() {
        return this.getName();
    }

    public String getName() {
        return BuiltInRegistries.CHUNK_STATUS.getKey(this).toString();
    }

    /**
     * Neo: Internal use only.
     * Patch to fix [MC-308222](https://report.bugs.mojang.com/servicedesk/customer/portal/2/MC-308222)
     * intended for loading a non-fully generated chunk from disk to keep worldgen heightmap data for
     * structures and features to heightmap snap properly again.
     * @return A set of the chunk status's heightmaps alongside _WG heightmaps for worldgen-only chunk statuses
     */
    public EnumSet<Heightmap.Types> getChunkSaveHeightmaps() {
        return this.chunkSaveHeightmaps;
    }
    private final EnumSet<Heightmap.Types> chunkSaveHeightmaps;
}
