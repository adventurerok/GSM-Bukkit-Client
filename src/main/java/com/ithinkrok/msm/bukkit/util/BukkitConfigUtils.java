package com.ithinkrok.msm.bukkit.util;

import com.ithinkrok.util.config.Config;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by paul on 18/02/16.
 */
public class BukkitConfigUtils {

    public static Vector getVector(Config config, String path) {
        if(config.isString(path)) {

            String[] parts = config.getString(path).split(",");
            if(parts.length < 3) return null;

            try{
                double x = Double.parseDouble(parts[0].trim());
                double y = Double.parseDouble(parts[1].trim());
                double z = Double.parseDouble(parts[2].trim());

                return new Vector(x, y, z);
            } catch (NumberFormatException ignored) {
                return null;
            }

        } else if(config.isConfig(path)) {
            return config.getConfigOrEmpty(path).saveObjectFields(new Vector());
        } else return null;
    }

    public static Location getLocation(Config config, World world, String path) {
        if(config.isString(path)){

            String[] parts = config.getString(path).split(",");
            if(parts.length < 3) return null;

            try{
                double x = Double.parseDouble(parts[0].trim());
                double y = Double.parseDouble(parts[1].trim());
                double z = Double.parseDouble(parts[2].trim());

                if(parts.length < 5) return new Location(world, x, y, z);

                double yaw = Double.parseDouble(parts[3].trim());
                double pitch = Double.parseDouble(parts[4].trim());

                return new Location(world, x, y, z, (float) yaw, (float) pitch);
            } catch (NumberFormatException ignored) {
                return null;
            }

        } else if(config.isConfig(path)) {
            return config.getConfigOrEmpty(path).saveObjectFields(new Location(world, 0, 0, 0));
        } else return null;
    }

    public static List<Vector> getVectorList(Config config, String path) {
        List<Config> configList = config.getConfigList(path);

        List<Vector> result = new ArrayList<>();

        for(Config vectorConfig : configList) {
            result.add(vectorConfig.saveObjectFields(new Vector()));
        }

        return result;
    }
}
