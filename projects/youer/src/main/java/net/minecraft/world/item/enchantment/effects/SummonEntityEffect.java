package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.weather.LightningStrikeEvent;

public record SummonEntityEffect(HolderSet<EntityType<?>> entityTypes, boolean joinTeam) implements EnchantmentEntityEffect {
    public static final MapCodec<SummonEntityEffect> CODEC = RecordCodecBuilder.mapCodec(
        p_345616_ -> p_345616_.group(
                    RegistryCodecs.homogeneousList(Registries.ENTITY_TYPE).fieldOf("entity").forGetter(SummonEntityEffect::entityTypes),
                    Codec.BOOL.optionalFieldOf("join_team", Boolean.valueOf(false)).forGetter(SummonEntityEffect::joinTeam)
                )
                .apply(p_345616_, SummonEntityEffect::new)
    );

    @Override
    public void apply(ServerLevel p_345900_, int p_346183_, EnchantedItemInUse p_345549_, Entity p_346414_, Vec3 p_346284_) {
        BlockPos blockpos = BlockPos.containing(p_346284_);
        if (Level.isInSpawnableBounds(blockpos)) {
            Optional<Holder<EntityType<?>>> optional = this.entityTypes().getRandomElement(p_345900_.getRandom());
            if (!optional.isEmpty()) {
                Entity entity = optional.get().value().create(p_345900_, null, blockpos, MobSpawnType.TRIGGERED, false, false);
                if (entity != null) {
                    if (entity instanceof LightningBolt lightningbolt) {
                        if (p_345549_.owner() instanceof ServerPlayer serverplayer) {
                            lightningbolt.setCause(serverplayer);
                        }
                        // CraftBukkit start
                        p_345900_.strikeLightning(entity, (p_345549_.itemStack().getItem() == Items.TRIDENT) ? LightningStrikeEvent.Cause.TRIDENT : LightningStrikeEvent.Cause.ENCHANTMENT);
                    } else {
                        p_345900_.addFreshEntityWithPassengers(entity, CreatureSpawnEvent.SpawnReason.ENCHANTMENT); // Paper - Fix typo when adding summoned entity
                        // CraftBukkit end
                    }
                    if (this.joinTeam && p_346414_.getTeam() != null) {
                        p_345900_.getScoreboard().addPlayerToTeam(entity.getScoreboardName(), p_346414_.getTeam());
                    }

                    entity.moveTo(p_346284_.x, p_346284_.y, p_346284_.z, entity.getYRot(), entity.getXRot());
                }
            }
        }
    }

    @Override
    public MapCodec<SummonEntityEffect> codec() {
        return CODEC;
    }
}
