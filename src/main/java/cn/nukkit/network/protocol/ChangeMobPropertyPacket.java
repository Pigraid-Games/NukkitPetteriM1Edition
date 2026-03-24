package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * Updates the value of ONE property on ONE specific entity instance.
 * Sent server→client whenever setEntityProperty() or removeEntityProperty() is called.
 *
 * All four value fields are always present on the wire. The client uses the schema
 * (from SyncEntityPropertyPacket) to know which field to read.
 * Set only the field matching your property type; leave others as zero/empty.
 */
@ToString
public class ChangeMobPropertyPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.CHANGE_MOB_PROPERTY_PACKET;

    /** Entity unique ID — encoded as LE 8-byte long (putLLong, NOT putLong). */
    public long uniqueEntityId;
    /** Property name, e.g. "example:fuel" */
    public String property;
    /** Value for TYPE_BOOL properties */
    public boolean boolValue;
    /** Value for TYPE_ENUM (string) properties */
    public String stringValue = "";
    /** Value for TYPE_INT properties */
    public int intValue;
    /** Value for TYPE_FLOAT properties */
    public float floatValue;

    @Override
    public void decode() {
        this.uniqueEntityId = this.getLLong();
        this.property       = this.getString();
        this.boolValue      = this.getBoolean();
        this.stringValue    = this.getString();
        this.intValue       = this.getVarInt();
        this.floatValue     = this.getLFloat();
    }

    @Override
    public void encode() {
        this.reset();
        this.putLLong(this.uniqueEntityId);
        this.putString(this.property);
        this.putBoolean(this.boolValue);
        this.putString(this.stringValue != null ? this.stringValue : "");
        this.putVarInt(this.intValue);
        this.putLFloat(this.floatValue);
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }
}
