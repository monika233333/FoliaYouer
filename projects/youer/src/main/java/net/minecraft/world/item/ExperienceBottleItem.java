package net.minecraft.world.item;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.level.Level;

public class ExperienceBottleItem extends Item implements ProjectileItem {
    public ExperienceBottleItem(Item.Properties p_41194_) {
        super(p_41194_);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level p_41196_, Player p_41197_, InteractionHand p_41198_) {
        ItemStack itemstack = p_41197_.getItemInHand(p_41198_);
        // Paper - PlayerLaunchProjectileEvent; moved down
        if (!p_41196_.isClientSide) {
            ThrownExperienceBottle thrownexperiencebottle = new ThrownExperienceBottle(p_41196_, p_41197_);
            thrownexperiencebottle.setItem(itemstack);
            thrownexperiencebottle.shootFromRotation(p_41197_, p_41197_.getXRot(), p_41197_.getYRot(), -20.0F, 0.7F, 1.0F);
            // Paper start - PlayerLaunchProjectileEvent
            com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent event = new com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent((org.bukkit.entity.Player) p_41197_.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemstack), (org.bukkit.entity.Projectile) thrownexperiencebottle.getBukkitEntity());
            if (event.callEvent() && p_41196_.addFreshEntity(thrownexperiencebottle)) {
                if (event.shouldConsume()) {
                    itemstack.consume(1, p_41197_);
                } else if (p_41197_ instanceof net.minecraft.server.level.ServerPlayer) {
                    ((net.minecraft.server.level.ServerPlayer) p_41197_).getBukkitEntity().updateInventory();
                }

                p_41196_.playSound(
                        null,
                        p_41197_.getX(),
                        p_41197_.getY(),
                        p_41197_.getZ(),
                        SoundEvents.EXPERIENCE_BOTTLE_THROW,
                        SoundSource.NEUTRAL,
                        0.5F,
                        0.4F / (p_41196_.getRandom().nextFloat() * 0.4F + 0.8F)
                );
                p_41197_.awardStat(Stats.ITEM_USED.get(this));
            } else {
                if (p_41197_ instanceof net.minecraft.server.level.ServerPlayer) {
                    ((net.minecraft.server.level.ServerPlayer) p_41197_).getBukkitEntity().updateInventory();
                }
                return InteractionResultHolder.fail(itemstack);
            }
            // Paper end - PlayerLaunchProjectileEvent
        }

        /* // Paper start - PlayerLaunchProjectileEvent; moved up
        user.awardStat(Stats.ITEM_USED.get(this));
        itemStack.consume(1, user);
        */ // Paper end - PlayerLaunchProjectileEvent
        return InteractionResultHolder.sidedSuccess(itemstack, p_41196_.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level p_338868_, Position p_338766_, ItemStack p_338321_, Direction p_338772_) {
        ThrownExperienceBottle thrownexperiencebottle = new ThrownExperienceBottle(p_338868_, p_338766_.x(), p_338766_.y(), p_338766_.z());
        thrownexperiencebottle.setItem(p_338321_);
        return thrownexperiencebottle;
    }

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder()
            .uncertainty(ProjectileItem.DispenseConfig.DEFAULT.uncertainty() * 0.5F)
            .power(ProjectileItem.DispenseConfig.DEFAULT.power() * 1.25F)
            .build();
    }
}
