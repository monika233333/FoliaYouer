package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.util.Mth;

public abstract class Goal {
    private final EnumSet<Goal.Flag> flags = EnumSet.noneOf(Goal.Flag.class);
    public abstract boolean canUse();

    public boolean canContinueToUse() {
        return this.canUse();
    }

    public boolean isInterruptable() {
        return true;
    }

    public void start() {
    }

    public void stop() {
    }

    public boolean requiresUpdateEveryTick() {
        return false;
    }

    public void tick() {
    }

    public void setFlags(EnumSet<Goal.Flag> p_25328_) {
        this.flags.clear();
        this.flags.addAll(p_25328_);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    public EnumSet<Goal.Flag> getFlags() {
        return this.flags;
    }

    protected int adjustedTickDelay(int p_186072_) {
        return this.requiresUpdateEveryTick() ? p_186072_ : reducedTickDelay(p_186072_);
    }

    protected static int reducedTickDelay(int p_186074_) {
        return Mth.positiveCeilDiv(p_186074_, 2);
    }

    // Paper start - Mob goal api
    private com.destroystokyo.paper.entity.ai.PaperVanillaGoal<?> vanillaGoal;
    public <T extends org.bukkit.entity.Mob> com.destroystokyo.paper.entity.ai.Goal<T> asPaperVanillaGoal() {
        if(this.vanillaGoal == null) {
            this.vanillaGoal = new com.destroystokyo.paper.entity.ai.PaperVanillaGoal<>(this);
        }
        //noinspection unchecked
        return (com.destroystokyo.paper.entity.ai.Goal<T>) this.vanillaGoal;
    }
    // Paper end - Mob goal api

    public static enum Flag {
        UNKNOWN_BEHAVIOR, // Paper - add UNKNOWN_BEHAVIOR
        MOVE,
        LOOK,
        JUMP,
        TARGET;
    }
}
