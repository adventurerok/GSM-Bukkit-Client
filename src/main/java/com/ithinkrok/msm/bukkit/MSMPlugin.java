package com.ithinkrok.msm.bukkit;

import com.google.common.net.HostAndPort;
import com.ithinkrok.msm.bukkit.protocol.ClientAPIProtocol;
import com.ithinkrok.msm.bukkit.protocol.ClientAutoUpdateProtocol;
import com.ithinkrok.msm.client.ClientListener;
import com.ithinkrok.msm.client.impl.MSMClient;
import com.ithinkrok.msm.common.MinecraftServerInfo;
import com.ithinkrok.msm.common.MinecraftServerType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by paul on 03/02/16.
 */
public class MSMPlugin extends JavaPlugin implements PluginMessageListener {

    private MSMClient client;

    @Override
    public void onEnable() {
        ConfigurationSection config = getConfig();

        boolean hasBungee = config.getBoolean("this_server.has_bungee");

        if(hasBungee) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);
        }

        addDefaultProtocols();

        getServer().getScheduler().scheduleSyncDelayedTask(this, this::startMSMClient);
    }

    private void addDefaultProtocols() {
        addProtocol("MSMAutoUpdate", new ClientAutoUpdateProtocol(this));

        ClientAPIProtocol apiProtocol = new ClientAPIProtocol(this);
        getServer().getPluginManager().registerEvents(apiProtocol, this);
        addProtocol("MSMAPI", apiProtocol);
    }

    private void startMSMClient() {
        ConfigurationSection config = getConfig();

        String hostname = config.getString("hostname");
        int port = config.getInt("port", 30824);

        HostAndPort address = HostAndPort.fromParts(hostname, port);

        String serverName = config.getString("this_server.name");
        boolean hasBungee = config.getBoolean("this_server.has_bungee");

        MinecraftServerInfo serverInfo = getServerInfo(serverName, hasBungee);

        getLogger().info("Connecting to MSM Server at " + address);
        client = new MSMClient(address, serverInfo);

        client.start();

    }

    public static void addProtocol(String protocolName, ClientListener protocolListener) {
        MSMClient.addProtocol(protocolName, protocolListener);
    }

    private MinecraftServerInfo getServerInfo(String serverName, boolean hasBungee) {

        MinecraftServerType serverType;
        if (Bukkit.getVersion().toLowerCase().contains("spigot")) serverType = MinecraftServerType.SPIGOT;
        else serverType = MinecraftServerType.CRAFTBUKKIT;

        int maxPlayerCount = Bukkit.getMaxPlayers();

        List<String> plugins = new ArrayList<>();

        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            plugins.add(plugin.getName());
        }

        return new MinecraftServerInfo(serverType, serverName, hasBungee, maxPlayerCount, plugins);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {

    }
}
