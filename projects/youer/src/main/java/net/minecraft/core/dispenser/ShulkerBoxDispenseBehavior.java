package net.minecraft.core.dispenser;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.DispenserBlock;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseEvent;
import org.slf4j.Logger;

public class ShulkerBoxDispenseBehavior extends OptionalDispenseItemBehavior {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    protected ItemStack execute(BlockSource p_302426_, ItemStack p_123588_) {
        this.setSuccess(false);
        Item item = p_123588_.getItem();
        if (item instanceof BlockItem) {
            Direction direction = p_302426_.state().getValue(DispenserBlock.FACING);
            BlockPos blockpos = p_302426_.pos().relative(direction);
            Direction direction1 = p_302426_.level().isEmptyBlock(blockpos.below()) ? direction : Direction.UP;
            // CraftBukkit start
            org.bukkit.block.Block bukkitBlock = CraftBlock.at(p_302426_.level(), p_302426_.pos());
            CraftItemStack craftItem = CraftItemStack.asCraftMirror(p_123588_);

            BlockDispenseEvent event = new BlockDispenseEvent(bukkitBlock, craftItem.clone(), new org.bukkit.util.Vector(blockpos.getX(), blockpos.getY(), blockpos.getZ()));
            if (!DispenserBlock.eventFired) {
                p_302426_.level().getCraftServer().getPluginManager().callEvent(event);
            }

            if (event.isCancelled()) {
                return p_123588_;
            }

            if (!event.getItem().equals(craftItem)) {
                // Chain to handler for new item
                ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                    idispensebehavior.dispense(p_302426_, eventStack);
                    return p_123588_;
                }
            }
            // CraftBukkit end
            try {
                this.setSuccess(
                    ((BlockItem)item).place(new DirectionalPlaceContext(p_302426_.level(), blockpos, direction, p_123588_, direction1)).consumesAction()
                );
            } catch (Exception exception) {
                LOGGER.error("Error trying to place shulker box at {}", blockpos, exception);
            }
        }

        return p_123588_;
    }
}
