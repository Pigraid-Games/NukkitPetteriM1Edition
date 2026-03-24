package cn.nukkit.network.protocol;

import cn.nukkit.entity.data.EntityMetadata;
import cn.nukkit.utils.Binary;
import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class SetEntityDataPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SET_ENTITY_DATA_PACKET;
    public long eid;
    public EntityMetadata metadata;
    public long frame;
    public int[] intPropertyIndices  = new int[0];
    public int[] intPropertyValues   = new int[0];
    public int[]   floatPropertyIndices = new int[0];
    public float[] floatPropertyValues  = new float[0];

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        this.putEntityRuntimeId(this.eid);
        this.put(Binary.writeMetadata(protocol, this.metadata));
        if (protocol >= ProtocolInfo.v1_16_100) {
            if (protocol >= ProtocolInfo.v1_19_40) {
                this.putUnsignedVarInt(intPropertyIndices.length);
                for (int i = 0; i < intPropertyIndices.length; i++) {
                    this.putUnsignedVarInt(intPropertyIndices[i]);
                    this.putVarInt(intPropertyValues[i]);
                }
                this.putUnsignedVarInt(floatPropertyIndices.length);
                for (int i = 0; i < floatPropertyIndices.length; i++) {
                    this.putUnsignedVarInt(floatPropertyIndices[i]);
                    this.putLFloat(floatPropertyValues[i]);
                }
            }
            this.putUnsignedVarLong(this.frame);
        }
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
