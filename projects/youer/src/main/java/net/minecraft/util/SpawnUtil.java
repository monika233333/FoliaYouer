package net.minecraft.util;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

public class SpawnUtil {

    // Youer start
    private static final AtomicReference<SpawnReason> trySpawnMob$reason = new AtomicReference<>(SpawnReason.DEFAULT);
    private static final AtomicReference<Runnable> trySpawnMob$onAbort = new AtomicReference<>(null);

    public static void trySpawnMob$reason(SpawnReason reason) {
        trySpawnMob$reason.set(reason);
    }

    public static void setTrySpawnMob$onAbort(Runnable onAbort) {
        trySpawnMob$onAbort.set(onAbort);
    }

    public static <T extends Mob> Optional<T> trySpawnMob(EntityType<T> pEntityType, MobSpawnType pSpawnType, ServerLevel pLevel, BlockPos pPos, int pAttempts, int p_216409_, int pYOffset, SpawnUtil.Strategy pStrategy, SpawnReason reason, @javax.annotation.Nullable Runnable onAbort) { // Paper
        trySpawnMob$reason(reason);
        trySpawnMob$onAbort.set(onAbort);
        return trySpawnMob(pEntityType, pSpawnType, pLevel, pPos, pAttempts, p_216409_, pYOffset, pStrategy);
    }
    // Youer end

    public static <T extends Mob> Optional<T> trySpawnMob(
        EntityType<T> p_216404_,
        MobSpawnType p_216405_,
        ServerLevel p_216406_,
        BlockPos p_216407_,
        int p_216408_,
        int p_216409_,
        int p_216410_,
        SpawnUtil.Strategy p_216411_
    ) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = p_216407_.mutable();
        org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason = trySpawnMob$reason.getAndSet(SpawnReason.DEFAULT);
        for (int i = 0; i < p_216408_; i++) {
            int j = Mth.randomBetweenInclusive(p_216406_.random, -p_216409_, p_216409_);
            int k = Mth.randomBetweenInclusive(p_216406_.random, -p_216409_, p_216409_);
            blockpos$mutableblockpos.setWithOffset(p_216407_, j, p_216410_, k);
            if (p_216406_.getWorldBorder().isWithinBounds(blockpos$mutableblockpos)
                && moveToPossibleSpawnPosition(p_216406_, p_216410_, blockpos$mutableblockpos, p_216411_)) {
                // Paper start - PreCreatureSpawnEvent
                com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent event = new com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent(
                        io.papermc.paper.util.MCUtil.toLocation(p_216406_, p_216407_),
                        org.bukkit.craftbukkit.entity.CraftEntityType.minecraftToBukkit(p_216404_),
                        reason
                );
                if (!event.callEvent()) {
                    Runnable onAbort = trySpawnMob$onAbort.getAndSet(null);
                    if (event.shouldAbortSpawn()) {
                        if (onAbort != null) {
                            onAbort.run();
                        }
                        return Optional.empty();
                    }
                    break;
                }
                // Paper end - PreCreatureSpawnEvent
                T t = (T)p_216404_.create(p_216406_, null, blockpos$mutableblockpos, p_216405_, false, false);
                if (t != null) {
                    if (net.neoforged.neoforge.event.EventHooks.checkSpawnPosition(t, p_216406_, p_216405_)) {
                        p_216406_.addFreshEntityWithPassengers(t, reason);
                        return Optional.of(t);
                    }

                    t.discard();
                }
            }
        }

        return Optional.empty();
    }

    private static boolean moveToPossibleSpawnPosition(ServerLevel p_216399_, int p_216400_, BlockPos.MutableBlockPos p_216401_, SpawnUtil.Strategy p_216402_) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos().set(p_216401_);
        BlockState blockstate = p_216399_.getBlockState(blockpos$mutableblockpos);

        for (int i = p_216400_; i >= -p_216400_; i--) {
            p_216401_.move(Direction.DOWN);
            blockpos$mutableblockpos.setWithOffset(p_216401_, Direction.UP);
            BlockState blockstate1 = p_216399_.getBlockState(p_216401_);
            if (p_216402_.canSpawnOn(p_216399_, p_216401_, blockstate1, blockpos$mutableblockpos, blockstate)) {
                p_216401_.move(Direction.UP);
                return true;
            }

            blockstate = blockstate1;
        }

        return false;
    }

    public interface Strategy {
        @Deprecated
        SpawnUtil.Strategy LEGACY_IRON_GOLEM = (p_289751_, p_289752_, p_289753_, p_289754_, p_289755_) -> !p_289753_.is(Blocks.COBWEB)
                    && !p_289753_.is(Blocks.CACTUS)
                    && !p_289753_.is(Blocks.GLASS_PANE)
                    && !(p_289753_.getBlock() instanceof StainedGlassPaneBlock)
                    && !(p_289753_.getBlock() instanceof StainedGlassBlock)
                    && !(p_289753_.getBlock() instanceof LeavesBlock)
                    && !p_289753_.is(Blocks.CONDUIT)
                    && !p_289753_.is(Blocks.ICE)
                    && !p_289753_.is(Blocks.TNT)
                    && !p_289753_.is(Blocks.GLOWSTONE)
                    && !p_289753_.is(Blocks.BEACON)
                    && !p_289753_.is(Blocks.SEA_LANTERN)
                    && !p_289753_.is(Blocks.FROSTED_ICE)
                    && !p_289753_.is(Blocks.TINTED_GLASS)
                    && !p_289753_.is(Blocks.GLASS)
                ? (p_289755_.isAir() || p_289755_.liquid()) && (p_289753_.isSolid() || p_289753_.is(Blocks.POWDER_SNOW))
                : false;
        SpawnUtil.Strategy ON_TOP_OF_COLLIDER = (p_216416_, p_216417_, p_216418_, p_216419_, p_216420_) -> p_216420_.getCollisionShape(p_216416_, p_216419_)
                    .isEmpty()
                && Block.isFaceFull(p_216418_.getCollisionShape(p_216416_, p_216417_), Direction.UP);

        boolean canSpawnOn(ServerLevel p_216428_, BlockPos p_216429_, BlockState p_216430_, BlockPos p_216431_, BlockState p_216432_);
    }
}
