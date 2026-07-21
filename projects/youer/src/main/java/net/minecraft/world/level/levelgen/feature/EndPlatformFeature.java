package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.util.BlockStateListPopulator;
import org.bukkit.event.world.PortalCreateEvent;

public class EndPlatformFeature extends Feature<NoneFeatureConfiguration> {
    public EndPlatformFeature(Codec<NoneFeatureConfiguration> p_352966_) {
        super(p_352966_);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> p_352935_) {
        createEndPlatform(p_352935_.level(), p_352935_.origin(), false);
        return true;
    }

    private static final AtomicReference<Entity> createEndPlatform$entity = new AtomicReference<>();

    public static void createEndPlatform(ServerLevelAccessor p_352905_, BlockPos p_352961_, boolean p_352931_) {
        BlockStateListPopulator blockList = new BlockStateListPopulator(p_352905_);
        // CraftBukkit end
        BlockPos.MutableBlockPos blockpos$mutableblockpos = p_352961_.mutable();

        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                for (int k = -1; k < 3; k++) {
                    BlockPos blockpos = blockpos$mutableblockpos.set(p_352961_).move(j, k, i);
                    Block block = k == -1 ? Blocks.OBSIDIAN : Blocks.AIR;
                    // CraftBukkit start
                    if (!blockList.getBlockState(blockpos).is(block)) {
                        if (p_352931_) {
                            // blockList.destroyBlock(blockposition_mutableblockposition1, true, (Entity) null); // Paper - moved down - cb implementation of LevelAccessor does not support destroyBlock
                        }

                        blockList.setBlock(blockpos, block.defaultBlockState(), 3);
                        // CraftBukkit end
                    }
                }
            }
        }

        // CraftBukkit start
        if (createEndPlatform$entity.get() != null) {
            org.bukkit.World bworld = p_352905_.getLevel().getWorld();
            PortalCreateEvent portalEvent = new PortalCreateEvent((List<BlockState>) (List) blockList.getList(), bworld, createEndPlatform$entity.getAndSet(null).getBukkitEntity(), org.bukkit.event.world.PortalCreateEvent.CreateReason.END_PLATFORM);

            p_352905_.getLevel().getCraftServer().getPluginManager().callEvent(portalEvent);
            if (portalEvent.isCancelled()) {
                return;
            }
        }

        // SPIGOT-7856: End platform not dropping items after replacing blocks
        if (p_352931_) {
            blockList.getList().forEach((state) -> p_352905_.destroyBlock(state.getPosition(), true, null));
        }
        blockList.updateList();
        // CraftBukkit end
    }

    public static void createEndPlatform(ServerLevelAccessor p_352905_, BlockPos p_352961_, boolean p_352931_, net.minecraft.world.entity.Entity entity) {
        createEndPlatform$entity.set(entity);
        createEndPlatform(p_352905_, p_352961_, p_352931_);
    }
}
