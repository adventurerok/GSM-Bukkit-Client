package com.ithinkrok.msm.client;

import com.ithinkrok.msm.common.Channel;
import com.ithinkrok.util.config.Config;

/**
 * Created by paul on 03/02/16.
 */
public interface ClientListener {

    void connectionOpened(Client client, Channel channel);

    void connectionClosed(Client client);

    void packetRecieved(Client client, Channel channel, Config payload);
}
