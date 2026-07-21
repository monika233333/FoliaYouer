package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.bukkit.craftbukkit.event.CraftEventFactory;

public class PressurePlateBlock extends BasePressurePlateBlock {
    public static final MapCodec<PressurePlateBlock> CODEC = RecordCodecBuilder.mapCodec(
        p_308833_ -> p_308833_.group(BlockSetType.CODEC.fieldOf("block_set_type").forGetter(p_304917_ -> p_304917_.type), propertiesCodec())
                .apply(p_308833_, PressurePlateBlock::new)
    );
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    @Override
    public MapCodec<PressurePlateBlock> codec() {
        return CODEC;
    }

    public PressurePlateBlock(BlockSetType p_273284_, BlockBehaviour.Properties p_273571_) {
        super(p_273571_, p_273284_);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, Boolean.valueOf(false)));
    }

    @Override
    protected int getSignalForState(BlockState p_55270_) {
        return p_55270_.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected BlockState setSignalForState(BlockState p_55259_, int p_55260_) {
        return p_55259_.setValue(POWERED, Boolean.valueOf(p_55260_ > 0));
    }

    @Override
    protected int getSignalStrength(Level p_55264_, BlockPos p_55265_) {
        Class<? extends Entity> oclass = switch (this.type.pressurePlateSensitivity()) {
            case EVERYTHING -> Entity.class;
            case MOBS -> LivingEntity.class;
        };

        // CraftBukkit start - Call interact event when turning on a pressure plate
        for (Entity entity : getEntities(p_55264_, TOUCH_AABB.move(p_55265_), oclass)) {
            if (this.getSignalForState(p_55264_.getBlockState(p_55265_)) == 0) {
                org.bukkit.World bworld = p_55264_.getWorld();
                org.bukkit.plugin.PluginManager manager = p_55264_.getCraftServer().getPluginManager();
                org.bukkit.event.Cancellable cancellable;

                if (entity instanceof net.minecraft.world.entity.player.Player) {
                    cancellable = CraftEventFactory.callPlayerInteractEvent((net.minecraft.world.entity.player.Player) entity, org.bukkit.event.block.Action.PHYSICAL, p_55265_, null, null, null);
                } else {
                    cancellable = new org.bukkit.event.entity.EntityInteractEvent(entity.getBukkitEntity(), bworld.getBlockAt(p_55265_.getX(), p_55265_.getY(), p_55265_.getZ()));
                    manager.callEvent((org.bukkit.event.entity.EntityInteractEvent) cancellable);
                }

                // We only want to block turning the plate on if all events are cancelled
                if (cancellable.isCancelled()) {
                    continue;
                }
            }

            return 15;
        }

        return 0;
        // CraftBukkit end
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_55262_) {
        p_55262_.add(POWERED);
    }
}
