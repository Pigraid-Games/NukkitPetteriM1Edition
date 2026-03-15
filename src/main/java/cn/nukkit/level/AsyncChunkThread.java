package cn.nukkit.level;

import cn.nukkit.Server;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.serializer.NetworkChunkData;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.bugreport.ExceptionHandler;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

class AsyncChunkThread {

    /** Maximum pending serialization tasks before CallerRunsPolicy kicks in. */
    private static final int MAX_PENDING = 256;

    private final ExecutorService threadedExecutor;
    final Queue<AsyncChunkData> out = new ConcurrentLinkedQueue<>();

    AsyncChunkThread(String levelName, int threadCount) {
        ThreadFactoryBuilder builder = new ThreadFactoryBuilder();
        builder.setNameFormat("AsyncChunkThread-%d for " + levelName);
        builder.setUncaughtExceptionHandler((thread, ex) -> {
            Server.getInstance().getLogger().error("Exception in " + thread.getName(), ex);
            ExceptionHandler.handleSilently(ex);
        });
        this.threadedExecutor = new ThreadPoolExecutor(
                threadCount, threadCount,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING),
                builder.build(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    void queue(IntSet protocols, BaseChunk chunk, long timestamp, int x, int z, boolean antiXray, DimensionData dimensionData) {
        this.threadedExecutor.execute(() -> this.run(protocols, chunk, timestamp, x, z, antiXray, dimensionData));
    }

    private void run(IntSet protocols, BaseChunk chunk, long timestamp, int chunkX, int chunkZ, boolean antiXray, DimensionData dimensionData) {
        BiConsumer<BinaryStream, NetworkChunkData> callback = (stream, data) ->
                this.out.add(new AsyncChunkData(data.getProtocol(), timestamp, chunkX, chunkZ, Level.chunkHash(chunkX, chunkZ), stream.getBuffer(), data.getChunkSections()));
        NetworkChunkSerializer.serialize(chunk, protocols, callback, antiXray, dimensionData);
    }

    void shutdown() {
        this.threadedExecutor.shutdownNow();
        try {
            this.threadedExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
