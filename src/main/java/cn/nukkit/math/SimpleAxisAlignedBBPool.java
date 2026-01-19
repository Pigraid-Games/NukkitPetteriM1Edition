package cn.nukkit.math;

/**
 * Object pool for temporary AABB calculations.
 * NOT thread-safe - designed for single-threaded main tick loop.
 */
public final class SimpleAxisAlignedBBPool {

    private static final int POOL_SIZE = 128;
    private static final SimpleAxisAlignedBB[] pool = new SimpleAxisAlignedBB[POOL_SIZE];
    private static int poolIndex = 0;

    static {
        for (int i = 0; i < POOL_SIZE; i++) {
            pool[i] = new SimpleAxisAlignedBB(0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * Get a pooled AABB instance. The returned instance is valid until
     * the next call to get() that cycles back to the same slot.
     *
     * WARNING: DO NOT store references to pooled AABBs - they will be reused!
     */
    public static SimpleAxisAlignedBB get(double minX, double minY, double minZ,
                                          double maxX, double maxY, double maxZ) {
        SimpleAxisAlignedBB bb = pool[poolIndex];
        bb.setMinX(minX);
        bb.setMinY(minY);
        bb.setMinZ(minZ);
        bb.setMaxX(maxX);
        bb.setMaxY(maxY);
        bb.setMaxZ(maxZ);
        poolIndex = (poolIndex + 1) % POOL_SIZE;
        return bb;
    }

    /**
     * Copy values from source to a pooled instance.
     * WARNING: DO NOT store the result - it will be reused!
     */
    public static SimpleAxisAlignedBB copyOf(AxisAlignedBB source) {
        return get(source.getMinX(), source.getMinY(), source.getMinZ(),
                   source.getMaxX(), source.getMaxY(), source.getMaxZ());
    }
}
