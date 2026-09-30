package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MigratePlayerWindow extends BasicWindow {

    private final WindowBasedTextGUI gui;
    private final TextBox oldNameBox;
    private final TextBox newNameBox;
    private final TextBox logBox;
    private final Label statusLabel;

    public MigratePlayerWindow(WindowBasedTextGUI gui) {
        super("Migrate Player Data - Murces");
        this.gui = gui;
        setHints(Arrays.asList(Hint.CENTERED, Hint.FIT_TERMINAL_WINDOW));

        Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
        root.setPreferredSize(new TerminalSize(74, 21));

        // Tooltip at top
        root.addComponent(KeyboardNavigationHelper.createTooltip());

        // Form
        Panel form = new Panel(new GridLayout(2));
        form.addComponent(new Label("Old Username / UUID:"));
        oldNameBox = new TextBox(new TerminalSize(25, 1));
        form.addComponent(oldNameBox);

        form.addComponent(new Label("New Username / UUID:"));
        newNameBox = new TextBox(new TerminalSize(25, 1));
        form.addComponent(newNameBox);

        root.addComponent(form.withBorder(Borders.singleLine("Player Identity")));

        // Action
        Panel actionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button migrateBtn = new Button("Execute Migration", this::onMigrate);
        actionPanel.addComponent(migrateBtn);
        root.addComponent(actionPanel);

        statusLabel = new Label("Ready to migrate player stats and inventory.");
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        root.addComponent(statusLabel);

        // Output
        logBox = new TextBox(new TerminalSize(70, 4));
        logBox.setReadOnly(true);
        root.addComponent(new Label("Migration Output:"));
        root.addComponent(logBox);

        // Footer
        Panel footer = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button backBtn = new Button("Back to Main Menu", this::close);
        footer.addComponent(backBtn);
        root.addComponent(footer);

        // Hotkeys
        Map<Character, Runnable> hotkeys = new HashMap<>();
        hotkeys.put('O', oldNameBox::takeFocus);
        hotkeys.put('N', newNameBox::takeFocus);
        hotkeys.put('E', KeyboardNavigationHelper.focus(migrateBtn, this::onMigrate));
        hotkeys.put('B', KeyboardNavigationHelper.focus(backBtn, this::close));
        KeyboardNavigationHelper.attach(this, hotkeys);

        setComponent(root);
    }

    private void appendLog(String line) {
        String curr = logBox.getText();
        if (curr.isEmpty()) {
            logBox.setText(line);
        } else {
            logBox.setText(curr + "\n" + line);
        }
    }

    private void onMigrate() {
        String oldName = oldNameBox.getText().trim();
        String newName = newNameBox.getText().trim();

        if (oldName.isEmpty() || newName.isEmpty()) {
            MessageDialog.showMessageDialog(gui, "Input Error", "Both old and new usernames must be specified!", MessageDialogButton.OK);
            return;
        }

        statusLabel.setText("[BUSY] Migrating data from '" + oldName + "' to '" + newName + "'...");
        appendLog("[INFO] Starting player data migration: " + oldName + " -> " + newName);

        new Thread(() -> {
            OrchestratorBridge.ProcessResult res = OrchestratorBridge.migratePlayer(oldName, newName);
            gui.getGUIThread().invokeLater(() -> {
                if (res.exitCode == 0) {
                    statusLabel.setText("[OK:] Migration successful!");
                    statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
                    appendLog("[OK:] " + (res.output.isEmpty() ? "Migration finished." : res.output));
                    MessageDialog.showMessageDialog(gui, "Migration Complete", "Successfully migrated player data!", MessageDialogButton.OK);
                } else {
                    statusLabel.setText("[ERR:] Migration failed (code " + res.exitCode + ")");
                    statusLabel.setForegroundColor(MinecraftTheme.REDSTONE_RED);
                    appendLog("[ERR:] " + res.output);
                    MessageDialog.showMessageDialog(gui, "Migration Error", "Migration failed: " + res.output, MessageDialogButton.OK);
                }
            });
        }).start();
    }
}
