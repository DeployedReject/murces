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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced Mod Browse & Download View with responsive 2-column layout,
 * live mod author and full description display, compatible version picker,
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
    private final Label titleAuthorLabel;
    private final Panel descPanel;
    private final Label statusLabel;
    private final MinecraftPickaxeAnimation pickaxeAnim;
    private final Panel detailsCard;
    private final Button searchBtn;
    private final Button downloadBtn;
    private final Button cancelBtn;
    private final Button backBtn;

    private final List<OrchestratorBridge.ModResult> currentResults = new ArrayList<>();
    private final List<OrchestratorBridge.ModVersionInfo> currentModVersions = new ArrayList<>();
    private final Map<Character, Runnable> hotkeys = new HashMap<>();

    private OrchestratorBridge.ModResult selectedMod = null;
    private boolean isDownloading = false;
    private int currentTermWidth = 80;
    private int descCardWidth = 46;
    private ScheduledExecutorService activeTicker = null;

    public ModBrowseView(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new Panel(new LinearLayout(Direction.VERTICAL));

        // 1. Top Filters row
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
        resultsList = new MurcesListBox(new TerminalSize(42, 10));

        searchBox.setInputFilter((interactable, keyStroke) -> {
            if (keyStroke.getKeyType() == KeyType.Escape || keyStroke.getKeyType() == KeyType.ArrowDown) {
                if (resultsList.getItemCount() > 0) {
                    resultsList.takeFocus();
                }
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
        searchPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        cancelBtn = new Button("[X] Cancel Download", this::cancelDownload);
        searchPanel.addComponent(cancelBtn);
        root.addComponent(searchPanel);

        // 3. Status Label
        statusLabel = new Label("Type query, press [S] to search, [D] to download, [X] to cancel.");
        statusLabel.setForegroundColor(LazyVimTheme.getLogWarnColor());
        root.addComponent(statusLabel);

        // 4. Middle 2-Column Section (filling space between list and activity log)
        Panel midCols = new Panel(new LinearLayout(Direction.HORIZONTAL));

        // Left Column: Results List
        Panel leftCol = new Panel(new LinearLayout(Direction.VERTICAL));
        leftCol.addComponent(resultsList.withBorder(Borders.singleLine("Results [L]ist (↑/↓)")));
        midCols.addComponent(leftCol);

        midCols.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        // Right Column: Mod Details, Version Picker, Multi-line Description, Pickaxe Animation
        Panel rightCol = new Panel(new LinearLayout(Direction.VERTICAL));
        detailsCard = new Panel(new LinearLayout(Direction.VERTICAL));

        titleAuthorLabel = new Label("Title: -\nAuthor: -");
        titleAuthorLabel.setForegroundColor(LazyVimTheme.getAccentColor());
        detailsCard.addComponent(titleAuthorLabel);

        Panel versionRow = new Panel(new LinearLayout(Direction.HORIZONTAL));
        versionRow.addComponent(new Label("Mod Version [K]: "));
        modVersionCombo = new ComboBox<>("[Latest Compatible]");
        versionRow.addComponent(modVersionCombo);
        detailsCard.addComponent(versionRow);

        descPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        descPanel.addComponent(new Label("Select a mod from the list to view its description."));
        detailsCard.addComponent(descPanel.withBorder(Borders.singleLine("Description")));

        pickaxeAnim = new MinecraftPickaxeAnimation();
        pickaxeAnim.setProgress(0.0);
        pickaxeAnim.setCustomMessage("Ready to download mods");
        detailsCard.addComponent(pickaxeAnim.withBorder(Borders.singleLine("Download Status")));

        detailsCard.setPreferredSize(new TerminalSize(48, 12));
        rightCol.addComponent(detailsCard.withBorder(Borders.singleLine("Mod Details & Version")));
        midCols.addComponent(rightCol);

        root.addComponent(midCols);

        // Instant arrow-key selection tracking: updates description and compatible versions immediately!
        resultsList.setSelectionListener(idx -> {
            if (idx >= 0 && idx < currentResults.size()) {
                mainWindow.getGui().getGUIThread().invokeLater(() -> {
                    onModSelected(currentResults.get(idx));
                });
            } else if (idx < 0) {
                mainWindow.getGui().getGUIThread().invokeLater(() -> {
                    selectedMod = null;
                    updateDetailsDisplay();
                });
            }
        });

        // 5. Footer
        backBtn = new Button("[B]ack to Main Menu", mainWindow::showMainMenu);
        root.addComponent(backBtn);

        // Hotkeys
        hotkeys.put('L', () -> {
            if (resultsList.getItemCount() > 0) {
                resultsList.takeFocus();
            } else {
                searchBox.takeFocus();
            }
        });
        hotkeys.put('S', KeyboardNavigationHelper.focus(searchBtn, this::onSearch));
        hotkeys.put('D', KeyboardNavigationHelper.focus(downloadBtn, this::onDownload));
        hotkeys.put('Q', searchBox::takeFocus);
        hotkeys.put('/', searchBox::takeFocus);
        hotkeys.put('K', () -> {
            if (modVersionCombo.getItemCount() > 0) {
                modVersionCombo.takeFocus();
            }
        });
        hotkeys.put('P', KeyboardNavigationHelper.focus(platformBox, () -> {
            if (platformBox.getItemCount() > 0) {
                int next = (platformBox.getSelectedIndex() + 1) % platformBox.getItemCount();
                platformBox.setSelectedIndex(next);
            }
        }));
        hotkeys.put('O', KeyboardNavigationHelper.focus(loaderBox, () -> {
            if (loaderBox.getItemCount() > 0) {
                int next = (loaderBox.getSelectedIndex() + 1) % loaderBox.getItemCount();
                loaderBox.setSelectedIndex(next);
            }
        }));
        hotkeys.put('V', KeyboardNavigationHelper.focus(versionComboBox, () -> MinecraftVersionHelper.cycleVersion(versionComboBox)));
        hotkeys.put('X', KeyboardNavigationHelper.focus(cancelBtn, this::cancelDownload));
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
    public void onDeactivated() {
        stopTicker();
    }

    private synchronized void stopTicker() {
        if (activeTicker != null) {
            try {
                activeTicker.shutdownNow();
            } catch (Exception ignored) {}
            activeTicker = null;
        }
    }

    @Override
    public void onResized(TerminalSize newSize) {
        if (newSize == null) return;
        this.currentTermWidth = newSize.getColumns();
        int rows = newSize.getRows();

        int actWidth = Math.max(28, Math.min(65, (currentTermWidth * 35) / 100));
        int wsWidth = Math.max(40, currentTermWidth - actWidth - 6);

        if (wsWidth >= 75) {
            int leftWidth = Math.max(36, Math.min(46, wsWidth / 2 - 2));
            int rightWidth = Math.max(38, wsWidth - leftWidth - 4);
            int listHeight = Math.max(8, Math.min(14, rows - 16));

            this.descCardWidth = rightWidth;
            resultsList.setPreferredSize(new TerminalSize(leftWidth, listHeight));
            detailsCard.setPreferredSize(new TerminalSize(rightWidth, listHeight + 1));
            pickaxeAnim.setPreferredSize(new TerminalSize(rightWidth - 4, 3));
        } else {
            int leftWidth = Math.max(36, wsWidth - 4);
            this.descCardWidth = leftWidth;
            resultsList.setPreferredSize(new TerminalSize(leftWidth, 5));
            detailsCard.setPreferredSize(new TerminalSize(leftWidth, 8));
            pickaxeAnim.setPreferredSize(new TerminalSize(leftWidth - 4, 3));
        }
        updateDetailsDisplay();
    }

    private void updateDetailsDisplay() {
        descPanel.removeAllComponents();
        if (selectedMod == null) {
            String q = searchBox != null ? searchBox.getText().trim() : "";
            if (!q.isEmpty() && currentResults.isEmpty()) {
                titleAuthorLabel.setText("Title: No mod found\nAuthor: -");
                Label noModLbl = new Label("No mods found matching query: \"" + q + "\"");
                noModLbl.setForegroundColor(LazyVimTheme.getLogWarnColor());
                descPanel.addComponent(noModLbl);
                Label hintLbl = new Label("Try checking the spelling or changing filters.");
                hintLbl.setForegroundColor(LazyVimTheme.getLogMutedColor());
                descPanel.addComponent(hintLbl);
            } else {
                titleAuthorLabel.setText("Title: No mod selected\nAuthor: -");
                descPanel.addComponent(new Label("Select a mod from the results list to view its description."));
            }
            return;
        }

        String author = (selectedMod.author != null && !selectedMod.author.trim().isEmpty())
                ? selectedMod.author
                : "Unknown";
        titleAuthorLabel.setText("Title: " + selectedMod.name + "\nAuthor: " + author);

        String rawDesc = (selectedMod.description != null && !selectedMod.description.trim().isEmpty())
                ? selectedMod.description.replace("\r\n", " ").replace("\n", " ").trim()
                : "No description provided.";

        int wrapWidth = Math.max(28, descCardWidth - 6);
        List<String> lines = wrapText(rawDesc, wrapWidth);
        int maxLines = Math.min(6, lines.size());
        for (int i = 0; i < maxLines; i++) {
            Label l = new Label(lines.get(i));
            l.setForegroundColor(LazyVimTheme.getActivePalette().fg);
            descPanel.addComponent(l);
        }
        if (lines.size() > maxLines) {
            Label more = new Label("... (" + (lines.size() - maxLines) + " more lines)");
            more.setForegroundColor(LazyVimTheme.getLogMutedColor());
            descPanel.addComponent(more);
        }
    }

    private static List<String> wrapText(String text, int width) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }
        int safeWidth = Math.max(10, width);
        String remaining = text.trim();
        while (!remaining.isEmpty()) {
            if (remaining.length() <= safeWidth) {
                result.add(remaining);
                break;
            }
            int split = remaining.lastIndexOf(' ', safeWidth);
            if (split <= 0) {
                split = Math.min(safeWidth, remaining.length());
            }
            result.add(remaining.substring(0, split).trim());
            remaining = remaining.substring(split).trim();
        }
        return result;
    }

    private void onModSelected(OrchestratorBridge.ModResult mod) {
        if (mod == null) {
            this.selectedMod = null;
            updateDetailsDisplay();
            return;
        }
        this.selectedMod = mod;
        updateDetailsDisplay();

        // Fetch compatible versions for the chosen game version
        String platform = platformBox.getSelectedItem() != null ? platformBox.getSelectedItem().toLowerCase() : "modrinth";
        if ("curseforge".equals(platform)) {
            platform = "curseForge";
        }
        String version = MinecraftVersionHelper.getSelectedVersion(versionComboBox);
        String loader = loaderBox.getSelectedItem() != null ? loaderBox.getSelectedItem() : "fabric";

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
                    try {
                        currentModVersions.clear();
                        currentModVersions.addAll(versions);
                        for (OrchestratorBridge.ModVersionInfo v : versions) {
                            if (v != null && v.versionNumber != null) {
                                modVersionCombo.addItem(v.toString());
                            }
                        }
                    } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private void onSearch() {
        String query = searchBox.getText().trim();
        String platform = platformBox.getSelectedItem() != null ? platformBox.getSelectedItem().toLowerCase() : "modrinth";
        if ("curseforge".equals(platform)) {
            platform = "curseForge";
        }
        String version = MinecraftVersionHelper.getSelectedVersion(versionComboBox);
        String loader = loaderBox.getSelectedItem() != null ? loaderBox.getSelectedItem() : "fabric";

        statusLabel.setText("[BUSY] Searching " + platform + " for '" + query + "'...");
        statusLabel.setForegroundColor(LazyVimTheme.getLogWarnColor());
        ActivityLogger.info("Searching " + platform + " for '" + query + "' (MC " + version + ", " + loader + ")");

        final String targetPlatform = platform;
        new Thread(() -> {
            try {
                List<OrchestratorBridge.ModResult> mods = OrchestratorBridge.getInstance().searchMods(targetPlatform, query, version, loader).get();
                mainWindow.getGui().getGUIThread().invokeLater(() -> {
                    try {
                        currentResults.clear();
                        currentResults.addAll(mods);
                        resultsList.clearItems();
                        selectedMod = null;

                        if (mods.isEmpty()) {
                            modVersionCombo.clearItems();
                            modVersionCombo.addItem("[No Mod Selected]");
                            modVersionCombo.setSelectedIndex(0);
                            currentModVersions.clear();
                            updateDetailsDisplay();

                            statusLabel.setText("No mods found matching query: \"" + query + "\"");
                            statusLabel.setForegroundColor(LazyVimTheme.getLogMutedColor());
                            ActivityLogger.info("No mods found matching query: " + query);
                            searchBox.takeFocus();
                        } else {
                            statusLabel.setText("Found " + mods.size() + " mods. [L]ist / [K] Version / [D]ownload.");
                            statusLabel.setForegroundColor(LazyVimTheme.getLogSuccessColor());
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
                    } catch (Exception err) {
                        ActivityLogger.err("Search UI error: " + err.getMessage());
                    }
                });
            } catch (Exception e) {
                mainWindow.getGui().getGUIThread().invokeLater(() -> {
                    statusLabel.setText("[ERR] Search failed: " + e.getMessage());
                    statusLabel.setForegroundColor(LazyVimTheme.getLogErrorColor());
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
            statusLabel.setForegroundColor(LazyVimTheme.getLogWarnColor());
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
        pickaxeAnim.setProgress(0.0);
        pickaxeAnim.setCustomMessage("Downloading " + mod.name + "...");

        stopTicker();
        if (showAnimation) {
            activeTicker = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "PickaxeAnimTicker");
                t.setDaemon(true);
                return t;
            });
            activeTicker.scheduleAtFixedRate(() -> {
                mainWindow.getGui().getGUIThread().invokeLater(() -> {
                    pickaxeAnim.tick();
                    mainWindow.invalidate();
                });
            }, 50, 110, TimeUnit.MILLISECONDS);
        }

        isDownloading = true;
        statusLabel.setText("[BUSY] Downloading " + mod.name + "...");
        statusLabel.setForegroundColor(LazyVimTheme.getLogWarnColor());
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
                    stopTicker();
                    statusLabel.setText("[OK] " + mod.name + " installed!");
                    statusLabel.setForegroundColor(LazyVimTheme.getLogSuccessColor());
                    pickaxeAnim.setProgress(100.0);
                    pickaxeAnim.setCustomMessage("[OK] " + mod.name + " downloaded & installed!");
                    mainWindow.invalidate();
                    try {
                        mainWindow.getGui().updateScreen();
                    } catch (Exception ignored) {}
                    ActivityLogger.ok("Mod " + mod.name + " downloaded and installed to mods/ folder!");
                });
            } catch (Exception e) {
                mainWindow.getGui().getGUIThread().invokeLater(() -> {
                    isDownloading = false;
                    stopTicker();
                    statusLabel.setText("[ERR] Download failed: " + e.getMessage());
                    statusLabel.setForegroundColor(LazyVimTheme.getLogErrorColor());
                    pickaxeAnim.setCustomMessage("[ERR] " + e.getMessage());
                    mainWindow.invalidate();
                    try {
                        mainWindow.getGui().updateScreen();
                    } catch (Exception ignored) {}
                    ActivityLogger.err("Mod download failed: " + e.getMessage());
                });
            }
        }).start();
    }

    public void cancelDownload() {
        if (!isDownloading) {
            ActivityLogger.info("No mod download currently in progress.");
            return;
        }
        ActivityLogger.warn("Cancelling active mod download...");
        org.codeberg.DeployedReject.tui.backend.JobTracker.getInstance().getActiveJobs().forEach(job -> {
            if ("Mod".equalsIgnoreCase(job.getType())) {
                job.cancel();
            }
        });
        isDownloading = false;
        stopTicker();
        statusLabel.setText("[CANCELLED] Download cancelled.");
        statusLabel.setForegroundColor(LazyVimTheme.getLogWarnColor());
        pickaxeAnim.setCustomMessage("[CANCELLED] Download aborted.");
        mainWindow.invalidate();
    }

    private volatile long lastProgressUiUpdate = 0;

    private void updateProgressUI(String modName, OrchestratorBridge.DownloadProgressInfo info) {
        long now = System.currentTimeMillis();
        if (info.percent < 100.0 && (now - lastProgressUiUpdate < 100)) {
            return;
        }
        lastProgressUiUpdate = now;

        mainWindow.getGui().getGUIThread().invokeLater(() -> {
            String status = String.format("[BUSY] Downloading %.2f%% (ETA: %s @ %s)...",
                    info.percent, info.formattedEta(), info.formattedSpeed());
            statusLabel.setText(status);
            statusLabel.setForegroundColor(LazyVimTheme.getLogWarnColor());

            pickaxeAnim.setProgress(info.percent);
            pickaxeAnim.setCustomMessage(String.format("Downloading %s: %.2f%% (ETA: %s @ %s)",
                    modName, info.percent, info.formattedEta(), info.formattedSpeed()));

            mainWindow.invalidate();
        });
    }
}
