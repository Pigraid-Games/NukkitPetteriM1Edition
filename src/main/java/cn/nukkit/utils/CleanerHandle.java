package cn.nukkit.utils;

/**
 * Utility class for managing resource lifecycle.
 * Wraps an AutoCloseable resource for use in ThreadLocal contexts.
 * On Java 8, native resource cleanup relies on thread termination and GC finalization,
 * consistent with how ZlibThreadLocal handles Deflater/Inflater instances.
 *
 * @param <RESOURCE> The type of resource to manage (must be AutoCloseable)
 */
public final class CleanerHandle<RESOURCE extends AutoCloseable> {
    private final RESOURCE resource;

    public CleanerHandle(RESOURCE resource) {
        this.resource = resource;
    }

    public RESOURCE getResource() {
        return resource;
    }
}
