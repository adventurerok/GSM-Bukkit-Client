package com.ithinkrok.msm.bukkit.protocol;

import com.ithinkrok.msm.client.Client;
import com.ithinkrok.msm.client.ClientListener;
import com.ithinkrok.msm.common.Channel;
import com.ithinkrok.util.config.Config;

/**
 * Created by paul on 06/03/16.
 */
public class ClientMinecraftRequestProtocol implements ClientListener {

    @Override
    public void connectionOpened(Client client, Channel channel) {

    }

    @Override
    public void connectionClosed(Client client) {

    }

    @Override
    public void packetRecieved(Client client, Channel channel, Config payload) {

    }
}
