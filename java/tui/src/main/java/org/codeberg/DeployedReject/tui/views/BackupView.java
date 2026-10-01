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
    private final Button refreshBtn;
    private final Button backBtn;
    private final Map<Character, Runnable> hotkeys = new HashMap<>();

    public BackupView(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new Panel(new LinearLayout(Direction.VERTICAL));

        // Top Actions
        Panel topPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        backupNowBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_SAVE + " [K] Backup Now"), this::onRunBackup);
        refreshBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_RESTART + " [R]efresh"), this::loadBackups);

        topPanel.addComponent(backupNowBtn);
        topPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        topPanel.addComponent(refreshBtn);
        root.addComponent(topPanel);

        statusLabel = new Label(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " Ready."));
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        root.addComponent(statusLabel);

        // Table
        table = new Table<>("Archive", "Size", "Date");
        table.setEscapeByArrowKey(false);
        table.setPreferredSize(new TerminalSize(42, 6));
        root.addComponent(table.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Backups [L]ist"))));

        root.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        // Footer
        backBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_BACK + " [B]ack to Main Menu"), mainWindow::showMainMenu);
        root.addComponent(backBtn);

        // Hotkeys
        hotkeys.put('L', table::takeFocus);
        hotkeys.put('K', KeyboardNavigationHelper.focus(backupNowBtn, this::onRunBackup));
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
        table.getTableModel().clear();
        List<OrchestratorBridge.BackupInfo> backups = OrchestratorBridge.listBackups();
        if (backups.isEmpty()) {
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_FILE + " No backup archives found."));
            return;
        }

        for (OrchestratorBridge.BackupInfo b : backups) {
            table.getTableModel().addRow(b.name, b.formattedSize(), b.formattedDate());
        }
        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Found " + backups.size() + " backup archive(s)."));
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
