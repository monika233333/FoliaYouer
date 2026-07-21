package net.minecraft.world.effect;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class MobEffectUtil {
    public static Component formatDuration(MobEffectInstance p_268116_, float p_268280_, float p_314720_) {
        if (p_268116_.isInfiniteDuration()) {
            return Component.translatable("effect.duration.infinite");
        } else {
            int i = Mth.floor((float)p_268116_.getDuration() * p_268280_);
            return Component.literal(StringUtil.formatTickDuration(i, p_314720_));
        }
    }

    public static boolean hasDigSpeed(LivingEntity p_19585_) {
        return p_19585_.hasEffect(MobEffects.DIG_SPEED) || p_19585_.hasEffect(MobEffects.CONDUIT_POWER);
    }

    public static int getDigSpeedAmplification(LivingEntity p_19587_) {
        int i = 0;
        int j = 0;
        if (p_19587_.hasEffect(MobEffects.DIG_SPEED)) {
            i = p_19587_.getEffect(MobEffects.DIG_SPEED).getAmplifier();
        }

        if (p_19587_.hasEffect(MobEffects.CONDUIT_POWER)) {
            j = p_19587_.getEffect(MobEffects.CONDUIT_POWER).getAmplifier();
        }

        return Math.max(i, j);
    }

    public static boolean hasWaterBreathing(LivingEntity p_19589_) {
        return p_19589_.hasEffect(MobEffects.WATER_BREATHING) || p_19589_.hasEffect(MobEffects.CONDUIT_POWER);
    }

    private final static AtomicReference<org.bukkit.event.entity.EntityPotionEffectEvent.Cause> addEffectToPlayersAround$cause = new AtomicReference<>(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN);
    public static void addEffectToPlayersAround$cause(org.bukkit.event.entity.EntityPotionEffectEvent.Cause cause) {
        addEffectToPlayersAround$cause.set(cause);
    }
    @Nullable static java.util.function.Predicate<ServerPlayer> playerPredicate = null;
    public static List<ServerPlayer> addEffectToPlayersAround(
        ServerLevel p_216947_, @Nullable Entity p_216948_, Vec3 p_216949_, double p_216950_, MobEffectInstance p_216951_, int p_216952_
    ) {
        Holder<MobEffect> holder = p_216951_.getEffect();
        List<ServerPlayer> list = p_216947_.getPlayers(
            p_267925_ -> {
                // Paper start - Add ElderGuardianAppearanceEvent
                boolean condition =  p_267925_.gameMode.isSurvival()
                        && (p_216948_ == null || !p_216948_.isAlliedTo(p_267925_))
                        && p_216949_.closerThan(p_267925_.position(), p_216950_)
                        && (
                            !p_267925_.hasEffect(holder)
                                    || p_267925_.getEffect(holder).getAmplifier() < p_216951_.getAmplifier()
                                    || p_267925_.getEffect(holder).endsWithin(p_216952_ - 1)
                );
                if (condition) {
                    boolean test = playerPredicate == null || playerPredicate.test(p_267925_); // Only test the player AFTER it is true
                    playerPredicate = null;
                    return test;
                } else {
                    playerPredicate = null;
                    return false;
                }
                // Paper ned - Add ElderGuardianAppearanceEvent
            }
        );
        list.forEach(p_238232_ -> {
            p_238232_.addEffectCause(addEffectToPlayersAround$cause.getAndSet(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.UNKNOWN));
            p_238232_.addEffect(new MobEffectInstance(p_216951_), p_216948_);
        });
        return list;
    }

    public static List<ServerPlayer> addEffectToPlayersAround(
            ServerLevel p_216947_, @Nullable Entity p_216948_, Vec3 p_216949_, double p_216950_, MobEffectInstance p_216951_, int p_216952_, org.bukkit.event.entity.EntityPotionEffectEvent.Cause cause, @Nullable java.util.function.Predicate<ServerPlayer> playerPredicate
    ) {
        addEffectToPlayersAround$cause(cause);
        MobEffectUtil.playerPredicate = playerPredicate;
        return addEffectToPlayersAround(p_216947_, p_216948_, p_216949_, p_216950_, p_216951_, p_216952_);
    }

    public static List<ServerPlayer> addEffectToPlayersAround(
            ServerLevel p_216947_, @Nullable Entity p_216948_, Vec3 p_216949_, double p_216950_, MobEffectInstance p_216951_, int p_216952_, org.bukkit.event.entity.EntityPotionEffectEvent.Cause cause
    ) {
        addEffectToPlayersAround$cause(cause);
        MobEffectUtil.playerPredicate = null;
        return addEffectToPlayersAround(p_216947_, p_216948_, p_216949_, p_216950_, p_216951_, p_216952_);
    }
}
