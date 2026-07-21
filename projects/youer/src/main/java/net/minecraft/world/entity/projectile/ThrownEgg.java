package net.minecraft.world.entity.projectile;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class ThrownEgg extends ThrowableItemProjectile {
    private static final EntityDimensions ZERO_SIZED_DIMENSIONS = EntityDimensions.fixed(0.0F, 0.0F);

    public ThrownEgg(EntityType<? extends ThrownEgg> p_37473_, Level p_37474_) {
        super(p_37473_, p_37474_);
    }

    public ThrownEgg(Level p_37481_, LivingEntity p_37482_) {
        super(EntityType.EGG, p_37482_, p_37481_);
    }

    public ThrownEgg(Level p_37476_, double p_37477_, double p_37478_, double p_37479_) {
        super(EntityType.EGG, p_37477_, p_37478_, p_37479_, p_37476_);
    }

    @Override
    public void handleEntityEvent(byte p_37484_) {
        if (p_37484_ == 3) {
            double d0 = 0.08;

            for (int i = 0; i < 8; i++) {
                this.level()
                    .addParticle(
                        new ItemParticleOption(ParticleTypes.ITEM, this.getItem()),
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        ((double)this.random.nextFloat() - 0.5) * 0.08,
                        ((double)this.random.nextFloat() - 0.5) * 0.08,
                        ((double)this.random.nextFloat() - 0.5) * 0.08
                    );
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult p_37486_) {
        super.onHitEntity(p_37486_);
        p_37486_.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
    }

    @Override
    protected void onHit(HitResult p_37488_) {
        super.onHit(p_37488_);
        if (!this.level().isClientSide) {
            // CraftBukkit start
            boolean hatching = this.random.nextInt(8) == 0;
            if (true) {
                // CraftBukkit end
                int i = 1;
                if (this.random.nextInt(32) == 0) {
                    i = 4;
                }

                // CraftBukkit start
                org.bukkit.entity.EntityType hatchingType = org.bukkit.entity.EntityType.CHICKEN;

                net.minecraft.world.entity.Entity shooter = this.getOwner();
                if (!hatching) {
                    i = 0;
                }

                if (shooter instanceof net.minecraft.server.level.ServerPlayer) {
                    org.bukkit.event.player.PlayerEggThrowEvent event = new org.bukkit.event.player.PlayerEggThrowEvent((org.bukkit.entity.Player) shooter.getBukkitEntity(), (org.bukkit.entity.Egg) this.getBukkitEntity(), hatching, (byte) i, hatchingType);
                    this.level().getCraftServer().getPluginManager().callEvent(event);

                    i = event.getNumHatches();
                    hatching = event.isHatching();
                    hatchingType = event.getHatchingType();
                    // If hatching is set to false, ensure child count is 0
                    if (!hatching) {
                        i = 0;
                    }
                }
                // CraftBukkit end

                // Paper start - Add ThrownEggHatchEvent
                com.destroystokyo.paper.event.entity.ThrownEggHatchEvent event = new com.destroystokyo.paper.event.entity.ThrownEggHatchEvent((org.bukkit.entity.Egg) getBukkitEntity(), hatching, (byte) i, hatchingType);
                event.callEvent();
                hatching = event.isHatching();
                i = hatching ? event.getNumHatches() : 0; // If hatching is set to false, ensure child count is 0
                hatchingType = event.getHatchingType();
                // Paper end - Add ThrownEggHatchEvent

                for (int j = 0; j < i; j++) {
                    Chicken chicken = EntityType.CHICKEN.create(this.level());
                    chicken = (Chicken) this.level().getWorld().makeEntity(new org.bukkit.Location(this.level().getWorld(), this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F), hatchingType.getEntityClass()); // CraftBukkit
                    if (chicken != null) {
                        // CraftBukkit start
                        if (chicken.getBukkitEntity() instanceof org.bukkit.entity.Ageable) {
                            ((org.bukkit.entity.Ageable) chicken.getBukkitEntity()).setBaby();
                        }
                        // CraftBukkit end
                        chicken.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                        if (!chicken.fudgePositionAfterSizeChange(ZERO_SIZED_DIMENSIONS)) {
                            break;
                        }

                        chicken.spawnReason(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.EGG);
                        this.level().addFreshEntity(chicken);
                    }
                }
            }

            this.level().broadcastEntityEvent(this, (byte)3);
            this.removeReason(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
            this.discard();
        }
    }

    @Override
    protected Item getDefaultItem() {
        return Items.EGG;
    }
}
