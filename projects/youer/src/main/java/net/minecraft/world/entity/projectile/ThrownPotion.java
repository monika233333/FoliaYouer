package net.minecraft.world.entity.projectile;

import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.event.CraftEventFactory;

public class ThrownPotion extends ThrowableItemProjectile implements ItemSupplier {
    public static final double SPLASH_RANGE = 4.0;
    private static final double SPLASH_RANGE_SQ = 16.0;
    public static final Predicate<LivingEntity> WATER_SENSITIVE_OR_ON_FIRE = p_350140_ -> p_350140_.isSensitiveToWater() || p_350140_.isOnFire();

    public ThrownPotion(EntityType<? extends ThrownPotion> p_37527_, Level p_37528_) {
        super(p_37527_, p_37528_);
    }

    public ThrownPotion(Level p_37535_, LivingEntity p_37536_) {
        super(EntityType.POTION, p_37536_, p_37535_);
    }

    public ThrownPotion(Level p_37530_, double p_37531_, double p_37532_, double p_37533_) {
        super(EntityType.POTION, p_37531_, p_37532_, p_37533_, p_37530_);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SPLASH_POTION;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    protected void onHitBlock(BlockHitResult p_37541_) {
        super.onHitBlock(p_37541_);
        if (!this.level().isClientSide) {
            ItemStack itemstack = this.getItem();
            Direction direction = p_37541_.getDirection();
            BlockPos blockpos = p_37541_.getBlockPos();
            BlockPos blockpos1 = blockpos.relative(direction);
            PotionContents potioncontents = itemstack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            if (potioncontents.is(Potions.WATER)) {
                this.dowseFire(blockpos1);
                this.dowseFire(blockpos1.relative(direction.getOpposite()));

                for (Direction direction1 : Direction.Plane.HORIZONTAL) {
                    this.dowseFire(blockpos1.relative(direction1));
                }
            }
        }
    }

    public void splash(@Nullable HitResult p_37543_) {
        if (!this.level().isClientSide) {
            ItemStack itemstack = this.getItem();
            PotionContents potioncontents = itemstack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            if (potioncontents.is(Potions.WATER)) {
                this.applyWater();
            } else if (true || potioncontents.hasEffects()) { // CraftBukkit - Call event even if no effects to apply
                if (this.isLingering()) {
                    makeAreaOfEffectCloud$position = p_37543_;
                    this.makeAreaOfEffectCloud(potioncontents);
                } else {
                    applySplash$position = p_37543_;
                    this.applySplash(
                            potioncontents.getAllEffects(), p_37543_ != null && p_37543_.getType() == HitResult.Type.ENTITY ? ((EntityHitResult)p_37543_).getEntity() : null
                    );
                }
            }

            int i = potioncontents.potion().isPresent() && potioncontents.potion().get().value().hasInstantEffects() ? 2007 : 2002;
            this.level().levelEvent(i, this.blockPosition(), potioncontents.getColor());
            this.removeReason(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT);
            this.discard(); // CraftBukkit - add Bukkit remove cause
        }
    }

    @Override
    protected void onHit(HitResult p_37543_) {
        super.onHit(p_37543_);
        if (!this.level().isClientSide) {
            ItemStack itemstack = this.getItem();
            PotionContents potioncontents = itemstack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);

            boolean showParticles = true; // Paper - Fix potions splash events
            if (potioncontents.is(Potions.WATER)) {
                this.applyWater();
            } else if (true || potioncontents.hasEffects()) { // CraftBukkit - Call event even if no effects to apply
                if (this.isLingering()) {
                    makeAreaOfEffectCloud$position = p_37543_;
                    this.makeAreaOfEffectCloud(potioncontents);
                } else {
                    applySplash$position = p_37543_;
                    this.applySplash(
                        potioncontents.getAllEffects(), p_37543_ != null && p_37543_.getType() == HitResult.Type.ENTITY ? ((EntityHitResult)p_37543_).getEntity() : null
                    );
                }
            }

            int i = potioncontents.potion().isPresent() && potioncontents.potion().get().value().hasInstantEffects() ? 2007 : 2002;
            this.level().levelEvent(i, this.blockPosition(), potioncontents.getColor());
            this.removeReason(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT);
            this.discard(); // CraftBukkit - add Bukkit remove cause
        }
    }

    private void applyWater() {
        AABB aabb = this.getBoundingBox().inflate(4.0, 2.0, 4.0);

        for (LivingEntity livingentity : this.level().getEntitiesOfClass(LivingEntity.class, aabb, WATER_SENSITIVE_OR_ON_FIRE)) {
            double d0 = this.distanceToSqr(livingentity);
            if (d0 < 16.0) {
                if (livingentity.isSensitiveToWater()) {
                    livingentity.hurt(this.damageSources().indirectMagic(this, this.getOwner()), 1.0F);
                }

                if (livingentity.isOnFire() && livingentity.isAlive()) {
                    livingentity.extinguishFire();
                }
            }
        }

        for (Axolotl axolotl : this.level().getEntitiesOfClass(Axolotl.class, aabb)) {
            axolotl.rehydrate();
        }
    }

    private HitResult applySplash$position = null;
    private void applySplash(Iterable<MobEffectInstance> p_330815_, @Nullable Entity p_37549_) {
        AABB aabb = this.getBoundingBox().inflate(4.0, 2.0, 4.0);
        List<LivingEntity> list = this.level().getEntitiesOfClass(LivingEntity.class, aabb);
        java.util.Map<org.bukkit.entity.LivingEntity, Double> affected = new java.util.HashMap<>(); // CraftBukkit
        if (!list.isEmpty()) {
            for (LivingEntity livingentity : list) {
                if (livingentity.isAffectedByPotions()) {
                    double d0 = this.distanceToSqr(livingentity);
                    if (d0 < 16.0) {
                        double d1;
                        if (livingentity == p_37549_) {
                            d1 = 1.0;
                        } else {
                            d1 = 1.0 - Math.sqrt(d0) / 4.0;
                        }
                        affected.put((org.bukkit.entity.LivingEntity) livingentity.getBukkitEntity(), d1);
                    }
                }
            }
        }
        org.bukkit.event.entity.PotionSplashEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPotionSplashEvent(this, applySplash$position, affected);
        applySplash$position = null;
        if (!event.isCancelled() && list != null && !list.isEmpty()) { // do not process effects if there are no effects to process
            Entity entity = this.getEffectSource();
            for (org.bukkit.entity.LivingEntity victim : event.getAffectedEntities()) {
                if (!(victim instanceof CraftLivingEntity)) {
                    continue;
                }

                net.minecraft.world.entity.LivingEntity livingentity = ((CraftLivingEntity) victim).getHandle();
                double d1 = event.getIntensity(victim);
                for (MobEffectInstance mobeffectinstance : p_330815_) {
                    Holder<MobEffect> holder = mobeffectinstance.getEffect();
                    // CraftBukkit start - Abide by PVP settings - for players only!
                    if (!this.level().pvpMode && this.getOwner() instanceof ServerPlayer && livingentity instanceof ServerPlayer && livingentity != this.getOwner()) {
                        MobEffect mobeffectlist = holder.value();
                        if (mobeffectlist == MobEffects.MOVEMENT_SLOWDOWN || mobeffectlist == MobEffects.DIG_SLOWDOWN || mobeffectlist == MobEffects.HARM || mobeffectlist == MobEffects.BLINDNESS
                                || mobeffectlist == MobEffects.HUNGER || mobeffectlist == MobEffects.WEAKNESS || mobeffectlist == MobEffects.POISON) {
                            continue;
                        }
                    }
                    // CraftBukkit end
                    if (holder.value().isInstantenous()) {
                        holder.value().applyInstantenousEffect(this, this.getOwner(), livingentity, mobeffectinstance.getAmplifier(), d1);
                    } else {
                        int i = mobeffectinstance.mapDuration(p_267930_ -> (int) (d1 * (double) p_267930_ + 0.5));
                        MobEffectInstance mobeffectinstance1 = new MobEffectInstance(
                                holder, i, mobeffectinstance.getAmplifier(), mobeffectinstance.isAmbient(), mobeffectinstance.isVisible()
                        );
                        if (!mobeffectinstance1.endsWithin(20)) {
                            livingentity.addEffectCause(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.POTION_SPLASH); // CraftBukkit
                            livingentity.addEffect(mobeffectinstance1, entity);
                        }
                    }
                }
            }
        }
    }

    private @Nullable HitResult makeAreaOfEffectCloud$position;
    private void makeAreaOfEffectCloud(PotionContents p_332124_) {
        AreaEffectCloud areaeffectcloud = new AreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
        if (this.getOwner() instanceof LivingEntity livingentity) {
            areaeffectcloud.setOwner(livingentity);
        }

        areaeffectcloud.setRadius(3.0F);
        areaeffectcloud.setRadiusOnUse(-0.5F);
        areaeffectcloud.setWaitTime(10);
        areaeffectcloud.setRadiusPerTick(-areaeffectcloud.getRadius() / (float)areaeffectcloud.getDuration());
        areaeffectcloud.setPotionContents(p_332124_);
        boolean noEffects = p_332124_.hasEffects(); // Paper - Fix potions splash events
        // CraftBukkit start
        org.bukkit.event.entity.LingeringPotionSplashEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callLingeringPotionSplashEvent(this, makeAreaOfEffectCloud$position, areaeffectcloud);
        if (!(event.isCancelled() || areaeffectcloud.isRemoved() || (!event.allowsEmptyCreation() && (noEffects && !areaeffectcloud.potionContents.hasEffects())))) { // Paper - don't spawn area effect cloud if the effects were empty and not changed during the event handling
            this.level().addFreshEntity(areaeffectcloud);
        } else {
            areaeffectcloud.discard(null); // CraftBukkit - add Bukkit remove cause
        }
        // CraftBukkit end
    }

    public boolean isLingering() {
        return this.getItem().is(Items.LINGERING_POTION);
    }

    private void dowseFire(BlockPos p_150193_) {
        BlockState blockstate = this.level().getBlockState(p_150193_);
        if (blockstate.is(BlockTags.FIRE)) {
            // CraftBukkit start
            if (CraftEventFactory.callEntityChangeBlockEvent(this, p_150193_, blockstate.getFluidState().createLegacyBlock())) { // Paper - fix wrong block state
                this.level().destroyBlock(p_150193_, false, this);
            }
            // CraftBukkit end
        } else if (AbstractCandleBlock.isLit(blockstate)) {
            AbstractCandleBlock.extinguish(null, blockstate, this.level(), p_150193_);
        } else if (CampfireBlock.isLitCampfire(blockstate)) {
            this.level().levelEvent(null, 1009, p_150193_, 0);
            CampfireBlock.dowse(this.getOwner(), this.level(), p_150193_, blockstate);
            this.level().setBlockAndUpdate(p_150193_, blockstate.setValue(CampfireBlock.LIT, Boolean.valueOf(false)));
        }
    }

    @Override
    public DoubleDoubleImmutablePair calculateHorizontalHurtKnockbackDirection(LivingEntity p_345103_, DamageSource p_345887_) {
        double d0 = p_345103_.position().x - this.position().x;
        double d1 = p_345103_.position().z - this.position().z;
        return DoubleDoubleImmutablePair.of(d0, d1);
    }
}
