package com.ithinkrok.msm.bukkit.protocol;

import com.ithinkrok.msm.client.Client;
import com.ithinkrok.msm.client.ClientListener;
import com.ithinkrok.msm.common.Channel;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by paul on 07/02/16.
 */
public class ClientAPIProtocol implements ClientListener, Listener {

    private final Plugin plugin;

    public ClientAPIProtocol(Plugin plugin) {
        this.plugin = plugin;
    }

    private Client client;
    private Channel channel;

    @Override
    public void connectionOpened(Client client, Channel channel) {
        this.client = client;
        this.channel = channel;

        List<ConfigurationSection> playerConfigs = new ArrayList<>();

        for(Player player : plugin.getServer().getOnlinePlayers()) {
            playerConfigs.add(createPlayerConfig(player));
        }

        ConfigurationSection payload = new MemoryConfiguration();

        payload.set("players", playerConfigs);
        payload.set("mode", "PlayerInfo");

        channel.write(payload);
    }

    @Override
    public void connectionClosed(Client client) {
        this.client = null;
        this.channel = null;
    }

    @Override
    public void packetRecieved(Client client, Channel channel, ConfigurationSection payload) {

    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        ConfigurationSection payload = createPlayerConfig(event.getPlayer());

        payload.set("mode", "PlayerJoin");

        channel.write(payload);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        ConfigurationSection payload = new MemoryConfiguration();

        payload.set("uuid", event.getPlayer().getUniqueId().toString());

        payload.set("mode", "PlayerQuit");

        channel.write(payload);
    }

    private ConfigurationSection createPlayerConfig(Player player) {
        ConfigurationSection config = new MemoryConfiguration();

        config.set("uuid", player.getUniqueId().toString());
        config.set("name", player.getName());

        return config;
    }
}
