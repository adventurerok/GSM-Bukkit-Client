package com.ithinkrok.msm.bukkit.protocol;

import com.ithinkrok.msm.client.protocol.ClientUpdateFileProtocol;
import com.ithinkrok.util.FIleUtil;
import com.ithinkrok.util.config.Config;
import com.ithinkrok.util.config.MemoryConfig;
import com.ithinkrok.util.config.YamlConfigIO;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by paul on 04/02/16.
 */
public class ClientAutoUpdateProtocol extends ClientUpdateFileProtocol {

    private final Map<String, Path> pluginNameToPathMap = new ConcurrentHashMap<>();

    private final Plugin plugin;

    private final ClientAPIProtocol apiProtocol;

    private boolean restarting = false;

    public ClientAutoUpdateProtocol(Plugin plugin, ClientAPIProtocol apiProtocol) {
        this(plugin, Paths.get("plugins"), apiProtocol);
    }

    public ClientAutoUpdateProtocol(Plugin plugin, Path pluginDirectory, ClientAPIProtocol apiProtocol) {
        super(true, pluginDirectory);

        this.plugin = plugin;
        this.apiProtocol = apiProtocol;
    }

    @Override
    protected String getResourceName(Path path) {
        if (!path.getFileName().toString().toLowerCase().endsWith(".jar")) {
            return "other:" + super.getResourceName(path);
        }

        try (FileSystem jarFile = FIleUtil.createZipFileSystem(path)) {

            Path pluginYmlPath = jarFile.getPath("/plugin.yml");

            if (!Files.exists(pluginYmlPath)) return null;

            Config pluginYml = YamlConfigIO.loadToConfig(pluginYmlPath, new MemoryConfig('/'));

            String name = pluginYml.getString("name");
            if (name == null) return null;

            //Put the plugin name -> path combination in the lookup.
            pluginNameToPathMap.put(name, path);

            return "plugin:" + name;
        } catch (IOException ignored) {
            return null;
        }
    }

    @Override
    protected boolean updateResource(String name, byte[] update) {
        if (!super.updateResource(name, update)) return false;

        scheduleRestart();
        return true;
    }

    @Override
    protected Path getResourcePath(String name) {
        if(name.startsWith("other:")) return super.getResourcePath(name.substring(6));
        else if(!name.startsWith("plugin:")) return null;

        name = name.substring(7);

        Path oldPath = pluginNameToPathMap.get(name);
        Path savePath;

        if (oldPath != null) {
            Path updatesDirectory = basePath.resolve("update");
            if (!Files.exists(updatesDirectory)) {
                try {
                    Files.createDirectory(updatesDirectory);
                } catch (IOException e) {
                    System.err.println("Failed to create \"plugins/updates\" directory");
                    e.printStackTrace();
                    return null;
                }
            }

            savePath = updatesDirectory.resolve(oldPath.getFileName());
        } else savePath = basePath.resolve(name + ".jar");

        return savePath;
    }

    public void scheduleRestart() {
        apiProtocol.scheduleRestart(20, 0);
    }


}
