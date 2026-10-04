package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import org.codeberg.DeployedReject.tui.theme.GlyphHelper;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MainMenuView implements WorkspaceView {

    private static final String[] SPLASHES = {
            "Native AOT GraalVM binary!",
            "100% Older laptop friendly!",
            "Creeper? Aw man!",
            "Herobrine removed!",
            "Also try Terraria!",
            "Unified Quad Dashboard!",
            "Redstone powered!",
            "Diamonds to you!"
    };

    private static final String BANNER =
            "  __  __                                \n" +
            " |  \\/  |_   _ _ __ ___ ___  ___        \n" +
            " | |\\/| | | | | '__/ __/ _ \\/ __|       \n" +
            " | |  | | |_| | | | (_|  __/\\__ \\       \n" +
            " |_|  |_|\\__,_|_|  \\___\\___||___/  v1.2.0";

    private final MainWindow mainWindow;
    private final Panel root;
    private final MurcesListBox menuList;
    private final Map<Character, Runnable> hotkeys = new HashMap<>();

    public MainMenuView(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new Panel(new LinearLayout(Direction.VERTICAL));

        Label bannerLabel = new Label(BANNER);
        bannerLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
        root.addComponent(bannerLabel);

        String splash = SPLASHES[new Random().nextInt(SPLASHES.length)];
        Label splashLabel = new Label(" ★ " + splash + " ★ ");
        splashLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        root.addComponent(splashLabel);

        root.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        menuList = new MurcesListBox(new TerminalSize(48, 10));
        menuList.addItem(GlyphHelper.apply("1.  " + GlyphHelper.ICON_SERVER + " [S]erver Control & Console"), mainWindow::showServerControl);
        menuList.addItem(GlyphHelper.apply("2.  " + GlyphHelper.ICON_PACKAGE + " [I]nstall Server Engine"), mainWindow::showInstallServer);
        menuList.addItem(GlyphHelper.apply("3.  " + GlyphHelper.ICON_CONFIG + " [C]onfigure Properties"), mainWindow::showConfigServer);
        menuList.addItem(GlyphHelper.apply("4.  " + GlyphHelper.ICON_SAVE + " [B]ackups (World)"), mainWindow::showBackup);
        menuList.addItem(GlyphHelper.apply("5.  " + GlyphHelper.ICON_USER + " [P]layer UUID Migration"), mainWindow::showMigratePlayer);
        menuList.addItem(GlyphHelper.apply("6.  " + GlyphHelper.ICON_DOWNLOAD + " [D]ownload & Browse Mods"), mainWindow::showModBrowse);
        menuList.addItem(GlyphHelper.apply("7.  " + GlyphHelper.ICON_MOD + " [M]anage Installed Mods"), mainWindow::showModManage);
        menuList.addItem(GlyphHelper.apply("8.  " + GlyphHelper.ICON_THEME + " [Z] Customization & Themes"), mainWindow::showCustomization);
        menuList.addItem(GlyphHelper.apply("9.  " + GlyphHelper.ICON_TASKS + " [J] Active Tasks & Job Manager"), mainWindow::showJobManager);
        menuList.addItem(GlyphHelper.apply("10. " + GlyphHelper.ICON_POWER + " [E]xit Murces"), mainWindow::exit);

        root.addComponent(menuList.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_OPTIONS + " Main Navigation (Enter to select)"))));

        Runnable openSvCtrlItem = () -> { menuList.takeFocus(); menuList.setSelectedIndex(0); mainWindow.showServerControl(); };
        Runnable openInstallItem = () -> { menuList.takeFocus(); menuList.setSelectedIndex(1); mainWindow.showInstallServer(); };
        Runnable openConfigItem = () -> { menuList.takeFocus(); menuList.setSelectedIndex(2); mainWindow.showConfigServer(); };
        Runnable openBackupItem = () -> { menuList.takeFocus(); menuList.setSelectedIndex(3); mainWindow.showBackup(); };
        Runnable openMigrateItem = () -> { menuList.takeFocus(); menuList.setSelectedIndex(4); mainWindow.showMigratePlayer(); };
        Runnable openBrowseItem = () -> { menuList.takeFocus(); menuList.setSelectedIndex(5); mainWindow.showModBrowse(); };
        Runnable openManageItem = () -> { menuList.takeFocus(); menuList.setSelectedIndex(6); mainWindow.showModManage(); };
        Runnable openCustomizationItem = () -> { menuList.takeFocus(); menuList.setSelectedIndex(7); mainWindow.showCustomization(); };
        Runnable openJobManagerItem = () -> { menuList.takeFocus(); menuList.setSelectedIndex(8); mainWindow.showJobManager(); };
        Runnable doExitItem = () -> { menuList.takeFocus(); menuList.setSelectedIndex(9); mainWindow.exit(); };

        hotkeys.put('S', openSvCtrlItem);
        hotkeys.put('1', openSvCtrlItem);
        hotkeys.put('I', openInstallItem);
        hotkeys.put('2', openInstallItem);
        hotkeys.put('C', openConfigItem);
        hotkeys.put('3', openConfigItem);
        hotkeys.put('B', openBackupItem);
        hotkeys.put('4', openBackupItem);
        hotkeys.put('P', openMigrateItem);
        hotkeys.put('5', openMigrateItem);
        hotkeys.put('D', openBrowseItem);
        hotkeys.put('6', openBrowseItem);
        hotkeys.put('M', openManageItem);
        hotkeys.put('7', openManageItem);
        hotkeys.put('Z', openCustomizationItem);
        hotkeys.put('8', openCustomizationItem);
        hotkeys.put('J', openJobManagerItem);
        hotkeys.put('9', openJobManagerItem);
        hotkeys.put('E', doExitItem);
        hotkeys.put('0', doExitItem);
        hotkeys.put('Q', doExitItem);
    }

    @Override
    public String getTitle() {
        return "Main Menu";
    }

    @Override
    public Component getComponent() {
        return root;
    }

    @Override
    public Map<Character, Runnable> getHotkeys() {
        return hotkeys;
    }

    @Override
    public Interactable getDefaultFocus() {
        return menuList;
    }

    @Override
    public void onResized(TerminalSize newSize) {
        if (newSize == null) return;
        int cols = newSize.getColumns();
        int rows = newSize.getRows();
        int actWidth = Math.max(28, Math.min(65, (cols * 35) / 100));
        int wsWidth = Math.max(44, cols - actWidth - 6);

        int listWidth = Math.max(48, wsWidth - 4);
        int listHeight = Math.max(10, Math.min(12, rows - 14));
        menuList.setPreferredSize(new TerminalSize(listWidth, listHeight));
    }
}
