package com.ithinkrok.msm.bukkit.util;

import com.ithinkrok.util.config.Config;
import com.ithinkrok.util.config.MemoryConfig;
import org.apache.commons.lang.Validate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.*;

/**
 * Created by paul on 09/02/16.
 */
public class BukkitConfig implements Config {

    private final ConfigurationSection bukkitConfig;
    private final char separator;

    public BukkitConfig() {
        this(new MemoryConfiguration());
    }

    public BukkitConfig(ConfigurationSection baseConfig) {
        this.bukkitConfig = baseConfig;
        this.separator = baseConfig.getRoot().options().pathSeparator();
    }

    public BukkitConfig(Config copyFrom) {
        this.bukkitConfig = toBukkitConfig(copyFrom);
        this.separator = copyFrom.getSeparator();
    }


    private static ConfigurationSection toBukkitConfig(Config copyFrom) {
        if (copyFrom instanceof BukkitConfig) return ((BukkitConfig) copyFrom).getBukkitConfig();

        ConfigurationSection copyTo = new MemoryConfiguration();

        Map<String, Object> values = copyFrom.getValues(true);

        for (Map.Entry<String, Object> valueEntry : values.entrySet()) {
            Object value = valueEntry.getValue();

            if (value instanceof Collection<?>) {
                List<Object> bukkitList = new ArrayList<>();

                for (Object listItem : (Iterable<?>) value) {
                    if (listItem instanceof Config) {
                        bukkitList.add(toBukkitConfig((Config) listItem));
                    } else bukkitList.add(listItem);
                }

                value = bukkitList;
            }

            copyTo.set(valueEntry.getKey(), value);
        }

        return copyTo;
    }

    public ConfigurationSection getBukkitConfig() {
        return bukkitConfig;
    }

    @Override
    public Map<String, Object> getValues(boolean deep) {
        Map<String, Object> result = new HashMap<>();

        for (String key : getKeys(deep)) {
            result.put(key, get(key));
        }

        return result;
    }

    @Override
    public boolean contains(String path) {
        return bukkitConfig.contains(path);
    }

    @Override
    public void set(String path, Object value) {
        if (value instanceof Config) {
            value = toBukkitConfig((Config) value);
        } else if (value instanceof Collection<?>) {
            List<Object> list = new ArrayList<>();

            for (Object obj : (Iterable<?>) value) {
                if (obj instanceof Config) {
                    list.add(toBukkitConfig((Config) obj));
                } else {
                    list.add(obj);
                }
            }

            value = list;
        }

        bukkitConfig.set(path, value);
    }

    @Override
    public Set<String> getKeys(boolean deep) {
        return bukkitConfig.getKeys(deep);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object get(String path, Object def) {
        Validate.notNull(path, "path cannot be null");

        if (path.isEmpty()) return this;

        Object obj = bukkitConfig.get(path, def);

        if (obj instanceof ConfigurationSection) {
            return new BukkitConfig((ConfigurationSection) obj);
        } else if (obj instanceof List<?>) {
            List<Object> result = new ArrayList<>();

            for (Object listItem : (Iterable<?>) obj) {
                if (listItem instanceof ConfigurationSection) {
                    result.add(new BukkitConfig((ConfigurationSection) listItem));
                } else if (listItem instanceof Map<?, ?>) {
                    result.add(new MemoryConfig((Map<String, Object>) listItem));
                } else {
                    result.add(listItem);
                }
            }

            return result;
        } else {
            return obj;
        }
    }

    @Override
    public char getSeparator() {
        return separator;
    }

    @Override
    public Config getConfigOrEmpty(String path) {
        Config config = getConfigOrNull(path);

        return config != null ? config : new MemoryConfig();
    }
}
