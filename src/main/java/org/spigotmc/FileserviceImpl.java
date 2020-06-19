package org.spigotmc;

import com.google.common.collect.Lists;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class FileserviceImpl implements Fileservice {

    @Override
    public void setupPluginDir(final File f) {
        f.mkdir();
        final File file = new File(Bukkit.getServer().getPluginManager().getPlugin("SerialkillerPlugin").getDataFolder()+"/config.yml");
        try {
            file.createNewFile();
            final FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            config.set("EnablePlugin", true);
            config.set("World", "world");
            config.set("ForrestRegionName", "serialkiller_region");
            config.set("killer.name", "SkeletonKing");
            config.set("killer.spawnChance", 15);
            config.set("killer.spawnRadius", 20);
            config.set("TimeOfDayToStartEvent", 14000);
            config.set("TimeOfDayToStopEvent", 22000);
            config.set("SecondsBetweenEventChecks", 20);
            config.set("respawnpoint.posX", null);
            config.set("respawnpoint.posY", null);
            config.set("respawnpoint.posZ", null);
            config.save(file);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void saveToFile(final FileConfiguration config) {
        try {
            config.save(new File(Bukkit.getServer().getPluginManager().getPlugin("SerialkillerPlugin").getDataFolder()+"/config.yml"));
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

}
