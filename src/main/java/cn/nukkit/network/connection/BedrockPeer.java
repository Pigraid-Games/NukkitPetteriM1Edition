package cn.nukkit.network.connection;

import cn.nukkit.network.connection.netty.BedrockPacketWrapper;
import cn.nukkit.network.connection.netty.codec.FrameIdCodec;
import cn.nukkit.network.connection.netty.codec.batch.BedrockBatchDecoder;
import cn.nukkit.network.connection.netty.codec.compression.CompressionCodec;
import cn.nukkit.network.connection.netty.codec.compression.CompressionStrategy;
import cn.nukkit.network.connection.netty.codec.compression.NoopCompression;
import cn.nukkit.network.connection.netty.codec.compression.SimpleCompressionStrategy;
import cn.nukkit.network.connection.netty.codec.compression.SnappyCompression;
import cn.nukkit.network.connection.netty.codec.compression.ZlibCompression;
import cn.nukkit.network.connection.netty.codec.encryption.BedrockEncryptionDecoder;
import cn.nukkit.network.connection.netty.codec.encryption.BedrockEncryptionEncoder;
import cn.nukkit.network.connection.util.EncryptionUtils;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.PacketCompressionAlgorithm;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.PlatformDependent;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.netty.channel.raknet.RakChildChannel;
import org.cloudburstmc.netty.channel.raknet.RakDisconnectReason;
import org.cloudburstmc.netty.channel.raknet.RakServerChannel;
import org.cloudburstmc.netty.handler.codec.raknet.common.RakSessionCodec;

import javax.crypto.SecretKey;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a single network connection to a remote Bedrock client.
 * Manages the Netty pipeline and holds one or more {@link BedrockSession}s.
 */
@Log4j2
public class BedrockPeer extends ChannelInboundHandlerAdapter {
    public static final String NAME = "bedrock-peer";

    protected final Int2ObjectMap<BedrockSession> sessions = new Int2ObjectOpenHashMap<>();
    protected final Queue<BedrockPacketWrapper> packetQueue = PlatformDependent.newMpscQueue();
    protected final Channel channel;
    protected final BedrockSessionFactory sessionFactory;
    protected ScheduledFuture<?> tickFuture;
    protected AtomicBoolean closed = new AtomicBoolean();

    public BedrockPeer(Channel channel, BedrockSessionFactory sessionFactory) {
        this.channel = channel;
        this.sessionFactory = sessionFactory;
    }

    protected void onBedrockPacket(BedrockPacketWrapper wrapper) {
        int targetId = wrapper.getTargetSubClientId();
        BedrockSession session = this.sessions.computeIfAbsent(targetId, this::onSessionCreated);
        session.onPacket(wrapper);
    }

    protected BedrockSession onSessionCreated(int sessionId) {
        return this.sessionFactory.createSession(this, sessionId);
    }

    protected void checkForClosed() {
        if (this.closed.get()) {
            throw new IllegalStateException("Peer has been closed");
        }
    }

    protected void removeSession(BedrockSession session) {
        this.sessions.remove(session.getSubClientId(), session);
    }

    protected void onTick() {
        if (this.closed.get()) {
            return;
        }
        this.flushSendQueue();
    }

    public void flushSendQueue() {
        if (!this.packetQueue.isEmpty()) {
            BedrockPacketWrapper packet;
            while ((packet = this.packetQueue.poll()) != null) {
                if (this.isConnected()) {
                    this.channel.write(packet);
                }
            }
            this.channel.flush();
        }
    }

    private void onRakNetDisconnect(ChannelHandlerContext ctx, RakDisconnectReason reason) {
        String disconnectReason = BedrockDisconnectReasons.getReason(reason);
        for (BedrockSession session : this.sessions.values()) {
            session.close(disconnectReason);
        }
    }

    private void free() {
        for (BedrockPacketWrapper wrapper : this.packetQueue) {
            ReferenceCountUtil.safeRelease(wrapper);
        }
    }

    /**
     * Queue a packet to be sent asynchronously on the next flush cycle.
     */
    public void sendPacket(int senderClientId, int targetClientId, DataPacket packet) {
        this.packetQueue.add(new BedrockPacketWrapper(0, senderClientId, targetClientId, packet, null));
    }

    /**
     * Send packet synchronously (blocks until the write is complete).
     */
    public void sendPacketSync(int senderClientId, int targetClientId, DataPacket packet) {
        this.channel.writeAndFlush(new BedrockPacketWrapper(0, senderClientId, targetClientId, packet, null)).syncUninterruptibly();
    }

    /**
     * Write and flush a packet immediately (non-blocking write+flush).
     */
    public void sendPacketImmediately(int senderClientId, int targetClientId, DataPacket packet) {
        this.channel.writeAndFlush(new BedrockPacketWrapper(0, senderClientId, targetClientId, packet, null));
    }

    public void sendRawPacket(BedrockPacketWrapper packet) {
        this.channel.writeAndFlush(packet);
    }

    public void flush() {
        this.channel.flush();
    }

    /**
     * Enable encryption for this peer. Must be called from the channel's event loop or
     * with appropriate thread safety.
     */
    public void enableEncryption(SecretKey secretKey) {
        Objects.requireNonNull(secretKey, "secretKey");
        if (!secretKey.getAlgorithm().equals("AES")) {
            throw new IllegalArgumentException("Invalid key algorithm");
        }
        if (this.channel.pipeline().get(BedrockEncryptionEncoder.class) != null ||
                this.channel.pipeline().get(BedrockEncryptionDecoder.class) != null) {
            throw new IllegalStateException("Encryption is already enabled");
        }

        // Protocol >= 428 uses CTR mode, older uses CFB8
        BedrockSession primary = this.sessions.get(0);
        int protocol = primary != null ? primary.getProtocol() : ProtocolInfo.CURRENT_PROTOCOL;
        boolean useCtr = protocol >= 428;

        this.channel.pipeline().addAfter(FrameIdCodec.NAME, BedrockEncryptionEncoder.NAME,
                new BedrockEncryptionEncoder(secretKey, EncryptionUtils.createCipher(useCtr, true, secretKey)));
        this.channel.pipeline().addAfter(FrameIdCodec.NAME, BedrockEncryptionDecoder.NAME,
                new BedrockEncryptionDecoder(secretKey, EncryptionUtils.createCipher(useCtr, false, secretKey)));

        log.debug("Encryption enabled for {}", getSocketAddress());
    }

    /**
     * Set compression algorithm for this peer.
     */
    public void setCompression(PacketCompressionAlgorithm algorithm) {
        Objects.requireNonNull(algorithm, "algorithm");
        this.setCompression(getCompressionStrategy(algorithm, false));
    }

    public void setCompression(CompressionStrategy strategy) {
        Objects.requireNonNull(strategy, "strategy");

        BedrockSession primary = this.sessions.get(0);
        int protocol = primary != null ? primary.getProtocol() : ProtocolInfo.CURRENT_PROTOCOL;
        boolean needsPrefix = protocol >= ProtocolInfo.v1_20_60;

        ChannelHandler handler = this.channel.pipeline().get(CompressionCodec.NAME);
        if (handler == null) {
            this.channel.pipeline().addBefore(BedrockBatchDecoder.NAME, CompressionCodec.NAME, new CompressionCodec(strategy, needsPrefix));
        } else {
            this.channel.pipeline().replace(CompressionCodec.NAME, CompressionCodec.NAME, new CompressionCodec(strategy, needsPrefix));
        }
    }

    public CompressionStrategy getCompressionStrategy() {
        ChannelHandler handler = this.channel.pipeline().get(CompressionCodec.NAME);
        if (!(handler instanceof CompressionCodec)) {
            return null;
        }
        return ((CompressionCodec) handler).getStrategy();
    }

    public static CompressionStrategy getCompressionStrategy(PacketCompressionAlgorithm algorithm, boolean initial) {
        if (initial) {
            return new SimpleCompressionStrategy(new NoopCompression());
        }
        switch (algorithm) {
            case ZLIB:
                return new SimpleCompressionStrategy(new ZlibCompression(true)); // raw deflate
            case SNAPPY:
                return new SimpleCompressionStrategy(new SnappyCompression());
            case NONE:
                return new SimpleCompressionStrategy(new NoopCompression());
            default:
                throw new UnsupportedOperationException("Unsupported compression: " + algorithm);
        }
    }

    public void close() {
        this.channel.disconnect();
    }

    protected void onClose() {
        if (this.channel.isOpen()) {
            log.warn("Tried to close peer, but channel is open!", new Throwable());
            return;
        }

        for (BedrockSession session : this.sessions.values()) {
            try {
                session.onClose();
            } catch (Exception e) {
                log.error("Exception whilst closing session", e);
            }
        }

        if (!this.closed.compareAndSet(false, true)) {
            return;
        }

        if (this.tickFuture != null) {
            this.tickFuture.cancel(false);
            this.tickFuture = null;
        }

        this.free();
    }

    public boolean isConnected() {
        return !this.closed.get() && this.channel.isOpen();
    }

    public SocketAddress getSocketAddress() {
        return this.channel.remoteAddress();
    }

    public Channel getChannel() {
        return this.channel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.sessions.put(0, this.sessionFactory.createSession(this, 0));
        this.tickFuture = this.channel.eventLoop().scheduleAtFixedRate(this::onTick, 10, 10, TimeUnit.MILLISECONDS);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        this.onClose();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            if (msg instanceof BedrockPacketWrapper) {
                this.onBedrockPacket((BedrockPacketWrapper) msg);
            } else {
                throw new DecoderException("Unexpected message type: " + msg.getClass().getName());
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof RakDisconnectReason) {
            onRakNetDisconnect(ctx, (RakDisconnectReason) evt);
        }
    }

    public long getPing() {
        RakServerChannel rakServerChannel = (RakServerChannel) this.channel.parent();
        RakChildChannel childChannel = rakServerChannel.getChildChannel(getSocketAddress());
        if (childChannel == null) {
            return -1L;
        }
        RakSessionCodec rakSessionCodec = childChannel.rakPipeline().get(RakSessionCodec.class);
        if (rakSessionCodec == null) {
            return -1L;
        }
        return rakSessionCodec.getPing();
    }
}
