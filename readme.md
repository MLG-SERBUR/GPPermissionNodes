# GPPermissionNodes

Restores permission-node features removed from GriefPrevention core in upstream `master`:

- `6b01dd2 Remove permission node trust (#2653)` — `/trust [permission.node]` trust
- `fdafe98 Remove group permission bonus blocks from /acb (#2654)` — `/acb [permission.node]` bonus blocks

**Download:** build with `mvn package` — jar at `target/GPPermissionNodes.jar`

Requires GriefPrevention (core `depend`, no `softdepend`).

## Permission trust

Old core behavior, restored via `ClaimPermissionCheckEvent` + trust command wrappers:

```bash
/trust [vip.pass]
/containertrust vip.pass
/accesstrust [vip.pass]
/managetrust vip.pass
/untrust [vip.pass]
/untrust vip.pass
/trustlist
```

- Bracketed `[node]` or bare `dotted.node` (when no player by that name exists) treated as permission.
- Stored as `[node]` in claim permission map via `Claim#setPermission`, persists in claim data.
- `ClaimPermissionCheckEvent` denial cleared when online player holds a trusted node at required level.
- Parent claims checked for subdivisions.
- `/trustlist` shows `[node]` entries as-is (core now shows `someone`).

Non-permission args (`<player>`, `public`, `all`) delegate to core unchanged.

## Group bonus blocks

Old `/acb [permission]` behavior, restored with local storage:

```bash
/adjustbonusclaimblocks [vip.blocks] 1000
/acb [vip.blocks] -500
```

- Totals kept in this addon's `config.yml` under `group-bonus-blocks:`, not GP datastore.
- Effective bonus applied by syncing delta into player's real `bonusClaimBlocks`:
  - `newGroupTotal - lastGranted` added to `PlayerData#getBonusClaimBlocks`.
  - Manual `/acb <player>` bonuses preserved.
  - Rank loss removes bonus on next sync.
- Sync triggers: player join, group bonus change, periodic task (`sync-interval-seconds`, default 60).
- On disable, online players' granted group bonuses reverted; offline keep persisted bonus until next sync.
- Reload: `/gppermissionnodes reload` (needs `gppermissionnodes.admin`).

## Notes

- Uses only stable GP API (`Claim#getPermission`, `setPermission`, `dropPermission`, `getPermissions`, `ClaimPermissionCheckEvent`, `TrustChangedEvent`, `PlayerData` bonus). No removed `Claim#hasExplicitPermission(Player,...)` or `DataStore#getGroupBonusBlocks` calls.
- Wraps GP command executors for `trust`, `untrust`, `accesstrust`, `containertrust`, `managetrust`, `trustlist`, `adjustbonusclaimblocks`. Aliases (`tr`, `ut`, `ct`, `at`, `acb`, `permissiontrust`) dispatch to same wrapped executor.
