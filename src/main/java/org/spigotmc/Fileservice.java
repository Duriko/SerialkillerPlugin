package org.spigotmc;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

public interface Fileservice  {

    void setupPluginDir(File f);

    void saveToFile(FileConfiguration fileConfiguration);

}
