package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.input.KeyType;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.config.ConfigManager;
import org.codeberg.DeployedReject.tui.config.TuiConfig;
import org.codeberg.DeployedReject.tui.theme.GlyphHelper;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.*;

public class BackupView implements WorkspaceView {

    private final MainWindow mainWindow;
    private final Panel root;
    private final Table<String> table;
    private final Label statusLabel;
    private final Button backupNowBtn;
    private final Button deleteBtn;
    private final Button refreshBtn;
    private final Button saveOptionsBtn;
    private final Button backBtn;

    private final TextBox sourceFolderBox;
    private final TextBox targetFolderBox;
    private final ComboBox<String> retentionCombo;
    private final CheckBox cloudSyncCheck;
    private final TextBox cloudRemoteBox;

    private final Map<Character, Runnable> hotkeys = new HashMap<>();
    private boolean confirmingDelete = false;
    private String backupToDelete = null;

    public BackupView(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new Panel(new LinearLayout(Direction.VERTICAL));

        TuiConfig config = ConfigManager.getInstance().getConfig();

        Panel topPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        backupNowBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_SAVE + " [K] Backup Now"), this::onRunBackup);
        deleteBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [D]elete Backup"), this::onDeleteBackup);
        refreshBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_RESTART + " [R]efresh"), this::loadBackups);
        saveOptionsBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CONFIG + " [S]ave Options"), this::onSaveOptions);

        topPanel.addComponent(backupNowBtn);
        topPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        topPanel.addComponent(deleteBtn);
        topPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        topPanel.addComponent(refreshBtn);
        topPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        topPanel.addComponent(saveOptionsBtn);
        root.addComponent(topPanel);

        statusLabel = new Label(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " Ready."));
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        root.addComponent(statusLabel);

        Panel optionsPanel = new Panel(new LinearLayout(Direction.VERTICAL));

        Panel row1 = new Panel(new LinearLayout(Direction.HORIZONTAL));
        row1.addComponent(new Label("Source World: "));
        sourceFolderBox = new TextBox(new TerminalSize(12, 1), config.getBackupSourceFolder());
        row1.addComponent(sourceFolderBox);

        row1.addComponent(new EmptySpace(new TerminalSize(2, 1)));
        row1.addComponent(new Label("Target Dir: "));
        targetFolderBox = new TextBox(new TerminalSize(12, 1), config.getBackupTargetFolder());
        row1.addComponent(targetFolderBox);

        row1.addComponent(new EmptySpace(new TerminalSize(2, 1)));
        row1.addComponent(new Label("Retain Count: "));
        retentionCombo = new ComboBox<>("1", "2", "3", "5", "10", "20");
        retentionCombo.setPreferredSize(new TerminalSize(6, 1));
        String curRet = String.valueOf(config.getBackupRetentionLimit());
        for (int i = 0; i < retentionCombo.getItemCount(); i++) {
            if (retentionCombo.getItem(i).equals(curRet)) {
                retentionCombo.setSelectedIndex(i);
                break;
            }
        }
        row1.addComponent(retentionCombo);
        optionsPanel.addComponent(row1);

        Panel row2 = new Panel(new LinearLayout(Direction.HORIZONTAL));
        cloudSyncCheck = new CheckBox("[C] Cloud Sync (rclone)");
        cloudSyncCheck.setChecked(config.isBackupCloudSync());
        row2.addComponent(cloudSyncCheck);

        row2.addComponent(new EmptySpace(new TerminalSize(2, 1)));
        row2.addComponent(new Label("Remote: "));
        cloudRemoteBox = new TextBox(new TerminalSize(16, 1), config.getBackupCloudRemote());
        row2.addComponent(cloudRemoteBox);
        optionsPanel.addComponent(row2);

        sourceFolderBox.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Enter || keyStroke.getKeyType() == KeyType.ArrowDown) {
                targetFolderBox.takeFocus();
                return false;
            }
            return true;
        });

        targetFolderBox.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Enter || keyStroke.getKeyType() == KeyType.ArrowDown) {
                retentionCombo.takeFocus();
                return false;
            }
            return true;
        });

        retentionCombo.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Enter || keyStroke.getKeyType() == KeyType.ArrowDown) {
                cloudSyncCheck.takeFocus();
                return false;
            }
            return true;
        });

        cloudSyncCheck.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Enter) {
                cloudSyncCheck.setChecked(!cloudSyncCheck.isChecked());
                cloudRemoteBox.takeFocus();
                return false;
            }
            return true;
        });

        cloudRemoteBox.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Enter) {
                saveOptionsBtn.takeFocus();
                return false;
            }
            return true;
        });

        root.addComponent(optionsPanel.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_CONFIG + " Backup Configuration"))));

        table = new Table<>("Archive", "Size", "Date");
        table.setEscapeByArrowKey(false);
        table.setSelectAction(this::onDeleteBackup);
        table.setPreferredSize(new TerminalSize(42, 6));
        root.addComponent(table.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Backups [L]ist"))));

        root.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        backBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_BACK + " [B]ack to Main Menu"), mainWindow::showMainMenu);
        root.addComponent(backBtn);

        hotkeys.put('L', table::takeFocus);
        hotkeys.put('K', KeyboardNavigationHelper.action(backupNowBtn, this::onRunBackup));
        hotkeys.put('D', KeyboardNavigationHelper.action(deleteBtn, this::onDeleteBackup));
        hotkeys.put('R', KeyboardNavigationHelper.action(refreshBtn, this::loadBackups));
        hotkeys.put('S', KeyboardNavigationHelper.action(saveOptionsBtn, this::onSaveOptions));
        hotkeys.put('B', KeyboardNavigationHelper.action(backBtn, mainWindow::showMainMenu));

        loadBackups();
    }

    @Override
    public String getTitle() {
        return GlyphHelper.apply(GlyphHelper.ICON_SAVE + " World Backups");
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
        return table;
    }

    @Override
    public void onResized(TerminalSize newSize) {
        if (newSize == null) return;
        int cols = newSize.getColumns();
        int rows = newSize.getRows();

        int actWidth = Math.max(28, Math.min(65, (cols * 35) / 100));
        int wsWidth = Math.max(44, cols - actWidth - 6);

        int tblWidth = Math.max(38, wsWidth - 4);
        int tblHeight = Math.max(5, rows - 19);
        table.setPreferredSize(new TerminalSize(tblWidth, tblHeight));
    }

    @Override
    public void onActivated() {
        syncUiFromConfig();
        loadBackups();
    }

    private void syncUiFromConfig() {
        TuiConfig config = ConfigManager.getInstance().getConfig();
        sourceFolderBox.setText(config.getBackupSourceFolder());
        targetFolderBox.setText(config.getBackupTargetFolder());
        String curRet = String.valueOf(config.getBackupRetentionLimit());
        for (int i = 0; i < retentionCombo.getItemCount(); i++) {
            if (retentionCombo.getItem(i).equals(curRet)) {
                retentionCombo.setSelectedIndex(i);
                break;
            }
        }
        cloudSyncCheck.setChecked(config.isBackupCloudSync());
        cloudRemoteBox.setText(config.getBackupCloudRemote());
    }

    private void saveOptionsToConfig() {
        TuiConfig config = ConfigManager.getInstance().getConfig();
        config.setBackupSourceFolder(sourceFolderBox.getText().trim());
        config.setBackupTargetFolder(targetFolderBox.getText().trim());
        try {
            int ret = Integer.parseInt(retentionCombo.getSelectedItem());
            config.setBackupRetentionLimit(ret);
        } catch (Exception ignored) {}
        config.setBackupCloudSync(cloudSyncCheck.isChecked());
        config.setBackupCloudRemote(cloudRemoteBox.getText().trim());
        ConfigManager.getInstance().save();
    }

    private void onSaveOptions() {
        saveOptionsToConfig();
        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [OK] Backup options saved to murces.json."));
        statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
        ActivityLogger.ok("Backup configuration saved.");
        loadBackups();
    }

    private void loadBackups() {
        confirmingDelete = false;
        backupToDelete = null;
        table.getTableModel().clear();
        String targetDir = targetFolderBox != null ? targetFolderBox.getText().trim() : "backup";
        List<OrchestratorBridge.BackupInfo> backups = OrchestratorBridge.listBackups(targetDir);
        if (backups.isEmpty()) {
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_FILE + " No backup archives found in '" + targetDir + "'."));
            statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
            return;
        }

        for (OrchestratorBridge.BackupInfo b : backups) {
            table.getTableModel().addRow(b.name, b.formattedSize(), b.formattedDate());
        }
        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Found " + backups.size() + " backup archive(s)."));
        statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
    }

    private void onDeleteBackup() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= table.getTableModel().getRowCount()) {
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " [WARN] Select a backup from the list first!"));
            statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
            ActivityLogger.warn("Please select a backup archive before deleting.");
            return;
        }

        String archiveName = table.getTableModel().getCell(0, selectedRow);
        if (archiveName == null || archiveName.trim().isEmpty()) {
            return;
        }

        if (!confirmingDelete || !archiveName.equals(backupToDelete)) {
            confirmingDelete = true;
            backupToDelete = archiveName;
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " [CONFIRM] Press [D] again to delete: " + archiveName));
            statusLabel.setForegroundColor(MinecraftTheme.REDSTONE_RED);
            ActivityLogger.warn("Confirm delete requested for backup: " + archiveName + " (press [D] to confirm)");
            return;
        }

        confirmingDelete = false;
        backupToDelete = null;
        String targetDir = targetFolderBox != null ? targetFolderBox.getText().trim() : "backup";
        boolean deleted = OrchestratorBridge.deleteBackup(targetDir, archiveName);
        if (deleted) {
            ActivityLogger.ok("Backup '" + archiveName + "' deleted successfully.");
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [OK] Deleted: " + archiveName));
            statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
            loadBackups();
        } else {
            ActivityLogger.err("Could not delete backup file: " + archiveName);
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [ERR] Could not delete backup file."));
            statusLabel.setForegroundColor(MinecraftTheme.REDSTONE_RED);
        }
    }

    private void onRunBackup() {
        saveOptionsToConfig();
        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " [BUSY] Creating world backup..."));
        ActivityLogger.info("Starting world backup process...");

        String src = sourceFolderBox.getText().trim();
        String tgt = targetFolderBox.getText().trim();
        int ret = 3;
        try {
            ret = Integer.parseInt(retentionCombo.getSelectedItem());
        } catch (Exception ignored) {}
        boolean sync = cloudSyncCheck.isChecked();
        String remote = cloudRemoteBox.getText().trim();

        final int finalRet = ret;
        new Thread(() -> {
            OrchestratorBridge.ProcessResult res = OrchestratorBridge.runBackup(src, tgt, finalRet, sync, remote);
            mainWindow.getGui().getGUIThread().invokeLater(() -> {
                loadBackups();
                if (res.exitCode == 0) {
                    statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [OK] Backup created!"));
                    ActivityLogger.ok(res.output.isEmpty() ? "World backup completed successfully." : res.output);
                } else {
                    statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [ERR] Backup failed (code " + res.exitCode + ")"));
                    ActivityLogger.err("Backup failed: " + res.output);
                }
            });
        }).start();
    }
}
