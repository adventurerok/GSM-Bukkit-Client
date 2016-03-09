package com.ithinkrok.msm.bukkit.protocol;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.ithinkrok.msm.bukkit.util.MSMCommandSender;
import com.ithinkrok.msm.bukkit.util.ResourceUsage;
import com.ithinkrok.msm.client.Client;
import com.ithinkrok.msm.client.ClientListener;
import com.ithinkrok.msm.client.command.ClientCommandInfo;
import com.ithinkrok.msm.common.Channel;
import com.ithinkrok.msm.common.command.CommandInfo;
import com.ithinkrok.util.StringUtils;
import com.ithinkrok.util.config.Config;
import com.ithinkrok.util.config.MemoryConfig;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
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

import java.time.Instant;
import java.util.*;

/**
 * Created by paul on 07/02/16.
 */
public class ClientAPIProtocol implements ClientListener, Listener {

    private final Plugin plugin;
    private final Map<String, CommandInfo> commandMap = new HashMap<>();
    private final ResourceUsage resourceUsageTracker = new ResourceUsage();
    private final Map<String, Set<String>> tabCompletionSets;
    private Client client;
    private Channel channel;

    public ClientAPIProtocol(Plugin plugin, Map<String, Set<String>> tabCompletionSets) {
        this.plugin = plugin;
        this.tabCompletionSets = tabCompletionSets;

        plugin.getServer().getScheduler()
                .scheduleSyncRepeatingTask(plugin, resourceUsageTracker, 1, resourceUsageTracker.getTickInterval());
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
            payload.set("mode", "ConnectInfo");

            List<Config> banConfigs = new ArrayList<>();

            for (BanEntry entry : plugin.getServer().getBanList(BanList.Type.NAME).getBanEntries()) {
                Config banConfig = createBanConfig(entry);

                banConfigs.add(banConfig);
            }

            payload.set("bans", banConfigs);

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
                handleBroadcast(payload);
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
                return;
            case "Kick":
                handleKick(payload);
                return;
            case "Ban":
                handleBan(payload);
                return;
            case "Unban":
                handleUnban(payload);
                return;
            case "ConsoleMessage":
                handleConsoleMessage(payload);
                return;
            case "ExecCommand":
                handleExecCommand(payload);
                return;
            case "TabSet":
                handleTabSet(payload);
                return;
            case "TabSets":
                handleTabSets(payload);
        }
    }

    private void handleTabSets(Config payload) {
        Config tabSets = payload.getConfigOrEmpty("tab_sets");

        for(String setName : tabSets.getKeys(false)) {
            Set<String> tabSet = new HashSet<>(tabSets.getStringList(setName));

            tabCompletionSets.put(setName, tabSet);
        }
    }

    private void handleBroadcast(Config payload) {
        String message = StringUtils.convertAmpersandToSelectionCharacter(payload.getString("message"));

        runOnMainThread(() -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.sendMessage(message);
            }
        });
    }

    private void handleMessage(Config payload) {
        List<String> recipients = payload.getStringList("recipients");

        String message = StringUtils.convertAmpersandToSelectionCharacter(payload.getString("message"));

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
            CommandInfo commandInfo = new ClientCommandInfo(commandInfoConfig);

            commandMap.put(commandInfo.getName(), commandInfo);

            for (String alias : commandInfo.getAliases()) {
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

    private void handleKick(Config payload) {
        UUID playerUUID = UUID.fromString(payload.getString("player"));

        Player player = plugin.getServer().getPlayer(playerUUID);
        if (player == null) return;

        String reason = payload.getString("reason");

        runOnMainThread(() -> {
            player.kickPlayer(reason);
        });
    }

    private void handleBan(Config payload) {
        UUID playerUUID = UUID.fromString(payload.getString("player"));

        Player player = plugin.getServer().getPlayer(playerUUID);
        if (player == null) return;

        String reason = payload.getString("reason");
        Instant expires = Instant.ofEpochMilli(payload.getLong("until"));
        String bannerName = payload.getString("banner_name");

        //Bukkit requires we use the old date time api
        @SuppressWarnings("UseOfObsoleteDateTimeApi")
        Date date = Date.from(expires);

        runOnMainThread(() -> {
            //Only supports banning players who have joined the server
            OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerUUID);
            if (offlinePlayer.getName() == null) return;

            BanList banList = plugin.getServer().getBanList(BanList.Type.NAME);

            banList.addBan(offlinePlayer.getName(), reason, date, bannerName);
        });
    }

    private void handleUnban(Config payload) {
        UUID playerUUID = UUID.fromString(payload.getString("player"));

        runOnMainThread(() -> {
            //If the name of the player is null then the player cannot be banned
            OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerUUID);
            if (offlinePlayer.getName() == null) return;

            BanList banList = plugin.getServer().getBanList(BanList.Type.NAME);

            banList.pardon(offlinePlayer.getName());
        });
    }

    private void handleConsoleMessage(Config payload) {
        String message = payload.getString("message");

        plugin.getServer().getConsoleSender().sendMessage(message);
    }

    private void handleExecCommand(Config payload) {
        String command = payload.getString("command");

        CommandSender sender;

        if (payload.getBoolean("console")) {
            sender = plugin.getServer().getConsoleSender();
        } else {
            sender = new MSMCommandSender(plugin.getServer(), channel, payload.getConfigOrEmpty("sender"));
        }

        runOnMainThread(() -> {
            plugin.getServer().dispatchCommand(sender, command);
        });

    }

    private void handleTabSet(Config payload) {
        String setName = payload.getString("set_name");

        Collection<String> change = payload.getStringList("change");

        Set<String> modifying = tabCompletionSets.get(setName);

        switch (payload.getString("modify_mode").toLowerCase()) {
            case "add":
                if (modifying == null) {
                    modifying = new HashSet<>();
                    tabCompletionSets.putIfAbsent(setName, modifying);
                    modifying = tabCompletionSets.get(setName);
                }

                modifying.addAll(change);
                return;
            case "remove":
                if (modifying == null) return;

                modifying.removeAll(change);

                if (modifying.isEmpty()) {
                    tabCompletionSets.remove(setName);
                }

                return;
            case "set":
                modifying = new HashSet<>(change);

                tabCompletionSets.put(setName, modifying);
        }
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

    private Config createBanConfig(BanEntry entry) {
        UUID uuid = plugin.getServer().getOfflinePlayer(entry.getTarget()).getUniqueId();

        Config banConfig = new MemoryConfig();
        banConfig.set("player", uuid.toString());
        banConfig.set("player_name", entry.getTarget());
        banConfig.set("banner_name", entry.getSource());
        banConfig.set("reason", entry.getReason());

        if (entry.getExpiration() == null) banConfig.set("until", Long.MAX_VALUE);
        else banConfig.set("until", entry.getExpiration().toInstant().toEpochMilli());

        banConfig.set("created", entry.getCreated().toInstant().toEpochMilli());
        return banConfig;
    }

    private void runAsync(Runnable runnable) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled() || channel == null) return;

        String command = event.getMessage().split(" ")[0].toLowerCase().substring(1);

        CommandInfo commandInfo = commandMap.get(command);

        if (commandInfo == null) return;

        String perm = commandInfo.getPermission();
        if (perm != null && !perm.isEmpty() && !event.getPlayer().hasPermission(perm)) return;

        event.setCancelled(true);

        Config payload = new MemoryConfig();

        payload.set("player", event.getPlayer().getUniqueId().toString());

        String fullCommand = event.getMessage().substring(1);
        payload.set("command", fullCommand);
        payload.set("mode", "PlayerCommand");

        channel.write(payload);
    }

    public void sendConsoleCommandPacket(String consoleCommand) {
        Config payload = new MemoryConfig();

        payload.set("command", consoleCommand);
        payload.set("mode", "ConsoleCommand");

        channel.write(payload);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (channel == null) return;

        Config payload = createPlayerConfig(event.getPlayer());

        payload.set("mode", "PlayerJoin");

        channel.write(payload);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (channel == null) return;

        Config payload = new MemoryConfig();

        payload.set("uuid", event.getPlayer().getUniqueId().toString());

        payload.set("mode", "PlayerQuit");

        channel.write(payload);
    }

}
