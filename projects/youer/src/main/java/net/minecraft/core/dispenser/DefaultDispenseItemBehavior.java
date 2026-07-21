package net.minecraft.core.dispenser;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftVector;
import org.bukkit.event.block.BlockDispenseEvent;

public class DefaultDispenseItemBehavior implements DispenseItemBehavior {
    private static final int DEFAULT_ACCURACY = 6;
    private Direction enumdirection; // Paper - cache facing direction
    // CraftBukkit start
    private boolean dropper;

    public DefaultDispenseItemBehavior(boolean dropper) {
        this.dropper = dropper;
    }
    // CraftBukkit end

    public DefaultDispenseItemBehavior() {} // Youer fix decompile

    @Override
    public final ItemStack dispense(BlockSource p_302432_, ItemStack p_123392_) {
        enumdirection = p_302432_.state().getValue(DispenserBlock.FACING); // Paper - cache facing direction
        ItemStack itemstack = this.execute(p_302432_, p_123392_);
        this.playSound(p_302432_);
        this.playAnimation(p_302432_, enumdirection); // Paper - cache facing direction
        return itemstack;
    }

    protected ItemStack execute(BlockSource p_302420_, ItemStack p_123386_) {
        Direction direction = p_302420_.state().getValue(DispenserBlock.FACING);
        Position position = DispenserBlock.getDispensePosition(p_302420_);
        ItemStack itemstack = p_123386_.split(1);
        // CraftBukkit start
        if (!DefaultDispenseItemBehavior.spawnItem(p_302420_.level(), itemstack, 6, enumdirection, p_302420_, this.dropper)) {
            p_123386_.grow(1);
        }
        // CraftBukkit end
        return p_123386_;
    }

    public static void spawnItem(Level p_123379_, ItemStack p_123380_, int p_123381_, Direction p_123382_, Position p_123383_) {
        double d0 = p_123383_.x();
        double d1 = p_123383_.y();
        double d2 = p_123383_.z();
        if (p_123382_.getAxis() == Direction.Axis.Y) {
            d1 -= 0.125;
        } else {
            d1 -= 0.15625;
        }

        ItemEntity itementity = new ItemEntity(p_123379_, d0, d1, d2, p_123380_);
        double d3 = p_123379_.random.nextDouble() * 0.1 + 0.2;
        itementity.setDeltaMovement(
                p_123379_.random.triangle((double)p_123382_.getStepX() * d3, 0.0172275 * (double)p_123381_),
                p_123379_.random.triangle(0.2, 0.0172275 * (double)p_123381_),
                p_123379_.random.triangle((double)p_123382_.getStepZ() * d3, 0.0172275 * (double)p_123381_)
        );
        p_123379_.addFreshEntity(itementity);
    }

    // CraftBukkit - void -> boolean return, IPosition -> ISourceBlock last argument, dropper
    public static boolean spawnItem(Level world, ItemStack itemstack, int i, Direction enumdirection, BlockSource sourceblock, boolean dropper) {
        if (itemstack.isEmpty()) return true;
        Position iposition = DispenserBlock.getDispensePosition(sourceblock);
        ItemEntity entityitem = DefaultDispenseItemBehavior.prepareItem(world, itemstack, i, enumdirection, iposition);

        org.bukkit.block.Block block = CraftBlock.at(world, sourceblock.pos());
        CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack);

        BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), CraftVector.toBukkit(entityitem.getDeltaMovement()));
        if (!DispenserBlock.eventFired) {
            world.getCraftServer().getPluginManager().callEvent(event);
        }

        if (event.isCancelled()) {
            return false;
        }

        entityitem.setItem(CraftItemStack.asNMSCopy(event.getItem()));
        entityitem.setDeltaMovement(CraftVector.toNMS(event.getVelocity()));

        if (!dropper && !event.getItem().getType().equals(craftItem.getType())) {
            // Chain to handler for new item
            ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
            DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
            if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior.getClass() != DefaultDispenseItemBehavior.class) {
                idispensebehavior.dispense(sourceblock, eventStack);
            } else {
                world.addFreshEntity(entityitem);
            }
            return false;
        }

        world.addFreshEntity(entityitem);

        return true;
        // CraftBukkit end
    }

    private static ItemEntity prepareItem(Level p_123379_, ItemStack p_123380_, int p_123381_, Direction p_123382_, Position p_123383_) {
        // CraftBukkit end
        double d0 = p_123383_.x();
        double d1 = p_123383_.y();
        double d2 = p_123383_.z();
        if (p_123382_.getAxis() == Direction.Axis.Y) {
            d1 -= 0.125;
        } else {
            d1 -= 0.15625;
        }

        ItemEntity itementity = new ItemEntity(p_123379_, d0, d1, d2, p_123380_);
        double d3 = p_123379_.random.nextDouble() * 0.1 + 0.2;
        itementity.setDeltaMovement(
            p_123379_.random.triangle((double)p_123382_.getStepX() * d3, 0.0172275 * (double)p_123381_),
            p_123379_.random.triangle(0.2, 0.0172275 * (double)p_123381_),
            p_123379_.random.triangle((double)p_123382_.getStepZ() * d3, 0.0172275 * (double)p_123381_)
        );
        return itementity;
    }

    protected void playSound(BlockSource p_302471_) {
        playDefaultSound(p_302471_);
    }

    protected void playAnimation(BlockSource p_302462_, Direction p_123389_) {
        playDefaultAnimation(p_302462_, p_123389_);
    }

    private static void playDefaultSound(BlockSource p_347476_) {
        p_347476_.level().levelEvent(1000, p_347476_.pos(), 0);
    }

    private static void playDefaultAnimation(BlockSource p_347531_, Direction p_347570_) {
        p_347531_.level().levelEvent(2000, p_347531_.pos(), p_347570_.get3DDataValue());
    }

    protected ItemStack consumeWithRemainder(BlockSource p_347658_, ItemStack p_347682_, ItemStack p_347670_) {
        p_347682_.shrink(1);
        if (p_347682_.isEmpty()) {
            return p_347670_;
        } else {
            this.addToInventoryOrDispense(p_347658_, p_347670_);
            return p_347682_;
        }
    }

    private void addToInventoryOrDispense(BlockSource p_347634_, ItemStack p_347604_) {
        ItemStack itemstack = p_347634_.blockEntity().insertItem(p_347604_);
        if (!itemstack.isEmpty()) {
            Direction direction = p_347634_.state().getValue(DispenserBlock.FACING);
            spawnItem(p_347634_.level(), itemstack, 6, direction, DispenserBlock.getDispensePosition(p_347634_));
            playDefaultSound(p_347634_);
            playDefaultAnimation(p_347634_, direction);
        }
    }
}
