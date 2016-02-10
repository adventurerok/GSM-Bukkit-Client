package com.ithinkrok.msm.bukkit.protocol;

import com.ithinkrok.msm.client.Client;
import com.ithinkrok.msm.client.ClientListener;
import com.ithinkrok.msm.common.Channel;
import com.ithinkrok.util.config.Config;
import com.ithinkrok.util.config.MemoryConfig;
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

        List<Config> playerConfigs = new ArrayList<>();

        for(Player player : plugin.getServer().getOnlinePlayers()) {
            playerConfigs.add(createPlayerConfig(player));
        }

        Config payload = new MemoryConfig();

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
    public void packetRecieved(Client client, Channel channel, Config payload) {
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

    private void handleRegisterCommands(Config payload) {
        for(Config commandInfoConfig : payload.getConfigList("commands")) {
            CommandInfo commandInfo = new CommandInfo(commandInfoConfig);

            commandMap.put(commandInfo.name, commandInfo);
        }
    }

    private void handleMessage(Config payload) {
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

        String command = event.getMessage().split(" ")[0].toLowerCase().substring(1);

        CommandInfo commandInfo = commandMap.get(command);

        if(commandInfo == null) return;

        String perm = commandInfo.permission;
        if(perm != null && !perm.isEmpty() && !event.getPlayer().hasPermission(perm)) return;

        event.setCancelled(true);

        Config payload = new MemoryConfig();

        payload.set("player", event.getPlayer().getUniqueId().toString());

        String fullCommand = event.getMessage().substring(1);
        payload.set("command", fullCommand);
        payload.set("mode", "PlayerCommand");

        channel.write(payload);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Config payload = createPlayerConfig(event.getPlayer());

        payload.set("mode", "PlayerJoin");

        channel.write(payload);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Config payload = new MemoryConfig();

        payload.set("uuid", event.getPlayer().getUniqueId().toString());

        payload.set("mode", "PlayerQuit");

        channel.write(payload);
    }

    private Config createPlayerConfig(Player player) {
        Config config = new MemoryConfig();

        config.set("uuid", player.getUniqueId().toString());
        config.set("name", player.getName());

        return config;
    }

    private static final class CommandInfo {
        final String name;
        final String usage;
        final String description;
        final String permission;

        private CommandInfo(Config config) {
            name = config.getString("name");
            usage = config.getString("usage");
            description = config.getString("description");
            permission = config.getString("permission");
        }
    }
}
