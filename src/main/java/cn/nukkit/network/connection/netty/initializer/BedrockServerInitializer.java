package cn.nukkit.network.connection.netty.initializer;

import cn.nukkit.network.RakNetInterface;
import cn.nukkit.network.connection.BedrockPeer;
import cn.nukkit.network.connection.BedrockSession;

/**
 * Server-side channel initializer that creates BedrockSession instances wired to the
 * RakNetInterface for player creation callbacks.
 */
public class BedrockServerInitializer extends BedrockChannelInitializer {

    private final RakNetInterface rakNetInterface;

    public BedrockServerInitializer(RakNetInterface rakNetInterface) {
        this.rakNetInterface = rakNetInterface;
    }

    @Override
    protected BedrockSession createSession(BedrockPeer peer, int subClientId) {
        return new BedrockSession(this.rakNetInterface, peer, subClientId);
    }
}
