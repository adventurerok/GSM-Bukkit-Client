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
import java.util.List;

/**
 * Created by paul on 04/02/16.
 */
public class ClientAutoUpdateProtocol implements ClientListener {

    private final Path pluginDirectory = Paths.get("plugins");

    private final Plugin plugin;

    public ClientAutoUpdateProtocol(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void connectionOpened(Client client, Channel channel) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            try (DirectoryStream<Path> pluginPaths = Files.newDirectoryStream(pluginDirectory, "**.jar")) {
                ConfigurationSection payload = new MemoryConfiguration();

                List<ConfigurationSection> pluginInfoList = new ArrayList<>();

                for (Path pluginPath : pluginPaths) {
                    ConfigurationSection pluginInfo = loadPluginInfo(pluginPath);
                    if (pluginInfo == null) continue;

                    pluginInfoList.add(pluginInfo);
                }

                payload.set("plugins", pluginInfoList);
                payload.set("mode", "PluginInfo");

                channel.write(payload);
            } catch (IOException | InvalidConfigurationException e) {
                e.printStackTrace();
            }

        });
    }

    @Override
    public void connectionClosed(Client client, Channel channel) {

    }

    @Override
    public void packetRecieved(Client client, Channel channel, ConfigurationSection payload) {

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

            if (pluginYml.contains("version")) result.set("version", pluginYml.getString("version"));
        }

        FileTime dateModified = Files.getLastModifiedTime(pluginPath);
        result.set("modified", dateModified.toMillis());

        return result;
    }

}
