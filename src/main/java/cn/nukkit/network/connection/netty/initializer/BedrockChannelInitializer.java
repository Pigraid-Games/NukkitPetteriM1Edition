package cn.nukkit.network.connection.netty.initializer;

import cn.nukkit.network.connection.BedrockPeer;
import cn.nukkit.network.connection.BedrockSession;
import cn.nukkit.network.connection.BedrockSessionFactory;
import cn.nukkit.network.connection.netty.codec.FrameIdCodec;
import cn.nukkit.network.connection.netty.codec.batch.BedrockBatchDecoder;
import cn.nukkit.network.connection.netty.codec.batch.BedrockBatchEncoder;
import cn.nukkit.network.connection.netty.codec.compression.CompressionCodec;
import cn.nukkit.network.connection.netty.codec.compression.CompressionStrategy;
import cn.nukkit.network.connection.netty.codec.compression.NoopCompression;
import cn.nukkit.network.connection.netty.codec.compression.SimpleCompressionStrategy;
import cn.nukkit.network.connection.netty.codec.compression.SnappyCompression;
import cn.nukkit.network.connection.netty.codec.compression.ZlibCompression;
import cn.nukkit.network.connection.netty.codec.packet.BedrockPacketCodec;
import cn.nukkit.network.connection.netty.codec.packet.BedrockPacketCodec_v3;
import cn.nukkit.network.protocol.types.PacketCompressionAlgorithm;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import lombok.extern.log4j.Log4j2;

/**
 * Sets up the Bedrock Netty pipeline:
 *
 * Inbound:  RakNet -> FrameIdCodec -> CompressionCodec -> BedrockBatchDecoder -> BedrockPacketCodec -> BedrockPeer
 * Outbound: BedrockPeer -> BedrockPacketCodec -> BedrockBatchEncoder -> CompressionCodec -> FrameIdCodec -> RakNet
 */
@Log4j2
public abstract class BedrockChannelInitializer extends ChannelInitializer<Channel> {
    public static final int RAKNET_MINECRAFT_ID = 0xFE;
    private static final FrameIdCodec RAKNET_FRAME_CODEC = new FrameIdCodec(RAKNET_MINECRAFT_ID);
    private static final BedrockBatchDecoder BATCH_DECODER = new BedrockBatchDecoder();

    @Override
    protected final void initChannel(Channel channel) throws Exception {
        this.preInitChannel(channel);

        channel.pipeline()
                .addLast(BedrockBatchDecoder.NAME, BATCH_DECODER)
                .addLast(BedrockBatchEncoder.NAME, new BedrockBatchEncoder());

        this.initPacketCodec(channel);

        channel.pipeline().addLast(BedrockPeer.NAME, this.createPeer(channel));

        this.postInitChannel(channel);
    }

    protected void preInitChannel(Channel channel) {
        channel.pipeline().addLast(FrameIdCodec.NAME, RAKNET_FRAME_CODEC);
        // Initial compression is NOOP until the client negotiates a compression algorithm
        CompressionStrategy compression = new SimpleCompressionStrategy(new NoopCompression());
        channel.pipeline().addLast(CompressionCodec.NAME, new CompressionCodec(compression, false));
    }

    protected void postInitChannel(Channel channel) {
    }

    protected void initPacketCodec(Channel channel) {
        channel.pipeline().addLast(BedrockPacketCodec.NAME, new BedrockPacketCodec_v3());
    }

    protected BedrockPeer createPeer(Channel channel) {
        return new BedrockPeer(channel, this::createSession);
    }

    protected abstract BedrockSession createSession(BedrockPeer peer, int subClientId);

    public static CompressionStrategy getCompression(PacketCompressionAlgorithm algorithm, boolean initial) {
        if (initial) {
            return new SimpleCompressionStrategy(new NoopCompression());
        }
        return getCompression(algorithm);
    }

    public static CompressionStrategy getCompression(PacketCompressionAlgorithm algorithm) {
        switch (algorithm) {
            case ZLIB:
                return new SimpleCompressionStrategy(new ZlibCompression(true));
            case SNAPPY:
                return new SimpleCompressionStrategy(new SnappyCompression());
            case NONE:
                return new SimpleCompressionStrategy(new NoopCompression());
            default:
                throw new UnsupportedOperationException("Unsupported compression: " + algorithm);
        }
    }
}
