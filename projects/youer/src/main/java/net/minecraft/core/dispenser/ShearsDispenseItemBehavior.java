package net.minecraft.core.dispenser;

import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseEvent;

public class ShearsDispenseItemBehavior extends OptionalDispenseItemBehavior {
    @Override
    protected ItemStack execute(BlockSource p_302443_, ItemStack p_123581_) {
        ServerLevel serverlevel = p_302443_.level();
        // CraftBukkit start
        org.bukkit.block.Block bukkitBlock = CraftBlock.at(serverlevel, p_302443_.pos());
        CraftItemStack craftItem = CraftItemStack.asCraftMirror(p_123581_);

        BlockDispenseEvent event = new BlockDispenseEvent(bukkitBlock, craftItem.clone(), new org.bukkit.util.Vector(0, 0, 0));
        if (!DispenserBlock.eventFired) {
            serverlevel.getCraftServer().getPluginManager().callEvent(event);
        }

        if (event.isCancelled()) {
            return p_123581_;
        }

        if (!event.getItem().equals(craftItem)) {
            // Chain to handler for new item
            ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
            DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
            if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                idispensebehavior.dispense(p_302443_, eventStack);
                return p_123581_;
            }
        }
        // CraftBukkit end
        if (!serverlevel.isClientSide()) {
            BlockPos blockpos = p_302443_.pos().relative(p_302443_.state().getValue(DispenserBlock.FACING));
            bukkitBlockAndcraftItem(bukkitBlock, craftItem); // Mohist
            this.setSuccess(net.neoforged.neoforge.common.CommonHooks.tryDispenseShearsHarvestBlock(p_302443_, p_123581_, serverlevel, blockpos) || tryShearBeehive(serverlevel, blockpos) || tryShearLivingEntity(serverlevel, blockpos, p_123581_)); // CraftBukkit
            if (this.isSuccess()) {
                p_123581_.hurtAndBreak(1, serverlevel, null, p_348118_ -> {
                });
            }
        }

        return p_123581_;
    }

    private static boolean tryShearBeehive(ServerLevel p_123577_, BlockPos p_123578_) {
        BlockState blockstate = p_123577_.getBlockState(p_123578_);
        if (blockstate.is(BlockTags.BEEHIVES, p_202454_ -> p_202454_.hasProperty(BeehiveBlock.HONEY_LEVEL) && p_202454_.getBlock() instanceof BeehiveBlock)) {
            int i = blockstate.getValue(BeehiveBlock.HONEY_LEVEL);
            if (i >= 5) {
                p_123577_.playSound(null, p_123578_, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
                BeehiveBlock.dropHoneycomb(p_123577_, p_123578_);
                ((BeehiveBlock)blockstate.getBlock())
                    .releaseBeesAndResetHoneyLevel(p_123577_, blockstate, p_123578_, null, BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED);
                p_123577_.gameEvent(null, GameEvent.SHEAR, p_123578_);
                return true;
            }
        }

        return false;
    }

    // Mohist start
    private static AtomicReference<Block> bukkitBlock0 = new AtomicReference<>();
    private static AtomicReference<CraftItemStack> craftItem0 = new AtomicReference<>();
    private static void bukkitBlockAndcraftItem(org.bukkit.block.Block bukkitBlock, CraftItemStack craftItem) {
        bukkitBlock0.set(bukkitBlock);
        craftItem0.set(craftItem);
    }

    private static boolean tryShearLivingEntity(ServerLevel p_123583_, BlockPos p_123584_, ItemStack stack) { // CraftBukkit - add args
        for (LivingEntity livingentity : p_123583_.getEntitiesOfClass(LivingEntity.class, new AABB(p_123584_), EntitySelector.NO_SPECTATORS)) {
            if (livingentity instanceof net.neoforged.neoforge.common.IShearable shearable && shearable.isShearable(null, stack, p_123583_, p_123584_)) {
                // CraftBukkit start
                // Paper start - Add drops to shear events
                org.bukkit.event.block.BlockShearEntityEvent event = CraftEventFactory.callBlockShearEntityEvent(livingentity, bukkitBlock0.getAndSet(null), craftItem0.getAndSet(null), shearable.generateDefaultDrops());
                if (event.isCancelled()) {
                    // Paper end - Add drops to shear events
                    continue;
                }
                // CraftBukkit end
                shearable.onSheared(null, stack, p_123583_, p_123584_, CraftItemStack.asNMSCopy(event.getDrops())) // Paper - Add drops to shear events
                        .forEach(drop -> shearable.spawnShearedDrop(p_123583_, p_123584_, drop));
                p_123583_.gameEvent(null, GameEvent.SHEAR, p_123584_);
                return true;
            }
        }

        return false;
    }
}
