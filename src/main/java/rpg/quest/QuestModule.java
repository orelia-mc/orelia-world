package rpg.quest;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicesManager;
import rpg.api.AccessoryApi;
import rpg.api.CombatApi;
import rpg.api.ItemApi;
import rpg.api.SkillApi;
import rpg.api.StatusApi;
import rpg.core.command.CommandAliasUtil;
import rpg.database.manager.DatabaseManager;
import rpg.extra.api.PartyApi;
import rpg.quest.command.QuestCommand;
import rpg.quest.command.TitleCommand;
import rpg.quest.config.QuestFeedbackConfig;
import rpg.quest.gui.QuestGuiScreen;
import rpg.quest.listener.QuestKillListener;
import rpg.quest.manager.QuestManager;
import rpg.quest.repository.PlayerQuestRepository;
import rpg.quest.repository.QuestRepository;
import rpg.quest.service.QuestEligibilityService;
import rpg.quest.service.QuestItemInventoryService;
import rpg.quest.service.QuestObjectiveFeedbackService;
import rpg.quest.service.QuestProgressService;
import rpg.quest.service.QuestRewardService;
import rpg.world.core.OreliaWorldPlugin;
import rpg.world.core.module.WorldModule;

import java.util.logging.Level;

/**
 * Quest module: config-driven quest definitions (quests.yml), the accept/progress/report
 * state machine, and reward distribution through orelia-core's published API (item/
 * economy/skill/status) - never through orelia-core's internal module classes.
 */
public final class QuestModule implements WorldModule {

    private final QuestRepository questRepository = new QuestRepository();
    private final QuestFeedbackConfig feedbackConfig = new QuestFeedbackConfig();
    private QuestProgressService progressService;
    private QuestGuiScreen questGuiScreen;
    private OreliaWorldPlugin plugin;

    @Override
    public String getName() {
        return "quest";
    }

    @Override
    public void onEnable(OreliaWorldPlugin plugin) {
        this.plugin = plugin;
        ServicesManager services = plugin.getServer().getServicesManager();

        DatabaseManager databaseManager = require(services, DatabaseManager.class);
        StatusApi statusApi = require(services, StatusApi.class);
        ItemApi itemApi = require(services, ItemApi.class);
        AccessoryApi accessoryApi = require(services, AccessoryApi.class);
        SkillApi skillApi = require(services, SkillApi.class);
        CombatApi combatApi = require(services, CombatApi.class);
        Economy economy = services.load(Economy.class);
        // Soft dependency - orelia-extra (and therefore PartyApi) may not be installed.
        // party-only quests fail closed when null (see QuestEligibilityService).
        PartyApi partyApi = services.load(PartyApi.class);

        reloadQuests();
        reloadFeedbackConfig();

        PlayerQuestRepository repository = new PlayerQuestRepository(databaseManager);
        try {
            repository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize quest schema", e);
        }
        QuestManager questManager = new QuestManager(repository);
        plugin.getPlayerDataManager().registerLoader(questManager);

        QuestEligibilityService eligibilityService = new QuestEligibilityService(plugin.getPlayerDataManager(), statusApi, partyApi);
        QuestItemInventoryService inventoryService = new QuestItemInventoryService(itemApi);
        QuestRewardService rewardService = new QuestRewardService(
                plugin.getPlayerDataManager(), statusApi, economy, itemApi, accessoryApi, skillApi);
        QuestObjectiveFeedbackService feedbackService = new QuestObjectiveFeedbackService(plugin.getMessageManager(), feedbackConfig);
        this.progressService = new QuestProgressService(plugin.getPlayerDataManager(), questRepository, eligibilityService,
                rewardService, inventoryService, plugin.getMessageManager(), feedbackService);
        this.questGuiScreen = new QuestGuiScreen(questRepository, progressService, eligibilityService,
                plugin.getPlayerDataManager(), plugin.getMessageManager());

        plugin.getServer().getPluginManager().registerEvents(new QuestKillListener(combatApi, progressService), plugin);

        QuestCommand questCommand = new QuestCommand(plugin.getPlayerDataManager(), questRepository, progressService, plugin.getMessageManager());
        plugin.getPlayerCommandRegistry().register("quest", questCommand,
                "クエストの受注状況を確認します。", "quest <list|abandon <id>>");
        CommandAliasUtil.registerAlias(plugin, "quest", questCommand,
                "クエストの受注状況を確認します。", "<list|abandon <id>>");

        TitleCommand titleCommand = new TitleCommand(plugin.getPlayerDataManager(), plugin.getMessageManager());
        String titleDescription = "獲得済みの称号を確認・装備します。";
        plugin.getPlayerCommandRegistry().register("title", titleCommand, titleDescription, "title <list|equip <title>|unequip>");
        CommandAliasUtil.registerAlias(plugin, "title", titleCommand, titleDescription, "<list|equip <title>|unequip>");

        long periodTicks = plugin.getConfigManager().get("config.yml").get().getLong("quest.objective-check-period-ticks", 40L);
        plugin.getSchedulerService().runTimer(() ->
                plugin.getServer().getOnlinePlayers().forEach(progressService::checkPeriodicObjectives),
                periodTicks, periodTicks);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadQuests();
        reloadFeedbackConfig();
    }

    private void reloadQuests() {
        plugin.getConfigManager().register("quests.yml");
        YamlConfiguration config = plugin.getConfigManager().get("quests.yml").get();
        questRepository.load(config);
    }

    private void reloadFeedbackConfig() {
        plugin.getConfigManager().register("config.yml");
        feedbackConfig.load(plugin.getConfigManager().get("config.yml").get());
    }

    private <T> T require(ServicesManager services, Class<T> type) {
        T service = services.load(type);
        if (service == null) {
            throw new IllegalStateException("quest module requires OreliaCore's " + type.getSimpleName());
        }
        return service;
    }

    public QuestProgressService getProgressService() {
        return progressService;
    }

    public QuestRepository getQuestRepository() {
        return questRepository;
    }

    public QuestGuiScreen getQuestGuiScreen() {
        return questGuiScreen;
    }
}
