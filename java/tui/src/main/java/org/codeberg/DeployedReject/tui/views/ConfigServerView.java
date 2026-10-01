package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyType;
import org.codeberg.DeployedReject.tui.backend.ServerPropertiesManager;
import org.codeberg.DeployedReject.tui.backend.ServerPropertiesManager.PropertyDef;
import org.codeberg.DeployedReject.tui.theme.GlyphHelper;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.io.IOException;
import java.util.*;

public class ConfigServerView implements WorkspaceView {

    private final MainWindow mainWindow;
    private final Panel root;
    private final ServerPropertiesManager manager = new ServerPropertiesManager();
    private final MurcesListBox listBox;
    private final TextBox filterBox;
    private final TextBox valueInput;
    private final Button setValueBtn;
    private final Label statusLabel;
    private final Label descLabel;
    private final Button saveBtn;
    private final Button reloadBtn;
    private final Button resetBtn;
    private final Button backBtn;
    private final List<String> displayedKeys = new ArrayList<>();
    private final Map<Character, Runnable> hotkeys = new HashMap<>();
    private String currentCategory = "All";

    public ConfigServerView(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new Panel(new LinearLayout(Direction.VERTICAL));

        // 1. Properties List
        listBox = new MurcesListBox(new TerminalSize(42, 6));
        listBox.setSelectionListener(idx -> {
            if (idx >= 0 && idx < displayedKeys.size()) {
                updateDetailForSelected();
            }
        });

        // 2. Search filter & Category Row
        Panel topPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        topPanel.addComponent(new Label(GlyphHelper.apply("󰍉 [Q] Filter: ")));
        filterBox = new TextBox(new TerminalSize(16, 1));
        filterBox.setTextChangeListener((newText, changedByUserInteraction) -> refreshList(null));
        filterBox.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Escape || keyStroke.getKeyType() == KeyType.ArrowDown) {
                listBox.takeFocus();
                return false;
            }
            return true;
        });
        topPanel.addComponent(filterBox);

        topPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        Button catBtn = new Button(GlyphHelper.apply("󰋊 [C]at: All"), this::cycleCategory);
        topPanel.addComponent(catBtn);
        root.addComponent(topPanel);

        root.addComponent(listBox.withBorder(Borders.singleLine(GlyphHelper.apply("󰒓 Properties [L]ist (Enter toggles/cycles)"))));

        // 3. Inline Value Editor Row
        Panel editPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        editPanel.addComponent(new Label(GlyphHelper.apply("󰏫 [E]dit Val: ")));
        valueInput = new TextBox(new TerminalSize(18, 1));
        setValueBtn = new Button(GlyphHelper.apply("󰄬 Set"), this::onApplyValue);
        valueInput.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Escape) {
                listBox.takeFocus();
                return false;
            }
            if (keyStroke.getKeyType() == KeyType.Enter) {
                onApplyValue();
                listBox.takeFocus();
                return false;
            }
            return true;
        });
        editPanel.addComponent(valueInput);
        editPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        editPanel.addComponent(setValueBtn);
        root.addComponent(editPanel);

        // 4. Status & Description
        descLabel = new Label("Select a property to view or edit.");
        descLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        statusLabel = new Label(GlyphHelper.apply("󰄬 [OK] Ready."));
        root.addComponent(descLabel);
        root.addComponent(statusLabel);

        // 5. Actions
        Panel actionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        saveBtn = new Button(GlyphHelper.apply("󰆓 [S]ave"), this::onSave);
        reloadBtn = new Button(GlyphHelper.apply("󰑪 [R]eload"), this::onReload);
        resetBtn = new Button(GlyphHelper.apply("󰁯 Reset [D]efaults"), this::onResetDefaults);
        actionPanel.addComponent(saveBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(reloadBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(resetBtn);
        root.addComponent(actionPanel);

        // 6. Footer
        backBtn = new Button(GlyphHelper.apply("󰁯 [B]ack to Main Menu"), mainWindow::showMainMenu);
        root.addComponent(backBtn);

        // Hotkeys
        hotkeys.put('L', listBox::takeFocus);
        hotkeys.put('S', KeyboardNavigationHelper.focus(saveBtn, this::onSave));
        hotkeys.put('R', KeyboardNavigationHelper.focus(reloadBtn, this::onReload));
        hotkeys.put('D', KeyboardNavigationHelper.focus(resetBtn, this::onResetDefaults));
        hotkeys.put('E', valueInput::takeFocus);
        hotkeys.put('C', this::cycleCategory);
        hotkeys.put('Q', filterBox::takeFocus);
        hotkeys.put('/', filterBox::takeFocus);
        hotkeys.put('B', KeyboardNavigationHelper.focus(backBtn, mainWindow::showMainMenu));

        refreshList(null);
    }

    @Override
    public String getTitle() {
        return GlyphHelper.apply("󰒓 Configure Properties");
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
        return listBox;
    }

    @Override
    public void onResized(TerminalSize newSize) {
        if (newSize == null) return;
        int cols = newSize.getColumns();
        int rows = newSize.getRows();

        int actWidth = Math.max(28, Math.min(65, (cols * 35) / 100));
        int wsWidth = Math.max(44, cols - actWidth - 6);

        int listWidth = Math.max(38, wsWidth - 4);
        int listHeight = Math.max(7, rows - 18);
        listBox.setPreferredSize(new TerminalSize(listWidth, listHeight));
        valueInput.setPreferredSize(new TerminalSize(Math.max(18, listWidth - 20), 1));
    }

    @Override
    public void onActivated() {
        refreshList(null);
    }

    private void cycleCategory() {
        List<String> cats = ServerPropertiesManager.CATEGORIES;
        int idx = cats.indexOf(currentCategory);
        int next = (idx + 1) % cats.size();
        currentCategory = cats.get(next);
        statusLabel.setText(GlyphHelper.apply("󰄬 [OK] Category: " + currentCategory));
        refreshList(null);
    }

    private synchronized void refreshList(String preserveKey) {
        String query = filterBox != null ? filterBox.getText().trim().toLowerCase() : "";
        displayedKeys.clear();
        listBox.clearItems();

        List<String> allKeys = manager.getAllKeys();
        int targetIdx = -1;

        for (String key : allKeys) {
            PropertyDef def = manager.getDef(key);
            if (!"All".equals(currentCategory)) {
                String cat = def != null ? def.getCategory() : "Custom";
                if (!currentCategory.equalsIgnoreCase(cat)) continue;
            }
            if (!query.isEmpty() && !key.toLowerCase().contains(query)) continue;

            displayedKeys.add(key);
            String val = manager.get(key, def != null ? def.getDefaultValue() : "");
            String label = String.format("• %-22s = %s", key, val);

            final String thisKey = key;
            listBox.addItem(label, () -> onPropertyActivated(thisKey));

            if (preserveKey != null && preserveKey.equals(key)) {
                targetIdx = displayedKeys.size() - 1;
            }
        }

        if (displayedKeys.isEmpty()) {
            descLabel.setText("No properties match query.");
            valueInput.setText("");
        } else {
            if (targetIdx >= 0) {
                listBox.setSelectedIndex(targetIdx);
            } else if (listBox.getSelectedIndex() < 0 || listBox.getSelectedIndex() >= displayedKeys.size()) {
                listBox.setSelectedIndex(0);
            }
            updateDetailForSelected();
        }
    }

    private void updateDetailForSelected() {
        int sel = listBox.getSelectedIndex();
        if (sel >= 0 && sel < displayedKeys.size()) {
            String key = displayedKeys.get(sel);
            PropertyDef def = manager.getDef(key);
            String val = manager.get(key, def != null ? def.getDefaultValue() : "");
            valueInput.setText(val);
            if (def != null) {
                descLabel.setText("[" + def.getType() + "] " + def.getDescription());
            } else {
                descLabel.setText("[Custom Property] " + key);
            }
        }
    }

    private void onPropertyActivated(String key) {
        PropertyDef def = manager.getDef(key);
        String currentVal = manager.get(key, def != null ? def.getDefaultValue() : "");
        if (def != null && def.getType() == PropertyDef.Type.BOOLEAN) {
            String newVal = "true".equalsIgnoreCase(currentVal) ? "false" : "true";
            manager.set(key, newVal);
            ActivityLogger.ok("Toggled '" + key + "' -> " + newVal);
            refreshList(key);
        } else if (def != null && def.getType() == PropertyDef.Type.ENUM && !def.getOptions().isEmpty()) {
            List<String> opts = def.getOptions();
            int curIdx = opts.indexOf(currentVal);
            int nextIdx = (curIdx + 1) % opts.size();
            String newVal = opts.get(nextIdx);
            manager.set(key, newVal);
            ActivityLogger.ok("Cycled '" + key + "' -> " + newVal);
            refreshList(key);
        } else {
            valueInput.takeFocus();
        }
    }

    private void onApplyValue() {
        int sel = listBox.getSelectedIndex();
        if (sel >= 0 && sel < displayedKeys.size()) {
            String key = displayedKeys.get(sel);
            String newVal = valueInput.getText().trim();
            manager.set(key, newVal);
            ActivityLogger.ok("Updated '" + key + "' -> " + newVal);
            refreshList(key);
        }
    }

    private void onSave() {
        try {
            manager.save();
            ActivityLogger.ok("server.properties updated and saved successfully.");
            statusLabel.setText(GlyphHelper.apply("󰄬 [OK] Saved successfully."));
        } catch (IOException e) {
            ActivityLogger.err("Failed to save server.properties: " + e.getMessage());
            statusLabel.setText(GlyphHelper.apply("󰅖 [ERR] Save failed."));
        }
    }

    private void onReload() {
        manager.load();
        ActivityLogger.ok("server.properties reloaded from disk.");
        statusLabel.setText(GlyphHelper.apply("󰄬 [OK] Reloaded."));
        refreshList(null);
    }

    private void onResetDefaults() {
        manager.resetDefaults();
        ActivityLogger.ok("Default server properties restored.");
        statusLabel.setText(GlyphHelper.apply("󰄬 [OK] Reset defaults."));
        refreshList(null);
    }
}
