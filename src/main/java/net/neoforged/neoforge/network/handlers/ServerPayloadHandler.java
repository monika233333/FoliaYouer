/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.network.handlers;

import net.neoforged.neoforge.network.configuration.SyncRegistries;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.payload.FrozenRegistrySyncCompletedPayload;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@ApiStatus.Internal
public final class ServerPayloadHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private ServerPayloadHandler() {}

    public static void handle(FrozenRegistrySyncCompletedPayload payload, IPayloadContext context) {
        LOGGER.info("[FoliaYouer-Debug] ServerPayloadHandler.handle FrozenRegistrySyncCompletedPayload, calling finishCurrentTask");
        context.finishCurrentTask(SyncRegistries.TYPE);
        LOGGER.info("[FoliaYouer-Debug] ServerPayloadHandler.handle finishCurrentTask returned");
    }
}
