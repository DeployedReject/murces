package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.table.Table;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.theme.GlyphHelper;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class BackupView implements WorkspaceView {

    private final MainWindow mainWindow;
    private final Panel root;
    private final Table<String> table;
    private final Label statusLabel;
    private final Button backupNowBtn;
    private final Button deleteBtn;
    private final Button refreshBtn;
    private final Button backBtn;
    private final Map<Character, Runnable> hotkeys = new HashMap<>();
    private boolean confirmingDelete = false;
    private String backupToDelete = null;

    public BackupView(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new Panel(new LinearLayout(Direction.VERTICAL));

        Panel topPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        backupNowBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_SAVE + " [K] Backup Now"), this::onRunBackup);
        deleteBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [D]elete Backup"), this::onDeleteBackup);
        refreshBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_RESTART + " [R]efresh"), this::loadBackups);

        topPanel.addComponent(backupNowBtn);
        topPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        topPanel.addComponent(deleteBtn);
        topPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        topPanel.addComponent(refreshBtn);
        root.addComponent(topPanel);

        statusLabel = new Label(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " Ready."));
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        root.addComponent(statusLabel);

        table = new Table<>("Archive", "Size", "Date");
        table.setEscapeByArrowKey(false);
        table.setSelectAction(this::onDeleteBackup);
        table.setPreferredSize(new TerminalSize(42, 6));
        root.addComponent(table.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Backups [L]ist"))));

        root.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        backBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_BACK + " [B]ack to Main Menu"), mainWindow::showMainMenu);
        root.addComponent(backBtn);

        hotkeys.put('L', table::takeFocus);
        hotkeys.put('K', KeyboardNavigationHelper.focus(backupNowBtn, this::onRunBackup));
        hotkeys.put('D', KeyboardNavigationHelper.focus(deleteBtn, this::onDeleteBackup));
        hotkeys.put('R', KeyboardNavigationHelper.focus(refreshBtn, this::loadBackups));
        hotkeys.put('B', KeyboardNavigationHelper.focus(backBtn, mainWindow::showMainMenu));

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
        int tblHeight = Math.max(6, rows - 16);
        table.setPreferredSize(new TerminalSize(tblWidth, tblHeight));
    }

    @Override
    public void onActivated() {
        loadBackups();
    }

    private void loadBackups() {
        confirmingDelete = false;
        backupToDelete = null;
        table.getTableModel().clear();
        List<OrchestratorBridge.BackupInfo> backups = OrchestratorBridge.listBackups();
        if (backups.isEmpty()) {
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_FILE + " No backup archives found."));
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
        boolean deleted = OrchestratorBridge.deleteBackup(archiveName);
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
        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " [BUSY] Creating world backup..."));
        ActivityLogger.info("Starting world backup process...");

        new Thread(() -> {
            OrchestratorBridge.ProcessResult res = OrchestratorBridge.runBackup();
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
