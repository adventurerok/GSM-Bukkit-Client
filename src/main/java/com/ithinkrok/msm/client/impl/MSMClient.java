package com.ithinkrok.msm.client.impl;

import com.google.common.base.Charsets;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.net.HostAndPort;
import com.ithinkrok.msm.client.Client;
import com.ithinkrok.msm.client.ClientListener;
import com.ithinkrok.msm.client.protocol.ClientLoginProtocol;
import com.ithinkrok.msm.common.Channel;
import com.ithinkrok.msm.common.MinecraftClientInfo;
import com.ithinkrok.msm.common.Packet;
import com.ithinkrok.msm.common.handler.MSMFrameDecoder;
import com.ithinkrok.msm.common.handler.MSMFrameEncoder;
import com.ithinkrok.msm.common.handler.MSMPacketDecoder;
import com.ithinkrok.msm.common.handler.MSMPacketEncoder;
import com.ithinkrok.util.config.Config;
import com.ithinkrok.util.config.MemoryConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.commons.lang.Validate;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by paul on 01/02/16.
 */
@ChannelHandler.Sharable
public class MSMClient extends ChannelInboundHandlerAdapter implements Client, ChannelFutureListener {

    private static final Map<String, ClientListener> preStartListenerMap = new HashMap<>();
    private static boolean started = false;
    private final Random random = new Random();
    private final HostAndPort address;
    private final Map<String, ClientListener> listenerMap = new HashMap<>();
    private final Map<Integer, MSMClientChannel> channelMap = new HashMap<>();
    private final BiMap<Integer, String> idToProtocolMap = HashBiMap.create();
    private final MinecraftClientInfo serverInfo;
    private final EventLoopGroup workerGroup = createNioEventLoopGroup();
    private volatile io.netty.channel.Channel channel;
    private boolean serverStopping = false;
    private int connectFails = 0;

    private final byte[] password;

    public MSMClient(HostAndPort address, MinecraftClientInfo serverInfo, String password) {
        this.address = address;
        this.serverInfo = serverInfo;
        this.password = password.getBytes(Charsets.UTF_8);

        reset();

        //Add default protocols
        listenerMap.put("MSMLogin", new ClientLoginProtocol());

        //Clear out the static map to prevent objects from being kept alive due to being kept in this
        listenerMap.putAll(preStartListenerMap);
        preStartListenerMap.clear();
    }

    private void reset() {
        started = false;
        channel = null;

        idToProtocolMap.clear();
        idToProtocolMap.put(0, "MSMLogin");

        channelMap.clear();
    }

    public static void addProtocol(String protocolName, ClientListener protocolListener) {
        if (started) throw new RuntimeException("The MSMClient has already started");
        preStartListenerMap.put(protocolName, protocolListener);
    }

    public void close() {
        serverStopping = true;

        if (channel != null) channel.close();

        workerGroup.shutdownGracefully();
    }

    @Override
    public MinecraftClientInfo getMinecraftServerInfo() {
        return serverInfo;
    }

    @Override
    public Channel getChannel(String protocol) {
        return getChannel(idToProtocolMap.inverse().get(protocol));
    }

    @Override
    public boolean changePlayerServer(UUID playerUUID, String serverName) {
        Validate.notNull(playerUUID, "playerUUID cannot be null");
        Validate.notNull(serverName, "serverName cannot be null");

        Config payload = new MemoryConfig();

        payload.set("player", playerUUID);
        payload.set("target", serverName);
        payload.set("mode", "ChangeServer");

        getAPIChannel().write(payload);

        return true;
    }

    private Channel getAPIChannel() {
        return getChannel("MSMAPI");
    }

    public Collection<String> getSupportedProtocols() {
        return idToProtocolMap.values();
    }

    public void setSupportedProtocols(Iterable<String> supportedProtocols) {
        idToProtocolMap.clear();

        int counter = 0;

        for (String protocol : supportedProtocols) {
            idToProtocolMap.put(counter++, protocol);
        }
    }

    public void start() {
        started = true;

        Bootstrap b = createBootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                setupPipeline(ch.pipeline());
            }
        });

        ChannelFuture future = b.connect(address.getHostText(), address.getPort());

        future.addListener(this);
    }

    Bootstrap createBootstrap() {
        return new Bootstrap();
    }

    private void setupPipeline(ChannelPipeline pipeline) {
        //inbound
        pipeline.addLast("MSMFrameDecoder", new MSMFrameDecoder());
        pipeline.addLast("MSMPacketDecoder", new MSMPacketDecoder());

        //outbound
        pipeline.addLast("MSMFrameEncoder", new MSMFrameEncoder());
        pipeline.addLast("MSMPacketEncoder", new MSMPacketEncoder());

        pipeline.addLast("MSMClient", this);
    }

    NioEventLoopGroup createNioEventLoopGroup() {
        return new NioEventLoopGroup(1);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        channel = ctx.channel();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        for (String protocol : idToProtocolMap.values()) {
            getListenerForProtocol(protocol).connectionClosed(this);
        }

        System.out.println("Lost connection, attempting reconnect");

        EventLoop loop = ctx.channel().eventLoop();
        reconnect(loop);
    }

    public ClientListener getListenerForProtocol(String protocol) {
        return listenerMap.get(protocol);
    }

    private void reconnect(ScheduledExecutorService eventLoop) {
        reset();

        if (serverStopping) return;

        ++connectFails;

        if ((connectFails % 10) == 0) {
            System.out.println("Failed to reconnect to MSM Server after " + connectFails + " attempts");
        }

        long waitTime = 15L;
        if (connectFails == 1) waitTime = 5L + random.nextInt(15);

        eventLoop.schedule(this::start, waitTime, TimeUnit.SECONDS);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //Pass on Objects that are not Packets to the next handler
        if (!Packet.class.isInstance(msg)) {
            super.channelRead(ctx, msg);
            return;
        }

        Packet packet = (Packet) msg;
        String protocol = idToProtocolMap.get(packet.getId());

        MSMClientChannel channel = getChannel(packet.getId());

        //Send the packet to the listener for the specified protocol
        listenerMap.get(protocol).packetRecieved(this, channel, packet.getPayload());
    }

    private MSMClientChannel getChannel(int id) {
        MSMClientChannel channel = channelMap.get(id);

        if (channel == null) {
            channel = new MSMClientChannel(id);
            channelMap.put(id, channel);
        }

        return channel;
    }

    /**
     * Called when the connection succeeds or fails.
     *
     * @param future The channel future
     * @throws Exception
     */
    @Override
    public void operationComplete(ChannelFuture future) throws Exception {
        if (future.isSuccess()) {
            System.out.println("Connected to MSM server: " + address);
            connectFails = 0;

            startRequest();
        } else {
            EventLoop loop = future.channel().eventLoop();
            reconnect(loop);
        }
    }

    void startRequest() {
        System.out.println("Connected successfully and sending login packet");

        Config loginPayload = new MemoryConfig();

        loginPayload.set("hostname", address.getHostText());
        loginPayload.set("protocols", new ArrayList<>(listenerMap.keySet()));
        loginPayload.set("version", 0);

        Config serverInfo = this.serverInfo.toConfig();

        loginPayload.set("client_info", serverInfo);

        loginPayload.set("password", password);

        Packet loginPacket = new Packet((byte) 0, loginPayload);

        channel.writeAndFlush(loginPacket);
    }

    private class MSMClientChannel implements Channel {

        private final int id;

        public MSMClientChannel(int id) {
            this.id = id;
        }

        @Override
        public void write(Config packet) {
            channel.writeAndFlush(new Packet(id, packet));
        }

    }
}
