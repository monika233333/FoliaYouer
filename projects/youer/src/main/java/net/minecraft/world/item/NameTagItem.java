package net.minecraft.world.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

public class NameTagItem extends Item {
    public NameTagItem(Item.Properties p_42952_) {
        super(p_42952_);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack p_42954_, Player p_42955_, LivingEntity p_42956_, InteractionHand p_42957_) {
        Component component = p_42954_.get(DataComponents.CUSTOM_NAME);
        if (component != null && !(p_42956_ instanceof Player)) {
            if (!p_42955_.level().isClientSide && p_42956_.isAlive()) {
                // Paper start - Add PlayerNameEntityEvent
                io.papermc.paper.event.player.PlayerNameEntityEvent event = new io.papermc.paper.event.player.PlayerNameEntityEvent(((net.minecraft.server.level.ServerPlayer) p_42955_).getBukkitEntity(), p_42956_.getBukkitLivingEntity(), io.papermc.paper.adventure.PaperAdventure.asAdventure(p_42954_.getHoverName()), true);
                if (!event.callEvent()) return InteractionResult.PASS;
                LivingEntity newEntity = ((org.bukkit.craftbukkit.entity.CraftLivingEntity) event.getEntity()).getHandle();
                newEntity.setCustomName(event.getName() != null ? io.papermc.paper.adventure.PaperAdventure.asVanilla(event.getName()) : null);
                if (event.isPersistent() && newEntity instanceof Mob mob) {
                    // Paper end - Add PlayerNameEntityEvent
                    mob.setPersistenceRequired();
                }

                p_42954_.shrink(1);
            }

            return InteractionResult.sidedSuccess(p_42955_.level().isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }
}
