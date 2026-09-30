package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.*;

public class ModBrowseWindow extends BasicWindow {

    private final WindowBasedTextGUI gui;
    private final ComboBox<String> platformBox;
    private final TextBox searchBox;
    private final ComboBox<String> versionComboBox;
    private final ComboBox<String> loaderBox;
    private final MurcesListBox resultsList;
    private final Label statusLabel;
    private final TextBox logBox;

    private final List<OrchestratorBridge.ModResult> currentResults = new ArrayList<>();
    private OrchestratorBridge.ModResult selectedMod = null;

    public ModBrowseWindow(WindowBasedTextGUI gui) {
        super("Browse & Install Mods - Murces");
        this.gui = gui;
        setHints(Arrays.asList(Hint.CENTERED, Hint.FIT_TERMINAL_WINDOW));

        Panel root = new Panel(new LinearLayout(Direction.VERTICAL));
        root.setPreferredSize(new TerminalSize(76, 21));

        // Tooltip at top
        root.addComponent(KeyboardNavigationHelper.createTooltip());

        // Results List
        resultsList = new MurcesListBox(new TerminalSize(72, 5));

        // Filters
        Panel filterGrid = new Panel(new GridLayout(4));

        filterGrid.addComponent(new Label("[P]latform:"));
        platformBox = new ComboBox<>("Modrinth", "CurseForge");
        platformBox.setPreferredSize(new TerminalSize(14, 1));
        filterGrid.addComponent(platformBox);

        filterGrid.addComponent(new Label("L[o]ader:"));
        loaderBox = new ComboBox<>("fabric", "forge", "neoforge", "quilt");
        loaderBox.setPreferredSize(new TerminalSize(14, 1));
        filterGrid.addComponent(loaderBox);

        filterGrid.addComponent(new Label("Minecraft [V]er:"));
        versionComboBox = MinecraftVersionHelper.createVersionComboBox(gui, new TerminalSize(14, 1));
        filterGrid.addComponent(versionComboBox);

        filterGrid.addComponent(new Label("[Q]uery (/):"));
        searchBox = new TextBox(new TerminalSize(16, 1));
        searchBox.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowDown) {
                resultsList.takeFocus();
                return false;
            }
            return true;
        });
        filterGrid.addComponent(searchBox);

        root.addComponent(filterGrid.withBorder(Borders.singleLine("Search Filters")));

        // Search & Download actions
        Panel actionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button searchBtn = new Button("Search Mods", this::onSearch);
        Button downloadBtn = new Button("Download Selected Mod", this::onDownload);

        actionPanel.addComponent(searchBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(2, 1)));
        actionPanel.addComponent(downloadBtn);
        root.addComponent(actionPanel);

        statusLabel = new Label("Press [Q] or [/] to type query, then [S] to search.");
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        root.addComponent(statusLabel);

        // Results List (Arrow keys scroll here!)
        root.addComponent(resultsList.withBorder(Borders.singleLine("Search Results [L]ist (Use Arrow keys to browse)")));

        // Output Log
        logBox = new TextBox(new TerminalSize(72, 2));
        logBox.setReadOnly(true);
        root.addComponent(logBox);

        // Footer
        Panel footer = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button backBtn = new Button("Back to Main Menu", this::close);
        footer.addComponent(backBtn);
        root.addComponent(footer);

        // Hotkeys
        Map<Character, Runnable> hotkeys = new HashMap<>();
        hotkeys.put('L', resultsList::takeFocus);
        hotkeys.put('S', KeyboardNavigationHelper.focus(searchBtn, this::onSearch));
        hotkeys.put('D', KeyboardNavigationHelper.focus(downloadBtn, this::onDownload));
        hotkeys.put('Q', searchBox::takeFocus);
        hotkeys.put('/', searchBox::takeFocus);
        hotkeys.put('P', KeyboardNavigationHelper.focus(platformBox, () -> {
            int next = (platformBox.getSelectedIndex() + 1) % platformBox.getItemCount();
            platformBox.setSelectedIndex(next);
        }));
        hotkeys.put('O', KeyboardNavigationHelper.focus(loaderBox, () -> {
            int next = (loaderBox.getSelectedIndex() + 1) % loaderBox.getItemCount();
            loaderBox.setSelectedIndex(next);
        }));
        hotkeys.put('V', KeyboardNavigationHelper.focus(versionComboBox, () -> MinecraftVersionHelper.cycleVersion(versionComboBox)));
        hotkeys.put('B', KeyboardNavigationHelper.focus(backBtn, this::close));
        KeyboardNavigationHelper.attach(this, hotkeys);

        // Pressing Enter in searchBox triggers search
        addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onInput(Window basePane, com.googlecode.lanterna.input.KeyStroke keyStroke, java.util.concurrent.atomic.AtomicBoolean deliver) {
                if (keyStroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Enter && basePane.getFocusedInteractable() == searchBox) {
                    deliver.set(false);
                    onSearch();
                }
            }
        });

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

    private void onSearch() {
        String query = searchBox.getText().trim();
        String platform = platformBox.getSelectedItem().toLowerCase();
        if ("curseforge".equals(platform)) {
            platform = "curseForge";
        }
        String version = MinecraftVersionHelper.getSelectedVersion(versionComboBox);
        String loader = loaderBox.getSelectedItem();

        statusLabel.setText("[BUSY] Searching " + platform + " for '" + query + "'...");
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        appendLog("[INFO] Querying " + platform + " API: " + query + " (MC " + version + ", " + loader + ")");

        final String targetPlatform = platform;
        new Thread(() -> {
            try {
                List<OrchestratorBridge.ModResult> mods = OrchestratorBridge.getInstance().searchMods(targetPlatform, query, version, loader).get();
                gui.getGUIThread().invokeLater(() -> {
                    currentResults.clear();
                    currentResults.addAll(mods);
                    resultsList.clearItems();
                    selectedMod = null;

                    if (mods.isEmpty()) {
                        statusLabel.setText("No mods found matching query.");
                        statusLabel.setForegroundColor(MinecraftTheme.STONE_GRAY);
                    } else {
                        statusLabel.setText("Found " + mods.size() + " mods. Use Arrow keys to select, [D] to download.");
                        statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);

                        for (OrchestratorBridge.ModResult m : mods) {
                            resultsList.addItem(m.name + " (" + m.id + ")", () -> {
                                selectedMod = m;
                                appendLog("[SELECTED] " + m.name);
                            });
                        }
                        resultsList.takeFocus();
                    }
                });
            } catch (Exception e) {
                gui.getGUIThread().invokeLater(() -> {
                    statusLabel.setText("[ERR:] Search failed: " + e.getMessage());
                    statusLabel.setForegroundColor(MinecraftTheme.REDSTONE_RED);
                    appendLog("[ERR:] " + e.getMessage());
                });
            }
        }).start();
    }

    private void onDownload() {
        if (selectedMod == null) {
            MessageDialog.showMessageDialog(gui, "No Selection", "Please click/select a mod from the results list first!", MessageDialogButton.OK);
            return;
        }

        String platform = platformBox.getSelectedItem().toLowerCase();
        if ("curseforge".equals(platform)) {
            platform = "curseForge";
        }
        String version = MinecraftVersionHelper.getSelectedVersion(versionComboBox);
        String loader = loaderBox.getSelectedItem();
        final String targetPlatform = platform;
        final OrchestratorBridge.ModResult mod = selectedMod;

        statusLabel.setText("[BUSY] Downloading " + mod.name + " (0%)...");
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        appendLog("[INFO] Starting download: " + mod.name + " [" + mod.id + "]");

        new Thread(() -> {
            try {
                OrchestratorBridge.getInstance().downloadMod(targetPlatform, mod.id, version, loader, progress -> {
                    gui.getGUIThread().invokeLater(() -> {
                        statusLabel.setText("[BUSY] Downloading " + mod.name + " (" + progress + "%)...");
                    });
                }).get();

                gui.getGUIThread().invokeLater(() -> {
                    statusLabel.setText("[OK:] Downloaded " + mod.name + " to mods/ directory!");
                    statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
                    appendLog("[OK:] Successfully downloaded " + mod.name);
                    MessageDialog.showMessageDialog(gui, "Download Complete", "Mod " + mod.name + " installed to mods/ folder!", MessageDialogButton.OK);
                });
            } catch (Exception e) {
                gui.getGUIThread().invokeLater(() -> {
                    statusLabel.setText("[ERR:] Download failed: " + e.getMessage());
                    statusLabel.setForegroundColor(MinecraftTheme.REDSTONE_RED);
                    appendLog("[ERR:] " + e.getMessage());
                    MessageDialog.showMessageDialog(gui, "Download Failed", "Failed: " + e.getMessage(), MessageDialogButton.OK);
                });
            }
        }).start();
    }
}
