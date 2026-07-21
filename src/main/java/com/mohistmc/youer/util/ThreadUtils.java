package com.mohistmc.youer.util;

import io.papermc.paper.threadedregions.RegionizedServer;

public class ThreadUtils {

    public static void executeOnMainThread(Runnable runnable) {
        // FoliaYouer - schedule on global tick thread instead of sleeping Server thread
        RegionizedServer.getInstance().addTask(runnable);
    }
}
