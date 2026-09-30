package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModManageWindow extends BasicWindow {

    private final WindowBasedTextGUI gui;
    private final MurcesListBox modsList;
    private final Label statusLabel;
    private String selectedModFile = null;

    public ModManageWindow(WindowBasedTextGUI gui) {
        super("Manage Installed Mods - Murces");
        this.gui = gui;
        setHints(Arrays.asList(Hint.CENTERED, Hint.FIT_TERMINAL_WINDOW));

        Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
        root.setPreferredSize(new TerminalSize(74, 21));

        // Tooltip at top
        root.addComponent(KeyboardNavigationHelper.createTooltip());

        // Actions
        Panel actionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button deleteBtn = new Button("Delete Selected Mod", this::onDeleteMod);
        Button refreshBtn = new Button("Refresh List", this::loadMods);

        actionPanel.addComponent(deleteBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(2, 1)));
        actionPanel.addComponent(refreshBtn);
        root.addComponent(actionPanel);

        statusLabel = new Label("Use Arrow keys to browse installed mods.");
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        root.addComponent(statusLabel);

        // Mods List (Arrow keys scroll here!)
        modsList = new MurcesListBox(new TerminalSize(70, 9));
        root.addComponent(modsList.withBorder(Borders.singleLine("Installed Mods [L]ist (mods/) - Use Arrow keys")));

        // Footer
        Panel footer = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button backBtn = new Button("Back to Main Menu", this::close);
        footer.addComponent(backBtn);
        root.addComponent(footer);

        // Hotkeys
        Map<Character, Runnable> hotkeys = new HashMap<>();
        hotkeys.put('L', modsList::takeFocus);
        hotkeys.put('D', KeyboardNavigationHelper.focus(deleteBtn, this::onDeleteMod));
        hotkeys.put('R', KeyboardNavigationHelper.focus(refreshBtn, this::loadMods));
        hotkeys.put('B', KeyboardNavigationHelper.focus(backBtn, this::close));
        KeyboardNavigationHelper.attach(this, hotkeys);

        setComponent(root);
        setFocusedInteractable(modsList);
        loadMods();
    }

    private void loadMods() {
        modsList.clearItems();
        selectedModFile = null;
        List<String> mods = OrchestratorBridge.listInstalledMods();
        if (mods.isEmpty()) {
            statusLabel.setText("No mods installed in ./mods directory.");
            statusLabel.setForegroundColor(MinecraftTheme.STONE_GRAY);
        } else {
            statusLabel.setText("Found " + mods.size() + " installed mod(s). Press [D] to delete selected.");
            statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);

            for (String file : mods) {
                modsList.addItem(file, () -> {
                    selectedModFile = file;
                    statusLabel.setText("Selected: " + file + " (Press [D] to delete)");
                    statusLabel.setForegroundColor(MinecraftTheme.DIAMOND_CYAN);
                });
            }
        }
    }

    private void onDeleteMod() {
        if (selectedModFile == null) {
            MessageDialog.showMessageDialog(gui, "No Selection", "Please select a mod file from the list first!", MessageDialogButton.OK);
            return;
        }

        MessageDialogButton choice = MessageDialog.showMessageDialog(
                gui,
                "Confirm Deletion",
                "Are you sure you want to delete '" + selectedModFile + "'?",
                MessageDialogButton.Yes,
                MessageDialogButton.No
        );

        if (choice == MessageDialogButton.Yes) {
            boolean ok = OrchestratorBridge.deleteMod(selectedModFile);
            if (ok) {
                MessageDialog.showMessageDialog(gui, "Deleted", "Mod '" + selectedModFile + "' deleted successfully.", MessageDialogButton.OK);
            } else {
                MessageDialog.showMessageDialog(gui, "Error", "Could not delete mod file.", MessageDialogButton.OK);
            }
            loadMods();
        }
    }
}
