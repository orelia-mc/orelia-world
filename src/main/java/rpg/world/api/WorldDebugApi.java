package rpg.world.api;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-plugin surface for debug/testplay tooling (orelia-debug) over orelia-world: config
 * inspection/editing (same pattern as orelia-core's {@code rpg.api.DebugApi}) plus a couple
 * of world-specific shortcuts (force-completing quest objectives, listing NPC ids) that don't
 * fit a generic config API.
 */
public interface WorldDebugApi {

    Set<String> listConfigFiles();

    Optional<String> getConfigValue(String fileName, String path);

    boolean setConfigValue(String fileName, String path, String rawValue);

    void saveConfig(String fileName);

    List<String> describeConfigKeys(String fileName);

    /**
     * One node per key in {@code fileName}, in on-disk order, depth-first - for a human-readable
     * indented "config view" listing rather than a flat dot-path dump. Same shape as
     * orelia-core's {@code rpg.api.DebugApi.ConfigTreeEntry}, duplicated here rather than shared
     * since orelia-world doesn't otherwise depend on orelia-core's {@code rpg.api} package.
     */
    List<ConfigTreeEntry> listConfigTree(String fileName);

    /** See {@link #listConfigTree}. {@code value} is {@code null} for a non-leaf (section) node. */
    record ConfigTreeEntry(String path, int depth, String label, String value, boolean isLeaf) {}

    /** Forces every objective of {@code questId} to completion for {@code playerId}, if in progress. */
    boolean forceCompleteQuestObjectives(UUID playerId, String questId);

    /** Force-starts {@code questId} for {@code playerId}, bypassing prerequisite/level eligibility checks. */
    boolean forceStartQuest(UUID playerId, String questId);

    /** Clears {@code questId}'s completion record for {@code playerId}, resetting a repeatable quest's cooldown. */
    boolean resetQuestCompletion(UUID playerId, String questId);

    /** Every quest id defined in {@code quests.yml}, sorted - for admin visibility/tab-completion. */
    List<String> listQuestIds();

    /** Grants {@code title} to {@code playerId} without requiring the quest reward that normally awards it. */
    boolean grantTitle(UUID playerId, String title);

    /** Force-equips {@code title} for {@code playerId}, bypassing the "must already be earned" check {@link QuestApi#equipTitle} enforces. */
    boolean forceEquipTitle(UUID playerId, String title);

    /** Unequips {@code playerId}'s currently-equipped title, if any. */
    boolean unequipTitle(UUID playerId);

    /** Every configured NPC id, sorted - for a "debug npc show" style listing. */
    List<String> listNpcIds();

    /** Every dungeon id defined in {@code dungeons.yml}, sorted. */
    List<String> listDungeonIds();

    /** Unlocks {@code dungeonId} for {@code playerId}, bypassing the trigger-block discovery flow. */
    boolean unlockDungeonForPlayer(UUID playerId, String dungeonId);

    /**
     * Force-starts a solo run of {@code dungeonId} for {@code playerId}, bypassing the unlock
     * check. Still subject to the dungeon's {@code min-party-size} (a solo player is the whole
     * party). Returns empty on success, or a failure reason on failure.
     */
    Optional<String> forceStartDungeon(UUID playerId, String dungeonId);

    /** Force-ends whichever dungeon run {@code playerId} is currently in, as a retire (no rewards). */
    boolean forceEndDungeon(UUID playerId);

    /** The dungeon id of the run {@code playerId} is currently in, if any. */
    Optional<String> getActiveDungeonId(UUID playerId);

    /** Force-opens the dungeon list screen (same as {@code DungeonGuiScreen}) for {@code player}. */
    void openDungeon(Player player);

    /**
     * Force-opens {@code player}'s full quest log (same as {@code QuestGuiScreen#build(Player)})
     * - not tied to any specific NPC's offer list, unlike the in-game NPC interaction path.
     */
    void openQuest(Player player);
}
