package com.ithinkrok.msm.client;

import com.ithinkrok.msm.common.Channel;
import com.ithinkrok.msm.common.MinecraftClientInfo;

import java.util.UUID;

/**
 * Created by paul on 04/02/16.
 */
public interface Client {

    MinecraftClientInfo getMinecraftServerInfo();

    Channel getChannel(String protocol);

    boolean changePlayerServer(UUID playerUUID, String serverName);
}
