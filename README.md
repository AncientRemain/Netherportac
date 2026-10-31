# NetherPortac

A Paper/Spigot **1.12.2** plugin that fixes nether portal linking. It works on
the backend server behind an Eaglercraft proxy (EaglerXBungee/EaglerXServer).

## The problem

Vanilla Minecraft doesn't remember which portals are linked. Every trip searches
for the closest portal near the scaled coordinates (x/8 in the nether, x*8 in the
overworld). When friends build bases near each other, players come out of each
other's portals or a brand new portal gets built on the way back.

## What the plugin does

- When you walk through portal **A** and come out of portal **B**, the plugin
  remembers that **A ↔ B** are linked.
- After that, going through **B** always sends you back to **A**, and going
  through **A** always sends you to **B**. Distance doesn't matter.
- Links are saved **per player**. If two friends' overworld portals both lead to
  the same nether portal, each friend goes back to their own base.
- A shared link (set by the first person through a portal) is used by players
  who don't have their own link yet, and by mobs, items and minecarts.
- If a linked portal is broken, its link is forgotten and vanilla picks a portal
  as usual. The next trip creates a new link.
- Links are stored in `plugins/NetherPortac/links.yml`.

## Install

1. Download the jar from the **Actions** tab (latest "Build plugin" run →
   *NetherPortac* artifact), or build it with `mvn package` (the jar ends up in `target/`).
2. Put `NetherPortac-1.0.0.jar` in your backend server's `plugins/` folder
   (the Paper 1.12.2 server, **not** the Bungee/Eagler proxy).
3. Restart the server.

**Portals that already exist:** the plugin links each pair the first time
someone goes through it. If an existing pair is linked wrong, stand in the bad
portal and run `/netherportac unlink`. Then walk through the correct portals
once to link them.

## Commands (op only)

| Command | What it does |
| --- | --- |
| `/netherportac info` | Shows the links for the portal you're standing in |
| `/netherportac unlink` | Forgets all links for the portal you're standing in |
| `/netherportac reload` | Reloads `config.yml` |

## Config

```yaml
per-player-links: true      # each player returns to their own portal
link-entities: true         # mobs/items/minecarts use shared links
save-interval-seconds: 60
```
