package net.minecraft.server.level.progress;

import com.mohistmc.youer.util.I18n;
import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;

public class LoggerChunkProgressListener implements ChunkProgressListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final int maxCount;
    private int count;
    private long startTime;
    private long nextTickTime = Long.MAX_VALUE;
    private int progress = 0;

    private LoggerChunkProgressListener(int p_9629_) {
        this.maxCount = p_9629_;
    }

    public static LoggerChunkProgressListener createFromGameruleRadius(int p_319913_) {
        return p_319913_ > 0 ? create(p_319913_ + 1) : createCompleted();
    }

    public static LoggerChunkProgressListener create(int p_320293_) {
        int i = ChunkProgressListener.calculateDiameter(p_320293_);
        return new LoggerChunkProgressListener(i * i);
    }

    public static LoggerChunkProgressListener createCompleted() {
        return new LoggerChunkProgressListener(0);
    }

    @Override
    public void updateSpawnPos(ChunkPos p_9631_) {
        this.nextTickTime = Util.getMillis();
        this.startTime = this.nextTickTime;
    }

    @Override
    public void onStatusChange(ChunkPos p_9633_, @Nullable ChunkStatus p_332174_) {
        if (p_332174_ == ChunkStatus.FULL) {
            this.count++;
        }

        int i = this.getProgress();
        if (Util.getMillis() > this.nextTickTime) {
            this.nextTickTime += 500L;
            int s = Mth.clamp(i, 0, 100);
            if (progress != s) {
                progress = s;
                LOGGER.info(Component.translatable("menu.preparingSpawn", s).getString());
            }
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        LOGGER.info(I18n.as("loggerchunkprogresslistener.timeelapsed", Util.getMillis() - this.startTime));
        this.nextTickTime = Long.MAX_VALUE;
    }

    public int getProgress() {
        return this.maxCount == 0 ? 100 : Mth.floor((float)this.count * 100.0F / (float)this.maxCount);
    }
}
