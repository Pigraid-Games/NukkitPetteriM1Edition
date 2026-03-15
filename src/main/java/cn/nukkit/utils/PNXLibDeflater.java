package cn.nukkit.utils;

import cn.nukkit.Server;
import cn.powernukkitx.libdeflate.LibdeflateCompressor;

public final class PNXLibDeflater extends LibdeflateCompressor {
    public PNXLibDeflater() {
        this(Server.getInstance() != null ? Server.getInstance().networkCompressionLevel : 7);
    }

    public PNXLibDeflater(int level) {
        super(level);
    }
}
