package net.minecraft.world.phys.shapes;

import com.google.common.collect.Lists;
import com.google.common.math.DoubleMath;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public abstract class VoxelShape implements ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape { // Paper - optimise collisions // Folia - moonrise collisions
    public final DiscreteVoxelShape shape; // Paper - optimise collisions - public
    @Nullable
    private VoxelShape[] faces;

    // Paper start - optimise collisions // Folia - moonrise collisions
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private AABB singleAABBRepresentation;
    private double[] rootCoordinatesX;
    private double[] rootCoordinatesY;
    private double[] rootCoordinatesZ;
    private ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData cachedShapeData;
    private boolean isEmpty;
    private ca.spottedleaf.moonrise.patches.collisions.shape.CachedToAABBs cachedToAABBs;
    private AABB cachedBounds;
    private Boolean isFullBlock;
    private Boolean occludesFullBlock;

    // must be power of two
    private static final int MERGED_CACHE_SIZE = 16;
    private ca.spottedleaf.moonrise.patches.collisions.shape.MergedORCache[] mergedORCache;

    @Override
    public final double moonrise$offsetX() {
        return this.offsetX;
    }

    @Override
    public final double moonrise$offsetY() {
        return this.offsetY;
    }

    @Override
    public final double moonrise$offsetZ() {
        return this.offsetZ;
    }

    @Override
    public final AABB moonrise$getSingleAABBRepresentation() {
        return this.singleAABBRepresentation;
    }

    @Override
    public final double[] moonrise$rootCoordinatesX() {
        return this.rootCoordinatesX;
    }

    @Override
    public final double[] moonrise$rootCoordinatesY() {
        return this.rootCoordinatesY;
    }

    @Override
    public final double[] moonrise$rootCoordinatesZ() {
        return this.rootCoordinatesZ;
    }

    private static double[] extractRawArray(final DoubleList list) {
        if (list instanceof it.unimi.dsi.fastutil.doubles.DoubleArrayList rawList) {
            final double[] raw = rawList.elements();
            final int expected = rawList.size();
            if (raw.length == expected) {
                return raw;
            } else {
                return java.util.Arrays.copyOf(raw, expected);
            }
        } else {
            return list.toDoubleArray();
        }
    }

    @Override
    public final void moonrise$initCache() {
        // FoliaYouer: make initCache idempotent - skip if already initialized
        if (this.cachedShapeData != null) {
            return;
        }
        this.cachedShapeData = ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionDiscreteVoxelShape)this.shape).moonrise$getOrCreateCachedShapeData();
        this.isEmpty = this.cachedShapeData.isEmpty();

        final DoubleList xList = this.getCoords(Direction.Axis.X);
        final DoubleList yList = this.getCoords(Direction.Axis.Y);
        final DoubleList zList = this.getCoords(Direction.Axis.Z);

        if (xList instanceof OffsetDoubleList offsetDoubleList) {
            this.offsetX = offsetDoubleList.offset;
            this.rootCoordinatesX = extractRawArray(offsetDoubleList.delegate);
        } else {
            this.offsetX = 0.0;
            this.rootCoordinatesX = extractRawArray(xList);
        }

        if (yList instanceof OffsetDoubleList offsetDoubleList) {
            this.offsetY = offsetDoubleList.offset;
            this.rootCoordinatesY = extractRawArray(offsetDoubleList.delegate);
        } else {
            this.offsetY = 0.0;
            this.rootCoordinatesY = extractRawArray(yList);
        }

        if (zList instanceof OffsetDoubleList offsetDoubleList) {
            this.offsetZ = offsetDoubleList.offset;
            this.rootCoordinatesZ = extractRawArray(offsetDoubleList.delegate);
        } else {
            this.offsetZ = 0.0;
            this.rootCoordinatesZ = extractRawArray(zList);
        }

        if (this.cachedShapeData.hasSingleAABB()) {
            this.singleAABBRepresentation = new AABB(
                this.rootCoordinatesX[0] + this.offsetX, this.rootCoordinatesY[0] + this.offsetY, this.rootCoordinatesZ[0] + this.offsetZ,
                this.rootCoordinatesX[1] + this.offsetX, this.rootCoordinatesY[1] + this.offsetY, this.rootCoordinatesZ[1] + this.offsetZ
            );
            this.cachedBounds = this.singleAABBRepresentation;
        }
    }

    @Override
    public final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData moonrise$getCachedVoxelData() {
        return this.cachedShapeData;
    }

    private VoxelShape[] faceShapeClampedCache;

    @Override
    public final VoxelShape moonrise$getFaceShapeClamped(final Direction direction) {
        if (this.isEmpty) {
            return (VoxelShape)(Object)this;
        }
        if ((VoxelShape)(Object)this == Shapes.block()) {
            return (VoxelShape)(Object)this;
        }

        VoxelShape[] cache = this.faceShapeClampedCache;
        if (cache != null) {
            final VoxelShape ret = cache[direction.ordinal()];
            if (ret != null) {
                return ret;
            }
        }


        if (cache == null) {
            this.faceShapeClampedCache = cache = new VoxelShape[6];
        }

        final Direction.Axis axis = direction.getAxis();

        final VoxelShape ret;

        if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            if (DoubleMath.fuzzyEquals(this.max(axis), 1.0, ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON)) {
                ret = tryForceBlock(new SliceShape((VoxelShape)(Object)this, axis, this.shape.getSize(axis) - 1));
            } else {
                ret = Shapes.empty();
            }
        } else {
            if (DoubleMath.fuzzyEquals(this.min(axis), 0.0, ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON)) {
                ret = tryForceBlock(new SliceShape((VoxelShape)(Object)this, axis, 0));
            } else {
                ret = Shapes.empty();
            }
        }

        cache[direction.ordinal()] = ret;

        return ret;
    }

    private static VoxelShape tryForceBlock(final VoxelShape other) {
        if (other == Shapes.block()) {
            return other;
        }

        final AABB otherAABB = ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)other).moonrise$getSingleAABBRepresentation();
        if (otherAABB == null) {
            return other;
        }

        if (((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)Shapes.block()).moonrise$getSingleAABBRepresentation().equals(otherAABB)) {
            return Shapes.block();
        }

        return other;
    }

    private boolean computeOccludesFullBlock() {
        if (this.isEmpty) {
            this.occludesFullBlock = Boolean.FALSE;
            return false;
        }

        if (this.moonrise$isFullBlock()) {
            this.occludesFullBlock = Boolean.TRUE;
            return true;
        }

        final AABB singleAABB = this.singleAABBRepresentation;
        if (singleAABB != null) {
            // check if the bounding box encloses the full cube
            final boolean ret =
                (singleAABB.minY <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON && singleAABB.maxY >= (1 - ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON)) &&
                    (singleAABB.minX <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON && singleAABB.maxX >= (1 - ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON)) &&
                    (singleAABB.minZ <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON && singleAABB.maxZ >= (1 - ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON));
            this.occludesFullBlock = Boolean.valueOf(ret);
            return ret;
        }

        final boolean ret = !Shapes.joinIsNotEmpty(Shapes.block(), ((VoxelShape)(Object)this), BooleanOp.ONLY_FIRST);
        this.occludesFullBlock = Boolean.valueOf(ret);
        return ret;
    }

    @Override
    public final boolean moonrise$occludesFullBlock() {
        final Boolean ret = this.occludesFullBlock;
        if (ret != null) {
            return ret.booleanValue();
        }

        return this.computeOccludesFullBlock();
    }

    @Override
    public final boolean moonrise$occludesFullBlockIfCached() {
        final Boolean ret = this.occludesFullBlock;
        return ret != null ? ret.booleanValue() : false;
    }

    private static int hash(final VoxelShape key) {
        return it.unimi.dsi.fastutil.HashCommon.mix(System.identityHashCode(key));
    }

    @Override
    public final VoxelShape moonrise$orUnoptimized(final VoxelShape other) {
        // don't cache simple cases
        if (((VoxelShape)(Object)this) == other) {
            return other;
        }

        if (this.isEmpty) {
            return other;
        }

        if (other.isEmpty()) {
            return (VoxelShape)(Object)this;
        }

        // try this cache first
        final int thisCacheKey = hash(other) & (MERGED_CACHE_SIZE - 1);
        final ca.spottedleaf.moonrise.patches.collisions.shape.MergedORCache cached = this.mergedORCache == null ? null : this.mergedORCache[thisCacheKey];
        if (cached != null && cached.key() == other) {
            return cached.result();
        }

        // try other cache
        final int otherCacheKey = hash((VoxelShape)(Object)this) & (MERGED_CACHE_SIZE - 1);
        final ca.spottedleaf.moonrise.patches.collisions.shape.MergedORCache otherCache = ((VoxelShape)(Object)other).mergedORCache == null ? null : ((VoxelShape)(Object)other).mergedORCache[otherCacheKey];
        if (otherCache != null && otherCache.key() == (VoxelShape)(Object)this) {
            return otherCache.result();
        }

        // note: unsure if joinUnoptimized(1, 2, OR) == joinUnoptimized(2, 1, OR) for all cases
        final VoxelShape result = Shapes.joinUnoptimized((VoxelShape)(Object)this, other, BooleanOp.OR);

        if (cached != null && otherCache == null) {
            // try to use second cache instead of replacing an entry in this cache
            if (((VoxelShape)(Object)other).mergedORCache == null) {
                ((VoxelShape)(Object)other).mergedORCache = new ca.spottedleaf.moonrise.patches.collisions.shape.MergedORCache[MERGED_CACHE_SIZE];
            }
            ((VoxelShape)(Object)other).mergedORCache[otherCacheKey] = new ca.spottedleaf.moonrise.patches.collisions.shape.MergedORCache((VoxelShape)(Object)this, result);
        } else {
            // line is not occupied or other cache line is full
            // always bias to replace this cache, as this cache is the first we check
            if (this.mergedORCache == null) {
                this.mergedORCache = new ca.spottedleaf.moonrise.patches.collisions.shape.MergedORCache[MERGED_CACHE_SIZE];
            }
            this.mergedORCache[thisCacheKey] = new ca.spottedleaf.moonrise.patches.collisions.shape.MergedORCache(other, result);
        }

        return result;
    }

    private static DoubleList offsetList(final DoubleList src, final double by) {
        if (src instanceof OffsetDoubleList offsetDoubleList) {
            return new OffsetDoubleList(offsetDoubleList.delegate, by + offsetDoubleList.offset);
        }
        return new OffsetDoubleList(src, by);
    }

    private List<AABB> toAabbsUncached() {
        final List<AABB> ret = new java.util.ArrayList<>();
        if (this.singleAABBRepresentation != null) {
            ret.add(this.singleAABBRepresentation);
        } else {
            final double[] coordsX = this.rootCoordinatesX;
            final double[] coordsY = this.rootCoordinatesY;
            final double[] coordsZ = this.rootCoordinatesZ;

            final double offX = this.offsetX;
            final double offY = this.offsetY;
            final double offZ = this.offsetZ;

            this.shape.forAllBoxes((final int minX, final int minY, final int minZ,
                                    final int maxX, final int maxY, final int maxZ) -> {
                ret.add(new AABB(
                    coordsX[minX] + offX,
                    coordsY[minY] + offY,
                    coordsZ[minZ] + offZ,

                    coordsX[maxX] + offX,
                    coordsY[maxY] + offY,
                    coordsZ[maxZ] + offZ
                ));
            }, true);
        }

        // cache result
        this.cachedToAABBs = new ca.spottedleaf.moonrise.patches.collisions.shape.CachedToAABBs(ret, false, 0.0, 0.0, 0.0);

        return ret;
    }

    private boolean computeFullBlock() {
        Boolean ret;
        if (this.isEmpty) {
            ret = Boolean.FALSE;
        } else if ((VoxelShape)(Object)this == Shapes.block()) {
            ret = Boolean.TRUE;
        } else {
            final AABB singleAABB = this.singleAABBRepresentation;
            if (singleAABB == null) {
                final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData shapeData = this.cachedShapeData;
                final int sMinX = shapeData.minFullX();
                final int sMinY = shapeData.minFullY();
                final int sMinZ = shapeData.minFullZ();

                final int sMaxX = shapeData.maxFullX();
                final int sMaxY = shapeData.maxFullY();
                final int sMaxZ = shapeData.maxFullZ();

                if (Math.abs(this.rootCoordinatesX[sMinX] + this.offsetX) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&
                    Math.abs(this.rootCoordinatesY[sMinY] + this.offsetY) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&
                    Math.abs(this.rootCoordinatesZ[sMinZ] + this.offsetZ) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&

                    Math.abs(1.0 - (this.rootCoordinatesX[sMaxX] + this.offsetX)) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&
                    Math.abs(1.0 - (this.rootCoordinatesY[sMaxY] + this.offsetY)) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&
                    Math.abs(1.0 - (this.rootCoordinatesZ[sMaxZ] + this.offsetZ)) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON) {

                    // index = z + y*sizeZ + x*(sizeZ*sizeY)

                    final int sizeY = shapeData.sizeY();
                    final int sizeZ = shapeData.sizeZ();

                    final long[] bitset = shapeData.voxelSet();

                    ret = Boolean.TRUE;

                    check_full:
                    for (int x = sMinX; x < sMaxX; ++x) {
                        for (int y = sMinY; y < sMaxY; ++y) {
                            final int baseIndex = y*sizeZ + x*(sizeZ*sizeY);
                            if (!ca.spottedleaf.moonrise.common.util.FlatBitsetUtil.isRangeSet(bitset, baseIndex + sMinZ, baseIndex + sMaxZ)) {
                                ret = Boolean.FALSE;
                                break check_full;
                            }
                        }
                    }
                } else {
                    ret = Boolean.FALSE;
                }
            } else {
                ret = Boolean.valueOf(
                    Math.abs(singleAABB.minX) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&
                        Math.abs(singleAABB.minY) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&
                        Math.abs(singleAABB.minZ) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&

                        Math.abs(1.0 - singleAABB.maxX) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&
                        Math.abs(1.0 - singleAABB.maxY) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON &&
                        Math.abs(1.0 - singleAABB.maxZ) <= ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON
                );
            }
        }

        this.isFullBlock = ret;

        return ret.booleanValue();
    }

    @Override
    public final boolean moonrise$isFullBlock() {
        final Boolean ret = this.isFullBlock;

        if (ret != null) {
            return ret.booleanValue();
        }

        return this.computeFullBlock();
    }

    private static BlockHitResult clip(final AABB aabb, final Vec3 from, final Vec3 to, final BlockPos offset) {
        final double[] minDistanceArr = new double[] { 1.0 };
        final double diffX = to.x - from.x;
        final double diffY = to.y - from.y;
        final double diffZ = to.z - from.z;

        final Direction direction = AABB.getDirection(aabb.move(offset), from, minDistanceArr, null, diffX, diffY, diffZ);

        if (direction == null) {
            return null;
        }

        final double minDistance = minDistanceArr[0];
        return new BlockHitResult(from.add(minDistance * diffX, minDistance * diffY, minDistance * diffZ), direction, offset, false);
    }
    // Paper end - optimise collisions

    protected VoxelShape(DiscreteVoxelShape p_83214_) {
        this.shape = p_83214_;
        // FoliaYouer: initCache() must be called by subclasses AFTER their fields are initialized,
        // because initCache() calls getCoords() which relies on subclass fields (xs/ys/zs, delegate, etc.)
    }

    public double min(Direction.Axis p_83289_) {
        // FoliaYouer: lazy init cache for mod compatibility
        if (this.cachedShapeData == null) { ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)this).moonrise$initCache(); }
        // Paper start - optimise collisions // Folia - moonrise collisions
        final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData shapeData = this.cachedShapeData;
        switch (p_83289_) {
            case X: {
                final int idx = shapeData.minFullX();
                return idx >= shapeData.sizeX() ? Double.POSITIVE_INFINITY : (this.rootCoordinatesX[idx] + this.offsetX);
            }
            case Y: {
                final int idx = shapeData.minFullY();
                return idx >= shapeData.sizeY() ? Double.POSITIVE_INFINITY : (this.rootCoordinatesY[idx] + this.offsetY);
            }
            case Z: {
                final int idx = shapeData.minFullZ();
                return idx >= shapeData.sizeZ() ? Double.POSITIVE_INFINITY : (this.rootCoordinatesZ[idx] + this.offsetZ);
            }
            default: {
                // should never get here
                return Double.POSITIVE_INFINITY;
            }
        }
        // Paper end - optimise collisions
    }

    public double max(Direction.Axis p_83298_) {
        // FoliaYouer: lazy init cache for mod compatibility
        if (this.cachedShapeData == null) { ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)this).moonrise$initCache(); }
        // Paper start - optimise collisions // Folia - moonrise collisions
        final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData shapeData = this.cachedShapeData;
        switch (p_83298_) {
            case X: {
                final int idx = shapeData.maxFullX();
                return idx <= 0 ? Double.NEGATIVE_INFINITY : (this.rootCoordinatesX[idx] + this.offsetX);
            }
            case Y: {
                final int idx = shapeData.maxFullY();
                return idx <= 0 ? Double.NEGATIVE_INFINITY : (this.rootCoordinatesY[idx] + this.offsetY);
            }
            case Z: {
                final int idx = shapeData.maxFullZ();
                return idx <= 0 ? Double.NEGATIVE_INFINITY : (this.rootCoordinatesZ[idx] + this.offsetZ);
            }
            default: {
                // should never get here
                return Double.NEGATIVE_INFINITY;
            }
        }
        // Paper end - optimise collisions
    }

    public AABB bounds() {
        // FoliaYouer: lazy init cache for mod compatibility
        if (this.cachedShapeData == null) { ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)this).moonrise$initCache(); }
        // Paper start - optimise collisions // Folia - moonrise collisions
        if (this.isEmpty) {
            throw Util.pauseInIde(new UnsupportedOperationException("No bounds for empty shape."));
        }
        AABB cached = this.cachedBounds;
        if (cached != null) {
            return cached;
        }

        final ca.spottedleaf.moonrise.patches.collisions.shape.CachedShapeData shapeData = this.cachedShapeData;

        final double[] coordsX = this.rootCoordinatesX;
        final double[] coordsY = this.rootCoordinatesY;
        final double[] coordsZ = this.rootCoordinatesZ;

        final double offX = this.offsetX;
        final double offY = this.offsetY;
        final double offZ = this.offsetZ;

        // note: if not empty, then there is one full AABB so no bounds checks are needed on the minFull/maxFull indices
        cached = new AABB(
            coordsX[shapeData.minFullX()] + offX,
            coordsY[shapeData.minFullY()] + offY,
            coordsZ[shapeData.minFullZ()] + offZ,

            coordsX[shapeData.maxFullX()] + offX,
            coordsY[shapeData.maxFullY()] + offY,
            coordsZ[shapeData.maxFullZ()] + offZ
        );

        this.cachedBounds = cached;
        return cached;
        // Paper end - optimise collisions
    }

    public VoxelShape singleEncompassing() {
        return this.isEmpty()
            ? Shapes.empty()
            : Shapes.box(
                this.min(Direction.Axis.X),
                this.min(Direction.Axis.Y),
                this.min(Direction.Axis.Z),
                this.max(Direction.Axis.X),
                this.max(Direction.Axis.Y),
                this.max(Direction.Axis.Z)
            );
    }

    protected double get(Direction.Axis p_83257_, int p_83258_) {
        return this.getCoords(p_83257_).getDouble(p_83258_);
    }

    public abstract DoubleList getCoords(Direction.Axis p_83249_);

    public boolean isEmpty() {
        // FoliaYouer: lazy init cache for mod compatibility
        if (this.cachedShapeData == null) { ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)this).moonrise$initCache(); }
        return this.isEmpty; // Paper - optimise collisions
    }

    public VoxelShape move(double p_83217_, double p_83218_, double p_83219_) {
        // FoliaYouer: lazy init cache for mod compatibility
        if (this.cachedShapeData == null) { ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)this).moonrise$initCache(); }
        // Paper start - optimise collisions // Folia - moonrise collisions
        if (this.isEmpty) {
            return Shapes.empty();
        }

        final ArrayVoxelShape ret = new ArrayVoxelShape(
            this.shape,
            offsetList(this.getCoords(Direction.Axis.X), p_83217_),
            offsetList(this.getCoords(Direction.Axis.Y), p_83218_),
            offsetList(this.getCoords(Direction.Axis.Z), p_83219_)
        );

        final ca.spottedleaf.moonrise.patches.collisions.shape.CachedToAABBs cachedToAABBs = this.cachedToAABBs;
        if (cachedToAABBs != null) {
            ((VoxelShape)(Object)ret).cachedToAABBs = ca.spottedleaf.moonrise.patches.collisions.shape.CachedToAABBs.offset(cachedToAABBs, p_83217_, p_83218_, p_83219_);
        }

        return ret;
        // Paper end - optimise collisions
    }

    public VoxelShape optimize() {
        // FoliaYouer: lazy init cache for mod compatibility
        if (this.cachedShapeData == null) { ((ca.spottedleaf.moonrise.patches.collisions.shape.CollisionVoxelShape)this).moonrise$initCache(); }
        // Paper start - optimise collisions // Folia - moonrise collisions
        if (this.isEmpty) {
            return Shapes.empty();
        }

        if (this.singleAABBRepresentation != null) {
            // note: the isFullBlock() is fuzzy, and Shapes.create() is also fuzzy which would return block()
            return this.moonrise$isFullBlock() ? Shapes.block() : (VoxelShape)(Object)this;
        }

        final List<AABB> aabbs = this.toAabbs();

        if (aabbs.size() == 1) {
            final AABB singleAABB = aabbs.get(0);
            final VoxelShape ret = Shapes.create(singleAABB);

            // forward AABB cache
            if (((VoxelShape)(Object)ret).cachedToAABBs == null) {
                ((VoxelShape)(Object)ret).cachedToAABBs = this.cachedToAABBs;
            }

            return ret;
        } else {
            // reduce complexity of joins by splitting the merges (old complexity: n^2, new: nlogn)

            // set up flat array so that this merge is done in-place
            final VoxelShape[] tmp = new VoxelShape[aabbs.size()];

            // initialise as unmerged
            for (int i = 0, len = aabbs.size(); i < len; ++i) {
                tmp[i] = Shapes.create(aabbs.get(i));
            }

            int size = aabbs.size();
            while (size > 1) {
                int newSize = 0;
                for (int i = 0; i < size; i += 2) {
                    final int next = i + 1;
                    if (next >= size) {
                        // nothing to merge with, so leave it for next iteration
                        tmp[newSize++] = tmp[i];
                        break;
                    } else {
                        // merge with adjacent
                        final VoxelShape first = tmp[i];
                        final VoxelShape second = tmp[next];

                        tmp[newSize++] = Shapes.joinUnoptimized(first, second, BooleanOp.OR);
                    }
                }
                size = newSize;
            }

            final VoxelShape ret = tmp[0];

            // forward AABB cache
            if (((VoxelShape)(Object)ret).cachedToAABBs == null) {
                ((VoxelShape)(Object)ret).cachedToAABBs = this.cachedToAABBs;
            }

            return ret;
        }
        // Paper end - optimise collisions
    }

    public void forAllEdges(Shapes.DoubleLineConsumer p_83225_) {
        this.shape
            .forAllEdges(
                (p_83228_, p_83229_, p_83230_, p_83231_, p_83232_, p_83233_) -> p_83225_.consume(
                        this.get(Direction.Axis.X, p_83228_),
                        this.get(Direction.Axis.Y, p_83229_),
                        this.get(Direction.Axis.Z, p_83230_),
                        this.get(Direction.Axis.X, p_83231_),
                        this.get(Direction.Axis.Y, p_83232_),
                        this.get(Direction.Axis.Z, p_83233_)
                    ),
                true
            );
    }

    public void forAllBoxes(Shapes.DoubleLineConsumer p_83287_) {
        DoubleList doublelist = this.getCoords(Direction.Axis.X);
        DoubleList doublelist1 = this.getCoords(Direction.Axis.Y);
        DoubleList doublelist2 = this.getCoords(Direction.Axis.Z);
        this.shape
            .forAllBoxes(
                (p_83239_, p_83240_, p_83241_, p_83242_, p_83243_, p_83244_) -> p_83287_.consume(
                        doublelist.getDouble(p_83239_),
                        doublelist1.getDouble(p_83240_),
                        doublelist2.getDouble(p_83241_),
                        doublelist.getDouble(p_83242_),
                        doublelist1.getDouble(p_83243_),
                        doublelist2.getDouble(p_83244_)
                    ),
                true
            );
    }

    public List<AABB> toAabbs() {
        // Paper start - optimise collisions // Folia - moonrise collisions
        ca.spottedleaf.moonrise.patches.collisions.shape.CachedToAABBs cachedToAABBs = this.cachedToAABBs;
        if (cachedToAABBs != null) {
            if (!cachedToAABBs.isOffset()) {
                return cachedToAABBs.aabbs();
            }

            // all we need to do is offset the cache
            cachedToAABBs = cachedToAABBs.removeOffset();
            // update cache
            this.cachedToAABBs = cachedToAABBs;

            return cachedToAABBs.aabbs();
        }

        // make new cache
        return this.toAabbsUncached();
        // Paper end - optimise collisions
    }

    public double min(Direction.Axis p_166079_, double p_166080_, double p_166081_) {
        Direction.Axis direction$axis = AxisCycle.FORWARD.cycle(p_166079_);
        Direction.Axis direction$axis1 = AxisCycle.BACKWARD.cycle(p_166079_);
        int i = this.findIndex(direction$axis, p_166080_);
        int j = this.findIndex(direction$axis1, p_166081_);
        int k = this.shape.firstFull(p_166079_, i, j);
        return k >= this.shape.getSize(p_166079_) ? Double.POSITIVE_INFINITY : this.get(p_166079_, k);
    }

    public double max(Direction.Axis p_83291_, double p_83292_, double p_83293_) {
        Direction.Axis direction$axis = AxisCycle.FORWARD.cycle(p_83291_);
        Direction.Axis direction$axis1 = AxisCycle.BACKWARD.cycle(p_83291_);
        int i = this.findIndex(direction$axis, p_83292_);
        int j = this.findIndex(direction$axis1, p_83293_);
        int k = this.shape.lastFull(p_83291_, i, j);
        return k <= 0 ? Double.NEGATIVE_INFINITY : this.get(p_83291_, k);
    }

    protected int findIndex(Direction.Axis p_83250_, double p_83251_) {
        return Mth.binarySearch(0, this.shape.getSize(p_83250_) + 1, p_166066_ -> p_83251_ < this.get(p_83250_, p_166066_)) - 1;
    }

    @Nullable
    public BlockHitResult clip(Vec3 p_83221_, Vec3 p_83222_, BlockPos p_83223_) {
        // Paper start - optimise collisions // Folia - moonrise collisions
        if (this.isEmpty) {
            return null;
        }

        final Vec3 directionOpposite = p_83222_.subtract(p_83221_);
        if (directionOpposite.lengthSqr() < ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON) {
            return null;
        }

        final Vec3 fromBehind = p_83221_.add(directionOpposite.scale(0.001));
        final double fromBehindOffsetX = fromBehind.x - (double)p_83223_.getX();
        final double fromBehindOffsetY = fromBehind.y - (double)p_83223_.getY();
        final double fromBehindOffsetZ = fromBehind.z - (double)p_83223_.getZ();

        final AABB singleAABB = this.singleAABBRepresentation;
        if (singleAABB != null) {
            if (singleAABB.contains(fromBehindOffsetX, fromBehindOffsetY, fromBehindOffsetZ)) {
                return new BlockHitResult(fromBehind, Direction.getNearest(directionOpposite.x, directionOpposite.y, directionOpposite.z).getOpposite(), p_83223_, true);
            }
            return clip(singleAABB, p_83221_, p_83222_, p_83223_);
        }

        if (ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.strictlyContains((VoxelShape)(Object)this, fromBehindOffsetX, fromBehindOffsetY, fromBehindOffsetZ)) {
            return new BlockHitResult(fromBehind, Direction.getNearest(directionOpposite.x, directionOpposite.y, directionOpposite.z).getOpposite(), p_83223_, true);
        }

        return AABB.clip(((VoxelShape)(Object)this).toAabbs(), p_83221_, p_83222_, p_83223_);
        // Paper end - optimise collisions
    }

    // Paper start - optimise collisions // Folia - moonrise collisions
    public Optional<Vec3> closestPointTo(Vec3 p_166068_) {
        if (this.isEmpty) {
            return Optional.empty();
        }

        Vec3 ret = null;
        double retDistance = Double.MAX_VALUE;

        final List<AABB> aabbs = this.toAabbs();
        for (int i = 0, len = aabbs.size(); i < len; ++i) {
            final AABB aabb = aabbs.get(i);
            final double x = Mth.clamp(p_166068_.x, aabb.minX, aabb.maxX);
            final double y = Mth.clamp(p_166068_.y, aabb.minY, aabb.maxY);
            final double z = Mth.clamp(p_166068_.z, aabb.minZ, aabb.maxZ);

            double dist = p_166068_.distanceToSqr(x, y, z);
            if (dist < retDistance) {
                ret = new Vec3(x, y, z);
                retDistance = dist;
            }
        }

        return Optional.ofNullable(ret);
        // Paper end - optimise collisions
    }

    public VoxelShape getFaceShape(Direction p_83264_) {
        if (!this.isEmpty() && this != Shapes.block()) {
            if (this.faces != null) {
                VoxelShape voxelshape = this.faces[p_83264_.ordinal()];
                if (voxelshape != null) {
                    return voxelshape;
                }
            } else {
                this.faces = new VoxelShape[6];
            }

            VoxelShape voxelshape1 = this.calculateFace(p_83264_);
            this.faces[p_83264_.ordinal()] = voxelshape1;
            return voxelshape1;
        } else {
            return this;
        }
    }

    private VoxelShape calculateFace(Direction p_83295_) {
        Direction.Axis direction$axis = p_83295_.getAxis();
        DoubleList doublelist = this.getCoords(direction$axis);
        if (doublelist.size() == 2
            && DoubleMath.fuzzyEquals(doublelist.getDouble(0), 0.0, 1.0E-7)
            && DoubleMath.fuzzyEquals(doublelist.getDouble(1), 1.0, 1.0E-7)) {
            return this;
        } else {
            Direction.AxisDirection direction$axisdirection = p_83295_.getAxisDirection();
            int i = this.findIndex(direction$axis, direction$axisdirection == Direction.AxisDirection.POSITIVE ? 0.9999999 : 1.0E-7);
            return new SliceShape(this, direction$axis, i);
        }
    }

    // Paper start - optimise collisions // Folia - moonrise collisions
    public double collide(final Direction.Axis p_83260_, final AABB p_83261_, final double p_83262_) {
        if (this.isEmpty) {
            return p_83262_;
        }
        if (Math.abs(p_83262_) < ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.COLLISION_EPSILON) {
            return 0.0;
        }
        switch (p_83260_) {
            case X: {
                return ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.collideX((VoxelShape)(Object)this, p_83261_, p_83262_);
            }
            case Y: {
                return ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.collideY((VoxelShape)(Object)this, p_83261_, p_83262_);
            }
            case Z: {
                return ca.spottedleaf.moonrise.patches.collisions.CollisionUtil.collideZ((VoxelShape)(Object)this, p_83261_, p_83262_);
            }
            default: {
                throw new RuntimeException("Unknown axis: " + p_83260_);
            }
        }
        // Paper end - optimise collisions
    }

    protected double collideX(AxisCycle p_83246_, AABB p_83247_, double p_83248_) {
        if (this.isEmpty()) {
            return p_83248_;
        } else if (Math.abs(p_83248_) < 1.0E-7) {
            return 0.0;
        } else {
            AxisCycle axiscycle = p_83246_.inverse();
            Direction.Axis direction$axis = axiscycle.cycle(Direction.Axis.X);
            Direction.Axis direction$axis1 = axiscycle.cycle(Direction.Axis.Y);
            Direction.Axis direction$axis2 = axiscycle.cycle(Direction.Axis.Z);
            double d0 = p_83247_.max(direction$axis);
            double d1 = p_83247_.min(direction$axis);
            int i = this.findIndex(direction$axis, d1 + 1.0E-7);
            int j = this.findIndex(direction$axis, d0 - 1.0E-7);
            int k = Math.max(0, this.findIndex(direction$axis1, p_83247_.min(direction$axis1) + 1.0E-7));
            int l = Math.min(this.shape.getSize(direction$axis1), this.findIndex(direction$axis1, p_83247_.max(direction$axis1) - 1.0E-7) + 1);
            int i1 = Math.max(0, this.findIndex(direction$axis2, p_83247_.min(direction$axis2) + 1.0E-7));
            int j1 = Math.min(this.shape.getSize(direction$axis2), this.findIndex(direction$axis2, p_83247_.max(direction$axis2) - 1.0E-7) + 1);
            int k1 = this.shape.getSize(direction$axis);
            if (p_83248_ > 0.0) {
                for (int l1 = j + 1; l1 < k1; l1++) {
                    for (int i2 = k; i2 < l; i2++) {
                        for (int j2 = i1; j2 < j1; j2++) {
                            if (this.shape.isFullWide(axiscycle, l1, i2, j2)) {
                                double d2 = this.get(direction$axis, l1) - d0;
                                if (d2 >= -1.0E-7) {
                                    p_83248_ = Math.min(p_83248_, d2);
                                }

                                return p_83248_;
                            }
                        }
                    }
                }
            } else if (p_83248_ < 0.0) {
                for (int k2 = i - 1; k2 >= 0; k2--) {
                    for (int l2 = k; l2 < l; l2++) {
                        for (int i3 = i1; i3 < j1; i3++) {
                            if (this.shape.isFullWide(axiscycle, k2, l2, i3)) {
                                double d3 = this.get(direction$axis, k2 + 1) - d1;
                                if (d3 <= 1.0E-7) {
                                    p_83248_ = Math.max(p_83248_, d3);
                                }

                                return p_83248_;
                            }
                        }
                    }
                }
            }

            return p_83248_;
        }
    }

    @Override
    public String toString() {
        return this.isEmpty() ? "EMPTY" : "VoxelShape[" + this.bounds() + "]";
    }
}
