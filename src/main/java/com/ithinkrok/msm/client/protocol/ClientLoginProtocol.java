package com.ithinkrok.msm.client.protocol;

import com.ithinkrok.msm.client.Client;
import com.ithinkrok.msm.client.ClientListener;
import com.ithinkrok.msm.client.impl.MSMClient;
import com.ithinkrok.msm.common.Channel;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * Created by paul on 04/02/16.
 */
public class ClientLoginProtocol implements ClientListener {

    @Override
    public void connectionOpened(Client client, Channel channel) {

    }

    @Override
    public void connectionClosed(Client client, Channel channel) {

    }

    @Override
    public void packetRecieved(Client client, Channel channel, ConfigurationSection payload) {
        List<String> sharedProtocols = payload.getStringList("protocols");

        ((MSMClient)client).setSupportedProtocols(sharedProtocols);
    }
}
