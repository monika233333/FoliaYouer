package net.minecraft.world.item;

import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.event.block.BlockCanBuildEvent;

public class StandingAndWallBlockItem extends BlockItem {
    public final Block wallBlock;
    private final Direction attachmentDirection;

    public StandingAndWallBlockItem(Block p_248873_, Block p_251044_, Item.Properties p_249308_, Direction p_250800_) {
        super(p_248873_, p_249308_);
        this.wallBlock = p_251044_;
        this.attachmentDirection = p_250800_;
    }

    protected boolean canPlace(LevelReader p_250350_, BlockState p_249311_, BlockPos p_250328_) {
        return p_249311_.canSurvive(p_250350_, p_250328_);
    }

    @Nullable
    @Override
    protected BlockState getPlacementState(BlockPlaceContext p_43255_) {
        BlockState blockstate = this.wallBlock.getStateForPlacement(p_43255_);
        BlockState blockstate1 = null;
        LevelReader levelreader = p_43255_.getLevel();
        BlockPos blockpos = p_43255_.getClickedPos();

        for (Direction direction : p_43255_.getNearestLookingDirections()) {
            if (direction != this.attachmentDirection.getOpposite()) {
                BlockState blockstate2 = direction == this.attachmentDirection ? this.getBlock().getStateForPlacement(p_43255_) : blockstate;
                if (blockstate2 != null && this.canPlace(levelreader, blockstate2, blockpos)) {
                    blockstate1 = blockstate2;
                    break;
                }
            }
        }

        // CraftBukkit start
        if (blockstate1 != null) {
            boolean defaultReturn = levelreader.isUnobstructed(blockstate1, blockpos, CollisionContext.empty());
            org.bukkit.entity.Player player = (p_43255_.getPlayer() instanceof ServerPlayer) ? (org.bukkit.entity.Player) p_43255_.getPlayer().getBukkitEntity() : null;

            BlockCanBuildEvent event = new BlockCanBuildEvent(CraftBlock.at(p_43255_.getLevel(), blockpos), player, CraftBlockData.fromData(blockstate1), defaultReturn, org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(p_43255_.getHand())); // Paper - Expose hand in BlockCanBuildEvent
            p_43255_.getLevel().getCraftServer().getPluginManager().callEvent(event);

            return (event.isBuildable()) ? blockstate1 : null;
        } else {
            return null;
        }
        // CraftBukkit end
    }

    @Override
    public void registerBlocks(Map<Block, Item> p_43252_, Item p_43253_) {
        super.registerBlocks(p_43252_, p_43253_);
        p_43252_.put(this.wallBlock, p_43253_);
    }

    /** @deprecated Neo: To be removed without replacement since registry replacement is not a feature anymore. */
    @Deprecated(forRemoval = true, since = "1.21.1")
    public void removeFromBlockToItemMap(Map<Block, Item> blockToItemMap, Item itemIn) {
        super.removeFromBlockToItemMap(blockToItemMap, itemIn);
        blockToItemMap.remove(this.wallBlock);
    }
}
