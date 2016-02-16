package com.ithinkrok.msm.bukkit.protocol;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.ithinkrok.msm.bukkit.util.ResourceUsage;
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
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Created by paul on 07/02/16.
 */
public class ClientAPIProtocol implements ClientListener, Listener {

    private final Plugin plugin;
    private final Map<String, CommandInfo> commandMap = new HashMap<>();
    private Client client;
    private Channel channel;
    private final ResourceUsage resourceUsageTracker = new ResourceUsage();

    public ClientAPIProtocol(Plugin plugin) {
        this.plugin = plugin;

        plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, resourceUsageTracker, 1,
                resourceUsageTracker.getTickInterval());
    }

    @Override
    public void connectionOpened(Client client, Channel channel) {
        this.client = client;
        this.channel = channel;

        resourceUsageTracker.setChannel(channel);

        runOnMainThread(() -> {
            List<Config> playerConfigs = new ArrayList<>();

            for (Player player : plugin.getServer().getOnlinePlayers()) {
                playerConfigs.add(createPlayerConfig(player));
            }

            Config payload = new MemoryConfig();

            payload.set("players", playerConfigs);
            payload.set("mode", "PlayerInfo");

            channel.write(payload);
        });
    }

    @Override
    public void connectionClosed(Client client) {
        this.client = null;
        this.channel = null;

        resourceUsageTracker.setChannel(null);
    }

    @Override
    public void packetRecieved(Client client, Channel channel, Config payload) {
        String mode = payload.getString("mode");
        if (mode == null) return;

        switch (mode) {
            case "Broadcast":
                String message = convertAmpersandToSelectionCharacter(payload.getString("message"));

                runOnMainThread(() -> plugin.getServer().broadcastMessage(message));
                return;
            case "Message":
                handleMessage(payload);
                return;
            case "RegisterCommands":
                handleRegisterCommands(payload);
                return;
            case "RegisterPermissions":
                handleRegisterPermissions(payload);
                return;
            case "ChangeServer":
                handleChangeServer(payload);
        }
    }

    private String convertAmpersandToSelectionCharacter(String message) {
        return message.replace('&', '§');
    }

    private void handleMessage(Config payload) {
        List<String> recipients = payload.getStringList("recipients");

        String message = convertAmpersandToSelectionCharacter(payload.getString("message"));

        runOnMainThread(() -> {
            for (String uuidString : recipients) {
                UUID uuid = UUID.fromString(uuidString);

                Player player = plugin.getServer().getPlayer(uuid);
                if (player == null) continue;

                player.sendMessage(message);
            }
        });
    }

    private void handleRegisterCommands(Config payload) {
        for (Config commandInfoConfig : payload.getConfigList("commands")) {
            CommandInfo commandInfo = new CommandInfo(commandInfoConfig);

            commandMap.put(commandInfo.name, commandInfo);

            for (String alias : commandInfo.aliases) {
                commandMap.put(alias, commandInfo);
            }
        }
    }

    private void handleRegisterPermissions(Config payload) {
        runOnMainThread(() -> {
            List<String> addedPermissions = new ArrayList<>();

            for (Config permissionInfoConfig : payload.getConfigList("permissions")) {
                String name = permissionInfoConfig.getString("name");
                String description = permissionInfoConfig.getString("description");

                PermissionDefault permissionDefault =
                        PermissionDefault.getByName(permissionInfoConfig.getString("default"));

                Map<String, Boolean> children = new HashMap<>();

                Config childrenConfig = permissionInfoConfig.getConfigOrEmpty("children");

                for (String childName : childrenConfig.getKeys(false)) {
                    children.put(childName, childrenConfig.getBoolean(childName));
                }

                Permission permission = new Permission(name, description, permissionDefault, children);

                addPermission(permission);

                addedPermissions.add(name);
            }

            for (String permissionName : addedPermissions) {
                Permission permission = plugin.getServer().getPluginManager().getPermission(permissionName);

                if (permission.getChildren().isEmpty()) continue;

                permission.recalculatePermissibles();
            }
        });
    }

    private void handleChangeServer(Config payload) {
        if (!client.getMinecraftServerInfo().hasBungee()) return;

        UUID playerUUID = UUID.fromString(payload.getString("player"));
        String target = payload.getString("target");

        Player player = plugin.getServer().getPlayer(playerUUID);
        if (player == null) return;

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(target);

        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        });
    }

    private void addPermission(Permission permission) {
        Permission old = plugin.getServer().getPluginManager().getPermission(permission.getName());

        if (old == null) {
            plugin.getServer().getPluginManager().addPermission(permission);
            return;
        }

        old.setDescription(permission.getDescription());

        old.setDefault(permission.getDefault());

        Map<String, Boolean> oldChildren = old.getChildren();
        oldChildren.clear();
        oldChildren.putAll(permission.getChildren());
    }

    private void runOnMainThread(Runnable runnable) {
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, runnable);
    }

    private Config createPlayerConfig(Player player) {
        Config config = new MemoryConfig();

        config.set("uuid", player.getUniqueId().toString());
        config.set("name", player.getName());

        return config;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;

        String command = event.getMessage().split(" ")[0].toLowerCase().substring(1);

        CommandInfo commandInfo = commandMap.get(command);

        if (commandInfo == null) return;

        String perm = commandInfo.permission;
        if (perm != null && !perm.isEmpty() && !event.getPlayer().hasPermission(perm)) return;

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

    private static final class CommandInfo {
        final String name;
        final String usage;
        final String description;
        final String permission;
        final List<String> aliases;

        private CommandInfo(Config config) {
            name = config.getString("name");
            usage = config.getString("usage");
            description = config.getString("description");
            permission = config.getString("permission");
            aliases = config.getStringList("aliases");
        }
    }
}
