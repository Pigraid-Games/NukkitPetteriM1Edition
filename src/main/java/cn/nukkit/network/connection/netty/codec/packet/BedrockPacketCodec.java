package cn.nukkit.network.connection.netty.codec.packet;

import cn.nukkit.network.connection.netty.BedrockBatchWrapper;
import cn.nukkit.network.connection.netty.BedrockPacketWrapper;
import cn.nukkit.network.connection.netty.codec.compression.CompressionCodec;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.utils.ByteBufVarInt;
import io.netty.channel.ChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.List;

/**
 * Packet codec for the Bedrock protocol.
 *
 * Decode: receives a raw ByteBuf (one packet from BedrockBatchDecoder), reads the header varint
 * to extract packetId and sub-client IDs, then wraps the remaining raw bytes into a
 * BedrockPacketWrapper. Actual DataPacket decoding happens in BedrockSession.serverTick().
 *
 * Encode: if the wrapper already has a pre-encoded packetBuffer, use that directly.
 * Otherwise, call tryEncode() on the DataPacket to get the encoded bytes (which include the
 * packed ID written by DataPacket.reset()), and store them as the packetBuffer.
 */
public abstract class BedrockPacketCodec extends MessageToMessageCodec<ByteBuf, BedrockPacketWrapper> {

    public static final String NAME = "bedrock-packet-codec";
    private static final InternalLogger log = InternalLoggerFactory.getInstance(BedrockPacketCodec.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, BedrockPacketWrapper msg, List<Object> out) throws Exception {
        if (msg.getPacketBuffer() != null) {
            // Pre-encoded buffer already present - use as-is
            out.add(msg.retain());
            return;
        }

        DataPacket packet = msg.getPacket();
        if (packet == null) {
            log.error("BedrockPacketWrapper has no packet and no packetBuffer");
            return;
        }

        // Special case: BatchPacket carries pre-compressed batch payload (used for cached chunk data).
        // Its encode() method is a no-op, so we must bypass normal packet encoding and send the
        // payload directly as a BedrockBatchWrapper so CompressionCodec passes it through unchanged.
        // For protocol >= 1.20.60 the CompressionCodec normally prepends a 1-byte algorithm header;
        // since we skip that codec we must add the header here ourselves.
        if (packet instanceof BatchPacket) {
            byte[] payload = ((BatchPacket) packet).payload;
            if (payload != null && payload.length > 0) {
                // Check whether the active CompressionCodec uses prefixed mode
                boolean prefixed = false;
                byte prefixByte = 0x00; // Zlib default
                ChannelHandler compressionHandler = ctx.pipeline().get(CompressionCodec.NAME);
                if (compressionHandler instanceof CompressionCodec) {
                    CompressionCodec cc = (CompressionCodec) compressionHandler;
                    if (cc.isPrefixed()) {
                        prefixed = true;
                        prefixByte = cc.getDefaultCompressionHeader();
                    }
                }

                ByteBuf compressedBuf;
                if (prefixed) {
                    compressedBuf = ctx.alloc().buffer(1 + payload.length);
                    compressedBuf.writeByte(prefixByte);
                    compressedBuf.writeBytes(payload);
                } else {
                    compressedBuf = ctx.alloc().buffer(payload.length);
                    compressedBuf.writeBytes(payload);
                }
                // newInstance(compressed, uncompressed=null) + modified=false → CompressionCodec passes through
                BedrockBatchWrapper batchWrapper = BedrockBatchWrapper.newInstance(compressedBuf, null);
                out.add(batchWrapper);
            }
            return;
        }

        // tryEncode() fills packet's internal buffer, including the packet ID header
        // written by DataPacket.reset() via putUnsignedVarInt(pid)
        packet.tryEncode();
        byte[] encoded = packet.getBuffer();
        if (encoded == null) {
            log.error("Packet {} encode produced null buffer", packet.getClass().getSimpleName());
            return;
        }

        // Build the full wire buffer: encode the sub-client header + already-encoded packet bytes
        ByteBuf buf = ctx.alloc().buffer(5 + encoded.length);
        try {
            msg.setPacketId(packet.pid() & 0xFF);
            encodeHeader(buf, msg);
            // The encoded byte array already starts with the packet ID, skip it by writing from
            // the header length offset. Since DataPacket.reset() writes the ID as a varint, we
            // need to skip those bytes. We compute the varint length of the packet ID.
            int idVarIntLen = varIntLength(msg.getPacketId());
            if (encoded.length > idVarIntLen) {
                buf.writeBytes(encoded, idVarIntLen, encoded.length - idVarIntLen);
            }
            msg.setPacketBuffer(buf.retain());
            out.add(msg.retain());
        } catch (Throwable t) {
            log.error("Error encoding packet {}", packet.getClass().getSimpleName(), t);
        } finally {
            buf.release();
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        BedrockPacketWrapper wrapper = new BedrockPacketWrapper();
        // Retain the full raw buffer for later processing in BedrockSession
        wrapper.setPacketBuffer(msg.retainedSlice());
        try {
            int index = msg.readerIndex();
            decodeHeader(msg, wrapper);
            wrapper.setHeaderLength(msg.readerIndex() - index);
            // Don't decode the DataPacket here - pass the raw buffer to BedrockSession
            // which will use Network.processBatch or handleDataPacket for actual decoding
            out.add(wrapper.retain());
        } catch (Throwable t) {
            log.warn("Failed to decode packet header", t);
        } finally {
            wrapper.release();
        }
    }

    public abstract void encodeHeader(ByteBuf buf, BedrockPacketWrapper msg);

    public abstract void decodeHeader(ByteBuf buf, BedrockPacketWrapper msg);

    /** Returns the number of bytes a VarInt-encoded unsigned int would occupy. */
    private static int varIntLength(int value) {
        int len = 1;
        long v = value & 0xFFFFFFFFL;
        while ((v & ~0x7FL) != 0) {
            len++;
            v >>>= 7;
        }
        return len;
    }
}
