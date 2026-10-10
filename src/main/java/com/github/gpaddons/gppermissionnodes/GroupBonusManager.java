package com.github.gpaddons.gppermissionnodes;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Restores group permission bonus blocks removed in {@code fdafe98}.
 * Totals live in this addon's config; effective bonus syncs as delta into
 * the player's real bonus blocks so core block checks keep working.
 */
final class GroupBonusManager
{
    private final GPPermissionNodes plugin;
    private final ConcurrentHashMap<String, Integer> permissionToBonus = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> grantedBonus = new ConcurrentHashMap<>();
    private File grantedFile;

    GroupBonusManager(GPPermissionNodes plugin)
    {
        this.plugin = plugin;
    }

    void load()
    {
        permissionToBonus.clear();
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("group-bonus-blocks");
        if (section != null)
        {
            for (String key : section.getKeys(false))
            {
                permissionToBonus.put(key, section.getInt(key, 0));
            }
        }

        grantedBonus.clear();
        grantedFile = new File(plugin.getDataFolder(), "granted.yml");
        if (grantedFile.exists())
        {
            YamlConfiguration granted = YamlConfiguration.loadConfiguration(grantedFile);
            for (String key : granted.getKeys(false))
            {
                try
                {
                    UUID id = UUID.fromString(key);
                    grantedBonus.put(id, granted.getInt(key, 0));
                }
                catch (IllegalArgumentException ignored) {}
            }
        }
    }

    void saveGroups()
    {
        FileConfiguration config = plugin.getConfig();
        config.set("group-bonus-blocks", null);
        ConfigurationSection section = config.createSection("group-bonus-blocks");
        for (Map.Entry<String, Integer> entry : permissionToBonus.entrySet())
        {
            section.set(entry.getKey(), entry.getValue());
        }
        plugin.saveConfig();
    }

    void saveGranted()
    {
        if (grantedFile == null)
            grantedFile = new File(plugin.getDataFolder(), "granted.yml");
        YamlConfiguration granted = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : grantedBonus.entrySet())
        {
            granted.set(entry.getKey().toString(), entry.getValue());
        }
        try
        {
            granted.save(grantedFile);
        }
        catch (IOException e)
        {
            plugin.getLogger().warning("Failed to save granted group bonuses: " + e.getMessage());
        }
    }

    static boolean isGroupArg(String arg)
    {
        return arg != null && arg.length() >= 3 && arg.startsWith("[") && arg.endsWith("]");
    }

    Map<String, Integer> snapshot()
    {
        return new HashMap<>(permissionToBonus);
    }

    int getGroupBonusBlocks(Player player)
    {
        long total = 0;
        for (Map.Entry<String, Integer> entry : permissionToBonus.entrySet())
        {
            try
            {
                if (player.hasPermission(entry.getKey()))
                    total += entry.getValue();
            }
            catch (Exception ignored) {}
        }
        if (total > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (total < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) total;
    }

    synchronized int adjustGroupBonusBlocks(String permission, int amount)
    {
        int current = permissionToBonus.getOrDefault(permission, 0);
        long next = (long) current + amount;
        int clamped;
        if (next > Integer.MAX_VALUE) clamped = Integer.MAX_VALUE;
        else if (next < Integer.MIN_VALUE) clamped = Integer.MIN_VALUE;
        else clamped = (int) next;
        permissionToBonus.put(permission, clamped);
        saveGroups();
        return clamped;
    }

    /** Sync one online player: apply delta between current group total and last granted. */
    void syncPlayer(Player player)
    {
        if (GriefPrevention.instance == null || GriefPrevention.instance.dataStore == null) return;
        UUID id = player.getUniqueId();
        int current = getGroupBonusBlocks(player);
        int last = grantedBonus.getOrDefault(id, 0);
        int delta = current - last;
        if (delta == 0) return;

        try
        {
            PlayerData data = GriefPrevention.instance.dataStore.getPlayerData(id);
            int bonus = data.getBonusClaimBlocks();
            long next = (long) bonus + delta;
            int clamped;
            if (next > Integer.MAX_VALUE) clamped = Integer.MAX_VALUE;
            else if (next < Integer.MIN_VALUE) clamped = Integer.MIN_VALUE;
            else clamped = (int) next;
            data.setBonusClaimBlocks(clamped);
            GriefPrevention.instance.dataStore.savePlayerData(id, data);
            grantedBonus.put(id, current);
            saveGranted();
        }
        catch (Exception e)
        {
            plugin.getLogger().warning("Failed to sync group bonus for " + player.getName() + ": " + e.getMessage());
        }
    }

    void syncAll()
    {
        for (Player player : Bukkit.getOnlinePlayers())
        {
            syncPlayer(player);
        }
    }

    /** Revert online players' granted bonuses, used on disable to avoid orphaned persistent bonus. */
    void revertOnline()
    {
        if (GriefPrevention.instance == null || GriefPrevention.instance.dataStore == null) return;
        for (Player player : Bukkit.getOnlinePlayers())
        {
            UUID id = player.getUniqueId();
            int last = grantedBonus.getOrDefault(id, 0);
            if (last == 0) continue;
            try
            {
                PlayerData data = GriefPrevention.instance.dataStore.getPlayerData(id);
                data.setBonusClaimBlocks(data.getBonusClaimBlocks() - last);
                GriefPrevention.instance.dataStore.savePlayerData(id, data);
            }
            catch (Exception e)
            {
                plugin.getLogger().warning("Failed to revert group bonus for " + player.getName() + ": " + e.getMessage());
            }
        }
        grantedBonus.clear();
        saveGranted();
    }
}
