package com.ithinkrok.msm.client;

import com.ithinkrok.msm.common.Channel;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Created by paul on 03/02/16.
 */
public interface ClientListener {

    void packetRecieved(Client client, Channel channel, ConfigurationSection payload);
}
