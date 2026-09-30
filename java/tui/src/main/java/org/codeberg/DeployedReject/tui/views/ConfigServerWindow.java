package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialog;
import com.googlecode.lanterna.input.KeyType;
import org.codeberg.DeployedReject.tui.backend.ServerPropertiesManager;
import org.codeberg.DeployedReject.tui.backend.ServerPropertiesManager.PropertyDef;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigServerWindow extends BasicWindow {

    private final WindowBasedTextGUI gui;
    private final ServerPropertiesManager manager = new ServerPropertiesManager();

    private final MurcesListBox listBox;
    private final TextBox filterBox;
    private final Label statusLabel;
    private final Label descLabel;

    private String currentCategory = "All";
    private String filterText = "";
    private final List<String> displayedKeys = new ArrayList<>();

    public ConfigServerWindow(WindowBasedTextGUI gui) {
        super("Server Properties Configuration - Murces");
        this.gui = gui;
        setHints(Arrays.asList(Hint.CENTERED, Hint.FIT_TERMINAL_WINDOW));

        Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
        root.setPreferredSize(new TerminalSize(76, 21));

        // Tooltip at top
        root.addComponent(KeyboardNavigationHelper.createTooltip());

        // Category Selection Bar
        Panel catPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        catPanel.addComponent(new Label("Category: "));
        catPanel.addComponent(new Button("All", () -> selectCategory("All")));
        catPanel.addComponent(new Button("Game", () -> selectCategory("Gameplay")));
        catPanel.addComponent(new Button("World", () -> selectCategory("World")));
        catPanel.addComponent(new Button("Net", () -> selectCategory("Network")));
        catPanel.addComponent(new Button("Perf", () -> selectCategory("Performance")));
        catPanel.addComponent(new Button("Sec", () -> selectCategory("Security")));
        catPanel.addComponent(new Button("Packs", () -> selectCategory("Resource Packs")));
        root.addComponent(catPanel);

        // Properties Menu List
        listBox = new MurcesListBox(new TerminalSize(72, 9));
        listBox.setSelectionListener(this::updateInfoForIndex);

        // Filter / Search Row
        Panel filterPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        filterPanel.addComponent(new Label("[Q]uery (/): "));
        filterBox = new TextBox(new TerminalSize(16, 1));
        filterBox.setTextChangeListener((newText, changedByUser) -> {
            filterText = newText.trim();
            refreshList(null);
        });
        filterBox.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Enter || keyStroke.getKeyType() == KeyType.ArrowDown) {
                listBox.takeFocus();
                return false;
            }
            return true;
        });
        filterPanel.addComponent(filterBox);
        filterPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        filterPanel.addComponent(new Label("Enter: toggle/cycle/edit | [E]dit Value"));
        root.addComponent(filterPanel);

        root.addComponent(listBox.withBorder(Borders.singleLine("Server Properties [L]ist (Arrow keys to browse, Enter to edit)")));

        // Status & Description Area
        descLabel = new Label("Description: Select a property with Arrow keys to view details.");
        statusLabel = new Label("[OK:] Ready. 77 properties loaded.");
        root.addComponent(descLabel);
        root.addComponent(statusLabel);

        // Action Buttons Row
        Panel actionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button saveBtn = new Button("Save Changes", this::onSave);
        Button reloadBtn = new Button("Reload from File", this::onReload);
        Button addBtn = new Button("Add Property", this::onAddCustom);
        Button resetBtn = new Button("Reset Defaults", this::onResetDefaults);

        actionPanel.addComponent(saveBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(reloadBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(addBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(resetBtn);
        root.addComponent(actionPanel);

        // Footer
        Panel footer = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button backBtn = new Button("Back to Main Menu", this::close);
        footer.addComponent(backBtn);
        root.addComponent(footer);

        // Initial populate and focus
        refreshList(null);
        setFocusedInteractable(listBox);

        // Hotkeys
        Map<Character, Runnable> hotkeys = new HashMap<>();
        hotkeys.put('L', listBox::takeFocus);
        hotkeys.put('S', KeyboardNavigationHelper.focus(saveBtn, this::onSave));
        hotkeys.put('R', KeyboardNavigationHelper.focus(reloadBtn, this::onReload));
        hotkeys.put('A', KeyboardNavigationHelper.focus(addBtn, this::onAddCustom));
        hotkeys.put('D', KeyboardNavigationHelper.focus(resetBtn, this::onResetDefaults));
        hotkeys.put('E', this::onEditValue);
        hotkeys.put('C', this::cycleCategory);
        hotkeys.put('Q', filterBox::takeFocus);
        hotkeys.put('/', filterBox::takeFocus);
        hotkeys.put('1', () -> selectCategory("All"));
        hotkeys.put('2', () -> selectCategory("Gameplay"));
        hotkeys.put('3', () -> selectCategory("World"));
        hotkeys.put('4', () -> selectCategory("Network"));
        hotkeys.put('5', () -> selectCategory("Performance"));
        hotkeys.put('6', () -> selectCategory("Security"));
        hotkeys.put('7', () -> selectCategory("Resource Packs"));
        hotkeys.put('B', KeyboardNavigationHelper.focus(backBtn, this::close));
        KeyboardNavigationHelper.attach(this, hotkeys);

        setComponent(root);
        setFocusedInteractable(listBox);
    }

    private void selectCategory(String cat) {
        this.currentCategory = cat;
        statusLabel.setText("[OK:] Switched to category: " + cat);
        refreshList(null);
    }

    private void cycleCategory() {
        List<String> cats = ServerPropertiesManager.CATEGORIES;
        int idx = cats.indexOf(currentCategory);
        int next = (idx + 1) % cats.size();
        selectCategory(cats.get(next));
    }

    private synchronized void refreshList(String preserveKey) {
        String selectedKey = preserveKey;
        if (selectedKey == null) {
            int sel = listBox.getSelectedIndex();
            if (sel >= 0 && sel < displayedKeys.size()) {
                selectedKey = displayedKeys.get(sel);
            }
        }

        displayedKeys.clear();
        listBox.clearItems();

        List<String> keys = manager.getKeysForCategory(currentCategory);
        for (String k : keys) {
            if (!filterText.isEmpty() && !k.toLowerCase().contains(filterText.toLowerCase())) {
                continue;
            }
            displayedKeys.add(k);
        }

        for (String key : displayedKeys) {
            PropertyDef def = manager.getDef(key);
            String val = manager.get(key, def != null ? def.getDefaultValue() : "");
            String rowText = formatRow(key, val, def);
            listBox.addItem(rowText, () -> onPropertyAction(key));
        }

        if (displayedKeys.isEmpty()) {
            listBox.addItem("  (No properties match current category / query)", () -> {});
            descLabel.setText("Description: (None)");
        } else {
            int targetIdx = 0;
            if (selectedKey != null) {
                int found = displayedKeys.indexOf(selectedKey);
                if (found >= 0) {
                    targetIdx = found;
                }
            }
            listBox.setSelectedIndex(targetIdx);
            updateInfoForIndex(targetIdx);
        }
    }

    private String formatRow(String key, String val, PropertyDef def) {
        String paddedKey = String.format("%-32s", key.length() > 32 ? key.substring(0, 31) : key);
        if (def != null && def.getType() == PropertyDef.Type.BOOLEAN) {
            String state = "true".equalsIgnoreCase(val) ? "[TRUE] " : "[FALSE]";
            return paddedKey + "= " + state + " (toggle)";
        } else if (def != null && def.getType() == PropertyDef.Type.ENUM) {
            String v = val.isEmpty() ? "<none>" : val;
            return paddedKey + "= [" + v + "] (cycle)";
        } else if (def != null && def.getType() == PropertyDef.Type.INTEGER) {
            String v = val.isEmpty() ? "<0>" : val;
            return paddedKey + "= [" + v + "] (number)";
        } else {
            String v = val.isEmpty() ? "<empty>" : (val.length() > 24 ? val.substring(0, 21) + "..." : val);
            return paddedKey + "= [" + v + "]";
        }
    }

    private void updateInfoForIndex(int idx) {
        if (idx >= 0 && idx < displayedKeys.size()) {
            String key = displayedKeys.get(idx);
            PropertyDef def = manager.getDef(key);
            if (def != null) {
                descLabel.setText(key + ": " + def.getDescription() + " [Default: " + def.getDefaultValue() + "]");
            } else {
                descLabel.setText(key + ": Custom server property");
            }
        }
    }

    private void onPropertyAction(String key) {
        PropertyDef def = manager.getDef(key);
        String currentVal = manager.get(key, def != null ? def.getDefaultValue() : "");

        if (def != null && def.getType() == PropertyDef.Type.BOOLEAN) {
            String newVal = "true".equalsIgnoreCase(currentVal) ? "false" : "true";
            manager.set(key, newVal);
            statusLabel.setText("[OK:] Toggled '" + key + "' -> " + newVal);
            refreshList(key);
        } else if (def != null && def.getType() == PropertyDef.Type.ENUM && !def.getOptions().isEmpty()) {
            List<String> opts = def.getOptions();
            int curIdx = opts.indexOf(currentVal);
            int nextIdx = (curIdx + 1) % opts.size();
            String newVal = opts.get(nextIdx);
            manager.set(key, newVal);
            statusLabel.setText("[OK:] Cycled '" + key + "' -> " + newVal);
            refreshList(key);
        } else {
            // Number, String, or Custom
            String desc = def != null ? def.getDescription() : "Custom property";
            String prompt = "Property: " + key + "\n" + desc + "\n\nEnter new value:";
            String newVal = TextInputDialog.showDialog(gui, "Edit " + key, prompt, currentVal);
            if (newVal != null) {
                manager.set(key, newVal.trim());
                statusLabel.setText("[OK:] Updated '" + key + "' -> " + newVal.trim());
                refreshList(key);
            }
        }
    }

    private void onEditValue() {
        int sel = listBox.getSelectedIndex();
        if (sel >= 0 && sel < displayedKeys.size()) {
            String key = displayedKeys.get(sel);
            PropertyDef def = manager.getDef(key);
            String currentVal = manager.get(key, def != null ? def.getDefaultValue() : "");
            String desc = def != null ? def.getDescription() : "Custom property";
            String prompt = "Property: " + key + "\n" + desc + "\n\nEnter new value:";
            String newVal = TextInputDialog.showDialog(gui, "Edit " + key, prompt, currentVal);
            if (newVal != null) {
                manager.set(key, newVal.trim());
                statusLabel.setText("[OK:] Updated '" + key + "' -> " + newVal.trim());
                refreshList(key);
            }
        }
    }

    private void onAddCustom() {
        String key = TextInputDialog.showDialog(gui, "Add Custom Property", "Enter custom property name (e.g. max-tick-length):", "");
        if (key != null && !key.trim().isEmpty()) {
            key = key.trim();
            String val = TextInputDialog.showDialog(gui, "Set Value", "Enter value for '" + key + "':", "");
            if (val != null) {
                manager.set(key, val.trim());
                statusLabel.setText("[OK:] Added custom property: " + key + "=" + val.trim());
                selectCategory("Custom");
                refreshList(key);
            }
        }
    }

    private void onResetDefaults() {
        MessageDialogButton result = MessageDialog.showMessageDialog(gui, "Reset Defaults",
                "Are you sure you want to reset ALL 77 properties to standard Minecraft defaults?",
                MessageDialogButton.Yes, MessageDialogButton.No);
        if (result == MessageDialogButton.Yes) {
            manager.resetDefaults();
            statusLabel.setText("[OK:] All properties reset to defaults. Press [S]ave to write.");
            refreshList(null);
        }
    }

    private void onSave() {
        try {
            manager.save();
            statusLabel.setText("[OK:] server.properties saved successfully.");
            MessageDialog.showMessageDialog(gui, "Saved", "server.properties updated and saved successfully!", MessageDialogButton.OK);
        } catch (IOException e) {
            statusLabel.setText("[ERROR:] Failed to save: " + e.getMessage());
            MessageDialog.showMessageDialog(gui, "Save Error", "Failed to save: " + e.getMessage(), MessageDialogButton.OK);
        }
    }

    private void onReload() {
        manager.load();
        statusLabel.setText("[OK:] Reloaded from server.properties file.");
        refreshList(null);
        MessageDialog.showMessageDialog(gui, "Reloaded", "server.properties reloaded successfully!", MessageDialogButton.OK);
    }
}
