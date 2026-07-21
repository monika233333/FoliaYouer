/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.network.handling;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask.Type;
import net.minecraft.server.network.ServerPlayerConnection;
import net.neoforged.neoforge.common.extensions.IServerConfigurationPacketListenerExtension;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public record ServerPayloadContext(ServerCommonPacketListener listener, ResourceLocation payloadId) implements IPayloadContext {
    @Override
    public void handle(CustomPacketPayload payload) {
        handle(new ServerboundCustomPayloadPacket(payload));
    }

    @Override
    public CompletableFuture<Void> enqueueWork(Runnable task) {
        if (listener.getMainThreadEventLoop().isSameThread()) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }
        // Folia: MinecraftServer event loop may not process tasks during configuration phase.
        // Schedule directly on the global region thread instead.
        org.slf4j.Logger logger = com.mojang.logging.LogUtils.getLogger();
        logger.info("[FoliaYouer-Debug] ServerPayloadContext.enqueueWork(Runnable) scheduling to global tick thread, payloadId={}, currentThread={}",
            this.payloadId, Thread.currentThread().getName());
        CompletableFuture<Void> future = new CompletableFuture<>();
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
                logger.error("[FoliaYouer-Debug] enqueueWork task failed for payloadId={}", this.payloadId, t);
            }
        });
        return future;
    }

    @Override
    public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
        if (listener.getMainThreadEventLoop().isSameThread()) {
            return CompletableFuture.completedFuture(task.get());
        }
        // Folia: MinecraftServer event loop may not process tasks during configuration phase.
        // Schedule directly on the global region thread instead.
        org.slf4j.Logger logger = com.mojang.logging.LogUtils.getLogger();
        logger.info("[FoliaYouer-Debug] ServerPayloadContext.enqueueWork(Supplier) scheduling to global tick thread, payloadId={}, currentThread={}",
            this.payloadId, Thread.currentThread().getName());
        CompletableFuture<T> future = new CompletableFuture<>();
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
                logger.error("[FoliaYouer-Debug] enqueueWork(Supplier) task failed for payloadId={}", this.payloadId, t);
            }
        });
        return future;
    }

    @Override
    public void finishCurrentTask(Type type) {
        if (listener instanceof IServerConfigurationPacketListenerExtension ext) {
            ext.finishCurrentTask(type);
        } else {
            throw new UnsupportedOperationException("Attempted to complete a configuration task outside of the configuration phase!");
        }
    }

    @Override
    public PacketFlow flow() {
        return PacketFlow.SERVERBOUND;
    }

    @Override
    public ServerPlayer player() {
        if (this.listener instanceof ServerPlayerConnection spc) {
            return spc.getPlayer();
        }
        throw new UnsupportedOperationException("Cannot retrieve the sending player during the configuration phase.");
    }
}
