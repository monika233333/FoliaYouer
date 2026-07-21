package net.minecraft.world.food;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

public class FoodData {
    public int foodLevel = 20;
    public float saturationLevel;
    public float exhaustionLevel;
    private int tickTimer;
    private int lastFoodLevel = 20;

    // CraftBukkit start
    public Player entityhuman = null;
    public int saturatedRegenRate = 10;
    public int unsaturatedRegenRate = 80;
    public int starvationRate = 80;
    // CraftBukkit end

    public FoodData() {
        this.saturationLevel = 5.0F;
    }

    public FoodData player(Player player) {
        this.entityhuman = player;
        return this;
    }

    private void add(int p_340988_, float p_340961_) {
        this.foodLevel = Mth.clamp(p_340988_ + this.foodLevel, 0, 20);
        this.saturationLevel = Mth.clamp(p_340961_ + this.saturationLevel, 0.0F, (float)this.foodLevel);
    }

    public void eat(int p_38708_, float p_38709_) {
        this.add(p_38708_, FoodConstants.saturationByModifier(p_38708_, p_38709_));
    }

    public ItemStack eat$itemstack = null;
    public void eat(FoodProperties p_347533_) {
        int oldFoodLevel = this.foodLevel;

        FoodLevelChangeEvent event = CraftEventFactory.callFoodLevelChangeEvent(this.entityhuman, p_347533_.nutrition() + oldFoodLevel, eat$itemstack);

        if (!event.isCancelled()) {
            this.add(event.getFoodLevel() - oldFoodLevel, p_347533_.saturation());
        }

        ((ServerPlayer) this.entityhuman).getBukkitEntity().sendHealthUpdate();
    }

    // CraftBukkit start
    public void eat(ItemStack itemstack, FoodProperties foodinfo) {
       this.eat$itemstack = itemstack;
       this.eat(foodinfo);
    }
    // CraftBukkit end

    public void tick(Player p_38711_) {
        Difficulty difficulty = p_38711_.level().getDifficulty();
        if (entityhuman == null) entityhuman = p_38711_;
        this.lastFoodLevel = this.foodLevel;
        if (this.exhaustionLevel > 4.0F) {
            this.exhaustionLevel -= 4.0F;
            if (this.saturationLevel > 0.0F) {
                this.saturationLevel = Math.max(this.saturationLevel - 1.0F, 0.0F);
            } else if (difficulty != Difficulty.PEACEFUL) {
                // CraftBukkit start
                FoodLevelChangeEvent event = CraftEventFactory.callFoodLevelChangeEvent(p_38711_, Math.max(this.foodLevel - 1, 0));

                if (!event.isCancelled()) {
                    this.foodLevel = event.getFoodLevel();
                }

                ((ServerPlayer) p_38711_).connection.send(new ClientboundSetHealthPacket(((ServerPlayer) p_38711_).getBukkitEntity().getScaledHealth(), this.foodLevel, this.saturationLevel));
                // CraftBukkit end
            }
        }

        boolean flag = p_38711_.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
        if (flag && this.saturationLevel > 0.0F && p_38711_.isHurt() && this.foodLevel >= 20) {
            this.tickTimer++;
            if (this.tickTimer >= this.saturatedRegenRate) { // CraftBukkit
                float f = Math.min(this.saturationLevel, 6.0F);
                p_38711_.pushHealReason(EntityRegainHealthEvent.RegainReason.SATIATED);
                p_38711_.pushHeal$isFastRegen(true); // Paper - This is fast regen
                p_38711_.heal(f / 6.0F);
                p_38711_.causeFoodExhaustion(f, EntityExhaustionEvent.ExhaustionReason.REGEN); // CraftBukkit - EntityExhaustionEvent
                fakeaddExhaustion.set(true);
                this.addExhaustion(f);
                this.tickTimer = 0;
            }
        } else if (flag && this.foodLevel >= 18 && p_38711_.isHurt()) {
            this.tickTimer++;
            if (this.tickTimer >= this.unsaturatedRegenRate) { // CraftBukkit - add regen rate manipulation
                p_38711_.pushHealReason(EntityRegainHealthEvent.RegainReason.SATIATED);
                p_38711_.heal(1.0F);
                p_38711_.causeFoodExhaustion(p_38711_.level().spigotConfig.regenExhaustion, EntityExhaustionEvent.ExhaustionReason.REGEN); // CraftBukkit - EntityExhaustionEvent
                fakeaddExhaustion.set(true);
                this.addExhaustion(6.0F);
                this.tickTimer = 0;
            }
        } else if (this.foodLevel <= 0) {
            this.tickTimer++;
            if (this.tickTimer >= this.starvationRate) { // CraftBukkit - add regen rate manipulation
                if (p_38711_.getHealth() > 10.0F || difficulty == Difficulty.HARD || p_38711_.getHealth() > 1.0F && difficulty == Difficulty.NORMAL) {
                    p_38711_.hurt(p_38711_.damageSources().starve(), p_38711_.level().purpurConfig.hungerStarvationDamage); // Purpur
                }

                this.tickTimer = 0;
            }
        } else {
            this.tickTimer = 0;
        }
    }

    public void readAdditionalSaveData(CompoundTag p_38716_) {
        if (p_38716_.contains("foodLevel", 99)) {
            this.foodLevel = p_38716_.getInt("foodLevel");
            this.tickTimer = p_38716_.getInt("foodTickTimer");
            this.saturationLevel = p_38716_.getFloat("foodSaturationLevel");
            this.exhaustionLevel = p_38716_.getFloat("foodExhaustionLevel");
        }
    }

    public void addAdditionalSaveData(CompoundTag p_38720_) {
        p_38720_.putInt("foodLevel", this.foodLevel);
        p_38720_.putInt("foodTickTimer", this.tickTimer);
        p_38720_.putFloat("foodSaturationLevel", this.saturationLevel);
        p_38720_.putFloat("foodExhaustionLevel", this.exhaustionLevel);
    }

    public int getFoodLevel() {
        return this.foodLevel;
    }

    public int getLastFoodLevel() {
        return this.lastFoodLevel;
    }

    public boolean needsFood() {
        return this.foodLevel < 20;
    }

    AtomicBoolean fakeaddExhaustion = new AtomicBoolean(false);
    public void addExhaustion(float p_38704_) {
        if (fakeaddExhaustion.getAndSet(false)) return;
        this.exhaustionLevel = Math.min(this.exhaustionLevel + p_38704_, 40.0F);
    }

    public float getExhaustionLevel() {
        return this.exhaustionLevel;
    }

    public float getSaturationLevel() {
        return this.saturationLevel;
    }

    public void setFoodLevel(int p_38706_) {
        this.foodLevel = p_38706_;
    }

    public void setSaturation(float p_38718_) {
        this.saturationLevel = p_38718_;
    }

    public void setExhaustion(float p_150379_) {
        this.exhaustionLevel = p_150379_;
    }
}
