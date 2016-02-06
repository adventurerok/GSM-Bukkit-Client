package com.ithinkrok.msm.bukkit.protocol;

import com.ithinkrok.msm.client.Client;
import com.ithinkrok.msm.client.ClientListener;
import com.ithinkrok.msm.common.Channel;
import com.ithinkrok.msm.common.util.FIleUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
                ConfigurationSection payload = new MemoryConfiguration();

                Collection<ConfigurationSection> pluginInfoList = new ArrayList<>();

                for (Path pluginPath : pluginPaths) {
                    ConfigurationSection pluginInfo = loadPluginInfo(pluginPath);
                    if (pluginInfo == null) continue;

                    pluginInfoList.add(pluginInfo);
                }

                payload.set("plugins", pluginInfoList);

                //Mode = "PluginInfo", send info about all available plugins
                payload.set("mode", "PluginInfo");

                channel.write(payload);
            } catch (IOException | InvalidConfigurationException e) {
                e.printStackTrace();
            }

        });
    }

    @Override
    public void connectionClosed(Client client) {

    }

    public void scheduleRestart() {
        plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if(plugin.getServer().getOnlinePlayers().size() > 1) return;

            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "restart");
        }, 20, 1200);
    }

    @Override
    public void packetRecieved(Client client, Channel channel, ConfigurationSection payload) {

        String mode = payload.getString("mode");
        if(mode == null) return;

        switch(mode) {
            case "PluginInstall":
                handlePluginInstall(payload);
                break;
        }
    }

    private void handlePluginInstall(ConfigurationSection payload) {
        String name = payload.getString("name");

        byte[] updateBytes = (byte[]) payload.get("bytes");

        Path oldPath = pluginNameToPathMap.get(name);
        Path savePath;

        if(oldPath != null) {
            Path updatesDirectory = pluginDirectory.resolve("update");
            if(!Files.exists(updatesDirectory)) {
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

        if(!append) System.out.println("Updating plugin \"" + name + "\"");

        try {
            if(append) Files.write(savePath, updateBytes, StandardOpenOption.APPEND);
            else Files.write(savePath, updateBytes);
        } catch (IOException e) {
            System.out.println("Error while saving plugin update for plugin: " + name);
            e.printStackTrace();
        }

        scheduleRestart();
    }

    private ConfigurationSection loadPluginInfo(Path pluginPath) throws IOException, InvalidConfigurationException {
        ConfigurationSection result = new MemoryConfiguration();

        try (FileSystem jarFile = FIleUtil.createZipFileSystem(pluginPath)) {

            Path pluginYmlPath = jarFile.getPath("/plugin.yml");

            if (!Files.exists(pluginYmlPath)) return null;

            YamlConfiguration pluginYml = new YamlConfiguration();

            try (Reader reader = Files.newBufferedReader(pluginYmlPath)) {
                pluginYml.load(reader);
            }

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
