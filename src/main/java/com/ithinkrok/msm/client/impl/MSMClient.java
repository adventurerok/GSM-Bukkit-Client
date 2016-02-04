package com.ithinkrok.msm.client.impl;

import com.google.common.net.HostAndPort;
import com.ithinkrok.msm.client.ClientListener;
import com.ithinkrok.msm.client.Client;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by paul on 01/02/16.
 */
public class MSMClient extends ChannelInboundHandlerAdapter implements Client {

    private static boolean started = false;
    private static Map<String, ClientListener> preStartListenerMap = new HashMap<>();

    private final HostAndPort address;
    private volatile io.netty.channel.Channel channel;

    private final Map<String, ClientListener> listenerMap = new HashMap<>();
    private final Map<String, Channel> channelMap = new HashMap<>();

    private final Map<Byte, String> idToProtocolMap = new HashMap<>();

    public MSMClient(HostAndPort address) {
        this.address = address;

        //Add the MSMLogin protocol to the protocol map to make logins work
        idToProtocolMap.put((byte) 0, "MSMLogin");
    }

    public static void addProtocol(String protocolName, ClientListener protocolListener) {
        if(started) throw new RuntimeException("The MSMClient has already started");
        preStartListenerMap.put(protocolName, protocolListener);
    }

    public void start() {
        started = true;

        //Clear out the static map to prevent objects from being kept alive due to being kept in this
        listenerMap.putAll(preStartListenerMap);
        preStartListenerMap.clear();

        System.out.println("Connecting to MSM server: " + address);

        EventLoopGroup workerGroup = createNioEventLoopGroup();

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

        //noinspection unchecked
        future.addListeners(unused1 -> startRequest());

        future.channel().closeFuture().addListener(unused2 -> workerGroup.shutdownGracefully());
    }

    NioEventLoopGroup createNioEventLoopGroup() {
        return new NioEventLoopGroup(1);
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

    void startRequest() {
        System.out.println("Connected successfully and sending login packet");

        MemoryConfiguration loginPayload = new MemoryConfiguration();

        loginPayload.set("hostname", address.getHostText());
        loginPayload.set("protocols", new ArrayList<>(listenerMap.keySet()));
        loginPayload.set("version", 0);

        Packet loginPacket = new Packet((byte) 0, loginPayload);

        channel.writeAndFlush(loginPacket);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        channel = ctx.channel();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println(msg);
    }

    private class MSMClientChannel implements Channel {

        private final byte id;

        public MSMClientChannel(byte id) {
            this.id = id;
        }

        @Override
        public void write(ConfigurationSection packet) {
            channel.writeAndFlush(new Packet(id, packet));
        }
    }
}
