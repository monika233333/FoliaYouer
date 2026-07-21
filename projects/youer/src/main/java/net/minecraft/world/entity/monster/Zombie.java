package net.minecraft.world.entity.monster;

import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTransformEvent;

public class Zombie extends Monster {
    private static final ResourceLocation SPEED_MODIFIER_BABY_ID = ResourceLocation.withDefaultNamespace("baby");
    private static final AttributeModifier SPEED_MODIFIER_BABY = new AttributeModifier(
        SPEED_MODIFIER_BABY_ID, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE
    );
    private final AttributeModifier babyModifier = new AttributeModifier(Zombie.SPEED_MODIFIER_BABY_ID, this.level().paperConfig().entities.behavior.babyZombieMovementModifier, AttributeModifier.Operation.ADD_MULTIPLIED_BASE); // Paper - Make baby speed configurable
    private static final ResourceLocation REINFORCEMENT_CALLER_CHARGE_ID = ResourceLocation.withDefaultNamespace("reinforcement_caller_charge");
    private static final AttributeModifier ZOMBIE_REINFORCEMENT_CALLEE_CHARGE = new AttributeModifier(
        ResourceLocation.withDefaultNamespace("reinforcement_callee_charge"), -0.05F, AttributeModifier.Operation.ADD_VALUE
    );
    private static final ResourceLocation LEADER_ZOMBIE_BONUS_ID = ResourceLocation.withDefaultNamespace("leader_zombie_bonus");
    private static final ResourceLocation ZOMBIE_RANDOM_SPAWN_BONUS_ID = ResourceLocation.withDefaultNamespace("zombie_random_spawn_bonus");
    private static final EntityDataAccessor<Boolean> DATA_BABY_ID = SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_SPECIAL_TYPE_ID = SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> DATA_DROWNED_CONVERSION_ID = SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.BOOLEAN);
    public static final float ZOMBIE_LEADER_CHANCE = 0.05F;
    public static final int REINFORCEMENT_ATTEMPTS = 50;
    public static final int REINFORCEMENT_RANGE_MAX = 40;
    public static final int REINFORCEMENT_RANGE_MIN = 7;
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.ZOMBIE.getDimensions().scale(0.5F).withEyeHeight(0.93F);
    private static final float BREAK_DOOR_CHANCE = 0.1F;
    public static final Predicate<Difficulty> DOOR_BREAKING_PREDICATE = p_34284_ -> p_34284_ == Difficulty.HARD;
    private final BreakDoorGoal breakDoorGoal;
    private boolean canBreakDoors;
    private int inWaterTime;
    public int conversionTime;

    public Zombie(EntityType<? extends Zombie> p_34271_, Level p_34272_) {
        super(p_34271_, p_34272_);
        this.breakDoorGoal = new BreakDoorGoal(this, com.google.common.base.Predicates.in(p_34272_.paperConfig().entities.behavior.doorBreakingDifficulty.getOrDefault(p_34271_, p_34272_.paperConfig().entities.behavior.doorBreakingDifficulty.get(EntityType.ZOMBIE)))); // Paper - Configurable door breaking difficulty
        this.setShouldBurnInDay(true); // Purpur - API for any mob to burn daylight
    }

    public Zombie(Level p_34274_) {
        this(EntityType.ZOMBIE, p_34274_);
    }

    @Override
    protected void registerGoals() {
        if (this.level().paperConfig().entities.behavior.zombiesTargetTurtleEggs) this.goalSelector.addGoal(4, new Zombie.ZombieAttackTurtleEggGoal(this, 1.0, 3));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.addBehaviourGoals();
    }

    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(2, new ZombieAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(6, new MoveThroughVillageGoal(this, 1.0, true, 4, this::canBreakDoors));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(ZombifiedPiglin.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        if ( this.level().spigotConfig.zombieAggressiveTowardsVillager ) this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false)); // Spigot
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Turtle.class, 10, true, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.FOLLOW_RANGE, 35.0)
            .add(Attributes.MOVEMENT_SPEED, 0.23F)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.ARMOR, 2.0)
            .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder p_326435_) {
        super.defineSynchedData(p_326435_);
        p_326435_.define(DATA_BABY_ID, false);
        p_326435_.define(DATA_SPECIAL_TYPE_ID, 0);
        p_326435_.define(DATA_DROWNED_CONVERSION_ID, false);
    }

    public boolean isUnderWaterConverting() {
        return this.getEntityData().get(DATA_DROWNED_CONVERSION_ID);
    }

    public boolean canBreakDoors() {
        return this.canBreakDoors;
    }

    public void setCanBreakDoors(boolean p_34337_) {
        if (this.supportsBreakDoorGoal() && GoalUtils.hasGroundPathNavigation(this)) {
            if (this.canBreakDoors != p_34337_) {
                this.canBreakDoors = p_34337_;
                ((GroundPathNavigation)this.getNavigation()).setCanOpenDoors(p_34337_);
                if (p_34337_) {
                    this.goalSelector.addGoal(1, this.breakDoorGoal);
                } else {
                    this.goalSelector.removeGoal(this.breakDoorGoal);
                }
            }
        } else if (this.canBreakDoors) {
            this.goalSelector.removeGoal(this.breakDoorGoal);
            this.canBreakDoors = false;
        }
    }

    public boolean supportsBreakDoorGoal() {
        return true;
    }

    @Override
    public boolean isBaby() {
        return this.getEntityData().get(DATA_BABY_ID);
    }

    @Override
    protected int getBaseExperienceReward() {
        final int previousReward = this.xpReward; // Paper - store previous value to reset after calculating XP reward
        if (this.isBaby()) {
            this.xpReward = (int)((double)this.xpReward * 2.5);
        }

        // Paper start - store previous value to reset after calculating XP reward
        int reward = super.getBaseExperienceReward();
        this.xpReward = previousReward;
        return reward;
        // Paper end - store previous value to reset after calculating XP reward
    }

    @Override
    public void setBaby(boolean p_34309_) {
        this.getEntityData().set(DATA_BABY_ID, p_34309_);
        if (this.level() != null && !this.level().isClientSide) {
            AttributeInstance attributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
            attributeinstance.removeModifier(this.babyModifier.id()); // Paper - Make baby speed configurable
            if (p_34309_) {
                attributeinstance.addTransientModifier(this.babyModifier); // Paper - Make baby speed configurable
            }
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> p_34307_) {
        if (DATA_BABY_ID.equals(p_34307_)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(p_34307_);
    }

    protected boolean convertsInWater() {
        return true;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.isAlive() && !this.isNoAi()) {
            if (this.isUnderWaterConverting()) {
                --this.conversionTime; // Paper - remove anti tick skipping measures / wall time
                if (this.conversionTime < 0) {
                    this.doUnderWaterConversion();
                }
            } else if (this.convertsInWater()) {
                if (this.isEyeInFluid(FluidTags.WATER)) {
                    this.inWaterTime++;
                    if (this.inWaterTime >= 600) {
                        this.startUnderWaterConversion(300);
                    }
                } else {
                    this.inWaterTime = -1;
                }
            }
        }

        super.tick();
    }

    @Override
    public void aiStep() {
        super.aiStep();
    }

    // Paper start - Add more Zombie API
    public void stopDrowning() {
        this.conversionTime = -1;
        this.getEntityData().set(Zombie.DATA_DROWNED_CONVERSION_ID, false);
    }
    // Paper end - Add more Zombie API

    public void startUnderWaterConversion(int p_34279_) {
        this.conversionTime = p_34279_;
        this.getEntityData().set(DATA_DROWNED_CONVERSION_ID, true);
    }

    protected void doUnderWaterConversion() {
        if (!net.neoforged.neoforge.event.EventHooks.canLivingConvert(this, EntityType.DROWNED, (timer) -> this.conversionTime = timer)) return;
        this.convertToZombieType(EntityType.DROWNED);
        if (!this.isSilent()) {
            this.level().levelEvent(null, 1040, this.blockPosition(), 0);
        }
    }

    protected void convertToZombieType(EntityType<? extends Zombie> p_34311_) {
        Zombie zombie = this.convertTo(p_34311_, true, org.bukkit.event.entity.EntityTransformEvent.TransformReason.DROWNED, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DROWNED);
        if (zombie != null) {
            zombie.handleAttributes(zombie.level().getCurrentDifficultyAt(zombie.blockPosition()).getSpecialMultiplier());
            zombie.setCanBreakDoors(zombie.supportsBreakDoorGoal() && this.canBreakDoors());
            net.neoforged.neoforge.event.EventHooks.onLivingConvert(this, zombie);
            // CraftBukkit start - SPIGOT-5208: End conversion to stop event spam
        } else {
            ((org.bukkit.entity.Zombie) getBukkitEntity()).setConversionTime(-1);
            // CraftBukkit end
        }
    }

    public boolean shouldBurnInDay() { return this.isSunSensitive(); } // Purpur - for ABI compatibility - API for any mob to burn daylight
    public boolean isSunSensitive() {
        return this.shouldBurnInDay; // Paper - Add more Zombie API
    }

    // Paper start - Add more Zombie API
    public void setShouldBurnInDay(boolean shouldBurnInDay) {
        this.shouldBurnInDay = shouldBurnInDay;
    }
    // Paper end - Add more Zombie API

    @Override
    public boolean hurt(DamageSource p_34288_, float p_34289_) {
        if (!super.hurt(p_34288_, p_34289_)) {
            return false;
        } else if (!(this.level() instanceof ServerLevel)) {
            return false;
        } else {
            ServerLevel serverlevel = (ServerLevel)this.level();
            LivingEntity livingentity = this.getTarget();
            if (livingentity == null && p_34288_.getEntity() instanceof LivingEntity) {
                livingentity = (LivingEntity)p_34288_.getEntity();
            }

            if (livingentity != null
                && this.level().getDifficulty() == Difficulty.HARD
                && (double)this.random.nextFloat() < this.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE)
                && this.level().getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                int i = Mth.floor(this.getX());
                int j = Mth.floor(this.getY());
                int k = Mth.floor(this.getZ());
                Zombie zombie = new Zombie(this.level());

                for (int l = 0; l < 50; l++) {
                    int i1 = i + Mth.nextInt(this.random, 7, 40) * Mth.nextInt(this.random, -1, 1);
                    int j1 = j + Mth.nextInt(this.random, 7, 40) * Mth.nextInt(this.random, -1, 1);
                    int k1 = k + Mth.nextInt(this.random, 7, 40) * Mth.nextInt(this.random, -1, 1);
                    BlockPos blockpos = new BlockPos(i1, j1, k1);
                    EntityType<?> entitytype = zombie.getType();
                    if (SpawnPlacements.isSpawnPositionOk(entitytype, this.level(), blockpos)
                        && SpawnPlacements.checkSpawnRules(entitytype, serverlevel, MobSpawnType.REINFORCEMENT, blockpos, this.level().random)) {
                        zombie.setPos((double)i1, (double)j1, (double)k1);
                        if (!this.level().hasNearbyAlivePlayerThatAffectsSpawning((double)i1, (double)j1, (double)k1, 7.0)
                            && this.level().isUnobstructed(zombie)
                            && this.level().noCollision(zombie)
                            && !this.level().containsAnyLiquid(zombie.getBoundingBox())) {
                            zombie.setTargetReason(org.bukkit.event.entity.EntityTargetEvent.TargetReason.REINFORCEMENT_TARGET, true); // CraftBukkit
                            zombie.setTarget(livingentity);
                            zombie.finalizeSpawn(serverlevel, this.level().getCurrentDifficultyAt(zombie.blockPosition()), MobSpawnType.REINFORCEMENT, null);
                            zombie.spawnReason(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.REINFORCEMENTS);
                            serverlevel.addFreshEntityWithPassengers(zombie); // CraftBukkit
                            AttributeInstance attributeinstance = this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
                            AttributeModifier attributemodifier = attributeinstance.getModifier(REINFORCEMENT_CALLER_CHARGE_ID);
                            double d0 = attributemodifier != null ? attributemodifier.amount() : 0.0;
                            attributeinstance.removeModifier(REINFORCEMENT_CALLER_CHARGE_ID);
                            attributeinstance.addPermanentModifier(
                                new AttributeModifier(REINFORCEMENT_CALLER_CHARGE_ID, d0 - 0.05, AttributeModifier.Operation.ADD_VALUE)
                            );
                            zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).addPermanentModifier(ZOMBIE_REINFORCEMENT_CALLEE_CHARGE);
                            break;
                        }
                    }
                }
            }

            return true;
        }
    }

    @Override
    public boolean doHurtTarget(Entity p_34276_) {
        boolean flag = super.doHurtTarget(p_34276_);
        if (flag) {
            float f = this.level().getCurrentDifficultyAt(this.blockPosition()).getEffectiveDifficulty();
            if (this.getMainHandItem().isEmpty() && this.isOnFire() && this.random.nextFloat() < f * 0.3F) {
                // CraftBukkit start
                org.bukkit.event.entity.EntityCombustByEntityEvent event = new org.bukkit.event.entity.EntityCombustByEntityEvent(this.getBukkitEntity(), p_34276_.getBukkitEntity(), (float) (2 * (int) f)); // PAIL: fixme
                this.level().getCraftServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    p_34276_.igniteForSeconds(event.getDuration(), false);
                }
                // CraftBukkit end
            }
        }

        return flag;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource p_34327_) {
        return SoundEvents.ZOMBIE_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_DEATH;
    }

    protected SoundEvent getStepSound() {
        return SoundEvents.ZOMBIE_STEP;
    }

    @Override
    protected void playStepSound(BlockPos p_34316_, BlockState p_34317_) {
        this.playSound(this.getStepSound(), 0.15F, 1.0F);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource p_219165_, DifficultyInstance p_219166_) {
        super.populateDefaultEquipmentSlots(p_219165_, p_219166_);
        if (p_219165_.nextFloat() < (this.level().getDifficulty() == Difficulty.HARD ? 0.05F : 0.01F)) {
            int i = p_219165_.nextInt(3);
            if (i == 0) {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            } else {
                this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag p_34319_) {
        super.addAdditionalSaveData(p_34319_);
        p_34319_.putBoolean("IsBaby", this.isBaby());
        p_34319_.putBoolean("CanBreakDoors", this.canBreakDoors());
        p_34319_.putInt("InWaterTime", this.isInWater() ? this.inWaterTime : -1);
        p_34319_.putInt("DrownedConversionTime", this.isUnderWaterConverting() ? this.conversionTime : -1);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag p_34305_) {
        super.readAdditionalSaveData(p_34305_);
        this.setBaby(p_34305_.getBoolean("IsBaby"));
        this.setCanBreakDoors(p_34305_.getBoolean("CanBreakDoors"));
        this.inWaterTime = p_34305_.getInt("InWaterTime");
        if (p_34305_.contains("DrownedConversionTime", 99) && p_34305_.getInt("DrownedConversionTime") > -1) {
            this.startUnderWaterConversion(p_34305_.getInt("DrownedConversionTime"));
        }
    }

    @Override
    public boolean killedEntity(ServerLevel p_219160_, LivingEntity p_219161_) {
        boolean flag = super.killedEntity(p_219160_, p_219161_);
        final double fallbackChance = p_219160_.getDifficulty() == Difficulty.HARD ? 100d : p_219160_.getDifficulty() == Difficulty.NORMAL ? 50d : 0d; // Paper - Configurable chance of villager zombie infection
        if (this.random.nextDouble() * 100 < p_219160_.paperConfig().entities.behavior.zombieVillagerInfectionChance.or(fallbackChance) && p_219161_ instanceof Villager villager && net.neoforged.neoforge.event.EventHooks.canLivingConvert(p_219161_, EntityType.ZOMBIE_VILLAGER, (timer) -> {})) {
            if (p_219160_.getDifficulty() != Difficulty.HARD && this.random.nextBoolean()) {
                return flag;
            }
            // CraftBukkit start
            flag = zombifyVillager(p_219160_, villager, this.blockPosition(), this.isSilent(), org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.INFECTION) == null;
        }
        return flag;
    }

    public static ZombieVillager zombifyVillager(ServerLevel worldserver, Villager entityvillager, net.minecraft.core.BlockPos blockPosition, boolean silent, CreatureSpawnEvent.SpawnReason spawnReason) {
        {
            ZombieVillager entityzombievillager = (ZombieVillager) entityvillager.convertTo(EntityType.ZOMBIE_VILLAGER, false, EntityTransformEvent.TransformReason.INFECTION, spawnReason);
            // CraftBukkit end

            if (entityzombievillager != null) {
                entityzombievillager.finalizeSpawn(worldserver, worldserver.getCurrentDifficultyAt(entityzombievillager.blockPosition()), MobSpawnType.CONVERSION, new Zombie.ZombieGroupData(false, true));
                entityzombievillager.setVillagerData(entityvillager.getVillagerData());
                entityzombievillager.setGossips((Tag) entityvillager.getGossips().store(NbtOps.INSTANCE));
                entityzombievillager.setTradeOffers(entityvillager.getOffers().copy());
                entityzombievillager.setVillagerXp(entityvillager.getVillagerXp());
                // CraftBukkit start
                if (!silent) {
                    worldserver.levelEvent((Player) null, 1026, blockPosition, 0);
                }

                // flag = false;
            }

            return entityzombievillager;
        }
        // CraftBukkit end
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose p_316771_) {
        return this.isBaby() ? BABY_DIMENSIONS : super.getDefaultDimensions(p_316771_);
    }

    @Override
    public boolean canHoldItem(ItemStack p_34332_) {
        return p_34332_.is(Items.EGG) && this.isBaby() && this.isPassenger() ? false : super.canHoldItem(p_34332_);
    }

    @Override
    public boolean wantsToPickUp(ItemStack p_182400_) {
        return p_182400_.is(Items.GLOW_INK_SAC) ? false : super.wantsToPickUp(p_182400_);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor p_34297_, DifficultyInstance p_34298_, MobSpawnType p_34299_, @Nullable SpawnGroupData p_34300_) {
        RandomSource randomsource = p_34297_.getRandom();
        p_34300_ = super.finalizeSpawn(p_34297_, p_34298_, p_34299_, p_34300_);
        float f = p_34298_.getSpecialMultiplier();
        this.setCanPickUpLoot(this.level().paperConfig().entities.behavior.mobsCanAlwaysPickUpLoot.zombies || randomsource.nextFloat() < 0.55F * f);
        if (p_34300_ == null) {
            p_34300_ = new Zombie.ZombieGroupData(getSpawnAsBabyOdds(randomsource), true);
        }

        if (p_34300_ instanceof Zombie.ZombieGroupData zombie$zombiegroupdata) {
            if (zombie$zombiegroupdata.isBaby) {
                this.setBaby(true);
                if (zombie$zombiegroupdata.canSpawnJockey) {
                    if ((double)randomsource.nextFloat() < 0.05) {
                        List<Chicken> list = p_34297_.getEntitiesOfClass(
                            Chicken.class, this.getBoundingBox().inflate(5.0, 3.0, 5.0), EntitySelector.ENTITY_NOT_BEING_RIDDEN
                        );
                        if (!list.isEmpty()) {
                            Chicken chicken = list.get(0);
                            chicken.setChickenJockey(true);
                            this.startRiding(chicken);
                        }
                    } else if ((double)randomsource.nextFloat() < 0.05) {
                        Chicken chicken1 = EntityType.CHICKEN.create(this.level());
                        if (chicken1 != null) {
                            chicken1.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                            chicken1.finalizeSpawn(p_34297_, p_34298_, MobSpawnType.JOCKEY, null);
                            chicken1.setChickenJockey(true);
                            this.startRiding(chicken1);
                            chicken1.spawnReason(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.MOUNT); // CraftBukkit
                            p_34297_.addFreshEntity(chicken1);
                        }
                    }
                }
            }

            this.setCanBreakDoors(this.supportsBreakDoorGoal() && randomsource.nextFloat() < f * 0.1F);
            this.populateDefaultEquipmentSlots(randomsource, p_34298_);
            this.populateDefaultEquipmentEnchantments(p_34297_, randomsource, p_34298_);
        }

        if (this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            LocalDate localdate = LocalDate.now();
            int i = localdate.get(ChronoField.DAY_OF_MONTH);
            int j = localdate.get(ChronoField.MONTH_OF_YEAR);
            if (j == 10 && i == 31 && randomsource.nextFloat() < 0.25F) {
                this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(randomsource.nextFloat() < 0.1F ? Blocks.JACK_O_LANTERN : Blocks.CARVED_PUMPKIN));
                this.armorDropChances[EquipmentSlot.HEAD.getIndex()] = 0.0F;
            }
        }

        this.handleAttributes(f);
        return p_34300_;
    }

    public static boolean getSpawnAsBabyOdds(RandomSource p_219163_) {
        return p_219163_.nextFloat() < 0.05F;
    }

    protected void handleAttributes(float p_34340_) {
        this.randomizeReinforcementsChance();
        this.getAttribute(Attributes.KNOCKBACK_RESISTANCE)
            .addOrReplacePermanentModifier(
                new AttributeModifier(RANDOM_SPAWN_BONUS_ID, this.random.nextDouble() * 0.05F, AttributeModifier.Operation.ADD_VALUE)
            );
        double d0 = this.random.nextDouble() * 1.5 * (double)p_34340_;
        if (d0 > 1.0) {
            this.getAttribute(Attributes.FOLLOW_RANGE)
                .addOrReplacePermanentModifier(new AttributeModifier(ZOMBIE_RANDOM_SPAWN_BONUS_ID, d0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        if (this.random.nextFloat() < p_34340_ * 0.05F) {
            this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE)
                .addOrReplacePermanentModifier(
                    new AttributeModifier(LEADER_ZOMBIE_BONUS_ID, this.random.nextDouble() * 0.25 + 0.5, AttributeModifier.Operation.ADD_VALUE)
                );
            this.getAttribute(Attributes.MAX_HEALTH)
                .addOrReplacePermanentModifier(
                    new AttributeModifier(LEADER_ZOMBIE_BONUS_ID, this.random.nextDouble() * 3.0 + 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                );
            this.setCanBreakDoors(this.supportsBreakDoorGoal());
        }
    }

    protected void randomizeReinforcementsChance() {
        this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(this.random.nextDouble() * 0.1F);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel p_348597_, DamageSource p_34291_, boolean p_34293_) {
        super.dropCustomDeathLoot(p_348597_, p_34291_, p_34293_);
        if (p_34291_.getEntity() instanceof Creeper creeper && creeper.canDropMobsSkull()) {
            ItemStack itemstack = this.getSkull();
            if (!itemstack.isEmpty()) {
                creeper.increaseDroppedSkulls();
                this.spawnAtLocation(itemstack);
            }
        }
    }

    protected ItemStack getSkull() {
        return new ItemStack(Items.ZOMBIE_HEAD);
    }

    class ZombieAttackTurtleEggGoal extends RemoveBlockGoal {
        ZombieAttackTurtleEggGoal(PathfinderMob p_34344_, double p_34345_, int p_34346_) {
            super(Blocks.TURTLE_EGG, p_34344_, p_34345_, p_34346_);
        }

        @Override
        public void playDestroyProgressSound(LevelAccessor p_34351_, BlockPos p_34352_) {
            p_34351_.playSound(null, p_34352_, SoundEvents.ZOMBIE_DESTROY_EGG, SoundSource.HOSTILE, 0.5F, 0.9F + Zombie.this.random.nextFloat() * 0.2F);
        }

        @Override
        public void playBreakSound(Level p_34348_, BlockPos p_34349_) {
            p_34348_.playSound(null, p_34349_, SoundEvents.TURTLE_EGG_BREAK, SoundSource.BLOCKS, 0.7F, 0.9F + p_34348_.random.nextFloat() * 0.2F);
        }

        @Override
        public double acceptedDistance() {
            return 1.14;
        }
    }

    public static class ZombieGroupData implements SpawnGroupData {
        public final boolean isBaby;
        public final boolean canSpawnJockey;

        public ZombieGroupData(boolean p_34357_, boolean p_34358_) {
            this.isBaby = p_34357_;
            this.canSpawnJockey = p_34358_;
        }
    }
}
