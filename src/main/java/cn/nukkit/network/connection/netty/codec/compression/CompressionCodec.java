package cn.nukkit.network.connection.netty.codec.compression;

import cn.nukkit.network.connection.netty.BedrockBatchWrapper;
import cn.nukkit.network.protocol.types.PacketCompressionAlgorithm;
import com.google.common.base.Preconditions;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;

import java.util.List;

public class CompressionCodec extends MessageToMessageCodec<BedrockBatchWrapper, BedrockBatchWrapper> {
    public static final String NAME = "compression-codec";

    /** Maximum allowed decompressed batch size (4 MiB) to prevent memory exhaustion. */
    private static final int MAX_DECOMPRESSED_SIZE = 4 * 1024 * 1024;

    private final CompressionStrategy strategy;
    private final boolean prefixed;

    public CompressionCodec(CompressionStrategy strategy, boolean prefixed) {
        this.strategy = strategy;
        this.prefixed = prefixed;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, BedrockBatchWrapper msg, List<Object> out) throws Exception {
        if (msg.getCompressed() == null && msg.getUncompressed() == null) {
            throw new IllegalStateException("Batch was not encoded before");
        }

        if (msg.getCompressed() != null && !msg.isModified()) {
            out.add(msg.retain());
            return;
        }

        BatchCompression compression = this.strategy.getCompression(msg);
        if (!this.prefixed && this.strategy.getDefaultCompression().getAlgorithm() != compression.getAlgorithm()) {
            throw new IllegalStateException("Non-default compression algorithm used without prefixing");
        }

        ByteBuf compressed = compression.encode(ctx, msg.getUncompressed());
        try {
            ByteBuf outBuf;
            if (this.prefixed) {
                // Do not use a composite buffer as encryption does not like it
                outBuf = ctx.alloc().ioBuffer(1 + compressed.readableBytes());
                outBuf.writeByte(getCompressionHeader(compression.getAlgorithm()));
                outBuf.writeBytes(compressed);
            } else {
                outBuf = compressed.retain();
            }

            msg.setCompressed(outBuf, compression.getAlgorithm());
        } finally {
            compressed.release();
        }

        out.add(msg.retain());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, BedrockBatchWrapper msg, List<Object> out) throws Exception {
        ByteBuf compressed = msg.getCompressed().slice();
        Preconditions.checkArgument(compressed.capacity() <= MAX_DECOMPRESSED_SIZE,
                "Compressed data size is too big: %s", compressed.capacity());

        BatchCompression compression;
        if (this.prefixed) {
            PacketCompressionAlgorithm algorithm = getCompressionAlgorithm(compressed.readByte());
            compression = this.strategy.getCompression(algorithm);
        } else {
            compression = this.strategy.getDefaultCompression();
        }

        msg.setAlgorithm(compression.getAlgorithm());

        msg.setUncompressed(compression.decode(ctx, compressed.slice()));
        out.add(msg.retain());
    }

    protected final byte getCompressionHeader(PacketCompressionAlgorithm algorithm) {
        if (algorithm == PacketCompressionAlgorithm.NONE) {
            return (byte) 0xff;
        } else if (algorithm == PacketCompressionAlgorithm.ZLIB) {
            return 0x00;
        } else if (algorithm == PacketCompressionAlgorithm.SNAPPY) {
            return 0x01;
        }
        throw new IllegalArgumentException("Unknown compression algorithm " + algorithm);
    }

    protected final PacketCompressionAlgorithm getCompressionAlgorithm(byte header) {
        switch (header) {
            case 0x00:
                return PacketCompressionAlgorithm.ZLIB;
            case 0x01:
                return PacketCompressionAlgorithm.SNAPPY;
            case (byte) 0xff:
                return PacketCompressionAlgorithm.NONE;
        }
        throw new IllegalArgumentException("Unknown compression algorithm header: " + header);
    }

    public CompressionStrategy getStrategy() {
        return this.strategy;
    }

    public boolean isPrefixed() {
        return this.prefixed;
    }

    /** Returns the 1-byte algorithm header that this codec writes before compressed data (prefixed mode). */
    public byte getDefaultCompressionHeader() {
        return getCompressionHeader(this.strategy.getDefaultCompression().getAlgorithm());
    }

    @Override
    public String toString() {
        return strategy.toString();
    }
}
