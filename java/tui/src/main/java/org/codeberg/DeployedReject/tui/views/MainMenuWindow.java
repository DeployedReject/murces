package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.*;

public class MainMenuWindow extends BasicWindow {

    private static final String[] SPLASHES = {
            "Native AOT GraalVM binary!",
            "100% Older laptop friendly!",
            "Creeper? Aw man!",
            "Herobrine removed!",
            "Also try Terraria!",
            "Now with pure Lanterna TUI!",
            "Redstone powered!",
            "Diamonds to you!"
    };

    private static final String BANNER =
            "  __  __                                \n" +
            " |  \\/  |_   _ _ __ ___ ___  ___        \n" +
            " | |\\/| | | | | '__/ __/ _ \\/ __|       \n" +
            " | |  | | |_| | | | (_|  __/\\__ \\       \n" +
            " |_|  |_|\\__,_|_|  \\___\\___||___/       \n" +
            "    Minecraft Server Manager v1.0.0     ";

    private final WindowBasedTextGUI gui;
    private final TextBox logBox;

    public MainMenuWindow(WindowBasedTextGUI gui) {
        super("Murces - Minecraft Server Manager");
        this.gui = gui;
        setHints(Arrays.asList(Hint.CENTERED, Hint.FIT_TERMINAL_WINDOW));

        Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
        root.setPreferredSize(new TerminalSize(76, 21));

        // Tooltip at the very top
        root.addComponent(KeyboardNavigationHelper.createTooltip());

        // Banner
        Label bannerLabel = new Label(BANNER);
        bannerLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
        root.addComponent(bannerLabel);

        // Splash text
        String splash = SPLASHES[new Random().nextInt(SPLASHES.length)];
        Label splashLabel = new Label(" * " + splash + " * ");
        splashLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        root.addComponent(splashLabel);

        Runnable openSvCtrl = () -> gui.addWindowAndWait(new ServerControlWindow(gui));
        Runnable openInstall = () -> gui.addWindowAndWait(new InstallServerWindow(gui));
        Runnable openConfig = () -> gui.addWindowAndWait(new ConfigServerWindow(gui));
        Runnable openBackup = () -> gui.addWindowAndWait(new BackupWindow(gui));
        Runnable openMigrate = () -> gui.addWindowAndWait(new MigratePlayerWindow(gui));
        Runnable openBrowse = () -> gui.addWindowAndWait(new ModBrowseWindow(gui));
        Runnable openManage = () -> gui.addWindowAndWait(new ModManageWindow(gui));
        Runnable doExit = this::close;

        // Navigation list (User can use Arrow keys and Enter, or press capital hotkeys)
        MurcesListBox menuList = new MurcesListBox(new TerminalSize(70, 8));
        menuList.addItem("1. [S]erver Control & Console", openSvCtrl);
        menuList.addItem("2. [I]nstall Server Engine", openInstall);
        menuList.addItem("3. [C]onfigure Properties", openConfig);
        menuList.addItem("4. [B]ackups (World)", openBackup);
        menuList.addItem("5. [P]layer UUID Migration", openMigrate);
        menuList.addItem("6. [D]ownload & Browse Mods", openBrowse);
        menuList.addItem("7. [M]anage Installed Mods", openManage);
        menuList.addItem("8. [E]xit Murces", doExit);

        root.addComponent(menuList.withBorder(Borders.singleLine("Main Menu [L]ist (Use Arrow keys to browse, Enter to select)")));

        // Hotkey mappings
        Map<Character, Runnable> hotkeys = new HashMap<>();
        hotkeys.put('L', menuList::takeFocus);

        Runnable openSvCtrlItem = () -> { menuList.takeFocus(); menuList.setSelectedIndex(0); openSvCtrl.run(); };
        Runnable openInstallItem = () -> { menuList.takeFocus(); menuList.setSelectedIndex(1); openInstall.run(); };
        Runnable openConfigItem = () -> { menuList.takeFocus(); menuList.setSelectedIndex(2); openConfig.run(); };
        Runnable openBackupItem = () -> { menuList.takeFocus(); menuList.setSelectedIndex(3); openBackup.run(); };
        Runnable openMigrateItem = () -> { menuList.takeFocus(); menuList.setSelectedIndex(4); openMigrate.run(); };
        Runnable openBrowseItem = () -> { menuList.takeFocus(); menuList.setSelectedIndex(5); openBrowse.run(); };
        Runnable openManageItem = () -> { menuList.takeFocus(); menuList.setSelectedIndex(6); openManage.run(); };
        Runnable doExitItem = () -> { menuList.takeFocus(); menuList.setSelectedIndex(7); doExit.run(); };

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

        hotkeys.put('E', doExitItem);
        hotkeys.put('8', doExitItem);
        hotkeys.put('Q', doExitItem);

        KeyboardNavigationHelper.attach(this, hotkeys);

        // Live Log Viewer
        root.addComponent(new Label("Live Activity Log:"));
        logBox = new TextBox(new TerminalSize(72, 3));
        logBox.setReadOnly(true);
        logBox.setText("[OK:] Murces TUI initialized.\n[OK:] Ready.");
        root.addComponent(logBox);

        OrchestratorBridge.getInstance().addLogListener(msg -> {
            gui.getGUIThread().invokeLater(() -> {
                String cur = logBox.getText();
                String[] lines = cur.split("\n");
                StringBuilder sb = new StringBuilder();
                int start = Math.max(0, lines.length - 6);
                for (int i = start; i < lines.length; i++) {
                    sb.append(lines[i]).append("\n");
                }
                sb.append(msg);
                logBox.setText(sb.toString());
            });
        });

        setComponent(root);
        setFocusedInteractable(menuList);
    }
}
