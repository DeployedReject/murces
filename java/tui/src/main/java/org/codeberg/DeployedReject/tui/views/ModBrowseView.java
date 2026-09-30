package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyType;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModBrowseView implements WorkspaceView {

    private final MainWindow mainWindow;
    private final Panel root;
    private final ComboBox<String> platformBox;
    private final ComboBox<String> loaderBox;
    private final ComboBox<String> versionComboBox;
    private final TextBox searchBox;
    private final MurcesListBox resultsList;
    private final Label statusLabel;
    private final Button searchBtn;
    private final Button downloadBtn;
    private final Button backBtn;
    private final List<OrchestratorBridge.ModResult> currentResults = new ArrayList<>();
    private final Map<Character, Runnable> hotkeys = new HashMap<>();
    private OrchestratorBridge.ModResult selectedMod = null;

    public ModBrowseView(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new Panel(new LinearLayout(Direction.VERTICAL));

        // Filters row
        Panel filterPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        filterPanel.addComponent(new Label("[P]lat:"));
        platformBox = new ComboBox<>("Modrinth", "CurseForge");
        filterPanel.addComponent(platformBox);

        filterPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        filterPanel.addComponent(new Label("L[o]ad:"));
        loaderBox = new ComboBox<>("fabric", "forge", "neoforge", "quilt");
        filterPanel.addComponent(loaderBox);

        filterPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        filterPanel.addComponent(new Label("[V]er:"));
        versionComboBox = MinecraftVersionHelper.createVersionComboBox(mainWindow.getGui(), new TerminalSize(10, 1));
        filterPanel.addComponent(versionComboBox);

        root.addComponent(filterPanel);

        // Search Bar row
        Panel searchPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        searchPanel.addComponent(new Label("[Q] Query: "));
        searchBox = new TextBox(new TerminalSize(16, 1), "jei");
        searchBtn = new Button("[S]earch", this::onSearch);
        downloadBtn = new Button("[D]ownload", this::onDownload);

        // Results list
        resultsList = new MurcesListBox(new TerminalSize(42, 6));

        searchBox.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Escape || keyStroke.getKeyType() == KeyType.ArrowDown) {
                resultsList.takeFocus();
                return false;
            }
            if (keyStroke.getKeyType() == KeyType.Enter) {
                onSearch();
                return false;
            }
            return true;
        });

        searchPanel.addComponent(searchBox);
        searchPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        searchPanel.addComponent(searchBtn);
        searchPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        searchPanel.addComponent(downloadBtn);
        root.addComponent(searchPanel);

        statusLabel = new Label("Type query, press [S] to search, [D] to download.");
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        root.addComponent(statusLabel);

        root.addComponent(resultsList.withBorder(Borders.singleLine("Results [L]ist (Enter or [D] to download)")));

        root.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        // Footer
        backBtn = new Button("[B]ack to Main Menu", mainWindow::showMainMenu);
        root.addComponent(backBtn);

        // Hotkeys
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
        hotkeys.put('B', KeyboardNavigationHelper.focus(backBtn, mainWindow::showMainMenu));
    }

    @Override
    public String getTitle() {
        return "Download & Browse Mods";
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
        return searchBtn;
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
        ActivityLogger.info("Searching " + platform + " for '" + query + "' (MC " + version + ", " + loader + ")");

        final String targetPlatform = platform;
        new Thread(() -> {
            try {
                List<OrchestratorBridge.ModResult> mods = OrchestratorBridge.getInstance().searchMods(targetPlatform, query, version, loader).get();
                mainWindow.getGui().getGUIThread().invokeLater(() -> {
                    currentResults.clear();
                    currentResults.addAll(mods);
                    resultsList.clearItems();
                    selectedMod = null;

                    if (mods.isEmpty()) {
                        statusLabel.setText("No mods found matching query.");
                        statusLabel.setForegroundColor(MinecraftTheme.STONE_GRAY);
                        ActivityLogger.info("No mods found matching query: " + query);
                    } else {
                        statusLabel.setText("Found " + mods.size() + " mods. [D] to download.");
                        statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
                        ActivityLogger.ok("Found " + mods.size() + " mods for query: " + query);

                        for (OrchestratorBridge.ModResult m : mods) {
                            resultsList.addItem(m.name + " (" + m.id + ")", () -> {
                                selectedMod = m;
                                onDownload();
                            });
                        }
                        resultsList.takeFocus();
                    }
                });
            } catch (Exception e) {
                mainWindow.getGui().getGUIThread().invokeLater(() -> {
                    statusLabel.setText("[ERR] Search failed: " + e.getMessage());
                    statusLabel.setForegroundColor(MinecraftTheme.REDSTONE_RED);
                    ActivityLogger.err("Mod search failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private void onDownload() {
        if (selectedMod == null && resultsList.getSelectedIndex() >= 0 && resultsList.getSelectedIndex() < currentResults.size()) {
            selectedMod = currentResults.get(resultsList.getSelectedIndex());
        }
        if (selectedMod == null) {
            statusLabel.setText("[WARN] Select a mod from the list first!");
            statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
            ActivityLogger.warn("Please select a mod from the results list before downloading.");
            return;
        }

        final OrchestratorBridge.ModResult mod = selectedMod;
        String platform = platformBox.getSelectedItem().toLowerCase();
        if ("curseforge".equals(platform)) {
            platform = "curseForge";
        }
        String version = MinecraftVersionHelper.getSelectedVersion(versionComboBox);
        String loader = loaderBox.getSelectedItem();

        statusLabel.setText("[BUSY] Downloading " + mod.name + "...");
        statusLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        ActivityLogger.info("Downloading mod: " + mod.name + " (" + mod.id + ")");

        final String targetPlatform = platform;
        new Thread(() -> {
            try {
                java.util.concurrent.atomic.AtomicInteger lastReported = new java.util.concurrent.atomic.AtomicInteger(-1);
                OrchestratorBridge.getInstance().downloadMod(targetPlatform, mod.id, version, loader, progress -> {
                    if (progress < 0) return;
                    int prev = lastReported.get();
                    if (prev == -1 || progress == 100 || progress >= prev + 5) {
                        if (lastReported.compareAndSet(prev, progress)) {
                            mainWindow.getGui().getGUIThread().invokeLater(() -> {
                                statusLabel.setText("[BUSY] Downloading (" + progress + "%)...");
                                ActivityLogger.prog("Downloading " + mod.name + "... " + progress + "%");
                            });
                        }
                    }
                }).get();

                mainWindow.getGui().getGUIThread().invokeLater(() -> {
                    statusLabel.setText("[OK] " + mod.name + " installed!");
                    statusLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
                    ActivityLogger.ok("Mod " + mod.name + " downloaded and installed to mods/ folder!");
                });
            } catch (Exception e) {
                mainWindow.getGui().getGUIThread().invokeLater(() -> {
                    statusLabel.setText("[ERR] Download failed: " + e.getMessage());
                    statusLabel.setForegroundColor(MinecraftTheme.REDSTONE_RED);
                    ActivityLogger.err("Mod download failed: " + e.getMessage());
                });
            }
        }).start();
    }
}
