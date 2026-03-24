package cn.nukkit.network.connection;

import cn.nukkit.Nukkit;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.RakNetInterface;
import cn.nukkit.network.connection.netty.BedrockPacketWrapper;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.DisconnectPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.PacketCompressionAlgorithm;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.utils.VarInt;
import cn.nukkit.utils.bugreport.ExceptionHandler;
import com.google.common.base.Preconditions;
import io.netty.buffer.ByteBuf;
import io.netty.util.internal.PlatformDependent;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.message.FormattedMessage;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Replaces RakNetPlayerSession. Bridges the new CloudBurst/Netty pipeline with Petter's
 * existing server-side session and player creation logic.
 *
 * The inbound packet flow is:
 * Netty pipeline (decompression, decryption) -> BedrockBatchDecoder -> BedrockPacketCodec
 * -> BedrockPeer.channelRead -> BedrockSession.onPacket -> inbound queue
 * -> serverTick() -> player.handleDataPacket()
 */
@Log4j2
public class BedrockSession implements NetworkPlayerSession {

    private final RakNetInterface server;
    protected final BedrockPeer peer;
    protected final int subClientId;

    private final Queue<DataPacket> inbound = PlatformDependent.newSpscQueue();

    private Player player;
    private String disconnectReason = null;

    private CompressionProvider compressionIn;
    private CompressionProvider compressionOut;
    private boolean compressionInitialized;

    private int protocol = ProtocolInfo.CURRENT_PROTOCOL;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    public BedrockSession(RakNetInterface server, BedrockPeer peer, int subClientId) {
        this.server = server;
        this.peer = peer;
        this.subClientId = subClientId;
        // Default compression: NONE until negotiated
        this.compressionIn = CompressionProvider.NONE;
        this.compressionOut = CompressionProvider.NONE;
    }

    public int getSubClientId() {
        return subClientId;
    }

    public int getProtocol() {
        return protocol;
    }

    public void setProtocol(int protocol) {
        this.protocol = protocol;
    }

    /**
     * Called by BedrockPeer when an inbound packet wrapper arrives from the pipeline.
     * Decodes the raw ByteBuf into a DataPacket and queues it for the server tick thread.
     */
    public void onPacket(BedrockPacketWrapper wrapper) {
        ByteBuf buf = wrapper.getPacketBuffer();
        if (buf == null) {
            return;
        }

        // The raw buffer contains the packet starting at headerLength (after the header varint)
        buf.readerIndex(wrapper.getHeaderLength());

        int len = buf.readableBytes();
        if (len > 12582912) {
            log.error("Received too big packet: {}", len);
            if (this.player != null) {
                this.player.close("Too big packet");
            }
            return;
        }

        int packetId = wrapper.getPacketId();

        // Use internal backwards compatible IDs until pid() is rewritten
        // Petter uses offset: vanilla ID >= 300 maps to internal id - 100
        int internalId = packetId >= 300 ? packetId - 100 : packetId;
        DataPacket pk = this.server.getNetwork().getPacket(internalId);

        if (pk == null) {
            log.warn("Received unknown packet with vanilla ID 0x{} from {}", Integer.toHexString(packetId), this.getAddress());
            if (this.player != null) {
                this.player.close("Unknown packet 0x" + Integer.toHexString(packetId));
            }
            return;
        }

        // Read remaining bytes as the packet payload (after the header varint which already consumed packetId)
        byte[] packetBytes = new byte[buf.readableBytes()];
        buf.readBytes(packetBytes);

        pk.setBuffer(packetBytes, 0);
        pk.protocol = this.protocol;

        try {
            pk.decode();
        } catch (Exception e) {
            log.warn("Unable to decode {} (id=0x{}) from {}: {}",
                    pk.getClass().getSimpleName(), Integer.toHexString(packetId),
                    this.getAddress(), e.getMessage());
            if (Nukkit.DEBUG > 1) {
                log.debug("Decode failure detail", e);
            }
            return;
        }

        this.inbound.offer(pk);
    }

    @Override
    public void disconnect(String reason) {
        if (this.disconnectReason != null) {
            return;
        }
        this.disconnectReason = reason;

        // Send disconnect packet and then close the channel after a short delay
        DisconnectPacket disconnectPacket = new DisconnectPacket();
        disconnectPacket.message = reason;
        disconnectPacket.protocol = this.protocol;
        disconnectPacket.tryEncode();
        this.peer.sendPacketImmediately(this.subClientId, 0, disconnectPacket);

        this.peer.getChannel().eventLoop().schedule(
                () -> this.peer.close(),
                10, TimeUnit.MILLISECONDS
        );
    }

    @Override
    public CompressionProvider getCompression() {
        return this.compressionOut;
    }

    public String getDisconnectReason() {
        return this.disconnectReason;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    public void setPlayer(Player player) {
        Preconditions.checkArgument(this.player == null && player != null);
        this.player = player;
    }

    @Override
    public void sendImmediatePacket(DataPacket packet, Runnable callback) {
        if (this.disconnectReason != null || this.closed.get()) {
            return;
        }
        sendPacket(packet);
        this.peer.getChannel().eventLoop().execute(() -> {
            this.peer.flushSendQueue();
            callback.run();
        });
    }

    @Override
    public void sendPacket(DataPacket packet) {
        if (this.disconnectReason != null || this.closed.get()) {
            return;
        }
        if (!(packet instanceof cn.nukkit.network.protocol.BatchPacket)) {
            packet.tryEncode();
        }
        this.peer.sendPacket(this.subClientId, 0, packet);
    }

    @Override
    public void setCompression(CompressionProvider compression) {
        Preconditions.checkNotNull(compression, "compression");
        this.compressionIn = compression;
        this.compressionOut = compression;
        this.compressionInitialized = true;

        // Map CompressionProvider to PacketCompressionAlgorithm for the pipeline
        PacketCompressionAlgorithm algorithm;
        if (compression == CompressionProvider.SNAPPY) {
            algorithm = PacketCompressionAlgorithm.SNAPPY;
        } else if (compression == CompressionProvider.ZLIB_RAW || compression == CompressionProvider.ZLIB) {
            algorithm = PacketCompressionAlgorithm.ZLIB;
        } else {
            algorithm = PacketCompressionAlgorithm.NONE;
        }
        this.peer.setCompression(algorithm);
    }

    @Override
    public void setEncryption(SecretKey encryptionKey, Cipher encryptionCipher, Cipher decryptionCipher) {
        // Encryption is managed at the peer/pipeline level
        this.peer.enableEncryption(encryptionKey);
    }

    @Override
    public long getPing() {
        return this.peer.getPing();
    }

    /**
     * Called by the server tick thread to drain the inbound packet queue.
     */
    public void serverTick() {
        DataPacket packet;
        while ((packet = this.inbound.poll()) != null) {
            try {
                this.player.handleDataPacket(packet);
            } catch (Throwable e) {
                log.error(new FormattedMessage("An error occurred whilst handling {} for {}",
                        new Object[]{packet.getClass().getSimpleName(), this.player.getName()}, e));
                ExceptionHandler.handleSilently(e);
            }
        }
    }

    /**
     * Called when the underlying channel closes (from BedrockPeer.onClose).
     */
    public void onClose() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        if (this.disconnectReason == null) {
            this.disconnectReason = "Connection closed";
        }
    }

    /**
     * Closes the session with the given reason (send disconnect + schedule channel close).
     */
    public void close(String reason) {
        if (this.closed.get()) {
            return;
        }
        this.disconnect(reason);
    }

    public InetSocketAddress getAddress() {
        SocketAddress addr = this.peer.getSocketAddress();
        if (addr instanceof InetSocketAddress) {
            return (InetSocketAddress) addr;
        }
        return null;
    }

    public BedrockPeer getPeer() {
        return peer;
    }
}
