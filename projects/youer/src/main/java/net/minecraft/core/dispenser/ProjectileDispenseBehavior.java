package net.minecraft.core.dispenser;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.projectiles.CraftBlockProjectileSource;
import org.bukkit.event.block.BlockDispenseEvent;

public class ProjectileDispenseBehavior extends DefaultDispenseItemBehavior {
    private final ProjectileItem projectileItem;
    private final ProjectileItem.DispenseConfig dispenseConfig;

    public ProjectileDispenseBehavior(Item p_338781_) {
        if (p_338781_ instanceof ProjectileItem projectileitem) {
            this.projectileItem = projectileitem;
            this.dispenseConfig = projectileitem.createDispenseConfig();
        } else {
            throw new IllegalArgumentException(p_338781_ + " not instance of " + ProjectileItem.class.getSimpleName());
        }
    }

    @Override
    public ItemStack execute(BlockSource p_338635_, ItemStack p_338423_) {
        Level level = p_338635_.level();
        Direction direction = p_338635_.state().getValue(DispenserBlock.FACING);
        Position position = this.dispenseConfig.positionFunction().getDispensePosition(p_338635_, direction);
        Projectile projectile = this.projectileItem.asProjectile(level, position, p_338423_, direction);
        ItemStack itemstack1 = p_338423_.split(1);
        org.bukkit.block.Block block = CraftBlock.at(level, p_338635_.pos());
        CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

        BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector((double) direction.getStepX(), (double) direction.getStepY(), (double) direction.getStepZ()));
        if (!DispenserBlock.eventFired) {
            level.getCraftServer().getPluginManager().callEvent(event);
        }

        if (event.isCancelled()) {
            p_338423_.grow(1);
            return p_338423_;
        }

        if (!event.getItem().equals(craftItem)) {
            p_338423_.grow(1);
            // Chain to handler for new item
            ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
            DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
            if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                idispensebehavior.dispense(p_338635_, eventStack);
                return p_338423_;
            }
        }
        this.projectileItem
            .shoot(
                projectile,
                event.getVelocity().getX(),
                event.getVelocity().getY(),
                event.getVelocity().getZ(),
                this.dispenseConfig.power(),
                this.dispenseConfig.uncertainty()
            );
        ((Entity) projectile).projectileSource = new CraftBlockProjectileSource(p_338635_.blockEntity());
        // CraftBukkit end
        level.addFreshEntity(projectile);
        //p_338423_.shrink(1);
        return p_338423_;
    }

    @Override
    protected void playSound(BlockSource p_338184_) {
        p_338184_.level().levelEvent(this.dispenseConfig.overrideDispenseEvent().orElse(1002), p_338184_.pos(), 0);
    }
}
