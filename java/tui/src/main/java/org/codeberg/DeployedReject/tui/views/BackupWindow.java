package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.table.Table;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BackupWindow extends BasicWindow {

    private final WindowBasedTextGUI gui;
    private final Table<String> table;
    private final TextBox logBox;
    private final Label statusLabel;

    public BackupWindow(WindowBasedTextGUI gui) {
        super("World Backups - Murces");
        this.gui = gui;
        setHints(Arrays.asList(Hint.CENTERED, Hint.FIT_TERMINAL_WINDOW));

        Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
        root.setPreferredSize(new TerminalSize(74, 21));

        // Tooltip at top
        root.addComponent(KeyboardNavigationHelper.createTooltip());

        // Status / Top Actions
        Panel topPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button backupNowBtn = new Button("Backup World Now", this::onRunBackup);
        Button refreshBtn = new Button("Refresh List", this::loadBackups);

        topPanel.addComponent(backupNowBtn);
        topPanel.addComponent(new EmptySpace(new TerminalSize(2, 1)));
        topPanel.addComponent(refreshBtn);
        root.addComponent(topPanel);

        statusLabel = new Label("Ready.");
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        root.addComponent(statusLabel);

        // Table
        table = new Table<>("Backup Archive", "Size", "Created Date");
        table.setEscapeByArrowKey(false);
        table.setPreferredSize(new TerminalSize(70, 6));
        root.addComponent(table.withBorder(Borders.singleLine("Stored Backups [L]ist (Use Arrow keys to scroll)")));

        // Output Log
        logBox = new TextBox(new TerminalSize(70, 3));
        logBox.setReadOnly(true);
        root.addComponent(new Label("Backup Output:"));
        root.addComponent(logBox);

        // Footer
        Panel footer = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button backBtn = new Button("Back to Main Menu", this::close);
        footer.addComponent(backBtn);
        root.addComponent(footer);

        // Hotkeys
        Map<Character, Runnable> hotkeys = new HashMap<>();
        hotkeys.put('L', table::takeFocus);
        hotkeys.put('B', KeyboardNavigationHelper.focus(backupNowBtn, this::onRunBackup));
        hotkeys.put('R', KeyboardNavigationHelper.focus(refreshBtn, this::loadBackups));
        hotkeys.put('X', KeyboardNavigationHelper.focus(backBtn, this::close));
        KeyboardNavigationHelper.attach(this, hotkeys);

        setComponent(root);
        setFocusedInteractable(table);
        loadBackups();
    }

    private void appendLog(String line) {
        String curr = logBox.getText();
        if (curr.isEmpty()) {
            logBox.setText(line);
        } else {
            logBox.setText(curr + "\n" + line);
        }
    }

    private void loadBackups() {
        table.getTableModel().clear();
        List<OrchestratorBridge.BackupInfo> backups = OrchestratorBridge.listBackups();
        if (backups.isEmpty()) {
            statusLabel.setText("No backups found in ./backup directory.");
            statusLabel.setForegroundColor(MinecraftTheme.STONE_GRAY);
        } else {
            statusLabel.setText("Found " + backups.size() + " backup(s).");
            statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
            for (OrchestratorBridge.BackupInfo b : backups) {
                table.getTableModel().addRow(b.name, b.formattedSize(), b.formattedDate());
            }
        }
    }

    private void onRunBackup() {
        statusLabel.setText("[BUSY] Running backup process...");
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        appendLog("[INFO] Starting backup.sh...");

        new Thread(() -> {
            OrchestratorBridge.ProcessResult res = OrchestratorBridge.runBackup();
            gui.getGUIThread().invokeLater(() -> {
                if (res.exitCode == 0) {
                    statusLabel.setText("[OK:] Backup completed successfully!");
                    statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
                    appendLog("[OK:] " + (res.output.isEmpty() ? "Backup finished." : res.output));
                    MessageDialog.showMessageDialog(gui, "Backup Complete", "World backup completed successfully!", MessageDialogButton.OK);
                } else {
                    statusLabel.setText("[ERR:] Backup failed (code " + res.exitCode + ")");
                    statusLabel.setForegroundColor(MinecraftTheme.REDSTONE_RED);
                    appendLog("[ERR:] " + res.output);
                    MessageDialog.showMessageDialog(gui, "Backup Failed", "Backup failed with exit code: " + res.exitCode, MessageDialogButton.OK);
                }
                loadBackups();
            });
        }).start();
    }
}
