package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.theme.GlyphHelper;
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

        Panel actionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        deleteBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [D]elete Mod"), this::onDeleteMod);
        refreshBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_RESTART + " [R]efresh"), this::loadMods);

        actionPanel.addComponent(deleteBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(refreshBtn);
        root.addComponent(actionPanel);

        statusLabel = new Label(GlyphHelper.apply(GlyphHelper.ICON_INFO + " Use Arrow keys to browse installed mods."));
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        root.addComponent(statusLabel);

        modsList = new MurcesListBox(new TerminalSize(42, 7));
        modsList.setSelectionListener(idx -> {
            List<String> mods = OrchestratorBridge.listInstalledMods();
            if (idx >= 0 && idx < mods.size()) {
                selectedModFile = mods.get(idx);
                confirmingDelete = false;
                statusLabel.setText("Selected: " + selectedModFile);
            }
        });
        root.addComponent(modsList.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_MOD + " Installed Mods [L]ist (mods/)"))));

        root.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        backBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_BACK + " [B]ack to Main Menu"), mainWindow::showMainMenu);
        root.addComponent(backBtn);

        hotkeys.put('L', modsList::takeFocus);
        hotkeys.put('D', KeyboardNavigationHelper.action(deleteBtn, this::onDeleteMod));
        hotkeys.put('R', KeyboardNavigationHelper.action(refreshBtn, this::loadMods));
        hotkeys.put('B', KeyboardNavigationHelper.action(backBtn, mainWindow::showMainMenu));

        loadMods();
    }

    @Override
    public String getTitle() {
        return GlyphHelper.apply(GlyphHelper.ICON_MOD + " Manage Installed Mods");
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

        int listWidth = Math.max(38, wsWidth - 4);
        int listHeight = Math.max(7, rows - 16);
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
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_FILE + " No mods installed in ./mods directory."));
            statusLabel.setForegroundColor(MinecraftTheme.STONE_GRAY);
            return;
        }

        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " Loaded " + mods.size() + " installed mod(s)."));
        statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);

        for (String m : mods) {
            modsList.addItem(GlyphHelper.apply(GlyphHelper.ICON_MOD + " " + m), () -> {
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
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " [WARN] Select a mod from the list first!"));
            statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
            ActivityLogger.warn("Please select a mod file before deleting.");
            return;
        }

        if (!confirmingDelete) {
            confirmingDelete = true;
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " [CONFIRM] Press [D] again to delete: " + selectedModFile));
            statusLabel.setForegroundColor(MinecraftTheme.REDSTONE_RED);
            ActivityLogger.warn("Confirm delete requested for mod: " + selectedModFile + " (press [D] to confirm)");
            return;
        }

        confirmingDelete = false;
        File f = new File("mods", selectedModFile);
        if (f.exists() && f.delete()) {
            ActivityLogger.ok("Mod '" + selectedModFile + "' deleted successfully.");
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [OK] Deleted: " + selectedModFile));
            statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
            loadMods();
        } else {
            ActivityLogger.err("Could not delete mod file: " + selectedModFile);
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [ERR] Could not delete mod file."));
            statusLabel.setForegroundColor(MinecraftTheme.REDSTONE_RED);
        }
    }
}
