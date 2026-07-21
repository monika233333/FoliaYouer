package net.minecraft.network.protocol;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.network.PacketListener;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.util.thread.BlockableEventLoop;
import org.slf4j.Logger;

public class PacketUtils {
    private static final Logger LOGGER = LogUtils.getLogger();

    public PacketUtils() {}

    public static <T extends PacketListener> void ensureRunningOnSameThread(Packet<T> p_131360_, T p_131361_, ServerLevel p_131362_) throws RunningOnDifferentThreadException {
        ensureRunningOnSameThread(p_131360_, p_131361_, (BlockableEventLoop) p_131362_.getServer());
    }

    public static <T extends PacketListener> void ensureRunningOnSameThread(Packet<T> p_131364_, T p_131365_, BlockableEventLoop<?> p_131366_) throws RunningOnDifferentThreadException {
        if (!p_131366_.isSameThread()) {
            Runnable run = () -> {
                if (p_131365_ instanceof ServerCommonPacketListenerImpl serverCommonPacketListener && serverCommonPacketListener.processedDisconnect) return; // CraftBukkit - Don't handle sync packets for kicked players
                if (p_131365_.shouldHandleMessage(p_131364_)) {
                    try {
                        p_131364_.handle(p_131365_);
                    } catch (Exception exception) {
                        if (exception instanceof ReportedException reportedexception && reportedexception.getCause() instanceof OutOfMemoryError) {
                            throw makeReportedException(exception, p_131364_, p_131365_);
                        }

                        p_131365_.onPacketError(p_131364_, exception);
                    }
                } else {
                    PacketUtils.LOGGER.debug("Ignoring packet due to disconnection: {}", p_131364_);
                }
            };

            // FoliaYouer start - region threading - dispatch to correct thread
            if (p_131365_ instanceof ServerGamePacketListenerImpl gamePacketListener) {
                gamePacketListener.player.getBukkitEntity().taskScheduler.schedule(
                    (net.minecraft.server.level.ServerPlayer player) -> {
                        run.run();
                    },
                    null, 1L
                );
            } else if (p_131365_ instanceof ServerConfigurationPacketListenerImpl) {
                io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(run);
            } else if (p_131365_ instanceof ServerLoginPacketListenerImpl) {
                io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(run);
            } else {
                throw new UnsupportedOperationException("Unknown listener: " + p_131365_);
            }
            // FoliaYouer end - region threading

            throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
        }
    }

    public static <T extends PacketListener> ReportedException makeReportedException(Exception p_341646_, Packet<T> p_341629_, T p_341619_) {
        if (p_341646_ instanceof ReportedException reportedexception) {
            fillCrashReport(reportedexception.getReport(), p_341619_, p_341629_);
            return reportedexception;
        } else {
            CrashReport crashreport = CrashReport.forThrowable(p_341646_, "Main thread packet handler");
            fillCrashReport(crashreport, p_341619_, p_341629_);
            return new ReportedException(crashreport);
        }
    }

    public static <T extends PacketListener> void fillCrashReport(CrashReport p_340891_, T p_340866_, @Nullable Packet<T> p_340900_) {
        if (p_340900_ != null) {
            CrashReportCategory crashreportcategory = p_340891_.addCategory("Incoming Packet");
            crashreportcategory.setDetail("Type", () -> p_340900_.type().toString());
            crashreportcategory.setDetail("Is Terminal", () -> Boolean.toString(p_340900_.isTerminal()));
            crashreportcategory.setDetail("Is Skippable", () -> Boolean.toString(p_340900_.isSkippable()));
        }

        p_340866_.fillCrashReport(p_340891_);
    }
}
