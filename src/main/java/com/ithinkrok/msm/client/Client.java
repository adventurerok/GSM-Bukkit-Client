package com.ithinkrok.msm.client;

import com.ithinkrok.msm.common.Channel;
import com.ithinkrok.msm.common.MinecraftServerInfo;

/**
 * Created by paul on 04/02/16.
 */
public interface Client {

    MinecraftServerInfo getMinecraftServerInfo();

    Channel getChannel(String protocol);
}
