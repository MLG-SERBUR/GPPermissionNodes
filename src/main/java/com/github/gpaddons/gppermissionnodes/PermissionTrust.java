package com.github.gpaddons.gppermissionnodes;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.events.TrustChangedEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Restores pre-removal permission trust handling for {@code [permission.node]} entries.
 * Mirrors removed core logic from {@code 6b01dd2 Remove permission node trust}.
 */
final class PermissionTrust
{
    static final String INVALID_PERMISSION_ID = "Please specify a player name, or a permission in [brackets].";

    private PermissionTrust() {}

    static boolean isBracketed(String arg)
    {
        return arg != null && arg.length() >= 3 && arg.startsWith("[") && arg.endsWith("]");
    }

    /** True when arg should be treated as a permission, not a player. Caller must exclude public/all first. */
    static boolean isPermissionTarget(String arg)
    {
        if (arg == null) return false;
        if (arg.equals("public") || arg.equals("all")) return false;
        if (isBracketed(arg)) return true;
        if (!arg.contains(".")) return false;
        try
        {
            return GriefPrevention.instance.resolvePlayerByName(arg) == null;
        }
        catch (Exception e)
        {
            return true;
        }
    }

    static String trustEntryToPlayerName(String entry)
    {
        if (entry.startsWith("[") || entry.equals("public"))
        {
            return entry;
        }
        try
        {
            UUID id = UUID.fromString(entry);
            OfflinePlayer offline = Bukkit.getServer().getOfflinePlayer(id);
            String name = offline.getName();
            return name != null ? name : entry;
        }
        catch (IllegalArgumentException e)
        {
            return entry;
        }
    }

    /** Replicates removed {@code handleTrustCommand} permission branch. */
    static void handleTrust(Player player, ClaimPermission permissionLevel, String recipientName)
    {
        String permission = null;
        OfflinePlayer otherPlayer = null;
        UUID recipientID = null;

        if (recipientName.startsWith("[") && recipientName.endsWith("]"))
        {
            permission = recipientName.substring(1, recipientName.length() - 1);
            if (permission == null || permission.isEmpty())
            {
                GriefPrevention.sendMessage(player, TextMode.Err, INVALID_PERMISSION_ID);
                return;
            }
        }
        else
        {
            otherPlayer = GriefPrevention.instance.resolvePlayerByName(recipientName);
            boolean isPermissionFormat = recipientName.contains(".");
            if (otherPlayer == null && !recipientName.equals("public") && !recipientName.equals("all") && !isPermissionFormat)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return;
            }

            if (otherPlayer == null && isPermissionFormat)
            {
                permission = recipientName;
            }
            else if (otherPlayer != null)
            {
                recipientName = otherPlayer.getName();
                recipientID = otherPlayer.getUniqueId();
            }
            else
            {
                recipientName = "public";
            }
        }

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), true, null);

        ArrayList<Claim> targetClaims = new ArrayList<>();
        if (claim == null)
        {
            targetClaims.addAll(GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId()).getClaims());
        }
        else
        {
            if (claim.checkPermission(player, ClaimPermission.Manage, null) != null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, ClaimPermission.Manage.getDenialMessage(), claim.getOwnerName());
                return;
            }
            targetClaims.add(claim);
        }

        if (targetClaims.isEmpty())
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.GrantPermissionNoClaim);
            return;
        }

        String identifierToAdd = recipientName;
        if (permission != null)
        {
            identifierToAdd = "[" + permission + "]";
            recipientName = identifierToAdd;
        }
        else if (recipientID != null)
        {
            identifierToAdd = recipientID.toString();
        }

        TrustChangedEvent event = new TrustChangedEvent(player, targetClaims, permissionLevel, true, identifierToAdd);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        for (Claim currentClaim : event.getClaims())
        {
            currentClaim.setPermission(identifierToAdd, permissionLevel);
            GriefPrevention.instance.dataStore.saveClaim(currentClaim);
        }

        if (recipientName.equals("public"))
            recipientName = GriefPrevention.instance.dataStore.getMessage(Messages.CollectivePublic);

        String permissionDescription;
        if (permissionLevel == ClaimPermission.Manage)
            permissionDescription = "manage permissions";
        else if (permissionLevel == ClaimPermission.Build)
            permissionDescription = GriefPrevention.instance.dataStore.getMessage(Messages.BuildPermission);
        else if (permissionLevel == ClaimPermission.Access)
            permissionDescription = GriefPrevention.instance.dataStore.getMessage(Messages.AccessPermission);
        else
            permissionDescription = GriefPrevention.instance.dataStore.getMessage(Messages.ContainersPermission);

        String location;
        if (claim == null)
            location = GriefPrevention.instance.dataStore.getMessage(Messages.LocationAllClaims);
        else
            location = GriefPrevention.instance.dataStore.getMessage(Messages.LocationCurrentClaim);

        GriefPrevention.sendMessage(player, TextMode.Success, Messages.GrantPermissionConfirmation, recipientName, permissionDescription, location);
    }

    /** Replicates removed untrust permission branch for a single identifier. */
    static void handleUntrust(Player player, String arg)
    {
        // Normalize bare dotted permission to bracketed form, mirroring removed core.
        String idToDrop = arg;
        if (!isBracketed(arg) && arg.contains(".") && GriefPrevention.instance.resolvePlayerByName(arg) == null)
        {
            idToDrop = "[" + arg + "]";
        }

        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), true, null);

        if (claim == null)
        {
            TrustChangedEvent event = new TrustChangedEvent(
                    player,
                    GriefPrevention.instance.dataStore.getPlayerData(player.getUniqueId()).getClaims(),
                    null, false, idToDrop);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return;

            for (Claim targetClaim : event.getClaims())
            {
                targetClaim.dropPermission(idToDrop);
                GriefPrevention.instance.dataStore.saveClaim(targetClaim);
            }

            String display = arg.equals("public") ? "the public" : arg;
            // Preserve bracketed display for permissions.
            if (isBracketed(arg) || idToDrop.startsWith("["))
                display = idToDrop;
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.UntrustIndividualAllClaims, display);
        }
        else if (claim.checkPermission(player, ClaimPermission.Manage, null) != null)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, ClaimPermission.Manage.getDenialMessage(), claim.getOwnerName());
        }
        else
        {
            boolean targetIsManager = claim.getPermission(idToDrop) == ClaimPermission.Manage;
            if (targetIsManager && claim.checkPermission(player, ClaimPermission.Edit, null) != null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ManagersDontUntrustManagers, claim.getOwnerName());
                return;
            }

            TrustChangedEvent event = new TrustChangedEvent(player, claim, null, false, idToDrop);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return;

            event.getClaims().forEach(targetClaim -> targetClaim.dropPermission(event.getIdentifier()));

            String display = arg.equals("public") ? "the public" : arg;
            if (isBracketed(arg) || idToDrop.startsWith("["))
                display = idToDrop;
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.UntrustIndividualSingleClaim, display);
            GriefPrevention.instance.dataStore.saveClaim(claim);
        }
    }

    /**
     * Fixed trustlist that shows {@code [permission]} entries as-is.
     * Returns true when handled (permission entries present), false to delegate to core.
     */
    static boolean handleTrustlistIfPermissionPresent(Player player)
    {
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(player.getLocation(), true, null);
        if (claim == null) return false;

        ArrayList<String> builders = new ArrayList<>();
        ArrayList<String> containers = new ArrayList<>();
        ArrayList<String> accessors = new ArrayList<>();
        ArrayList<String> managers = new ArrayList<>();
        claim.getPermissions(builders, containers, accessors, managers);

        boolean hasPermissionEntry = false;
        for (String entry : builders)
            if (entry.startsWith("[")) hasPermissionEntry = true;
        for (String entry : containers)
            if (entry.startsWith("[")) hasPermissionEntry = true;
        for (String entry : accessors)
            if (entry.startsWith("[")) hasPermissionEntry = true;
        for (String entry : managers)
            if (entry.startsWith("[")) hasPermissionEntry = true;

        if (!hasPermissionEntry) return false;

        if (claim.checkPermission(player, ClaimPermission.Manage, null) != null)
        {
            GriefPrevention.sendMessage(player, TextMode.Err, ClaimPermission.Manage.getDenialMessage(), claim.getOwnerName());
            return true;
        }

        GriefPrevention.sendMessage(player, TextMode.Info, Messages.TrustListHeader, claim.getOwnerName());

        StringBuilder permissions = new StringBuilder();
        permissions.append(ChatColor.GOLD).append('>');
        for (String manager : managers)
            permissions.append(trustEntryToPlayerName(manager)).append(' ');
        player.sendMessage(permissions.toString());

        permissions = new StringBuilder();
        permissions.append(ChatColor.YELLOW).append('>');
        for (String builder : builders)
            permissions.append(trustEntryToPlayerName(builder)).append(' ');
        player.sendMessage(permissions.toString());

        permissions = new StringBuilder();
        permissions.append(ChatColor.GREEN).append('>');
        for (String container : containers)
            permissions.append(trustEntryToPlayerName(container)).append(' ');
        player.sendMessage(permissions.toString());

        permissions = new StringBuilder();
        permissions.append(ChatColor.BLUE).append('>');
        for (String accessor : accessors)
            permissions.append(trustEntryToPlayerName(accessor)).append(' ');
        player.sendMessage(permissions.toString());

        player.sendMessage(
                ChatColor.GOLD + GriefPrevention.instance.dataStore.getMessage(Messages.Manage) + " " +
                        ChatColor.YELLOW + GriefPrevention.instance.dataStore.getMessage(Messages.Build) + " " +
                        ChatColor.GREEN + GriefPrevention.instance.dataStore.getMessage(Messages.Containers) + " " +
                        ChatColor.BLUE + GriefPrevention.instance.dataStore.getMessage(Messages.Access));

        if (claim.getSubclaimRestrictions())
        {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.HasSubclaimRestriction);
        }

        return true;
    }

    /** Check claim chain for a trusted permission node. Mirrors removed Claim#hasExplicitPermission(Player,...). */
    static boolean hasPermissionTrust(Player player, Claim claim, ClaimPermission required)
    {
        Claim current = claim;
        while (current != null)
        {
            ArrayList<String> builders = new ArrayList<>();
            ArrayList<String> containers = new ArrayList<>();
            ArrayList<String> accessors = new ArrayList<>();
            ArrayList<String> managers = new ArrayList<>();
            current.getPermissions(builders, containers, accessors, managers);

            for (String entry : builders)
            {
                if (isGrantedPermissionEntry(current, entry, required, player)) return true;
            }
            for (String entry : containers)
            {
                if (isGrantedPermissionEntry(current, entry, required, player)) return true;
            }
            for (String entry : accessors)
            {
                if (isGrantedPermissionEntry(current, entry, required, player)) return true;
            }
            for (String entry : managers)
            {
                if (isGrantedPermissionEntry(current, entry, required, player)) return true;
            }

            current = current.parent;
        }
        return false;
    }

    private static boolean isGrantedPermissionEntry(Claim claim, String entry, ClaimPermission required, Player player)
    {
        if (entry == null || entry.length() < 3) return false;
        if (entry.charAt(0) != '[' || entry.charAt(entry.length() - 1) != ']') return false;
        ClaimPermission granted = claim.getPermission(entry);
        if (!required.isGrantedBy(granted)) return false;
        String node = entry.substring(1, entry.length() - 1);
        return player.hasPermission(node);
    }
}
