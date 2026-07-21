package net.minecraft.world.level.gameevent;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class DynamicGameEventListener<T extends GameEventListener> {
    private final T listener;
    @Nullable
    private SectionPos lastSection;

    public DynamicGameEventListener(T p_223615_) {
        this.listener = p_223615_;
    }

    public void add(ServerLevel p_223618_) {
        this.move(p_223618_);
    }

    public T getListener() {
        return this.listener;
    }

    public void remove(ServerLevel p_223635_) {
        ifChunkExists(p_223635_, this.lastSection, p_248453_ -> p_248453_.unregister(this.listener));
        // We need to unset the last section when removed, otherwise if the same instance is re-added at the same position it
        // will assume there was no change and fail to re-register.
        // In vanilla, chunks rarely unload and re-load quickly enough to trigger this issue. However, our chunk system has a
        // quirk where fast chunk reload cycles will often occur on player login (see PR #22).
        // So we fix this vanilla oversight as our changes cause it to manifest in bugs much more often (see issue #87).
        this.lastSection = null;
    }

    public void move(ServerLevel p_223642_) {
        this.listener.getListenerSource().getPosition(p_223642_).map(SectionPos::of).ifPresent(p_223621_ -> {
            if (this.lastSection == null || !this.lastSection.equals(p_223621_)) {
                ifChunkExists(p_223642_, this.lastSection, p_248452_ -> p_248452_.unregister(this.listener));
                this.lastSection = p_223621_;
                ifChunkExists(p_223642_, this.lastSection, p_248451_ -> p_248451_.register(this.listener));
            }
        });
    }

    private static void ifChunkExists(LevelReader p_223623_, @Nullable SectionPos p_223624_, Consumer<GameEventListenerRegistry> p_223625_) {
        if (p_223624_ != null) {
            ChunkAccess chunkaccess = p_223623_.getChunk(p_223624_.x(), p_223624_.z(), ChunkStatus.FULL, false);
            if (chunkaccess != null) {
                p_223625_.accept(chunkaccess.getListenerRegistry(p_223624_.y()));
            }
        }
    }
}
