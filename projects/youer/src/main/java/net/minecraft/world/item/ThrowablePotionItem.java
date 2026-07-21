package net.minecraft.world.item;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.Level;

public class ThrowablePotionItem extends PotionItem implements ProjectileItem {
    public ThrowablePotionItem(Item.Properties p_43301_) {
        super(p_43301_);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level p_43303_, Player p_43304_, InteractionHand p_43305_) {
        ItemStack itemstack = p_43304_.getItemInHand(p_43305_);
        if (!p_43303_.isClientSide) {
            ThrownPotion thrownpotion = new ThrownPotion(p_43303_, p_43304_);
            thrownpotion.setItem(itemstack);
            thrownpotion.shootFromRotation(p_43304_, p_43304_.getXRot(), p_43304_.getYRot(), -20.0F, 0.5F, 1.0F);
            // Paper start - PlayerLaunchProjectileEvent
            com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent event = new com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent((org.bukkit.entity.Player) p_43304_.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemstack), (org.bukkit.entity.Projectile) thrownpotion.getBukkitEntity());
            if (event.callEvent() && p_43303_.addFreshEntity(thrownpotion)) {
                if (event.shouldConsume()) {
                    itemstack.consume(1, p_43304_);
                } else if (p_43304_ instanceof net.minecraft.server.level.ServerPlayer) {
                    ((net.minecraft.server.level.ServerPlayer) p_43304_).getBukkitEntity().updateInventory();
                }

                p_43304_.awardStat(Stats.ITEM_USED.get(this));
            } else {
                if (p_43304_ instanceof net.minecraft.server.level.ServerPlayer) {
                    ((net.minecraft.server.level.ServerPlayer) p_43304_).getBukkitEntity().updateInventory();
                }
                return InteractionResultHolder.fail(itemstack);
            }
            // Paper end - PlayerLaunchProjectileEvent
        }

        /* // Paper start - PlayerLaunchProjectileEvent; moved up
        p_43304_.awardStat(Stats.ITEM_USED.get(this));
        itemStack.consume(1, p_43304_);
        */ // Paper end
        return InteractionResultHolder.sidedSuccess(itemstack, p_43303_.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level p_338465_, Position p_338661_, ItemStack p_338506_, Direction p_338517_) {
        ThrownPotion thrownpotion = new ThrownPotion(p_338465_, p_338661_.x(), p_338661_.y(), p_338661_.z());
        thrownpotion.setItem(p_338506_);
        return thrownpotion;
    }

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder()
            .uncertainty(ProjectileItem.DispenseConfig.DEFAULT.uncertainty() * 0.5F)
            .power(ProjectileItem.DispenseConfig.DEFAULT.power() * 1.25F)
            .build();
    }
}
