package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyType;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.config.ConfigManager;
import org.codeberg.DeployedReject.tui.theme.GlyphHelper;
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
 * live mod author and full description display with "Read More" dropdown,
 * description pagination, compatible version picker, Minecraft pickaxe
 * loading animation, and download finish ETA estimate.
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
    private final ComboBox<String> descModeCombo;
    private final Label titleAuthorLabel;
    private final Panel descPanel;
    private final Label statusLabel;
    private final MinecraftPickaxeAnimation pickaxeAnim;
    private final Panel detailsCard;
    private final Button searchBtn;
    private final Button downloadBtn;
    private final Button cancelBtn;
    private final Button backBtn;
    private final Button prevPageBtn;
    private final Button nextPageBtn;
    private final Label pageIndicatorLabel;
    private final Panel paginationPanel;

    private final List<OrchestratorBridge.ModResult> currentResults = new ArrayList<>();
    private final List<OrchestratorBridge.ModVersionInfo> currentModVersions = new ArrayList<>();
    private final Map<Character, Runnable> hotkeys = new HashMap<>();
    private final Map<String, String> fullDescCache = new HashMap<>();

    private OrchestratorBridge.ModResult selectedMod = null;
    private boolean isDownloading = false;
    private boolean isFetchingFullDesc = false;
    private int descPageIndex = 0;
    private int currentTermWidth = 80;
    private int descCardWidth = 46;
    private int descLinesPerPage = 8;
    private ScheduledExecutorService activeTicker = null;

    public ModBrowseView(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new Panel(new LinearLayout(Direction.VERTICAL));

        // 1. Top Filters row
        Panel filterPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        filterPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_SEARCH + " [P]lat:")));
        platformBox = new ComboBox<>("Modrinth", "CurseForge");
        filterPanel.addComponent(platformBox);

        filterPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        filterPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_OPTIONS + " L[o]ad:")));
        loaderBox = new ComboBox<>("fabric", "forge", "neoforge", "quilt");
        filterPanel.addComponent(loaderBox);

        filterPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        filterPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_CONFIG + " [V]er:")));
        versionComboBox = MinecraftVersionHelper.createVersionComboBox(mainWindow.getGui(), new TerminalSize(10, 1));
        filterPanel.addComponent(versionComboBox);

        root.addComponent(filterPanel);

        // 2. Search Bar row
        Panel searchPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        searchPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_SEARCH + " [Q] Query: ")));
        searchBox = new TextBox(new TerminalSize(16, 1), "jei");
        searchBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_SEARCH + " [S]earch"), this::onSearch);
        downloadBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_DOWNLOAD + " [D]ownload"), this::onDownload);

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
        cancelBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [X] Cancel Download"), this::cancelDownload);
        searchPanel.addComponent(cancelBtn);
        root.addComponent(searchPanel);

        // 3. Status Label
        statusLabel = new Label(GlyphHelper.apply(GlyphHelper.ICON_INFO + " Type query, press [S] to search, [D] to download, [X] to cancel."));
        statusLabel.setForegroundColor(LazyVimTheme.getLogWarnColor());
        root.addComponent(statusLabel);

        // 4. Middle 2-Column Section (filling full workspace area)
        Panel midCols = new Panel(new LinearLayout(Direction.HORIZONTAL));

        // Left Column: Results List
        Panel leftCol = new Panel(new LinearLayout(Direction.VERTICAL));
        leftCol.addComponent(resultsList.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Results [L]ist (↑/↓)"))));
        midCols.addComponent(leftCol);

        midCols.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        // Right Column: Mod Details, Version Picker, Read More Mode, Description, Pagination, Pickaxe Animation
        Panel rightCol = new Panel(new LinearLayout(Direction.VERTICAL));
        detailsCard = new Panel(new LinearLayout(Direction.VERTICAL));

        titleAuthorLabel = new Label(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Title: -\n" + GlyphHelper.ICON_USER + " Author: -"));
        titleAuthorLabel.setForegroundColor(LazyVimTheme.getAccentColor());
        detailsCard.addComponent(titleAuthorLabel);

        Panel versionRow = new Panel(new LinearLayout(Direction.HORIZONTAL));
        versionRow.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_OPTIONS + " Mod Version [K]: ")));
        modVersionCombo = new ComboBox<>("[Latest Compatible]");
        versionRow.addComponent(modVersionCombo);
        detailsCard.addComponent(versionRow);

        Panel modeRow = new Panel(new LinearLayout(Direction.HORIZONTAL));
        modeRow.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_FILE + " View [M]ode: ")));
        descModeCombo = new ComboBox<>(GlyphHelper.apply("▾ Summary (Heading)"), GlyphHelper.apply("▾ Read More (Full Description)"));
        descModeCombo.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
            descPageIndex = 0;
            if (selectedIndex == 1 && selectedMod != null) {
                fetchFullDescriptionIfNeeded(selectedMod);
            }
            updateDetailsDisplay();
        });
        modeRow.addComponent(descModeCombo);
        detailsCard.addComponent(modeRow);

        descPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        descPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_INFO + " Select a mod from the list to view its description.")));
        detailsCard.addComponent(descPanel.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Description"))));

        // Pagination controls for long descriptions
        paginationPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        prevPageBtn = new Button(GlyphHelper.apply("◀ Prev ([)"), this::onPrevPage);
        pageIndicatorLabel = new Label(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Page 1/1"));
        pageIndicatorLabel.setForegroundColor(LazyVimTheme.getLogMutedColor());
        nextPageBtn = new Button(GlyphHelper.apply("Next (]) ▶"), this::onNextPage);

        paginationPanel.addComponent(prevPageBtn);
        paginationPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        paginationPanel.addComponent(pageIndicatorLabel);
        paginationPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        paginationPanel.addComponent(nextPageBtn);
        detailsCard.addComponent(paginationPanel);

        pickaxeAnim = new MinecraftPickaxeAnimation();
        pickaxeAnim.setProgress(0.0);
        pickaxeAnim.setCustomMessage("Ready to download mods");
        detailsCard.addComponent(pickaxeAnim.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_TOOL + " Download Status"))));

        detailsCard.setPreferredSize(new TerminalSize(48, 12));
        rightCol.addComponent(detailsCard.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_MOD + " Mod Details & Versions"))));
        midCols.addComponent(rightCol);

        root.addComponent(midCols);

        // Instant arrow-key selection tracking: updates description and compatible versions immediately
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
        backBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_BACK + " [B]ack to Main Menu"), mainWindow::showMainMenu);
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
        hotkeys.put('M', () -> {
            descModeCombo.takeFocus();
            int next = (descModeCombo.getSelectedIndex() + 1) % descModeCombo.getItemCount();
            descModeCombo.setSelectedIndex(next);
        });
        hotkeys.put('[', this::onPrevPage);
        hotkeys.put(']', this::onNextPage);
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
        return GlyphHelper.apply(GlyphHelper.ICON_DOWNLOAD + " Download & Browse Mods");
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

        int middleHeight = Math.max(10, rows - 13);

        if (wsWidth >= 70) {
            int leftWidth = Math.max(34, (wsWidth * 42) / 100);
            int rightWidth = Math.max(38, wsWidth - leftWidth - 3);

            this.descCardWidth = rightWidth;
            this.descLinesPerPage = Math.max(5, middleHeight - 11);

            resultsList.setPreferredSize(new TerminalSize(leftWidth, middleHeight));
            detailsCard.setPreferredSize(new TerminalSize(rightWidth, middleHeight));
            descPanel.setPreferredSize(new TerminalSize(rightWidth - 4, descLinesPerPage + 2));
            pickaxeAnim.setPreferredSize(new TerminalSize(Math.max(20, rightWidth - 4), 3));
            searchBox.setPreferredSize(new TerminalSize(Math.max(16, (wsWidth * 25) / 100), 1));
        } else {
            int fullWidth = Math.max(34, wsWidth - 4);
            this.descCardWidth = fullWidth;
            int halfH = Math.max(5, middleHeight / 2);
            this.descLinesPerPage = Math.max(4, halfH - 6);

            resultsList.setPreferredSize(new TerminalSize(fullWidth, halfH));
            detailsCard.setPreferredSize(new TerminalSize(fullWidth, halfH + 2));
            descPanel.setPreferredSize(new TerminalSize(fullWidth - 4, descLinesPerPage + 2));
            pickaxeAnim.setPreferredSize(new TerminalSize(Math.max(20, fullWidth - 4), 3));
        }
        updateDetailsDisplay();
    }

    private void onPrevPage() {
        if (descPageIndex > 0) {
            descPageIndex--;
            updateDetailsDisplay();
            mainWindow.invalidate();
        }
    }

    private void onNextPage() {
        descPageIndex++;
        updateDetailsDisplay();
        mainWindow.invalidate();
    }

    private void fetchFullDescriptionIfNeeded(OrchestratorBridge.ModResult mod) {
        if (mod == null) return;
        String platform = platformBox.getSelectedItem() != null ? platformBox.getSelectedItem().toLowerCase() : "modrinth";
        if ("curseforge".equals(platform)) {
            platform = "curseForge";
        }
        String cacheKey = (platform + ":" + mod.id).toLowerCase();
        if (fullDescCache.containsKey(cacheKey)) {
            return;
        }

        isFetchingFullDesc = true;
        final String targetPlatform = platform;
        final String modId = mod.id;

        OrchestratorBridge.getInstance().getModFullDescription(targetPlatform, modId)
                .thenAccept(fullDesc -> {
                    mainWindow.getGui().getGUIThread().invokeLater(() -> {
                        if (fullDesc != null && !fullDesc.trim().isEmpty()) {
                            fullDescCache.put(cacheKey, fullDesc.trim());
                        } else {
                            fullDescCache.put(cacheKey, "No extended description provided by " + targetPlatform + ".");
                        }
                        isFetchingFullDesc = false;
                        if (selectedMod != null && selectedMod.id.equals(modId)) {
                            updateDetailsDisplay();
                            mainWindow.invalidate();
                        }
                    });
                }).exceptionally(ex -> {
                    mainWindow.getGui().getGUIThread().invokeLater(() -> {
                        fullDescCache.put(cacheKey, "Failed to load full description: " + ex.getMessage());
                        isFetchingFullDesc = false;
                        if (selectedMod != null && selectedMod.id.equals(modId)) {
                            updateDetailsDisplay();
                            mainWindow.invalidate();
                        }
                    });
                    return null;
                });
    }

    private void updateDetailsDisplay() {
        descPanel.removeAllComponents();
        if (selectedMod == null) {
            String q = searchBox != null ? searchBox.getText().trim() : "";
            if (!q.isEmpty() && currentResults.isEmpty()) {
                titleAuthorLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Title: No mod found\n" + GlyphHelper.ICON_USER + " Author: -"));
                Label noModLbl = new Label(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " No mods found matching query: \"" + q + "\""));
                noModLbl.setForegroundColor(LazyVimTheme.getLogWarnColor());
                descPanel.addComponent(noModLbl);
                Label hintLbl = new Label(GlyphHelper.apply(GlyphHelper.ICON_INFO + " Try checking the spelling or changing filters."));
                hintLbl.setForegroundColor(LazyVimTheme.getLogMutedColor());
                descPanel.addComponent(hintLbl);
            } else {
                titleAuthorLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Title: No mod selected\n" + GlyphHelper.ICON_USER + " Author: -"));
                descPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_INFO + " Select a mod from the results list to view its description.")));
            }
            pageIndicatorLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Page 1/1"));
            return;
        }

        String author = (selectedMod.author != null && !selectedMod.author.trim().isEmpty())
                ? selectedMod.author
                : "Unknown";
        titleAuthorLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Title: " + selectedMod.name + "\n" + GlyphHelper.ICON_USER + " Author: " + author));

        boolean isFullDescMode = (descModeCombo.getSelectedIndex() == 1);
        String platform = platformBox.getSelectedItem() != null ? platformBox.getSelectedItem().toLowerCase() : "modrinth";
        if ("curseforge".equals(platform)) {
            platform = "curseForge";
        }
        String cacheKey = (platform + ":" + selectedMod.id).toLowerCase();

        List<String> lines;
        if (isFullDescMode) {
            if (isFetchingFullDesc && !fullDescCache.containsKey(cacheKey)) {
                Label loadingLbl = new Label(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " Fetching full description from " + platform + "..."));
                loadingLbl.setForegroundColor(LazyVimTheme.getAccentColor());
                descPanel.addComponent(loadingLbl);
                pageIndicatorLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Loading..."));
                return;
            }
            String fullDesc = fullDescCache.get(cacheKey);
            if (fullDesc == null || fullDesc.trim().isEmpty()) {
                fetchFullDescriptionIfNeeded(selectedMod);
                Label loadingLbl = new Label(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " Requesting full description..."));
                loadingLbl.setForegroundColor(LazyVimTheme.getAccentColor());
                descPanel.addComponent(loadingLbl);
                pageIndicatorLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_FILE + " Loading..."));
                return;
            }
            int wrapWidth = Math.max(28, descCardWidth - 6);
            lines = formatAndWrapDescription(fullDesc, wrapWidth);
        } else {
            String rawDesc = (selectedMod.description != null && !selectedMod.description.trim().isEmpty())
                ? selectedMod.description
                : "No description provided.";
            int wrapWidth = Math.max(28, descCardWidth - 6);
            lines = formatAndWrapDescription(rawDesc, wrapWidth);
        }

        if (lines.isEmpty()) {
            lines.add("No description text available.");
        }

        int pageSize = Math.max(4, descLinesPerPage);
        int totalPages = Math.max(1, (int) Math.ceil((double) lines.size() / pageSize));
        if (descPageIndex >= totalPages) {
            descPageIndex = totalPages - 1;
        }
        if (descPageIndex < 0) {
            descPageIndex = 0;
        }

        int startIdx = descPageIndex * pageSize;
        int endIdx = Math.min(lines.size(), startIdx + pageSize);

        for (int i = startIdx; i < endIdx; i++) {
            String line = lines.get(i);
            Label l = new Label(line.isEmpty() ? " " : line);
            l.setForegroundColor(LazyVimTheme.getActivePalette().fg);
            descPanel.addComponent(l);
        }

        pageIndicatorLabel.setText(GlyphHelper.apply(String.format(GlyphHelper.ICON_FILE + " Page %d/%d (%d lines)", descPageIndex + 1, totalPages, lines.size())));
    }

    private List<String> formatAndWrapDescription(String text, int width) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }
        String[] paragraphs = text.split("\r?\n");
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) {
                if (!result.isEmpty() && !result.get(result.size() - 1).isEmpty()) {
                    result.add("");
                }
                continue;
            }
            result.addAll(wrapText(trimmed, width));
        }
        return result;
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
        this.descPageIndex = 0;
        if (descModeCombo != null && descModeCombo.getSelectedIndex() == 1) {
            fetchFullDescriptionIfNeeded(mod);
        }
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

        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " [BUSY] Searching " + platform + " for '" + query + "'..."));
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

                            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_FILE + " No mods found matching query: \"" + query + "\""));
                            statusLabel.setForegroundColor(LazyVimTheme.getLogMutedColor());
                            ActivityLogger.info("No mods found matching query: " + query);
                            searchBox.takeFocus();
                        } else {
                            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [OK] Found " + mods.size() + " mods. [L]ist / [K] Version / [D]ownload."));
                            statusLabel.setForegroundColor(LazyVimTheme.getLogSuccessColor());
                            ActivityLogger.ok("Found " + mods.size() + " mods for query: " + query);

                            for (OrchestratorBridge.ModResult m : mods) {
                                resultsList.addItem(GlyphHelper.apply(GlyphHelper.ICON_MOD + " " + m.name + (m.author.isEmpty() ? "" : " • " + m.author)), () -> {
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
                    statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [ERR] Search failed: " + e.getMessage()));
                    statusLabel.setForegroundColor(LazyVimTheme.getLogErrorColor());
                    ActivityLogger.err("Mod search failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private void onDownload() {
        if (isDownloading) {
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " [WARN] Another download is already running."));
            return;
        }

        if (selectedMod == null && resultsList.getSelectedIndex() >= 0 && resultsList.getSelectedIndex() < currentResults.size()) {
            selectedMod = currentResults.get(resultsList.getSelectedIndex());
        }
        if (selectedMod == null) {
            statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_WARN + " [WARN] Select a mod from the list first!"));
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
        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_BUSY + " [BUSY] Downloading " + mod.name + "..."));
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
                    statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [OK] " + mod.name + " installed!"));
                    statusLabel.setForegroundColor(LazyVimTheme.getLogSuccessColor());
                    pickaxeAnim.setProgress(100.0);
                    pickaxeAnim.setCustomMessage(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [OK] " + mod.name + " downloaded & installed!"));
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
                    statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [ERR] Download failed: " + e.getMessage()));
                    statusLabel.setForegroundColor(LazyVimTheme.getLogErrorColor());
                    pickaxeAnim.setCustomMessage(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [ERR] " + e.getMessage()));
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
        statusLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [CANCELLED] Download cancelled."));
        statusLabel.setForegroundColor(LazyVimTheme.getLogWarnColor());
        pickaxeAnim.setCustomMessage(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [CANCELLED] Download aborted."));
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
            String status = String.format("%s [BUSY] Downloading %.2f%% (ETA: %s @ %s)...",
                    GlyphHelper.ICON_BUSY, info.percent, info.formattedEta(), info.formattedSpeed());
            statusLabel.setText(GlyphHelper.apply(status));
            statusLabel.setForegroundColor(LazyVimTheme.getLogWarnColor());

            pickaxeAnim.setProgress(info.percent);
            pickaxeAnim.setCustomMessage(GlyphHelper.apply(String.format("%s Downloading %s: %.2f%% (ETA: %s @ %s)",
                    GlyphHelper.ICON_TOOL, modName, info.percent, info.formattedEta(), info.formattedSpeed())));

            mainWindow.invalidate();
        });
    }
}
