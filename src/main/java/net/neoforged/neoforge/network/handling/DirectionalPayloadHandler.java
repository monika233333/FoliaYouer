/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.network.handling;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Helper class that merges two unidirectional handlers into a single bidirectional handler.
 */
public record DirectionalPayloadHandler<T extends CustomPacketPayload>(IPayloadHandler<T> clientSide, IPayloadHandler<T> serverSide) implements IPayloadHandler<T> {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    @Override
    public void handle(T payload, IPayloadContext context) {
        LOGGER.info("[FoliaYouer-Debug] DirectionalPayloadHandler.handle called, payload={}, flow={}, isClientbound={}, isServerbound={}",
            payload.type().id(), context.flow(), context.flow().isClientbound(), context.flow().isServerbound());
        if (context.flow().isClientbound()) {
            LOGGER.info("[FoliaYouer-Debug] DirectionalPayloadHandler: routing to clientSide handler");
            clientSide.handle(payload, context);
        } else if (context.flow().isServerbound()) {
            LOGGER.info("[FoliaYouer-Debug] DirectionalPayloadHandler: routing to serverSide handler");
            serverSide.handle(payload, context);
        } else {
            LOGGER.error("[FoliaYouer-Debug] DirectionalPayloadHandler: NEITHER clientbound nor serverbound! flow={}", context.flow());
        }
    }
}
