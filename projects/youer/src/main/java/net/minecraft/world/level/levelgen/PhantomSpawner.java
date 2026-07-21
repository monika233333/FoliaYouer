package net.minecraft.world.level.levelgen;

import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class PhantomSpawner implements CustomSpawner {
    private int nextTick;

    @Override
    public int tick(ServerLevel p_64576_, boolean p_64577_, boolean p_64578_) {
        if (!p_64577_) {
            return 0;
        } else if (!p_64576_.getGameRules().getBoolean(GameRules.RULE_DOINSOMNIA)) {
            return 0;
        } else {
            RandomSource randomsource = p_64576_.random;
            this.nextTick--;
            if (this.nextTick > 0) {
                return 0;
            } else {
                this.nextTick = this.nextTick + (60 + randomsource.nextInt(60)) * 20;
                if (p_64576_.getSkyDarken() < 5 && p_64576_.dimensionType().hasSkyLight()) {
                    return 0;
                } else {
                    int i = 0;

                    for (ServerPlayer serverplayer : p_64576_.players()) {
                        if (!serverplayer.isSpectator() && (!p_64576_.paperConfig().entities.behavior.phantomsDoNotSpawnOnCreativePlayers || !serverplayer.isCreative())) { // Paper - Add phantom creative and insomniac controls
                            BlockPos blockpos = serverplayer.blockPosition();
                            var event = net.neoforged.neoforge.event.EventHooks.firePlayerSpawnPhantoms(serverplayer, p_64576_, blockpos);
                            boolean isAllow = event.getResult() == net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent.Result.ALLOW;
                            if (event.shouldSpawnPhantoms(p_64576_, blockpos)) {
                                DifficultyInstance difficultyinstance = p_64576_.getCurrentDifficultyAt(blockpos);
                                if (isAllow || difficultyinstance.isHarderThan(randomsource.nextFloat() * 3.0F)) {
                                    ServerStatsCounter serverstatscounter = serverplayer.getStats();
                                    int j = Mth.clamp(serverstatscounter.getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)), 1, Integer.MAX_VALUE);
                                    int k = 24000;
                                    if (isAllow || randomsource.nextInt(j) >= 72000) {
                                        BlockPos blockpos1 = blockpos.above(20 + randomsource.nextInt(15))
                                            .east(-10 + randomsource.nextInt(21))
                                            .south(-10 + randomsource.nextInt(21));
                                        BlockState blockstate = p_64576_.getBlockState(blockpos1);
                                        FluidState fluidstate = p_64576_.getFluidState(blockpos1);
                                        if (NaturalSpawner.isValidEmptySpawnBlock(p_64576_, blockpos1, blockstate, fluidstate, EntityType.PHANTOM)) {
                                            SpawnGroupData spawngroupdata = null;
                                            int l = event.getPhantomsToSpawn();

                                            for (int i1 = 0; i1 < l; i1++) {
                                                // Paper start - PhantomPreSpawnEvent
                                                PhantomPreSpawnEvent eventPaper = new PhantomPreSpawnEvent(io.papermc.paper.util.MCUtil.toLocation(p_64576_, blockpos1), serverplayer.getBukkitEntity(), org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL);
                                                if (!eventPaper.callEvent()) {
                                                    if (eventPaper.shouldAbortSpawn()) {
                                                        break;
                                                    }
                                                    continue;
                                                }
                                                // Paper end - PhantomPreSpawnEvent
                                                Phantom phantom = EntityType.PHANTOM.create(p_64576_);
                                                if (phantom != null) {
                                                    phantom.setSpawningEntity(serverplayer.getUUID()); // Paper - PhantomPreSpawnEvent
                                                    phantom.moveTo(blockpos1, 0.0F, 0.0F);
                                                    spawngroupdata = phantom.finalizeSpawn(p_64576_, difficultyinstance, MobSpawnType.NATURAL, spawngroupdata);
                                                    phantom.spawnReason(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL);  // CraftBukkit
                                                    p_64576_.addFreshEntityWithPassengers(phantom);
                                                    i++;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    return i;
                }
            }
        }
    }
}
