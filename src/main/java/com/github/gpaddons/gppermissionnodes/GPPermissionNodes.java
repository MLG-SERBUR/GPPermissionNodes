package com.github.gpaddons.gppermissionnodes;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Restores permission-node trust and group bonus blocks removed from GriefPrevention core.
 */
public class GPPermissionNodes extends JavaPlugin implements Listener
{
    private GroupBonusManager bonusManager;
    private final Map<String, WrappedGPCommand> wrappers = new HashMap<>();
    private int syncTaskId = -1;

    GroupBonusManager groupBonusManager()
    {
        return bonusManager;
    }

    @Override
    public void onEnable()
    {
        saveDefaultConfig();
        bonusManager = new GroupBonusManager(this);
        bonusManager.load();

        Bukkit.getPluginManager().registerEvents(new PermissionCheckListener(), this);
        Bukkit.getPluginManager().registerEvents(this, this);

        CommandExecutor self = this::onReloadCommand;
        PluginCommand own = getCommand("gppermissionnodes");
        if (own != null) own.setExecutor(self);

        wrapGPCommands();
        startSyncTask();

        // Catch up players already online during reload.
        Bukkit.getScheduler().runTask(this, () -> bonusManager.syncAll());
    }

    @Override
    public void onDisable()
    {
        if (syncTaskId != -1)
        {
            Bukkit.getScheduler().cancelTask(syncTaskId);
            syncTaskId = -1;
        }
        if (bonusManager != null)
        {
            bonusManager.revertOnline();
            bonusManager.saveGroups();
        }
        unwrapGPCommands();
    }

    private boolean onReloadCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args)
    {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload"))
        {
            reloadConfig();
            bonusManager.load();
            startSyncTask();
            bonusManager.syncAll();
            sender.sendMessage("GPPermissionNodes config reloaded.");
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event)
    {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> bonusManager.syncPlayer(player), 20L);
    }

    private void startSyncTask()
    {
        if (syncTaskId != -1)
        {
            Bukkit.getScheduler().cancelTask(syncTaskId);
            syncTaskId = -1;
        }
        long intervalSeconds = getConfig().getLong("sync-interval-seconds", 60);
        if (intervalSeconds <= 0) return;
        long ticks = intervalSeconds * 20L;
        syncTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> bonusManager.syncAll(), ticks, ticks);
    }

    private void wrapGPCommands()
    {
        if (GriefPrevention.instance == null)
        {
            getLogger().severe("GriefPrevention instance not available, permission wrappers disabled.");
            return;
        }
        String[] names = {"trust", "untrust", "accesstrust", "containertrust", "managetrust", "permissiontrust", "trustlist", "adjustbonusclaimblocks"};
        for (String name : names)
        {
            PluginCommand cmd;
            try
            {
                cmd = GriefPrevention.instance.getCommand(name);
            }
            catch (Exception e)
            {
                getLogger().warning("Failed to look up GP command /" + name + ": " + e.getMessage());
                continue;
            }
            if (cmd == null)
            {
                getLogger().warning("GP command /" + name + " not found, skipping wrapper.");
                continue;
            }
            CommandExecutor original = cmd.getExecutor();
            if (original instanceof WrappedGPCommand) continue;
            WrappedGPCommand wrapper = new WrappedGPCommand(original, this);
            cmd.setExecutor(wrapper);
            wrappers.put(name, wrapper);
        }
    }

    private void unwrapGPCommands()
    {
        if (GriefPrevention.instance == null) return;
        for (Map.Entry<String, WrappedGPCommand> entry : wrappers.entrySet())
        {
            try
            {
                PluginCommand cmd = GriefPrevention.instance.getCommand(entry.getKey());
                if (cmd != null && cmd.getExecutor() == entry.getValue())
                    cmd.setExecutor(entry.getValue().getOriginal());
            }
            catch (Exception e)
            {
                getLogger().warning("Failed to restore GP command /" + entry.getKey() + ": " + e.getMessage());
            }
        }
        wrappers.clear();
    }
}
