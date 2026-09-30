package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyType;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.HashMap;
import java.util.Map;

public class MigratePlayerView implements WorkspaceView {

    private final MainWindow mainWindow;
    private final Panel root;
    private final TextBox oldNameBox;
    private final TextBox newNameBox;
    private final Button migrateBtn;
    private final Button backBtn;
    private final Label statusLabel;
    private final Map<Character, Runnable> hotkeys = new HashMap<>();

    public MigratePlayerView(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new Panel(new LinearLayout(Direction.VERTICAL));

        // Form
        Panel form = new Panel(new GridLayout(2));
        form.addComponent(new Label("Old [O] Name:"));
        oldNameBox = new TextBox(new TerminalSize(18, 1));
        form.addComponent(oldNameBox);

        form.addComponent(new Label("New [N] Name:"));
        newNameBox = new TextBox(new TerminalSize(18, 1));
        form.addComponent(newNameBox);

        migrateBtn = new Button("[M]igrate Player", this::onMigrate);

        oldNameBox.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Escape || keyStroke.getKeyType() == KeyType.ArrowDown) {
                newNameBox.takeFocus();
                return false;
            }
            return true;
        });

        newNameBox.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Escape || keyStroke.getKeyType() == KeyType.ArrowDown) {
                migrateBtn.takeFocus();
                return false;
            }
            return true;
        });

        root.addComponent(form.withBorder(Borders.singleLine("Player Identity")));

        // Action
        Panel actionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        actionPanel.addComponent(migrateBtn);
        root.addComponent(actionPanel);

        statusLabel = new Label("Ready to migrate player stats.");
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        root.addComponent(statusLabel);

        root.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        // Footer
        backBtn = new Button("[B]ack to Main Menu", mainWindow::showMainMenu);
        root.addComponent(backBtn);

        // Hotkeys
        hotkeys.put('O', oldNameBox::takeFocus);
        hotkeys.put('N', newNameBox::takeFocus);
        hotkeys.put('M', KeyboardNavigationHelper.focus(migrateBtn, this::onMigrate));
        hotkeys.put('B', KeyboardNavigationHelper.focus(backBtn, mainWindow::showMainMenu));
    }

    @Override
    public String getTitle() {
        return "Player UUID Migration";
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
        return migrateBtn;
    }

    @Override
    public void onResized(TerminalSize newSize) {
        if (newSize == null) return;
        int cols = newSize.getColumns();
        int actWidth = Math.max(28, Math.min(65, (cols * 35) / 100));
        int wsWidth = Math.max(44, cols - actWidth - 6);

        int inputWidth = Math.max(18, Math.min(36, wsWidth - 22));
        oldNameBox.setPreferredSize(new TerminalSize(inputWidth, 1));
        newNameBox.setPreferredSize(new TerminalSize(inputWidth, 1));
    }

    private void onMigrate() {
        String oldName = oldNameBox.getText().trim();
        String newName = newNameBox.getText().trim();
        if (oldName.isEmpty() || newName.isEmpty()) {
            ActivityLogger.warn("Both old and new usernames must be specified for migration.");
            statusLabel.setText("[WARN] Enter both old and new names.");
            return;
        }

        statusLabel.setText("[BUSY] Migrating player data...");
        ActivityLogger.info("Starting player migration: " + oldName + " -> " + newName);

        new Thread(() -> {
            OrchestratorBridge.ProcessResult res = OrchestratorBridge.migratePlayer(oldName, newName);
            mainWindow.getGui().getGUIThread().invokeLater(() -> {
                if (res.exitCode == 0) {
                    statusLabel.setText("[OK] Migration complete!");
                    ActivityLogger.ok("Player migration completed successfully: " + oldName + " -> " + newName);
                } else {
                    statusLabel.setText("[ERR] Migration failed.");
                    ActivityLogger.err("Migration error (code " + res.exitCode + "): " + res.output);
                }
            });
        }).start();
    }
}
