package rpg.world.playerinfo.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.util.ItemBuilder;

/**
 * Dedicated "実績" sub-screen of the player-info nether-star menu. Achievement tracking itself
 * lives in orelia-extra's {@code AchievementModule} (chat-based, paginated {@code /ol
 * achievement}) - orelia-world has no compile-time dependency on orelia-extra (only the reverse,
 * via {@code QuestApi}), so rather than reimplementing a GUI here, this screen is a launcher:
 * clicking the icon closes the GUI and runs {@code /ol achievement} for the player. If
 * OreliaExtra isn't installed, that command simply fails the normal "unknown command" way - no
 * direct linkage needed either direction. Opened from {@link PlayerInfoGuiScreen}, which
 * supplies the back button placed in this screen's bottom-right slot.
 */
public final class PlayerInfoAchievementGuiScreen {

    private static final int SIZE = 36;
    private static final int LAUNCH_SLOT = 13;
    private static final int BACK_SLOT = SIZE - 1;

    public Gui build(Player player, GuiButton backButton) {
        Gui gui = new Gui("&%8プレイヤー情報 - 実績", SIZE);
        gui.set(BACK_SLOT, backButton);
        gui.set(LAUNCH_SLOT, new GuiButton(new ItemBuilder(Material.NETHER_STAR)
                .name("&%b実績一覧を開く")
                .lore("&%7クリックすると &%f/ol achievement &%7を実行します。", "&%8（要 OreliaExtra）")
                .build(), (p, clickType) -> {
            p.closeInventory();
            p.performCommand("ol achievement");
        }));
        return gui;
    }
}
