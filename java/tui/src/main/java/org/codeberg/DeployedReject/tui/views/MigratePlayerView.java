package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialog;
import com.googlecode.lanterna.input.KeyType;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.backend.PlayerAccessManager;
import org.codeberg.DeployedReject.tui.backend.PlayerAccessManager.Entry;
import org.codeberg.DeployedReject.tui.theme.GlyphHelper;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;
import org.codeberg.DeployedReject.tui.theme.Themes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * View for managing Player Access (Whitelist, Blacklist/Bans) and Player UUID Migration.
 */
public class MigratePlayerView implements WorkspaceView {

    private final MainWindow mainWindow;
    private final Panel root;

    // Tabs: 0 = Whitelist, 1 = Blacklist / Bans, 2 = UUID Migration
    private int currentTab = 0;
    private final Panel tabHeaderPanel;
    private final Button whitelistTabBtn;
    private final Button blacklistTabBtn;
    private final Button migrateTabBtn;
    private final Panel tabContentPanel;

    // Tab 0: Whitelist components
    private final Panel whitelistPanel;
    private final CheckBox whitelistToggleCheck;
    private final MurcesListBox whitelistListBox;
    private final List<Entry> whitelistEntries = new ArrayList<>();
    private final Button addWhitelistBtn;
    private final Button removeWhitelistBtn;

    // Tab 1: Blacklist / Bans components
    private final Panel blacklistPanel;
    private final MurcesListBox blacklistListBox;
    private final List<Entry> blacklistEntries = new ArrayList<>();
    private final Button addBlacklistBtn;
    private final Button removeBlacklistBtn;

    // Tab 2: Migration components
    private final Panel migratePanel;
    private final TextBox oldNameBox;
    private final TextBox newNameBox;
    private final Button migrateBtn;

    private final Button backBtn;
    private final Label statusLabel;
    private final Map<Character, Runnable> hotkeys = new HashMap<>();

    public MigratePlayerView(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new Panel(new LinearLayout(Direction.VERTICAL));

        // Tab header
        tabHeaderPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        whitelistTabBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_USER + " [1] Whitelist"), () -> switchTab(0));
        blacklistTabBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [2] Blacklist / Bans"), () -> switchTab(1));
        migrateTabBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CONFIG + " [3] UUID Migration"), () -> switchTab(2));
        tabHeaderPanel.addComponent(whitelistTabBtn);
        tabHeaderPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        tabHeaderPanel.addComponent(blacklistTabBtn);
        tabHeaderPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        tabHeaderPanel.addComponent(migrateTabBtn);
        root.addComponent(tabHeaderPanel);

        statusLabel = new Label(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " Ready."));
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);

        tabContentPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        root.addComponent(tabContentPanel);

        // ==========================================
        // Build Tab 0: Whitelist
        // ==========================================
        whitelistPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        Panel wlTopPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        whitelistToggleCheck = new CheckBox("Whitelist Enabled (server.properties)");
        whitelistToggleCheck.setChecked(PlayerAccessManager.isWhitelistEnabled());
        whitelistToggleCheck.addListener(checked -> {
            try {
                PlayerAccessManager.setWhitelistEnabled(checked);
                ActivityLogger.ok("Whitelist is now " + (checked ? "ENABLED" : "DISABLED"));
                statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " Whitelist " + (checked ? "enabled" : "disabled") + "."));
            } catch (Exception e) {
                ActivityLogger.err("Failed to toggle whitelist: " + e.getMessage());
                statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " Error: " + e.getMessage()));
            }
        });
        wlTopPanel.addComponent(whitelistToggleCheck);
        whitelistPanel.addComponent(wlTopPanel);

        whitelistListBox = new MurcesListBox(new TerminalSize(44, 8));
        whitelistPanel.addComponent(whitelistListBox.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_USER + " Whitelisted Players (Name / UUID)"))));

        Panel wlActionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        addWhitelistBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CONFIG + " [A]dd Player"), this::promptAddWhitelist);
        removeWhitelistBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [R]emove Player"), this::removeSelectedWhitelist);
        wlActionPanel.addComponent(addWhitelistBtn);
        wlActionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        wlActionPanel.addComponent(removeWhitelistBtn);
        whitelistPanel.addComponent(wlActionPanel);

        // ==========================================
        // Build Tab 1: Blacklist / Bans
        // ==========================================
        blacklistPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        blacklistListBox = new MurcesListBox(new TerminalSize(44, 8));
        blacklistPanel.addComponent(blacklistListBox.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " Banned Players (Name / UUID / Reason)"))));

        Panel blActionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        addBlacklistBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [A]dd Ban"), this::promptAddBlacklist);
        removeBlacklistBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [P]ardon / Remove"), this::removeSelectedBlacklist);
        blActionPanel.addComponent(addBlacklistBtn);
        blActionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        blActionPanel.addComponent(removeBlacklistBtn);
        blacklistPanel.addComponent(blActionPanel);

        // ==========================================
        // Build Tab 2: Migration
        // ==========================================
        migratePanel = new Panel(new LinearLayout(Direction.VERTICAL));
        Panel form = new Panel(new GridLayout(2));
        form.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_USER + " Old [O] Name:")));
        oldNameBox = new TextBox(new TerminalSize(18, 1));
        form.addComponent(oldNameBox);

        form.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_USER + " New [N] Name:")));
        newNameBox = new TextBox(new TerminalSize(18, 1));
        form.addComponent(newNameBox);

        migrateBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_USER + " [M]igrate Player"), this::onMigrate);

        oldNameBox.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Enter || keyStroke.getKeyType() == KeyType.ArrowDown) {
                newNameBox.takeFocus();
                return false;
            }
            return true;
        });

        newNameBox.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Enter) {
                onMigrate();
                return false;
            }
            if (keyStroke.getKeyType() == KeyType.ArrowUp) {
                oldNameBox.takeFocus();
                return false;
            }
            if (keyStroke.getKeyType() == KeyType.ArrowDown) {
                migrateBtn.takeFocus();
                return false;
            }
            return true;
        });

        migratePanel.addComponent(form.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_USER + " Player Identity (Enter advances)"))));

        Panel migActionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        migActionPanel.addComponent(migrateBtn);
        migratePanel.addComponent(migActionPanel);

        // Status & Back
        root.addComponent(statusLabel);

        root.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        backBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_BACK + " [B]ack to Main Menu"), mainWindow::showMainMenu);
        root.addComponent(backBtn);

        // Register Global Hotkeys
        hotkeys.put('1', () -> switchTab(0));
        hotkeys.put('2', () -> switchTab(1));
        hotkeys.put('3', () -> switchTab(2));
        hotkeys.put('W', () -> switchTab(0));
        hotkeys.put('A', this::handleTabActionAdd);
        hotkeys.put('R', this::handleTabActionRemove);
        hotkeys.put('P', this::handleTabActionRemove);
        hotkeys.put('O', oldNameBox::takeFocus);
        hotkeys.put('N', newNameBox::takeFocus);
        hotkeys.put('M', KeyboardNavigationHelper.action(migrateBtn, this::onMigrate));
        hotkeys.put('B', KeyboardNavigationHelper.action(backBtn, mainWindow::showMainMenu));

        switchTab(0);
    }

    private void switchTab(int tab) {
        this.currentTab = tab;
        tabContentPanel.removeAllComponents();

        whitelistTabBtn.setEnabled(tab != 0);
        blacklistTabBtn.setEnabled(tab != 1);
        migrateTabBtn.setEnabled(tab != 2);

        if (tab == 0) {
            tabContentPanel.addComponent(whitelistPanel);
            refreshWhitelist();
            whitelistListBox.takeFocus();
        } else if (tab == 1) {
            tabContentPanel.addComponent(blacklistPanel);
            refreshBlacklist();
            blacklistListBox.takeFocus();
        } else {
            tabContentPanel.addComponent(migratePanel);
            oldNameBox.takeFocus();
        }
        mainWindow.invalidate();
    }

    private void refreshWhitelist() {
        whitelistToggleCheck.setChecked(PlayerAccessManager.isWhitelistEnabled());
        whitelistEntries.clear();
        whitelistEntries.addAll(PlayerAccessManager.getWhitelist());
        whitelistListBox.clearItems();
        for (Entry e : whitelistEntries) {
            whitelistListBox.addItem(GlyphHelper.apply(GlyphHelper.ICON_USER + " " + e.name + " (" + e.uuid + ")"), () -> {});
        }
        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " Whitelist: " + whitelistEntries.size() + " players listed."));
    }

    private void refreshBlacklist() {
        blacklistEntries.clear();
        blacklistEntries.addAll(PlayerAccessManager.getBlacklist());
        blacklistListBox.clearItems();
        for (Entry e : blacklistEntries) {
            String r = e.reason.isEmpty() ? "" : " - " + e.reason;
            blacklistListBox.addItem(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " " + e.name + " (" + e.uuid + ")" + r), () -> {});
        }
        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " Blacklist: " + blacklistEntries.size() + " players banned."));
    }

    private void promptAddWhitelist() {
        String input = TextInputDialog.showDialog(
            mainWindow.getGui(),
            "Add to Whitelist",
            "Enter Minecraft username or UUID to whitelist:",
            ""
        );
        if (input != null && !input.trim().isEmpty()) {
            try {
                PlayerAccessManager.addWhitelist(input.trim());
                ActivityLogger.ok("Added " + input.trim() + " to whitelist.");
                refreshWhitelist();
            } catch (Exception e) {
                ActivityLogger.err("Failed to add to whitelist: " + e.getMessage());
                MessageDialog.showMessageDialog(mainWindow.getGui(), "Error", e.getMessage(), MessageDialogButton.OK);
            }
        }
    }

    private void removeSelectedWhitelist() {
        int idx = whitelistListBox.getSelectedIndex();
        if (idx >= 0 && idx < whitelistEntries.size()) {
            Entry target = whitelistEntries.get(idx);
            try {
                PlayerAccessManager.removeWhitelist(target.name.isEmpty() ? target.uuid : target.name);
                ActivityLogger.ok("Removed " + target.name + " from whitelist.");
                refreshWhitelist();
            } catch (Exception e) {
                ActivityLogger.err("Failed to remove from whitelist: " + e.getMessage());
                MessageDialog.showMessageDialog(mainWindow.getGui(), "Error", e.getMessage(), MessageDialogButton.OK);
            }
        } else {
            promptRemoveWhitelistManual();
        }
    }

    private void promptRemoveWhitelistManual() {
        String input = TextInputDialog.showDialog(
            mainWindow.getGui(),
            "Remove from Whitelist",
            "Enter Minecraft username or UUID to remove:",
            ""
        );
        if (input != null && !input.trim().isEmpty()) {
            try {
                PlayerAccessManager.removeWhitelist(input.trim());
                ActivityLogger.ok("Removed " + input.trim() + " from whitelist.");
                refreshWhitelist();
            } catch (Exception e) {
                ActivityLogger.err("Failed to remove from whitelist: " + e.getMessage());
                MessageDialog.showMessageDialog(mainWindow.getGui(), "Error", e.getMessage(), MessageDialogButton.OK);
            }
        }
    }

    private void promptAddBlacklist() {
        String name = TextInputDialog.showDialog(
            mainWindow.getGui(),
            "Add Player Ban (Blacklist)",
            "Enter Minecraft username or UUID to ban:",
            ""
        );
        if (name != null && !name.trim().isEmpty()) {
            String reason = TextInputDialog.showDialog(
                mainWindow.getGui(),
                "Ban Reason",
                "Enter ban reason (optional):",
                "Banned by operator"
            );
            try {
                PlayerAccessManager.addBlacklist(name.trim(), reason != null ? reason.trim() : "Banned by operator");
                ActivityLogger.ok("Banned player " + name.trim() + " (" + reason + ")");
                refreshBlacklist();
            } catch (Exception e) {
                ActivityLogger.err("Failed to ban player: " + e.getMessage());
                MessageDialog.showMessageDialog(mainWindow.getGui(), "Error", e.getMessage(), MessageDialogButton.OK);
            }
        }
    }

    private void removeSelectedBlacklist() {
        int idx = blacklistListBox.getSelectedIndex();
        if (idx >= 0 && idx < blacklistEntries.size()) {
            Entry target = blacklistEntries.get(idx);
            try {
                PlayerAccessManager.removeBlacklist(target.name.isEmpty() ? target.uuid : target.name);
                ActivityLogger.ok("Pardoned / unbanned " + target.name);
                refreshBlacklist();
            } catch (Exception e) {
                ActivityLogger.err("Failed to unban: " + e.getMessage());
                MessageDialog.showMessageDialog(mainWindow.getGui(), "Error", e.getMessage(), MessageDialogButton.OK);
            }
        } else {
            promptRemoveBlacklistManual();
        }
    }

    private void promptRemoveBlacklistManual() {
        String input = TextInputDialog.showDialog(
            mainWindow.getGui(),
            "Pardon Player (Unban)",
            "Enter Minecraft username or UUID to pardon:",
            ""
        );
        if (input != null && !input.trim().isEmpty()) {
            try {
                PlayerAccessManager.removeBlacklist(input.trim());
                ActivityLogger.ok("Pardoned player " + input.trim());
                refreshBlacklist();
            } catch (Exception e) {
                ActivityLogger.err("Failed to unban: " + e.getMessage());
                MessageDialog.showMessageDialog(mainWindow.getGui(), "Error", e.getMessage(), MessageDialogButton.OK);
            }
        }
    }

    private void handleTabActionAdd() {
        if (currentTab == 0) promptAddWhitelist();
        else if (currentTab == 1) promptAddBlacklist();
    }

    private void handleTabActionRemove() {
        if (currentTab == 0) removeSelectedWhitelist();
        else if (currentTab == 1) removeSelectedBlacklist();
    }

    @Override
    public String getTitle() {
        return GlyphHelper.apply(GlyphHelper.ICON_USER + " Player Access & Migration");
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
        if (currentTab == 0) return whitelistListBox;
        if (currentTab == 1) return blacklistListBox;
        return oldNameBox;
    }

    @Override
    public void onActivated() {
        if (currentTab == 0) refreshWhitelist();
        else if (currentTab == 1) refreshBlacklist();
    }

    @Override
    public void onResized(TerminalSize newSize) {
        if (newSize == null) return;
        int cols = newSize.getColumns();
        int actWidth = Math.max(28, Math.min(65, (cols * 35) / 100));
        int wsWidth = Math.max(44, cols - actWidth - 6);

        int listWidth = Math.max(38, wsWidth - 4);
        whitelistListBox.setPreferredSize(new TerminalSize(listWidth, 8));
        blacklistListBox.setPreferredSize(new TerminalSize(listWidth, 8));

        int inputWidth = Math.max(22, wsWidth - 26);
        oldNameBox.setPreferredSize(new TerminalSize(inputWidth, 1));
        newNameBox.setPreferredSize(new TerminalSize(inputWidth, 1));
    }

    private void onMigrate() {
        String oldName = oldNameBox.getText().trim();
        String newName = newNameBox.getText().trim();
        if (oldName.isEmpty() || newName.isEmpty()) {
            ActivityLogger.warn("Both old and new usernames must be specified for migration.");
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " [WARN] Enter both old and new names."));
            return;
        }

        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " [BUSY] Migrating player data..."));
        ActivityLogger.info("Starting player migration: " + oldName + " -> " + newName);

        new Thread(() -> {
            OrchestratorBridge.ProcessResult res = OrchestratorBridge.migratePlayer(oldName, newName);
            mainWindow.getGui().getGUIThread().invokeLater(() -> {
                if (res.exitCode == 0) {
                    statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [OK] Migration complete!"));
                    ActivityLogger.ok("Player migration completed successfully: " + oldName + " -> " + newName);
                } else {
                    statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [ERR] Migration failed."));
                    ActivityLogger.err("Migration error (code " + res.exitCode + "): " + res.output);
                }
            });
        }).start();
    }
}
