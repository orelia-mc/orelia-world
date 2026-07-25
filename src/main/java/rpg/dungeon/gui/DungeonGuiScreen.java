package rpg.dungeon.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.api.StatusApi;
import rpg.core.message.MessageManager;
import rpg.core.player.PlayerDataManager;
import rpg.dungeon.model.DungeonData;
import rpg.dungeon.model.PlayerDungeonComponent;
import rpg.dungeon.repository.DungeonRepository;
import rpg.dungeon.service.DungeonEncounterService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiManager;
import rpg.util.ItemBuilder;

import java.util.List;
import java.util.Set;

/**
 * Lists the viewing player's unlocked dungeons; clicking one opens {@link #buildDifficultySelect}
 * instead of starting the run directly, same as right-clicking its trigger block a second time
 * ({@code DungeonBlockInteractListener}). Same shape as {@code QuestGuiScreen} - plain
 * {@code build(Player) -> Gui} methods reusing orelia-core's generic Gui framework.
 */
public final class DungeonGuiScreen {

    private final DungeonRepository repository;
    private final DungeonEncounterService encounterService;
    private final PlayerDataManager playerDataManager;
    private final StatusApi statusApi;
    private final GuiManager guiManager;
    private final MessageManager messages;

    public DungeonGuiScreen(DungeonRepository repository, DungeonEncounterService encounterService,
                             PlayerDataManager playerDataManager, StatusApi statusApi, GuiManager guiManager,
                             MessageManager messages) {
        this.repository = repository;
        this.encounterService = encounterService;
        this.playerDataManager = playerDataManager;
        this.statusApi = statusApi;
        this.guiManager = guiManager;
        this.messages = messages;
    }

    public Gui build(Player player) {
        Gui gui = new Gui("&%8ダンジョン", 27);
        PlayerDungeonComponent component = playerDataManager.get(player.getUniqueId())
                .flatMap(d -> d.component(PlayerDungeonComponent.class))
                .orElse(null);
        Set<String> unlocked = component == null ? Set.of() : component.getUnlockedDungeonIds();

        int slot = 10;
        for (String dungeonId : unlocked) {
            DungeonData data = repository.findById(dungeonId).orElse(null);
            if (data == null) {
                continue;
            }
            gui.set(slot++, new GuiButton(new ItemBuilder(Material.NETHER_STAR)
                    .name("&%e" + data.getName())
                    .lore(List.of(
                            "&%7クリックして難易度を選択",
                            "&%7人数: " + data.getMinPartySize() + "〜" + data.getMaxPartySize(),
                            "&%7制限時間: " + data.getTimeLimitSeconds() + "秒"))
                    .build(), (clicker, clickType) -> guiManager.open(clicker, buildDifficultySelect(clicker, dungeonId))));
        }
        return gui;
    }

    /**
     * Fixed difficulty tiers ({@link DungeonEncounterService#DIFFICULTY_TIERS}) up to the
     * viewer's own character level - if none qualify (very low level), falls back to a single
     * "通常" (unscaled) button instead of leaving the screen empty.
     */
    public Gui buildDifficultySelect(Player player, String dungeonId) {
        Gui gui = new Gui("&%8難易度選択", 27);
        int level = statusApi.getLevel(player.getUniqueId()).orElse(1);
        List<Integer> eligible = DungeonEncounterService.DIFFICULTY_TIERS.stream().filter(tier -> tier <= level).toList();

        if (eligible.isEmpty()) {
            gui.set(13, new GuiButton(new ItemBuilder(Material.NETHER_STAR)
                    .name("&%e通常")
                    .lore(List.of("&%7クリックして挑戦", "&%8推奨レベルに届いていないため難易度選択は利用できません。"))
                    .build(), (clicker, clickType) -> handleDifficultyClick(clicker, dungeonId, null)));
            return gui;
        }
        int slot = 10;
        for (int tier : eligible) {
            gui.set(slot++, new GuiButton(new ItemBuilder(Material.NETHER_STAR)
                    .name("&%eLv. " + tier)
                    .lore(List.of("&%7クリックしてこの難易度で挑戦"))
                    .build(), (clicker, clickType) -> handleDifficultyClick(clicker, dungeonId, tier)));
        }
        return gui;
    }

    private void handleDifficultyClick(Player player, String dungeonId, Integer difficulty) {
        player.closeInventory();
        encounterService.challenge(player, dungeonId, difficulty)
                .ifPresent(failure -> messages.send(player, "dungeon.challenge-failed." + failure.name().toLowerCase()));
    }
}
