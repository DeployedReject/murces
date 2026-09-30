package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModManageView implements WorkspaceView {

    private final MainWindow mainWindow;
    private final Panel root;
    private final MurcesListBox modsList;
    private final Label statusLabel;
    private final Button deleteBtn;
    private final Button refreshBtn;
    private final Button backBtn;
    private final Map<Character, Runnable> hotkeys = new HashMap<>();
    private String selectedModFile = null;
    private boolean confirmingDelete = false;

    public ModManageView(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new Panel(new LinearLayout(Direction.VERTICAL));

        // Top actions
        Panel actionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        deleteBtn = new Button("[D]elete Mod", this::onDeleteMod);
        refreshBtn = new Button("[R]efresh", this::loadMods);

        actionPanel.addComponent(deleteBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(refreshBtn);
        root.addComponent(actionPanel);

        statusLabel = new Label("Use Arrow keys to browse installed mods.");
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        root.addComponent(statusLabel);

        // Mods List
        modsList = new MurcesListBox(new TerminalSize(42, 7));
        modsList.setSelectionListener(idx -> {
            List<String> mods = OrchestratorBridge.listInstalledMods();
            if (idx >= 0 && idx < mods.size()) {
                selectedModFile = mods.get(idx);
                confirmingDelete = false;
                statusLabel.setText("Selected: " + selectedModFile);
            }
        });
        root.addComponent(modsList.withBorder(Borders.singleLine("Installed Mods [L]ist (mods/)")));

        root.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        // Footer
        backBtn = new Button("[B]ack to Main Menu", mainWindow::showMainMenu);
        root.addComponent(backBtn);

        // Hotkeys
        hotkeys.put('L', modsList::takeFocus);
        hotkeys.put('D', KeyboardNavigationHelper.focus(deleteBtn, this::onDeleteMod));
        hotkeys.put('R', KeyboardNavigationHelper.focus(refreshBtn, this::loadMods));
        hotkeys.put('B', KeyboardNavigationHelper.focus(backBtn, mainWindow::showMainMenu));

        loadMods();
    }

    @Override
    public String getTitle() {
        return "Manage Installed Mods";
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
        return modsList;
    }

    @Override
    public void onResized(TerminalSize newSize) {
        if (newSize == null) return;
        int cols = newSize.getColumns();
        int rows = newSize.getRows();

        int actWidth = Math.max(28, Math.min(65, (cols * 35) / 100));
        int wsWidth = Math.max(44, cols - actWidth - 6);

        int listWidth = Math.min(72, wsWidth - 4);
        int listHeight = Math.max(7, Math.min(14, rows - 16));
        modsList.setPreferredSize(new TerminalSize(listWidth, listHeight));
    }

    @Override
    public void onActivated() {
        loadMods();
    }

    private void loadMods() {
        confirmingDelete = false;
        modsList.clearItems();
        selectedModFile = null;
        List<String> mods = OrchestratorBridge.listInstalledMods();
        if (mods.isEmpty()) {
            statusLabel.setText("No mods installed in ./mods directory.");
            statusLabel.setForegroundColor(MinecraftTheme.STONE_GRAY);
            return;
        }

        statusLabel.setText("Loaded " + mods.size() + " installed mod(s).");
        statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);

        for (String m : mods) {
            modsList.addItem(m, () -> {
                selectedModFile = m;
                confirmingDelete = false;
                statusLabel.setText("Selected: " + m);
            });
        }
    }

    private void onDeleteMod() {
        if (selectedModFile == null && modsList.getSelectedIndex() >= 0) {
            List<String> mods = OrchestratorBridge.listInstalledMods();
            if (modsList.getSelectedIndex() < mods.size()) {
                selectedModFile = mods.get(modsList.getSelectedIndex());
            }
        }

        if (selectedModFile == null) {
            statusLabel.setText("[WARN] Select a mod from the list first!");
            statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
            ActivityLogger.warn("Please select a mod file before deleting.");
            return;
        }

        if (!confirmingDelete) {
            confirmingDelete = true;
            statusLabel.setText("[CONFIRM] Press [D] again to delete: " + selectedModFile);
            statusLabel.setForegroundColor(MinecraftTheme.REDSTONE_RED);
            ActivityLogger.warn("Confirm delete requested for mod: " + selectedModFile + " (press [D] to confirm)");
            return;
        }

        // Confirmed deletion
        confirmingDelete = false;
        File f = new File("mods", selectedModFile);
        if (f.exists() && f.delete()) {
            ActivityLogger.ok("Mod '" + selectedModFile + "' deleted successfully.");
            statusLabel.setText("[OK] Deleted: " + selectedModFile);
            statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
            loadMods();
        } else {
            ActivityLogger.err("Could not delete mod file: " + selectedModFile);
            statusLabel.setText("[ERR] Could not delete mod file.");
            statusLabel.setForegroundColor(MinecraftTheme.REDSTONE_RED);
        }
    }
}
