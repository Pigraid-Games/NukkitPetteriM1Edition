package cn.nukkit.network.connection.netty.codec.compression;

import cn.nukkit.network.protocol.types.PacketCompressionAlgorithm;
import cn.nukkit.utils.Zlib;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Zlib (deflate) compression implementation adapted for Petter's Zlib utilities.
 * Uses raw deflate when useRaw=true (protocol >= 1.16), or standard zlib wrapping otherwise.
 */
@ToString
public class ZlibCompression implements BatchCompression {

    private final boolean useRaw;

    @Getter @Setter
    private int level = 7;

    /**
     * @param useRaw true for raw deflate (ZLIB_RAW, protocol >= 1.16), false for standard zlib wrapping
     */
    public ZlibCompression(boolean useRaw) {
        this.useRaw = useRaw;
    }

    @Override
    public ByteBuf encode(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        int readableBytes = msg.readableBytes();
        byte[] bytes = new byte[readableBytes];
        msg.readBytes(bytes);
        byte[] compressed = useRaw ? Zlib.deflateRaw(bytes, level) : Zlib.deflate(bytes, level);
        ByteBuf outBuf = ctx.alloc().ioBuffer(compressed.length);
        outBuf.writeBytes(compressed);
        return outBuf;
    }

    @Override
    public ByteBuf decode(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        int readableBytes = msg.readableBytes();
        byte[] bytes = new byte[readableBytes];
        msg.readBytes(bytes);
        byte[] decompressed = useRaw ? Zlib.inflateRaw(bytes, 6291456) : Zlib.inflate(bytes, 6291456);
        ByteBuf outBuf = ctx.alloc().ioBuffer(decompressed.length);
        outBuf.writeBytes(decompressed);
        return outBuf;
    }

    @Override
    public PacketCompressionAlgorithm getAlgorithm() {
        return PacketCompressionAlgorithm.ZLIB;
    }
}
