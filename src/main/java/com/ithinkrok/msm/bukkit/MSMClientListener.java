package com.ithinkrok.msm.bukkit;

import com.ithinkrok.msm.common.Packet;

/**
 * Created by paul on 03/02/16.
 */
public interface MSMClientListener {

    void packetRecieved(Packet packet);
}
