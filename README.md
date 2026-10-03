# Deserter

A server-side Fabric anti-combat-log mod for Minecraft 26.1 and newer. Players don't need to install it.

## How it works

- When a player damages another player (melee, projectiles, or a tamed pet), both players are **in combat**. A countdown shows above the hotbar, and each new hit resets it.
- If a player disconnects while in combat, **their body stays in the world** until the timer runs out. The body glows red, shows a `[Deserter]` tag, and still falls and takes fall damage.
- Other players can kill the body and take the loot. The death message reads "B was slain by A while deserting".
- If the body survives, it disappears when combat ends and the player can rejoin safely.
- If the player rejoins before that, they take back control of their body exactly where it is, and they are still in combat.
- Every desertion is recorded. Repeat deserters' bodies stay longer, and you can optionally temp-ban them.
- Nobody is tagged in creative or spectator mode, in safe zones, or with the bypass permission. Admin kicks, server shutdowns and the LAN host leaving never leave a body. Network timeouts do, so pulling the cable doesn't save you.

## Installation

Requires Minecraft 26.1 or newer (one jar for all versions), Fabric Loader 0.19.5+, [Fabric API](https://modrinth.com/mod/fabric-api) and Java 25. Put the jar in the server's `mods` folder.

## Configuration

`config/deserter.json` is created on first start. Run `/deserter reload` to apply changes.

| Option | Default | Description |
| --- | --- | --- |
| `combatSeconds` | `15` | How long combat lasts after the last hit |
| `petsTriggerCombat` | `true` | Tamed pets attacking counts as PvP |
| `kicksLeaveBody` | `false` | Whether kicks during combat leave a body |
| `display` | `actionbar` | `actionbar`, `bossbar` or `none` |
| `sounds.*` | | Sounds for entering and leaving combat (sound IDs; empty disables) |
| `body.glow`, `body.nameTag` | `true` | How bodies are marked |
| `body.gravity` | `true` | Bodies fall. `false` freezes them where the player logged out |
| `safeZones.disabledDimensions` | `[]` | e.g. `["minecraft:the_end"]` |
| `safeZones.zones` | `[]` | Boxes where hits don't start combat, see below |
| `punishments.memoryDays` | `30` | Only desertions this recent count towards punishments |
| `punishments.extraBodySecondsPerDesertion` | `5` | Repeat deserters' bodies stay this much longer per recent desertion, capped at `maxExtraBodySeconds` (`60`) |
| `punishments.banAfterDesertions` | `0` | Temp-ban after this many recent desertions, for `banMinutes` (`60`). `0` disables bans |
| `messages.*` | | All texts. Supports `&` color codes and `{placeholders}`. An empty `deserted` disables the broadcast |

A safe zone around spawn:

```json
"zones": [
  { "dimension": "minecraft:overworld", "minX": -50, "minY": -64, "minZ": -50, "maxX": 50, "maxY": 320, "maxZ": 50 }
]
```

## Commands

| Command | Permission | Default |
| --- | --- | --- |
| `/deserter reload` | `deserter.command.reload` | op level 3 |
| `/deserter tag <players> [seconds]` | `deserter.command.tag` | op level 2 |
| `/deserter untag <players>` | `deserter.command.tag` | op level 2 |
| `/deserter list` (who's in combat, and bodies) | `deserter.command.list` | op level 1 |
| `/deserter stats <player>` | `deserter.command.stats` | op level 1 |

`deserter.bypass` (default op level 2) means the player is never tagged. All permissions work with any Fabric permission mod, such as LuckPerms, on 26.1.2 and newer. On 26.1 and 26.1.1 only the op levels apply.

Desertions are saved in `<world>/deserter/stats.json` and logged in `<world>/deserter/desertions.log`.

## Placeholders

With [Text Placeholder API](https://modrinth.com/mod/placeholder-api) installed: `%deserter:combat_time%`, `%deserter:in_combat%` and `%deserter:desertions%`.

## For mod developers

Listen to `io.github.nussico.deserter.api.DeserterEvents.DESERTED` to react when a player deserts. `Deserter.COMBAT.isInCombat(uuid, CombatManager.now(server))` tells you if someone is in combat.

## Building

Requires Java 25.

```powershell
./gradlew build
```

The jar is written to `build/libs/deserter-<version>.jar`. To start a test server, run `./gradlew runServer`. To run the automated tests, run `./gradlew runGameTest`.

## License

CC0-1.0
