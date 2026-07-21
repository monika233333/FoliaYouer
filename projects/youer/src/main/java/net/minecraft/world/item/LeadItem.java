package net.minecraft.world.item;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import org.bukkit.craftbukkit.CraftEquipmentSlot;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.hanging.HangingPlaceEvent;

public class LeadItem extends Item {
    public LeadItem(Item.Properties p_42828_) {
        super(p_42828_);
    }

    @Override
    public InteractionResult useOn(UseOnContext p_42834_) {
        Level level = p_42834_.getLevel();
        BlockPos blockpos = p_42834_.getClickedPos();
        BlockState blockstate = level.getBlockState(blockpos);
        if (blockstate.is(BlockTags.FENCES)) {
            Player player = p_42834_.getPlayer();
            if (!level.isClientSide && player != null) {
                enumHand.set(p_42834_.getHand()); // Youer
                bindPlayerMobs(player, level, blockpos);
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    public static AtomicReference<InteractionHand> enumHand = new AtomicReference<>(net.minecraft.world.InteractionHand.MAIN_HAND);
    public static InteractionResult bindPlayerMobs(Player p_42830_, Level p_42831_, BlockPos p_42832_) {
        LeashFenceKnotEntity leashfenceknotentity = null;
        List<Leashable> list = leashableInArea(p_42831_, p_42832_, p_353025_ -> p_353025_.getLeashHolder() == p_42830_);
        net.minecraft.world.InteractionHand interactionHand = enumHand.getAndSet(net.minecraft.world.InteractionHand.MAIN_HAND);
        for (Leashable leashable : list) {
            if (leashfenceknotentity == null) {
                leashfenceknotentity = LeashFenceKnotEntity.getOrCreateKnot(p_42831_, p_42832_);
                // CraftBukkit start - fire HangingPlaceEvent

                org.bukkit.inventory.EquipmentSlot hand = CraftEquipmentSlot.getHand(interactionHand);
                HangingPlaceEvent event = new HangingPlaceEvent((org.bukkit.entity.Hanging) leashfenceknotentity.getBukkitEntity(), p_42830_ != null ? (org.bukkit.entity.Player) p_42830_.getBukkitEntity() : null, CraftBlock.at(p_42831_, p_42832_), org.bukkit.block.BlockFace.SELF, hand);
                p_42831_.getCraftServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    leashfenceknotentity.discard(null); // CraftBukkit - add Bukkit remove cause
                    return InteractionResult.PASS;
                }
                // CraftBukkit end
                leashfenceknotentity.playPlacementSound();
            }

            // CraftBukkit start
            if (leashable instanceof Entity leashed) {
                if (CraftEventFactory.callPlayerLeashEntityEvent(leashed, leashfenceknotentity, p_42830_, interactionHand).isCancelled()) {
                    list.remove(leashable);
                    continue;
                }
            }
            leashable.setLeashedTo(leashfenceknotentity, true);
        }

        if (!list.isEmpty()) {
            p_42831_.gameEvent(GameEvent.BLOCK_ATTACH, p_42832_, GameEvent.Context.of(p_42830_));
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    // CraftBukkit start
    public static InteractionResult bindPlayerMobs(Player pPlayer, Level pLevel, BlockPos pPos, net.minecraft.world.InteractionHand enumhand) {
        enumHand.set(enumhand);
        return bindPlayerMobs(pPlayer, pLevel, pPos);
    }
    // CraftBukkit end

    public static List<Leashable> leashableInArea(Level p_353033_, BlockPos p_353031_, Predicate<Leashable> p_353062_) {
        double d0 = 7.0;
        int i = p_353031_.getX();
        int j = p_353031_.getY();
        int k = p_353031_.getZ();
        AABB aabb = new AABB((double)i - 7.0, (double)j - 7.0, (double)k - 7.0, (double)i + 7.0, (double)j + 7.0, (double)k + 7.0);
        return p_353033_.getEntitiesOfClass(Entity.class, aabb, p_353023_ -> {
            if (p_353023_ instanceof Leashable leashable && p_353062_.test(leashable)) {
                return true;
            }

            return false;
        }).stream().map(Leashable.class::cast).toList();
    }
}
