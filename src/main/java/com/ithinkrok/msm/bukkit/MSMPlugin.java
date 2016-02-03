package com.ithinkrok.msm.bukkit;

import com.google.common.net.HostAndPort;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Created by paul on 03/02/16.
 */
public class MSMPlugin extends JavaPlugin {

    private MSMClient client;

    @Override
    public void onEnable() {
        getServer().getScheduler().scheduleSyncDelayedTask(this, () -> {
            ConfigurationSection config = getConfig();

            String hostname = config.getString("hostname");
            int port = config.getInt("port", 30824);

            HostAndPort address = HostAndPort.fromParts(hostname, port);


            getLogger().info("Connecting to MSM Server at " + address);
            client = new MSMClient(address);

            client.start();
        });
    }
}
