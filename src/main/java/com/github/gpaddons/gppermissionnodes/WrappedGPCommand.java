package com.github.gpaddons.gppermissionnodes;

import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.TextMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Wraps GriefPrevention trust and bonus commands to restore {@code [permission]} handling.
 * Non-permission args delegate to the original core executor unchanged.
 */
final class WrappedGPCommand implements CommandExecutor
{
    private final CommandExecutor original;
    private final GPPermissionNodes plugin;

    WrappedGPCommand(CommandExecutor original, GPPermissionNodes plugin)
    {
        this.original = original;
        this.plugin = plugin;
    }

    CommandExecutor getOriginal()
    {
        return original;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args)
    {
        String name = cmd.getName().toLowerCase();

        if (name.equals("trust") || name.equals("accesstrust") || name.equals("containertrust") || name.equals("managetrust") || name.equals("permissiontrust"))
        {
            if (args.length == 1 && sender instanceof Player player && PermissionTrust.isPermissionTarget(args[0]))
            {
                ClaimPermission level;
                if (name.equals("accesstrust")) level = ClaimPermission.Access;
                else if (name.equals("containertrust")) level = ClaimPermission.Container;
                else if (name.equals("managetrust") || name.equals("permissiontrust")) level = ClaimPermission.Manage;
                else level = ClaimPermission.Build;
                PermissionTrust.handleTrust(player, level, args[0]);
                return true;
            }
        }
        else if (name.equals("untrust"))
        {
            if (args.length == 1 && sender instanceof Player player
                    && !args[0].equals("all") && !args[0].equals("public")
                    && PermissionTrust.isPermissionTarget(args[0]))
            {
                PermissionTrust.handleUntrust(player, args[0]);
                return true;
            }
        }
        else if (name.equals("trustlist"))
        {
            if (sender instanceof Player player)
            {
                try
                {
                    if (PermissionTrust.handleTrustlistIfPermissionPresent(player))
                        return true;
                }
                catch (Exception e)
                {
                    plugin.getLogger().warning("Trustlist handling failed, delegating to core: " + e.getMessage());
                }
            }
        }
        else if (name.equals("adjustbonusclaimblocks"))
        {
            if (args.length == 2 && GroupBonusManager.isGroupArg(args[0]))
            {
                return handleGroupBonus(sender, args[0], args[1]);
            }
        }

        if (original != null)
            return original.onCommand(sender, cmd, label, args);
        return false;
    }

    private boolean handleGroupBonus(CommandSender sender, String bracketed, String amountArg)
    {
        String permission = bracketed.substring(1, bracketed.length() - 1);
        if (permission.isEmpty())
        {
            String msg = "Please specify a player name, or a permission in [brackets].";
            if (sender instanceof Player player)
                GriefPrevention.sendMessage(player, TextMode.Err, msg);
            else
                sender.sendMessage(msg);
            return true;
        }

        int adjustment;
        try
        {
            adjustment = Integer.parseInt(amountArg);
        }
        catch (NumberFormatException e)
        {
            return false;
        }

        int newTotal = plugin.groupBonusManager().adjustGroupBonusBlocks(permission, adjustment);

        String msg = "Adjusted bonus claim blocks for players with the " + permission
                + " permission by " + adjustment + ".  New total: " + newTotal + ".";
        if (sender instanceof Player player)
            GriefPrevention.sendMessage(player, TextMode.Success, msg);
        else
            sender.sendMessage(msg);

        String actor = sender.getName();
        GriefPrevention.AddLogEntry(actor + " adjusted " + permission + "'s bonus claim blocks by " + adjustment + ".");

        plugin.groupBonusManager().syncAll();
        return true;
    }
}
