package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;

public class SweetBerryBushBlock extends BushBlock implements BonemealableBlock {
    public static final MapCodec<SweetBerryBushBlock> CODEC = simpleCodec(SweetBerryBushBlock::new);
    private static final float HURT_SPEED_THRESHOLD = 0.003F;
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    private static final VoxelShape SAPLING_SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 8.0, 13.0);
    private static final VoxelShape MID_GROWTH_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);

    @Override
    public MapCodec<SweetBerryBushBlock> codec() {
        return CODEC;
    }

    public SweetBerryBushBlock(BlockBehaviour.Properties p_57249_) {
        super(p_57249_);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, Integer.valueOf(0)));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader p_304655_, BlockPos p_57257_, BlockState p_57258_) {
        return new ItemStack(Items.SWEET_BERRIES);
    }

    @Override
    protected VoxelShape getShape(BlockState p_57291_, BlockGetter p_57292_, BlockPos p_57293_, CollisionContext p_57294_) {
        if (p_57291_.getValue(AGE) == 0) {
            return SAPLING_SHAPE;
        } else {
            return p_57291_.getValue(AGE) < 3 ? MID_GROWTH_SHAPE : super.getShape(p_57291_, p_57292_, p_57293_, p_57294_);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState p_57284_) {
        return p_57284_.getValue(AGE) < 3;
    }

    @Override
    protected void randomTick(BlockState p_222563_, ServerLevel p_222564_, BlockPos p_222565_, RandomSource p_222566_) {
        int i = p_222563_.getValue(AGE);
        if (i < 3 && p_222564_.getRawBrightness(p_222565_.above(), 0) >= 9 && net.neoforged.neoforge.common.CommonHooks.canCropGrow(p_222564_, p_222565_, p_222563_, p_222566_.nextFloat() < (p_222564_.spigotConfig.sweetBerryModifier / (100.0f * 5)))) { // Spigot - SPIGOT-7159: Better modifier resolution
            BlockState blockstate = p_222563_.setValue(AGE, Integer.valueOf(i + 1));

            // CraftBukkit start
            if (!CraftEventFactory.handleBlockGrowEvent(p_222564_, p_222565_, blockstate, 2)) return;
            // CraftBukkit end

            net.neoforged.neoforge.common.CommonHooks.fireCropGrowPost(p_222564_, p_222565_, p_222563_);
            p_222564_.gameEvent(GameEvent.BLOCK_CHANGE, p_222565_, GameEvent.Context.of(blockstate));
        }
    }

    @Override
    protected void entityInside(BlockState p_57270_, Level p_57271_, BlockPos p_57272_, Entity p_57273_) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(p_57273_.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(p_57271_, p_57272_)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (p_57273_ instanceof LivingEntity && p_57273_.getType() != EntityType.FOX && p_57273_.getType() != EntityType.BEE) {
            p_57273_.makeStuckInBlock(p_57270_, new Vec3(0.8F, 0.75, 0.8F));
            if (!p_57271_.isClientSide && p_57270_.getValue(AGE) > 0 && (p_57273_.xOld != p_57273_.getX() || p_57273_.zOld != p_57273_.getZ())) {
                double d0 = Math.abs(p_57273_.getX() - p_57273_.xOld);
                double d1 = Math.abs(p_57273_.getZ() - p_57273_.zOld);
                if (d0 >= 0.003F || d1 >= 0.003F) {
                    p_57273_.hurt(p_57271_.damageSources().sweetBerryBush().directBlock(p_57271_, p_57272_), 1.0F); // CraftBukkit
                }
            }
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack p_316636_, BlockState p_316295_, Level p_316812_, BlockPos p_316380_, Player p_316731_, InteractionHand p_316188_, BlockHitResult p_316626_
    ) {
        int i = p_316295_.getValue(AGE);
        boolean flag = i == 3;
        return !flag && p_316636_.is(Items.BONE_MEAL)
            ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
            : super.useItemOn(p_316636_, p_316295_, p_316812_, p_316380_, p_316731_, p_316188_, p_316626_);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState p_316134_, Level p_316429_, BlockPos p_316748_, Player p_316431_, BlockHitResult p_316474_) {
        int i = p_316134_.getValue(AGE);
        boolean flag = i == 3;
        if (i > 1) {
            int j = 1 + p_316429_.random.nextInt(2);

            // CraftBukkit start - useWithoutItem is always MAIN_HAND
            org.bukkit.event.player.PlayerHarvestBlockEvent event = CraftEventFactory.callPlayerHarvestBlockEvent(p_316429_, p_316748_, p_316431_, InteractionHand.MAIN_HAND, java.util.Collections.singletonList(new ItemStack(Items.SWEET_BERRIES, j + (flag ? 1 : 0))));
            if (event.isCancelled()) {
                return InteractionResult.SUCCESS; // We need to return a success either way, because making it PASS or FAIL will result in a bug where cancelling while harvesting w/ block in hand places block
            }
            for (org.bukkit.inventory.ItemStack itemStack : event.getItemsHarvested()) {
                popResource(p_316429_, p_316748_, CraftItemStack.asNMSCopy(itemStack));
            }
            // CraftBukkit end

            p_316429_.playSound(
                null, p_316748_, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + p_316429_.random.nextFloat() * 0.4F
            );
            BlockState blockstate = p_316134_.setValue(AGE, Integer.valueOf(1));
            p_316429_.setBlock(p_316748_, blockstate, 2);
            p_316429_.gameEvent(GameEvent.BLOCK_CHANGE, p_316748_, GameEvent.Context.of(p_316431_, blockstate));
            return InteractionResult.sidedSuccess(p_316429_.isClientSide);
        } else {
            return super.useWithoutItem(p_316134_, p_316429_, p_316748_, p_316431_, p_316474_);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_57282_) {
        p_57282_.add(AGE);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader p_256056_, BlockPos p_57261_, BlockState p_57262_) {
        return p_57262_.getValue(AGE) < 3;
    }

    @Override
    public boolean isBonemealSuccess(Level p_222558_, RandomSource p_222559_, BlockPos p_222560_, BlockState p_222561_) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel p_222553_, RandomSource p_222554_, BlockPos p_222555_, BlockState p_222556_) {
        int i = Math.min(3, p_222556_.getValue(AGE) + 1);
        p_222553_.setBlock(p_222555_, p_222556_.setValue(AGE, Integer.valueOf(i)), 2);
    }
}
