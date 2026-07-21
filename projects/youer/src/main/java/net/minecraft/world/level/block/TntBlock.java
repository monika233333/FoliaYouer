package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.bukkit.craftbukkit.event.CraftEventFactory;

public class TntBlock extends Block {
    public static final MapCodec<TntBlock> CODEC = simpleCodec(TntBlock::new);
    public static final BooleanProperty UNSTABLE = BlockStateProperties.UNSTABLE;

    @Override
    public MapCodec<TntBlock> codec() {
        return CODEC;
    }

    public TntBlock(BlockBehaviour.Properties p_57422_) {
        super(p_57422_);
        this.registerDefaultState(this.defaultBlockState().setValue(UNSTABLE, Boolean.valueOf(false)));
    }

    @Override
    public void onCaughtFire(BlockState state, Level world, BlockPos pos, @Nullable net.minecraft.core.Direction face, @Nullable LivingEntity igniter) {
        explode(world, pos, igniter);
    }

    protected void onPlace(BlockState p_57466_, Level p_57467_, BlockPos p_57468_, BlockState p_57469_, boolean p_57470_) {
        if (!p_57469_.is(p_57466_.getBlock())) {
            if (p_57467_.hasNeighborSignal(p_57468_) && CraftEventFactory.callTNTPrimeEvent(p_57467_, p_57468_, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.REDSTONE, null, null)) { // CraftBukkit - TNTPrimeEvent
                // Paper start - TNTPrimeEvent
                org.bukkit.block.Block tntBlock = org.bukkit.craftbukkit.block.CraftBlock.at(p_57467_, p_57468_);
                if (!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.REDSTONE, null).callEvent()) {
                    return;
                }
                // Paper end - TNTPrimeEvent
                onCaughtFire(p_57466_, p_57467_, p_57468_, null, null);
                p_57467_.removeBlock(p_57468_, false);
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState p_57457_, Level p_57458_, BlockPos p_57459_, Block p_57460_, BlockPos p_57461_, boolean p_57462_) {
        if (p_57458_.hasNeighborSignal(p_57459_) && CraftEventFactory.callTNTPrimeEvent(p_57458_, p_57459_, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.REDSTONE, null, p_57461_)) { // CraftBukkit - TNTPrimeEvent
            // Paper start - TNTPrimeEvent
            org.bukkit.block.Block tntBlock = org.bukkit.craftbukkit.block.CraftBlock.at(p_57458_, p_57459_);
            if (!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.REDSTONE, null).callEvent()) {
                return;
            }
            // Paper end - TNTPrimeEvent
            onCaughtFire(p_57457_, p_57458_, p_57459_, null, null);
            p_57458_.removeBlock(p_57459_, false);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level p_57445_, BlockPos p_57446_, BlockState p_57447_, Player p_57448_) {
        if (!p_57445_.isClientSide() && !p_57448_.isCreative() && p_57447_.getValue(UNSTABLE) && CraftEventFactory.callTNTPrimeEvent(p_57445_, p_57446_, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.BLOCK_BREAK, p_57448_, null)) { // CraftBukkit - TNTPrimeEvent
            onCaughtFire(p_57447_, p_57445_, p_57446_, null, null);
        }

        return super.playerWillDestroy(p_57445_, p_57446_, p_57447_, p_57448_);
    }

    @Override
    public void wasExploded(Level p_57441_, BlockPos p_57442_, Explosion p_57443_) {
        if (!p_57441_.isClientSide) {
            // Paper start - TNTPrimeEvent
            org.bukkit.block.Block tntBlock = org.bukkit.craftbukkit.block.CraftBlock.at(p_57441_, p_57442_);
            org.bukkit.entity.Entity source = p_57443_.source != null ? p_57443_.source.getBukkitEntity() : null;
            if (!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.EXPLOSION, source).callEvent()) {
                return;
            }
            // Paper end - TNTPrimeEvent
            PrimedTnt primedtnt = new PrimedTnt(
                p_57441_, (double)p_57442_.getX() + 0.5, (double)p_57442_.getY(), (double)p_57442_.getZ() + 0.5, p_57443_.getIndirectSourceEntity()
            );
            int i = primedtnt.getFuse();
            primedtnt.setFuse((short)(p_57441_.random.nextInt(i / 4) + i / 8));
            p_57441_.addFreshEntity(primedtnt);
        }
    }

    @Deprecated //Forge: Prefer using IForgeBlock#catchFire
    public static void explode(Level p_57434_, BlockPos p_57435_) {
        explode(p_57434_, p_57435_, null);
    }

    @Deprecated //Forge: Prefer using IForgeBlock#catchFire
    private static void explode(Level p_57437_, BlockPos p_57438_, @Nullable LivingEntity p_57439_) {
        if (!p_57437_.isClientSide) {
            PrimedTnt primedtnt = new PrimedTnt(p_57437_, (double)p_57438_.getX() + 0.5, (double)p_57438_.getY(), (double)p_57438_.getZ() + 0.5, p_57439_);
            p_57437_.addFreshEntity(primedtnt);
            p_57437_.playSound(null, primedtnt.getX(), primedtnt.getY(), primedtnt.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
            p_57437_.gameEvent(p_57439_, GameEvent.PRIME_FUSE, p_57438_);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack p_316149_, BlockState p_316217_, Level p_316520_, BlockPos p_316601_, Player p_316770_, InteractionHand p_316393_, BlockHitResult p_316532_
    ) {
        if (!p_316149_.is(Items.FLINT_AND_STEEL) && !p_316149_.is(Items.FIRE_CHARGE)) {
            return super.useItemOn(p_316149_, p_316217_, p_316520_, p_316601_, p_316770_, p_316393_, p_316532_);
        } else {
            // CraftBukkit start - TNTPrimeEvent
            if (!CraftEventFactory.callTNTPrimeEvent(p_316520_, p_316601_, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.PLAYER, p_316770_, null)) {
                return ItemInteractionResult.CONSUME;
            }
            // CraftBukkit end
            // Paper start - TNTPrimeEvent
            org.bukkit.block.Block tntBlock = org.bukkit.craftbukkit.block.CraftBlock.at(p_316520_, p_316601_);
            if (!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.ITEM, p_316770_.getBukkitEntity()).callEvent()) {
                return ItemInteractionResult.FAIL;
            }
            // Paper end - TNTPrimeEvent
            onCaughtFire(p_316217_, p_316520_, p_316601_, p_316532_.getDirection(), p_316770_);
            p_316520_.setBlock(p_316601_, Blocks.AIR.defaultBlockState(), 11);
            Item item = p_316149_.getItem();
            if (p_316149_.is(Items.FLINT_AND_STEEL)) {
                p_316149_.hurtAndBreak(1, p_316770_, LivingEntity.getSlotForHand(p_316393_));
            } else {
                p_316149_.consume(1, p_316770_);
            }

            p_316770_.awardStat(Stats.ITEM_USED.get(item));
            return ItemInteractionResult.sidedSuccess(p_316520_.isClientSide);
        }
    }

    @Override
    protected void onProjectileHit(Level p_57429_, BlockState p_57430_, BlockHitResult p_57431_, Projectile p_57432_) {
        if (!p_57429_.isClientSide) {
            BlockPos blockpos = p_57431_.getBlockPos();
            Entity entity = p_57432_.getOwner();
            if (p_57432_.isOnFire() && p_57432_.mayInteract(p_57429_, blockpos)) {
                // CraftBukkit start
                if (!CraftEventFactory.callEntityChangeBlockEvent(p_57432_, blockpos, p_57430_.getFluidState().createLegacyBlock()) || !CraftEventFactory.callTNTPrimeEvent(p_57429_, blockpos, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.PROJECTILE, p_57432_, null)) { // Paper - fix wrong block state
                    return;
                }
                // CraftBukkit end
                // Paper start - TNTPrimeEvent
                org.bukkit.block.Block tntBlock = org.bukkit.craftbukkit.block.CraftBlock.at(p_57429_, blockpos);
                if (!new com.destroystokyo.paper.event.block.TNTPrimeEvent(tntBlock, com.destroystokyo.paper.event.block.TNTPrimeEvent.PrimeReason.PROJECTILE, p_57432_.getBukkitEntity()).callEvent()) {
                    return;
                }
                // Paper end - TNTPrimeEvent
                onCaughtFire(p_57430_, p_57429_, blockpos, null, entity instanceof LivingEntity ? (LivingEntity)entity : null);
                p_57429_.removeBlock(blockpos, false);
            }
        }
    }

    @Override
    public boolean dropFromExplosion(Explosion p_57427_) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_57464_) {
        p_57464_.add(UNSTABLE);
    }
}
