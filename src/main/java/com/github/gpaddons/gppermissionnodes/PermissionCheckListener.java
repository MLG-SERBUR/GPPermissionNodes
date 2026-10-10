package com.github.gpaddons.gppermissionnodes;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.events.ClaimPermissionCheckEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Restores permission-node trust checks removed from {@code Claim#getDefaultDenial}.
 * Clears denial when the checked player holds a trusted {@code [permission.node]} at required level.
 */
final class PermissionCheckListener implements Listener
{
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPermissionCheck(ClaimPermissionCheckEvent event)
    {
        if (event.getDenialReason() == null) return;

        Player checked = event.getCheckedPlayer();
        if (checked == null) return;

        Claim claim = event.getClaim();
        ClaimPermission required = event.getRequiredPermission();

        if (PermissionTrust.hasPermissionTrust(checked, claim, required))
        {
            event.setDenialReason(null);
        }
    }
}
