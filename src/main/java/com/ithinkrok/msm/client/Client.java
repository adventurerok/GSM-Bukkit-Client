package com.ithinkrok.msm.client;

import com.ithinkrok.msm.common.Channel;
import com.ithinkrok.msm.common.MinecraftServerInfo;

import java.util.UUID;

/**
 * Created by paul on 04/02/16.
 */
public interface Client {

    MinecraftServerInfo getMinecraftServerInfo();

    Channel getChannel(String protocol);

    void changePlayerServer(UUID playerUUID, String serverName);
}
