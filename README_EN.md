<img src="https://orelia-mc.github.io/assets/logo_wide.jpg" />
<h1 align="center">Orelia World</h1>
<p align="center">Content Plugin of Orelia-MC</p>

## About

`orelia-world` is the content plugin (Paper 1.21.x / Java 21) of the Minecraft RPG plugin suite **Orelia**, covering quest, NPC, dialogue, story, dungeon, cutscene, and event systems.

Orelia is split into 3 plugins:

- [orelia-core](https://github.com/orelia-mc/orelia-core) — combat/player/status foundation (required dependency)
- **orelia-world** (this repo) — Quest, NPC, Dialogue, Story, Dungeon, CutScene, Event
- [orelia-extra](https://github.com/orelia-mc/orelia-extra) — later MMORPG features

Requires **orelia-core** to be installed and enabled first (`depend: [OreliaCore]` in `plugin.yml`). Talks to it only through `rpg.api` (published via Bukkit's `ServicesManager`) — never through orelia-core's internal module classes. Money goes through Vault directly, the same way any other Vault-integrated plugin would.

## Setup

```bash
./gradlew build
```

Requires network access to `repo.papermc.io` (Paper API) and `jitpack.io` (resolves orelia-core and Vault API straight from GitHub).

## Structure

- Config — `quests.yml`, `npc.yml`, `dungeons.yml`, `dialogues.yml`, `story.yml`, `cutscenes.yml`, `events.yml`, `config.yml`. Reload all of them with `/oladmin worldreload`.
- NPCs are no longer auto-spawned on startup. Run `/oladmin npc spawnall` to place every configured, not-yet-present NPC from `npc.yml` at once (safe to re-run, never duplicates). The job-change NPC is the one exception - place it individually at the sender's location with `/oladmin spawnnpc <npc-id>` (e.g. `/oladmin spawnnpc job_master`).
- A `type: RELIC_UPGRADE` NPC opens orelia-core's "選べる厳選" substat-upgrade screen directly (`rpg.api.RelicApi#openUpgradeGui`), and a shop NPC's `shop-stock` can use `kind: RELIC` (id matching a key under orelia-core's `relics.yml` `shop-relics:`). The bundled default `npc.yml` swaps the accessory merchant's (`accessory_merchant`) stock from fixed accessories to relics (`shop_guardian_charm`, etc.) and adds a new upgrader NPC (`relic_upgrader`) as a worked example - but that's only the fresh-install template, not something that retroactively updates an already-running server's own `npc.yml` (`ConfigMigrator` only splices in missing keys, never overwrites values that already exist). To pick this up on an existing server, either hand-edit that NPC's entry in your own `npc.yml` or add a new one with `/oladmin npc create relic_upgrader RELIC_UPGRADE`.
- The nether-star player-info menu's skill tab now opens orelia-core's weapon-skill screen (learn/level-up/socket skills onto the held weapon).
- Quests — prerequisite quests (`quests.yml`'s `prerequisite-quests`) already worked and now also notify the player when completing one unlocks another. Repeatable quests (`repeatable: true`) can set `cooldown-hours` to require a wait before re-accepting (unset/0 = instantly re-acceptable, same as before). `/ol quest list` now shows a progress bar per objective.
- Titles — a title earned from a quest reward (`reward.title`) can be viewed with `/ol title list` and equipped with `/ol title equip <title>` (`/ol title unequip` to remove it). The equipped title is exposed via `QuestApi#getEquippedTitle` for cross-plugin display - see orelia-serverutil's `{title}` placeholder for chat/tab-list.
- Dungeons — `dungeons.yml` configures the enemy roster (`enemies:`, monsters.yml ids × count), an optional boss (`boss-id:`, a bosses.yml id), a time limit (`time-limit-seconds:`), and up to 3 `arenas:` (physical entry coordinates; the arena count is how many parties can run the dungeon at once). Admins register the block they're looking at as a dungeon's unlock trigger with `/oladmin dungeonblock set <dungeon-id>` (`remove` to unregister, `list` to list them). Right-clicking that block first "discovers" the dungeon (persisted per player), and opens a difficulty-select screen on the next click, same as `/ol dungeon` (GUI). Difficulty is one of a fixed set of tiers (5/10/15/20/30/40/50/60/70/80/90/100), capped at the challenger's own character level (falls back to unscaled "通常" if none qualify; an 8th+ eligible tier pages, same as `/ol dungeon`) - the chosen tier scales enemy/boss stats via `target_level` (also selectable directly with `/ol dungeon start <id> [difficulty]`). Choosing a difficulty immediately reserves an arena (rejected as full if none are free), then counts down once per second ("N seconds until you challenge") before the actual teleport/enemy spawn happens (the reservation is released instead if everyone queued logs out during the wait). If the challenger is in an orelia-extra party, its online members join as the party; otherwise they enter solo (always solo if OreliaExtra isn't installed). Only the party leader needs to have discovered the dungeon. Clearing every enemy and the boss within the time limit grants the reward and advances any quest's `CLEAR_DUNGEON` objective (a bossed dungeon also drops `config.yml`'s `dungeon.relic-drop-min`/`max` relics per member - `rpg.api.RelicApi`, orelia-core's rollable relic system); timing out force-exits with no reward, and `/ol dungeon retire` lets a party leave voluntarily at any time (also no reward; the unlock itself is never lost, so a retry is always possible). Clearing shows the EXP/money gained (`dungeons.yml`'s `reward-exp`/`reward-money`) in chat, and each member's own relic count (if any dropped) is shown separately.
