package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * Sent by the client to notify the server when a loading screen has been shown or dismissed.
 * Introduced in Bedrock ~1.20.x alongside the optional loadingScreenId in ChangeDimensionPacket.
 * The server does not need to respond; this packet is accepted and ignored.
 */
@ToString
public class ServerboundLoadingScreenPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.__INTERNAL__SERVERBOUND_LOADING_SCREEN_PACKET;

    /** Loading screen state reported by the client (0 = unknown, 2 = loaded). */
    public int type;
    /** Optional screen ID matching the one sent in ChangeDimensionPacket. */
    public Integer screenId;

    @Override
    public void decode() {
        this.type = (int) this.getUnsignedVarInt();
        if (this.getOffset() < this.getCount() && this.getBoolean()) {
            this.screenId = this.getLInt();
        }
    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
