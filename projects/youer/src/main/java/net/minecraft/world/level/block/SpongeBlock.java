package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.util.BlockStateListPopulator;

public class SpongeBlock extends Block {
    public static final MapCodec<SpongeBlock> CODEC = simpleCodec(SpongeBlock::new);
    public static final int MAX_DEPTH = 6;
    public static final int MAX_COUNT = 64;
    private static final Direction[] ALL_DIRECTIONS = Direction.values();

    @Override
    public MapCodec<SpongeBlock> codec() {
        return CODEC;
    }

    public SpongeBlock(BlockBehaviour.Properties p_56796_) {
        super(p_56796_);
    }

    @Override
    protected void onPlace(BlockState p_56811_, Level p_56812_, BlockPos p_56813_, BlockState p_56814_, boolean p_56815_) {
        if (!p_56814_.is(p_56811_.getBlock())) {
            this.tryAbsorbWater(p_56812_, p_56813_);
        }
    }

    @Override
    protected void neighborChanged(BlockState p_56801_, Level p_56802_, BlockPos p_56803_, Block p_56804_, BlockPos p_56805_, boolean p_56806_) {
        this.tryAbsorbWater(p_56802_, p_56803_);
        super.neighborChanged(p_56801_, p_56802_, p_56803_, p_56804_, p_56805_, p_56806_);
    }

    protected void tryAbsorbWater(Level p_56798_, BlockPos p_56799_) {
        if (this.removeWaterBreadthFirstSearch(p_56798_, p_56799_)) {
            p_56798_.setBlock(p_56799_, Blocks.WET_SPONGE.defaultBlockState(), 2);
            p_56798_.playSound(null, p_56799_, SoundEvents.SPONGE_ABSORB, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private boolean removeWaterBreadthFirstSearch(Level p_56808_, BlockPos p_56809_) {
        BlockState spongeState = p_56808_.getBlockState(p_56809_);
        // CraftBukkit start - Use BlockStateListPopulator
        BlockStateListPopulator blockList = new BlockStateListPopulator(p_56808_);
        BlockPos.breadthFirstTraversal(
                p_56809_,
                6,
                65,
                (p_277519_, p_277492_) -> {
                    for (Direction direction : ALL_DIRECTIONS) {
                        p_277492_.accept(p_277519_.relative(direction));
                    }
                },
                p_294069_ -> {
                    if (p_294069_.equals(p_56809_)) {
                        return true;
                    } else {
                    // CraftBukkit - Use blockList instead of direct world access
                    BlockState blockstate = blockList.getBlockState(p_294069_);
                    FluidState fluidstate = blockList.getFluidState(p_294069_);

                        if (!spongeState.canBeHydrated(p_56808_, p_56809_, fluidstate, p_294069_)) {
                            return false;
                        } else {
                            if (blockstate.getBlock() instanceof BucketPickup bucketpickup
                            && !bucketpickup.pickupBlock(null, blockList, p_294069_, blockstate).isEmpty()) {
                                return true;
                            }

                            if (blockstate.getBlock() instanceof LiquidBlock) {
                            blockList.setBlock(p_294069_, Blocks.AIR.defaultBlockState(), 3);
                            } else {
                                if (!blockstate.is(Blocks.KELP)
                                    && !blockstate.is(Blocks.KELP_PLANT)
                                    && !blockstate.is(Blocks.SEAGRASS)
                                    && !blockstate.is(Blocks.TALL_SEAGRASS)) {
                                    return false;
                                }

                            // Don't drop items in populators
                            blockList.setBlock(p_294069_, Blocks.AIR.defaultBlockState(), 3);
                        }

                        return true;
                    }
                }
            }
        );

        java.util.List<CraftBlockState> blocks = blockList.getList(); // Is a clone
        if (!blocks.isEmpty()) {
            final org.bukkit.block.Block bblock = p_56808_.getWorld().getBlockAt(p_56809_.getX(), p_56809_.getY(), p_56809_.getZ());

            org.bukkit.event.block.SpongeAbsorbEvent event = new org.bukkit.event.block.SpongeAbsorbEvent(bblock, (java.util.List<org.bukkit.block.BlockState>) (java.util.List) blocks);
            p_56808_.getCraftServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return false;
            }

            for (CraftBlockState block : blocks) {
                BlockPos blockPos = block.getPosition();
                BlockState blockState = p_56808_.getBlockState(blockPos);
                FluidState fluidState = p_56808_.getFluidState(blockPos);

                if (fluidState.is(FluidTags.WATER)) {
                    if (blockState.getBlock() instanceof BucketPickup bucketPickup
                            && !bucketPickup.pickupBlock(null, blockList, blockPos, blockState).isEmpty()) {
                        // NOP
                    } else if (blockState.getBlock() instanceof LiquidBlock) {
                        // NOP
                    } else if (blockState.is(Blocks.KELP) || blockState.is(Blocks.KELP_PLANT)
                            || blockState.is(Blocks.SEAGRASS) || blockState.is(Blocks.TALL_SEAGRASS)) {
                        BlockEntity blockEntity = blockState.hasBlockEntity() ? p_56808_.getBlockEntity(blockPos) : null;
                        // Paper start - Fix SpongeAbsortEvent handling
                        if (block.getHandle().isAir()) {
                        dropResources(blockState, p_56808_, blockPos, blockEntity);
                        }
                        // Paper end - Fix SpongeAbsortEvent handling
                    }
                }
                p_56808_.setBlock(blockPos, block.getHandle(), block.getFlag());
                            }

                            return true;
                        }
        return false;
        // CraftBukkit end
    }
}
