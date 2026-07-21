package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.block.CraftBlockStates;
import org.bukkit.craftbukkit.event.CraftEventFactory;

public class ConcretePowderBlock extends FallingBlock {
    public static final MapCodec<ConcretePowderBlock> CODEC = RecordCodecBuilder.mapCodec(
        p_344654_ -> p_344654_.group(BuiltInRegistries.BLOCK.byNameCodec().fieldOf("concrete").forGetter(p_304618_ -> p_304618_.concrete), propertiesCodec())
                .apply(p_344654_, ConcretePowderBlock::new)
    );
    private final Block concrete;

    @Override
    public MapCodec<ConcretePowderBlock> codec() {
        return CODEC;
    }

    public ConcretePowderBlock(Block p_52060_, BlockBehaviour.Properties p_52061_) {
        super(p_52061_);
        this.concrete = p_52060_;
    }

    @Override
    public void onLand(Level p_52068_, BlockPos p_52069_, BlockState p_52070_, BlockState p_52071_, FallingBlockEntity p_52072_) {
        if (shouldSolidify(p_52068_, p_52069_, p_52070_, p_52071_.getFluidState())) { // Forge: Use block of falling entity instead of block at replaced position, and check if shouldSolidify with the FluidState of the replaced block
            CraftEventFactory.handleBlockFormEvent(p_52068_, p_52069_, this.concrete.defaultBlockState(), 3); // CraftBukkit
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext p_52063_) {
        BlockGetter blockgetter = p_52063_.getLevel();
        BlockPos blockpos = p_52063_.getClickedPos();
        BlockState blockstate = blockgetter.getBlockState(blockpos);

        // CraftBukkit start
        if (!shouldSolidify(blockgetter, blockpos, blockstate)) {
            return super.getStateForPlacement(p_52063_);
        }

        // TODO: An event factory call for methods like this
        CraftBlockState blockState = CraftBlockStates.getBlockState(p_52063_.getLevel(), blockpos);
        blockState.setData(this.concrete.defaultBlockState());

        org.bukkit.event.block.BlockFormEvent event = new org.bukkit.event.block.BlockFormEvent(blockState.getBlock(), blockState);
        ((Level) blockgetter).getCraftServer().getPluginManager().callEvent(event);

        if (!event.isCancelled()) {
            return blockState.getHandle();
        }

        return super.getStateForPlacement(p_52063_);
        // CraftBukkit end
    }

    private static boolean shouldSolidify(BlockGetter p_52081_, BlockPos p_52082_, BlockState p_52083_, net.minecraft.world.level.material.FluidState fluidState) {
        return p_52083_.canBeHydrated(p_52081_, p_52082_, fluidState, p_52082_) || touchesLiquid(p_52081_, p_52082_, p_52083_);
    }

    private static boolean shouldSolidify(BlockGetter p_52081_, BlockPos p_52082_, BlockState p_52083_) {
        return shouldSolidify(p_52081_, p_52082_, p_52083_, p_52081_.getFluidState(p_52082_));
    }

    private static boolean touchesLiquid(BlockGetter p_52065_, BlockPos p_52066_, BlockState state) {
        boolean flag = false;
        BlockPos.MutableBlockPos blockpos$mutableblockpos = p_52066_.mutable();

        for (Direction direction : Direction.values()) {
            BlockState blockstate = p_52065_.getBlockState(blockpos$mutableblockpos);
            if (direction != Direction.DOWN || state.canBeHydrated(p_52065_, p_52066_, blockstate.getFluidState(), blockpos$mutableblockpos)) {
                blockpos$mutableblockpos.setWithOffset(p_52066_, direction);
                blockstate = p_52065_.getBlockState(blockpos$mutableblockpos);
                if (state.canBeHydrated(p_52065_, p_52066_, blockstate.getFluidState(), blockpos$mutableblockpos) && !blockstate.isFaceSturdy(p_52065_, p_52066_, direction.getOpposite())) {
                    flag = true;
                    break;
                }
            }
        }

        return flag;
    }

    private static boolean canSolidify(BlockState p_52089_) {
        return p_52089_.getFluidState().is(FluidTags.WATER);
    }

    @Override
    protected BlockState updateShape(BlockState p_52074_, Direction p_52075_, BlockState p_52076_, LevelAccessor p_52077_, BlockPos p_52078_, BlockPos p_52079_) {
        // CraftBukkit start
        if (touchesLiquid(p_52077_, p_52078_, p_52074_)) {
            // Suppress during worldgen
            if (!(p_52077_ instanceof Level)) {
                return this.concrete.defaultBlockState();
            }
            CraftBlockState blockState = CraftBlockStates.getBlockState(p_52077_, p_52078_);
            blockState.setData(this.concrete.defaultBlockState());

            org.bukkit.event.block.BlockFormEvent event = new org.bukkit.event.block.BlockFormEvent(blockState.getBlock(), blockState);
            ((Level) p_52077_).getCraftServer().getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                return blockState.getHandle();
            }
        }

        return super.updateShape(p_52074_, p_52075_, p_52076_, p_52077_, p_52078_, p_52079_);
        // CraftBukkit end
    }

    @Override
    public int getDustColor(BlockState p_52085_, BlockGetter p_52086_, BlockPos p_52087_) {
        return p_52085_.getMapColor(p_52086_, p_52087_).col;
    }
}
