package cn.nukkit.network;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.player.PlayerCreationEvent;
import cn.nukkit.event.server.QueryRegenerateEvent;
import cn.nukkit.network.connection.BedrockPeer;
import cn.nukkit.network.connection.BedrockPong;
import cn.nukkit.network.connection.BedrockSession;
import cn.nukkit.network.connection.netty.initializer.BedrockServerInitializer;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.session.NetworkPlayerSession;
import cn.nukkit.utils.Utils;
import cn.nukkit.utils.bugreport.ExceptionHandler;
import com.google.common.base.Strings;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.internal.PlatformDependent;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.RakServerChannel;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;

import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;

/**
 * Bedrock network interface using the CloudBurst RakNet transport and Netty pipeline.
 * Replaces the old vendored RakNet implementation.
 */
@Log4j2
public class RakNetInterface implements AdvancedSourceInterface {

    private final Server server;
    private Network network;

    private final RakServerChannel rakServerChannel;
    private final EventLoopGroup eventLoopGroup;

    private final Map<InetSocketAddress, BedrockSession> sessions = new HashMap<>();
    private final Queue<BedrockSession> sessionCreationQueue = PlatformDependent.newMpscQueue();

    private final Map<InetAddress, LocalDateTime> blockedAddresses = new HashMap<>();

    private BedrockPong pong;

    public RakNetInterface(Server server) {
        this.server = server;

        // Pick optimal transport based on the OS
        Class<? extends DatagramChannel> channelClass;
        if (Epoll.isAvailable()) {
            channelClass = EpollDatagramChannel.class;
            this.eventLoopGroup = new EpollEventLoopGroup();
        } else if (KQueue.isAvailable()) {
            channelClass = KQueueDatagramChannel.class;
            this.eventLoopGroup = new KQueueEventLoopGroup();
        } else {
            channelClass = NioDatagramChannel.class;
            this.eventLoopGroup = new NioEventLoopGroup();
        }

        InetSocketAddress bindAddress = new InetSocketAddress(
                Strings.isNullOrEmpty(this.server.getIp()) ? "0.0.0.0" : this.server.getIp(),
                this.server.getPort()
        );

        this.pong = new BedrockPong()
                .edition("MCPE")
                .motd(server.getMotd())
                .subMotd(server.getSubMotd() == null ? "" : server.getSubMotd())
                .playerCount(server.getOnlinePlayers().size())
                .maximumPlayerCount(server.getMaxPlayers())
                .serverId(java.util.concurrent.ThreadLocalRandom.current().nextLong())
                .gameType(server.getDefaultGamemode() == 1 ? "Creative" : "Survival")
                .nintendoLimited(false)
                .protocolVersion(ProtocolInfo.CURRENT_PROTOCOL)
                .version(ProtocolInfo.MINECRAFT_VERSION_NETWORK)
                .ipv4Port(server.getPort())
                .ipv6Port(server.getPort());

        this.rakServerChannel = (RakServerChannel) new ServerBootstrap()
                .channelFactory(RakChannelFactory.server(channelClass))
                .option(RakChannelOption.RAK_ADVERTISEMENT, this.pong.toByteBuf())
                // CloudBurst RakNet protocol versions supported — Bedrock uses RakNet protocol 11
                .option(RakChannelOption.RAK_SUPPORTED_PROTOCOLS, new int[]{7, 8, 9, 10, 11})
                .option(RakChannelOption.RAK_PACKET_LIMIT, 200)
                .group(this.eventLoopGroup)
                .childHandler(new BedrockServerInitializer(this) {
                    @Override
                    protected BedrockSession createSession(BedrockPeer peer, int subClientId) {
                        BedrockSession session = super.createSession(peer, subClientId);
                        RakNetInterface.this.sessionCreationQueue.offer(session);
                        return session;
                    }
                })
                .bind(bindAddress)
                .awaitUninterruptibly()
                .channel();

        log.info("RakNet interface bound to {}", bindAddress);
    }

    // ---- AdvancedSourceInterface ----

    @Override
    public void blockAddress(InetAddress address) {
        this.blockedAddresses.put(address, LocalDateTime.MAX);
    }

    @Override
    public void blockAddress(InetAddress address, int timeout) {
        this.blockedAddresses.put(address, LocalDateTime.now().plusSeconds(timeout));
    }

    @Override
    public void unblockAddress(InetAddress address) {
        this.blockedAddresses.remove(address);
    }

    @Override
    public void sendRawPacket(InetSocketAddress socketAddress, ByteBuf payload) {
        // Send a raw datagram via the RakNet server channel
        this.rakServerChannel.pipeline().fireChannelRead(payload);
    }

    @Override
    public void setNetwork(Network network) {
        this.network = network;
    }

    // ---- SourceInterface ----

    @Override
    public boolean process() {
        // Expire timed-out IP blocks
        LocalDateTime now = LocalDateTime.now();
        this.blockedAddresses.entrySet().removeIf(entry -> entry.getValue().isBefore(now));

        // Process newly created sessions
        BedrockSession newSession;
        while ((newSession = this.sessionCreationQueue.poll()) != null) {
            InetSocketAddress address = newSession.getAddress();
            if (address == null) {
                continue;
            }

            // Check if the address is blocked
            if (isAddressBlocked(address.getAddress())) {
                newSession.disconnect("Your IP address is blocked by this server");
                continue;
            }

            try {
                PlayerCreationEvent event = new PlayerCreationEvent(this, Player.class, Player.class, null, address);
                this.server.getPluginManager().callEvent(event);

                this.sessions.put(event.getSocketAddress(), newSession);

                Constructor<? extends Player> constructor = event.getPlayerClass()
                        .getConstructor(SourceInterface.class, Long.class, InetSocketAddress.class);
                Player player = constructor.newInstance(this, event.getClientId(), event.getSocketAddress());
                // Set RakNet protocol version (CloudBurst uses v11)
                player.raknetProtocol = 11;
                newSession.setPlayer(player);
                this.server.addPlayer(address, player);
            } catch (Exception e) {
                Server.getInstance().getLogger().error("Failed to create Player", e);
                newSession.disconnect("Internal Server Error");
                this.sessions.remove(address);
                ExceptionHandler.handleSilently(e);
            }
        }

        // Tick active sessions, remove disconnected ones
        Iterator<Map.Entry<InetSocketAddress, BedrockSession>> iterator = this.sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<InetSocketAddress, BedrockSession> entry = iterator.next();
            BedrockSession session = entry.getValue();
            Player player = session.getPlayer();

            if (session.getDisconnectReason() != null) {
                if (player != null) {
                    player.close(player.getLeaveMessage(), session.getDisconnectReason(), false);
                }
                iterator.remove();
            } else if (player != null) {
                session.serverTick();
            }
        }

        return true;
    }

    @Override
    public Integer putPacket(Player player, DataPacket packet) {
        return this.putPacket(player, packet, false);
    }

    @Override
    public Integer putPacket(Player player, DataPacket packet, boolean needACK) {
        return this.putPacket(player, packet, needACK, false);
    }

    @Override
    public Integer putPacket(Player player, DataPacket packet, boolean needACK, boolean immediate) {
        BedrockSession session = this.sessions.get(player.getSocketAddress());
        if (session != null) {
            if (immediate) {
                session.sendImmediatePacket(packet, () -> {});
            } else {
                session.sendPacket(packet);
            }
        }
        return null;
    }

    @Override
    public void close(Player player) {
        this.close(player, "unknown reason");
    }

    @Override
    public void close(Player player, String reason) {
        NetworkPlayerSession playerSession = this.getSession(player.getSocketAddress());
        if (playerSession != null) {
            playerSession.disconnect(reason);
        }
    }

    @Override
    public void emergencyShutdown() {
        this.sessions.values().forEach(session -> session.disconnect("Shutdown"));
        this.rakServerChannel.close().syncUninterruptibly();
        this.eventLoopGroup.shutdownGracefully();
    }

    @Override
    public void shutdown() {
        this.sessions.values().forEach(session -> session.disconnect("Shutdown"));
        this.rakServerChannel.close().syncUninterruptibly();
        this.eventLoopGroup.shutdownGracefully();
    }

    @Override
    public int getNetworkLatency(Player player) {
        BedrockSession session = this.sessions.get(player.getSocketAddress());
        return session == null ? -1 : (int) session.getPing();
    }

    @Override
    public NetworkPlayerSession getSession(InetSocketAddress address) {
        return this.sessions.get(address);
    }

    @Override
    public void setName(String name) {
        QueryRegenerateEvent info = this.server.getQueryInformation();
        String[] names = name.split("!@#");
        String motd = Utils.rtrim(names[0].replace(";", "\\;"), '\\');
        String subMotd = names.length > 1 ? Utils.rtrim(names[1].replace(";", "\\;"), '\\') : "";

        this.pong.motd(motd)
                .subMotd(subMotd)
                .playerCount(info.getPlayerCount())
                .maximumPlayerCount(info.getMaxPlayerCount());

        this.rakServerChannel.config().setOption(RakChannelOption.RAK_ADVERTISEMENT, this.pong.toByteBuf());
    }

    public Network getNetwork() {
        return this.network;
    }

    // ---- Internal helpers ----

    private boolean isAddressBlocked(InetAddress address) {
        LocalDateTime blockedUntil = this.blockedAddresses.get(address);
        if (blockedUntil == null) return false;
        if (blockedUntil.isAfter(LocalDateTime.now())) return true;
        this.blockedAddresses.remove(address);
        return false;
    }
}
