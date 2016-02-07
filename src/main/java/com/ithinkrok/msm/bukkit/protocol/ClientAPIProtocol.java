package com.ithinkrok.msm.bukkit.protocol;

import com.ithinkrok.msm.client.Client;
import com.ithinkrok.msm.client.ClientListener;
import com.ithinkrok.msm.common.Channel;
import com.ithinkrok.msm.common.util.ConfigUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;

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

    private final Map<String, CommandInfo> commandMap = new HashMap<>();

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
        String mode = payload.getString("mode");
        if(mode == null) return;

        switch(mode) {
            case "Broadcast":
                plugin.getServer().broadcastMessage(payload.getString("message"));
                return;
            case "Message":
                handleMessage(payload);
                return;
            case "RegisterCommands":
                handleRegisterCommands(payload);
        }
    }

    private void handleRegisterCommands(ConfigurationSection payload) {
        for(ConfigurationSection commandInfoConfig : ConfigUtils.getConfigList(payload, "commands")) {
            CommandInfo commandInfo = new CommandInfo(commandInfoConfig);

            commandMap.put(commandInfo.name, commandInfo);
        }
    }

    private void handleMessage(ConfigurationSection payload) {
        List<String> recipients = payload.getStringList("recipients");

        String message = payload.getString("message");

        for(String uuidString : recipients) {
            UUID uuid = UUID.fromString(uuidString);

            Player player = plugin.getServer().getPlayer(uuid);
            if(player == null) continue;

            player.sendMessage(message);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if(event.isCancelled()) return;

        String command = event.getMessage().split(" ")[0].toLowerCase();

        CommandInfo commandInfo = commandMap.get(command);

        if(commandInfo == null) return;

        if(!event.getPlayer().hasPermission(commandInfo.permission)) return;

        event.setCancelled(true);

        ConfigurationSection payload = new MemoryConfiguration();

        payload.set("player", event.getPlayer().getUniqueId().toString());
        payload.set("command", event.getMessage());
        payload.set("mode", "PlayerCommand");

        channel.write(payload);
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

    private static final class CommandInfo {
        final String name;
        final String usage;
        final String description;
        final String permission;

        private CommandInfo(ConfigurationSection config) {
            name = config.getString("name");
            usage = config.getString("usage");
            description = config.getString("description");
            permission = config.getString("permission");
        }
    }
}
