package org.spigotmc;

import com.google.common.collect.Lists;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.adapters.AbstractPlayer;
import io.lumine.xikage.mythicmobs.mobs.ActiveMob;
import org.apache.commons.lang.StringUtils;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

public final class SerialkillerPlugin extends JavaPlugin implements Listener {

    private Fileservice fileservice;
    private ConfigurationSection config;

    private static boolean pluginEnabled;
    private static World world;
    private static String regionName;
    private static int chance;
    private static int timeToStartEvent;
    private static int timeToStopEvent;
    private static int secondsBetweenEvents;
    private static String killerName;
    private static int radius;

    private static MythicMobs mythicMobs;
    private static RegionContainer regionContainer;
    private static RegionManager  regionManager;

    private boolean eventIsRunning = false;
    private Player targetPlayer = null;
    private UUID killerUuid = null;
    private ActiveMob activeKiller = null;
    private Location playerRespawnPoint;

    public static String PREFIX = new StringBuilder(ChatColor.YELLOW+"[").append(ChatColor.GREEN).append("Serialkiller").append(ChatColor.YELLOW).append("] ").append(ChatColor.GREEN).toString();

    @Override
    public void onEnable() {
        final Logger logger = getLogger();
        fileservice = new FileserviceImpl();
        final File f = new File(Bukkit.getServer().getPluginManager().getPlugin("SerialkillerPlugin").getDataFolder() + "/");
        if(!f.exists()) {
            fileservice.setupPluginDir(f);
            logger.info("Created plugin directories and files");
        }
        else
            logger.info("Loaded plugin directories");
        loadConfig();
        mythicMobs = MythicMobs.inst();
        logger.info("Loaded configuration!");
        regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
        regionManager = regionContainer.get(BukkitAdapter.adapt(world));
        Bukkit.getServer().getPluginManager().registerEvents(this, this);
        start();
    }

    private void loadConfig() {
        config = getConfig();
        pluginEnabled = config.getBoolean("EnablePlugin");
        world = Bukkit.getWorld( config.getString("World"));
        regionName = config.getString("ForrestRegionName");
        killerName = config.getString("killer.name");
        radius = config.getInt("killer.spawnRadius");
        chance = config.getInt("killer.spawnChance");
        timeToStartEvent = config.getInt("TimeOfDayToStartEvent");
        timeToStopEvent = config.getInt("TimeOfDayToStopEvent");
        secondsBetweenEvents = config.getInt("SecondsBetweenEventChecks");
        playerRespawnPoint = new Location(world, config.getDouble("respawnpoint.posX"), config.getDouble("respawnpoint.posY"), config.getDouble("respawnpoint.posZ"));
    }

    public void start() {
        new BukkitRunnable() {
            public void run() {
                if(!eventIsRunning) {
                    long currentTime = world.getTime();
                    if(currentTime > timeToStartEvent && currentTime < timeToStopEvent) {
                        if(new Random().nextInt(100) < chance) {
                            List<Player> playersInForrest = Lists.newArrayList();
                            world.getPlayers().stream().filter(p -> regionManager.getRegion(regionName).contains(BlockVector3.at(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ())) ).forEach(playersInForrest::add);
                            if(!playersInForrest.isEmpty()) {
                                for(int i = 0; i<playersInForrest.size(); i++) {
                                    targetPlayer = playersInForrest.get(new Random().nextInt(playersInForrest.size()));
                                    if(regionManager.getRegion(regionName).contains(BlockVector3.at(targetPlayer.getLocation().getX(), targetPlayer.getLocation().getY(), targetPlayer.getLocation().getZ()))) {
                                        List<Location> location = Lists.newArrayList();
                                        double height = world.getHighestBlockYAt(targetPlayer.getLocation().getBlockX(), targetPlayer.getLocation().getBlockZ() + 1); //Look above you
                                        int inc = 32;
                                        for (double rad = 0; rad < 2*Math.PI; rad += Math.PI / inc) {
                                            double x = radius*(Math.cos(rad));
                                            double z = radius*(Math.sin(rad));
                                            Location loc = new Location(targetPlayer.getWorld(), x + targetPlayer.getLocation().getX(), height, z + targetPlayer.getLocation().getZ());
                                            if(regionManager.getRegion(regionName).contains(BlockVector3.at(loc.getX(), loc.getY(), loc.getZ()))) {
                                                location.add(loc);
                                            }
                                        }
                                        if(!location.isEmpty()) {
                                            activeKiller = mythicMobs.getMobManager().spawnMob(killerName, location.get(new Random().nextInt(location.size())));
                                            killerUuid = activeKiller.getUniqueId();
                                            world.strikeLightningEffect(new Location(world, activeKiller.getLocation().getX(), activeKiller.getLocation().getY(), activeKiller.getLocation().getZ()));
                                            targetPlayer.sendMessage(ChatColor.GRAY + "You have a feeling that you are being followed...");
                                            List<AbstractPlayer> tmp = Lists.newArrayList();
                                            mythicMobs.getEntityManager().getPlayers().stream().filter(p -> p.getName().equals(targetPlayer.getName())).forEach(tmp::add);
                                            activeKiller.resetTarget();
                                            activeKiller.setTarget(tmp.get(0));
                                            eventIsRunning = true;
                                            break;
                                        }
                                    } else {
                                        playersInForrest.remove(targetPlayer);
                                    }
                                }
                            }
                        }
                    }
                }
                else {
                    if(mythicMobs.getMobManager().getActiveMobs().contains(activeKiller)) {
                        checkIfTargetIsAliveOrNearbyPlayers();
                    } else {
                        if(!activeKiller.isDead())
                            Bukkit.getEntity(activeKiller.getUniqueId()).remove();
                        killerUuid = null;
                        targetPlayer = null;
                        eventIsRunning = false;
                    }
                }
            }
        }.runTaskTimer(this, 0, secondsBetweenEvents*20);
    }

    @EventHandler
    public void onPlayerMove(final PlayerMoveEvent e) {
        final Player player = e.getPlayer();
        if(player.getWorld() == world) {
            if (targetPlayer == player) {
                if(activeKiller != null) {
                    if(!regionManager.getRegion(regionName).contains(BlockVector3.at(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ()))) {
                        if(!regionManager.getRegion(regionName).contains(BlockVector3.at(targetPlayer.getLocation().getX(), targetPlayer.getLocation().getY(), targetPlayer.getLocation().getZ()))) {
                            List<AbstractPlayer> nearbyPlayers = Lists.newArrayList(mythicMobs.getEntityManager().getPlayersInRangeSq(activeKiller.getLocation(), 10));
                            if(nearbyPlayers.isEmpty()) {
                                if(!activeKiller.isDead())
                                    Bukkit.getEntity(activeKiller.getUniqueId()).remove();
                                killerUuid = null;
                                targetPlayer = null;
                                eventIsRunning = false;
                            } else {
                                for(int i = 0; i<nearbyPlayers.size()+1; i++) {
                                    int index = new Random().nextInt(nearbyPlayers.size());
                                    AbstractPlayer tmpPlayer = nearbyPlayers.get(index);
                                    if(regionManager.getRegion(regionName).contains(BlockVector3.at(tmpPlayer.getLocation().getX(), tmpPlayer.getLocation().getY(), tmpPlayer.getLocation().getZ()))) {
                                        targetPlayer = ((Player) tmpPlayer.getBukkitEntity()).getPlayer();
                                        activeKiller.setTarget(tmpPlayer);
                                        break;
                                    } else {
                                        nearbyPlayers.remove(index);
                                    }
                                }
                                if(nearbyPlayers.isEmpty())
                                {
                                    if(!activeKiller.isDead())
                                        Bukkit.getEntity(activeKiller.getUniqueId()).remove();
                                    killerUuid = null;
                                    targetPlayer = null;
                                    eventIsRunning = false;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void checkIfTargetIsAliveOrNearbyPlayers() {
        if(!regionManager.getRegion(regionName).contains(BlockVector3.at(targetPlayer.getLocation().getX(), targetPlayer.getLocation().getY(), targetPlayer.getLocation().getZ()))) {
            List<AbstractPlayer> nearbyPlayers = Lists.newArrayList(mythicMobs.getEntityManager().getPlayersInRangeSq(activeKiller.getLocation(), 10));
            if(nearbyPlayers.isEmpty()) {
                Bukkit.getEntity(killerUuid).remove();
                killerUuid = null;
                targetPlayer = null;
                eventIsRunning = false;
            } else {
                for(int i = 0; i<nearbyPlayers.size()+1; i++) {
                    int index = new Random().nextInt(nearbyPlayers.size());
                    AbstractPlayer tmpPlayer = nearbyPlayers.get(index);
                    if(regionManager.getRegion(regionName).contains(BlockVector3.at(tmpPlayer.getLocation().getX(), tmpPlayer.getLocation().getY(), tmpPlayer.getLocation().getZ()))) {
                        targetPlayer = ((Player) tmpPlayer.getBukkitEntity()).getPlayer();
                        activeKiller.setTarget(tmpPlayer);
                        break;
                    } else {
                        nearbyPlayers.remove(index);
                    }
                }
                if(nearbyPlayers.isEmpty())
                {
                    if(!activeKiller.isDead())
                        Bukkit.getEntity(activeKiller.getUniqueId()).remove();
                    killerUuid = null;
                    targetPlayer = null;
                    eventIsRunning = false;
                }
            }
        }
    }

    @EventHandler
    public void EntityDamageByEntityEvent(EntityDamageByEntityEvent e) {
        if(eventIsRunning) {
            if(e.getDamager().getUniqueId() == activeKiller.getUniqueId()) {
                if (e.getEntity() instanceof Player && e.getEntity() == targetPlayer) {
                    //targetPlayer.teleport(playerRespawnPoint);
                    checkIfTargetIsAliveOrNearbyPlayers();
                }
            }
            if (e.getDamager() instanceof Player) {
                e.setCancelled(true);
            }
        }
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (label.equalsIgnoreCase("serialkiller")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if(player.hasPermission("serialkiller.admin")) {
                    if (args.length > 0 && args[0] != null) {
                        /**
                         * To get a list of admincommand use the folllowing command
                         * /serialkiller help
                         **/
                        if (args[0].equalsIgnoreCase("help")) {
                            player.sendMessage(ChatColor.GREEN + "--------------- " + PREFIX + ChatColor.GREEN + "---------------");
                            player.sendMessage(ChatColor.GREEN + "/serialkiller status " + ChatColor.YELLOW + "- Show current status and information.");
                            player.sendMessage(ChatColor.GREEN + "/serialkiller setRespawnPoint " + ChatColor.YELLOW + "- Set respawn point for players.");
                            player.sendMessage(ChatColor.GREEN + "/serialkiller setSpawnChance " + ChatColor.YELLOW + "- Set spawn chance for serialkiller.");
                            player.sendMessage(ChatColor.GREEN + "/serialkiller setKiller " + ChatColor.YELLOW + "- Set Mythic Mobs npc.");
                            player.sendMessage(ChatColor.GREEN + "/serialkiller setWorld " + ChatColor.YELLOW + "- Set world.");
                            player.sendMessage(ChatColor.GREEN + "/serialkiller setForrestRegion " + ChatColor.YELLOW + "- Set forrest region.");
                            player.sendMessage(ChatColor.GREEN + "/serialkiller setSecondsBetweenEvents " + ChatColor.YELLOW + "- Set seconds between events.");
                        }
                        if (args[0].equalsIgnoreCase("status")) {
                            String runningValue = eventIsRunning ? ChatColor.GREEN + "true" : ChatColor.RED + "false";
                            String worldValue = config.getString("World") != null ? ChatColor.GREEN + world.getName() : ChatColor.RED + "Not Set!";
                            String killerNameValue = StringUtils.isNotBlank(config.getString("killer.name")) ? ChatColor.GREEN + killerName : ChatColor.RED + "Not set!";
                            String spawnRadiusValue = StringUtils.isNotBlank(config.getString("killer.spawnRadius")) ? ChatColor.GREEN + "" + radius : ChatColor.RED + "Not set!";
                            String forrestRegionValue = StringUtils.isNotBlank(regionName) && regionManager.getRegion(regionName) != null ? ChatColor.GREEN + regionName : ChatColor.RED + "Not set!";
                            String respawnpointValue = config.isSet("respawnpoint") ? ChatColor.WHITE+ "[" + ChatColor.GREEN + Double.valueOf(playerRespawnPoint.getX()).intValue() +
                                    ChatColor.WHITE+ ", "+ ChatColor.GREEN + Double.valueOf(playerRespawnPoint.getY()).intValue() + ChatColor.WHITE + ", "+ ChatColor.GREEN+
                                    Double.valueOf(playerRespawnPoint.getZ()).intValue() + ChatColor.WHITE+"]" : ChatColor.RED + "Not set!";

                            player.sendMessage(ChatColor.YELLOW + "--------------- " + new StringBuilder(ChatColor.YELLOW+"[").append(ChatColor.GREEN).append("Serialkiller Status").append(ChatColor.YELLOW).append("] ").append(ChatColor.YELLOW).toString() + "---------------");
                            player.sendMessage(ChatColor.GREEN + "Running" + ChatColor.YELLOW + " : " + runningValue);
                            player.sendMessage(ChatColor.GREEN + "World" + ChatColor.YELLOW + " : " + worldValue);
                            player.sendMessage(ChatColor.GREEN + "Killer name" + ChatColor.YELLOW + " : " + killerNameValue);
                            player.sendMessage(ChatColor.GREEN + "Killer Spawn chance" + ChatColor.YELLOW + " : " + ChatColor.GREEN + chance + "%");
                            player.sendMessage(ChatColor.GREEN + "Killer spawnradius" + ChatColor.YELLOW + " : " + spawnRadiusValue);
                            player.sendMessage(ChatColor.GREEN + "Forrest Region" + ChatColor.YELLOW + " : " + forrestRegionValue);
                            player.sendMessage(ChatColor.GREEN + "Seconds between events" + ChatColor.YELLOW + " : " + ChatColor.GREEN + secondsBetweenEvents);
                            player.sendMessage(ChatColor.GREEN + "Player respawn point" + ChatColor.YELLOW + " : " + respawnpointValue);
                        }
                        if (args[0].equalsIgnoreCase("setRespawnpoint")) {
                            config.set("respawnpoint.posX" , player.getLocation().getX());
                            config.set("respawnpoint.posY" , player.getLocation().getY());
                            config.set("respawnpoint.posZ" , player.getLocation().getZ());
                            fileservice.saveToFile(getConfig());
                            playerRespawnPoint = new Location(world, config.getDouble("respawnpoint.posX"), config.getDouble("respawnpoint.posY"), config.getDouble("respawnpoint.posZ"));
                            player.sendMessage(PREFIX + "New respawn point set!");
                        }
                        if (args[0].equalsIgnoreCase("setSpawnChance")) {
                            if(args.length > 0 && args[1] != null) {
                                if(StringUtils.isNumeric(args[1])){
                                    int oldValue = config.getInt("killer.spawnChance");
                                    config.set("killer.spawnChance", Integer.valueOf(args[1]));
                                    fileservice.saveToFile(getConfig());
                                    chance = Integer.valueOf(args[1]);
                                    player.sendMessage(PREFIX + "Killer spawn chance changed from: " + oldValue + " to: " + chance + ".");
                                }
                            }
                        }
                        if (args[0].equalsIgnoreCase("setSpawnRadius")) {
                            if(args.length > 0 && args[1] != null) {
                                if(StringUtils.isNumeric(args[1])){
                                    int oldValue = config.getInt("killer.spawnRadius");
                                    config.set("killer.spawnRadius", Integer.valueOf(args[1]));
                                    fileservice.saveToFile(getConfig());
                                    chance = Integer.valueOf(args[1]);
                                    player.sendMessage(PREFIX + "Killer spawn radius changed from: " + oldValue + " to: " + chance + ".");
                                }
                            }
                        }
                        if (args[0].equalsIgnoreCase("setKiller")) {
                            if(args.length > 0 && args[1] != null) {
                                String oldValue = config.getString("killer.name");
                                config.set("killer.name", args[1]);
                                fileservice.saveToFile(getConfig());
                                killerName = args[1];
                                player.sendMessage(PREFIX + "Killer name changed from: " + oldValue + " to: " + killerName + ".");
                            }
                        }
                        if (args[0].equalsIgnoreCase("setForrestRegion")) {
                            if(args.length > 0 && args[1] != null) {
                                String oldValue = config.getString("ForrestRegionName");
                                config.set("ForrestRegionName", args[1]);
                                fileservice.saveToFile(getConfig());
                                regionName = args[1];
                                player.sendMessage(PREFIX + "Forrest region changed from: " + oldValue + " to: " + regionName + ".");
                            }
                        }
                        if (args[0].equalsIgnoreCase("setWorld")) {
                            if(args.length > 0 && args[1] != null) {
                                String oldValue = config.getString("World");
                                config.set("World", args[1]);
                                fileservice.saveToFile(getConfig());
                                world = Bukkit.getWorld(args[1]);
                                player.sendMessage(PREFIX + "World changed from: " + oldValue + " to: " + world + ".");
                            }
                        }
                        if (args[0].equalsIgnoreCase("setSecondsBetweenEvents")) {
                            if(args.length > 0 && args[1] != null) {
                                if(StringUtils.isNumeric(args[1])){
                                    int oldValue = config.getInt("SecondsBetweenEventChecks");
                                    config.set("SecondsBetweenEventChecks", Integer.valueOf(args[1]));
                                    fileservice.saveToFile(getConfig());
                                    secondsBetweenEvents = Integer.valueOf(args[1]);
                                    player.sendMessage(PREFIX + "Seconds between event changed from: " + oldValue + " to: " + secondsBetweenEvents + ".");
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete (CommandSender sender, Command cmd, String label, String[] args){
        List<String> commandList = Lists.newArrayList("help", "status", "setRespawnpoint", "setSpawnChance", "setSpawnRadius", "setKiller", "setForrestRegion", "setWorld", "setSecondsBetweenEvents");
        if(cmd.getName().equalsIgnoreCase("serialkiller") && args.length == 1){
            if (!args[0].equals("")) {
                List list = Lists.newArrayList();
                commandList.stream().filter(s -> s.startsWith(args[0])).forEach(list::add);
                return list;
            }
            else
                return commandList;
        }
        return Lists.newArrayList();
    }

    @Override
    public void onDisable() {
        if(!activeKiller.isDead())
            Bukkit.getEntity(activeKiller.getUniqueId()).remove();
        killerUuid = null;
        targetPlayer = null;
        eventIsRunning = false;
    }

}
