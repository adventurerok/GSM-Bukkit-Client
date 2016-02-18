package com.ithinkrok.msm.bukkit.protocol;

import com.ithinkrok.msm.client.Client;
import com.ithinkrok.msm.client.ClientListener;
import com.ithinkrok.msm.common.Channel;
import com.ithinkrok.util.FIleUtil;
import com.ithinkrok.util.config.Config;
import com.ithinkrok.util.config.InvalidConfigException;
import com.ithinkrok.util.config.MemoryConfig;
import com.ithinkrok.util.config.YamlConfigIO;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by paul on 04/02/16.
 */
public class ClientAutoUpdateProtocol implements ClientListener {

    private final Path pluginDirectory;

    private final Map<String, Path> pluginNameToPathMap = new ConcurrentHashMap<>();

    private final Plugin plugin;

    public ClientAutoUpdateProtocol(Plugin plugin) {
        this(plugin, Paths.get("plugins"));
    }

    public ClientAutoUpdateProtocol(Plugin plugin, Path pluginDirectory) {
        this.plugin = plugin;
        this.pluginDirectory = pluginDirectory;
    }

    @Override
    public void connectionOpened(Client client, Channel channel) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {

            try (DirectoryStream<Path> pluginPaths = Files.newDirectoryStream(pluginDirectory, "**.jar")) {
                Config payload = new MemoryConfig();

                Collection<Config> pluginInfoList = new ArrayList<>();

                for (Path pluginPath : pluginPaths) {
                    try {
                        Config pluginInfo = loadPluginInfo(pluginPath);
                        if (pluginInfo == null) continue;

                        pluginInfoList.add(pluginInfo);
                    } catch (InvalidConfigException e) {
                        System.err.println("Plugin " + pluginPath + " has an invalid configuration");
                        e.printStackTrace();
                    } catch (IOException e) {
                        System.err.println("IOException while polling plugin version: " + pluginPath);
                        e.printStackTrace();
                    }
                }

                payload.set("plugins", pluginInfoList);

                //Mode = "PluginInfo", send info about all available plugins
                payload.set("mode", "PluginInfo");

                channel.write(payload);
            } catch (IOException e) {
                System.err.println("Failed to iterate over plugin directory: " + pluginDirectory);
                e.printStackTrace();
            }

        });
    }

    @Override
    public void connectionClosed(Client client) {

    }

    @Override
    public void packetRecieved(Client client, Channel channel, Config payload) {

        String mode = payload.getString("mode");
        if (mode == null) return;

        switch (mode) {
            case "PluginInstall":
                handlePluginInstall(payload);
                break;
        }
    }

    private void handlePluginInstall(Config payload) {
        String name = payload.getString("name");

        byte[] updateBytes = (byte[]) payload.get("bytes");

        Path oldPath = pluginNameToPathMap.get(name);
        Path savePath;

        if (oldPath != null) {
            Path updatesDirectory = pluginDirectory.resolve("update");
            if (!Files.exists(updatesDirectory)) {
                try {
                    Files.createDirectory(updatesDirectory);
                } catch (IOException e) {
                    System.err.println("Failed to create \"plugins/updates\" directory");
                    e.printStackTrace();
                    return;
                }
            }

            savePath = updatesDirectory.resolve(oldPath.getFileName());
        } else savePath = pluginDirectory.resolve(name + ".jar");

        //Whether the packet is the first of this file or not
        boolean append = payload.getBoolean("append", false);

        if (!append) System.out.println("Updating plugin \"" + name + "\"");

        try {
            if (append) Files.write(savePath, updateBytes, StandardOpenOption.APPEND);
            else Files.write(savePath, updateBytes);
        } catch (IOException e) {
            System.out.println("Error while saving plugin update for plugin: " + name);
            e.printStackTrace();
        }

        scheduleRestart();
    }

    public void scheduleRestart() {
        plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (plugin.getServer().getOnlinePlayers().size() > 1) return;

            plugin.getServer()
                    .broadcastMessage(ChatColor.RED.toString() + ChatColor.BOLD.toString() + "Server restarting now" +
                            " for plugin" +
                            " updates");

            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin,
                    () -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "restart"), 100);


        }, 20, 1200);
    }

    private Config loadPluginInfo(Path pluginPath) throws IOException {
        Config result = new MemoryConfig();

        try (FileSystem jarFile = FIleUtil.createZipFileSystem(pluginPath)) {

            Path pluginYmlPath = jarFile.getPath("/plugin.yml");

            if (!Files.exists(pluginYmlPath)) return null;

            Config pluginYml = YamlConfigIO.loadToConfig(pluginYmlPath, new MemoryConfig('/'));

            String name = pluginYml.getString("name");
            if (name == null) return null;

            result.set("name", name);

            //Put the plugin name -> path combination in the lookup.
            pluginNameToPathMap.put(name, pluginPath);

            if (pluginYml.contains("version")) result.set("version", pluginYml.getString("version"));
        }

        FileTime dateModified = Files.getLastModifiedTime(pluginPath);
        result.set("modified", dateModified.toMillis());

        return result;
    }

}
