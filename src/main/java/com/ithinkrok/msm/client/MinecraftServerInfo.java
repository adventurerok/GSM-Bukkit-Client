package com.ithinkrok.msm.client;

import com.ithinkrok.msm.common.MinecraftServerType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by paul on 06/02/16.
 * <p>
 * Represents
 */
public class MinecraftServerInfo {

    /**
     * The type of the minecraft server
     */
    private final MinecraftServerType type;

    /**
     * The name of this minecraft server.
     * Must be equal to the name in the bungeecord config for bungeecord network servers
     */
    private final String name;

    /**
     * If this minecraft server is in a bungeecord network
     */
    private final boolean hasBungee;

    /**
     * The maximum player count for this server. -1 indicates no maximum
     */
    private final int maxPlayerCount;

    /**
     * The list of plugins that the server has
     */
    private final List<String> plugins;

    public MinecraftServerInfo(MinecraftServerType type, String name, boolean hasBungee, int maxPlayerCount,
                               List<String> plugins) {
        this.type = type;
        this.name = name;
        this.hasBungee = hasBungee;
        this.maxPlayerCount = maxPlayerCount;
        this.plugins = plugins;
    }

    public MinecraftServerInfo(ConfigurationSection config) {

        name = config.getString("name");
        type = MinecraftServerType.valueOf(config.getString("type").toUpperCase());
        hasBungee = config.getBoolean("has_bungee");
        maxPlayerCount = config.getInt("max_players");
        plugins = config.getStringList("plugins");
    }

    public MinecraftServerType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public boolean hasBungee() {
        return hasBungee;
    }

    public int getMaxPlayerCount() {
        return maxPlayerCount;
    }

    public List<String> getPlugins() {
        return plugins;
    }

    public ConfigurationSection toConfig() {
        MemoryConfiguration config = new MemoryConfiguration();

        config.set("name", name);
        config.set("type", type.toString().toLowerCase());
        config.set("has_bungee", hasBungee);
        config.set("max_players", maxPlayerCount);
        config.set("plugins", new ArrayList<>(plugins));

        return config;
    }
}
