package com.ithinkrok.msm.client.impl;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.net.HostAndPort;
import com.ithinkrok.msm.client.ClientListener;
import com.ithinkrok.msm.client.Client;
import com.ithinkrok.msm.common.MinecraftServerInfo;
import com.ithinkrok.msm.client.protocol.ClientLoginProtocol;
import com.ithinkrok.msm.common.Channel;
import com.ithinkrok.msm.common.Packet;
import com.ithinkrok.msm.common.handler.MSMFrameDecoder;
import com.ithinkrok.msm.common.handler.MSMFrameEncoder;
import com.ithinkrok.msm.common.handler.MSMPacketDecoder;
import com.ithinkrok.msm.common.handler.MSMPacketEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

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
    private final HostAndPort address;
    private final Map<String, ClientListener> listenerMap = new HashMap<>();
    private final Map<Integer, MSMClientChannel> channelMap = new HashMap<>();
    private final BiMap<Integer, String> idToProtocolMap = HashBiMap.create();
    private final MinecraftServerInfo serverInfo;
    private volatile io.netty.channel.Channel channel;
    private boolean serverStopping = false;

    private int connectFails = 0;

    private EventLoopGroup workerGroup = createNioEventLoopGroup();

    public MSMClient(HostAndPort address, MinecraftServerInfo serverInfo) {
        this.address = address;
        this.serverInfo = serverInfo;

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
    public MinecraftServerInfo getMinecraftServerInfo() {
        return serverInfo;
    }

    @Override
    public Channel getChannel(String protocol) {
        return getChannel(idToProtocolMap.inverse().get(protocol));
    }

    private MSMClientChannel getChannel(int id) {
        MSMClientChannel channel = channelMap.get(id);

        if (channel == null) {
            channel = new MSMClientChannel(id);
            channelMap.put(id, channel);
        }

        return channel;
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

        MemoryConfiguration loginPayload = new MemoryConfiguration();

        loginPayload.set("hostname", address.getHostText());
        loginPayload.set("protocols", new ArrayList<>(listenerMap.keySet()));
        loginPayload.set("version", 0);

        ConfigurationSection serverInfo = this.serverInfo.toConfig();

        loginPayload.set("server_info", serverInfo);

        Packet loginPacket = new Packet((byte) 0, loginPayload);

        channel.writeAndFlush(loginPacket);
    }

    private void reconnect(ScheduledExecutorService eventLoop) {
        reset();

        if (serverStopping) return;

        ++connectFails;

        if ((connectFails % 10) == 0) {
            System.out.println("Failed to reconnect to MSM Server after " + connectFails + " attempts");
        }

        eventLoop.schedule(this::start, 15L, TimeUnit.SECONDS);
    }

    private class MSMClientChannel implements Channel {

        private final int id;

        public MSMClientChannel(int id) {
            this.id = id;
        }

        @Override
        public void write(ConfigurationSection packet) {
            channel.writeAndFlush(new Packet(id, packet));
        }
    }
}
