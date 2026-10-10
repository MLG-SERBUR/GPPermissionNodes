# GPPermissionNodes

A GriefPrevention addon that brings back permission-based trust and bonus claim blocks.

GriefPrevention core removed these features. This addon restores them without changing how regular player trust works.

- Permission trust: grant claim access to everyone who holds a permission node, for example `/trust [vip.pass]`.
- Group bonus blocks: grant extra claim blocks to everyone who holds a permission node, for example `/adjustbonusclaimblocks [vip.blocks] 1000`.

**Download:** [GPPermissionNodes.jar (latest release)](https://github.com/MLG-SERBUR/GPPermissionNodes/releases/latest/download/GPPermissionNodes.jar) — [All releases](https://github.com/MLG-SERBUR/GPPermissionNodes/releases) — [Source](https://github.com/MLG-SERBUR/GPPermissionNodes)

Requires GriefPrevention v18.0.0 (not legacy).

## Installation

Install GriefPrevention and GPPermissionNodes into your plugins folder, then restart the server. Permission trust works immediately with no configuration.

## Permission trust usage

Trust a permission node the same way you trust a player. Use the permission in brackets, or type the bare permission node if no player with that name exists:

```bash
/trust [vip.pass]
/containertrust vip.pass
/accesstrust [vip.pass]
/managetrust vip.pass
/untrust [vip.pass]
/untrust vip.pass
/trustlist
```

Notes:

- The permission is stored with the claim as `[permission]`, so it survives restarts and works with existing claims that already contain permission entries.
- Anyone online who holds the permission receives that trust level while they hold it. Removing the permission removes the access.
- Subdivisions inherit permission trust from their parent claim.
- Trusting individual players and `public` works exactly as before and is still handled by GriefPrevention core.

## Group bonus blocks usage

Give bonus claim blocks to a permission group:

```bash
/adjustbonusclaimblocks [vip.blocks] 1000
/adjustbonusclaimblocks [vip.blocks] -500
```

This needs the `griefprevention.adjustclaimblocks` permission, the same permission as GriefPrevention's own bonus block commands.

How it works in practice:

- Group totals are stored in this addon's `config.yml`, not in GriefPrevention's data. Losing a rank removes the bonus automatically.
- The addon keeps each player's effective bonus in sync, so claim creation and resizing see the extra blocks right away. Bonuses granted directly to a player with `/adjustbonusclaimblocks <player>` are left alone.
- Syncing happens when a player joins, when a group total changes, and on a timer. If you remove the addon, online players have their group-granted bonus removed; offline players keep their stored bonus until the next sync.

Reload without restart: `/gppermissionnodes reload` (needs `gppermissionnodes.admin`).

## Configuration

```yaml
# config.yml
# Bonus blocks granted while a player holds each permission.
# Example:
# group-bonus-blocks:
#   vip.blocks: 1000
#   staff.blocks: 5000
group-bonus-blocks: {}

# How often (seconds) to re-check online players.
# 0 disables the timer. Join and group changes still sync immediately.
sync-interval-seconds: 60
```

## Compatibility

Player names, `public`, and `all` behave exactly as GriefPrevention core defines them. Only bracketed permissions and bare dotted permission nodes without a matching player are handled by this addon. Command aliases (`tr`, `ut`, `ct`, `at`, `acb`, `permissiontrust`) work as usual.

## Building from source

```bash
mvn package
```

The jar is created at `target/GPPermissionNodes.jar`.
