package cn.nukkit.network.connection.netty.codec.compression;

import cn.nukkit.network.protocol.types.PacketCompressionAlgorithm;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import lombok.ToString;

@ToString
public class SnappyCompression implements BatchCompression {

    @Override
    public ByteBuf encode(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        int readableBytes = msg.readableBytes();
        byte[] data = new byte[readableBytes];
        msg.readBytes(data);
        byte[] compressed = cn.nukkit.utils.SnappyCompression.compress(data);
        ByteBuf output = ctx.alloc().ioBuffer(compressed.length);
        output.writeBytes(compressed);
        return output;
    }

    @Override
    public ByteBuf decode(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        int readableBytes = msg.readableBytes();
        byte[] data = new byte[readableBytes];
        msg.readBytes(data);
        byte[] decompressed = cn.nukkit.utils.SnappyCompression.decompress(data, 6291456);
        ByteBuf output = ctx.alloc().ioBuffer(decompressed.length);
        output.writeBytes(decompressed);
        return output;
    }

    @Override
    public PacketCompressionAlgorithm getAlgorithm() {
        return PacketCompressionAlgorithm.SNAPPY;
    }

    @Override
    public void setLevel(int level) {
    }

    @Override
    public int getLevel() {
        return -1;
    }
}
