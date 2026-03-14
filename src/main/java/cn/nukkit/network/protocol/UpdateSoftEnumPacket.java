package cn.nukkit.network.protocol;

import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@ToString
public class UpdateSoftEnumPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.UPDATE_SOFT_ENUM_PACKET;

    public List<String> values = new ArrayList<>();
    public String name = "";
    public Type type = Type.SET;

    public enum Type {
        ADD,
        REMOVE,
        SET
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(name);
        this.putUnsignedVarInt(values.size());

        for (String value : values) {
            this.putString(value);
        }
        this.putByte((byte) type.ordinal());
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
