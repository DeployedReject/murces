package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyType;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.config.ConfigManager;
import org.codeberg.DeployedReject.tui.theme.LazyVimTheme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced Mod Browse & Download View with responsive layout scaling,
 * mod author and full-screen description display, compatible version picker,
 * Minecraft pickaxe dirt-breaking loading animation, and download finish ETA estimate.
 */
public class ModBrowseView implements WorkspaceView {

    private final MainWindow mainWindow;
    private final Panel root;
    private final ComboBox<String> platformBox;
    private final ComboBox<String> loaderBox;
    private final ComboBox<String> versionComboBox;
    private final TextBox searchBox;
    private final MurcesListBox resultsList;
    private final ComboBox<String> modVersionCombo;
    private final Label authorLabel;
    private final Label descLabel;
    private final Label statusLabel;
    private final MinecraftPickaxeAnimation pickaxeAnim;
    private final Panel animPanel;
    private final Button searchBtn;
    private final Button downloadBtn;
    private final Button backBtn;

    private final List<OrchestratorBridge.ModResult> currentResults = new ArrayList<>();
    private final List<OrchestratorBridge.ModVersionInfo> currentModVersions = new ArrayList<>();
    private final Map<Character, Runnable> hotkeys = new HashMap<>();

    private OrchestratorBridge.ModResult selectedMod = null;
    private boolean isDownloading = false;
    private int currentTermWidth = 80;

    public ModBrowseView(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new Panel(new LinearLayout(Direction.VERTICAL));

        // 1. Filters row
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

        // 2. Search Bar row
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

        // 3. Status Label
        statusLabel = new Label("Type query, press [S] to search, [D] to download.");
        statusLabel.setForegroundColor(LazyVimTheme.getWarningColor());
        root.addComponent(statusLabel);

        // 4. Results List
        root.addComponent(resultsList.withBorder(Borders.singleLine("Results [L]ist (Enter or [D] to download)")));

        // 5. Mod Details & Version Picker Panel
        Panel detailsPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        Panel versionRow = new Panel(new LinearLayout(Direction.HORIZONTAL));
        versionRow.addComponent(new Label("Mod Version [K]: "));
        modVersionCombo = new ComboBox<>("[Latest Compatible]");
        versionRow.addComponent(modVersionCombo);
        detailsPanel.addComponent(versionRow);

        authorLabel = new Label("Author: -");
        authorLabel.setForegroundColor(LazyVimTheme.getAccentColor());
        detailsPanel.addComponent(authorLabel);

        descLabel = new Label("Description: Select a mod to view details.");
        descLabel.setForegroundColor(LazyVimTheme.getActivePalette().fg);
        detailsPanel.addComponent(descLabel);

        root.addComponent(detailsPanel.withBorder(Borders.singleLine("Mod Details & Version")));

        // 6. Minecraft Pickaxe Dirt-Breaking Animation & ETA Container
        pickaxeAnim = new MinecraftPickaxeAnimation();
        animPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        animPanel.addComponent(pickaxeAnim);
        root.addComponent(animPanel);
        animPanel.setVisible(false);

        // 7. Footer
        backBtn = new Button("[B]ack to Main Menu", mainWindow::showMainMenu);
        root.addComponent(backBtn);

        // Hotkeys
        hotkeys.put('L', resultsList::takeFocus);
        hotkeys.put('S', KeyboardNavigationHelper.focus(searchBtn, this::onSearch));
        hotkeys.put('D', KeyboardNavigationHelper.focus(downloadBtn, this::onDownload));
        hotkeys.put('Q', searchBox::takeFocus);
        hotkeys.put('/', searchBox::takeFocus);
        hotkeys.put('K', modVersionCombo::takeFocus);
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

    @Override
    public void onResized(TerminalSize newSize) {
        if (newSize == null) return;
        this.currentTermWidth = newSize.getColumns();
        int rows = newSize.getRows();

        // Responsive scaling on full screen
        if (currentTermWidth >= 100) {
            int listWidth = Math.min(68, currentTermWidth - 36);
            int listHeight = Math.max(6, Math.min(10, rows - 19));
            resultsList.setPreferredSize(new TerminalSize(listWidth, listHeight));
            descLabel.setVisible(true);
        } else {
            resultsList.setPreferredSize(new TerminalSize(42, 5));
            // In compact view, keep author and hide lengthy multi-line description
            descLabel.setVisible(false);
        }
        updateDetailsDisplay();
    }

    private void updateDetailsDisplay() {
        if (selectedMod == null) {
            authorLabel.setText("Author: -");
            descLabel.setText(currentTermWidth >= 100 ? "Select a mod to view full description." : "[Fullscreen to view description]");
            return;
        }

        String author = (selectedMod.author != null && !selectedMod.author.trim().isEmpty())
                ? selectedMod.author
                : "Unknown";

        if (currentTermWidth >= 100) {
            authorLabel.setText("Author: " + author);
            String desc = (selectedMod.description != null && !selectedMod.description.trim().isEmpty())
                    ? selectedMod.description.replace("\r", " ").replace("\n", " ").trim()
                    : "No description provided.";
            if (desc.length() > 220) {
                desc = desc.substring(0, 217) + "...";
            }
            descLabel.setText("Description: " + desc);
            descLabel.setVisible(true);
        } else {
            authorLabel.setText("Author: " + author + "  (Enlarge window to view description)");
            descLabel.setVisible(false);
        }
    }

    private void onModSelected(OrchestratorBridge.ModResult mod) {
        this.selectedMod = mod;
        updateDetailsDisplay();

        // Fetch compatible versions for the chosen game version
        String platform = platformBox.getSelectedItem().toLowerCase();
        if ("curseforge".equals(platform)) {
            platform = "curseForge";
        }
        String version = MinecraftVersionHelper.getSelectedVersion(versionComboBox);
        String loader = loaderBox.getSelectedItem();

        modVersionCombo.clearItems();
        modVersionCombo.addItem("[Latest Compatible]");
        modVersionCombo.setSelectedIndex(0);
        currentModVersions.clear();

        final String finalPlat = platform;
        new Thread(() -> {
            try {
                List<OrchestratorBridge.ModVersionInfo> versions = OrchestratorBridge.getInstance()
                        .getModVersions(finalPlat, mod.id, version, loader).get();
                mainWindow.getGui().getGUIThread().invokeLater(() -> {
                    currentModVersions.clear();
                    currentModVersions.addAll(versions);
                    for (OrchestratorBridge.ModVersionInfo v : versions) {
                        modVersionCombo.addItem(v.toString());
                    }
                });
            } catch (Exception ignored) {}
        }).start();
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
        statusLabel.setForegroundColor(LazyVimTheme.getWarningColor());
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
                    updateDetailsDisplay();

                    if (mods.isEmpty()) {
                        statusLabel.setText("No mods found matching query.");
                        statusLabel.setForegroundColor(LazyVimTheme.getMutedColor());
                        ActivityLogger.info("No mods found matching query: " + query);
                    } else {
                        statusLabel.setText("Found " + mods.size() + " mods. [L]ist / [K] Version / [D]ownload.");
                        statusLabel.setForegroundColor(LazyVimTheme.getSuccessColor());
                        ActivityLogger.ok("Found " + mods.size() + " mods for query: " + query);

                        for (OrchestratorBridge.ModResult m : mods) {
                            resultsList.addItem(m.name + (m.author.isEmpty() ? "" : " by " + m.author), () -> {
                                onModSelected(m);
                                onDownload();
                            });
                        }
                        if (!mods.isEmpty()) {
                            onModSelected(mods.get(0));
                        }
                        resultsList.takeFocus();
                    }
                });
            } catch (Exception e) {
                mainWindow.getGui().getGUIThread().invokeLater(() -> {
                    statusLabel.setText("[ERR] Search failed: " + e.getMessage());
                    statusLabel.setForegroundColor(LazyVimTheme.getErrorColor());
                    ActivityLogger.err("Mod search failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private void onDownload() {
        if (isDownloading) {
            statusLabel.setText("[WARN] Another download is already running.");
            return;
        }

        if (selectedMod == null && resultsList.getSelectedIndex() >= 0 && resultsList.getSelectedIndex() < currentResults.size()) {
            selectedMod = currentResults.get(resultsList.getSelectedIndex());
        }
        if (selectedMod == null) {
            statusLabel.setText("[WARN] Select a mod from the list first!");
            statusLabel.setForegroundColor(LazyVimTheme.getWarningColor());
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

        // Check if specific version chosen
        int verIndex = modVersionCombo.getSelectedIndex();
        OrchestratorBridge.ModVersionInfo chosenVer = null;
        if (verIndex > 0 && (verIndex - 1) < currentModVersions.size()) {
            chosenVer = currentModVersions.get(verIndex - 1);
        }

        boolean showAnimation = ConfigManager.getInstance().getConfig().isPickaxeAnimation();
        if (showAnimation) {
            animPanel.setVisible(true);
            pickaxeAnim.setProgress(0.0);
            pickaxeAnim.setCustomMessage("Starting download: " + mod.name + "...");
        }

        isDownloading = true;
        statusLabel.setText("[BUSY] Downloading " + mod.name + "...");
        statusLabel.setForegroundColor(LazyVimTheme.getWarningColor());
        ActivityLogger.info("Downloading mod: " + mod.name + (chosenVer != null ? " (" + chosenVer.versionNumber + ")" : ""));

        final String targetPlatform = platform;
        final OrchestratorBridge.ModVersionInfo specificVer = chosenVer;

        new Thread(() -> {
            try {
                if (specificVer != null && specificVer.downloadUrl != null && !specificVer.downloadUrl.isEmpty()) {
                    // Direct version file download
                    OrchestratorBridge.getInstance().downloadModDirect(specificVer.downloadUrl, specificVer.filename, info -> {
                        updateProgressUI(mod.name, info);
                    }).get();
                } else {
                    // Platform managed download
                    OrchestratorBridge.getInstance().downloadMod(targetPlatform, mod.id, version, loader, null, info -> {
                        updateProgressUI(mod.name, info);
                    }).get();
                }

                mainWindow.getGui().getGUIThread().invokeLater(() -> {
                    isDownloading = false;
                    statusLabel.setText("[OK] " + mod.name + " installed!");
                    statusLabel.setForegroundColor(LazyVimTheme.getSuccessColor());
                    if (showAnimation) {
                        pickaxeAnim.setProgress(100.0);
                        pickaxeAnim.setCustomMessage("[OK] " + mod.name + " downloaded and installed!");
                    }
                    ActivityLogger.ok("Mod " + mod.name + " downloaded and installed to mods/ folder!");
                });
            } catch (Exception e) {
                mainWindow.getGui().getGUIThread().invokeLater(() -> {
                    isDownloading = false;
                    statusLabel.setText("[ERR] Download failed: " + e.getMessage());
                    statusLabel.setForegroundColor(LazyVimTheme.getErrorColor());
                    if (showAnimation) {
                        pickaxeAnim.setCustomMessage("[ERR] " + e.getMessage());
                    }
                    ActivityLogger.err("Mod download failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private void updateProgressUI(String modName, OrchestratorBridge.DownloadProgressInfo info) {
        mainWindow.getGui().getGUIThread().invokeLater(() -> {
            String status = String.format("[BUSY] Downloading %.2f%% (ETA: %s @ %s)...",
                    info.percent, info.formattedEta(), info.formattedSpeed());
            statusLabel.setText(status);

            if (animPanel.isVisible()) {
                pickaxeAnim.setProgress(info.percent);
                pickaxeAnim.setCustomMessage(String.format("Downloading %s: %.2f%% (ETA: %s @ %s)",
                        modName, info.percent, info.formattedEta(), info.formattedSpeed()));
            }

            ActivityLogger.prog(String.format("Downloading %s... %.2f%% (ETA: %s @ %s)",
                    modName, info.percent, info.formattedEta(), info.formattedSpeed()));
        });
    }
}
