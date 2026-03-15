package cn.nukkit.network.protocol.types;

public enum PacketCompressionAlgorithm {

    ZLIB((byte) 0x00),
    SNAPPY((byte) 0x01),
    NONE((byte) 0xFF);

    private final byte id;

    PacketCompressionAlgorithm(byte id) {
        this.id = id;
    }

    public byte getId() {
        return this.id;
    }
}
