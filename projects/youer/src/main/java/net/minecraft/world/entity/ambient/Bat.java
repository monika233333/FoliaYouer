package net.minecraft.world.entity.ambient;

import java.time.LocalDate;
import java.time.temporal.ChronoField;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.event.CraftEventFactory;

public class Bat extends AmbientCreature {
    public static final float FLAP_LENGTH_SECONDS = 0.5F;
    public static final float TICKS_PER_FLAP = 10.0F;
    private static final EntityDataAccessor<Byte> DATA_ID_FLAGS = SynchedEntityData.defineId(Bat.class, EntityDataSerializers.BYTE);
    private static final int FLAG_RESTING = 1;
    private static final TargetingConditions BAT_RESTING_TARGETING = TargetingConditions.forNonCombat().range(4.0);
    public final AnimationState flyAnimationState = new AnimationState();
    public final AnimationState restAnimationState = new AnimationState();
    @Nullable
    public BlockPos targetPosition;

    public Bat(EntityType<? extends Bat> p_27412_, Level p_27413_) {
        super(p_27412_, p_27413_);
        this.moveControl = new org.purpurmc.purpur.controller.FlyingWithSpacebarMoveControllerWASD(this, 0.075F); // Purpur
        if (!p_27413_.isClientSide) {
            this.setResting(true);
        }
    }

    // Purpur start
    @Override
    public boolean shouldSendAttribute(net.minecraft.world.entity.ai.attributes.Attribute attribute) { return attribute != Attributes.FLYING_SPEED.value(); } // Fixes log spam on clients

    @Override
    public boolean isRidable() {
        return level().purpurConfig.batRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.batRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.batControllable;
    }

    @Override
    public double getMaxY() {
        return level().purpurConfig.batMaxY;
    }

    @Override
    public void onMount(Player rider) {
        super.onMount(rider);
        if (isResting()) {
            setResting(false);
            level().levelEvent(null, 1025, new BlockPos(this).above(), 0);
        }
    }

    @Override
    public void travel(Vec3 vec3) {
        super.travel(vec3);
        if (getRider() != null && this.isControllable() && !onGround) {
            float speed = (float) getAttributeValue(Attributes.FLYING_SPEED) * 2;
            setSpeed(speed);
            Vec3 mot = getDeltaMovement();
            move(net.minecraft.world.entity.MoverType.SELF, mot.multiply(speed, 0.25, speed));
            setDeltaMovement(mot.scale(0.9D));
        }
    }
    // Purpur end

    @Override
    public boolean isFlapping() {
        return !this.isResting() && (float)this.tickCount % 10.0F == 0.0F;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder p_326297_) {
        super.defineSynchedData(p_326297_);
        p_326297_.define(DATA_ID_FLAGS, (byte)0);
    }

    @Override
    public float getSoundVolume() {
        return 0.1F;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.95F;
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound() {
        return this.isResting() && this.random.nextInt(4) != 0 ? null : SoundEvents.BAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource p_27451_) {
        return SoundEvents.BAT_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.BAT_DEATH;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity p_27415_) {
    }

    @Override
    protected void pushEntities() {
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.FLYING_SPEED, 0.6D); // Purpur
    }

    public boolean isResting() {
        return (this.entityData.get(DATA_ID_FLAGS) & 1) != 0;
    }

    public void setResting(boolean p_27457_) {
        byte b0 = this.entityData.get(DATA_ID_FLAGS);
        if (p_27457_) {
            this.entityData.set(DATA_ID_FLAGS, (byte)(b0 | 1));
        } else {
            this.entityData.set(DATA_ID_FLAGS, (byte)(b0 & -2));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isResting()) {
            this.setDeltaMovement(Vec3.ZERO);
            this.setPosRaw(this.getX(), (double)Mth.floor(this.getY()) + 1.0 - (double)this.getBbHeight(), this.getZ());
        } else {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.6, 1.0));
        }

        this.setupAnimationStates();
    }

    @Override
    protected void customServerAiStep() {
        // Purpur start
        if (getRider() != null && this.isControllable()) {
            Vec3 mot = getDeltaMovement();
            setDeltaMovement(mot.x(), mot.y() + (getVerticalMot() > 0 ? 0.07D : 0.0D), mot.z());
            return;
        }
        // Purpur end

        super.customServerAiStep();
        BlockPos blockpos = this.blockPosition();
        BlockPos blockpos1 = blockpos.above();
        if (this.isResting()) {
            boolean flag = this.isSilent();
            if (this.level().getBlockState(blockpos1).isRedstoneConductor(this.level(), blockpos)) {
                if (this.random.nextInt(200) == 0) {
                    this.yHeadRot = (float)this.random.nextInt(360);
                }

                if (this.level().getNearestPlayer(BAT_RESTING_TARGETING, this) != null && CraftEventFactory.handleBatToggleSleepEvent(this, true)) { // CraftBukkit - Call BatToggleSleepEvent
                    this.setResting(false);
                    if (!flag) {
                        this.level().levelEvent(null, 1025, blockpos, 0);
                    }
                }
            } else if (CraftEventFactory.handleBatToggleSleepEvent(this, true)) { // CraftBukkit - Call BatToggleSleepEvent
                this.setResting(false);
                if (!flag) {
                    this.level().levelEvent(null, 1025, blockpos, 0);
                }
            }
        } else {
            if (this.targetPosition != null
                && (!this.level().isEmptyBlock(this.targetPosition) || this.targetPosition.getY() <= this.level().getMinBuildHeight())) {
                this.targetPosition = null;
            }

            if (this.targetPosition == null || this.random.nextInt(30) == 0 || this.targetPosition.closerToCenterThan(this.position(), 2.0)) {
                this.targetPosition = BlockPos.containing(
                    this.getX() + (double)this.random.nextInt(7) - (double)this.random.nextInt(7),
                    this.getY() + (double)this.random.nextInt(6) - 2.0,
                    this.getZ() + (double)this.random.nextInt(7) - (double)this.random.nextInt(7)
                );
            }

            double d2 = (double)this.targetPosition.getX() + 0.5 - this.getX();
            double d0 = (double)this.targetPosition.getY() + 0.1 - this.getY();
            double d1 = (double)this.targetPosition.getZ() + 0.5 - this.getZ();
            Vec3 vec3 = this.getDeltaMovement();
            Vec3 vec31 = vec3.add((Math.signum(d2) * 0.5 - vec3.x) * 0.1F, (Math.signum(d0) * 0.7F - vec3.y) * 0.1F, (Math.signum(d1) * 0.5 - vec3.z) * 0.1F);
            this.setDeltaMovement(vec31);
            float f = (float)(Mth.atan2(vec31.z, vec31.x) * 180.0F / (float)Math.PI) - 90.0F;
            float f1 = Mth.wrapDegrees(f - this.getYRot());
            this.zza = 0.5F;
            this.setYRot(this.getYRot() + f1);
            if (this.random.nextInt(100) == 0 && this.level().getBlockState(blockpos1).isRedstoneConductor(this.level(), blockpos1) && CraftEventFactory.handleBatToggleSleepEvent(this, false)) { // CraftBukkit - Call BatToggleSleepEvent
                this.setResting(true);
            }
        }
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.EVENTS;
    }

    @Override
    protected void checkFallDamage(double p_27419_, boolean p_27420_, BlockState p_27421_, BlockPos p_27422_) {
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource p_27424_, float p_27425_) {
        if (this.isInvulnerableTo(p_27424_)) {
            return false;
        } else {
            if (!this.level().isClientSide && this.isResting() && CraftEventFactory.handleBatToggleSleepEvent(this, true)) { // CraftBukkit - Call BatToggleSleepEvent
                this.setResting(false);
            }

            return super.hurt(p_27424_, p_27425_);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag p_27427_) {
        super.readAdditionalSaveData(p_27427_);
        this.entityData.set(DATA_ID_FLAGS, p_27427_.getByte("BatFlags"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag p_27443_) {
        super.addAdditionalSaveData(p_27443_);
        p_27443_.putByte("BatFlags", this.entityData.get(DATA_ID_FLAGS));
    }

    public static boolean checkBatSpawnRules(
        EntityType<Bat> p_218099_, LevelAccessor p_218100_, MobSpawnType p_218101_, BlockPos p_218102_, RandomSource p_218103_
    ) {
        if (p_218102_.getY() >= p_218100_.getSeaLevel()) {
            return false;
        } else {
            int i = p_218100_.getMaxLocalRawBrightness(p_218102_);
            int j = 4;
            if (isHalloween()) {
                j = 7;
            } else if (p_218103_.nextBoolean()) {
                return false;
            }

            return i > p_218103_.nextInt(j) ? false : checkMobSpawnRules(p_218099_, p_218100_, p_218101_, p_218102_, p_218103_);
        }
    }

    // Pufferfish start - only check for spooky season once an hour
    private static boolean isSpookySeason = false;
    private static final int ONE_HOUR = 20 * 60 * 60;
    private static int lastSpookyCheck = -ONE_HOUR;
    public static boolean isHalloweenSeason(Level level) { return level.purpurConfig.forceHalloweenSeason || isHalloween(); } // Purpur
    private static boolean isHalloween() {
        if (net.minecraft.server.MinecraftServer.currentTick - lastSpookyCheck > ONE_HOUR) {
            LocalDate localdate = LocalDate.now();
            int i = localdate.get(ChronoField.DAY_OF_MONTH);
            int j = localdate.get(ChronoField.MONTH_OF_YEAR);

            isSpookySeason = j == 10 && i >= 20 || j == 11 && i <= 3;
            lastSpookyCheck = net.minecraft.server.MinecraftServer.currentTick;
        }
        return isSpookySeason;
    }
    // Pufferfish end

    private void setupAnimationStates() {
        if (this.isResting()) {
            this.flyAnimationState.stop();
            this.restAnimationState.startIfStopped(this.tickCount);
        } else {
            this.restAnimationState.stop();
            this.flyAnimationState.startIfStopped(this.tickCount);
        }
    }
}
