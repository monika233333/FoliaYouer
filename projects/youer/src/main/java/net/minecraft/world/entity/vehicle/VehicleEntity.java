package net.minecraft.world.entity.vehicle;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public abstract class VehicleEntity extends Entity {
    protected static final EntityDataAccessor<Integer> DATA_ID_HURT = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> DATA_ID_HURTDIR = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Float> DATA_ID_DAMAGE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);

    public VehicleEntity(EntityType<?> p_306130_, Level p_306167_) {
        super(p_306130_, p_306167_);
    }

    @Override
    public boolean hurt(DamageSource p_305898_, float p_305999_) {
        if (this.level().isClientSide || this.isRemoved()) {
            return true;
        } else if (this.isInvulnerableTo(p_305898_)) {
            return false;
        } else {
            // CraftBukkit start
            org.bukkit.entity.Vehicle vehicle = (org.bukkit.entity.Vehicle) this.getBukkitEntity();
            org.bukkit.entity.Entity attacker = (p_305898_.getEntity() == null) ? null : p_305898_.getEntity().getBukkitEntity();

            org.bukkit.event.vehicle.VehicleDamageEvent event = new org.bukkit.event.vehicle.VehicleDamageEvent(vehicle, attacker, (double) p_305999_);
            this.level().getCraftServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return false;
            }
            p_305999_ = (float) event.getDamage();
            // CraftBukkit end
            this.setHurtDir(-this.getHurtDir());
            this.setHurtTime(10);
            this.markHurt();
            this.setDamage(this.getDamage() + p_305999_ * 10.0F);
            this.gameEvent(GameEvent.ENTITY_DAMAGE, p_305898_.getEntity());
            boolean flag = p_305898_.getEntity() instanceof Player && ((Player)p_305898_.getEntity()).getAbilities().instabuild;
            if ((flag || !(this.getDamage() > 40.0F)) && !this.shouldSourceDestroy(p_305898_)) {
                if (flag) {
                    // CraftBukkit start
                    org.bukkit.event.vehicle.VehicleDestroyEvent destroyEvent = new org.bukkit.event.vehicle.VehicleDestroyEvent(vehicle, attacker);
                    this.level().getCraftServer().getPluginManager().callEvent(destroyEvent);

                    if (destroyEvent.isCancelled()) {
                        this.setDamage(40.0F); // Maximize damage so this doesn't get triggered again right away
                        return true;
                    }
                    // CraftBukkit end
                    this.removeReason(org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
                    this.discard();
                }
            } else {
                // CraftBukkit start
                org.bukkit.event.vehicle.VehicleDestroyEvent destroyEvent = new org.bukkit.event.vehicle.VehicleDestroyEvent(vehicle, attacker);
                this.level().getCraftServer().getPluginManager().callEvent(destroyEvent);
                if (destroyEvent.isCancelled()) {
                    this.setDamage(40.0F); // Maximize damage so this doesn't get triggered again right away
                    return true;
                }
                // CraftBukkit end
                this.destroy(p_305898_);
            }

            return true;
        }
    }

    boolean shouldSourceDestroy(DamageSource p_312875_) {
        return false;
    }

    public void destroy(Item p_306235_) {
        this.kill();
        if (this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            ItemStack itemstack = new ItemStack(p_306235_);
            itemstack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
            this.spawnAtLocation(itemstack);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder p_326046_) {
        p_326046_.define(DATA_ID_HURT, 0);
        p_326046_.define(DATA_ID_HURTDIR, 1);
        p_326046_.define(DATA_ID_DAMAGE, 0.0F);
    }

    public void setHurtTime(int p_306126_) {
        this.entityData.set(DATA_ID_HURT, p_306126_);
    }

    public void setHurtDir(int p_306138_) {
        this.entityData.set(DATA_ID_HURTDIR, p_306138_);
    }

    public void setDamage(float p_306297_) {
        this.entityData.set(DATA_ID_DAMAGE, p_306297_);
    }

    public float getDamage() {
        return this.entityData.get(DATA_ID_DAMAGE);
    }

    public int getHurtTime() {
        return this.entityData.get(DATA_ID_HURT);
    }

    public int getHurtDir() {
        return this.entityData.get(DATA_ID_HURTDIR);
    }

    protected void destroy(DamageSource p_306223_) {
        this.destroy(this.getDropItem());
    }

    protected abstract Item getDropItem();
}
