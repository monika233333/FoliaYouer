package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.phys.Vec3;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;

public record Ignite(LevelBasedValue duration) implements EnchantmentEntityEffect {
    public static final MapCodec<Ignite> CODEC = RecordCodecBuilder.mapCodec(
        p_345641_ -> p_345641_.group(LevelBasedValue.CODEC.fieldOf("duration").forGetter(p_345622_ -> p_345622_.duration)).apply(p_345641_, Ignite::new)
    );

    @Override
    public void apply(ServerLevel p_345606_, int p_344968_, EnchantedItemInUse p_346032_, Entity p_346370_, Vec3 p_344775_) {
        // CraftBukkit start - Call a combust event when somebody hits with a fire enchanted item
        EntityCombustEvent entityCombustEvent;
        if (p_346032_.owner() != null) {
            entityCombustEvent = new EntityCombustByEntityEvent(p_346032_.owner().getBukkitEntity(), p_346370_.getBukkitEntity(), this.duration.calculate(p_344968_));
        } else {
            entityCombustEvent = new EntityCombustEvent(p_346370_.getBukkitEntity(), this.duration.calculate(p_344968_));
        }

        org.bukkit.Bukkit.getPluginManager().callEvent(entityCombustEvent);
        if (entityCombustEvent.isCancelled()) {
            return;
        }

        p_346370_.igniteForSeconds(entityCombustEvent.getDuration(), false);
        // CraftBukkit end
    }

    @Override
    public MapCodec<Ignite> codec() {
        return CODEC;
    }
}
