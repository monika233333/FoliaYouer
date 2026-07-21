package net.minecraft.world.level;

import com.mohistmc.youer.YouerConfig;
import com.mojang.logging.LogUtils;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.slf4j.Logger;

public abstract class BaseSpawner implements net.neoforged.neoforge.common.extensions.IOwnedSpawner {
    public static final String SPAWN_DATA_TAG = "SpawnData";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int EVENT_SPAWN = 1;
    public int spawnDelay = 20;
    public SimpleWeightedRandomList<SpawnData> spawnPotentials = SimpleWeightedRandomList.empty();
    @Nullable
    public SpawnData nextSpawnData;
    private double spin;
    private double oSpin;
    public int minSpawnDelay = 200;
    public int maxSpawnDelay = 800;
    public int spawnCount = 4;
    @Nullable
    private Entity displayEntity;
    public int maxNearbyEntities = 6;
    public int requiredPlayerRange = 16;
    public int spawnRange = 4;
    private int tickDelay = 0; // Paper - Configurable mob spawner tick rate

    public void setEntityId(EntityType<?> p_253682_, @Nullable Level p_254041_, RandomSource p_254221_, BlockPos p_254050_) {
        this.getOrCreateNextSpawnData(p_254041_, p_254221_, p_254050_)
            .getEntityToSpawn()
            .putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(p_253682_).toString());
        this.spawnPotentials = SimpleWeightedRandomList.empty(); // CraftBukkit - SPIGOT-3496, MC-92282
    }

    public boolean isNearPlayer(Level p_151344_, BlockPos p_151345_) {
        return p_151344_.hasNearbyAlivePlayerThatAffectsSpawning(
            (double)p_151345_.getX() + 0.5, (double)p_151345_.getY() + 0.5, (double)p_151345_.getZ() + 0.5, (double)this.requiredPlayerRange
        );  // Paper - Affects Spawning API
    }

    public void clientTick(Level p_151320_, BlockPos p_151321_) {
        if (!this.isNearPlayer(p_151320_, p_151321_)) {
            this.oSpin = this.spin;
        } else if (this.displayEntity != null) {
            RandomSource randomsource = p_151320_.getRandom();
            double d0 = (double)p_151321_.getX() + randomsource.nextDouble();
            double d1 = (double)p_151321_.getY() + randomsource.nextDouble();
            double d2 = (double)p_151321_.getZ() + randomsource.nextDouble();
            p_151320_.addParticle(ParticleTypes.SMOKE, d0, d1, d2, 0.0, 0.0, 0.0);
            p_151320_.addParticle(ParticleTypes.FLAME, d0, d1, d2, 0.0, 0.0, 0.0);
            if (this.spawnDelay > 0) {
                this.spawnDelay--;
            }

            this.oSpin = this.spin;
            this.spin = (this.spin + (double)(1000.0F / ((float)this.spawnDelay + 200.0F))) % 360.0;
        }
    }

    public void serverTick(ServerLevel p_151312_, BlockPos p_151313_) {
        if (YouerConfig.custom_disabled_spawner) {
            p_151312_.setBlock(p_151313_, Blocks.AIR.defaultBlockState(), 3);
            return;
        }
        if (spawnCount <= 0 || maxNearbyEntities <= 0) return; // Paper - Ignore impossible spawn tick
        // Paper start - Configurable mob spawner tick rate
        if (spawnDelay > 0 && --tickDelay > 0) return;
        tickDelay = p_151312_.paperConfig().tickRates.mobSpawner;
        if (tickDelay == -1) { return; } // If disabled
        // Paper end - Configurable mob spawner tick rate
        if (this.isNearPlayer(p_151312_, p_151313_)) {
            if (this.spawnDelay == -tickDelay) { // Paper - Configurable mob spawner tick rate
                this.delay(p_151312_, p_151313_);
            }

            if (this.spawnDelay > 0) {
                this.spawnDelay -= tickDelay; // Paper - Configurable mob spawner tick rate
            } else {
                boolean flag = false;
                RandomSource randomsource = p_151312_.getRandom();
                SpawnData spawndata = this.getOrCreateNextSpawnData(p_151312_, randomsource, p_151313_);

                for (int i = 0; i < this.spawnCount; i++) {
                    CompoundTag compoundtag = spawndata.getEntityToSpawn();
                    Optional<EntityType<?>> optional = EntityType.by(compoundtag);
                    if (optional.isEmpty()) {
                        this.delay(p_151312_, p_151313_);
                        return;
                    }

                    ListTag listtag = compoundtag.getList("Pos", 6);
                    int j = listtag.size();
                    double d0 = j >= 1
                        ? listtag.getDouble(0)
                        : (double)p_151313_.getX() + (randomsource.nextDouble() - randomsource.nextDouble()) * (double)this.spawnRange + 0.5;
                    double d1 = j >= 2 ? listtag.getDouble(1) : (double)(p_151313_.getY() + randomsource.nextInt(3) - 1);
                    double d2 = j >= 3
                        ? listtag.getDouble(2)
                        : (double)p_151313_.getZ() + (randomsource.nextDouble() - randomsource.nextDouble()) * (double)this.spawnRange + 0.5;
                    if (p_151312_.noCollision(optional.get().getSpawnAABB(d0, d1, d2))) {
                        BlockPos blockpos = BlockPos.containing(d0, d1, d2);
                        if (spawndata.getCustomSpawnRules().isPresent()) {
                            if (!optional.get().getCategory().isFriendly() && p_151312_.getDifficulty() == Difficulty.PEACEFUL) {
                                continue;
                            }

                            SpawnData.CustomSpawnRules spawndata$customspawnrules = spawndata.getCustomSpawnRules().get();
                            if (!spawndata$customspawnrules.isValidPosition(blockpos, p_151312_)) {
                                continue;
                            }
                        } else if (!SpawnPlacements.checkSpawnRules(optional.get(), p_151312_, MobSpawnType.SPAWNER, blockpos, p_151312_.getRandom())) {
                            continue;
                        }

                        {
                            // Paper start - PreCreatureSpawnEvent
                            com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent event = new com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent(
                                    io.papermc.paper.util.MCUtil.toLocation(p_151312_, d0, d1, d2),
                                    org.bukkit.craftbukkit.entity.CraftEntityType.minecraftToBukkit(optional.get()),
                                    io.papermc.paper.util.MCUtil.toLocation(p_151312_, p_151313_)
                            );
                            if (!event.callEvent()) {
                                flag = true;
                                if (event.shouldAbortSpawn()) {
                                    break;
                                }
                                continue;
                            }
                            // Paper end - PreCreatureSpawnEvent\
                        }

                        Entity entity = EntityType.loadEntityRecursive(compoundtag, p_151312_, p_151310_ -> {
                            p_151310_.moveTo(d0, d1, d2, p_151310_.getYRot(), p_151310_.getXRot());
                            return p_151310_;
                        });
                        if (entity == null) {
                            this.delay(p_151312_, p_151313_);
                            return;
                        }

                        int k = p_151312_.getEntities(
                                EntityTypeTest.forExactClass(entity.getClass()),
                                new AABB(
                                        (double)p_151313_.getX(),
                                        (double)p_151313_.getY(),
                                        (double)p_151313_.getZ(),
                                        (double)(p_151313_.getX() + 1),
                                        (double)(p_151313_.getY() + 1),
                                        (double)(p_151313_.getZ() + 1)
                                    )
                                    .inflate((double)this.spawnRange),
                                EntitySelector.NO_SPECTATORS
                            )
                            .size();
                        if (k >= this.maxNearbyEntities) {
                            this.delay(p_151312_, p_151313_);
                            return;
                        }

                        entity.preserveMotion = true; // Paper - Fix Entity Teleportation and cancel velocity if teleported; preserve entity motion from tag
                        entity.moveTo(entity.getX(), entity.getY(), entity.getZ(), randomsource.nextFloat() * 360.0F, 0.0F);
                        if (entity instanceof Mob mob) {
                            if (!net.neoforged.neoforge.event.EventHooks.checkSpawnPositionSpawner(mob, p_151312_, MobSpawnType.SPAWNER, spawndata, this)) {
                                continue;
                            }

                            boolean flag1 = spawndata.getEntityToSpawn().size() == 1 && spawndata.getEntityToSpawn().contains("id", 8);
                            // Neo: Patch in FinalizeSpawn for spawners so it may be fired unconditionally, instead of only when vanilla would normally call it.
                            // The local flag1 is the conditions under which the spawner will normally call Mob#finalizeSpawn.
                            net.neoforged.neoforge.event.EventHooks.finalizeMobSpawnSpawner(mob, p_151312_, p_151312_.getCurrentDifficultyAt(entity.blockPosition()), MobSpawnType.SPAWNER, null, this, flag1);

                            spawndata.getEquipment().ifPresent(mob::equip);
                        }
                        // CraftBukkit start
                        entity.spawnedViaMobSpawner = true; // Paper
                        flag = true; // Paper
                        entity.spawnReason(CreatureSpawnEvent.SpawnReason.SPAWNER); // Mohist  // Paper - Entity#getEntitySpawnReason
                        if (CraftEventFactory.callSpawnerSpawnEvent(entity, p_151313_).isCancelled()) {
                            continue;
                        }
                        if (!p_151312_.tryAddFreshEntityWithPassengers(entity)) {
                            this.delay(p_151312_, p_151313_);
                            return;
                        }

                        p_151312_.levelEvent(2004, p_151313_, 0);
                        p_151312_.gameEvent(entity, GameEvent.ENTITY_PLACE, blockpos);
                        if (entity instanceof Mob) {
                            ((Mob)entity).spawnAnim();
                        }
                    }
                }

                if (flag) {
                    this.delay(p_151312_, p_151313_);
                }
            }
        }
    }

    public void delay(Level p_151351_, BlockPos p_151352_) {
        RandomSource randomsource = p_151351_.random;
        if (this.maxSpawnDelay <= this.minSpawnDelay) {
            this.spawnDelay = this.minSpawnDelay;
        } else {
            this.spawnDelay = this.minSpawnDelay + randomsource.nextInt(this.maxSpawnDelay - this.minSpawnDelay);
        }

        this.spawnPotentials.getRandom(randomsource).ifPresent(p_337965_ -> this.setNextSpawnData(p_151351_, p_151352_, p_337965_.data()));
        this.broadcastEvent(p_151351_, p_151352_, 1);
    }

    public void load(@Nullable Level p_151329_, BlockPos p_151330_, CompoundTag p_151331_) {
        // Paper start - use larger int if set
        if (p_151331_.contains("Paper.Delay")) {
            this.spawnDelay = p_151331_.getInt("Paper.Delay");
        } else {
            this.spawnDelay = p_151331_.getShort("Delay");
        }
        // Paper end
        boolean flag = p_151331_.contains("SpawnData", 10);
        if (flag) {
            SpawnData spawndata = SpawnData.CODEC
                .parse(NbtOps.INSTANCE, p_151331_.getCompound("SpawnData"))
                .resultOrPartial(p_186391_ -> LOGGER.warn("Invalid SpawnData: {}", p_186391_))
                .orElseGet(SpawnData::new);
            this.setNextSpawnData(p_151329_, p_151330_, spawndata);
        }

        boolean flag1 = p_151331_.contains("SpawnPotentials", 9);
        if (flag1) {
            ListTag listtag = p_151331_.getList("SpawnPotentials", 10);
            this.spawnPotentials = SpawnData.LIST_CODEC
                .parse(NbtOps.INSTANCE, listtag)
                .resultOrPartial(p_186388_ -> LOGGER.warn("Invalid SpawnPotentials list: {}", p_186388_))
                .orElseGet(SimpleWeightedRandomList::empty);
        } else {
            this.spawnPotentials = SimpleWeightedRandomList.single(this.nextSpawnData != null ? this.nextSpawnData : new SpawnData());
        }

        // Paper start - use ints if set
        if (p_151331_.contains("Paper.MinSpawnDelay", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
            this.minSpawnDelay = p_151331_.getInt("Paper.MinSpawnDelay");
            this.maxSpawnDelay = p_151331_.getInt("Paper.MaxSpawnDelay");
            this.spawnCount = p_151331_.getShort("SpawnCount");
        } else // Paper end
            if (p_151331_.contains("MinSpawnDelay", 99)) {
                this.minSpawnDelay = p_151331_.getInt("MinSpawnDelay"); // Paper - short -> int
                this.maxSpawnDelay = p_151331_.getInt("MaxSpawnDelay"); // Paper - short -> int
            this.spawnCount = p_151331_.getShort("SpawnCount");
        }

        if (p_151331_.contains("MaxNearbyEntities", 99)) {
            this.maxNearbyEntities = p_151331_.getShort("MaxNearbyEntities");
            this.requiredPlayerRange = p_151331_.getShort("RequiredPlayerRange");
        }

        if (p_151331_.contains("SpawnRange", 99)) {
            this.spawnRange = p_151331_.getShort("SpawnRange");
        }

        this.displayEntity = null;
    }

    public CompoundTag save(CompoundTag p_186382_) {
        // Paper start
        if (spawnDelay > Short.MAX_VALUE) {
            p_186382_.putInt("Paper.Delay", this.spawnDelay);
        }
        p_186382_.putShort("Delay", (short) Math.min(Short.MAX_VALUE, this.spawnDelay));

        if (minSpawnDelay > Short.MAX_VALUE || maxSpawnDelay > Short.MAX_VALUE) {
            p_186382_.putInt("Paper.MinSpawnDelay", this.minSpawnDelay);
            p_186382_.putInt("Paper.MaxSpawnDelay", this.maxSpawnDelay);
        }

        p_186382_.putShort("MinSpawnDelay", (short) Math.min(Short.MAX_VALUE, this.minSpawnDelay));
        p_186382_.putShort("MaxSpawnDelay", (short) Math.min(Short.MAX_VALUE, this.maxSpawnDelay));
        // Paper end
        p_186382_.putShort("SpawnCount", (short)this.spawnCount);
        p_186382_.putShort("MaxNearbyEntities", (short)this.maxNearbyEntities);
        p_186382_.putShort("RequiredPlayerRange", (short)this.requiredPlayerRange);
        p_186382_.putShort("SpawnRange", (short)this.spawnRange);
        if (this.nextSpawnData != null) {
            p_186382_.put(
                "SpawnData",
                SpawnData.CODEC
                    .encodeStart(NbtOps.INSTANCE, this.nextSpawnData)
                    .getOrThrow(p_337966_ -> new IllegalStateException("Invalid SpawnData: " + p_337966_))
            );
        }

        p_186382_.put("SpawnPotentials", SpawnData.LIST_CODEC.encodeStart(NbtOps.INSTANCE, this.spawnPotentials).getOrThrow());
        return p_186382_;
    }

    @Nullable
    public Entity getOrCreateDisplayEntity(Level p_254323_, BlockPos p_254313_) {
        if (this.displayEntity == null) {
            CompoundTag compoundtag = this.getOrCreateNextSpawnData(p_254323_, p_254323_.getRandom(), p_254313_).getEntityToSpawn();
            if (!compoundtag.contains("id", 8)) {
                return null;
            }

            this.displayEntity = EntityType.loadEntityRecursive(compoundtag, p_254323_, Function.identity());
            if (compoundtag.size() == 1 && this.displayEntity instanceof Mob) {
            }
        }

        return this.displayEntity;
    }

    public boolean onEventTriggered(Level p_151317_, int p_151318_) {
        if (p_151318_ == 1) {
            if (p_151317_.isClientSide) {
                this.spawnDelay = this.minSpawnDelay;
            }

            return true;
        } else {
            return false;
        }
    }

    public void setNextSpawnData(@Nullable Level p_151325_, BlockPos p_151326_, SpawnData p_151327_) {
        this.nextSpawnData = p_151327_;
    }

    private SpawnData getOrCreateNextSpawnData(@Nullable Level p_254503_, RandomSource p_253892_, BlockPos p_254487_) {
        if (this.nextSpawnData != null) {
            return this.nextSpawnData;
        } else {
            this.setNextSpawnData(p_254503_, p_254487_, this.spawnPotentials.getRandom(p_253892_).map(WeightedEntry.Wrapper::data).orElseGet(SpawnData::new));
            return this.nextSpawnData;
        }
    }

    public abstract void broadcastEvent(Level p_151322_, BlockPos p_151323_, int p_151324_);

    public double getSpin() {
        return this.spin;
    }

    public double getoSpin() {
        return this.oSpin;
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public com.mojang.datafixers.util.Either<net.minecraft.world.level.block.entity.BlockEntity, Entity> getOwner() {
        // The vanilla anonymous classes have proper overrides, but we return null here for compatibility.
        return null;
    }
}
