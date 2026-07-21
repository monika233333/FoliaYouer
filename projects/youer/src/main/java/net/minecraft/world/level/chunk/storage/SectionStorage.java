package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.OptionalDynamic;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import org.slf4j.Logger;

public abstract class SectionStorage<R> implements AutoCloseable, ca.spottedleaf.moonrise.patches.chunk_system.level.storage.ChunkSystemSectionStorage { // Paper - rewrite chunk system
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SECTIONS_TAG = "Sections";
    private final SimpleRegionStorage simpleRegionStorage;
    private final Long2ObjectMap<Optional<R>> storage = new Long2ObjectOpenHashMap<>();
    private final LongLinkedOpenHashSet dirty = new LongLinkedOpenHashSet();
    private final Function<Runnable, Codec<R>> codec;
    private final Function<Runnable, R> factory;
    private final RegistryAccess registryAccess;
    private final ChunkIOErrorReporter errorReporter;
    protected final LevelHeightAccessor levelHeightAccessor;

    // Paper start - rewrite chunk system
    private final RegionFileStorage regionStorage;

    @Override
    public final RegionFileStorage moonrise$getRegionStorage() {
        return this.regionStorage;
    }
    // Paper end - rewrite chunk system

    public SectionStorage(
        SimpleRegionStorage p_321814_,
        Function<Runnable, Codec<R>> p_223510_,
        Function<Runnable, R> p_223511_,
        RegistryAccess p_223515_,
        ChunkIOErrorReporter p_352357_,
        LevelHeightAccessor p_223516_
    ) {
        this.simpleRegionStorage = p_321814_;
        this.codec = p_223510_;
        this.factory = p_223511_;
        this.registryAccess = p_223515_;
        this.errorReporter = p_352357_;
        this.levelHeightAccessor = p_223516_;
        this.regionStorage = p_321814_.worker.storage; // Paper - rewrite chunk system
    }

    protected void tick(BooleanSupplier p_63812_) {
        while (this.hasWork() && p_63812_.getAsBoolean()) {
            ChunkPos chunkpos = SectionPos.of(this.dirty.firstLong()).chunk();
            this.writeColumn(chunkpos);
        }
    }

    public boolean hasWork() {
        return !this.dirty.isEmpty();
    }

    @Nullable
    protected Optional<R> get(long p_63819_) {
        return this.storage.get(p_63819_);
    }

    protected Optional<R> getOrLoad(long p_63824_) {
        if (this.outsideStoredRange(p_63824_)) {
            return Optional.empty();
        } else {
            Optional<R> optional = this.get(p_63824_);
            if (optional != null) {
                return optional;
            } else {
                this.readColumn(SectionPos.of(p_63824_).chunk());
                optional = this.get(p_63824_);
                if (optional == null) {
                    throw (IllegalStateException)Util.pauseInIde(new IllegalStateException());
                } else {
                    return optional;
                }
            }
        }
    }

    protected boolean outsideStoredRange(long p_156631_) {
        int i = SectionPos.sectionToBlockCoord(SectionPos.y(p_156631_));
        return this.levelHeightAccessor.isOutsideBuildHeight(i);
    }

    protected R getOrCreate(long p_63828_) {
        if (this.outsideStoredRange(p_63828_)) {
            throw (IllegalArgumentException)Util.pauseInIde(new IllegalArgumentException("sectionPos out of bounds"));
        } else {
            Optional<R> optional = this.getOrLoad(p_63828_);
            if (optional.isPresent()) {
                return optional.get();
            } else {
                R r = this.factory.apply(() -> this.setDirty(p_63828_));
                this.storage.put(p_63828_, Optional.of(r));
                return r;
            }
        }
    }

    private void readColumn(ChunkPos p_63815_) {
        Optional<CompoundTag> optional = this.tryRead(p_63815_).join();
        RegistryOps<Tag> registryops = this.registryAccess.createSerializationContext(NbtOps.INSTANCE);
        this.readColumn(p_63815_, registryops, optional.orElse(null));
    }

    private CompletableFuture<Optional<CompoundTag>> tryRead(ChunkPos p_223533_) {
        // Paper start - rewrite chunk system
        try {
            return CompletableFuture.completedFuture(Optional.ofNullable(this.moonrise$read(p_223533_.x, p_223533_.z)));
        } catch (final Throwable thr) {
            return CompletableFuture.failedFuture(thr);
        }
        // Paper end - rewrite chunk system
    }

    private void readColumn(ChunkPos p_63802_, RegistryOps<Tag> p_321830_, @Nullable CompoundTag p_321530_) {
        throw new IllegalStateException("Only chunk system can load in state, offending class:" + this.getClass().getName()); // Paper - rewrite chunk system
    }

    private void writeColumn(ChunkPos p_63826_) {
        RegistryOps<Tag> registryops = this.registryAccess.createSerializationContext(NbtOps.INSTANCE);
        Dynamic<Tag> dynamic = this.writeColumn(p_63826_, registryops);
        Tag tag = dynamic.getValue();
        if (tag instanceof CompoundTag) {
            // Paper start - rewrite chunk system
            try {
                this.moonrise$write(p_63826_.x, p_63826_.z, (net.minecraft.nbt.CompoundTag)tag);
            } catch (final IOException ex) {
                LOGGER.error("Error writing poi chunk data to disk for chunk " + p_63826_, ex);
            }
            // Paper end - rewrite chunk system
        } else {
            LOGGER.error("Expected compound tag, got {}", tag);
        }
    }

    private <T> Dynamic<T> writeColumn(ChunkPos p_63799_, DynamicOps<T> p_63800_) {
        Map<T, T> map = Maps.newHashMap();

        for (int i = this.levelHeightAccessor.getMinSection(); i < this.levelHeightAccessor.getMaxSection(); i++) {
            long j = getKey(p_63799_, i);
            this.dirty.remove(j);
            Optional<R> optional = this.storage.get(j);
            if (optional != null && !optional.isEmpty()) {
                DataResult<T> dataresult = this.codec.apply(() -> this.setDirty(j)).encodeStart(p_63800_, optional.get());
                String s = Integer.toString(i);
                dataresult.resultOrPartial(LOGGER::error).ifPresent(p_223531_ -> map.put(p_63800_.createString(s), (T)p_223531_));
            }
        }

        return new Dynamic<>(
            p_63800_,
            p_63800_.createMap(
                ImmutableMap.of(
                    p_63800_.createString("Sections"),
                    p_63800_.createMap(map),
                    p_63800_.createString("DataVersion"),
                    p_63800_.createInt(SharedConstants.getCurrentVersion().getDataVersion().getVersion())
                )
            )
        );
    }

    private static long getKey(ChunkPos p_156628_, int p_156629_) {
        return SectionPos.asLong(p_156628_.x, p_156629_, p_156628_.z);
    }

    protected void onSectionLoad(long p_63813_) {
    }

    protected void setDirty(long p_63788_) {
        Optional<R> optional = this.storage.get(p_63788_);
        if (optional != null && !optional.isEmpty()) {
            this.dirty.add(p_63788_);
        } else {
            LOGGER.warn("No data for position: {}", SectionPos.of(p_63788_));
        }
    }

    private static int getVersion(Dynamic<?> p_63806_) {
        return p_63806_.get("DataVersion").asInt(1945);
    }

    public void flush(ChunkPos p_63797_) {
        if (this.hasWork()) {
            for (int i = this.levelHeightAccessor.getMinSection(); i < this.levelHeightAccessor.getMaxSection(); i++) {
                long j = getKey(p_63797_, i);
                if (this.dirty.contains(j)) {
                    this.writeColumn(p_63797_);
                    return;
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        this.moonrise$close(); // Paper - rewrite chunk system
    }

    /**
     * Neo: Removes the data for the given chunk position.
     * See PR #937
     */
    public void remove(long sectionPosAsLong) {
        this.storage.remove(sectionPosAsLong);
    }
}
