package net.minecraft.world.damagesource;

import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.block.CraftBlock;

public class DamageSource {
    private final Holder<DamageType> type;
    @Nullable
    private final Entity causingEntity;
    @Nullable
    private final Entity directEntity;
    @Nullable
    private final Vec3 damageSourcePosition;
    // CraftBukkit start
    @Nullable
    private org.bukkit.block.Block directBlock; // The block that caused the damage. damageSourcePosition is not used for all block damages
    @Nullable
    private org.bukkit.block.BlockState directBlockState; // The block state of the block relevant to this damage source
    private boolean sweep = false;
    private boolean melting = false;
    private boolean poison = false;
    @Nullable
    private Entity customEventDamager = null; // This field is a helper for when causing entity damage is not set by vanilla // Paper - fix DamageSource API

    public DamageSource sweep() {
        this.sweep = true;
        return this;
    }

    public boolean isSweep() {
        return this.sweep;
    }

    public DamageSource melting() {
        this.melting = true;
        return this;
    }

    public boolean isMelting() {
        return this.melting;
    }

    public DamageSource poison() {
        this.poison = true;
        return this;
    }

    public boolean isPoison() {
        return this.poison;
    }

    // Paper start - fix DamageSource API
    @Nullable
    public Entity getCustomEventDamager() {
        return (this.customEventDamager != null) ? this.customEventDamager : this.directEntity;
    }

    public DamageSource customEventDamager(Entity entity) {
        if (this.directEntity != null) {
            throw new IllegalStateException("Cannot set custom event damager when direct entity is already set (report a bug to Paper)");
        }
        DamageSource damageSource = this.cloneInstance();
        damageSource.customEventDamager = entity;
        // Paper end - fix DamageSource API
        return damageSource;
    }

    public org.bukkit.block.Block getDirectBlock() {
        return this.directBlock;
    }

    public DamageSource directBlock(net.minecraft.world.level.Level world, net.minecraft.core.BlockPos blockPosition) {
        if (blockPosition == null || world == null) {
            return this;
        }
        return directBlock(CraftBlock.at(world, blockPosition));
    }

    public DamageSource directBlock(org.bukkit.block.Block block) {
        if (block == null) {
            return this;
        }
        // Cloning the instance lets us return unique instances of DamageSource without affecting constants defined in DamageSources
        DamageSource damageSource = this.cloneInstance();
        damageSource.directBlock = block;
        return damageSource;
    }

    public org.bukkit.block.BlockState getDirectBlockState() {
        return this.directBlockState;
    }

    public DamageSource directBlockState(org.bukkit.block.BlockState blockState) {
        if (blockState == null) {
            return this;
        }
        // Cloning the instance lets us return unique instances of DamageSource without affecting constants defined in DamageSources
        DamageSource damageSource = this.cloneInstance();
        damageSource.directBlockState = blockState;
        return damageSource;
    }

    private DamageSource cloneInstance() {
        DamageSource damageSource = new DamageSource(this.type, this.directEntity, this.causingEntity, this.damageSourcePosition);
        damageSource.directBlock = this.getDirectBlock();
        damageSource.directBlockState = this.getDirectBlockState();
        damageSource.sweep = this.isSweep();
        damageSource.poison = this.isPoison();
        damageSource.melting = this.isMelting();
        return damageSource;
    }
    // CraftBukkit end

    @Override
    public String toString() {
        return "DamageSource (" + this.type().msgId() + ")";
    }

    public float getFoodExhaustion() {
        return this.type().exhaustion();
    }

    public boolean isDirect() {
        return this.causingEntity == this.directEntity;
    }

    public DamageSource(Holder<DamageType> p_270906_, @Nullable Entity p_270796_, @Nullable Entity p_270459_, @Nullable Vec3 p_270623_) {
        this.type = p_270906_;
        this.causingEntity = p_270459_;
        this.directEntity = p_270796_;
        this.damageSourcePosition = p_270623_;
    }

    public DamageSource(Holder<DamageType> p_270818_, @Nullable Entity p_270162_, @Nullable Entity p_270115_) {
        this(p_270818_, p_270162_, p_270115_, null);
    }

    public DamageSource(Holder<DamageType> p_270690_, Vec3 p_270579_) {
        this(p_270690_, null, null, p_270579_);
    }

    public DamageSource(Holder<DamageType> p_270811_, @Nullable Entity p_270660_) {
        this(p_270811_, p_270660_, p_270660_);
    }

    public DamageSource(Holder<DamageType> p_270475_) {
        this(p_270475_, null, null, null);
    }

    @Nullable
    public Entity getDirectEntity() {
        return this.directEntity;
    }

    @Nullable
    public Entity getEntity() {
        return this.causingEntity;
    }

    @Nullable
    public ItemStack getWeaponItem() {
        return this.directEntity != null ? this.directEntity.getWeaponItem() : null;
    }

    public Component getLocalizedDeathMessage(LivingEntity p_19343_) {
        String s = "death.attack." + this.type().msgId();
        if (this.causingEntity == null && this.directEntity == null) {
            LivingEntity livingentity1 = p_19343_.getKillCredit();
            String s1 = s + ".player";
            return livingentity1 != null
                ? Component.translatable(s1, p_19343_.getDisplayName(), livingentity1.getDisplayName())
                : Component.translatable(s, p_19343_.getDisplayName());
        } else {
            Component component = this.causingEntity == null ? this.directEntity.getDisplayName() : this.causingEntity.getDisplayName();
            ItemStack itemstack = this.causingEntity instanceof LivingEntity livingentity ? livingentity.getMainHandItem() : ItemStack.EMPTY;
            return !itemstack.isEmpty() && itemstack.has(DataComponents.CUSTOM_NAME)
                ? Component.translatable(s + ".item", p_19343_.getDisplayName(), component, itemstack.getDisplayName())
                : Component.translatable(s, p_19343_.getDisplayName(), component);
        }
    }

    // Purpur start
    public Component getLocalizedDeathMessage(String str, LivingEntity entity) {
        net.kyori.adventure.text.Component name = io.papermc.paper.adventure.PaperAdventure.asAdventure(entity.getDisplayName());
        net.kyori.adventure.text.minimessage.tag.resolver.TagResolver template = net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("player", name);
        net.kyori.adventure.text.Component component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(str, template);
        return io.papermc.paper.adventure.PaperAdventure.asVanilla(component);
    }
    // Purpur end

    public String getMsgId() {
        return this.type().msgId();
    }

    /**
     * @deprecated Use {@link DamageScaling#getScalingFunction()}
     */
    @Deprecated(since = "1.20.1")
    public boolean scalesWithDifficulty() {
        return switch (this.type().scaling()) {
            case NEVER -> false;
            case WHEN_CAUSED_BY_LIVING_NON_PLAYER -> this.causingEntity instanceof LivingEntity && !(this.causingEntity instanceof Player);
            case ALWAYS -> true;
        };
    }

    public boolean isCreativePlayer() {
        if (this.getEntity() instanceof Player player && player.getAbilities().instabuild) {
            return true;
        }

        return false;
    }

    @Nullable
    public Vec3 getSourcePosition() {
        if (this.damageSourcePosition != null) {
            return this.damageSourcePosition;
        } else {
            return this.directEntity != null ? this.directEntity.position() : null;
        }
    }

    @Nullable
    public Vec3 sourcePositionRaw() {
        return this.damageSourcePosition;
    }

    public boolean is(TagKey<DamageType> p_270890_) {
        return this.type.is(p_270890_);
    }

    public boolean is(ResourceKey<DamageType> p_276108_) {
        return this.type.is(p_276108_);
    }

    public DamageType type() {
        return this.type.value();
    }

    public Holder<DamageType> typeHolder() {
        return this.type;
    }

    // Paper start - add critical damage API
    private boolean critical;
    public boolean isCritical() {
        return this.critical;
    }
    public DamageSource critical() {
        return this.critical(true);
    }
    public DamageSource critical(boolean critical) {
        this.critical = critical;
        return this;
    }
    // Paper end - add critical damage API
}
