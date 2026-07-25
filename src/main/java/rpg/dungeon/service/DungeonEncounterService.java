package rpg.dungeon.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import rpg.api.CombatApi;
import rpg.core.message.MessageManager;
import rpg.core.scheduler.SchedulerService;
import rpg.dungeon.manager.DungeonInstanceManager;
import rpg.dungeon.model.DungeonArena;
import rpg.dungeon.model.DungeonData;
import rpg.dungeon.model.DungeonEndReason;
import rpg.dungeon.model.DungeonInstance;
import rpg.dungeon.repository.PlayerDungeonRepository;
import rpg.extra.api.PartyApi;
import rpg.quest.service.QuestProgressService;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Drives a dungeon run's live encounter on top of {@link DungeonService}'s bare
 * teleport-in/teleport-out/reward shell: spawning the configured enemies/boss, tracking
 * which ones are still alive, the time-limit countdown, and manual retire. Composes
 * {@link DungeonService} rather than extending it - that class's own job (party-size
 * validation, teleport, reward) stays a clean, narrow unit.
 */
public final class DungeonEncounterService {

    public enum ChallengeFailure {
        NOT_UNLOCKED, UNKNOWN_DUNGEON, PARTY_TOO_SMALL, PARTY_TOO_LARGE, WORLD_NOT_FOUND, ALREADY_IN_DUNGEON, DUNGEON_FULL
    }

    private static final double SPAWN_JITTER_RADIUS = 2.5;

    private final DungeonService dungeonService;
    private final DungeonInstanceManager instanceManager;
    private final CombatApi combatApi;
    private final SchedulerService schedulerService;
    private final PlayerDungeonRepository playerDungeonRepository;
    private final PartyApi partyApi;
    private final QuestProgressService questProgressService;
    private final MessageManager messages;
    private final Random random = new Random();

    /** The party a challenge is resolved against: who must have the dungeon unlocked, and who actually enters. */
    private record PartyResolution(UUID leaderId, List<Player> members) {
    }

    public DungeonEncounterService(DungeonService dungeonService, DungeonInstanceManager instanceManager,
                                    CombatApi combatApi, SchedulerService schedulerService,
                                    PlayerDungeonRepository playerDungeonRepository, PartyApi partyApi,
                                    QuestProgressService questProgressService, MessageManager messages) {
        this.dungeonService = dungeonService;
        this.instanceManager = instanceManager;
        this.combatApi = combatApi;
        this.schedulerService = schedulerService;
        this.playerDungeonRepository = playerDungeonRepository;
        this.partyApi = partyApi;
        this.questProgressService = questProgressService;
        this.messages = messages;
    }

    /**
     * Attempts to start a dungeon run for {@code initiator}'s real party (see
     * {@link #resolveParty}) - a solo player if they aren't in one. Only the party leader
     * needs to have unlocked the dungeon; other online members ride along.
     */
    public Optional<ChallengeFailure> challenge(Player initiator, String dungeonId) {
        PartyResolution resolution = resolveParty(initiator);
        if (!playerDungeonRepository.isUnlocked(resolution.leaderId(), dungeonId)) {
            return Optional.of(ChallengeFailure.NOT_UNLOCKED);
        }

        Optional<DungeonService.StartFailure> failure = dungeonService.start(dungeonId, resolution.members());
        if (failure.isPresent()) {
            return Optional.of(mapFailure(failure.get()));
        }

        DungeonInstance instance = instanceManager.getByPlayer(initiator.getUniqueId()).orElseThrow();
        spawnEncounter(instance);
        scheduleTimeout(instance);
        return Optional.empty();
    }

    /** Manually ends the run the given player is currently in. Returns false if they aren't in one. */
    public boolean retire(Player player) {
        return instanceManager.getByPlayer(player.getUniqueId())
                .map(instance -> {
                    forceEnd(instance, DungeonEndReason.RETIRED);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Debug helper: starts a solo run for {@code player} bypassing the unlock check (party-size
     * validation against {@code min-party-size} still applies via {@link DungeonService#start},
     * since a single player is the whole "party" here - a dungeon requiring more than one
     * member will still fail with {@link ChallengeFailure#PARTY_TOO_SMALL}).
     */
    public Optional<ChallengeFailure> forceStart(Player player, String dungeonId) {
        Optional<DungeonService.StartFailure> failure = dungeonService.start(dungeonId, List.of(player));
        if (failure.isPresent()) {
            return Optional.of(mapFailure(failure.get()));
        }
        DungeonInstance instance = instanceManager.getByPlayer(player.getUniqueId()).orElseThrow();
        spawnEncounter(instance);
        scheduleTimeout(instance);
        return Optional.empty();
    }

    /** The dungeon id of the instance the given player is currently in, if any. */
    public Optional<String> getActiveDungeonId(UUID playerId) {
        return instanceManager.getByPlayer(playerId).map(instance -> instance.getData().getId());
    }

    /** Called by {@code DungeonMobDeathListener} whenever any entity dies - a no-op if it wasn't a tracked dungeon mob. */
    public void onMobDeath(UUID entityId) {
        instanceManager.getByMonster(entityId).ifPresent(instance -> {
            if (instance.untrackMonster(entityId) && instance.isCleared()) {
                forceEnd(instance, DungeonEndReason.CLEARED);
            }
        });
    }

    /**
     * Resolves who a challenge is judged against: the real party from orelia-extra's
     * {@link PartyApi} (leader's unlock status gates entry, every online member rides along),
     * falling back to a solo party of just {@code initiator} when orelia-extra isn't installed
     * or {@code initiator} isn't in a party.
     */
    private PartyResolution resolveParty(Player initiator) {
        if (partyApi != null) {
            Set<UUID> memberIds = partyApi.getMemberIds(initiator.getUniqueId());
            if (!memberIds.isEmpty()) {
                UUID leaderId = partyApi.getLeaderId(initiator.getUniqueId()).orElse(initiator.getUniqueId());
                List<Player> onlineMembers = memberIds.stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .toList();
                return new PartyResolution(leaderId, onlineMembers);
            }
        }
        return new PartyResolution(initiator.getUniqueId(), List.of(initiator));
    }

    private void spawnEncounter(DungeonInstance instance) {
        DungeonData data = instance.getData();
        DungeonArena arena = data.getArenas().get(instance.getArenaIndex());
        var world = Bukkit.getWorld(arena.world());
        if (world == null) {
            return;
        }
        Location entry = new Location(world, arena.x(), arena.y(), arena.z());
        for (Map.Entry<String, Integer> entry2 : data.getEnemies().entrySet()) {
            for (int i = 0; i < entry2.getValue(); i++) {
                combatApi.spawnMonster(entry2.getKey(), jitter(entry)).ifPresent(mob -> track(instance, mob));
            }
        }
        if (data.getBossId() != null) {
            combatApi.spawnBoss(data.getBossId(), jitter(entry)).ifPresent(boss -> track(instance, boss));
        }
    }

    private void track(DungeonInstance instance, LivingEntity entity) {
        instance.trackMonster(entity.getUniqueId());
        instanceManager.registerMonster(entity.getUniqueId(), instance.getId());
    }

    private Location jitter(Location center) {
        double dx = (random.nextDouble() * 2 - 1) * SPAWN_JITTER_RADIUS;
        double dz = (random.nextDouble() * 2 - 1) * SPAWN_JITTER_RADIUS;
        return center.clone().add(dx, 0, dz);
    }

    private void scheduleTimeout(DungeonInstance instance) {
        long delayTicks = instance.getData().getTimeLimitSeconds() * 20L;
        instance.setTimeoutTask(schedulerService.runLater(() -> forceEnd(instance, DungeonEndReason.TIMED_OUT), delayTicks));
    }

    private void forceEnd(DungeonInstance instance, DungeonEndReason reason) {
        instance.cancelTimeoutTask();
        despawnRemainingMobs(instance);
        Set<UUID> memberIds = instance.getMembers().keySet();
        String dungeonId = instance.getData().getId();
        UUID anyMember = memberIds.iterator().next();
        dungeonService.finish(anyMember, reason);
        if (reason == DungeonEndReason.CLEARED) {
            memberIds.forEach(id -> questProgressService.onDungeonCleared(id, dungeonId));
        }
        notifyOutcome(memberIds, reason);
    }

    private void despawnRemainingMobs(DungeonInstance instance) {
        for (UUID entityId : instance.getAliveMonsterIds()) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity instanceof LivingEntity living && living.isValid()) {
                living.remove();
            }
        }
    }

    private void notifyOutcome(Set<UUID> memberIds, DungeonEndReason reason) {
        String key = switch (reason) {
            case CLEARED -> "dungeon.cleared";
            case TIMED_OUT -> "dungeon.timed-out";
            case RETIRED -> "dungeon.retired";
        };
        for (UUID memberId : memberIds) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                messages.send(member, key);
            }
        }
    }

    private ChallengeFailure mapFailure(DungeonService.StartFailure failure) {
        return switch (failure) {
            case UNKNOWN_DUNGEON -> ChallengeFailure.UNKNOWN_DUNGEON;
            case PARTY_TOO_SMALL -> ChallengeFailure.PARTY_TOO_SMALL;
            case PARTY_TOO_LARGE -> ChallengeFailure.PARTY_TOO_LARGE;
            case WORLD_NOT_FOUND -> ChallengeFailure.WORLD_NOT_FOUND;
            case ALREADY_IN_DUNGEON -> ChallengeFailure.ALREADY_IN_DUNGEON;
            case DUNGEON_FULL -> ChallengeFailure.DUNGEON_FULL;
        };
    }
}
