package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.ComboBox;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialog;

public class MinecraftVersionHelper {

    public static final String[] POPULAR_VERSIONS = {
        "1.21.4",
        "1.21.3",
        "1.21.2",
        "1.21.1",
        "1.21",
        "1.20.6",
        "1.20.4",
        "1.20.2",
        "1.20.1",
        "1.20",
        "1.19.4",
        "1.19.3",
        "1.19.2",
        "1.18.2",
        "1.17.1",
        "1.16.5",
        "1.15.2",
        "1.14.4",
        "1.13.2",
        "1.12.2",
        "1.8.9",
        "1.7.10",
        "Custom..."
    };

    public static ComboBox<String> createVersionComboBox(WindowBasedTextGUI gui, TerminalSize size) {
        ComboBox<String> comboBox = new ComboBox<>(POPULAR_VERSIONS);
        if (size != null) {
            comboBox.setPreferredSize(size);
        }
        comboBox.setDropDownNumberOfRows(8);
        comboBox.setSelectedItem("1.21.1");

        comboBox.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
            if (selectedIndex >= 0 && selectedIndex < comboBox.getItemCount()) {
                String selected = comboBox.getItem(selectedIndex);
                if ("Custom...".equals(selected)) {
                    gui.getGUIThread().invokeLater(() -> promptCustomVersion(gui, comboBox, previousSelection));
                }
            }
        });

        return comboBox;
    }

    public static void promptCustomVersion(WindowBasedTextGUI gui, ComboBox<String> comboBox, int fallbackSelection) {
        String custom = TextInputDialog.showDialog(gui, "Custom Version", "Enter custom Minecraft version (e.g. 1.20.3):", "");
        if (custom != null && !custom.trim().isEmpty()) {
            custom = custom.trim();

            int existingIdx = -1;
            for (int i = 0; i < comboBox.getItemCount(); i++) {
                if (comboBox.getItem(i).equalsIgnoreCase(custom)) {
                    existingIdx = i;
                    break;
                }
            }
            if (existingIdx >= 0) {
                comboBox.setSelectedIndex(existingIdx);
            } else {
                int customIndex = Math.max(0, comboBox.getItemCount() - 1);
                comboBox.addItem(customIndex, custom);
                comboBox.setSelectedIndex(customIndex);
            }
        } else {
            if (fallbackSelection >= 0 && fallbackSelection < comboBox.getItemCount()) {
                comboBox.setSelectedIndex(fallbackSelection);
            } else {
                comboBox.setSelectedItem("1.21.1");
            }
        }
    }

    public static void cycleVersion(ComboBox<String> comboBox) {
        int count = comboBox.getItemCount();
        if (count == 0) return;

        int maxPresets = count > 1 && "Custom...".equals(comboBox.getItem(count - 1)) ? count - 1 : count;
        int next = (comboBox.getSelectedIndex() + 1) % maxPresets;
        comboBox.setSelectedIndex(next);
    }

    public static String getSelectedVersion(ComboBox<String> comboBox) {
        String v = comboBox.getSelectedItem();
        if (v == null || v.trim().isEmpty() || "Custom...".equals(v)) {
            return "1.21.1";
        }
        return v.trim();
    }
}
