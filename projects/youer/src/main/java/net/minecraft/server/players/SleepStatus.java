package net.minecraft.server.players;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class SleepStatus {
    private int activePlayers;
    private int sleepingPlayers;

    public boolean areEnoughSleeping(int p_144003_) {
        return this.sleepingPlayers >= this.sleepersNeeded(p_144003_);
    }

    public boolean areEnoughDeepSleeping(int p_144005_, List<ServerPlayer> p_144006_) {
        // CraftBukkit start
        int j = (int) p_144006_.stream().filter((eh) -> { return eh.isSleepingLongEnough() || eh.fauxSleeping || (eh.level().purpurConfig.idleTimeoutCountAsSleeping && eh.isAfk()); }).count(); // Purpur
        boolean anyDeepSleep = p_144006_.stream().anyMatch(Player::isSleepingLongEnough);
        return anyDeepSleep && j >= this.sleepersNeeded(p_144005_);
        // CraftBukkit end
    }

    public int sleepersNeeded(int p_144011_) {
        return Math.max(1, Mth.ceil((float)(this.activePlayers * p_144011_) / 100.0F));
    }

    public void removeAllSleepers() {
        this.sleepingPlayers = 0;
    }

    public int amountSleeping() {
        return this.sleepingPlayers;
    }

    public boolean update(List<ServerPlayer> p_144008_) {
        int i = this.activePlayers;
        int j = this.sleepingPlayers;
        this.activePlayers = 0;
        this.sleepingPlayers = 0;
        boolean anySleep = false; // CraftBukkit

        for (ServerPlayer serverplayer : p_144008_) {
            if (!serverplayer.isSpectator()) {
                this.activePlayers++;
                if ((serverplayer.isSleeping() || serverplayer.fauxSleeping) || (serverplayer.level().purpurConfig.idleTimeoutCountAsSleeping && serverplayer.isAfk())) { // CraftBukkit // Purpur
                    this.sleepingPlayers++;
                }
                // CraftBukkit start
                if (serverplayer.isSleeping()) {
                    anySleep = true;
                }
                // CraftBukkit end
            }
        }

        return anySleep && (j > 0 || this.sleepingPlayers > 0) && (i != this.activePlayers || j != this.sleepingPlayers); // CraftBukkit
    }
}
