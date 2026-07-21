package net.minecraft.world.effect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;

class SaturationMobEffect extends InstantenousMobEffect {
    protected SaturationMobEffect(MobEffectCategory p_294221_, int p_295725_) {
        super(p_294221_, p_295725_);
    }

    @Override
    public boolean applyEffectTick(LivingEntity p_295892_, int p_296026_) {
        if (!p_295892_.level().isClientSide && p_295892_ instanceof Player player) {
            // CraftBukkit start
            int oldFoodLevel = player.getFoodData().foodLevel;
            org.bukkit.event.entity.FoodLevelChangeEvent event = CraftEventFactory.callFoodLevelChangeEvent(player, p_296026_ + 1 + oldFoodLevel);
            if (!event.isCancelled()) {
                player.getFoodData().eat(event.getFoodLevel() - oldFoodLevel, 1.0F);
            }

            ((CraftPlayer) player.getBukkitEntity()).sendHealthUpdate();
            // CraftBukkit end
        }

        return true;
    }
}
