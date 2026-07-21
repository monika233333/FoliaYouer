package net.minecraft.world.entity.projectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.bukkit.craftbukkit.event.CraftEventFactory;

public class ThrownExperienceBottle extends ThrowableItemProjectile {
    public ThrownExperienceBottle(EntityType<? extends ThrownExperienceBottle> p_37510_, Level p_37511_) {
        super(p_37510_, p_37511_);
    }

    public ThrownExperienceBottle(Level p_37518_, LivingEntity p_37519_) {
        super(EntityType.EXPERIENCE_BOTTLE, p_37519_, p_37518_);
    }

    public ThrownExperienceBottle(Level p_37513_, double p_37514_, double p_37515_, double p_37516_) {
        super(EntityType.EXPERIENCE_BOTTLE, p_37514_, p_37515_, p_37516_, p_37513_);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.EXPERIENCE_BOTTLE;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.07;
    }

    @Override
    protected void onHit(HitResult p_37521_) {
        super.onHit(p_37521_);
        if (this.level() instanceof ServerLevel) {
            // CraftBukkit - moved to after event
            int i = 3 + this.level().random.nextInt(5) + this.level().random.nextInt(5);

            // CraftBukkit start
            org.bukkit.event.entity.ExpBottleEvent event = CraftEventFactory.callExpBottleEvent(this, p_37521_, i);
            i = event.getExperience();
            if (event.getShowEffect()) {
                this.level().levelEvent(2002, this.blockPosition(), PotionContents.getColor(Potions.WATER));
            }
            // CraftBukkit end

            ExperienceOrb.award((ServerLevel)this.level(), this.position(), i, org.bukkit.entity.ExperienceOrb.SpawnReason.EXP_BOTTLE, this.getOwner(), this); // Paper
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }
    }
}
