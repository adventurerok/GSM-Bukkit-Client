package com.ithinkrok.msm.client;

import com.ithinkrok.msm.common.Packet;
import io.netty.channel.Channel;

/**
 * Created by paul on 03/02/16.
 */
public interface ClientListener {

    void packetRecieved(Client client, Channel channel, Packet packet);
}
