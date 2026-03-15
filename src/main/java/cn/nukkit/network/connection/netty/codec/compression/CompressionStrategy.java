package cn.nukkit.network.connection.netty.codec.compression;

import cn.nukkit.network.connection.netty.BedrockBatchWrapper;
import cn.nukkit.network.protocol.types.PacketCompressionAlgorithm;

public interface CompressionStrategy {

    BatchCompression getCompression(BedrockBatchWrapper wrapper);

    BatchCompression getCompression(PacketCompressionAlgorithm algorithm);

    BatchCompression getDefaultCompression();
}
